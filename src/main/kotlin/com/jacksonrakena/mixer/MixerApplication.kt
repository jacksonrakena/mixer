package com.jacksonrakena.mixer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.scheduling.annotation.EnableScheduling

@EnableCaching
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(MixerConfiguration::class)
class MixerApplication

fun main(args: Array<String>) {
    runApplication<MixerApplication>(*args)
}

