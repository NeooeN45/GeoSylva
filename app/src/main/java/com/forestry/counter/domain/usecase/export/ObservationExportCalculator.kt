package com.forestry.counter.domain.usecase.export

import com.forestry.counter.domain.calculation.ClassSynthesis
import com.forestry.counter.domain.calculation.SynthesisParameters
import com.forestry.counter.domain.calculation.SynthesisReport
import com.forestry.counter.domain.calculation.SynthesisReportCalculator
import com.forestry.counter.domain.calculation.SynthesisTotals
import com.forestry.counter.domain.calculation.synthesisDiameterClass
import com.forestry.counter.domain.calculation.tarifs.TarifSelection
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.model.Tige

data class ObservationExportVolume(val m3: Double?, val reasons: Map<String, Int>) {
    /** Stable machine-readable explanation; an unavailable value is never encoded as zero. */
    val status: String get() = reasons.toSortedMap().entries.joinToString(" | ") { "${it.key}:${it.value}" }
}

data class ObservationExportSynthesis(
    val perClass: List<ClassSynthesis>,
    val totals: SynthesisTotals,
    val volumeByClass: Map<Int, ObservationExportVolume>,
)

/**
 * New exports of live observations use the same cubage policy as the current Synthèse.
 * A tariff choice, default height, price or form coefficient cannot qualify a method here.
 * This adapter never reads or recalculates a closed report or its archived export.
 */
object ObservationExportCalculator {
    fun volume(
        observation: Tige,
        parcel: Parcelle?,
        plots: List<Placette>,
        selection: TarifSelection?,
    ): ObservationExportVolume = when {
        observation.deletedAt != null -> unavailable("OBSERVATION_DELETED", 1)
        parcel == null || parcel.deletedAt != null || parcel.id != observation.parcelleId -> unavailable("MISSING_PARCEL", 1)
        else -> volumeFromReports(reports(parcel, plots, listOf(observation), selection))
    }

    fun synthesis(
        essenceCode: String,
        classes: List<Int>,
        observations: List<Tige>,
        parcel: Parcelle?,
        plots: List<Placette>,
        selection: TarifSelection?,
    ): ObservationExportSynthesis {
        val trees = observations.filter {
            it.deletedAt == null && it.essenceCode.trim().equals(essenceCode.trim(), ignoreCase = true)
        }.distinctBy { it.id }
        require(trees.map { it.parcelleId }.distinct().size <= 1) { "EXPORT_PARCEL_GROUP_REQUIRED" }
        val context = parcel?.takeIf { p -> p.deletedAt == null && trees.all { it.parcelleId == p.id } }
        val grouped = trees.groupBy { synthesisDiameterClass(it.diamCm, classes) }
        val volumes = grouped.mapValues { (_, group) ->
            if (context == null) unavailable("MISSING_PARCEL", group.size)
            else volumeFromReports(reports(context, plots, group, selection))
        }
        val rows = (classes + grouped.keys).distinct().sorted().map { diameterClass ->
            val group = grouped[diameterClass].orEmpty()
            val enteredValues = group.mapNotNull { it.valueEur?.takeIf { value -> value.isFinite() && value >= 0.0 } }
            ClassSynthesis(
                diamClass = diameterClass,
                count = group.size,
                hMean = measuredHeightMean(group),
                vSum = volumes[diameterClass]?.m3,
                valueSumEur = enteredValues.takeIf { it.isNotEmpty() }?.sum(),
            )
        }
        val validDiameters = trees.map { it.diamCm }.filter { it.isFinite() && it > 0.0 }
        val volume = if (context == null) unavailable("MISSING_PARCEL", trees.size)
            else volumeFromReports(reports(context, plots, trees, selection))
        val computedCount = if (volume.m3 != null) trees.size else 0
        return ObservationExportSynthesis(rows, SynthesisTotals(
            nTotal = trees.size,
            dmWeighted = validDiameters.takeIf { it.isNotEmpty() }?.average(),
            hMean = measuredHeightMean(trees),
            vTotal = volume.m3,
            volumeCompletenessPct = if (trees.isEmpty()) 100.0 else computedCount * 100.0 / trees.size,
            volumeComputedCount = computedCount,
            volumeExpectedCount = trees.size,
        ), volumes)
    }

    private fun reports(parcel: Parcelle, plots: List<Placette>, trees: List<Tige>, selection: TarifSelection?) =
        // A project export may contain several revisions; Synthèse's session filter stays intact.
        trees.groupBy { it.sessionId }.map { (sessionId, observations) ->
            SynthesisReportCalculator.calculate(
                parcel = parcel, plots = plots, observations = observations, essenceNames = emptyMap(),
                parameters = SynthesisParameters(tarifSelection = selection), sessionId = sessionId,
                campaignId = null, revisionNumber = null, generatedAt = 0L,
            )
        }

    private fun volumeFromReports(reports: List<SynthesisReport>): ObservationExportVolume {
        val reasons = linkedMapOf<String, Int>()
        reports.forEach { report -> report.uncomputedReasons.forEach { (reason, count) ->
            reasons[reason] = (reasons[reason] ?: 0) + count
        } }
        val complete = reports.isNotEmpty() && reports.all {
            it.isVolumeComplete && it.volumeM3?.let { volume -> volume.isFinite() && volume >= 0.0 } == true
        }
        val volume = if (complete) reports.sumOf { requireNotNull(it.volumeM3) } else null
        if (volume == null && reasons.isEmpty()) reasons["NO_QUALIFIED_VOLUME"] = 1
        return ObservationExportVolume(volume, reasons)
    }

    private fun measuredHeightMean(trees: List<Tige>): Double? = trees.mapNotNull {
        it.hauteurM?.takeIf { height -> height.isFinite() && height > 0.0 }
    }.takeIf { it.isNotEmpty() }?.average()

    private fun unavailable(reason: String, count: Int) = ObservationExportVolume(null, mapOf(reason to count))
}
