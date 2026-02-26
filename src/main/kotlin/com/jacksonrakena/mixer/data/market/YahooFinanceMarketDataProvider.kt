package com.jacksonrakena.mixer.data.market

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import org.springframework.stereotype.Component
import yahoofinance.YahooFinance
import yahoofinance.histquotes.Interval
import java.util.Calendar
import java.util.TimeZone

private val logger = KotlinLogging.logger {}

@Component
class YahooFinanceMarketDataProvider : MarketDataProvider {

    override fun getHistoricalPrices(
        ticker: String,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Map<LocalDate, Double> {
        val from = localDateToCalendar(startDate)
        val to = localDateToCalendar(endDate)

        logger.info { "Fetching Yahoo Finance history for $ticker from $startDate to $endDate" }

        val stock = YahooFinance.get(ticker, from, to, Interval.DAILY)
            ?: run {
                logger.warn { "Yahoo Finance returned null for ticker $ticker" }
                return emptyMap()
            }

        val history = stock.history ?: run {
            logger.warn { "No history available for ticker $ticker" }
            return emptyMap()
        }

        val prices = mutableMapOf<LocalDate, Double>()
        for (quote in history) {
            val close = quote.adjClose ?: quote.close ?: continue
            val cal = quote.date ?: continue
            val date = LocalDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
            prices[date] = close.toDouble()
        }

        logger.info { "Got ${prices.size} daily prices for $ticker" }
        return prices
    }

    private fun localDateToCalendar(date: LocalDate): Calendar {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val jd = date.toJavaLocalDate()
        cal.set(jd.year, jd.monthValue - 1, jd.dayOfMonth, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }
}
