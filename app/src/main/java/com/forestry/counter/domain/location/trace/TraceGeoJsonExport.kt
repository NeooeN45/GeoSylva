package com.forestry.counter.domain.location.trace

import com.forestry.counter.domain.repository.CartographicTraceDocument
import kotlinx.serialization.json.*

/** One feature per portion: exports never invent a connecting line over an interruption. */
object TraceGeoJsonExport {
    fun encode(document: CartographicTraceDocument): String = buildJsonObject {
        put("type", "FeatureCollection")
        putJsonObject("properties") {
            document.name?.let { put("name", it) }
            put("source", document.source)
            put("coordinateReferenceSystem", document.coordinateReferenceSystem)
            put("method", document.methodId)
            put("methodVersion", document.methodVersion)
            put("horizontalLengthM", document.snapshot.lengthM)
            put("elapsedSessionSeconds", document.snapshot.sessionElapsedNanos / 1_000_000_000.0)
            put("overallUncertaintyM", JsonNull)
            put("unrecordedPassages", document.snapshot.betweenPortionsGapCount)
        }
        putJsonArray("features") {
            document.snapshot.portions.forEachIndexed { index, portion ->
                addJsonObject {
                    put("type", "Feature")
                    putJsonObject("properties") {
                        put("portion", index + 1)
                        put("startReason", portion.startReason.name)
                        put("horizontalLengthM", portion.lengthM)
                        putJsonArray("observations") {
                            portion.points.forEach { point -> addJsonObject {
                                point.fixTimestampMillis?.let { put("timestampMillis", it) }
                                put("elapsedRealtimeNanos", point.fixElapsedRealtimeNanos)
                                point.clockDomainId?.let { put("clockDomain", it) }
                                point.horizontalAccuracyM?.let { put("reportedAccuracyM", it) }
                                put("source", point.source.name)
                            } }
                        }
                    }
                    putJsonObject("geometry") {
                        put("type", if (portion.points.size == 1) "Point" else "LineString")
                        if (portion.points.size == 1) put("coordinates", coordinate(portion.points.first().longitude, portion.points.first().latitude))
                        else put("coordinates", JsonArray(portion.points.map { coordinate(it.longitude, it.latitude) }))
                    }
                }
            }
        }
    }.toString()
    private fun coordinate(lon: Double, lat: Double) = JsonArray(listOf(JsonPrimitive(lon), JsonPrimitive(lat)))
}
