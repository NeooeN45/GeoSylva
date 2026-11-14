package com.forestry.counter.presentation.components.maps

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.Window
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/** Retains a source window, never a captured theme/appearance boolean. */
internal class MapModalStatusBarPolicy internal constructor(internal val sourceWindow: Window)

/** Call in the map host before opening its ModalBottomSheet/Dialog content. */
@Composable
internal fun rememberMapModalStatusBarPolicy(): MapModalStatusBarPolicy? {
    val view = LocalView.current
    return remember(view) {
        if (view.isInEditMode) null
        else (view.context.mapHostActivity()?.window ?: view.mapDialogWindow())?.let(::MapModalStatusBarPolicy)
    }
}

/**
 * Call inside the actual modal content. The canvas already chose the source window's
 * contrast from its current basemap. Only status-icon appearance is inherited;
 * navigation icons/colors, scrim, geometry and the Activity window are untouched.
 */
@Composable
internal fun MapModalStatusBarEffect(policy: MapModalStatusBarPolicy?) {
    val view = LocalView.current
    val source = policy?.sourceWindow ?: return
    val target = view.mapDialogWindow() ?: return
    if (view.isInEditMode || target === source) return
    val applyAppearance: () -> Unit = remember(source, target) {
        {
            val darkIcons = WindowCompat.getInsetsController(source, source.decorView).isAppearanceLightStatusBars
            val targetController = WindowCompat.getInsetsController(target, target.decorView)
            if (targetController.isAppearanceLightStatusBars != darkIcons)
                targetController.isAppearanceLightStatusBars = darkIcons
        }
    }
    DisposableEffect(view, target, source) {
        val beforeDraw = ViewTreeObserver.OnPreDrawListener { applyAppearance(); true }
        val focus = ViewTreeObserver.OnWindowFocusChangeListener { focused -> if (focused) applyAppearance() }
        view.viewTreeObserver.addOnPreDrawListener(beforeDraw)
        view.viewTreeObserver.addOnWindowFocusChangeListener(focus)
        applyAppearance()
        onDispose {
            if (view.viewTreeObserver.isAlive) {
                view.viewTreeObserver.removeOnPreDrawListener(beforeDraw)
                view.viewTreeObserver.removeOnWindowFocusChangeListener(focus)
            }
            // The transient Dialog owns this appearance. Never restore a stale theme
            // into the host Activity while this modal is being dismissed.
        }
    }
    SideEffect { applyAppearance() }
}

private fun Context.mapHostActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val next = current.baseContext
        if (next === current) return null
        current = next
    }
    return current as? Activity
}

private fun View.mapDialogWindow(): Window? {
    if (this is DialogWindowProvider) return window
    var current = parent
    while (current != null) {
        if (current is DialogWindowProvider) return current.window
        current = current.parent
    }
    return null
}
