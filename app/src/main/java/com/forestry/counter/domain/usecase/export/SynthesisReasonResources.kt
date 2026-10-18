package com.forestry.counter.domain.usecase.export

import androidx.annotation.StringRes
import com.forestry.counter.R
import com.forestry.counter.domain.calculation.SynthesisReport

/** New notices describe only the explicitly versioned calculation, never legacy sealed reports. */
internal fun currentSynthesisVolumeReasons(report: SynthesisReport): Map<String, Int> =
    if (report.engineVersion == SynthesisReport.CURRENT_ENGINE_VERSION) report.uncomputedReasons else emptyMap()

internal fun needsSynthesisVolumeQualification(report: SynthesisReport): Boolean =
    (currentSynthesisVolumeReasons(report)["UNQUALIFIED_TARIF"] ?: 0) > 0

/** A saved choice is distinct from a method actually applied by the captured engine. */
@StringRes
internal fun synthesisEmptyAppliedMethodResource(report: SynthesisReport): Int =
    if (report.engineVersion == SynthesisReport.CURRENT_ENGINE_VERSION)
        R.string.synthesis_method_not_applied else R.string.synthesis_method_none

@StringRes
internal fun synthesisRecordedHeightResource(report: SynthesisReport, @StringRes historicalLabel: Int): Int =
    if (report.engineVersion == SynthesisReport.CURRENT_ENGINE_VERSION)
        R.string.synthesis_sections_recorded_heights else historicalLabel

@StringRes
internal fun synthesisEngineDescriptionResource(report: SynthesisReport): Int =
    if (report.engineVersion == SynthesisReport.CURRENT_ENGINE_VERSION)
        R.string.synthesis_recorded_engine else R.string.synthesis_existing_engine

/** Shared wording for recorded volume-unavailability reasons; never reinterpret a sealed result. */
@StringRes
internal fun synthesisVolumeReasonResource(reason: String): Int = when (reason) {
    "MISSING_TARIF" -> R.string.synthesis_reason_tarif
    "UNQUALIFIED_TARIF" -> R.string.synthesis_reason_unqualified_tarif
    "MISSING_TARIF_NUMBER" -> R.string.synthesis_reason_number
    "MISSING_HEIGHT" -> R.string.synthesis_reason_height
    "MISSING_FORM_COEFFICIENT" -> R.string.synthesis_reason_coefficient
    "MISSING_ESSENCE_COEFFICIENTS" -> R.string.synthesis_reason_essence
    "INVALID_DIAMETER" -> R.string.synthesis_reason_diameter
    else -> R.string.synthesis_reason_other
}
