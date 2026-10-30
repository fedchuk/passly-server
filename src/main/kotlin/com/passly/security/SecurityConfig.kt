package com.passly.security

import com.passly.domain.user.Permission
import jakarta.servlet.DispatcherType
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.util.matcher.DispatcherTypeRequestMatcher

@Configuration
class SecurityConfig {

    @Bean
    @Order(1)
    fun apiSecurityFilterChain(http: HttpSecurity, venues: VenueAuthorization): SecurityFilterChain {
        http {
            securityMatcher("/api/**")
            authorizeHttpRequests {
                authorize(DispatcherTypeRequestMatcher(DispatcherType.ASYNC), permitAll)
                authorize("/api/v1/auth/login", permitAll)
                authorize("/api/v1/auth/refresh", permitAll)
                authorize("/api/v1/auth/logout", permitAll)
                authorize(HttpMethod.GET, "/api/v1/venues/{venueId}/roles", venues.requires(Permission.MANAGE_STAFF))
                authorize("/api/v1/venues/{venueId}/roles/**", venues.requires(Permission.MANAGE_ROLES))
                authorize("/api/v1/venues/{venueId}/staff/**", venues.requires(Permission.MANAGE_STAFF))
                authorize(HttpMethod.PUT, "/api/v1/venues/{venueId}/items/{itemId}/availability", venues.requires(Permission.MANAGE_STOP_LIST))
                authorize("/api/v1/venues/{venueId}/orders/{orderId}/payments", venues.requires(Permission.CLOSE_ORDERS))
                authorize("/api/v1/venues/{venueId}/orders/{orderId}/close", venues.requires(Permission.CLOSE_ORDERS))
                authorize("/api/v1/venues/{venueId}/orders/{orderId}/discount", venues.requires(Permission.CLOSE_ORDERS))
                authorize("/api/v1/venues/{venueId}/orders/{orderId}/voucher", venues.requires(Permission.CLOSE_ORDERS))
                authorize("/api/v1/venues/{venueId}/orders/**", venues.requires(Permission.TAKE_ORDERS))
                authorize("/api/v1/venues/{venueId}/kitchen/**", venues.requires(Permission.PREPARE_ORDERS))
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
    fun webSecurityFilterChain(
        http: HttpSecurity,
        venues: VenueAuthorization,
        userDetailsService: UserDetailsService,
        @Value("\${passly.remember-me.key}") rememberMeKey: String,
    ): SecurityFilterChain {
        http {
            authorizeHttpRequests {
                authorize("/login", permitAll)
                authorize("/css/**", permitAll)
                authorize("/js/**", permitAll)
                authorize("/webjars/**", permitAll)
                authorize("/actuator/health", permitAll)
                authorize("/error", permitAll)
                authorize("/v3/api-docs", permitAll)
                authorize("/v3/api-docs/**", permitAll)
                authorize("/swagger-ui.html", permitAll)
                authorize("/swagger-ui/**", permitAll)
                authorize("/admin/venues/{venueId}/roles/**", venues.requires(Permission.MANAGE_ROLES))
                authorize("/admin/venues/{venueId}/staff/**", venues.requires(Permission.MANAGE_STAFF))
                authorize("/admin/venues/{venueId}/floor/**", venues.requires(Permission.MANAGE_TABLES))
                authorize("/admin/venues/{venueId}/settings/**", venues.requires(Permission.MANAGE_VENUE))
                authorize("/admin/venues/{venueId}/vouchers/**", venues.requires(Permission.MANAGE_VENUE))
                authorize("/admin/venues/{venueId}/**", venues.requires(Permission.EDIT_MENU))
                authorize("/service/venues/{venueId}/tables/{tableId}/payments", venues.requires(Permission.CLOSE_ORDERS))
                authorize("/service/venues/{venueId}/tables/{tableId}/close", venues.requires(Permission.CLOSE_ORDERS))
                authorize("/service/venues/{venueId}/tables/{tableId}/discount/**", venues.requires(Permission.CLOSE_ORDERS))
                authorize("/service/venues/{venueId}/tables/{tableId}/voucher/**", venues.requires(Permission.CLOSE_ORDERS))
                authorize("/service/venues/{venueId}/**", venues.requires(Permission.TAKE_ORDERS))
                authorize("/kitchen/venues/{venueId}/stop-list/**", venues.requires(Permission.MANAGE_STOP_LIST))
                authorize("/kitchen/venues/{venueId}/**", venues.requires(Permission.PREPARE_ORDERS))
                authorize("/platform/**", hasRole("PLATFORM_ADMIN"))
                authorize("/", authenticated)
                authorize("/admin", authenticated)
                authorize("/account/**", authenticated)
                authorize(anyRequest, denyAll)
            }
            formLogin {
                loginPage = "/login"
                defaultSuccessUrl("/admin", true)
            }
            rememberMe {
                key = rememberMeKey
                this.userDetailsService = userDetailsService
                tokenValiditySeconds = REMEMBER_ME_SECONDS
            }
            logout {
                logoutSuccessUrl = "/login?logout"
            }
        }
        return http.build()
    }

    private companion object {
        const val REMEMBER_ME_SECONDS = 14 * 24 * 60 * 60
    }
}
