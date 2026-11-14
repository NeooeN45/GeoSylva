package com.forestry.counter.data.maps

import com.forestry.counter.domain.maps.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Public, user-requested searches only. No account tokens or field records are sent. */
class PublicMapsRepository(
    private val client: OkHttpClient = sharedClient,
    private val mapboxToken: String = "",
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : MapsRepository {
    private val blockedUntil = AtomicLong(0)
    override suspend fun search(query: String): List<MapsPlace> {
        val clean = query.trim().take(160)
        if (clean.length < 3) return emptyList()
        val url = "https://data.geopf.fr/geocodage/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", clean).addQueryParameter("limit", "5")
            .addQueryParameter("index", "address").build()
        val response = get(url)
        return withContext(Dispatchers.Default) { parseSearch(response) }
    }

    override suspend fun route(start: MapsPoint, end: MapsPoint, profile: MapsProfile): MapsRoute {
        require(start.distanceTo(end) >= 1.0)
        if (mapboxToken.isNotBlank()) return mapboxRoute(start, end, profile)
        val url = "https://data.geopf.fr/navigation/itineraire".toHttpUrl().newBuilder()
            .addQueryParameter("resource", ROUTING_RESOURCE)
            .addQueryParameter("start", "${start.longitude},${start.latitude}")
            .addQueryParameter("end", "${end.longitude},${end.latitude}")
            .addQueryParameter("profile", profile.apiValue)
            .addQueryParameter("optimization", "fastest")
            .addQueryParameter("geometryFormat", "geojson")
            .addQueryParameter("getSteps", "false")
            .addQueryParameter("distanceUnit", "meter")
            .addQueryParameter("timeUnit", "second").build()
        val response = get(url)
        return withContext(Dispatchers.Default) { parseRoute(response, profile) }
    }

    private suspend fun mapboxRoute(start: MapsPoint, end: MapsPoint, profile: MapsProfile): MapsRoute {
        if (!mapboxToken.startsWith("pk.")) throw MapsServiceException(MapsFailure.CONFIGURATION)
        val apiProfile = if (profile == MapsProfile.CAR) "driving" else "walking"
        val url = "https://api.mapbox.com/directions/v5/mapbox/$apiProfile/${start.longitude},${start.latitude};${end.longitude},${end.latitude}"
            .toHttpUrl().newBuilder().addQueryParameter("access_token", mapboxToken)
            .addQueryParameter("geometries", "geojson").addQueryParameter("overview", "full")
            .addQueryParameter("steps", "true").addQueryParameter("language", if (java.util.Locale.getDefault().language == "fr") "fr" else "en")
            .addQueryParameter("alternatives", "false").build()
        val response = get(url)
        return withContext(Dispatchers.Default) { parseMapboxRoute(response, profile) }
    }

    private suspend fun get(url: HttpUrl): String = suspendCancellableCoroutine { continuation ->
        require(url.scheme == "https" && url.host in setOf("data.geopf.fr", "api.mapbox.com"))
        if (nowMillis() < blockedUntil.get()) {
            continuation.resumeWithException(MapsServiceException(MapsFailure.RATE_LIMIT))
            return@suspendCancellableCoroutine
        }
        val call = client.newCall(Request.Builder().url(url)
            .header("User-Agent", "GeoSylva/3.0 (+https://geosylva.fr)")
            .header("Accept", "application/json").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(MapsServiceException(MapsFailure.NETWORK))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val text = response.use {
                        if (it.code == 429) {
                            val now = nowMillis()
                            val retry = it.header("Retry-After")
                            val seconds = retry?.toLongOrNull()?.coerceIn(1, Int.MAX_VALUE.toLong())
                            val deadline = seconds?.let { value -> now + value * 1000 }
                                ?: runCatching { ZonedDateTime.parse(retry, DateTimeFormatter.RFC_1123_DATE_TIME)
                                    .toInstant().toEpochMilli() }.getOrNull()?.coerceAtLeast(now + 1000)
                                ?: (now + 30_000)
                            blockedUntil.updateAndGet { previous -> maxOf(previous, deadline) }
                            throw MapsServiceException(MapsFailure.RATE_LIMIT)
                        }
                        if (it.code == 401 || it.code == 403) throw MapsServiceException(MapsFailure.AUTHORIZATION)
                        if (!it.isSuccessful) throw MapsServiceException(MapsFailure.NETWORK)
                        val body = it.body ?: throw MapsServiceException(MapsFailure.INVALID_RESPONSE)
                        if (body.contentLength() > MAX_BYTES) throw MapsServiceException(MapsFailure.INVALID_RESPONSE)
                        val source = body.source()
                        source.request(MAX_BYTES + 1)
                        if (source.buffer.size > MAX_BYTES) throw MapsServiceException(MapsFailure.INVALID_RESPONSE)
                        source.buffer.readUtf8()
                    }
                    if (continuation.isActive) continuation.resume(text)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(
                        e as? MapsServiceException ?: MapsServiceException(MapsFailure.NETWORK))
                }
            }
        })
    }

    companion object {
        // Qualified against IGN getCapabilities and both profiles on 2026-11-01.
        private const val ROUTING_RESOURCE = "bdtopo-valhalla"
        private const val MAX_BYTES = 2_000_000L
        private val sharedClient by lazy {
            OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS)
                .followRedirects(false).followSslRedirects(false).build()
        }

        fun parseSearch(text: String): List<MapsPlace> = parse {
            val root = document(text)
            require(root.string("type") == "FeatureCollection")
            val features = root["features"] as? JsonArray ?: error("Missing features")
            require(features.size <= 20)
            features.mapNotNull { value ->
                // One malformed result must not hide the remaining valid destinations.
                runCatching {
                    val feature = value.jsonObject
                    require(feature.string("type") == "Feature")
                    val geometry = feature.getValue("geometry").jsonObject
                    require(geometry.string("type") == "Point")
                    val label = feature.getValue("properties").jsonObject.string("label")
                    require(!label.isNullOrBlank() && label.length <= 500)
                    MapsPlace(label, point(geometry.getValue("coordinates")))
                }.getOrNull()
            }.distinctBy { it.label to it.point }.take(5)
        }

        fun parseRoute(text: String, requestedProfile: MapsProfile): MapsRoute = parse {
            val root = document(text)
            require(root.string("crs") == "EPSG:4326")
            require(root.string("resource") == ROUTING_RESOURCE)
            require(root.string("profile") == requestedProfile.apiValue)
            require(root.string("distanceUnit") == "meter" && root.string("timeUnit") == "second")
            val geometry = root.getValue("geometry").jsonObject
            require(geometry.string("type") == "LineString")
            val coordinates = geometry.getValue("coordinates").jsonArray
            require(coordinates.size in 2..50_000)
            val points = coordinates.map(::point)
            require(points.any { it != points.first() })
            MapsRoute(points, root.getValue("distance").jsonPrimitive.double,
                root.getValue("duration").jsonPrimitive.double, requestedProfile,
                root.string("resourceVersion")?.take(80).orEmpty())
        }

        fun parseMapboxRoute(text: String, requestedProfile: MapsProfile): MapsRoute {
            val root = parse { document(text) }
            when (root.string("code")) {
                "NoRoute", "NoSegment" -> throw MapsServiceException(MapsFailure.NO_ROUTE)
                "Ok" -> Unit
                else -> throw MapsServiceException(MapsFailure.INVALID_RESPONSE)
            }
            return parse {
                val routes = root.getValue("routes").jsonArray
                require(routes.isNotEmpty() && routes.size <= 5)
                val route = routes.first().jsonObject
                fun line(value: JsonElement): List<MapsPoint> {
                    val geometry = value.jsonObject
                    require(geometry.string("type") == "LineString")
                    val coordinates = geometry.getValue("coordinates").jsonArray
                    require(coordinates.size in 2..50_000)
                    return coordinates.map(::point)
                }
                val points = line(route.getValue("geometry"))
                require(points.any { it != points.first() })
                val legs = route.getValue("legs").jsonArray
                require(legs.size == 1)
                val rawSteps = legs.single().jsonObject.getValue("steps").jsonArray
                require(rawSteps.size in 1..2_000)
                val steps = rawSteps.map { value ->
                    val step = value.jsonObject
                    val maneuver = step.getValue("maneuver").jsonObject
                    MapsStep(checkNotNull(maneuver.string("instruction")), point(maneuver.getValue("location")),
                        step.getValue("distance").jsonPrimitive.double, step.getValue("duration").jsonPrimitive.double,
                        line(step.getValue("geometry")))
                }
                MapsRoute(points, route.getValue("distance").jsonPrimitive.double,
                    route.getValue("duration").jsonPrimitive.double, requestedProfile, "Directions v5", steps, MapsRouteSource.MAPBOX)
            }
        }

        private fun document(text: String): JsonObject {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
            return Json.parseToJsonElement(text).jsonObject
        }
        private fun point(value: JsonElement): MapsPoint {
            val c = value.jsonArray
            require(c.size in 2..3)
            return MapsPoint(c[0].jsonPrimitive.double, c[1].jsonPrimitive.double)
        }
        private fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
        private fun <T> parse(block: () -> T): T = try { block() }
            catch (e: Exception) { throw MapsServiceException(MapsFailure.INVALID_RESPONSE) }
    }
}
