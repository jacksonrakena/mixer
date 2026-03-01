package com.jacksonrakena.mixer.data
import com.jacksonrakena.mixer.data.AssetTransaction
import com.jacksonrakena.mixer.data.AssetTransactionAggregation
import com.jacksonrakena.mixer.data.AssetTransactionSource
import com.jacksonrakena.mixer.data.AssetTransactionType
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeExactly
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
                        value = 100.0, // $10/unit
                    ),
                    AssetTransaction(
                        assetId = testAsset,
                        timestamp = now - 4.days,
                        type = AssetTransactionType.Trade,
                        amount = -1.0,
                        value = 10.0, // $10/unit
                    ),
                    AssetTransaction(
                        assetId = testAsset,
                        timestamp = now - 3.days,
                        type = AssetTransactionType.Reconciliation,
                        amount = 11.0,
                        value = 121.0, // $11/unit
                    ),
                    AssetTransaction(
                        assetId = testAsset,
                        timestamp = now,
                        type = AssetTransactionType.Trade,
                        amount = 2.0,
                        value = 24.0 // $12/unit
                    ),
                )
            )
            val result = ag.forwardAggregate(
                testAsset,
                TimeZone.currentSystemDefault(),
                mats,
                end
            ).toList()

            // Day 1: 10 units @ $10/unit = $100
            result[0].amount shouldBeExactly 10.0
            result[0].unitPrice shouldBe 10.0
            result[0].nativeValue shouldBeExactly 100.0
            result[0].amountDeltaTrades shouldBeExactly 10.0

            // Day 2: 9 units @ $10/unit (carry-forward from sell at $10) = $90
            result[1].amount shouldBeExactly 9.0
            result[1].unitPrice shouldBe 10.0
            result[1].nativeValue shouldBeExactly 90.0

            // Day 3: reconciliation 11 units @ $11/unit = $121
            result[2].amount shouldBeExactly 11.0
            result[2].unitPrice shouldBe 11.0
            result[2].nativeValue shouldBeExactly 121.0

            // Day 4-5: carry-forward, 11 units @ $11/unit = $121
            result[3].amount shouldBeExactly 11.0
            result[3].unitPrice shouldBe 11.0
            result[3].nativeValue shouldBeExactly 121.0
            result[4].nativeValue shouldBeExactly 121.0

            // Day 6: trade +2, 13 units @ $12/unit = $156
            result[5].amount shouldBeExactly 13.0
            result[5].unitPrice shouldBe 12.0
            result[5].nativeValue shouldBeExactly 156.0

            // Day 7: carry-forward, 13 @ $12 = $156
            result[6].nativeValue shouldBeExactly 156.0
            result[6].amount shouldBeExactly 13.0

            result shouldHaveSize 7
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
                        value = 100.0, // $10/unit
                    ),
                    AssetTransaction(
                        assetId = testAsset,
                        timestamp = now - 4.days,
                        type = AssetTransactionType.Reconciliation,
                        amount = 0.0,
                        value = 0.0,
                    ),
                    AssetTransaction(
                        assetId = testAsset,
                        timestamp = now - 3.days,
                        type = AssetTransactionType.Reconciliation,
                        amount = 11.0,
                        value = 110.0, // $10/unit
                    ),
                    AssetTransaction(
                        assetId = testAsset,
                        timestamp = now,
                        type = AssetTransactionType.Reconciliation,
                        amount = 2.0,
                        value = 22.0, // $11/unit
                    ),
                )
            )
            val result = ag.forwardAggregate(
                testAsset,
                TimeZone.currentSystemDefault(),
                mats,
                end
            ).toList()

            // Day 1: 10 units @ $10/unit
            result[0].amount shouldBeExactly 10.0
            result[0].nativeValue shouldBeExactly 100.0

            // Day 2: reconciliation to 0 units, amount=0 so unitPrice unchanged (carry-forward $10)
            result[1].amount shouldBeExactly 0.0
            result[1].nativeValue shouldBeExactly 0.0

            // Day 3: reconciliation to 11 @ $10/unit = $110
            result[2].amount shouldBeExactly 11.0
            result[2].nativeValue shouldBeExactly 110.0

            // Day 4-5: carry-forward 11 @ $10/unit
            result[3].nativeValue shouldBeExactly 110.0
            result[4].nativeValue shouldBeExactly 110.0

            // Day 6: reconciliation to 2 @ $11/unit = $22
            result[5].amount shouldBeExactly 2.0
            result[5].nativeValue shouldBeExactly 22.0

            // Day 7: carry-forward
            result[6].nativeValue shouldBeExactly 22.0

            result shouldHaveSize 7
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
                        value = 100.0, // $10/unit
                    ),
                    AssetTransaction(
                        assetId = testAsset,
                        timestamp = now - 4.days,
                        type = AssetTransactionType.Trade,
                        amount = -1.0,
                        value = 10.0, // $10/unit
                    ),
                    AssetTransaction(
                        assetId = testAsset,
                        timestamp = now - 2.days,
                        type = AssetTransactionType.Trade,
                        amount = 2.0,
                        value = 20.0, // $10/unit
                    ),
                    AssetTransaction(
                        assetId = testAsset,
                        timestamp = now - 2.days + 2.minutes,
                        type = AssetTransactionType.Reconciliation,
                        amount = 11.0,
                        value = 110.0, // $10/unit
                    ),
                    AssetTransaction(
                        assetId = testAsset,
                        timestamp = now - 2.days + 4.minutes,
                        type = AssetTransactionType.Trade,
                        amount = 2.0,
                        value = 22.0, // $11/unit
                    ),
                )
            )
            val result = ag.forwardAggregate(
                testAsset,
                TimeZone.currentSystemDefault(),
                mats,
                end
            ).toList()

            // Day 1: 10 @ $10 = $100
            result[0].amount shouldBeExactly 10.0
            result[0].nativeValue shouldBeExactly 100.0

            // Day 2: 9 @ $10 = $90
            result[1].amount shouldBeExactly 9.0
            result[1].nativeValue shouldBeExactly 90.0

            // Day 3: carry-forward 9 @ $10 = $90
            result[2].amount shouldBeExactly 9.0
            result[2].nativeValue shouldBeExactly 90.0

            // Day 4: trade+2, reconciliation=11, trade+2 = 13 units, last tx @ $11/unit = $143
            result[3].amount shouldBeExactly 13.0
            result[3].unitPrice shouldBe 11.0
            result[3].nativeValue shouldBeExactly 143.0

            // Days 5-7: carry-forward 13 @ $11 = $143
            result[4].nativeValue shouldBeExactly 143.0
            result[5].nativeValue shouldBeExactly 143.0
            result[6].nativeValue shouldBeExactly 143.0

            result shouldHaveSize 7
        }
    }
}