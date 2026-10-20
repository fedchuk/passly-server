package com.passly.security

import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

object RsaKeys {
    private const val KEY_SIZE = 2048
    private const val INVALID_KEY = "The JWT private key must be an RSA key in PKCS#8 PEM format"

    fun generate(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(KEY_SIZE) }.generateKeyPair()

    fun keyPairFromPem(pem: String): KeyPair {
        val privateKey = privateKeyFromPem(pem)
        return KeyPair(publicKeyOf(privateKey), privateKey)
    }

    fun privateKeyFromPem(pem: String): RSAPrivateCrtKey {
        val base64 = pem.replace("\\n", "\n")
            .lineSequence()
            .filterNot { it.startsWith("-----") }
            .joinToString("")
            .filterNot(Char::isWhitespace)
        val key = try {
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)))
        } catch (e: GeneralSecurityException) {
            throw IllegalArgumentException(INVALID_KEY, e)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException(INVALID_KEY, e)
        }
        return key as? RSAPrivateCrtKey ?: throw IllegalArgumentException(INVALID_KEY)
    }

    fun publicKeyOf(privateKey: RSAPrivateCrtKey): RSAPublicKey =
        KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(privateKey.modulus, privateKey.publicExponent)) as RSAPublicKey

    fun toPem(keyPair: KeyPair): String {
        val body = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(keyPair.private.encoded)
        return "-----BEGIN PRIVATE KEY-----\n$body\n-----END PRIVATE KEY-----\n"
    }
}
