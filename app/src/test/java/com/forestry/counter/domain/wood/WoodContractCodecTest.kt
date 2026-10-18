package com.forestry.counter.domain.wood

import com.forestry.counter.domain.model.WoodDepotLocation
import com.forestry.counter.domain.model.WoodDepotLocationSource
import com.forestry.counter.domain.model.WoodDepotOwnerScope
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class WoodContractCodecTest {
    private val objectId = id(1)
    private val context = WoodCommandContext(id(2), id(3), objectId,
        WoodOwner(WoodDepotOwnerScope.LOCAL_DEVICE), "local-device")
    private val header = WoodObjectHeader("TEST · Pile A", tag = "Étiquette 123",
        declaredOrigin = "Déclaration terrain", declaredSpecies = "Chêne", declaredQuality = "À contrôler")
    private val pile = WoodObservationPayload(RawWoodMeasurement.Pile(
        length = RawWoodDimension(" +003,500 ", "m"), width = RawWoodDimension(null, "cm"),
        meanHeight = RawWoodDimension("NaN", "unknown"), shapeCode = "forme-déclarée",
        protocolReference = "TEST\nprotocole", pieceLength = RawWoodDimension("-0,00", "m")),
        observedTime = WoodObservedTime(Instant.parse("2026-10-18T10:00:00.123Z"), "Europe/Paris", 7200),
        description = "🌲\n\"photo à ajouter\"\\", declaredOrigin = "Parcelle déclarée",
        declaredSpecies = "Chêne", declaredQuality = "À vérifier",
        location = WoodDepotLocation(46.1827, -0.5371, 8.5, WoodDepotLocationSource.GPS))

    @Test fun saved_header_preserves_exact_declarations_and_rejects_changed_bytes() {
        val frozen = WoodContractCodec.freezeHeader(header)
        assertEquals(header, WoodContractCodec.readHeader(frozen.utf8Bytes(), frozen.sha256))
        try {
            WoodContractCodec.readHeader(frozen.text.replace("Pile A", "Pile B").toByteArray(), frozen.sha256)
            fail("altered header accepted")
        } catch (e: WoodContractException) { assertEquals(WoodContractError.HASH_MISMATCH, e.code) }
    }

    @Test fun exact_raw_readings_and_provenance_survive_roundtrip_without_becoming_numbers() {
        val frozen = WoodContractCodec.freezePayload(pile)
        assertEquals(pile, WoodContractCodec.readPayload(frozen.utf8Bytes(), frozen.sha256))
        assertTrue(frozen.text.contains("\"pileLength\""))
        assertTrue(frozen.text.contains("\"pieceLength\""))
        assertTrue(frozen.text.contains(" +003,500 "))
        assertFalse(frozen.text.contains("canonicalDecimal"))
        assertFalse(frozen.text.contains("recordedAt"))
        assertFalse(frozen.text.contains("volume"))
    }

    @Test fun unknown_methods_bark_and_unused_sections_are_preserved_not_coerced() {
        val p = WoodObservationPayload(RawWoodMeasurement.Log(
            length = RawWoodDimension("4", "m"), startDiameter = RawWoodDimension("17", "cm"),
            middleDiameter = RawWoodDimension("20", "cm"), endDiameter = RawWoodDimension("18", "cm"),
            methodCode = "future-method", barkConventionCode = "future-bark"))
        val f = WoodContractCodec.freezePayload(p)
        val read = WoodContractCodec.readPayload(f.utf8Bytes(), f.sha256)
        assertEquals(p, read)
        val raw = RawWoodObservation(id(4), objectId, Instant.EPOCH, read.measurement)
        assertTrue(WoodObservationCalculator.calculate(raw) is WoodObservationCalculation.Uncalculable)
    }

    @Test fun missing_empty_invalid_and_zero_dimensions_have_distinct_saved_bytes() {
        val values = listOf(null, RawWoodDimension(null, "m"), RawWoodDimension("", "m"),
            RawWoodDimension("NaN", "m"), RawWoodDimension("0", "m"), RawWoodDimension("-0,00", "m"))
        val frozen = values.map { WoodContractCodec.freezePayload(WoodObservationPayload(RawWoodMeasurement.Pile(length = it))) }
        assertEquals(values.size, frozen.map { it.sha256 }.toSet().size)
        frozen.zip(values).forEach { (f, expected) ->
            val value = WoodContractCodec.readPayload(f.utf8Bytes(), f.sha256).measurement as RawWoodMeasurement.Pile
            assertEquals(expected, value.length)
        }
    }

    @Test fun minimal_payload_has_a_stable_explicit_v1_representation() {
        val expected = """{"format":"wood-observation-payload","schemaVersion":1,"parserVersion":"wood-raw-decimal-1","measurement":{"kind":"PILE","pileLength":null,"pileDepth":null,"meanPileHeight":null,"pieceLength":null,"shapeCode":null,"protocolReference":null,"segmentLength":null,"diameterAtStart":null,"diameterAtMiddle":null,"diameterAtEnd":null,"methodCode":null,"barkConventionCode":null},"observedTime":null,"description":null,"declaredOrigin":null,"declaredSpecies":null,"declaredQuality":null,"location":null}"""
        val f = WoodContractCodec.freezePayload(WoodObservationPayload(RawWoodMeasurement.Pile()))
        assertEquals(expected, f.text)
        assertEquals(sha(expected.toByteArray()), f.sha256)
    }

    @Test fun all_six_command_intents_roundtrip_including_initial_observation_and_selection() {
        val o = NewWoodObservation(id(4), pile)
        val commands = listOf(
            WoodObjectCommand.Create(context, WoodObjectKind.PILE, header),
            WoodObjectCommand.Create(context, WoodObjectKind.PILE, header, o, true),
            WoodObjectCommand.UpdateHeader(context, 5, header),
            WoodObjectCommand.AddObservation(context, 5, o, true),
            WoodObjectCommand.CorrectObservation(context, 5, id(5), 2, o, "Longueur corrigée"),
            WoodObjectCommand.SelectEffective(context, 5, id(4)),
            WoodObjectCommand.Archive(context, 5),
        )
        commands.forEach { c ->
            val f = WoodContractCodec.freezeCommand(c)
            assertEquals(c, WoodContractCodec.readCommand(f.utf8Bytes(), f.sha256))
            assertEquals(f.text, WoodContractCodec.freezeCommand(c).text)
        }
        assertEquals(commands.size, commands.map { WoodContractCodec.freezeCommand(it).sha256 }.toSet().size)
    }

    @Test fun context_scope_author_revision_and_selection_are_all_bound_to_the_fingerprint() {
        val c = WoodObjectCommand.AddObservation(context, 5, NewWoodObservation(id(4), pile), true)
        val variants = listOf(c, c.copy(context = context.copy(commandId = id(20))),
            c.copy(context = context.copy(depotId = id(20))), c.copy(context = context.copy(objectId = id(20))),
            c.copy(context = context.copy(owner = WoodOwner(WoodDepotOwnerScope.PERSONAL, "account-A"))),
            c.copy(context = context.copy(actorId = "other-author")), c.copy(expectedObjectRevision = 6),
            c.copy(makeEffective = false), c.copy(observation = c.observation.copy(id = id(20))),
            c.copy(observation = c.observation.copy(payload = pile.copy(description = "Autre déclaration"))))
        assertEquals(variants.size, variants.map { WoodContractCodec.freezeCommand(it).sha256 }.toSet().size)
        val personal = c.copy(context = context.copy(owner = WoodOwner(WoodDepotOwnerScope.PERSONAL, "account-A")))
        assertNotEquals(WoodContractCodec.freezeCommand(personal).sha256,
            WoodContractCodec.freezeCommand(personal.copy(context = personal.context.copy(
                owner = WoodOwner(WoodDepotOwnerScope.PERSONAL, "account-B")))).sha256)
    }

    @Test fun correction_target_revision_reason_and_new_reading_change_the_fingerprint() {
        val c = WoodObjectCommand.CorrectObservation(context, 5, id(5), 2, NewWoodObservation(id(4), pile), "Diamètre revu")
        val variants = listOf(c, c.copy(targetObservationId = id(6)), c.copy(expectedObservationRevision = 3),
            c.copy(reason = "Autre motif"), c.copy(correction = c.correction.copy(payload = pile.copy(observedTime = null))))
        assertEquals(variants.size, variants.map { WoodContractCodec.freezeCommand(it).sha256 }.toSet().size)
        val f = WoodContractCodec.freezeCommand(c)
        assertTrue(f.text.contains("\"makeEffective\":null"))
        rejectCommand(f.text.replace("\"makeEffective\":null", "\"makeEffective\":true"), WoodContractError.NON_CANONICAL_DOCUMENT)
    }

    @Test fun changing_header_does_not_rewrite_the_observation_snapshot() {
        val f = WoodContractCodec.freezePayload(pile)
        val changed = header.copy(declaredSpecies = "Hêtre", location = WoodDepotLocation(47.0, 1.0))
        WoodContractCodec.freezeCommand(WoodObjectCommand.UpdateHeader(context, 5, changed))
        assertEquals(pile, WoodContractCodec.readPayload(f.utf8Bytes(), f.sha256))
        assertEquals("Chêne", pile.declaredSpecies)
    }

    @Test fun source_bytes_cannot_be_mutated_through_a_frozen_document() {
        val f = WoodContractCodec.freezePayload(pile)
        val bytes = f.utf8Bytes()
        bytes.fill(0)
        assertEquals(pile, WoodContractCodec.readPayload(f.utf8Bytes(), f.sha256))
        assertError(WoodContractError.HASH_MISMATCH) { WoodContractCodec.readPayload(bytes, f.sha256) }
    }

    @Test fun tampered_bytes_and_untrusted_hashes_are_refused_before_reading() {
        val f = WoodContractCodec.freezePayload(pile)
        assertError(WoodContractError.HASH_MISMATCH) { WoodContractCodec.readPayload(f.utf8Bytes(), "0".repeat(64)) }
        assertError(WoodContractError.HASH_MISMATCH) { WoodContractCodec.readPayload(f.utf8Bytes(), f.sha256.uppercase()) }
        val tampered = f.text.replace("Chêne", "Hêtre").toByteArray()
        assertError(WoodContractError.HASH_MISMATCH) { WoodContractCodec.readPayload(tampered, f.sha256) }
    }

    @Test fun unknown_fields_versions_duplicates_and_noncanonical_json_are_not_silently_lost() {
        val f = WoodContractCodec.freezePayload(pile)
        rejectPayload(f.text.replace("\"schemaVersion\":1", "\"schemaVersion\":2"), WoodContractError.UNSUPPORTED_VERSION)
        rejectPayload(f.text.replace("wood-raw-decimal-1", "wood-raw-decimal-2"), WoodContractError.UNSUPPORTED_VERSION)
        rejectPayload(f.text.dropLast(1) + ",\"photos\":[]}", WoodContractError.MALFORMED_DOCUMENT)
        rejectPayload(f.text.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"), WoodContractError.NON_CANONICAL_DOCUMENT)
        rejectPayload(" " + f.text, WoodContractError.NON_CANONICAL_DOCUMENT)
        rejectPayload(f.text.replace("\"diameterAtStart\":null", "\"diameterAtStart\":{\"rawText\":\"12\",\"unit\":\"cm\"}"),
            WoodContractError.NON_CANONICAL_DOCUMENT)
    }

    @Test fun malformed_utf8_excessive_size_invalid_time_and_invalid_canonical_ids_are_refused() {
        val bytes = byteArrayOf(0xc3.toByte(), 0x28)
        assertError(WoodContractError.MALFORMED_DOCUMENT) { WoodContractCodec.readPayload(bytes, sha(bytes)) }
        val huge = ByteArray(WoodContractCodec.MAX_DOCUMENT_BYTES + 1)
        assertError(WoodContractError.DOCUMENT_TOO_LARGE) { WoodContractCodec.readPayload(huge, "") }
        rejectPayload(WoodContractCodec.freezePayload(pile).text.replace("2026-10-18T10:00:00.123Z", "not-an-instant"),
            WoodContractError.MALFORMED_DOCUMENT)
        val f = WoodContractCodec.freezeCommand(WoodObjectCommand.Archive(context, 1))
        rejectCommand(f.text.replace(context.objectId.toString(), "0-0-0-0-1"), WoodContractError.INVALID_ID)
        rejectCommand(f.text.replace("\"expectedObjectRevision\":1", "\"expectedObjectRevision\":0"), WoodContractError.INVALID_REVISION)
    }

    @Test fun incompatible_identity_kind_scope_and_missing_reason_cannot_become_commands() {
        assertError(WoodContractError.INVALID_OWNER) { WoodOwner(WoodDepotOwnerScope.LOCAL_DEVICE, "account-A") }
        assertError(WoodContractError.INVALID_OWNER) { WoodOwner(WoodDepotOwnerScope.PERSONAL) }
        assertError(WoodContractError.IDENTICAL_IDS) { context.copy(commandId = context.objectId) }
        assertError(WoodContractError.IDENTICAL_IDS) {
            WoodObjectCommand.AddObservation(context, 1, NewWoodObservation(context.objectId, pile), false)
        }
        assertError(WoodContractError.INVALID_INITIAL_SELECTION) {
            WoodObjectCommand.Create(context, WoodObjectKind.PILE, header, makeEffective = true)
        }
        assertError(WoodContractError.KIND_MISMATCH) {
            WoodObjectCommand.Create(context, WoodObjectKind.LOG, header, NewWoodObservation(id(4), pile))
        }
        assertError(WoodContractError.INVALID_TEXT) {
            WoodObjectCommand.CorrectObservation(context, 1, id(5), 1, NewWoodObservation(id(4), pile), " \u0085")
        }
    }

    @Test fun piece_length_is_saved_but_never_used_as_missing_pile_depth() {
        val p = WoodObservationPayload(RawWoodMeasurement.Pile(length = RawWoodDimension("10", "m"),
            meanHeight = RawWoodDimension("2", "m"), pieceLength = RawWoodDimension("4", "m"),
            shapeCode = RawWoodMeasurement.Pile.RECTANGULAR_ENVELOPE, protocolReference = "TEST"))
        val f = WoodContractCodec.freezePayload(p)
        val saved = WoodContractCodec.readPayload(f.utf8Bytes(), f.sha256)
        val calc = WoodObservationCalculator.calculate(RawWoodObservation(id(4), objectId, Instant.EPOCH, saved.measurement))
        assertTrue(calc is WoodObservationCalculation.Uncalculable)
        assertTrue((calc as WoodObservationCalculation.Uncalculable).diagnostics.any {
            it.field == WoodField.WIDTH && it.code == WoodDiagnosticCode.MISSING_VALUE })
    }

    @Test fun malformed_unicode_is_refused_without_truncating_valid_supplementary_characters() {
        val valid = pile.copy(description = "🌲".repeat(5_000))
        val f = WoodContractCodec.freezePayload(valid)
        assertEquals(valid, WoodContractCodec.readPayload(f.utf8Bytes(), f.sha256))
        assertError(WoodContractError.INVALID_TEXT) { pile.copy(description = "bad\uD800") }
        try {
            RawWoodDimension("\uDC00", "m")
            fail("Malformed UTF-16 must not be replaced")
        } catch (e: WoodStructureException) { assertEquals(WoodStructureError.INVALID_UNICODE, e.code) }
        try {
            RawWoodMeasurement.Log(methodCode = "x".repeat(65))
            fail("Codes must be bounded")
        } catch (e: WoodStructureException) { assertEquals(WoodStructureError.CODE_TOO_LONG, e.code) }
    }

    private fun rejectPayload(text: String, code: WoodContractError) = assertError(code) {
        val b = text.toByteArray(); WoodContractCodec.readPayload(b, sha(b))
    }
    private fun rejectCommand(text: String, code: WoodContractError) = assertError(code) {
        val b = text.toByteArray(); WoodContractCodec.readCommand(b, sha(b))
    }
    private fun assertError(code: WoodContractError, block: () -> Unit) {
        try { block(); fail("Expected $code") }
        catch (e: WoodContractException) { assertEquals(code, e.code) }
    }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private fun id(n: Int) = UUID(0, n.toLong())
}
