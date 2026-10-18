package com.forestry.counter.domain.calculation

import com.forestry.counter.domain.model.Tige
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

enum class RecordedDendrometryStatus { AVAILABLE, REPORT_MISMATCH, NUMERIC_ERROR }

enum class RecordedDendrometryReason {
    REPORT_OBSERVATIONS_MISMATCH, NUMERIC_DOMAIN_ERROR,
    HEIGHT_PROTOCOL_UNDOCUMENTED, STAND_PROTOCOL_UNDOCUMENTED,
}

/** Frequencies of stored DBH values, not automatically rounded diameter classes. */
data class RecordedDiameterFrequency(val diameterCm: Double, val count: Int)

data class RecordedObservationReference(
    val id: String, val version: Int, val timestamp: Long, val source: String?,
)

/** Read-only complement; never serialized into a report, snapshot or historical PDF. */
data class RecordedDendrometry(
    val status: RecordedDendrometryStatus,
    val reason: RecordedDendrometryReason? = null,
    val recordedCount: Int? = null,
    val validDiameterCount: Int? = null,
    val invalidDiameterCount: Int? = null,
    val basalAreaM2: Double? = null,
    val meanDiameterCm: Double? = null,
    val quadraticDiameterCm: Double? = null,
    val diameterDistribution: List<RecordedDiameterFrequency> = emptyList(),
    val observationReferences: List<RecordedObservationReference> = emptyList(),
    val reportEngineVersion: String,
    val reportGeneratedAt: Long,
    val diameterBasisUnknown: Boolean = true,
    val heightDiameterRatio: Double? = null,
    val heightDiameterReason: RecordedDendrometryReason =
        RecordedDendrometryReason.HEIGHT_PROTOCOL_UNDOCUMENTED,
    val dominantSlenderness: Double? = null,
    val spacingPercent: Double? = null,
    val standIndicatorReason: RecordedDendrometryReason =
        RecordedDendrometryReason.STAND_PROTOCOL_UNDOCUMENTED,
)

/**
 * Checks consistency, not authenticity: legacy reports contain no observation identity hash.
 * The caller supplies the selected report's captured observations and enforces access/fingerprint.
 * Diameter measurement versus class representative is undocumented in Tige. Legacy hauteurM
 * lacks total/commercial nature and origin: no H/D, Hdom/Dg, S% or per-ha inventory is inferred.
 */
object RecordedDendrometryCalculator {
    fun calculate(report: SynthesisReport, observations: List<Tige>): RecordedDendrometry {
        fun unavailable(status: RecordedDendrometryStatus) = RecordedDendrometry(
            status = status,
            reason = if (status == RecordedDendrometryStatus.NUMERIC_ERROR)
                RecordedDendrometryReason.NUMERIC_DOMAIN_ERROR
            else RecordedDendrometryReason.REPORT_OBSERVATIONS_MISMATCH,
            reportEngineVersion = report.engineVersion,
            reportGeneratedAt = report.generatedAt,
        )
        val selected = observations.filter {
            it.parcelleId == report.parcelId && it.sessionId == report.sessionId &&
                it.deletedAt == null &&
                (report.selectedPlotId == null || it.placetteId == report.selectedPlotId)
        }
        if (selected.any { it.id.isBlank() || it.version < 1 } ||
            selected.groupBy { it.id }.values.any { it.distinct().size > 1 }) {
            return unavailable(RecordedDendrometryStatus.REPORT_MISMATCH)
        }
        val rows = selected.distinctBy { it.id }
        val valid = rows.filter { it.diamCm.isFinite() && it.diamCm > 0.0 }
        val invalid = rows.size - valid.size
        fun code(value: String) = value.trim().uppercase(Locale.ROOT)
        val counts = rows.groupingBy { code(it.essenceCode) }.eachCount()
        if (report.observationCount != rows.size || report.invalidDiameterCount != invalid ||
            report.perEssence.map { code(it.code) }.distinct().size != report.perEssence.size ||
            report.perEssence.associate { code(it.code) to it.count } != counts ||
            report.firstObservationAt != rows.minOfOrNull { it.timestamp } ||
            report.lastObservationAt != rows.maxOfOrNull { it.timestamp } ||
            rows.mapNotNull { it.source?.takeIf(String::isNotBlank) }
                .any { it !in report.sourceReferences }) {
            return unavailable(RecordedDendrometryStatus.REPORT_MISMATCH)
        }
        fun area(row: Tige): Double {
            if (!row.diamCm.isFinite() || row.diamCm <= 0.0) return 0.0
            val radiusM = row.diamCm / 200.0
            return PI * radiusM * radiusM
        }
        val areas = valid.map(::area)
        val expectedArea = areas.sum()
        val largest = valid.maxOfOrNull { it.diamCm }
        val mean = largest?.let { scale ->
            valid.sumOf { it.diamCm / scale / valid.size } * scale
        }
        val quadratic = largest?.let { scale ->
            sqrt(valid.sumOf { (it.diamCm / scale) * (it.diamCm / scale) / valid.size }) * scale
        }
        if (areas.any { !it.isFinite() || it <= 0.0 } || !expectedArea.isFinite() ||
            !report.basalAreaM2.isFinite() || report.basalAreaM2 < 0.0 ||
            listOfNotNull(mean, quadratic, report.meanDiameterCm, report.quadraticDiameterCm)
                .any { !it.isFinite() || it <= 0.0 }) {
            return unavailable(RecordedDendrometryStatus.NUMERIC_ERROR)
        }
        if ((valid.isNotEmpty() && report.basalAreaM2 == 0.0) ||
            !close(report.basalAreaM2, expectedArea) ||
            !close(report.meanDiameterCm, mean) || !close(report.quadraticDiameterCm, quadratic) ||
            report.perEssence.any { row ->
                !row.basalAreaM2.isFinite() || !close(row.basalAreaM2,
                    rows.filter { code(it.essenceCode) == code(row.code) }.sumOf(::area))
            }) {
            return unavailable(RecordedDendrometryStatus.REPORT_MISMATCH)
        }
        return RecordedDendrometry(
            status = RecordedDendrometryStatus.AVAILABLE,
            recordedCount = report.observationCount,
            validDiameterCount = valid.size,
            invalidDiameterCount = report.invalidDiameterCount,
            basalAreaM2 = report.basalAreaM2.takeIf { valid.isNotEmpty() || rows.isEmpty() },
            meanDiameterCm = report.meanDiameterCm,
            quadraticDiameterCm = report.quadraticDiameterCm,
            diameterDistribution = valid.groupingBy { it.diamCm }.eachCount().toSortedMap()
                .map { (diameter, count) -> RecordedDiameterFrequency(diameter, count) },
            observationReferences = rows.map {
                RecordedObservationReference(it.id, it.version, it.timestamp, it.source)
            },
            reportEngineVersion = report.engineVersion,
            reportGeneratedAt = report.generatedAt,
        )
    }

    private fun close(left: Double?, right: Double?): Boolean {
        if (left == null || right == null) return left == right
        if (!left.isFinite() || !right.isFinite()) return false
        // Numeric consistency only, never a field precision or inventory tolerance.
        return abs(left - right) <= maxOf(1e-12, maxOf(abs(left), abs(right)) * 1e-9)
    }
}
