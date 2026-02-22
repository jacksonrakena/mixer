package com.jacksonrakena.mixer.data

import kotlinx.datetime.TimeZone
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.date
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object ExchangeRate: Table("exchange_rates") {
    val base = varchar("base", 4)
    val counter = varchar("counter", 4)
    val referenceDate = date("reference_date")

    val rate = double("rate")

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(referenceDate, base, counter)
}

data class UserDto(
    val id: Uuid,
    val timezone: TimeZone
) {
    companion object {
        fun fromEntity(row: ResultRow): UserDto {
            return UserDto(
                id = row[User.id],
                timezone = TimeZone.of(row[User.timezone])
            )
        }
    }
}

object User: Table("users") {
    val id = uuid("id").autoGenerate()
    val timezone = varchar("tz", 255).default("Australia/Sydney")

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(id)
}

object Asset: Table("assets") {
    val id = uuid("id").autoGenerate()
    val name = varchar("name", 255)
    val ownerId = uuid("owner_id").references(User.id)
    val currency = varchar("currency_code", 10)

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(id)
}

object AssetAggregate: Table("generated_asset_aggregate") {
    val assetId = uuid("asset_id").references(Asset.id)
    val aggregationPeriod = enumeration("aggregation_period", AggregationPeriod::class)


    val totalValue = double("total_value")

    // The end date of the period this aggregate represents
    val periodEndDate = date("period_end_date")

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(assetId, aggregationPeriod, periodEndDate)
}

@OptIn(ExperimentalUuidApi::class)
object Transaction: Table("translog") {
    val id = uuid("id").autoGenerate()
    val timestamp = long("timestamp").clientDefault { System.currentTimeMillis() }
    val assetId = uuid("asset_id").references(Asset.id)
    val type = enumeration("transaction_type", AssetTransactionType::class)

    val amount = double("amount").nullable()
    val value = double("value").nullable()

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(id)
}