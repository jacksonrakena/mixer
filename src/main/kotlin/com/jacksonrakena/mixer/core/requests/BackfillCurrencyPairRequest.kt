package com.jacksonrakena.mixer.core.requests

import com.jacksonrakena.mixer.data.tables.markets.ExchangeRate
import com.jacksonrakena.mixer.upstream.CurrencyService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import org.jobrunr.jobs.lambdas.JobRequest
import org.jobrunr.jobs.lambdas.JobRequestHandler
import org.slf4j.MDC
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

@Serializable
data class BackfillCurrencyPairRequest(val base: String, val counter: String): JobRequest {
    override fun getJobRequestHandler(): Class<out JobRequestHandler<*>?> {
        return BackfillCurrencyPairRequestHandler::class.java
    }

    @Component
    class BackfillCurrencyPairRequestHandler(
        val database: Database,
        val currencyService: CurrencyService,
    ) : JobRequestHandler<BackfillCurrencyPairRequest> {
        private val logger = KotlinLogging.logger {}

        override fun run(request: BackfillCurrencyPairRequest?) {
            if (request == null) {
                logger.warn { "Received null request for BackfillCurrencyPairRequestHandler" }
                return
            }

            MDC.put("currencyPair", "${request.base}/${request.counter}")
            try {
                // Find the latest existing rate for this pair
                val latestExisting = transaction {
                    ExchangeRate.selectAll()
                        .where { (ExchangeRate.base eq request.base) and (ExchangeRate.counter eq request.counter) }
                        .orderBy(ExchangeRate.referenceDate, SortOrder.DESC)
                        .limit(1)
                        .firstOrNull()
                        ?.get(ExchangeRate.referenceDate)
                }

                val fetchFrom: ZonedDateTime? = if (latestExisting != null) {
                    val javaDate = LocalDate.of(latestExisting.year, latestExisting.monthNumber, latestExisting.dayOfMonth)
                    val today = LocalDate.now(ZoneOffset.UTC)
                    // Find the most recent trading day (last weekday, since forex markets
                    // are closed on weekends). If the latest rate is on or after that day,
                    // there's nothing new to fetch.
                    val lastTradingDay = generateSequence(today) { it.minusDays(1) }
                        .first { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
                    if (!javaDate.isBefore(lastTradingDay)) {
//                        logger.info { "${request.base}/${request.counter} is up to date (latest: $latestExisting)" }
                        return
                    }
                    javaDate.plusDays(1).atStartOfDay(ZoneOffset.UTC)
                } else {
                    null
                }

                val rate = currencyService.getHistoricExchangeRates(Pair(request.base, request.counter), from = fetchFrom)

                val inserted = transaction {
                    var count = 0
                    for (entry in rate.rates) {
                        // Oanda daily candles start at 5 PM ET (typically 22:00 or 21:00 UTC),
                        // which falls on the previous calendar day in UTC.
                        // Add 1 day to get the actual trading day the candle represents.
                        val tradingDay = entry.key.atOffset(ZoneOffset.UTC).toLocalDate().plusDays(1).toKotlinLocalDate()
                        ExchangeRate.upsert {
                            it[base] = request.base
                            it[counter] = request.counter
                            it[referenceDate] = tradingDay
                            it[ExchangeRate.rate] = entry.value ?: 0.0
                        }
                        count++
                    }
                    count
                }

                val mode = if (fetchFrom != null) "Incremental (from $fetchFrom)" else "Full"
                logger.info { "$mode backfill for ${request.base}/${request.counter}: $inserted rates" }
            } catch (e: Exception) {
                logger.error(e) { "failed to fetch currency pair ${request.base}/${request.counter}" }
            } finally {
                MDC.remove("currencyPair")
            }
        }
    }
}