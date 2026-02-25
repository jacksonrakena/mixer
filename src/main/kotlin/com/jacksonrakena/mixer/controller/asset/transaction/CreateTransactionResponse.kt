package com.jacksonrakena.mixer.controller.asset.transaction

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class CreateTransactionResponse(
    val transactionId: Uuid,
    val assetId: Uuid,
    val jobId: Uuid,
    val staleAfter: Long
)