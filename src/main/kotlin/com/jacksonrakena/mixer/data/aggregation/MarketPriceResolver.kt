package com.jacksonrakena.mixer.data.aggregation

import com.jacksonrakena.mixer.data.market.MarketDataProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component
import kotlin.uuid.Uuid

@Component
class MarketPriceResolver(
    private val marketDataProvider: MarketDataProvider,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Resolves market prices for an asset based on its provider.
     * Returns null for USER assets (no market pricing).
     */
    fun resolveMarketPrices(
        provider: String,
        providerData: String?,
        startDate: LocalDate,
        endDate: LocalDate,
        assetId: Uuid,
    ): Map<LocalDate, Double>? {
        if (provider == "USER") return null

        if (provider == "YFIN") {
            val ticker = extractTickerCode(providerData, assetId) ?: return null
            return try {
                marketDataProvider.getHistoricalPrices(ticker, startDate, endDate)
            } catch (e: Exception) {
                logger.error(e) { "Failed to fetch market data for asset $assetId (ticker=$ticker)" }
                null
            }
        }

        logger.warn { "Unknown provider '$provider' for asset $assetId, skipping market data" }
        return null
    }

    fun extractTickerCode(providerData: String?, assetId: Uuid): String? {
        if (providerData == null) {
            logger.warn { "YFIN asset $assetId has no providerData" }
            return null
        }
        return try {
            Json.parseToJsonElement(providerData).jsonObject["tickerCode"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse providerData for asset $assetId: $providerData" }
            null
        }
    }
}
