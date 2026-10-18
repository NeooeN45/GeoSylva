package com.forestry.counter.data.packs

import com.forestry.counter.capsule.json.CanonicalJson
import com.forestry.counter.capsule.json.StrictJsonParser
import com.forestry.counter.domain.model.pack.PackStatus
import com.forestry.counter.domain.model.pack.SignedPackMetadata
import com.forestry.counter.domain.model.pack.TerritorialContext
import com.forestry.counter.domain.model.pack.PackLevel
import com.forestry.counter.domain.usecase.pack.PackResolver
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Manifestes synthétiques uniquement : aucun objet de démonstration dans l'application. */
class ForestryPackContractTest {
    private fun manifest(onf: Boolean = false): JsonObject = buildJsonObject {
        put("pack_id", "test-forestry"); put("version", "f1-test")
        put("territory_id", "department:79"); put("name", "Protocole de test")
        put("region_code", "75"); put("content_kind", "forestry_layers")
        put("format", "gsie.forestry.sqlite.v1"); put("archive_bytes", 20480)
        put("sha256", "a".repeat(64)); put("min_reader_version", 1)
        put("created_at", "2026-10-03T10:00:00Z")
        put("source_id", "ign-parcellaire-express-wfs-2026"); put("source_edition", "2026-10-03")
        put("attribution", "IGN / DGFiP — PCI Vecteur, édition juin 2026")
        put("licence", "Licence Ouverte / Etalab 2.0")
        put("feature_count", if (onf) 5 else 4); put("label_count", 0)
        put("download_path", "/api/v1/packs/test-forestry/versions/f1-test/artifact")
        put("layers", buildJsonArray {
            for ((name, count) in listOf("cadastre" to 3, "public_forests" to 1) + if (onf) listOf("onf_parcels" to 1) else emptyList()) add(buildJsonObject {
                put("layer", name); put("source_id", requireNotNull(OfflineForestryLayerStore.QUALIFIED_SOURCES[name]))
                put("source_edition", "2026-10-03")
                put("licence", if (name == "onf_parcels") OfflineForestryLayerStore.ONF_LICENCE else "Licence Ouverte / Etalab 2.0")
                put("attribution", when (name) {
                    "cadastre" -> "IGN / DGFiP — PCI Vecteur, édition juin 2026"
                    "onf_parcels" -> OfflineForestryLayerStore.ONF_ATTRIBUTION
                    else -> "Forêts publiques, © ONF Maisons-Alfort, 2026"
                })
                put("feature_count", count); put("acquisition_sha256", "b".repeat(64)); put("coverage", "complete")
            })
        })
    }

    private fun signed(value: JsonObject): Pair<AdministrativePackContract, SignedPackMetadata> {
        val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val raw = CanonicalJson.encode(StrictJsonParser.parse(value.toString().toByteArray(Charsets.UTF_8)))
        val signature = Signature.getInstance("Ed25519").apply { initSign(pair.private); update(raw) }.sign()
        val id = MessageDigest.getInstance("SHA-256").digest(pair.public.encoded).joinToString("") { "%02x".format(it) }
        return AdministrativePackContract(Base64.getEncoder().encodeToString(pair.public.encoded)) to SignedPackMetadata(raw.toString(Charsets.UTF_8), Base64.getEncoder().encodeToString(signature), id)
    }

    private fun rejects(change: MutableMap<String, JsonElement>.() -> Unit) {
        val (reader, metadata) = signed(JsonObject(manifest().toMutableMap().apply(change)))
        assertTrue(runCatching { reader.verify(metadata) }.isFailure)
    }

    @Test fun signedForestryDescriptorKeepsRealLayerCountsWithoutCreatingForestRules() {
        val (reader, metadata) = signed(manifest())
        val descriptor = reader.descriptor(metadata, null).copy(status = PackStatus.INSTALLED)
        assertEquals("forestry_layers", descriptor.contentKind)
        assertEquals(mapOf("cadastre" to 3, "public_forests" to 1), descriptor.forestryLayerCounts)
        assertEquals(listOf(PackResolver.EMBEDDED_NATIONAL_PACK), PackResolver.resolveActivePacks(TerritorialContext("75", "79", listOf(descriptor), PackLevel.DEPARTEMENTAL), listOf(descriptor)))
        assertEquals("fallback", PackResolver.resolve(listOf(descriptor), { "ineligible-rule" }, "fallback").value)
    }

    @Test fun customOnfLicenceIsPreservedRatherThanRelabelledAsLo2() {
        val (reader, metadata) = signed(manifest(onf = true))
        val layer = reader.verify(metadata).layers.single { it.layer == "onf_parcels" }
        assertEquals(OfflineForestryLayerStore.ONF_LICENCE, layer.licence)
        assertTrue(layer.attribution.contains("2026-03-22"))
    }

    @Test fun copiedLegacyAdminHeaderCannotAdvertiseSqliteLayers() = rejects { this["format"] = JsonPrimitive("gsie.admin.geojson.v1") }
    @Test fun oversizedForestArchiveIsRefusedBeforeAnyFileRead() = rejects { this["archive_bytes"] = JsonPrimitive(OfflineForestryLayerStore.MAX_ARCHIVE_BYTES + 1) }
    @Test fun inconsistentTotalCountIsRejected() = rejects { this["feature_count"] = JsonPrimitive(999) }
    @Test fun missingForestExtentLayerIsRejected() = rejects { this["layers"] = JsonArray(getValue("layers").jsonArray.take(1)) }
    @Test fun externalArtifactPathIsRejectedDespiteValidSignature() = rejects { this["download_path"] = JsonPrimitive("https://external.example/artifact") }
    @Test fun partialProviderAcquisitionNeverBecomesACompleteOfflinePack() = rejects {
        val layers = getValue("layers").jsonArray.toMutableList()
        layers[0] = JsonObject(layers[0].jsonObject.toMutableMap().apply { this["coverage"] = JsonPrimitive("partial") })
        this["layers"] = JsonArray(layers)
    }
    @Test fun aDifferentQualifiedDatasetCannotMasqueradeAsCadastre() = rejects {
        val layers = getValue("layers").jsonArray.toMutableList()
        layers[0] = JsonObject(layers[0].jsonObject.toMutableMap().apply { this["source_id"] = JsonPrimitive("ign-admin-express-cog-carto-pe-2026") })
        this["layers"] = JsonArray(layers)
    }
    @Test fun normalizationRetainsRealNameAndOnlyBuildsSearchKey() {
        assertEquals("foret domaniale de chize", OfflineForestryLayerStore.searchKey(" Forêt  domaniale de Chizé "))
        assertEquals("cnpf", OfflineForestryLayerStore.searchKey("CNPF"))
    }
}
