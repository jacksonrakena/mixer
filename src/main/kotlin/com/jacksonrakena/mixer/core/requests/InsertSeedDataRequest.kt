package com.jacksonrakena.mixer.core.requests

import com.jacksonrakena.mixer.data.AssetTransaction
import com.jacksonrakena.mixer.data.AssetTransactionType
import com.jacksonrakena.mixer.data.tables.concrete.Asset
import com.jacksonrakena.mixer.data.tables.concrete.Transaction
import com.jacksonrakena.mixer.data.tables.concrete.User
import com.jacksonrakena.mixer.upstream.CurrencyService
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jobrunr.jobs.lambdas.JobRequest
import org.jobrunr.jobs.lambdas.JobRequestHandler
import org.jobrunr.scheduling.JobRequestScheduler
import org.springframework.stereotype.Component
import java.util.logging.Logger
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
class InsertSeedDataRequest: JobRequest {
    override fun getJobRequestHandler(): Class<out JobRequestHandler<*>?> {
        return InsertSeedDataRequestHandler::class.java
    }

    @Component
    class InsertSeedDataRequestHandler(
        val database: Database,
        val currencyService: CurrencyService,
        val jobRequestScheduler: JobRequestScheduler
    ) : JobRequestHandler<InsertSeedDataRequest> {
        override fun run(request: InsertSeedDataRequest?) {
            if (request == null) {
                logger.warning("Received null request for InsertSeedDataRequestHandler")
                return
            }

            val time = Instant.parse("2026-02-16T23:05:37.337365Z")
            val assetId = Uuid.parse("6c942179-c993-4b25-86dd-6346fb0e3005")
            transaction {
                User.insert {
                    it[User.id] = Uuid.parse("6c942179-c993-4b25-86dd-6346fb0e3005")
                    it[User.timezone] = "Australia/Sydney"
                }
                Asset.insert {
                    it[Asset.id] = assetId
                    it[Asset.name] = "test asset"
                    it[Asset.currency] = "AUD"
                    it[Asset.ownerId] = Uuid.parse("6c942179-c993-4b25-86dd-6346fb0e3005")
                }
                Transaction.batchInsert(
                    listOf(
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 5.days,
                            type = AssetTransactionType.Trade,
                            amount = 10.0,
                            value = 100.0,
                        ),
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 4.days,
                            type = AssetTransactionType.Trade,
                            amount = -1.0,
                            value = 100.0,
                        ),
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 3.days,
                            type = AssetTransactionType.Reconciliation,
                            amount = 11.0,
                            value = 101.0,
                        ),
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time,
                            type = AssetTransactionType.Trade,
                            amount = 2.0,
                            value = 101.0
                        ),
                    )
                ) {
                    this[Transaction.assetId] = it.assetId
                    this[Transaction.timestamp] = it.timestamp.toEpochMilliseconds()
                    this[Transaction.type] = it.type
                    this[Transaction.amount] = it.amount
                    this[Transaction.value] = it.value
                }
            }
            logger.info("Finished seed data")
            jobRequestScheduler.enqueue(RecomputeUserAggregationRequest(Uuid.parse("6c942179-c993-4b25-86dd-6346fb0e3005")))
        }

        companion object {
            val logger = Logger.getLogger(InsertSeedDataRequestHandler::class.java.name)
        }
    }
}
