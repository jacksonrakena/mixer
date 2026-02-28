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
                            (Transaction.assetId eq asset) //and
                            //  / /(Transaction.timestamp greater after.to()) TODO fix
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

    suspend fun regenerateAggregatesForAsset(
        assetId: Uuid,
        userTimezone: TimeZone,
    ) {
        clearAggregatesForAsset(assetId)
        MDC.put("assetId", assetId.toString())
        try {
            val today = Clock.System.now().toLocalDateTime(userTimezone).date

            // Resolve provider info for market data lookup
            val (provider, providerData) = transaction {
                val row = Asset.selectAll().where { Asset.id eq assetId }.firstOrNull()
                    ?: return@transaction Pair("USER", null as String?)
                Pair(row[Asset.provider], row[Asset.providerData])
            }

            val marketPrices: Map<LocalDate, Double>? = resolveMarketPrices(provider, providerData, today, assetId)

            val aggregates = aggregationService.forwardAggregate(
                assetId,
                userTimezone,
                DatabaseAssetTransactionSource(),
                today,
                marketPrices,
            )
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
            }
            logger.info { "Regenerated aggregates for asset $assetId, total ${aggregates.size} entries, through $today" }
        } finally {
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