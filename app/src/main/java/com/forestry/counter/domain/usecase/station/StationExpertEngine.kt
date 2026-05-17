package com.forestry.counter.domain.usecase.station

import com.forestry.counter.domain.model.ClimateZone
import com.forestry.counter.domain.model.station.StationObservation
import com.forestry.counter.domain.usecase.autecology.DRIASDatabase

/**
 * Moteur expert de diagnostic stationnel — enrichit StationDiagnosticEngine
 * avec les recommandations SSP245/SSP585, stress hydrique ETP et
 * adaptation sylvicole aux projections GIEC CMIP6.
 */
object StationExpertEngine {

    data class ExpertRecommandation(
        val titre: String,
        val description: String,
        val horizon: Horizon,
        val scenarioCible: Scenario,
        val priorite: Int
    )

    data class ExpertDiagnostic(
        val recommandations: List<ExpertRecommandation>,
        val alertesSsp585: List<String>,
        val synthese: String,
        val stressIndexSsp245: Double,
        val stressIndexSsp585: Double,
        val essencesASubstituer: List<String>,
        val essencesFuturOptimal: List<String>
    )

    enum class Horizon { H2035, H2050, H2070 }
    enum class Scenario { SSP245, SSP585, BOTH }

    fun buildExpertDiagnostic(
        zone: ClimateZone,
        station: StationObservation?,
        essencesDominantes: List<String>
    ): ExpertDiagnostic {
        val proj = DRIASDatabase.getProjection(zone)
        val recos = mutableListOf<ExpertRecommandation>()

        val stressIdx245 = computeStress245(proj)
        val stressIdx585 = proj.stressIndex()

        if (stressIdx585 > 0.6) {
            recos.add(ExpertRecommandation(
                titre = "Diversifier les essences dès maintenant — éviter les monocultures",
                description = "Stress SSP5-8.5 : ${(stressIdx585 * 100).toInt()}%. En scénario haut, la viabilité des peuplements mono-spécifiques est compromise avant 2050.",
                horizon = Horizon.H2050,
                scenarioCible = Scenario.SSP585,
                priorite = 1
            ))
        }

        if (proj.droughtRiskSsp585 in listOf(DRIASDatabase.DroughtRisk.FORT, DRIASDatabase.DroughtRisk.TRES_FORT)) {
            recos.add(ExpertRecommandation(
                titre = "Introduire des essences méridionales résistantes à la sécheresse",
                description = "Précipitations estivales : ${proj.deltaPrecipSsp585Pct.toInt()}% (SSP5-8.5). Cèdre de l'Atlas, chêne pubescent, pin laricio adaptés à la zone projetée.",
                horizon = Horizon.H2050,
                scenarioCible = Scenario.SSP585,
                priorite = 1
            ))
            recos.add(ExpertRecommandation(
                titre = "Adapter les densités (éclaircies préventives) pour réduire la compétition hydrique",
                description = "ETP estivale +${proj.deltaEtpPct.toInt()}% — réduire la densité de 20–30% avant les sécheresses structurelles.",
                horizon = Horizon.H2035,
                scenarioCible = Scenario.BOTH,
                priorite = 2
            ))
        }

        if (proj.joursGelPerdus >= 12) {
            recos.add(ExpertRecommandation(
                titre = "Anticiper le risque de gel tardif sur débourrement précoce",
                description = "−${proj.joursGelPerdus} jours de gel à 2050. Les essences à débourrement précoce (frêne, chêne pédonculé) sont exposées aux gelées tardives plus fréquentes.",
                horizon = Horizon.H2050,
                scenarioCible = Scenario.BOTH,
                priorite = 2
            ))
        }

        if (proj.joursChaudsSsp585 > 30) {
            recos.add(ExpertRecommandation(
                titre = "Préparer la substitution progressive de : ${essencesDominantes.take(2).joinToString(", ")}",
                description = "+${proj.joursChaudsSsp585} jours >30°C/an en SSP5-8.5. Planifier la conversion sur 2 rotations pour les essences les plus sensibles.",
                horizon = Horizon.H2070,
                scenarioCible = Scenario.SSP585,
                priorite = 3
            ))
        }

        station?.let { st ->
            if (st.profondeurSolCm != null && st.profondeurSolCm < 40 &&
                proj.droughtRiskSsp245 != DRIASDatabase.DroughtRisk.FAIBLE) {
                recos.add(ExpertRecommandation(
                    titre = "Sol superficiel × sécheresse — risque cumulé",
                    description = "Profondeur ${st.profondeurSolCm} cm + sécheresse croissante. Réserve utile très limitée. Favoriser les essences à enracinement pivotant profond.",
                    horizon = Horizon.H2035,
                    scenarioCible = Scenario.BOTH,
                    priorite = 1
                ))
            }
        }

        val alertesSsp585 = buildAlertsSsp585(proj, zone)
        val essencesSubst = DRIASDatabase.droughtResistantEssences(zone).take(3)
        val essencesFutur = buildFutureEssences(zone, proj)

        return ExpertDiagnostic(
            recommandations = recos.sortedBy { it.priorite },
            alertesSsp585 = alertesSsp585,
            synthese = buildSynthese(proj, stressIdx585, recos),
            stressIndexSsp245 = stressIdx245,
            stressIndexSsp585 = stressIdx585,
            essencesASubstituer = essencesDominantes.take(2),
            essencesFuturOptimal = essencesFutur
        )
    }

    private fun computeStress245(proj: DRIASDatabase.DRIASProjection): Double {
        val tempFactor = proj.deltaTSsp245_2050 / 4.0
        val precipFactor = -proj.deltaPrecipSsp245Pct / 20.0
        val etpFactor = proj.deltaEtpPct / 30.0 * 0.7
        return ((tempFactor + precipFactor + etpFactor) / 3.0).coerceIn(0.0, 1.0)
    }

    private fun buildAlertsSsp585(
        proj: DRIASDatabase.DRIASProjection,
        zone: ClimateZone
    ): List<String> {
        val alerts = mutableListOf<String>()
        alerts.add("SSP5-8.5 : ${proj.labelSsp585()}")
        alerts.add("+${proj.joursChaudsSsp585} jours >30°C/an à 2050")
        alerts.add("ETP estivale +${proj.deltaEtpPct.toInt()}% — bilan hydrique fortement déficitaire")
        if (proj.droughtRiskSsp585 == DRIASDatabase.DroughtRisk.TRES_FORT) {
            alerts.add("Risque sécheresse TRÈS FORT — conversion sylvicole urgente recommandée")
        }
        if (zone == ClimateZone.MEDITERRANEENNE) {
            alerts.add("Zone méditerranéenne : risque incendie amplifié — maintenir DFCI")
        }
        return alerts
    }

    private fun buildFutureEssences(
        zone: ClimateZone,
        proj: DRIASDatabase.DRIASProjection
    ): List<String> {
        val futureZone = when (zone) {
            ClimateZone.SEMI_OCEANIQUE -> ClimateZone.MEDITERRANEENNE
            ClimateZone.CONTINENTALE  -> ClimateZone.SEMI_OCEANIQUE
            ClimateZone.MONTAGNARDE   -> ClimateZone.SEMI_OCEANIQUE
            else -> zone
        }
        return DRIASDatabase.droughtResistantEssences(futureZone)
    }

    private fun buildSynthese(
        proj: DRIASDatabase.DRIASProjection,
        stressIdx: Double,
        recos: List<ExpertRecommandation>
    ): String {
        val level = when {
            stressIdx > 0.7 -> "stress climatique ÉLEVÉ"
            stressIdx > 0.4 -> "stress climatique MODÉRÉ"
            else -> "stress climatique FAIBLE"
        }
        val urgentes = recos.count { it.priorite == 1 }
        return "Projection DRIAS 2050 : $level (indice ${(stressIdx * 100).toInt()}%). " +
            "${proj.labelSsp585()}. $urgentes action(s) prioritaire(s) identifiée(s)."
    }
}
