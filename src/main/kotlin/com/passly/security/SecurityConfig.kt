package com.passly.security

import com.passly.domain.user.Permission
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig {

    @Bean
    @Order(1)
    fun apiSecurityFilterChain(http: HttpSecurity, venues: VenueAuthorization): SecurityFilterChain {
        http {
            securityMatcher("/api/**")
            authorizeHttpRequests {
                authorize("/api/v1/auth/login", permitAll)
                authorize("/api/v1/auth/refresh", permitAll)
                authorize("/api/v1/auth/logout", permitAll)
                authorize("/api/v1/venues/{venueId}/staff/**", venues.requires(Permission.MANAGE_STAFF))
                authorize(HttpMethod.PUT, "/api/v1/venues/{venueId}/items/{itemId}/availability", venues.requires(Permission.MANAGE_STOP_LIST))
                authorize(HttpMethod.GET, "/api/v1/venues/{venueId}/**", venues.requires(Permission.VIEW_MENU))
                authorize("/api/v1/venues/{venueId}/**", venues.requires(Permission.EDIT_MENU))
                authorize(anyRequest, authenticated)
            }
            csrf { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            oauth2ResourceServer { jwt { } }
        }
        return http.build()
    }

    @Bean
    @Order(2)
    fun defaultSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            authorizeHttpRequests {
                authorize("/actuator/health", permitAll)
                authorize("/error", permitAll)
                authorize(anyRequest, denyAll)
            }
        }
        return http.build()
    }
}
