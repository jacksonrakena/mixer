package com.jacksonrakena.mixer

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@ConfigurationProperties(prefix = "mixer")
@ConfigurationPropertiesScan
data class MixerConfiguration(
    val currency: CurrencyConfiguration
) {
    @Bean
    fun restClient(): RestClient = RestClient.create()
}

@ConfigurationPropertiesScan
data class CurrencyConfiguration(
    val provider: String,
    val token: String
)