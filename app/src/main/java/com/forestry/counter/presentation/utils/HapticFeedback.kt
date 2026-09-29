package com.forestry.counter.presentation.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

class HapticFeedback(private val context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun performHapticFeedback(type: HapticType = HapticType.LIGHT) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return

        // `createPredefined()` (API 29+) délègue à l'implémentation haptique du
        // constructeur OEM : sur de nombreux téléphones réels (notamment hors
        // Pixel/Samsung haut de gamme), l'effet demandé n'est pas supporté et
        // l'appel ne produit alors AUCUNE vibration, sans erreur ni log — d'où
        // le "réglable dans les paramètres mais ne vibre jamais en pratique".
        // `createOneShot(duration, amplitude)` est lui garanti fonctionner sur
        // tout appareil doté d'un vibrateur depuis l'API 26 : on ne tente donc
        // l'effet prédéfini que si `areEffectsSupported` le confirme, et on
        // retombe sinon systématiquement sur une vibration manuelle explicite.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // `areEffectsSupported` lui-même n'existe qu'à partir de l'API 30 —
            // en dessous (API 29 Q), impossible de vérifier le support, donc on
            // saute directement au repli `createOneShot` garanti fiable.
            val predefinedId = when (type) {
                HapticType.LIGHT -> VibrationEffect.EFFECT_TICK
                HapticType.MEDIUM -> VibrationEffect.EFFECT_CLICK
                HapticType.HEAVY -> VibrationEffect.EFFECT_HEAVY_CLICK
                HapticType.SUCCESS -> VibrationEffect.EFFECT_DOUBLE_CLICK
            }
            val supported = v.areEffectsSupported(predefinedId)
            if (supported.size > 0 && supported[0] == Vibrator.VIBRATION_EFFECT_SUPPORT_YES) {
                v.vibrate(VibrationEffect.createPredefined(predefinedId))
                return
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val (duration, amplitude) = when (type) {
                HapticType.LIGHT -> 12L to 90
                HapticType.MEDIUM -> 20L to 140
                HapticType.HEAVY -> 30L to 200
                HapticType.SUCCESS -> 45L to 180
            }
            v.vibrate(VibrationEffect.createOneShot(duration, amplitude.coerceIn(1, 255)))
        } else {
            @Suppress("DEPRECATION")
            val duration = when (type) {
                HapticType.LIGHT -> 12L
                HapticType.MEDIUM -> 20L
                HapticType.HEAVY -> 30L
                HapticType.SUCCESS -> 45L
            }
            @Suppress("DEPRECATION")
            v.vibrate(duration)
        }
    }

    fun performWithIntensity(level: Int) {
        val mapped = when (level.coerceIn(1, 3)) {
            1 -> HapticType.LIGHT
            2 -> HapticType.MEDIUM
            else -> HapticType.HEAVY
        }
        performHapticFeedback(mapped)
    }
}

enum class HapticType {
    LIGHT,
    MEDIUM,
    HEAVY,
    SUCCESS
}

@Composable
fun rememberHapticFeedback(): HapticFeedback {
    val context = LocalContext.current
    return remember { HapticFeedback(context) }
}
