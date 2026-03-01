package com.jacksonrakena.mixer.upstream.oanda

import com.fasterxml.jackson.annotation.JsonProperty

data class CandlestickValue(
    @JsonProperty("h")
    val high: Double,

    @JsonProperty("l")
    val low: Double,

    @JsonProperty("c")
    val close: Double,

    @JsonProperty("o")
    val open: Double,
)
