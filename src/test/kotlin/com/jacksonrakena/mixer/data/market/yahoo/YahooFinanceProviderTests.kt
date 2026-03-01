package com.jacksonrakena.mixer.data.market.yahoo

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class YahooFinanceProviderTests {

    /**
     * Tests that exercise JSON parsing and error handling
     * using a fake [YahooFinanceClient] that returns canned responses.
     */
    @Nested
    inner class JsonParsing {

        private fun providerWithCannedResponse(json: String): YahooFinanceProvider {
            val fakeClient = object : YahooFinanceClient() {
                override fun get(url: String, params: Map<String, String>, timeout: Int): String = json
            }
            return YahooFinanceProvider(fakeClient)
        }

        @Test
        fun `parses a valid chart response with timestamps and close prices`() {
            // Timestamps at 21:00 UTC (16:00 EST) = market close in New York
            val json = """
            {
              "chart": {
                "result": [{
                  "meta": {
                    "currency": "USD",
                    "symbol": "AAPL",
                    "exchangeTimezoneName": "America/New_York",
                    "instrumentType": "EQUITY"
                  },
                  "timestamp": [1704229200, 1704315600, 1704402000],
                  "indicators": {
                    "quote": [{
                      "open":  [185.0, 186.0, 184.0],
                      "high":  [186.0, 187.0, 185.0],
                      "low":   [184.0, 185.0, 183.0],
                      "close": [185.5, 186.5, 184.5],
                      "volume": [1000000, 1100000, 900000]
                    }],
                    "adjclose": [{
                      "adjclose": [185.0, 186.0, 184.0]
                    }]
                  }
                }],
                "error": null
              }
            }
            """.trimIndent()

            val provider = providerWithCannedResponse(json)
            val prices = provider.getHistoricalPrices(
                "AAPL",
                LocalDate(2024, 1, 2),
                LocalDate(2024, 1, 5),
            )

            prices.size shouldBe 3
            // Should use adjclose values when available
            prices[LocalDate(2024, 1, 2)] shouldBe 185.0
            prices[LocalDate(2024, 1, 3)] shouldBe 186.0
            prices[LocalDate(2024, 1, 4)] shouldBe 184.0
        }

        @Test
        fun `falls back to close when adjclose is absent`() {
            // Timestamp at 21:00 UTC (16:00 EST)
            val json = """
            {
              "chart": {
                "result": [{
                  "meta": {
                    "currency": "USD",
                    "symbol": "TEST",
                    "exchangeTimezoneName": "America/New_York",
                    "instrumentType": "EQUITY"
                  },
                  "timestamp": [1704229200],
                  "indicators": {
                    "quote": [{
                      "close": [190.5]
                    }]
                  }
                }],
                "error": null
              }
            }
            """.trimIndent()

            val prices = providerWithCannedResponse(json)
                .getHistoricalPrices("TEST", LocalDate(2024, 1, 2), LocalDate(2024, 1, 3))

            prices.size shouldBe 1
            prices[LocalDate(2024, 1, 2)] shouldBe 190.5
        }

        @Test
        fun `skips null close prices in the response`() {
            val json = """
            {
              "chart": {
                "result": [{
                  "meta": {
                    "currency": "USD",
                    "symbol": "TEST",
                    "exchangeTimezoneName": "UTC"
                  },
                  "timestamp": [1704067200, 1704153600, 1704240000],
                  "indicators": {
                    "quote": [{
                      "close": [100.0, null, 102.0]
                    }]
                  }
                }],
                "error": null
              }
            }
            """.trimIndent()

            val prices = providerWithCannedResponse(json)
                .getHistoricalPrices("TEST", LocalDate(2024, 1, 1), LocalDate(2024, 1, 4))

            prices.size shouldBe 2
            prices.keys shouldContain LocalDate(2024, 1, 1)
            prices.keys shouldContain LocalDate(2024, 1, 3)
        }

        @Test
        fun `throws YahooFinancePricesMissingException when chart error is present`() {
            val json = """
            {
              "chart": {
                "result": null,
                "error": {
                  "code": "Not Found",
                  "description": "No data found, symbol may be delisted"
                }
              }
            }
            """.trimIndent()

            assertThrows<YahooFinancePricesMissingException> {
                providerWithCannedResponse(json)
                    .getHistoricalPrices("INVALID", LocalDate(2024, 1, 1), LocalDate(2024, 1, 2))
            }
        }

        @Test
        fun `throws YahooFinancePricesMissingException when result is null`() {
            val json = """
            {
              "chart": {
                "result": null,
                "error": null
              }
            }
            """.trimIndent()

            assertThrows<YahooFinancePricesMissingException> {
                providerWithCannedResponse(json)
                    .getHistoricalPrices("EMPTY", LocalDate(2024, 1, 1), LocalDate(2024, 1, 2))
            }
        }

        @Test
        fun `throws YahooFinancePricesMissingException when timestamps are empty`() {
            val json = """
            {
              "chart": {
                "result": [{
                  "meta": { "currency": "USD", "symbol": "TEST" },
                  "timestamp": [],
                  "indicators": { "quote": [{}] }
                }],
                "error": null
              }
            }
            """.trimIndent()

            assertThrows<YahooFinancePricesMissingException> {
                providerWithCannedResponse(json)
                    .getHistoricalPrices("TEST", LocalDate(2024, 1, 1), LocalDate(2024, 1, 2))
            }
        }

        @Test
        fun `throws YahooFinanceException when response is not valid JSON`() {
            assertThrows<YahooFinanceException> {
                providerWithCannedResponse("not json at all")
                    .getHistoricalPrices("TEST", LocalDate(2024, 1, 1), LocalDate(2024, 1, 2))
            }
        }

        @Test
        fun `throws YahooFinanceException when Yahoo Finance is down`() {
            assertThrows<YahooFinanceException> {
                providerWithCannedResponse("Will be right back")
                    .getHistoricalPrices("TEST", LocalDate(2024, 1, 1), LocalDate(2024, 1, 2))
            }
        }

        @Test
        fun `converts ticker to uppercase`() {
            val json = """
            {
              "chart": {
                "result": [{
                  "meta": { "currency": "USD", "symbol": "AAPL", "exchangeTimezoneName": "UTC" },
                  "timestamp": [1704067200],
                  "indicators": { "quote": [{ "close": [150.0] }] }
                }],
                "error": null
              }
            }
            """.trimIndent()

            val prices = providerWithCannedResponse(json)
                .getHistoricalPrices("aapl", LocalDate(2024, 1, 1), LocalDate(2024, 1, 2))

            prices.size shouldBe 1
        }

        @Test
        fun `handles exchange timezone correctly for date assignment`() {
            // Timestamp 1704085200 = 2024-01-01 05:00:00 UTC = 2024-01-01 00:00:00 EST (America/New_York)
            // Should be assigned to Jan 1 in New York timezone
            val json = """
            {
              "chart": {
                "result": [{
                  "meta": { "currency": "USD", "symbol": "TEST", "exchangeTimezoneName": "America/New_York" },
                  "timestamp": [1704085200],
                  "indicators": { "quote": [{ "close": [100.0] }] }
                }],
                "error": null
              }
            }
            """.trimIndent()

            val prices = providerWithCannedResponse(json)
                .getHistoricalPrices("TEST", LocalDate(2024, 1, 1), LocalDate(2024, 1, 2))

            prices.size shouldBe 1
            prices.keys.first() shouldBe LocalDate(2024, 1, 1)
        }

        @Test
        fun `falls back to UTC when exchange timezone is missing`() {
            val json = """
            {
              "chart": {
                "result": [{
                  "meta": { "currency": "USD", "symbol": "TEST" },
                  "timestamp": [1704067200],
                  "indicators": { "quote": [{ "close": [100.0] }] }
                }],
                "error": null
              }
            }
            """.trimIndent()

            val prices = providerWithCannedResponse(json)
                .getHistoricalPrices("TEST", LocalDate(2024, 1, 1), LocalDate(2024, 1, 2))

            prices.size shouldBe 1
        }

        @Test
        fun `ignores unknown JSON fields gracefully`() {
            val json = """
            {
              "chart": {
                "result": [{
                  "meta": {
                    "currency": "USD",
                    "symbol": "TEST",
                    "exchangeTimezoneName": "UTC",
                    "someUnknownField": "value",
                    "anotherField": 42
                  },
                  "timestamp": [1704067200],
                  "indicators": {
                    "quote": [{ "close": [100.0], "unknownIndicator": [1,2,3] }]
                  }
                }],
                "error": null
              }
            }
            """.trimIndent()

            val prices = providerWithCannedResponse(json)
                .getHistoricalPrices("TEST", LocalDate(2024, 1, 1), LocalDate(2024, 1, 2))

            prices.size shouldBe 1
        }
    }

    /**
     * Integration test that hits the real Yahoo Finance API.
     * Requires network access. Tests the full flow: cookie → crumb → chart data.
     */
    @Nested
    inner class Integration {

        @Test
        fun `fetches real AAPL historical prices from Yahoo Finance`() {
            val client = YahooFinanceClient()
            val provider = YahooFinanceProvider(client)

            val startDate = LocalDate(2024, 1, 2)
            val endDate = LocalDate(2024, 1, 31)

            val prices = provider.getHistoricalPrices("AAPL", startDate, endDate)

            // January 2024 had ~21 trading days
            prices.size shouldBeGreaterThanOrEqual 15
            prices.keys.all { it >= startDate && it <= endDate } shouldBe true
            prices.values.all { it > 0.0 } shouldBe true

            // AAPL was trading around $180-$195 in Jan 2024
            prices.values.forEach { price ->
                price shouldBeGreaterThan 100.0
            }
        }

        @Test
        fun `fetches real MSFT historical prices from Yahoo Finance`() {
            val client = YahooFinanceClient()
            val provider = YahooFinanceProvider(client)

            val prices = provider.getHistoricalPrices(
                "MSFT",
                LocalDate(2024, 6, 1),
                LocalDate(2024, 6, 30),
            )

            prices.size shouldBeGreaterThanOrEqual 15
            prices.values.all { it > 0.0 } shouldBe true
        }

        @Test
        fun `fetches non-US ticker with different exchange timezone`() {
            val client = YahooFinanceClient()
            val provider = YahooFinanceProvider(client)

            // Toyota on Tokyo Stock Exchange
            val prices = provider.getHistoricalPrices(
                "7203.T",
                LocalDate(2024, 1, 4),
                LocalDate(2024, 1, 31),
            )

            prices shouldNotBe emptyMap<LocalDate, Double>()
            prices.values.all { it > 0.0 } shouldBe true
        }

        @Test
        fun `throws for an invalid ticker symbol`() {
            val client = YahooFinanceClient()
            val provider = YahooFinanceProvider(client)

            assertThrows<YahooFinanceException> {
                provider.getHistoricalPrices(
                    "ZZZZZNOTREAL123",
                    LocalDate(2024, 1, 1),
                    LocalDate(2024, 1, 31),
                )
            }
        }
    }
}
