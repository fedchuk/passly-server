package com.passly.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.RememberMeAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.stereotype.Component

@Component
class SessionPrincipalRefresher(
    private val userDetailsService: UserDetailsService,
    @Value("\${passly.remember-me.key}") private val rememberMeKey: String,
) {
    private val contextRepository = HttpSessionSecurityContextRepository()

    fun refresh(request: HttpServletRequest, response: HttpServletResponse) {
        val current = SecurityContextHolder.getContext().authentication ?: return
        val principal = userDetailsService.loadUserByUsername(current.name)
        val context = SecurityContextHolder.createEmptyContext()
        context.setAuthentication(rebuild(current, principal))
        SecurityContextHolder.setContext(context)
        contextRepository.saveContext(context, request, response)
    }

    private fun rebuild(current: Authentication, principal: UserDetails): Authentication {
        val rebuilt: AbstractAuthenticationToken = when (current) {
            is RememberMeAuthenticationToken -> RememberMeAuthenticationToken(rememberMeKey, principal, principal.authorities)
            else -> UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities)
        }
        rebuilt.setDetails(current.details)
        return rebuilt
    }
}
