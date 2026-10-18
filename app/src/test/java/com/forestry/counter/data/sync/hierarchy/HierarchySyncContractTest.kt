package com.forestry.counter.data.sync.hierarchy

import com.forestry.counter.data.local.entity.InventaireSessionEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class HierarchySyncContractTest {
    @Test fun closed_snapshot_string_keeps_its_original_bytes_and_device_clocks_are_not_transmitted() {
        val closed = " \n{ \"fixture\" : \"Chêne 🌳\", \"escaped\": \"a\\\\b\" }\n "
        val session = InventaireSessionEntity(
            "s1", "p1", "MARTELAGE", 100L, 200L, null, null, null, null, null,
            createdAt = 100L, martelageState = "CLOSED", activeDurationMs = 50L,
            segmentStartedElapsedMs = 999L, segmentBootCount = 7, campaignId = "11111111-1111-4111-8111-111111111111",
            closedSnapshotJson = closed, recoveryRequired = true,
        )
        val encoded = hierarchyJson.encodeToString(session.toHierarchySession())
        val decoded = hierarchyJson.decodeFromString<HierarchySessionDto>(encoded)
        assertArrayEquals(closed.toByteArray(Charsets.UTF_8), decoded.closedSnapshotJson?.toByteArray(Charsets.UTF_8))
        val objectValue = hierarchyJson.parseToJsonElement(encoded).jsonObject
        assertFalse(objectValue.containsKey("segmentStartedElapsedMs"))
        assertFalse(objectValue.containsKey("segmentBootCount"))
        assertFalse(objectValue.containsKey("recoveryRequired"))
        assertEquals("s1", objectValue["sessionId"]?.jsonPrimitive?.content)
    }

    @Test fun inspecting_photo_references_does_not_rewrite_or_mistake_null_for_a_media_upload() {
        val source = " { \"tiges\" : [{\"photoUri\":\"content://fixture/photo\"}] } "
        val originalHash = hierarchySha256(source)
        assertTrue(hasClosedPhotoReferences(source))
        assertEquals(originalHash, hierarchySha256(source))
        assertFalse(hasClosedPhotoReferences("{\"tiges\":[{\"photoUri\":null}]}"))
        assertFalse(hasClosedPhotoReferences("{\"tiges\":[{\"photoUri\":\"\"}]}"))
        assertFalse(hasClosedPhotoReferences(null))
    }

    @Test fun native_scope_and_trace_are_preserved_without_reassigning_null_legacy() {
        val legacy = InventaireSessionEntity(
            "s1", "p1", "MARTELAGE", 100L, null, null, null, null, null, null,
            createdAt = 100L, martelageState = "PAUSED", activeDurationMs = 50L,
            campaignId = "11111111-1111-4111-8111-111111111111",
        )
        val legacyWire = hierarchyJson.parseToJsonElement(hierarchyJson.encodeToString(legacy.toHierarchySession())).jsonObject
        assertEquals("1", legacyWire["sessionScopeVersion"]?.jsonPrimitive?.content)
        assertEquals(kotlinx.serialization.json.JsonNull, legacyWire["placetteId"])
        val trace = " \n{\"version\":1,\"points\":[{\"latitude\":46.0,\"longitude\":-0.4,\"capturedAtMs\":101,\"elapsedRealtimeMs\":200,\"accuracyM\":4.0,\"altitudeM\":null,\"segmentId\":\"7:100:150\"}]}\n "
        val scoped = legacy.copy(placetteId = "plot-1", traceJson = trace)
        val encoded = hierarchyJson.encodeToString(scoped.toHierarchySession())
        val decoded = hierarchyJson.decodeFromString<HierarchySessionDto>(encoded)
        assertEquals(2, decoded.sessionScopeVersion)
        assertEquals("plot-1", decoded.placetteId)
        assertArrayEquals(trace.toByteArray(Charsets.UTF_8), decoded.traceJson?.toByteArray(Charsets.UTF_8))
        assertEquals("p1", decoded.parcelleId)
        assertFalse(encoded.contains("segmentBootCount"))
        assertFalse(encoded.contains("recoveryRequired"))
    }
}
