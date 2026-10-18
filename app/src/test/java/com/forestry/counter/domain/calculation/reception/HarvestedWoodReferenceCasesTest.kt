package com.forestry.counter.domain.calculation.reception

import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Références analytiques identiques au serveur ; aucune donnée terrain fictive dans le produit. */
class HarvestedWoodReferenceCasesTest {
    @Test
    fun should_match_shared_aggregation_boundaries() {
        val document = requireNotNull(javaClass.getResourceAsStream("/reception/measurement_reference_cases.json"))
            .bufferedReader().use { it.readText() }
        val cases = Json.parseToJsonElement(document).jsonObject.getValue("aggregate_cases").jsonArray
        assertEquals(2, cases.size)
        cases.forEach { element ->
            val case = element.jsonObject
            val inputs = case.getValue("heights_m").jsonArray.map {
                RectangularStackMeasurement(1.0, 1.0, it.jsonPrimitive.double, "boundary-test-protocol")
            }
            if (case.getValue("accepted").jsonPrimitive.boolean) {
                assertEquals(case.getValue("volume_m3").jsonPrimitive.double,
                    HarvestedWoodCalculator.aggregate(inputs).volumeM3, 0.0)
            } else {
                assertThrows(IllegalArgumentException::class.java) { HarvestedWoodCalculator.aggregate(inputs) }
            }
        }
    }

    @Test
    fun should_match_the_same_eight_analytic_cases_as_gsie() {
        val stream = javaClass.getResourceAsStream("/reception/measurement_reference_cases.json")
        assertNotNull("Le jeu de références analytiques doit être embarqué dans les tests.", stream)
        val document = requireNotNull(stream).bufferedReader().use { it.readText() }
        val root = Json.parseToJsonElement(document).jsonObject
        assertEquals(HarvestedWoodCalculator.CONTRACT_VERSION, root.getValue("schema_version").jsonPrimitive.content)
        val cases = root.getValue("cases").jsonArray
        assertEquals(8, cases.size)
        cases.forEach { element ->
            val case = element.jsonObject
            val input = case.getValue("input").jsonObject
            val kind = input.getValue("kind").jsonPrimitive.content
            fun number(key: String): Double = input.getValue(key).jsonPrimitive.double
            fun optionalNumber(key: String): Double? = input[key]?.jsonPrimitive?.double
            val measurement = when (kind) {
                "log" -> LogMeasurement(
                    method = RoundwoodVolumeMethod.valueOf(input.getValue("method").jsonPrimitive.content.uppercase(Locale.ROOT)),
                    lengthM = number("length_m"),
                    barkConvention = BarkConvention.valueOf(input.getValue("bark_convention").jsonPrimitive.content),
                    startDiameterCm = optionalNumber("start_diameter_cm"),
                    middleDiameterCm = optionalNumber("middle_diameter_cm"),
                    endDiameterCm = optionalNumber("end_diameter_cm"),
                )
                "rectangular-stack" -> RectangularStackMeasurement(
                    lengthM = number("length_m"), widthM = number("width_m"),
                    meanHeightM = number("mean_height_m"),
                    protocolReference = input.getValue("protocol_reference").jsonPrimitive.content,
                )
                else -> error("Référence analytique de type inconnu.")
            }
            val result = HarvestedWoodCalculator.calculate(measurement)
            val expected = case.getValue("volume_m3").jsonPrimitive.double
            assertEquals(case.getValue("id").jsonPrimitive.content, expected, result.volumeM3, expected * 1e-12)
            assertEquals(case.getValue("volume_kind").jsonPrimitive.content, result.kind.name)
            assertEquals(case.getValue("method_code").jsonPrimitive.content, result.methodCode)
            assertEquals("1.0.0", result.methodVersion)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun should_refuse_a_protocol_reference_exceeding_the_shared_contract() {
        HarvestedWoodCalculator.calculate(RectangularStackMeasurement(5.0, 2.0, 1.5, "a".repeat(301)))
    }

    @Test
    fun should_preserve_protocol_reference_exactly() {
        val input = RectangularStackMeasurement(5.0, 2.0, 1.5, " source-protocol-v1 ")
        assertEquals(input.protocolReference, HarvestedWoodCalculator.calculate(input).protocolReference)
    }
}
