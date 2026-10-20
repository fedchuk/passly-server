package com.passly.security

import java.security.Signature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RsaKeysTest {

    private val original = RsaKeys.generate()

    @Test
    fun `key pair survives a round trip through pem`() {
        val restored = RsaKeys.keyPairFromPem(RsaKeys.toPem(original))

        assertEquals(original.private, restored.private)
        assertEquals(original.public, restored.public)
    }

    @Test
    fun `public key derived from the private one verifies its signatures`() {
        val restored = RsaKeys.keyPairFromPem(RsaKeys.toPem(original))
        val data = "passly".toByteArray()
        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(original.private)
            update(data)
        }.sign()

        val valid = Signature.getInstance("SHA256withRSA").apply {
            initVerify(restored.public)
            update(data)
        }.verify(signature)

        assertTrue(valid)
    }

    @Test
    fun `escaped newlines from an environment variable are accepted`() {
        val oneLine = RsaKeys.toPem(original).replace("\n", "\\n")

        assertEquals(original.private, RsaKeys.privateKeyFromPem(oneLine))
    }

    @Test
    fun `garbage is rejected`() {
        assertFailsWith<IllegalArgumentException> { RsaKeys.privateKeyFromPem("not a key") }
    }
}
