package com.passly.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("passly.jwt")
data class JwtProperties(
    val privateKey: String = "",
    val generateIfMissing: Boolean = true,
) {
    override fun toString() = "JwtProperties(privateKey=***, generateIfMissing=$generateIfMissing)"
}
