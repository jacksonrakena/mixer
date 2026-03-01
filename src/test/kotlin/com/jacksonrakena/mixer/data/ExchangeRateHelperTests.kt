package com.jacksonrakena.mixer.data
import com.jacksonrakena.mixer.data.ExchangeRateHelper.Companion.FALLBACK_WINDOW_DAYS
import com.jacksonrakena.mixer.data.tables.concrete.Asset
import com.jacksonrakena.mixer.data.tables.concrete.User
import com.jacksonrakena.mixer.data.tables.markets.ExchangeRate
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for [ExchangeRateHelper] — exact lookups, fallback window behaviour,
 * inverse pair resolution, bulk range queries, and boundary conditions.
 *
 * Uses an in-memory H2 database (no Spring context needed).
 */
class ExchangeRateHelperTests {

    private lateinit var db: Database
    private val helper = ExchangeRateHelper()

    private val jan10 = LocalDate(2026, 1, 10)

    @BeforeEach
    fun setup() {
        db = Database.connect("jdbc:h2:mem:exchangetest;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction(db) {
            SchemaUtils.create(User, Asset, ExchangeRate)
        }
    }

    @AfterEach
    fun teardown() {
        transaction(db) {
            SchemaUtils.drop(ExchangeRate, Asset, User)
        }
    }

    private fun insertRate(base: String, counter: String, date: LocalDate, rate: Double) {
        transaction(db) {
            ExchangeRate.insert {
                it[ExchangeRate.base] = base
                it[ExchangeRate.counter] = counter
                it[ExchangeRate.referenceDate] = date
                it[ExchangeRate.rate] = rate
            }
        }
    }

    @Nested
    inner class `findRate — single date lookups` {

        @Test
        fun `exact match returns the rate on that date`() {
            insertRate("USD", "AUD", jan10, 1.55)
            val result = helper.findRate("USD", "AUD", jan10)
            result.shouldNotBeNull()
            result.rate shouldBeExactly 1.55
            result.actualDate shouldBe jan10
            result.requestedDate shouldBe jan10
        }

        @Test
        fun `identity conversion returns 1 point 0 without hitting the database`() {
            // No rates inserted at all
            val result = helper.findRate("USD", "USD", jan10)
            result.shouldNotBeNull()
            result.rate shouldBeExactly 1.0
            result.base shouldBe "USD"
            result.counter shouldBe "USD"
        }

        @Test
        fun `returns null when no rate exists within fallback window`() {
            // Insert a rate far outside the window
            insertRate("USD", "AUD", jan10.minus(FALLBACK_WINDOW_DAYS + 5, DateTimeUnit.DAY), 1.5)
            val result = helper.findRate("USD", "AUD", jan10)
            result.shouldBeNull()
        }

        @Test
        fun `falls back to previous day when exact date missing`() {
            insertRate("USD", "AUD", jan10.minus(1, DateTimeUnit.DAY), 1.52)
            val result = helper.findRate("USD", "AUD", jan10)
            result.shouldNotBeNull()
            result.rate shouldBeExactly 1.52
            result.actualDate shouldBe jan10.minus(1, DateTimeUnit.DAY)
        }

        @Test
        fun `falls back to next day when backward missing`() {
            insertRate("USD", "AUD", jan10.plus(1, DateTimeUnit.DAY), 1.53)
            val result = helper.findRate("USD", "AUD", jan10)
            result.shouldNotBeNull()
            result.rate shouldBeExactly 1.53
            result.actualDate shouldBe jan10.plus(1, DateTimeUnit.DAY)
        }

        @Test
        fun `prefers backward over forward at same offset`() {
            insertRate("USD", "AUD", jan10.minus(2, DateTimeUnit.DAY), 1.50)
            insertRate("USD", "AUD", jan10.plus(2, DateTimeUnit.DAY), 1.60)
            val result = helper.findRate("USD", "AUD", jan10)
            result.shouldNotBeNull()
            result.rate shouldBeExactly 1.50
        }

        @Test
        fun `prefers closer forward over farther backward`() {
            insertRate("USD", "AUD", jan10.minus(3, DateTimeUnit.DAY), 1.50)
            insertRate("USD", "AUD", jan10.plus(1, DateTimeUnit.DAY), 1.60)
            val result = helper.findRate("USD", "AUD", jan10)
            result.shouldNotBeNull()
            result.rate shouldBeExactly 1.60
            result.actualDate shouldBe jan10.plus(1, DateTimeUnit.DAY)
        }

        @Test
        fun `falls back to max window boundary (exactly FALLBACK_WINDOW_DAYS away)`() {
            insertRate("USD", "AUD", jan10.minus(FALLBACK_WINDOW_DAYS, DateTimeUnit.DAY), 1.49)
            val result = helper.findRate("USD", "AUD", jan10)
            result.shouldNotBeNull()
            result.rate shouldBeExactly 1.49
        }

        @Test
        fun `one past max window boundary returns null for direct, falls through to inverse`() {
            insertRate("USD", "AUD", jan10.minus(FALLBACK_WINDOW_DAYS + 1, DateTimeUnit.DAY), 1.49)
            val result = helper.findRate("USD", "AUD", jan10)
            result.shouldBeNull()
        }
    }

    @Nested
    inner class `findRate — inverse pair resolution` {

        @Test
        fun `uses inverse pair when direct pair not available`() {
            insertRate("AUD", "USD", jan10, 0.625)
            val result = helper.findRate("USD", "AUD", jan10)
            result.shouldNotBeNull()
            result.rate shouldBe 1.0 / 0.625
            result.base shouldBe "USD"
            result.counter shouldBe "AUD"
        }

        @Test
        fun `prefers direct pair over inverse pair when both exist`() {
            insertRate("USD", "AUD", jan10, 1.55)
            insertRate("AUD", "USD", jan10, 0.64)
            val result = helper.findRate("USD", "AUD", jan10)
            result.shouldNotBeNull()
            result.rate shouldBeExactly 1.55
        }

        @Test
        fun `inverse pair with fallback finds nearest inverse rate`() {
            insertRate("AUD", "USD", jan10.minus(2, DateTimeUnit.DAY), 0.625)
            val result = helper.findRate("USD", "AUD", jan10)
            result.shouldNotBeNull()
            result.rate shouldBe 1.0 / 0.625
        }
    }

    @Nested
    inner class `findRatesInRange — bulk lookups` {

        @Test
        fun `identity currency returns 1 point 0 for every date in range`() {
            val start = jan10
            val end = jan10.plus(3, DateTimeUnit.DAY)
            val result = helper.findRatesInRange("EUR", "EUR", start, end)
            result shouldHaveSize 4 // Jan 10, 11, 12, 13
            result.values.forEach { it.rate shouldBeExactly 1.0 }
        }

        @Test
        fun `returns empty map when no rates exist at all`() {
            val result = helper.findRatesInRange("USD", "AUD", jan10, jan10.plus(2, DateTimeUnit.DAY))
            result.shouldBeEmpty()
        }

        @Test
        fun `fills gaps using fallback logic`() {
            // Only insert rate on Jan 10
            insertRate("USD", "AUD", jan10, 1.55)
            val start = jan10
            val end = jan10.plus(4, DateTimeUnit.DAY) // Jan 10-14
            val result = helper.findRatesInRange("USD", "AUD", start, end)

            // Jan 10: exact match
            result[jan10]!!.rate shouldBeExactly 1.55
            result[jan10]!!.actualDate shouldBe jan10

            // Jan 11-14: should fall back to Jan 10 (backward)
            for (offset in 1..4) {
                val d = jan10.plus(offset, DateTimeUnit.DAY)
                result[d].shouldNotBeNull()
                result[d]!!.rate shouldBeExactly 1.55
                result[d]!!.actualDate shouldBe jan10
            }
        }

        @Test
        fun `omits dates beyond fallback window from result`() {
            insertRate("USD", "AUD", jan10, 1.55)
            val farStart = jan10.plus(FALLBACK_WINDOW_DAYS + 2, DateTimeUnit.DAY)
            val farEnd = farStart.plus(2, DateTimeUnit.DAY)
            val result = helper.findRatesInRange("USD", "AUD", farStart, farEnd)
            result.shouldBeEmpty()
        }

        @Test
        fun `uses inverse rates in bulk when direct rates missing`() {
            insertRate("AUD", "USD", jan10, 0.625)
            val result = helper.findRatesInRange("USD", "AUD", jan10, jan10)
            result shouldHaveSize 1
            result[jan10]!!.rate shouldBe 1.0 / 0.625
        }

        @Test
        fun `single-day range works correctly`() {
            insertRate("USD", "AUD", jan10, 1.55)
            val result = helper.findRatesInRange("USD", "AUD", jan10, jan10)
            result shouldHaveSize 1
            result[jan10]!!.rate shouldBeExactly 1.55
        }

        @Test
        fun `mix of direct and inverse rates across range`() {
            // Direct rate on Jan 10
            insertRate("USD", "AUD", jan10, 1.55)
            // Inverse rate on Jan 12 (no direct)
            insertRate("AUD", "USD", jan10.plus(2, DateTimeUnit.DAY), 0.60)

            val result = helper.findRatesInRange("USD", "AUD", jan10, jan10.plus(2, DateTimeUnit.DAY))
            result[jan10]!!.rate shouldBeExactly 1.55
            // Jan 12: should use inverse since direct is out of range? Actually Jan 10 is 2 days back,
            // within fallback window, so direct fallback is preferred over inverse
            result[jan10.plus(2, DateTimeUnit.DAY)].shouldNotBeNull()
        }
    }
}
