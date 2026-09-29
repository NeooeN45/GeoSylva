package com.forestry.counter.domain.calculation.cubage

import com.forestry.counter.domain.calculation.tarifs.TarifCalculator
import com.forestry.counter.domain.calculation.tarifs.TarifMethod

/**
 * Enveloppe [CubageMethod] autour des 6 méthodes historiques de [TarifCalculator]
 * (Schaeffer 1E/2E, Algan, IFN rapide/lent, FGH), sans dupliquer aucune formule —
 * chaque `compute()` délègue intégralement à `TarifCalculator.computeVolume(...)`.
 *
 * Qualification volontairement EXPERIMENTAL (pas QUALIFIED) : l'API actuelle de
 * `TarifCalculator` retourne un simple `Double?` et ne signale pas de l'extérieur
 * un repli silencieux déjà présent dans le code de production (ex: `fallbackAlganCoefs`
 * pour une essence inconnue, tarif par défaut 8/4). Ce wrapper ne peut donc pas
 * distinguer honnêtement ce cas d'un calcul pleinement sourcé — limite documentée,
 * pas cachée. Une correction complète demanderait une méthode-sœur côté
 * `TarifCalculator` exposant ce détail (`computeVolumeDetailed`), non faite ici.
 */
private class TarifCalculatorCubageMethod(
    override val id: String,
    private val method: TarifMethod
) : CubageMethod<StandingTreeMeasurement> {
    override val version: String = "1.0.0"
    override val qualification: MethodQualification = MethodQualification.EXPERIMENTAL
    override val sourceCitation: String =
        "Méthode historique déléguée à TarifCalculator.kt (${method.description})"

    override fun compute(input: StandingTreeMeasurement): CubageResult {
        if (method.entrees == 2 && input.hauteurM == null) {
            return CubageResult.Blocked("Hauteur manquante (requise pour ${method.code})")
        }
        val volume = TarifCalculator.computeVolume(
            method = method,
            essenceCode = input.essenceCode,
            diamCm = input.diamCm,
            hauteurM = input.hauteurM,
            tarifNumero = input.tarifNumero,
            coefFormOverride = input.coefFormOverride
        ) ?: return CubageResult.Blocked("Données insuffisantes pour ${method.code}")
        // Valid (pas Warning) : la réserve honnête est portée une fois pour toutes par
        // `qualification = EXPERIMENTAL` ci-dessus, pas répétée à chaque résultat —
        // on ne sait pas ICI si CE calcul précis a utilisé un repli silencieux.
        return CubageResult.Valid(volumeM3 = volume)
    }
}

val Schaeffer1ECubageMethod: CubageMethod<StandingTreeMeasurement> =
    TarifCalculatorCubageMethod("SCHAEFFER_1E", TarifMethod.SCHAEFFER_1E)

val Schaeffer2ECubageMethod: CubageMethod<StandingTreeMeasurement> =
    TarifCalculatorCubageMethod("SCHAEFFER_2E", TarifMethod.SCHAEFFER_2E)

val AlganCubageMethod: CubageMethod<StandingTreeMeasurement> =
    TarifCalculatorCubageMethod("ALGAN", TarifMethod.ALGAN)

val IfnRapideCubageMethod: CubageMethod<StandingTreeMeasurement> =
    TarifCalculatorCubageMethod("IFN_RAPIDE", TarifMethod.IFN_RAPIDE)

val IfnLentCubageMethod: CubageMethod<StandingTreeMeasurement> =
    TarifCalculatorCubageMethod("IFN_LENT", TarifMethod.IFN_LENT)

val FghCubageMethod: CubageMethod<StandingTreeMeasurement> =
    TarifCalculatorCubageMethod("FGH", TarifMethod.FGH)
