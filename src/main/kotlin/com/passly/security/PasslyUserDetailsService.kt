package com.passly.security

import com.passly.domain.user.Email
import com.passly.domain.user.UserRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PasslyUserDetailsService(private val users: UserRepository) : UserDetailsService {

    @Transactional(readOnly = true)
    override fun loadUserByUsername(username: String): UserDetails {
        val email = runCatching { Email.of(username) }.getOrNull() ?: throw UsernameNotFoundException("Unknown user")
        val user = users.findByEmail(email) ?: throw UsernameNotFoundException("Unknown user")
        return PasslyUserPrincipal.of(user)
    }
}
