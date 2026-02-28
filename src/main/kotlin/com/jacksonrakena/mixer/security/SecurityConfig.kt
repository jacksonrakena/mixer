package com.jacksonrakena.mixer.security

import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.session.web.http.CookieSerializer
import org.springframework.session.web.http.DefaultCookieSerializer
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig {
    @Bean
    fun cookieSerializer(): CookieSerializer {
        return DefaultCookieSerializer().apply {
            setSameSite("None")
        }
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { cors ->
                cors.configurationSource(UrlBasedCorsConfigurationSource().apply {
                    registerCorsConfiguration("/**", CorsConfiguration().apply {
                        allowedOriginPatterns = listOf("*")
                        allowedMethods = listOf("*")
                        allowedHeaders = listOf("*")
                        allowCredentials = true
                    })
                })
            }
            .authorizeHttpRequests { auth ->
                auth.requestMatchers(HttpMethod.POST, "/auth/signup", "/auth/login", "/auth/logout").permitAll()
                auth.requestMatchers(HttpMethod.GET, "/auth/verify").permitAll()
                auth.requestMatchers("/h2-console/**").permitAll()
                auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                auth.requestMatchers("/actuator/**").permitAll()
                auth.requestMatchers("/admin/**").hasRole("GLOBAL_ADMIN")
                auth.requestMatchers("/auth/**", "/asset/**", "/agg/**").authenticated()
                auth.anyRequest().permitAll()
            }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .exceptionHandling { ex ->
                ex.authenticationEntryPoint { _, response, _ ->
                    response.status = HttpServletResponse.SC_UNAUTHORIZED
                    response.contentType = "application/json"
                    response.writer.write("""{"error":"Unauthorized"}""")
                }
                ex.accessDeniedHandler { _, response, _ ->
                    response.status = HttpServletResponse.SC_FORBIDDEN
                    response.contentType = "application/json"
                    response.writer.write("""{"error":"Forbidden"}""")
                }
            }
            .sessionManagement { session ->
                session.sessionFixation { it.migrateSession() }
            }
            .headers { it.frameOptions { fo -> fo.disable() } }

        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
