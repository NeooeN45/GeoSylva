package com.forestry.counter.domain.diagnostic

import com.forestry.counter.data.local.entity.StationEnvironnementaleEntity
import com.forestry.counter.data.sylviculture.FertiliteEssenceSerData

/**
 * Score d'adéquation d'une essence à une station forestière.
 * Sources : règles CNPF (guides IFN) + données FertiliteEssenceSerData embarquées.
 *
 * Score final 0–100 pondéré :
 *   40% fertilité CNPF (FertiliteEssenceSerData × SER)
 *   20% adéquation climatique (T, P, ETP)
 *   20% adéquation sol (pH, RU, drainage)
 *   10% projection climatique 2050
 *   10% risques sanitaires
 */
object EssenceSuitabilityScorer {

    private const val WEIGHT_FERTILITE = 0.40
    private const val WEIGHT_CLIMAT    = 0.20
    private const val WEIGHT_SOL       = 0.20
    private const val WEIGHT_CC2050    = 0.10
    private const val WEIGHT_SANITAIRE = 0.10

    data class SuitabilityScore(
        val essenceCode: String,
        val scoreTotal: Int,
        val scoreFertilite: Int,
        val scoreClimat: Int,
        val scoreSol: Int,
        val scoreCc2050: Int,
        val scoreSanitaire: Int,
        val classeAdequation: ClasseAdequation,
        val recommandations: List<String>
    )

    enum class ClasseAdequation(val label: String, val emoji: String) {
        TRES_FAVORABLE("Très favorable", "✅"),
        FAVORABLE("Favorable", "🟢"),
        MOYEN("Moyen", "🟡"),
        DEFAVORABLE("Défavorable", "🔴"),
        INADAPTE("Inadapté", "⛔")
    }

    /**
     * Score une liste d'essences contre une station environnementale.
     * Retourne les résultats triés par score décroissant.
     */
    fun scoreAll(
        essenceCodes: List<String>,
        station: StationEnvironnementaleEntity
    ): List<SuitabilityScore> =
        essenceCodes.map { score(it, station) }.sortedByDescending { it.scoreTotal }

    /**
     * Score une essence contre une station.
     */
    fun score(essenceCode: String, station: StationEnvironnementaleEntity): SuitabilityScore {
        val fertilite = scoreFertilite(essenceCode, station.codeSer)
        val climat    = scoreClimat(essenceCode, station)
        val sol       = scoreSol(essenceCode, station)
        val cc2050    = scoreCc2050(essenceCode, station.scoreVulnCC2050)
        val sanitaire = scoreSanitaire(essenceCode, station)

        val total = (
            fertilite * WEIGHT_FERTILITE +
            climat    * WEIGHT_CLIMAT    +
            sol       * WEIGHT_SOL       +
            cc2050    * WEIGHT_CC2050    +
            sanitaire * WEIGHT_SANITAIRE
        ).toInt().coerceIn(0, 100)

        return SuitabilityScore(
            essenceCode       = essenceCode,
            scoreTotal        = total,
            scoreFertilite    = fertilite,
            scoreClimat       = climat,
            scoreSol          = sol,
            scoreCc2050       = cc2050,
            scoreSanitaire    = sanitaire,
            classeAdequation  = classFromScore(total),
            recommandations   = buildRecommandations(essenceCode, fertilite, climat, sol, cc2050, sanitaire)
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Score fertilité CNPF × SER
    // ──────────────────────────────────────────────────────────────────────────
    private fun scoreFertilite(essenceCode: String, codeSer: String?): Int {
        if (codeSer == null) return SCORE_NEUTRAL
        val fertilite = FertiliteEssenceSerData.get(essenceCode, codeSer) ?: return SCORE_NEUTRAL
        return when (fertilite.classeStation) {
            5    -> 100
            4    -> 80
            3    -> 55
            2    -> 30
            1    -> 10
            else -> SCORE_NEUTRAL
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Score climatique (normales embarquées)
    // ──────────────────────────────────────────────────────────────────────────
    private fun scoreClimat(essenceCode: String, station: StationEnvironnementaleEntity): Int {
        val t = station.tempMoyC ?: return SCORE_NEUTRAL
        val p = station.precipMmAn ?: return SCORE_NEUTRAL
        val specs = CLIMATE_SPECS[essenceCode] ?: return SCORE_NEUTRAL

        var score = 100
        if (t < specs.tMinOptC)  score -= ((specs.tMinOptC - t) * 15).toInt().coerceIn(0, 60)
        if (t > specs.tMaxOptC)  score -= ((t - specs.tMaxOptC) * 15).toInt().coerceIn(0, 60)
        if (p < specs.pMinOptMm) score -= ((specs.pMinOptMm - p) / 20).toInt().coerceIn(0, 50)
        if (p > specs.pMaxOptMm) score -= ((p - specs.pMaxOptMm) / 50).toInt().coerceIn(0, 20)
        return score.coerceIn(0, 100)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Score sol (pH, RU, drainage)
    // ──────────────────────────────────────────────────────────────────────────
    private fun scoreSol(essenceCode: String, station: StationEnvironnementaleEntity): Int {
        val ph = station.soilPh ?: return SCORE_NEUTRAL
        val ru = station.soilRumMm ?: return SCORE_NEUTRAL
        val specs = SOIL_SPECS[essenceCode] ?: return SCORE_NEUTRAL

        var score = 100
        if (ph < specs.phMin) score -= ((specs.phMin - ph) * 20).toInt().coerceIn(0, 60)
        if (ph > specs.phMax) score -= ((ph - specs.phMax) * 20).toInt().coerceIn(0, 60)
        if (ru < specs.ruMinMm) score -= ((specs.ruMinMm - ru) / 5).toInt().coerceIn(0, 50)
        return score.coerceIn(0, 100)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Score vulnérabilité CC 2050
    // ──────────────────────────────────────────────────────────────────────────
    private fun scoreCc2050(essenceCode: String, scoreVulnStation: Int?): Int {
        val vulnStation = scoreVulnStation ?: return SCORE_NEUTRAL
        val cc2050EssenceSpec = CC2050_RESILIENCE[essenceCode] ?: return SCORE_NEUTRAL
        return (cc2050EssenceSpec - vulnStation).coerceIn(0, 100)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Score sanitaire (risques pathogènes connus × essence)
    // ──────────────────────────────────────────────────────────────────────────
    private fun scoreSanitaire(essenceCode: String, station: StationEnvironnementaleEntity): Int {
        val risques = PATHOGEN_RISK[essenceCode] ?: return SCORE_GOOD
        var score = 100
        val t = station.tempMoyC ?: return SCORE_GOOD
        val p = station.precipMmAn ?: return SCORE_GOOD
        risques.forEach { risk ->
            if (t > risk.tempSeuilC && p < risk.precipSeuilMm) score -= risk.malus
        }
        return score.coerceIn(0, 100)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Classe d'adéquation depuis score total
    // ──────────────────────────────────────────────────────────────────────────
    private fun classFromScore(score: Int): ClasseAdequation = when {
        score >= 80 -> ClasseAdequation.TRES_FAVORABLE
        score >= 65 -> ClasseAdequation.FAVORABLE
        score >= 45 -> ClasseAdequation.MOYEN
        score >= 25 -> ClasseAdequation.DEFAVORABLE
        else        -> ClasseAdequation.INADAPTE
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Recommandations textuelles
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildRecommandations(
        essenceCode: String,
        fertilite: Int, climat: Int, sol: Int, cc2050: Int, sanitaire: Int
    ): List<String> {
        val recs = mutableListOf<String>()
        if (fertilite < 40) recs += "Fertilité insuffisante dans cette SylvoÉcoRégion"
        if (climat < 40) recs += "Conditions climatiques défavorables (T° ou précipitations)"
        if (sol < 40) recs += "Sol inadapté (pH ou réserve utile insuffisante)"
        if (cc2050 < 40) recs += "Vulnérabilité élevée au changement climatique 2050"
        if (sanitaire < 50) recs += "Risques sanitaires accrus dans ces conditions"
        return recs
    }

    private const val SCORE_NEUTRAL = 60
    private const val SCORE_GOOD    = 90

    val DEFAULT_ESSENCES = listOf(
        "QUPE", "QUPU", "FASY", "PSME", "LADA",
        "PISY", "ABBA", "PIAB", "CASA", "ACPS"
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Référentiel climatique simplifié (CNPF + IFN)
    // Tmin/Tmax optimales (°C), Pmin/Pmax optimales (mm/an)
    // ──────────────────────────────────────────────────────────────────────────
    private data class ClimateSpec(val tMinOptC: Double, val tMaxOptC: Double, val pMinOptMm: Double, val pMaxOptMm: Double)

    private val CLIMATE_SPECS = mapOf(
        "QUPE"  to ClimateSpec(7.0, 14.0, 550.0, 1800.0),  // Chêne pédonculé
        "QUPU"  to ClimateSpec(8.0, 15.0, 600.0, 1500.0),  // Chêne pubescent
        "QUIL"  to ClimateSpec(10.0, 17.0, 450.0, 900.0),  // Chêne vert
        "QURU"  to ClimateSpec(6.0, 13.0, 600.0, 1200.0),  // Chêne rouge
        "FASY"  to ClimateSpec(6.0, 13.0, 700.0, 2000.0),  // Hêtre
        "ABBA"  to ClimateSpec(3.0, 11.0, 800.0, 2500.0),  // Sapin pectiné
        "PIAB"  to ClimateSpec(2.0, 10.0, 700.0, 2000.0),  // Épicéa commun
        "PSME"  to ClimateSpec(5.0, 13.0, 650.0, 1800.0),  // Douglas
        "LADA"  to ClimateSpec(2.0, 12.0, 700.0, 1800.0),  // Mélèze
        "PISY"  to ClimateSpec(4.0, 14.0, 450.0, 1200.0),  // Pin sylvestre
        "PIPE"  to ClimateSpec(11.0, 18.0, 400.0, 900.0),  // Pin maritime
        "PIHA"  to ClimateSpec(10.0, 18.0, 500.0, 1200.0), // Pin d'Alep
        "PINI"  to ClimateSpec(5.0, 15.0, 500.0, 1200.0),  // Pin noir
        "CASA"  to ClimateSpec(8.0, 15.0, 700.0, 1800.0),  // Châtaignier
        "ROPC"  to ClimateSpec(9.0, 16.0, 500.0, 1200.0),  // Robinier
        "FREX"  to ClimateSpec(7.0, 13.0, 600.0, 1500.0),  // Frêne
        "ACPS"  to ClimateSpec(6.0, 13.0, 600.0, 1800.0),  // Érable sycomore
        "JUGR"  to ClimateSpec(9.0, 15.0, 600.0, 1200.0),  // Noyer commun
        "POHY"  to ClimateSpec(8.0, 15.0, 600.0, 1400.0),  // Peuplier hybride
        "BIPE"  to ClimateSpec(6.0, 13.0, 600.0, 1600.0)   // Bouleau
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Référentiel sol (CNPF)
    // phMin, phMax optimaux, RU minimum (mm)
    // ──────────────────────────────────────────────────────────────────────────
    private data class SoilSpec(val phMin: Double, val phMax: Double, val ruMinMm: Double)

    private val SOIL_SPECS = mapOf(
        "QUPE"  to SoilSpec(4.5, 7.5, 80.0),
        "QUPU"  to SoilSpec(5.0, 8.0, 60.0),
        "QUIL"  to SoilSpec(5.5, 8.5, 50.0),
        "QURU"  to SoilSpec(4.0, 6.5, 70.0),
        "FASY"  to SoilSpec(4.5, 7.5, 100.0),
        "ABBA"  to SoilSpec(4.0, 7.0, 120.0),
        "PIAB"  to SoilSpec(3.5, 6.5, 80.0),
        "PSME"  to SoilSpec(4.5, 7.0, 90.0),
        "LADA"  to SoilSpec(4.5, 7.5, 70.0),
        "PISY"  to SoilSpec(4.0, 7.0, 50.0),
        "PIPE"  to SoilSpec(4.5, 7.5, 60.0),
        "PIHA"  to SoilSpec(6.5, 8.5, 40.0),
        "PINI"  to SoilSpec(5.0, 8.0, 50.0),
        "CASA"  to SoilSpec(4.0, 6.5, 80.0),
        "ROPC"  to SoilSpec(6.0, 8.0, 60.0),
        "FREX"  to SoilSpec(5.5, 7.5, 100.0),
        "ACPS"  to SoilSpec(5.0, 7.5, 90.0),
        "JUGR"  to SoilSpec(6.0, 8.0, 90.0),
        "POHY"  to SoilSpec(5.5, 7.5, 110.0),
        "BIPE"  to SoilSpec(4.0, 7.0, 60.0)
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Résilience CC2050 par essence (score 0–100, 100 = très résilient)
    // Source : CNPF "Quelles essences pour demain" + synthèse INRAE 2022
    // ──────────────────────────────────────────────────────────────────────────
    private val CC2050_RESILIENCE = mapOf(
        "QUPE"  to 45, "QUPU"  to 75, "QUIL"  to 90,
        "QURU"  to 55, "FASY"  to 30, "ABBA"  to 20,
        "PIAB"  to 15, "PSME"  to 65, "LADA"  to 60,
        "PISY"  to 50, "PIPE"  to 70, "PIHA"  to 85,
        "PINI"  to 70, "CASA"  to 70, "ROPC"  to 80,
        "FREX"  to 20, "ACPS"  to 55, "JUGR"  to 60,
        "POHY"  to 50, "BIPE"  to 55
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Risques pathogènes par essence (seuils T°, P, malus score)
    // Source : DSF / PathoEntomoDB
    // ──────────────────────────────────────────────────────────────────────────
    private data class PathogenRisk(val tempSeuilC: Double, val precipSeuilMm: Double, val malus: Int)

    private val PATHOGEN_RISK = mapOf(
        "PIAB" to listOf(PathogenRisk(12.0, 700.0, 30)), // Scolytes Ips typographus
        "FREX" to listOf(PathogenRisk(12.0, 800.0, 40)), // Chalarose Hymenoscyphus fraxineus
        "ABBA" to listOf(PathogenRisk(10.0, 900.0, 20)), // Sapins sécheresse
        "PIPE" to listOf(PathogenRisk(15.0, 600.0, 15))  // Armillaire
    )
}
