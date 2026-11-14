package com.forestry.counter.domain.location

import com.forestry.counter.domain.geometry.PlanarSegmentIntersection
import java.math.BigDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** WGS84 position. GeoJSON and this API always use longitude before latitude. */
class ParcelTopologyException(message: String) : IllegalArgumentException(message)

data class ParcelCoordinate(val longitude: Double, val latitude: Double) {
    init {
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Invalid WGS84 longitude" }
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Invalid WGS84 latitude" }
    }
}

data class ParcelBounds(val west: Double, val south: Double, val east: Double, val north: Double) {
    init {
        ParcelCoordinate(west, south)
        ParcelCoordinate(east, north)
        require(west <= east && south <= north) { "Invalid parcel bounds" }
    }

    /** Bounding-box centre for framing a map, not a point guaranteed to be in the parcel. */
    val center: ParcelCoordinate get() = ParcelCoordinate((west + east) / 2.0, (south + north) / 2.0)
}

/**
 * Validated, immutable 2D Polygon/MultiPolygon geometry in WGS84 longitude/latitude.
 *
 * Area uses a spherical longitude/latitude integral with mean Earth radius 6,371,008.8 m;
 * it is an approximation, not a cadastral/survey surface. Holes are subtracted regardless
 * of winding. Rings use straight segments in GeoJSON coordinates for containment and
 * topology validation. Antimeridian/hemisphere-spanning geometry is deliberately rejected.
 *
 * Limits include closing vertices: 2 MiB UTF-8, 8,000 positions, 4,000 per ring, 200
 * polygons. Parse/validation failures throw IllegalArgumentException; there is no bbox
 * fallback. Feature wrappers, projected CRS, Z/M, EWKT and empty geometry are unsupported.
 */
class ParcelGeometry private constructor(private val polygons: List<List<List<ParcelCoordinate>>>) {
    val bounds: ParcelBounds
    val areaSquareMetres: Double

    init {
        validate(polygons)
        val positions = polygons.flatMap { polygon -> polygon.flatten() }
        bounds = ParcelBounds(
            positions.minOf { it.longitude }, positions.minOf { it.latitude },
            positions.maxOf { it.longitude }, positions.maxOf { it.latitude },
        )
        require(bounds.east - bounds.west < 180.0) { "Antimeridian or hemisphere-spanning geometry is unsupported" }
        areaSquareMetres = polygons.sumOf { polygon ->
            sphericalRingArea(polygon.first()) - polygon.drop(1).sumOf(::sphericalRingArea)
        }
        require(areaSquareMetres.isFinite() && areaSquareMetres > 0.0) { "Degenerate parcel area" }
    }

    /** Outer boundaries are included; holes and their boundaries are always excluded. */
    fun contains(point: ParcelCoordinate): Boolean = polygons.any { polygonContains(it, point) }

    fun toGeoJson(): String {
        fun ring(ring: List<ParcelCoordinate>) = JsonArray(ring.map {
            JsonArray(listOf(JsonPrimitive(it.longitude), JsonPrimitive(it.latitude)))
        })
        fun polygon(polygon: List<List<ParcelCoordinate>>) = JsonArray(polygon.map(::ring))
        val single = polygons.size == 1
        return JsonObject(mapOf(
            "type" to JsonPrimitive(if (single) "Polygon" else "MultiPolygon"),
            "coordinates" to if (single) polygon(polygons.single()) else JsonArray(polygons.map(::polygon)),
        )).toString()
    }

    /** Locale-independent decimal representation retaining the original Double values. */
    fun toWkt(): String {
        fun number(value: Double) = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
        fun ring(ring: List<ParcelCoordinate>) = ring.joinToString(", ", "(", ")") {
            "${number(it.longitude)} ${number(it.latitude)}"
        }
        fun polygon(polygon: List<List<ParcelCoordinate>>) = polygon.joinToString(", ", "(", ")", transform = ::ring)
        return if (polygons.size == 1) "POLYGON ${polygon(polygons.single())}"
        else polygons.joinToString(", ", "MULTIPOLYGON (", ")", transform = ::polygon)
    }

    companion object {
        private const val MAX_BYTES = 2 * 1024 * 1024
        private const val MAX_COORDINATES = 8_000
    private const val MAX_RING_COORDINATES = 4_000
        private const val MAX_POLYGONS = 200
        private const val EARTH_RADIUS_METRES = 6_371_008.8
        // Sub-micrometre numerical tolerance, not an allowance for GPS uncertainty.
        private const val BOUNDARY_EPSILON = 1e-12
        private val strictJson = Json { isLenient = false; allowSpecialFloatingPointValues = false }

        fun fromGeoJson(json: String): ParcelGeometry {
            requireInputSize(json)
            checkJsonDepth(json)
            val root = try {
                strictJson.parseToJsonElement(json) as? JsonObject
            } catch (exception: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid parcel GeoJSON", exception)
            } ?: throw IllegalArgumentException("Parcel GeoJSON must be a geometry object")
            // Only an explicit CRS84 declaration has unambiguous longitude/latitude axes.
            root["crs"]?.let { declared ->
                val crs = declared as? JsonObject ?: throw IllegalArgumentException("Unsupported GeoJSON CRS")
                val properties = crs["properties"] as? JsonObject
                val name = properties?.get("name") as? JsonPrimitive
                require(crs["type"] == JsonPrimitive("name") && name?.isString == true &&
                    name.content in setOf("OGC:CRS84", "urn:ogc:def:crs:OGC::CRS84")) { "Unsupported GeoJSON CRS or axes" }
            }
            val type = root["type"] as? JsonPrimitive
            require(type?.isString == true) { "Missing GeoJSON geometry type" }
            val coordinates = root["coordinates"] as? JsonArray
                ?: throw IllegalArgumentException("Missing GeoJSON coordinates")
            val budget = CoordinateBudget()
            fun readRing(element: kotlinx.serialization.json.JsonElement): List<ParcelCoordinate> {
                val ring = element as? JsonArray ?: throw IllegalArgumentException("Invalid GeoJSON ring")
                require(ring.size in 4..MAX_RING_COORDINATES) { "Invalid ring coordinate count" }
                return ring.map { position ->
                    val values = position as? JsonArray ?: throw IllegalArgumentException("Invalid GeoJSON position")
                    require(values.size == 2) { "Only 2D longitude/latitude positions are supported" }
                    fun number(index: Int): Double {
                        val primitive = values[index] as? JsonPrimitive
                            ?: throw IllegalArgumentException("Invalid GeoJSON coordinate")
                        require(!primitive.isString && primitive.content.length <= 64) { "Invalid GeoJSON coordinate" }
                        return primitive.content.toDoubleOrNull()
                            ?: throw IllegalArgumentException("Invalid GeoJSON number")
                    }
                    budget.add()
                    coordinate(number(0), number(1))
                }
            }
            fun readPolygon(element: kotlinx.serialization.json.JsonElement): List<List<ParcelCoordinate>> {
                val polygon = element as? JsonArray ?: throw IllegalArgumentException("Invalid GeoJSON polygon")
                require(polygon.isNotEmpty() && polygon.size <= MAX_COORDINATES / 4) { "Invalid polygon ring count" }
                return polygon.map(::readRing)
            }
            val polygons = when (requireNotNull(type).content) {
                "Polygon" -> listOf(readPolygon(coordinates))
                "MultiPolygon" -> {
                    require(coordinates.size in 1..MAX_POLYGONS) { "Invalid polygon count" }
                    coordinates.map(::readPolygon)
                }
                else -> throw IllegalArgumentException("Only Polygon/MultiPolygon GeoJSON is supported")
            }
            return ParcelGeometry(polygons).also { geometry ->
                root["bbox"]?.let { value ->
                    val box = value as? JsonArray ?: throw IllegalArgumentException("Invalid GeoJSON bbox")
                    require(box.size == 4) { "Only 2D GeoJSON bbox is supported" }
                    val expected = listOf(geometry.bounds.west, geometry.bounds.south, geometry.bounds.east, geometry.bounds.north)
                    box.forEachIndexed { index, element ->
                        val primitive = element as? JsonPrimitive
                            ?: throw IllegalArgumentException("Invalid GeoJSON bbox")
                        require(!primitive.isString && primitive.content.length <= 64) { "Invalid GeoJSON bbox" }
                        val number = primitive.content.toDoubleOrNull()
                            ?: throw IllegalArgumentException("Invalid GeoJSON bbox")
                        require(number.isFinite() && abs(number - expected[index]) <= BOUNDARY_EPSILON) { "GeoJSON bbox does not match geometry" }
                    }
                }
            }
        }

        fun fromWkt(wkt: String): ParcelGeometry {
            requireInputSize(wkt)
            return ParcelGeometry(WktReader(wkt).read())
        }

        private fun requireInputSize(input: String) {
            require(input.isNotBlank() && input.length <= MAX_BYTES && input.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
                "Empty or oversized parcel geometry"
            }
        }

        private fun checkJsonDepth(input: String) {
            // Reject duplicate members as well: last-key-wins decoding could conceal a CRS
            // or replace coordinates depending on which consumer reads the same document.
            class Frame(val keys: MutableSet<String>?, var expectsKey: Boolean)
            val frames = mutableListOf<Frame>()
            var quoted = false
            var escaped = false
            var stringStart = 0
            var keyFrame: Frame? = null
            input.forEachIndexed { index, char ->
                if (quoted) {
                    if (escaped) escaped = false
                    else if (char == '\\') escaped = true
                    else if (char == '"') {
                        quoted = false
                        keyFrame?.let { frame ->
                            val key = (strictJson.parseToJsonElement(input.substring(stringStart, index + 1)) as JsonPrimitive).content
                            require(frame.keys!!.add(key)) { "Duplicate GeoJSON member" }
                            frame.expectsKey = false
                        }
                        keyFrame = null
                    }
                } else {
                    when (char) {
                        '"' -> {
                            quoted = true
                            stringStart = index
                            keyFrame = frames.lastOrNull()?.takeIf { it.expectsKey && it.keys != null }
                        }
                        '[', '{' -> {
                            frames.add(Frame(if (char == '{') mutableSetOf() else null, char == '{'))
                            require(frames.size <= 16) { "Excessive GeoJSON nesting" }
                        }
                        ']', '}' -> {
                            require(frames.isNotEmpty()) { "Invalid GeoJSON nesting" }
                            frames.removeAt(frames.lastIndex)
                        }
                        ',' -> frames.lastOrNull()?.let { it.expectsKey = it.keys != null }
                    }
                }
            }
            require(frames.isEmpty() && !quoted) { "Invalid GeoJSON syntax" }
        }

        private fun coordinate(longitude: Double, latitude: Double) = ParcelCoordinate(
            if (longitude == 0.0) 0.0 else longitude,
            if (latitude == 0.0) 0.0 else latitude,
        )

        private class CoordinateBudget {
            private var count = 0
            fun add() { count++; require(count <= MAX_COORDINATES) { "Too many parcel coordinates" } }
        }

        private class WktReader(private val text: String) {
            private var position = 0
            private val budget = CoordinateBudget()

            fun read(): List<List<List<ParcelCoordinate>>> {
                whitespace()
                val start = position
                while (position < text.length && text[position].isLetter()) position++
                val type = text.substring(start, position)
                val polygons = when {
                    type.equals("POLYGON", ignoreCase = true) -> listOf(polygon())
                    type.equals("MULTIPOLYGON", ignoreCase = true) -> {
                        expect('(')
                        val result = mutableListOf<List<List<ParcelCoordinate>>>()
                        do {
                            require(result.size < MAX_POLYGONS) { "Too many parcel polygons" }
                            result.add(polygon())
                        } while (consume(','))
                        expect(')')
                        result
                    }
                    else -> throw IllegalArgumentException("Only 2D Polygon/MultiPolygon WKT is supported")
                }
                whitespace()
                require(position == text.length) { "Unexpected trailing WKT content" }
                return polygons
            }

            private fun polygon(): List<List<ParcelCoordinate>> {
                expect('(')
                val rings = mutableListOf<List<ParcelCoordinate>>()
                do {
                    require(rings.size < MAX_COORDINATES / 4) { "Too many polygon rings" }
                    expect('(')
                    val ring = mutableListOf<ParcelCoordinate>()
                    do {
                        require(ring.size < MAX_RING_COORDINATES) { "Too many ring coordinates" }
                        val longitude = number()
                        require(position < text.length && text[position].isWhitespace()) { "WKT axes must be separated by whitespace" }
                        val latitude = number()
                        budget.add()
                        ring.add(coordinate(longitude, latitude))
                    } while (consume(','))
                    expect(')')
                    rings.add(ring)
                } while (consume(','))
                expect(')')
                return rings
            }

            private fun number(): Double {
                whitespace()
                val start = position
                if (position < text.length && text[position] in "+-") position++
                var digits = 0
                while (position < text.length && text[position] in '0'..'9') { position++; digits++ }
                if (position < text.length && text[position] == '.') {
                    position++
                    while (position < text.length && text[position] in '0'..'9') { position++; digits++ }
                }
                require(digits > 0) { "Invalid WKT number" }
                if (position < text.length && text[position] in "eE") {
                    position++
                    if (position < text.length && text[position] in "+-") position++
                    val exponentStart = position
                    while (position < text.length && text[position] in '0'..'9') position++
                    require(position > exponentStart) { "Invalid WKT exponent" }
                }
                require(position - start <= 64) { "Oversized WKT number" }
                return text.substring(start, position).toDoubleOrNull()
                    ?: throw IllegalArgumentException("Invalid WKT number")
            }

            private fun whitespace() { while (position < text.length && text[position].isWhitespace()) position++ }
            private fun consume(char: Char): Boolean {
                whitespace()
                if (position < text.length && text[position] == char) { position++; return true }
                return false
            }
            private fun expect(char: Char) { require(consume(char)) { "Expected '$char' in WKT" } }
        }

        private enum class RingLocation { OUTSIDE, BOUNDARY, INSIDE }

        private fun locate(ring: List<ParcelCoordinate>, point: ParcelCoordinate): RingLocation {
            var inside = false
            for (index in 0 until ring.lastIndex) {
                val a = ring[index]
                val b = ring[index + 1]
                if (onSegment(a, b, point, BOUNDARY_EPSILON)) return RingLocation.BOUNDARY
                if ((a.latitude > point.latitude) != (b.latitude > point.latitude)) {
                    val crossingLongitude = a.longitude + (point.latitude - a.latitude) *
                        (b.longitude - a.longitude) / (b.latitude - a.latitude)
                    if (point.longitude < crossingLongitude) inside = !inside
                }
            }
            return if (inside) RingLocation.INSIDE else RingLocation.OUTSIDE
        }

        private fun polygonContains(polygon: List<List<ParcelCoordinate>>, point: ParcelCoordinate, strict: Boolean = false): Boolean {
            val outer = locate(polygon.first(), point)
            return (outer == RingLocation.INSIDE || (!strict && outer == RingLocation.BOUNDARY)) &&
                polygon.drop(1).none { locate(it, point) != RingLocation.OUTSIDE }
        }

        private fun cross(a: ParcelCoordinate, b: ParcelCoordinate, c: ParcelCoordinate) =
            (b.longitude - a.longitude) * (c.latitude - a.latitude) -
                (b.latitude - a.latitude) * (c.longitude - a.longitude)

        private fun orientation(a: ParcelCoordinate, b: ParcelCoordinate, c: ParcelCoordinate): Int =
            PlanarSegmentIntersection.orientation(a.longitude, a.latitude, b.longitude, b.latitude, c.longitude, c.latitude)

        private fun onSegment(a: ParcelCoordinate, b: ParcelCoordinate, point: ParcelCoordinate, tolerance: Double = 0.0): Boolean {
            if (point.longitude < min(a.longitude, b.longitude) - tolerance || point.longitude > max(a.longitude, b.longitude) + tolerance ||
                point.latitude < min(a.latitude, b.latitude) - tolerance || point.latitude > max(a.latitude, b.latitude) + tolerance) return false
            return if (tolerance == 0.0) orientation(a, b, point) == 0
            else abs(cross(a, b, point)) <= tolerance * max(abs(b.longitude - a.longitude), abs(b.latitude - a.latitude))
        }

        private fun intersects(a: ParcelCoordinate, b: ParcelCoordinate, c: ParcelCoordinate, d: ParcelCoordinate): Boolean =
            PlanarSegmentIntersection.intersects(a.longitude, a.latitude, b.longitude, b.latitude,
                c.longitude, c.latitude, d.longitude, d.latitude)

        private fun ringsIntersect(first: List<ParcelCoordinate>, second: List<ParcelCoordinate>): Boolean {
            for (a in 0 until first.lastIndex) for (b in 0 until second.lastIndex) {
                if (intersects(first[a], first[a + 1], second[b], second[b + 1])) return true
            }
            return false
        }

        private fun requireTopology(condition: Boolean, message: () -> String) {
            if (!condition) throw ParcelTopologyException(message())
        }

        private fun validate(polygons: List<List<List<ParcelCoordinate>>>) {
            require(polygons.size in 1..MAX_POLYGONS) { "Invalid parcel polygon count" }
            // Validate every ring before reporting any topology error. A bad first
            // ring must never hide malformed coordinates in a later ring.
            val budget = CoordinateBudget()
            polygons.forEach { polygon ->
                require(polygon.isNotEmpty()) { "Polygon has no outer ring" }
                polygon.forEach { ring ->
                    require(ring.size in 4..MAX_RING_COORDINATES && ring.first() == ring.last()) { "Rings must be closed with at least three vertices" }
                    ring.forEach { budget.add() }
                    require(ring.zipWithNext().all { (a, b) -> a != b }) { "Repeated adjacent ring vertex" }
                }
            }
            polygons.forEach { polygon ->
                require(polygon.isNotEmpty()) { "Polygon has no outer ring" }
                polygon.forEach { ring ->
                    require(ring.size in 4..MAX_RING_COORDINATES && ring.first() == ring.last()) { "Rings must be closed with at least three vertices" }
                    requireTopology(ring.dropLast(1).distinct().size >= 3) { "Ring needs three distinct vertices" }
                    val origin = ring.first()
                    val planarArea = ring.zipWithNext().sumOf { (a, b) -> cross(origin, a, b) }
                    requireTopology(planarArea.isFinite() && planarArea != 0.0) { "Degenerate polygon ring" }
                    for (index in 0 until ring.lastIndex) {
                        val previous = ring[(index + ring.lastIndex - 1) % ring.lastIndex]
                        val current = ring[index]
                        val next = ring[index + 1]
                        require(current != next) { "Repeated adjacent ring vertex" }
                        if (orientation(previous, current, next) == 0) {
                            val dot = (previous.longitude - current.longitude) * (next.longitude - current.longitude) +
                                (previous.latitude - current.latitude) * (next.latitude - current.latitude)
                            requireTopology(dot <= 0.0) { "Overlapping adjacent ring edges" }
                        }
                        for (other in index + 1 until ring.lastIndex) {
                            if (other == index + 1 || (index == 0 && other == ring.lastIndex - 1)) continue
                            requireTopology(!intersects(current, next, ring[other], ring[other + 1])) { "Self-intersecting polygon ring" }
                        }
                    }
                }
                val outer = polygon.first()
                polygon.drop(1).forEachIndexed { index, hole ->
                    requireTopology(!ringsIntersect(outer, hole) && hole.dropLast(1).all { locate(outer, it) == RingLocation.INSIDE }) { "Hole is not strictly inside outer ring" }
                    polygon.subList(1, index + 1).forEach { previous ->
                        requireTopology(!ringsIntersect(previous, hole) && locate(previous, hole.first()) == RingLocation.OUTSIDE &&
                            locate(hole, previous.first()) == RingLocation.OUTSIDE) { "Overlapping or nested polygon holes" }
                    }
                }
            }
            // MultiPolygon components may touch at a point, but cannot overlap or share an edge.
            polygons.forEachIndexed { index, polygon ->
                polygons.take(index).forEach { previous ->
                    requireTopology(!componentsOverlap(polygon, previous)) { "Overlapping MultiPolygon components" }
                }
            }
        }

        private fun componentsOverlap(first: List<List<ParcelCoordinate>>, second: List<List<ParcelCoordinate>>): Boolean {
            first.forEach { one -> second.forEach { two ->
                for (a in 0 until one.lastIndex) for (b in 0 until two.lastIndex) {
                    val p = one[a]; val q = one[a + 1]; val r = two[b]; val s = two[b + 1]
                    if (orientation(p, q, r) * orientation(p, q, s) < 0 && orientation(r, s, p) * orientation(r, s, q) < 0) return true
                    if (orientation(p, q, r) == 0 && orientation(p, q, s) == 0) {
                        val longitude = abs(q.longitude - p.longitude) >= abs(q.latitude - p.latitude)
                        fun axis(point: ParcelCoordinate) = if (longitude) point.longitude else point.latitude
                        if (min(max(axis(p), axis(q)), max(axis(r), axis(s))) > max(min(axis(p), axis(q)), min(axis(r), axis(s)))) return true
                    }
                }
            } }
            fun outerInside(one: List<List<ParcelCoordinate>>, two: List<List<ParcelCoordinate>>) =
                one.first().zipWithNext().any { (a, b) -> polygonContains(two, a, strict = true) ||
                    polygonContains(two, ParcelCoordinate((a.longitude + b.longitude) / 2.0, (a.latitude + b.latitude) / 2.0), strict = true) }
            return outerInside(first, second) || outerInside(second, first)
        }

        private fun sphericalRingArea(ring: List<ParcelCoordinate>): Double {
            val baseline = sin(Math.toRadians(ring.first().latitude))
            var total = 0.0
            var compensation = 0.0
            ring.zipWithNext().forEach { (a, b) ->
                val value = Math.toRadians(b.longitude - a.longitude) *
                    (sin(Math.toRadians(a.latitude)) + sin(Math.toRadians(b.latitude)) - 2.0 * baseline)
                val adjusted = value - compensation
                val sum = total + adjusted
                compensation = (sum - total) - adjusted
                total = sum
            }
            return abs(total) * EARTH_RADIUS_METRES * EARTH_RADIUS_METRES / 2.0
        }
    }
}
