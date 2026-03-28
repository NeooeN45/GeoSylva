package com.forestry.counter.domain.usecase.correlateur

import com.forestry.counter.domain.model.ClimateZone
import kotlin.math.roundToInt

/**
 * Base de données DRIAS simplifiée — projections climatiques par zone bioclimatique française.
 *
 * Valeurs issues des scenarios DRIAS 2021 (CNRM-CM6-1, IPSL-CM6A-LR) pour les scénarios
 * SSP2-4.5 et SSP5-8.5 (équivalents RCP4.5 et RCP8.5), moyennées sur chaque domaine
 * bioclimatique français. Précision : mailles de 8 km, valeurs représentatives de zone.
 *
 * Source de référence : https://drias-prod.meteo.fr/
 */
object DRIASDatabase {

    /** Niveau de risque sécheresse */
    enum class DroughtRisk(val labelFr: String, val colorHex: Long) {
        FAIBLE("Faible", 0xFF43A047),
        MODERE("Modéré", 0xFFF9A825),
        FORT("Fort", 0xFFEF6C00),
        TRES_FORT("Très fort", 0xFFC62828)
    }

    /** Scénario SSP utilisé pour les projections */
    enum class SSPScenario(val label: String) {
        SSP245("SSP2-4.5 (optimiste)"),
        SSP585("SSP5-8.5 (pessimiste)")
    }

    /**
     * Projection climatique pour une zone et un horizon temporel donnés.
     * Toutes les anomalies sont relatives à la période de référence 1981-2010.
     */
    data class DRIASProjection(
        val zone: ClimateZone,
        /** Anomalie de température annuelle à +2050 en °C (SSP4.5 / SSP8.5) */
        val deltaT2050_ssp245: Double,
        val deltaT2050_ssp585: Double,
        /** Anomalie de température annuelle à +2100 en °C */
        val deltaT2100_ssp245: Double,
        val deltaT2100_ssp585: Double,
        /** Anomalie de précipitation estivale à +2050 en % (négatif = déficit) */
        val deltaPsummer2050_ssp245: Double,
        val deltaPsummer2050_ssp585: Double,
        val deltaPsummer2100_ssp245: Double,
        val deltaPsummer2100_ssp585: Double,
        /** Anomalie de précipitation annuelle à +2050 en % */
        val deltaPannual2050_ssp245: Double,
        val deltaPannual2050_ssp585: Double,
        /** Jours de sécheresse sévère (SWI < 0.1) par an — horizon 2050, SSP8.5 */
        val droughtDays2050: Int,
        /** Jours de chaleur extrême (Tmax > 35°C) par an — horizon 2050, SSP8.5 */
        val extremeHeatDays2050: Int,
        /** Nombre d'épisodes de canicule (≥ 3 jours consécutifs > 33°C) par an en 2050 */
        val heatwaveEpisodes2050: Int,
        /** Risque sécheresse estimé à l'horizon 2050 (SSP8.5) */
        val droughtRisk2050: DroughtRisk,
        /** Risque sécheresse estimé à l'horizon 2100 (SSP8.5) */
        val droughtRisk2100: DroughtRisk,
        /** Réduction estimée du manteau neigeux en % (zones de montagne uniquement) */
        val snowpackReduction2050Pct: Int?,
        /** Note de vulnérabilité incendie (1–5, 5 = extrême) */
        val fireRisk2050: Int,
        /** Résumé textuel pour l'affichage utilisateur */
        val syntheseTexte: String
    )

    // ── Projections par zone bioclimatique ────────────────────────────────────

    private val projections: Map<ClimateZone, DRIASProjection> = mapOf(

        ClimateZone.ATLANTIQUE to DRIASProjection(
            zone                    = ClimateZone.ATLANTIQUE,
            deltaT2050_ssp245       = 1.4,
            deltaT2050_ssp585       = 1.8,
            deltaT2100_ssp245       = 2.1,
            deltaT2100_ssp585       = 3.9,
            deltaPsummer2050_ssp245 = -8.0,
            deltaPsummer2050_ssp585 = -13.0,
            deltaPsummer2100_ssp245 = -12.0,
            deltaPsummer2100_ssp585 = -22.0,
            deltaPannual2050_ssp245 = +2.0,
            deltaPannual2050_ssp585 = +1.0,
            droughtDays2050         = 28,
            extremeHeatDays2050     = 8,
            heatwaveEpisodes2050    = 2,
            droughtRisk2050         = DroughtRisk.MODERE,
            droughtRisk2100         = DroughtRisk.FORT,
            snowpackReduction2050Pct = null,
            fireRisk2050            = 2,
            syntheseTexte           =
                "Zone Atlantique : réchauffement modéré (+1.8°C/2050 SSP8.5), déficit estival croissant " +
                "(-13% précipitations en été). Canicules en augmentation. Impact sur le hêtre et le " +
                "chêne pédonculé en bas de versant. Chêne sessile et douglas favorisés."
        ),

        ClimateZone.SEMI_OCEANIQUE to DRIASProjection(
            zone                    = ClimateZone.SEMI_OCEANIQUE,
            deltaT2050_ssp245       = 1.6,
            deltaT2050_ssp585       = 2.1,
            deltaT2100_ssp245       = 2.4,
            deltaT2100_ssp585       = 4.5,
            deltaPsummer2050_ssp245 = -14.0,
            deltaPsummer2050_ssp585 = -22.0,
            deltaPsummer2100_ssp245 = -20.0,
            deltaPsummer2100_ssp585 = -35.0,
            deltaPannual2050_ssp245 = 0.0,
            deltaPannual2050_ssp585 = -3.0,
            droughtDays2050         = 45,
            extremeHeatDays2050     = 15,
            heatwaveEpisodes2050    = 3,
            droughtRisk2050         = DroughtRisk.FORT,
            droughtRisk2100         = DroughtRisk.TRES_FORT,
            snowpackReduction2050Pct = null,
            fireRisk2050            = 3,
            syntheseTexte           =
                "Zone Semi-océanique : réchauffement sensible (+2.1°C/2050 SSP8.5), déficit estival " +
                "marqué (-22%). Sécheresses estivales en forte augmentation. Dépérissement du hêtre " +
                "et de l'épicéa attendu. Orienter vers chêne sessile, charme, douglas et tilleul."
        ),

        ClimateZone.CONTINENTALE to DRIASProjection(
            zone                    = ClimateZone.CONTINENTALE,
            deltaT2050_ssp245       = 1.8,
            deltaT2050_ssp585       = 2.4,
            deltaT2100_ssp245       = 2.7,
            deltaT2100_ssp585       = 5.1,
            deltaPsummer2050_ssp245 = -12.0,
            deltaPsummer2050_ssp585 = -20.0,
            deltaPsummer2100_ssp245 = -18.0,
            deltaPsummer2100_ssp585 = -32.0,
            deltaPannual2050_ssp245 = +3.0,
            deltaPannual2050_ssp585 = +1.0,
            droughtDays2050         = 38,
            extremeHeatDays2050     = 18,
            heatwaveEpisodes2050    = 3,
            droughtRisk2050         = DroughtRisk.FORT,
            droughtRisk2100         = DroughtRisk.TRES_FORT,
            snowpackReduction2050Pct = null,
            fireRisk2050            = 3,
            syntheseTexte           =
                "Zone Continentale : réchauffement fort (+2.4°C/2050 SSP8.5), hivers moins froids, " +
                "étés plus secs (-20% été). Amplitudes thermiques accrues. Vulnérabilité épicéa " +
                "et sapin pectiné. Favoriser douglas, cèdre de l'Atlas et chêne pubescent."
        ),

        ClimateZone.MONTAGNARDE to DRIASProjection(
            zone                    = ClimateZone.MONTAGNARDE,
            deltaT2050_ssp245       = 1.5,
            deltaT2050_ssp585       = 2.0,
            deltaT2100_ssp245       = 2.2,
            deltaT2100_ssp585       = 4.2,
            deltaPsummer2050_ssp245 = -6.0,
            deltaPsummer2050_ssp585 = -10.0,
            deltaPsummer2100_ssp245 = -10.0,
            deltaPsummer2100_ssp585 = -18.0,
            deltaPannual2050_ssp245 = +2.0,
            deltaPannual2050_ssp585 = +0.0,
            droughtDays2050         = 18,
            extremeHeatDays2050     = 5,
            heatwaveEpisodes2050    = 1,
            droughtRisk2050         = DroughtRisk.MODERE,
            droughtRisk2100         = DroughtRisk.FORT,
            snowpackReduction2050Pct = 35,
            fireRisk2050            = 2,
            syntheseTexte           =
                "Zone Montagnarde : réchauffement modéré en altitude (+2.0°C/2050 SSP8.5) mais " +
                "réduction significative du manteau neigeux (-35% en 2050). Remontée des étages " +
                "de végétation. Sapin pectiné sous pression aux expositions sud. Favoriser " +
                "le mélèze et le pin cembro en altitude, hêtre et douglas en étage montagnard bas."
        ),

        ClimateZone.MEDITERRANEENNE to DRIASProjection(
            zone                    = ClimateZone.MEDITERRANEENNE,
            deltaT2050_ssp245       = 2.0,
            deltaT2050_ssp585       = 2.8,
            deltaT2100_ssp245       = 3.0,
            deltaT2100_ssp585       = 6.2,
            deltaPsummer2050_ssp245 = -20.0,
            deltaPsummer2050_ssp585 = -30.0,
            deltaPsummer2100_ssp245 = -28.0,
            deltaPsummer2100_ssp585 = -45.0,
            deltaPannual2050_ssp245 = -8.0,
            deltaPannual2050_ssp585 = -14.0,
            droughtDays2050         = 90,
            extremeHeatDays2050     = 40,
            heatwaveEpisodes2050    = 8,
            droughtRisk2050         = DroughtRisk.TRES_FORT,
            droughtRisk2100         = DroughtRisk.TRES_FORT,
            snowpackReduction2050Pct = null,
            fireRisk2050            = 5,
            syntheseTexte           =
                "Zone Méditerranéenne : scénario le plus préoccupant (+2.8°C/2050 SSP8.5, " +
                "-30% précipitations estivales). Aridification progressive. Risque incendie " +
                "extrême. Sécheresses estivales sévères > 90 j/an. Favoriser absolument les " +
                "essences xérophytes : chêne vert, chêne liège, pin d'Alep, cèdre de l'Atlas."
        ),

        ClimateZone.UNKNOWN to DRIASProjection(
            zone                    = ClimateZone.UNKNOWN,
            deltaT2050_ssp245       = 1.5,
            deltaT2050_ssp585       = 2.0,
            deltaT2100_ssp245       = 2.2,
            deltaT2100_ssp585       = 4.0,
            deltaPsummer2050_ssp245 = -12.0,
            deltaPsummer2050_ssp585 = -18.0,
            deltaPsummer2100_ssp245 = -16.0,
            deltaPsummer2100_ssp585 = -28.0,
            deltaPannual2050_ssp245 = 0.0,
            deltaPannual2050_ssp585 = -2.0,
            droughtDays2050         = 35,
            extremeHeatDays2050     = 12,
            heatwaveEpisodes2050    = 2,
            droughtRisk2050         = DroughtRisk.MODERE,
            droughtRisk2100         = DroughtRisk.FORT,
            snowpackReduction2050Pct = null,
            fireRisk2050            = 2,
            syntheseTexte           =
                "Zone non déterminée : projections moyennées sur l'ensemble de la France métropolitaine. " +
                "Renseignez les coordonnées GPS pour des projections DRIAS précises."
        )
    )

    /** Retourne la projection DRIAS pour une zone bioclimatique. Ne retourne jamais null. */
    fun getProjection(zone: ClimateZone): DRIASProjection =
        projections[zone] ?: projections[ClimateZone.UNKNOWN]!!

    /**
     * Calcule un score de vulnérabilité climatique (0–100, 100 = très vulnérable).
     * Basé sur le ΔT 2050 SSP8.5, le déficit estival et les jours de sécheresse.
     */
    fun computeVulnerabilityScore(zone: ClimateZone): Int {
        val p = getProjection(zone)
        val tScore = ((p.deltaT2050_ssp585 / 4.0) * 35).coerceIn(0.0, 35.0)
        val pScore = (((-p.deltaPsummer2050_ssp585) / 50.0) * 35).coerceIn(0.0, 35.0)
        val dScore = ((p.droughtDays2050 / 120.0) * 30).coerceIn(0.0, 30.0)
        return (tScore + pScore + dScore).roundToInt()
    }

    /**
     * Retourne les essences résistantes à la sécheresse recommandées par DRIAS
     * pour le niveau de risque estimé à l'horizon 2050.
     */
    fun droughtResistantEssences(zone: ClimateZone): List<String> {
        val p = getProjection(zone)
        return when (p.droughtRisk2050) {
            DroughtRisk.TRES_FORT -> listOf(
                "Chêne vert (Quercus ilex)",
                "Chêne liège (Quercus suber)",
                "Pin d'Alep (Pinus halepensis)",
                "Cèdre de l'Atlas (Cedrus atlantica)",
                "Chêne pubescent (Quercus pubescens)"
            )
            DroughtRisk.FORT -> listOf(
                "Chêne pubescent (Quercus pubescens)",
                "Cèdre de l'Atlas (Cedrus atlantica)",
                "Chêne sessile (Quercus petraea)",
                "Douglas (Pseudotsuga menziesii)",
                "Pin laricio (Pinus laricio)"
            )
            DroughtRisk.MODERE -> listOf(
                "Chêne sessile (Quercus petraea)",
                "Douglas (Pseudotsuga menziesii)",
                "Charme (Carpinus betulus)",
                "Tilleul (Tilia cordata)",
                "Alisier torminal (Sorbus torminalis)"
            )
            DroughtRisk.FAIBLE -> listOf(
                "Chêne sessile (Quercus petraea)",
                "Hêtre (Fagus sylvatica)",
                "Douglas (Pseudotsuga menziesii)",
                "Merisier (Prunus avium)"
            )
        }
    }

    /**
     * Génère une liste de risques climatiques DRIAS à l'horizon 2050 pour une zone.
     */
    fun generateRisques(zone: ClimateZone): List<String> {
        val p = getProjection(zone)
        val risks = mutableListOf<String>()

        if (p.deltaT2050_ssp585 >= 2.5)
            risks += "Réchauffement fort (+${p.deltaT2050_ssp585}°C en 2050 SSP8.5) : remontée des étages de végétation"
        else if (p.deltaT2050_ssp585 >= 1.5)
            risks += "Réchauffement modéré (+${p.deltaT2050_ssp585}°C en 2050 SSP8.5)"

        if (p.deltaPsummer2050_ssp585 <= -25)
            risks += "Déficit estival sévère (${p.deltaPsummer2050_ssp585.toInt()}% en 2050 SSP8.5) : stress hydrique estival intense"
        else if (p.deltaPsummer2050_ssp585 <= -15)
            risks += "Déficit estival marqué (${p.deltaPsummer2050_ssp585.toInt()}% en 2050 SSP8.5)"

        if (p.droughtDays2050 >= 60)
            risks += "Sécheresses fréquentes : ~${p.droughtDays2050} jours/an de sécheresse sévère en 2050"
        else if (p.droughtDays2050 >= 30)
            risks += "Sécheresses estivales accrues : ~${p.droughtDays2050} jours/an en 2050"

        if (p.extremeHeatDays2050 >= 30)
            risks += "Chaleurs extrêmes (>35°C) très fréquentes : ~${p.extremeHeatDays2050} jours/an en 2050"
        else if (p.extremeHeatDays2050 >= 10)
            risks += "Épisodes caniculaires accrus : ~${p.extremeHeatDays2050} jours/an > 35°C en 2050"

        if (p.heatwaveEpisodes2050 >= 5)
            risks += "Canicules répétées : ${p.heatwaveEpisodes2050} épisodes/an projetés en 2050"

        if (p.fireRisk2050 >= 4)
            risks += "Risque incendie critique (indice DRIAS : ${p.fireRisk2050}/5)"
        else if (p.fireRisk2050 >= 3)
            risks += "Risque incendie élevé en projection 2050 (indice : ${p.fireRisk2050}/5)"

        p.snowpackReduction2050Pct?.let { snow ->
            if (snow >= 25)
                risks += "Réduction importante du manteau neigeux (-$snow% en 2050) : impact sur les régimes hydriques printaniers"
        }

        return risks.ifEmpty { listOf("Risque climatique modéré d'après les projections DRIAS 2021") }
    }
}

