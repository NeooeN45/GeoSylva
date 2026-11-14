@file:OptIn(com.mapbox.maps.MapboxExperimental::class)

package com.quintessences.geosylva.cartography.mapbox

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.mapbox.common.MapboxOptions
import com.mapbox.common.Cancelable
import com.mapbox.bindgen.Value
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraBoundsOptions
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapLoadingErrorType
import com.mapbox.maps.Style
import com.mapbox.maps.RenderedQueryGeometry
import com.mapbox.maps.RenderedQueryOptions
import com.mapbox.maps.ScreenBox
import com.mapbox.maps.ScreenCoordinate
import com.mapbox.maps.extension.compose.ComposeMapInitOptions
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.MapViewportState
import com.mapbox.maps.extension.compose.rememberMapState
import com.mapbox.maps.extension.compose.style.GenericStyle
import com.mapbox.maps.extension.compose.style.StyleState
import com.mapbox.maps.plugin.gestures.generated.GesturesSettings
import com.mapbox.maps.plugin.viewport.viewport
import com.mapbox.maps.plugin.DistanceUnits
import android.os.SystemClock
import android.os.Looper
import androidx.core.os.HandlerCompat
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Shared renderer only: no business repository, permission inference or GPS request.
 *
 * A blank/non-public token creates no MapView. All hosts use the same application
 * public token; it must never be a secret sk token. The host owns loading/error UI
 * and can recreate this composable with a new key for an explicit retry.
 *
 * [initialCamera] is read once. Later moves use uniquely identified commands;
 * [onCameraChanged] reports the SDK camera, including user gestures, independently
 * of the requested camera. The Compose SDK owns lifecycle and required ornaments.
 * [onReliefChanged] acknowledges values read back from the SDK style, never a request.
 * [onDiagnostic] emits fixed codes, deduplicated within the active render generation.
 * [contentPadding] keeps native ornaments outside the host's overlay/sheet area;
 * it does not itself modify camera or measure a physical terrain slope.
 * [scaleBarVisible] hides only the distance scale when a host panel needs that
 * space. Provider logo and attribution always remain available.
 */
@Composable
fun SharedMapboxCanvas(
    accessToken: String?,
    style: MapboxStyleSpec,
    initialCamera: MapCameraPosition,
    modifier: Modifier = Modifier,
    cameraCommand: MapCameraCommand? = null,
    relief: MapRelief = MapRelief(),
    contentPadding: PaddingValues = PaddingValues(12.dp),
    onCameraChanged: (MapCameraPosition) -> Unit = {},
    onReliefChanged: (MapRelief) -> Unit = {},
    onCommandConsumed: (String) -> Unit = {},
    onStatusChanged: (MapRenderStatus) -> Unit = {},
    onDiagnostic: (MapRenderDiagnostic) -> Unit = {},
    overlays: MapOverlayScene = MapOverlayScene.Empty,
    frameCommand: MapCameraFitCommand? = null,
    onFrameCommandConsumed: (String) -> Unit = {},
    onMapTap: (MapOverlayTap) -> Unit = {},
    onMapLongPress: (MapCoordinate) -> Unit = {},
    onViewportChanged: (MapViewport) -> Unit = {},
    onOverlayStatusChanged: (MapOverlayStatus) -> Unit = {},
    /** Synchronous coordinate capture. Returning true consumes the tap without an asynchronous hit query. */
    onCoordinateTap: (MapCoordinate) -> Boolean = { false },
    scaleBarVisible: Boolean = true,
    /** Pixel position relative to this native map view, not the padded camera target. */
    reticlePositionPx: Offset? = null,
    onReticleCoordinateChanged: (MapCoordinate?) -> Unit = {},
    onCameraUserGesture: () -> Unit = {},
    onCameraCommandEvent: (MapCameraCommandEvent) -> Unit = {},
) {
    val statusCallback by rememberUpdatedState(onStatusChanged)
    val diagnosticCallback by rememberUpdatedState(onDiagnostic)
    val cameraCallback by rememberUpdatedState(onCameraChanged)
    val reliefCallback by rememberUpdatedState(onReliefChanged)
    val consumedCallback by rememberUpdatedState(onCommandConsumed)
    val frameConsumedCallback by rememberUpdatedState(onFrameCommandConsumed)
    val tapCallback by rememberUpdatedState(onMapTap)
    val longPressCallback by rememberUpdatedState(onMapLongPress)
    val viewportCallback by rememberUpdatedState(onViewportChanged)
    val overlayCallback by rememberUpdatedState(onOverlayStatusChanged)
    val coordinateTapCallback by rememberUpdatedState(onCoordinateTap)
    val reticleCallback by rememberUpdatedState(onReticleCoordinateChanged)
    val cameraGestureCallback by rememberUpdatedState(onCameraUserGesture)
    val cameraCommandEventCallback by rememberUpdatedState(onCameraCommandEvent)
    var retainedCamera by remember { mutableStateOf(initialCamera) }
    var lastCommandId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastFrameCommandId by rememberSaveable { mutableStateOf<String?>(null) }
    val issue = mapConfigurationIssue(accessToken, style, relief)
    val rendererKey = remember(style.id, style.document, style.demSourceUrl, accessToken, issue, overlays.scopeId) { Any() }
    val activeRendererKey by rememberUpdatedState(rendererKey)
    LaunchedEffect(rendererKey, reticlePositionPx == null) { reticleCallback(null) }
    var lastDiagnostic by remember(rendererKey) { mutableStateOf<MapRenderDiagnostic?>(null) }
    fun publishDiagnostic(diagnostic: MapRenderDiagnostic) {
        if (activeRendererKey === rendererKey && lastDiagnostic != diagnostic) {
            lastDiagnostic = diagnostic
            diagnosticCallback(diagnostic)
        }
    }
    if (issue != null) {
        LaunchedEffect(rendererKey, issue) {
            publishDiagnostic(when {
                issue == MapRenderStatus.ConfigurationRequired -> MapRenderDiagnostic.CONFIGURATION_REQUIRED
                issue is MapRenderStatus.Failed && issue.reason == MapFailureReason.DEM_UNAVAILABLE ->
                    MapRenderDiagnostic.DEM_UNAVAILABLE
                else -> MapRenderDiagnostic.PUBLIC_TOKEN_REQUIRED
            })
            statusCallback(issue)
        }
        Box(modifier)
        return
    }

    // Configure before composing MapboxMap: SideEffect would run too late for
    // AndroidView construction. No Mapbox API is touched in the rejected branch.
    var tokenConfigured by remember(accessToken) { mutableStateOf(false) }
    LaunchedEffect(accessToken) {
        publishDiagnostic(MapRenderDiagnostic.TOKEN_CONFIGURING)
        check(configureMapboxPublicToken(accessToken))
        com.quintessences.geosylva.cartography.mapbox.offline.MapboxOfflineResources.configureRendering()
        tokenConfigured = true
    }
    LaunchedEffect(rendererKey, style.demSourceUrl, relief) {
        statusCallback(MapRenderStatus.Loading(style.id))
    }
    if (!tokenConfigured) {
        Box(modifier)
        return
    }

    Box(modifier) {
        // Dispose the previous renderer when changing styles. A delayed callback from
        // its old resource/style generation cannot overwrite the new host state.
        key(rendererKey) {
            ConfiguredMapboxCanvas(style, retainedCamera, Modifier.fillMaxSize(), cameraCommand, lastCommandId, relief, contentPadding, scaleBarVisible,
                overlays, frameCommand, lastFrameCommandId,
                onCameraChanged = { camera ->
                    if (activeRendererKey === rendererKey) {
                        retainedCamera = camera
                        cameraCallback(camera)
                    }
                },
                onReliefChanged = { if (activeRendererKey === rendererKey) reliefCallback(it) },
                onCommandConsumed = {
                    if (activeRendererKey === rendererKey) {
                        lastCommandId = it
                        consumedCallback(it)
                    }
                },
                onStatusChanged = { if (activeRendererKey === rendererKey) statusCallback(it) },
                onFrameCommandConsumed = {
                    if (activeRendererKey === rendererKey) { lastFrameCommandId = it; frameConsumedCallback(it) }
                },
                onMapTap = { if (activeRendererKey === rendererKey) tapCallback(it) },
                onMapLongPress = { if (activeRendererKey === rendererKey) longPressCallback(it) },
                onViewportChanged = { if (activeRendererKey === rendererKey) viewportCallback(it) },
                onOverlayStatusChanged = { if (activeRendererKey === rendererKey) overlayCallback(it) },
                onCoordinateTap = { activeRendererKey === rendererKey && coordinateTapCallback(it) },
                reticlePositionPx = reticlePositionPx,
                onReticleCoordinateChanged = { if (activeRendererKey === rendererKey) reticleCallback(it) },
                onCameraUserGesture = { if (activeRendererKey === rendererKey) cameraGestureCallback() },
                onCameraCommandEvent = { if (activeRendererKey === rendererKey) cameraCommandEventCallback(it) },
                onDiagnostic = ::publishDiagnostic)
        }
        MapCanvasStatusBarChrome(style,Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun ConfiguredMapboxCanvas(
    style: MapboxStyleSpec,
    initialCamera: MapCameraPosition,
    modifier: Modifier,
    cameraCommand: MapCameraCommand?,
    lastCommandId: String?,
    relief: MapRelief,
    contentPadding: PaddingValues,
    scaleBarVisible: Boolean,
    overlays: MapOverlayScene,
    frameCommand: MapCameraFitCommand?,
    lastFrameCommandId: String?,
    onCameraChanged: (MapCameraPosition) -> Unit,
    onReliefChanged: (MapRelief) -> Unit,
    onCommandConsumed: (String) -> Unit,
    onStatusChanged: (MapRenderStatus) -> Unit,
    onDiagnostic: (MapRenderDiagnostic) -> Unit,
    onFrameCommandConsumed: (String) -> Unit,
    onMapTap: (MapOverlayTap) -> Unit,
    onMapLongPress: (MapCoordinate) -> Unit,
    onViewportChanged: (MapViewport) -> Unit,
    onOverlayStatusChanged: (MapOverlayStatus) -> Unit,
    onCoordinateTap: (MapCoordinate) -> Boolean,
    reticlePositionPx: Offset?,
    onReticleCoordinateChanged: (MapCoordinate?) -> Unit,
    onCameraUserGesture: () -> Unit,
    onCameraCommandEvent: (MapCameraCommandEvent) -> Unit,
) {
    val cameraCallback by rememberUpdatedState(onCameraChanged)
    val consumedCallback by rememberUpdatedState(onCommandConsumed)
    val reliefCallback by rememberUpdatedState(onReliefChanged)
    val requestedRelief by rememberUpdatedState(relief)
    val statusCallback by rememberUpdatedState(onStatusChanged)
    val diagnosticCallback by rememberUpdatedState(onDiagnostic)
    val currentStyle by rememberUpdatedState(style)
    val currentScene by rememberUpdatedState(overlays)
    val currentFrameCommand by rememberUpdatedState(frameCommand)
    val currentCameraCommand by rememberUpdatedState(cameraCommand)
    val consumedPointId by rememberUpdatedState(lastCommandId)
    val consumedFrameId by rememberUpdatedState(lastFrameCommandId)
    val frameConsumedCallback by rememberUpdatedState(onFrameCommandConsumed)
    val tapCallback by rememberUpdatedState(onMapTap)
    val longPressCallback by rememberUpdatedState(onMapLongPress)
    val viewportCallback by rememberUpdatedState(onViewportChanged)
    val overlayCallback by rememberUpdatedState(onOverlayStatusChanged)
    val coordinateTapCallback by rememberUpdatedState(onCoordinateTap)
    val currentReticlePosition by rememberUpdatedState(reticlePositionPx)
    val reticleCallback by rememberUpdatedState(onReticleCoordinateChanged)
    val cameraGestureCallback by rememberUpdatedState(onCameraUserGesture)
    val cameraCommandEventCallback by rememberUpdatedState(onCameraCommandEvent)
    val density = LocalDensity.current.density
    val fontScale = LocalDensity.current.fontScale
    val currentRenderDensity by rememberUpdatedState(density)
    val currentRenderFontScale by rememberUpdatedState(fontScale)
    val layoutDirection = LocalLayoutDirection.current
    val visibleInsets = viewportInsetsPx(contentPadding, density, layoutDirection)
    val currentVisibleInsets by rememberUpdatedState(visibleInsets)
    // Preserve Mapbox's device-density/default initialization. MSAA x2 smooths
    // vector/terrain edges; it does not increase imagery resolution or accuracy.
    // Extra GPU samples have a cost to qualify on the target field devices.
    val mapInitOptions = remember(density) {
        ComposeMapInitOptions(pixelRatio = density, antialiasingSampleCount = MAP_ANTIALIASING_SAMPLES)
    }
    // SDK callbacks and their uptime deadlines use the real async Main Looper, including
    // under Compose tests. The remembered parent Job still cancels with this composition.
    val nativeMainDispatcher = remember {
        HandlerCompat.createAsync(Looper.getMainLooper()).asCoroutineDispatcher("Mapbox-native-main")
    }
    val callbackScope = rememberCoroutineScope { nativeMainDispatcher }
    val overlayRenderer = remember { NativeMapOverlays() }
    var querySerial by remember { mutableStateOf(0L) }
    var pendingQuery by remember { mutableStateOf<Cancelable?>(null) }
    var alive by remember { mutableStateOf(true) }
    var issuedFrameCommandId by remember { mutableStateOf<String?>(null) }
    var issuedPointCommandId by remember { mutableStateOf<String?>(null) }
    val cameraGate = remember { MapCameraCommandGate() }
    var issuedFrameFitWindow by remember { mutableStateOf<IssuedFrameFitWindow?>(null) }
    var frameStatusReconciliationPending by remember { mutableStateOf(false) }
    var frameStatusReconciliationFence by remember { mutableStateOf<NativeRestoreFence?>(null) }
    var frameFitFailure by remember { mutableStateOf<MapFitFailure?>(null) }
    var nativeLayoutRevision by remember { mutableStateOf(0L) }
    DisposableEffect(Unit) {
        onDispose { alive = false; issuedFrameFitWindow?.close(); cameraGate.invalidate(); querySerial++; pendingQuery?.cancel() }
    }
    val mapState = rememberMapState {
        gesturesState.gesturesSettings = GesturesSettings.Builder().apply { pitchEnabled = true }.build()
    }
    var sdkMap by remember { mutableStateOf<com.mapbox.maps.MapboxMap?>(null) }
    var sdkMapView by remember { mutableStateOf<com.mapbox.maps.MapView?>(null) }
    var lastReticleCoordinate by remember { mutableStateOf<MapCoordinate?>(null) }
    // Callback deduplication only; never a cache of map resources or terrain rays.
    var lastPublishedViewport by remember { mutableStateOf<MapViewport?>(null) }
    // Observation only, deliberately NOT rememberMapViewportState/its Saver: restoring an SDK
    // saved camera would enqueue an unguarded setCameraOptions action in its Channel.
    // The host's observed camera is the sole saved authority. Its optional same-observation
    // physical witness is consumed once after resources; the logical seed locates the DEM first.
    val viewport = remember { MapViewportState() }
    // One owner for terrain: native style commands below. INITIAL is the
    // Compose no-op; DISABLED would restore a style's default terrain instead
    // of reliably removing it (notably Mapbox Standard).
    val styleState = remember { StyleState() }

    val resourceGeneration = remember { Any() }
    val resourceCycle = remember { MapResourceCycle() }
    var observersReady by remember { mutableStateOf(false) }
    var boundsAccepted by remember { mutableStateOf(false) }
    var acknowledgedRelief by remember { mutableStateOf<MapRelief?>(null) }
    var resourceFailure by remember { mutableStateOf<MapFailureReason?>(null) }
    var readyPublished by remember { mutableStateOf(false) }
    var terrainControlFailed by remember { mutableStateOf(false) }
    var lastTerrainStyle by remember { mutableStateOf<Style?>(null) }
    var lastTerrainRequest by remember { mutableStateOf<MapRelief?>(null) }
    val nativeRestoration = remember { MapNativeCameraRestoration(initialCamera) }
    val nativeRestoreEvents = remember { NativeRestoreEvents() }
    var nativeRestoreDeadline by remember { mutableStateOf<Job?>(null) }
    val nativePoseStyle = remember(style.id,style.document,style.demSourceUrl) { nativePoseStyleKey(style) }

    fun poseContext(): MapNativeCameraContext? = runCatching {
        val map = sdkMap ?: return null
        val view = sdkMapView ?: return null
        val size = map.getSize()
        if (view.width <= 0 || view.height <= 0 || size.width.toDouble() != view.width.toDouble() ||
            size.height.toDouble() != view.height.toDouble()) return null
        MapNativeCameraContext(nativePoseStyle,currentScene.scopeId,requestedRelief,view.width,view.height,
            map.getMapOptions().pixelRatio.toDouble(),map.cameraState.verticalFov,map.getCenterAltitudeMode().name,
            currentVisibleInsets.let {MapCameraPadding(it.top,it.left,it.bottom,it.right)},
            map.cameraState.padding.toMapCameraPadding())
    }.getOrNull()

    fun observedCamera(): MapCameraPosition? {
        if (nativeRestoration.pending || nativeRestoration.failed) return null
        val map = sdkMap ?: return null
        val logical = map.cameraState.toMapCameraPosition()
        if (!nativeRestoreEvents.readyForPose() || !resourceCycle.isComplete(resourceGeneration) ||
            resourceFailure != null || terrainControlFailed || acknowledgedRelief?.let {reliefMatches(it,requestedRelief)} != true ||
            map.style?.let {it.isStyleLoaded() && currentStyle.matchesNativeStyleUri(it.styleURI)} != true) return logical
        val context = poseContext() ?: return logical
        val pose = runCatching {
            val free = map.getFreeCameraOptions()
            val p = free.position ?: return logical
            val q = free.orientation ?: return logical
            MapNativeCameraPose(logical,context,p.x,p.y,p.z,q.x,q.y,q.z,q.w)
        }.getOrNull() ?: return logical
        return if (logical == map.cameraState.toMapCameraPosition() && context == poseContext())
            logical.copy(nativePose=pose) else logical
    }

    fun publishObservedCamera() { observedCamera()?.let(cameraCallback) }
    fun cancelNativeRestoration() {
        val wasFailed = nativeRestoration.failed
        nativeRestoration.cancel()
        nativeRestoreDeadline?.cancel();nativeRestoreDeadline=null
        if (wasFailed) {readyPublished=false;statusCallback(MapRenderStatus.Loading(currentStyle.id))}
    }
    fun newerCameraIntention(): Boolean =
        currentCameraCommand?.id?.let {it != consumedPointId && it != issuedPointCommandId} == true ||
        currentFrameCommand?.id?.let {it != consumedFrameId && it != issuedFrameCommandId} == true

    fun restoreNativePose(): Boolean {
        if (newerCameraIntention()) cancelNativeRestoration()
        if (nativeRestoration.failed) {
            diagnosticCallback(MapRenderDiagnostic.CAMERA_RESTORE_FAILED)
            statusCallback(MapRenderStatus.Failed(MapFailureReason.CAMERA_RESTORE));return false
        }
        if (!nativeRestoration.pending) return true
        val map = sdkMap ?: return false
        val context = poseContext() ?: return false
        if (!nativeRestoration.compatible(context)) {cancelNativeRestoration();return true}
        if (nativeRestoration.observe(nativeRestoreEvents)) {
            nativeRestoreDeadline?.cancel();nativeRestoreDeadline=null;return true
        }
        if (!nativeRestoreEvents.readyForPose()) return false
        val pose = nativeRestoration.proposal(context) ?: return false
        fun samePhysicalPose(): Boolean {
            val free=map.getFreeCameraOptions();val p=free.position;val q=free.orientation
            return p != null && q != null && p.x==pose.x && p.y==pose.y && p.z==pose.z &&
                q.x==pose.qx && q.y==pose.qy && q.z==pose.qz && q.w==pose.qw &&
                map.cameraState.padding.toMapCameraPadding()==pose.camera.padding && poseContext()==context
        }
        try {
            val unchangedBefore=samePhysicalPose()
            val beforeCameraSequence=nativeRestoreEvents.cameraSequence
            val boundary=nativeRestoreEvents.fence(System.currentTimeMillis())
            nativeRestoration.submitted(boundary)
            val free=map.getFreeCameraOptions()
            free.position=com.mapbox.maps.Vec3(pose.x,pose.y,pose.z)
            free.orientation=com.mapbox.maps.Vec4(pose.qx,pose.qy,pose.qz,pose.qw)
            // The geographic seed loaded this region's DEM. Restore this physical observation once,
            // with unchanged size/FOV/padding/terrain, not by reapplying the lossy CameraOptions.
            map.setCamera(free)
            if (resourceFailure != null || terrainControlFailed || !alive || sdkMap !== map ||
                !nativeRestoration.isAwaiting(boundary)) return false
            if (poseContext()!=context) {
                nativeRestoration.fail()
                diagnosticCallback(MapRenderDiagnostic.CAMERA_RESTORE_FAILED)
                statusCallback(MapRenderStatus.Failed(MapFailureReason.CAMERA_RESTORE));return false
            }
            if (unchangedBefore && nativeRestoreEvents.cameraSequence==beforeCameraSequence && samePhysicalPose()) {
                nativeRestoration.exactNoOp();return true
            }
            // A single native application must finish or fail; never leave restoration pending silently.
            nativeRestoreDeadline=callbackScope.launch {
                delay(10_000)
                if (alive && sdkMap === map && nativeRestoration.isAwaiting(boundary)) {
                    if (resourceFailure != null || terrainControlFailed || poseContext()!=context || newerCameraIntention())
                        cancelNativeRestoration()
                    else {
                        nativeRestoration.fail();readyPublished=false
                        diagnosticCallback(MapRenderDiagnostic.CAMERA_RESTORE_FAILED)
                        statusCallback(MapRenderStatus.Failed(MapFailureReason.CAMERA_RESTORE))
                    }
                }
            }
        } catch (_: RuntimeException) {
            if (resourceFailure == null && !terrainControlFailed && alive && sdkMap === map) {
                nativeRestoration.fail()
                diagnosticCallback(MapRenderDiagnostic.CAMERA_RESTORE_FAILED)
                statusCallback(MapRenderStatus.Failed(MapFailureReason.CAMERA_RESTORE))
            } else nativeRestoration.cancel()
        }
        return false
    }
    // Native Loaded/Idle dates and System.currentTimeMillis use the same epoch.
    // A queued event predating a real terrain mutation cannot complete that new cycle.

    fun publishReticle() {
        val map = sdkMap
        val view = sdkMapView
        val pixel = reticlePixelInsideView(currentReticlePosition, view?.width ?: 0, view?.height ?: 0)
        val coordinate = if (!readyPublished || map == null || pixel == null) null else runCatching {
            // The native terrain ray must actually intersect the surface. A sky
            // pixel must never become the camera centre or an arbitrary (0,0).
            val hit = map.coordinateInfoForPixel(pixel)
            if (hit.isOnSurface) MapCoordinate(canonicalLongitude(hit.coordinate.longitude()), hit.coordinate.latitude()) else null
        }.getOrNull()
        if (coordinate != lastReticleCoordinate) {
            lastReticleCoordinate = coordinate
            reticleCallback(coordinate)
        }
    }

    fun applyOverlays() {
        val nativeStyle = sdkMap?.style ?: return
        if (!nativeStyle.isStyleLoaded() || !currentStyle.matchesNativeStyleUri(nativeStyle.styleURI)) return
        // Failed native registration is retried by a new host snapshot/style, never every rendered frame.
        if (overlayRenderer.hasAttempted(nativeStyle, currentScene, currentRenderDensity, currentRenderFontScale)) return
        overlayCallback(overlayRenderer.apply(nativeStyle, currentScene, currentRenderDensity, currentRenderFontScale))
    }

    fun cameraEvent(ticket: MapCameraCommandGate.Ticket, stage: MapCameraCommandStage,
        camera: MapCameraPosition? = null) {
        cameraCommandEventCallback(MapCameraCommandEvent(ticket.id, ticket.kind, stage, ticket.padding, camera))
    }

    // Preserve the native view witness before a Loading FIT loses ownership, even to another FIT.
    fun retainLoadingFrameStatus(): Boolean {
        val window = issuedFrameFitWindow ?: return false
        if (readyPublished) return false
        frameStatusReconciliationPending = true
        window.firstFrameFence?.let { frameStatusReconciliationFence = it }
        return true
    }

    fun closeFrameFit(rearmForContext: Boolean = false, resourceFailed: Boolean = false) {
        val window = issuedFrameFitWindow
        val unfinished = window != null || frameFitFailure != null
        retainLoadingFrameStatus()
        window?.close()
        issuedFrameFitWindow = null
        // Closing a stale context must not depend on that context still being valid.
        if (window != null && cameraGate.isCurrent(window.ticket)) {
            cameraGate.invalidate()
            cameraEvent(window.ticket,MapCameraCommandStage.REJECTED_OPTIONS)
        }
        if (rearmForContext && unfinished) {
            issuedFrameCommandId = null
            frameFitFailure = null
        } else if (resourceFailed && window != null) {
            // Keep the source/terrain Failed status; a later MapLoaded cannot silently erase it.
            frameFitFailure = MapFitFailure.RESOURCE_FAILURE
        }
    }

    fun observeCameraCommand() {
        val map = sdkMap ?: return
        if (!alive) return
        val actual = map.cameraState.toMapCameraPosition()
        val fit = cameraGate.fitObservationCandidate()
        val frame = currentFrameCommand
        val view = sdkMapView
        var fitProjectionVerified = false
        val window = issuedFrameFitWindow
        if (fit != null && frame?.id == fit.id && view != null && window != null && window.ticket === fit &&
            window.stillCurrent()) {
            if (window.refinement.awaitingResources &&
                window.firstFrameFence?.let(nativeRestoreEvents::readyAfter) == true &&
                resourceCycle.isComplete(resourceGeneration) &&
                acknowledgedRelief?.let { reliefMatches(it, requestedRelief) } == true &&
                window.refinement.startAfterResources(SystemClock.uptimeMillis())) {
                window.deadline = callbackScope.launch {
                    delay(window.refinement.maxDurationMs)
                    if (window.stillCurrent()) window.reject(MapFitFailure.BUDGET_EXHAUSTED)
                    else if (issuedFrameFitWindow === window) {
                        val failedResources = resourceFailure != null || terrainControlFailed
                        closeFrameFit(rearmForContext = !failedResources, resourceFailed = failedResources)
                    }
                }
            }
            if (!window.refinement.awaitingResources && fit.target?.let { cameraMatches(it, actual) } == true) {
                // Collect every point, including censored projections; never short-circuit on the first failure.
                val pixels = frame.coordinates.map { coordinate ->
                    val pixel = map.pixelForCoordinate(Point.fromLngLat(coordinate.longitude, coordinate.latitude))
                    MapFitPixel(pixel.x, pixel.y)
                }
                if (cameraMatches(actual, map.cameraState.toMapCameraPosition()) &&
                    cameraGate.fitObservationCandidate() === fit && window.stillCurrent()) {
                    when (val decision = window.refinement.observe(actual, pixels, SystemClock.uptimeMillis())) {
                        MapFitDecision.Verified -> fitProjectionVerified = true
                        is MapFitDecision.Retry -> window.propose(decision.attempt)
                        is MapFitDecision.Failed -> window.reject(decision.reason)
                        MapFitDecision.Waiting -> Unit
                    }
                }
            }
        }
        val ticket = cameraGate.observe(actual, fitProjectionVerified) ?: return
        if (ticket.kind == MapCameraCommandKind.FIT) {
            issuedFrameFitWindow?.close()
            issuedFrameFitWindow = null
        }
        cameraEvent(ticket, MapCameraCommandStage.OBSERVED, actual)
        publishObservedCamera()
        when (ticket.kind) {
            MapCameraCommandKind.POINT -> consumedCallback(ticket.id)
            MapCameraCommandKind.FIT -> frameConsumedCallback(ticket.id)
        }
    }

    fun cameraUserGesture() {
        if (!alive) return
        val canceledLoadingFit = retainLoadingFrameStatus()
        cancelNativeRestoration()
        // A command may already be in props while its LaunchedEffect is still queued.
        issuedPointCommandId = currentCameraCommand?.id ?: issuedPointCommandId
        issuedFrameCommandId = currentFrameCommand?.id ?: issuedFrameCommandId
        issuedFrameFitWindow?.close()
        issuedFrameFitWindow = null
        frameFitFailure = null
        cameraGate.invalidate()?.let { cameraEvent(it, MapCameraCommandStage.CANCELED_BY_GESTURE) }
        cameraGestureCallback()
        if (canceledLoadingFit) sdkMap?.triggerRepaint()
    }

    fun applyPointCommand() {
        val command = currentCameraCommand ?: return
        if (command.id == consumedPointId || command.id == issuedPointCommandId) return
        cancelNativeRestoration()
        val map = sdkMap ?: return
        if (!boundsAccepted || map.style?.isStyleLoaded() != true) return
        issuedPointCommandId = command.id
        retainLoadingFrameStatus()
        issuedFrameFitWindow?.close()
        issuedFrameFitWindow = null
        frameFitFailure = null
        val ticket = cameraGate.submit(command.id, MapCameraCommandKind.POINT, command.position.padding)
        cameraEvent(ticket, MapCameraCommandStage.REQUESTED, map.cameraState.toMapCameraPosition())
        cameraGate.resolve(ticket, command.position)
        cameraEvent(ticket, MapCameraCommandStage.OPTIONS_CALCULATED, command.position)
        sdkMapView?.viewport?.idle()
        map.setCamera(command.position.toCameraOptions())
        observeCameraCommand()
        if (frameStatusReconciliationPending) map.triggerRepaint()
    }

    fun applyFrameCommand() {
        val command = currentFrameCommand ?: return
        if (command.id == consumedFrameId || command.id == issuedFrameCommandId) return
        cancelNativeRestoration()
        val map = sdkMap ?: return
        val commandView = sdkMapView ?: return
        val nativeStyle = map.style ?: return
        if (!boundsAccepted || terrainControlFailed || resourceFailure != null || !nativeStyle.isStyleLoaded() ||
            commandView.width <= 0 || commandView.height <= 0) return
        issuedFrameCommandId = command.id
        retainLoadingFrameStatus()
        issuedFrameFitWindow?.close()
        issuedFrameFitWindow = null
        if (frameFitFailure != null) statusCallback(MapRenderStatus.Loading(currentStyle.id))
        frameFitFailure = null
        val padding = MapCameraPadding(command.paddingTopPx, command.paddingLeftPx,
            command.paddingBottomPx, command.paddingRightPx)
        val ticket = cameraGate.submit(command.id, MapCameraCommandKind.FIT, padding)
        cameraEvent(ticket, MapCameraCommandStage.REQUESTED, map.cameraState.toMapCameraPosition())
        val points = command.coordinates.map { Point.fromLngLat(it.longitude, it.latitude) }
        val state = map.cameraState
        val absolutePadding = EdgeInsets(padding.top,padding.left,padding.bottom,padding.right)
        val camera = CameraOptions.Builder().padding(absolutePadding)
            .bearing(state.bearing).pitch(state.pitch).build()
        val width = commandView.width
        val height = commandView.height
        val layoutRevision = nativeLayoutRevision
        val fitRelief = requestedRelief
        val fitDensity = currentRenderDensity
        val refinement = MapCameraFitRefinement(width,height,padding,points.size,map.getBounds().minZoom)
        lateinit var window: IssuedFrameFitWindow
        fun contextCurrent(): Boolean = alive && sdkMap === map && sdkMapView === commandView &&
            map.style === nativeStyle && nativeStyle.isStyleLoaded() && currentFrameCommand == command &&
            issuedFrameFitWindow === window &&
            commandView.width == width && commandView.height == height && nativeLayoutRevision == layoutRevision &&
            requestedRelief == fitRelief && currentRenderDensity == fitDensity &&
            !terrainControlFailed && resourceFailure == null
        fun stillCurrent(): Boolean = contextCurrent() && cameraGate.isCurrent(ticket)
        fun reject(reason: MapFitFailure) {
            if (!contextCurrent()) return
            window.close()
            issuedFrameFitWindow = null
            cameraGate.invalidate()
            cameraEvent(ticket,MapCameraCommandStage.REJECTED_OPTIONS)
            frameFitFailure = reason
            readyPublished = false
            publishReticle()
            diagnosticCallback(MapRenderDiagnostic.CAMERA_FIT_FAILED)
            statusCallback(MapRenderStatus.Failed(MapFailureReason.CAMERA_FIT))
        }
        fun applyCalculated(attempt: MapFitAttempt, options: CameraOptions) {
            // A result is applicable only to this intent, layout and exact proposal nonce.
            commandView.post applyOnMain@{
                if (!stillCurrent() || !refinement.isCurrent(attempt)) return@applyOnMain
                // Preserve all SDK fields, including the original Point, anchor and vertical field of view.
                val nativeOptions = options.toBuilder().padding(absolutePadding)
                    .bearing(options.bearing ?: state.bearing).pitch(options.pitch ?: state.pitch).build()
                val center = nativeOptions.center
                val zoom = nativeOptions.zoom
                val target = runCatching {
                    if (center == null || zoom == null) null else MapCameraPosition(
                        canonicalLongitude(center.longitude()),center.latitude(),zoom,
                        nativeOptions.bearing ?: state.bearing,nativeOptions.pitch ?: state.pitch,padding)
                }.getOrNull()
                if (target == null) { reject(MapFitFailure.INVALID_MEASUREMENT); return@applyOnMain }
                if (!cameraGate.resolve(ticket,target)) return@applyOnMain
                cameraEvent(ticket,MapCameraCommandStage.OPTIONS_CALCULATED,target)
                commandView.viewport.idle()
                if (!stillCurrent() || !refinement.isCurrent(attempt)) return@applyOnMain
                val key = listOf(map,nativeStyle,commandView,width,height,layoutRevision,
                    nativeOptions,command.coordinates,fitRelief)
                if (!cameraGate.beginNativeFitApplication(ticket,map.cameraState.toMapCameraPosition(),key,
                        System.currentTimeMillis(),::stillCurrent)) return@applyOnMain
                if (window.firstFrameFence == null)
                    window.firstFrameFence = nativeRestoreEvents.fence(ticket.submittedAtMs)
                var returnedNormally = false
                var readbackAfterSetter: MapCameraPosition? = null
                try {
                    map.setCamera(nativeOptions)
                    // setCamera is synchronous. Read the same map immediately: a constrained
                    // repeat can be a native no-op and need not produce CameraChanged.
                    if (stillCurrent() && refinement.isCurrent(attempt))
                        readbackAfterSetter = map.cameraState.toMapCameraPosition()
                    returnedNormally = true
                } catch (_: RuntimeException) {
                    // Only a fixed failure code escapes; an SDK exception may include credentials.
                } finally { cameraGate.endNativeFitApplication(ticket,returnedNormally,readbackAfterSetter) }
                if (!returnedNormally) { reject(MapFitFailure.INVALID_MEASUREMENT); return@applyOnMain }
                if (stillCurrent() && refinement.applied(attempt) && refinement.awaitingResources) {
                    // A constrained native no-op still needs a real first frame and Idle.
                    map.triggerRepaint()
                }
                observeCameraCommand()
            }
        }
        fun propose(attempt: MapFitAttempt) {
            if (!stillCurrent() || !refinement.isCurrent(attempt)) return
            if (command.coordinates.distinct().size == 1) {
                // Keep the existing single-point FIT policy; POINT commands are independent.
                applyCalculated(attempt,CameraOptions.Builder().center(points.first())
                    .zoom(minOf(17.0,attempt.maxZoom ?: 17.0)).bearing(state.bearing).pitch(state.pitch)
                    .padding(absolutePadding).build())
            } else {
                val extra = attempt.coordinatesPadding
                val coordinatesPadding = if (extra == MapCameraPadding()) null
                    else EdgeInsets(extra.top,extra.left,extra.bottom,extra.right)
                // Only measured coordinate padding is added. UI padding stays absolute and unchanged.
                try {
                    map.cameraForCoordinates(points,camera,coordinatesPadding,attempt.maxZoom,null) { options ->
                        applyCalculated(attempt,options)
                    }
                } catch (_: RuntimeException) { reject(MapFitFailure.INVALID_MEASUREMENT) }
            }
        }
        window = IssuedFrameFitWindow(ticket,refinement,::stillCurrent,::propose,::reject)
        issuedFrameFitWindow = window
        // Loading remains interactive and cancellable; only native events open refinement.
        readyPublished = false
        publishReticle()
        diagnosticCallback(MapRenderDiagnostic.AWAITING_RESOURCES)
        statusCallback(MapRenderStatus.Loading(currentStyle.id))
        propose(refinement.initialAttempt)
    }

    fun publishViewport() {
        val map = sdkMap ?: return
        val view = sdkMapView
        val publication = runCatching {
            val state = map.cameraState
            val options = CameraOptions.Builder().center(state.center).zoom(state.zoom)
                .bearing(state.bearing).pitch(state.pitch).padding(state.padding).build()
            val bounds = map.coordinateBoundsForCamera(options)
            fun longitude(value: Double) = if (value in -180.0..180.0) value else canonicalLongitude(value)
            val corners = visibleViewportCorners(view?.width ?: 0, view?.height ?: 0, currentVisibleInsets)
            val footprint = if (!readyPublished) null else projectVisibleFootprint(corners) { pixel ->
                val hit = map.coordinateInfoForPixel(pixel)
                if (hit.isOnSurface) MapCoordinate(canonicalLongitude(hit.coordinate.longitude()), hit.coordinate.latitude()) else null
            }
            MapViewport(MapBounds(longitude(bounds.southwest.longitude()), bounds.southwest.latitude(),
                longitude(bounds.northeast.longitude()), bounds.northeast.latitude()),
                MapCameraPosition(canonicalLongitude(state.center.longitude()), state.center.latitude(),
                    state.zoom, state.bearing, state.pitch, state.padding.toMapCameraPadding()), footprint)
        }.getOrNull() ?: return
        if (publication != lastPublishedViewport) {
            lastPublishedViewport = publication
            viewportCallback(publication)
        }
    }

    fun rejectTerrainControl(diagnostic: MapRenderDiagnostic) {
        closeFrameFit(resourceFailed = true)
        nativeRestoration.cancel();nativeRestoreDeadline?.cancel();nativeRestoreDeadline=null
        terrainControlFailed = true
        readyPublished = false
        diagnosticCallback(diagnostic)
        statusCallback(MapRenderStatus.Failed(MapFailureReason.TERRAIN_CONTROL))
    }

    /** Returns true when this call starts a new DEM resource-loading cycle. */
    fun applyRequestedRelief(): Boolean {
        val map = sdkMap ?: return false
        val nativeStyle = map.style ?: return false
        if (!currentStyle.matchesNativeStyleUri(nativeStyle.styleURI) || !nativeStyle.isStyleLoaded()) return false
        val request = requestedRelief
        // A failed command also counts as attempted: only an explicit host retry,
        // new style or changed request can retry it. Frame events never issue commands.
        if (lastTerrainStyle === nativeStyle && lastTerrainRequest == request) return false
        val needsDemResources = request.enabled &&
            (lastTerrainStyle !== nativeStyle || lastTerrainRequest?.enabled != true)
        lastTerrainStyle = nativeStyle
        lastTerrainRequest = request
        terrainControlFailed = false
        acknowledgedRelief = null
        readyPublished = false

        // A flat local style may finish rendering before this UI callback runs.
        // Read back the absence of terrain: issuing setStyleTerrain(null) here
        // would invent a new resource barrier without changing native resources.
        val currentTerrainSource = nativeStyle.getStyleTerrainProperty("source").value.contents as? String
        if (!request.enabled && currentTerrainSource.isNullOrBlank()) {
            resourceCycle.recordTerrainApplication(resourceGeneration, System.currentTimeMillis(), changed = false)
            return false
        }

        val terrainValue = if (request.enabled) {
            val demUrl = currentStyle.demSourceUrl
            if (demUrl == null) {
                rejectTerrainControl(MapRenderDiagnostic.DEM_UNAVAILABLE)
                return false
            }
            if (nativeStyle.styleSourceExists(DEM_SOURCE_ID)) {
                val info = nativeStyle.styleSources.firstOrNull { it.id == DEM_SOURCE_ID }
                val existingUrl = nativeStyle.getStyleSourceProperty(DEM_SOURCE_ID, "url").value.contents as? String
                if (info?.type != "raster-dem" || existingUrl != demUrl) {
                    rejectTerrainControl(MapRenderDiagnostic.DEM_SOURCE_INVALID)
                    return false
                }
            } else {
                val source = Value(hashMapOf(
                    "type" to Value("raster-dem"),
                    "url" to Value(demUrl),
                    "tileSize" to Value(512L),
                ))
                if (nativeStyle.addStyleSource(DEM_SOURCE_ID, source).isError) {
                    rejectTerrainControl(MapRenderDiagnostic.DEM_REGISTRATION_REJECTED)
                    return false
                }
            }
            Value(hashMapOf(
                "source" to Value(DEM_SOURCE_ID),
                "exaggeration" to Value(request.exaggeration),
            ))
        } else Value.nullValue()
        if (map.setStyleTerrain(terrainValue).isError) {
            rejectTerrainControl(MapRenderDiagnostic.TERRAIN_UPDATE_REJECTED)
            return false
        }
        val terrainAppliedAtMs=System.currentTimeMillis()
        nativeRestoreEvents.resourcesAfterMs=terrainAppliedAtMs
        resourceCycle.recordTerrainApplication(resourceGeneration, terrainAppliedAtMs, changed = true)
        return needsDemResources
    }

    fun acknowledgeRelief() {
        if (terrainControlFailed || frameFitFailure != null) return
        val prior = acknowledgedRelief
        if (prior == null || !reliefMatches(prior, requestedRelief)) {
            val map = sdkMap
            if (map == null) {
                diagnosticCallback(MapRenderDiagnostic.SDK_MAP_ABSENT)
                return
            }
            val nativeStyle = map.style
            if (nativeStyle == null) {
                diagnosticCallback(MapRenderDiagnostic.STYLE_ABSENT)
                return
            }
            if (!currentStyle.matchesNativeStyleUri(nativeStyle.styleURI)) {
                diagnosticCallback(MapRenderDiagnostic.STYLE_URI_MISMATCH)
                return
            }
            if (!nativeStyle.isStyleLoaded()) {
                diagnosticCallback(MapRenderDiagnostic.STYLE_NOT_READY)
                return
            }
            val sourceId = nativeStyle.getStyleTerrainProperty("source").value.contents as? String
            val actual = if (sourceId.isNullOrBlank() && !requestedRelief.enabled) {
                MapRelief(enabled = false) // no elevation multiplication when terrain is disabled
            } else {
                if (sourceId != DEM_SOURCE_ID) {
                    diagnosticCallback(MapRenderDiagnostic.TERRAIN_SOURCE_MISMATCH)
                    return
                }
                if (!nativeStyle.styleSourceExists(DEM_SOURCE_ID)) {
                    diagnosticCallback(MapRenderDiagnostic.DEM_NOT_REGISTERED)
                    return
                }
                val actualUrl = nativeStyle.getStyleSourceProperty(DEM_SOURCE_ID, "url").value.contents as? String
                if (actualUrl.isNullOrBlank()) {
                    diagnosticCallback(MapRenderDiagnostic.DEM_URL_UNAVAILABLE)
                    return
                }
                if (actualUrl != currentStyle.demSourceUrl) {
                    diagnosticCallback(MapRenderDiagnostic.DEM_URL_MISMATCH)
                    return
                }
                val actualExaggeration = (nativeStyle.getStyleTerrainProperty("exaggeration")
                    .value.contents as? Number)?.toDouble()
                if (actualExaggeration == null || !actualExaggeration.isFinite() || actualExaggeration !in 1.0..3.0) {
                    diagnosticCallback(MapRenderDiagnostic.EXAGGERATION_UNREADABLE)
                    return
                }
                MapRelief(enabled = true, exaggeration = actualExaggeration)
            }
            if (!reliefMatches(actual, requestedRelief)) {
                diagnosticCallback(MapRenderDiagnostic.EXAGGERATION_PENDING)
                return
            }
            acknowledgedRelief = actual
            reliefCallback(actual)
        }
        when {
            issuedFrameFitWindow?.refinement?.awaitingResources == true ->
                diagnosticCallback(MapRenderDiagnostic.AWAITING_RESOURCES)
            !resourceCycle.isComplete(resourceGeneration) -> diagnosticCallback(MapRenderDiagnostic.AWAITING_RESOURCES)
            !boundsAccepted -> diagnosticCallback(MapRenderDiagnostic.AWAITING_CAMERA_BOUNDS)
            resourceFailure != null -> diagnosticCallback(MapRenderDiagnostic.RESOURCE_FAILURE)
            frameStatusReconciliationPending &&
                (newerCameraIntention() || !nativeRestoreEvents.readyForPose() ||
                    frameStatusReconciliationFence?.let(nativeRestoreEvents::readyAfter) == false) ->
                diagnosticCallback(MapRenderDiagnostic.AWAITING_RESOURCES)
            else -> {
                if (!restoreNativePose()) return
                frameStatusReconciliationPending = false
                frameStatusReconciliationFence = null
                diagnosticCallback(MapRenderDiagnostic.READY)
                if (!readyPublished) {
                    readyPublished = true
                    statusCallback(MapRenderStatus.Ready(currentStyle.id))
                }
                publishReticle()
                publishObservedCamera()
            }
        }
    }

    LaunchedEffect(sdkMap, relief) {
        if (issuedFrameFitWindow?.stillCurrent?.invoke() == false ||
            (frameFitFailure != null && lastTerrainRequest != requestedRelief))
            closeFrameFit(rearmForContext = true)
        acknowledgedRelief = null
        readyPublished = false
        applyRequestedRelief()
        acknowledgeRelief()
        applyFrameCommand()
    }
    LaunchedEffect(sdkMap, overlays, density, fontScale) {
        querySerial++
        pendingQuery?.cancel()
        applyOverlays()
    }
    LaunchedEffect(sdkMap, frameCommand, frameStatusReconciliationPending) {
        val canceledLoadingFit = currentFrameCommand == null && issuedFrameFitWindow != null && !readyPublished
        if (issuedFrameFitWindow?.stillCurrent?.invoke() == false)
            closeFrameFit(rearmForContext = currentFrameCommand != null)
        applyFrameCommand()
        // Reconcile only a canceled Loading; a replacement or a real failure keeps its own status.
        if (alive && frameStatusReconciliationPending && issuedFrameFitWindow == null &&
            frameFitFailure == null && !terrainControlFailed && resourceFailure == null && !newerCameraIntention()) {
            acknowledgeRelief()
            if (canceledLoadingFit && frameStatusReconciliationPending) sdkMap?.triggerRepaint()
        }
    }
    LaunchedEffect(sdkMap, reticlePositionPx) { publishReticle() }
    LaunchedEffect(sdkMap, sdkMapView, visibleInsets, density, layoutDirection, readyPublished) { publishViewport() }
    LaunchedEffect(sdkMap, cameraCommand?.id) { applyPointCommand() }
    LaunchedEffect(sdkMap, visibleInsets, density, relief) {
        val context=poseContext()
        if (context != null && nativeRestoration.pending && !nativeRestoration.compatible(context))
            cancelNativeRestoration()
        acknowledgeRelief()
    }
    fun onNativeMapLoaded(event: com.mapbox.maps.MapLoaded) {
        if (sdkMap?.style?.styleURI?.let(currentStyle::matchesNativeStyleUri) == true) {
            applyOverlays()
            val startedDemLoad = applyRequestedRelief()
            if (!startedDemLoad && resourceCycle.acceptNativeResourceEvent(resourceGeneration, event.timeInterval.end.time))
                resourceFailure = null
            applyFrameCommand()
            acknowledgeRelief()
        }
    }

    fun onNativeMapIdle(event: com.mapbox.maps.MapIdle, idleSequence: Long) {
        val nativeStyle = sdkMap?.style
        if (!terrainControlFailed && resourceFailure == null && nativeStyle != null &&
            currentStyle.matchesNativeStyleUri(nativeStyle.styleURI) && nativeStyle.isStyleLoaded() &&
            lastTerrainStyle === nativeStyle && lastTerrainRequest == requestedRelief) {
            resourceCycle.acceptNativeResourceEvent(resourceGeneration, event.timestamp.time)
        }
        acknowledgeRelief()
        applyOverlays()
        applyFrameCommand()
        applyPointCommand()
        cameraGate.acceptNativeIdle(idleSequence, event.timestamp.time)
        val wasAwaitingResources = issuedFrameFitWindow?.refinement?.awaitingResources == true
        observeCameraCommand()
        if (wasAwaitingResources && issuedFrameFitWindow?.refinement?.awaitingResources != true)
            acknowledgeRelief()
        publishViewport()
    }

    fun onNativeResourceError(error: com.mapbox.maps.MapLoadingError) {
        closeFrameFit(resourceFailed = true)
        nativeRestoration.cancel()
        nativeRestoreDeadline?.cancel();nativeRestoreDeadline=null
        if (error.type == MapLoadingErrorType.SOURCE && error.sourceId == MAP_OVERLAY_SOURCE_ID) {
            sdkMap?.style?.let { overlayRenderer.onSourceError(it,currentScene) }?.let(overlayCallback)
        }
        val reason = when (error.type) {
            MapLoadingErrorType.STYLE -> MapFailureReason.STYLE
            MapLoadingErrorType.SOURCE -> MapFailureReason.SOURCE
            MapLoadingErrorType.TILE -> MapFailureReason.TILE
            MapLoadingErrorType.GLYPHS -> MapFailureReason.GLYPHS
            MapLoadingErrorType.SPRITE -> MapFailureReason.SPRITE
        }
        resourceFailure = reason
        readyPublished = false
        publishReticle()
        diagnosticCallback(MapRenderDiagnostic.RESOURCE_FAILURE)
        // SDK URLs and error strings may contain credentials; only expose fixed codes.
        statusCallback(MapRenderStatus.Failed(reason))
    }

    MapboxMap(
        modifier = modifier,
        composeMapInitOptions = mapInitOptions,
        mapViewportState = viewport,
        mapState = mapState,
        onMapClickListener = click@ { point ->
            val coordinate = runCatching { MapCoordinate(canonicalLongitude(point.longitude()), point.latitude()) }.getOrNull()
            // Measurement/placement tools cannot lose a rapid tap to cancellation of an older rendered query.
            if (coordinate != null && alive && coordinateTapCallback(coordinate)) {
                querySerial++
                pendingQuery?.cancel()
                return@click true
            }
            val map = sdkMap
            val scene = currentScene
            if (coordinate != null && map != null && overlayRenderer.appliedScene === scene) {
                val ticket = ++querySerial
                pendingQuery?.cancel()
                val pixel = map.pixelForCoordinate(point)
                val radius = 12.0 * density
                pendingQuery = map.queryRenderedFeatures(RenderedQueryGeometry(ScreenBox(
                    ScreenCoordinate(pixel.x - radius, pixel.y - radius),
                    ScreenCoordinate(pixel.x + radius, pixel.y + radius))),
                    RenderedQueryOptions(overlayRenderer.queryLayerIds,
                        Value(listOf(Value("=="), Value(listOf(Value("get"), Value("gs_selectable"))), Value(true))))) { result ->
                    callbackScope.launch {
                        if (alive && ticket == querySerial && scene === currentScene && overlayRenderer.appliedScene === scene) {
                            if (result.isError) overlayCallback(MapOverlayStatus.Failed(MapOverlayFailure.QUERY))
                            else {
                                val key = result.value.orEmpty().firstNotNullOfOrNull { hit ->
                                    val feature = hit.queriedFeature.feature
                                    val layer = feature.getStringProperty(OVERLAY_LAYER_PROPERTY)
                                    val id = feature.getStringProperty(OVERLAY_FEATURE_PROPERTY)
                                    if (layer.isNullOrBlank() || id.isNullOrBlank()) null
                                    else resolveOverlayHit(scene, currentScene, MapFeatureKey(layer, id))
                                }
                                tapCallback(MapOverlayTap(coordinate, key))
                            }
                        }
                    }
                }
            }
            true
        },
        onMapLongClickListener = { point ->
            querySerial++
            pendingQuery?.cancel()
            runCatching { MapCoordinate(canonicalLongitude(point.longitude()), point.latitude()) }
                .getOrNull()?.let(longPressCallback)
            true
        },
        compass = { Compass(contentPadding = contentPadding, alignment = Alignment.TopEnd) },
        logo = { Logo(contentPadding = contentPadding, alignment = Alignment.BottomStart) },
        attribution = { Attribution(modifier = Modifier.padding(start = ATTRIBUTION_AFTER_LOGO)
                .size(24.dp).background(Color.Black.copy(alpha = .55f),CircleShape).padding(3.dp),
            iconColor = Color.White, contentPadding = contentPadding, alignment = Alignment.BottomStart) },
        scaleBar = {
            if (scaleBarVisible) ScaleBar(modifier = Modifier.padding(bottom = SCALE_ABOVE_LOGO),
                contentPadding = contentPadding, alignment = Alignment.BottomStart,
                distanceUnits = DistanceUnits.METRIC)
        },
        // ComposeMapInitOptions creates the native map with styleUri=null. Loading
        // starts only after the observers below are synchronously registered.
        style = { if (observersReady) GenericStyle(style = style.document, styleState = styleState) },
    ) {
        MapEffect(Unit) { mapView ->
            val map = mapView.mapboxMap
            val seed=initialCamera.toCameraOptions().toBuilder()
            initialCamera.nativePose?.takeIf {it.belongsTo(initialCamera)}?.let {
                if (map.cameraState.verticalFov != it.context.verticalFov) seed.verticalFov(it.context.verticalFov)
            }
            map.setCamera(seed.build())
            sdkMap = map
            sdkMapView = mapView
            val layoutListener = android.view.View.OnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
                if (alive && sdkMap === map) {
                    if (l != oldL || t != oldT || r != oldR || b != oldB) {
                        nativeLayoutRevision++
                        closeFrameFit(rearmForContext = true)
                        mapView.post { if (alive && sdkMap === map) applyFrameCommand() }
                    }
                    publishReticle(); publishViewport()
                }
            }
            mapView.addOnLayoutChangeListener(layoutListener)
            val removeCameraGestures = observeMapCameraGestures(mapView, ::cameraUserGesture)
            val result = map.setBounds(CameraBoundsOptions.Builder()
                .minPitch(0.0).maxPitch(60.0).build())
            boundsAccepted = !result.isError
            if (!boundsAccepted) {
                diagnosticCallback(MapRenderDiagnostic.CAMERA_BOUNDS_REJECTED)
                statusCallback(MapRenderStatus.Failed(MapFailureReason.CAMERA_BOUNDS))
            }
            // MapState's event flows are cold callbackFlow/flatMapLatest streams.
            // Subscribing after a fast local style load can permanently miss Idle.
            // Keep SDK camera/gesture observation; register resource observers
            // before activating the one GenericStyle owner instead of reloading it.
            fun dispatch(block: () -> Unit) {
                callbackScope.launch { if (alive && sdkMap === map) block() }
            }
            val subscriptions = listOf(
                map.subscribeStyleLoaded { dispatch {
                    applyRequestedRelief(); applyOverlays(); applyFrameCommand(); applyPointCommand(); acknowledgeRelief()
                } },
                map.subscribeMapLoaded { event -> dispatch { onNativeMapLoaded(event) } },
                map.subscribeMapIdle { event ->
                    val sequence = cameraGate.nextNativeIdleSequence()
                    if (alive && sdkMap === map) {
                        nativeRestoreEvents.idleSequence=sequence
                        nativeRestoreEvents.idleAtMs=event.timestamp.time
                    }
                    dispatch { onNativeMapIdle(event, sequence) }
                },
                map.subscribeCameraChanged { event ->
                    // The SDK contract makes this synchronous. Do not dispatch: the
                    // application window binds this read-back to the specific setter.
                    if (alive && sdkMap === map) {
                        nativeRestoreEvents.cameraAtMs=event.timestamp.time
                        nativeRestoreEvents.cameraSequence++
                        cameraGate.captureNativeCamera(event.cameraState.toMapCameraPosition())
                    }
                },
                map.subscribeMapLoadingError { event -> dispatch { onNativeResourceError(event) } },
                map.subscribeSourceDataLoaded { event -> dispatch {
                    val nativeStyle = map.style
                    if (nativeStyle != null && currentStyle.matchesNativeStyleUri(nativeStyle.styleURI)) {
                        overlayRenderer.onSourceDataLoaded(nativeStyle,currentScene,event.sourceId,event.dataId)?.let {
                            overlayCallback(it)
                            map.triggerRepaint()
                        }
                    }
                } },
                map.subscribeRenderFrameFinished { event ->
                    if (alive && sdkMap === map && event.renderMode == com.mapbox.maps.RenderModeType.FULL) {
                        nativeRestoreEvents.fullSequence++
                        nativeRestoreEvents.fullAtMs=event.timeInterval.begin.time
                    }
                    dispatch {
                    observeCameraCommand()
                    acknowledgeRelief()
                    val nativeStyle = map.style
                    if (nativeStyle != null && currentStyle.matchesNativeStyleUri(nativeStyle.styleURI)) {
                        val full = event.renderMode == com.mapbox.maps.RenderModeType.FULL
                        overlayRenderer.onRenderFrameFinished(nativeStyle,currentScene,event.timeInterval.begin.time,full)?.let(overlayCallback)
                        // A frame already in flight at visibility change cannot satisfy the new scene.
                        if (full && overlayRenderer.awaitingRenderedFrame(nativeStyle,currentScene)) map.triggerRepaint()
                    }
                } },
                map.subscribeCameraChangedCoalesced { dispatch {
                    observeCameraCommand();publishObservedCamera();publishReticle();publishViewport()
                } },
            )
            val styleStartedAtMs=System.currentTimeMillis()
            nativeRestoreEvents.resourcesAfterMs=styleStartedAtMs
            resourceCycle.beginStyleLoad(resourceGeneration, styleStartedAtMs)
            observersReady = true
            try { awaitCancellation() }
            finally {
                nativeRestoreDeadline?.cancel();nativeRestoration.cancel()
                mapView.removeOnLayoutChangeListener(layoutListener)
                removeCameraGestures()
                cameraGate.invalidate()
                subscriptions.forEach { it.cancel() }
                reticleCallback(null)
            }
        }
    }
}

private fun MapCameraPosition.toCameraOptions(): CameraOptions = CameraOptions.Builder()
    .center(Point.fromLngLat(longitude, latitude)).zoom(zoom).bearing(bearing).pitch(pitch)
    .padding(EdgeInsets(padding.top, padding.left, padding.bottom, padding.right)).build()

private fun EdgeInsets.toMapCameraPadding(): MapCameraPadding = MapCameraPadding(top, left, bottom, right)

/** Canonical GeoJSON longitude while the SDK camera may pan through wrapped worlds. */
internal fun canonicalLongitude(longitude: Double): Double =
    ((longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0

private const val DEM_SOURCE_ID = "geosylva-terrain-dem"
private const val MAP_ANTIALIASING_SAMPLES = 2

private val SCALE_ABOVE_LOGO = 36.dp
private val ATTRIBUTION_AFTER_LOGO = 96.dp


private fun com.mapbox.maps.CameraState.toMapCameraPosition(): MapCameraPosition = MapCameraPosition(
    canonicalLongitude(center.longitude()),center.latitude(),zoom,bearing,pitch,padding.toMapCameraPadding())

/** One FIT intent, not a viewport owner or terrain cache. All camera authority remains in the gate. */
private class IssuedFrameFitWindow(
    val ticket: MapCameraCommandGate.Ticket,
    val refinement: MapCameraFitRefinement,
    val stillCurrent: () -> Boolean,
    val propose: (MapFitAttempt) -> Unit,
    val reject: (MapFitFailure) -> Unit,
) {
    var deadline: Job? = null
    var firstFrameFence: NativeRestoreFence? = null
    fun close() { refinement.cancel(); deadline?.cancel() }
}
