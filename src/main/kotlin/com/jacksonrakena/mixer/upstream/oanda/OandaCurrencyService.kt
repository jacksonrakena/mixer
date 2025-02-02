package com.jacksonrakena.mixer.upstream.oanda

import com.fasterxml.jackson.annotation.JsonProperty
import com.jacksonrakena.mixer.MixerApplication
import com.jacksonrakena.mixer.MixerConfiguration
import com.jacksonrakena.mixer.upstream.CurrencyRangeResponse
import com.jacksonrakena.mixer.upstream.CurrencyResponse
import com.jacksonrakena.mixer.upstream.CurrencyResponseMeta
import com.jacksonrakena.mixer.upstream.CurrencyService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Instant
import java.time.ZoneId
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
@ConditionalOnProperty("mixer.currency.provider", havingValue = "oanda")
class OandaCurrencyService(val app: MixerApplication, val client: RestClient, val config: MixerConfiguration): CurrencyService {
   companion object {
        val logger = Logger.getLogger(OandaCurrencyService::class.java.name)
    }

    override fun getExchangeRate(base: String, pair: String): CurrencyResponse {
        logger.info("Fetching exchange rate for $base/$pair")
        val response = client
            .get()
            .uri("https://api-fxtrade.oanda.com/v3/instruments/${base}_$pair/candles")
            .headers {
                it.setBearerAuth(config.currency.token)
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
                generatedBy = "oanda",
                dateOfRate = Instant.now()
            )
        )
    }

    override fun getHistoricExchangeRates(pair: Pair<String, String>): CurrencyRangeResponse {
        val end = Instant.now().atZone(ZoneId.systemDefault())
        val start = end.minusDays(4950)
        logger.info("$pair: fetching rate history from $start to $end")
        val response = client
            .get()
            .uri("https://api-fxtrade.oanda.com/v3/instruments/${pair.first}_${pair.second}/candles" +
                    "?granularity=D&from=${start.toEpochSecond()}&to=${end.toEpochSecond()}")
            .headers {
                it.setBearerAuth(config.currency.token)
            }
            .retrieve()
            .toEntity(OandaResponse::class.java)

        if (!response.statusCode.is2xxSuccessful || response.body == null) {
            throw Error("Could not get exchange rate for ${pair.first}/${pair.second}")
        }

        logger.info { "$pair: retrieved ${response.body!!.candles.size} days of rate history"}
        return CurrencyRangeResponse(
            meta = CurrencyResponseMeta(
                generatedBy = "oanda-range",
                generatedAt = Instant.now(),
                dateOfRate = Instant.now()
            ),
            rates = response.body!!.candles.associate { it -> Pair(it.time, it.mid.close) }
        )
    }
}