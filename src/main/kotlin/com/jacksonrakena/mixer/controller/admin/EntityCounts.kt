package com.jacksonrakena.mixer.controller.admin

import kotlinx.serialization.Serializable

@Serializable
data class EntityCounts(
    val users: Long,
    val assets: Long,
    val transactions: Long,
    val aggregates: Long,
    val exchangeRates: Long,
    val userRoles: Long,
)
