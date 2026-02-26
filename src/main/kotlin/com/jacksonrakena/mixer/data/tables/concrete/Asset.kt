package com.jacksonrakena.mixer.data.tables.concrete

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.date

object Asset: Table("assets") {
    val id = uuid("id").autoGenerate()
    val name = varchar("name", 255)
    val ownerId = uuid("owner_id").references(User.id)
    val currency = varchar("currency_code", 10)

    /** Where the asset's market data comes from: "USER" (manual), "YFIN" (Yahoo Finance), etc. */
    val provider = varchar("provider", 20).default("USER")

    /** Provider-specific configuration as JSON (e.g. {"tickerCode":"TEAM"} for YFIN). */
    val providerData = text("provider_data").nullable().default(null)

    // The timestamp after which all aggregate data for this asset is considered stale and is awaiting
    // reaggregation.
    val staleAfter = long("stale_after").default(0L)

    // The last date (inclusive) for which aggregations have been computed.
    // Null means no aggregations exist yet.
    val aggregatedThrough = date("aggregated_through").nullable().default(null)

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(id)
}