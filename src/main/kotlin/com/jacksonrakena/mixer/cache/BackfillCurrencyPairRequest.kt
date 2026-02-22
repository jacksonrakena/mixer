package com.jacksonrakena.mixer.cache

import com.jacksonrakena.mixer.data.ExchangeRate
import com.jacksonrakena.mixer.upstream.CurrencyService
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jobrunr.jobs.lambdas.JobRequest
import org.jobrunr.jobs.lambdas.JobRequestHandler
import org.springframework.stereotype.Component
import java.time.ZoneOffset
import java.util.logging.Level
import java.util.logging.Logger

@Serializable
data class BackfillCurrencyPairRequest(val base: String, val counter: String): JobRequest {
    override fun getJobRequestHandler(): Class<out JobRequestHandler<*>?> {
        return BackfillCurrencyPairRequestHandler::class.java
    }
}

@Component
class BackfillCurrencyPairRequestHandler(
    val database: Database,
    val currencyService: CurrencyService,
) : JobRequestHandler<BackfillCurrencyPairRequest> {
    override fun run(request: BackfillCurrencyPairRequest?) {
        if (request == null) {
            logger.warning("Received null request for BackfillCurrencyPairRequestHandler")
            return
        }

//        logger.info("Processing backfill request for ${request.base}/${request.counter}")

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
//            logger.info("Successfully backfilled currency pair ${request.base}/${request.counter}\n" +
//                    "${transaction { ExchangeRate.select(ExchangeRate.referenceDate.count()).count()}} records total.")
        } catch (e: Error) {
            logger.log(Level.SEVERE, e) {
                "failed to fetch currency pair ${request.base}/${request.counter}"
            }
        }
    }

    companion object {
        val logger = Logger.getLogger(RateCache::class.java.name)
    }
}