package com.jacksonrakena.mixer.data.market

import kotlinx.datetime.LocalDate

/**
 * Provides historical daily close prices for a given ticker symbol.
 */
interface MarketDataProvider {
    /**
     * Returns a map of date → close price for the given ticker over the requested range.
     * Dates with no trading (weekends, holidays) may be absent from the map.
     */
    fun getHistoricalPrices(
        ticker: String,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Map<LocalDate, Double>

    /**
     * Validates that the given ticker code is recognized by this provider.
     * Returns true if the ticker is valid, false otherwise.
     */
    fun validateTicker(ticker: String): Boolean
}
