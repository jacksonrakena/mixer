package com.jacksonrakena.mixer.data.tables.concrete

import org.jetbrains.exposed.v1.core.Table

object User: Table("users") {
    val id = uuid("id").autoGenerate()
    val timezone = varchar("tz", 255).default("Australia/Sydney")

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(id)
}