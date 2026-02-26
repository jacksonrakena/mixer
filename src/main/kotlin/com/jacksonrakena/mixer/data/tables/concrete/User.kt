package com.jacksonrakena.mixer.data.tables.concrete

import org.jetbrains.exposed.v1.core.Table

object User: Table("users") {
    val id = uuid("id").autoGenerate()
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val displayName = varchar("display_name", 255).default("")
    val emailVerified = bool("email_verified").default(false)
    val emailVerificationToken = varchar("email_verification_token", 255).nullable()
    val timezone = varchar("tz", 255).default("Australia/Sydney")
    val displayCurrency = varchar("display_currency", 10).default("AUD")
    val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(id)
}