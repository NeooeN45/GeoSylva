package com.forestry.counter.domain.usecase.export

import com.forestry.counter.domain.calculation.SynthesisParameters
import com.forestry.counter.domain.calculation.SynthesisReport
import com.forestry.counter.domain.calculation.synthesisDiameterClass
import com.forestry.counter.domain.location.ParcelGeometry
import com.forestry.counter.domain.model.MartelageSnapshotFingerprint
import com.forestry.counter.domain.model.MartelageSnapshotV1
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** Separate presentation inputs: historical SynthesisDocument bytes and archives stay unchanged. */
@Serializable
data class IllustratedSynthesisDocument(
    val document: SynthesisDocument,
    val parcel: Parcelle?,
    val plots: List<Placette> = emptyList(),
    val formatVersion: Int = 1,
    val timeZoneId: String = java.util.TimeZone.getDefault().id,
) {
    init {
        val report = document.report
        require(formatVersion == 1)
        java.time.ZoneId.of(timeZoneId)
        require(parcel == null || parcel.id == report.parcelId)
        require(!document.closed || parcel != null)
        require(plots.map { it.id }.distinct().size == plots.size)
        require(plots.all { it.parcelleId == report.parcelId &&
            (report.selectedPlotId == null || it.id == report.selectedPlotId) })
        require(document.observations.size == report.observationCount)
        require(document.observations.map { it.id }.distinct().size == document.observations.size)
        require(document.observations.all { it.deletedAt == null &&
            (report.selectedPlotId == null || it.placetteId == report.selectedPlotId) })
    }
}

/** A closed report can only capture its validated snapshot, never the current parcel or plots. */
fun captureIllustratedSynthesisDocument(
    document: SynthesisDocument,
    snapshot: MartelageSnapshotV1?,
    currentParcel: Parcelle?,
    currentPlots: List<Placette>,
): IllustratedSynthesisDocument {
    if (!document.closed) return IllustratedSynthesisDocument(document, currentParcel,
        currentPlots.filter { document.report.selectedPlotId == null || it.id == document.report.selectedPlotId }.toList())
    val frozen = requireNotNull(snapshot) { "ILLUSTRATED_SNAPSHOT_MISSING" }
    val calculation = requireNotNull(frozen.calculation) { "ILLUSTRATED_CALCULATION_MISSING" }
    val json = Json { ignoreUnknownKeys = true }
    val expectedFingerprint = if (frozen.schemaVersion == 1)
        MartelageSnapshotFingerprint.computeLegacyV1(frozen.parcelle, frozen.placettes, frozen.tiges)
    else MartelageSnapshotFingerprint.compute(frozen.parcelle, frozen.placettes, frozen.tiges)
    require(calculation.sourceFingerprint == expectedFingerprint && document.sourceFingerprint == expectedFingerprint)
    require(json.decodeFromJsonElement(SynthesisReport.serializer(), calculation.results) == document.report)
    require(document.parameters == calculation.parameters && document.closedAt == frozen.closedAt)
    require(document.traceJson == frozen.traceJson)
    require(calculation.engineVersion == document.report.engineVersion && calculation.generatedAt == document.report.generatedAt)
    require(document.report.sessionId == frozen.sessionId && document.report.parcelId == frozen.parcelleId &&
        document.report.revisionNumber == frozen.revisionNumber && document.report.campaignId == frozen.campaignId)
    require(frozen.schemaVersion in 1..2 && (frozen.schemaVersion != 2 || document.report.selectedPlotId == frozen.placetteId))
    val observations = frozen.tiges.filter {
        document.report.selectedPlotId == null || it.placetteId == document.report.selectedPlotId
    }
    require(document.observations.sortedBy { it.id } == observations.sortedBy { it.id })
    return IllustratedSynthesisDocument(document, frozen.parcelle,
        frozen.placettes.filter { document.report.selectedPlotId == null || it.id == document.report.selectedPlotId }.toList())
}

data class IllustratedDiameterRow(val diameterCm: Int, val count: Int)
data class IllustratedMapPoint(val longitude: Double, val latitude: Double)
data class IllustratedMapPolygon(val exterior: List<IllustratedMapPoint>, val holes: List<List<IllustratedMapPoint>>)
data class IllustratedMapBounds(val west: Double, val south: Double, val east: Double, val north: Double)
data class IllustratedMapData(
    val parcel: List<IllustratedMapPolygon>,
    val footprints: List<IllustratedMapPolygon>,
    val plotCenters: List<IllustratedMapPoint>,
    val trees: List<IllustratedMapPoint>,
    val traceSegments: List<List<IllustratedMapPoint>>,
    val bounds: IllustratedMapBounds,
)
data class IllustratedSynthesisData(
    val diameters: List<IllustratedDiameterRow>,
    val invalidDiameters: Int,
    val map: IllustratedMapData?,
)

/** Presentation aggregates observations only; volumes and scientific results remain those sealed. */
fun buildIllustratedSynthesisData(input: IllustratedSynthesisDocument): IllustratedSynthesisData {
    val valid = input.document.observations.filter { it.diamCm.isFinite() && it.diamCm > 0.0 }
    val parameters = Json { ignoreUnknownKeys = true }
        .decodeFromJsonElement(SynthesisParameters.serializer(), input.document.parameters)
    val diameters = valid.groupingBy { synthesisDiameterClass(it.diamCm, parameters.diameterClasses) }
        .eachCount().toSortedMap().map { IllustratedDiameterRow(it.key, it.value) }
    return IllustratedSynthesisData(diameters, input.document.observations.size - valid.size,
        buildIllustratedMap(input))
}

private fun buildIllustratedMap(input: IllustratedSynthesisDocument): IllustratedMapData? {
    val parcel = input.parcel ?: return null
    // Unknown or projected coordinates must never be interpreted as longitude/latitude.
    if (parcel.srid != 4326) return null
    val raw = parcel.geometrieIgnWkt ?: parcel.shape ?: return null
    val geometry = decodeIllustratedGeometry(raw) ?: return null
    val polygons = polygons(geometry)
    val footprints = input.plots.flatMap { plot ->
        plot.footprintGeoJson?.let(::decodeIllustratedGeometry)?.let(::polygons).orEmpty()
    }
    val centers = input.plots.mapNotNull { illustratedMapPoint(it.centerWkt) }
    val trees = input.document.observations.mapNotNull { illustratedMapPoint(it.gpsWkt) }
    val segments = decodedSynthesisTrace(input.document.traceJson)?.points.orEmpty()
        .groupBy { it.segmentId }.values.map { points -> points.map { IllustratedMapPoint(it.longitude, it.latitude) } }
    val all = polygons.flatMap { it.exterior } + footprints.flatMap { it.exterior } + centers + trees + segments.flatten()
    return IllustratedMapData(polygons, footprints, centers, trees, segments,
        IllustratedMapBounds(all.minOf { it.longitude }, all.minOf { it.latitude },
            all.maxOf { it.longitude }, all.maxOf { it.latitude }))
}

private fun decodeIllustratedGeometry(raw: String): ParcelGeometry? = runCatching {
    if (raw.trimStart().startsWith("{")) ParcelGeometry.fromGeoJson(raw) else ParcelGeometry.fromWkt(raw)
}.getOrNull()

private fun polygons(geometry: ParcelGeometry): List<IllustratedMapPolygon> {
    val json = Json.parseToJsonElement(geometry.toGeoJson()) as JsonObject
    val coordinates = json.getValue("coordinates").jsonArray
    val polygons = if (json.getValue("type").jsonPrimitive.content == "Polygon") listOf(coordinates)
        else coordinates.map { it.jsonArray }
    return polygons.map { polygon ->
        val rings = polygon.map { ring -> ring.jsonArray.map { point ->
            val values = point.jsonArray
            IllustratedMapPoint(values[0].jsonPrimitive.double, values[1].jsonPrimitive.double)
        } }
        IllustratedMapPolygon(rings.first(), rings.drop(1))
    }
}

/** Entire WKT match, finite coordinates and geographic bounds; no substring or bbox GPS. */
internal fun illustratedMapPoint(value: String?): IllustratedMapPoint? {
    val match = Regex("^POINT( Z)?\\s*\\(\\s*([-+0-9.eE]+)\\s+([-+0-9.eE]+)(?:\\s+([-+0-9.eE]+))?\\s*\\)$", RegexOption.IGNORE_CASE)
        .matchEntire(value?.trim() ?: return null) ?: return null
    if ((match.groupValues[1].isNotEmpty()) != match.groupValues[4].isNotEmpty()) return null
    if (match.groupValues[4].isNotEmpty() && match.groupValues[4].toDoubleOrNull()?.isFinite() != true) return null
    val longitude = match.groupValues[2].toDoubleOrNull() ?: return null
    val latitude = match.groupValues[3].toDoubleOrNull() ?: return null
    if (!longitude.isFinite() || !latitude.isFinite() || longitude !in -180.0..180.0 || latitude !in -90.0..90.0) return null
    return IllustratedMapPoint(longitude, latitude)
}
