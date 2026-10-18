package com.forestry.counter.data.packs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

data class TerritoryOutline(
    val territoryId: String, val name: String, val regionCode: String,
    /** Polygones, anneaux et positions longitude/latitude du snapshot IGN réel. */
    val polygons: List<List<List<Pair<Double, Double>>>>
)

data class TerritoryPresentationAssets(
    val outline: TerritoryOutline,
    val presentation: TerritoryPresentation?,
    val presentationUnavailable: Boolean,
    val rightsQualification: TerritoryMediaRightsQualification?
)

/** Lecture locale uniquement ; aucune requête réseau ni dépendance à la session GSIE. */
internal object TerritoryPresentationAssetStore {
    private const val ROOT = "packs/territory-presentation"
    private val mutex = Mutex()
    private var outlines: Map<String, TerritoryOutline>? = null
    private var presentations: Map<String, TerritoryPresentation>? = null
    private var rightsQualification: TerritoryMediaRightsQualification? = null
    private var presentationUnavailable = false

    suspend fun load(context: Context, territoryId: String): TerritoryPresentationAssets = withContext(Dispatchers.IO) {
        mutex.withLock {
            val allOutlines = outlines ?: loadOutlines(context).also { outlines = it }
            if (presentations == null) {
                presentations = try {
                    context.assets.open("$ROOT/index.json").use { TerritoryPresentationParser.parse(readBounded(it, TerritoryPresentationParser.MAX_INDEX_BYTES)) }
                        .also { document -> require(document.territories.keys.all { it in allOutlines }); rightsQualification = document.rightsQualification }.territories
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { presentationUnavailable = true; emptyMap() }
            }
            TerritoryPresentationAssets(
                outline = allOutlines[territoryId] ?: error("Contour administratif introuvable"),
                presentation = presentations?.get(territoryId), presentationUnavailable = presentationUnavailable,
                rightsQualification = rightsQualification
            )
        }
    }

    suspend fun readPhoto(context: Context, image: TerritoryImage): Bitmap = withContext(Dispatchers.IO) {
        TerritoryPresentationParser.requireSafeAssetPath(image.assetPath)
        val bytes = context.assets.open("$ROOT/${image.assetPath}").use { readBounded(it, TerritoryPresentationParser.MAX_PHOTO_BYTES.toInt()) }
        require(bytes.size.toLong() == image.bytes && MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } == image.sha256) {
            "Média local altéré"
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth == image.width && bounds.outHeight == image.height) { "Dimensions du média différentes des métadonnées" }
        var sample = 1
        while (maxOf(image.width, image.height) / sample > 1600) sample *= 2
        requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })) {
            "Média local illisible"
        }
    }

    private fun loadOutlines(context: Context): Map<String, TerritoryOutline> {
        val result = mutableMapOf<String, TerritoryOutline>()
        for ((filename, prefix) in listOf("regions.geojson" to "region:", "departments.geojson" to "department:")) {
            val json = context.assets.open("packs/$filename").use { Json.parseToJsonElement(readBounded(it, 4 * 1024 * 1024).toString(Charsets.UTF_8)) }.jsonObject
            require(json.getValue("type").jsonPrimitive.content == "FeatureCollection")
            for (featureValue in json.getValue("features").jsonArray) {
                val feature = featureValue.jsonObject
                val props = feature.getValue("properties").jsonObject
                val id = props.getValue("territory_id").jsonPrimitive.content
                require(id.startsWith(prefix) && feature.getValue("id").jsonPrimitive.content == id && id !in result)
                val geometry = feature.getValue("geometry").jsonObject
                val polygons = when (geometry.getValue("type").jsonPrimitive.content) {
                    "Polygon" -> JsonArray(listOf(geometry.getValue("coordinates")))
                    "MultiPolygon" -> geometry.getValue("coordinates").jsonArray
                    else -> error("Contour administratif invalide")
                }.map { polygon -> polygon.jsonArray.map { ring -> ring.jsonArray.map { position ->
                    val coordinates = position.jsonArray
                    require(coordinates.size == 2)
                    val longitude = coordinates[0].jsonPrimitive.double
                    val latitude = coordinates[1].jsonPrimitive.double
                    require(longitude.isFinite() && latitude.isFinite() && longitude in -180.0..180.0 && latitude in -90.0..90.0)
                    longitude to latitude
                }.also { require(it.size >= 4 && it.first() == it.last()) } } }
                result[id] = TerritoryOutline(id, props.getValue("name").jsonPrimitive.content, props.getValue("region_code").jsonPrimitive.content, polygons)
            }
        }
        require(result.size == 119)
        return result
    }

    private fun readBounded(input: InputStream, maximum: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return output.toByteArray()
            require(output.size() + count <= maximum) { "Asset de présentation hors limites" }
            output.write(buffer, 0, count)
        }
    }
}
