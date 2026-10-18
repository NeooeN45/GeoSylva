package com.forestry.counter.capsule

import com.forestry.counter.capsule.json.CanonicalJson
import com.forestry.counter.capsule.json.JsonValue
import com.forestry.counter.capsule.json.StrictJsonParser
import java.security.MessageDigest
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo

/** Contrat packs GSIE : Ed25519 détaché, empreinte DER hex sans préfixe. */
object DetachedManifestVerifier {
    fun verify(publicKeyDer: ByteArray, manifest: ByteArray, signature: ByteArray, keyId: String) {
        require(manifest.size in 1..20_000) { "Manifeste hors limites" }
        require(signature.size == 64) { "Signature Ed25519 invalide" }
        val keyInfo = SubjectPublicKeyInfo.getInstance(publicKeyDer)
        require(keyInfo.algorithm.algorithm == ASN1ObjectIdentifier("1.3.101.112") && keyInfo.algorithm.parameters == null &&
            keyInfo.publicKeyData.bytes.size == 32 && keyInfo.encoded.contentEquals(publicKeyDer)) {
            "Clé publique Ed25519 DER invalide"
        }
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(publicKeyDer).joinToString("") { "%02x".format(it) }
        require(fingerprint == keyId) { "Clé de distribution non approuvée" }
        require(Ed25519Support.verify(Ed25519Support.parsePublicKeyDer(publicKeyDer), manifest, signature)) {
            "Signature du manifeste invalide"
        }
        requireBoundedJsonDepth(manifest, 32)
        val parsed = StrictJsonParser.parse(manifest)
        require(parsed is JsonValue.Obj && CanonicalJson.encode(parsed).contentEquals(manifest)) {
            "Manifeste non canonique"
        }
    }

    /** Borne la profondeur avant l'analyse récursive ; ignore les accolades dans les chaînes. */
    fun requireBoundedJsonDepth(bytes: ByteArray, maximum: Int = 32) {
        var depth = 0
        var quoted = false
        var escaped = false
        for (byte in bytes) {
            val c = byte.toInt().toChar()
            if (quoted) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= maximum) { "JSON trop profondément imbriqué" } }
                '}', ']' -> depth--
            }
        }
    }
}
