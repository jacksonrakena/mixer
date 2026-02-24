package com.jacksonrakena.mixer.controller.asset

import com.jacksonrakena.mixer.controller.asset.AssetController.Companion.logger
import com.jacksonrakena.mixer.core.requests.RecomputeAssetAggregationRequest
import com.jacksonrakena.mixer.data.tables.concrete.Asset
import com.jacksonrakena.mixer.data.tables.concrete.Transaction
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jobrunr.scheduling.JobRequestScheduler
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import kotlin.uuid.toKotlinUuid

@RestController
@RequestMapping("/asset/{assetId}/transaction")
class AssetTransactionController(
    val scheduler: JobRequestScheduler
) {

    @PutMapping
    fun createTransaction(
        @PathVariable assetId: UUID,
        @RequestBody request: CreateTransactionRequest
    ): CreateTransactionResponse {
        val (transactionId, staleAfter) = transaction {
            val insertedId = Transaction.insert {
                it[Transaction.assetId] = assetId.toKotlinUuid()
                it[type] = request.type
                it[amount] = request.amount
                it[value] = request.value
                it[timestamp] = request.timestamp.toEpochMilliseconds()
            }[Transaction.id]

            val txTimestamp = request.timestamp.toEpochMilliseconds()
            val currentStaleAfter = Asset.selectAll().where { Asset.id eq assetId.toKotlinUuid() }.first()[Asset.staleAfter]
            val newStaleAfter = if (currentStaleAfter == 0L || txTimestamp < currentStaleAfter) txTimestamp else currentStaleAfter
            Asset.update({ Asset.id eq assetId.toKotlinUuid() }) {
                it[Asset.staleAfter] = newStaleAfter
            }

            Pair(insertedId, newStaleAfter)
        }

        val jobId = scheduler.enqueue(RecomputeAssetAggregationRequest(assetId.toKotlinUuid()))
        logger.info("Scheduled RecomputeAssetAggregationRequest $jobId for asset $assetId after transaction $transactionId")

        return CreateTransactionResponse(
            transactionId = transactionId,
            assetId = assetId.toKotlinUuid(),
            jobId = jobId.asUUID().toKotlinUuid(),
            staleAfter = staleAfter
        )
    }


    @DeleteMapping("{transactionId}")
    fun deleteTransaction(
        @PathVariable assetId: UUID,
        @PathVariable transactionId: UUID
    ): DeleteTransactionResponse {
        val assetUuid = assetId.toKotlinUuid()
        val transactionUuid = transactionId.toKotlinUuid()

        val (deleted, staleAfter) = transaction {
            val txRow = Transaction.selectAll().where { Transaction.id eq transactionUuid }.firstOrNull()
            if (txRow == null) {
                Pair(false, 0L)
            } else {
                val txTimestamp = txRow[Transaction.timestamp]
                Transaction.deleteWhere { Transaction.id eq transactionUuid }

                val currentStaleAfter = Asset.selectAll().where { Asset.id eq assetUuid }.first()[Asset.staleAfter]
                val newStaleAfter = if (currentStaleAfter == 0L || txTimestamp < currentStaleAfter) txTimestamp else currentStaleAfter
                Asset.update({ Asset.id eq assetUuid }) {
                    it[Asset.staleAfter] = newStaleAfter
                }

                Pair(true, newStaleAfter)
            }
        }

        val jobId = scheduler.enqueue(RecomputeAssetAggregationRequest(assetUuid))
        logger.info("Deleted transaction $transactionUuid, scheduled RecomputeAssetAggregationRequest $jobId for asset $assetUuid")

        return DeleteTransactionResponse(
            transactionId = transactionUuid,
            assetId = assetUuid,
            deleted = deleted,
            jobId = jobId.asUUID().toKotlinUuid(),
            staleAfter = staleAfter
        )
    }
}