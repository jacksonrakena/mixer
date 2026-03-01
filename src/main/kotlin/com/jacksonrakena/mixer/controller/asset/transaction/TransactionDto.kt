package com.jacksonrakena.mixer.controller.asset.transaction

import com.jacksonrakena.mixer.data.aggregation.AssetTransactionType
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class TransactionDto(
    val id: Uuid,
    val assetId: Uuid,
    val type: AssetTransactionType,
    val amount: Double?,
    val value: Double?,
    val timestamp: Long
)