package com.jacksonrakena.mixer.controller.auth

import kotlinx.serialization.Serializable

@Serializable
data class SignupRequest(
    val email: String,
    val password: String,
    val displayName: String,
)