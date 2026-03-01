package com.jacksonrakena.mixer.controller.asset

import com.jacksonrakena.mixer.controller.auth.AuthController
import com.jacksonrakena.mixer.data.tables.concrete.Asset
import com.jacksonrakena.mixer.data.tables.concrete.Transaction
import com.jacksonrakena.mixer.data.tables.virtual.AssetAggregate
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import kotlin.uuid.toKotlinUuid

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/asset")
class AssetController(
) {
    @GetMapping
    fun getAllAssets(): List<AssetDto> {
        val userId = AuthController.currentUserId()
        return transaction {
            Asset.selectAll().where { Asset.ownerId eq userId }.map {
                AssetDto(
                    id = it[Asset.id],
                    name = it[Asset.name],
                    ownerId = it[Asset.ownerId],
                    currency = it[Asset.currency],
                    staleAfter = it[Asset.staleAfter],
                    aggregatedThrough = it[Asset.aggregatedThrough]?.toString(),
                    provider = it[Asset.provider],
                    providerData = it[Asset.providerData],
                )
            }
        }
    }

    @PostMapping
    fun createAsset(@RequestBody request: CreateAssetRequest): CreateAssetResponse {
        val userId = AuthController.currentUserId()
        val assetId = transaction {
            Asset.insert {
                it[name] = request.name
                it[ownerId] = userId
                it[currency] = request.currency
                it[provider] = request.provider
                it[providerData] = request.providerData
            }[Asset.id]
        }

        logger.info { "Created asset $assetId for user $userId" }

        return CreateAssetResponse(assetId = assetId)
    }

    @DeleteMapping("/{id}")
    fun deleteAsset(@PathVariable id: UUID): DeleteAssetResponse {
        val userId = AuthController.currentUserId()
        val assetId = id.toKotlinUuid()

        // Verify ownership
        val owns = transaction {
            Asset.selectAll().where { (Asset.id eq assetId) and (Asset.ownerId eq userId) }.firstOrNull() != null
        }
        if (!owns) {
            return DeleteAssetResponse(assetId = assetId, deleted = false)
        }

        transaction {
            Transaction.deleteWhere { Transaction.assetId eq assetId }
        }
        transaction {
            AssetAggregate.deleteWhere { AssetAggregate.assetId eq assetId }
        }
        val deleted = transaction {
            Asset.deleteWhere { Asset.id eq assetId } > 0
        }

        logger.info { "Deleted asset $assetId: $deleted" }

        return DeleteAssetResponse(assetId = assetId, deleted = deleted)
    }

}