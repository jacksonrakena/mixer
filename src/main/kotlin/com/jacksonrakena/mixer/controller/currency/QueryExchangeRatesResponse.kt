package com.jacksonrakena.mixer.controller.currency

import java.time.Instant

data class QueryExchangeRatesResponse(
    val rates: Map<Instant, Map<String, Map<String, Double>>>
)