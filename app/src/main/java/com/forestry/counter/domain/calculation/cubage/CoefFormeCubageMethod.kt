package com.forestry.counter.domain.calculation.cubage

import com.forestry.counter.domain.calculation.tarifs.TarifCalculator
import com.forestry.counter.domain.calculation.tarifs.TarifMethod

/**
 * Enveloppe [CubageMethod] autour de [TarifCalculator] (COEF_FORME), sans dupliquer
 * la formule V = G × H × f. Vérifiée contre le vecteur "form_factor_tree".
 */
object CoefFormeCubageMethod : CubageMethod<StandingTreeMeasurement> {
    override val id: String = "COEF_FORME"
    override val version: String = "1.0.0"
    override val qualification: MethodQualification = MethodQualification.QUALIFIED
    override val sourceCitation: String = "V = G × H × f — méthode classique, TarifCalculator.kt"

    override fun compute(input: StandingTreeMeasurement): CubageResult {
        val hauteurM = input.hauteurM
            ?: return CubageResult.Blocked("Hauteur manquante (requise pour COEF_FORME)")
        val volume = TarifCalculator.computeVolume(
            method = TarifMethod.COEF_FORME,
            essenceCode = input.essenceCode,
            diamCm = input.diamCm,
            hauteurM = hauteurM,
            tarifNumero = input.tarifNumero,
            coefFormOverride = input.coefFormOverride
        ) ?: return CubageResult.Blocked("Données insuffisantes pour COEF_FORME (diamètre/hauteur invalides)")
        return CubageResult.Valid(volumeM3 = volume)
    }
}
