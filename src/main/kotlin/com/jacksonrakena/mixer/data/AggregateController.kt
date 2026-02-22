package com.jacksonrakena.mixer.data

import com.jacksonrakena.mixer.cache.RateCache
import com.jacksonrakena.mixer.upstream.CurrencyService
import io.swagger.v3.oas.annotations.Operation
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.logging.Logger
import kotlin.uuid.Uuid

@RestController
@RequestMapping("/agg")
class AggregateController(val currencyService: CurrencyService, val rateCache: RateCache) {
    companion object {
        val logger = Logger.getLogger(AggregateController::class.java.name)
    }

    @Operation(
        summary = "Get FX rate",
        description = "Gets the latest available FX rate for a currency pair.",
    )
    @GetMapping("/asset/{id}/{start}/{end}")
    fun getExchangeRate(
        @PathVariable id: String,
        @PathVariable start: String,
        @PathVariable end: String
    ): List<AssetTransactionAggregation> {
        val uuid = Uuid.parse(id)
        val sdate = LocalDate.parse(start)
        val edate = LocalDate.parse(end)
        val aggregates = transaction {
            AssetAggregate
                .selectAll()
                .where {
                    (AssetAggregate.assetId eq uuid) and
                    (AssetAggregate.aggregationPeriod eq AggregationPeriod.DAILY) and
                    (AssetAggregate.periodEndDate greater sdate) and
                    (AssetAggregate.periodEndDate less edate)
                }
                .toList()
        }
        return aggregates.map {
            AssetTransactionAggregation(
                it[AssetAggregate.assetId],
                it[AssetAggregate.periodEndDate].atStartOfDayIn(TimeZone.currentSystemDefault()),
                it[AssetAggregate.totalValue]
            )
        }
    }
}