package com.jacksonrakena.mixer.cache

import com.jacksonrakena.mixer.data.AggregationService
import com.jacksonrakena.mixer.data.UserAggregationManager
import com.jacksonrakena.mixer.upstream.CurrencyService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jobrunr.jobs.lambdas.JobRequest
import org.jobrunr.jobs.lambdas.JobRequestHandler
import org.springframework.stereotype.Component
import java.util.logging.Logger
import kotlin.uuid.Uuid

@Serializable
class RecomputeUserAggregationRequest(val userId: Uuid): JobRequest {
    override fun getJobRequestHandler(): Class<out JobRequestHandler<*>?> {
        return RecomputeUserAggregationRequestHandler::class.java
    }
}

@Component
class RecomputeUserAggregationRequestHandler(
    val database: Database,
    val currencyService: CurrencyService,
    val aggregationService: AggregationService,
    val userAggregationManager: UserAggregationManager
) : JobRequestHandler<RecomputeUserAggregationRequest> {
    override fun run(request: RecomputeUserAggregationRequest?) {
        if (request == null) {
            logger.warning("Received null request for RecomputeUserAggregationRequestHandler")
            return
        }

        runBlocking {
            userAggregationManager.forceAggregateUserAssets(request.userId)
        }
    }

    companion object {
        val logger = Logger.getLogger(RateCache::class.java.name)
    }
}