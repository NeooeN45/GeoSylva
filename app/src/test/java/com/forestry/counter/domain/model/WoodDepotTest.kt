package com.forestry.counter.domain.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class WoodDepotTest {
    @Test fun unknown_location_remains_unknown_after_roundtrip() {
        val draft = WoodDepotDraft("Dépôt bord de route", WoodDepotKind.ROADSIDE, "Accès à vérifier")
        val saved = Json.decodeFromString<WoodDepotDraft>(Json.encodeToString(draft))
        assertEquals(draft, saved)
        assertNull(saved.location)
    }

    @Test fun location_preserves_declared_source_and_accuracy_without_reclassification() {
        for (source in WoodDepotLocationSource.values()) {
            val location = WoodDepotLocation(46.12, -0.45, 7.5, source)
            assertEquals(location, Json.decodeFromString<WoodDepotLocation>(Json.encodeToString(location)))
            assertEquals(source, location.source)
        }
        assertNull(WoodDepotLocation(0.0, 0.0).accuracyM)
    }

    @Test fun latitude_and_longitude_must_be_finite_and_within_real_bounds() {
        for (latitude in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -90.01, 90.01)) {
            invalid { WoodDepotLocation(latitude, 0.0) }
        }
        for (longitude in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -180.01, 180.01)) {
            invalid { WoodDepotLocation(0.0, longitude) }
        }
        assertEquals(90.0, WoodDepotLocation(90.0, 180.0).latitude, 0.0)
        assertEquals(-180.0, WoodDepotLocation(-90.0, -180.0).longitude, 0.0)
    }

    @Test fun accuracy_is_optional_but_cannot_be_zero_negative_or_nonfinite() {
        for (accuracy in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            invalid { WoodDepotLocation(46.0, 0.0, accuracy) }
        }
        assertEquals(0.01, WoodDepotLocation(46.0, 0.0, 0.01).accuracyM!!, 0.0)
    }

    @Test fun name_and_notes_are_bounded_while_user_text_is_preserved() {
        for (name in listOf("", " \n\t ", "n".repeat(WoodDepotDraft.MAX_NAME_LENGTH + 1))) {
            invalid { WoodDepotDraft(name, WoodDepotKind.YARD) }
        }
        invalid { WoodDepotDraft("Dépôt", WoodDepotKind.YARD, "n".repeat(WoodDepotDraft.MAX_NOTES_LENGTH + 1)) }
        val valid = WoodDepotDraft("n".repeat(WoodDepotDraft.MAX_NAME_LENGTH), WoodDepotKind.OTHER,
            "n".repeat(WoodDepotDraft.MAX_NOTES_LENGTH))
        assertEquals(WoodDepotDraft.MAX_NOTES_LENGTH, valid.notes!!.length)
        assertEquals(" Dépôt de l'équipe ", WoodDepotDraft(" Dépôt de l'équipe ", WoodDepotKind.OTHER).name)
    }

    @Test fun ids_require_canonical_uuids_without_accepting_abbreviations() {
        requireWoodDepotId(ID)
        for (id in listOf("", "TEST-depot", "1-1-1-1-1", "A0000000-0000-0000-0000-000000000001")) {
            invalid { requireWoodDepotId(id) }
        }
    }

    @Test fun record_ownership_is_explicit_and_not_inferred_from_author() {
        val guest = record(WoodDepotOwnerScope.LOCAL_DEVICE, null)
        assertNull(guest.ownerAccountId)
        assertEquals("TEST-author", guest.createdBy)
        val account = record(WoodDepotOwnerScope.PERSONAL, "TEST-account")
        assertEquals("TEST-account", account.ownerAccountId)
        assertEquals("TEST-author", account.createdBy)
        invalid { record(WoodDepotOwnerScope.LOCAL_DEVICE, "TEST-account") }
        invalid { record(WoodDepotOwnerScope.PERSONAL, null) }
        invalid { record(WoodDepotOwnerScope.PERSONAL, " ") }
    }

    @Test fun revisions_timestamps_and_actors_are_validated_without_inventing_a_stock() {
        val saved = record(WoodDepotOwnerScope.LOCAL_DEVICE, null)
        invalid { saved.copy(revision = 0) }
        invalid { saved.copy(createdAt = -1) }
        invalid { saved.copy(updatedAt = -1) }
        invalid { saved.copy(deletedAt = -1) }
        invalid { saved.copy(createdBy = " ") }
        invalid { saved.copy(updatedBy = "") }
        assertEquals(saved, Json.decodeFromString<WoodDepotRecord>(Json.encodeToString(saved)))
    }

    private fun record(scope: WoodDepotOwnerScope, account: String?) = WoodDepotRecord(
        ID, WoodDepotDraft("TEST depot", WoodDepotKind.ROADSIDE), scope, account,
        1, 100, "TEST-author", 100, "TEST-author",
    )

    private fun invalid(block: () -> Unit) {
        try { block(); fail("Invalid TEST input was accepted") }
        catch (_: IllegalArgumentException) { }
    }

    private companion object { const val ID = "a0000000-0000-0000-0000-000000000001" }
}
