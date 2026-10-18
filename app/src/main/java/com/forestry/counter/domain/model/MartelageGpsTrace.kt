package com.forestry.counter.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Positions mesurées ; les segments distincts ne doivent jamais être reliés à travers une pause. */
@Serializable
data class MartelageGpsPoint(
    val latitude: Double,
    val longitude: Double,
    val capturedAtMs: Long,
    val elapsedRealtimeMs: Long,
    val accuracyM: Double,
    val altitudeM: Double? = null,
    val segmentId: String,
)

@Serializable
data class MartelageGpsTrace(val version: Int = 1, val points: List<MartelageGpsPoint>)

object MartelageGpsTraceCodec {
    const val MAX_POINTS = 10_000
    const val MAX_BYTES = 3_000_000
    const val MAX_AGE_MS = 15_000L
    const val MAX_ACCURACY_M = 100.0
    private val json = Json { encodeDefaults = true; explicitNulls = true; ignoreUnknownKeys = false }
    private val segmentPattern = Regex("[0-9]+:[0-9]+:[0-9]+")

    fun decode(value: String): MartelageGpsTrace {
        require(value.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "MARTELAGE_TRACE_TOO_LARGE" }
        return json.decodeFromString<MartelageGpsTrace>(value).also(::validate)
    }

    fun encode(value: MartelageGpsTrace): String {
        validate(value)
        return json.encodeToString(value).also {
            require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "MARTELAGE_TRACE_TOO_LARGE" }
        }
    }

    fun validate(value: MartelageGpsTrace) {
        require(value.version == 1 && value.points.isNotEmpty() && value.points.size <= MAX_POINTS) {
            "MARTELAGE_TRACE_INVALID"
        }
        val segments = mutableSetOf<String>()
        val elapsedByBoot = mutableMapOf<Int, Long>()
        var previous: MartelageGpsPoint? = null
        value.points.forEach { point ->
            validatePoint(point)
            val parsed = checkNotNull(parseSegment(point.segmentId))
            elapsedByBoot[parsed.boot]?.let { lastElapsed ->
                require(point.elapsedRealtimeMs > lastElapsed) { "MARTELAGE_TRACE_ORDER" }
            }
            previous?.let { last ->
                if (last.segmentId == point.segmentId) {
                    require(point.elapsedRealtimeMs > last.elapsedRealtimeMs) { "MARTELAGE_TRACE_ORDER" }
                } else {
                    require(point.segmentId !in segments) { "MARTELAGE_TRACE_SEGMENT_REUSED" }
                }
            }
            segments.add(point.segmentId)
            elapsedByBoot[parsed.boot] = point.elapsedRealtimeMs
            previous = point
        }
    }

    fun validatePoint(point: MartelageGpsPoint) {
        val segment = parseSegment(point.segmentId)
        require(point.latitude.isFinite() && point.latitude in -90.0..90.0 &&
            point.longitude.isFinite() && point.longitude in -180.0..180.0 &&
            point.accuracyM.isFinite() && point.accuracyM > 0.0 && point.accuracyM <= MAX_ACCURACY_M &&
            (point.altitudeM == null || point.altitudeM.isFinite()) &&
            point.capturedAtMs > 0L && point.elapsedRealtimeMs >= 0L &&
            segment != null && point.elapsedRealtimeMs >= segment.sensorStartedElapsedMs) {
            "MARTELAGE_TRACE_POINT_INVALID"
        }
    }

    private data class Segment(val boot: Int, val segmentStartedElapsedMs: Long, val sensorStartedElapsedMs: Long)

    /** Bounded integers, not just digits: overflowing clock identifiers never describe a measured segment. */
    private fun parseSegment(value: String): Segment? {
        if (value.length > 80 || !segmentPattern.matches(value)) return null
        val parts = value.split(':')
        val boot = parts[0].toIntOrNull() ?: return null
        val start = parts[1].toLongOrNull() ?: return null
        val sensor = parts[2].toLongOrNull() ?: return null
        if (boot < 0 || start < 0L || sensor < start) return null
        return Segment(boot, start, sensor)
    }

    /** Refuse les positions en cache antérieures au démarrage, après pause ou redémarrage. */
    fun isCurrentPoint(point: MartelageGpsPoint, session: InventaireSession, clock: MartelageClockReading): Boolean {
        if (session.martelageState != MartelageStates.RUNNING || session.placetteId == null) return false
        val start = session.segmentStartedElapsedMs ?: return false
        val boot = session.segmentBootCount ?: return false
        if (boot != clock.bootCount || clock.elapsedRealtimeMs < start) return false
        val segment = parseSegment(point.segmentId) ?: return false
        return segment.boot == boot && segment.segmentStartedElapsedMs == start &&
            point.elapsedRealtimeMs >= segment.sensorStartedElapsedMs &&
            point.elapsedRealtimeMs <= clock.elapsedRealtimeMs &&
            clock.elapsedRealtimeMs - point.elapsedRealtimeMs <= MAX_AGE_MS
    }
}
