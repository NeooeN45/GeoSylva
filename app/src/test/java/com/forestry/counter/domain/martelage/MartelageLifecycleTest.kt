package com.forestry.counter.domain.martelage

import com.forestry.counter.domain.model.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

class MartelageLifecycleTest {
    @Test fun elapsed_time_survives_process_restart_without_using_wall_clock() {
        val session = running()
        assertEquals(1_500L, session.durationAt(MartelageClockReading(1L, 2_000L, 7)))
        assertEquals(1_500L, session.durationAt(MartelageClockReading(Long.MAX_VALUE, 2_000L, 7)))
    }
    @Test fun pause_excludes_new_elapsed_time() {
        assertEquals(500L, running().copy(martelageState = MartelageStates.PAUSED)
            .durationAt(MartelageClockReading(1000L, 9_000L, 7)))
    }
    @Test fun reboot_never_invents_unmeasured_work_time() {
        val session = running()
        val reading = MartelageClockReading(500_000L, 900_000L, 8)
        assertTrue(session.needsClockRecovery(reading))
        assertEquals(500L, session.durationAt(reading))
    }
    @Test fun elapsed_clock_rollback_requires_recovery() {
        val reading = MartelageClockReading(500L, 100L, 7)
        assertTrue(running().needsClockRecovery(reading))
        assertEquals(500L, running().durationAt(reading))
    }
    @Test fun unknown_boot_identity_never_adds_wall_time() {
        val reading = MartelageClockReading(100_000L, 20_000L, null)
        assertTrue(running().copy(segmentBootCount = null).needsClockRecovery(reading))
        assertEquals(500L, running().copy(segmentBootCount = null).durationAt(reading))
    }
    @Test fun elapsed_accumulation_saturates_instead_of_overflowing_negative() {
        val session = running().copy(activeDurationMs = Long.MAX_VALUE - 2L)
        assertEquals(Long.MAX_VALUE, session.durationAt(MartelageClockReading(10L, 5_000L, 7)))
    }
    @Test fun unknown_legacy_sessions_do_not_become_active() {
        val session = running().copy(typeSession = "OLD_UNKNOWN_TYPE", martelageState = null)
        assertFalse(session.needsClockRecovery(MartelageClockReading(10L, 0L, 9)))
        assertEquals(500L, session.durationAt(MartelageClockReading(10L, 5_000L, 7)))
    }
    @Test fun fingerprint_ignores_collection_order_but_detects_a_new_measurement() {
        val first = tree("one")
        val second = tree("two")
        val hash = MartelageSnapshotFingerprint.compute(parcel(), emptyList(), listOf(first, second))
        assertEquals(hash, MartelageSnapshotFingerprint.compute(parcel(), emptyList(), listOf(second, first)))
        assertNotEquals(hash, MartelageSnapshotFingerprint.compute(parcel(), emptyList(), listOf(first.copy(hauteurM = 15.0), second)))
    }
    @Test fun full_snapshot_roundtrip_preserves_null_unknowns_and_measurements() {
        val tree = tree("one").copy(numero = 12, qualite = 3, defauts = listOf("gel", "fente"), originTigeId = "original",
            gpsWkt = "POINT (2.3 46.3)", precisionM = 7.0, hauteurM = 14.75, photoUri = "content://TEST/photo")
        val snapshot = snapshot(listOf(tree))
        assertEquals(snapshot, MartelageSnapshotCodec.decode(MartelageSnapshotCodec.encode(snapshot)))
        assertNull(snapshot.parcelle.slopePct)
        assertNull(snapshot.calculation)
    }
    @Test fun calculation_roundtrip_retains_actual_results_and_parameters() {
        val snapshot = snapshot(listOf(tree("one")))
        val calculation = MartelageCalculationSnapshotV1(
            generatedAt = 5L, engineVersion = "TEST-engine-v1",
            parameters = buildJsonObject { put("heightMode", "measured") },
            results = buildJsonObject { put("volumeM3", 1.25) },
            sourceFingerprint = MartelageSnapshotFingerprint.compute(snapshot.parcelle, snapshot.placettes, snapshot.tiges),
        )
        val sealed = snapshot.copy(calculation = calculation)
        assertEquals(sealed, MartelageSnapshotCodec.decode(MartelageSnapshotCodec.encode(sealed)))
    }
    @Test fun codec_refuses_tree_from_another_parcel() {
        val wrong = snapshot(listOf(tree("one").copy(parcelleId = "foreign")))
        assertTrue(runCatching { MartelageSnapshotCodec.decode(MartelageSnapshotCodec.encode(wrong)) }.isFailure)
    }
    @Test fun codec_refuses_tree_from_another_revision() {
        val wrong = snapshot(listOf(tree("one").copy(sessionId = "foreign")))
        assertTrue(runCatching { MartelageSnapshotCodec.decode(MartelageSnapshotCodec.encode(wrong)) }.isFailure)
    }

    @Test fun v2_codec_requires_one_matching_plot_and_tree_scope() {
        val plot = Placette("plot", "parcel", "TEST plot", "CIRC", 10.0, 314.159, "POINT (2 46)")
        val scoped = snapshot(listOf(tree("one").copy(placetteId = "plot")))
            .copy(schemaVersion = 2, placetteId = "plot", placettes = listOf(plot))
        assertEquals(scoped, MartelageSnapshotCodec.decode(MartelageSnapshotCodec.encode(scoped)))
        assertTrue(runCatching { MartelageSnapshotCodec.decode(MartelageSnapshotCodec.encode(scoped.copy(placettes = emptyList()))) }.isFailure)
        assertTrue(runCatching { MartelageSnapshotCodec.decode(MartelageSnapshotCodec.encode(scoped.copy(placetteId = "other"))) }.isFailure)
        assertTrue(runCatching { MartelageSnapshotCodec.decode(MartelageSnapshotCodec.encode(scoped.copy(tiges = listOf(tree("one"))))) }.isFailure)
    }

    @Test fun v1_does_not_accept_a_new_plot_scope_or_trace() {
        val legacy = snapshot(listOf(tree("one")))
        assertTrue(runCatching { MartelageSnapshotCodec.decode(MartelageSnapshotCodec.encode(legacy.copy(placetteId = "plot"))) }.isFailure)
        assertTrue(runCatching { MartelageSnapshotCodec.decode(MartelageSnapshotCodec.encode(legacy.copy(traceJson = "{}"))) }.isFailure)
    }

    @Test fun v1_fingerprint_ignores_new_sampling_fields_while_v2_tracks_them() {
        val plot = Placette("plot", "parcel", "TEST plot", "CIRC", 10.0, 314.159, "POINT (2 46)")
        val annotated = plot.copy(samplingPlanJson = "{\"schemaVersion\":1}", footprintGeoJson = "{\"type\":\"Polygon\"}")
        assertEquals(MartelageSnapshotFingerprint.computeLegacyV1(parcel(), listOf(plot), emptyList()),
            MartelageSnapshotFingerprint.computeLegacyV1(parcel(), listOf(annotated), emptyList()))
        assertNotEquals(MartelageSnapshotFingerprint.compute(parcel(), listOf(plot), emptyList()),
            MartelageSnapshotFingerprint.compute(parcel(), listOf(annotated), emptyList()))
    }

    private fun snapshot(trees: List<Tige>) = MartelageSnapshotV1(
        sessionId = "session", parcelleId = "parcel", campaignId = "campaign", revisionNumber = 1,
        previousSessionId = null, startedAt = 1L, closedAt = 5L, activeDurationMs = 4L,
        auteur = "TEST-user", parcelle = parcel(), placettes = emptyList(), tiges = trees,
    )
    private fun running() = InventaireSession(
        sessionId = "session", parcelleId = "parcel", typeSession = MartelageStates.TYPE,
        dateDebut = 10L, dateFin = null, operateurNom = null, methode = null,
        intensiteEchantillonnagePct = null, objectifSession = null, remarques = null, createdAt = 10L,
        martelageState = MartelageStates.RUNNING, activeDurationMs = 500L,
        segmentStartedElapsedMs = 1_000L, segmentBootCount = 7, campaignId = "campaign",
    )
    private fun tree(id: String) = Tige(
        id = id, parcelleId = "parcel", placetteId = null, essenceCode = "TEST-oak", diamCm = 30.0,
        hauteurM = null, gpsWkt = null, precisionM = null, altitudeM = null, timestamp = 100L,
        note = null, produit = null, fCoef = null, valueEur = null, sessionId = "session",
    )
    private fun parcel() = Parcelle(
        id = "parcel", forestId = null, name = "TEST parcel", surfaceHa = 2.5, shape = null,
        slopePct = null, aspect = null, access = null, altitudeM = null, objectifType = null,
        objectifVal = null, tolerancePct = null, samplingMode = null, sampleAreaM2 = null,
        targetSpeciesCsv = null, srid = 4326, remarks = null, createdAt = 10L, updatedAt = 10L,
    )
}
