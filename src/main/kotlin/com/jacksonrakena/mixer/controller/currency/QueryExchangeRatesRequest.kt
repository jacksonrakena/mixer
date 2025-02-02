package com.jacksonrakena.mixer.controller.currency

import java.time.Instant

data class ExchangeRateInstrument(val base: String, val target: String)

data class QueryExchangeRatesRequest(
    val instruments: List<ExchangeRateInstrument>,
    val startDate: Instant?,
    val endDate: Instant?
)