package com.jacksonrakena.mixer.controller

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.jacksonrakena.mixer.cache.RateCache
import com.jacksonrakena.mixer.upstream.CurrencyResponse
import com.jacksonrakena.mixer.upstream.CurrencyResponseMeta
import com.jacksonrakena.mixer.upstream.CurrencyService
import com.jacksonrakena.mixer.upstream.oanda.OandaCurrencyService
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.*
import java.util.logging.Logger

@RestController
@RequestMapping("/currency")
class CurrencyController(val currencyService: CurrencyService, val rateCache: RateCache) {
    companion object {
        val logger = Logger.getLogger(CurrencyController::class.java.name)
    }

    @GetMapping("/{base}/{target}")
    fun getExchangeRate(@PathVariable base: String, @PathVariable target: String): CurrencyResponse {
        val pair = Pair(base, target)
        val cachedRate = rateCache.rateCache[pair]
        if (cachedRate != null) {
            logger.info {
                "Returning cached rate for $pair: ${cachedRate.rate}"
            }
            return cachedRate
        }
        return CurrencyResponse(
            meta = CurrencyResponseMeta(
                generatedAt = Instant.now(),
                generatedBy = "error"
            ),
            rate = null
        )
    }

    @PostMapping("/bulk")
    fun getBulkExchangeRates(@RequestBody request: BulkExchangeRateRequest): BulkExchangeRateResponse {
        val response = mutableMapOf<String, MutableMap<String, Double>>()
        for (rate in request.rates) {
             val cachedRate = rateCache.rateCache[Pair(rate.base, rate.target)]
             if (cachedRate != null) {
                 response[rate.base] = (response[rate.base] ?: mutableMapOf())
                 response[rate.base]!![rate.target] = cachedRate.rate!!
             }
        }
        return BulkExchangeRateResponse(
            rates = response
        )
    }
}

data class ExchangeRateInstrument(val base: String, val target: String)
data class BulkExchangeRateRequest(
    val rates: List<ExchangeRateInstrument>
)

data class BulkExchangeRateResponse(
    val rates: Map<String, Map<String, Double>>
)