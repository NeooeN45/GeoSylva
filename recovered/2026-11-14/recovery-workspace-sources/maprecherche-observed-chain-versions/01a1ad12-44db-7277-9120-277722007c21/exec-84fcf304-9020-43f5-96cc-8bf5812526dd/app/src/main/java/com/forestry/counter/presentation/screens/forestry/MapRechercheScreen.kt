package com.forestry.counter.presentation.screens.forestry

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forestry.counter.R
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.domain.location.OfflineTileManager
import com.forestry.counter.domain.location.OfflineTilePolicy
import com.forestry.counter.domain.location.MapSourcePolicy
import com.forestry.counter.domain.location.TreeNavigator
import com.forestry.counter.domain.location.WktUtils
import com.forestry.counter.domain.model.Essence
import com.forestry.counter.domain.usecase.maps.MapDataScope
import com.forestry.counter.domain.usecase.maps.MapTreeContent
import com.forestry.counter.domain.usecase.maps.ObserveMapTrees
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.LocalDensity
import com.forestry.counter.BuildConfig
import com.forestry.counter.presentation.components.maps.MapboxRenderStatusOverlay
import com.forestry.counter.presentation.components.maps.rememberMapPhoneLocation
import com.quintessences.geosylva.cartography.mapbox.*
import com.mapbox.geojson.Point
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Mode Recherche de l'onglet Carte — nouvelle base (esprit QField).
 *
 * Utilise le moteur Mapbox commun aux cartes et des données déjà autorisées.
 * Conserve les outils et les routes existants pendant l’intégration : une
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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val mapScope = remember(parcelleId) { MapDataScope.fromLegacyKey(parcelleId) }
    var dataRetry by remember(mapScope) { mutableStateOf(0) }
    val observeMapTrees = remember(tigeRepository, parcelleRepository) {
        ObserveMapTrees(tigeRepository, parcelleRepository)
    }
    val mapContentFlow = remember(observeMapTrees, mapScope, dataRetry) { observeMapTrees(mapScope) }
    val collectedContent by key(mapContentFlow) {
        mapContentFlow.collectAsStateWithLifecycle<MapTreeContent>(initialValue = MapTreeContent.Loading(mapScope))
    }
    // collectAsState can retain the previous flow's last value until the next emission.
    // A new context never displays that previous context's trees, even for one frame.
    val mapContent = collectedContent.takeIf { it.scope == mapScope } ?: MapTreeContent.Loading(mapScope)
    val tiges = (mapContent as? MapTreeContent.Ready)?.trees.orEmpty()
    val essencesFlow = remember(essenceRepository) { essenceRepository?.getAllEssences() ?: flowOf(emptyList<Essence>()) }
    val essences by essencesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val essenceMap = remember(essences) { essences.associateBy { it.code.uppercase() } }

    val mapLastLayerKey by preferencesManager.mapLastLayerKey.collectAsStateWithLifecycle(
        initialValue = MapSourcePolicy.DEFAULT_LAYER_KEY,
    )
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

    @Suppress("NAME_SHADOWING")
    val offlineTileManager = offlineTileManager ?: remember(context) { OfflineTileManager(context) }
    val offlineProgress by offlineTileManager.downloadProgress.collectAsStateWithLifecycle()
    var hasOfflineTilesState by remember { mutableStateOf(offlineTileManager.hasOfflineTiles()) }
    var offlineRegions by remember { mutableStateOf(offlineTileManager.listRegions()) }
    var showOfflineSheet by remember { mutableStateOf(false) }
    var offlineEstimate by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var pendingDownloadBounds by remember { mutableStateOf<MapBounds?>(null) }
    LaunchedEffect(offlineProgress) {
        if (offlineProgress?.isComplete == true && offlineProgress?.error == null) {
            hasOfflineTilesState = offlineTileManager.hasOfflineTiles()
            offlineRegions = offlineTileManager.listRegions()
        }
    }

    val geoTiges = remember(tiges) {
        tiges.mapNotNull { t ->
            val (lon, lat, _) = WktUtils.parsePointZ(t.gpsWkt)
            if (lon != null && lat != null && lon.isFinite() && lat.isFinite() && lon in -180.0..180.0 && lat in -90.0..90.0) Triple(t, lon, lat) else null
        }
    }
    val displayedGeoTiges = remember(geoTiges, mapOnlyReliableGps, mapReliableGpsThresholdM) {
        if (!mapOnlyReliableGps) geoTiges
        else geoTiges.filter { (t, _, _) -> t.precisionM?.let { it.isFinite() && it > 0 && it <= mapReliableGpsThresholdM.toDouble() } == true }
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

    val density = LocalDensity.current.density
    var camera by rememberSaveable(mapScope.toString(), stateSaver = MapCameraPositionSaver) { mutableStateOf(MapCameraPosition(2.5, 46.6, 5.0)) }
    var cameraCommand by remember(mapScope) { mutableStateOf<MapCameraCommand?>(null) }
    var frameCommand by remember(mapScope) { mutableStateOf<MapCameraFitCommand?>(null) }
    var viewport by remember(mapScope) { mutableStateOf<MapViewport?>(null) }
    var overlayFailed by remember(mapScope) { mutableStateOf(false) }
    var renderStatus by remember(mapScope) { mutableStateOf<MapRenderStatus>(MapRenderStatus.Loading("satellite")) }
    var retryGeneration by remember { mutableStateOf(0) }
    val phonePosition = rememberMapPhoneLocation(hasLocationPermission)
    val gpsAccuracyM = phonePosition?.takeIf { it.hasAccuracy() && it.accuracy.isFinite() && it.accuracy > 0 }?.accuracy
    val initialLayerIdx = remember(mapLastLayerKey) { MAP_LAYERS.indexOfFirst { it.key == mapLastLayerKey }.takeIf { it >= 0 } ?: 0 }
    var layerLoadState by remember(mapScope) { mutableStateOf(MapLayerLoadState(initialLayerIdx)) }
    val currentLayerIdx = layerLoadState.displayedIndex
    var showLayerPicker by remember { mutableStateOf(false) }
    var showLegend by remember { mutableStateOf(false) }
    val is3DActive = camera.pitch > 1.0
    var showGpsPrecisionDialog by remember { mutableStateOf(false) }
    var toolbarExpanded by remember { mutableStateOf(false) }
    var showMeasureSheet by remember { mutableStateOf(false) }
    var tappedTree by remember(parcelleId) { mutableStateOf<TappedTreeInfo?>(null) }
    var dismissedGpsBanner by remember { mutableStateOf(false) }

    // ── Outil de mesure ──
    val measureActiveState = remember { mutableStateOf(false) }
    var measureActive by measureActiveState
    val measurePointsState = remember { mutableStateOf<List<MeasurePoint>>(emptyList()) }
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
    DisposableEffect(treeNavigator, parcelleId) { onDispose { treeNavigator.stopNavigation() } }
    // The repository/filters own visibility; obsolete selections cannot remain active.
    val visibleTreeIds = remember(filteredGeoTiges) {
        filteredGeoTiges.map { it.first.id to it.first.parcelleId }.toSet()
    }
    val visibleTappedTree = tappedTree?.takeIf { (it.tigeId to it.parcelleId) in visibleTreeIds }
    val handleCurrentTreeTap by rememberUpdatedState<(TappedTreeInfo) -> Unit>({ info ->
        if ((info.tigeId to info.parcelleId) in visibleTreeIds) tappedTree = info
    })
    LaunchedEffect(visibleTreeIds, parcelleId) {
        if (tappedTree != null && visibleTappedTree == null) tappedTree = null
        treeNavigator.state.value.target?.let { target ->
            if (filteredGeoTiges.none { it.first.id == target.tigeId }) treeNavigator.stopNavigation()
        }
    }

    fun requestCamera(position: MapCameraPosition) {
        cameraCommand = MapCameraCommand(UUID.randomUUID().toString(), position)
    }
    fun toggle3D() { requestCamera(camera.copy(pitch = if (is3DActive) 0.0 else 55.0)) }
    fun layerStyle(index: Int): MapboxStyleSpec {
        val layer = MAP_LAYERS.getOrElse(index) { MAP_LAYERS.first() }
        val inline = if (layer.key == MapSourcePolicy.LOCAL_LAYER_KEY)
            offlineTileManager.buildOfflineStyle() else layer.styleJson
        return MapboxStyleSpec(layer.key, inline, demSourceUrl =
            if (layer.key == MapSourcePolicy.LOCAL_LAYER_KEY) null else "mapbox://mapbox.mapbox-terrain-dem-v1")
    }
    var selectedStyle by remember(mapScope) { mutableStateOf(layerStyle(initialLayerIdx)) }
    fun applyLayerDecision(decision: MapLayerLoadDecision) {
        layerLoadState = decision.state
        if (decision.requestToStart != null) selectedStyle = layerStyle(decision.requestToStart.layerIndex)
        else if (!decision.state.isLoading && decision.state.failedIndex != null) {
            selectedStyle = layerStyle(decision.state.activeIndex)
        }
        if (!decision.state.isLoading && decision.state.failedIndex == null) showLayerPicker = false
    }
    var layerChoiceMade by remember(mapScope) { mutableStateOf(false) }
    LaunchedEffect(mapScope) {
        val stored = MapSourcePolicy.resolveLayerKey(preferencesManager.mapLastLayerKey.first())
        if (!layerChoiceMade) {
            val index = MAP_LAYERS.indexOfFirst { it.key == stored }.takeIf { it >= 0 } ?: initialLayerIdx
            layerLoadState = MapLayerLoadState(index)
            selectedStyle = layerStyle(index)
        }
    }
    fun switchLayer(index: Int) {
        layerChoiceMade = true
        applyLayerDecision(layerLoadState.request(index))
    }
    fun retryLayer() {
        retryGeneration++
        applyLayerDecision(layerLoadState.retry())
    }
    val sceneRevision = remember { AtomicLong(0) }
    val projection = remember(mapScope, filteredGeoTiges, essenceMap, essenceColors, camera.zoom.toInt(),
        measurePoints, measureMode, measureColor, visibleTappedTree?.tigeId, visibleTappedTree?.parcelleId) {
        buildTerrainMapProjection(mapScope.toString(), sceneRevision.incrementAndGet(), filteredGeoTiges,
            essenceMap, essenceColors, camera.zoom, measurePoints, measureMode, measureColor.toArgb(),
            visibleTappedTree?.let { MapTreeIdentity(it.tigeId, it.parcelleId) })
    }
    val scene = remember(projection, phonePosition, measureActive, showMeasureSaveDialog) {
        val locationFeature = phonePosition?.let {
            MapOverlayFeature(MapFeatureKey("device-position", "current"), Point.fromLngLat(it.longitude, it.latitude),
                MapOverlayStyle(colorHex = "#176BDF", pointRadius = 7.0), selectable = false)
        }
        MapOverlayScene(projection.scene.scopeId, sceneRevision.incrementAndGet(),
            projection.scene.features + listOfNotNull(locationFeature), projection.scene.selectedKey)
    }
    val currentProjection by rememberUpdatedState(projection)
    var framedContext by rememberSaveable(mapScope.toString()) { mutableStateOf(false) }
    var initialFrameRequested by remember(mapScope) { mutableStateOf(false) }
    LaunchedEffect(mapContent, displayedGeoTiges, phonePosition) {
        if (!framedContext && !initialFrameRequested && mapContent is MapTreeContent.Ready && !measureActive && displayedGeoTiges.isNotEmpty()) {
            frameCommand = MapCameraFitCommand(UUID.randomUUID().toString(),
                displayedGeoTiges.map { MapCoordinate(it.second, it.third) },
                paddingTopPx = 100.0 * density, paddingBottomPx = 130.0 * density,
                paddingLeftPx = 55.0 * density, paddingRightPx = 55.0 * density)
            initialFrameRequested = true
        } else if (!framedContext && !initialFrameRequested && mapContent is MapTreeContent.Ready && !measureActive && phonePosition != null) {
            requestCamera(camera.copy(longitude = phonePosition.longitude, latitude = phonePosition.latitude, zoom = 13.0))
            initialFrameRequested = true
        }
    }
    LaunchedEffect(layerLoadState.loadingRequest?.id) {
        val request = layerLoadState.loadingRequest ?: return@LaunchedEffect
        delay(25_000)
        if (layerLoadState.loadingRequest?.id == request.id) applyLayerDecision(layerLoadState.fail(request.id))
    }

    Box(modifier = modifier.fillMaxSize()) {
        key(mapScope, retryGeneration) {
            SharedMapboxCanvas(
                accessToken = BuildConfig.MAPBOX_PUBLIC_ACCESS_TOKEN,
                style = selectedStyle, initialCamera = camera, cameraCommand = cameraCommand,
                frameCommand = frameCommand, overlays = scene,
                relief = MapRelief(enabled = selectedStyle.demSourceUrl != null),
                contentPadding = PaddingValues(start = 12.dp, top = 68.dp, end = 12.dp,
                    bottom = if (showMeasureSheet) 330.dp else 80.dp),
                modifier = Modifier.fillMaxSize().pointerInput(mapScope) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            if (event.changes.any { it.pressed && it.position != it.previousPosition }) {
                                framedContext = true
                                frameCommand = null
                                cameraCommand = null
                            }
                        }
                    }
                },
                onOverlayStatusChanged = { overlayFailed = it is MapOverlayStatus.Failed },
                onCameraChanged = { camera = it },
                onViewportChanged = { viewport = it },
                onCommandConsumed = {
                    if (cameraCommand?.id == it) { cameraCommand = null; framedContext = true }
                },
                onFrameCommandConsumed = {
                    if (frameCommand?.id == it) { frameCommand = null; framedContext = true }
                },
                onCoordinateTap = { coordinate ->
                    if (measureActive) {
                        if (!showMeasureSaveDialog) measurePoints = measurePoints + MeasurePoint(coordinate.latitude, coordinate.longitude)
                        true
                    } else false
                },
                onMapTap = { tap ->
                    if (!measureActive && !showMeasureSaveDialog) tap.featureKey?.let { key ->
                        currentProjection.treesByKey[key]?.let { handleCurrentTreeTap(it) }
                        currentProjection.clustersByKey[key]?.let { members ->
                            frameCommand = MapCameraFitCommand(UUID.randomUUID().toString(), members,
                                paddingTopPx = 100.0 * density, paddingBottomPx = 130.0 * density,
                                paddingLeftPx = 55.0 * density, paddingRightPx = 55.0 * density)
                        }
                    }
                },
                onStatusChanged = { status ->
                    renderStatus = status
                    layerLoadState.loadingRequest?.let { request ->
                        if (status is MapRenderStatus.Ready && status.styleId == selectedStyle.id) {
                            val loadedStyleId = status.styleId
                            applyLayerDecision(layerLoadState.succeed(request.id))
                            scope.launch { preferencesManager.setMapLastLayerKey(loadedStyleId) }
                        } else if (status is MapRenderStatus.Failed) applyLayerDecision(layerLoadState.fail(request.id))
                    }
                },
            )
        }
        MapboxRenderStatusOverlay(if (overlayFailed) MapRenderStatus.Failed(MapFailureReason.SOURCE) else renderStatus, ::retryLayer,
            Modifier.align(Alignment.Center).padding(Space.md))

        // ── Bouton retour minimal ──
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
            tappedTree = visibleTappedTree,
            navActive = navState.isActive,
            hasLocationPermission = hasLocationPermission,
            onRequestPermission = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
            onStartNavigation = start@ { info ->
                // Re-resolve against currently visible repository data, not the rendered snapshot.
                val current = filteredGeoTiges.firstOrNull {
                    it.first.id == info.tigeId && it.first.parcelleId == info.parcelleId
                } ?: run { tappedTree = null; return@start }
                val (tree, lon, lat) = current
                if (!lon.isFinite() || lon !in -180.0..180.0 ||
                    !lat.isFinite() || lat !in -90.0..90.0) {
                    tappedTree = null
                    return@start
                }
                treeNavigator.startNavigation(
                    TreeNavigator.Target(
                        tigeId = tree.id,
                        essenceName = essenceMap[tree.essenceCode.uppercase()]?.name ?: tree.essenceCode,
                        essenceCode = tree.essenceCode, diamCm = tree.diamCm,
                        hauteurM = tree.hauteurM, lat = lat, lon = lon,
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

        if (mapContent is MapTreeContent.Loading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        if (mapContent is MapTreeContent.Unavailable) {
            Surface(Modifier.align(Alignment.Center).padding(Space.md)) {
                Column(Modifier.padding(Space.md), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.map_tree_data_unavailable))
                    if (mapContent.reason == MapTreeContent.Reason.DATA_READ) {
                        androidx.compose.material3.TextButton(onClick = { dataRetry++ }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
        }
        if (mapContent is MapTreeContent.Ready) MapNoGpsMessage(
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
                onClick = { requestCamera(camera.copy(zoom = (camera.zoom + 1).coerceAtMost(22.0))) },
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
                onClick = { requestCamera(camera.copy(zoom = (camera.zoom - 1).coerceAtLeast(0.0))) },
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
                if (!hasLocationPermission) locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                else phonePosition?.let { requestCamera(camera.copy(longitude = it.longitude, latitude = it.latitude, zoom = 18.0)) }
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
                val currentViewport = viewport ?: return@MapOfflineRegionsSheet
                val bounds = currentViewport.bounds
                val layer = MAP_LAYERS.getOrElse(currentLayerIdx) { MAP_LAYERS[0] }
                val minZoom = camera.zoom.toInt().coerceAtLeast(8)
                val maxZoom = (camera.zoom.toInt() + 4).coerceAtMost(17)
                pendingDownloadBounds = bounds
                offlineEstimate = offlineTileManager.estimateDownload(
                    latSouth = bounds.south, latNorth = bounds.north,
                    lonWest = bounds.west, lonEast = bounds.east,
                    minZoom = minZoom, maxZoom = maxZoom, layerCount = layer.tileUrls.size.coerceAtLeast(1),
                )
            },
            onConfirmDownload = {
                val bounds = pendingDownloadBounds
                val layer = MAP_LAYERS.getOrElse(currentLayerIdx) { MAP_LAYERS[0] }
                if (bounds != null) {
                    val minZoom = camera.zoom.toInt().coerceAtLeast(8)
                    val maxZoom = (camera.zoom.toInt() + 4).coerceAtMost(17)
                    offlineTileManager.downloadRegion(
                        name = "$parcelleId · ${MAP_LAYERS.getOrElse(currentLayerIdx) { MAP_LAYERS[0] }.key}",
                        latSouth = bounds.south, latNorth = bounds.north,
                        lonWest = bounds.west, lonEast = bounds.east,
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
