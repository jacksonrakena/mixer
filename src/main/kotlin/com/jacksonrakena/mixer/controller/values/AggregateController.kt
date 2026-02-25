package com.jacksonrakena.mixer.controller.values

import com.jacksonrakena.mixer.data.AggregationPeriod
import com.jacksonrakena.mixer.data.AssetTransactionAggregation
import com.jacksonrakena.mixer.data.ExchangeRateHelper
import com.jacksonrakena.mixer.data.FxConversionInfo
import com.jacksonrakena.mixer.data.tables.concrete.Asset
import com.jacksonrakena.mixer.data.tables.concrete.User
import com.jacksonrakena.mixer.data.tables.virtual.AssetAggregate
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import kotlin.uuid.Uuid

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/agg")
class AggregateController(
    val exchangeRateHelper: ExchangeRateHelper,
) {

    @Operation(
        summary = "Get asset aggregations in a date range",
        description = "Gets daily aggregations for an asset within a date range, with values converted to the user's display currency.",
    )
    @GetMapping("/asset/{id}/{start}/{end}")
    fun getAggregateValue(
        @PathVariable id: String,
        @PathVariable start: String,
        @PathVariable end: String,
        @RequestParam(required = false) displayCurrency: String? = null,
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
        val baseAggs = aggregates.map { AssetTransactionAggregation.fromResultRow(it) }
        return applyFxConversion(uuid, baseAggs, displayCurrency)
    }

    @Operation(
        summary = "Get all aggregations",
        description = "Gets all daily aggregations for an asset across its entire history, with values converted to the user's display currency.",
    )
    @GetMapping("/asset/{id}/all")
    fun getAllAggregateValues(
        @PathVariable id: String,
        @RequestParam(required = false) displayCurrency: String? = null,
    ): List<AssetTransactionAggregation> {
        val uuid = Uuid.parse(id)
        val aggregates = transaction {
            AssetAggregate
                .selectAll()
                .where {
                    (AssetAggregate.assetId eq uuid) and
                            (AssetAggregate.aggregationPeriod eq AggregationPeriod.DAILY)
                }
                .toList()
        }
        val baseAggs = aggregates.map { AssetTransactionAggregation.fromResultRow(it) }
        return applyFxConversion(uuid, baseAggs, displayCurrency)
    }

    /**
     * Resolves the user's display currency for a given asset, then bulk-loads
     * exchange rates and applies FX conversion to each aggregation point.
     */
    private fun applyFxConversion(
        assetId: Uuid,
        aggregations: List<AssetTransactionAggregation>,
        overrideDisplayCurrency: String? = null,
    ): List<AssetTransactionAggregation> {
        if (aggregations.isEmpty()) return aggregations

        // Look up the asset's native currency and its owner's display currency
        val (assetCurrency, userDisplayCurrency) = transaction {
            val asset = Asset.selectAll().where { Asset.id eq assetId }.firstOrNull()
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found")
            val user = User.selectAll().where { User.id eq asset[Asset.ownerId] }.firstOrNull()
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
            Pair(asset[Asset.currency], user[User.displayCurrency])
        }

        val targetCurrency = overrideDisplayCurrency ?: userDisplayCurrency

        // If same currency, no conversion needed
        if (assetCurrency == targetCurrency) {
            return aggregations.map { agg ->
                agg.copy(
                    displayValue = agg.nativeValue,
                    nativeCurrency = assetCurrency,
                    displayCurrency = targetCurrency,
                )
            }
        }

        // Bulk-load exchange rates for the entire date range
        val dates = aggregations.map { it.date.toLocalDateTime(TimeZone.currentSystemDefault()).date }
        val startDate = dates.min()
        val endDate = dates.max()
        val rateMap = exchangeRateHelper.findRatesInRange(assetCurrency, targetCurrency, startDate, endDate)

        return aggregations.map { agg ->
            val aggDate = agg.date.toLocalDateTime(TimeZone.currentSystemDefault()).date
            val rateLookup = rateMap[aggDate]
            if (rateLookup != null) {
                agg.copy(
                    displayValue = agg.nativeValue * rateLookup.rate,
                    nativeCurrency = assetCurrency,
                    displayCurrency = targetCurrency,
                    fxConversion = FxConversionInfo(
                        rate = rateLookup.rate,
                        fromCurrency = assetCurrency,
                        toCurrency = targetCurrency,
                        rateDate = rateLookup.actualDate,
                    ),
                )
            } else {
                agg.copy(
                    displayValue = null,
                    nativeCurrency = assetCurrency,
                    displayCurrency = targetCurrency,
                    fxConversion = null,
                )
            }
        }
    }
}