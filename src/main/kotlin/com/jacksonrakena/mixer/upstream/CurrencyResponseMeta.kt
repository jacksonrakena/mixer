package com.jacksonrakena.mixer.upstream

import java.time.Instant

data class CurrencyResponseMeta(
    val generatedAt: Instant,
    val generatedBy: String,
    val dateOfRate: Instant
)
