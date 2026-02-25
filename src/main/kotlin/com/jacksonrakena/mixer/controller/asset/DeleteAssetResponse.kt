package com.jacksonrakena.mixer.controller.asset

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class DeleteAssetResponse(
    val assetId: Uuid,
    val deleted: Boolean
)