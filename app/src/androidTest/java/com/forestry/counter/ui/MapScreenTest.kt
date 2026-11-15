package com.forestry.counter.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.BuildConfig
import com.forestry.counter.R
import com.forestry.counter.data.preferences.ThemeMode
import com.forestry.counter.presentation.components.maps.MapOfflineConnectivityBadge
import com.forestry.counter.presentation.theme.ForestryCounterTheme
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.gestures.gestures
import com.quintessences.geosylva.cartography.mapbox.*
import com.quintessences.geosylva.cartography.ui.*
import com.quintessences.geosylva.cartography.ui.R as MapUiR
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Vrais composants partagés et vrai SDK Mapbox, sur un style V8 local de laboratoire.
 * Aucun dossier utilisateur n'est ouvert ou modifié. Les entrées TEST appartiennent
 * à l'hôte de test ; elles ne remplacent ni les widgets, ni le rendu, ni les gestes.
 * Les callbacks de localisation sont vérifiés, pas la réception GPS du téléphone.
 */
@RunWith(AndroidJUnit4::class)
class MapScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var restoreWindow: (() -> Unit)? = null
    private val state = mutableStateOf(workspace("TEST · Parcelle A"))
    private val scene = mutableStateOf(MapOverlayScene("TEST-scope-A", 1L, emptyList()))
    private val offline = mutableStateOf(false)
    private val status = AtomicReference<MapRenderStatus>(MapRenderStatus.ConfigurationRequired)
    private val overlay = AtomicReference<MapOverlayStatus?>(null)
    private val camera = AtomicReference<MapCameraPosition?>(null)
    private val tap = AtomicReference<MapOverlayTap?>(null)
    private val locateRequests = AtomicInteger()
    private val backRequests = AtomicInteger()
    private val contextRequests = AtomicInteger()

    @After fun restoreWindowConfiguration() { restoreWindow?.invoke() }

    @Test fun mapScreen_displays_real_native_pixels_after_loading() {
        show()
        awaitNative()
        compose.onNodeWithTag(CANVAS).assertIsDisplayed()
        compose.onNodeWithText("TEST · Parcelle A").assertIsDisplayed()
        awaitMapChromePresentation(compose.activity)
        val sample = IntArray(2)
        compose.runOnUiThread {
            val map = nativeMap(compose.activity.window.decorView)
            map.getLocationOnScreen(sample)
            sample[0] += map.width / 2
            sample[1] += map.height / 2
        }
        val image = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            // Le fond bleu-gris distingue le vrai rendu d'un écran blanc ou noir.
            val pixel = image.getPixel(sample[0], sample[1])
            assertTrue("Le fond natif local n'est pas présenté", abs(((pixel shr 16) and 255) - 49) <= 12)
            assertTrue("Le fond natif local n'est pas présenté", abs(((pixel shr 8) and 255) - 67) <= 12)
            assertTrue("Le fond natif local n'est pas présenté", abs((pixel and 255) - 77) <= 12)
        } finally { image.recycle() }
    }

    @Test fun location_request_obeys_host_availability_and_never_claims_a_GPS_fix() {
        state.value = state.value.copy(locationEnabled = false)
        show()
        locate().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, locateRequests.get()); state.value = state.value.copy(locationEnabled = true) }
        locate().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, locateRequests.get()) }
    }

    @Test fun rendered_marker_is_hit_only_in_its_current_scope_and_cleared_on_scope_change() {
        val key = MapFeatureKey("TEST-trees", "TEST-tree-A")
        scene.value = MapOverlayScene("TEST-scope-A", 2L, listOf(MapOverlayFeature(key, Point.fromLngLat(LON, LAT),
            MapOverlayStyle(pointRadius = 15.0))))
        show()
        awaitScene("TEST-scope-A", 2L)
        compose.onNodeWithTag(CANVAS).performTouchInput { click(center) }
        compose.waitUntil(TIMEOUT) { tap.get()?.featureKey == key }
        compose.runOnIdle {
            tap.set(null)
            state.value = workspace("TEST · Parcelle B")
            scene.value = MapOverlayScene("TEST-scope-B", 1L, emptyList())
        }
        awaitScene("TEST-scope-B", 1L)
        compose.onNodeWithTag(CANVAS).performTouchInput { click(center) }
        compose.waitUntil(TIMEOUT) { tap.get() != null }
        compose.runOnIdle {
            assertNull(tap.get()?.featureKey)
        }
        compose.onNodeWithText("TEST · Parcelle A").assertDoesNotExist()
        compose.onNodeWithText("TEST · Parcelle B").assertIsDisplayed()
    }

    @Test fun real_map_commands_keep_accessible_targets_and_explicit_back_navigation() {
        show()
        locate().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        compose.onNodeWithContentDescription(label(MapUiR.string.gs_map_back)).assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(1, backRequests.get()) }
        compose.onNode(hasText("TEST · Parcelle A") and hasClickAction()).performClick()
        compose.runOnIdle { assertEquals(1, contextRequests.get()) }
    }

    @Test fun pinch_is_acknowledged_by_the_real_native_camera() {
        show()
        awaitNative()
        awaitMapChromePresentation(compose.activity)
        val before = checkNotNull(camera.get()).zoom
        val bounds = IntArray(4)
        compose.runOnUiThread {
            val view = nativeMap(compose.activity.window.decorView)
            assertTrue("Le SDK doit autoriser le zoom tactile", view.gestures.pinchToZoomEnabled)
            view.getLocationOnScreen(bounds)
            bounds[2] = view.width
            bounds[3] = view.height
        }
        // Le SDK Android natif reçoit des MotionEvent horodatés par l'horloge réelle.
        // L'horloge synthétique Compose ne rythme pas les détecteurs/animations natifs.
        pinchOutWithAndroidInput(bounds)
        try {
            compose.waitUntil(TIMEOUT) { camera.get()?.zoom?.let { it > before + 0.15 } == true }
        } catch (failure: ComposeTimeoutException) {
            var nativeZoom = Double.NaN
            compose.runOnUiThread { nativeZoom = nativeMap(compose.activity.window.decorView).mapboxMap.cameraState.zoom }
            throw AssertionError("Zoom initial=$before ; natif=$nativeZoom ; publié=${camera.get()?.zoom}", failure)
        }
    }

    private fun pinchOutWithAndroidInput(bounds: IntArray) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()
        val x = bounds[0] + bounds[2] / 2f
        val y = bounds[1] + bounds[3] / 2f
        val properties = Array(2) { id -> MotionEvent.PointerProperties().apply {
            this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER
        } }
        fun send(action: Int, count: Int, span: Float) {
            val coordinates = Array(count) { id -> MotionEvent.PointerCoords().apply {
                this.x = x + (if (id == 0) -span else span); this.y = y
                pressure = 1f; size = 1f
            } }
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, count,
                properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { assertTrue("Échec d'injection Android : action=$action, pointeurs=$count", automation.injectInputEvent(event, true)) }
            finally { event.recycle() }
        }
        val start = bounds[2] * 0.12f
        val end = bounds[2] * 0.32f
        var completed = false
        try {
            send(MotionEvent.ACTION_DOWN, 1, start)
            SystemClock.sleep(24)
            send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, start)
            for (step in 1..30) {
                SystemClock.sleep(20)
                send(MotionEvent.ACTION_MOVE, 2, start + (end - start) * step / 30f)
            }
            send(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, end)
            send(MotionEvent.ACTION_UP, 1, end)
            completed = true
        } finally {
            if (!completed) runCatching { send(MotionEvent.ACTION_CANCEL, 1, end) }
        }
    }

    @Test fun panel_back_closes_the_panel_without_leaving_the_current_scope() {
        show()
        compose.onNode(hasText(label(MapUiR.string.gs_map_layers)) and hasClickAction()).performClick()
        compose.onNodeWithContentDescription(label(MapUiR.string.gs_map_close)).assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription(label(MapUiR.string.gs_map_close)).assertDoesNotExist()
        compose.onNodeWithText("TEST · Parcelle A").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, backRequests.get()); assertNull(state.value.panel) }
    }

    @Test fun local_style_stays_renderable_with_the_real_offline_hint_without_claiming_downloaded_data() {
        offline.value = true
        show()
        awaitNative()
        compose.onNodeWithText(compose.activity.getString(R.string.mapbox_local_mode_title)).assertIsDisplayed()
        compose.onNodeWithTag(CANVAS).assertIsDisplayed()
        compose.runOnIdle { assertTrue(status.get() is MapRenderStatus.Ready) }
    }

    @Test fun location_command_exposes_its_real_semantic_click_action() {
        show()
        locate().performSemanticsAction(SemanticsActions.OnClick)
        compose.runOnIdle { assertEquals(1, locateRequests.get()) }
        compose.onNodeWithTag(CANVAS).assertIsDisplayed()
    }

    private fun show() {
        check(BuildConfig.MAPBOX_PUBLIC_ACCESS_TOKEN.startsWith("pk.")) {
            "Configurer un jeton public Mapbox local pour ces essais natifs ; aucune valeur n'est affichée."
        }
        restoreWindow = useFrenchMapTestWindow(compose.activity)
        compose.setContent {
            ForestryCounterTheme(themeMode = ThemeMode.LIGHT, dynamicColor = false) {
                MapWorkspace(state.value, actions, Modifier.fillMaxSize()) {
                    SharedMapboxCanvas(
                        accessToken = BuildConfig.MAPBOX_PUBLIC_ACCESS_TOKEN,
                        style = LOCAL_STYLE,
                        initialCamera = MapCameraPosition(LON, LAT, 15.0),
                        modifier = Modifier.fillMaxSize().testTag(CANVAS),
                        overlays = scene.value,
                        onStatusChanged = status::set,
                        onOverlayStatusChanged = overlay::set,
                        onCameraChanged = camera::set,
                        onMapTap = tap::set,
                        scaleBarVisible = state.value.panel == null,
                    )
                    if (offline.value) MapOfflineConnectivityBadge(Modifier.padding(top = 130.dp, start = 12.dp))
                }
            }
        }
    }

    private fun awaitNative() = compose.waitUntil(TIMEOUT) {
        status.get() is MapRenderStatus.Ready && camera.get() != null
    }
    private fun awaitScene(scope: String, revision: Long) = compose.waitUntil(TIMEOUT) {
        val applied = overlay.get() as? MapOverlayStatus.Applied
        status.get() is MapRenderStatus.Ready && applied?.scopeId == scope && applied.revision == revision
    }
    private fun locate() = compose.onNodeWithContentDescription(label(MapUiR.string.gs_map_position))
    private fun label(id: Int) = compose.activity.getString(id)
    private fun nativeMap(view: View): MapView {
        if (view is MapView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            runCatching { nativeMap(view.getChildAt(i)) }.getOrNull()?.let { return it }
        }
        error("Aucune vue Mapbox native dans la fenêtre de test")
    }
    private val actions = object : MapWorkspaceActions {
        override fun onBack() { backRequests.incrementAndGet() }
        override fun onChooseContext() { contextRequests.incrementAndGet() }
        override fun onPanelChanged(panel: MapPanel?) { state.value = state.value.copy(panel = panel) }
        override fun onLocate() { locateRequests.incrementAndGet() }
        override fun onTask(task: MapTask) { state.value = state.value.copy(selectedTask = task) }
        override fun onSelectBasemap(id: String) = Unit
        override fun onToggleLayer(id: String, visible: Boolean) = Unit
        override fun onAddLayer(id: String) = Unit
        override fun onPitchChange(degrees: Double) = Unit
        override fun onExaggerationChange(exaggeration: Double) = Unit
        override fun onFlatView(flat: Boolean) = Unit
        override fun onExplain(help: MapHelp) = Unit
    }

    companion object {
        private const val CANVAS = "TEST-map-native-canvas"
        private const val TIMEOUT = 30_000L
        private const val LON = 2.0
        private const val LAT = 47.0
        private fun workspace(title: String) = MapWorkspaceState(contextTitle = title,
            contextActionLabel = "TEST · Choisir le contexte", locationEnabled = true)
        private val LOCAL_STYLE = MapboxStyleSpec("TEST-local-v8", "TEST-local-v8", demSourceUrl = null,
            styleJson = """{"version":8,"sources":{},"layers":[{"id":"TEST-background","type":"background","paint":{"background-color":"#31434D"}}]}""")
    }
}
