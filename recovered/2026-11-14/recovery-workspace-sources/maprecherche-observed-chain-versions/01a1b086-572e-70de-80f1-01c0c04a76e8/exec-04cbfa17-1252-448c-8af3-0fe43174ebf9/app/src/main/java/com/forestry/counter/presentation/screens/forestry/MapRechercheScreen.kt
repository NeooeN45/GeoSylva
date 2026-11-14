package com.forestry.counter.presentation.screens.forestry

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.forestry.counter.domain.repository.EssenceRepository
import com.forestry.counter.domain.repository.ParcelleRepository
import com.forestry.counter.domain.repository.TigeRepository
import com.forestry.counter.presentation.theme.Elevation
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.Space
import com.forestry.counter.presentation.theme.Touch
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import com.forestry.counter.BuildConfig
import com.forestry.counter.presentation.components.maps.MapboxRenderStatusOverlay
import com.forestry.counter.presentation.components.maps.rememberMapPhoneLocation
import com.forestry.counter.presentation.components.maps.rememberMapNetworkAvailable
import com.forestry.counter.presentation.components.maps.MapboxLocalVectorStyle
import com.forestry.counter.presentation.components.maps.MapLocalModeBadge
import com.forestry.counter.presentation.components.maps.MapCatalogThumbnail
import com.quintessences.geosylva.cartography.ui.*
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
    var hiddenEssences by remember(mapScope) { mutableStateOf(emptySet<String>()) }
    var treesVisible by rememberSaveable(mapScope.toString()) { mutableStateOf(true) }
    var measurementVisible by rememberSaveable(mapScope.toString()) { mutableStateOf(true) }
    val filteredGeoTiges = remember(displayedGeoTiges, hiddenEssences, treesVisible) {
        if (!treesVisible) emptyList()
        else if (hiddenEssences.isEmpty()) displayedGeoTiges
        else displayedGeoTiges.filter { (t, _, _) -> t.essenceCode.uppercase() !in hiddenEssences }
    }

    val networkAvailable = rememberMapNetworkAvailable()
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
    var workspacePanel by remember(mapScope) { mutableStateOf<MapPanel?>(null) }
    var showTools by remember(mapScope) { mutableStateOf(false) }
    var showPlaceSearch by remember(mapScope) { mutableStateOf(false) }
    var selectedPlaceLabel by remember(mapScope) { mutableStateOf<String?>(null) }
    var workspaceHelp by remember { mutableStateOf<MapHelp?>(null) }
    var workspaceMessage by remember { mutableStateOf<Int?>(null) }
    var requestedRelief by remember(mapScope) { mutableStateOf(MapRelief(enabled = true)) }
    var appliedRelief by remember(mapScope) { mutableStateOf(MapRelief()) }
    var showLegend by remember { mutableStateOf(false) }
    var showGpsPrecisionDialog by remember { mutableStateOf(false) }
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
            workspaceMessage = R.string.workspace_basemap_failed
        }
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
        if (!networkAvailable) {
            // Remember the requested background without pretending its remote resources loaded.
            layerLoadState = MapLayerLoadState(index)
            selectedStyle = layerStyle(index)
        } else applyLayerDecision(layerLoadState.request(index))
    }
    val localVectorMode = !networkAvailable && selectedStyle.id != MapSourcePolicy.LOCAL_LAYER_KEY
    val renderedStyle = if (localVectorMode) MapboxLocalVectorStyle else selectedStyle
    LaunchedEffect(networkAvailable) {
        if (!networkAvailable && layerLoadState.isLoading) {
            val index = layerLoadState.displayedIndex
            layerLoadState = MapLayerLoadState(index)
            selectedStyle = layerStyle(index)
        }
    }
    fun retryLayer() {
        retryGeneration++
        applyLayerDecision(layerLoadState.retry())
    }
    val sceneRevision = remember { AtomicLong(0) }
    val projection = remember(mapScope, filteredGeoTiges, essenceMap, essenceColors, camera.zoom.toInt(),
        measurePoints, measurementVisible, measureMode, measureColor, visibleTappedTree?.tigeId, visibleTappedTree?.parcelleId) {
        buildTerrainMapProjection(mapScope.toString(), sceneRevision.incrementAndGet(), filteredGeoTiges,
            essenceMap, essenceColors, camera.zoom, if (measurementVisible) measurePoints else emptyList(), measureMode, measureColor.toArgb(),
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

    val isFreeMap = mapScope == MapDataScope.Free
    val contextTitle = when (mapScope) {
        MapDataScope.Free -> selectedPlaceLabel ?: stringResource(R.string.workspace_explore)
        MapDataScope.AllForests -> stringResource(R.string.recherche_scope_all)
        is MapDataScope.Parcel -> stringResource(R.string.workspace_parcel)
        is MapDataScope.Forest -> stringResource(R.string.workspace_forest)
        is MapDataScope.Plot -> stringResource(R.string.workspace_plot)
        MapDataScope.Invalid -> stringResource(R.string.map_tree_data_unavailable)
    }
    val contextSubtitle = if (isFreeMap || mapContent !is MapTreeContent.Ready) null else stringResource(R.string.workspace_tree_count, filteredGeoTiges.size)
    val configured = BuildConfig.MAPBOX_PUBLIC_ACCESS_TOKEN.startsWith("pk.")
    val baseChoices = MAP_LAYERS.map { layer ->
        MapBasemapChoice(layer.key, stringResource(layer.labelResId),
            when {
                layer.key.startsWith("MAPBOX_") -> "Mapbox"
                layer.key == "ORTHO_IGN" -> "IGN · BD ORTHO®"
                else -> stringResource(R.string.workspace_on_device)
            }, availability = when {
                !configured -> MapChoiceAvailability.CONFIGURATION_REQUIRED
                layer.key == MapSourcePolicy.LOCAL_LAYER_KEY && !hasOfflineTilesState -> MapChoiceAvailability.DATA_REQUIRED
                else -> MapChoiceAvailability.AVAILABLE
            })
    }
    val treeLayerTitle = stringResource(R.string.workspace_tree_layer)
    val measureLayerTitle = stringResource(R.string.workspace_measure_layer)
    val layerDetailsLabel = stringResource(R.string.workspace_layer_details)
    val workspaceLayers = buildList {
        if (!isFreeMap) add(MapLayerChoice("LOCAL_TREES", treeLayerTitle,
            onMap = true, visible = treesVisible,
            availability = if (geoTiges.isEmpty()) MapChoiceAvailability.DATA_REQUIRED else MapChoiceAvailability.AVAILABLE,
            detailActionLabel = layerDetailsLabel))
        add(MapLayerChoice("LOCAL_MEASUREMENTS", measureLayerTitle,
            onMap = true, visible = measurementVisible && measurePoints.isNotEmpty(),
            availability = if (measurePoints.isEmpty()) MapChoiceAvailability.DATA_REQUIRED else MapChoiceAvailability.AVAILABLE,
            detailActionLabel = layerDetailsLabel))
    }
    val reliefControlsEnabled = workspaceControlsEnabled(renderStatus, renderedStyle.id,
        localVectorMode, renderedStyle.demSourceUrl != null, overlayFailed)
    var compassTop by remember { mutableStateOf(156.dp) }
    val workspaceState = MapWorkspaceState(
        contextTitle = contextTitle, contextSubtitle = contextSubtitle,
        presentation = if (isFreeMap) MapPresentation.FREE else MapPresentation.TERRAIN,
        basemaps = baseChoices, selectedBasemapId = acknowledgedWorkspaceBasemapId(renderStatus, renderedStyle.id, localVectorMode),
        layers = workspaceLayers, panel = workspacePanel,
        pitchDegrees = camera.pitch, verticalExaggeration = appliedRelief.exaggeration,
        controlsEnabled = reliefControlsEnabled,
        controlsUnavailableReason = stringResource(if (localVectorMode || renderedStyle.demSourceUrl == null)
            R.string.workspace_relief_unavailable else R.string.workspace_wait_for_map),
        taskBarVisible = !measureActive && !showMeasureSheet,
        chromeVisible = !measureActive && !showMeasureSheet,
        selectedTask = if (showLegend) MapTask.SURVEYS else null,
        locationEnabled = !hasLocationPermission || phonePosition != null,
        contextActionLabel = if (isFreeMap) stringResource(R.string.workspace_explore) else null,
    )
    fun openMeasurements(saved: Boolean = false) {
        workspacePanel = null
        showLegend = false
        measurementVisible = true
        measureActive = true
        showSavedMeasuresPanel = saved
        showMeasureSheet = true
    }
    val workspaceActions = object : MapWorkspaceActions {
        override fun onBack() = onNavigateBack()
        override fun onChooseContext() { if (isFreeMap) showPlaceSearch = true }
        override fun onPanelChanged(panel: MapPanel?) {
            workspacePanel = panel
            if (panel != null) { showLegend = false; tappedTree = null }
        }
        override fun onLocate() {
            if (!hasLocationPermission) locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            else phonePosition?.let { requestCamera(camera.copy(longitude = it.longitude, latitude = it.latitude, zoom = 18.0)) }
        }
        override fun onTask(task: MapTask) {
            workspacePanel = null
            when (task) {
                MapTask.SURVEYS -> {
                    treesVisible = true
                    if (essenceCounts.isNotEmpty()) showLegend = !showLegend
                    else workspaceMessage = R.string.workspace_no_releves
                }
                MapTask.MEASURE -> openMeasurements()
                MapTask.TOOLS -> { showLegend = false; showTools = true }
            }
        }
        override fun onSelectBasemap(id: String) {
            val choice = baseChoices.firstOrNull { it.id == id } ?: return
            if (choice.availability == MapChoiceAvailability.AVAILABLE) {
                val index = MAP_LAYERS.indexOfFirst { it.key == id }
                if (index >= 0) switchLayer(index)
            } else workspaceMessage = if (choice.availability == MapChoiceAvailability.CONFIGURATION_REQUIRED)
                R.string.workspace_configuration_required else R.string.workspace_local_data_required
        }
        override fun onToggleLayer(id: String, visible: Boolean) {
            when (id) {
                "LOCAL_TREES" -> treesVisible = visible
                "LOCAL_MEASUREMENTS" -> {
                    measurementVisible = visible
                    if (!visible) { measureActive = false; showMeasureSheet = false }
                }
            }
        }
        override fun onAddLayer(id: String) { onLayerDetails(id) }
        override fun onLayerDetails(id: String) {
            workspaceMessage = when (id) {
                "LOCAL_TREES" -> R.string.workspace_trees_source
                "LOCAL_MEASUREMENTS" -> R.string.workspace_measure_source
                else -> return
            }
        }
        override fun onPitchChange(degrees: Double) {
            if (reliefControlsEnabled) requestCamera(camera.copy(pitch = degrees.coerceIn(0.0, 60.0)))
        }
        override fun onExaggerationChange(exaggeration: Double) {
            if (reliefControlsEnabled) requestedRelief = requestedRelief.copy(exaggeration = exaggeration.coerceIn(1.0, 3.0))
        }
        override fun onFlatView(flat: Boolean) {
            if (reliefControlsEnabled) requestCamera(camera.copy(pitch = if (flat) 0.0 else 45.0))
        }
        override fun onExplain(help: MapHelp) { workspaceHelp = help }
    }
    BackHandler(enabled = measureActive && !showMeasureSheet && workspacePanel == null) { measureActive = false }
    BackHandler(enabled = showLegend) { showLegend = false }
    MapWorkspace(state = workspaceState, actions = workspaceActions, modifier = modifier,
        onCompassTopChanged = { compassTop = it },
        thumbnail = { preview, thumbnailModifier -> MapCatalogThumbnail(preview, thumbnailModifier) }) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val ornamentsBottom = when {
                workspacePanel != null -> mapWorkspacePanelHeight(maxHeight, LocalDensity.current.fontScale)
                showMeasureSheet -> 330.dp
                measureActive -> 84.dp
                else -> 112.dp
            } + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp
            key(mapScope, retryGeneration) {
                SharedMapboxCanvas(
                    accessToken = BuildConfig.MAPBOX_PUBLIC_ACCESS_TOKEN,
                    style = renderedStyle, initialCamera = camera, cameraCommand = cameraCommand,
                    frameCommand = frameCommand, overlays = scene,
                    scaleBarVisible = workspacePanel == null,
                    relief = requestedRelief.copy(enabled = renderedStyle.demSourceUrl != null),
                    contentPadding = PaddingValues(start = 12.dp, top = compassTop, end = 12.dp, bottom = ornamentsBottom),
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
                    onReliefChanged = { appliedRelief = it },
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
                            if (status is MapRenderStatus.Ready && status.styleId == renderedStyle.id) {
                                val loadedStyleId = status.styleId
                                applyLayerDecision(layerLoadState.succeed(request.id))
                                if (!localVectorMode) scope.launch { preferencesManager.setMapLastLayerKey(loadedStyleId) }
                            } else if (status is MapRenderStatus.Failed) applyLayerDecision(layerLoadState.fail(request.id))
                        }
                    },
                )
            }
            MapboxRenderStatusOverlay(if (overlayFailed) MapRenderStatus.Failed(MapFailureReason.SOURCE) else renderStatus, ::retryLayer,
                Modifier.align(Alignment.Center).padding(Space.md))

            if (localVectorMode) MapLocalModeBadge(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 132.dp, start = 16.dp, end = 16.dp))

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
                        var thresholdDraft by remember(mapReliableGpsThresholdM) { mutableStateOf(mapReliableGpsThresholdM) }
                        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if (gpsAccuracyM != null) stringResource(R.string.gps_precision_dialog_body, qualityLabel, gpsAccuracyM)
                                else stringResource(R.string.gps_precision_dialog_unknown))
                            Text(stringResource(R.string.workspace_recorded_precision), fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.settings_map_only_reliable_gps), Modifier.weight(1f))
                                Switch(checked = mapOnlyReliableGps,
                                    onCheckedChange = { enabled -> scope.launch { preferencesManager.setMapOnlyReliableGps(enabled) } })
                            }
                            Text(stringResource(R.string.settings_map_reliable_gps_threshold_format, thresholdDraft))
                            Slider(value = thresholdDraft.coerceIn(3f, 100f), onValueChange = { thresholdDraft = it },
                                onValueChangeFinished = { scope.launch { preferencesManager.setMapReliableGpsThresholdM(thresholdDraft) } },
                                valueRange = 3f..100f)
                            Text(stringResource(R.string.workspace_precision_limit), style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { showGpsPrecisionDialog = false }) {
                            Text(stringResource(R.string.ok))
                        }
                    },
                )
            }

            MapGpsWarningBanner(
                geoTiges = geoTiges,
                dismissed = dismissedGpsBanner || workspacePanel != null,
                onDismiss = { dismissedGpsBanner = true },
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 136.dp),
            )

            MapTigeInfoPanel(
                tappedTree = visibleTappedTree.takeIf { workspacePanel == null },
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
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 136.dp, start = Space.md, end = Space.md),
            )

            MapNavigationOverlay(
                navState = navState,
                onStopNavigation = { treeNavigator.stopNavigation() },
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 136.dp, start = Space.md, end = Space.md),
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
            if (mapContent is MapTreeContent.Ready && !dismissedGpsBanner && workspacePanel == null) MapNoGpsMessage(
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
                        is MapMeasurePanelEvent.SetActive -> { measureActive = event.active; if (event.active) measurementVisible = true }
                        is MapMeasurePanelEvent.SetPoints -> measurePoints = event.points
                        is MapMeasurePanelEvent.SetMode -> measureMode = event.mode
                        is MapMeasurePanelEvent.SetDistUnit -> measureDistUnit = event.unit
                        is MapMeasurePanelEvent.SetAreaUnit -> measureAreaUnit = event.unit
                        is MapMeasurePanelEvent.SetColor -> measureColor = event.color
                        MapMeasurePanelEvent.ToggleSavedPanel -> showSavedMeasuresPanel = !showSavedMeasuresPanel
                        MapMeasurePanelEvent.SaveRequest -> { measureSaveName = ""; showMeasureSaveDialog = true }
                        is MapMeasurePanelEvent.LoadSavedMeasure -> {
                            measureMode = event.mode; measurePoints = event.points; measurementVisible = true; showSavedMeasuresPanel = false
                        }
                    }
                },
                onRequestHideSheet = { showMeasureSheet = false },
            )

            // ── Contrôles flottants pendant une mesure en cours, tiroir fermé :
            // rouvrir (avec résultat en direct) + enregistrer directement ──
            if (measureActive && !showMeasureSheet) {
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = Space.md, vertical = Space.lg),
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
                        modifier = Modifier.weight(1f).height(Touch.fieldPrimary),
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
                    Surface(
                        onClick = { measureActive = false },
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = GsShape.pill,
                        shadowElevation = Elevation.overlay,
                        modifier = Modifier.size(Touch.fieldPrimary),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.workspace_close_measure))
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

        }
    }

    if (showLegend) AlertDialog(
        onDismissRequest = { showLegend = false },
        title = { Text(stringResource(R.string.workspace_tree_layer)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                MapLegendPanel(visible = true, essenceColors = essenceColors, essenceCounts = essenceCounts,
                    hiddenEssences = hiddenEssences, essenceMap = essenceMap,
                    onToggleEssence = { code, hide -> hiddenEssences = if (hide) hiddenEssences + code else hiddenEssences - code })
            }
        },
        confirmButton = { TextButton(onClick = { showLegend = false }) { Text(stringResource(R.string.cd_close)) } },
    )
    if (showTools) MapWorkspaceTools(
        onDismiss = { showTools = false },
        onGpsPrecision = { showGpsPrecisionDialog = true },
        onOffline = { offlineRegions = offlineTileManager.listRegions(); showOfflineSheet = true },
        onEssences = { treesVisible = true; showLegend = true },
        onSavedMeasures = { openMeasurements(saved = true) },
        onZoomIn = { requestCamera(camera.copy(zoom = (camera.zoom + 1).coerceAtMost(22.0))) },
        onZoomOut = { requestCamera(camera.copy(zoom = (camera.zoom - 1).coerceAtLeast(0.0))) },
        canZoomIn = camera.zoom < 22.0, canZoomOut = camera.zoom > 0.0,
        showEssences = !isFreeMap && essenceCounts.isNotEmpty(),
        onMeasure = { openMeasurements() },
    )
    if (showPlaceSearch) MapWorkspacePlaceSearch(
        onDismiss = { showPlaceSearch = false },
        onSelect = { place ->
            selectedPlaceLabel = place.label
            showPlaceSearch = false
            requestCamera(camera.copy(longitude = place.point.longitude, latitude = place.point.latitude, zoom = 15.0))
        },
    )
    workspaceHelp?.let { help ->
        val displayedBasemap = MAP_LAYERS.firstOrNull {
            it.key == acknowledgedWorkspaceBasemapId(renderStatus, renderedStyle.id, localVectorMode)
        }
        MapWorkspaceSourceDetails(help = help, basemap = displayedBasemap,
            onDismiss = { workspaceHelp = null })
    }
    workspaceMessage?.let { message -> AlertDialog(
        onDismissRequest = { workspaceMessage = null },
        title = { Text(stringResource(R.string.workspace_information)) },
        text = { Text(stringResource(message)) },
        confirmButton = { TextButton(onClick = { workspaceMessage = null }) { Text(stringResource(R.string.ok)) } },
    ) }

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
