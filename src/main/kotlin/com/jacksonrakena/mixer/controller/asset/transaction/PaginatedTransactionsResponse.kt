package com.jacksonrakena.mixer.controller.asset.transaction

import kotlinx.serialization.Serializable

@Serializable
data class PaginatedTransactionsResponse(
    val transactions: List<TransactionDto>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)