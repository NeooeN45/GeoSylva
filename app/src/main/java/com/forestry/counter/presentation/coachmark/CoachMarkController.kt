package com.forestry.counter.presentation.coachmark

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.forestry.counter.R

/** Une étape de la visite guidée : le titre/texte pointent une cible enregistrée via [com.forestry.counter.presentation.coachmark.coachMarkAnchor]. */
data class CoachMarkStep(
    val anchorKey: String,
    val titleRes: Int,
    val descRes: Int,
)

/**
 * Visite guidée des 5 onglets principaux — déclenchée une seule fois, juste
 * après que l'utilisateur a répondu aux autorisations GPS/caméra/galerie
 * qui suivent l'onboarding (voir `OnboardingNavGraph`). Contrairement au
 * didacticiel (slides illustrées), elle pointe les vrais boutons de
 * navigation pendant que l'utilisateur est dans l'application.
 */
val COACH_MARK_STEPS = listOf(
    CoachMarkStep("nav_accueil", R.string.coachmark_accueil_title, R.string.coachmark_accueil_desc),
    CoachMarkStep("nav_explorer", R.string.coachmark_explorer_title, R.string.coachmark_explorer_desc),
    CoachMarkStep("nav_missions", R.string.coachmark_missions_title, R.string.coachmark_missions_desc),
    CoachMarkStep("nav_carte", R.string.coachmark_carte_title, R.string.coachmark_carte_desc),
    CoachMarkStep("nav_parametres", R.string.coachmark_parametres_title, R.string.coachmark_parametres_desc),
)

/** Décision pure afin de garder le déclenchement persistant testable. */
fun shouldStartCoachMarkTour(
    pending: Boolean,
    completed: Boolean,
    isTopLevel: Boolean,
    isActive: Boolean,
): Boolean = pending && !completed && isTopLevel && !isActive

class CoachMarkController(steps: List<CoachMarkStep> = COACH_MARK_STEPS) {
    private val steps = steps.toList()
    val totalSteps: Int get() = steps.size
    var stepIndex by mutableStateOf<Int?>(null)
        private set

    val isActive: Boolean get() = stepIndex != null

    val currentStep: CoachMarkStep?
        get() = stepIndex?.let { steps.getOrNull(it) }

    fun start() {
        stepIndex = 0.takeIf { steps.isNotEmpty() }
    }

    /** Avance d'une étape ; termine la visite après la dernière. */
    fun next() {
        val current = stepIndex ?: return
        val nextIndex = current + 1
        stepIndex = if (nextIndex < steps.size) nextIndex else null
    }

    fun stop() {
        stepIndex = null
    }
}

@Composable
fun rememberCoachMarkController(steps: List<CoachMarkStep> = COACH_MARK_STEPS): CoachMarkController =
    remember(steps) { CoachMarkController(steps) }
