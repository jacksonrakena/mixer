package com.jacksonrakena.mixer.core.bootstrap

import com.jacksonrakena.mixer.core.requests.BackfillCurrencyPairRequest
import com.jacksonrakena.mixer.data.UserAggregationManager
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.jobrunr.scheduling.JobRequestScheduler
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class RecurringTaskScheduler(
    private val userAggregationManager: UserAggregationManager,
    private val jobRequestScheduler: JobRequestScheduler,
) {
    companion object {
        private val SUPPORTED_CURRENCIES = listOf("EUR", "GBP", "AUD", "NZD", "USD", "HKD")

        val SUPPORTED_PAIRS by lazy {
            SUPPORTED_CURRENCIES.mapIndexed { index, currency ->
                if (index == SUPPORTED_CURRENCIES.lastIndex) return@mapIndexed listOf()
                SUPPORTED_CURRENCIES
                    .subList(index + 1, SUPPORTED_CURRENCIES.lastIndex + 1)
                    .map { target -> Pair(currency, target) }
            }.flatten()
        }
    }

    @Scheduled(initialDelay = 10_000, fixedRate = 300_000) // first run after 10s, then every 5 minutes
    fun refreshAggregations() {
        logger.debug { "Running scheduled aggregation refresh" }
        runBlocking {
            userAggregationManager.ensureAllAggregationsUpToDate()
        }
    }

    @Scheduled(initialDelay = 10_000, fixedRate = 300_000) // on startup, then daily
    fun refreshCurrencyRates() {
        logger.info { "Scheduling currency backfill jobs" }
        for (pair in SUPPORTED_PAIRS) {
            jobRequestScheduler.enqueue(
                BackfillCurrencyPairRequest(base = pair.first, counter = pair.second)
            )
        }
    }
}
