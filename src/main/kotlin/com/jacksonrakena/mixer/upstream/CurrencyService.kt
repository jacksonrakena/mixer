package com.jacksonrakena.mixer.upstream

import java.time.Instant

data class CurrencyResponseMeta(
    val generatedAt: Instant,
    val generatedBy: String,
    val dateOfRate: Instant
)
data class CurrencyResponse(
    val meta: CurrencyResponseMeta,
    val rate: Double?
)

data class CurrencyRangeResponse(
    val meta: CurrencyResponseMeta,
    val rates: Map<Instant, Double>
)
interface CurrencyService {
    fun getExchangeRate(base: String, pair: String): CurrencyResponse
    fun getHistoricExchangeRates(pair: Pair<String, String>): CurrencyRangeResponse
}