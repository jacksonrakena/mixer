package com.jacksonrakena.mixer.controller.auth

import kotlinx.serialization.Serializable

@Serializable
data class UpdateProfileRequest(
    val displayName: String? = null,
    val timezone: String? = null,
    val displayCurrency: String? = null,
)
