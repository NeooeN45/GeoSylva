package com.forestry.counter.domain.usecase.ripisylve

import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.model.ripisylve.InadapteesMode
import com.forestry.counter.domain.model.ripisylve.LargeurMode
import com.forestry.counter.domain.model.ripisylve.RipisylveObservation
import com.forestry.counter.domain.model.ripisylve.RipisylveScore

/**
 * Moteur de scoring du diagnostic ripisylve.
 * Référence : Forêt-Entreprise n°242 – CRPF Hauts-de-France / Agence de l'eau Artois-Picardie.
 *
 * Score maximal : 100 pts  |  Score minimal : -20 pts
 */
object RipisylveScorer {

    // ─────────────────────────────────────────────────────────────────
    //  Calcul principal
    // ─────────────────────────────────────────────────────────────────

    fun score(obs: RipisylveObservation, tiges: List<Tige> = emptyList()): RipisylveScore {
        val scoreContinuite = scoreContinuite(obs.continuitePct)
        val scoreLargeur    = obs.largeurMode.points
        val nbStrates       = countStrates(obs)
        val scoreStrates    = scoreStrates(nbStrates)
        val scoreDiversite  = scoreDiversite(obs.nbEspecesObservees)
        val nbClassesDiam   = countClassesDiametre(obs, tiges)
        val scoreDiametres  = scoreDiametres(nbClassesDiam)
        val nbMicro         = countMicrohabitats(obs)
        val scoreMicro      = scoreMicrohabitats(nbMicro)
        val scoreSanitaire  = scoreSanitaire(obs.sanitairePct)
        val scoreInvasives  = scoreInvasives(obs.invasivesPct)
        val scoreInadaptees = obs.inadapteesMode.points
        val scoreStabilite  = scoreStabilite(obs.stabilitePct)

        val confidenceScore = computeConfidenceScore(obs, nbStrates, nbMicro, nbClassesDiam)

        return RipisylveScore(
            scoreContinuite    = scoreContinuite,
            scoreLargeur       = scoreLargeur,
            scoreStrates       = scoreStrates,
            scoreDiversite     = scoreDiversite,
            scoreDiametres     = scoreDiametres,
            scoreMicrohabitats = scoreMicro,
            scoreSanitaire     = scoreSanitaire,
            scoreInvasives     = scoreInvasives,
            scoreInadaptees    = scoreInadaptees,
            scoreStabilite     = scoreStabilite,
            nbMicrohabitats    = nbMicro,
            nbStrates          = nbStrates,
            nbClassesDiam      = nbClassesDiam,
            confidenceScore    = confidenceScore
        )
    }

    // ─────────────────────────────────────────────────────────────────
    //  Critère 1 — Continuité  (0 / 5 / 10 / 20 / 30)
    // ─────────────────────────────────────────────────────────────────

    fun scoreContinuite(pct: Double): Int = when {
        pct > 75.0 -> 30
        pct > 50.0 -> 20
        pct > 25.0 -> 10
        pct > 10.0 -> 5
        else       -> 0
    }

    fun continuiteLabelForPct(pct: Double): String = when {
        pct > 75.0 -> "Continue (> 75 %)"
        pct > 50.0 -> "Semi-continue (50–75 %)"
        pct > 25.0 -> "Discontinue (25–50 %)"
        pct > 10.0 -> "Clairsemée (10–25 %)"
        else       -> "Absente (≤ 10 %)"
    }

    // ─────────────────────────────────────────────────────────────────
    //  Critère 3 — Strates
    // ─────────────────────────────────────────────────────────────────

    fun countStrates(obs: RipisylveObservation): Int {
        var n = 0
        if (obs.strateHerbacee) n++
        if (obs.strateArbustive) n++
        if (obs.strateArborescente) n++
        return n
    }

    fun scoreStrates(nb: Int): Int = when {
        nb >= 3 -> 20
        nb == 2 -> 10
        else    -> 0
    }

    // ─────────────────────────────────────────────────────────────────
    //  Critère 4 — Diversité spécifique  (0 / 5 / 10)
    // ─────────────────────────────────────────────────────────────────

    fun scoreDiversite(nbEspeces: Int): Int = when {
        nbEspeces >= 8 -> 10
        nbEspeces >= 5 -> 5
        else           -> 0
    }

    // ─────────────────────────────────────────────────────────────────
    //  Critère 5 — Classes de diamètre  (0 / 5 / 10)
    //  Auto-calculé depuis les tiges si disponibles
    // ─────────────────────────────────────────────────────────────────

    fun countClassesDiametre(obs: RipisylveObservation, tiges: List<Tige>): Int {
        return if (tiges.isNotEmpty()) {
            // Calcul automatique depuis l'inventaire
            var hasTPB = false; var hasPB = false; var hasMB = false; var hasGB = false
            tiges.forEach { t ->
                when {
                    t.diamCm <= 7.0  -> hasTPB = true
                    t.diamCm < 20.0  -> hasPB  = true
                    t.diamCm < 40.0  -> hasMB  = true
                    else             -> hasGB  = true
                }
            }
            listOf(hasTPB, hasPB, hasMB, hasGB).count { it }
        } else {
            listOf(obs.hasTresPetitBois, obs.hasPetitBois, obs.hasMoyenBois, obs.hasGrosBois).count { it }
        }
    }

    fun scoreDiametres(nbClasses: Int): Int = when {
        nbClasses >= 4 -> 10
        nbClasses >= 2 -> 5
        else           -> 0
    }

    // ─────────────────────────────────────────────────────────────────
    //  Critère 6 — Microhabitats  (0 / 5 / 10)
    // ─────────────────────────────────────────────────────────────────

    fun countMicrohabitats(obs: RipisylveObservation): Int {
        return listOf(
            obs.microhabitatTresGrosBois,
            obs.microhabitatBoisMort,
            obs.microhabitatCavites,
            obs.microhabitatFissures,
            obs.microhabitatDecollementEcorce,
            obs.microhabitatChampignons
        ).count { it }
    }

    fun scoreMicrohabitats(nb: Int): Int = when {
        nb >= 2 -> 10
        nb == 1 -> 5
        else    -> 0
    }

    // ─────────────────────────────────────────────────────────────────
    //  Critère 7 — État sanitaire  (0 / -10 / -15 / -20)
    // ─────────────────────────────────────────────────────────────────

    fun scoreSanitaire(pct: Double): Int = when {
        pct >= 50.0 -> -20
        pct >= 25.0 -> -15
        pct >= 5.0  -> -10
        else        -> 0
    }

    // ─────────────────────────────────────────────────────────────────
    //  Critère 8 — Espèces invasives  (0 / -5 / -10 / -15 / -20)
    // ─────────────────────────────────────────────────────────────────

    fun scoreInvasives(pct: Double): Int = when {
        pct >= 50.0 -> -20
        pct >= 25.0 -> -15
        pct >= 5.0  -> -10
        pct > 0.0   -> -5
        else        -> 0
    }

    // ─────────────────────────────────────────────────────────────────
    //  Critère 10 — Stabilité  (0 / -10 / -20)
    // ─────────────────────────────────────────────────────────────────

    fun scoreStabilite(pct: Double): Int = when {
        pct >= 30.0 -> -20
        pct >= 10.0 -> -10
        else        -> 0
    }

    // ─────────────────────────────────────────────────────────────────
    //  Confiance — 0 à 100 pts selon données réellement observées
    //  Principe : chaque critère EXPLICITEMENT renseigné ajoute des pts.
    //  Une observation à 0.0 sur tous les champs = confiance minimale.
    // ─────────────────────────────────────────────────────────────────

    fun computeConfidenceScore(
        obs: RipisylveObservation,
        nbStrates: Int,
        nbMicro: Int,
        nbClassesDiam: Int
    ): Int {
        var score = 0

        // GPS (15 pts)
        if (obs.latitude != null && obs.longitude != null) score += 15

        // Photos (20 pts — indispensables pour valider le diagnostic)
        score += when {
            obs.photos.size >= 3 -> 20
            obs.photos.size >= 1 -> 10
            else -> 0
        }

        // Continuité renseignée (> 0 ou notes de section fournies) (10 pts)
        if (obs.continuitePct > 0.0 || obs.sectionNotes.isNotBlank()) score += 10

        // Largeur renseignée (≠ valeur par défaut UNE_RANGEE) (5 pts)
        if (obs.largeurMode != LargeurMode.UNE_RANGEE) score += 5

        // Strates observées (0, 5, 10, 15 pts selon nb)
        score += nbStrates * 5

        // Diversité floristique (0, 5, 10 pts)
        score += when {
            obs.nbEspecesObservees >= 5 || obs.especesObservees.size >= 5 -> 10
            obs.nbEspecesObservees >= 2 || obs.especesObservees.size >= 2 -> 5
            else -> 0
        }

        // Classes diamètre renseignées (5 pts si ≥ 1)
        if (nbClassesDiam >= 1) score += 5

        // Microhabitats observés (5 pts si ≥ 1)
        if (nbMicro >= 1) score += 5

        // Pressions évaluées : sanitaire, invasives, inadaptées, stabilité (5 pts si ≥ 2 critères évalués)
        val pressionsEvaluees = listOf(
            obs.sanitairePct > 0.0,
            obs.invasivesPct > 0.0,
            obs.inadapteesMode != InadapteesMode.ABSENCE,
            obs.stabilitePct > 0.0
        ).count { it }
        if (pressionsEvaluees >= 2) score += 5

        // Notes de terrain fournies (bonus léger)
        if (obs.globalNotes.isNotBlank()) score += 5

        return score.coerceIn(0, 100)
    }

    // ─────────────────────────────────────────────────────────────────
    //  Pré-remplissage automatique depuis données dendro
    // ─────────────────────────────────────────────────────────────────

    fun autoFillFromTiges(obs: RipisylveObservation, tiges: List<Tige>): RipisylveObservation {
        if (tiges.isEmpty()) return obs
        var hasTPB = false; var hasPB = false; var hasMB = false; var hasGB = false
        tiges.forEach { t ->
            when {
                t.diamCm <= 7.0  -> hasTPB = true
                t.diamCm < 20.0  -> hasPB  = true
                t.diamCm < 40.0  -> hasMB  = true
                else             -> hasGB  = true
            }
        }
        return obs.copy(
            diamAutoFromDendro = true,
            hasTresPetitBois   = hasTPB,
            hasPetitBois       = hasPB,
            hasMoyenBois       = hasMB,
            hasGrosBois        = hasGB
        )
    }
}
