package com.jacksonrakena.mixer.core.requests

import com.jacksonrakena.mixer.data.tables.markets.ExchangeRate
import com.jacksonrakena.mixer.upstream.CurrencyService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jobrunr.jobs.lambdas.JobRequest
import org.jobrunr.jobs.lambdas.JobRequestHandler
import org.slf4j.MDC
import org.springframework.stereotype.Component
import java.time.ZoneOffset

private val logger = KotlinLogging.logger {}

@Serializable
data class BackfillCurrencyPairRequest(val base: String, val counter: String): JobRequest {
    override fun getJobRequestHandler(): Class<out JobRequestHandler<*>?> {
        return BackfillCurrencyPairRequestHandler::class.java
    }

    @Component
    class BackfillCurrencyPairRequestHandler(
        val database: Database,
        val currencyService: CurrencyService,
    ) : JobRequestHandler<BackfillCurrencyPairRequest> {
        override fun run(request: BackfillCurrencyPairRequest?) {
            if (request == null) {
                logger.warn { "Received null request for BackfillCurrencyPairRequestHandler" }
                return
            }

            MDC.put("currencyPair", "${request.base}/${request.counter}")
            try {
                val rate = currencyService.getHistoricExchangeRates(Pair(request.base, request.counter))

                transaction {
                    for (entry in rate.rates) {
                        ExchangeRate.insert {
                            it[base] = request.base
                            it[counter] = request.counter
                            it[referenceDate] =
                                entry.key.atOffset(ZoneOffset.UTC).toLocalDate().toKotlinLocalDate()
                            it[ExchangeRate.rate] = entry.value ?: 0.0
                        }
                    }
                }
            } catch (e: Error) {
                logger.error(e) { "failed to fetch currency pair ${request.base}/${request.counter}" }
            } finally {
                MDC.remove("currencyPair")
            }
        }
    }
}