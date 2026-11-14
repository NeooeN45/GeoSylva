package com.forestry.counter.domain.maps

import kotlin.math.*

/** WGS84, always longitude then latitude at the service boundary. */
data class MapsPoint(val longitude: Double, val latitude: Double) {
    init {
        require(longitude.isFinite() && latitude.isFinite())
        require(longitude in -180.0..180.0 && latitude in -90.0..90.0)
    }
    fun distanceTo(other: MapsPoint): Double {
        val lat = Math.toRadians(other.latitude - latitude)
        val lon = Math.toRadians(other.longitude - longitude)
        val a = sin(lat / 2).pow(2) + cos(Math.toRadians(latitude)) *
            cos(Math.toRadians(other.latitude)) * sin(lon / 2).pow(2)
        return 6_371_000.0 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}

data class MapsPlace(val label: String, val point: MapsPoint)
enum class MapsProfile(val apiValue: String) { WALK("pedestrian"), CAR("car") }
enum class MapsBasemap(val styleUrl: String?) {
    DAY("https://tiles.openfreemap.org/styles/liberty"),
    NIGHT("https://tiles.openfreemap.org/styles/dark"),
    AERIAL(null)
}

data class MapsRoute(
    val points: List<MapsPoint>,
    val distanceMetres: Double,
    val durationSeconds: Double,
    val profile: MapsProfile,
    val sourceVersion: String,
    val steps: List<MapsStep> = emptyList(),
    val source: MapsRouteSource = MapsRouteSource.IGN,
) {
    init {
        require(points.size >= 2)
        require(distanceMetres.isFinite() && distanceMetres > 0)
        require(durationSeconds.isFinite() && durationSeconds > 0)
    }
}

enum class MapsFailure { NETWORK, RATE_LIMIT, INVALID_RESPONSE, LOCATION, LOCATION_DENIED, NO_ORIGIN, NO_ROUTE, CONFIGURATION, AUTHORIZATION }
class MapsServiceException(val failure: MapsFailure) : Exception(failure.name)

interface MapsRepository {
    suspend fun search(query: String): List<MapsPlace>
    suspend fun route(start: MapsPoint, end: MapsPoint, profile: MapsProfile): MapsRoute
}
