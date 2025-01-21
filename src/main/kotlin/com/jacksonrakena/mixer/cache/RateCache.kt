package com.jacksonrakena.mixer.cache

import com.jacksonrakena.mixer.upstream.CurrencyResponse
import com.jacksonrakena.mixer.upstream.CurrencyResponseMeta
import com.jacksonrakena.mixer.upstream.CurrencyService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

@Component
class RateCache(val currencyService: CurrencyService) {
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

        fun generateFxPairs(): List<Pair<String, String>> {
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

    var rateCache = mutableMapOf<Pair<String, String>, CurrencyResponse>()

    @Scheduled(fixedDelay = 60_000 * 60, initialDelay = 1_000)
    fun updateAllCachedRates() {
        logger.info("Updating all cached rates")

        for (pair in SUPPORTED_PAIRS) {
            try {
                val rate = currencyService.getExchangeRate(pair.first, pair.second)

                if (rate.rate == null) throw Error("Rate was null")

                rateCache[pair] = rate.copy(
                    meta = rate.meta.copy(
                        generatedBy = "cached-" + rate.meta.generatedBy
                    )
                )

                rateCache[Pair(pair.second, pair.first)] = rate.copy(
                    meta =  rate.meta.copy(
                        generatedBy = "cached-" + rate.meta.generatedBy
                    ),
                    rate = 1.0/rate.rate
                )
                logger.info {
                    "$pair: ${rate.rate}, ${Pair(pair.second, pair.first)}: ${1.0/rate.rate}"
                }
            } catch (e: Error) {
                logger.log(Level.SEVERE, e) {
                    "failed to fetch currency pair ${pair.first}/${pair.second}"
                }
            }
        }
    }
}