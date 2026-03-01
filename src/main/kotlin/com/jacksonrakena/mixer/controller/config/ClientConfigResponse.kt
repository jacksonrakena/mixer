package com.jacksonrakena.mixer.controller.config

import kotlinx.serialization.Serializable

@Serializable
data class ClientConfigResponse(
    val currencies: List<String>,
)
