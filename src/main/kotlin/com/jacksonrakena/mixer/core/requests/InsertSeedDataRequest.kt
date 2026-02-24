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
                        // --- Initial investment (Feb 2025) ---
                        // Buy 100 units @ $10.50 = $1050
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 360.days,
                            type = AssetTransactionType.Trade,
                            amount = 100.0,
                            value = 1050.00,
                        ),
                        // Buy 50 units @ $10.60 = $530
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 345.days,
                            type = AssetTransactionType.Trade,
                            amount = 50.0,
                            value = 530.00,
                        ),
                        // Buy 30 units @ $10.75 = $322.50
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 330.days,
                            type = AssetTransactionType.Trade,
                            amount = 30.0,
                            value = 322.50,
                        ),
                        // Sell 20 units @ $11.00 = $220 (take early profit)
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 315.days,
                            type = AssetTransactionType.Trade,
                            amount = -20.0,
                            value = 220.00,
                        ),
                        // Buy 40 units @ $10.90 = $436
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 300.days,
                            type = AssetTransactionType.Trade,
                            amount = 40.0,
                            value = 436.00,
                        ),
                        // Reconciliation: verify 200 units @ $11.05 = $2210
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 290.days,
                            type = AssetTransactionType.Reconciliation,
                            amount = 200.0,
                            value = 2210.00,
                        ),
                        // Buy 25 units @ $11.20 = $280
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 275.days,
                            type = AssetTransactionType.Trade,
                            amount = 25.0,
                            value = 280.00,
                        ),
                        // Buy 35 units @ $11.15 = $390.25
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 260.days,
                            type = AssetTransactionType.Trade,
                            amount = 35.0,
                            value = 390.25,
                        ),
                        // Sell 15 units @ $11.50 = $172.50
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 245.days,
                            type = AssetTransactionType.Trade,
                            amount = -15.0,
                            value = 172.50,
                        ),
                        // Buy 20 units @ $11.40 = $228
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 230.days,
                            type = AssetTransactionType.Trade,
                            amount = 20.0,
                            value = 228.00,
                        ),
                        // Buy 60 units @ $11.80 = $708 (larger accumulation)
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 215.days,
                            type = AssetTransactionType.Trade,
                            amount = 60.0,
                            value = 708.00,
                        ),
                        // Sell 30 units @ $12.10 = $363
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 200.days,
                            type = AssetTransactionType.Trade,
                            amount = -30.0,
                            value = 363.00,
                        ),
                        // Reconciliation: verify 295 units @ $12.00 = $3540
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 190.days,
                            type = AssetTransactionType.Reconciliation,
                            amount = 295.0,
                            value = 3540.00,
                        ),
                        // Buy 45 units @ $12.25 = $551.25
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 175.days,
                            type = AssetTransactionType.Trade,
                            amount = 45.0,
                            value = 551.25,
                        ),
                        // Buy 15 units @ $12.40 = $186
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 160.days,
                            type = AssetTransactionType.Trade,
                            amount = 15.0,
                            value = 186.00,
                        ),
                        // Sell 50 units @ $12.80 = $640 (profit-taking)
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 145.days,
                            type = AssetTransactionType.Trade,
                            amount = -50.0,
                            value = 640.00,
                        ),
                        // Buy 10 units @ $12.55 = $125.50
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 130.days,
                            type = AssetTransactionType.Trade,
                            amount = 10.0,
                            value = 125.50,
                        ),
                        // Buy 70 units @ $12.90 = $903 (big accumulation on dip)
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 115.days,
                            type = AssetTransactionType.Trade,
                            amount = 70.0,
                            value = 903.00,
                        ),
                        // Sell 25 units @ $13.20 = $330
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 105.days,
                            type = AssetTransactionType.Trade,
                            amount = -25.0,
                            value = 330.00,
                        ),
                        // Reconciliation: verify 360 units @ $13.10 = $4716
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 95.days,
                            type = AssetTransactionType.Reconciliation,
                            amount = 360.0,
                            value = 4716.00,
                        ),
                        // Buy 20 units @ $13.30 = $266
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 85.days,
                            type = AssetTransactionType.Trade,
                            amount = 20.0,
                            value = 266.00,
                        ),
                        // Buy 15 units @ $13.50 = $202.50
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 72.days,
                            type = AssetTransactionType.Trade,
                            amount = 15.0,
                            value = 202.50,
                        ),
                        // Sell 40 units @ $13.75 = $550
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 60.days,
                            type = AssetTransactionType.Trade,
                            amount = -40.0,
                            value = 550.00,
                        ),
                        // Buy 25 units @ $13.60 = $340
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 50.days,
                            type = AssetTransactionType.Trade,
                            amount = 25.0,
                            value = 340.00,
                        ),
                        // Buy 30 units @ $14.00 = $420
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 38.days,
                            type = AssetTransactionType.Trade,
                            amount = 30.0,
                            value = 420.00,
                        ),
                        // Sell 10 units @ $14.25 = $142.50
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 28.days,
                            type = AssetTransactionType.Trade,
                            amount = -10.0,
                            value = 142.50,
                        ),
                        // Buy 20 units @ $14.10 = $282
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 18.days,
                            type = AssetTransactionType.Trade,
                            amount = 20.0,
                            value = 282.00,
                        ),
                        // Reconciliation: verify 420 units @ $14.20 = $5964
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 10.days,
                            type = AssetTransactionType.Reconciliation,
                            amount = 420.0,
                            value = 5964.00,
                        ),
                        // Buy 15 units @ $14.35 = $215.25
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 5.days,
                            type = AssetTransactionType.Trade,
                            amount = 15.0,
                            value = 215.25,
                        ),
                        // Sell 5 units @ $14.50 = $72.50
                        AssetTransaction(
                            assetId = assetId,
                            timestamp = time - 2.days,
                            type = AssetTransactionType.Trade,
                            amount = -5.0,
                            value = 72.50,
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
