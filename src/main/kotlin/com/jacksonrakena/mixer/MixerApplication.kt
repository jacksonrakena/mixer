package com.jacksonrakena.mixer

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.client.RestClient

@EnableCaching
@SpringBootApplication
@EnableScheduling
class MixerApplication(
    @Value("\${oanda.token}") val oandaToken: String,
)

fun main(args: Array<String>) {
    runApplication<MixerApplication>(*args)
}

@Configuration
class MixerConfiguration {
    @Bean
    fun restClient(): RestClient = RestClient.create()
}