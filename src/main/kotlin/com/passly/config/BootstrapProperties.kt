package com.passly.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("passly.bootstrap")
data class BootstrapProperties(
    val businessName: String = "",
    val venueName: String = "",
    val ownerEmail: String = "",
    val ownerName: String = "",
    val ownerPassword: String = "",
) {
    val enabled: Boolean get() = ownerEmail.isNotBlank()

    override fun toString() = "BootstrapProperties(businessName=$businessName, venueName=$venueName, ownerEmail=$ownerEmail, ownerPassword=***)"
}
