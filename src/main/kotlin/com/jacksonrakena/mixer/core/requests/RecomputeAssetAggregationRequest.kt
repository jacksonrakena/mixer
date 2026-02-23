package com.jacksonrakena.mixer.core.requests

import com.jacksonrakena.mixer.data.UserAggregationManager
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.jobrunr.jobs.lambdas.JobRequest
import org.jobrunr.jobs.lambdas.JobRequestHandler
import org.springframework.stereotype.Component
import java.util.logging.Logger
import kotlin.uuid.Uuid

@Serializable
class RecomputeAssetAggregationRequest(
    val assetId: Uuid,
    val force: Boolean = false
) : JobRequest {
    override fun getJobRequestHandler(): Class<out JobRequestHandler<*>?> {
        return RecomputeAssetAggregationRequestHandler::class.java
    }

    @Component
    class RecomputeAssetAggregationRequestHandler(
        val userAggregationManager: UserAggregationManager
    ) : JobRequestHandler<RecomputeAssetAggregationRequest> {
        override fun run(request: RecomputeAssetAggregationRequest?) {
            if (request == null) {
                logger.warning("Received null request for RecomputeAssetAggregationRequestHandler")
                return
            }

            runBlocking {
                userAggregationManager.regenerateAggregatesForAsset(request.assetId)
            }
        }

        companion object {
            val logger: Logger = Logger.getLogger(RecomputeAssetAggregationRequestHandler::class.java.name)
        }
    }
}


