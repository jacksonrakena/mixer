package com.jacksonrakena.mixer

import com.jacksonrakena.mixer.data.AggregationService
import com.jacksonrakena.mixer.data.AssetTransaction
import com.jacksonrakena.mixer.data.AssetTransactionAggregation
import com.jacksonrakena.mixer.data.AssetTransactionSource
import com.jacksonrakena.mixer.data.AssetTransactionType
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MockAssetTransactionSource(
    val transactions: List<AssetTransaction>,
) : AssetTransactionSource {
    override suspend fun getLatestReconciliation(asset: Uuid, before: Instant): AssetTransaction? {
        return transactions.filter {
            it.assetId == asset &&
                    it.timestamp <= before
                    && it.type == AssetTransactionType.Reconciliation
        }
            .maxByOrNull { it.timestamp }
    }

    override suspend fun getTransactions(asset: Uuid, after: Instant?): Iterable<AssetTransaction> {
        return transactions.filter {
            it.assetId == asset &&
                    (after == null || it.timestamp >= after)
        }
    }

    override suspend fun getEarliestTransaction(asset: Uuid, after: Instant?): AssetTransaction? {
        return transactions.filter {
            it.assetId == asset &&
                    (after == null || it.timestamp >= after)
        }
            .minByOrNull { it.timestamp }
    }
}
class AggregationServiceTests {
    @Nested
    inner class `Forward aggregation` {
        @Test
        fun `handles a reasonable test case`() = runTest {
            val testAsset = Uuid.random()
            val ag = AggregationService()
            val now = Instant.parse("2026-02-16T23:05:37.337365Z")
            val end = (now + 1.days).toLocalDateTime(TimeZone.currentSystemDefault()).date
            val mats = MockAssetTransactionSource(
                listOf(
                    AssetTransaction(
                        assetId = testAsset,
                        timestamp = now - 5.days,
                        type = AssetTransactionType.Trade,
                        amount = 10.0,
                        value = 100.0,
                    ),
                    AssetTransaction(
                        assetId = testAsset,
                        timestamp = now - 4.days,
                        type = AssetTransactionType.Trade,
                        amount = -1.0,
                        value = 100.0,
                    ),
                    AssetTransaction(
                        assetId = testAsset,
                        timestamp = now - 3.days,
                        type = AssetTransactionType.Reconciliation,
                        amount = 11.0,
                        value = 101.0,
                    ),
                    AssetTransaction(
                        assetId = testAsset,
                        timestamp = now,
                        type = AssetTransactionType.Trade,
                        amount = 2.0,
                        value = 101.0
                    ),
                )
            )
            val result = ag.forwardAggregate(
                testAsset,
                TimeZone.currentSystemDefault(),
                mats,
                end
            )

            result shouldBe listOf(
                AssetTransactionAggregation(testAsset, date=Instant.parse("2026-02-12T12:59:00Z"), amount=10.0),
                AssetTransactionAggregation(testAsset, date=Instant.parse("2026-02-13T12:59:00Z"), amount=9.0),
                AssetTransactionAggregation(testAsset, date=Instant.parse("2026-02-14T12:59:00Z"), amount=11.0),
                AssetTransactionAggregation(testAsset, date=Instant.parse("2026-02-15T12:59:00Z"), amount=11.0),
                AssetTransactionAggregation(testAsset, date=Instant.parse("2026-02-16T12:59:00Z"), amount=11.0),
                AssetTransactionAggregation(testAsset, date=Instant.parse("2026-02-17T12:59:00Z"), amount=13.0),
                AssetTransactionAggregation(testAsset, date=Instant.parse("2026-02-18T12:59:00Z"), amount=13.0),
            )
        }

        @Test
        fun `handles only reconciliation transactions`() = runTest {
            val testAsset = Uuid.random()
            val ag = AggregationService()
            val now = Instant.parse("2026-02-16T23:05:37.337365Z")
            val end = (now + 1.days).toLocalDateTime(TimeZone.currentSystemDefault()).date
            val mats = MockAssetTransactionSource(
                listOf(
                    AssetTransaction(
                        assetId = testAsset,
                        timestamp = now - 5.days,
                        type = AssetTransactionType.Reconciliation,
                        amount = 10.0,
                        value = 100.0,
                    ),
                    AssetTransaction(
                        assetId = testAsset,
                        timestamp = now - 4.days,
                        type = AssetTransactionType.Reconciliation,
                        amount = 0.0,
                        value = 100.0,
                    ),
                    AssetTransaction(
                        assetId = testAsset,
                        timestamp = now - 3.days,
                        type = AssetTransactionType.Reconciliation,
                        amount = 11.0,
                        value = 101.0,
                    ),
                    AssetTransaction(
                        assetId = testAsset,
                        timestamp = now,
                        type = AssetTransactionType.Reconciliation,
                        amount = 2.0,
                        value = 101.0
                    ),
                )
            )
            val result = ag.forwardAggregate(
                testAsset,
                TimeZone.currentSystemDefault(),
                mats,
                end
            )

            result shouldBe listOf(
                AssetTransactionAggregation(
                    assetId = testAsset,
                    date = Instant.parse("2026-02-12T12:59:00Z"),
                    amount = 10.0
                ),
                AssetTransactionAggregation(
                    assetId = testAsset,
                    date = Instant.parse("2026-02-13T12:59:00Z"),
                    amount = 0.0
                ),
                AssetTransactionAggregation(
                    assetId = testAsset,
                    date = Instant.parse("2026-02-14T12:59:00Z"),
                    amount = 11.0
                ),
                AssetTransactionAggregation(
                    assetId = testAsset,
                    date = Instant.parse("2026-02-15T12:59:00Z"),
                    amount = 11.0
                ),
                AssetTransactionAggregation(
                    assetId = testAsset,
                    date = Instant.parse("2026-02-16T12:59:00Z"),
                    amount = 11.0
                ),
                AssetTransactionAggregation(
                    assetId = testAsset,
                    date = Instant.parse("2026-02-17T12:59:00Z"),
                    amount = 2.0
                ),
                AssetTransactionAggregation(
                    assetId = testAsset,
                    date = Instant.parse("2026-02-18T12:59:00Z"),
                    amount = 2.0
                )
            )
        }

        @Test
        fun `handles a case where two deltas and a reconciliation happen on the same day`() = runTest {
            val testAsset = Uuid.random()
            val ag = AggregationService()
            val now = Instant.parse("2026-02-16T23:05:37.337365Z")
            val end = (now + 1.days).toLocalDateTime(TimeZone.currentSystemDefault()).date
            val mats = MockAssetTransactionSource(
                listOf(
                    AssetTransaction(
                        assetId = testAsset,
                        timestamp = now - 5.days,
                        type = AssetTransactionType.Trade,
                        amount = 10.0,
                        value = 100.0,
                    ),
                    AssetTransaction(
                        assetId = testAsset,
                        timestamp = now - 4.days,
                        type = AssetTransactionType.Trade,
                        amount = -1.0,
                        value = 100.0,
                    ),
                    AssetTransaction(
                        assetId = testAsset,
                        timestamp = now - 2.days,
                        type = AssetTransactionType.Trade,
                        amount = 2.0,
                        value = 101.0,
                    ),
                    AssetTransaction(
                        assetId = testAsset,
                        timestamp = now - 2.days + 2.minutes,
                        type = AssetTransactionType.Reconciliation,
                        amount = 11.0,
                        value = 101.0,
                    ),
                    AssetTransaction(
                        assetId = testAsset,
                        timestamp = now - 2.days + 4.minutes,
                        type = AssetTransactionType.Trade,
                        amount = 2.0,
                        value = 101.0
                    ),
                )
            )
            val result = ag.forwardAggregate(
                testAsset,
                TimeZone.currentSystemDefault(),
                mats,
                end
            )

            result shouldBe listOf(
                AssetTransactionAggregation(
                    assetId = testAsset,
                    date = Instant.parse("2026-02-12T12:59:00Z"),
                    amount = 10.0
                ),
                AssetTransactionAggregation(
                    assetId = testAsset,
                    date = Instant.parse("2026-02-13T12:59:00Z"),
                    amount = 9.0
                ),
                AssetTransactionAggregation(
                    assetId = testAsset,
                    date = Instant.parse("2026-02-14T12:59:00Z"),
                    amount = 9.0
                ),
                AssetTransactionAggregation(
                    assetId = testAsset,
                    date = Instant.parse("2026-02-15T12:59:00Z"),
                    amount = 13.0
                ),
                AssetTransactionAggregation(
                    assetId = testAsset,
                    date = Instant.parse("2026-02-16T12:59:00Z"),
                    amount = 13.0
                ),
                AssetTransactionAggregation(
                    assetId = testAsset,
                    date = Instant.parse("2026-02-17T12:59:00Z"),
                    amount = 13.0
                ),
                AssetTransactionAggregation(
                    assetId = testAsset,
                    date = Instant.parse("2026-02-18T12:59:00Z"),
                    amount = 13.0
                )
            )
        }
    }
}