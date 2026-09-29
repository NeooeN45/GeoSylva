package com.forestry.counter.presentation.screens.forestry

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forestry.counter.R
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.domain.location.OfflineTileManager
import com.forestry.counter.domain.location.OfflineTilePolicy
import com.forestry.counter.domain.location.TreeNavigator
import com.forestry.counter.domain.location.WktUtils
import com.forestry.counter.domain.model.Essence
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.repository.EssenceRepository
import com.forestry.counter.domain.repository.ParcelleRepository
import com.forestry.counter.domain.repository.TigeRepository
import com.forestry.counter.presentation.theme.Elevation
import com.forestry.counter.presentation.theme.GpsBon
import com.forestry.counter.presentation.theme.GpsExcellent
import com.forestry.counter.presentation.theme.GpsMauvais
import com.forestry.counter.presentation.theme.GpsModere
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.Motion
import com.forestry.counter.presentation.theme.Space
import com.forestry.counter.presentation.theme.Touch
import androidx.compose.ui.graphics.toArgb
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapboxMapOptions
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "MapRechercheScreen"

/**
 * Mode Recherche de l'onglet Carte — nouvelle base (esprit QField).
 *
 * Reprend le moteur de rendu MapLibre existant (fiable, ce n'est pas lui
 * qui posait problème) mais avec une interface entièrement repensée : une
 * seule barre d'outils flottante en bas, cibles tactiles 64dp, plus de
 * panneaux qui se chevauchent. V1 volontairement resserrée sur l'essentiel
 * (calques, mesure, légende, position, tiges géolocalisées, navigation
 * vers une tige) — tracé GPS de parcelle et shapefile reviendront sur
 * cette même base dans une passe dédiée plutôt que d'être portés tels
 * quels depuis l'ancien écran.
 */
@Composable
fun MapRechercheScreen(
    parcelleId: String,
    tigeRepository: TigeRepository,
    essenceRepository: EssenceRepository? = null,
    parcelleRepository: ParcelleRepository? = null,
    preferencesManager: UserPreferencesManager,
    offlineTileManager: OfflineTileManager? = null,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    // Masqué quand la carte est intégrée comme simple onglet d'un écran qui
    // a déjà sa propre flèche retour (ex. onglet Carte de ForestDetailScreen)
    // — éviter deux flèches retour redondantes à l'écran.
    showBackButton: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val tigesFlow = remember(parcelleId) {
        when {
            parcelleId == "none" -> flowOf(emptyList<Tige>())
            parcelleId == "all" -> tigeRepository.getAllTiges()
            parcelleId.startsWith("forest_") -> {
                val forestId = parcelleId.removePrefix("forest_")
                if (parcelleRepository != null) {
                    parcelleRepository.getParcellesByForest(forestId).flatMapLatest { parcelles ->
                        if (parcelles.isEmpty()) flowOf(emptyList())
                        else combine(parcelles.map { tigeRepository.getTigesByParcelle(it.id) }) { it.flatMap { l -> l } }
                    }
                } else flowOf(emptyList())
            }
            parcelleId.startsWith("placette_") -> tigeRepository.getTigesByPlacette(parcelleId.removePrefix("placette_"))
            else -> tigeRepository.getTigesByParcelle(parcelleId)
        }
    }
    val tiges by tigesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val essences by (essenceRepository?.getAllEssences() ?: flowOf(emptyList<Essence>()))
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val essenceMap = remember(essences) { essences.associateBy { it.code.uppercase() } }

    val mapLastLayerKey by preferencesManager.mapLastLayerKey.collectAsStateWithLifecycle(initialValue = "OSM_STANDARD")
    val mapOnlyReliableGps by preferencesManager.mapOnlyReliableGps.collectAsStateWithLifecycle(initialValue = false)
    val mapReliableGpsThresholdM by preferencesManager.mapReliableGpsThresholdM.collectAsStateWithLifecycle(initialValue = 8f)

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) hasLocationPermission = true }
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    remember(context) {
        try { Mapbox.getInstance(context) } catch (e: Throwable) { Log.w(TAG, "Mapbox.getInstance failed", e) }
        com.forestry.counter.domain.location.MapLibreHttpConfig.configure(context)
        true
    }

    @Suppress("NAME_SHADOWING")
    val offlineTileManager = offlineTileManager ?: remember(context) { OfflineTileManager(context) }
    val offlineProgress by offlineTileManager.downloadProgress.collectAsStateWithLifecycle()
    var hasOfflineTilesState by remember { mutableStateOf(offlineTileManager.hasOfflineTiles()) }
    var offlineRegions by remember { mutableStateOf(offlineTileManager.listRegions()) }
    var showOfflineSheet by remember { mutableStateOf(false) }
    var offlineEstimate by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var pendingDownloadBounds by remember { mutableStateOf<com.mapbox.mapboxsdk.geometry.LatLngBounds?>(null) }
    LaunchedEffect(offlineProgress) {
        if (offlineProgress?.isComplete == true && offlineProgress?.error == null) {
            hasOfflineTilesState = offlineTileManager.hasOfflineTiles()
            offlineRegions = offlineTileManager.listRegions()
        }
    }

    val geoTiges = remember(tiges) {
        tiges.mapNotNull { t ->
            val (lon, lat, _) = WktUtils.parsePointZ(t.gpsWkt)
            if (lon != null && lat != null) Triple(t, lon, lat) else null
        }
    }
    val displayedGeoTiges = remember(geoTiges, mapOnlyReliableGps, mapReliableGpsThresholdM) {
        if (!mapOnlyReliableGps) geoTiges
        else geoTiges.filter { (t, _, _) -> (t.precisionM ?: Double.MAX_VALUE) <= mapReliableGpsThresholdM.toDouble() }
    }
    val withGps = displayedGeoTiges.size
    val total = tiges.size

    val essenceColors = remember(displayedGeoTiges) {
        val map = mutableMapOf<String, Int>()
        var idx = 0
        displayedGeoTiges.forEach { (t, _, _) -> map.getOrPut(t.essenceCode.uppercase()) { ESSENCE_COLOR_PALETTE[idx++ % ESSENCE_COLOR_PALETTE.size] } }
        map
    }
    val essenceCounts = remember(displayedGeoTiges) {
        displayedGeoTiges.groupBy { it.first.essenceCode.uppercase() }.mapValues { it.value.size }
    }
    var hiddenEssences by remember { mutableStateOf(emptySet<String>()) }
    val filteredGeoTiges = remember(displayedGeoTiges, hiddenEssences) {
        if (hiddenEssences.isEmpty()) displayedGeoTiges
        else displayedGeoTiges.filter { (t, _, _) -> t.essenceCode.uppercase() !in hiddenEssences }
    }

    var mapReady by remember { mutableStateOf(false) }
    var mapLibreMap by remember { mutableStateOf<MapboxMap?>(null) }
    // Clé de la couche réellement rendue par MapLibre (peut différer de
    // mapLastLayerKey si les préférences ont chargé après l'initialisation
    // asynchrone de la carte) — sert à détecter et corriger le désynchronisme.
    var mapRenderedLayerKey by remember { mutableStateOf("") }
    // true quand le style actif utilise les ids stables "active_base"/
    // "active_overlayN" (voir MapRenderers.kt) — condition nécessaire pour
    // pouvoir passer par swapRasterLayer() au prochain changement de
    // calque plutôt que par un setStyle() complet.
    var activeStyleIsIncremental by remember { mutableStateOf(false) }
    val initialLayerIdx = remember(mapLastLayerKey) { MAP_LAYERS.indexOfFirst { it.key == mapLastLayerKey }.takeIf { it >= 0 } ?: 0 }
    var layerLoadState by remember(mapLastLayerKey) { mutableStateOf(MapLayerLoadState(initialLayerIdx)) }
    val currentLayerIdx = layerLoadState.displayedIndex
    var showLayerPicker by remember { mutableStateOf(false) }
    var showLegend by remember { mutableStateOf(false) }
    var tigeTapAttached by remember { mutableStateOf(false) }
    var is3DActive by remember { mutableStateOf(false) }
    var gpsAccuracyM by remember { mutableStateOf<Float?>(null) }
    var showGpsPrecisionDialog by remember { mutableStateOf(false) }
    var toolbarExpanded by remember { mutableStateOf(false) }
    var showMeasureSheet by remember { mutableStateOf(false) }
    var tappedTree by remember { mutableStateOf<TappedTreeInfo?>(null) }
    var dismissedGpsBanner by remember { mutableStateOf(false) }

    // ── Outil de mesure ──
    val measureActiveState = remember { mutableStateOf(false) }
    var measureActive by measureActiveState
    val measurePointsState = remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var measurePoints by measurePointsState
    var measureMode by remember { mutableStateOf(MeasureMode.DISTANCE) }
    var measureDistUnit by remember { mutableStateOf(MeasureDistUnit.M) }
    var measureAreaUnit by remember { mutableStateOf(MeasureAreaUnit.HA) }
    var measureColor by remember { mutableStateOf(MEASURE_COLORS[0]) }
    var showSavedMeasuresPanel by remember { mutableStateOf(false) }
    var showMeasureSaveDialog by remember { mutableStateOf(false) }
    var measureSaveName by remember { mutableStateOf("") }

    val treeNavigator = remember(context) { TreeNavigator(context) }
    val navState by treeNavigator.state.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose { treeNavigator.stopNavigation() } }

    // Tracé GPS de parcelle : hors périmètre v1, tracer non-actif conservé pour
    // satisfaire la signature partagée de MapRenderEffects.
    val gpsTracer = remember(context) { com.forestry.counter.domain.location.GpsParcelTracer(context) }
    val traceState by gpsTracer.state.collectAsStateWithLifecycle()

    // vectorStyleSimple() (MapLayers.kt) renvoie une URL de style.json distante pour
    // certains fonds de carte (Satellite/Streets/Dark) plutôt que du JSON inline —
    // fromJson() sur une URL échoue silencieusement, d'où ce garde-fou.
    fun styleBuilderFor(styleJson: String): Style.Builder =
        if (styleJson.startsWith("http")) Style.Builder().fromUri(styleJson)
        else Style.Builder().fromJson(styleJson)

    fun toggle3D() {
        val map = mapLibreMap ?: return
        is3DActive = !is3DActive
        val targetTilt = if (is3DActive) 55.0 else 0.0
        val newPosition = com.mapbox.mapboxsdk.camera.CameraPosition.Builder(map.cameraPosition)
            .tilt(targetTilt)
            .build()
        map.animateCameraSmooth(CameraUpdateFactory.newCameraPosition(newPosition))
    }

    lateinit var startLayerRequest: (MapLayerLoadRequest) -> Unit

    fun applyLayerDecision(decision: MapLayerLoadDecision) {
        layerLoadState = decision.state
        val next = decision.requestToStart
        if (next != null) {
            startLayerRequest(next)
        } else if (!decision.state.isLoading && decision.state.failedIndex == null) {
            showLayerPicker = false
        }
    }

    startLayerRequest = start@ { request ->
        val map = mapLibreMap
        if (map == null) {
            applyLayerDecision(layerLoadState.fail(request.id))
            return@start
        }
        val layer = MAP_LAYERS.getOrElse(request.layerIndex) { MAP_LAYERS[0] }
        val isOfflineSpecial = layer.key == "OFFLINE_LOCAL" && offlineTileManager.hasOfflineTiles()
        scope.launch { preferencesManager.setMapLastLayerKey(layer.key) }

        // Chemin rapide : le style actif utilise déjà les ids stables
        // "active_base"/"active_overlayN" et le nouveau calque est un
        // raster simple — on remplace juste la source/couche active, sans
        // recharger tout le style (voir swapRasterLayer, MapRenderers.kt).
        // Bien plus fiable qu'un setStyle() complet à chaque changement.
        if (activeStyleIsIncremental && !layer.isVector && !isOfflineSpecial && layer.tileUrls.isNotEmpty()) {
            val style = map.style
            if (style != null) {
                try {
                    swapRasterLayer(
                        style = style,
                        baseTileUrl = layer.tileUrls[0],
                        overlayTileUrls = layer.tileUrls.drop(1),
                        attribution = layer.attribution,
                        maxZoom = layer.rasterMaxZoom,
                    )
                    renderTigesOnMap(style, filteredGeoTiges, essenceMap, essenceColors)
                    // Le composant de localisation garde une référence interne
                    // au Style — la mutation directe (add/removeSource/Layer)
                    // sans passer par un setStyle() complet peut l'invalider
                    // silencieusement (observé : LocationComponentNotInitializedException
                    // en boucle après un changement de calque incrémental).
                    // Réactivation défensive à chaque changement.
                    enableLocationComponent(map, style, context)
                    mapRenderedLayerKey = layer.key
                    applyLayerDecision(layerLoadState.succeed(request.id))
                    return@start
                } catch (e: Throwable) {
                    Log.w(TAG, "Incremental layer swap failed, falling back to full reload", e)
                    // tombe dans le repli setStyle() complet ci-dessous
                }
            }
        }

        // Repli setStyle() complet — vectoriel (MapTiler), hors-ligne, ou
        // échec du chemin rapide. Les calques raster utilisent ici aussi
        // les ids "active_base"/"active_overlayN" (activeStyleJsonFor)
        // pour que le PROCHAIN changement de calque puisse, lui, emprunter
        // le chemin rapide.
        val styleJson = when {
            isOfflineSpecial -> offlineTileManager.buildOfflineStyle(offlineTileManager.downloadedLayerCount().coerceAtLeast(1))
            layer.isVector -> layer.styleJson
            else -> activeStyleJsonFor(layer)
        }
        try {
            map.setStyle(styleBuilderFor(styleJson)) { style ->
                activeStyleIsIncremental = !isOfflineSpecial && !layer.isVector
                enableLocationComponent(map, style, context)
                renderTigesOnMap(style, filteredGeoTiges, essenceMap, essenceColors)
                mapRenderedLayerKey = layer.key
                applyLayerDecision(layerLoadState.succeed(request.id))
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Style switch failed", e)
            applyLayerDecision(layerLoadState.fail(request.id))
        }
    }

    fun switchLayer(index: Int) {
        applyLayerDecision(layerLoadState.request(index))
    }

    fun retryLayer() {
        applyLayerDecision(layerLoadState.retry())
    }

    // Si la préférence de calque (mapLastLayerKey) a changé après que la carte
    // soit déjà rendue (race condition initialisation async vs DataStore), forcer
    // le passage au bon calque. Sans ça, la carte reste sur le calque chargé
    // par la factory (valeur initiale "OSM_STANDARD") même si l'utilisateur
    // avait sauvegardé un calque différent lors d'une session précédente.
    LaunchedEffect(mapReady, mapLastLayerKey) {
        if (!mapReady || mapRenderedLayerKey.isEmpty()) return@LaunchedEffect
        if (mapRenderedLayerKey == mapLastLayerKey) return@LaunchedEffect
        val targetIdx = MAP_LAYERS.indexOfFirst { it.key == mapLastLayerKey }.takeIf { it >= 0 }
            ?: return@LaunchedEffect
        if (!layerLoadState.isLoading) switchLayer(targetIdx)
    }

    LaunchedEffect(layerLoadState.loadingRequest?.id) {
        val request = layerLoadState.loadingRequest ?: return@LaunchedEffect
        delay(20_000)
        if (layerLoadState.loadingRequest?.id == request.id) {
            Log.w(TAG, "Map style load timed out for ${MAP_LAYERS.getOrNull(request.layerIndex)?.key}")
            applyLayerDecision(layerLoadState.fail(request.id))
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // ── Fond de repli Compose ──
        // Derrière la TextureView (TextureMode MapLibre) : couvre tout pixel
        // transparent que le moteur GL émet pendant les transitions de style
        // (setStyle async) ou avant le premier rendu — couche indépendante du
        // GL, toujours visible à travers l'alpha du TextureView.
        Box(modifier = Modifier.fillMaxSize().background(Color(android.graphics.Color.parseColor("#EFF5EC"))))

        // ── Carte MapLibre ──
        val lifecycleOwner = LocalLifecycleOwner.current
        var mapError by remember { mutableStateOf(false) }
        val mapView = remember {
            try {
                // TextureMode : MapLibre dessine dans une TextureView (rendu
                // composité Android) plutôt qu'une SurfaceView OpenGL dont la
                // surface est toujours noire par défaut (setBackgroundColor
                // est sans effet sur une SurfaceView). En TextureMode, la
                // couleur de fond Android s'applique réellement et couvre les
                // zones sans tuile (chargement en cours, 404 hors couverture).
                val options = MapboxMapOptions.createFromAttributes(context)
                    .textureMode(true)
                MapView(context, options).apply {
                    setBackgroundColor(android.graphics.Color.parseColor("#EFF5EC"))
                }
            } catch (e: Throwable) { mapError = true; null }
        }

        DisposableEffect(mapView) {
            val listener = MapView.OnDidFailLoadingMapListener {
                val request = layerLoadState.loadingRequest ?: return@OnDidFailLoadingMapListener
                Log.w(TAG, "MapLibre reported a style loading failure for ${MAP_LAYERS.getOrNull(request.layerIndex)?.key}")
                applyLayerDecision(layerLoadState.fail(request.id))
            }
            mapView?.addOnDidFailLoadingMapListener(listener)
            onDispose { mapView?.removeOnDidFailLoadingMapListener(listener) }
        }

        if (mapView != null && !mapError) {
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    try {
                        when (event) {
                            Lifecycle.Event.ON_START -> mapView.onStart()
                            Lifecycle.Event.ON_RESUME -> mapView.onResume()
                            Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                            Lifecycle.Event.ON_STOP -> mapView.onStop()
                            Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                            else -> {}
                        }
                    } catch (e: Throwable) { Log.w(TAG, "mapView lifecycle event failed", e) }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    // Désactive le LocationComponent (coupe son écouteur de
                    // capteur boussole) AVANT de détruire la vue — sinon un
                    // événement capteur en vol peut retomber sur un Style
                    // déjà invalidé et planter l'app (IllegalStateException
                    // MapLibre "newer style is loading/has loaded").
                    try { mapLibreMap?.locationComponent?.isLocationComponentEnabled = false } catch (e: Throwable) { Log.w(TAG, "locationComponent disable failed", e) }
                    mapReady = false
                    mapLibreMap = null
                    try { mapView.onDestroy() } catch (e: Throwable) { Log.w(TAG, "mapView.onDestroy failed", e) }
                }
            }

            AndroidView(
                factory = {
                    mapView.apply {
                        try {
                            onCreate(null)
                            getMapAsync { map ->
                                try {
                                    val selectedLayer = MAP_LAYERS.getOrElse(currentLayerIdx) { MAP_LAYERS[0] }
                                    val initIsOfflineSpecial = selectedLayer.key == "OFFLINE_LOCAL" && offlineTileManager.hasOfflineTiles()
                                    val initStyleJson = when {
                                        initIsOfflineSpecial -> offlineTileManager.buildOfflineStyle(offlineTileManager.downloadedLayerCount().coerceAtLeast(1))
                                        selectedLayer.isVector -> selectedLayer.styleJson
                                        // Ids stables "active_base"/"active_overlayN" dès le premier
                                        // chargement, pour que le premier changement de calque puisse
                                        // déjà emprunter le remplacement incrémental (swapRasterLayer).
                                        else -> activeStyleJsonFor(selectedLayer)
                                    }
                                    map.setStyle(styleBuilderFor(initStyleJson)) { style ->
                                        mapLibreMap = map
                                        mapReady = true
                                        activeStyleIsIncremental = !initIsOfflineSpecial && !selectedLayer.isVector
                                        mapRenderedLayerKey = selectedLayer.key
                                        enableLocationComponent(map, style, context)
                                        renderTigesOnMap(style, filteredGeoTiges, essenceMap, essenceColors)
                                        if (!tigeTapAttached) {
                                            map.addOnMapClickListener { latLng ->
                                                if (measureActiveState.value) {
                                                    measurePointsState.value = measurePointsState.value + latLng
                                                    true
                                                } else false
                                            }
                                            attachTigeTapInfo(map, context) { info -> tappedTree = info }
                                            tigeTapAttached = true
                                        }
                                    }
                                    map.uiSettings.apply {
                                        isCompassEnabled = true
                                        // Par défaut la boussole se place en haut à droite,
                                        // exactement derrière la pastille de qualité GPS
                                        // (elle aussi TopEnd) — déplacée en bas à droite,
                                        // zone libre de tout autre contrôle flottant.
                                        compassGravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                                        setCompassMargins(0, 0, 16, 16)
                                        isRotateGesturesEnabled = true
                                        isZoomGesturesEnabled = true
                                        isScrollGesturesEnabled = true
                                        isTiltGesturesEnabled = true
                                        setAttributionMargins(16, 0, 0, 16)
                                    }
                                } catch (e: Throwable) {
                                    Log.w(TAG, "map style setup failed, fallback", e)
                                    map.setStyle(Style.Builder().fromJson(offlineLocalStyle("Offline fallback"))) { style ->
                                        mapLibreMap = map
                                        mapReady = true
                                        enableLocationComponent(map, style, context)
                                    }
                                }
                            }
                        } catch (e: Throwable) { Log.w(TAG, "map style setup failed", e) }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Fallback si le SDK carte échoue à s'initialiser.
            Image(
                painter = painterResource(id = R.drawable.forest_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        MapRenderEffects(
            mapLibreMap = mapLibreMap,
            mapReady = mapReady,
            hasLocationPermission = hasLocationPermission,
            displayedGeoTiges = displayedGeoTiges,
            filteredGeoTiges = filteredGeoTiges,
            geoTiges = geoTiges,
            essenceColors = essenceColors,
            essenceMap = essenceMap,
            traceState = traceState,
            gpsTracer = gpsTracer,
            measurePoints = measurePoints,
            measureMode = measureMode,
            measureColor = measureColor,
        )

        // Sonde légère de la précision GPS courante (mêmes seuils que
        // GpsAverager.GpsQuality) — alimente la pastille de fiabilité en
        // haut à droite et la couleur de pulsation du puck. Pas de second
        // listener de localisation en parallèle de celui de MapLibre.
        //
        // Un repli automatique de calque (hors couverture IGN → calque
        // mondial) avait été tenté ici, mais déclenchait un second
        // setStyle() en pleine phase de chargement du calque initial : les
        // deux transitions de style se chevauchaient et le rendu composait
        // des tuiles des deux styles à la fois (par ex. relief sombre et
        // OSM couleur dans des quadrants différents de l'écran) — un bug
        // bien plus gênant que celui qu'il tentait de corriger. Retiré :
        // mieux vaut un unique changement de style explicite (choix de
        // l'utilisateur) qu'une bascule automatique qui peut entrer en
        // course avec le chargement en cours.
        LaunchedEffect(mapReady, hasLocationPermission) {
            if (!mapReady || !hasLocationPermission) return@LaunchedEffect
            var lastColor: Int? = null
            while (isActive) {
                val map = mapLibreMap
                if (map != null) {
                    try {
                        val accuracy = map.locationComponent.lastKnownLocation?.accuracy
                        // Ne pas écraser la dernière précision connue par un null
                        // transitoire (le composant peut renvoyer une position
                        // nulle entre deux fix) — sinon la pastille clignote au
                        // gris "inconnu" en boucle, donnant une impression de
                        // signal peu fiable alors que le GPS fonctionne.
                        if (accuracy != null) {
                            gpsAccuracyM = accuracy
                            val color = when {
                                accuracy <= 3f -> GpsExcellent
                                accuracy <= 6f -> GpsBon
                                accuracy <= 12f -> GpsModere
                                else -> GpsMauvais
                            }
                            if (color.toArgb() != lastColor) {
                                val options = map.locationComponent.locationComponentOptions
                                    .toBuilder()
                                    .pulseColor(color.toArgb())
                                    .build()
                                map.locationComponent.applyStyle(options)
                                lastColor = color.toArgb()
                            }
                        }
                    } catch (e: Throwable) { Log.w(TAG, "puck accuracy recolor failed", e) }
                }
                kotlinx.coroutines.delay(1000)
            }
        }

        // ── Bouton retour minimal ──
        if (showBackButton) {
            Surface(
                onClick = onNavigateBack,
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shadowElevation = Elevation.overlay,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Space.sm)
                    .size(Touch.min),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        modifier = Modifier.size(Space.md),
                    )
                }
            }
        }

        if (offlineProgress != null && offlineProgress?.isComplete != true) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shadowElevation = Elevation.overlay,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = Space.sm, start = Touch.min + Space.md),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(Touch.min).padding(Space.xs))
            }
        }

        // ── Pastille de fiabilité GPS ──
        val gpsQualityColor = when {
            gpsAccuracyM == null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            gpsAccuracyM!! <= 6f -> GpsExcellent
            gpsAccuracyM!! <= 12f -> GpsModere
            else -> GpsMauvais
        }
        Surface(
            onClick = { showGpsPrecisionDialog = true },
            shape = androidx.compose.foundation.shape.CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shadowElevation = Elevation.overlay,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Space.sm)
                .size(Touch.min),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(Space.sm)
                        .background(gpsQualityColor, androidx.compose.foundation.shape.CircleShape),
                )
            }
        }

        if (showGpsPrecisionDialog) {
            val qualityLabel = when {
                gpsAccuracyM == null -> stringResource(R.string.gps_quality_unknown)
                gpsAccuracyM!! <= 3f -> stringResource(R.string.gps_quality_excellent)
                gpsAccuracyM!! <= 6f -> stringResource(R.string.gps_quality_good)
                gpsAccuracyM!! <= 12f -> stringResource(R.string.gps_quality_moderate)
                else -> stringResource(R.string.gps_quality_poor)
            }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showGpsPrecisionDialog = false },
                title = { Text(stringResource(R.string.gps_precision_dialog_title)) },
                text = {
                    Text(
                        if (gpsAccuracyM != null) {
                            stringResource(R.string.gps_precision_dialog_body, qualityLabel, gpsAccuracyM!!)
                        } else {
                            stringResource(R.string.gps_precision_dialog_unknown)
                        }
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showGpsPrecisionDialog = false }) {
                        Text(stringResource(R.string.ok))
                    }
                },
            )
        }

        MapLayerPicker(
            visible = showLayerPicker,
            loadState = layerLoadState,
            hasOfflineTilesState = hasOfflineTilesState,
            offlineTileManager = offlineTileManager,
            onLayerSelected = { index -> switchLayer(index) },
            onRetry = { retryLayer() },
            onDismiss = { showLayerPicker = false },
            is3DActive = is3DActive,
            onToggle3D = { toggle3D() },
        )

        MapLegendPanel(
            visible = showLegend,
            essenceColors = essenceColors,
            essenceCounts = essenceCounts,
            hiddenEssences = hiddenEssences,
            essenceMap = essenceMap,
            onToggleEssence = { code, hide -> hiddenEssences = if (hide) hiddenEssences + code else hiddenEssences - code },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = Touch.fieldPrimary + Space.xl, end = Space.sm),
        )

        MapGpsWarningBanner(
            geoTiges = geoTiges,
            dismissed = dismissedGpsBanner,
            onDismiss = { dismissedGpsBanner = true },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = Touch.fieldPrimary + Space.xl),
        )

        MapTigeInfoPanel(
            tappedTree = tappedTree,
            navActive = navState.isActive,
            hasLocationPermission = hasLocationPermission,
            onRequestPermission = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
            onStartNavigation = { info ->
                treeNavigator.startNavigation(
                    TreeNavigator.Target(
                        tigeId = "", essenceName = info.essenceName, essenceCode = info.essenceCode,
                        diamCm = info.diamCm ?: 0.0, hauteurM = info.hauteurM, lat = info.lat, lon = info.lon,
                    )
                )
                tappedTree = null
            },
            onDismiss = { tappedTree = null },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = Touch.fieldPrimary + Space.xl, start = Space.md, end = Space.md),
        )

        MapNavigationOverlay(
            navState = navState,
            onStopNavigation = { treeNavigator.stopNavigation() },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = Touch.fieldPrimary + Space.xl, start = Space.md, end = Space.md),
        )

        MapOfflineProgressBar(
            progress = offlineProgress,
            onClearProgress = { offlineTileManager.clearProgress() },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = Space.md, vertical = Touch.fieldPrimary + Space.xl),
        )

        MapNoGpsMessage(
            total = total, withGps = withGps, geoTigesSize = geoTiges.size,
            mapOnlyReliableGps = mapOnlyReliableGps, mapReliableGpsThresholdM = mapReliableGpsThresholdM,
            onDismiss = { dismissedGpsBanner = true },
            modifier = Modifier.align(Alignment.Center).padding(Space.xl),
        )
        // ── Tiroir de mesure (visibilité pilotée par showMeasureSheet, pas
        // par measureActive — voir MapMeasurePanel) ──
        MapMeasurePanel(
            state = MapMeasurePanelState(
                isActive = measureActive, points = measurePoints, mode = measureMode,
                distUnit = measureDistUnit, areaUnit = measureAreaUnit, color = measureColor,
                showSavedPanel = showSavedMeasuresPanel,
            ),
            sheetVisible = showMeasureSheet,
            context = context,
            onEvent = { event ->
                when (event) {
                    is MapMeasurePanelEvent.SetActive -> measureActive = event.active
                    is MapMeasurePanelEvent.SetPoints -> measurePoints = event.points
                    is MapMeasurePanelEvent.SetMode -> measureMode = event.mode
                    is MapMeasurePanelEvent.SetDistUnit -> measureDistUnit = event.unit
                    is MapMeasurePanelEvent.SetAreaUnit -> measureAreaUnit = event.unit
                    is MapMeasurePanelEvent.SetColor -> measureColor = event.color
                    MapMeasurePanelEvent.ToggleSavedPanel -> showSavedMeasuresPanel = !showSavedMeasuresPanel
                    MapMeasurePanelEvent.SaveRequest -> { measureSaveName = ""; showMeasureSaveDialog = true }
                    is MapMeasurePanelEvent.LoadSavedMeasure -> {
                        measureMode = event.mode; measurePoints = event.points; showSavedMeasuresPanel = false
                    }
                }
            },
            onRequestHideSheet = { showMeasureSheet = false },
        )

        // ── Contrôles flottants pendant une mesure en cours, tiroir fermé :
        // rouvrir (avec résultat en direct) + enregistrer directement ──
        if (measureActive && !showMeasureSheet) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Space.lg),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                val liveResult = remember(measurePoints, measureMode, measureDistUnit, measureAreaUnit) {
                    when {
                        measureMode == MeasureMode.DISTANCE && measurePoints.size >= 2 -> {
                            val d = measurePolylineM(measurePoints)
                            if (d >= 1000.0) String.format(java.util.Locale.getDefault(), "%.2f km", d / 1000.0)
                            else String.format(java.util.Locale.getDefault(), "%.0f m", d)
                        }
                        measureMode == MeasureMode.AREA && measurePoints.size >= 3 -> {
                            String.format(java.util.Locale.getDefault(), "%.4f ha", measureAreaM2(measurePoints) / 10_000.0)
                        }
                        else -> null
                    }
                }
                Surface(
                    onClick = { showMeasureSheet = true },
                    color = measureColor,
                    shape = GsShape.pill,
                    shadowElevation = Elevation.overlay,
                    modifier = Modifier.height(Touch.fieldPrimary),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        Icon(Icons.Default.Straighten, contentDescription = stringResource(R.string.measure_reopen_panel), tint = Color.White)
                        Text(
                            liveResult ?: stringResource(R.string.measure_tap_hint),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
                if (liveResult != null) {
                    Surface(
                        onClick = { measureSaveName = ""; showMeasureSaveDialog = true },
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = GsShape.pill,
                        shadowElevation = Elevation.overlay,
                        modifier = Modifier.size(Touch.fieldPrimary),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.LocationOn, contentDescription = stringResource(R.string.measure_save_floating))
                        }
                    }
                }
            }
        }

        // ── Zoom +/- à gauche, centré verticalement ──
        Column(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Surface(
                onClick = { mapLibreMap?.let { it.animateCameraSmooth(CameraUpdateFactory.zoomIn()) } },
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                shape = androidx.compose.foundation.shape.CircleShape,
                shadowElevation = Elevation.overlay,
                modifier = Modifier.size(Touch.field),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_zoom_in))
                }
            }
            Surface(
                onClick = { mapLibreMap?.let { it.animateCameraSmooth(CameraUpdateFactory.zoomOut()) } },
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                shape = androidx.compose.foundation.shape.CircleShape,
                shadowElevation = Elevation.overlay,
                modifier = Modifier.size(Touch.field),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.cd_zoom_out))
                }
            }
        }

        // ── Barre d'outils verticale, ancrée à droite, repliable ──
        RechercheToolbar(
            expanded = toolbarExpanded,
            onToggleExpanded = { toolbarExpanded = !toolbarExpanded },
            measureActive = measureActive,
            layersActive = showLayerPicker,
            legendActive = showLegend,
            hasGeoTiges = displayedGeoTiges.isNotEmpty(),
            onToggleMeasure = {
                if (measureActive) {
                    measureActive = false
                    showMeasureSheet = false
                    measurePoints = emptyList()
                } else {
                    measureActive = true
                    showMeasureSheet = true
                }
            },
            onToggleLayers = { showLayerPicker = !showLayerPicker; if (showLayerPicker) showLegend = false },
            onToggleLegend = { showLegend = !showLegend; if (showLegend) showLayerPicker = false },
            onLocate = {
                val map = mapLibreMap ?: return@RechercheToolbar
                if (!hasLocationPermission) {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    return@RechercheToolbar
                }
                try {
                    val loc = map.locationComponent.lastKnownLocation
                    if (loc != null) {
                        map.animateCameraSmooth(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 18.0))
                    }
                } catch (e: Throwable) { Log.w(TAG, "animateCamera to GPS location failed", e) }
            },
            onDownloadOffline = { offlineRegions = offlineTileManager.listRegions(); showOfflineSheet = true },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = Space.sm),
        )
    }

    if (showOfflineSheet) {
        MapOfflineRegionsSheet(
            regions = offlineRegions,
            estimate = offlineEstimate,
            downloadAvailable = MAP_LAYERS.getOrElse(currentLayerIdx) { MAP_LAYERS[0] }
                .tileUrls.let { templates ->
                    templates.isNotEmpty() && templates.all { OfflineTilePolicy.validateTemplate(it) == null }
                },
            onRequestDownloadCurrentView = {
                val map = mapLibreMap ?: return@MapOfflineRegionsSheet
                val bounds = map.projection.visibleRegion.latLngBounds
                val layer = MAP_LAYERS.getOrElse(currentLayerIdx) { MAP_LAYERS[0] }
                val minZoom = map.cameraPosition.zoom.toInt().coerceAtLeast(8)
                val maxZoom = (map.cameraPosition.zoom.toInt() + 4).coerceAtMost(17)
                pendingDownloadBounds = bounds
                offlineEstimate = offlineTileManager.estimateDownload(
                    latSouth = bounds.southWest.latitude, latNorth = bounds.northEast.latitude,
                    lonWest = bounds.southWest.longitude, lonEast = bounds.northEast.longitude,
                    minZoom = minZoom, maxZoom = maxZoom, layerCount = layer.tileUrls.size.coerceAtLeast(1),
                )
            },
            onConfirmDownload = {
                val bounds = pendingDownloadBounds
                val layer = MAP_LAYERS.getOrElse(currentLayerIdx) { MAP_LAYERS[0] }
                val map = mapLibreMap
                if (bounds != null && map != null) {
                    val minZoom = map.cameraPosition.zoom.toInt().coerceAtLeast(8)
                    val maxZoom = (map.cameraPosition.zoom.toInt() + 4).coerceAtMost(17)
                    offlineTileManager.downloadRegion(
                        name = "$parcelleId · ${MAP_LAYERS.getOrElse(currentLayerIdx) { MAP_LAYERS[0] }.key}",
                        latSouth = bounds.southWest.latitude, latNorth = bounds.northEast.latitude,
                        lonWest = bounds.southWest.longitude, lonEast = bounds.northEast.longitude,
                        tileUrlTemplates = layer.tileUrls,
                        minZoom = minZoom, maxZoom = maxZoom,
                    )
                }
                offlineEstimate = null
                pendingDownloadBounds = null
                showOfflineSheet = false
            },
            onCancelEstimate = { offlineEstimate = null; pendingDownloadBounds = null },
            onDeleteRegion = { id ->
                offlineTileManager.deleteRegion(id)
                offlineRegions = offlineTileManager.listRegions()
                hasOfflineTilesState = offlineTileManager.hasOfflineTiles()
            },
            onDismiss = { showOfflineSheet = false },
        )
    }

    MapMeasureSaveDialog(
        visible = showMeasureSaveDialog,
        measureMode = measureMode,
        measurePoints = measurePoints,
        measureSaveName = measureSaveName,
        onMeasureSaveNameChange = { measureSaveName = it },
        onDismiss = { showMeasureSaveDialog = false },
        context = context,
    )
}

/**
 * Barre d'outils verticale du mode Recherche, ancrée à droite : un bouton
 * principal toujours visible qui déplie/replie les 4 autres — gagne de la
 * place sur un écran désormais plein écran (plus de bottom nav dessous).
 */
@Composable
private fun RechercheToolbar(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    measureActive: Boolean,
    layersActive: Boolean,
    legendActive: Boolean,
    hasGeoTiges: Boolean,
    onToggleMeasure: () -> Unit,
    onToggleLayers: () -> Unit,
    onToggleLegend: () -> Unit,
    onLocate: () -> Unit,
    onDownloadOffline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = expanded,
            enter = androidx.compose.animation.expandVertically(tween(Motion.NORMAL)) + fadeIn(tween(Motion.NORMAL)),
            exit = androidx.compose.animation.shrinkVertically(tween(Motion.FAST)) + fadeOut(tween(Motion.FAST)),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                shape = GsShape.pill,
                shadowElevation = Elevation.overlay,
            ) {
                Column(
                    modifier = Modifier.padding(Space.xs),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RechercheToolbarButton(Icons.Default.Layers, stringResource(R.string.map_style), layersActive, onToggleLayers)
                    RechercheToolbarButton(Icons.Default.Straighten, stringResource(R.string.measure_tool_title), measureActive, onToggleMeasure)
                    RechercheToolbarButton(Icons.AutoMirrored.Filled.FormatListBulleted, stringResource(R.string.map_legend), legendActive, onToggleLegend, enabled = hasGeoTiges)
                    RechercheToolbarButton(Icons.Default.GpsFixed, stringResource(R.string.map_my_location), false, onLocate)
                    RechercheToolbarButton(Icons.Default.CloudDownload, stringResource(R.string.offline_download), false, onDownloadOffline)
                }
            }
        }
        Surface(
            onClick = onToggleExpanded,
            modifier = Modifier.size(Touch.fieldPrimary),
            shape = GsShape.pill,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
            shadowElevation = Elevation.overlay,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (expanded) Icons.Default.Close else Icons.Default.Menu,
                    contentDescription = stringResource(if (expanded) R.string.cd_close else R.string.map_toolbar_expand),
                )
            }
        }
    }
}

@Composable
private fun RechercheToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(Touch.fieldPrimary),
        shape = GsShape.pill,
        color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = when {
            active -> MaterialTheme.colorScheme.onPrimary
            enabled -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription)
        }
    }
}
