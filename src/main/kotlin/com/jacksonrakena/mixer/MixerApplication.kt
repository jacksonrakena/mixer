package com.jacksonrakena.mixer

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Contact
import io.swagger.v3.oas.annotations.info.Info
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.scheduling.annotation.EnableScheduling

@EnableCaching
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(MixerConfiguration::class)
@OpenAPIDefinition(
    info = Info(
        title = "Mixer",
        version = "1.0",
        description = "Simple foreign exchange rate caching multiplexer",
        contact = Contact(
            url = "https://github.com/jacksonrakena/mixer",
        )
    )
)
class MixerApplication

fun main(args: Array<String>) {
    runApplication<MixerApplication>(*args)
}

