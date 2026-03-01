package com.jacksonrakena.mixer.controller.config

import com.jacksonrakena.mixer.MixerConfiguration
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/config")
class ConfigController(private val config: MixerConfiguration) {
    @GetMapping
    fun getConfig(): ClientConfigResponse {
        return ClientConfigResponse(
            currencies = config.fx.currencies,
            enabledMarketSources = config.markets.sources.enabled,
        )
    }
}
