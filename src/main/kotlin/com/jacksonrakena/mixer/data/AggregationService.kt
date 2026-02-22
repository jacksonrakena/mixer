package com.jacksonrakena.mixer.data

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.util.logging.Logger
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

interface AssetValueSource {
    suspend fun getAssetValue(asset: Uuid, at: Instant): Double?
}

data class AggregationOutput(
    val asset: Uuid,
    val aggregationPeriod: AggregationPeriod,
    val periodEndDate: Instant,
    val totalValue: Double
)

data class Asset2(
    val id: Uuid,
    val name: String
)

enum class AggregationPeriod(val lookbackPeriodDays: Int) {
    DAILY(1),
}

enum class AssetTransactionType {
    // Buy / transfer in / get
    Trade,

    // Anchor
    Reconciliation,
}

@Serializable
data class AssetTransaction(
    val assetId: Uuid,
    val timestamp: Instant,
    val type: AssetTransactionType,

    val amount: Double? = null,
    val value: Double? = null,
)

@Serializable
data class AssetTransactionAggregation(
    val assetId: Uuid,
    val date: Instant,
    val holding: Double
)

interface AssetTransactionSource {
    suspend fun getLatestReconciliation(
        asset: Uuid,
        before: Instant = Clock.System.now()
    ): AssetTransaction?

    suspend fun getTransactions(
        asset: Uuid,
        after: Instant? = null,
    ) : Iterable<AssetTransaction>

    suspend fun getEarliestTransaction(
        asset: Uuid,
        after: Instant? = null,
    ) : AssetTransaction?
}

@Component
class UserAggregationManager(
    val database: Database,
    val aggregationService: AggregationService
) {
    companion object {
        val logger = Logger.getLogger(UserAggregationManager::class.java.name)
    }
    suspend fun ensureUserAggregated(
        userId: Uuid
    ) {
    }

    suspend fun forceAggregateUserAssets(
        userId: Uuid
    ) {
        val existingAssets = transaction {
            Asset.selectAll().where { Asset.ownerId.eq(userId) }.toList()
        }
        logger.info("Forcing aggregation for user $userId, total ${existingAssets.size} assets")

        for (asset in existingAssets) {
            regenerateAggregatesForAsset(asset[Asset.id])
        }
    }

    class DatabaseAssetTransactionSource: AssetTransactionSource {
        override suspend fun getLatestReconciliation(asset: Uuid, before: Instant): AssetTransaction? {
            return transaction {
                val result = Transaction.selectAll()
                    .where { (Transaction.assetId eq asset) and
                            (Transaction.type eq AssetTransactionType.Reconciliation) and
                            (Transaction.timestamp less before.toEpochMilliseconds()) }
                    .orderBy(Transaction.timestamp, SortOrder.DESC)
                    .limit(1)
                    .firstOrNull()

                if (result != null) {
                    return@transaction AssetTransaction(
                        assetId = result[Transaction.assetId],
                        timestamp = Instant.fromEpochMilliseconds(result[Transaction.timestamp]),
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
                        timestamp = Instant.fromEpochMilliseconds(it[Transaction.timestamp]),
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
                        timestamp = Instant.fromEpochMilliseconds(result[Transaction.timestamp]),
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
        val aggregates = aggregationService.forwardAggregate(
            assetId,
            TimeZone.currentSystemDefault(),
            DatabaseAssetTransactionSource(),
            Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        )
        transaction {
            AssetAggregate.batchInsert(aggregates) { agg ->
                this[AssetAggregate.assetId] = assetId
                this[AssetAggregate.aggregationPeriod] = AggregationPeriod.DAILY
                this[AssetAggregate.periodEndDate] = agg.date.toLocalDateTime(TimeZone.currentSystemDefault()).date
                this[AssetAggregate.totalValue] = agg.holding
            }
        }
        logger.info("Regenerated aggregates for asset $assetId, total ${aggregates.size} entries")
    }
}

@Component
@OptIn(ExperimentalUuidApi::class)
class AggregationService {
    companion object {
        val logger = Logger.getLogger(AggregationService::class.java.name)
    }

    suspend fun forwardAggregate(
        asset: Uuid,
        timezone: TimeZone,
        ats: AssetTransactionSource,
        end: LocalDate,
    ): Collection<AssetTransactionAggregation> {
        val start = ats.getEarliestTransaction(asset)?.timestamp ?: return emptyList()
        logger.info("First transaction ${start.toLocalDateTime(timezone)}")
        val transactions = ats.getTransactions(asset, start)
        logger.info("${transactions}")
        val dailyAggregations = mutableListOf<AssetTransactionAggregation>()
        var currentHolding = 0.0

        val startDate = start.toLocalDateTime(timezone).date

        val dateSpan = startDate..end// (end - startDate).days
        logger.info("Opening aggregation window from $startDate to $end, total ${dateSpan.count()} days")
        for (day in dateSpan) {
            val transactionsForDay = transactions.filter { it.timestamp.toLocalDateTime(timezone).date == day }.sortedBy { it.timestamp }

            logger.info("== Processing day ${day} ==")
            logger.info("Transactions: ${transactionsForDay}")
            for (transaction in transactionsForDay) {
                logger.info("Processing ${transaction}")
                if (transaction.type == AssetTransactionType.Reconciliation) {
                    currentHolding = transaction.amount ?: 0.0
                    logger.info("SET ${currentHolding}")
                }
                else {
                    currentHolding += transaction.amount ?: 0.0
                    logger.info("CHANGE ${transaction.amount ?: 0.0}")
                }

            }
            logger.info("== End processing ${day}: ${currentHolding} ==")
            dailyAggregations.add(AssetTransactionAggregation(asset, day.atTime(23, 59, 0).toInstant(timezone), currentHolding))
        }

        return dailyAggregations
    }

    suspend fun openCachedExchangeRateWindow(
        base: String,
        counter: String,
        start: LocalDate,
        end: LocalDate
    ): List<Pair<Instant, Double>> {
        val rates = transaction {
            ExchangeRate
                .selectAll()
                .where {
                    (ExchangeRate.base eq base) and
                            (ExchangeRate.counter eq counter) and
                            (ExchangeRate.referenceDate greater start) and
                            (ExchangeRate.referenceDate less end)
                }
                .orderBy(ExchangeRate.referenceDate, SortOrder.ASC)
                .map {
                    Pair(
                        Instant.fromEpochMilliseconds(
                            it[ExchangeRate.referenceDate].atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
                        ), it[ExchangeRate.rate]
                    )
                }
        }
        return rates
    }
}