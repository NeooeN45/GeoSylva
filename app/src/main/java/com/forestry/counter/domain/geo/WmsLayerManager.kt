package com.forestry.counter.domain.geo

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Couche WMS/WMTS ou XYZ définie par l'utilisateur, affichée en overlay sur la carte.
 *
 * L'URL doit être un template XYZ : https://…/{z}/{x}/{y}.png
 * Les services WMS/WMTS peuvent être convertis via leur URL GetTile/GetMap habituelle.
 */
data class WmsLayerConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,           // Template XYZ : {z}/{x}/{y}
    val opacity: Float = 0.7f,
    val visible: Boolean = true,
    val tileSize: Int = 256,
    val maxZoom: Int = 19,
    val attribution: String = ""
) {
    companion object {
        /** Couches WMS/WMTS prédéfinies — IGN GéoPortail */
        val PRESETS = listOf(
            WmsLayerConfig(
                id = "preset_ign_foret",
                name = "IGN — Forêts (BD Forêt)",
                url = "https://data.geopf.fr/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0" +
                        "&STYLE=normal&FORMAT=image/png&TILEMATRIXSET=PM" +
                        "&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&LAYER=LANDCOVER.FORESTINVENTORY.V2",
                attribution = "IGN © GéoPortail"
            ),
            WmsLayerConfig(
                id = "preset_ign_pentes",
                name = "IGN — Pentes (estompage)",
                url = "https://data.geopf.fr/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0" +
                        "&STYLE=estompage_grayscale&FORMAT=image/png&TILEMATRIXSET=PM" +
                        "&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&LAYER=ELEVATION.SLOPES",
                attribution = "IGN © GéoPortail"
            ),
            WmsLayerConfig(
                id = "preset_ign_mnt",
                name = "IGN — Relief / MNT (hypsométrie)",
                url = "https://data.geopf.fr/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0" +
                        "&STYLE=normal&FORMAT=image/png&TILEMATRIXSET=PM" +
                        "&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&LAYER=ELEVATION.ELEVATIONGRIDCOVERAGE.HIGHRES.MNT",
                attribution = "IGN © GéoPortail"
            ),
            WmsLayerConfig(
                id = "preset_ign_rpg",
                name = "IGN — RPG (parcelles agricoles)",
                url = "https://data.geopf.fr/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0" +
                        "&STYLE=normal&FORMAT=image/png&TILEMATRIXSET=PM" +
                        "&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&LAYER=AGRICULTURE.LAND_USE_PARCELS.RPG.LATEST",
                attribution = "IGN © GéoPortail"
            ),
            WmsLayerConfig(
                id = "preset_ign_cadastre",
                name = "IGN — Cadastre",
                url = "https://data.geopf.fr/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0" +
                        "&STYLE=normal&FORMAT=image/png&TILEMATRIXSET=PM" +
                        "&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&LAYER=CADASTRALPARCELS.PARCELLAIRE_EXPRESS",
                attribution = "IGN © GéoPortail"
            ),
            WmsLayerConfig(
                id = "preset_ign_hydro",
                name = "IGN — Hydrographie",
                url = "https://data.geopf.fr/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0" +
                        "&STYLE=normal&FORMAT=image/png&TILEMATRIXSET=PM" +
                        "&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&LAYER=HYDROGRAPHY.HYDROGRAPHY",
                attribution = "IGN © GéoPortail"
            ),
            WmsLayerConfig(
                id = "preset_osm",
                name = "OpenStreetMap",
                url = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
                attribution = "© OpenStreetMap contributors"
            )
        )
    }
}

/**
 * Gère la liste des couches WMS/WMTS utilisateur — persistance JSON dans files/wms_layers.json.
 */
class WmsLayerManager(private val context: Context) {

    companion object {
        private const val TAG = "WmsLayerManager"
        private const val FILE_NAME = "wms_layers.json"
    }

    private val file: File get() = File(context.filesDir, FILE_NAME)

    fun loadLayers(): List<WmsLayerConfig> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText(Charsets.UTF_8))
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                WmsLayerConfig(
                    id          = o.getString("id"),
                    name        = o.getString("name"),
                    url         = o.getString("url"),
                    opacity     = o.optDouble("opacity", 0.7).toFloat(),
                    visible     = o.optBoolean("visible", true),
                    tileSize    = o.optInt("tileSize", 256),
                    maxZoom     = o.optInt("maxZoom", 19),
                    attribution = o.optString("attribution", "")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadLayers failed", e)
            emptyList()
        }
    }

    fun saveLayers(layers: List<WmsLayerConfig>) {
        try {
            val arr = JSONArray()
            layers.forEach { l ->
                arr.put(JSONObject().apply {
                    put("id",          l.id)
                    put("name",        l.name)
                    put("url",         l.url)
                    put("opacity",     l.opacity.toDouble())
                    put("visible",     l.visible)
                    put("tileSize",    l.tileSize)
                    put("maxZoom",     l.maxZoom)
                    put("attribution", l.attribution)
                })
            }
            file.writeText(arr.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "saveLayers failed", e)
        }
    }

    fun addLayer(layer: WmsLayerConfig): List<WmsLayerConfig> {
        val list = loadLayers().toMutableList()
        list.removeAll { it.id == layer.id }
        list.add(layer)
        saveLayers(list)
        return list
    }

    fun updateLayer(layer: WmsLayerConfig): List<WmsLayerConfig> {
        val list = loadLayers().map { if (it.id == layer.id) layer else it }
        saveLayers(list)
        return list
    }

    fun removeLayer(id: String): List<WmsLayerConfig> {
        val list = loadLayers().filter { it.id != id }
        saveLayers(list)
        return list
    }
}
