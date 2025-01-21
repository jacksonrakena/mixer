package com.jacksonrakena.mixer.upstream

import java.time.Instant

data class CurrencyResponseMeta(
    val generatedAt: Instant,
    val generatedBy: String
)
data class CurrencyResponse(
    val meta: CurrencyResponseMeta,
    val rate: Double?
)

interface CurrencyService {
    fun getExchangeRate(base: String, pair: String): CurrencyResponse
}