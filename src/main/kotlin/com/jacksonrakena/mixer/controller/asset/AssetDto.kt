package com.jacksonrakena.mixer.controller.asset

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class AssetDto(
    val id: Uuid,
    val name: String,
    val ownerId: Uuid,
    val currency: String,
    val staleAfter: Long = 0,
    val aggregatedThrough: String? = null,
    val provider: String = "USER",
    val providerData: String? = null,
)