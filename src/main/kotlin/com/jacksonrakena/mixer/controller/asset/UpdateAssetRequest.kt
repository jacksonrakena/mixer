package com.jacksonrakena.mixer.controller.asset

import kotlinx.serialization.Serializable

@Serializable
data class UpdateAssetRequest(
    val name: String? = null,
    val provider: String? = null,
    val providerData: String? = null,
)
