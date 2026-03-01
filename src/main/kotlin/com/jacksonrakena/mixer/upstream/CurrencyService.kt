package com.jacksonrakena.mixer.upstream

interface CurrencyService {
    fun getHistoricExchangeRates(pair: Pair<String, String>, from: java.time.ZonedDateTime? = null): CurrencyRangeResponse
}