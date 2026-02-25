package com.jacksonrakena.mixer.data

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface AssetTransactionSource {
    suspend fun getLatestReconciliation(
        asset: Uuid,
        before: Instant = Clock.System.now()
    ): AssetTransaction?

    suspend fun getTransactions(
        asset: Uuid,
        after: Instant? = null,
    ) : Iterable<AssetTransaction>

    suspend fun getEarliestTransaction(
        asset: Uuid,
        after: Instant? = null,
    ) : AssetTransaction?
}