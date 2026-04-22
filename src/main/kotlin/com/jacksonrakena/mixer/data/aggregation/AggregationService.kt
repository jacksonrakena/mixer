package com.jacksonrakena.mixer.data.aggregation

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
     * @param startOverride if provided, starts aggregation from this date instead of the earliest transaction.
     * @param initialHolding starting holding amount (for partial reaggregation from a known state).
     * @param initialPrice starting unit price (for partial reaggregation).
     * @param initialPriceDate the date of the starting unit price (for partial reaggregation).
     * @param initialCostBasis starting cost basis (for partial reaggregation from a known state).
     */
    suspend fun forwardAggregate(
        asset: Uuid,
        timezone: TimeZone,
        ats: AssetTransactionSource,
        end: LocalDate,
        marketPrices: Map<LocalDate, Double>? = null,
        startOverride: LocalDate? = null,
        initialHolding: Double = 0.0,
        initialPrice: Double? = null,
        initialPriceDate: LocalDate? = null,
        initialCostBasis: Double = 0.0,
    ): Collection<AssetTransactionAggregation> {
        val startDate: LocalDate
        val transactions: List<AssetTransaction>

        if (startOverride != null) {
            startDate = startOverride
            val startInstant = startOverride.atTime(0, 0).toInstant(timezone)
            transactions = ats.getTransactions(asset, startInstant).toList()
        } else {
            val earliest = ats.getEarliestTransaction(asset)?.timestamp ?: return emptyList()
            startDate = earliest.toLocalDateTime(timezone).date
            transactions = ats.getTransactions(asset).toList()
        }

        val transactionsByDate = transactions.groupBy { it.timestamp.toLocalDateTime(timezone).date }
        val dailyAggregations = mutableListOf<AssetTransactionAggregation>()
        var currentHolding = initialHolding
        var costBasis = initialCostBasis

        val dayCount = end.toEpochDays() - startDate.toEpochDays() + 1
        var lastKnownPrice: Double? = initialPrice
        var lastKnownPriceDate: LocalDate? = initialPriceDate

        for (day in startDate..end) {
            val transactionsForDay = transactionsByDate[day]?.sortedBy { it.timestamp } ?: emptyList()

            var amountDeltaReconciliation = 0.0
            var amountDeltaTrades = 0.0
            var cashFlowNative = 0.0
            for (transaction in transactionsForDay) {
                val txAmount = transaction.amount ?: 0.0
                val txValue = transaction.value

                when (transaction.type) {
                    AssetTransactionType.Trade -> {
                        // Update cost basis BEFORE holding (need pre-sell holding for proportional reduction)
                        if (txAmount > 0 && txValue != null) {
                            costBasis += txValue
                            cashFlowNative += txValue
                        } else if (txAmount < 0 && currentHolding > 0) {
                            val sellRatio = (kotlin.math.abs(txAmount) / currentHolding).coerceAtMost(1.0)
                            costBasis *= (1.0 - sellRatio)
                            if (txValue != null) {
                                cashFlowNative -= txValue
                            }
                        }
                        currentHolding += txAmount
                        amountDeltaTrades += txAmount
                    }
                    AssetTransactionType.Reconciliation -> {
                        // If reconciliation reduces holding, proportionally reduce cost basis
                        if (currentHolding > 0 && txAmount < currentHolding) {
                            costBasis *= (txAmount / currentHolding).coerceAtLeast(0.0)
                        }
                        amountDeltaTrades = 0.0
                        currentHolding = txAmount
                        amountDeltaReconciliation += txAmount
                    }
                }

                // Derive per-unit price from the transaction's value/amount
                if (txAmount != 0.0 && txValue != null) {
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

            val nativeValue = if (unitPrice != null) currentHolding * unitPrice else 0.0

            dailyAggregations.add(
                AssetTransactionAggregation(
                    assetId = asset,
                    date = day.toString(),
                    amount = currentHolding,
                    nativeValue = nativeValue,
                    amountDeltaReconciliation = amountDeltaReconciliation,
                    amountDeltaTrades = amountDeltaTrades,
                    unitPrice = unitPrice,
                    valueDate = valueDate,
                    costBasis = costBasis,
                    cashFlowNative = cashFlowNative,
                )
            )
        }

        return dailyAggregations
    }
}