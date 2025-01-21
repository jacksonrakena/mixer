package com.jacksonrakena.mixer.upstream.oanda

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.jacksonrakena.mixer.MixerApplication
import com.jacksonrakena.mixer.upstream.CurrencyResponse
import com.jacksonrakena.mixer.upstream.CurrencyResponseMeta
import com.jacksonrakena.mixer.upstream.CurrencyService
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Instant
import java.util.logging.Logger

data class OandaResponse(
    val instrument: String,
    val granularity: String,
    val candles: List<Candlestick>
)
data class Candlestick(
    val time: Instant,
    val mid: CandlestickValue
)
data class CandlestickValue (
    @JsonProperty("h")
    val high: Double,

    @JsonProperty("l")
    val low: Double,

    @JsonProperty("c")
    val close: Double,

    @JsonProperty("o")
    val open: Double,
)

@Component
class OandaCurrencyService(val app: MixerApplication, val client: RestClient) : CurrencyService {
   companion object {
        val logger = Logger.getLogger(OandaCurrencyService::class.java.name)
    }

    @Cacheable("oanda-fx")
    override fun getExchangeRate(base: String, pair: String): CurrencyResponse {
        logger.info("Fetching exchange rate for $base/$pair")
        val response = client
            .get()
            .uri("https://api-fxtrade.oanda.com/v3/instruments/${base}_$pair/candles")
            .headers {
                it.setBearerAuth(app.oandaToken)
            }
            .retrieve()
            .toEntity(OandaResponse::class.java)

        if (!response.statusCode.is2xxSuccessful || response.body == null) {
            throw Error("Could not get exchange rate for $base/$pair")
        }

        val rate = response.body!!.candles[0].mid.close
        logger.info("Established exchange rate for $base/$pair as $rate")
        return CurrencyResponse(
            rate = rate,
            meta = CurrencyResponseMeta(
                generatedAt = Instant.now(),
                generatedBy = "oanda"
            )
        )
    }
}