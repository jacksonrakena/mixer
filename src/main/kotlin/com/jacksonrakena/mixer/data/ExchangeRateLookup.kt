package com.jacksonrakena.mixer.data

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Result of an exchange rate lookup, including the rate and the actual date
 * of the rate record used (which may differ from the requested date if
 * the exact date was missing from the source data).
 */
@Serializable
data class ExchangeRateLookup(
    val rate: Double,
    val base: String,
    val counter: String,
    val requestedDate: LocalDate,
    val actualDate: LocalDate,
)