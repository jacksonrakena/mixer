package com.jacksonrakena.mixer.controller.admin

import com.jacksonrakena.mixer.core.requests.RecomputeUserAggregationRequest
import com.jacksonrakena.mixer.controller.auth.UserResponse
import com.jacksonrakena.mixer.data.tables.concrete.Asset
import com.jacksonrakena.mixer.data.tables.concrete.Transaction
import com.jacksonrakena.mixer.data.tables.concrete.User
import com.jacksonrakena.mixer.data.tables.concrete.UserRole
import com.jacksonrakena.mixer.data.tables.virtual.AssetAggregate
import com.jacksonrakena.mixer.data.tables.markets.ExchangeRate
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jobrunr.scheduling.JobRequestScheduler
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import kotlin.uuid.Uuid

private val logger = KotlinLogging.logger {}

@Serializable
data class AdminCreateUserRequest(
    val email: String,
    val password: String,
    val displayName: String,
    val emailVerified: Boolean = false,
)

@Serializable
data class EntityCounts(
    val users: Long,
    val assets: Long,
    val transactions: Long,
    val aggregates: Long,
    val exchangeRates: Long,
    val userRoles: Long,
)

@RestController
@RequestMapping("/admin")
class AdminController(
    private val passwordEncoder: PasswordEncoder,
    private val jobRequestScheduler: JobRequestScheduler,
) {

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

    @PostMapping("/users")
    fun createUser(@RequestBody request: AdminCreateUserRequest): UserResponse {
        if (request.email.isBlank() || !request.email.contains("@")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email")
        }
        if (request.password.length < 8) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters")
        }
        if (request.displayName.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Display name is required")
        }

        val existing = transaction {
            User.selectAll().where { User.email eq request.email.lowercase().trim() }.firstOrNull()
        }
        if (existing != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Email already in use")
        }

        val now = System.currentTimeMillis()
        val userId = transaction {
            User.insert {
                it[email] = request.email.lowercase().trim()
                it[passwordHash] = passwordEncoder.encode(request.password)!!
                it[displayName] = request.displayName.trim()
                it[emailVerified] = request.emailVerified
                it[createdAt] = now
            }[User.id]
        }

        logger.info { "Admin created user: ${request.email} (id=$userId, verified=${request.emailVerified})" }

        val user = transaction {
            User.selectAll().where { User.id eq userId }.first()
        }

        return UserResponse(
            id = userId,
            email = user[User.email],
            displayName = user[User.displayName],
            emailVerified = user[User.emailVerified],
            timezone = user[User.timezone],
            displayCurrency = user[User.displayCurrency],
            roles = emptyList(),
            createdAt = user[User.createdAt],
        )
    }

    @DeleteMapping("/users/{userId}")
    fun deleteUser(@PathVariable userId: Uuid): Map<String, Any> {
        val exists = transaction {
            User.selectAll().where { User.id eq userId }.firstOrNull()
        } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        transaction {
            // Delete aggregates for user's assets
            val assetIds = Asset.selectAll().where { Asset.ownerId eq userId }.map { it[Asset.id] }
            for (assetId in assetIds) {
                AssetAggregate.deleteWhere { AssetAggregate.assetId eq assetId }
                Transaction.deleteWhere { Transaction.assetId eq assetId }
            }
            Asset.deleteWhere { Asset.ownerId eq userId }
            UserRole.deleteWhere { UserRole.userId eq userId }
            User.deleteWhere { User.id eq userId }
        }

        logger.info { "Admin deleted user: ${exists[User.email]} (id=$userId)" }
        return mapOf("deleted" to true, "userId" to userId.toString())
    }

    @PostMapping("/aggregations/force-all")
    fun forceReaggregateAll(): Map<String, Any> {
        val userIds = transaction {
            User.selectAll().map { it[User.id] }
        }
        logger.info { "Admin force reaggregating all users (${userIds.size} users), enqueuing background jobs" }
        for (uid in userIds) {
            jobRequestScheduler.enqueue(RecomputeUserAggregationRequest(uid))
        }
        return mapOf("status" to "queued", "usersEnqueued" to userIds.size)
    }

    @GetMapping("/debug/counts")
    fun debugCounts(): EntityCounts {
        return transaction {
            EntityCounts(
                users = User.selectAll().count(),
                assets = Asset.selectAll().count(),
                transactions = Transaction.selectAll().count(),
                aggregates = AssetAggregate.selectAll().count(),
                exchangeRates = ExchangeRate.selectAll().count(),
                userRoles = UserRole.selectAll().count(),
            )
        }
    }
}
