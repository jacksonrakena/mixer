package com.jacksonrakena.mixer.controller.asset

import kotlinx.serialization.Serializable

@Serializable
data class CreateAssetRequest(
    val name: String,
    val currency: String,
    val provider: String = "USER",
    val providerData: String? = null,
)