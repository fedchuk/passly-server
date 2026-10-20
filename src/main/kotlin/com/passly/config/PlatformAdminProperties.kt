package com.passly.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("passly.platform-admin")
data class PlatformAdminProperties(
    val email: String = "",
    val name: String = "Platform admin",
    val password: String = "",
) {
    val enabled: Boolean get() = email.isNotBlank()

    override fun toString() = "PlatformAdminProperties(email=$email, name=$name, password=***)"
}
