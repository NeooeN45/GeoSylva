package com.forestry.counter.domain.wood

import com.forestry.counter.domain.calculation.reception.BarkConvention
import com.forestry.counter.domain.calculation.reception.HarvestedWoodCalculator
import com.forestry.counter.domain.calculation.reception.HarvestedWoodMeasurement
import com.forestry.counter.domain.calculation.reception.HarvestedWoodVolumeResult
import com.forestry.counter.domain.calculation.reception.LogMeasurement
import com.forestry.counter.domain.calculation.reception.RectangularStackMeasurement
import com.forestry.counter.domain.calculation.reception.RoundwoodVolumeMethod
import java.math.BigDecimal
import java.util.Collections

sealed class WoodObservationCalculation(
    val observation: RawWoodObservation,
    dimensions: Map<WoodField, ParsedWoodDimension>,
) {
    val dimensions: Map<WoodField, ParsedWoodDimension> =
        Collections.unmodifiableMap(LinkedHashMap(dimensions))
    val parserVersion: String get() = WoodRawDecimalParser.CONTRACT_VERSION

    class Calculated internal constructor(
        observation: RawWoodObservation,
        dimensions: Map<WoodField, ParsedWoodDimension>,
        val result: HarvestedWoodVolumeResult,
    ) : WoodObservationCalculation(observation, dimensions)

    class Uncalculable internal constructor(
        observation: RawWoodObservation,
        dimensions: Map<WoodField, ParsedWoodDimension>,
        diagnostics: List<WoodDiagnostic>,
    ) : WoodObservationCalculation(observation, dimensions) {
        val diagnostics: List<WoodDiagnostic> = Collections.unmodifiableList(ArrayList(diagnostics))
    }
}

/**
 * Qualifies raw inputs before delegating geometry to the existing calculation
 * kernel. Invalid values remain in the observation; no zero or guessed method
 * is substituted. All diagnostics prevent a result. No stock, payment, bark
 * correction or conversion from a stack envelope to solid wood is produced.
 */
object WoodObservationCalculator {
    fun calculate(observation: RawWoodObservation): WoodObservationCalculation {
        val qualification = Qualification()
        val input = when (val raw = observation.measurement) {
            is RawWoodMeasurement.Pile -> qualification.pile(raw)
            is RawWoodMeasurement.Log -> qualification.log(raw)
        }
        if (input == null || qualification.diagnostics.isNotEmpty()) {
            return WoodObservationCalculation.Uncalculable(
                observation, qualification.dimensions, qualification.diagnostics,
            )
        }
        // All domain constraints have been checked above. The kernel can still
        // reject unrepresentable intermediate areas, products or sums. Classify
        // this numerically, without inspecting its French exception messages.
        return try {
            WoodObservationCalculation.Calculated(
                observation, qualification.dimensions, HarvestedWoodCalculator.calculate(input),
            )
        } catch (_: IllegalArgumentException) {
            WoodObservationCalculation.Uncalculable(
                observation, qualification.dimensions,
                listOf(WoodDiagnostic(WoodField.RESULT, WoodDiagnosticCode.NUMERIC_RESULT_UNREPRESENTABLE)),
            )
        }
    }

    private class Qualification {
        val diagnostics = mutableListOf<WoodDiagnostic>()
        val dimensions = linkedMapOf<WoodField, ParsedWoodDimension>()

        fun pile(raw: RawWoodMeasurement.Pile): HarvestedWoodMeasurement? {
            when {
                raw.shapeCode.isNullOrBlank() -> add(WoodField.SHAPE, WoodDiagnosticCode.MISSING_SHAPE)
                raw.shapeCode != RawWoodMeasurement.Pile.RECTANGULAR_ENVELOPE ->
                    add(WoodField.SHAPE, WoodDiagnosticCode.UNSUPPORTED_SHAPE)
            }
            if (raw.protocolReference == null || raw.protocolReference.all { it.isWhitespace() || it == '\u0085' }) {
                add(WoodField.PROTOCOL, WoodDiagnosticCode.MISSING_PROTOCOL)
            }
            val length = dimension(raw.length, WoodField.LENGTH)
            val width = dimension(raw.width, WoodField.WIDTH)
            val height = dimension(raw.meanHeight, WoodField.MEAN_HEIGHT)
            if (diagnostics.isNotEmpty() || length == null || width == null || height == null) return null
            return RectangularStackMeasurement(length, width, height, raw.protocolReference!!)
        }

        fun log(raw: RawWoodMeasurement.Log): HarvestedWoodMeasurement? {
            val method = RoundwoodVolumeMethod.values().find { it.code == raw.methodCode }
            if (method == null) add(
                WoodField.METHOD,
                if (raw.methodCode.isNullOrBlank()) WoodDiagnosticCode.MISSING_METHOD else WoodDiagnosticCode.UNSUPPORTED_METHOD,
            )
            val bark = BarkConvention.values().find { it.name == raw.barkConventionCode }
            if (bark == null) add(
                WoodField.BARK_CONVENTION,
                if (raw.barkConventionCode.isNullOrBlank()) WoodDiagnosticCode.MISSING_BARK_CONVENTION
                else WoodDiagnosticCode.UNSUPPORTED_BARK_CONVENTION,
            )
            val length = dimension(raw.length, WoodField.LENGTH)
            val usesStart = method == RoundwoodVolumeMethod.SMALIAN || method == RoundwoodVolumeMethod.NEWTON
            val usesMiddle = method == RoundwoodVolumeMethod.HUBER || method == RoundwoodVolumeMethod.NEWTON
            val usesEnd = usesStart
            val start = section(raw.startDiameter, WoodField.START_DIAMETER, method, usesStart, allowZero = true)
            val middle = section(raw.middleDiameter, WoodField.MIDDLE_DIAMETER, method, usesMiddle,
                allowZero = method != RoundwoodVolumeMethod.HUBER)
            val end = section(raw.endDiameter, WoodField.END_DIAMETER, method, usesEnd, allowZero = true)
            val required = when (method) {
                RoundwoodVolumeMethod.HUBER -> listOf(middle)
                RoundwoodVolumeMethod.SMALIAN -> listOf(start, end)
                RoundwoodVolumeMethod.NEWTON -> listOf(start, middle, end)
                null -> emptyList()
            }
            if (required.isNotEmpty() && required.all { it != null && it == 0.0 }) {
                add(WoodField.SECTION, WoodDiagnosticCode.ZERO_SECTION)
            }
            if (diagnostics.isNotEmpty() || length == null || method == null || bark == null) return null
            return LogMeasurement(method, length, bark, start, middle, end)
        }

        private fun section(
            raw: RawWoodDimension?,
            field: WoodField,
            method: RoundwoodVolumeMethod?,
            required: Boolean,
            allowZero: Boolean,
        ): Double? {
            if (method != null && !required) {
                if (raw != null) {
                    add(field, WoodDiagnosticCode.UNUSED_SECTION_PRESENT)
                    // Keep parsing a supplied section for correction, never use it.
                    dimension(raw, field, centimetres = true, allowZero = true)
                }
                return null
            }
            if (method == null && raw == null) return null
            return dimension(raw, field, centimetres = true, allowZero = allowZero)
        }

        private fun dimension(
            raw: RawWoodDimension?,
            field: WoodField,
            centimetres: Boolean = false,
            allowZero: Boolean = false,
        ): Double? {
            val parsed = WoodRawDecimalParser.parse(raw, field)
            dimensions[field] = parsed
            diagnostics += parsed.diagnostics
            val value = if (centimetres) parsed.centimetres else parsed.metres
            if (value == null) return null
            if (value.signum() < 0) {
                add(field, WoodDiagnosticCode.NEGATIVE_VALUE)
                return null
            }
            if (!allowZero && value.signum() == 0) {
                add(field, WoodDiagnosticCode.ZERO_NOT_ALLOWED)
                return null
            }
            return representable(value, field)
        }

        private fun representable(value: BigDecimal, field: WoodField): Double? {
            val double = value.toDouble()
            if (!double.isFinite() || (value.signum() != 0 && double == 0.0)) {
                add(field, WoodDiagnosticCode.NUMERIC_INPUT_UNREPRESENTABLE)
                return null
            }
            return double
        }

        private fun add(field: WoodField, code: WoodDiagnosticCode) {
            diagnostics += WoodDiagnostic(field, code)
        }
    }
}
