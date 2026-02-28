package com.jacksonrakena.mixer

import kotlinx.serialization.json.Json
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import com.jacksonrakena.mixer.web.MdcRequestInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer




@ConfigurationProperties(prefix = "mixer")
@ConfigurationPropertiesScan
data class MixerConfiguration(
    val currency: CurrencyConfiguration,
    val refresh: RefreshConfiguration = RefreshConfiguration(),
    val fx: FxConfiguration = FxConfiguration(),
    val data: DataConfiguration = DataConfiguration(),
    val user: UserConfiguration = UserConfiguration(),
) {
    @Bean
    fun restClient(): RestClient = RestClient.create()
}

@ConfigurationPropertiesScan
data class CurrencyConfiguration(
    val provider: String = "",
    val token: String = ""
)

data class RefreshConfiguration(
    val aggregations: ScheduleConfiguration = ScheduleConfiguration(initial = 10000, interval = 300000),
    val fx: ScheduleConfiguration = ScheduleConfiguration(initial = 10000, interval = 300000),
)

data class ScheduleConfiguration(
    val initial: Long = 10000,
    val interval: Long = 300000,
)

data class FxConfiguration(
    val currencies: List<String> = listOf("EUR", "GBP", "AUD", "NZD", "USD", "HKD"),
)

data class DataConfiguration(
    val seed: SeedConfiguration = SeedConfiguration(),
)

data class SeedConfiguration(
    val insert: Boolean = true,
)

data class UserConfiguration(
    val signup: SignupConfiguration = SignupConfiguration(),
)

data class SignupConfiguration(
    val enabled: Boolean = true,
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
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(MdcRequestInterceptor())
    }
}