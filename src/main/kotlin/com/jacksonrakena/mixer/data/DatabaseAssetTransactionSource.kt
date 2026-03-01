package com.jacksonrakena.mixer.data

import com.jacksonrakena.mixer.data.tables.concrete.Transaction
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Component
class DatabaseAssetTransactionSource : AssetTransactionSource {
    override suspend fun getLatestReconciliation(asset: Uuid, before: Instant): AssetTransaction? {
        return transaction {
            val result = Transaction.selectAll()
                .where {
                    (Transaction.assetId eq asset) and
                            (Transaction.type eq AssetTransactionType.Reconciliation) and
                            (Transaction.timestamp less before.toEpochMilliseconds())
                }
                .orderBy(Transaction.timestamp, SortOrder.DESC)
                .limit(1)
                .firstOrNull()

            if (result != null) {
                return@transaction AssetTransaction(
                    assetId = result[Transaction.assetId],
                    timestamp = Instant.Companion.fromEpochMilliseconds(result[Transaction.timestamp]),
                    type = result[Transaction.type],
                    amount = result[Transaction.amount],
                    value = result[Transaction.value]
                )
            }
            return@transaction null
        }
    }

    override suspend fun getTransactions(asset: Uuid, after: Instant?): Iterable<AssetTransaction> {
        return transaction {
            val result = if (after == null) {
                Transaction.selectAll()
                    .where {
                        (Transaction.assetId eq asset)
                    }
            } else {
                Transaction.selectAll()
                    .where {
                        (Transaction.assetId eq asset) and
                            (Transaction.timestamp greaterEq after.toEpochMilliseconds())
                    }
            }.orderBy(Transaction.timestamp, SortOrder.DESC)

            return@transaction result.map {
                AssetTransaction(
                    assetId = it[Transaction.assetId],
                    timestamp = Instant.Companion.fromEpochMilliseconds(it[Transaction.timestamp]),
                    type = it[Transaction.type],
                    amount = it[Transaction.amount],
                    value = it[Transaction.value]
                )
            }.toList()
        }.toList()
    }

    override suspend fun getEarliestTransaction(asset: Uuid, after: Instant?): AssetTransaction? {
        return transaction {
            val result = if (after == null) {
                Transaction.selectAll()
                    .where {
                        (Transaction.assetId eq asset)
                    }
            } else {
                Transaction.selectAll()
                    .where {
                        (Transaction.assetId eq asset) and
                                (Transaction.timestamp greater after.toEpochMilliseconds())
                    }
            }.orderBy(Transaction.timestamp, SortOrder.ASC)
                .limit(1)
                .firstOrNull()
            if (result != null) {
                return@transaction AssetTransaction(
                    assetId = result[Transaction.assetId],
                    timestamp = Instant.Companion.fromEpochMilliseconds(result[Transaction.timestamp]),
                    type = result[Transaction.type],
                    amount = result[Transaction.amount],
                    value = result[Transaction.value]
                )
            }
            return@transaction null
        }
    }
}
