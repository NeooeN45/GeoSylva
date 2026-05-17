package com.forestry.counter.domain.ibp

import com.forestry.counter.data.local.entity.StationEnvironnementaleEntity
import com.forestry.counter.domain.model.IbpAnswers

/**
 * Calcule le score CF (H — Continuité temporelle forestière) de l'IBP
 * depuis les données de la station environnementale.
 *
 * Règle IBP v3 pour CF :
 *   0 → Forêt < 30 ans OU pas de données
 *   2 → Forêt 30–200 ans (intermédiaire) OU connectivité spatiale 20–60%
 *   5 → Forêt ancienne confirmée (>200 ans) OU connectivité ≥60% + ancienneté ≥100 ans
 *
 * Sources indicatives d'ancienneté :
 *   - Carte de Cassini (1756–1815) : forêt présente → ≥200 ans
 *   - État Major (1820–1866)       : forêt présente → ≥160 ans
 *   - BD Forêt IGN v1 (1986)       : forêt présente → ≥40 ans
 *   - isForetAncienne (DB)         : marqueur consolidé
 */
object IbpContinuiteCalculator {

    data class ContinuiteResult(
        val scoreCf: Int,
        val continuiteForestiereAns: Int,
        val continuiteSpatialePct: Int,
        val isForetAncienneConfirmee: Boolean,
        val justification: String
    )

    fun calculate(
        station: StationEnvironnementaleEntity?,
        ancienneteManuelleAns: Int = 0,
        connectiviteManuelPct: Int = 0
    ): ContinuiteResult {
        val isAncienne = station?.isForetAncienne ?: false
        val anciennete = if (ancienneteManuelleAns > 0) ancienneteManuelleAns
                         else if (isAncienne) 250 else 0
        val connectivite = if (connectiviteManuelPct > 0) connectiviteManuelPct else 0

        val score = scoreCf(anciennete, connectivite, isAncienne)
        val justification = buildJustification(anciennete, connectivite, isAncienne)

        return ContinuiteResult(
            scoreCf = score,
            continuiteForestiereAns = anciennete,
            continuiteSpatialePct = connectivite,
            isForetAncienneConfirmee = isAncienne,
            justification = justification
        )
    }

    fun applyTo(answers: IbpAnswers, result: ContinuiteResult): IbpAnswers =
        answers.copy(
            cf = result.scoreCf,
            continuiteForestiereAns = result.continuiteForestiereAns,
            continuiteSpatialePct = result.continuiteSpatialePct,
            isForetAncienneConfirmee = result.isForetAncienneConfirmee
        )

    private fun scoreCf(ancienneteAns: Int, connectivitePct: Int, isAncienne: Boolean): Int = when {
        isAncienne && connectivitePct >= 60             -> 5
        isAncienne || ancienneteAns >= 200              -> 5
        ancienneteAns >= 100 && connectivitePct >= 60   -> 5
        ancienneteAns >= 30 || connectivitePct >= 20    -> 2
        else                                            -> 0
    }

    private fun buildJustification(ancienneteAns: Int, connectivitePct: Int, isAncienne: Boolean): String {
        val parts = mutableListOf<String>()
        if (isAncienne) parts += "Forêt ancienne confirmée"
        if (ancienneteAns > 0) parts += "Ancienneté ~${ancienneteAns} ans"
        if (connectivitePct > 0) parts += "Connectivité ${connectivitePct}%"
        return parts.joinToString(" — ").ifEmpty { "Données insuffisantes" }
    }
}
