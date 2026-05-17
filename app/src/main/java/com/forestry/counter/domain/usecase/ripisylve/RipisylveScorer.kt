package com.forestry.counter.domain.usecase.ripisylve

import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.model.ripisylve.InadapteesMode
import com.forestry.counter.domain.model.ripisylve.RipisylveObservation
import com.forestry.counter.domain.model.ripisylve.RipisylveScore

object RipisylveScorer {
    fun score(obs: RipisylveObservation, tiges: List<Tige> = emptyList()): RipisylveScore {
        val scoreContinuite = when {
            obs.continuitePct >= 75 -> 30
            obs.continuitePct >= 50 -> 20
            obs.continuitePct >= 25 -> 10
            obs.continuitePct > 0   -> 5
            else -> 0
        }
        val scoreLargeur = obs.largeurMode.points
        val nbStrates = listOf(obs.strateHerbacee, obs.strateArbustive, obs.strateArborescente).count { it }
        val scoreStrates = when (nbStrates) { 3 -> 20; 2 -> 10; else -> 0 }
        val scoreDiversite = when {
            obs.nbEspecesObservees >= 5 -> 10
            obs.nbEspecesObservees >= 3 -> 5
            else -> 0
        }
        val nbClasses = listOf(obs.hasTresPetitBois, obs.hasPetitBois, obs.hasMoyenBois, obs.hasGrosBois).count { it }
        val scoreDiametres = when (nbClasses) { 4 -> 10; 3 -> 10; 2 -> 5; else -> 0 }
        val nbMicroh = listOf(obs.microhabitatCavites, obs.microhabitatFissures, obs.microhabitatDecollementEcorce,
            obs.microhabitatChampignons, obs.microhabitatBoisMort, obs.microhabitatTresGrosBois).count { it }
        val scoreMicrohabitats = when { nbMicroh >= 3 -> 10; nbMicroh >= 1 -> 5; else -> 0 }
        val scoreSanitaire = when {
            obs.sanitairePct >= 75 -> -20; obs.sanitairePct >= 50 -> -15; obs.sanitairePct >= 25 -> -10; else -> 0
        }
        val scoreInvasives = when {
            obs.invasivesPct >= 75 -> -20; obs.invasivesPct >= 50 -> -15; obs.invasivesPct >= 25 -> -10
            obs.invasivesPct > 0 -> -5; else -> 0
        }
        val scoreInadaptees = obs.inadapteesMode.points
        val scoreStabilite = when {
            obs.stabilitePct >= 50 -> -20; obs.stabilitePct >= 25 -> -10; else -> 0
        }
        val confidence = minOf(100, (nbStrates * 10) + (nbClasses * 5) + (nbMicroh * 5) +
            if (obs.nbEspecesObservees > 0) 20 else 0)
        return RipisylveScore(
            scoreContinuite = scoreContinuite, scoreLargeur = scoreLargeur,
            scoreStrates = scoreStrates, scoreDiversite = scoreDiversite,
            scoreDiametres = scoreDiametres, scoreMicrohabitats = scoreMicrohabitats,
            scoreSanitaire = scoreSanitaire, scoreInvasives = scoreInvasives,
            scoreInadaptees = scoreInadaptees, scoreStabilite = scoreStabilite,
            nbMicrohabitats = nbMicroh, nbStrates = nbStrates, nbClassesDiam = nbClasses,
            confidenceScore = confidence
        )
    }
}
