package com.jacksonrakena.mixer.data.market.yahoo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Kotlinx Serialization models for the Yahoo Finance v8 chart API response.
 *
 * Example URL: https://query2.finance.yahoo.com/v8/finance/chart/AAPL?period1=...&period2=...&interval=1d
 *
 * Translated from the JSON parsing logic in yfinance's utils.parse_quotes() and scrapers/history.py.
 */
@Serializable
data class YahooChartEnvelope(
    val chart: YahooChart,
)

@Serializable
data class YahooChart(
    val result: List<YahooChartResult>? = null,
    val error: YahooChartError? = null,
)

@Serializable
data class YahooChartError(
    val code: String? = null,
    val description: String? = null,
)

@Serializable
data class YahooChartResult(
    val meta: YahooChartMeta,
    val timestamp: List<Long>? = null,
    val indicators: YahooChartIndicators,
)

@Serializable
data class YahooChartMeta(
    val currency: String? = null,
    val symbol: String? = null,
    val exchangeTimezoneName: String? = null,
    val instrumentType: String? = null,
    val regularMarketPrice: Double? = null,
    val previousClose: Double? = null,
    val validRanges: List<String>? = null,
    @SerialName("dataGranularity")
    val dataGranularity: String? = null,
    // Capture any other metadata fields
)

@Serializable
data class YahooChartIndicators(
    val quote: List<YahooQuoteData>,
    val adjclose: List<YahooAdjCloseData>? = null,
)

@Serializable
data class YahooQuoteData(
    val open: List<Double?>? = null,
    val high: List<Double?>? = null,
    val low: List<Double?>? = null,
    val close: List<Double?>? = null,
    val volume: List<Long?>? = null,
)

@Serializable
data class YahooAdjCloseData(
    val adjclose: List<Double?>? = null,
)
