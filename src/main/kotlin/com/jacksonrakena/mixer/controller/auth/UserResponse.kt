package com.jacksonrakena.mixer.controller.auth

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class UserResponse(
    val id: Uuid,
    val email: String,
    val displayName: String,
    val emailVerified: Boolean,
    val timezone: String,
    val displayCurrency: String,
    val roles: List<String>,
    val createdAt: Long,
)