package com.jacksonrakena.mixer.data

import com.jacksonrakena.mixer.data.tables.concrete.Asset
import com.jacksonrakena.mixer.data.tables.concrete.Transaction
import com.jacksonrakena.mixer.data.tables.virtual.AssetAggregate
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow
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
import org.slf4j.MDC
import org.springframework.stereotype.Component
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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

/**
 * Metadata about the exchange rate used for FX conversion on a given aggregation point.
 */
@Serializable
data class FxConversionInfo(
    /** The exchange rate applied (asset currency -> display currency). */
    val rate: Double,
    /** The base currency (asset's native currency). */
    val fromCurrency: String,
    /** The target currency (user's display currency). */
    val toCurrency: String,
    /** The date of the exchange rate record actually used (may differ from the aggregation date due to fallback). */
    val rateDate: kotlinx.datetime.LocalDate,
)

@Serializable
data class AssetTransactionAggregation(
    val assetId: Uuid,
    val date: Instant,

    /** Holding amount in native asset units. */
    val amount: Double,
    val amountDeltaTrades: Double = 0.0,
    val amountDeltaReconciliation: Double = 0.0,
    val amountDeltaOther: Double = 0.0,

    /** Value in the asset's native currency. */
    val nativeValue: Double = 0.0,

    /** Value converted to the user's display currency, or null if no FX rate was available. */
    val displayValue: Double? = null,

    /** The currency code of the asset's native currency. */
    val nativeCurrency: String? = null,

    /** The user's display currency code. */
    val displayCurrency: String? = null,

    /** FX conversion details, or null if no conversion was needed or no rate was available. */
    val fxConversion: FxConversionInfo? = null,
) {
    companion object {
        fun fromResultRow(row: ResultRow): AssetTransactionAggregation {
            return AssetTransactionAggregation(
                assetId = row[AssetAggregate.assetId],
                date = row[AssetAggregate.periodEndDate].atStartOfDayIn(TimeZone.currentSystemDefault()).toJavaInstant().toKotlinInstant(),
                amount = row[AssetAggregate.totalValue],
                amountDeltaTrades = row[AssetAggregate.deltaTrades],
                amountDeltaReconciliation = row[AssetAggregate.deltaReconciliation],
                amountDeltaOther = row[AssetAggregate.deltaOther],
                nativeValue = row[AssetAggregate.totalValue],
            )
        }
    }
}

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
        MDC.put("assetId", assetId.toString())
        try {
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
                    this[AssetAggregate.totalValue] = agg.amount
                    this[AssetAggregate.deltaReconciliation] = agg.amountDeltaReconciliation
                    this[AssetAggregate.deltaTrades] = agg.amountDeltaTrades
                    this[AssetAggregate.deltaOther] = agg.amountDeltaOther
                }
            }
            logger.info { "Regenerated aggregates for asset $assetId, total ${aggregates.size} entries" }
        } finally {
            MDC.remove("assetId")
        }
    }
}

@Component
@OptIn(ExperimentalUuidApi::class)
class AggregationService {
    companion object {
        val logger = KotlinLogging.logger {}
    }

    suspend fun forwardAggregate(
        asset: Uuid,
        timezone: TimeZone,
        ats: AssetTransactionSource,
        end: LocalDate,
    ): Collection<AssetTransactionAggregation> {
        val start = ats.getEarliestTransaction(asset)?.timestamp ?: return emptyList()
        val transactions = ats.getTransactions(asset, start)
        val dailyAggregations = mutableListOf<AssetTransactionAggregation>()
        var currentHolding = 0.0

        val startDate = start.toLocalDateTime(timezone).date

        val dateSpan = startDate..end
        logger.info { "Opening aggregation window from $startDate to $end, total ${dateSpan.count()} days" }
        for (day in dateSpan) {
            val transactionsForDay = transactions.filter { it.timestamp.toLocalDateTime(timezone).date == day }.sortedBy { it.timestamp }

            var amountDeltaReconciliation = 0.0
            var amountDeltaTrades = 0.0
            for (transaction in transactionsForDay) {
                when (transaction.type) {
                    AssetTransactionType.Trade -> {
                        currentHolding += transaction.amount ?: 0.0
                        amountDeltaTrades += transaction.amount ?: 0.0
                    }
                    AssetTransactionType.Reconciliation -> {
                        amountDeltaTrades = 0.0
                        currentHolding = transaction.amount ?: 0.0
                        amountDeltaReconciliation += transaction.amount ?: 0.0
                    }
                }

            }
            dailyAggregations.add(
                AssetTransactionAggregation(
                    assetId = asset,
                    date = day.atTime(23, 59, 0).toInstant(timezone),
                    amount = currentHolding,
                    nativeValue = currentHolding,
                    amountDeltaReconciliation = amountDeltaReconciliation,
                    amountDeltaTrades = amountDeltaTrades,
                )
            )
        }

        return dailyAggregations
    }
}