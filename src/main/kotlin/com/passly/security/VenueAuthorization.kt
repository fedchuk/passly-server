package com.passly.security

import com.passly.application.access.AccessService
import com.passly.domain.user.Permission
import com.passly.domain.user.UserId
import com.passly.domain.venue.VenueId
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.authorization.AuthorizationManager
import org.springframework.security.core.Authentication
import org.springframework.security.web.access.intercept.RequestAuthorizationContext
import org.springframework.stereotype.Component
import java.util.*

@Component
class VenueAuthorization(private val access: AccessService) {

    fun requires(permission: Permission) = AuthorizationManager<RequestAuthorizationContext> { authentication, context ->
        AuthorizationDecision(isAllowed(authentication.get(), context, permission))
    }

    private fun isAllowed(authentication: Authentication?, context: RequestAuthorizationContext, permission: Permission): Boolean {
        val userId = authentication?.userIdOrNull() ?: return false
        val venueId = context.variables["venueId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return false
        return access.isAllowed(UserId(userId), VenueId(venueId), permission)
    }
}
