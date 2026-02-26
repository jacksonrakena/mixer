package com.jacksonrakena.mixer.core.requests

import com.jacksonrakena.mixer.data.UserAggregationManager
import com.jacksonrakena.mixer.data.tables.concrete.Asset
import com.jacksonrakena.mixer.data.tables.concrete.User
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jobrunr.jobs.lambdas.JobRequest
import org.jobrunr.jobs.lambdas.JobRequestHandler
import org.slf4j.MDC
import org.springframework.stereotype.Component
import kotlin.uuid.Uuid

private val logger = KotlinLogging.logger {}

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
                logger.warn { "Received null request for RecomputeAssetAggregationRequestHandler" }
                return
            }

            MDC.put("assetId", request.assetId.toString())
            try {
                val userTimezone = transaction {
                    val ownerId = Asset.selectAll().where { Asset.id eq request.assetId }.first()[Asset.ownerId]
                    val tz = User.selectAll().where { User.id eq ownerId }.first()[User.timezone]
                    TimeZone.of(tz)
                }

                runBlocking {
                    userAggregationManager.regenerateAggregatesForAsset(request.assetId, userTimezone)
                }

                transaction {
                    Asset.update({ Asset.id eq request.assetId }) {
                        it[staleAfter] = 0L
                    }
                }
                logger.info { "Cleared staleAfter for asset ${request.assetId}" }
            } finally {
                MDC.remove("assetId")
            }
        }
    }
}
