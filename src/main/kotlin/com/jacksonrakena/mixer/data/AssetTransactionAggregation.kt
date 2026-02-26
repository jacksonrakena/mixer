package com.jacksonrakena.mixer.data

import com.jacksonrakena.mixer.data.tables.virtual.AssetAggregate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import kotlin.uuid.Uuid

@Serializable
data class AssetTransactionAggregation(
    val assetId: Uuid,
    val date: Instant,

    /** Holding amount in native asset units. */
    val amount: Double,
    val amountDeltaTrades: Double = 0.0,
    val amountDeltaReconciliation: Double = 0.0,
    val amountDeltaOther: Double = 0.0,

    /** Value in the asset's native currency (= amount for USER assets, amount × price for market assets). */
    val nativeValue: Double = 0.0,

    /** Value converted to the user's display currency, or null if no FX rate was available. */
    val displayValue: Double? = null,

    /** The currency code of the asset's native currency. */
    val nativeCurrency: String? = null,

    /** The user's display currency code. */
    val displayCurrency: String? = null,

    /** FX conversion details, or null if no conversion was needed or no rate was available. */
    val fxConversion: FxConversionInfo? = null,

    /** Per-unit market price (for market-data assets); null for manual/USER assets. */
    val unitPrice: Double? = null,

    /** The date from which the unit price was sourced (may differ from the aggregation date due to carry-forward). */
    val valueDate: LocalDate? = null,
) {
    companion object {
        fun fromResultRow(row: ResultRow, userTimezone: TimeZone): AssetTransactionAggregation {
            return AssetTransactionAggregation(
                assetId = row[AssetAggregate.assetId],
                date = row[AssetAggregate.periodEndDate].atStartOfDayIn(userTimezone).toJavaInstant().toKotlinInstant(),
                amount = row[AssetAggregate.holding],
                amountDeltaTrades = row[AssetAggregate.deltaTrades],
                amountDeltaReconciliation = row[AssetAggregate.deltaReconciliation],
                amountDeltaOther = row[AssetAggregate.deltaOther],
                nativeValue = row[AssetAggregate.totalValue],
                unitPrice = row[AssetAggregate.unitPrice],
                valueDate = row[AssetAggregate.valueDate],
            )
        }
    }
}