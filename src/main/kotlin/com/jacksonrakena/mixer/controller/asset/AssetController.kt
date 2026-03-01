package com.jacksonrakena.mixer.controller.asset

import com.jacksonrakena.mixer.controller.auth.AuthController
import com.jacksonrakena.mixer.core.requests.RecomputeAssetAggregationRequest
import com.jacksonrakena.mixer.data.market.MarketDataProvider
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
import org.jetbrains.exposed.v1.jdbc.update
import org.jobrunr.scheduling.JobRequestScheduler
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.uuid.toKotlinUuid

@RestController
@RequestMapping("/asset")
class AssetController(
    val scheduler: JobRequestScheduler,
    val marketDataProvider: MarketDataProvider,
) {
    private val logger = KotlinLogging.logger {}
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

        validateProvider(request.provider, request.providerData)

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

    @PatchMapping("/{id}")
    fun updateAsset(@PathVariable id: UUID, @RequestBody request: UpdateAssetRequest): AssetDto {
        val userId = AuthController.currentUserId()
        val assetId = id.toKotlinUuid()

        val asset = transaction {
            Asset.selectAll().where { (Asset.id eq assetId) and (Asset.ownerId eq userId) }.firstOrNull()
        } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

        val effectiveProvider = request.provider ?: asset[Asset.provider]
        val effectiveProviderData = request.providerData ?: asset[Asset.providerData]
        validateProvider(effectiveProvider, effectiveProviderData)

        val providerChanged = request.provider != null && request.provider != asset[Asset.provider]
        val providerDataChanged = request.providerData != null && request.providerData != asset[Asset.providerData]
        val needsReaggregation = providerChanged || providerDataChanged

        transaction {
            Asset.update({ Asset.id eq assetId }) {
                if (request.name != null) it[name] = request.name.trim()
                if (request.provider != null) it[provider] = request.provider
                if (request.providerData != null) it[providerData] = request.providerData
                if (needsReaggregation) {
                    it[staleAfter] = System.currentTimeMillis()
                    it[aggregatedThrough] = null
                }
            }
        }

        if (needsReaggregation) {
            // Clear existing aggregates and enqueue full reaggregation
            transaction {
                AssetAggregate.deleteWhere { AssetAggregate.assetId eq assetId }
            }
            scheduler.enqueue(RecomputeAssetAggregationRequest(assetId, force = true))
            logger.info { "Updated asset $assetId provider, enqueued reaggregation" }
        } else {
            logger.info { "Updated asset $assetId" }
        }

        return AssetDto(
            id = asset[Asset.id],
            name = request.name?.trim() ?: asset[Asset.name],
            ownerId = asset[Asset.ownerId],
            currency = asset[Asset.currency],
            staleAfter = if (needsReaggregation) System.currentTimeMillis() else asset[Asset.staleAfter],
            aggregatedThrough = if (needsReaggregation) null else asset[Asset.aggregatedThrough]?.toString(),
            provider = request.provider ?: asset[Asset.provider],
            providerData = request.providerData ?: asset[Asset.providerData],
        )
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

    private fun validateProvider(provider: String?, providerData: String?) {
        if (provider == null || provider == "USER") return

        if (provider == "YFIN") {
            val tickerCode = providerData?.let {
                try {
                    val parsed = kotlinx.serialization.json.Json.parseToJsonElement(it)
                    (parsed as? kotlinx.serialization.json.JsonObject)
                        ?.get("tickerCode")
                        ?.let { el -> (el as? kotlinx.serialization.json.JsonPrimitive)?.content }
                } catch (_: Exception) {
                    null
                }
            }
            if (tickerCode.isNullOrBlank()) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticker code is required for Yahoo! Finance assets.")
            }
            if (!marketDataProvider.validateTicker(tickerCode)) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ticker code: $tickerCode. Could not find this symbol on Yahoo! Finance.")
            }
        }
    }
}