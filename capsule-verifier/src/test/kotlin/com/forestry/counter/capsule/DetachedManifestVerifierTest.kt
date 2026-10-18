package com.forestry.counter.capsule

import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import kotlin.test.Test
import kotlin.test.assertFails

class DetachedManifestVerifierTest {
    private val key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val keyId = MessageDigest.getInstance("SHA-256").digest(key.public.encoded).joinToString("") { "%02x".format(it) }
    private fun sign(bytes: ByteArray) = Signature.getInstance("Ed25519").run { initSign(key.private); update(bytes); sign() }

    @Test fun canonicalUtf8SignatureAcceptsAccentedName() {
        val bytes = "{\"name\":\"Charente — forêt\",\"size\":123}".toByteArray()
        DetachedManifestVerifier.verify(key.public.encoded, bytes, sign(bytes), keyId)
    }
    @Test fun tamperedManifestRejected() {
        val bytes = "{\"size\":123}".toByteArray()
        assertFails { DetachedManifestVerifier.verify(key.public.encoded, "{\"size\":124}".toByteArray(), sign(bytes), keyId) }
    }
    @Test fun differentlyTrustedDerRejected() {
        val bytes = "{\"size\":123}".toByteArray()
        val other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        assertFails { DetachedManifestVerifier.verify(other.public.encoded, bytes, sign(bytes), keyId) }
    }
    @Test fun signedButNonCanonicalOrDuplicateJsonRejected() {
        for (text in listOf("{\"size\": 123}", "{\"size\":123,\"size\":124}")) {
            val bytes = text.toByteArray()
            assertFails { DetachedManifestVerifier.verify(key.public.encoded, bytes, sign(bytes), keyId) }
        }
    }
    @Test fun rejectsDeepNestingBeforeRecursiveParser() {
        val bytes = ("[".repeat(33) + "0" + "]".repeat(33)).toByteArray()
        assertFails { DetachedManifestVerifier.verify(key.public.encoded, bytes, sign(bytes), keyId) }
    }
}
