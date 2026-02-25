package com.jacksonrakena.mixer

import com.jacksonrakena.mixer.data.AggregationService
import com.jacksonrakena.mixer.data.AssetTransaction
import com.jacksonrakena.mixer.data.AssetTransactionType
import io.kotest.matchers.collections.shouldBeEmpty
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

/**
 * Edge-case and unexpected-behaviour tests for [AggregationService.forwardAggregate].
 */
class AggregationServiceEdgeCaseTests {

    private val service = AggregationService()
    private val tz = TimeZone.UTC

    private fun makeEnd(now: Instant, offsetDays: Int = 1) =
        (now + offsetDays.days).toLocalDateTime(tz).date

    @Nested
    inner class `Empty and degenerate inputs` {

        @Test
        fun `returns empty when transaction source has no transactions`() = runTest {
            val asset = Uuid.random()
            val source = MockAssetTransactionSource(emptyList())
            val result = service.forwardAggregate(asset, tz, source, makeEnd(Instant.parse("2026-01-10T00:00:00Z")))
            result.shouldBeEmpty()
        }

        @Test
        fun `returns empty when asset id does not match any transaction`() = runTest {
            val asset = Uuid.random()
            val other = Uuid.random()
            val source = MockAssetTransactionSource(
                listOf(
                    AssetTransaction(other, Instant.parse("2026-01-05T12:00:00Z"), AssetTransactionType.Trade, 10.0, 100.0)
                )
            )
            val result = service.forwardAggregate(asset, tz, source, makeEnd(Instant.parse("2026-01-10T00:00:00Z")))
            result.shouldBeEmpty()
        }

        @Test
        fun `single transaction on the end date produces exactly one aggregation point`() = runTest {
            val asset = Uuid.random()
            val ts = Instant.parse("2026-01-10T12:00:00Z")
            val source = MockAssetTransactionSource(
                listOf(AssetTransaction(asset, ts, AssetTransactionType.Trade, 5.0, 50.0))
            )
            val endDate = ts.toLocalDateTime(tz).date
            val result = service.forwardAggregate(asset, tz, source, endDate).toList()
            result shouldHaveSize 1
            result[0].amount shouldBeExactly 5.0
            result[0].amountDeltaTrades shouldBeExactly 5.0
        }
    }

    @Nested
    inner class `Null and zero amounts` {

        @Test
        fun `null amount on trade is treated as zero`() = runTest {
            val asset = Uuid.random()
            val ts = Instant.parse("2026-01-10T12:00:00Z")
            val source = MockAssetTransactionSource(
                listOf(
                    AssetTransaction(asset, ts, AssetTransactionType.Trade, amount = null, value = 100.0)
                )
            )
            val result = service.forwardAggregate(asset, tz, source, ts.toLocalDateTime(tz).date).toList()
            result shouldHaveSize 1
            result[0].amount shouldBeExactly 0.0
            result[0].amountDeltaTrades shouldBeExactly 0.0
        }

        @Test
        fun `null amount on reconciliation sets holding to zero`() = runTest {
            val asset = Uuid.random()
            val ts = Instant.parse("2026-01-10T12:00:00Z")
            val source = MockAssetTransactionSource(
                listOf(
                    AssetTransaction(asset, ts - 1.days, AssetTransactionType.Trade, 10.0, 100.0),
                    AssetTransaction(asset, ts, AssetTransactionType.Reconciliation, amount = null, value = 100.0),
                )
            )
            val result = service.forwardAggregate(asset, tz, source, ts.toLocalDateTime(tz).date).toList()
            result.last().amount shouldBeExactly 0.0
            result.last().amountDeltaReconciliation shouldBeExactly 0.0
        }

        @Test
        fun `zero-amount trade does not change holding`() = runTest {
            val asset = Uuid.random()
            val ts = Instant.parse("2026-01-10T12:00:00Z")
            val source = MockAssetTransactionSource(
                listOf(
                    AssetTransaction(asset, ts - 1.days, AssetTransactionType.Trade, 10.0, 100.0),
                    AssetTransaction(asset, ts, AssetTransactionType.Trade, 0.0, 0.0),
                )
            )
            val result = service.forwardAggregate(asset, tz, source, ts.toLocalDateTime(tz).date).toList()
            result.last().amount shouldBeExactly 10.0
            result.last().amountDeltaTrades shouldBeExactly 0.0
        }
    }

    @Nested
    inner class `Negative amounts` {

        @Test
        fun `sell more than held results in negative holding`() = runTest {
            val asset = Uuid.random()
            val ts = Instant.parse("2026-01-10T12:00:00Z")
            val source = MockAssetTransactionSource(
                listOf(
                    AssetTransaction(asset, ts - 1.days, AssetTransactionType.Trade, 5.0, 50.0),
                    AssetTransaction(asset, ts, AssetTransactionType.Trade, -10.0, 100.0),
                )
            )
            val result = service.forwardAggregate(asset, tz, source, ts.toLocalDateTime(tz).date).toList()
            result.last().amount shouldBeExactly -5.0
            result.last().amountDeltaTrades shouldBeExactly -10.0
        }

        @Test
        fun `negative reconciliation amount`() = runTest {
            val asset = Uuid.random()
            val ts = Instant.parse("2026-01-10T12:00:00Z")
            val source = MockAssetTransactionSource(
                listOf(
                    AssetTransaction(asset, ts, AssetTransactionType.Reconciliation, -3.0, 0.0),
                )
            )
            val result = service.forwardAggregate(asset, tz, source, ts.toLocalDateTime(tz).date).toList()
            result shouldHaveSize 1
            result[0].amount shouldBeExactly -3.0
        }
    }

    @Nested
    inner class `Multiple transactions same day` {

        @Test
        fun `multiple reconciliations on same day, last one wins for holding`() = runTest {
            val asset = Uuid.random()
            val ts = Instant.parse("2026-01-10T12:00:00Z")
            val source = MockAssetTransactionSource(
                listOf(
                    AssetTransaction(asset, ts, AssetTransactionType.Reconciliation, 100.0, 1000.0),
                    AssetTransaction(asset, ts + 1.minutes, AssetTransactionType.Reconciliation, 200.0, 2000.0),
                    AssetTransaction(asset, ts + 2.minutes, AssetTransactionType.Reconciliation, 50.0, 500.0),
                )
            )
            val result = service.forwardAggregate(asset, tz, source, ts.toLocalDateTime(tz).date).toList()
            result shouldHaveSize 1
            result[0].amount shouldBeExactly 50.0
            // All three reconciliation deltas accumulated
            result[0].amountDeltaReconciliation shouldBeExactly 350.0
        }

        @Test
        fun `trade after reconciliation same day, reconciliation resets deltaTrades`() = runTest {
            val asset = Uuid.random()
            val ts = Instant.parse("2026-01-10T12:00:00Z")
            val source = MockAssetTransactionSource(
                listOf(
                    AssetTransaction(asset, ts, AssetTransactionType.Trade, 10.0, 100.0),
                    AssetTransaction(asset, ts + 1.minutes, AssetTransactionType.Reconciliation, 20.0, 200.0),
                    AssetTransaction(asset, ts + 2.minutes, AssetTransactionType.Trade, 5.0, 50.0),
                )
            )
            val result = service.forwardAggregate(asset, tz, source, ts.toLocalDateTime(tz).date).toList()
            result shouldHaveSize 1
            result[0].amount shouldBeExactly 25.0
            // The reconciliation zeroes out deltaTrades, then 5 is added
            result[0].amountDeltaTrades shouldBeExactly 5.0
            result[0].amountDeltaReconciliation shouldBeExactly 20.0
        }
    }

    @Nested
    inner class `Timezone edge cases` {

        @Test
        fun `transaction at 23 59 UTC falls on that UTC day, not next day`() = runTest {
            val asset = Uuid.random()
            val ts = Instant.parse("2026-01-10T23:59:59Z")
            val source = MockAssetTransactionSource(
                listOf(AssetTransaction(asset, ts, AssetTransactionType.Trade, 7.0, 70.0))
            )
            val result = service.forwardAggregate(asset, TimeZone.UTC, source, ts.toLocalDateTime(TimeZone.UTC).date).toList()
            result shouldHaveSize 1
            result[0].amount shouldBeExactly 7.0
        }

        @Test
        fun `transaction near midnight in non-UTC timezone lands on correct day`() = runTest {
            val asset = Uuid.random()
            // 2026-01-10 at 23:30 UTC is 2026-01-11 10:30 in Australia/Sydney (AEDT +11)
            val ts = Instant.parse("2026-01-10T23:30:00Z")
            val sydneyTz = TimeZone.of("Australia/Sydney")
            val source = MockAssetTransactionSource(
                listOf(AssetTransaction(asset, ts, AssetTransactionType.Trade, 3.0, 30.0))
            )
            val expectedDay = ts.toLocalDateTime(sydneyTz).date
            val result = service.forwardAggregate(asset, sydneyTz, source, expectedDay).toList()
            result shouldHaveSize 1
            result[0].amount shouldBeExactly 3.0
            // The aggregation date should be in the Sydney date
            expectedDay.toString() shouldBe "2026-01-11"
        }
    }

    @Nested
    inner class `End date before or equal to start` {

        @Test
        fun `end date equals transaction date produces exactly one point`() = runTest {
            val asset = Uuid.random()
            val ts = Instant.parse("2026-01-10T12:00:00Z")
            val source = MockAssetTransactionSource(
                listOf(AssetTransaction(asset, ts, AssetTransactionType.Trade, 10.0, 100.0))
            )
            val txDay = ts.toLocalDateTime(tz).date
            val result = service.forwardAggregate(asset, tz, source, txDay).toList()
            result shouldHaveSize 1
        }

        @Test
        fun `end date before transaction date produces empty result from date iteration`() = runTest {
            val asset = Uuid.random()
            val ts = Instant.parse("2026-01-10T12:00:00Z")
            val source = MockAssetTransactionSource(
                listOf(AssetTransaction(asset, ts, AssetTransactionType.Trade, 10.0, 100.0))
            )
            val endBeforeTx = (ts - 5.days).toLocalDateTime(tz).date
            val result = service.forwardAggregate(asset, tz, source, endBeforeTx).toList()
            // The earliest transaction is on Jan 10 but end is Jan 5 — the date range Jan 10..Jan 5 is empty
            result.shouldBeEmpty()
        }
    }

    @Nested
    inner class `Carry-forward over gaps` {

        @Test
        fun `days with no transactions carry forward the previous holding`() = runTest {
            val asset = Uuid.random()
            val ts = Instant.parse("2026-01-01T12:00:00Z")
            val source = MockAssetTransactionSource(
                listOf(
                    AssetTransaction(asset, ts, AssetTransactionType.Trade, 10.0, 100.0),
                    AssetTransaction(asset, ts + 7.days, AssetTransactionType.Trade, 5.0, 50.0),
                )
            )
            val result = service.forwardAggregate(asset, tz, source, (ts + 7.days).toLocalDateTime(tz).date).toList()
            result shouldHaveSize 8 // Jan 1 through Jan 8
            // Days 2-7 (indices 1-6) should carry forward 10.0
            for (i in 1..6) {
                result[i].amount shouldBeExactly 10.0
                result[i].amountDeltaTrades shouldBeExactly 0.0
                result[i].amountDeltaReconciliation shouldBeExactly 0.0
            }
            // Last day has the additional trade
            result[7].amount shouldBeExactly 15.0
            result[7].amountDeltaTrades shouldBeExactly 5.0
        }
    }

    @Nested
    inner class `Many transactions stress` {

        @Test
        fun `100 trades accumulate correctly`() = runTest {
            val asset = Uuid.random()
            val baseTs = Instant.parse("2026-01-01T12:00:00Z")
            val txns = (0 until 100).map { i ->
                AssetTransaction(asset, baseTs + i.minutes, AssetTransactionType.Trade, 1.0, 10.0)
            }
            val source = MockAssetTransactionSource(txns)
            val result = service.forwardAggregate(asset, tz, source, baseTs.toLocalDateTime(tz).date).toList()
            result shouldHaveSize 1
            result[0].amount shouldBeExactly 100.0
            result[0].amountDeltaTrades shouldBeExactly 100.0
        }
    }
}
