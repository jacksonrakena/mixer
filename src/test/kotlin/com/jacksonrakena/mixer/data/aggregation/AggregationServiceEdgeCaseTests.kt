package com.jacksonrakena.mixer.data.aggregation
import com.jacksonrakena.mixer.data.aggregation.AssetTransaction
import com.jacksonrakena.mixer.data.aggregation.AssetTransactionType
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

    @Nested
    inner class `Partial reaggregation` {

        @Test
        fun `partial from midpoint matches full aggregation for overlapping days`() = runTest {
            val asset = Uuid.random()
            val ts = Instant.parse("2026-01-01T12:00:00Z")
            val transactions = listOf(
                AssetTransaction(asset, ts, AssetTransactionType.Trade, 10.0, 100.0),           // day 1: buy 10 @ $10
                AssetTransaction(asset, ts + 2.days, AssetTransactionType.Trade, -3.0, 30.0),   // day 3: sell 3 @ $10
                AssetTransaction(asset, ts + 4.days, AssetTransactionType.Trade, 5.0, 60.0),    // day 5: buy 5 @ $12
                AssetTransaction(asset, ts + 6.days, AssetTransactionType.Trade, 2.0, 28.0),    // day 7: buy 2 @ $14
            )
            val source = MockAssetTransactionSource(transactions)
            val endDate = (ts + 7.days).toLocalDateTime(tz).date

            // Full aggregation
            val full = service.forwardAggregate(asset, tz, source, endDate).toList()

            // Partial from day 3 (index 2 in the full result)
            // State at end of day 2: holding=10, price=$10, priceDate=day1
            val partialStart = (ts + 2.days).toLocalDateTime(tz).date
            val partial = service.forwardAggregate(
                asset, tz, source, endDate,
                startOverride = partialStart,
                initialHolding = 10.0,
                initialPrice = 10.0,
                initialPriceDate = ts.toLocalDateTime(tz).date,
            ).toList()

            // Partial should produce days 3-8 (6 days), matching full[2..7]
            partial shouldHaveSize 6
            for (i in partial.indices) {
                val fullIdx = i + 2
                partial[i].amount shouldBeExactly full[fullIdx].amount
                partial[i].nativeValue shouldBeExactly full[fullIdx].nativeValue
                partial[i].amountDeltaTrades shouldBeExactly full[fullIdx].amountDeltaTrades
                partial[i].unitPrice shouldBe full[fullIdx].unitPrice
            }
        }

        @Test
        fun `partial carries forward initial price across empty days`() = runTest {
            val asset = Uuid.random()
            val ts = Instant.parse("2026-01-05T12:00:00Z")
            // No transactions in the partial window — only initial state matters
            val source = MockAssetTransactionSource(emptyList())
            val startDate = ts.toLocalDateTime(tz).date
            val endDate = (ts + 3.days).toLocalDateTime(tz).date

            val result = service.forwardAggregate(
                asset, tz, source, endDate,
                startOverride = startDate,
                initialHolding = 25.0,
                initialPrice = 8.0,
                initialPriceDate = (ts - 1.days).toLocalDateTime(tz).date,
            ).toList()

            // 4 days of carry-forward: 25 units @ $8 = $200
            result shouldHaveSize 4
            for (day in result) {
                day.amount shouldBeExactly 25.0
                day.unitPrice shouldBe 8.0
                day.nativeValue shouldBeExactly 200.0
                day.amountDeltaTrades shouldBeExactly 0.0
            }
        }

        @Test
        fun `partial after removing a transaction produces correct values`() = runTest {
            val asset = Uuid.random()
            val ts = Instant.parse("2026-01-01T12:00:00Z")

            // Original transactions: buy 10, buy 5, buy 3
            val allTransactions = listOf(
                AssetTransaction(asset, ts, AssetTransactionType.Trade, 10.0, 100.0),           // day 1: 10 @ $10
                AssetTransaction(asset, ts + 2.days, AssetTransactionType.Trade, 5.0, 50.0),    // day 3: 5 @ $10
                AssetTransaction(asset, ts + 4.days, AssetTransactionType.Trade, 3.0, 36.0),    // day 5: 3 @ $12
            )
            val endDate = (ts + 5.days).toLocalDateTime(tz).date

            // Full aggregation with all transactions (before deletion)
            val fullBefore = service.forwardAggregate(
                asset, tz, MockAssetTransactionSource(allTransactions), endDate,
            ).toList()
            // Day 5 should have holding=18, price=$12
            fullBefore[4].amount shouldBeExactly 18.0
            fullBefore[4].unitPrice shouldBe 12.0

            // Simulate removing the day-3 transaction (index 1)
            // Transactions after removal: buy 10 on day 1, buy 3 on day 5
            val afterRemoval = listOf(allTransactions[0], allTransactions[2])

            // Full rebuild after removal — ground truth
            val fullAfter = service.forwardAggregate(
                asset, tz, MockAssetTransactionSource(afterRemoval), endDate,
            ).toList()

            // Partial reaggregation from the deleted transaction's date (day 3)
            // State at end of day 2: holding=10, price=$10 (from day 1's transaction)
            val partialStart = (ts + 2.days).toLocalDateTime(tz).date
            val partial = service.forwardAggregate(
                asset, tz, MockAssetTransactionSource(afterRemoval), endDate,
                startOverride = partialStart,
                initialHolding = 10.0,
                initialPrice = 10.0,
                initialPriceDate = ts.toLocalDateTime(tz).date,
            ).toList()

            // Partial should match the full-after-removal for days 3-6
            partial shouldHaveSize 4
            for (i in partial.indices) {
                val fullIdx = i + 2 // offset into full result
                partial[i].amount shouldBeExactly fullAfter[fullIdx].amount
                partial[i].nativeValue shouldBeExactly fullAfter[fullIdx].nativeValue
                partial[i].unitPrice shouldBe fullAfter[fullIdx].unitPrice
            }

            // After removal: day 3 has no transaction, carry-forward 10 @ $10 = $100
            partial[0].amount shouldBeExactly 10.0
            partial[0].nativeValue shouldBeExactly 100.0
            // Day 5 (partial index 2): trade +3 → 13 units @ $12 = $156
            partial[2].amount shouldBeExactly 13.0
            partial[2].unitPrice shouldBe 12.0
            partial[2].nativeValue shouldBeExactly 156.0
        }

        @Test
        fun `partial with two deletes at timestamps X and Y where X is earlier aggregates from X`() = runTest {
            // Simulates: user deletes transaction Y (more recent), then X (earlier).
            // staleAfter should track the minimum (X), and partial from X should be correct.
            val asset = Uuid.random()
            val ts = Instant.parse("2026-01-01T12:00:00Z")

            val transactions = listOf(
                AssetTransaction(asset, ts, AssetTransactionType.Trade, 10.0, 100.0),            // day 1: 10 @ $10
                AssetTransaction(asset, ts + 1.days, AssetTransactionType.Trade, 5.0, 50.0),     // day 2: 5 @ $10 (X - will be deleted)
                AssetTransaction(asset, ts + 3.days, AssetTransactionType.Trade, 3.0, 36.0),     // day 4: 3 @ $12 (Y - will be deleted)
                AssetTransaction(asset, ts + 5.days, AssetTransactionType.Trade, 2.0, 28.0),     // day 6: 2 @ $14
            )
            val endDate = (ts + 6.days).toLocalDateTime(tz).date

            // Remove both X (day 2) and Y (day 4) — simulating two deletes
            val afterBothDeleted = listOf(transactions[0], transactions[3])

            // Full rebuild is ground truth
            val fullAfter = service.forwardAggregate(
                asset, tz, MockAssetTransactionSource(afterBothDeleted), endDate,
            ).toList()

            // Partial from X's date (day 2), which is earlier than Y's date (day 4)
            // State at end of day 1: holding=10, price=$10
            val staleDate = (ts + 1.days).toLocalDateTime(tz).date
            val partial = service.forwardAggregate(
                asset, tz, MockAssetTransactionSource(afterBothDeleted), endDate,
                startOverride = staleDate,
                initialHolding = 10.0,
                initialPrice = 10.0,
                initialPriceDate = ts.toLocalDateTime(tz).date,
            ).toList()

            // Partial starts from day 2, so 6 days (day 2 through day 7)
            partial shouldHaveSize 6

            // Must match full rebuild for overlapping dates
            for (i in partial.indices) {
                val fullIdx = i + 1
                partial[i].amount shouldBeExactly fullAfter[fullIdx].amount
                partial[i].nativeValue shouldBeExactly fullAfter[fullIdx].nativeValue
                partial[i].unitPrice shouldBe fullAfter[fullIdx].unitPrice
            }

            // Day 2 (partial[0]): no transaction (X was deleted), carry-forward 10 @ $10 = $100
            partial[0].amount shouldBeExactly 10.0
            partial[0].nativeValue shouldBeExactly 100.0
            // Day 4 (partial[2]): no transaction (Y was deleted), still 10 @ $10 = $100
            partial[2].amount shouldBeExactly 10.0
            partial[2].nativeValue shouldBeExactly 100.0
            // Day 6 (partial[4]): trade +2, 12 @ $14 = $168
            partial[4].amount shouldBeExactly 12.0
            partial[4].unitPrice shouldBe 14.0
            partial[4].nativeValue shouldBeExactly 168.0
        }

        @Test
        fun `partial with market prices uses market data from stale point onward`() = runTest {
            val asset = Uuid.random()
            val ts = Instant.parse("2026-01-01T12:00:00Z")
            val transactions = listOf(
                AssetTransaction(asset, ts, AssetTransactionType.Trade, 10.0, null),
                AssetTransaction(asset, ts + 4.days, AssetTransactionType.Trade, 5.0, null),
            )
            val endDate = (ts + 5.days).toLocalDateTime(tz).date
            val marketPrices = mapOf(
                ts.toLocalDateTime(tz).date to 20.0,
                (ts + 1.days).toLocalDateTime(tz).date to 21.0,
                (ts + 2.days).toLocalDateTime(tz).date to 22.0,
                (ts + 3.days).toLocalDateTime(tz).date to 23.0,
                (ts + 4.days).toLocalDateTime(tz).date to 24.0,
                (ts + 5.days).toLocalDateTime(tz).date to 25.0,
            )

            // Full aggregation
            val full = service.forwardAggregate(
                asset, tz, MockAssetTransactionSource(transactions), endDate, marketPrices,
            ).toList()

            // Partial from day 3, initial state from end of day 2: holding=10, price=$22
            val partialStart = (ts + 2.days).toLocalDateTime(tz).date
            val partial = service.forwardAggregate(
                asset, tz, MockAssetTransactionSource(transactions), endDate, marketPrices,
                startOverride = partialStart,
                initialHolding = 10.0,
                initialPrice = 22.0,
                initialPriceDate = partialStart,
            ).toList()

            partial shouldHaveSize 4
            for (i in partial.indices) {
                val fullIdx = i + 2
                partial[i].amount shouldBeExactly full[fullIdx].amount
                partial[i].nativeValue shouldBeExactly full[fullIdx].nativeValue
                partial[i].unitPrice shouldBe full[fullIdx].unitPrice
            }

            // Day 3 (partial[0]): 10 @ $22 = $220 (market price for day 3 in original is day index 2, but
            // partial starts at day 3 which is (ts+2.days). Let's verify directly.)
            partial[0].unitPrice shouldBe 22.0
            partial[0].nativeValue shouldBeExactly 220.0
            // Day 5 (partial[2]): trade +5, 15 @ $24 = $360
            partial[2].amount shouldBeExactly 15.0
            partial[2].unitPrice shouldBe 24.0
            partial[2].nativeValue shouldBeExactly 360.0
        }
    }
}
