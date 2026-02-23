package com.jacksonrakena.mixer.data

import com.jacksonrakena.mixer.data.tables.markets.ExchangeRate
import com.jacksonrakena.mixer.data.tables.virtual.AssetAggregate
import com.jacksonrakena.mixer.data.tables.virtual.UserAggregate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.util.logging.Logger
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import kotlin.uuid.Uuid


@Serializable
data class AssetGroupAggregation(
    val userId: Uuid,
    val date: Instant,

    val value: Double = 0.0,
    val valueDeltaCapitalGains: Double = 0.0,
    val valueDeltaTrades: Double = 0.0,
    val valueDeltaReconciliation: Double = 0.0,
    val valueDeltaOther: Double = 0.0,
) {
    companion object {
        fun fromResultRow(row: ResultRow): AssetGroupAggregation {
            return AssetGroupAggregation(
                userId = row[UserAggregate.userId],
                date = row[UserAggregate.periodEndDate].atStartOfDayIn(TimeZone.currentSystemDefault()).toJavaInstant().toKotlinInstant(),
                value = row[UserAggregate.value],
                valueDeltaCapitalGains = row[UserAggregate.deltaCapitalGains],
                valueDeltaTrades = row[UserAggregate.deltaTrades],
                valueDeltaReconciliation = row[UserAggregate.deltaReconciliation],
                valueDeltaOther = row[UserAggregate.deltaOther],
            )
        }
    }
}