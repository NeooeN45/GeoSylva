package com.forestry.counter.presentation.screens.forestry

import android.util.Log
import com.forestry.counter.BuildConfig
import com.forestry.counter.R
import com.forestry.counter.network.SecureHttpClient

private const val TAG_LAYERS = "MapLayers"

// ── Attributions légales obligatoires ──────────────────────────────────────
internal const val ATTR_IGN = "IGN Géoportail — Licence Ouverte 2.0 (Etalab)"
internal const val ATTR_OSM = "© OpenStreetMap contributors (ODbL)"
internal const val ATTR_OPENTOPO = "© OpenStreetMap contributors, SRTM | OpenTopoMap (CC-BY-SA)"
internal const val ATTR_CARTO = "© OpenStreetMap contributors, © CARTO"
internal const val ATTR_ESRI = "© Esri, Maxar, Earthstar Geographics"
internal const val ATTR_MAPTILER = "© MapTiler © OpenStreetMap contributors"

// ── URL WMTS GéoPortail (data.geopf.fr) ─────────────────────────────────────
internal fun geopfLayer(layer: String, format: String = "image/png") =
    "https://data.geopf.fr/wmts?" +
    "SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&STYLE=normal&FORMAT=$format" +
    "&TILEMATRIXSET=PM&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&LAYER=$layer"

// ── URL tuiles vectorielles MapTiler ────────────────────────────────────────
private fun maptilerUrl(style: String): String {
    val key = BuildConfig.MAPTILER_KEY
    if (key.isBlank()) {
        Log.w(TAG_LAYERS, "MAPTILER_KEY is empty — vector tiles will fail")
    }
    return "https://api.maptiler.com/maps/$style/style.json?key=$key"
}

private fun maptilerTilesUrl(style: String): String {
    val key = BuildConfig.MAPTILER_KEY
    return "https://api.maptiler.com/tiles/$style/{z}/{x}/{y}.pbf?key=$key"
}

private fun maptilerTerrainUrl(): String {
    val key = BuildConfig.MAPTILER_KEY
    return "https://api.maptiler.com/tiles/terrain-rgb/{z}/{x}/{y}.png?key=$key"
}

// ── Styles raster (fallback / couches IGN) ──────────────────────────────────

internal fun offlineLocalStyle(name: String = "Offline Local"): String {
    return """{
  "version": 8,
  "name": "$name",
  "sources": {},
  "layers": [
    { "id": "background", "type": "background", "paint": { "background-color": "#EFF5EC" } }
  ]
}"""
}

internal fun rasterStyle(
    id: String,
    name: String,
    tileUrl: String,
    tileSize: Int = 256,
    maxZoom: Int = 19,
    attribution: String = ""
): String {
    if (!SecureHttpClient.isSecureDomain(tileUrl)) {
        Log.e(TAG_LAYERS, "URL de tuile non sécurisée: $tileUrl")
        throw SecurityException("URL de tuile non sécurisée: $tileUrl")
    }
    val attributionField = if (attribution.isNotEmpty()) ""","attribution":"$attribution"""" else ""
    // L'id de source/couche doit être unique par calque (ici dérivé de la
    // clé du calque, ex. "PLAN_IGN", "SATELLITE"...) — toutes les couches
    // raster réutilisaient auparavant le même id "tiles", ce qui amenait
    // MapLibre à considérer la source comme "compatible" d'un style à
    // l'autre lors d'un changement de calque (setStyle fait un diff plutôt
    // qu'un remplacement complet) et à réafficher les tuiles déjà en cache
    // de l'ANCIEN calque au lieu de charger celles du nouveau — d'où des
    // tuiles d'un calque précédent qui restaient "figées" (souvent en
    // noir ou en niveaux de gris) mélangées aux nouvelles.
    return """{
  "version": 8,
  "name": "$name",
  "glyphs": "https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf",
  "sources": {
    "$id": {
      "type": "raster",
      "tiles": ["$tileUrl"],
      "tileSize": $tileSize,
      "maxzoom": $maxZoom$attributionField
    }
  },
  "layers": [
    { "id": "background", "type": "background", "paint": { "background-color": "#EFF5EC" } },
    { "id": "$id", "type": "raster", "source": "$id", "paint": { "raster-fade-duration": 0 } }
  ]
}"""
}

internal fun rasterStyleMulti(
    id: String,
    name: String,
    baseTileUrl: String,
    overlayTileUrls: List<String> = emptyList(),
    tileSize: Int = 256,
    maxZoom: Int = 19,
    baseAttribution: String = "",
    overlayAttributions: List<String> = emptyList()
): String {
    val sources = mutableListOf<String>()
    val layers = mutableListOf<String>()
    val baseAttrField = if (baseAttribution.isNotEmpty()) ""","attribution":"$baseAttribution"""" else ""
    // Voir le commentaire de rasterStyle() : id unique par calque pour
    // éviter que MapLibre ne réutilise le cache de tuiles d'un calque
    // composite précédent lors d'un changement de style.
    val baseId = "${id}_base"
    sources += """"$baseId":{"type":"raster","tiles":["$baseTileUrl"],"tileSize":$tileSize,"maxzoom":$maxZoom$baseAttrField}"""
    layers += """{ "id": "background", "type": "background", "paint": { "background-color": "#EFF5EC" } }"""
    layers += """{ "id": "$baseId", "type": "raster", "source": "$baseId", "paint": { "raster-fade-duration": 0 } }"""
    overlayTileUrls.forEachIndexed { i, url ->
        val ovAttr = overlayAttributions.getOrNull(i) ?: ""
        val ovAttrField = if (ovAttr.isNotEmpty()) ""","attribution":"$ovAttr"""" else ""
        val overlayId = "${id}_overlay$i"
        sources += """"$overlayId":{"type":"raster","tiles":["$url"],"tileSize":256,"maxzoom":$maxZoom$ovAttrField}"""
        layers += """{ "id": "$overlayId", "type": "raster", "source": "$overlayId", "paint": { "raster-opacity": 0.7, "raster-fade-duration": 0 } }"""
    }
    return """{"version":8,"name":"$name","glyphs":"https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf","sources":{${sources.joinToString(",")}},"layers":[${layers.joinToString(",")}]}"""
}

/**
 * Style JSON complet d'un calque raster, mais avec les ids stables
 * "active_base"/"active_overlayN" (voir MapRenderers.kt) plutôt que l'id
 * propre au calque — utilisé pour le tout premier chargement de la carte
 * et pour tout repli en setStyle() complet, afin que les changements de
 * calque suivants puissent ensuite utiliser le remplacement incrémental
 * (swapRasterLayer) sans jamais laisser une source d'un ancien calque
 * derrière eux.
 */
internal fun activeStyleJsonFor(layer: MapLayerDef): String {
    val base = layer.tileUrls.getOrNull(0) ?: return offlineLocalStyle(layer.key)
    val overlays = layer.tileUrls.drop(1)
    return if (overlays.isEmpty()) {
        rasterStyle(ACTIVE_BASE_ID, layer.key, base, maxZoom = layer.rasterMaxZoom, attribution = layer.attribution)
    } else {
        rasterStyleMulti(
            "active",
            layer.key,
            base,
            overlayTileUrls = overlays,
            maxZoom = layer.rasterMaxZoom,
            baseAttribution = layer.attribution,
            overlayAttributions = overlays.map { layer.attribution },
        )
    }
}

// ── Styles vectoriels MapTiler avec terrain 3D ──────────────────────────────

/**
 * Style vectoriel MapTiler avec relief 3D (terrain-rgb + hillshade).
 * Inclut source DEM pour terrain 3D + hillshade.
 */
internal fun vectorStyleWithTerrain(
    styleName: String,
    maptilerStyleId: String,
    displayName: String,
    isDark: Boolean = false
): String {
    val key = BuildConfig.MAPTILER_KEY
    if (key.isBlank()) {
        Log.w(TAG_LAYERS, "MAPTILER_KEY empty — falling back to raster style")
        return rasterStyle("fallback_$styleName", displayName, geopfLayer("GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2"), attribution = ATTR_IGN)
    }

    val terrainSource = """
    "terrain-rgb": {
      "type": "raster-dem",
      "tiles": ["${maptilerTerrainUrl()}"],
      "tileSize": 256,
      "maxzoom": 12,
      "attribution": "$ATTR_MAPTILER"
    }"""

    val hillshadeLayer = """
    { "id": "hillshade", "type": "hillshade", "source": "terrain-rgb",
      "paint": {
        "hillshade-shadow-color": "${if (isDark) "#1a1a2e" else "#473B30"}",
        "hillshade-highlight-color": "${if (isDark) "#2a2a4e" else "#EEEEEE"}",
        "hillshade-accent-color": "${if (isDark) "#3a3a5e" else "#8c7a60"}",
        "hillshade-illumination-direction": 315,
        "hillshade-illumination-anchor": "map",
        "hillshade-exaggeration": 1.2
      }
    }"""

    // Contenu de base du fond de carte (schéma vectoriel OpenMapTiles) : sans ces
    // couches, seuls le fond et le relief ombré s'affichaient — la couche vecteur
    // "maptiler" n'était référencée que par les bâtiments 3D, jamais dessinée.
    val baseContentLayers = """
    { "id": "landcover-wood", "type": "fill", "source": "maptiler", "source-layer": "landcover",
      "filter": ["in", "class", "wood", "forest", "grass"],
      "paint": { "fill-color": "${if (isDark) "#1f2e22" else "#c8dbb8"}", "fill-opacity": 0.6 } },
    { "id": "landuse-park", "type": "fill", "source": "maptiler", "source-layer": "landuse",
      "filter": ["==", "class", "park"],
      "paint": { "fill-color": "${if (isDark) "#243523" else "#c9e2b3"}", "fill-opacity": 0.5 } },
    { "id": "water", "type": "fill", "source": "maptiler", "source-layer": "water",
      "paint": { "fill-color": "${if (isDark) "#16334d" else "#a3ccf0"}" } },
    { "id": "roads-major", "type": "line", "source": "maptiler", "source-layer": "transportation",
      "filter": ["in", "class", "motorway", "trunk", "primary"],
      "layout": { "line-cap": "round", "line-join": "round" },
      "paint": { "line-color": "${if (isDark) "#5a4a3a" else "#e8a94f"}", "line-width": ["interpolate", ["linear"], ["zoom"], 8, 1, 16, 4] } },
    { "id": "roads-minor", "type": "line", "source": "maptiler", "source-layer": "transportation",
      "filter": ["in", "class", "secondary", "tertiary", "minor", "service"],
      "minzoom": 11,
      "layout": { "line-cap": "round", "line-join": "round" },
      "paint": { "line-color": "${if (isDark) "#4a4a4a" else "#ffffff"}", "line-width": ["interpolate", ["linear"], ["zoom"], 11, 0.5, 16, 2.5], "line-opacity": 0.9 } },
    { "id": "place-labels", "type": "symbol", "source": "maptiler", "source-layer": "place",
      "filter": ["in", "class", "city", "town", "village"],
      "layout": { "text-field": ["get", "name"], "text-font": ["Noto Sans Regular"], "text-size": ["interpolate", ["linear"], ["zoom"], 6, 10, 14, 16] },
      "paint": { "text-color": "${if (isDark) "#e0e0e0" else "#3a3a3a"}", "text-halo-color": "${if (isDark) "#1a1a2e" else "#f5f5f0"}", "text-halo-width": 1.2 } }"""

    // Ambiance atmosphérique quand la carte est inclinée (bascule 2D/3D).
    val skyLayer = """
    { "id": "sky", "type": "sky",
      "paint": {
        "sky-type": "atmosphere",
        "sky-atmosphere-sun": [0, 0],
        "sky-atmosphere-sun-intensity": 15
      }
    }"""

    // Bâtiments extrudés en 3D (schéma OpenMapTiles, source-layer "building").
    // minzoom élevé : coût de rendu nul tant qu'on n'est pas proche du sol.
    val buildingsExtrusionLayer = """
    { "id": "buildings-3d", "type": "fill-extrusion", "source": "maptiler", "source-layer": "building",
      "minzoom": 15,
      "paint": {
        "fill-extrusion-color": "${if (isDark) "#3a3a4e" else "#d9d3c8"}",
        "fill-extrusion-height": ["coalesce", ["get", "render_height"], ["*", ["coalesce", ["get", "levels"], 2], 3]],
        "fill-extrusion-base": ["coalesce", ["get", "render_min_height"], 0],
        "fill-extrusion-opacity": 0.85
      }
    }"""

    return """{
  "version": 8,
  "name": "$displayName",
  "glyphs": "https://api.maptiler.com/fonts/{fontstack}/{range}.pbf?key=$key",
  "sources": {
    "maptiler": {
      "type": "vector",
      "url": "https://api.maptiler.com/tiles/$maptilerStyleId/tiles.json?key=$key"
    },
    $terrainSource
  },
  "terrain": {
    "source": "terrain-rgb",
    "exaggeration": 1.3
  },
  "layers": [
    { "id": "background", "type": "background",
      "paint": { "background-color": "${if (isDark) "#1a1a2e" else "#f5f5f0"}" } },
    $hillshadeLayer,
    $baseContentLayers,
    $buildingsExtrusionLayer,
    $skyLayer
  ]
}"""
}

/**
 * Style vectoriel MapTiler simple (sans terrain 3D).
 */
internal fun vectorStyleSimple(
    maptilerStyleId: String,
    displayName: String
): String {
    val key = BuildConfig.MAPTILER_KEY
    if (key.isBlank()) {
        Log.w(TAG_LAYERS, "MAPTILER_KEY empty — falling back to raster")
        return rasterStyle("fallback_$maptilerStyleId", displayName, geopfLayer("GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2"), attribution = ATTR_IGN)
    }
    // Utiliser l'URL style.json complète de MapTiler (style prêt-à-l'emploi)
    return maptilerUrl(maptilerStyleId)
}

// ── Catégories de couches ───────────────────────────────────────────────────

enum class LayerCategory(val labelResId: Int) {
    GENERAL(R.string.map_category_general)
}

/**
 * Description d'une couche de fond de carte.
 * @param styleJson Style MapLibre JSON (vectoriel ou raster)
 * @param isVector Indique si la couche utilise des tuiles vectorielles
 * @param hasTerrain Indique si la couche inclut le terrain 3D
 * @param isDark Indique si la couche est sombre (pour l'UI)
 * @param tileUrls URLs de tuiles pour téléchargement hors-ligne
 */
data class MapLayerDef(
    val key: String,
    val labelResId: Int,
    val emoji: String,
    val styleJson: String,
    val isVector: Boolean = false,
    val hasTerrain: Boolean = false,
    val isDark: Boolean = false,
    val category: LayerCategory = LayerCategory.GENERAL,
    val tileUrls: List<String> = emptyList(),
    /** Nom court adapté pour le tiroir de calques (ex. "Satellite 20cm"). */
    val previewLabelResId: Int = labelResId,
    /**
     * Source IGN (data.geopf.fr) : couverture strictement France — hors de
     * cette zone, le serveur renvoie 404 sur toutes les tuiles. Sert au
     * repli automatique vers un fond mondial (voir MapRechercheScreen)
     * plutôt que de laisser un écran noir quand la caméra se retrouve hors
     * de cette couverture (le style ne peint alors même pas son fond de
     * repli — limite observée du moteur de rendu natif).
     */
    val franceOnly: Boolean = false,
    /**
     * Attribution légale de la source (identique pour base et éventuelle
     * surcouche dans ce catalogue). Utilisée par le changement de calque
     * incrémental (swapRasterLayer) — voir MapRenderers.kt.
     */
    val attribution: String = "",
    /** Zoom max de la source raster, pour le changement de calque incrémental. */
    val rasterMaxZoom: Int = 19,
)

// ── Liste des couches disponibles ───────────────────────────────────────────

internal val MAP_LAYERS: List<MapLayerDef> = buildList {
    // ── Couches vectorielles MapTiler (qualité supérieure) ── Nécessitent
    // MAPTILER_KEY (local.properties / variable d'environnement) : sans clé,
    // vectorStyleWithTerrain()/vectorStyleSimple() replient silencieusement
    // sur le Plan IGN, rendant ces 4 entrées indiscernables les unes des
    // autres et de PLAN_IGN — on ne les propose donc que si la clé existe,
    // remplacées sinon par les couches OSM/CARTO ci-dessous (gratuites, sans
    // clé, couverture mondiale).
    if (BuildConfig.MAPTILER_KEY.isNotBlank()) {
        add(MapLayerDef(
            key = "MAPTILER_TOPO",
            labelResId = R.string.map_layer_topo,
            emoji = "⛰️",
            styleJson = vectorStyleWithTerrain("topo", "topo-v2", "MapTiler Topo 3D"),
            isVector = true,
            hasTerrain = true,
            category = LayerCategory.GENERAL,
            tileUrls = listOf(maptilerTilesUrl("topo-v2")),
            previewLabelResId = R.string.map_preview_maptiler_topo,
        ))
        add(MapLayerDef(
            key = "MAPTILER_SATELLITE",
            labelResId = R.string.map_layer_satellite,
            emoji = "🛰️",
            styleJson = vectorStyleSimple("hybrid", "MapTiler Satellite"),
            isVector = true,
            isDark = true,
            category = LayerCategory.GENERAL,
            tileUrls = listOf(maptilerTilesUrl("hybrid")),
            previewLabelResId = R.string.map_preview_maptiler_satellite,
        ))
        add(MapLayerDef(
            key = "MAPTILER_STREETS",
            labelResId = R.string.map_layer_carto,
            emoji = "🧭",
            styleJson = vectorStyleSimple("streets-v2", "MapTiler Streets"),
            isVector = true,
            category = LayerCategory.GENERAL,
            tileUrls = listOf(maptilerTilesUrl("streets-v2")),
            previewLabelResId = R.string.map_preview_streets,
        ))
        add(MapLayerDef(
            key = "MAPTILER_DARK",
            labelResId = R.string.map_layer_dark,
            emoji = "🌙",
            styleJson = vectorStyleSimple("dark-v2", "MapTiler Dark Matter"),
            isVector = true,
            isDark = true,
            category = LayerCategory.GENERAL,
            tileUrls = listOf(maptilerTilesUrl("dark-v2")),
            previewLabelResId = R.string.map_preview_dark,
        ))
    }

    // ── Couches IGN (raster — hybride) ──
    add(MapLayerDef(
        key = "PLAN_IGN",
        labelResId = R.string.map_layer_plan_ign,
        emoji = "🗺️",
        styleJson = rasterStyle("PLAN_IGN", "Plan IGN v2", geopfLayer("GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2"), attribution = ATTR_IGN),
        category = LayerCategory.GENERAL,
        tileUrls = listOf(geopfLayer("GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2")),
        previewLabelResId = R.string.map_preview_plan_ign,
        franceOnly = true,
        attribution = ATTR_IGN,
    ))
    add(MapLayerDef(
        key = "ORTHO_IGN",
        labelResId = R.string.map_layer_ortho_ign,
        emoji = "🛰️",
        styleJson = rasterStyle("ORTHO_IGN", "Ortho IGN", geopfLayer("ORTHOIMAGERY.ORTHOPHOTOS", "image/jpeg"), attribution = ATTR_IGN),
        isDark = true,
        category = LayerCategory.GENERAL,
        tileUrls = listOf(geopfLayer("ORTHOIMAGERY.ORTHOPHOTOS", "image/jpeg")),
        previewLabelResId = R.string.map_preview_ortho_ign,
        franceOnly = true,
        attribution = ATTR_IGN,
    ))

    // ── Couches composites IGN ──
    add(MapLayerDef(
        key = "PLAN_IGN_CADASTRE",
        labelResId = R.string.map_layer_plan_ign_cadastre,
        emoji = "📐",
        styleJson = rasterStyleMulti(
            "PLAN_IGN_CADASTRE",
            "Plan IGN + Cadastre",
            geopfLayer("GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2"),
            overlayTileUrls = listOf(geopfLayer("CADASTRALPARCELS.PARCELLAIRE_EXPRESS")),
            baseAttribution = ATTR_IGN,
            overlayAttributions = listOf(ATTR_IGN)
        ),
        category = LayerCategory.GENERAL,
        tileUrls = listOf(
            geopfLayer("GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2"),
            geopfLayer("CADASTRALPARCELS.PARCELLAIRE_EXPRESS")
        ),
        previewLabelResId = R.string.map_preview_plan_cadastre,
        franceOnly = true,
        attribution = ATTR_IGN,
    ))
    add(MapLayerDef(
        key = "ORTHO_CADASTRE",
        labelResId = R.string.map_layer_ortho_cadastre,
        emoji = "🏘️",
        styleJson = rasterStyleMulti(
            "ORTHO_CADASTRE",
            "Ortho IGN + Cadastre",
            geopfLayer("ORTHOIMAGERY.ORTHOPHOTOS", "image/jpeg"),
            overlayTileUrls = listOf(geopfLayer("CADASTRALPARCELS.PARCELLAIRE_EXPRESS")),
            baseAttribution = ATTR_IGN,
            overlayAttributions = listOf(ATTR_IGN)
        ),
        isDark = true,
        category = LayerCategory.GENERAL,
        tileUrls = listOf(
            geopfLayer("ORTHOIMAGERY.ORTHOPHOTOS", "image/jpeg"),
            geopfLayer("CADASTRALPARCELS.PARCELLAIRE_EXPRESS")
        ),
        previewLabelResId = R.string.map_preview_ortho_cadastre,
        franceOnly = true,
        attribution = ATTR_IGN,
    ))

    // ── Couches internationales (raster) ──
    add(MapLayerDef(
        key = "TOPO",
        labelResId = R.string.map_layer_topo,
        emoji = "🏔️",
        styleJson = rasterStyle("TOPO", "OpenTopoMap", "https://tile.opentopomap.org/{z}/{x}/{y}.png", maxZoom = 17, attribution = ATTR_OPENTOPO),
        tileUrls = listOf("https://tile.opentopomap.org/{z}/{x}/{y}.png"),
        previewLabelResId = R.string.map_preview_relief,
        attribution = ATTR_OPENTOPO,
        rasterMaxZoom = 17,
    ))
    add(MapLayerDef(
        key = "SATELLITE",
        labelResId = R.string.map_layer_satellite,
        emoji = "🌍",
        styleJson = rasterStyle("SATELLITE", "ESRI Satellite", "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}", attribution = ATTR_ESRI),
        isDark = true,
        tileUrls = listOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"),
        previewLabelResId = R.string.map_preview_satellite_monde,
        attribution = ATTR_ESRI,
    ))
    add(MapLayerDef(
        key = "OSM_STANDARD",
        labelResId = R.string.map_layer_osm,
        emoji = "🗺️",
        styleJson = rasterStyle("OSM_STANDARD", "OpenStreetMap", "https://tile.openstreetmap.org/{z}/{x}/{y}.png", maxZoom = 19, attribution = ATTR_OSM),
        tileUrls = listOf("https://tile.openstreetmap.org/{z}/{x}/{y}.png"),
        previewLabelResId = R.string.map_layer_osm,
        attribution = ATTR_OSM,
    ))
    add(MapLayerDef(
        key = "CARTO_LIGHT",
        labelResId = R.string.map_layer_carto_light,
        emoji = "☀️",
        styleJson = rasterStyle("CARTO_LIGHT", "CARTO Positron", "https://basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png", maxZoom = 20, attribution = ATTR_CARTO),
        tileUrls = listOf("https://basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png"),
        previewLabelResId = R.string.map_preview_carto_light,
        attribution = ATTR_CARTO,
        rasterMaxZoom = 20,
    ))
    add(MapLayerDef(
        key = "CARTO_DARK",
        labelResId = R.string.map_layer_carto_dark,
        emoji = "🌑",
        styleJson = rasterStyle("CARTO_DARK", "CARTO Dark Matter", "https://basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png", maxZoom = 20, attribution = ATTR_CARTO),
        isDark = true,
        tileUrls = listOf("https://basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png"),
        previewLabelResId = R.string.map_preview_carto_dark,
        attribution = ATTR_CARTO,
        rasterMaxZoom = 20,
    ))

    // ── Couche hors-ligne locale ──
    add(MapLayerDef(
        key = "OFFLINE_LOCAL",
        labelResId = R.string.map_layer_offline_local,
        emoji = "📥",
        styleJson = offlineLocalStyle("Offline Local"),
        tileUrls = emptyList(),
        previewLabelResId = R.string.map_layer_offline_local,
    ))
}

// ── Palette de couleurs pour les essences ───────────────────────────────────
internal val ESSENCE_COLOR_PALETTE = intArrayOf(
    0xFF2E7D32.toInt(),
    0xFF1565C0.toInt(),
    0xFFEF6C00.toInt(),
    0xFF7B1FA2.toInt(),
    0xFFC62828.toInt(),
    0xFF00838F.toInt(),
    0xFF4E342E.toInt(),
    0xFF9E9D24.toInt(),
    0xFFAD1457.toInt(),
    0xFF37474F.toInt(),
)

// ── Palette pour les pickers de couleur (fill / border) ─────────────────────
internal val SHP_COLOR_PALETTE = listOf(
    0xFF2E7D32.toInt(),
    0xFF1B5E20.toInt(),
    0xFF4CAF50.toInt(),
    0xFF81C784.toInt(),
    0xFF1565C0.toInt(),
    0xFF42A5F5.toInt(),
    0xFFEF6C00.toInt(),
    0xFFF44336.toInt(),
    0xFF9C27B0.toInt(),
    0xFF795548.toInt(),
    0xFF607D8B.toInt(),
    0xFF000000.toInt(),
)
