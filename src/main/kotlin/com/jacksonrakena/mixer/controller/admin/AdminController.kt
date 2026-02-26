package com.jacksonrakena.mixer.controller.admin

import com.jacksonrakena.mixer.controller.auth.UserResponse
import com.jacksonrakena.mixer.data.tables.concrete.User
import com.jacksonrakena.mixer.data.tables.concrete.UserRole
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin")
class AdminController {

    @GetMapping("/users")
    fun listUsers(): List<UserResponse> {
        return transaction {
            User.selectAll().map { row ->
                val userId = row[User.id]
                val roles = UserRole.selectAll().where { UserRole.userId eq userId }
                    .map { it[UserRole.role] }
                UserResponse(
                    id = userId,
                    email = row[User.email],
                    displayName = row[User.displayName],
                    emailVerified = row[User.emailVerified],
                    timezone = row[User.timezone],
                    displayCurrency = row[User.displayCurrency],
                    roles = roles,
                    createdAt = row[User.createdAt],
                )
            }
        }
    }
}
