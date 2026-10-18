package com.forestry.counter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SurveyContextTest {
    @Test
    fun should_preserve_unknown_facts_when_roundtripping_an_empty_draft() {
        val draft = SurveyContextDraft()
        val decoded = SurveyContextCodec.decode(SurveyContextCodec.encode(draft))
        assertEquals(draft, decoded)
        assertNull(decoded.purpose)
        assertNull(decoded.populationScope)
        assertNull(decoded.populationReference)
        assertNull(decoded.protocolReference)
        assertNull(decoded.sourceReference)
        assertNull(decoded.inventoryComplete)
        assertNull(decoded.inventoriedHorizontalAreaM2)
        assertNull(decoded.areaSourceReference)
    }

    @Test
    fun should_distinguish_unknown_incomplete_and_complete_declarations() {
        listOf(null, false, true).forEach { complete ->
            val decoded = SurveyContextCodec.decode(
                SurveyContextCodec.encode(SurveyContextDraft(inventoryComplete = complete)),
            )
            assertEquals(complete, decoded.inventoryComplete)
            assertNull(decoded.protocolReference)
            assertNull(decoded.inventoriedHorizontalAreaM2)
        }
    }

    @Test
    fun should_keep_each_purpose_independent_from_population_and_completeness() {
        SurveyPurpose.entries.forEach { purpose ->
            val draft = SurveyContextDraft(purpose = purpose)
            val decoded = SurveyContextCodec.decode(SurveyContextCodec.encode(draft))
            assertEquals(purpose, decoded.purpose)
            assertNull(decoded.populationScope)
            assertNull(decoded.inventoryComplete)
            assertNull(decoded.protocolReference)
        }
    }

    @Test
    fun should_preserve_explicit_scope_and_references_without_inventing_a_qualification() {
        SurveyPopulationScope.entries.forEach { scope ->
            val draft = SurveyContextDraft(
                purpose = SurveyPurpose.COMBINED,
                populationReference = "TEST-population",
                populationScope = scope,
                protocolReference = "TEST-protocol",
                protocolVersion = "TEST-version-1",
                sourceReference = "TEST-source",
                inventoryComplete = false,
                inventoriedHorizontalAreaM2 = 1250.5,
                areaSourceReference = "TEST-horizontal-area-measurement",
            )
            assertEquals(draft, SurveyContextCodec.decode(SurveyContextCodec.encode(draft)))
        }
    }

    @Test
    fun should_keep_a_partial_area_in_square_metres_without_filling_its_source() {
        val decoded = SurveyContextCodec.decode(
            """{"schemaVersion":1,"inventoriedHorizontalAreaM2":10000.0}""",
        )
        assertEquals(10000.0, requireNotNull(decoded.inventoriedHorizontalAreaM2), 0.0)
        assertNull(decoded.areaSourceReference)
        assertNull(decoded.inventoryComplete)
        assertNull(decoded.populationScope)
    }

    @Test
    fun should_reject_nonfinite_zero_and_negative_horizontal_areas() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, -0.0, -1.0)
            .forEach { area ->
                assertThrows(IllegalArgumentException::class.java) {
                    SurveyContextDraft(inventoriedHorizontalAreaM2 = area)
                }
            }
        listOf("0", "-1", "1e309", "NaN", "Infinity").forEach { area ->
            assertThrows(IllegalArgumentException::class.java) {
                SurveyContextCodec.decode("""{"schemaVersion":1,"inventoriedHorizontalAreaM2":$area}""")
            }
        }
    }

    @Test
    fun should_reject_missing_or_unsupported_schema_versions() {
        listOf("{}", "{\"purpose\":\"MARKING\"}",
            "{\"schemaVersion\":0}", "{\"schemaVersion\":2}",
            "{\"schemaVersion\":null}", "{\"schemaVersion\":\"1\"}",
            "{\"schemaVersion\":1.0}").forEach { payload ->
            assertThrows(IllegalArgumentException::class.java) { SurveyContextCodec.decode(payload) }
        }
        assertThrows(IllegalArgumentException::class.java) { SurveyContextDraft(schemaVersion = 2) }
    }

    @Test
    fun should_reject_unknown_fields_and_enum_values_instead_of_dropping_them() {
        listOf(
            """{"schemaVersion":1,"inventoriedAreaHa":1}""",
            """{"schemaVersion":1,"purpose":"MARTELAGE"}""",
            """{"schemaVersion":1,"populationScope":"TOTAL_FOREST"}""",
        ).forEach { payload ->
            assertThrows(IllegalArgumentException::class.java) { SurveyContextCodec.decode(payload) }
        }
    }

    @Test
    fun should_reject_malformed_or_nonobject_payloads() {
        listOf("", "null", "[]", "1", "{", "{schemaVersion:1}",
            """{"schemaVersion":1,"inventoryComplete":{}}""")
            .forEach { payload ->
                assertThrows(IllegalArgumentException::class.java) { SurveyContextCodec.decode(payload) }
            }
    }

    @Test
    fun should_reject_quoted_or_mismatched_number_and_boolean_types() {
        listOf(
            """{"schemaVersion":1,"inventoriedHorizontalAreaM2":"10"}""",
            """{"schemaVersion":1,"inventoriedHorizontalAreaM2":true}""",
            """{"schemaVersion":1,"inventoriedHorizontalAreaM2":{}}""",
            """{"schemaVersion":1,"inventoryComplete":"false"}""",
            """{"schemaVersion":1,"inventoryComplete":0}""",
            """{"schemaVersion":1,"protocolReference":100}""",
        ).forEach { payload ->
            assertThrows(IllegalArgumentException::class.java) { SurveyContextCodec.decode(payload) }
        }
        assertEquals(false, SurveyContextCodec.decode(
            """{"schemaVersion":1,"inventoryComplete":false,"inventoriedHorizontalAreaM2":10}""",
        ).inventoryComplete)
    }

    @Test
    fun should_refuse_oversized_payloads_before_parsing() {
        listOf(
            " ".repeat(16 * 1024 + 1),
            """{"schemaVersion":1,"sourceReference":"${"x".repeat(16 * 1024)}"}""",
        ).forEach { payload ->
            val failure = assertThrows(IllegalArgumentException::class.java) {
                SurveyContextCodec.decode(payload)
            }
            assertEquals("SURVEY_CONTEXT_PAYLOAD_TOO_LARGE", failure.message)
        }
        val atLimit = """{"schemaVersion":1}""".padEnd(16 * 1024, ' ')
        assertEquals(SurveyContextDraft(), SurveyContextCodec.decode(atLimit))
    }

    @Test
    fun should_accept_all_maximum_references_even_with_six_character_json_escapes() {
        val escapedCharacter = 0.toChar().toString()
        val reference = escapedCharacter.repeat(300)
        val draft = SurveyContextDraft(
            purpose = SurveyPurpose.COMBINED,
            populationReference = reference,
            populationScope = SurveyPopulationScope.REMAINING_STEMS,
            protocolReference = reference,
            protocolVersion = escapedCharacter.repeat(100),
            sourceReference = reference,
            inventoryComplete = true,
            inventoriedHorizontalAreaM2 = Double.MAX_VALUE,
            areaSourceReference = reference,
        )
        val encoded = SurveyContextCodec.encode(draft)
        assertTrue(encoded.length >= 7800)
        assertTrue(encoded.length < 16 * 1024)
        assertEquals(draft, SurveyContextCodec.decode(encoded))
    }

    @Test
    fun should_require_json_strings_for_purpose_and_population_scope() {
        listOf("purpose", "populationScope").forEach { field ->
            listOf("1", "true", "{}", "[]").forEach { value ->
                val failure = assertThrows(IllegalArgumentException::class.java) {
                    SurveyContextCodec.decode("""{"schemaVersion":1,"$field":$value}""")
                }
                assertEquals("SURVEY_CONTEXT_INVALID_ENUM_TYPE", failure.message)
            }
        }
        assertEquals(SurveyContextDraft(), SurveyContextCodec.decode(
            """{"schemaVersion":1,"purpose":null,"populationScope":null}""",
        ))
    }

    @Test
    fun should_refuse_blank_and_oversized_references_without_trimming_user_values() {
        listOf("", " \n\t", "x".repeat(301)).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { SurveyContextDraft(populationReference = value) }
            assertThrows(IllegalArgumentException::class.java) { SurveyContextDraft(protocolReference = value) }
            assertThrows(IllegalArgumentException::class.java) { SurveyContextDraft(sourceReference = value) }
            assertThrows(IllegalArgumentException::class.java) { SurveyContextDraft(areaSourceReference = value) }
        }
        val paddedReference = " TEST-declared-reference "
        assertEquals(paddedReference, SurveyContextCodec.decode(SurveyContextCodec.encode(
            SurveyContextDraft(sourceReference = paddedReference),
        )).sourceReference)
        assertEquals("x".repeat(300), SurveyContextDraft(protocolReference = "x".repeat(300)).protocolReference)
    }

    @Test
    fun should_enforce_the_shorter_protocol_version_bound() {
        assertEquals("x".repeat(100), SurveyContextDraft(protocolVersion = "x".repeat(100)).protocolVersion)
        listOf("", " ", "x".repeat(101)).forEach { version ->
            assertThrows(IllegalArgumentException::class.java) { SurveyContextDraft(protocolVersion = version) }
        }
    }

    @Test
    fun should_encode_explicit_version_and_null_without_fabricating_a_population() {
        val payload = SurveyContextCodec.encode(SurveyContextDraft(inventoryComplete = false))
        assertTrue(payload.contains("\"schemaVersion\":1"))
        assertTrue(payload.contains("\"inventoryComplete\":false"))
        assertTrue(payload.contains("\"populationScope\":null"))
        assertTrue(payload.contains("\"inventoriedHorizontalAreaM2\":null"))
        assertFalse(payload.contains("surfaceHa"))
    }

    @Test
    fun should_keep_creation_and_last_edit_provenance_distinct() {
        val created = record()
        val edited = created.copy(revision = 2, updatedAt = 200, updatedBy = "TEST-editor")
        assertEquals(100L, edited.createdAt)
        assertEquals("TEST-creator", edited.createdBy)
        assertEquals(200L, edited.updatedAt)
        assertEquals("TEST-editor", edited.updatedBy)
        assertFalse(edited.isSealed)
        assertNull(edited.copiedFromSessionId)
    }

    @Test
    fun should_refuse_invalid_record_revision_or_missing_provenance() {
        assertThrows(IllegalArgumentException::class.java) { record().copy(revision = 0) }
        assertThrows(IllegalArgumentException::class.java) { record().copy(createdAt = -1) }
        assertThrows(IllegalArgumentException::class.java) { record().copy(updatedAt = -1) }
        assertThrows(IllegalArgumentException::class.java) { record().copy(sessionId = "") }
        assertThrows(IllegalArgumentException::class.java) { record().copy(createdBy = " ") }
        assertThrows(IllegalArgumentException::class.java) { record().copy(updatedBy = "") }
        assertThrows(IllegalArgumentException::class.java) { record().copy(copiedFromSessionId = " ") }
    }

    private fun record() = SurveyContextRecord(
        sessionId = "TEST-session",
        revision = 1,
        draft = SurveyContextDraft(),
        createdAt = 100,
        createdBy = "TEST-creator",
        updatedAt = 100,
        updatedBy = "TEST-creator",
    )
}
