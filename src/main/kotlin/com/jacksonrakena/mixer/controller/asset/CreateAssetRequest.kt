package com.jacksonrakena.mixer.controller.asset

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class CreateAssetRequest(
    val name: String,
    val ownerId: Uuid,
    val currency: String
)