package com.forestry.counter.domain.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationQualificationTest {
    @Test
    fun should_preserve_unknown_declarations_without_inference_from_raw_height_or_gps() {
        val payload = payload(ObservationQualificationDraft())
        val decoded = ObservationQualificationCodec.decode(ObservationQualificationCodec.encode(payload))
        assertEquals(payload, decoded)
        assertEquals(18.0, requireNotNull(decoded.basis.hauteurM), 0.0)
        assertEquals("POINT(0 0)", decoded.basis.gpsWkt)
        assertNull(decoded.draft.support)
        assertNull(decoded.draft.diameter)
        assertNull(decoded.draft.height)
        assertNull(decoded.draft.position)
        assertNull(decoded.copiedFrom)
    }

    @Test
    fun should_preserve_partial_origins_without_inventing_method_source_or_measurement_time() {
        MeasurementOrigin.entries.forEach { origin ->
            val draft = ObservationQualificationDraft(
                diameter = DiameterQualificationDraft(origin = origin),
                height = HeightQualificationDraft(origin = origin),
            )
            val decoded = roundtrip(draft).draft
            assertEquals(draft, decoded)
            assertNull(decoded.diameter?.methodReference)
            assertNull(decoded.diameter?.conventionReference)
            assertNull(decoded.height?.observedAt)
            assertNull(decoded.height?.sourceReference)
            assertNull(decoded.height?.nature)
            assertNull(decoded.support)
        }
    }

    @Test
    fun should_keep_position_origin_distinct_without_inferred_capture_from_raw_timestamp() {
        PositionOrigin.entries.forEach { origin ->
            val draft = ObservationQualificationDraft(position = PositionQualificationDraft(origin = origin))
            val decoded = roundtrip(draft).draft
            assertEquals(origin, decoded.position?.origin)
            assertNull(decoded.position?.capturedAt)
            assertNull(decoded.position?.methodReference)
            assertNull(decoded.position?.sourceReference)
        }
    }

    @Test
    fun should_distinguish_unknown_false_and_true_pairing_when_individual_total_height_is_declared() {
        listOf(null, false, true).forEach { paired ->
            val draft = ObservationQualificationDraft(
                support = ObservationSupport.INDIVIDUAL_TREE,
                height = HeightQualificationDraft(
                    nature = HeightNature.TOTAL,
                    support = ObservationSupport.INDIVIDUAL_TREE,
                    pairedWithDiameter = paired,
                ),
            )
            assertEquals(paired, roundtrip(draft).draft.height?.pairedWithDiameter)
        }
    }

    @Test
    fun should_refuse_pairing_without_explicit_individual_support_and_total_height() {
        listOf(null, ObservationSupport.GROUP_REFERENCE).forEach { support ->
            assertThrows(IllegalArgumentException::class.java) {
                HeightQualificationDraft(
                    nature = HeightNature.TOTAL,
                    support = support,
                    pairedWithDiameter = true,
                )
            }
            assertThrows(IllegalArgumentException::class.java) {
                ObservationQualificationDraft(
                    support = support,
                    height = HeightQualificationDraft(
                        nature = HeightNature.TOTAL,
                        support = ObservationSupport.INDIVIDUAL_TREE,
                        pairedWithDiameter = true,
                    ),
                )
            }
        }
        listOf(null, HeightNature.MERCHANTABLE).forEach { nature ->
            assertThrows(IllegalArgumentException::class.java) {
                HeightQualificationDraft(
                    nature = nature,
                    support = ObservationSupport.INDIVIDUAL_TREE,
                    pairedWithDiameter = true,
                )
            }
        }
    }

    @Test
    fun should_keep_group_reference_height_without_reclassifying_it_as_an_individual_measurement() {
        val draft = ObservationQualificationDraft(
            support = ObservationSupport.GROUP_REFERENCE,
            height = HeightQualificationDraft(
                nature = HeightNature.TOTAL,
                origin = MeasurementOrigin.ASSIGNED_REFERENCE,
                support = ObservationSupport.GROUP_REFERENCE,
                pairedWithDiameter = false,
                sourceReference = "TEST-height-reference",
            ),
            position = PositionQualificationDraft(origin = PositionOrigin.REUSED_POINT),
        )
        assertEquals(draft, roundtrip(draft).draft)
        assertFalse(requireNotNull(roundtrip(draft).draft.height?.pairedWithDiameter))
    }

    @Test
    fun should_refuse_pairing_assigned_reference_values_even_when_individual_support_is_declared() {
        assertThrows(IllegalArgumentException::class.java) {
            HeightQualificationDraft(
                nature = HeightNature.TOTAL,
                origin = MeasurementOrigin.ASSIGNED_REFERENCE,
                support = ObservationSupport.INDIVIDUAL_TREE,
                pairedWithDiameter = true,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            fullDraft().copy(diameter = DiameterQualificationDraft(origin = MeasurementOrigin.ASSIGNED_REFERENCE))
        }
        assertInvalid(listOf("draft", "height", "origin"), JsonPrimitive("ASSIGNED_REFERENCE"))
        assertInvalid(listOf("draft", "diameter", "origin"), JsonPrimitive("ASSIGNED_REFERENCE"))
    }

    @Test
    fun should_capture_only_raw_values_with_a_stable_independently_computed_fingerprint() {
        val tige = tige()
        val basis = ObservationMeasurementBasis.capture(tige)
        assertEquals("TEST-tree", basis.tigeId)
        assertEquals("TEST-parcelle", basis.parcelleId)
        assertEquals("TEST-plot", basis.placetteId)
        assertEquals("TEST-session", basis.sessionId)
        assertNull(basis.originTigeId)
        assertEquals("QRO", basis.essenceCode)
        assertEquals(40.0, basis.diamCm, 0.0)
        assertEquals(18.0, requireNotNull(basis.hauteurM), 0.0)
        assertEquals("POINT(0 0)", basis.gpsWkt)
        assertEquals(3.0, requireNotNull(basis.precisionM), 0.0)
        assertEquals(101.0, requireNotNull(basis.altitudeM), 0.0)
        assertEquals(100L, basis.timestamp)
        assertEquals(1, basis.observationVersion)
        // Référence calculée indépendamment avec Python hashlib sur la projection JSON V1 ordonnée.
        assertEquals("0af259b2f64106acf1bbc300879646a7950ef3c0f88aee005f98f6cedbc24162", basis.fingerprint())
        assertTrue(basis.matches(tige))
    }

    @Test
    fun should_invalidate_the_basis_when_any_raw_value_or_scope_changes() {
        val tige = tige()
        val basis = ObservationMeasurementBasis.capture(tige)
        val changes = listOf(
            tige.copy(id = "TEST-other-tree"),
            tige.copy(parcelleId = "TEST-other-parcelle"),
            tige.copy(placetteId = "TEST-other-plot"),
            tige.copy(sessionId = "TEST-other-session"),
            tige.copy(originTigeId = "TEST-source-tree"),
            tige.copy(essenceCode = "QPE"),
            tige.copy(diamCm = 41.0),
            tige.copy(hauteurM = 19.0),
            tige.copy(gpsWkt = "POINT(1 0)"),
            tige.copy(precisionM = 4.0),
            tige.copy(altitudeM = 102.0),
            tige.copy(timestamp = 101L),
            tige.copy(version = 2),
        )
        changes.forEach { changed ->
            assertFalse(basis.matches(changed))
            assertNotEquals(basis.fingerprint(), ObservationMeasurementBasis.capture(changed).fingerprint())
        }
    }

    @Test
    fun should_keep_returned_measurements_stale_after_an_intermediate_edit() {
        val original = tige()
        val basis = ObservationMeasurementBasis.capture(original)
        val edited = original.copy(hauteurM = 19.0, version = 2)
        val returned = edited.copy(hauteurM = original.hauteurM, version = 3)
        assertFalse(basis.matches(edited))
        assertFalse(basis.matches(returned))
        assertFalse(basis.matches(original.copy(qualite = 2, version = 2)))
    }

    @Test
    fun should_preserve_null_raw_measurements_without_substituting_zero() {
        val basis = ObservationMeasurementBasis.capture(
            tige().copy(placetteId = null, sessionId = null, hauteurM = null, gpsWkt = null,
                precisionM = null, altitudeM = null),
        )
        val decoded = ObservationQualificationCodec.decode(ObservationQualificationCodec.encode(
            payload().copy(basis = basis),
        ))
        assertEquals(basis, decoded.basis)
        assertNull(decoded.basis.hauteurM)
        assertNull(decoded.basis.gpsWkt)
        assertNull(decoded.draft.height)
    }

    @Test
    fun should_preserve_raw_zero_and_negative_altitude_without_scientific_admissibility_claim() {
        val basis = ObservationMeasurementBasis.capture(
            tige().copy(diamCm = 0.0, hauteurM = 0.0, precisionM = 0.0, altitudeM = -50.0),
        )
        val decoded = ObservationQualificationCodec.decode(ObservationQualificationCodec.encode(
            payload().copy(basis = basis),
        ))
        assertEquals(basis, decoded.basis)
        assertNull(decoded.draft.diameter)
        assertNull(decoded.draft.height)
    }

    @Test
    fun should_refuse_nonfinite_negative_measurements_and_invalid_raw_identity_or_version() {
        val original = tige()
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0).forEach { number ->
            assertThrows(IllegalArgumentException::class.java) { ObservationMeasurementBasis.capture(original.copy(diamCm = number)) }
            assertThrows(IllegalArgumentException::class.java) { ObservationMeasurementBasis.capture(original.copy(hauteurM = number)) }
            assertThrows(IllegalArgumentException::class.java) { ObservationMeasurementBasis.capture(original.copy(precisionM = number)) }
        }
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { number ->
            assertThrows(IllegalArgumentException::class.java) { ObservationMeasurementBasis.capture(original.copy(altitudeM = number)) }
        }
        listOf(original.copy(id = ""), original.copy(parcelleId = " "), original.copy(essenceCode = ""),
            original.copy(gpsWkt = "x".repeat(4097)), original.copy(version = 0), original.copy(timestamp = -1L))
            .forEach { invalid ->
                assertThrows(IllegalArgumentException::class.java) { ObservationMeasurementBasis.capture(invalid) }
                assertFalse(ObservationMeasurementBasis.capture(original).matches(invalid))
            }
    }

    @Test
    fun should_make_stale_declarations_consultable_without_exposing_them_as_current() {
        val record = record(payload(fullDraft()))
        val current = ObservationQualificationRead(record, ObservationQualificationStatus.CURRENT)
        val stale = ObservationQualificationRead(record, ObservationQualificationStatus.STALE)
        assertEquals(record.payload.draft, current.currentDraft)
        assertNull(stale.currentDraft)
        assertEquals(current.record, stale.record)
    }

    @Test
    fun should_keep_source_copy_provenance_separate_from_current_author_and_revision() {
        val source = ObservationQualificationCopySource("TEST-source-tree", 4, 80, "TEST-source-author")
        val copied = payload(fullDraft()).copy(copiedFrom = source)
        val decoded = ObservationQualificationCodec.decode(ObservationQualificationCodec.encode(copied))
        assertEquals(copied, decoded)
        val current = record(decoded)
        assertEquals(1, current.revision)
        assertEquals("TEST-author", current.updatedBy)
        assertEquals(4, current.payload.copiedFrom?.revision)
        assertEquals("TEST-source-author", current.payload.copiedFrom?.updatedBy)
    }

    @Test
    fun should_refuse_invalid_copy_source_or_record_provenance() {
        val source = ObservationQualificationCopySource("TEST-source-tree", 1, 10, "TEST-author")
        listOf(
            { source.copy(tigeId = "") },
            { source.copy(revision = 0) },
            { source.copy(updatedAt = -1) },
            { source.copy(updatedBy = " ") },
            { record().copy(tigeId = "TEST-wrong-tree") },
            { record().copy(revision = 0) },
            { record().copy(createdAt = -1) },
            { record().copy(updatedAt = -1) },
            { record().copy(createdBy = "") },
            { record().copy(updatedBy = " ") },
        ).forEach { invalid -> assertThrows(IllegalArgumentException::class.java) { invalid() } }
    }

    @Test
    fun should_require_explicit_supported_version_and_required_basis_and_draft() {
        listOf(JsonNull, JsonPrimitive("1"), JsonPrimitive(0), JsonPrimitive(2), JsonPrimitive(1.0))
            .forEach { version -> assertInvalid(listOf("schemaVersion"), version) }
        val encoded = encodedFullPayload()
        listOf("schemaVersion", "basis", "draft").forEach { field ->
            val withoutRequiredField = JsonObject(encoded.filterKeys { it != field }).toString()
            assertThrows(IllegalArgumentException::class.java) { ObservationQualificationCodec.decode(withoutRequiredField) }
        }
        assertThrows(IllegalArgumentException::class.java) { payload().copy(schemaVersion = 2) }
    }

    @Test
    fun should_reject_quoted_or_mismatched_json_number_and_integer_types_at_every_level() {
        val numbers = listOf("diamCm", "hauteurM", "precisionM", "altitudeM")
        numbers.forEach { field ->
            listOf(JsonPrimitive("18.0"), JsonPrimitive(true), JsonObject(emptyMap()), JsonPrimitive("NaN"))
                .forEach { value -> assertInvalid(listOf("basis", field), value) }
        }
        val integers = listOf(
            listOf("basis", "timestamp"), listOf("basis", "observationVersion"),
            listOf("draft", "diameter", "observedAt"), listOf("draft", "height", "observedAt"),
            listOf("draft", "position", "capturedAt"),
            listOf("copiedFrom", "revision"), listOf("copiedFrom", "updatedAt"),
        )
        integers.forEach { path ->
            listOf(JsonPrimitive("1"), JsonPrimitive(1.0), JsonPrimitive(false), JsonObject(emptyMap()))
                .forEach { value -> assertInvalid(path, value) }
        }
        assertInvalid(listOf("basis", "observationVersion"), JsonPrimitive(Long.MAX_VALUE))
        assertInvalid(listOf("basis", "diamCm"), Json.parseToJsonElement("1e309"))
    }

    @Test
    fun should_reject_nonstring_json_identity_references_and_enum_values() {
        val strings = listOf(
            listOf("basis", "tigeId"), listOf("basis", "parcelleId"), listOf("basis", "placetteId"),
            listOf("basis", "sessionId"), listOf("basis", "originTigeId"), listOf("basis", "essenceCode"),
            listOf("basis", "gpsWkt"), listOf("draft", "support"),
            listOf("draft", "diameter", "origin"), listOf("draft", "diameter", "conventionReference"),
            listOf("draft", "diameter", "methodReference"), listOf("draft", "diameter", "sourceReference"),
            listOf("draft", "height", "nature"), listOf("draft", "height", "origin"),
            listOf("draft", "height", "support"), listOf("draft", "height", "methodReference"),
            listOf("draft", "height", "sourceReference"), listOf("draft", "position", "origin"),
            listOf("draft", "position", "methodReference"), listOf("draft", "position", "sourceReference"),
            listOf("copiedFrom", "tigeId"), listOf("copiedFrom", "updatedBy"),
        )
        strings.forEach { path ->
            listOf(JsonPrimitive(1), JsonPrimitive(true), JsonObject(emptyMap()))
                .forEach { value -> assertInvalid(path, value) }
        }
        assertInvalid(listOf("draft", "support"), JsonPrimitive("CLASS"))
        assertInvalid(listOf("draft", "height", "nature"), JsonPrimitive("UNKNOWN"))
        assertInvalid(listOf("draft", "diameter", "origin"), JsonPrimitive("AUTOMATIC"))
        assertInvalid(listOf("draft", "position", "origin"), JsonPrimitive("GPS"))
    }

    @Test
    fun should_reject_quoted_boolean_and_nonobject_nested_payloads() {
        listOf(JsonPrimitive("true"), JsonPrimitive(1), JsonObject(emptyMap()))
            .forEach { value -> assertInvalid(listOf("draft", "height", "pairedWithDiameter"), value) }
        listOf(listOf("basis"), listOf("draft"), listOf("draft", "diameter"),
            listOf("draft", "height"), listOf("draft", "position"), listOf("copiedFrom"))
            .forEach { path ->
                listOf(JsonPrimitive("{}"), JsonPrimitive(true), Json.parseToJsonElement("[]"))
                    .forEach { value -> assertInvalid(path, value) }
            }
        assertInvalid(listOf("basis"), JsonNull)
        assertInvalid(listOf("draft"), JsonNull)
    }

    @Test
    fun should_reject_unknown_fields_even_in_nested_objects_instead_of_silently_losing_them() {
        listOf(emptyList(), listOf("basis"), listOf("draft"), listOf("draft", "diameter"),
            listOf("draft", "height"), listOf("draft", "position"), listOf("copiedFrom"))
            .forEach { parent -> assertInvalid(parent + "unexpected", JsonPrimitive("TEST-value")) }
    }

    @Test
    fun should_refuse_blank_oversized_references_and_negative_declared_times_without_trimming() {
        listOf("", " \n\t", "x".repeat(301)).forEach { reference ->
            assertThrows(IllegalArgumentException::class.java) { DiameterQualificationDraft(conventionReference = reference) }
            assertThrows(IllegalArgumentException::class.java) { DiameterQualificationDraft(methodReference = reference) }
            assertThrows(IllegalArgumentException::class.java) { DiameterQualificationDraft(sourceReference = reference) }
            assertThrows(IllegalArgumentException::class.java) { HeightQualificationDraft(methodReference = reference) }
            assertThrows(IllegalArgumentException::class.java) { HeightQualificationDraft(sourceReference = reference) }
            assertThrows(IllegalArgumentException::class.java) { PositionQualificationDraft(methodReference = reference) }
            assertThrows(IllegalArgumentException::class.java) { PositionQualificationDraft(sourceReference = reference) }
        }
        assertThrows(IllegalArgumentException::class.java) { DiameterQualificationDraft(observedAt = -1) }
        assertThrows(IllegalArgumentException::class.java) { HeightQualificationDraft(observedAt = -1) }
        assertThrows(IllegalArgumentException::class.java) { PositionQualificationDraft(capturedAt = -1) }
        val reference = " TEST-declared-reference "
        assertEquals(reference, roundtrip(ObservationQualificationDraft(
            diameter = DiameterQualificationDraft(sourceReference = reference),
        )).draft.diameter?.sourceReference)
        assertEquals("x".repeat(300), DiameterQualificationDraft(methodReference = "x".repeat(300)).methodReference)
    }

    @Test
    fun should_refuse_payloads_at_the_limit_before_parsing_and_enforce_the_same_bound_when_encoding() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            ObservationQualificationCodec.decode(" ".repeat(32 * 1024))
        }
        assertEquals("OBSERVATION_QUALIFICATION_PAYLOAD_TOO_LARGE", failure.message)
        val belowLimit = ObservationQualificationCodec.encode(payload()).padEnd(32 * 1024 - 1, ' ')
        assertEquals(payload(), ObservationQualificationCodec.decode(belowLimit))
        val escapedReference = 0.toChar().toString().repeat(300)
        val largeBasis = payload().basis.copy(
            tigeId = escapedReference, parcelleId = escapedReference, placetteId = escapedReference,
            sessionId = escapedReference, originTigeId = escapedReference, essenceCode = escapedReference,
            gpsWkt = 0.toChar().toString().repeat(4096),
        )
        val encodingFailure = assertThrows(IllegalArgumentException::class.java) {
            ObservationQualificationCodec.encode(payload().copy(basis = largeBasis))
        }
        assertEquals("OBSERVATION_QUALIFICATION_PAYLOAD_TOO_LARGE", encodingFailure.message)
    }

    @Test
    fun should_refuse_malformed_or_nonobject_root_payloads() {
        listOf("", "null", "[]", "1", "{", "{schemaVersion:1}")
            .forEach { encoded ->
                assertThrows(IllegalArgumentException::class.java) { ObservationQualificationCodec.decode(encoded) }
            }
    }

    private fun assertInvalid(path: List<String>, value: JsonElement) {
        val encoded = replaceField(encodedFullPayload(), path, value).toString()
        assertThrows(IllegalArgumentException::class.java) { ObservationQualificationCodec.decode(encoded) }
    }

    private fun replaceField(parent: JsonObject, path: List<String>, value: JsonElement): JsonObject {
        val field = path.first()
        val replacement = if (path.size == 1) value else
            replaceField(parent[field] as JsonObject, path.drop(1), value)
        return JsonObject(parent + (field to replacement))
    }

    private fun encodedFullPayload(): JsonObject = Json.parseToJsonElement(
        ObservationQualificationCodec.encode(payload(fullDraft()).copy(
            copiedFrom = ObservationQualificationCopySource("TEST-source-tree", 2, 80, "TEST-source-author"),
        )),
    ) as JsonObject

    private fun roundtrip(draft: ObservationQualificationDraft): ObservationQualificationPayload =
        ObservationQualificationCodec.decode(ObservationQualificationCodec.encode(payload(draft)))

    private fun payload(draft: ObservationQualificationDraft = ObservationQualificationDraft()) =
        ObservationQualificationPayload(basis = ObservationMeasurementBasis.capture(tige()), draft = draft)

    private fun fullDraft() = ObservationQualificationDraft(
        support = ObservationSupport.INDIVIDUAL_TREE,
        diameter = DiameterQualificationDraft(
            origin = MeasurementOrigin.MEASURED,
            conventionReference = "TEST-convention",
            methodReference = "TEST-diameter-method",
            sourceReference = "TEST-diameter-source",
            observedAt = 90,
        ),
        height = HeightQualificationDraft(
            nature = HeightNature.TOTAL,
            origin = MeasurementOrigin.MEASURED,
            support = ObservationSupport.INDIVIDUAL_TREE,
            pairedWithDiameter = true,
            methodReference = "TEST-height-method",
            sourceReference = "TEST-height-source",
            observedAt = 91,
        ),
        position = PositionQualificationDraft(
            origin = PositionOrigin.CAPTURED_FOR_OBSERVATION,
            methodReference = "TEST-position-method",
            sourceReference = "TEST-position-source",
            capturedAt = 92,
        ),
    )

    private fun record(payload: ObservationQualificationPayload = payload()) = ObservationQualificationRecord(
        tigeId = payload.basis.tigeId,
        revision = 1,
        payload = payload,
        createdAt = 100,
        createdBy = "TEST-author",
        updatedAt = 100,
        updatedBy = "TEST-author",
    )

    private fun tige() = Tige(
        id = "TEST-tree", parcelleId = "TEST-parcelle", placetteId = "TEST-plot", sessionId = "TEST-session",
        essenceCode = "QRO", diamCm = 40.0, hauteurM = 18.0, gpsWkt = "POINT(0 0)", precisionM = 3.0,
        altitudeM = 101.0, timestamp = 100, note = null, produit = null, fCoef = null, valueEur = null,
    )
}
