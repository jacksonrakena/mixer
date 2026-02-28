package com.jacksonrakena.mixer.controller.asset.transaction

import com.jacksonrakena.mixer.core.requests.RecomputeAssetAggregationRequest
import com.jacksonrakena.mixer.data.tables.concrete.Asset
import io.github.oshai.kotlinlogging.KotlinLogging
import com.jacksonrakena.mixer.data.tables.concrete.Transaction
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jobrunr.scheduling.JobRequestScheduler
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import kotlin.uuid.toKotlinUuid

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/asset/{assetId}/transaction")
class AssetTransactionController(
    val scheduler: JobRequestScheduler
) {

    @GetMapping
    fun listTransactions(
        @PathVariable assetId: UUID,
        @RequestParam page: Int = 0,
        @RequestParam size: Int = 10
    ): PaginatedTransactionsResponse {
        val assetUuid = assetId.toKotlinUuid()
        return transaction {
            val totalElements = Transaction.selectAll()
                .where { Transaction.assetId eq assetUuid }
                .count()

            val transactions = Transaction.selectAll()
                .where { Transaction.assetId eq assetUuid }
                .orderBy(Transaction.timestamp, SortOrder.DESC)
                .limit(size)
                .offset((page * size).toLong())
                .map {
                    TransactionDto(
                        id = it[Transaction.id],
                        assetId = it[Transaction.assetId],
                        type = it[Transaction.type],
                        amount = it[Transaction.amount],
                        value = it[Transaction.value],
                        timestamp = it[Transaction.timestamp]
                    )
                }

            val totalPages = if (totalElements == 0L) 0 else ((totalElements + size - 1) / size).toInt()

            PaginatedTransactionsResponse(
                transactions = transactions,
                page = page,
                size = size,
                totalElements = totalElements,
                totalPages = totalPages
            )
        }
    }

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
            val currentStaleAfter =
                Asset.selectAll().where { Asset.id eq assetId.toKotlinUuid() }.first()[Asset.staleAfter]
            val newStaleAfter =
                if (currentStaleAfter == 0L || txTimestamp < currentStaleAfter) txTimestamp else currentStaleAfter
            Asset.update({ Asset.id eq assetId.toKotlinUuid() }) {
                it[Asset.staleAfter] = newStaleAfter
            }

            Pair(insertedId, newStaleAfter)
        }

        val jobId = scheduler.enqueue(RecomputeAssetAggregationRequest(assetId.toKotlinUuid()))
        logger.info { "Scheduled RecomputeAssetAggregationRequest $jobId for asset $assetId after transaction $transactionId" }

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
                val newStaleAfter =
                    if (currentStaleAfter == 0L || txTimestamp < currentStaleAfter) txTimestamp else currentStaleAfter
                Asset.update({ Asset.id eq assetUuid }) {
                    it[Asset.staleAfter] = newStaleAfter
                }

                Pair(true, newStaleAfter)
            }
        }

        val jobId = if (deleted) {
            val id = scheduler.enqueue(RecomputeAssetAggregationRequest(assetUuid))
            logger.info { "Deleted transaction $transactionUuid, scheduled RecomputeAssetAggregationRequest $id for asset $assetUuid" }
            id.asUUID().toKotlinUuid()
        } else {
            logger.info { "Transaction $transactionUuid not found, skipping reaggregation for asset $assetUuid" }
            null
        }

        return DeleteTransactionResponse(
            transactionId = transactionUuid,
            assetId = assetUuid,
            deleted = deleted,
            jobId = jobId,
            staleAfter = staleAfter
        )
    }
}