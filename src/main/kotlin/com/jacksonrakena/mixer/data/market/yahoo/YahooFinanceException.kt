package com.jacksonrakena.mixer.data.market.yahoo

/**
 * Base exception for Yahoo Finance errors.
 */
open class YahooFinanceException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Thrown when Yahoo Finance returns HTTP 429 (Too Many Requests).
 */
class YahooFinanceRateLimitException : YahooFinanceException("Too Many Requests. Rate limited. Try after a while.")

/**
 * Thrown when no price data is returned for a ticker.
 */
class YahooFinancePricesMissingException(
    val ticker: String,
    debugInfo: String = "",
) : YahooFinanceException(
    if (debugInfo.isNotBlank()) "$ticker: no price data found $debugInfo"
    else "$ticker: no price data found"
)

/**
 * Thrown when a ticker appears to be delisted or invalid.
 */
class YahooFinanceTickerMissingException(
    val ticker: String,
    val rationale: String,
) : YahooFinanceException("$ticker: possibly delisted; $rationale")
