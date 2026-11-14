package com.forestry.counter.ui

import android.view.View
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forestry.counter.presentation.components.maps.MapModalStatusBarEffect
import com.forestry.counter.presentation.components.maps.rememberMapModalStatusBarPolicy
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Activity/Dialog windows; verifies inheritance, not photographic image brightness or Mapbox rendering. */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
class MapModalStatusBarTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var previousStatusIcons: Boolean? = null
    @After fun restoreHost() {
        previousStatusIcons?.let { old -> compose.runOnUiThread {
            WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView).isAppearanceLightStatusBars = old
        } }
    }
    @Test fun dark_map_host_keeps_light_status_icons_in_its_real_modal_without_changing_navigation() = verifyInheritance(false)
    @Test fun light_plan_host_keeps_dark_status_icons_in_its_real_modal_without_changing_navigation() = verifyInheritance(true)

    private fun verifyInheritance(darkStatusIcons: Boolean) {
        val modal = AtomicReference<Pair<Window, View>?>()
        val navigationBefore = AtomicReference<Boolean?>()
        compose.runOnUiThread {
            val host = WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
            previousStatusIcons = host.isAppearanceLightStatusBars
            host.isAppearanceLightStatusBars = darkStatusIcons
        }
        compose.setContent {
            MaterialTheme {
                val policy = rememberMapModalStatusBarPolicy()
                ModalBottomSheet(onDismissRequest = {}, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                    val actualView = LocalView.current
                    SideEffect {
                        var parent = actualView.parent
                        while (parent != null && parent !is DialogWindowProvider) parent = parent.parent
                        val window = (parent as? DialogWindowProvider)?.window
                        if (window != null) {
                            navigationBefore.compareAndSet(null, WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars)
                            modal.set(window to actualView)
                        }
                    }
                    MapModalStatusBarEffect(policy)
                    Text("TEST actual modal window")
                }
            }
        }
        compose.waitUntil(10_000) {
            var matched = false
            compose.runOnUiThread {
                modal.get()?.let { (window, view) -> matched = view.isAttachedToWindow && view.rootView.hasWindowFocus() &&
                    WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars == darkStatusIcons }
            }
            matched
        }
        val (window, view) = checkNotNull(modal.get())
        assertNotSame(compose.activity.window, window)
        assertNotSame(compose.activity.window.decorView, view.rootView)
        awaitMapWindowPresentation(view.rootView)
        compose.runOnUiThread {
            val target = WindowCompat.getInsetsController(window, window.decorView)
            assertEquals(darkStatusIcons, target.isAppearanceLightStatusBars)
            assertEquals(navigationBefore.get(), target.isAppearanceLightNavigationBars)
            assertEquals(darkStatusIcons, WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView).isAppearanceLightStatusBars)
        }
    }
}
