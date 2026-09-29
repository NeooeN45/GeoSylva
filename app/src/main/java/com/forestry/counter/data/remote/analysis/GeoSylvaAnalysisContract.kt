package com.forestry.counter.data.remote.analysis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class GeoJsonGeometryDto(
    val type: String,
    val coordinates: JsonElement,
)

@Serializable
internal data class GeoSylvaAnalysisRequestDto(
    @SerialName("schema_version") val schemaVersion: String = "geosylva.analysis.request.v1",
    @SerialName("request_id") val requestId: String,
    @SerialName("station_id") val stationId: String,
    val geometry: GeoJsonGeometryDto,
    @SerialName("species_taxref_ids") val speciesTaxrefIds: List<Int>,
    val question: String,
    val objective: String,
    @SerialName("requested_capabilities") val requestedCapabilities: List<String>,
    @SerialName("evidence_levels") val evidenceLevels: Map<String, String> = emptyMap(),
    @SerialName("user_constraints") val userConstraints: List<String> = emptyList(),
    @SerialName("alternatives_requested") val alternativesRequested: Boolean = true,
    @SerialName("maximum_depth") val maximumDepth: Int = 5,
    @SerialName("app_version") val appVersion: String,
)

@Serializable
internal data class AnalysisAcceptedDto(
    @SerialName("analysis_id") val analysisId: String,
    val status: String,
    @SerialName("status_url") val statusUrl: String,
    @SerialName("result_url") val resultUrl: String,
    @SerialName("retry_after_seconds") val retryAfterSeconds: Int,
)

@Serializable
internal data class AnalysisStatusDto(
    @SerialName("analysis_id") val analysisId: String,
    val status: String,
    @SerialName("progress_percent") val progressPercent: Int,
    @SerialName("completed_engines") val completedEngines: List<String> = emptyList(),
    @SerialName("unavailable_engines") val unavailableEngines: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    @SerialName("error_code") val errorCode: String? = null,
    @SerialName("retry_after_seconds") val retryAfterSeconds: Int? = null,
)

@Serializable
internal data class ScientificAnalysisResultDto(
    @SerialName("schema_version") val schemaVersion: String,
    @SerialName("analysis_id") val analysisId: String,
    @SerialName("request_id") val requestId: String,
    @SerialName("station_id") val stationId: String,
    val summary: String,
    val observations: List<JsonElement> = emptyList(),
    val calculations: List<JsonElement> = emptyList(),
    val conclusions: List<JsonElement> = emptyList(),
    val recommendations: List<JsonElement> = emptyList(),
    val uncertainties: List<String> = emptyList(),
    val contradictions: List<JsonElement> = emptyList(),
    @SerialName("data_coverage") val dataCoverage: JsonElement,
    val sources: List<JsonElement> = emptyList(),
    @SerialName("data_versions") val dataVersions: JsonElement,
    val engines: List<JsonElement> = emptyList(),
    @SerialName("unavailable_engines") val unavailableEngines: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    @SerialName("duration_ms") val durationMs: Long,
    @SerialName("trace_id") val traceId: String,
    @SerialName("completed_at") val completedAt: String,
)

internal enum class AnalysisState {
    PENDING,
    RUNNING,
    PARTIAL,
    COMPLETED,
    FAILED,
    EXPIRED;

    val isTerminal: Boolean
        get() = this in setOf(PARTIAL, COMPLETED, FAILED, EXPIRED)

    companion object {
        fun fromWire(value: String): AnalysisState = when (value) {
            "pending" -> PENDING
            "running" -> RUNNING
            "partial" -> PARTIAL
            "completed" -> COMPLETED
            "failed" -> FAILED
            "expired" -> EXPIRED
            else -> FAILED
        }
    }
}
