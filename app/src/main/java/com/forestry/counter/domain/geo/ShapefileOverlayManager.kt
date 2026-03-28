package com.forestry.counter.domain.geo

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Métadonnées d'une couche shapefile importée.
 */
/**
 * Champ d'étiquette disponible pour l'affichage sur la carte.
 */
enum class LabelField(val key: String, val frLabel: String, val enLabel: String) {
    PARCELLE("ccod_prf", "N° parcelle", "Parcel #"),
    FORET("llib_frt", "Forêt", "Forest"),
    SURFACE("SURFACE_HA", "Surface (ha)", "Area (ha)"),
    DISTRICT("qdis_prf", "District", "District"),
    NOM("NOM", "ID global", "Global ID"),
    PERIMETRE("PERIMETRE", "Périmètre (m)", "Perimeter (m)"),
    SURF_CADASTRALE("qsret_prf", "Surface cadastrale (ha)", "Cadastral area (ha)"),
    OBSERVATION("lobs_prf", "Observation", "Observation"),
    CATEGORIE("ccod_cact", "Code catégorie", "Category code");

    companion object {
        fun fromKey(key: String): LabelField? = entries.firstOrNull { it.key == key }
    }
}

data class ShapefileOverlay(
    val id: String,
    val displayName: String,
    val forestNames: List<String>,
    val featureCount: Int,
    val fillColor: Int = 0x4D2E7D32,     // vert semi-transparent par défaut (ARGB)
    val fillOpacity: Float = 0.3f,
    val borderColor: Int = 0xFF1B5E20.toInt(),
    val borderOpacity: Float = 0.9f,
    val borderWidth: Float = 1.5f,
    val labelSize: Float = 12f,
    val labelFields: List<LabelField> = listOf(LabelField.PARCELLE),
    val combineLabels: Boolean = true,
    val visible: Boolean = true,
    val geoJsonFile: String
)

/**
 * Gère l'import, la persistance et le chargement des couches shapefile.
 * Les GeoJSON sont stockés dans le répertoire files/shapefiles/.
 * Les métadonnées sont dans files/shapefiles/index.json.
 */
class ShapefileOverlayManager(private val context: Context) {

    companion object {
        private const val TAG = "ShpOverlayMgr"
        private const val DIR_NAME = "shapefiles"
        private const val INDEX_FILE = "index.json"
    }

    private val dir: File
        get() = File(context.filesDir, DIR_NAME).also { it.mkdirs() }

    /**
     * Importe un shapefile depuis un URI (fichier .zip).
     * Parse, reprojette, convertit en GeoJSON et sauvegarde.
     *
     * @return L'overlay créé, ou null en cas d'erreur.
     */
    suspend fun importFromUri(uri: Uri, displayName: String? = null): ShapefileOverlay? =
        withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: run {
                    Log.e(TAG, "Cannot open URI: $uri")
                    return@withContext null
                }

                // Copier vers un fichier temporaire pour éviter les problèmes de ZipInputStream
                // sur les flux content provider Android (readBytes peut retourner 0 octets)
                val tempFile = java.io.File.createTempFile("shp_import", ".zip", context.cacheDir)
                try {
                    inputStream.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    Log.d(TAG, "Copied to temp file: ${tempFile.length()} bytes")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to copy URI to temp file", e)
                    tempFile.delete()
                    return@withContext null
                }

                val result = ShapefileParser.parseZip(tempFile.inputStream()) ?: run {
                    Log.e(TAG, "Failed to parse shapefile")
                    tempFile.delete()
                    return@withContext null
                }
                tempFile.delete()

                if (result.features.isEmpty()) {
                    Log.w(TAG, "No features in shapefile")
                    return@withContext null
                }

                val geoJson = ShapefileParser.toGeoJson(result)
                Log.d(TAG, "GeoJSON generated: ${geoJson.length} chars")
                Log.d(TAG, "GeoJSON first 300 chars: ${geoJson.take(300)}")
                val id = "shp_${System.currentTimeMillis()}"
                val geoJsonFileName = "$id.geojson"
                val geoJsonFile = File(dir, geoJsonFileName)
                geoJsonFile.writeText(geoJson, Charsets.UTF_8)
                Log.d(TAG, "GeoJSON saved: ${geoJsonFile.absolutePath} (${geoJsonFile.length()} bytes)")

                val name = displayName
                    ?: result.forestNames.firstOrNull()?.replaceFirstChar { it.uppercase() }
                    ?: "Parcellaire"

                val overlay = ShapefileOverlay(
                    id = id,
                    displayName = name,
                    forestNames = result.forestNames.sorted().toList(),
                    featureCount = result.features.size,
                    geoJsonFile = geoJsonFileName
                )

                saveOverlayMeta(overlay)
                Log.i(TAG, "Imported $id: ${result.features.size} features, ${result.forestNames.size} forests")
                overlay
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                null
            }
        }

    /**
     * Importe n'importe quel format géospatial supporté par GeoImportParser
     * (GeoJSON, KML, KMZ, GPX, CSV coords, GeoPackage).
     */
    suspend fun importGenericUri(uri: Uri, displayName: String? = null): ShapefileOverlay? =
        withContext(Dispatchers.IO) {
            try {
                val result = GeoImportParser.parse(context, uri, displayName) ?: run {
                    Log.e(TAG, "GeoImportParser returned null for $uri")
                    return@withContext null
                }
                if (result.featureCount == 0) {
                    Log.w(TAG, "No features parsed from $uri (format=${result.format})")
                }
                val id            = "${result.format}_${System.currentTimeMillis()}"
                val geoJsonFile   = "$id.geojson"
                File(dir, geoJsonFile).writeText(result.geoJson, Charsets.UTF_8)
                val name = displayName ?: result.displayName.ifBlank { "Import ${result.format.uppercase()}" }
                val overlay = ShapefileOverlay(
                    id           = id,
                    displayName  = name,
                    forestNames  = emptyList(),
                    featureCount = result.featureCount,
                    geoJsonFile  = geoJsonFile
                )
                saveOverlayMeta(overlay)
                Log.i(TAG, "importGenericUri OK: ${result.featureCount} features (${result.format})")
                if (result.warnings.isNotEmpty()) Log.w(TAG, "Warnings: ${result.warnings}")
                overlay
            } catch (e: Exception) {
                Log.e(TAG, "importGenericUri failed", e)
                null
            }
        }

    /**
     * Charge le GeoJSON d'un overlay.
     */
    suspend fun loadGeoJson(overlay: ShapefileOverlay): String? =
        withContext(Dispatchers.IO) {
            try {
                File(dir, overlay.geoJsonFile).readText(Charsets.UTF_8)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load GeoJSON for ${overlay.id}", e)
                null
            }
        }

    /**
     * Retourne le fichier GeoJSON d'un overlay (pour chargement par URI).
     */
    fun getGeoJsonFile(overlay: ShapefileOverlay): File? {
        val f = File(dir, overlay.geoJsonFile)
        return if (f.exists()) f else null
    }

    /**
     * Retourne la liste de tous les overlays importés.
     */
    fun listOverlays(): List<ShapefileOverlay> {
        val indexFile = File(dir, INDEX_FILE)
        if (!indexFile.exists()) return emptyList()
        return try {
            val json = JSONObject(indexFile.readText(Charsets.UTF_8))
            val arr = json.optJSONArray("overlays") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                ShapefileOverlay(
                    id = obj.getString("id"),
                    displayName = obj.getString("displayName"),
                    forestNames = run {
                        val fa = obj.optJSONArray("forestNames")
                        if (fa != null) (0 until fa.length()).map { fa.getString(it) } else emptyList()
                    },
                    featureCount = obj.optInt("featureCount", 0),
                    fillColor = obj.optLong("fillColor", 0x4D2E7D32).toInt(),
                    fillOpacity = obj.optDouble("fillOpacity", 0.3).toFloat(),
                    borderColor = obj.optLong("borderColor", 0xFF1B5E20).toInt(),
                    borderOpacity = obj.optDouble("borderOpacity", 0.9).toFloat(),
                    borderWidth = obj.optDouble("borderWidth", 1.5).toFloat(),
                    labelSize = obj.optDouble("labelSize", 12.0).toFloat(),
                    labelFields = run {
                        val la = obj.optJSONArray("labelFields")
                        if (la != null) (0 until la.length()).mapNotNull { LabelField.fromKey(la.getString(it)) }
                        else listOf(LabelField.PARCELLE)
                    },
                    combineLabels = obj.optBoolean("combineLabels", true),
                    visible = obj.optBoolean("visible", true),
                    geoJsonFile = obj.getString("geoJsonFile")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read index", e)
            emptyList()
        }
    }

    /**
     * Met à jour les paramètres d'affichage d'un overlay.
     */
    fun updateOverlay(overlay: ShapefileOverlay) {
        val overlays = listOverlays().toMutableList()
        val idx = overlays.indexOfFirst { it.id == overlay.id }
        if (idx >= 0) {
            overlays[idx] = overlay
        } else {
            overlays.add(overlay)
        }
        saveIndex(overlays)
    }

    /**
     * Supprime un overlay et son fichier GeoJSON.
     */
    fun deleteOverlay(id: String) {
        val overlays = listOverlays().filter { it.id != id }
        saveIndex(overlays)
        File(dir, "$id.geojson").delete()
    }

    // ── Persistance index ──

    private fun saveOverlayMeta(overlay: ShapefileOverlay) {
        val overlays = listOverlays().toMutableList()
        overlays.removeAll { it.id == overlay.id }
        overlays.add(overlay)
        saveIndex(overlays)
    }

    private fun saveIndex(overlays: List<ShapefileOverlay>) {
        val json = JSONObject()
        val arr = org.json.JSONArray()
        overlays.forEach { o ->
            val obj = JSONObject()
            obj.put("id", o.id)
            obj.put("displayName", o.displayName)
            obj.put("forestNames", org.json.JSONArray(o.forestNames))
            obj.put("featureCount", o.featureCount)
            obj.put("fillColor", o.fillColor.toLong() and 0xFFFFFFFFL)
            obj.put("fillOpacity", o.fillOpacity.toDouble())
            obj.put("borderColor", o.borderColor.toLong() and 0xFFFFFFFFL)
            obj.put("borderOpacity", o.borderOpacity.toDouble())
            obj.put("borderWidth", o.borderWidth.toDouble())
            obj.put("labelSize", o.labelSize.toDouble())
            obj.put("labelFields", org.json.JSONArray(o.labelFields.map { it.key }))
            obj.put("combineLabels", o.combineLabels)
            obj.put("visible", o.visible)
            obj.put("geoJsonFile", o.geoJsonFile)
            arr.put(obj)
        }
        json.put("overlays", arr)
        File(dir, INDEX_FILE).writeText(json.toString(2), Charsets.UTF_8)
    }
}
