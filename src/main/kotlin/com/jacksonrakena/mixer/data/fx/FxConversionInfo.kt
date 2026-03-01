package com.jacksonrakena.mixer.data.fx

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Metadata about the exchange rate used for FX conversion on a given aggregation point.
 */
@Serializable
data class FxConversionInfo(
    /** The exchange rate applied (asset currency -> display currency). */
    val rate: Double,
    /** The base currency (asset's native currency). */
    val fromCurrency: String,
    /** The target currency (user's display currency). */
    val toCurrency: String,
    /** The date of the exchange rate record actually used (may differ from the aggregation date due to fallback). */
    val rateDate: LocalDate,
)