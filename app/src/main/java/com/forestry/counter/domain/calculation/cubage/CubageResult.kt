package com.forestry.counter.domain.calculation.cubage

/**
 * Résultat d'un calcul de cubage V2. Jamais de 0.0 silencieux pour une
 * donnée manquante ou un calcul impossible — c'est [Blocked] qui porte
 * l'explication (interdiction explicite de docs/MARTELAGE_V2_SPECIFICATION.md §6).
 */
sealed class CubageResult {

    /** Calcul réussi sans réserve. */
    data class Valid(
        val volumeM3: Double,
        val uncertaintyM3: Double? = null
    ) : CubageResult()

    /** Calcul réussi mais avec une réserve explicite à afficher à l'utilisateur. */
    data class Warning(
        val volumeM3: Double,
        val reason: String
    ) : CubageResult()

    /** Calcul impossible : données insuffisantes ou incohérentes. */
    data class Blocked(
        val reason: String
    ) : CubageResult()
}
