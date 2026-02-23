package com.jacksonrakena.mixer.data.tables.markets

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.date

object ExchangeRate: Table("exchange_rates") {
    val base = varchar("base", 4)
    val counter = varchar("counter", 4)
    val referenceDate = date("reference_date")

    val rate = double("rate")

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(referenceDate, base, counter)
}