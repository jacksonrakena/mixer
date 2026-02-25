package com.jacksonrakena.mixer.data.tables.virtual

import com.jacksonrakena.mixer.data.AggregationPeriod
import com.jacksonrakena.mixer.data.tables.concrete.Asset
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.date

object AssetAggregate: Table("generated_asset_aggregate") {
    val assetId = uuid("asset_id").references(Asset.id)
    val aggregationPeriod = enumeration("aggregation_period", AggregationPeriod::class)
    val totalValue = double("total_value")
    // The end date of the period this aggregate represents
    val periodEndDate = date("period_end_date")

    val created = long("created").clientDefault { System.currentTimeMillis() }

    val deltaReconciliation = double("delta_reconciliation").default(0.0)
    val deltaTrades = double("delta_trades").default(0.0)
    val deltaOther = double("delta_other").default(0.0)

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(assetId, aggregationPeriod, periodEndDate)
}