package com.forestry.counter.domain.maps

/** Published stationary equipment categories; no operating state or current road limit is inferred. */
enum class FixedRadarKind { ETF, ETD, ETT, ETU, ETVM, ETFR, ETPN }

data class FixedRadar(
    val id: String,
    val kind: FixedRadarKind,
    val point: MapsPoint,
    val declaredSpeedKmh: Int?,
    /** Original local date/time text. The source does not publish a time zone. */
    val commissioningSourceText: String?,
)

data class FixedRadarSource(
    val datasetId: String,
    val datasetUrl: String,
    val producer: String,
    val producerId: String,
    val licenseId: String,
    val licenseUrl: String,
    val resourceId: String,
    val resourceTitle: String,
    val resourceUrl: String,
    /** Resource publication metadata, distinct from HTTP transport validators and download time. */
    val publishedModifiedAt: String,
    val catalogTemporalStart: String?,
    val catalogTemporalEnd: String?,
    val sourceSha256: String,
    val sourceEncoding: String,
    val httpLastModified: String?,
    val retrievedAtMillis: Long,
    val loadedAtMillis: Long,
    val fromHttpCache: Boolean,
    val cacheAgeMillis: Long,
    /** The official notice says GPS latitude/longitude, without an explicit geodetic CRS code. */
    val sourceCrsExplicit: Boolean = false,
    val exhaustiveCoverageGuaranteed: Boolean = false,
    val operationalStateKnown: Boolean = false,
)

data class FixedRadarsSnapshot(
    val radars: List<FixedRadar>,
    val source: FixedRadarSource,
    val center: MapsPoint,
    val radiusMetres: Double,
    val sourceRowCount: Int,
    val acceptedMetropolitanCount: Int,
    val excludedOutsideMetropolitanExtent: Int,
    val rejectedRows: Int,
    val matchingInRadius: Int,
    val truncated: Boolean,
)

enum class FixedRadarsFailure { NETWORK, RATE_LIMIT, UNAVAILABLE, SOURCE_CHANGED, INVALID_DATA, OUTSIDE_SUPPORTED_AREA, INVALID_QUERY }

sealed interface FixedRadarsLoadResult {
    data class Available(val snapshot: FixedRadarsSnapshot) : FixedRadarsLoadResult
    data class Failure(val reason: FixedRadarsFailure) : FixedRadarsLoadResult
}

interface FixedRadarsRepository {
    /** Center/radius only select locally; providers receive a fixed national dataset URL. */
    suspend fun loadSnapshot(center: MapsPoint, radiusMetres: Double = 25_000.0): FixedRadarsLoadResult
}
