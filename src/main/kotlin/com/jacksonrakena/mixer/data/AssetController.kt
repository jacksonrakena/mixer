package com.jacksonrakena.mixer.data

import com.jacksonrakena.mixer.cache.RecomputeAssetAggregationRequest
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jobrunr.scheduling.JobRequestScheduler
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import java.util.logging.Logger
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

@Serializable
data class CreateTransactionRequest(
    val type: AssetTransactionType,
    val amount: Double? = null,
    val value: Double? = null,
    val timestamp: Instant,
)

@Serializable
data class CreateTransactionResponse(
    val transactionId: Uuid,
    val assetId: Uuid,
    val jobId: Uuid
)

@Serializable
data class CreateAssetRequest(
    val name: String,
    val ownerId: Uuid,
    val currency: String
)

@Serializable
data class CreateAssetResponse(
    val assetId: Uuid
)

@Serializable
data class DeleteAssetResponse(
    val assetId: Uuid,
    val deleted: Boolean
)

@Serializable
data class DeleteTransactionResponse(
    val transactionId: Uuid,
    val assetId: Uuid,
    val deleted: Boolean,
    val jobId: Uuid
)

@Serializable
data class AssetDto(
    val id: Uuid,
    val name: String,
    val ownerId: Uuid,
    val currency: String
)

@RestController
@RequestMapping("/asset")
class AssetController(
    val scheduler: JobRequestScheduler
) {
    companion object {
        val logger = Logger.getLogger(AssetController::class.java.name)
    }

    @GetMapping
    fun getAllAssets(): List<AssetDto> {
        return transaction {
            Asset.selectAll().map {
                AssetDto(
                    id = it[Asset.id],
                    name = it[Asset.name],
                    ownerId = it[Asset.ownerId],
                    currency = it[Asset.currency]
                )
            }
        }
    }

    @PutMapping("/{id}/transaction")
    fun createTransaction(
        @PathVariable id: UUID,
        @RequestBody request: CreateTransactionRequest
    ): CreateTransactionResponse {
        val transactionId = transaction {
            val insertedId = Transaction.insert {
                it[assetId] = id.toKotlinUuid()
                it[type] = request.type
                it[amount] = request.amount
                it[value] = request.value
                it[timestamp] = request.timestamp.toEpochMilliseconds()
            }[Transaction.id]

            insertedId
        }

        val jobId = scheduler.enqueue(RecomputeAssetAggregationRequest(id.toKotlinUuid()))
        logger.info("Scheduled RecomputeAssetAggregationRequest $jobId for asset $id after transaction $transactionId")

        return CreateTransactionResponse(
            transactionId = transactionId,
            assetId = id.toKotlinUuid(),
            jobId = jobId.asUUID().toKotlinUuid()
        )
    }

    @PostMapping
    fun createAsset(@RequestBody request: CreateAssetRequest): CreateAssetResponse {
        val assetId = transaction {
            Asset.insert {
                it[name] = request.name
                it[ownerId] = request.ownerId
                it[currency] = request.currency
            }[Asset.id]
        }

        logger.info("Created asset $assetId for user ${request.ownerId}")

        return CreateAssetResponse(assetId = assetId)
    }

    @DeleteMapping("/{id}")
    fun deleteAsset(@PathVariable id: UUID): DeleteAssetResponse {
        val assetId = id.toKotlinUuid()

        // Delete associated transactions first
        transaction {
            Transaction.deleteWhere { Transaction.assetId eq assetId }
        }

        // Delete associated aggregates
        transaction {
            AssetAggregate.deleteWhere { AssetAggregate.assetId eq assetId }
        }

        // Delete the asset
        val deleted = transaction {
            Asset.deleteWhere { Asset.id eq assetId } > 0
        }

        logger.info("Deleted asset $assetId: $deleted")

        return DeleteAssetResponse(assetId = assetId, deleted = deleted)
    }

    @DeleteMapping("/{assetId}/transaction/{transactionId}")
    fun deleteTransaction(
        @PathVariable assetId: UUID,
        @PathVariable transactionId: UUID
    ): DeleteTransactionResponse {
        val assetUuid = assetId.toKotlinUuid()
        val transactionUuid = transactionId.toKotlinUuid()

        val deleted = transaction {
            Transaction.deleteWhere { Transaction.id eq transactionUuid } > 0
        }

        val jobId = scheduler.enqueue(RecomputeAssetAggregationRequest(assetUuid))
        logger.info("Deleted transaction $transactionUuid, scheduled RecomputeAssetAggregationRequest $jobId for asset $assetUuid")

        return DeleteTransactionResponse(
            transactionId = transactionUuid,
            assetId = assetUuid,
            deleted = deleted,
            jobId = jobId.asUUID().toKotlinUuid()
        )
    }
}