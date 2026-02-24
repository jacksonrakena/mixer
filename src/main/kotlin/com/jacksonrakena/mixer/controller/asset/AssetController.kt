package com.jacksonrakena.mixer.controller.asset

import com.jacksonrakena.mixer.data.AssetTransactionType
import com.jacksonrakena.mixer.data.tables.concrete.Asset
import com.jacksonrakena.mixer.data.tables.concrete.Transaction
import com.jacksonrakena.mixer.data.tables.virtual.AssetAggregate
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
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
    val jobId: Uuid,
    val staleAfter: Long
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
    val jobId: Uuid,
    val staleAfter: Long
)

@Serializable
data class AssetDto(
    val id: Uuid,
    val name: String,
    val ownerId: Uuid,
    val currency: String,
    val staleAfter: Long = 0
)

@Serializable
data class AssetStalenessResponse(
    val assetId: Uuid,
    val staleAfter: Long
)

@RestController
@RequestMapping("/asset")
class AssetController(
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
                    currency = it[Asset.currency],
                    staleAfter = it[Asset.staleAfter]
                )
            }
        }
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

    @GetMapping("/{id}/staleness")
    fun getAssetStaleness(@PathVariable id: UUID): AssetStalenessResponse {
        val assetId = id.toKotlinUuid()
        val asset = transaction {
            Asset.selectAll().where { Asset.id eq assetId }.firstOrNull()
        } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found")

        return AssetStalenessResponse(
            assetId = assetId,
            staleAfter = asset[Asset.staleAfter]
        )
    }
}