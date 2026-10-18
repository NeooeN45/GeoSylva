package com.forestry.counter.data.repository

import com.forestry.counter.domain.location.*
import com.forestry.counter.data.packs.OfflineForestryLayerStore
import com.forestry.counter.data.packs.OfflineForestryFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.security.MessageDigest
import java.text.Normalizer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Bounded, public HTTPS queries. No GSIE identity or private field records reach providers. */
class ParcelMapRepositoryImpl(private val client: OkHttpClient, private val offline: OfflineForestryLayerStore? = null) : ParcelMapRepository {
    override fun isAvailable(source: ParcelMapSource) = true

    override suspend fun searchForests(query: String): List<ForestSearchResult> = withContext(Dispatchers.Default) {
        val clean = query.trim().take(160)
        if (clean.length < 3) return@withContext emptyList()
        // A prefix also finds accent/typing variants. Candidates always require confirmation.
        val word = clean.split(Regex("\\s+")).lastOrNull { it.length >= 3 } ?: return@withContext emptyList()
        val prefix = word.take(4).replace("'", "''")
        val local = offline?.searchForests(word.take(4))
        if (local != null && local.features.isNotEmpty()) return@withContext local.features.map(::localForest)
            .sortedBy { editDistance(normalize(it.name), normalize(clean)) }.take(8)
        val publicForests = getJson(wfsUrl("ONF.FORETS_PUBLIQUES:ONF_FORETS_PUBLIQUES", 20)
            .addQueryParameter("cql_filter", "llib_frt ILIKE '%$prefix%'").build())
        val candidates = publicForests.array("features").mapNotNull { raw ->
            val feature = raw as? JsonObject ?: return@mapNotNull null
            val properties = feature.obj("properties")
            val name = properties.string("llib_frt") ?: return@mapNotNull null
            val geometry = try { ParcelGeometry.fromGeoJson(feature["geometry"].toString()) }
                catch (_: IllegalArgumentException) { null }
            ForestSearchResult(properties.string("iidtn_frt") ?: name, name,
                properties.string("cinse_dep"), geometry, geometry?.bounds?.center,
                "Forêts publiques · © ONF Maisons-Alfort, 2026 · Licence Ouverte 2.0", "onf-public-forest-extents-wfs-2026")
        }.sortedBy { editDistance(normalize(it.name), normalize(clean)) }
        if (candidates.isNotEmpty()) return@withContext candidates.take(8)
        val poi = getJson("https://data.geopf.fr/geocodage/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", clean).addQueryParameter("index", "poi")
            .addQueryParameter("limit", "5").build())
        return@withContext poi.array("features").mapNotNull { raw ->
            val feature = raw as? JsonObject ?: return@mapNotNull null
            val properties = feature.obj("properties")
            val score = (properties["score"] as? JsonPrimitive)?.doubleOrNull ?: 0.0
            if (score < 0.55) return@mapNotNull null
            val geometry = feature.obj("geometry")
            if (geometry.string("type") != "Point") return@mapNotNull null
            val coords = geometry.array("coordinates")
            val lon = (coords.getOrNull(0) as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val lat = (coords.getOrNull(1) as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            if (!lon.isFinite() || !lat.isFinite() || lon !in -180.0..180.0 || lat !in -90.0..90.0) return@mapNotNull null
            val name = properties.string("toponym") ?: return@mapNotNull null
            ForestSearchResult("poi:$name:$lon:$lat", name,
                (properties["depcode"] as? JsonArray)?.firstOrNull()?.jsonPrimitive?.contentOrNull,
                null, ParcelCoordinate(lon, lat), "IGN · BD TOPO® · point toponymique · Licence Ouverte 2.0")
        }
    }

    override suspend fun loadForests(bounds: ParcelBounds): List<ForestSearchResult> = withContext(Dispatchers.Default) {
        require(bounds.east - bounds.west in 0.0..1.0 && bounds.north - bounds.south in 0.0..1.0)
        val local = offline?.loadForests(bounds)
        if (local?.fullyCovered == true) return@withContext local.features.map(::localForest)
        val remote = try {
        val root = getJson(wfsUrl("ONF.FORETS_PUBLIQUES:ONF_FORETS_PUBLIQUES", 50)
            .addQueryParameter("bbox", "${bounds.west},${bounds.south},${bounds.east},${bounds.north},CRS:84").build())
        withContext(Dispatchers.Default) { root.array("features").mapNotNull { raw ->
            try {
                val f = raw.jsonObject; val p = f.obj("properties")
                val id = p.string("iidtn_frt") ?: return@mapNotNull null
                val name = p.string("llib_frt") ?: return@mapNotNull null
                val g = ParcelGeometry.fromGeoJson(f["geometry"].toString())
                ForestSearchResult(id, name, p.string("cinse_dep"), g, g.bounds.center,
                    "Forêts publiques · © ONF Maisons-Alfort, 2026 · Licence Ouverte 2.0", "onf-public-forest-extents-wfs-2026")
            } catch (_: IllegalArgumentException) { null }
        } }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { if (local?.layerAvailable == true) emptyList() else throw e }
        return@withContext (local?.features?.map(::localForest).orEmpty() + remote).distinctBy { it.id }
    }

    override suspend fun loadParcels(bounds: ParcelBounds, source: ParcelMapSource): ParcelMapResult = withContext(Dispatchers.Default) {
        require(isAvailable(source)) { "Source non qualifiée pour la diffusion commerciale" }
        require(bounds.east - bounds.west in 0.0..0.15 && bounds.north - bounds.south in 0.0..0.15) {
            "Zoom nécessaire pour charger les parcelles"
        }
        val local = offline?.loadParcels(bounds, if (source == ParcelMapSource.ONF) "onf_parcels" else "cadastre")
        if (local?.fullyCovered == true) return@withContext ParcelMapResult(local.features.map(::localParcel), local.truncated, 0, local.unavailableSourceIds)
        val bbox = "${bounds.west},${bounds.south},${bounds.east},${bounds.north}"
        val url = if (source == ParcelMapSource.CADASTRE) wfsUrl("CADASTRALPARCELS.PARCELLAIRE_EXPRESS:parcelle", 200)
            .addQueryParameter("bbox", "$bbox,CRS:84").build()
        else ONF_QUERY.toHttpUrl().newBuilder()
            .addQueryParameter("where", "1=1").addQueryParameter("geometry", bbox)
            .addQueryParameter("geometryType", "esriGeometryEnvelope").addQueryParameter("inSR", "4326")
            .addQueryParameter("spatialRel", "esriSpatialRelIntersects").addQueryParameter("outSR", "4326")
            .addQueryParameter("outFields", "objectid,iidtn_frt,llib_frt,ccod_prf,cinse_dep,Shape__Area,Shape__Length")
            .addQueryParameter("resultRecordCount", "200").addQueryParameter("f", "geojson").build()
        val remote = try {
            val json = getJson(url).toString()
            withContext(Dispatchers.Default) {
                if (source == ParcelMapSource.CADASTRE) parseCadastre(json, System.currentTimeMillis())
                else parseOnf(json, System.currentTimeMillis())
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            if (local?.layerAvailable == true) return@withContext ParcelMapResult(local.features.map(::localParcel), true, 0, local.unavailableSourceIds)
            throw e
        }
        return@withContext remote.copy(parcels = (local?.features?.map(::localParcel).orEmpty() + remote.parcels).distinctBy { it.id },
            truncated = remote.truncated || local?.truncated == true, unavailableSourceIds = local?.unavailableSourceIds.orEmpty())
    }

    private fun localForest(feature: OfflineForestryFeature): ForestSearchResult {
        val properties = Json.parseToJsonElement(feature.propertiesJson).jsonObject
        val geometry = ParcelGeometry.fromGeoJson(feature.geometryGeoJson)
        return ForestSearchResult(feature.sourceFeatureId, requireNotNull(feature.name), properties.string("cinse_dep"),
            geometry, geometry.bounds.center, feature.attribution, feature.sourceId)
    }

    private fun localParcel(feature: OfflineForestryFeature): SourceParcel {
        val p = Json.parseToJsonElement(feature.propertiesJson).jsonObject
        val g = ParcelGeometry.fromGeoJson(feature.geometryGeoJson)
        val area = (p["contenance"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() && it > 0.0 }
        val source = if (feature.sourceLayer == "onf_parcels") ParcelMapSource.ONF else ParcelMapSource.CADASTRE
        val sourceArea = (p["Shape__Area"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() && it > 0.0 }
        return SourceParcel(feature.sourceFeatureId, feature.name ?: feature.sourceFeatureId, g, source,
            feature.attribution, System.currentTimeMillis(), feature.geometrySha256, p.string("code_insee"), p.string("nom_com"),
            p.string("section"), p.string("numero"), area, sourceAreaSquareMetres = sourceArea, sourceDataset = feature.sourceId,
            packReference = "${feature.packId}@${feature.packVersion}; source-edition=${feature.sourceEdition}")
    }

    override fun featureCollection(parcels: List<SourceParcel>): String = buildJsonObject {
        put("type", "FeatureCollection")
        putJsonArray("features") {
            parcels.forEach { parcel -> add(buildJsonObject {
                put("type", "Feature"); put("id", parcel.id)
                put("geometry", Json.parseToJsonElement(parcel.geometry.toGeoJson()))
                putJsonObject("properties") { put("parcel_id", parcel.id); put("parcel_label", parcel.label) }
            }) }
        }
    }.toString()

    private fun wfsUrl(type: String, count: Int) = "https://data.geopf.fr/wfs/ows".toHttpUrl().newBuilder()
        .addQueryParameter("service", "WFS").addQueryParameter("version", "2.0.0")
        .addQueryParameter("request", "GetFeature").addQueryParameter("typeNames", type)
        .addQueryParameter("count", count.toString()).addQueryParameter("outputFormat", "application/json")
        .addQueryParameter("srsName", "CRS:84")

    private suspend fun getJson(url: HttpUrl): JsonObject = suspendCancellableCoroutine { continuation ->
        require(url.scheme == "https" && url.host in setOf("data.geopf.fr", "services1.arcgis.com"))
        val call = client.newCall(Request.Builder().url(url).header("Accept", "application/json").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val json = response.use {
                        if (!it.isSuccessful) throw IOException("Service cartographique indisponible (${it.code})")
                        val body = it.body ?: throw IOException("Réponse cartographique vide")
                        if (body.contentLength() > MAX_BYTES) throw IOException("Réponse cartographique trop volumineuse")
                        val source = body.source()
                        source.request(MAX_BYTES + 1)
                        if (source.buffer.size > MAX_BYTES) throw IOException("Réponse cartographique trop volumineuse")
                        Json.parseToJsonElement(source.buffer.readUtf8()).jsonObject
                    }
                    if (continuation.isActive) continuation.resume(json)
                } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
            }
        })
    }

    companion object {
        private const val MAX_BYTES = 2_000_000L
        const val CADASTRE_ATTRIBUTION = "IGN / DGFiP · Parcellaire Express PCI · juin 2026 · Licence Ouverte 2.0"
        const val ONF_ATTRIBUTION = "Parcelles forestières publiques, © ONF Paris, 2026 · mise à jour 22/03/2026 · utilisation libre avec source et date de mise à jour"
        private const val ONF_QUERY = "https://services1.arcgis.com/Y4HgaQpzkE7kenlE/arcgis/rest/services/Parcelles_foresti%C3%A8res_publiques_de_France_m%C3%A9tropolitaine/FeatureServer/11/query"

        fun parseOnf(json: String, fetchedAt: Long): ParcelMapResult {
            require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
            val root = Json.parseToJsonElement(json).jsonObject
            require(root.string("type") == "FeatureCollection")
            if (root.containsKey("crs")) {
                val crs = root["crs"] as? JsonObject ?: throw IllegalArgumentException("CRS ONF invalide")
                require(crs.keys == setOf("type", "properties") && crs.string("type") == "name" &&
                    crs.obj("properties").keys == setOf("name") && crs.obj("properties").string("name") == "EPSG:4326") {
                    "La réponse ONF GeoJSON doit suivre WGS84 longitude/latitude"
                }
            }
            val features = root["features"] as? JsonArray ?: throw IllegalArgumentException("Collection ONF invalide")
            require(features.size <= 200)
            var rejected = 0
            val parcels = features.mapNotNull { raw ->
                try {
                    val f = raw.jsonObject; require(f.string("type") == "Feature")
                    val p = f.obj("properties")
                    val forest = p.string("iidtn_frt") ?: throw IllegalArgumentException("Forêt absente")
                    val number = p.string("ccod_prf") ?: throw IllegalArgumentException("Parcelle absente")
                    require(forest.matches(Regex("[A-Za-z0-9]{1,40}")) && number.isNotBlank() && number.length <= 40)
                    val g = ParcelGeometry.fromGeoJson(f["geometry"].toString())
                    val sourceArea = (p["Shape__Area"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() && it > 0.0 }
                    val sha = MessageDigest.getInstance("SHA-256").digest(g.toGeoJson().toByteArray())
                        .joinToString("") { "%02x".format(it) }
                    SourceParcel("$forest:$number", number, g, ParcelMapSource.ONF, ONF_ATTRIBUTION, fetchedAt, sha,
                        sourceAreaSquareMetres = sourceArea, sourceDataset = "onf-public-forest-parcels-arcgis-2026")
                } catch (_: IllegalArgumentException) { rejected++; null }
            }.distinctBy { it.id }
            return ParcelMapResult(parcels, root["exceededTransferLimit"] == JsonPrimitive(true) || features.size == 200, rejected)
        }

        /** Source geometry stays distinct from the legal cadastral contenance. */
        fun parseCadastre(json: String, fetchedAt: Long): ParcelMapResult {
            require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
            val root = Json.parseToJsonElement(json).jsonObject
            require(root.string("type") == "FeatureCollection")
            // The GeoPF GeoJSON response to srsName=CRS:84 declares EPSG4326, while
            // its coordinates follow the requested longitude/latitude convention.
            if (root.containsKey("crs")) {
                val crs = root["crs"] as? JsonObject ?: throw IllegalArgumentException("CRS invalide")
                require(crs.string("type") == "name")
                require(crs.obj("properties").string("name") in setOf(
                    "urn:ogc:def:crs:OGC:1.3:CRS84", "urn:ogc:def:crs:EPSG::4326", "CRS:84",
                )) { "CRS non WGS84 longitude/latitude" }
            }
            val features = root["features"] as? JsonArray ?: throw IllegalArgumentException("Collection invalide")
            require(features.size <= 200)
            var rejected = 0
            val parcels = features.mapNotNull { raw ->
                try {
                    val feature = raw.jsonObject
                    require(feature.string("type") == "Feature")
                    val properties = feature.obj("properties")
                    val id = properties.string("idu") ?: throw IllegalArgumentException("Identité cadastrale absente")
                    require(id.matches(Regex("[0-9A-Za-z]{8,32}")))
                    val geometryJson = feature["geometry"].toString()
                    val geometry = ParcelGeometry.fromGeoJson(geometryJson)
                    val section = properties.string("section")
                    val number = properties.string("numero")
                    val area = (properties["contenance"] as? JsonPrimitive)?.doubleOrNull
                        ?.takeIf { it.isFinite() && it > 0.0 }
                    val sha = MessageDigest.getInstance("SHA-256").digest(geometry.toGeoJson().toByteArray())
                        .joinToString("") { "%02x".format(it) }
                    SourceParcel(id, listOfNotNull(section, number).joinToString(" ").ifBlank { id },
                        geometry, ParcelMapSource.CADASTRE, CADASTRE_ATTRIBUTION, fetchedAt, sha,
                        properties.string("code_insee"), properties.string("nom_com"), section, number, area)
                } catch (_: IllegalArgumentException) { rejected++; null }
            }.distinctBy { it.id }
            val matched = (root["numberMatched"] as? JsonPrimitive)?.intOrNull
            return ParcelMapResult(parcels, (matched != null && matched > features.size) || features.size == 200, rejected)
        }

        fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(java.util.Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ").trim()

        private fun editDistance(a: String, b: String): Int {
            var prev = IntArray(b.length + 1) { it }
            for (i in a.indices) {
                val next = IntArray(b.length + 1); next[0] = i + 1
                for (j in b.indices) next[j + 1] = minOf(next[j] + 1, prev[j + 1] + 1, prev[j] + if (a[i] == b[j]) 0 else 1)
                prev = next
            }
            return prev[b.length]
        }
    }
}

private fun JsonObject.obj(key: String): JsonObject = this[key] as? JsonObject ?: JsonObject(emptyMap())
private fun JsonObject.array(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())
private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
