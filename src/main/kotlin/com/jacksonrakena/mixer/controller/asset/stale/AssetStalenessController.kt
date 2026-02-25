package com.jacksonrakena.mixer.controller.asset.stale

import com.jacksonrakena.mixer.data.tables.concrete.Asset
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.uuid.toKotlinUuid

@RestController
@RequestMapping("/asset/{id}/staleness")
class AssetStalenessController {
    @GetMapping
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