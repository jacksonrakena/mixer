package com.jacksonrakena.mixer.controller.asset.stale

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class AssetStalenessResponse(
    val assetId: Uuid,
    val staleAfter: Long
)