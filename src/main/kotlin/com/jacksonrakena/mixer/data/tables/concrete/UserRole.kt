package com.jacksonrakena.mixer.data.tables.concrete

import org.jetbrains.exposed.v1.core.Table

object UserRole : Table("user_roles") {
    val userId = uuid("user_id").references(User.id)
    val role = varchar("role", 50)

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(userId, role)
}
