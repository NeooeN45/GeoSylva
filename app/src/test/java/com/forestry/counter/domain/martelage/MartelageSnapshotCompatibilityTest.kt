package com.forestry.counter.domain.martelage

import com.forestry.counter.domain.model.MartelageSnapshotCodec
import com.forestry.counter.domain.model.MartelageSnapshotFingerprint
import com.forestry.counter.domain.model.MartelageSnapshotV1
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

/** Fixtures TEST capturées avant les projections figées, jamais régénérées par ces tests. */
class MartelageSnapshotCompatibilityTest {
    @Test fun historical_codec_bytes_stay_exact_for_both_scopes_and_a_correction() {
        for (name in listOf("snapshot-v1", "snapshot-v2", "snapshot-v2-correction")) {
            val archived = fixture(name)
            assertEquals(name, archived, MartelageSnapshotCodec.encode(MartelageSnapshotCodec.decode(archived)))
        }
    }

    @Test fun historical_fingerprints_match_pre_change_jvm_and_independent_json_hashes() {
        assertFingerprints("snapshot-v1",
            "2d7cd54909ee2bb9355eff01ddade1d264d0a3fd143233ac0835f16884b185a1",
            "b5f20aee2e8dc5666bee687db395a6b6b4e0165a512f4882a05c927dc069fdcf")
        assertFingerprints("snapshot-v2",
            "d10677c665c5aae1f623bda79842b58bbedfded6e06f0dfea00bcc1a0905f5dd",
            "2702c6c20134e1f9e623d571eb6b60275640be716a37168ec99bf2fdae4c8d27")
        assertFingerprints("snapshot-v2-correction",
            "6f74a7e507945794065255f261edd387d1800be81f0961976e874aebf41a6ad4",
            "e0a753d1db0fa00bb73231319d88bac4d9e37131f8c6447e4606198ef28f2454")
    }

    @Test fun pre_sampling_v1_remains_readable_without_qualifying_unknown_fields() {
        val snapshot = read("snapshot-v1-pre-sampling")
        assertEquals(1, snapshot.schemaVersion)
        assertNull(snapshot.placetteId)
        assertNull(snapshot.traceJson)
        assertTrue(snapshot.placettes.all { it.samplingPlanJson == null && it.footprintGeoJson == null })
        assertNull(snapshot.tiges.first().hauteurM)
        assertNull(snapshot.tiges.last().gpsWkt)
        val calculation = requireNotNull(snapshot.calculation)
        assertEquals("TEST-pre-sampling-engine", calculation.engineVersion)
        assertEquals(JsonNull, calculation.parameters["TEST-unknown"])
        assertEquals(JsonNull, calculation.results["TEST-volumeM3"])
        assertEquals("2d7cd54909ee2bb9355eff01ddade1d264d0a3fd143233ac0835f16884b185a1",
            calculation.sourceFingerprint)
        assertEquals(calculation.sourceFingerprint, legacyFingerprint(snapshot))
        // Le décodage ne qualifie pas une hauteur/un GPS ancien ; rejouer le JSON persisté brut.
        assertEquals(calculation, MartelageSnapshotCodec.decode(MartelageSnapshotCodec.encode(snapshot)).calculation)
    }

    @Test fun ordering_is_canonical_only_for_fingerprints_and_not_rewritten_in_the_archive() {
        val snapshot = read("snapshot-v1")
        assertEquals(listOf("TEST-plot-B", "TEST-plot-A"), snapshot.placettes.map { it.id })
        assertEquals(listOf("TEST-tree-B", "TEST-tree-A"), snapshot.tiges.map { it.id })
        val reordered = snapshot.copy(placettes = snapshot.placettes.reversed(), tiges = snapshot.tiges.reversed())
        assertEquals(fingerprint(snapshot), fingerprint(reordered))
        assertEquals(legacyFingerprint(snapshot), legacyFingerprint(reordered))
        assertNotEquals(MartelageSnapshotCodec.encode(snapshot), MartelageSnapshotCodec.encode(reordered))
    }

    @Test fun historic_measurements_and_provenance_cannot_borrow_an_existing_fingerprint() {
        val snapshot = read("snapshot-v2")
        val tree = snapshot.tiges.single()
        val changes = listOf(
            snapshot.copy(tiges = listOf(tree.copy(diamCm = 31.0))),
            snapshot.copy(tiges = listOf(tree.copy(hauteurM = 18.5))),
            snapshot.copy(tiges = listOf(tree.copy(gpsWkt = "POINT (2.1 46)"))),
            snapshot.copy(tiges = listOf(tree.copy(precisionM = null))),
            snapshot.copy(tiges = listOf(tree.copy(originTigeId = "TEST-origin"))),
            snapshot.copy(tiges = listOf(tree.copy(auteur = "TEST-other-author"))),
            snapshot.copy(tiges = listOf(tree.copy(version = 2))),
            snapshot.copy(parcelle = snapshot.parcelle.copy(surfaceHa = 3.0)),
            snapshot.copy(parcelle = snapshot.parcelle.copy(source = "TEST-other-source")),
            snapshot.copy(placettes = listOf(snapshot.placettes.single().copy(surfaceM2 = 350.0))),
        )
        for ((index, changed) in changes.withIndex()) {
            assertNotEquals("V2 mutation $index", fingerprint(snapshot), fingerprint(changed))
            assertNotEquals("V1 mutation $index", legacyFingerprint(snapshot), legacyFingerprint(changed))
        }
    }

    @Test fun sampling_changes_belong_to_v2_and_never_modify_a_v1_calculation_key() {
        val snapshot = read("snapshot-v2")
        val changed = snapshot.copy(placettes = listOf(snapshot.placettes.single().copy(
            samplingPlanJson = "{\"TEST-new-plan\":true}",
            footprintGeoJson = null,
        )))
        assertEquals(legacyFingerprint(snapshot), legacyFingerprint(changed))
        assertNotEquals(fingerprint(snapshot), fingerprint(changed))
    }

    @Test fun explicit_correction_preserves_its_links_and_does_not_rewrite_the_previous_version() {
        val original = read("snapshot-v2")
        val correction = read("snapshot-v2-correction")
        assertEquals(original.sessionId, correction.previousSessionId)
        assertEquals(original.campaignId, correction.campaignId)
        assertEquals(2, correction.revisionNumber)
        assertEquals(original.tiges.single().id, correction.tiges.single().originTigeId)
        assertEquals(30.0, original.tiges.single().diamCm, 0.0)
        assertNull(original.tiges.single().hauteurM)
        assertEquals(31.0, correction.tiges.single().diamCm, 0.0)
        assertEquals(17.5, requireNotNull(correction.tiges.single().hauteurM), 0.0)
        assertEquals(120L, original.closedAt)
        assertEquals(140L, correction.closedAt)
        assertNotEquals(fingerprint(original), fingerprint(correction))
        assertEquals(fixture("snapshot-v2"), MartelageSnapshotCodec.encode(original))
    }

    @Test fun unsupported_schema_and_new_scientific_fields_are_not_silently_discarded() {
        val value = Json.parseToJsonElement(fixture("snapshot-v2")).jsonObject
        expectFailure(JsonObject(value + ("schemaVersion" to JsonPrimitive(3))))
        expectFailure(JsonObject(value + ("declaredPurpose" to JsonPrimitive("combined"))))
        expectFailure(JsonObject(value + ("parcelle" to JsonObject(
            value.getValue("parcelle").jsonObject + ("observedHorizontalAreaM2" to JsonPrimitive(314.159))
        ))))
        for (key in listOf("placettes", "tiges")) {
            val entries = value.getValue(key).jsonArray
            val extended = JsonObject(entries.single().jsonObject + ("measurementOrigin" to JsonPrimitive("measured")))
            expectFailure(JsonObject(value + (key to JsonArray(listOf(extended)))))
        }
    }

    @Test fun invalid_cycle_or_scope_cannot_be_accepted_as_an_old_valid_snapshot() {
        val snapshot = read("snapshot-v2")
        for (invalid in listOf(
            snapshot.copy(revisionNumber = 0),
            snapshot.copy(activeDurationMs = -1L),
            snapshot.copy(placetteId = "TEST-foreign-plot"),
            snapshot.copy(tiges = listOf(snapshot.tiges.single().copy(sessionId = "TEST-foreign-revision"))),
            snapshot.copy(placettes = emptyList()),
        )) {
            assertTrue(runCatching { MartelageSnapshotCodec.decode(MartelageSnapshotCodec.encode(invalid)) }.isFailure)
        }
    }

    private fun assertFingerprints(name: String, v1: String, v2: String) {
        val snapshot = read(name)
        assertEquals("$name legacy V1", v1, legacyFingerprint(snapshot))
        assertEquals("$name V2", v2, fingerprint(snapshot))
    }

    private fun fingerprint(snapshot: MartelageSnapshotV1): String =
        MartelageSnapshotFingerprint.compute(snapshot.parcelle, snapshot.placettes, snapshot.tiges)

    private fun legacyFingerprint(snapshot: MartelageSnapshotV1): String =
        MartelageSnapshotFingerprint.computeLegacyV1(snapshot.parcelle, snapshot.placettes, snapshot.tiges)

    private fun expectFailure(value: JsonObject) {
        assertTrue(runCatching { MartelageSnapshotCodec.decode(value.toString()) }.isFailure)
    }

    private fun read(name: String): MartelageSnapshotV1 = MartelageSnapshotCodec.decode(fixture(name))

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/domain/martelage/snapshot-compatibility/$name.json")) {
            "Missing archived TEST fixture $name"
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }
}
