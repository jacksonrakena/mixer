package com.jacksonrakena.mixer.data.market.yahoo

import com.jacksonrakena.mixer.data.market.MarketDataProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json

/**
 * MarketDataProvider implementation backed by Yahoo Finance's v8 chart API.
 *
 * Translated from yfinance's scrapers/history.py PriceHistory class.
 * Only implements the daily close price fetching flow needed by this application.
 *
 * Flow:
 *   1. Convert start/end dates to Unix timestamps
 *   2. Build query params: period1, period2, interval=1d, events=div,splits,capitalGains
 *   3. GET https://query2.finance.yahoo.com/v8/finance/chart/{TICKER}
 *   4. Parse JSON response, extract timestamps + close prices
 *   5. Convert timestamps to LocalDate, return Map<LocalDate, Double>
 */
class YahooFinanceProvider(
    private val client: YahooFinanceClient,
) : MarketDataProvider {
    private val logger = KotlinLogging.logger {}

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override fun getHistoricalPrices(
        ticker: String,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Map<LocalDate, Double> {
        val upperTicker = ticker.uppercase()
        logger.debug { "$upperTicker: Fetching historical prices $startDate -> $endDate" }

        // Convert dates to Unix timestamps (translated from utils._parse_user_dt + history())
        val period1 = startDate.atStartOfDayIn(TimeZone.UTC).epochSeconds
        val period2 = endDate.atStartOfDayIn(TimeZone.UTC).epochSeconds

        val params = mapOf(
            "period1" to period1.toString(),
            "period2" to period2.toString(),
            "interval" to "1d",
            "includePrePost" to "false",
            "events" to "div,splits,capitalGains",
        )

        val url = "${YahooFinanceClient.BASE_URL}/v8/finance/chart/$upperTicker"

        val responseBody = try {
            client.get(url, params)
        } catch (e: YahooFinanceRateLimitException) {
            throw e
        } catch (e: Exception) {
            throw YahooFinanceException("Failed to fetch chart data for $upperTicker", e)
        }

        if (responseBody.contains("Will be right back")) {
            throw YahooFinanceException("Yahoo Finance is currently down")
        }

        val envelope = try {
            json.decodeFromString<YahooChartEnvelope>(responseBody)
        } catch (e: Exception) {
            throw YahooFinanceException("Failed to parse Yahoo Finance response for $upperTicker", e)
        }

        // Validate response (translated from history.py error checking)
        val chart = envelope.chart
        if (chart.error != null) {
            throw YahooFinancePricesMissingException(
                upperTicker,
                chart.error.description ?: "unknown error"
            )
        }

        val result = chart.result
        if (result.isNullOrEmpty()) {
            throw YahooFinancePricesMissingException(upperTicker)
        }

        val chartResult = result[0]
        val timestamps = chartResult.timestamp
        val quoteData = chartResult.indicators.quote.firstOrNull()

        if (timestamps.isNullOrEmpty() || quoteData == null) {
            throw YahooFinancePricesMissingException(upperTicker)
        }

        // Use adjusted close if available, otherwise fall back to close
        // (translated from utils.parse_quotes)
        val adjCloseData = chartResult.indicators.adjclose?.firstOrNull()?.adjclose
        val closePrices = adjCloseData ?: quoteData.close

        if (closePrices.isNullOrEmpty()) {
            throw YahooFinancePricesMissingException(upperTicker)
        }

        // Build date → close price map (translated from utils.parse_quotes + set_df_tz)
        val exchangeTimezone = chartResult.meta.exchangeTimezoneName
        val tz = if (exchangeTimezone != null) {
            try {
                TimeZone.of(exchangeTimezone)
            } catch (e: Exception) {
                TimeZone.UTC
            }
        } else {
            TimeZone.UTC
        }

        val priceMap = mutableMapOf<LocalDate, Double>()
        for (i in timestamps.indices) {
            val closePrice = closePrices.getOrNull(i) ?: continue

            // Convert Unix timestamp to LocalDate in the exchange's timezone
            val instant = kotlinx.datetime.Instant.fromEpochSeconds(timestamps[i])
            val date = instant.toLocalDate(tz)

            priceMap[date] = closePrice
        }

        logger.debug { "$upperTicker: Received ${priceMap.size} daily prices" }
        return priceMap
    }

    @Suppress("DEPRECATION")
    private fun kotlinx.datetime.Instant.toLocalDate(tz: TimeZone): LocalDate {
        return this.toLocalDateTime(tz).date
    }

    override fun validateTicker(ticker: String): Boolean {
        val upperTicker = ticker.uppercase()
        logger.debug { "$upperTicker: Validating ticker" }

        val url = "${YahooFinanceClient.BASE_URL}/v8/finance/chart/$upperTicker"
        val params = mapOf(
            "range" to "1d",
            "interval" to "1d",
        )

        return try {
            val responseBody = client.get(url, params)
            val envelope = json.decodeFromString<YahooChartEnvelope>(responseBody)
            val chart = envelope.chart
            chart.error == null && !chart.result.isNullOrEmpty()
        } catch (e: Exception) {
            logger.debug(e) { "$upperTicker: Ticker validation failed" }
            false
        }
    }
}
