package com.jacksonrakena.mixer.cache

import com.jacksonrakena.mixer.core.requests.BackfillCurrencyPairRequest
import com.jacksonrakena.mixer.core.requests.InsertSeedDataRequest
import com.jacksonrakena.mixer.upstream.CurrencyRangeResponse
import com.jacksonrakena.mixer.upstream.CurrencyResponse
import com.jacksonrakena.mixer.upstream.CurrencyResponseMeta
import com.jacksonrakena.mixer.upstream.CurrencyService
import jakarta.annotation.PostConstruct
import org.jetbrains.exposed.v1.jdbc.Database
import org.jobrunr.scheduling.JobRequestScheduler
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.logging.Logger

@Component
class RateCache(val currencyService: CurrencyService, val scheduler: JobRequestScheduler,
                private val database: Database
) {
    companion object {
        val logger = Logger.getLogger(RateCache::class.java.name)

        private val SUPPORTED_CURRENCIES = listOf(
            "EUR",
            "GBP",
            "AUD",
            "NZD",
            "USD",
            "HKD"
        )

        private fun generateFxPairs(): List<Pair<String, String>> {
            return SUPPORTED_CURRENCIES.mapIndexed { index, currency ->
                if (index == SUPPORTED_CURRENCIES.lastIndex) return@mapIndexed listOf()
                return@mapIndexed SUPPORTED_CURRENCIES
                    .subList(index+1, SUPPORTED_CURRENCIES.lastIndex+1).map { target ->
                    return@map Pair(currency, target)
                }
            }.flatten()
        }
        val SUPPORTED_PAIRS = generateFxPairs()
    }

    private val rateCache = mutableMapOf<Pair<String, String>, CurrencyRangeResponse>()

    fun<T: Comparable<T>> Iterable<T>.findClosest(input: T) = fold(null) { acc: T?, num ->
        val closest = if (num <= input && (acc == null || num > acc)) num else acc
        if (closest == input) return@findClosest closest else return@fold closest
    }

    fun queryTemporalBucketRate(
        pair: Pair<String, String>,
        from: Instant,
        to: Instant
    ): Map<Instant, Double> {
        if (to < from) throw Error("Cannot search rates backwards from $from to $to")
        val ratesForPair = rateCache[pair] ?: return mapOf()
        val sortedRates = ratesForPair.rates.toSortedMap()
        val closestStart = sortedRates.keys.findClosest(from)
        val closestEnd = sortedRates.keys.findClosest(to)
        if (closestEnd == null || closestStart == null) return mapOf()
        if (closestEnd < closestStart) throw Error("Invalid time state, trying to index from $closestStart to $closestEnd")

        return sortedRates.subMap(closestStart, closestEnd)
    }

    fun findRateOnDay(pair: Pair<String, String>, day: Instant): CurrencyResponse {
        val ratesForPair = rateCache[pair] ?: return CurrencyResponse(
                meta = CurrencyResponseMeta(
                    generatedBy = "error",
                    generatedAt = Instant.now(),
                    dateOfRate = Instant.now()
                ),
                rate = null

            )
        val closestKey = ratesForPair.rates.toSortedMap().keys.findClosest(day)
        if (closestKey == null) {
            logger.info { "Couldn't find close key for $day" }
            return CurrencyResponse(
                meta = CurrencyResponseMeta(
                    generatedBy = "error",
                    generatedAt = Instant.now(),
                    dateOfRate = Instant.now()
                ),
                rate = null
            )
        }
        return CurrencyResponse(
            rate = ratesForPair.rates[closestKey],
            meta = CurrencyResponseMeta(
                generatedAt = ratesForPair.meta.generatedAt,
                generatedBy = ratesForPair.meta.generatedBy,
                dateOfRate = closestKey
            )
        )
    }

    fun insertSeedData() {

    }

//    @Scheduled(fixedDelay = 60_000 * 60, initialDelay = 1_000)
    @PostConstruct
    fun updateAllCachedRates() {
        logger.info("Updating all cached rates")

        for (pair in SUPPORTED_PAIRS) {
            val jobId = scheduler.enqueue(
                BackfillCurrencyPairRequest(
                    base = pair.first,
                    counter = pair.second,
                )
            )
            logger.info("Scheduled backfill job ${jobId} for ${pair.first}/${pair.second}")
        }
//    InsertSeedDataRequestHandler(database, currencyService).run(InsertSeedDataRequest())
        scheduler.enqueue(InsertSeedDataRequest())
    }
}