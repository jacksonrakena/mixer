package com.jacksonrakena.mixer.controller.asset

import com.jacksonrakena.mixer.data.tables.concrete.Asset
import com.jacksonrakena.mixer.data.tables.concrete.Transaction
import com.jacksonrakena.mixer.data.tables.virtual.AssetAggregate
import io.github.oshai.kotlinlogging.KotlinLogging
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

        logger.info { "Created asset $assetId for user ${request.ownerId}" }

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

        logger.info { "Deleted asset $assetId: $deleted" }

        return DeleteAssetResponse(assetId = assetId, deleted = deleted)
    }

}