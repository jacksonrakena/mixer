package com.jacksonrakena.mixer.upstream.oanda

data class OandaResponse(
    val instrument: String,
    val granularity: String,
    val candles: List<Candlestick>
)
