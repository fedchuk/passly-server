package com.passly.security

import com.passly.domain.user.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.util.UUID

class PasslyUserPrincipal(
    val userId: UUID,
    val displayName: String,
    private val email: String,
    private val passwordHash: String,
    private val enabled: Boolean,
    private val platformAdmin: Boolean,
) : UserDetails {

    override fun getUsername(): String = email

    override fun getPassword(): String = passwordHash

    override fun isEnabled(): Boolean = enabled

    override fun getAuthorities(): Collection<GrantedAuthority> =
        if (platformAdmin) listOf(SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")) else emptyList()

    override fun toString() = "PasslyUserPrincipal($userId, $email)"

    companion object {
        fun of(user: User) = PasslyUserPrincipal(
            userId = user.id.value,
            displayName = user.displayName,
            email = user.email.value,
            passwordHash = user.passwordHash,
            enabled = user.enabled,
            platformAdmin = user.platformAdmin,
        )
    }
}
