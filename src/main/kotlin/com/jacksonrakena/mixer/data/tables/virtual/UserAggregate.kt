package com.jacksonrakena.mixer.data.tables.virtual

import com.jacksonrakena.mixer.data.AggregationPeriod
import com.jacksonrakena.mixer.data.tables.concrete.User
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.date

object UserAggregate: Table("virtual_user_agg") {
    val userId = uuid("user_id").references(User.id)
    val aggregationPeriod = enumeration("aggregation_period", AggregationPeriod::class)
    val periodEndDate = date("period_end_date")

    val created = long("created").clientDefault { System.currentTimeMillis() }

    val value = double("value")

    val deltaCapitalGains = double("delta_capital_gains").default(0.0)
    val deltaReconciliation = double("delta_reconciliation").default(0.0)
    val deltaTrades = double("delta_trades").default(0.0)
    val deltaOther = double("delta_other").default(0.0)

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(userId, aggregationPeriod, periodEndDate)
}