package com.forestry.counter.domain.location

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class SampleFootprintShape { CIRCLE, HEXAGON }

/** Planned sampling geometry, not a biometric protocol or a GPS measurement. */
@Serializable
data class PlotSamplingPlan(
    val schemaVersion: Int = 1,
    val placement: String,
    val gridLayout: String? = null,
    val cellAreaHa: Double? = null,
    val rotationDeg: Double = 0.0,
    val footprintShape: String,
    val footprintAreaM2: Double,
    val metricMethod: String = "LOCAL_TANGENT_APPROXIMATION",
    val boundaryPolicy: String = "CENTER_INSIDE",
    val boundaryWarning: Boolean = false,
) {
    fun toJson(): String = Json { encodeDefaults = true }.encodeToString(this)

    fun validate() {
        require(schemaVersion == 1 && placement in setOf("MANUAL", "AUTO"))
        require(metricMethod == "LOCAL_TANGENT_APPROXIMATION")
        require(boundaryPolicy in setOf("CENTER_INSIDE", "FULLY_INSIDE"))
        require(rotationDeg.isFinite() && rotationDeg >= 0.0 && rotationDeg < 360.0)
        require(footprintShape in setOf("CIRCLE", "HEXAGON") && footprintAreaM2.isFinite() && footprintAreaM2 in 1.0..1_000_000.0)
        if (placement == "AUTO") require(gridLayout == "HEX" && cellAreaHa?.let { it.isFinite() && it in 0.01..10_000.0 } == true)
        else require(gridLayout == null && cellAreaHa == null)
        require(boundaryPolicy != "FULLY_INSIDE" || !boundaryWarning)
    }

    companion object {
        fun fromJson(value: String): PlotSamplingPlan {
            require(value.toByteArray(Charsets.UTF_8).size <= 4096)
            return Json.decodeFromString<PlotSamplingPlan>(value).also { it.validate() }
        }
    }
}

data class SamplingFootprint(
    val center: ParcelCoordinate,
    val geometry: ParcelGeometry,
    /** Conservative warning: the planned footprint touches/crosses a parcel edge or hole. */
    val crossesBoundary: Boolean,
)

data class SamplingGridPreview(
    val footprints: List<SamplingFootprint>,
    val cells: List<ParcelGeometry>,
    val neighborDistanceM: Double,
    val rowDistanceM: Double,
)

class SamplingGridLimitException : IllegalArgumentException("Sampling grid exceeds the plot or candidate limit")

/**
 * A triangular lattice gives one centre per regular hexagonal cell. Its cell area
 * controls density and is deliberately independent of the footprint measured at
 * each centre. Cell edges are a planning aid, not parcel boundaries. Clipping is
 * by centre (holes excluded), never by silently truncating the result.
 *
 * Distances use a local tangent approximation on a mean-radius sphere. Automatic
 * generation is limited to a 50 km extent and latitude ±80°; it is not a survey,
 * does not correct slope and does not establish sampling representativeness.
 * Circle outlines use 48 segments; the declared target area remains separate.
 */
object PlotSamplingGeometry {
    const val MAX_PLOTS = 500
    private const val MAX_CANDIDATES = 100_000
    private const val EARTH_RADIUS_M = 6_371_008.8
    private const val MAX_EXTENT_M = 50_000.0

    /** Same theoretical lattice spacing used by generation, independent of footprint area. */
    fun neighborDistanceM(cellAreaHa: Double): Double {
        require(cellAreaHa.isFinite() && cellAreaHa in 0.01..10_000.0)
        return sqrt(2.0 * cellAreaHa * 10_000.0 / sqrt(3.0))
    }

    fun grid(
        parcel: ParcelGeometry,
        cellAreaHa: Double,
        shape: SampleFootprintShape,
        footprintAreaM2: Double,
        rotationDeg: Double = 0.0,
    ): SamplingGridPreview {
        require(cellAreaHa.isFinite() && cellAreaHa in 0.01..10_000.0)
        requireArea(footprintAreaM2)
        require(rotationDeg.isFinite() && rotationDeg >= 0.0 && rotationDeg < 360.0)
        val frame = Frame(parcel.bounds.center)
        val corners = listOf(
            ParcelCoordinate(parcel.bounds.west, parcel.bounds.south),
            ParcelCoordinate(parcel.bounds.east, parcel.bounds.south),
            ParcelCoordinate(parcel.bounds.east, parcel.bounds.north),
            ParcelCoordinate(parcel.bounds.west, parcel.bounds.north),
        ).map(frame::toMetres)
        require(corners.maxOf { it.x } - corners.minOf { it.x } <= MAX_EXTENT_M &&
            corners.maxOf { it.y } - corners.minOf { it.y } <= MAX_EXTENT_M) { "Parcel is too large for a local sampling grid" }
        val angle = rotationDeg * PI / 180.0
        val unrotated = corners.map { it.rotated(-angle) }
        val spacing = neighborDistanceM(cellAreaHa)
        val rowSpacing = spacing * sqrt(3.0) / 2.0
        val minRow = floor(unrotated.minOf { it.y } / rowSpacing).toLong()
        val maxRow = ceil(unrotated.maxOf { it.y } / rowSpacing).toLong()
        val minColumn = floor(unrotated.minOf { it.x } / spacing - 0.5).toLong()
        val maxColumn = ceil(unrotated.maxOf { it.x } / spacing + 0.5).toLong()
        val candidates = (maxRow - minRow + 1) * (maxColumn - minColumn + 1)
        if (candidates > MAX_CANDIDATES) throw SamplingGridLimitException()
        val boundary = Boundary(parcel)
        val footprints = mutableListOf<SamplingFootprint>()
        val cells = mutableListOf<ParcelGeometry>()
        for (row in minRow..maxRow) for (column in minColumn..maxColumn) {
            val point = Metres((column + if (row % 2L == 0L) 0.0 else 0.5) * spacing, row * rowSpacing).rotated(angle)
            val center = frame.toCoordinate(point)
            if (!parcel.contains(center)) continue
            if (footprints.size == MAX_PLOTS) throw SamplingGridLimitException()
            footprints += footprint(center, shape, footprintAreaM2, rotationDeg, boundary)
            // Pointy hexagon Voronoi cell: nearest neighbours along the local x axis.
            cells += polygon(center, SampleFootprintShape.HEXAGON, cellAreaHa * 10_000.0, rotationDeg + 30.0)
        }
        return SamplingGridPreview(footprints, cells, spacing, rowSpacing)
    }

    fun footprints(
        parcel: ParcelGeometry,
        centers: List<ParcelCoordinate>,
        shape: SampleFootprintShape,
        areaM2: Double,
        rotationDeg: Double = 0.0,
    ): List<SamplingFootprint> {
        require(centers.size <= MAX_PLOTS)
        requireArea(areaM2)
        require(rotationDeg.isFinite())
        val boundary = Boundary(parcel)
        return centers.map { center ->
            require(parcel.contains(center)) { "Sampling centre must be inside the parcel" }
            footprint(center, shape, areaM2, rotationDeg, boundary)
        }
    }

    fun polygon(center: ParcelCoordinate, shape: SampleFootprintShape, areaM2: Double, rotationDeg: Double = 0.0): ParcelGeometry {
        requireArea(areaM2)
        require(rotationDeg.isFinite())
        val frame = Frame(center)
        val radius = when (shape) {
            SampleFootprintShape.CIRCLE -> sqrt(areaM2 / PI)
            SampleFootprintShape.HEXAGON -> sqrt(2.0 * areaM2 / (3.0 * sqrt(3.0)))
        }
        val count = if (shape == SampleFootprintShape.CIRCLE) 48 else 6
        val points = (0 until count).map { index ->
            val angle = 2.0 * PI * index / count + rotationDeg * PI / 180.0
            frame.toCoordinate(Metres(radius * cos(angle), radius * sin(angle)))
        }
        val ring = JsonArray((points + points.first()).map { JsonArray(listOf(JsonPrimitive(it.longitude), JsonPrimitive(it.latitude))) })
        return ParcelGeometry.fromGeoJson(JsonObject(mapOf("type" to JsonPrimitive("Polygon"), "coordinates" to JsonArray(listOf(ring)))).toString())
    }

    private fun footprint(center: ParcelCoordinate, shape: SampleFootprintShape, areaM2: Double, rotationDeg: Double, boundary: Boundary): SamplingFootprint {
        val geometry = polygon(center, shape, areaM2, rotationDeg)
        return SamplingFootprint(center, geometry, boundary.crosses(center, geometry, shape, areaM2))
    }

    private fun requireArea(area: Double) = require(area.isFinite() && area in 1.0..1_000_000.0) { "Invalid sampling footprint area" }

    private data class Metres(val x: Double, val y: Double) {
        fun rotated(angle: Double) = Metres(x * cos(angle) - y * sin(angle), x * sin(angle) + y * cos(angle))
    }

    private class Frame(private val origin: ParcelCoordinate) {
        private val longitudeScale = EARTH_RADIUS_M * cos(origin.latitude * PI / 180.0)
        init { require(abs(origin.latitude) <= 80.0) { "Local metric sampling is unsupported near a pole" } }
        fun toMetres(point: ParcelCoordinate) = Metres(
            (point.longitude - origin.longitude) * PI / 180.0 * longitudeScale,
            (point.latitude - origin.latitude) * PI / 180.0 * EARTH_RADIUS_M,
        )
        fun toCoordinate(point: Metres) = ParcelCoordinate(
            origin.longitude + point.x / longitudeScale * 180.0 / PI,
            origin.latitude + point.y / EARTH_RADIUS_M * 180.0 / PI,
        )
    }

    private class Boundary(private val parcel: ParcelGeometry) {
        private val parentRings = rings(parcel)
        fun crosses(center: ParcelCoordinate, footprint: ParcelGeometry, shape: SampleFootprintShape, areaM2: Double): Boolean {
            val frame = Frame(center)
            if (shape == SampleFootprintShape.CIRCLE) {
                val radius = sqrt(areaM2 / PI)
                // Test the true circle radius against every segment, not only preview vertices.
                return parentRings.any { ring -> ring.zipWithNext().any { (a, b) ->
                    distanceToSegment(frame.toMetres(a), frame.toMetres(b)) <= radius
                } }
            }
            val outline = rings(footprint).single()
            if (outline.any { !parcel.contains(it) }) return true
            // An enclosed hole can escape a vertex-only or centre-only containment test.
            if (parentRings.any { ring -> ring.any(footprint::contains) }) return true
            return parentRings.any { ring -> ring.zipWithNext().any { (a, b) ->
                outline.zipWithNext().any { (c, d) -> intersects(frame.toMetres(a), frame.toMetres(b), frame.toMetres(c), frame.toMetres(d)) }
            } }
        }
    }

    private fun rings(geometry: ParcelGeometry): List<List<ParcelCoordinate>> {
        val root = Json.parseToJsonElement(geometry.toGeoJson()) as JsonObject
        val coordinates = root.getValue("coordinates").jsonArray
        val polygons = if (root.getValue("type").jsonPrimitive.content == "Polygon") listOf(coordinates) else coordinates.map { it.jsonArray }
        return polygons.flatMap { polygon -> polygon.map { ring -> ring.jsonArray.map { position ->
            val values = position.jsonArray
            ParcelCoordinate(values[0].jsonPrimitive.double, values[1].jsonPrimitive.double)
        } } }
    }

    private fun distanceToSegment(a: Metres, b: Metres): Double {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0.0) 0.0 else (-(a.x * dx + a.y * dy) / lengthSquared).coerceIn(0.0, 1.0)
        return hypot(a.x + t * dx, a.y + t * dy)
    }

    private fun intersects(a: Metres, b: Metres, c: Metres, d: Metres): Boolean {
        fun cross(first: Metres, second: Metres, third: Metres) = (second.x - first.x) * (third.y - first.y) - (second.y - first.y) * (third.x - first.x)
        fun onSegment(first: Metres, second: Metres, point: Metres) = abs(cross(first, second, point)) <= 1e-7 &&
            point.x in min(first.x, second.x)..max(first.x, second.x) && point.y in min(first.y, second.y)..max(first.y, second.y)
        val abC = cross(a, b, c)
        val abD = cross(a, b, d)
        val cdA = cross(c, d, a)
        val cdB = cross(c, d, b)
        return (abC * abD < 0.0 && cdA * cdB < 0.0) || onSegment(a, b, c) || onSegment(a, b, d) || onSegment(c, d, a) || onSegment(c, d, b)
    }
}
