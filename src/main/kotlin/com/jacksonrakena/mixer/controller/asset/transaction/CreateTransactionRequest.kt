package com.jacksonrakena.mixer.controller.asset.transaction

import com.jacksonrakena.mixer.data.aggregation.AssetTransactionType
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class CreateTransactionRequest(
    val type: AssetTransactionType,
    val amount: Double? = null,
    val value: Double? = null,
    val timestamp: Instant,
)