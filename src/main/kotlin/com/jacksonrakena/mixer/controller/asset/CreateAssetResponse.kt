package com.jacksonrakena.mixer.controller.asset

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class CreateAssetResponse(
    val assetId: Uuid
)

