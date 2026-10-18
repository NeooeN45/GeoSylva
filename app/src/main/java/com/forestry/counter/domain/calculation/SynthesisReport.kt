package com.forestry.counter.domain.calculation

import com.forestry.counter.domain.calculation.tarifs.TarifSelection
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.location.PlotSamplingPlan
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.roundToInt

/** Versioned, self-contained results. These are observations, never a stand inventory. */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class SynthesisReport(
    val schemaVersion: Int = 1,
    val engineVersion: String = ENGINE_VERSION,
    val parcelId: String,
    val parcelName: String,
    val selectedPlotId: String?,
    val campaignId: String?,
    val sessionId: String?,
    val revisionNumber: Int?,
    val generatedAt: Long,
    val surfaceM2: Double?,
    val surfaceReason: String,
    val observationCount: Int,
    val invalidDiameterCount: Int,
    val measuredHeightCount: Int,
    val locatedCount: Int,
    val qualityCount: Int,
    val volumeComputedCount: Int,
    val basalAreaM2: Double,
    val volumeM3: Double?,
    val enteredValueEur: Double?,
    val enteredValueCount: Int,
    val meanDiameterCm: Double?,
    val quadraticDiameterCm: Double?,
    val firstObservationAt: Long?,
    val lastObservationAt: Long?,
    val perEssence: List<SynthesisEssenceRow>,
    val categories: Map<String, Int>,
    val methods: List<String>,
    val uncomputedReasons: Map<String, Int>,
    val sourceReferences: List<String>,
    /** Captured only by new calculations; omitted null keeps historical report/archive JSON identical. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val selectedPlotName: String? = null,
) {
    val isVolumeComplete: Boolean get() = observationCount > 0 && volumeComputedCount == observationCount
    val basalAreaPerHa: Double? get() = surfaceM2?.let { basalAreaM2 * 10_000.0 / it }
    val volumePerHa: Double? get() = surfaceM2?.let { area -> volumeM3?.let { it * 10_000.0 / area } }
    val observationsPerHa: Double? get() = surfaceM2?.let { observationCount * 10_000.0 / it }

    companion object {
        /** Historical decoding default; changing the active engine never reinterprets an archive. */
        const val ENGINE_VERSION = "geosylva-observation-synthesis-2"
        const val CURRENT_ENGINE_VERSION = "geosylva-observation-synthesis-3"
    }
}

@Serializable
data class SynthesisEssenceRow(
    val code: String,
    val name: String,
    val count: Int,
    val basalAreaM2: Double,
    val volumeM3: Double?,
    val volumeComputedCount: Int,
    val measuredHeightCount: Int,
    val enteredValueEur: Double?,
    val enteredValueCount: Int,
)

@Serializable
data class SynthesisParameters(
    val tarifSelection: TarifSelection? = null,
    val diameterClasses: List<Int> = emptyList(),
    val heightModes: List<HeightModeEntry> = emptyList(),
    val selectedPlotId: String? = null,
    // No global height defaults, regional price multipliers, or arbitrary product split.
    val sourcePolicy: String = "measured-or-explicit-class-height;explicit-tarif;entered-values-only",
) {
    companion object {
        /** Explicit for new captures only; the historical decoding default above stays unchanged. */
        const val CURRENT_SOURCE_POLICY =
            "qualified-method-catalog-required;unqualified-volume-unavailable;entered-values-only"
    }
}

data class SynthesisSurface(val m2: Double?, val reason: String)

/** Same midpoint rule as ForestryCalculator; a boundary belongs to the upper class. */
fun synthesisDiameterClass(diameter: Double, classes: List<Int>): Int {
    if (!diameter.isFinite() || diameter <= 0.0) return 0
    val sorted = classes.distinct().sorted()
    if (sorted.isEmpty()) return diameter.roundToInt()
    for (i in 0 until sorted.lastIndex) {
        if (diameter < (sorted[i].toDouble() + sorted[i + 1]) / 2.0) return sorted[i]
    }
    return sorted.last()
}

/** A parcel's area is not the sampled area of several overlapping plots. */
fun synthesisSurface(parcel: Parcelle, plots: List<Placette>, tiges: List<Tige>, plotId: String?): SynthesisSurface {
    val ids = if (plotId != null) setOf(plotId) else tiges.map { it.placetteId }.toSet()
    if (ids.size != 1 || ids.firstOrNull() == null) return SynthesisSurface(null, "UNRESOLVED_COVERAGE")
    val plot = plots.singleOrNull { it.id == ids.first() && it.parcelleId == parcel.id && it.deletedAt == null }
        ?: return SynthesisSurface(null, "MISSING_PLOT")
    plot.samplingPlanJson?.let { raw ->
        val plan = runCatching { PlotSamplingPlan.fromJson(raw) }.getOrNull()
            ?: return SynthesisSurface(null, "INVALID_SAMPLING_PLAN")
        if (plan.boundaryWarning) return SynthesisSurface(null, "SAMPLING_BOUNDARY_UNKNOWN")
    }
    val area = plot.surfaceM2?.takeIf { it.isFinite() && it > 0.0 }
    if (plot.type == "ENTIRE_PARCEL") {
        val parentArea = parcel.surfaceHa?.takeIf { it.isFinite() && it > 0.0 }?.times(10_000.0)
        if (parentArea == null || area == null || abs(area - parentArea) > maxOf(0.01, parentArea * 1e-6)) {
            return SynthesisSurface(null, "CHANGED_PARCEL_AREA")
        }
        return SynthesisSurface(parentArea, "ENTIRE_PARCEL")
    }
    val radius = plot.rayonM?.takeIf { it.isFinite() && it > 0.0 }
    if (plot.type == "CIRC" && radius != null && area != null) {
        val expected = PI * radius * radius
        if (abs(area - expected) <= maxOf(0.01, expected * 1e-6)) return SynthesisSurface(area, "CIRCULAR_PLOT")
        return SynthesisSurface(null, "INCONSISTENT_PLOT_AREA")
    }
    // Preserve other recorded plot methods without fabricating coverage.
    return if (area != null) SynthesisSurface(area, "RECORDED_PLOT_AREA") else SynthesisSurface(null, "MISSING_PLOT_AREA")
}

/**
 * Descriptive observations and entered values only. There is no qualified cubage catalog bound
 * to this path: selecting a historical tariff, number, height or coefficient cannot activate it.
 * Raw and class heights stay recorded; neither supplies an implicitly qualified cubage input.
 * Closed reports retain their captured results and engine; this function creates new reports only.
 */
object SynthesisReportCalculator {
    fun calculate(
        parcel: Parcelle,
        plots: List<Placette>,
        observations: List<Tige>,
        essenceNames: Map<String, String>,
        parameters: SynthesisParameters,
        sessionId: String?,
        campaignId: String?,
        revisionNumber: Int?,
        generatedAt: Long,
    ): SynthesisReport {
        val tiges = observations.filter {
            it.parcelleId == parcel.id && it.deletedAt == null &&
                (if (sessionId == null) it.sessionId == null else it.sessionId == sessionId) &&
                (parameters.selectedPlotId == null || it.placetteId == parameters.selectedPlotId)
        }.distinctBy { it.id }
        val surface = synthesisSurface(parcel, plots, tiges, parameters.selectedPlotId)
        val reasons = linkedMapOf<String, Int>()
        fun code(raw: String) = raw.trim().uppercase(Locale.ROOT)
        val valid = tiges.filter { it.diamCm.isFinite() && it.diamCm > 0.0 }
        valid.forEach { tige ->
            val essence = code(tige.essenceCode)
            val selection = parameters.tarifSelection
            val methodCode = selection?.essenceOverrides?.entries?.firstOrNull { code(it.key) == essence }?.value
                ?: selection?.method
            // The choice is retained in parameters, not listed as a method actually applied.
            val reason = if (methodCode.isNullOrBlank()) "MISSING_TARIF" else "UNQUALIFIED_TARIF"
            reasons[reason] = (reasons[reason] ?: 0) + 1
        }
        if (valid.size != tiges.size) reasons["INVALID_DIAMETER"] = tiges.size - valid.size
        fun g(t: Tige) = if (t.diamCm.isFinite() && t.diamCm > 0.0) PI * (t.diamCm / 200.0) * (t.diamCm / 200.0) else 0.0
        fun entered(t: Tige) = t.valueEur?.takeIf { it.isFinite() && it >= 0.0 }
        val rows = tiges.groupBy { code(it.essenceCode) }.map { (essence, trees) ->
            val values = trees.mapNotNull(::entered)
            SynthesisEssenceRow(essence, essenceNames.entries.firstOrNull { code(it.key) == essence }?.value ?: essence,
                trees.size, trees.sumOf(::g), null, 0,
                trees.count { it.hauteurM?.let { h -> h.isFinite() && h > 0.0 } == true },
                values.takeIf { it.isNotEmpty() }?.sum(), values.size)
        }.sortedBy { it.name.lowercase(Locale.ROOT) }
        val values = tiges.mapNotNull(::entered)
        return SynthesisReport(
            engineVersion = SynthesisReport.CURRENT_ENGINE_VERSION,
            parcelId = parcel.id, parcelName = parcel.name, selectedPlotId = parameters.selectedPlotId,
            campaignId = campaignId, sessionId = sessionId, revisionNumber = revisionNumber, generatedAt = generatedAt,
            surfaceM2 = surface.m2, surfaceReason = surface.reason, observationCount = tiges.size,
            invalidDiameterCount = tiges.size - valid.size,
            measuredHeightCount = tiges.count { it.hauteurM?.let { h -> h.isFinite() && h > 0.0 } == true },
            locatedCount = tiges.count { validGps(it.gpsWkt) },
            qualityCount = tiges.count { it.qualite != null }, volumeComputedCount = 0,
            basalAreaM2 = tiges.sumOf(::g), volumeM3 = null,
            enteredValueEur = values.takeIf { it.isNotEmpty() }?.sum(), enteredValueCount = values.size,
            meanDiameterCm = valid.takeIf { it.isNotEmpty() }?.map { it.diamCm }?.average(),
            quadraticDiameterCm = valid.takeIf { it.isNotEmpty() }?.let { sqrt(it.sumOf { t -> t.diamCm * t.diamCm } / it.size) },
            firstObservationAt = tiges.minOfOrNull { it.timestamp }, lastObservationAt = tiges.maxOfOrNull { it.timestamp },
            perEssence = rows, categories = tiges.mapNotNull { it.categorie?.takeIf(String::isNotBlank) }.groupingBy { it }.eachCount(),
            methods = emptyList(), uncomputedReasons = reasons,
            sourceReferences = listOfNotNull(parcel.source?.takeIf(String::isNotBlank)) +
                tiges.mapNotNull { it.source?.takeIf(String::isNotBlank) }.distinct(),
            selectedPlotName = parameters.selectedPlotId?.let { selected ->
                plots.singleOrNull { it.id == selected && it.parcelleId == parcel.id && it.deletedAt == null }
                    ?.name?.takeIf(String::isNotBlank)
            },
        )
    }

    private fun validGps(wkt: String?): Boolean {
        val match = Regex("^POINT(?: Z)?\\s*\\(\\s*([-+0-9.eE]+)\\s+([-+0-9.eE]+)(?:\\s+[-+0-9.eE]+)?\\s*\\)$", RegexOption.IGNORE_CASE)
            .matchEntire(wkt?.trim() ?: return false) ?: return false
        val lon = match.groupValues[1].toDoubleOrNull() ?: return false
        val lat = match.groupValues[2].toDoubleOrNull() ?: return false
        return lon.isFinite() && lat.isFinite() && lon in -180.0..180.0 && lat in -90.0..90.0
    }
}
