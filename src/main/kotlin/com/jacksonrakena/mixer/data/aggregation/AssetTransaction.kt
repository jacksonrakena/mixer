package com.jacksonrakena.mixer.data.aggregation

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class AssetTransaction(
    val assetId: Uuid,
    val timestamp: Instant,
    val type: AssetTransactionType,

    val amount: Double? = null,
    val value: Double? = null,
)