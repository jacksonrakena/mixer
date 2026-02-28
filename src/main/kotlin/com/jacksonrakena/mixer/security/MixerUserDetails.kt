package com.jacksonrakena.mixer.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.User as SpringUser

class MixerUserDetails(
    val userId: String,
    email: String,
    password: String,
    authorities: Collection<GrantedAuthority>,
) : SpringUser(email, password, authorities) {
    companion object {
        private const val serialVersionUID = 1L
    }
}
