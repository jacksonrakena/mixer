package com.jacksonrakena.mixer.controller.values

import com.jacksonrakena.mixer.controller.auth.AuthController
import com.jacksonrakena.mixer.data.aggregation.AggregationPeriod
import com.jacksonrakena.mixer.data.aggregation.AssetTransactionAggregation
import com.jacksonrakena.mixer.data.fx.ExchangeRateHelper
import com.jacksonrakena.mixer.data.fx.FxConversionInfo
import com.jacksonrakena.mixer.data.tables.concrete.Asset
import com.jacksonrakena.mixer.data.tables.concrete.User
import com.jacksonrakena.mixer.data.tables.virtual.AssetAggregate
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
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

@RestController
@RequestMapping("/agg")
class AggregateController(
    val exchangeRateHelper: ExchangeRateHelper,
) {
    private val logger = KotlinLogging.logger {}

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
                            (AssetAggregate.periodEndDate greaterEq sdate) and
                            (AssetAggregate.periodEndDate lessEq edate)
                }
                .toList()
        }
        val baseAggs = aggregates.map { AssetTransactionAggregation.fromResultRow(it) }
        return applyFxConversion(uuid, baseAggs, displayCurrency)
    }

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
                    displayCostBasis = agg.costBasis,
                    nativeCurrency = assetCurrency,
                    displayCurrency = targetCurrency,
                )
            }
        }

        // Bulk-load exchange rates for the entire date range
        val dates = aggregations.map { LocalDate.parse(it.date) }
        val startDate = dates.min()
        val endDate = dates.max()
        val rateMap = exchangeRateHelper.findRatesInRange(assetCurrency, targetCurrency, startDate, endDate)

        return aggregations.map { agg ->
            val aggDate = LocalDate.parse(agg.date)
            val rateLookup = rateMap[aggDate]
            if (rateLookup != null) {
                agg.copy(
                    displayValue = agg.nativeValue * rateLookup.rate,
                    displayCostBasis = agg.costBasis * rateLookup.rate,
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

    @GetMapping("/portfolio/{start}/{end}")
    fun getPortfolioAggregation(
        @PathVariable start: String,
        @PathVariable end: String,
        @RequestParam(required = false) displayCurrency: String? = null,
    ): List<PortfolioAggregationPoint> {
        val userId = AuthController.currentUserId()
        return buildPortfolioAggregation(userId, LocalDate.parse(start), LocalDate.parse(end), displayCurrency)
    }

    @GetMapping("/portfolio/all")
    fun getAllPortfolioAggregation(
        @RequestParam(required = false) displayCurrency: String? = null,
    ): List<PortfolioAggregationPoint> {
        val userId = AuthController.currentUserId()
        return buildPortfolioAggregation(userId, null, null, displayCurrency)
    }

    private fun buildPortfolioAggregation(
        userId: Uuid,
        startDate: LocalDate?,
        endDate: LocalDate?,
        overrideDisplayCurrency: String?,
    ): List<PortfolioAggregationPoint> {
        // Fetch all user's assets
        val assets = transaction {
            Asset.selectAll().where { Asset.ownerId eq userId }.toList()
        }
        if (assets.isEmpty()) return emptyList()

        val targetCurrency = overrideDisplayCurrency ?: transaction {
            User.selectAll().where { User.id eq userId }.first()[User.displayCurrency]
        }

        // For each asset, fetch aggregations and convert to display currency
        data class ConvertedPoint(val dateStr: String, val assetId: Uuid, val assetName: String, val nativeCurrency: String, val value: Double, val costBasis: Double)

        val allPoints = mutableListOf<ConvertedPoint>()

        for (asset in assets) {
            val assetId = asset[Asset.id]
            val assetCurrency = asset[Asset.currency]
            val assetName = asset[Asset.name]

            val aggregates = transaction {
                var condition = (AssetAggregate.assetId eq assetId) and
                        (AssetAggregate.aggregationPeriod eq AggregationPeriod.DAILY)
                if (startDate != null && endDate != null) {
                    condition = condition and (AssetAggregate.periodEndDate greaterEq startDate) and (AssetAggregate.periodEndDate lessEq endDate)
                }
                AssetAggregate.selectAll().where { condition }.toList()
            }
            if (aggregates.isEmpty()) continue

            val baseAggs = aggregates.map { AssetTransactionAggregation.fromResultRow(it) }

            if (assetCurrency == targetCurrency) {
                for (agg in baseAggs) {
                    allPoints.add(ConvertedPoint(agg.date, assetId, assetName, assetCurrency, agg.nativeValue, agg.costBasis))
                }
            } else {
                val dates = baseAggs.map { LocalDate.parse(it.date) }
                val rateMap = exchangeRateHelper.findRatesInRange(assetCurrency, targetCurrency, dates.min(), dates.max())
                for (agg in baseAggs) {
                    val aggDate = LocalDate.parse(agg.date)
                    val rate = rateMap[aggDate]
                    val convertedValue = if (rate != null) agg.nativeValue * rate.rate else agg.nativeValue
                    val convertedCostBasis = if (rate != null) agg.costBasis * rate.rate else agg.costBasis
                    allPoints.add(ConvertedPoint(agg.date, assetId, assetName, assetCurrency, convertedValue, convertedCostBasis))
                }
            }
        }

        // Group by date and sum
        return allPoints.groupBy { it.dateStr }
            .map { (date, points) ->
                PortfolioAggregationPoint(
                    date = date,
                    totalValue = points.sumOf { it.value },
                    totalCostBasis = points.sumOf { it.costBasis },
                    displayCurrency = targetCurrency,
                    assetCount = points.map { it.assetId }.distinct().size,
                    assetBreakdown = points.map { p ->
                        PortfolioAssetValue(
                            assetId = p.assetId.toString(),
                            assetName = p.assetName,
                            nativeCurrency = p.nativeCurrency,
                            value = p.value,
                            costBasis = p.costBasis,
                        )
                    },
                )
            }
            .sortedBy { it.date }
    }
}
