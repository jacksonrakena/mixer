package com.jacksonrakena.mixer.data

import com.jacksonrakena.mixer.data.tables.markets.ExchangeRate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component

/**
 * Helper for querying exchange rates directly from the database,
 * with fallback logic for missing dates.
 *
 * When an exact date is not available, it searches backwards up to 5 days,
 * then forwards up to 5 days, for the nearest available record.
 * Returns null if no rate can be found within that window.
 */
@Component
class ExchangeRateHelper {

    companion object {
        /** Maximum number of days to search in each direction when an exact rate is missing. */
        const val FALLBACK_WINDOW_DAYS = 5
    }

    /**
     * Look up the exchange rate for a currency pair on a specific date.
     * If no exact match, searches ±[FALLBACK_WINDOW_DAYS] days for the nearest record
     * (backwards first, then forwards).
     *
     * If base == counter, returns a rate of 1.0 (identity conversion).
     *
     * @return the rate lookup result, or null if no rate is available within the search window.
     */
    fun findRate(base: String, counter: String, date: LocalDate): ExchangeRateLookup? {
        if (base == counter) {
            return ExchangeRateLookup(
                rate = 1.0,
                base = base,
                counter = counter,
                requestedDate = date,
                actualDate = date,
            )
        }

        // Try exact date first
        val exact = queryRate(base, counter, date)
        if (exact != null) return exact

        // Search backwards then forwards
        for (offset in 1..FALLBACK_WINDOW_DAYS) {
            val backward = queryRate(base, counter, date.minus(offset, DateTimeUnit.DAY))
            if (backward != null) return backward

            val forward = queryRate(base, counter, date.plus(offset, DateTimeUnit.DAY))
            if (forward != null) return forward
        }

        // Try the inverse pair (e.g. if we have USD/AUD but need AUD/USD)
        val inverse = findInverseRate(base, counter, date)
        if (inverse != null) return inverse

        return null
    }

    /**
     * Look up exchange rates for a currency pair across a date range.
     * For each date in the range, applies the same fallback logic as [findRate].
     *
     * @return a map from each date in the range to its lookup result (dates with no available rate are omitted).
     */
    fun findRatesInRange(
        base: String,
        counter: String,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Map<LocalDate, ExchangeRateLookup> {
        if (base == counter) {
            val result = mutableMapOf<LocalDate, ExchangeRateLookup>()
            var cursor = startDate
            while (cursor <= endDate) {
                result[cursor] = ExchangeRateLookup(
                    rate = 1.0,
                    base = base,
                    counter = counter,
                    requestedDate = cursor,
                    actualDate = cursor,
                )
                cursor = cursor.plus(1, DateTimeUnit.DAY)
            }
            return result
        }

        // Bulk-load all rates in an expanded window to minimize DB queries
        val expandedStart = startDate.minus(FALLBACK_WINDOW_DAYS, DateTimeUnit.DAY)
        val expandedEnd = endDate.plus(FALLBACK_WINDOW_DAYS, DateTimeUnit.DAY)

        val directRates = bulkQueryRates(base, counter, expandedStart, expandedEnd)
        val inverseRates = bulkQueryRates(counter, base, expandedStart, expandedEnd)

        val result = mutableMapOf<LocalDate, ExchangeRateLookup>()
        var cursor = startDate
        while (cursor <= endDate) {
            val lookup = resolveFromBulk(base, counter, cursor, directRates, inverseRates)
            if (lookup != null) {
                result[cursor] = lookup
            }
            cursor = cursor.plus(1, DateTimeUnit.DAY)
        }
        return result
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun queryRate(base: String, counter: String, date: LocalDate): ExchangeRateLookup? {
        return transaction {
            ExchangeRate
                .selectAll()
                .where {
                    (ExchangeRate.base eq base) and
                            (ExchangeRate.counter eq counter) and
                            (ExchangeRate.referenceDate eq date)
                }
                .firstOrNull()
                ?.let {
                    ExchangeRateLookup(
                        rate = it[ExchangeRate.rate],
                        base = base,
                        counter = counter,
                        requestedDate = date,
                        actualDate = it[ExchangeRate.referenceDate],
                    )
                }
        }
    }

    private fun findInverseRate(base: String, counter: String, date: LocalDate): ExchangeRateLookup? {
        // Search exact, then backwards, then forwards for the inverse pair
        for (offset in 0..FALLBACK_WINDOW_DAYS) {
            if (offset == 0) {
                val inv = queryRate(counter, base, date)
                if (inv != null) {
                    return ExchangeRateLookup(
                        rate = 1.0 / inv.rate,
                        base = base,
                        counter = counter,
                        requestedDate = date,
                        actualDate = inv.actualDate,
                    )
                }
            } else {
                val backward = queryRate(counter, base, date.minus(offset, DateTimeUnit.DAY))
                if (backward != null) {
                    return ExchangeRateLookup(
                        rate = 1.0 / backward.rate,
                        base = base,
                        counter = counter,
                        requestedDate = date,
                        actualDate = backward.actualDate,
                    )
                }
                val forward = queryRate(counter, base, date.plus(offset, DateTimeUnit.DAY))
                if (forward != null) {
                    return ExchangeRateLookup(
                        rate = 1.0 / forward.rate,
                        base = base,
                        counter = counter,
                        requestedDate = date,
                        actualDate = forward.actualDate,
                    )
                }
            }
        }
        return null
    }

    private fun bulkQueryRates(
        base: String,
        counter: String,
        start: LocalDate,
        end: LocalDate,
    ): Map<LocalDate, Double> {
        return transaction {
            ExchangeRate
                .selectAll()
                .where {
                    (ExchangeRate.base eq base) and
                            (ExchangeRate.counter eq counter) and
                            (ExchangeRate.referenceDate greaterEq start) and
                            (ExchangeRate.referenceDate lessEq end)
                }
                .orderBy(ExchangeRate.referenceDate, SortOrder.ASC)
                .associate { it[ExchangeRate.referenceDate] to it[ExchangeRate.rate] }
        }
    }

    private fun resolveFromBulk(
        base: String,
        counter: String,
        date: LocalDate,
        directRates: Map<LocalDate, Double>,
        inverseRates: Map<LocalDate, Double>,
    ): ExchangeRateLookup? {
        // Try exact direct
        directRates[date]?.let {
            return ExchangeRateLookup(it, base, counter, date, date)
        }

        // Search ±FALLBACK_WINDOW_DAYS in direct rates (backwards first)
        for (offset in 1..FALLBACK_WINDOW_DAYS) {
            val backward = date.minus(offset, DateTimeUnit.DAY)
            directRates[backward]?.let {
                return ExchangeRateLookup(it, base, counter, date, backward)
            }
            val forward = date.plus(offset, DateTimeUnit.DAY)
            directRates[forward]?.let {
                return ExchangeRateLookup(it, base, counter, date, forward)
            }
        }

        // Try inverse rates with same fallback logic
        inverseRates[date]?.let {
            return ExchangeRateLookup(1.0 / it, base, counter, date, date)
        }
        for (offset in 1..FALLBACK_WINDOW_DAYS) {
            val backward = date.minus(offset, DateTimeUnit.DAY)
            inverseRates[backward]?.let {
                return ExchangeRateLookup(1.0 / it, base, counter, date, backward)
            }
            val forward = date.plus(offset, DateTimeUnit.DAY)
            inverseRates[forward]?.let {
                return ExchangeRateLookup(1.0 / it, base, counter, date, forward)
            }
        }

        return null
    }
}
