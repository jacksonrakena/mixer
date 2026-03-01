package com.jacksonrakena.mixer.data.tables.concrete

import com.jacksonrakena.mixer.data.aggregation.AssetTransactionType
import org.jetbrains.exposed.v1.core.Table
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
object Transaction: Table("translog") {
    val id = uuid("id").autoGenerate()
    val timestamp = long("timestamp").clientDefault { System.currentTimeMillis() }
    val assetId = uuid("asset_id").references(Asset.id)
    val type = enumerationByName("transaction_type", 20, AssetTransactionType::class)

    val amount = double("amount").nullable()
    val value = double("value").nullable()

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(id)
}