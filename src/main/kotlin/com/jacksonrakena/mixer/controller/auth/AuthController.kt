package com.jacksonrakena.mixer.controller.auth

import com.jacksonrakena.mixer.MixerConfiguration
import com.jacksonrakena.mixer.core.requests.RecomputeUserAggregationRequest
import com.jacksonrakena.mixer.data.tables.concrete.User
import com.jacksonrakena.mixer.data.tables.concrete.UserRole
import com.jacksonrakena.mixer.security.MixerUserDetails
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jobrunr.scheduling.JobRequestScheduler
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import kotlin.uuid.Uuid

private val logger = KotlinLogging.logger {}

@Serializable
data class SignupRequest(
    val email: String,
    val password: String,
    val displayName: String,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class UserResponse(
    val id: Uuid,
    val email: String,
    val displayName: String,
    val emailVerified: Boolean,
    val timezone: String,
    val displayCurrency: String,
    val roles: List<String>,
    val createdAt: Long,
)

@Serializable
data class UpdateProfileRequest(
    val displayName: String? = null,
    val timezone: String? = null,
    val displayCurrency: String? = null,
)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
)

@RestController
@RequestMapping("/auth")
class AuthController(
    private val passwordEncoder: PasswordEncoder,
    private val authenticationManager: AuthenticationManager,
    private val jobRequestScheduler: JobRequestScheduler,
    private val mixerConfiguration: MixerConfiguration,
) {
    private val securityContextRepository = HttpSessionSecurityContextRepository()

    @PostMapping("/signup")
    fun signup(
        @RequestBody request: SignupRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): UserResponse {
        if (!mixerConfiguration.user.signup.enabled) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Signup is disabled")
        }
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

        val verificationToken = Uuid.random().toString()
        val now = System.currentTimeMillis()
        val userId = transaction {
            User.insert {
                it[email] = request.email.lowercase().trim()
                it[passwordHash] = passwordEncoder.encode(request.password)!!
                it[displayName] = request.displayName.trim()
                it[emailVerificationToken] = verificationToken
                it[createdAt] = now
            }[User.id]
        }

        logger.info { "New user signup: ${request.email} (id=$userId)" }
        logger.info { "Email verification link: /auth/verify?token=$verificationToken" }

        val auth = authenticateAndSaveSession(request.email, request.password, httpRequest, httpResponse)
        val userDetails = auth.principal as MixerUserDetails

        // Read back the created user to return actual defaults from the DB schema
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
            roles = userDetails.authorities.mapNotNull { it.authority?.removePrefix("ROLE_") },
            createdAt = user[User.createdAt],
        )
    }

    @PostMapping("/login")
    fun login(
        @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): UserResponse {
        val auth = try {
            authenticateAndSaveSession(request.email, request.password, httpRequest, httpResponse)
        } catch (e: AuthenticationException) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")
        }

        val userDetails = auth.principal as MixerUserDetails
        val userId = Uuid.parse(userDetails.userId)
        logger.info { "User logged in: ${request.email}" }

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
            roles = userDetails.authorities.mapNotNull { it.authority?.removePrefix("ROLE_") },
            createdAt = user[User.createdAt],
        )
    }

    @PostMapping("/logout")
    fun logout(httpRequest: HttpServletRequest): Map<String, String> {
        httpRequest.session.invalidate()
        SecurityContextHolder.clearContext()
        return mapOf("status" to "ok")
    }

    @GetMapping("/verify")
    fun verify(@RequestParam token: String): Map<String, String> {
        transaction {
            val user = User.selectAll().where { User.emailVerificationToken eq token }.firstOrNull()
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid verification token")
            User.update({ User.id eq user[User.id] }) {
                it[emailVerified] = true
                it[emailVerificationToken] = null
            }
        }
        return mapOf("status" to "verified")
    }

    @GetMapping("/me")
    fun me(): UserResponse {
        val userId = currentUserId()
        val user = transaction {
            User.selectAll().where { User.id eq userId }.firstOrNull()
        } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        val roles = transaction {
            UserRole.selectAll().where { UserRole.userId eq userId }.map { it[UserRole.role] }
        }

        return UserResponse(
            id = user[User.id],
            email = user[User.email],
            displayName = user[User.displayName],
            emailVerified = user[User.emailVerified],
            timezone = user[User.timezone],
            displayCurrency = user[User.displayCurrency],
            roles = roles,
            createdAt = user[User.createdAt],
        )
    }

    @PutMapping("/profile")
    fun updateProfile(@RequestBody request: UpdateProfileRequest): UserResponse {
        val userId = currentUserId()

        // Check if timezone is changing — requires full reaggregation
        val timezoneChanged = request.timezone?.let { newTz ->
            val currentTz = transaction {
                User.selectAll().where { User.id eq userId }.first()[User.timezone]
            }
            currentTz != newTz
        } ?: false

        transaction {
            User.update({ User.id eq userId }) {
                request.displayName?.let { dn -> it[displayName] = dn.trim() }
                request.timezone?.let { tz -> it[timezone] = tz }
                request.displayCurrency?.let { dc -> it[displayCurrency] = dc }
            }
        }

        if (timezoneChanged) {
            logger.info { "Timezone changed for user $userId, enqueuing reaggregation" }
            jobRequestScheduler.enqueue(RecomputeUserAggregationRequest(userId))
        }

        return me()
    }

    @PutMapping("/password")
    fun changePassword(@RequestBody request: ChangePasswordRequest): Map<String, String> {
        if (request.newPassword.length < 8) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be at least 8 characters")
        }

        val userId = currentUserId()
        val user = transaction {
            User.selectAll().where { User.id eq userId }.firstOrNull()
        } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        if (!passwordEncoder.matches(request.currentPassword, user[User.passwordHash])) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current password is incorrect")
        }

        transaction {
            User.update({ User.id eq userId }) {
                it[passwordHash] = passwordEncoder.encode(request.newPassword)!!
            }
        }

        logger.info { "Password changed for user $userId" }
        return mapOf("status" to "ok")
    }

    private fun authenticateAndSaveSession(
        email: String,
        password: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): org.springframework.security.core.Authentication {
        val auth = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(email.lowercase().trim(), password)
        )
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = auth
        SecurityContextHolder.setContext(context)
        securityContextRepository.saveContext(context, request, response)
        return auth
    }

    companion object {
        fun currentUserId(): Uuid {
            val auth = SecurityContextHolder.getContext().authentication
                ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated")
            val principal = auth.principal as? MixerUserDetails
                ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authentication")
            return Uuid.parse(principal.userId)
        }
    }
}
