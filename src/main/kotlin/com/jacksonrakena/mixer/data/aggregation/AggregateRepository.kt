package com.jacksonrakena.mixer.data.aggregation

import com.jacksonrakena.mixer.data.tables.concrete.Asset
import com.jacksonrakena.mixer.data.tables.concrete.User
import com.jacksonrakena.mixer.data.tables.virtual.AssetAggregate
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Component
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * State of an asset relevant for aggregation decisions.
 */
data class AssetAggregationState(
    val provider: String,
    val providerData: String?,
    val staleAfter: Long,
    val aggregatedThrough: LocalDate?,
)

/**
 * State snapshot from an existing aggregate row, used for partial reaggregation.
 */
data class PartialAggregateState(
    val startDate: LocalDate,
    val initialHolding: Double,
    val initialPrice: Double?,
    val initialPriceDate: LocalDate?,
    val initialCostBasis: Double = 0.0,
)

/**
 * An asset that needs (re)aggregation, with its owner's timezone.
 */
data class StaleAsset(
    val assetId: Uuid,
    val userTimezone: TimeZone,
)

@Component
class AggregateRepository {
    private val logger = KotlinLogging.logger {}

    fun batchInsertAggregates(assetId: Uuid, aggregates: Collection<AssetTransactionAggregation>) {
        transaction {
            AssetAggregate.batchInsert(aggregates) { agg ->
                this[AssetAggregate.assetId] = assetId
                this[AssetAggregate.aggregationPeriod] = AggregationPeriod.DAILY
                this[AssetAggregate.periodEndDate] = LocalDate.parse(agg.date)
                this[AssetAggregate.totalValue] = agg.nativeValue
                this[AssetAggregate.holding] = agg.amount
                this[AssetAggregate.deltaReconciliation] = agg.amountDeltaReconciliation
                this[AssetAggregate.deltaTrades] = agg.amountDeltaTrades
                this[AssetAggregate.deltaOther] = agg.amountDeltaOther
                this[AssetAggregate.unitPrice] = agg.unitPrice
                this[AssetAggregate.valueDate] = agg.valueDate
                this[AssetAggregate.costBasis] = agg.costBasis
                this[AssetAggregate.cashFlowNative] = agg.cashFlowNative
            }
        }
    }

    fun deleteAggregatesForAsset(assetId: Uuid) {
        transaction {
            AssetAggregate.deleteWhere { AssetAggregate.assetId eq assetId }
        }
    }

    fun deleteAggregatesFrom(assetId: Uuid, fromDate: LocalDate) {
        transaction {
            AssetAggregate.deleteWhere {
                (AssetAggregate.assetId eq assetId) and
                        (AssetAggregate.periodEndDate greaterEq fromDate)
            }
        }
    }

    /**
     * Returns the last aggregate before the given date, used to seed partial reaggregation.
     */
    fun getLastAggregateBefore(assetId: Uuid, beforeDate: LocalDate): PartialAggregateState? {
        val row = transaction {
            AssetAggregate.selectAll()
                .where {
                    (AssetAggregate.assetId eq assetId) and
                            (AssetAggregate.periodEndDate less beforeDate)
                }
                .orderBy(AssetAggregate.periodEndDate, SortOrder.DESC)
                .limit(1)
                .firstOrNull()
        } ?: return null

        return PartialAggregateState(
            startDate = beforeDate,
            initialHolding = row[AssetAggregate.holding],
            initialPrice = row[AssetAggregate.unitPrice],
            initialPriceDate = row[AssetAggregate.valueDate],
            initialCostBasis = row[AssetAggregate.costBasis],
        )
    }

    /**
     * Marks an asset as aggregated through the given date and clears the stale marker
     * only if it hasn't been modified concurrently.
     */
    fun markAssetAggregated(assetId: Uuid, through: LocalDate, expectedStaleAfter: Long) {
        transaction {
            Asset.update({ Asset.id eq assetId }) {
                it[aggregatedThrough] = through
            }
            Asset.update({ (Asset.id eq assetId) and (Asset.staleAfter eq expectedStaleAfter) }) {
                it[staleAfter] = 0L
            }
        }
    }

    /**
     * Resets an asset's aggregatedThrough to null, forcing a full reaggregation on next run.
     */
    fun resetAssetAggregatedThrough(assetId: Uuid) {
        transaction {
            Asset.update({ Asset.id eq assetId }) {
                it[aggregatedThrough] = null
                it[staleAfter] = 0L
            }
        }
    }

    fun getAssetAggregationState(assetId: Uuid): AssetAggregationState? {
        val row = transaction {
            Asset.selectAll().where { Asset.id eq assetId }.firstOrNull()
        } ?: return null

        return AssetAggregationState(
            provider = row[Asset.provider],
            providerData = row[Asset.providerData],
            staleAfter = row[Asset.staleAfter],
            aggregatedThrough = row[Asset.aggregatedThrough],
        )
    }

    /**
     * Finds all assets whose aggregation is behind today (in the owner's timezone)
     * or has never been computed.
     */
    fun findStaleAssets(): List<StaleAsset> {
        val allAssets = transaction {
            (Asset innerJoin User)
                .selectAll()
                .map { Triple(it[Asset.id], TimeZone.of(it[User.timezone]), it[Asset.aggregatedThrough]) }
        }
        return allAssets.filter { (_, tz, aggregatedThrough) ->
            val userToday = Clock.System.now().toLocalDateTime(tz).date
            aggregatedThrough == null || aggregatedThrough < userToday
        }.map { (assetId, tz, _) -> StaleAsset(assetId, tz) }
    }

    /**
     * Attempts to acquire a PostgreSQL advisory lock for the given asset ID.
     * Returns true if the lock was acquired, false if another process holds it.
     * On non-PostgreSQL databases (e.g. H2 in tests), always returns true.
     */
    fun tryAdvisoryLock(assetId: Uuid): Boolean {
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

    fun releaseAdvisoryLock(assetId: Uuid) {
        try {
            transaction {
                val key = assetId.hashCode().toLong()
                exec("SELECT pg_advisory_unlock($key)")
            }
        } catch (_: Exception) {
            // Not on PostgreSQL; no lock to release
        }
    }

    /**
     * Returns assets and timezone for a given user, or null if the user doesn't exist.
     */
    fun getUserAssetsWithTimezone(userId: Uuid): Pair<List<Uuid>, TimeZone>? {
        return transaction {
            val tz = User.selectAll().where { User.id eq userId }.firstOrNull()?.get(User.timezone) ?: return@transaction null
            val assetIds = Asset.selectAll().where { Asset.ownerId eq userId }.map { it[Asset.id] }
            Pair(assetIds, TimeZone.of(tz))
        }
    }
}
