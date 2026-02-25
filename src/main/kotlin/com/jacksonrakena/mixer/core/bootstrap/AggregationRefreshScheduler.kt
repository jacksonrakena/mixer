package com.jacksonrakena.mixer.core.bootstrap

import com.jacksonrakena.mixer.data.UserAggregationManager
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class AggregationRefreshScheduler(
    private val userAggregationManager: UserAggregationManager
) {
    @Scheduled(fixedRate = 300_000) // every 5 minutes
    fun refreshAggregations() {
        logger.debug { "Running scheduled aggregation refresh" }
        runBlocking {
            userAggregationManager.ensureAllAggregationsUpToDate()
        }
    }
}
