package com.forestry.counter.ui

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import java.util.concurrent.atomic.AtomicReference
import android.view.View
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import android.content.res.Configuration
import android.view.WindowManager
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.Locale

/**
 * Popups use the Activity's Android resources, not just Compose's LocalContext.
 * Match MainActivity's resize policy and restore this in-memory test configuration afterwards.
 */
@Suppress("DEPRECATION")
internal fun useFrenchMapTestWindow(activity: ComponentActivity, fontScale: Float = 1f): () -> Unit {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    lateinit var previous: Configuration
    var previousInputMode = 0
    instrumentation.runOnMainSync {
        previous = Configuration(activity.resources.configuration)
        previousInputMode = activity.window.attributes.softInputMode
        activity.resources.updateConfiguration(Configuration(previous).apply {
            setLocale(Locale.FRANCE)
            this.fontScale = fontScale
        }, activity.resources.displayMetrics)
        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        println("GEOSYLVA_MAP_TEST_WINDOW localeBefore=${previous.locales.toLanguageTags()} " +
            "localeAfter=${activity.resources.configuration.locales.toLanguageTags()} fontScale=$fontScale adjustResize=true")
    }
    return {
        instrumentation.runOnMainSync {
            activity.resources.updateConfiguration(previous, activity.resources.displayMetrics)
            activity.window.setSoftInputMode(previousInputMode)
        }
    }
}

/** Test-only presentation barrier, matching the verified workspace native capture harness. */
internal fun awaitMapChromePresentation(activity: ComponentActivity) = awaitMapWindowPresentation(activity.window.decorView)

/** The node must belong to the visible dialog/sheet. Uses its real Compose window owner on API26+. */
internal fun awaitMapNodeWindowPresentation(node: SemanticsNode) {
    val actualView = checkNotNull((node.root as? ViewRootForTest)?.view) { "The captured node has no Android view owner" }
    awaitMapWindowPresentation(actualView.rootView)
}

/** Observe an Android traversal in this exact window, including full-screen Compose Dialogs. */
internal fun awaitMapWindowPresentation(decor: View) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val presented = CountDownLatch(1)
    lateinit var drawn: ViewTreeObserver.OnDrawListener
    instrumentation.runOnMainSync {
        drawn = object : ViewTreeObserver.OnDrawListener {
            private var handled = false
            override fun onDraw() {
                if (handled) return
                handled = true
                decor.post {
                    if (decor.viewTreeObserver.isAlive) decor.viewTreeObserver.removeOnDrawListener(this)
                    decor.postOnAnimation { decor.postOnAnimation { presented.countDown() } }
                }
            }
        }
        decor.viewTreeObserver.addOnDrawListener(drawn)
        decor.postInvalidateOnAnimation()
    }
    try {
        assertTrue("Android UI draw did not settle", presented.await(10, TimeUnit.SECONDS))
    } finally {
        instrumentation.runOnMainSync {
            if (decor.viewTreeObserver.isAlive) decor.viewTreeObserver.removeOnDrawListener(drawn)
        }
    }
    instrumentation.uiAutomation.waitForIdle(1_000, 10_000)
    instrumentation.runOnMainSync {
        assertTrue("A system window would obscure this evidence", decor.hasWindowFocus())
    }
}

/** Only this test's bounded screenshots and public fixture metadata are written. */
internal fun saveMapTestEvidence(activity: ComponentActivity, directoryName: String, name: String, metadata: String) {
    require(directoryName.matches(Regex("[a-z0-9-]+")))
    require(name.matches(Regex("[a-z0-9-]+")))
    val directory = File(activity.cacheDir, directoryName).apply { check(isDirectory || mkdirs()) }
    val interactiveBefore = activity.getSystemService(PowerManager::class.java)?.isInteractive
    val screen = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
    try {
        var pixelReadFailure: String? = null
        val allRgbZero = try { screenshotHasOnlyZeroRgb(screen) }
            catch (failure: Throwable) { pixelReadFailure = failure.javaClass.simpleName; null }
        val screenshotAccepted = allRgbZero == false
        val captureDiagnostic = "screenshotApi=UiAutomation.takeScreenshot\nscreenshotRetried=false\n" +
            "screenInteractiveBeforeCapture=$interactiveBefore\nscreenshotBitmapConfig=${screen.config}\n" +
            "screenshotBitmapGenerationId=${screen.generationId}\nscreenshotHasAlpha=${screen.hasAlpha()}\n" +
            "screenshotAllRgbZero=$allRgbZero\nscreenshotPixelReadFailure=$pixelReadFailure\n" +
            "screenshotAccepted=$screenshotAccepted\n" +
            (if (screenshotAccepted) "" else runCatching { screenshotWindowFailureDiagnostic(activity) }
                .getOrElse { "windowDiagnostic=FAILED_${it.javaClass.simpleName}\n" })
        // Preserve the exact rejected bitmap and metadata before failing. Never retry or reuse another image.
        File(directory, "$name.png").outputStream().use { check(screen.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        File(directory, "$name.txt").writeText(
            "screen=${screen.width}x${screen.height}\ndensity=${activity.resources.displayMetrics.density}\n" +
                metadata + (if (metadata.endsWith("\n")) "" else "\n") + captureDiagnostic,
        )
        if (!screenshotAccepted) throw AssertionError(
            "Native screenshot rejected; original PNG and diagnostics retained: allRgbZero=$allRgbZero pixelReadFailure=$pixelReadFailure")
    } finally { screen.recycle() }
}

/** Exact RGB0 guard only. A single nonzero RGB channel is sufficient; alpha cannot disguise a black image. */
internal fun screenshotHasOnlyZeroRgb(bitmap: Bitmap): Boolean {
    val row = IntArray(bitmap.width)
    for (y in 0 until bitmap.height) {
        bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
        if (row.any { (it and 0x00ffffff) != 0 }) return false
    }
    return true
}

/** Diagnostic natif en lecture seule ; ne réessaie ni ne retouche la capture. */
private fun screenshotWindowFailureDiagnostic(activity: ComponentActivity): String {
    val result = AtomicReference("windowDiagnostic=MAIN_THREAD_TIMEOUT\n")
    val finished = CountDownLatch(1)
    val handler = Handler(Looper.getMainLooper())
    val read = Runnable {
        try {
            val decor = activity.window.decorView
            val attributes = activity.window.attributes
            val visible = Rect().also(decor::getWindowVisibleDisplayFrame)
            result.set("windowDiagnostic=OBSERVED_AFTER_SCREENSHOT\n" +
                "activityFinishing=${activity.isFinishing}\nactivityDestroyed=${activity.isDestroyed}\n" +
                "activityLifecycle=${activity.lifecycle.currentState}\nwindowHasFocus=${decor.hasWindowFocus()}\n" +
                "windowFlagSecure=${attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0}\n" +
                "windowFlagKeepScreenOn=${attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0}\n" +
                "windowVisibility=${decor.windowVisibility}\ndecorVisibility=${decor.visibility}\ndecorShown=${decor.isShown}\n" +
                "decorAttached=${decor.isAttachedToWindow}\ndecorWidth=${decor.width}\ndecorHeight=${decor.height}\n" +
                "decorHardwareAccelerated=${decor.isHardwareAccelerated}\n" +
                "windowVisibleFrame=${visible.left},${visible.top},${visible.right},${visible.bottom}\n" +
                "screenInteractiveAfterCapture=${activity.getSystemService(PowerManager::class.java)?.isInteractive}\n")
        } catch (failure: Throwable) { result.set("windowDiagnostic=FAILED_${failure.javaClass.simpleName}\n") }
        finally { finished.countDown() }
    }
    if (Looper.myLooper() == Looper.getMainLooper()) read.run() else {
        if (handler.post(read)) finished.await(1, TimeUnit.SECONDS)
        handler.removeCallbacks(read)
    }
    return result.get()
}
