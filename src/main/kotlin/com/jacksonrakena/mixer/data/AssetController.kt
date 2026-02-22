package com.jacksonrakena.mixer.data

import com.jacksonrakena.mixer.cache.RateCache
import com.jacksonrakena.mixer.upstream.CurrencyService
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.logging.Logger

@RestController
@RequestMapping("/asset")
class AssetController(val currencyService: CurrencyService, val rateCache: RateCache) {
    companion object {
        val logger = Logger.getLogger(AssetController::class.java.name)
    }

    
}