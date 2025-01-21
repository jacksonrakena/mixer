package com.jacksonrakena.mixer.controller

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.jacksonrakena.mixer.upstream.CurrencyResponse
import com.jacksonrakena.mixer.upstream.CurrencyService
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/currency")
class CurrencyController(val currencyService: CurrencyService) {
    @GetMapping("/{base}/{target}")
    fun getExchangeRate(@PathVariable base: String, @PathVariable target: String): CurrencyResponse {
        return currencyService.getExchangeRate(base, target)
    }
}