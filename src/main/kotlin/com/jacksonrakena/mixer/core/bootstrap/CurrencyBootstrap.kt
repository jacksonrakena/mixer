package com.jacksonrakena.mixer.core.bootstrap

import com.jacksonrakena.mixer.core.requests.BackfillCurrencyPairRequest
import com.jacksonrakena.mixer.core.requests.InsertSeedDataRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.jobrunr.scheduling.JobRequestScheduler
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class CurrencyBootstrap(
    val scheduler: JobRequestScheduler
) {
    companion object {
        private val SUPPORTED_CURRENCIES = listOf(
            "EUR",
            "GBP",
            "AUD",
            "NZD",
            "USD",
            "HKD"
        )

        val SUPPORTED_PAIRS by lazy {
            SUPPORTED_CURRENCIES.mapIndexed { index, currency ->
                if (index == SUPPORTED_CURRENCIES.lastIndex) return@mapIndexed listOf()
                return@mapIndexed SUPPORTED_CURRENCIES
                    .subList(index + 1, SUPPORTED_CURRENCIES.lastIndex + 1).map { target ->
                        return@map Pair(currency, target)
                    }
            }.flatten()
        }
    }

    @PostConstruct
    fun updateAllCachedRates() {
        for (pair in SUPPORTED_PAIRS) {
            val jobId = scheduler.enqueue(
                BackfillCurrencyPairRequest(
                    base = pair.first,
                    counter = pair.second,
                )
            )
            logger.info { "Scheduled backfill job $jobId for ${pair.first}/${pair.second}" }
        }
        scheduler.enqueue(InsertSeedDataRequest())
    }
}