package com.jacksonrakena.mixer

import com.jacksonrakena.mixer.data.AggregationService
import com.jacksonrakena.mixer.data.AssetTransactionType
import com.jacksonrakena.mixer.data.UserAggregationManager
import com.jacksonrakena.mixer.data.market.MarketDataProvider
import com.jacksonrakena.mixer.data.tables.concrete.Asset
import com.jacksonrakena.mixer.data.tables.concrete.Transaction
import com.jacksonrakena.mixer.data.tables.concrete.User
import com.jacksonrakena.mixer.data.tables.markets.ExchangeRate
import com.jacksonrakena.mixer.data.tables.virtual.AssetAggregate
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Integration tests for [DatabaseAssetTransactionSource] and [UserAggregationManager],
 * using an in-memory H2 database.
 */
class DatabaseIntegrationTests {

    private lateinit var db: Database
    private val userId = Uuid.random()
    private val assetId = Uuid.random()
    private val otherAssetId = Uuid.random()

    private val baseTime = Instant.parse("2026-01-10T12:00:00Z")

    /** No-op provider for USER asset tests. */
    private val noOpMarketDataProvider = object : MarketDataProvider {
        override fun getHistoricalPrices(ticker: String, startDate: LocalDate, endDate: LocalDate) = emptyMap<LocalDate, Double>()
    }

    private fun createManager() = UserAggregationManager(db, AggregationService(), noOpMarketDataProvider)

    @BeforeEach
    fun setup() {
        db = Database.connect("jdbc:h2:mem:dbtest_${System.nanoTime()};DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction(db) {
            SchemaUtils.create(User, Asset, Transaction, ExchangeRate, AssetAggregate)
            User.insert {
                it[User.id] = userId
                it[User.timezone] = "UTC"
            }
            Asset.insert {
                it[Asset.id] = assetId
                it[Asset.name] = "Test Asset"
                it[Asset.ownerId] = userId
                it[Asset.currency] = "USD"
            }
            Asset.insert {
                it[Asset.id] = otherAssetId
                it[Asset.name] = "Other Asset"
                it[Asset.ownerId] = userId
                it[Asset.currency] = "AUD"
            }
        }
    }

    @AfterEach
    fun teardown() {
        transaction(db) {
            SchemaUtils.drop(AssetAggregate, Transaction, ExchangeRate, Asset, User)
        }
    }

    private fun insertTx(
        asset: Uuid,
        timestamp: Instant,
        type: AssetTransactionType,
        amount: Double?,
        value: Double?,
    ): Uuid {
        return transaction(db) {
            Transaction.insert {
                it[Transaction.assetId] = asset
                it[Transaction.timestamp] = timestamp.toEpochMilliseconds()
                it[Transaction.type] = type
                it[Transaction.amount] = amount
                it[Transaction.value] = value
            }[Transaction.id]
        }
    }

    @Nested
    inner class `DatabaseAssetTransactionSource queries` {

        private val source = com.jacksonrakena.mixer.data.UserAggregationManager.DatabaseAssetTransactionSource()

        @Test
        fun `getEarliestTransaction returns null for asset with no transactions`() = runTest {
            val result = source.getEarliestTransaction(assetId)
            result.shouldBeNull()
        }

        @Test
        fun `getEarliestTransaction returns the chronologically first transaction`() = runTest {
            insertTx(assetId, baseTime + 2.days, AssetTransactionType.Trade, 5.0, 50.0)
            insertTx(assetId, baseTime, AssetTransactionType.Trade, 10.0, 100.0)
            insertTx(assetId, baseTime + 1.days, AssetTransactionType.Trade, 3.0, 30.0)

            val result = source.getEarliestTransaction(assetId)
            result.shouldNotBeNull()
            result.amount shouldBe 10.0
        }

        @Test
        fun `getEarliestTransaction with after filter excludes earlier transactions`() = runTest {
            insertTx(assetId, baseTime, AssetTransactionType.Trade, 10.0, 100.0)
            insertTx(assetId, baseTime + 2.days, AssetTransactionType.Trade, 5.0, 50.0)
            insertTx(assetId, baseTime + 4.days, AssetTransactionType.Trade, 3.0, 30.0)

            val result = source.getEarliestTransaction(assetId, baseTime + 1.days)
            result.shouldNotBeNull()
            result.amount shouldBe 5.0
        }

        @Test
        fun `getEarliestTransaction does not return transactions from other assets`() = runTest {
            insertTx(otherAssetId, baseTime, AssetTransactionType.Trade, 99.0, 990.0)

            val result = source.getEarliestTransaction(assetId)
            result.shouldBeNull()
        }

        @Test
        fun `getTransactions returns all transactions for the asset`() = runTest {
            insertTx(assetId, baseTime, AssetTransactionType.Trade, 10.0, 100.0)
            insertTx(assetId, baseTime + 1.days, AssetTransactionType.Reconciliation, 20.0, 200.0)
            insertTx(otherAssetId, baseTime, AssetTransactionType.Trade, 99.0, 990.0)

            val result = source.getTransactions(assetId).toList()
            result shouldHaveSize 2
        }

        @Test
        fun `getTransactions returns empty for asset with no transactions`() = runTest {
            val result = source.getTransactions(assetId).toList()
            result.shouldBeEmpty()
        }

        @Test
        fun `getTransactions returns results ordered by timestamp descending`() = runTest {
            insertTx(assetId, baseTime, AssetTransactionType.Trade, 1.0, 10.0)
            insertTx(assetId, baseTime + 2.days, AssetTransactionType.Trade, 2.0, 20.0)
            insertTx(assetId, baseTime + 1.days, AssetTransactionType.Trade, 3.0, 30.0)

            val result = source.getTransactions(assetId).toList()
            result shouldHaveSize 3
            result[0].amount shouldBe 2.0 // most recent first
            result[1].amount shouldBe 3.0
            result[2].amount shouldBe 1.0
        }

        @Test
        fun `getLatestReconciliation returns null when no reconciliations exist`() = runTest {
            insertTx(assetId, baseTime, AssetTransactionType.Trade, 10.0, 100.0)
            val result = source.getLatestReconciliation(assetId)
            result.shouldBeNull()
        }

        @Test
        fun `getLatestReconciliation returns the most recent reconciliation before the cutoff`() = runTest {
            insertTx(assetId, baseTime, AssetTransactionType.Reconciliation, 10.0, 100.0)
            insertTx(assetId, baseTime + 2.days, AssetTransactionType.Reconciliation, 20.0, 200.0)
            insertTx(assetId, baseTime + 4.days, AssetTransactionType.Reconciliation, 30.0, 300.0)

            // Before should be exclusive, so baseTime + 3.days excludes the one at +4 days
            val result = source.getLatestReconciliation(assetId, baseTime + 3.days)
            result.shouldNotBeNull()
            result.amount shouldBe 20.0
        }

        @Test
        fun `getLatestReconciliation ignores trade transactions`() = runTest {
            insertTx(assetId, baseTime, AssetTransactionType.Reconciliation, 10.0, 100.0)
            insertTx(assetId, baseTime + 1.days, AssetTransactionType.Trade, 99.0, 990.0)

            val result = source.getLatestReconciliation(assetId, baseTime + 2.days)
            result.shouldNotBeNull()
            result.amount shouldBe 10.0
            result.type shouldBe AssetTransactionType.Reconciliation
        }

        @Test
        fun `handles null amount and value correctly`() = runTest {
            insertTx(assetId, baseTime, AssetTransactionType.Trade, null, null)

            val result = source.getEarliestTransaction(assetId)
            result.shouldNotBeNull()
            result.amount.shouldBeNull()
            result.value.shouldBeNull()
        }
    }

    @Nested
    inner class `UserAggregationManager` {

        @Test
        fun `regenerateAggregatesForAsset writes aggregates to database`() = runTest {
            insertTx(assetId, baseTime, AssetTransactionType.Trade, 10.0, 100.0)
            insertTx(assetId, baseTime + 1.days, AssetTransactionType.Trade, 5.0, 50.0)

            val manager = createManager()
            manager.regenerateAggregatesForAsset(assetId)

            val aggregates = transaction(db) {
                AssetAggregate.selectAll()
                    .where { AssetAggregate.assetId eq assetId }
                    .toList()
            }
            // Should have an entry for every day from Jan 10 to today (at minimum 2 entries)
            aggregates.size shouldBe aggregates.size // non-empty check
            (aggregates.size >= 2) shouldBe true
        }

        @Test
        fun `clearAggregatesForAsset removes all aggregates for the asset`() = runTest {
            insertTx(assetId, baseTime, AssetTransactionType.Trade, 10.0, 100.0)

            val manager = createManager()
            manager.regenerateAggregatesForAsset(assetId)

            // Verify something exists
            val before = transaction(db) {
                AssetAggregate.selectAll().where { AssetAggregate.assetId eq assetId }.count()
            }
            (before > 0) shouldBe true

            manager.clearAggregatesForAsset(assetId)

            val after = transaction(db) {
                AssetAggregate.selectAll().where { AssetAggregate.assetId eq assetId }.count()
            }
            after shouldBe 0
        }

        @Test
        fun `regenerateAggregatesForAsset clears old aggregates before writing new ones`() = runTest {
            insertTx(assetId, baseTime, AssetTransactionType.Trade, 10.0, 100.0)

            val manager = createManager()
            manager.regenerateAggregatesForAsset(assetId)

            val countFirst = transaction(db) {
                AssetAggregate.selectAll().where { AssetAggregate.assetId eq assetId }.count()
            }

            // Regenerate again — should not double up
            manager.regenerateAggregatesForAsset(assetId)

            val countSecond = transaction(db) {
                AssetAggregate.selectAll().where { AssetAggregate.assetId eq assetId }.count()
            }
            countSecond shouldBe countFirst
        }

        @Test
        fun `regenerateAggregatesForAsset does not affect other assets`() = runTest {
            insertTx(assetId, baseTime, AssetTransactionType.Trade, 10.0, 100.0)
            insertTx(otherAssetId, baseTime, AssetTransactionType.Trade, 99.0, 990.0)

            val manager = createManager()
            manager.regenerateAggregatesForAsset(assetId)
            manager.regenerateAggregatesForAsset(otherAssetId)

            val assetAggs = transaction(db) {
                AssetAggregate.selectAll().where { AssetAggregate.assetId eq assetId }.count()
            }
            val otherAggs = transaction(db) {
                AssetAggregate.selectAll().where { AssetAggregate.assetId eq otherAssetId }.count()
            }

            // Clear only one asset
            manager.clearAggregatesForAsset(assetId)

            val assetAggsAfter = transaction(db) {
                AssetAggregate.selectAll().where { AssetAggregate.assetId eq assetId }.count()
            }
            val otherAggsAfter = transaction(db) {
                AssetAggregate.selectAll().where { AssetAggregate.assetId eq otherAssetId }.count()
            }

            assetAggsAfter shouldBe 0
            otherAggsAfter shouldBe otherAggs
        }

        @Test
        fun `forceAggregateUserAssets regenerates aggregates for all user assets`() = runTest {
            insertTx(assetId, baseTime, AssetTransactionType.Trade, 10.0, 100.0)
            insertTx(otherAssetId, baseTime, AssetTransactionType.Trade, 20.0, 200.0)

            val manager = createManager()
            manager.forceAggregateUserAssets(userId)

            val assetAggs = transaction(db) {
                AssetAggregate.selectAll().where { AssetAggregate.assetId eq assetId }.count()
            }
            val otherAggs = transaction(db) {
                AssetAggregate.selectAll().where { AssetAggregate.assetId eq otherAssetId }.count()
            }

            (assetAggs > 0) shouldBe true
            (otherAggs > 0) shouldBe true
        }

        @Test
        fun `forceAggregateUserAssets with unknown user id produces no aggregates`() = runTest {
            val manager = createManager()
            manager.forceAggregateUserAssets(Uuid.random())

            val totalAggs = transaction(db) {
                AssetAggregate.selectAll().count()
            }
            totalAggs shouldBe 0
        }

        @Test
        fun `regenerateAggregatesForAsset with no transactions writes nothing`() = runTest {
            val manager = createManager()
            manager.regenerateAggregatesForAsset(assetId)

            val count = transaction(db) {
                AssetAggregate.selectAll().where { AssetAggregate.assetId eq assetId }.count()
            }
            count shouldBe 0
        }

        @Test
        fun `aggregates contain correct delta values`() = runTest {
            // Use 00:00 UTC so both transactions safely land on the same day in any timezone
            val day1 = Instant.parse("2026-01-10T00:00:00Z")
            val day2 = Instant.parse("2026-01-11T00:00:00Z")
            insertTx(assetId, day1, AssetTransactionType.Trade, 10.0, 100.0)
            insertTx(assetId, day2, AssetTransactionType.Reconciliation, 15.0, 150.0)
            insertTx(assetId, day2 + 1.minutes, AssetTransactionType.Trade, 3.0, 30.0)

            val manager = createManager()
            manager.regenerateAggregatesForAsset(assetId)

            val aggregates = transaction(db) {
                AssetAggregate.selectAll()
                    .where { AssetAggregate.assetId eq assetId }
                    .orderBy(AssetAggregate.periodEndDate)
                    .toList()
            }

            // First day: trade of 10 units @ $10/unit = $100
            val first = aggregates[0]
            first[AssetAggregate.totalValue] shouldBeExactly 100.0
            first[AssetAggregate.deltaTrades] shouldBeExactly 10.0
            first[AssetAggregate.deltaReconciliation] shouldBeExactly 0.0

            // Second day: reconciliation to 15 @ $10/unit, then trade +3 @ $10/unit = 18 units, $180
            val second = aggregates[1]
            second[AssetAggregate.totalValue] shouldBeExactly 180.0
            second[AssetAggregate.deltaReconciliation] shouldBeExactly 15.0
            second[AssetAggregate.deltaTrades] shouldBeExactly 3.0
        }

        @Test
        fun `regenerateAggregatesForAsset sets aggregatedThrough to today`() = runTest {
            insertTx(assetId, baseTime, AssetTransactionType.Trade, 10.0, 100.0)

            val manager = createManager()
            manager.regenerateAggregatesForAsset(assetId)

            val aggregatedThrough = transaction(db) {
                Asset.selectAll().where { Asset.id eq assetId }.first()[Asset.aggregatedThrough]
            }
            val today = kotlinx.datetime.Clock.System.now()
                .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
            aggregatedThrough shouldBe today
        }

        @Test
        fun `aggregatedThrough is null before any aggregation`() = runTest {
            val aggregatedThrough = transaction(db) {
                Asset.selectAll().where { Asset.id eq assetId }.first()[Asset.aggregatedThrough]
            }
            aggregatedThrough.shouldBeNull()
        }

        @Test
        fun `ensureAllAggregationsUpToDate regenerates assets with null aggregatedThrough`() = runTest {
            insertTx(assetId, baseTime, AssetTransactionType.Trade, 10.0, 100.0)

            val manager = createManager()
            manager.ensureAllAggregationsUpToDate()

            val count = transaction(db) {
                AssetAggregate.selectAll().where { AssetAggregate.assetId eq assetId }.count()
            }
            (count > 0) shouldBe true

            val aggregatedThrough = transaction(db) {
                Asset.selectAll().where { Asset.id eq assetId }.first()[Asset.aggregatedThrough]
            }
            aggregatedThrough.shouldNotBeNull()
        }

        @Test
        fun `ensureAllAggregationsUpToDate skips assets already aggregated through today`() = runTest {
            insertTx(assetId, baseTime, AssetTransactionType.Trade, 10.0, 100.0)

            val manager = createManager()
            // First call aggregates
            manager.regenerateAggregatesForAsset(assetId)

            val countAfterFirst = transaction(db) {
                AssetAggregate.selectAll().where { AssetAggregate.assetId eq assetId }.count()
            }

            // Manually verify aggregatedThrough is today
            val today = kotlinx.datetime.Clock.System.now()
                .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
            val aggregatedThrough = transaction(db) {
                Asset.selectAll().where { Asset.id eq assetId }.first()[Asset.aggregatedThrough]
            }
            aggregatedThrough shouldBe today

            // ensureAllAggregationsUpToDate should be a no-op since already up-to-date
            manager.ensureAllAggregationsUpToDate()

            val countAfterEnsure = transaction(db) {
                AssetAggregate.selectAll().where { AssetAggregate.assetId eq assetId }.count()
            }
            countAfterEnsure shouldBe countAfterFirst
        }

        @Test
        fun `ensureAllAggregationsUpToDate handles assets with no transactions gracefully`() = runTest {
            // assetId has no transactions
            val manager = createManager()
            manager.ensureAllAggregationsUpToDate()

            val count = transaction(db) {
                AssetAggregate.selectAll().where { AssetAggregate.assetId eq assetId }.count()
            }
            count shouldBe 0
        }

        @Test
        fun `ensureAllAggregationsUpToDate refreshes multiple stale assets`() = runTest {
            insertTx(assetId, baseTime, AssetTransactionType.Trade, 10.0, 100.0)
            insertTx(otherAssetId, baseTime, AssetTransactionType.Trade, 20.0, 200.0)

            val manager = createManager()
            manager.ensureAllAggregationsUpToDate()

            val assetAggs = transaction(db) {
                AssetAggregate.selectAll().where { AssetAggregate.assetId eq assetId }.count()
            }
            val otherAggs = transaction(db) {
                AssetAggregate.selectAll().where { AssetAggregate.assetId eq otherAssetId }.count()
            }

            (assetAggs > 0) shouldBe true
            (otherAggs > 0) shouldBe true
        }
    }
}
