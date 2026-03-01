package com.jacksonrakena.mixer.core.bootstrap

import com.jacksonrakena.mixer.MixerConfiguration
import com.jacksonrakena.mixer.core.requests.BackfillCurrencyPairRequest
import com.jacksonrakena.mixer.data.AssetAggregationOrchestrator
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.jobrunr.scheduling.JobRequestScheduler
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class RecurringTaskScheduler(
    private val assetAggregationOrchestrator: AssetAggregationOrchestrator,
    private val jobRequestScheduler: JobRequestScheduler,
    private val config: MixerConfiguration,
) {
    private val logger = KotlinLogging.logger {}
    companion object {
        fun currencyPairs(currencies: List<String>): List<Pair<String, String>> {
            return currencies.mapIndexed { index, currency ->
                if (index == currencies.lastIndex) return@mapIndexed listOf()
                currencies
                    .subList(index + 1, currencies.lastIndex + 1)
                    .map { target -> Pair(currency, target) }
            }.flatten()
        }
    }

    @Scheduled(
        initialDelayString = "\${mixer.refresh.aggregations.initial:10000}",
        fixedRateString = "\${mixer.refresh.aggregations.interval:300000}"
    )
    fun refreshAggregations() {
        logger.debug { "Running scheduled aggregation refresh" }
        runBlocking {
            assetAggregationOrchestrator.ensureAllAggregationsUpToDate()
        }
    }

    @Scheduled(
        initialDelayString = "\${mixer.refresh.fx.initial:10000}",
        fixedRateString = "\${mixer.refresh.fx.interval:300000}"
    )
    fun refreshCurrencyRates() {
        val pairs = currencyPairs(config.fx.currencies)
        logger.info { "Scheduling currency backfill jobs for ${pairs.size} pairs" }
        for (pair in pairs) {
            jobRequestScheduler.enqueue(
                BackfillCurrencyPairRequest(base = pair.first, counter = pair.second)
            )
        }
    }
}
