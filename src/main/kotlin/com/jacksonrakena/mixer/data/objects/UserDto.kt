package com.jacksonrakena.mixer.data.objects

import com.jacksonrakena.mixer.data.tables.concrete.User
import kotlinx.datetime.TimeZone
import org.jetbrains.exposed.v1.core.ResultRow
import kotlin.uuid.Uuid

data class UserDto(
    val id: Uuid,
    val timezone: TimeZone
) {
    companion object {
        fun fromEntity(row: ResultRow): UserDto {
            return UserDto(
                id = row[User.id],
                timezone = TimeZone.Companion.of(row[User.timezone])
            )
        }
    }
}