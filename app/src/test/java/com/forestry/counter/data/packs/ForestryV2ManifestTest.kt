package com.forestry.counter.data.packs

import java.util.Base64
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/** Real, bounded, TESTONLY source sample; never a published department archive. */
class ForestryV2ManifestTest {
    private fun resource(name: String) = checkNotNull(javaClass.getResourceAsStream("/forestry-v2-testonly/$name")).use { it.readBytes() }
    @Test fun signed_manifest_distinguishes_retained_originals_from_usable_features() {
        val signed = Json.decodeFromString<SignedManifestDto>(resource("manifest.json").toString(Charsets.UTF_8)).metadata()
        val contract = AdministrativePackContract(Base64.getEncoder().encodeToString(resource("signing-public-key.der")))
        val manifest = contract.verify(signed)
        val descriptor = contract.descriptor(signed, null)
        assertEquals("gsie.forestry.sqlite.v2", manifest.format)
        assertEquals(2, manifest.minReaderVersion)
        assertEquals(14, manifest.featureCount)
        assertEquals(2, manifest.quarantinedCount)
        assertEquals(2, descriptor.forestryQuarantinedCounts["onf_parcels"])
        assertEquals(8, descriptor.forestryLayerCounts["public_forests"])
        assertTrue(manifest.name.contains("TEST ONLY"))
    }
    @Test fun signed_original_counts_cannot_be_edited_after_signing() {
        val signed = Json.decodeFromString<SignedManifestDto>(resource("manifest.json").toString(Charsets.UTF_8)).metadata()
        val contract = AdministrativePackContract(Base64.getEncoder().encodeToString(resource("signing-public-key.der")))
        assertTrue(runCatching { contract.verify(signed.copy(manifestJson = signed.manifestJson.replace("\"quarantined_count\":2", "\"quarantined_count\":0"))) }.isFailure)
    }
}
