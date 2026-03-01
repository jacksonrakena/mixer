package com.jacksonrakena.mixer.data.market

import com.jacksonrakena.mixer.MixerConfiguration
import com.jacksonrakena.mixer.data.market.yahoo.YahooFinanceClient
import com.jacksonrakena.mixer.data.market.yahoo.YahooFinanceProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.LocalDate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configures [MarketDataProvider] based on enabled market sources.
 *
 * If "yfin" is in `mixer.markets.sources.enabled`, creates [YahooFinanceProvider].
 * Otherwise, provides a no-op implementation that returns empty price maps.
 */
@Configuration
class MarketDataConfiguration(private val config: MixerConfiguration) {
    private val logger = KotlinLogging.logger {}

    @Bean
    fun marketDataProvider(): MarketDataProvider {
        val enabled = config.markets.sources.enabled.map { it.lowercase() }

        if ("yfin" in enabled) {
            logger.info { "Yahoo Finance market data provider enabled" }
            return YahooFinanceProvider(YahooFinanceClient())
        }

        logger.info { "No market data providers enabled, using no-op provider" }
        return object : MarketDataProvider {
            override fun getHistoricalPrices(
                ticker: String,
                startDate: LocalDate,
                endDate: LocalDate,
            ): Map<LocalDate, Double> = emptyMap()

            override fun validateTicker(ticker: String): Boolean = true
        }
    }
}
