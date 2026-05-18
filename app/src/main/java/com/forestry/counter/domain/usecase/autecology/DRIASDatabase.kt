package com.forestry.counter.domain.usecase.autecology

import com.forestry.counter.domain.model.ClimateZone
import kotlin.math.roundToInt

/**
 * Base de données DRIAS — Projections climatiques pour les forêts françaises.
 * Sources : DRIAS-2020, CNRM-CM6 / IPSL-CM6, scénarios GIEC CMIP6 SSP245 et SSP585.
 * Période de référence : 1981–2010.
 */
object DRIASDatabase {

    enum class DroughtRisk(val labelFr: String) {
        FAIBLE("Faible"),
        MODERE("Modéré"),
        FORT("Fort"),
        TRES_FORT("Très fort")
    }

    /**
     * Projection climatique par zone bioclimatique.
     *
     * @param zone                  Zone bioclimatique ciblée
     * @param deltaTSsp245_2050     Delta T médian SSP2-4.5 à horizon 2050 (°C)
     * @param deltaTSsp585_2050     Delta T médian SSP5-8.5 à horizon 2050 (°C)
     * @param deltaPrecipSsp245Pct  Delta précipitations estivales SSP2-4.5 à 2050 (%)
     * @param deltaPrecipSsp585Pct  Delta précipitations estivales SSP5-8.5 à 2050 (%)
     * @param droughtRiskSsp245     Risque sécheresse scénario bas (SSP2-4.5)
     * @param droughtRiskSsp585     Risque sécheresse scénario haut (SSP5-8.5)
     * @param joursChaudsSsp245     Jours supplémentaires >30°C/an SSP2-4.5 à 2050
     * @param joursChaudsSsp585     Jours supplémentaires >30°C/an SSP5-8.5 à 2050
     * @param joursGelPerdus        Jours de gel printanier perdus à 2050 (tous scénarios, médiane)
     * @param deltaEtpPct           Delta ETP estivale à 2050 (%) — augmentation de l'évapotranspiration
     * @param descriptionFr         Synthèse textuelle
     */
    data class DRIASProjection(
        val zone: ClimateZone,
        val deltaTSsp245_2050: Double,
        val deltaTSsp585_2050: Double,
        val deltaPrecipSsp245Pct: Double,
        val deltaPrecipSsp585Pct: Double,
        val droughtRiskSsp245: DroughtRisk,
        val droughtRiskSsp585: DroughtRisk,
        val joursChaudsSsp245: Int,
        val joursChaudsSsp585: Int,
        val joursGelPerdus: Int,
        val deltaEtpPct: Double,
        val descriptionFr: String
    ) {
        val deltaTempC2050: Double get() = deltaTSsp585_2050
        val deltaT2050_ssp585: Double get() = deltaTSsp585_2050
        val deltaPrecipPct2050: Double get() = deltaPrecipSsp585Pct
        val deltaPsummer2050_ssp585: Double get() = deltaPrecipSsp585Pct
        val droughtRisk2050: DroughtRisk get() = droughtRiskSsp585
        val nbJoursChauds2050: Int get() = joursChaudsSsp585
        val syntheseTexte: String get() = descriptionFr
        val droughtDays2050: Int get() = joursChaudsSsp585

        fun stressIndex(): Double {
            val tempFactor = deltaTSsp585_2050 / 4.0
            val precipFactor = -deltaPrecipSsp585Pct / 20.0
            val etpFactor = deltaEtpPct / 30.0
            return ((tempFactor + precipFactor + etpFactor) / 3.0).coerceIn(0.0, 1.0)
        }

        fun labelSsp245(): String = "+${deltaTSsp245_2050}°C / précip. ${deltaPrecipSsp245Pct.toInt()}% (SSP2-4.5)"
        fun labelSsp585(): String = "+${deltaTSsp585_2050}°C / précip. ${deltaPrecipSsp585Pct.toInt()}% (SSP5-8.5)"
    }

    private val PROJECTIONS: Map<ClimateZone, DRIASProjection> = mapOf(

        ClimateZone.ATLANTIQUE to DRIASProjection(
            zone = ClimateZone.ATLANTIQUE,
            deltaTSsp245_2050 = 1.4, deltaTSsp585_2050 = 1.9,
            deltaPrecipSsp245Pct = -5.0, deltaPrecipSsp585Pct = -9.0,
            droughtRiskSsp245 = DroughtRisk.FAIBLE, droughtRiskSsp585 = DroughtRisk.MODERE,
            joursChaudsSsp245 = 10, joursChaudsSsp585 = 22,
            joursGelPerdus = 8, deltaEtpPct = 7.0,
            descriptionFr = "Étés plus chauds, pluviométrie estivale légèrement en baisse. Côte atlantique moins exposée."
        ),

        ClimateZone.SEMI_OCEANIQUE to DRIASProjection(
            zone = ClimateZone.SEMI_OCEANIQUE,
            deltaTSsp245_2050 = 1.7, deltaTSsp585_2050 = 2.3,
            deltaPrecipSsp245Pct = -8.0, deltaPrecipSsp585Pct = -14.0,
            droughtRiskSsp245 = DroughtRisk.MODERE, droughtRiskSsp585 = DroughtRisk.FORT,
            joursChaudsSsp245 = 18, joursChaudsSsp585 = 32,
            joursGelPerdus = 12, deltaEtpPct = 12.0,
            descriptionFr = "Sécheresses estivales plus fréquentes et intenses. Zone clé de transition bioclimatique."
        ),

        ClimateZone.MEDITERRANEENNE to DRIASProjection(
            zone = ClimateZone.MEDITERRANEENNE,
            deltaTSsp245_2050 = 2.1, deltaTSsp585_2050 = 2.9,
            deltaPrecipSsp245Pct = -12.0, deltaPrecipSsp585Pct = -20.0,
            droughtRiskSsp245 = DroughtRisk.FORT, droughtRiskSsp585 = DroughtRisk.TRES_FORT,
            joursChaudsSsp245 = 35, joursChaudsSsp585 = 55,
            joursGelPerdus = 20, deltaEtpPct = 22.0,
            descriptionFr = "Stress hydrique sévère, saison sèche allongée. Risque incendie majeur amplifié."
        ),

        ClimateZone.CONTINENTALE to DRIASProjection(
            zone = ClimateZone.CONTINENTALE,
            deltaTSsp245_2050 = 1.8, deltaTSsp585_2050 = 2.5,
            deltaPrecipSsp245Pct = -6.0, deltaPrecipSsp585Pct = -11.0,
            droughtRiskSsp245 = DroughtRisk.MODERE, droughtRiskSsp585 = DroughtRisk.FORT,
            joursChaudsSsp245 = 22, joursChaudsSsp585 = 38,
            joursGelPerdus = 15, deltaEtpPct = 14.0,
            descriptionFr = "Amplification des extrêmes thermiques. Hivers plus doux mais étés plus secs."
        ),

        ClimateZone.MONTAGNARDE to DRIASProjection(
            zone = ClimateZone.MONTAGNARDE,
            deltaTSsp245_2050 = 1.5, deltaTSsp585_2050 = 2.1,
            deltaPrecipSsp245Pct = -3.0, deltaPrecipSsp585Pct = -7.0,
            droughtRiskSsp245 = DroughtRisk.FAIBLE, droughtRiskSsp585 = DroughtRisk.MODERE,
            joursChaudsSsp245 = 8, joursChaudsSsp585 = 17,
            joursGelPerdus = 18, deltaEtpPct = 9.0,
            descriptionFr = "Remontée des étages de végétation. Fonte neige précoce et stress printanier accru."
        ),

        ClimateZone.UNKNOWN to DRIASProjection(
            zone = ClimateZone.UNKNOWN,
            deltaTSsp245_2050 = 1.7, deltaTSsp585_2050 = 2.3,
            deltaPrecipSsp245Pct = -7.0, deltaPrecipSsp585Pct = -12.0,
            droughtRiskSsp245 = DroughtRisk.MODERE, droughtRiskSsp585 = DroughtRisk.FORT,
            joursChaudsSsp245 = 18, joursChaudsSsp585 = 30,
            joursGelPerdus = 13, deltaEtpPct = 11.0,
            descriptionFr = "Projection médiane France — localisation non résolue."
        )
    )

    fun getProjection(zone: ClimateZone): DRIASProjection =
        PROJECTIONS[zone]
            ?: PROJECTIONS[ClimateZone.UNKNOWN]
            ?: error("DRIASDatabase: projection manquante pour zone=$zone et UNKNOWN absent")

    fun computeVulnerabilityScore(zone: ClimateZone): Float {
        val p = getProjection(zone)
        val base = when (p.droughtRiskSsp585) {
            DroughtRisk.FAIBLE    -> 20f
            DroughtRisk.MODERE    -> 45f
            DroughtRisk.FORT      -> 68f
            DroughtRisk.TRES_FORT -> 88f
        }
        val etpBonus = (p.deltaEtpPct / 30.0 * 12.0).toFloat()
        return (base + etpBonus).coerceIn(0f, 100f)
    }

    fun computeEssenceVulnerabilityScore(
        zone: ClimateZone,
        droughtSensitivity: Int,
        frostSensitivity: Int
    ): Int {
        val p = getProjection(zone)
        val droughtScore = droughtSensitivity * 8 *
            when (p.droughtRiskSsp585) {
                DroughtRisk.FAIBLE    -> 0.5
                DroughtRisk.MODERE    -> 1.0
                DroughtRisk.FORT      -> 1.5
                DroughtRisk.TRES_FORT -> 2.0
            }
        val frostScore = frostSensitivity * p.joursGelPerdus * 0.3
        return (droughtScore + frostScore).roundToInt().coerceIn(0, 100)
    }

    fun droughtResistantEssences(zone: ClimateZone): List<String> = when (zone) {
        ClimateZone.MEDITERRANEENNE -> listOf("Chêne vert", "Cèdre de l'Atlas", "Chêne pubescent", "Pin d'Alep", "Chêne liège")
        ClimateZone.SEMI_OCEANIQUE  -> listOf("Chêne sessile", "Douglas", "Cèdre de l'Atlas", "Pin laricio", "Chêne pubescent")
        ClimateZone.CONTINENTALE    -> listOf("Pin sylvestre", "Mélèze d'Europe", "Chêne sessile", "Robinier faux-acacia")
        ClimateZone.MONTAGNARDE     -> listOf("Mélèze d'Europe", "Pin sylvestre", "Sapin de Nordmann", "Douglas")
        ClimateZone.ATLANTIQUE      -> listOf("Chêne sessile", "Chêne pubescent", "Douglas", "Pin maritime")
        else -> listOf("Chêne sessile", "Douglas", "Mélèze d'Europe", "Cèdre de l'Atlas")
    }

    fun generateRisques(zone: ClimateZone): List<String> {
        val p = getProjection(zone)
        return listOf(
            "SSP2-4.5 : ${p.labelSsp245()} · SSP5-8.5 : ${p.labelSsp585()}",
            "${p.joursChaudsSsp585} jours supplémentaires >30°C/an (SSP5-8.5)",
            "ETP estivale en hausse de +${p.deltaEtpPct.toInt()}% — stress hydrique amplifié",
            "Gel printanier : −${p.joursGelPerdus} jours — risque de débourrement précoce",
            when (p.droughtRiskSsp585) {
                DroughtRisk.TRES_FORT -> "Risque sécheresse TRÈS FORT — diversification urgente"
                DroughtRisk.FORT      -> "Risque sécheresse FORT — adapter le peuplement"
                DroughtRisk.MODERE    -> "Risque sécheresse MODÉRÉ — surveiller les essences sensibles"
                DroughtRisk.FAIBLE    -> "Risque sécheresse faible à moyen terme"
            }
        )
    }
}
