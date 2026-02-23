package com.jacksonrakena.mixer.data.tables.concrete

import org.jetbrains.exposed.v1.core.Table

object Asset: Table("assets") {
    val id = uuid("id").autoGenerate()
    val name = varchar("name", 255)
    val ownerId = uuid("owner_id").references(User.id)
    val currency = varchar("currency_code", 10)

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(id)
}