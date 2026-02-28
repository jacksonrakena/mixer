package com.jacksonrakena.mixer

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Contact
import io.swagger.v3.oas.annotations.info.Info
import org.jetbrains.exposed.v1.jdbc.Database
import org.jobrunr.jobs.mappers.JobMapper
import org.jobrunr.storage.InMemoryStorageProvider
import org.jobrunr.storage.StorageProvider
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling
import javax.sql.DataSource

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
class MixerApplication {
    @Bean
    fun storageProvider(jobMapper: JobMapper): StorageProvider {
        return InMemoryStorageProvider().also { it.setJobMapper(jobMapper) }
    }

    @Bean
    fun database(dataSource: DataSource): Database {
        return Database.connect(dataSource)
    }
}

fun main(args: Array<String>) {
    runApplication<MixerApplication>(*args)
}

