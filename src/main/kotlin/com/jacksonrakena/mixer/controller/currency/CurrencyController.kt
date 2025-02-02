package com.jacksonrakena.mixer.controller.currency

import com.jacksonrakena.mixer.cache.RateCache
import com.jacksonrakena.mixer.upstream.CurrencyResponse
import com.jacksonrakena.mixer.upstream.CurrencyService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.ZoneOffset
import java.util.logging.Logger

@RestController
@RequestMapping("/currency")
class CurrencyController(val currencyService: CurrencyService, val rateCache: RateCache) {
    companion object {
        val logger = Logger.getLogger(CurrencyController::class.java.name)
    }

    @Operation(
        summary = "Get FX rate",
        description = "Gets the latest available FX rate for a currency pair.",
    )
    @GetMapping("/{base}/{target}")
    fun getExchangeRate(
        @PathVariable @Parameter(description = "The base currency code.") base: String,
        @PathVariable @Parameter(description = "The currency to compare against.") target: String): CurrencyResponse {
        return rateCache.findRateOnDay(Pair(base, target), Instant.now())
    }

    @Operation(
        summary = "Get FX rate for a specific date",
        description = "Gets the historic FX rate for a currency pair on a specific date.",
    )
    @GetMapping("/{base}/{target}/{date}")
    fun getExchangeRateAtDate(
        @PathVariable @Parameter(description = "The base currency code.") base: String,
        @PathVariable @Parameter(description = "The currency to compare against.") target: String,
        @PathVariable @Parameter(description = "The day of the requested FX rate.") date: Instant
    ): CurrencyResponse {
        return rateCache.findRateOnDay(Pair(base, target), date)
    }

    @Operation(
        summary = "Multi-currency historic rate request",
        description = "Compose an advanced query for multiple currency pairs over a range of dates.",
    )
    @ApiResponses(
        *[
        ApiResponse(
                responseCode = "200",
                description = "Successful query",
                content = [Content(
                        mediaType = "application/json",
                        examples = [ExampleObject(
                            """
                                {
                                  "rates": {
                                    "2011-09-03T21:00:00Z": {
                                      "USD": {
                                        "NZD": 1.1840905592459712
                                      },
                                      "NZD": {
                                        "USD": 0.84453,
                                        "HKD": 6.57779
                                      },
                                      "HKD": {
                                        "NZD": 0.15202674454490034
                                      }
                                    }
                                  }
                                }
                            """
                        )]
                )]
        ),
    ])
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
                response[date]!![pair.target]!![pair.base] = 1.0/rate
            }

        }
        return QueryExchangeRatesResponse(
            rates = response
        )
    }
}