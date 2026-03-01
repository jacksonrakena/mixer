package com.jacksonrakena.mixer.upstream

import java.time.Instant

data class CurrencyRangeResponse(
    val meta: CurrencyResponseMeta,
    val rates: Map<Instant, Double>
)
