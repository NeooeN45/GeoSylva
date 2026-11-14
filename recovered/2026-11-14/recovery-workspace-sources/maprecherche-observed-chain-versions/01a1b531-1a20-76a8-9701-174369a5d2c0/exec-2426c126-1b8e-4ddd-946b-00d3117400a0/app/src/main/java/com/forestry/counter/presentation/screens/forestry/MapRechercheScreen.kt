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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.produceState
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
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.forestry.counter.domain.repository.CartographicLayerRepository
import com.forestry.counter.presentation.screens.forestry.layers.*
import com.forestry.counter.presentation.screens.forestry.trace.*
import com.forestry.counter.presentation.screens.packs.offline.*
import com.quintessences.geosylva.cartography.ui.trees.*
import com.quintessences.geosylva.cartography.ui.source.MapSourceLayerUi
import com.quintessences.geosylva.cartography.ui.trace.TraceScreen
import com.forestry.counter.domain.repository.CartographicMeasurementRepository
import com.forestry.counter.domain.calculation.cartography.CartographicMeasureKind
import com.quintessences.geosylva.cartography.ui.measurement.MeasurePanel
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    measurementRepository: CartographicMeasurementRepository,
    layerRepository: CartographicLayerRepository,
    traceController: MapTraceController,
    offlineController: OfflineMapPackController,
    offlineTileManager: OfflineTileManager? = null,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val mapScope = remember(parcelleId) { MapDataScope.fromLegacyKey(parcelleId) }
    val manualModel = viewModel<MapManualMeasureViewModel>(
        key = "manual-map-measure-$parcelleId",
        factory = remember(measurementRepository, mapScope) { viewModelFactory {
            initializer { MapManualMeasureViewModel(measurementRepository, mapScope, createSavedStateHandle()) }
        } },
    )
    val manual by manualModel.state.collectAsStateWithLifecycle()
    val layerModel = viewModel<MapLayerViewModel>(key = "map-layers-$parcelleId",
        factory = remember(layerRepository) { viewModelFactory { initializer { MapLayerViewModel(layerRepository) } } })
    val layerState by layerModel.state.collectAsStateWithLifecycle()
    val traceState by traceController.state.collectAsStateWithLifecycle()
    val traceActive = traceState.visible && traceState.scope == mapScope
    val sourceActive by traceController.sourceActive.collectAsStateWithLifecycle()
    val tracePhone by traceController.currentPhoneLocation.collectAsStateWithLifecycle()
    LaunchedEffect(mapScope) { layerModel.bind(mapScope); traceController.contextChanged(mapScope) }

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
    LaunchedEffect(offlineProgress) {
        if (offlineProgress?.isComplete == true && offlineProgress?.error == null) {
            hasOfflineTilesState = offlineTileManager.hasOfflineTiles()
        }
    }

    var treesVisible by rememberSaveable(mapScope.toString()) { mutableStateOf(true) }
    var measurementVisible by rememberSaveable(mapScope.toString()) { mutableStateOf(true) }
    var traceVisible by rememberSaveable(mapScope.toString()) { mutableStateOf(true) }
    var treeOpacity by rememberSaveable(mapScope.toString()) { mutableStateOf(1f) }
    var measureOpacity by rememberSaveable(mapScope.toString()) { mutableStateOf(1f) }
    var traceOpacity by rememberSaveable(mapScope.toString()) { mutableStateOf(1f) }
    var sourceLayerId by remember(mapScope) { mutableStateOf<String?>(null) }
    var sourcePanelHeight by remember { mutableStateOf(0.dp) }
    val treeNavigator = remember(context) { TreeNavigator(context) }
    val treeUi = rememberMapTreeWorkspace(mapScope, mapContent, essences, treeNavigator,
        mapOnlyReliableGps, mapReliableGpsThresholdM, hasLocationPermission,
        onAccuracyFilterChanged = { scope.launch { preferencesManager.setMapOnlyReliableGps(it) } },
        onAccuracyThresholdChanged = { scope.launch { preferencesManager.setMapReliableGpsThresholdM(it) } },
        onRequestLocation = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
        onRetryRead = { dataRetry++ }, treesVisible = treesVisible)
    val geoTiges = remember(tiges) { tiges.mapNotNull { t ->
        val (lon, lat, _) = WktUtils.parsePointZ(t.gpsWkt)
        if (lon != null && lat != null && lon.isFinite() && lat.isFinite() && lon in -180.0..180.0 && lat in -90.0..90.0)
            Triple(t, lon, lat) else null
    } }
    val filteredGeoTiges = remember(treeUi.projection.visible) { treeUi.projection.visible.map { Triple(it.tree, it.longitude, it.latitude) } }
    val displayedGeoTiges = filteredGeoTiges
    val withGps = treeUi.projection.counts.shown
    val total = treeUi.projection.counts.total
    val essenceColors = treeUi.projection.species.associate { it.code to it.colorArgb }
    val essenceCounts = treeUi.projection.species.associate { it.code to it.locatedCount }
    val selectedTree = treeUi.state.record
    val handleCurrentTreeTap by rememberUpdatedState<(TappedTreeInfo) -> Unit>({ treeUi.selectTree(it.tigeId, it.parcelleId) })
    var treePanelHeight by remember { mutableStateOf(0.dp) }
    var layerPanelHeight by remember { mutableStateOf(0.dp) }
    var offlinePanelHeight by remember { mutableStateOf(0.dp) }
    var tracePadding by remember { mutableStateOf(PaddingValues(12.dp)) }
    var viewPack by remember(mapScope) { mutableStateOf<OfflineViewPackDraft?>(null) }
    val treePanelActive = treeUi.state.panel != null && !manual.active && !traceActive && !layerState.visible && viewPack == null

    val networkAvailable = rememberMapNetworkAvailable()
    val density = LocalDensity.current.density
    var camera by rememberSaveable(mapScope.toString(), stateSaver = MapCameraPositionSaver) { mutableStateOf(MapCameraPosition(2.5, 46.6, 5.0)) }
    var cameraCommand by remember(mapScope) { mutableStateOf<MapCameraCommand?>(null) }
    var frameCommand by remember(mapScope) { mutableStateOf<MapCameraFitCommand?>(null) }
    var viewport by remember(mapScope) { mutableStateOf<MapViewport?>(null) }
    var overlayFailed by remember(mapScope) { mutableStateOf(false) }
    var renderStatus by remember(mapScope) { mutableStateOf<MapRenderStatus>(MapRenderStatus.Loading("satellite")) }
    var retryGeneration by remember { mutableStateOf(0) }
    val standardPhonePosition = rememberMapPhoneLocation(hasLocationPermission && !sourceActive && !treeUi.state.guidance.active)
    val guidancePhonePosition = treeUi.guidanceMap?.takeIf { it.userPositionIsCurrent }?.let { fix ->
        if (fix.userLatitude == null || fix.userLongitude == null) null else android.location.Location("tree-guidance").apply {
            latitude = fix.userLatitude; longitude = fix.userLongitude
            treeUi.state.guidance.phoneAccuracyM?.let { accuracy = it.toFloat() }
        }
    }
    val phonePosition = if (sourceActive) tracePhone else guidancePhonePosition ?: standardPhonePosition
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
    var dismissedGpsBanner by remember { mutableStateOf(false) }

    // Only the authorised editor snapshot reaches the common map projection.
    val measureActive = manual.active
    val showMeasureSaveDialog = measureActive && manual.panel == MeasurePanel.SAVE
    val viewingSaved = manual.auxiliary in setOf(ManualMeasureAuxiliary.DETAIL, ManualMeasureAuxiliary.HELP, ManualMeasureAuxiliary.SHARE)
    val selectedMeasure = manual.selected?.takeIf { viewingSaved && manual.ready }
    val measurePoints = if (!manual.ready) emptyList() else (selectedMeasure?.points ?: manual.points)
        .map { MeasurePoint(it.latitude, it.longitude) }
    val measureMode = if ((selectedMeasure?.kind ?: manual.mode) == CartographicMeasureKind.DISTANCE) MeasureMode.DISTANCE else MeasureMode.AREA
    val measureColor = Color(manual.colorArgb)
    var measurePanelHeight by remember { mutableStateOf(300.dp) }

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
    val localVectorMode = false
    val renderedStyle = selectedStyle
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
        measurePoints, measurementVisible, measureMode, measureColor, selectedTree?.treeId, selectedTree?.parcelId) {
        buildTerrainMapProjection(mapScope.toString(), sceneRevision.incrementAndGet(), filteredGeoTiges,
            essenceMap, essenceColors, camera.zoom, if (measurementVisible) measurePoints else emptyList(), measureMode, measureColor.toArgb(),
            selectedTree?.let { MapTreeIdentity(it.treeId, it.parcelId) })
    }
    val layerProjection by produceState<Pair<List<LayerRenderSnapshot>, List<MapOverlayFeature>>?>(null, mapScope, layerState.renders) {
        val requested = layerState.renders
        value = requested to withContext(Dispatchers.Default) { buildLayerMapFeatures(requested) }
    }
    val importedFeatures = layerProjection?.takeIf { it.first == layerState.renders }?.second.orEmpty()
    val traceSnapshot = traceState.snapshot.takeIf { traceState.authorised && traceState.scope == mapScope }
    val traceFeatures = remember(traceSnapshot?.portions, traceSnapshot?.phase, traceState.selectedPortionId) { buildTraceMapFeatures(traceSnapshot, traceState.selectedPortionId) }
    val guidanceFeatures = remember(treeUi.guidanceMap) { buildTreeGuidanceFeatures(treeUi.guidanceMap) }
    val scene = remember(projection, phonePosition, importedFeatures, traceFeatures, guidanceFeatures, layerState.renders, treeOpacity, measureOpacity, traceVisible, traceOpacity) {
        val locationFeature = phonePosition?.let {
            runCatching { MapOverlayFeature(MapFeatureKey("device-position", "current"), Point.fromLngLat(it.longitude, it.latitude),
                MapOverlayStyle(colorHex = "#176BDF", pointRadius = 7.0), selectable = false) }.getOrNull()
        }
        val local = projection.scene.features.map { feature ->
            val opacity = if (feature.key.layerId.startsWith("terrain-measure")) measureOpacity else treeOpacity
            feature.copy(style = feature.style.copy(opacity = opacity.toDouble()))
        }
        val traces = if (!traceVisible) emptyList() else traceFeatures.map { it.copy(style = it.style.copy(opacity = traceOpacity.toDouble())) }
        val features = local + importedFeatures + traces + guidanceFeatures + listOfNotNull(locationFeature)
        val selected = selectedLayerMapKey(layerState.renders, importedFeatures) ?: projection.scene.selectedKey
        MapOverlayScene(projection.scene.scopeId, sceneRevision.incrementAndGet(), features, selected)
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
    val importedChoices = layerState.ui.layers.map { layer -> MapLayerChoice("imported:${layer.id}", layer.name,
        onMap = true, visible = layer.visible, availability = MapChoiceAvailability.AVAILABLE,
        detailActionLabel = layerDetailsLabel) } + MapLayerChoice("IMPORT_LAYER", stringResource(R.string.root_workspace_import_layer),
        onMap = false, visible = false, availability = MapChoiceAvailability.AVAILABLE)
    val reliefControlsEnabled = workspaceControlsEnabled(renderStatus, renderedStyle.id,
        localVectorMode, renderedStyle.demSourceUrl != null, overlayFailed)
    var compassTop by remember { mutableStateOf(156.dp) }
    val workspaceState = MapWorkspaceState(
        contextTitle = contextTitle, contextSubtitle = contextSubtitle,
        presentation = if (isFreeMap) MapPresentation.FREE else MapPresentation.TERRAIN,
        basemaps = baseChoices, selectedBasemapId = acknowledgedWorkspaceBasemapId(renderStatus, renderedStyle.id, localVectorMode),
        layers = workspaceLayers + importedChoices + listOf(MapLayerChoice("GPS_TRACES", stringResource(R.string.root_workspace_trace),
            onMap = true, visible = traceVisible, availability = if (traceSnapshot == null) MapChoiceAvailability.DATA_REQUIRED else MapChoiceAvailability.AVAILABLE,
            detailActionLabel = layerDetailsLabel)), panel = workspacePanel,
        pitchDegrees = camera.pitch, verticalExaggeration = appliedRelief.exaggeration,
        controlsEnabled = reliefControlsEnabled,
        controlsUnavailableReason = stringResource(if (localVectorMode || renderedStyle.demSourceUrl == null)
            R.string.workspace_relief_unavailable else R.string.workspace_wait_for_map),
        taskBarVisible = !measureActive && !traceActive && !layerState.visible && !treePanelActive && viewPack == null,
        chromeVisible = !measureActive && !traceActive && !layerState.visible && viewPack == null,
        selectedTask = if (treePanelActive) MapTask.SURVEYS else null,
        locationEnabled = !hasLocationPermission || phonePosition != null,
        contextActionLabel = if (isFreeMap) stringResource(R.string.workspace_explore) else null,
    )
    fun openMeasurements(saved: Boolean = false) {
        workspacePanel = null
        viewPack = null
        treeUi.dispatch(TreeWorkspaceAction.Close)
        layerModel.onClose()
        treeNavigator.stopNavigation()
        traceController.hide()
        measurementVisible = true
        manualModel.open(saved)
    }
    val workspaceActions = object : MapWorkspaceActions {
        override fun onBack() = onNavigateBack()
        override fun onChooseContext() { if (isFreeMap) showPlaceSearch = true }
        override fun onPanelChanged(panel: MapPanel?) {
            workspacePanel = panel
            if (panel != null) { treeUi.dispatch(TreeWorkspaceAction.Close) }
        }
        override fun onLocate() {
            if (!hasLocationPermission) locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            else phonePosition?.let { requestCamera(camera.copy(longitude = it.longitude, latitude = it.latitude, zoom = 18.0)) }
        }
        override fun onTask(task: MapTask) {
            workspacePanel = null
            when (task) {
                MapTask.SURVEYS -> { treesVisible = true; treeUi.openRecords() }
                MapTask.MEASURE -> openMeasurements()
                MapTask.TOOLS -> { showTools = true }
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
                "GPS_TRACES" -> traceVisible = visible
                "LOCAL_MEASUREMENTS" -> {
                    measurementVisible = visible
                    if (!visible) manualModel.leave()
                }
                else -> if (id.startsWith("imported:")) layerModel.setLayerVisible(id.removePrefix("imported:"), visible)
            }
        }
        override fun onAddLayer(id: String) { workspacePanel = null; layerModel.open(mapScope) }
        override fun onLayerDetails(id: String) {
            if (id == "IMPORT_LAYER" || id.startsWith("imported:")) {
                workspacePanel = null; layerModel.open(mapScope)
                if (id.startsWith("imported:")) layerModel.onOpenLayer(id.removePrefix("imported:"))
                return
            }
            if (id in setOf("LOCAL_TREES", "LOCAL_MEASUREMENTS", "GPS_TRACES")) {
                workspacePanel = null
                sourceLayerId = id
                workspaceHelp = MapHelp.BASEMAP_SOURCE
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
        override fun onExplain(help: MapHelp) { sourceLayerId = null; workspaceHelp = help }
    }
    BackHandler(enabled = viewPack != null) { viewPack = null }
    BackHandler(enabled = treePanelActive) { treeUi.dispatch(TreeWorkspaceAction.Close) }
    MapWorkspace(state = workspaceState, actions = workspaceActions, modifier = modifier,
        onCompassTopChanged = { compassTop = it },
        thumbnail = { preview, thumbnailModifier -> MapCatalogThumbnail(preview, thumbnailModifier) }) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val ornamentsBottom = if (workspaceHelp != null) sourcePanelHeight + 8.dp else if (traceActive) tracePadding.calculateBottomPadding() else if (layerState.visible) layerPanelHeight + 8.dp
                else if (treePanelActive) treePanelHeight + 8.dp else if (viewPack != null) offlinePanelHeight + 8.dp
                else if (measureActive) measurePanelHeight + 8.dp else (
                if (workspacePanel != null) mapWorkspacePanelHeight(maxHeight, LocalDensity.current.fontScale) else 112.dp
            ) + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp
            key(mapScope, retryGeneration) {
                SharedMapboxCanvas(
                    accessToken = BuildConfig.MAPBOX_PUBLIC_ACCESS_TOKEN,
                    style = renderedStyle, initialCamera = camera, cameraCommand = cameraCommand,
                    frameCommand = frameCommand, overlays = scene,
                    scaleBarVisible = workspaceHelp == null && !traceActive && !layerState.visible && !treePanelActive && viewPack == null && workspacePanel == null && (!measureActive ||
                        manual.panel == MeasurePanel.COLLAPSED && manual.auxiliary == ManualMeasureAuxiliary.NONE),
                    relief = requestedRelief.copy(enabled = renderedStyle.demSourceUrl != null),
                    contentPadding = PaddingValues(start = 12.dp, top = if (traceActive) tracePadding.calculateTopPadding() else compassTop, end = 12.dp, bottom = ornamentsBottom),
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
                            manualModel.addPoint(coordinate.latitude, coordinate.longitude)
                            true
                        } else false
                    },
                    onMapTap = { tap ->
                        val key = tap.featureKey
                        if (!measureActive && key != null) {
                            if (traceActive && key.layerId.startsWith("gps-trace")) traceController.selectPortion(key.featureId)
                            else if (!traceActive) {
                                val imported = layerFeatureIdentity(key)
                                if (imported != null) { workspacePanel = null; layerModel.onFeatureSelected(imported.layerId, imported.featureId) }
                                else {
                                    currentProjection.treesByKey[key]?.let { handleCurrentTreeTap(it) }
                                    currentProjection.clustersByKey[key]?.let { members ->
                                        frameCommand = MapCameraFitCommand(UUID.randomUUID().toString(), members,
                                            paddingTopPx = 100.0 * density, paddingBottomPx = 130.0 * density,
                                            paddingLeftPx = 55.0 * density, paddingRightPx = 55.0 * density)
                                    }
                                }
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
            if (measureActive) MapManualMeasureUi(
                unverifiedState = manual, model = manualModel, contextLabel = contextTitle,
                locationEnabled = !hasLocationPermission || phonePosition != null,
                onOpenLayers = { manualModel.leave(); workspacePanel = MapPanel.LAYERS },
                onLocateOnMap = { workspaceActions.onLocate() },
                onPanelHeightChanged = { measurePanelHeight = it },
                onCompassTopChanged = { compassTop = it },
            )

            if (treePanelActive) TreeWorkspacePanel(treeUi.state, treeUi.dispatch,
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp),
                onHeightChanged = { treePanelHeight = it })
            MapLayerHost(layerModel, Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                onPanelHeightChanged = { layerPanelHeight = it })
            if (traceActive) MapTraceHost(traceController, mapScope, contextTitle,
                onClose = { }, onLayers = { traceController.hide(); workspacePanel = MapPanel.LAYERS },
                onFrame = {
                    val coordinates = traceSnapshot?.portions.orEmpty().flatMap { it.points }.map { MapCoordinate(it.longitude, it.latitude) }
                    if (coordinates.isNotEmpty()) frameCommand = MapCameraFitCommand(UUID.randomUUID().toString(), coordinates,
                        paddingTopPx = 130.0 * density, paddingBottomPx = tracePadding.calculateBottomPadding().value * density + 40.0,
                        paddingLeftPx = 32.0 * density, paddingRightPx = 72.0 * density)
                },
                onRequestPermissions = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                previewLocation = standardPhonePosition,
                canvas = { padding -> SideEffect { tracePadding = padding } })
            viewPack?.let { draft ->
                Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp)
                    .onSizeChanged { offlinePanelHeight = (it.height / density).dp },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp)) {
                    OfflineViewPackPreparation(offlineController, mapScope, draft, onDismiss = { viewPack = null },
                        modifier = Modifier.heightIn(max = maxHeight * 0.58f).verticalScroll(rememberScrollState()).padding(16.dp))
                }
            }
        }
    }

    if (showTools) MapWorkspaceTools(
        onDismiss = { showTools = false },
        onGpsPrecision = { workspacePanel = null; treeUi.openAccuracy() },
        onOffline = {
            val view = viewport
            val styleUri = renderedStyle.document.takeIf { it.startsWith("mapbox://styles/") &&
                (renderStatus as? MapRenderStatus.Ready)?.styleId == renderedStyle.id }
            val footprint = view?.visibleFootprint
            if (view == null || styleUri == null || footprint == null) workspaceMessage = R.string.root_workspace_offline_style_required
            else {
                // Freeze the exposed map surface, using native pixel-to-surface rays.
                // Covered panels and sky never become an invented download rectangle.
                val ring = footprint.map { Point.fromLngLat(it.longitude, it.latitude) }
                val z = kotlin.math.floor(view.camera.zoom).toInt().coerceIn(0, 22)
                viewPack = OfflineViewPackDraft(name = contextTitle, geometryJson = com.mapbox.geojson.Polygon.fromLngLats(listOf(ring)).toJson(),
                    minZoom = z, maxZoom = (z + 3).coerceAtMost(22), pixelRatio = density, styleUri = styleUri,
                    reliefSource = renderedStyle.demSourceUrl, initiallyRelief = requestedRelief.enabled)
            }
        },
        onEssences = { workspacePanel = null; treesVisible = true; treeUi.openSpecies() },
        onSavedMeasures = { openMeasurements(saved = true) },
        onZoomIn = { requestCamera(camera.copy(zoom = (camera.zoom + 1).coerceAtMost(22.0))) },
        onZoomOut = { requestCamera(camera.copy(zoom = (camera.zoom - 1).coerceAtLeast(0.0))) },
        canZoomIn = camera.zoom < 22.0, canZoomOut = camera.zoom > 0.0,
        showEssences = !isFreeMap && essenceCounts.isNotEmpty(),
        onMeasure = { openMeasurements() },
        onImportLayer = { viewPack = null; manualModel.leave(); treeUi.dispatch(TreeWorkspaceAction.Close); workspacePanel = null; layerModel.open(mapScope) },
        onTrace = { viewPack = null; manualModel.leave(); layerModel.onClose(); treeUi.dispatch(TreeWorkspaceAction.Close); treeNavigator.stopNavigation(); workspacePanel = null; traceController.open(mapScope) },
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
        val layer = sourceLayerId?.let { id -> MapSourceLayerUi(
            id = id,
            title = stringResource(if (id == "LOCAL_TREES") R.string.workspace_tree_layer else if (id == "GPS_TRACES") R.string.root_workspace_trace else R.string.workspace_measure_layer),
            previewKey = MapPreviewKey(if (id == "LOCAL_TREES") "LOCAL_TREES" else "LOCAL_MEASUREMENTS"),
            visible = when (id) { "LOCAL_TREES" -> treesVisible; "GPS_TRACES" -> traceVisible; else -> measurementVisible },
            opacity = when (id) { "LOCAL_TREES" -> treeOpacity; "GPS_TRACES" -> traceOpacity; else -> measureOpacity },
            legendLabel = stringResource(if (id == "LOCAL_TREES") R.string.workspace_tree_layer else if (id == "GPS_TRACES") R.string.root_workspace_trace else R.string.workspace_measure_layer),
            legendColorHex = if (id == "LOCAL_TREES") "#176F39" else "#176BDF",
            origin = "Géosylva", distribution = stringResource(R.string.workspace_on_device),
            note = stringResource(if (id == "LOCAL_TREES") R.string.workspace_trees_source else R.string.workspace_measure_source)) }
        MapWorkspaceSourceDetails(help = help, basemap = displayedBasemap, layer = layer,
            onPanelHeightChanged = { sourcePanelHeight = it },
            onLayerVisibilityChanged = { visible -> when (sourceLayerId) {
                "LOCAL_TREES" -> treesVisible = visible
                "GPS_TRACES" -> traceVisible = visible
                else -> measurementVisible = visible
            } },
            onLayerOpacityChanged = { opacity -> if (opacity.isFinite() && opacity in 0f..1f) when (sourceLayerId) {
                "LOCAL_TREES" -> treeOpacity = opacity
                "GPS_TRACES" -> traceOpacity = opacity
                else -> measureOpacity = opacity
            } },
            onDismiss = { workspaceHelp = null; sourceLayerId = null })
    }
    workspaceMessage?.let { message -> AlertDialog(
        onDismissRequest = { workspaceMessage = null },
        title = { Text(stringResource(R.string.workspace_information)) },
        text = { Text(stringResource(message)) },
        confirmButton = { TextButton(onClick = { workspaceMessage = null }) { Text(stringResource(R.string.ok)) } },
    ) }

    if (viewPack != null) OfflinePackDialogs(offlineController)
}
