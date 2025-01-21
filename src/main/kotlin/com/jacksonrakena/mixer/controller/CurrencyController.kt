package com.jacksonrakena.mixer.controller

import com.jacksonrakena.mixer.cache.RateCache
import com.jacksonrakena.mixer.upstream.CurrencyRangeResponse
import com.jacksonrakena.mixer.upstream.CurrencyResponse
import com.jacksonrakena.mixer.upstream.CurrencyService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.ZoneOffset
import java.util.logging.Logger

@RestController
@RequestMapping("/currency")
class CurrencyController(val currencyService: CurrencyService, val rateCache: RateCache) {
    companion object {
        val logger = Logger.getLogger(CurrencyController::class.java.name)
    }

    @GetMapping("/{base}/{target}")
    fun getExchangeRate(@PathVariable base: String, @PathVariable target: String): CurrencyResponse {
        return rateCache.findRateOnDay(Pair(base, target), Instant.now())
    }

    @GetMapping("/{base}/{target}/{date}")
    fun getExchangeRateAtDate(
        @PathVariable base: String,
        @PathVariable target: String,
        @PathVariable date: Instant): CurrencyResponse {
        return rateCache.findRateOnDay(Pair(base, target), date)
    }

    @PostMapping("/query")
    fun queryExchangeRates(@RequestBody request: QueryExchangeRatesRequest): QueryExchangeRatesResponse {
        val response = mutableMapOf<Instant, MutableMap<String, MutableMap<String, Double>>>()
        val end = request.endDate ?: Instant.now()
        val start = request.startDate ?: Instant.now().atZone(ZoneOffset.UTC).minusDays(4900).toInstant()

        for (pair in request.instruments) {
            val query = rateCache.queryRatesOverTime(
                Pair(pair.base, pair.target),
                start,
                end
            )

            for ((date, rate) in query) {
                response[date] = response[date] ?: mutableMapOf()

                response[date]!![pair.base] = response[date]!![pair.base] ?: mutableMapOf()
                response[date]!![pair.base]!![pair.target] = rate

                response[date]!![pair.target] = response[date]!![pair.target] ?: mutableMapOf()
                response[date]!![pair.target]!![pair.base] = rate
            }

        }
        return QueryExchangeRatesResponse(
            rates = response
        )
    }
}

data class ExchangeRateInstrument(val base: String, val target: String)
data class QueryExchangeRatesRequest(
    val instruments: List<ExchangeRateInstrument>,
    val startDate: Instant?,
    val endDate: Instant?
)

data class QueryExchangeRatesResponse(
    val rates: Map<Instant, Map<String, Map<String, Double>>>
)