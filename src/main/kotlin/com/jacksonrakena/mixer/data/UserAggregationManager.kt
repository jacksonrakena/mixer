package com.jacksonrakena.mixer.data

import com.jacksonrakena.mixer.data.tables.concrete.Asset
import com.jacksonrakena.mixer.data.tables.concrete.Transaction
import com.jacksonrakena.mixer.data.tables.virtual.AssetAggregate
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
    val aggregationService: AggregationService
) {
    companion object {
        val logger = KotlinLogging.logger {}
    }

    suspend fun forceAggregateUserAssets(
        userId: Uuid
    ) {
        val existingAssets = transaction {
            Asset.selectAll().where { Asset.ownerId.eq(userId) }.toList()
        }
        MDC.put("userId", userId.toString())
        try {
            logger.info { "Forcing aggregation for user $userId, total ${existingAssets.size} assets" }
            for (asset in existingAssets) {
                regenerateAggregatesForAsset(asset[Asset.id])
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
        assetId: Uuid
    ) {
        clearAggregatesForAsset(assetId)
        MDC.put("assetId", assetId.toString())
        try {
            val today = Clock.System.now().toLocalDateTime(TimeZone.Companion.currentSystemDefault()).date
            val aggregates = aggregationService.forwardAggregate(
                assetId,
                TimeZone.Companion.currentSystemDefault(),
                DatabaseAssetTransactionSource(),
                today
            )
            transaction {
                AssetAggregate.batchInsert(aggregates) { agg ->
                    this[AssetAggregate.assetId] = assetId
                    this[AssetAggregate.aggregationPeriod] = AggregationPeriod.DAILY
                    this[AssetAggregate.periodEndDate] =
                        agg.date.toLocalDateTime(TimeZone.Companion.currentSystemDefault()).date
                    this[AssetAggregate.totalValue] = agg.amount
                    this[AssetAggregate.deltaReconciliation] = agg.amountDeltaReconciliation
                    this[AssetAggregate.deltaTrades] = agg.amountDeltaTrades
                    this[AssetAggregate.deltaOther] = agg.amountDeltaOther
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
     * Checks all assets in the database and regenerates aggregations for any
     * whose [Asset.aggregatedThrough] is behind today's date or has never been computed.
     * Assets with no transactions are skipped.
     */
    suspend fun ensureAllAggregationsUpToDate() {
        val today = Clock.System.now().toLocalDateTime(TimeZone.Companion.currentSystemDefault()).date
        val staleAssets = transaction {
            Asset.selectAll()
                .where { (Asset.aggregatedThrough less today) or (Asset.aggregatedThrough.isNull()) }
                .map { it[Asset.id] }
        }
        if (staleAssets.isEmpty()) {
            logger.debug { "All asset aggregations are up-to-date through $today" }
            return
        }
        logger.info { "Found ${staleAssets.size} assets needing aggregation refresh through $today" }
        for (assetId in staleAssets) {
            try {
                regenerateAggregatesForAsset(assetId)
            } catch (e: Exception) {
                logger.error(e) { "Failed to refresh aggregations for asset $assetId" }
            }
        }
    }
}