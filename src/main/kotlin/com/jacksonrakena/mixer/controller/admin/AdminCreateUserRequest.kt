package com.jacksonrakena.mixer.controller.admin

import kotlinx.serialization.Serializable

@Serializable
data class AdminCreateUserRequest(
    val email: String,
    val password: String,
    val displayName: String,
    val emailVerified: Boolean = false,
)
