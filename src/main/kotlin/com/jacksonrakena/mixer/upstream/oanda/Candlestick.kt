package com.jacksonrakena.mixer.upstream.oanda

import java.time.Instant

data class Candlestick(
    val time: Instant,
    val mid: CandlestickValue
)
