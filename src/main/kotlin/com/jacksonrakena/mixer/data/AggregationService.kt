package com.jacksonrakena.mixer.data

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.springframework.stereotype.Component
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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