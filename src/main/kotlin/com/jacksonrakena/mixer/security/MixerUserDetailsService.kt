package com.jacksonrakena.mixer.security

import com.jacksonrakena.mixer.data.tables.concrete.User
import com.jacksonrakena.mixer.data.tables.concrete.UserRole
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class MixerUserDetailsService : UserDetailsService {
    override fun loadUserByUsername(username: String): UserDetails {
        val user = transaction {
            User.selectAll().where { User.email eq username.lowercase().trim() }.firstOrNull()
        } ?: throw UsernameNotFoundException("User not found: $username")

        val roles = transaction {
            UserRole.selectAll().where { UserRole.userId eq user[User.id] }.map { it[UserRole.role] }
        }

        return MixerUserDetails(
            userId = user[User.id].toString(),
            email = user[User.email],
            password = user[User.passwordHash],
            authorities = roles.map { SimpleGrantedAuthority("ROLE_$it") },
        )
    }
}
