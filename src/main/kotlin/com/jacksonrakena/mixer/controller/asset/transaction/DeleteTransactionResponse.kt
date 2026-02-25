package com.jacksonrakena.mixer.controller.asset.transaction

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class DeleteTransactionResponse(
    val transactionId: Uuid,
    val assetId: Uuid,
    val deleted: Boolean,
    val jobId: Uuid,
    val staleAfter: Long
)