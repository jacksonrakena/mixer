package com.jacksonrakena.mixer.controller.currency

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.time.Instant

data class ExchangeRateInstrument(val base: String, val target: String)

@Schema(description = "Exchange rate data request")
data class QueryExchangeRatesRequest(
    @NotNull
    @Parameter(description = "The currencies to request rates for.")
    val instruments: List<ExchangeRateInstrument>,

    @Parameter(description = "The start date of the requested range. If null, defaults to the current date.")
    val startDate: Instant?,

    @NotNull
    @Parameter(description = "The end date of the requested range. If null, defaults to the current date.")
    val endDate: Instant?
)