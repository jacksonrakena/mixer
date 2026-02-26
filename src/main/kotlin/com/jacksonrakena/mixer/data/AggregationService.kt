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

    /**
     * @param marketPrices optional map of date → per-unit close price for market-data assets.
     *                     When non-null, nativeValue = holding × price (with carry-forward for missing dates).
     *                     When null (USER mode), unit price is derived from transaction value/amount
     *                     and carried forward for days without transactions.
     */
    suspend fun forwardAggregate(
        asset: Uuid,
        timezone: TimeZone,
        ats: AssetTransactionSource,
        end: LocalDate,
        marketPrices: Map<LocalDate, Double>? = null,
    ): Collection<AssetTransactionAggregation> {
        val start = ats.getEarliestTransaction(asset)?.timestamp ?: return emptyList()
        val transactions = ats.getTransactions(asset, start)
        val dailyAggregations = mutableListOf<AssetTransactionAggregation>()
        var currentHolding = 0.0

        val startDate = start.toLocalDateTime(timezone).date

        val dateSpan = startDate..end
        logger.info { "Opening aggregation window from $startDate to $end, total ${dateSpan.count()} days" }

        var lastKnownPrice: Double? = null
        var lastKnownPriceDate: LocalDate? = null

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

                // Derive per-unit price from the transaction's value/amount
                val txAmount = transaction.amount
                val txValue = transaction.value
                if (txAmount != null && txAmount != 0.0 && txValue != null) {
                    lastKnownPrice = txValue / kotlin.math.abs(txAmount)
                    lastKnownPriceDate = day
                }
            }

            // Resolve per-unit price: market data source takes priority, then transaction-derived
            val unitPrice: Double?
            val valueDate: LocalDate?
            if (marketPrices != null) {
                val marketPrice = marketPrices[day] ?: lastKnownPrice
                if (marketPrice != null && marketPrices.containsKey(day)) {
                    lastKnownPrice = marketPrice
                    lastKnownPriceDate = day
                }
                unitPrice = marketPrice
                valueDate = if (marketPrices.containsKey(day)) day else lastKnownPriceDate
            } else {
                unitPrice = lastKnownPrice
                valueDate = lastKnownPriceDate
            }

            val nativeValue = if (unitPrice != null) currentHolding * unitPrice else currentHolding

            dailyAggregations.add(
                AssetTransactionAggregation(
                    assetId = asset,
                    date = day.atTime(23, 59, 0).toInstant(timezone),
                    amount = currentHolding,
                    nativeValue = nativeValue,
                    amountDeltaReconciliation = amountDeltaReconciliation,
                    amountDeltaTrades = amountDeltaTrades,
                    unitPrice = unitPrice,
                    valueDate = valueDate,
                )
            )
        }

        return dailyAggregations
    }
}