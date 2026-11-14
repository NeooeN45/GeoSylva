package com.forestry.counter.presentation.screens.forestry

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.forestry.counter.domain.location.TreeNavigator
import com.forestry.counter.domain.location.TreePositionStatus
import com.forestry.counter.domain.model.Essence
import com.forestry.counter.domain.usecase.maps.*
import com.quintessences.geosylva.cartography.ui.trees.*

/** Values for the host's shared map projection, never a second SDK canvas. */
data class TreeGuidanceMapOverlay(
    val targetLatitude: Double,
    val targetLongitude: Double,
    val userLatitude: Double?,
    val userLongitude: Double?,
    val userPositionIsCurrent: Boolean,
    val userHeadingDeg: Float?,
    /** Only a fresh fix may produce this straight, indicative connector. */
    val drawIndicativeConnector: Boolean,
    val lastFixAgeMillis: Long?,
)

data class MapTreeWorkspaceBinding(
    val state: TreeWorkspaceUiState,
    val projection: MapTreeDisplayProjection,
    val guidanceMap: TreeGuidanceMapOverlay?,
    val dispatch: (TreeWorkspaceAction) -> Unit,
    /** Captured SDK taps are checked against the latest authorised projection. */
    val selectTree: (treeId: String, parcelId: String) -> Unit,
    val openRecords: () -> Unit,
    val openSpecies: () -> Unit,
    val openAccuracy: () -> Unit,
)

/**
 * Adapter around the existing authorised read flow and preferences, not a new read path.
 * The host retains ObserveMapTrees, preference persistence and common SDK projection.
 * onParentLabel may only return a label from an already authorised parent read.
 */
@Composable
fun rememberMapTreeWorkspace(
    scope: MapDataScope,
    content: MapTreeContent,
    essences: List<Essence>,
    navigator: TreeNavigator,
    accuracyFilterEnabled: Boolean,
    accuracyThresholdM: Float,
    hasLocationPermission: Boolean,
    onAccuracyFilterChanged: (Boolean) -> Unit,
    onAccuracyThresholdChanged: (Float) -> Unit,
    onRequestLocation: () -> Unit,
    onRetryRead: () -> Unit = {},
    treesVisible: Boolean = true,
    onParentLabel: (String) -> String? = { null },
): MapTreeWorkspaceBinding {
    val scopeKey = scope.toString()
    var hiddenSpecies by rememberSaveable(scopeKey) { mutableStateOf(emptyList<String>()) }
    var selectedId by rememberSaveable(scopeKey) { mutableStateOf<String?>(null) }
    var selectedParent by rememberSaveable(scopeKey) { mutableStateOf<String?>(null) }
    var panelName by rememberSaveable(scopeKey) { mutableStateOf<String?>(null) }
    var previousPanelName by rememberSaveable(scopeKey) { mutableStateOf<String?>(null) }
    var help by rememberSaveable(scopeKey) { mutableStateOf(TreeHelp.RECORD.name) }
    var permissionStartPending by remember(scopeKey) { mutableStateOf(false) }
    val threshold = accuracyThresholdM.takeIf { it.isFinite() && it in 3f..100f } ?: 8f
    var emptyDismissed by rememberSaveable(scopeKey, accuracyFilterEnabled, threshold, hiddenSpecies, treesVisible) { mutableStateOf(false) }
    val safeContent = content.takeIf { it.scope == scope } ?: MapTreeContent.Loading(scope)
    val dataReady = safeContent is MapTreeContent.Ready
    val trees = (safeContent as? MapTreeContent.Ready)?.trees.orEmpty()
    val projection = remember(trees, essences, hiddenSpecies, accuracyFilterEnabled, threshold, treesVisible) {
        MapTreeDisplay.project(trees, essences, hiddenSpecies.toSet(), accuracyFilterEnabled, threshold, treesVisible)
    }
    val essenceByCode = remember(essences) { essences.associateBy { MapTreeDisplay.speciesCode(it.code) } }
    val currentProjection by rememberUpdatedState(projection)
    val currentReady by rememberUpdatedState(dataReady)
    val currentEssences by rememberUpdatedState(essenceByCode)
    val changeFilter by rememberUpdatedState(onAccuracyFilterChanged)
    val changeThreshold by rememberUpdatedState(onAccuracyThresholdChanged)
    val requestLocation by rememberUpdatedState(onRequestLocation)
    val retryRead by rememberUpdatedState(onRetryRead)
    val permission by rememberUpdatedState(hasLocationPermission)
    val collectedNavigation by navigator.state.collectAsStateWithLifecycle()

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(navigator, scopeKey, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> navigator.pauseNavigation()
                Lifecycle.Event.ON_START -> navigator.resumeNavigation()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); navigator.stopNavigation() }
    }

    fun currentPoint(id: String?, parent: String?): MapTreePoint? = if (!currentReady) null else
        currentProjection.visible.firstOrNull { it.tree.id == id && it.tree.parcelleId == parent }
    val point = currentPoint(selectedId, selectedParent)
    val navTarget = collectedNavigation.target
    val currentTargetPoint = navTarget?.let { currentPoint(it.tigeId, it.parcelleId) }
    // Rendering suppresses stale identities immediately, before the cleanup effect executes.
    val navigation = collectedNavigation.takeIf { navTarget != null && currentTargetPoint != null &&
        navTarget.lat == currentTargetPoint.latitude && navTarget.lon == currentTargetPoint.longitude &&
        navTarget.recordedAccuracyM == MapTreeDisplay.knownAccuracy(currentTargetPoint.tree.precisionM) }
        ?: TreeNavigator.NavigationState()
    LaunchedEffect(projection, dataReady, navTarget) {
        if (point == null && selectedId != null) {
            selectedId = null
            selectedParent = null
            permissionStartPending = false
            if (panelName in setOf(TreeWorkspacePanel.RECORD.name, TreeWorkspacePanel.GUIDANCE.name)) panelName = null
        }
        if (collectedNavigation.isActive && !navigation.isActive) navigator.stopNavigation()
    }
    LaunchedEffect(hasLocationPermission, permissionStartPending, projection) {
        if (hasLocationPermission && permissionStartPending) {
            permissionStartPending = false
            currentPoint(selectedId, selectedParent)?.let { navigator.startNavigation(it.target(currentEssences)) }
        }
    }
    val select: (String, String) -> Unit = remember(scopeKey, navigator) {
        { id, parent ->
            currentPoint(id, parent)?.let {
                if (navigator.state.value.target?.let { target -> target.tigeId != id || target.parcelleId != parent } == true) navigator.stopNavigation()
                selectedId = id
                selectedParent = parent
                panelName = TreeWorkspacePanel.RECORD.name
                permissionStartPending = false
            }
        }
    }
    val dispatch: (TreeWorkspaceAction) -> Unit = { action ->
        when (action) {
            TreeWorkspaceAction.Close -> when (panelName?.let(TreeWorkspacePanel::valueOf)) {
                TreeWorkspacePanel.HELP -> { panelName = previousPanelName; previousPanelName = null }
                TreeWorkspacePanel.RECORD -> panelName = if (navigator.state.value.isActive) TreeWorkspacePanel.GUIDANCE.name else null
                TreeWorkspacePanel.GUIDANCE -> { navigator.stopNavigation(); permissionStartPending = false; panelName = null }
                else -> { panelName = null; emptyDismissed = true }
            }
            TreeWorkspaceAction.ShowAllSpecies -> hiddenSpecies = emptyList()
            is TreeWorkspaceAction.ToggleSpecies -> {
                val code = MapTreeDisplay.speciesCode(action.code)
                if (currentProjection.species.any { it.code == code }) hiddenSpecies =
                    if (action.selected) hiddenSpecies - code else (hiddenSpecies + code).distinct()
            }
            is TreeWorkspaceAction.SetAccuracyFilter -> {
                changeFilter(action.enabled)
                if (!action.enabled && (panelName == TreeWorkspacePanel.EMPTY.name || panelName == null)) panelName = null
                emptyDismissed = !action.enabled
            }
            is TreeWorkspaceAction.SetAccuracyThreshold -> if (action.metres.isFinite() && action.metres in 3f..100f) changeThreshold(action.metres)
            TreeWorkspaceAction.AdjustAccuracy -> panelName = TreeWorkspacePanel.ACCURACY.name
            TreeWorkspaceAction.StartGuidance -> currentPoint(selectedId, selectedParent)?.let { targetPoint ->
                if (!permission) {
                    permissionStartPending = true
                    requestLocation()
                } else if (!navigator.state.value.isActive || navigator.state.value.target != targetPoint.target(currentEssences)) {
                    navigator.startNavigation(targetPoint.target(currentEssences))
                }
                panelName = TreeWorkspacePanel.GUIDANCE.name
            }
            TreeWorkspaceAction.StopGuidance -> { navigator.stopNavigation(); permissionStartPending = false; panelName = TreeWorkspacePanel.RECORD.name }
            TreeWorkspaceAction.ViewRecord -> if (currentPoint(selectedId, selectedParent) != null) panelName = TreeWorkspacePanel.RECORD.name
            is TreeWorkspaceAction.SelectRecord -> select(action.treeId, action.parcelId)
            is TreeWorkspaceAction.Help -> {
                previousPanelName = panelName ?: if (currentProjection.emptyBecauseOfAccuracy) TreeWorkspacePanel.EMPTY.name else null
                help = action.topic.name
                panelName = TreeWorkspacePanel.HELP.name
            }
            TreeWorkspaceAction.RetryRead -> retryRead()
            TreeWorkspaceAction.RequestLocation -> requestLocation()
        }
    }
    val activePanel = panelName?.let(TreeWorkspacePanel::valueOf)
        ?: if (projection.emptyBecauseOfAccuracy && !emptyDismissed) TreeWorkspacePanel.EMPTY else null
    val record = point?.record(essenceByCode, onParentLabel)
    val safePanel = if (activePanel in setOf(TreeWorkspacePanel.RECORD, TreeWorkspacePanel.GUIDANCE) && record == null) null else activePanel
    val gps = if (permissionStartPending && !hasLocationPermission) TreeGpsStatus.PERMISSION_REQUIRED
        else TreeGpsStatus.valueOf(navigation.positionStatus.name)
    val counts = projection.counts
    val state = TreeWorkspaceUiState(safePanel,
        when (safeContent) {
            is MapTreeContent.Loading -> TreeReadStatus.LOADING
            is MapTreeContent.Ready -> TreeReadStatus.READY
            is MapTreeContent.Unavailable -> TreeReadStatus.UNAVAILABLE
        }, record = record,
        records = projection.visible.map { it.record(essenceByCode, onParentLabel) },
        species = projection.species.map { TreeSpeciesUi(it.code, it.name, it.colorArgb, it.locatedCount, it.selected) },
        counts = TreeDisplayCountsUi(counts.total, counts.located, counts.withoutCoordinates, counts.speciesHidden,
            counts.selectedLocated, counts.aboveThreshold, counts.unknownAccuracy, counts.shown),
        accuracyFilterEnabled = accuracyFilterEnabled, accuracyThresholdM = threshold,
        guidance = TreeGuidanceUi(navigation.isActive, gps, TreeHeadingStatus.valueOf(navigation.orientationStatus.name),
            navigation.distanceM?.toDouble(), navigation.relativeBearingDeg, navigation.userAccuracyM?.toDouble(),
            navigation.lastFixAgeMillis?.div(1_000L), navigation.nearRecordedPoint), help = TreeHelp.valueOf(help))
    val overlay = navigation.target?.takeIf { navigation.isActive }?.let { target ->
        val fresh = navigation.positionStatus == TreePositionStatus.FRESH
        TreeGuidanceMapOverlay(target.lat, target.lon, navigation.userLat, navigation.userLon,
            fresh, if (fresh) navigation.deviceAzimuthDeg else null,
            fresh && navigation.distanceM != null, navigation.lastFixAgeMillis)
    }
    return MapTreeWorkspaceBinding(state, projection, overlay, dispatch, select,
        openRecords = { panelName = TreeWorkspacePanel.RECORDS.name },
        openSpecies = { panelName = TreeWorkspacePanel.SPECIES.name },
        openAccuracy = { panelName = TreeWorkspacePanel.ACCURACY.name })
}

private fun MapTreePoint.target(essences: Map<String, Essence>): TreeNavigator.Target {
    val code = MapTreeDisplay.speciesCode(tree.essenceCode)
    return TreeNavigator.Target(tree.id, essences[code]?.name ?: tree.essenceCode, code,
        tree.diamCm, tree.hauteurM, latitude, longitude, tree.parcelleId,
        MapTreeDisplay.knownAccuracy(tree.precisionM), tree.numero)
}
private fun MapTreePoint.record(essences: Map<String, Essence>, parent: (String) -> String?): TreeRecordUi {
    val code = MapTreeDisplay.speciesCode(tree.essenceCode)
    return TreeRecordUi(tree.id, tree.parcelleId, tree.numero, essences[code]?.name ?: tree.essenceCode,
        tree.diamCm.takeIf { it.isFinite() && it > 0 }, null,
        tree.hauteurM?.takeIf { it.isFinite() && it > 0 }, MapTreeDisplay.knownAccuracy(tree.precisionM), true,
        parent(tree.parcelleId))
}
