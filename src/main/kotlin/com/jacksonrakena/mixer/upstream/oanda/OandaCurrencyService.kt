package com.jacksonrakena.mixer.upstream.oanda

import com.jacksonrakena.mixer.MixerApplication
import com.jacksonrakena.mixer.MixerConfiguration
import com.jacksonrakena.mixer.upstream.CurrencyRangeResponse
import com.jacksonrakena.mixer.upstream.CurrencyResponseMeta
import com.jacksonrakena.mixer.upstream.CurrencyService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Instant
import java.time.ZoneId

@Component
@ConditionalOnProperty("mixer.currency.provider", havingValue = "oanda")
class OandaCurrencyService(val app: MixerApplication, val client: RestClient, val config: MixerConfiguration) :
    CurrencyService {
    private val logger = KotlinLogging.logger {}

    override fun getHistoricExchangeRates(pair: Pair<String, String>, from: java.time.ZonedDateTime?): CurrencyRangeResponse {
        val end = Instant.now().atZone(ZoneId.systemDefault())
        val start = from ?: end.minusDays(4950)
        val response = client
            .get()
            .uri(
                "https://api-fxtrade.oanda.com/v3/instruments/${pair.first}_${pair.second}/candles" +
                        "?granularity=D&from=${start.toEpochSecond()}&to=${end.toEpochSecond()}"
            )
            .headers {
                it.setBearerAuth(config.currency.token)
            }
            .retrieve()
            .toEntity(OandaResponse::class.java)

        if (!response.statusCode.is2xxSuccessful || response.body == null) {
            throw Error("Could not get exchange rate for ${pair.first}/${pair.second}")
        }

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