package com.forestry.counter.domain.usecase.export

import com.forestry.counter.domain.calculation.SynthesisParameters
import com.forestry.counter.domain.calculation.SynthesisReportCalculator
import com.forestry.counter.domain.calculation.SynthesisReport
import com.forestry.counter.domain.calculation.tarifs.TarifSelection
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.model.Tige
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class SpecializedSynthesisDocumentTest {
    private val parcel = Parcelle("P", null, name = "TEST parcel", surfaceHa = 1.0,
        shape = null, slopePct = null, aspect = null, access = null, altitudeM = null,
        objectifType = null, objectifVal = null, tolerancePct = null, samplingMode = null,
        sampleAreaM2 = null, targetSpeciesCsv = null, srid = 4326, remarks = null,
        createdAt = 1, updatedAt = 1)
    private val plot = Placette("Q", "P", "TEST plot", "ENTIRE_PARCEL", null,
        10000.0, null, createdAt = 1, updatedAt = 1)
    private val parameters = SynthesisParameters(selectedPlotId = "Q")
    private val json = Json { encodeDefaults = true }

    private fun tree(id: String = "T", value: Double? = null) = Tige(
        id, "P", "Q", "CH_PED", 40.0, 20.0, null, null, null, timestamp = 1234,
        note = null, produit = "TEST product", fCoef = null, valueEur = value,
        qualite = 2, sessionId = "S", source = "TEST observation source")

    private fun illustrated(trees: List<Tige> = listOf(tree()), calculationParameters: SynthesisParameters = parameters): IllustratedSynthesisDocument {
        val report = SynthesisReportCalculator.calculate(parcel, listOf(plot), trees,
            mapOf("CH_PED" to "TEST oak"), calculationParameters, "S", "C", 1, 2)
        val doc = SynthesisDocument(report, false, null, null,
            json.encodeToJsonElement(calculationParameters).jsonObject, trees)
        return IllustratedSynthesisDocument(doc, parcel, listOf(plot), timeZoneId = "Europe/Paris")
    }

    private fun revenue(trees: List<Tige> = listOf(tree())) = SpecializedSynthesisDocument(
        SpecializedReportType.REVENUE, illustrated(trees))

    private fun costLine(id: String = "L", quantity: String? = "2", price: String? = "12.345") =
        SpecializedCostLine(id, "TEST extraction operation", quantity, SpecializedCostUnit.HOUR,
            price, "TEST user quotation", "2026-10-07", SpecializedCostBasis.USER_QUOTATION)

    private fun costs(lines: List<SpecializedCostLine>) = SpecializedSynthesisDocument(
        SpecializedReportType.COSTS, illustrated(), lines)

    @Test fun no_entered_value_is_unknown_and_never_becomes_zero() {
        val data = buildSpecializedRevenueData(revenue())
        assertNull(data.totalCents)
        assertNull(data.rows.single().amountCents)
        assertEquals(0, data.pricedCount)
        assertEquals(1, data.missingCount)
        assertEquals(0, data.invalidCount)
        assertFalse(data.isComplete)
    }

    @Test fun unavailable_unqualified_volume_is_preserved_in_all_report_types_without_inventing_amounts() {
        val captured = illustrated(listOf(tree(value = 17.05)),
            parameters.copy(tarifSelection = TarifSelection("COEF_FORME")))
        val source = captured.document
        val original = json.encodeToString(source)
        assertEquals(SynthesisReport.CURRENT_ENGINE_VERSION, source.report.engineVersion)
        assertNull(source.report.volumeM3)
        assertNull(source.report.volumePerHa)
        assertEquals(0, source.report.volumeComputedCount)
        assertEquals(mapOf("UNQUALIFIED_TARIF" to 1), currentSynthesisVolumeReasons(source.report))
        assertTrue(needsSynthesisVolumeQualification(source.report))
        for (type in SpecializedReportType.entries) {
            val input = SpecializedSynthesisDocument(type, captured)
            assertEquals(1705L, buildSpecializedRevenueData(input).totalCents)
            assertNull(buildSpecializedCostData(input).totalCents)
            assertEquals(1, buildSpecializedEnvironmentalData(input).observationCount)
            assertEquals(original, json.encodeToString(input.illustrated.document))
            assertTrue(input.illustrated.document.report.perEssence.all { it.volumeM3 == null })
        }
    }

    @Test fun historical_v2_reports_retain_their_content_and_receive_no_new_volume_notice() {
        val captured = illustrated(listOf(tree("known", 17.05), tree("missing"))).let { input ->
            val current = input.document.report
            val legacy = current.copy(
                engineVersion = SynthesisReport.ENGINE_VERSION,
                volumeM3 = 0.89123,
                volumeComputedCount = 1,
                methods = listOf("TEST historical tariff"),
                uncomputedReasons = mapOf("MISSING_HEIGHT" to 1),
                perEssence = current.perEssence.map { it.copy(volumeM3 = 0.89123, volumeComputedCount = 1) },
            )
            input.copy(document = input.document.copy(report = legacy))
        }
        val original = json.encodeToString(captured.document)
        assertTrue(currentSynthesisVolumeReasons(captured.document.report).isEmpty())
        assertFalse(needsSynthesisVolumeQualification(captured.document.report))
        for (type in SpecializedReportType.entries) {
            val input = SpecializedSynthesisDocument(type, captured)
            val restored = json.decodeFromString<SpecializedSynthesisDocument>(json.encodeToString(input))
            buildIllustratedSynthesisData(restored.illustrated)
            buildSpecializedRevenueData(restored)
            buildSpecializedCostData(restored)
            buildSpecializedEnvironmentalData(restored)
            assertEquals(original, json.encodeToString(restored.illustrated.document))
            assertEquals(0.89123, restored.illustrated.document.report.volumeM3 ?: Double.NaN, 0.0)
            assertEquals(mapOf("MISSING_HEIGHT" to 1), restored.illustrated.document.report.uncomputedReasons)
            assertTrue(currentSynthesisVolumeReasons(restored.illustrated.document.report).isEmpty())
        }
    }

    @Test fun explicitly_entered_zero_is_available_without_becoming_a_guaranteed_revenue() {
        val data = buildSpecializedRevenueData(revenue(listOf(tree(value = 0.0))))
        assertEquals(0L, data.totalCents)
        assertEquals(1, data.pricedCount)
        assertEquals(0, data.missingCount)
        assertTrue(data.isComplete)
    }

    @Test fun partial_revenue_has_only_known_entered_amounts_and_real_source_metadata() {
        val input = revenue(listOf(tree("known", 12.345), tree("unknown")))
        val original = json.encodeToString(input.illustrated.document)
        val data = buildSpecializedRevenueData(input)
        assertEquals(1235L, data.totalCents)
        assertEquals(1, data.pricedCount)
        assertEquals(1, data.missingCount)
        assertFalse(data.isComplete)
        val row = data.rows.first()
        assertEquals("known", row.observationId)
        assertEquals("CH_PED", row.essenceCode)
        assertEquals("TEST oak", row.essenceName)
        assertEquals(2, row.quality)
        assertEquals("TEST product", row.product)
        assertEquals("TEST observation source", row.source)
        assertEquals(1234L, row.observedAt)
        assertEquals(original, json.encodeToString(input.illustrated.document))
        assertNull(input.illustrated.document.report.volumeM3)
    }

    @Test fun missing_source_is_not_replaced_with_an_invented_price_provenance() {
        val data = buildSpecializedRevenueData(revenue(listOf(tree(value = 10.0).copy(source = null))))
        assertNull(data.rows.single().source)
        assertEquals(1000L, data.totalCents)
    }

    @Test fun sums_are_exact_cents_from_displayed_rows_without_rewriting_original_double_values() {
        val input = revenue(listOf(tree("one", 0.005), tree("two", 0.005)))
        val data = buildSpecializedRevenueData(input)
        assertEquals(listOf(1L, 1L), data.rows.map { it.amountCents })
        assertEquals(2L, data.totalCents)
        assertEquals(0.01, input.illustrated.document.report.enteredValueEur!!, 0.0)
        assertEquals(0.005, input.illustrated.document.observations.first().valueEur!!, 0.0)
    }

    @Test fun invalid_recorded_values_remain_unavailable_and_do_not_lower_a_subtotal() {
        val data = buildSpecializedRevenueData(revenue(listOf(
            tree("negative", -3.0), tree("nan", Double.NaN), tree("infinite", Double.POSITIVE_INFINITY),
            tree("valid", 2.0), tree("missing"))))
        assertEquals(200L, data.totalCents)
        assertEquals(3, data.invalidCount)
        assertEquals(1, data.missingCount)
        assertEquals(1, data.pricedCount)
        assertFalse(data.isComplete)
    }

    @Test fun monetary_line_or_total_overflow_is_refused_rather_than_wrapped_or_clamped() {
        assertThrows(IllegalArgumentException::class.java) {
            buildSpecializedRevenueData(revenue(listOf(tree(value = Double.MAX_VALUE))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildSpecializedRevenueData(revenue(listOf(
                tree("one", 50_000_000_000_000_000.0), tree("two", 50_000_000_000_000_000.0))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            costLine(quantity = "999999999999999999", price = "999999999999999999")
        }
        assertThrows(IllegalArgumentException::class.java) {
            costs(listOf(costLine("max", "1", "92233720368547758.07"), costLine("extra", "1", "0.01")))
        }
    }

    @Test fun missing_cost_operands_stay_unknown_even_if_the_other_operand_is_zero() {
        val data = buildSpecializedCostData(costs(listOf(
            costLine("no-price", "0", null), costLine("no-quantity", null, "0"))))
        assertNull(data.totalCents)
        assertEquals(0, data.computedCount)
        assertEquals(2, data.missingCount)
        assertFalse(data.isComplete)
        assertTrue(data.rows.all { it.amountCents == null })
    }

    @Test fun known_costs_multiply_exactly_and_round_each_line_once() {
        val data = buildSpecializedCostData(costs(listOf(
            costLine("one", "0.1", "0.2"), costLine("two", "1", "0.005"))))
        assertEquals(listOf(2L, 1L), data.rows.map { it.amountCents })
        assertEquals(3L, data.totalCents)
        assertEquals(2, data.computedCount)
        assertTrue(data.isComplete)
        assertEquals("0.1", data.rows.first().line.quantity)
        assertEquals("0.2", data.rows.first().line.unitPriceEur)
    }

    @Test fun explicit_zero_cost_and_partial_cost_have_distinct_coverage() {
        val zero = buildSpecializedCostData(costs(listOf(costLine(price = "0"))))
        assertEquals(0L, zero.totalCents)
        assertTrue(zero.isComplete)
        val partial = buildSpecializedCostData(costs(listOf(costLine("known"), costLine("unknown", price = null))))
        assertEquals(2469L, partial.totalCents)
        assertEquals(1, partial.computedCount)
        assertEquals(1, partial.missingCount)
        assertFalse(partial.isComplete)
    }

    @Test fun empty_input_does_not_assert_financial_coverage_or_an_exploitation_budget() {
        val noCosts = buildSpecializedCostData(costs(emptyList()))
        assertNull(noCosts.totalCents)
        assertEquals(0, noCosts.lineCount)
        assertFalse(noCosts.isComplete)
        val noRevenue = buildSpecializedRevenueData(revenue(emptyList()))
        assertNull(noRevenue.totalCents)
        assertEquals(0, noRevenue.observationCount)
        assertFalse(noRevenue.isComplete)
    }

    @Test fun decimal_syntax_refuses_nonfinite_negative_exponent_and_excessive_precision() {
        for (raw in listOf("NaN", "Infinity", "-1", "+1", "1e3", "1,2", " 1", "1 ", ".5",
            "1.", "1.0000001", "1234567890123456789")) {
            assertThrows(raw, IllegalArgumentException::class.java) { costLine(quantity = raw) }
            assertThrows(raw, IllegalArgumentException::class.java) { costLine(price = raw) }
        }
    }

    @Test fun fixed_operation_is_applied_once_and_cannot_be_multiplied_as_a_variable_quantity() {
        val fixed = costLine(quantity = "1.000").copy(unit = SpecializedCostUnit.FIXED_JOB)
        assertEquals(1235L, buildSpecializedCostData(costs(listOf(fixed))).totalCents)
        assertThrows(IllegalArgumentException::class.java) {
            fixed.copy(quantity = "2")
        }
        val unknown = fixed.copy(quantity = null)
        assertNull(buildSpecializedCostData(costs(listOf(unknown))).totalCents)
    }

    @Test fun source_and_labels_are_required_and_price_dates_are_valid_explicit_dates() {
        assertThrows(IllegalArgumentException::class.java) { costLine().copy(id = " ") }
        assertThrows(IllegalArgumentException::class.java) { costLine().copy(label = " ") }
        assertThrows(IllegalArgumentException::class.java) { costLine().copy(provenance = " ") }
        for (date in listOf("2026-02-29", "2026-13-01", "2026-10-7", "", "tomorrow")) {
            assertThrows(date, IllegalArgumentException::class.java) { costLine().copy(priceDate = date) }
        }
        assertEquals("2024-02-29", costLine().copy(priceDate = "2024-02-29").priceDate)
        assertNull(costLine().copy(priceDate = null).priceDate)
    }

    @Test fun duplicate_operations_and_unknown_format_versions_cannot_be_archived_as_valid() {
        assertThrows(IllegalArgumentException::class.java) { costs(listOf(costLine(), costLine())) }
        assertThrows(IllegalArgumentException::class.java) { costs(emptyList()).copy(formatVersion = 2) }
        val encoded = json.encodeToString(costs(listOf(costLine())))
        assertThrows(Exception::class.java) {
            json.decodeFromString<SpecializedSynthesisDocument>(encoded.replace("\"formatVersion\":1", "\"formatVersion\":2"))
        }
    }

    @Test fun other_report_types_cannot_hide_cost_inputs_in_construction_copy_or_serialized_replay() {
        val costInput = costs(listOf(costLine()))
        val encodedCosts = json.encodeToString(costInput)
        for (type in listOf(SpecializedReportType.REVENUE, SpecializedReportType.ENVIRONMENTAL)) {
            assertThrows(IllegalArgumentException::class.java) {
                SpecializedSynthesisDocument(type, costInput.illustrated, costInput.costLines)
            }
            assertThrows(IllegalArgumentException::class.java) {
                costInput.copy(type = type)
            }
            assertThrows(Exception::class.java) {
                json.decodeFromString<SpecializedSynthesisDocument>(encodedCosts.replace(
                    "\"type\":\"COSTS\"", "\"type\":\"${type.name}\""))
            }
        }
    }

    @Test fun versioned_envelope_roundtrips_all_types_and_source_bytes_without_upgrading_historical_data() {
        val frozen = illustrated(listOf(tree(value = 17.05)))
        val original = json.encodeToString(frozen.document)
        for (type in SpecializedReportType.values()) {
            val input = SpecializedSynthesisDocument(type, frozen, if (type == SpecializedReportType.COSTS) listOf(costLine()) else emptyList())
            val restored = json.decodeFromString<SpecializedSynthesisDocument>(json.encodeToString(input))
            assertEquals(input, restored)
            assertEquals(1, restored.formatVersion)
            assertEquals("Europe/Paris", restored.illustrated.timeZoneId)
            assertEquals(original, json.encodeToString(restored.illustrated.document))
        }
    }

    @Test fun environmental_projection_reports_only_recorded_categories_and_health_not_a_diagnostic() {
        val trees = listOf(
            tree("habitat").copy(isTigeHabitat = true, categorie = "TEST bio", etatSanitaire = "TEST affected"),
            tree("ordinary").copy(categorie = "TEST bio", qualite = null, hauteurM = null),
            tree("uncategorized").copy(categorie = " ", etatSanitaire = " ", qualite = null))
        val input = SpecializedSynthesisDocument(SpecializedReportType.ENVIRONMENTAL, illustrated(trees))
        val original = json.encodeToString(input.illustrated.document)
        val data = buildSpecializedEnvironmentalData(input)
        assertEquals(3, data.observationCount)
        assertEquals(1, data.habitatCount)
        assertEquals(mapOf("TEST bio" to 2), data.categories)
        assertEquals(mapOf("TEST affected" to 1), data.healthStates)
        assertEquals(1, data.qualityCount)
        assertEquals(0, data.locatedCount)
        assertEquals(2, data.measuredHeightCount)
        assertEquals(10000.0, data.referenceAreaM2!!, 0.0)
        assertEquals("ENTIRE_PARCEL", data.surfaceReason)
        assertEquals(original, json.encodeToString(input.illustrated.document))
    }

    @Test fun unresolved_reference_area_does_not_become_an_observed_surface_or_ecological_extrapolation() {
        val captured = illustrated().let { it.copy(document = it.document.copy(report =
            it.document.report.copy(surfaceM2 = null, surfaceReason = "SAMPLING_BOUNDARY_UNKNOWN"))) }
        val data = buildSpecializedEnvironmentalData(SpecializedSynthesisDocument(
            SpecializedReportType.ENVIRONMENTAL, captured))
        assertNull(data.referenceAreaM2)
        assertEquals("SAMPLING_BOUNDARY_UNKNOWN", data.surfaceReason)
        assertEquals(1, data.observationCount)
    }
}
