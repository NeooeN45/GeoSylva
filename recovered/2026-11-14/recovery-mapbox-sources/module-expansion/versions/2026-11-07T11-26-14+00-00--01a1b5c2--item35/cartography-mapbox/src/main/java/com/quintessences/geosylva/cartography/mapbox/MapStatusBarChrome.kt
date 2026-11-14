package com.quintessences.geosylva.cartography.mapbox

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.quintessences.geosylva.cartography.R

private fun statusBarPolicy(window: Window, view: View): MapStatusBarPolicy {
    val root = view.rootView
    return (root.getTag(R.id.geosylva_map_status_bar_policy) as? MapStatusBarPolicy) ?: MapStatusBarPolicy(
        WindowCompat.getInsetsController(window,view).isAppearanceLightStatusBars
    ).also { root.setTag(R.id.geosylva_map_status_bar_policy,it) }
}

/** Theme calls this before its content; an active photographic canvas has icon priority. */
fun applyThemedMapStatusBarAppearance(window: Window, view: View, preferDarkIcons: Boolean) {
    val policy = statusBarPolicy(window,view)
    policy.themeChanged(preferDarkIcons)
    WindowCompat.getInsetsController(window,view).isAppearanceLightStatusBars = policy.darkIcons
}

private fun Context.mapActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val next = current.baseContext
        if (next === current) return null
        current = next
    }
    return current as? Activity
}

/** Non-interactive, local scrim; no system-bar color or navigation-bar appearance is changed. */
@Composable
internal fun PhotographicMapStatusBarScrim(modifier: Modifier = Modifier) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = view.context.mapActivity()?.window ?: return
    val inset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    if (inset <= 0.dp) return
    val density = LocalDensity.current
    var coversStatusBar by remember(view) { mutableStateOf(false) }
    val policy = remember(window,view) { statusBarPolicy(window,view) }
    val owner = remember { Any() }
    DisposableEffect(policy,coversStatusBar) {
        if (coversStatusBar) policy.acquire(owner)
        WindowCompat.getInsetsController(window,view).isAppearanceLightStatusBars = policy.darkIcons
        onDispose {
            policy.release(owner)
            WindowCompat.getInsetsController(window,view).isAppearanceLightStatusBars = policy.darkIcons
        }
    }
    SideEffect {
        if (coversStatusBar) WindowCompat.getInsetsController(window,view).isAppearanceLightStatusBars = policy.darkIcons
    }
    val gradient = Brush.verticalGradient(0f to Color.Black.copy(alpha = .70f),
        .70f to Color.Black.copy(alpha = .55f),1f to Color.Transparent)
    Box(modifier.fillMaxWidth().height(inset+16.dp).onGloballyPositioned { coordinates ->
        val position = coordinates.positionInWindow()
        coversStatusBar = mapCoversStatusBar(position.x,position.y,coordinates.size.width,view.rootView.width,
            with(density) { inset.roundToPx() })
    }.then(if (coversStatusBar) Modifier.background(gradient) else Modifier))
}
