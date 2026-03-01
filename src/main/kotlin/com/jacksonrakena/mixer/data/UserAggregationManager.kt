package com.jacksonrakena.mixer.data

import com.jacksonrakena.mixer.data.market.MarketDataProvider
import com.jacksonrakena.mixer.data.tables.concrete.Asset
import com.jacksonrakena.mixer.data.tables.concrete.Transaction
import com.jacksonrakena.mixer.data.tables.concrete.User
import com.jacksonrakena.mixer.data.tables.virtual.AssetAggregate
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.MDC
import org.springframework.stereotype.Component
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Component
class UserAggregationManager(
    val database: Database,
    val aggregationService: AggregationService,
    val marketDataProvider: MarketDataProvider,
) {
    companion object {
        val logger = KotlinLogging.logger {}
    }

    suspend fun forceAggregateUserAssets(
        userId: Uuid
    ) {
        val result = transaction {
            val tz = User.selectAll().where { User.id eq userId }.firstOrNull()?.get(User.timezone) ?: return@transaction null
            val assets = Asset.selectAll().where { Asset.ownerId.eq(userId) }.toList()
            Pair(assets, TimeZone.of(tz))
        } ?: return
        val (existingAssets, userTimezone) = result
        MDC.put("userId", userId.toString())
        try {
            logger.info { "Forcing aggregation for user $userId (tz=$userTimezone), total ${existingAssets.size} assets" }
            for (asset in existingAssets) {
                regenerateAggregatesForAsset(asset[Asset.id], userTimezone)
            }
        } finally {
            MDC.remove("userId")
        }
    }

    class DatabaseAssetTransactionSource: AssetTransactionSource {
        override suspend fun getLatestReconciliation(asset: Uuid, before: Instant): AssetTransaction? {
            return transaction {
                val result = Transaction.selectAll()
                    .where {
                        (Transaction.assetId eq asset) and
                                (Transaction.type eq AssetTransactionType.Reconciliation) and
                                (Transaction.timestamp less before.toEpochMilliseconds())
                    }
                    .orderBy(Transaction.timestamp, SortOrder.DESC)
                    .limit(1)
                    .firstOrNull()

                if (result != null) {
                    return@transaction AssetTransaction(
                        assetId = result[Transaction.assetId],
                        timestamp = Instant.Companion.fromEpochMilliseconds(result[Transaction.timestamp]),
                        type = result[Transaction.type],
                        amount = result[Transaction.amount],
                        value = result[Transaction.value]
                    )
                }
                return@transaction null
            }
        }

        override suspend fun getTransactions(asset: Uuid, after: Instant?): Iterable<AssetTransaction> {
            return transaction {
                val result = if (after == null) {
                    Transaction.selectAll()
                        .where {
                            (Transaction.assetId eq asset)
                        }
                } else {
                    Transaction.selectAll()
                        .where {
                            (Transaction.assetId eq asset) and
                                (Transaction.timestamp greaterEq after.toEpochMilliseconds())
                        }
                }.orderBy(Transaction.timestamp, SortOrder.DESC)

                return@transaction result.map {
                    AssetTransaction(
                        assetId = it[Transaction.assetId],
                        timestamp = Instant.Companion.fromEpochMilliseconds(it[Transaction.timestamp]),
                        type = it[Transaction.type],
                        amount = it[Transaction.amount],
                        value = it[Transaction.value]
                    )
                }.toList()
            }.toList()
        }

        override suspend fun getEarliestTransaction(asset: Uuid, after: Instant?): AssetTransaction? {
            return transaction {
                val result = if (after == null) {
                    Transaction.selectAll()
                        .where {
                            (Transaction.assetId eq asset)
                        }
                } else {
                    Transaction.selectAll()
                        .where {
                            (Transaction.assetId eq asset) and
                                    (Transaction.timestamp greater after.toEpochMilliseconds())
                        }
                }.orderBy(Transaction.timestamp, SortOrder.ASC)
                    .limit(1)
                    .firstOrNull()
                if (result != null) {
                    return@transaction AssetTransaction(
                        assetId = result[Transaction.assetId],
                        timestamp = Instant.Companion.fromEpochMilliseconds(result[Transaction.timestamp]),
                        type = result[Transaction.type],
                        amount = result[Transaction.amount],
                        value = result[Transaction.value]
                    )
                }
                return@transaction null
            }
        }
    }

    suspend fun clearAggregatesForAsset(
        assetId: Uuid
    ) {
        transaction {
            AssetAggregate.deleteWhere { AssetAggregate.assetId eq assetId }
        }
    }

    /**
     * Attempts to acquire a PostgreSQL advisory lock for the given asset ID.
     * Returns true if the lock was acquired, false if another process holds it.
     * On non-PostgreSQL databases (e.g. H2 in tests), always returns true.
     */
    private fun tryAdvisoryLock(assetId: Uuid): Boolean {
        return try {
            transaction {
                val key = assetId.hashCode().toLong()
                exec("SELECT pg_try_advisory_lock($key)") { rs ->
                    rs.next() && rs.getBoolean(1)
                } ?: false
            }
        } catch (_: Exception) {
            true
        }
    }

    private fun releaseAdvisoryLock(assetId: Uuid) {
        try {
            transaction {
                val key = assetId.hashCode().toLong()
                exec("SELECT pg_advisory_unlock($key)")
            }
        } catch (_: Exception) {
            // Not on PostgreSQL; no lock to release
        }
    }

    suspend fun regenerateAggregatesForAsset(
        assetId: Uuid,
        userTimezone: TimeZone,
    ) {
        // Acquire an advisory lock to prevent concurrent aggregation of the same asset.
        if (!tryAdvisoryLock(assetId)) {
            logger.info { "Asset $assetId is being aggregated by another instance, skipping" }
            return
        }

        val totalStart = System.nanoTime()
        MDC.put("assetId", assetId.toString())
        try {
            val today = Clock.System.now().toLocalDateTime(userTimezone).date

            // Resolve provider info and staleness state
            val resolveStart = System.nanoTime()
            val assetRow = transaction {
                Asset.selectAll().where { Asset.id eq assetId }.firstOrNull()
            }
            if (assetRow == null) {
                logger.warn { "Asset $assetId not found, skipping reaggregation" }
                return
            }
            val provider = assetRow[Asset.provider]
            val providerData = assetRow[Asset.providerData]
            val staleAfter = assetRow[Asset.staleAfter]
            val currentAggregatedThrough = assetRow[Asset.aggregatedThrough]
            val resolveMs = (System.nanoTime() - resolveStart) / 1_000_000.0

            // Skip if asset is already fully up-to-date (handles duplicate job enqueuing)
            if (staleAfter == 0L && currentAggregatedThrough != null && currentAggregatedThrough >= today) {
                val totalMs = (System.nanoTime() - totalStart) / 1_000_000.0
                logger.info { "Asset $assetId already up-to-date (through=$currentAggregatedThrough), skipping | total=${String.format("%.1f", totalMs)}ms" }
                return
            }

            // Determine if partial reaggregation is possible:
            // requires a staleAfter marker and at least one existing aggregate before that date
            data class PartialState(
                val startDate: LocalDate,
                val initialHolding: Double,
                val initialPrice: Double?,
                val initialPriceDate: LocalDate?,
            )
            val partialStart = System.nanoTime()
            val partialState: PartialState? = if (staleAfter > 0L) {
                val staleDate = Instant.fromEpochMilliseconds(staleAfter).toLocalDateTime(userTimezone).date
                val lastValid = transaction {
                    AssetAggregate.selectAll()
                        .where {
                            (AssetAggregate.assetId eq assetId) and
                                    (AssetAggregate.periodEndDate less staleDate)
                        }
                        .orderBy(AssetAggregate.periodEndDate, SortOrder.DESC)
                        .limit(1)
                        .firstOrNull()
                }
                if (lastValid != null) {
                    PartialState(
                        startDate = staleDate,
                        initialHolding = lastValid[AssetAggregate.holding],
                        initialPrice = lastValid[AssetAggregate.unitPrice],
                        initialPriceDate = lastValid[AssetAggregate.valueDate],
                    )
                } else null
            } else null

            val deleteStart = System.nanoTime()
            val partialCheckMs = (deleteStart - partialStart) / 1_000_000.0
            if (partialState != null) {
                transaction {
                    AssetAggregate.deleteWhere {
                        (AssetAggregate.assetId eq assetId) and
                                (AssetAggregate.periodEndDate greaterEq partialState.startDate)
                    }
                }
                logger.info { "Partial reaggregation for asset $assetId from ${partialState.startDate}" }
            } else {
                clearAggregatesForAsset(assetId)
            }
            val deleteMs = (System.nanoTime() - deleteStart) / 1_000_000.0

            val marketStart = System.nanoTime()
            val marketPrices: Map<LocalDate, Double>? = resolveMarketPrices(provider, providerData, today, assetId)
            val marketMs = (System.nanoTime() - marketStart) / 1_000_000.0

            val aggStart = System.nanoTime()
            val aggregates = aggregationService.forwardAggregate(
                assetId,
                userTimezone,
                DatabaseAssetTransactionSource(),
                today,
                marketPrices,
                startOverride = partialState?.startDate,
                initialHolding = partialState?.initialHolding ?: 0.0,
                initialPrice = partialState?.initialPrice,
                initialPriceDate = partialState?.initialPriceDate,
            )
            val aggMs = (System.nanoTime() - aggStart) / 1_000_000.0

            val insertStart = System.nanoTime()
            transaction {
                AssetAggregate.batchInsert(aggregates) { agg ->
                    this[AssetAggregate.assetId] = assetId
                    this[AssetAggregate.aggregationPeriod] = AggregationPeriod.DAILY
                    this[AssetAggregate.periodEndDate] =
                        LocalDate.parse(agg.date)
                    this[AssetAggregate.totalValue] = agg.nativeValue
                    this[AssetAggregate.holding] = agg.amount
                    this[AssetAggregate.deltaReconciliation] = agg.amountDeltaReconciliation
                    this[AssetAggregate.deltaTrades] = agg.amountDeltaTrades
                    this[AssetAggregate.deltaOther] = agg.amountDeltaOther
                    this[AssetAggregate.unitPrice] = agg.unitPrice
                    this[AssetAggregate.valueDate] = agg.valueDate
                }
                Asset.update({ Asset.id eq assetId }) {
                    it[aggregatedThrough] = today
                }
                // Only clear staleAfter if it hasn't been modified since we read it,
                // preserving any new staleness markers set by concurrent transaction changes
                Asset.update({ (Asset.id eq assetId) and (Asset.staleAfter eq staleAfter) }) {
                    it[Asset.staleAfter] = 0L
                }
            }
            val insertMs = (System.nanoTime() - insertStart) / 1_000_000.0
            val totalMs = (System.nanoTime() - totalStart) / 1_000_000.0

            val mode = if (partialState != null) "partial(from=${partialState.startDate})" else "full"
            logger.info {
                "Reaggregation complete for asset $assetId: mode=$mode, entries=${aggregates.size}, through=$today | " +
                "total=${String.format("%.1f", totalMs)}ms " +
                "[resolve=${String.format("%.1f", resolveMs)}ms, " +
                "partialCheck=${String.format("%.1f", partialCheckMs)}ms, " +
                "delete=${String.format("%.1f", deleteMs)}ms, " +
                "market=${String.format("%.1f", marketMs)}ms, " +
                "aggregate=${String.format("%.1f", aggMs)}ms, " +
                "insert=${String.format("%.1f", insertMs)}ms]"
            }
        } finally {
            releaseAdvisoryLock(assetId)
            MDC.remove("assetId")
        }
    }

    /**
     * Resolves market prices for an asset based on its provider.
     * Returns null for USER assets (no market pricing).
     */
    private fun resolveMarketPrices(
        provider: String,
        providerData: String?,
        today: LocalDate,
        assetId: Uuid,
    ): Map<LocalDate, Double>? {
        if (provider == "USER") return null

        if (provider == "YFIN") {
            val ticker = extractTickerCode(providerData, assetId) ?: return null
            // Fetch from earliest possible date — Yahoo will clamp to available data
            val startDate = LocalDate(2000, 1, 1)
            return try {
                marketDataProvider.getHistoricalPrices(ticker, startDate, today)
            } catch (e: Exception) {
                logger.error(e) { "Failed to fetch market data for asset $assetId (ticker=$ticker)" }
                null
            }
        }

        logger.warn { "Unknown provider '$provider' for asset $assetId, skipping market data" }
        return null
    }

    private fun extractTickerCode(providerData: String?, assetId: Uuid): String? {
        if (providerData == null) {
            logger.warn { "YFIN asset $assetId has no providerData" }
            return null
        }
        return try {
            Json.parseToJsonElement(providerData).jsonObject["tickerCode"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse providerData for asset $assetId: $providerData" }
            null
        }
    }

    /**
     * Checks all assets in the database and regenerates aggregations for any
     * whose [Asset.aggregatedThrough] is behind today's date or has never been computed.
     * Each asset's "today" is determined by its owner's configured timezone.
     */
    suspend fun ensureAllAggregationsUpToDate() {
        val allAssets = transaction {
            (Asset innerJoin User)
                .selectAll()
                .map { Triple(it[Asset.id], TimeZone.of(it[User.timezone]), it[Asset.aggregatedThrough]) }
        }
        val staleAssets = allAssets.filter { (_, tz, aggregatedThrough) ->
            val userToday = Clock.System.now().toLocalDateTime(tz).date
            aggregatedThrough == null || aggregatedThrough < userToday
        }
        if (staleAssets.isEmpty()) {
            logger.debug { "All asset aggregations are up-to-date" }
            return
        }
        logger.info { "Found ${staleAssets.size} assets needing aggregation refresh" }
        for ((assetId, userTimezone, _) in staleAssets) {
            try {
                regenerateAggregatesForAsset(assetId, userTimezone)
            } catch (e: Exception) {
                logger.error(e) { "Failed to refresh aggregations for asset $assetId" }
            }
        }
    }
}