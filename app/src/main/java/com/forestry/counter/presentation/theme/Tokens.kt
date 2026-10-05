package com.forestry.counter.presentation.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Jetons de conception GeoSylva 3.0.
 *
 * Règle contraignante : **aucune valeur `.dp` littérale dans un écran**.
 * Tout passe par [Space], [Radius], [Elevation], [Touch] ou [Motion].
 * Toute couleur passe par `MaterialTheme.colorScheme` ou `Color.kt`.
 *
 * Cette règle existe pour que les écrans écrits par des agents IA restent
 * cohérents entre eux : un jeu de valeurs fermé ne peut pas dériver.
 */

// ─── Espacements ─────────────────────────────────────────────────────────────
// Rythme strict de 4 dp. Rien entre deux paliers.

object Space {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp

    /** Marge latérale d'écran, registre consultation. */
    val screenH = 20.dp

    /** Marge latérale d'écran, registre terrain (plus compact, plus de contenu). */
    val screenHField = 16.dp
}

// ─── Rayons ──────────────────────────────────────────────────────────────────
// Le registre consultation est arrondi et généreux ; le registre terrain est
// nettement plus anguleux — c'est un des signaux visuels de la bascule.

object Radius {
    val xs = 6.dp
    val sm = 10.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val full = 999.dp

    /** Angles du registre terrain : francs, presque droits. */
    val field = 8.dp
}

// ─── Élévations ──────────────────────────────────────────────────────────────

object Elevation {
    val flat = 0.dp
    val card = 1.dp
    val raised = 3.dp
    val overlay = 8.dp
    val modal = 16.dp
}

// ─── Cibles tactiles ─────────────────────────────────────────────────────────

object Touch {
    /** Minimum Material — calculé pour un doigt nu. */
    val min = 48.dp

    /**
     * Registre terrain. Un forestier porte des gants : 48 dp ne suffit pas.
     * Tout élément tactile d'un écran de saisie terrain utilise cette valeur.
     */
    val field = 56.dp

    /** Action principale d'un écran terrain (valider, incrémenter). */
    val fieldPrimary = 64.dp

    /** Écart minimal entre deux cibles adjacentes. */
    val gap = 12.dp
}

// ─── Mouvement ───────────────────────────────────────────────────────────────

object Motion {
    /** Retour d'appui, changement d'état d'un contrôle. */
    const val FAST = 150

    /** Transition d'état dans un écran. */
    const val NORMAL = 250

    /** Transition entre écrans. */
    const val SLOW = 400

    /** Une sortie dure environ 65 % de son entrée. */
    fun exitOf(enterMs: Int): Int = (enterMs * 0.65f).toInt()

    // Courbes Material 3 « Emphasized »
    val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val emphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    val standard = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /**
     * Ressorts — c'est ce qui donne le caractère « Expressive ».
     * Préférer ces spécifications aux courbes de Bézier pour tout ce qui
     * répond à un geste de l'utilisateur.
     */
    fun <T> springSnappy() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh,
    )

    fun <T> springBouncy() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun <T> springGentle() = spring<T>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Échelle appliquée à l'appui d'un contrôle. */
    const val PRESS_SCALE = 0.97f
}

// ─── Formes dérivées ─────────────────────────────────────────────────────────

// Nommé `GsShape` et non `Shape` : `androidx.compose.ui.graphics.Shape` est un
// type très utilisé, un objet homonyme dans ce package créerait une ambiguïté.
object GsShape {
    val xs = RoundedCornerShape(Radius.xs)
    val sm = RoundedCornerShape(Radius.sm)
    val md = RoundedCornerShape(Radius.md)
    val lg = RoundedCornerShape(Radius.lg)
    val xl = RoundedCornerShape(Radius.xl)
    val pill = RoundedCornerShape(Radius.full)
    val field = RoundedCornerShape(Radius.field)
}

// ─── Opacités des fonds ──────────────────────────────────────────────────────

object Backdrop {
    /** Texture à peine perceptible sous du contenu dense. */
    const val TEXTURE_ALPHA = 0.06f

    /** Texture en thème sombre — légèrement plus présente pour rester visible. */
    const val TEXTURE_ALPHA_DARK = 0.09f

    /** Verre dépoli sur fond photo. */
    const val GLASS_ALPHA = 0.16f
    const val GLASS_BORDER_ALPHA = 0.30f

    /** Verre sur fond clair uni (pas de photo derrière). */
    const val GLASS_TINT_ALPHA = 0.07f
    const val GLASS_TINT_BORDER_ALPHA = 0.16f

    /** Rayon de flou du verre. */
    val blurRadius = 18.dp

    /** Seuil de batterie sous lequel les fonds passent en aplat uni. */
    const val LOW_BATTERY_THRESHOLD = 15
}
