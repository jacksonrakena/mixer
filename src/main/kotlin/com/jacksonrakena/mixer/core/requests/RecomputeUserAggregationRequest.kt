package com.jacksonrakena.mixer.core.requests

import com.jacksonrakena.mixer.data.aggregation.AssetAggregationOrchestrator
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jobrunr.jobs.lambdas.JobRequest
import org.jobrunr.jobs.lambdas.JobRequestHandler
import org.slf4j.MDC
import org.springframework.stereotype.Component
import kotlin.uuid.Uuid

@Serializable
class RecomputeUserAggregationRequest(val userId: Uuid) : JobRequest {
    override fun getJobRequestHandler(): Class<out JobRequestHandler<*>?> {
        return RecomputeUserAggregationRequestHandler::class.java
    }

    @Component
    class RecomputeUserAggregationRequestHandler(
        val database: Database,
        val assetAggregationOrchestrator: AssetAggregationOrchestrator
    ) : JobRequestHandler<RecomputeUserAggregationRequest> {
        private val logger = KotlinLogging.logger {}

        override fun run(request: RecomputeUserAggregationRequest?) {
            if (request == null) {
                logger.warn { "Received null request for RecomputeUserAggregationRequestHandler" }
                return
            }

            MDC.put("userId", request.userId.toString())
            try {
                runBlocking {
                    assetAggregationOrchestrator.forceAggregateUserAssets(request.userId)
                }
            } finally {
                MDC.remove("userId")
            }
        }
    }
}
