package com.jacksonrakena.mixer

import kotlinx.serialization.json.Json
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import com.jacksonrakena.mixer.web.MdcRequestInterceptor
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer




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
@Configuration
class SerializationConfiguration {

    @Bean
    fun json(): Json {
        return Json {
            // Set encodeDefaults to true to include properties with default values in the serialized output
            encodeDefaults = true
            // Add any other desired configurations, e.g., prettyPrint, ignoreUnknownKeys, etc.
            prettyPrint = true
//            ignoreUnknownKeys = true
        }
    }
}


@Configuration
class WebConfig : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOrigins("*")
            .allowedMethods("*")
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(MdcRequestInterceptor())
    }
}