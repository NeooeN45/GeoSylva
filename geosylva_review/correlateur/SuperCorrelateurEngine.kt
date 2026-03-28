package com.forestry.counter.domain.usecase.correlateur

import com.forestry.counter.domain.model.ClimateZone
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.model.ripisylve.RipisylveObservation
import com.forestry.counter.domain.model.ripisylve.RipisylveScore
import com.forestry.counter.domain.model.station.Drainage
import com.forestry.counter.domain.model.station.StationObservation
import com.forestry.counter.domain.usecase.autecology.AutecologyDatabase
import com.forestry.counter.domain.usecase.autecology.CompatibilityLevel
import com.forestry.counter.domain.usecase.autecology.EssenceAutecology
import com.forestry.counter.domain.usecase.ripisylve.RipisylveScorer
import com.forestry.counter.domain.usecase.station.StationDiagnosticEngine
import kotlin.math.roundToInt

/**
 * Moteur central de corrélation multi-sources GeoSylva.
 *
 * Synthétise :
 *  - Données GPS / zone bioclimatique
 *  - Inventaire dendromèrique (tiges)
 *  - Diagnostic stationnel
 *  - Diagnostic ripisylve
 *
 * Pour produire :
 *  - Un bilan global de la parcelle
 *  - Des recommandations d'essences adaptées au climat futur
 *  - Des alertes de risques climatiques et sanitaires
 *  - Un plan d'action sylvicole priorisé
 *  - Un score de résilience écosystémique
 */
object SuperCorrelateurEngine {

    // ─────────────────────────────────────────────────────────────────────────
    //  Résultat de la corrélation complète
    // ─────────────────────────────────────────────────────────────────────────

    data class CorrelateurResult(
        val score: ResilienceScore,
        val climateZone: ClimateZone,
        val risquesClimatiques: List<RisqueClimatique>,
        val essencesBilan: List<EssenceBilan>,
        val essencesRecommandeesFutur: List<EssenceRecommandation>,
        val planActionSylvicole: List<ActionSylvicole>,
        val alertesSanitaires: List<AlerteSanitaire>,
        val synthese: String,
        val datasources: DataSources,
        val driasProjection: DRIASDatabase.DRIASProjection? = null
    )

    data class ResilienceScore(
        val global: Int,            // 0–100
        val dendro: Int?,           // score dendro (densité, diversité, structure)
        val station: Int?,          // score station (contraintes)
        val ripisylve: Int?,        // score ripisylve
        val climatiqueAdaptation: Int, // adéquation essences × zone bioclimatique actuelle
        val label: String,
        val labelFr: String
    )

    data class EssenceBilan(
        val code: String,
        val nameFr: String,
        val nTiges: Int,
        val pctBasalArea: Double,
        val compatibility: CompatibilityLevel,
        val climateChangeResilience: Int,   // 1–5 (données AutecologyDatabase)
        val futureZoneCompatibility: ZoneFutureCompatibility,
        val alertes: List<String>
    )

    enum class ZoneFutureCompatibility(val label: String, val colorHex: Long) {
        OPTIMAL("Optimal 2050+", 0xFF2E7D32),
        ACCEPTABLE("Acceptable 2050", 0xFF8BC34A),
        MARGINAL("Marginal 2050", 0xFFF9A825),
        AT_RISK("À risque 2050+", 0xFFEF6C00),
        CRITICAL("Critique – reconsidérer", 0xFFC62828)
    }

    data class EssenceRecommandation(
        val nameFr: String,
        val code: String,
        val rationale: String,
        val priority: Int,          // 1=urgent, 2=important, 3=opportuniste
        val minHa: Double?,         // proportion minimale recommandée (0–1)
        val climateResilience: Int  // 1–5
    )

    data class ActionSylvicole(
        val titre: String,
        val description: String,
        val urgence: Urgence,
        val domaine: DomaineSylvicole
    )

    enum class Urgence(val label: String) {
        IMMEDIATE("Immédiate (< 1 an)"),
        COURT_TERME("Court terme (1–3 ans)"),
        MOYEN_TERME("Moyen terme (3–10 ans)"),
        LONG_TERME("Long terme (10 ans+)")
    }

    enum class DomaineSylvicole(val label: String) {
        ECLAIRCIE("Éclaircie / Dépressage"),
        REBOISEMENT("Reboisement / Enrichissement"),
        PROTECTION("Protection sanitaire"),
        RIPISYLVE("Gestion ripisylve"),
        SOLS("Amélioration des sols"),
        BIODIVERSITE("Biodiversité / Habitats")
    }

    data class AlerteSanitaire(
        val titre: String,
        val description: String,
        val niveau: NiveauAlerte,
        val essencesConcernees: List<String>
    )

    enum class NiveauAlerte(val label: String, val colorHex: Long) {
        CRITIQUE("Critique", 0xFFC62828),
        ELEVEE("Élevée", 0xFFEF6C00),
        MODEREE("Modérée", 0xFFF9A825),
        FAIBLE("Faible", 0xFF8BC34A)
    }

    data class DataSources(
        val hasDendro: Boolean,
        val hasStation: Boolean,
        val hasRipisylve: Boolean,
        val hasGPS: Boolean,
        val nbTiges: Int,
        val completeness: Int   // 0–100 %
    )

    // ─────────────────────────────────────────────────────────────────────────
    //  Point d'entrée principal
    // ─────────────────────────────────────────────────────────────────────────

    fun correlate(
        tiges: List<Tige> = emptyList(),
        surfaceHa: Double = 1.0,
        station: StationObservation? = null,
        ripisylve: RipisylveObservation? = null,
        lat: Double? = null,
        lon: Double? = null,
        altM: Double? = null,
        essenceNames: Map<String, String> = emptyMap()
    ): CorrelateurResult {

        val climateZone = when {
            lat != null && lon != null -> ClimateZone.detect(lat, lon, altM)
            station?.latitude != null && station.longitude != null ->
                ClimateZone.detect(station.latitude, station.longitude, station.altitudeM)
            else -> ClimateZone.UNKNOWN
        }

        val datasources = buildDataSources(tiges, station, ripisylve, lat, lon)

        // ── Scores composants
        val stationResult = station?.let { StationDiagnosticEngine.diagnose(it) }
        val ripisylveScore = ripisylve?.let { RipisylveScorer.score(it, tiges) }

        val dendroScore    = if (tiges.isNotEmpty()) computeDendroScore(tiges, surfaceHa) else null
        val stationScore   = stationResult?.let { computeStationScore(it) }
        val ripisylveInt   = ripisylveScore?.scoreTotal

        // ── Bilan par essence
        val essencesBilan = computeEssencesBilan(
            tiges, surfaceHa, station, climateZone, essenceNames
        )

        // ── Risques climatiques
        val risques = computeRisquesClimatiques(
            tiges, climateZone, station, stationResult, essencesBilan
        )

        // ── Essences recommandées pour le futur
        val essencesReco = computeRecommandationsFutur(
            climateZone, station, essencesBilan
        )

        // ── Plan d'action
        val planAction = buildPlanAction(
            tiges, surfaceHa, station, stationResult, ripisylveScore,
            essencesBilan, risques, climateZone
        )

        // ── Alertes sanitaires
        val alertes = buildAlertesSanitaires(tiges, climateZone, stationResult)

        // ── Projection DRIAS
        val driasProjection = if (climateZone != ClimateZone.UNKNOWN) DRIASDatabase.getProjection(climateZone) else null

        // ── Score résilience global
        val resilienceScore = computeResilienceScore(
            dendroScore, stationScore, ripisylveInt, essencesBilan, climateZone
        )

        return CorrelateurResult(
            score                     = resilienceScore,
            climateZone               = climateZone,
            risquesClimatiques        = risques,
            essencesBilan             = essencesBilan,
            essencesRecommandeesFutur = essencesReco,
            planActionSylvicole       = planAction,
            alertesSanitaires         = alertes,
            synthese                  = buildSynthese(resilienceScore, climateZone, risques, planAction),
            datasources               = datasources,
            driasProjection           = driasProjection
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Score de résilience global
    // ─────────────────────────────────────────────────────────────────────────

    private fun computeResilienceScore(
        dendroScore: Int?,
        stationScore: Int?,
        ripisylveScore: Int?,
        essencesBilan: List<EssenceBilan>,
        climateZone: ClimateZone
    ): ResilienceScore {
        // Score d'adaptation climatique pondéré par la surface terrière (%G)
        val climAdaptScore = if (essencesBilan.isNotEmpty()) {
            val totalWeight = essencesBilan.sumOf { it.pctBasalArea }.coerceAtLeast(1.0)
            essencesBilan
                .sumOf { it.climateChangeResilience * 20.0 * (it.pctBasalArea / totalWeight) }
                .toInt().coerceIn(0, 100)
        } else 50

        val components = listOfNotNull(
            dendroScore,
            stationScore,
            ripisylveScore?.coerceIn(0, 100),
            climAdaptScore
        )
        val global = if (components.isNotEmpty()) components.average().roundToInt() else 50

        val label = when {
            global >= 80 -> "Resilient"
            global >= 60 -> "Moderate"
            global >= 40 -> "Fragile"
            else         -> "Critical"
        }
        val labelFr = when {
            global >= 80 -> "Peuplement résilient"
            global >= 60 -> "Résilience modérée"
            global >= 40 -> "Peuplement fragile"
            else         -> "État critique — intervention urgente"
        }

        return ResilienceScore(global, dendroScore, stationScore, ripisylveScore, climAdaptScore, label, labelFr)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Score dendromètrique (structure, densité, diversité)
    // ─────────────────────────────────────────────────────────────────────────

    private fun isResineuxCode(code: String): Boolean {
        val up = code.uppercase()
        return up.contains("PIN") || up.contains("SAPIN") || up.contains("EPICEA") ||
               up.contains("DOUGLAS") || up.contains("MELEZE") || up.contains("CEDRE") ||
               up.contains("SEQUOIA") || up.contains("THUYA") || up.contains("CYPR")
    }

    private fun computeDendroScore(tiges: List<Tige>, surfaceHa: Double): Int {
        var score = 50
        val nHa = tiges.size / surfaceHa
        val essencesCount = tiges.map { it.essenceCode.uppercase() }.distinct().size
        val withHeight = tiges.count { it.hauteurM != null }

        // Densité raisonnée (300–1500 tiges/ha = optimum)
        when {
            nHa in 300.0..1500.0 -> score += 10
            nHa in 100.0..299.0 || nHa in 1501.0..3000.0 -> score += 3
            nHa < 100 || nHa > 3000 -> score -= 15
        }

        // Diversité spécifique
        score += when {
            essencesCount >= 5 -> 18
            essencesCount >= 4 -> 15
            essencesCount >= 2 -> 8
            else -> 0
        }

        // Données de hauteur disponibles
        if (withHeight >= tiges.size / 2) score += 5

        // Présence de gros bois (biodiversité structurelle)
        val grosBois = tiges.count { it.diamCm >= 40 }
        score += when {
            grosBois >= 5 -> 12
            grosBois >= 3 -> 8
            grosBois >= 1 -> 3
            tiges.size > 20 -> -5   // peuplement dépourvu de gros bois
            else -> 0
        }

        // Élancement moyen — résineux uniquement (H/D non pertinent pour feuillus)
        val slendernesses = tiges.filter { isResineuxCode(it.essenceCode) }.mapNotNull { t ->
            t.hauteurM?.let { h -> if (t.diamCm > 0) (h * 100.0) / t.diamCm else null }
        }
        if (slendernesses.isNotEmpty()) {
            val meanS = slendernesses.average()
            when {
                meanS < 70  -> score += 8   // peuplement stable
                meanS < 90  -> score += 3
                meanS > 120 -> score -= 10  // risque de volis/chablis
                meanS > 100 -> score -= 5
            }
        }

        // Distribution diamétrique : présence de petits bois (régénération/renouvellement)
        val petitsBois = tiges.count { it.diamCm < 20 }
        if (petitsBois > tiges.size * 0.15 && grosBois >= 1) score += 5  // structure étagée

        return score.coerceIn(0, 100)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Score stationnel (0–100, inversé depuis les contraintes)
    // ─────────────────────────────────────────────────────────────────────────

    private fun computeStationScore(result: StationDiagnosticEngine.StationResult): Int {
        var score = 70
        val contrainteScore = { c: StationDiagnosticEngine.Contrainte ->
            when (c) {
                StationDiagnosticEngine.Contrainte.NULLE      -> 0
                StationDiagnosticEngine.Contrainte.FAIBLE     -> -5
                StationDiagnosticEngine.Contrainte.MODEREE    -> -12
                StationDiagnosticEngine.Contrainte.FORTE      -> -20
                StationDiagnosticEngine.Contrainte.TRES_FORTE -> -30
            }
        }
        score += contrainteScore(result.contrainteHydrique)
        score += contrainteScore(result.contrainteTrophique)
        score += contrainteScore(result.contrainteProfondeur)
        if (result.risqueEngorgement) score -= 10
        if (result.risqueDepiecement) score -= 10
        score += result.atouts.size * 5
        score -= result.alertes.size * 8
        return score.coerceIn(0, 100)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Bilan par essence
    // ─────────────────────────────────────────────────────────────────────────

    private fun computeEssencesBilan(
        tiges: List<Tige>,
        surfaceHa: Double,
        station: StationObservation?,
        climateZone: ClimateZone,
        essenceNames: Map<String, String>
    ): List<EssenceBilan> {
        if (tiges.isEmpty()) return emptyList()

        val totalG = tiges.sumOf { Math.PI * (it.diamCm / 200.0) * (it.diamCm / 200.0) }
        val byEssence = tiges.groupBy { it.essenceCode.uppercase() }

        return byEssence.mapNotNull { (code, list) ->
            val auteco = AutecologyDatabase.getByCodeOrName(code) ?: return@mapNotNull null
            val n = list.size
            val g = list.sumOf { Math.PI * (it.diamCm / 200.0) * (it.diamCm / 200.0) }
            val pctG = if (totalG > 0) (g / totalG * 100).roundToInt().toDouble() else 0.0

            val compatibility = if (station != null) {
                StationDiagnosticEngine.evaluateCompatibility(auteco, station).compatibility
            } else CompatibilityLevel.OPTIMUM

            val futureCompat = computeFutureZoneCompatibility(auteco, climateZone)
            val essenceAlerts = buildEssenceAlerts(auteco, station, climateZone)

            EssenceBilan(
                code                    = code,
                nameFr                  = essenceNames[code] ?: auteco.nameFr,
                nTiges                  = n,
                pctBasalArea            = pctG,
                compatibility           = compatibility,
                climateChangeResilience = auteco.climateChangeResilience,
                futureZoneCompatibility = futureCompat,
                alertes                 = essenceAlerts
            )
        }.sortedByDescending { it.pctBasalArea }
    }

    private fun computeFutureZoneCompatibility(
        auteco: EssenceAutecology,
        currentZone: ClimateZone
    ): ZoneFutureCompatibility {
        // Projection simplifiée : en 2050, la zone bioclimatique se décale vers le sud/chaud
        // Zones futures potentielles estimées selon gradient latitudinal
        val projectedZone = when (currentZone) {
            ClimateZone.SEMI_OCEANIQUE   -> ClimateZone.MEDITERRANEENNE   // transition vers Med
            ClimateZone.CONTINENTALE     -> ClimateZone.SEMI_OCEANIQUE
            ClimateZone.MONTAGNARDE      -> ClimateZone.SEMI_OCEANIQUE    // étagement vers le haut
            ClimateZone.ATLANTIQUE       -> ClimateZone.SEMI_OCEANIQUE
            else -> currentZone
        }

        val inOptimalNow    = currentZone  in auteco.optimalZones
        val inOptimalFuture = projectedZone in auteco.optimalZones
        val inAcceptFuture  = projectedZone in auteco.acceptableZones
        val resilience      = auteco.climateChangeResilience

        return when {
            inOptimalNow && inOptimalFuture && resilience >= 4 -> ZoneFutureCompatibility.OPTIMAL
            inOptimalNow && (inOptimalFuture || inAcceptFuture) -> ZoneFutureCompatibility.ACCEPTABLE
            inOptimalNow && !inOptimalFuture && !inAcceptFuture && resilience >= 3 -> ZoneFutureCompatibility.MARGINAL
            !inOptimalNow && inAcceptFuture -> ZoneFutureCompatibility.MARGINAL
            resilience <= 2 -> ZoneFutureCompatibility.CRITICAL
            else -> ZoneFutureCompatibility.AT_RISK
        }
    }

    private fun buildEssenceAlerts(
        auteco: EssenceAutecology,
        station: StationObservation?,
        zone: ClimateZone
    ): List<String> {
        val alerts = mutableListOf<String>()
        alerts.addAll(auteco.specificAlerts)
        if (auteco.climateChangeResilience <= 2) {
            alerts.add("⚠ Faible résilience climatique — risque de dépérissement accru")
        }
        if (zone == ClimateZone.MEDITERRANEENNE && auteco.minHydric >= 3) {
            alerts.add("Zone méditerranéenne : stress hydrique estival probable")
        }
        station?.let {
            if (it.drainage == Drainage.MAUVAIS && !auteco.toleratesHydromorphy) {
                alerts.add("Drainage insuffisant incompatible avec cette essence")
            }
        }
        return alerts.distinct()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Risques climatiques
    // ─────────────────────────────────────────────────────────────────────────

    private fun computeRisquesClimatiques(
        tiges: List<Tige>,
        zone: ClimateZone,
        station: StationObservation?,
        stationResult: StationDiagnosticEngine.StationResult?,
        essencesBilan: List<EssenceBilan>
    ): List<RisqueClimatique> {
        val risques = mutableListOf<RisqueClimatique>()

        // Risque sécheresse — enrichi par DRIAS
        val driasP = DRIASDatabase.getProjection(zone)
        val driasRisques = DRIASDatabase.generateRisques(zone)
        driasRisques.take(3).forEachIndexed { idx, risk ->
            risques.add(RisqueClimatique(
                type = "DRIAS 2050",
                description = risk,
                niveau = when (driasP.droughtRisk2050) {
                    DRIASDatabase.DroughtRisk.TRES_FORT -> NiveauAlerte.CRITIQUE
                    DRIASDatabase.DroughtRisk.FORT      -> NiveauAlerte.ELEVEE
                    DRIASDatabase.DroughtRisk.MODERE    -> NiveauAlerte.MODEREE
                    DRIASDatabase.DroughtRisk.FAIBLE    -> NiveauAlerte.FAIBLE
                },
                essencesConcernees = if (idx == 0) DRIASDatabase.droughtResistantEssences(zone).take(3) else emptyList(),
                horizon = "2050 SSP8.5"
            ))
        }

        val vulnerablesDrought = essencesBilan.filter {
            it.futureZoneCompatibility in listOf(ZoneFutureCompatibility.AT_RISK, ZoneFutureCompatibility.CRITICAL)
        }
        if (vulnerablesDrought.isNotEmpty()) {
            risques.add(RisqueClimatique(
                type = "Sécheresse climatique — essences en risque",
                description = "Projections DRIAS 2050 (ΔT +${driasP.deltaT2050_ssp585}°C, précip. estivales ${driasP.deltaPsummer2050_ssp585.toInt()}%) : ${vulnerablesDrought.size} essence(s) en zone à risque hydrique.",
                niveau = if (vulnerablesDrought.size >= 2) NiveauAlerte.ELEVEE else NiveauAlerte.MODEREE,
                essencesConcernees = vulnerablesDrought.map { it.nameFr },
                horizon = "2030–2050"
            ))
        }

        // Risque épicéa / scolytes
        val hasEpicea = tiges.any { it.essenceCode.uppercase().contains("EPICEA") }
        if (hasEpicea && zone in listOf(ClimateZone.SEMI_OCEANIQUE, ClimateZone.CONTINENTALE, ClimateZone.MONTAGNARDE)) {
            risques.add(RisqueClimatique(
                type = "Pullulation de scolytes (Ips typographus)",
                description = "L'épicéa commun est fortement exposé aux scolytes en cas de sécheresse et chaleur. Risque systémique en forêts résineuses denses.",
                niveau = NiveauAlerte.CRITIQUE,
                essencesConcernees = listOf("Épicéa commun"),
                horizon = "Immédiat à 2030"
            ))
        }

        // Risque chalarose frêne
        val hasFrene = tiges.any { it.essenceCode.uppercase().contains("FRENE") }
        if (hasFrene) {
            risques.add(RisqueClimatique(
                type = "Chalarose du frêne (Hymenoscyphus fraxineus)",
                description = "Maladie fongique en expansion rapide, mortalité jusqu'à 90% dans certains peuplements.",
                niveau = NiveauAlerte.ELEVEE,
                essencesConcernees = listOf("Frêne commun", "Frêne oxyphylle"),
                horizon = "Immédiat"
            ))
        }

        // Risque hêtre
        val hasHetre = tiges.any { it.essenceCode.uppercase() == "HETRE" }
        if (hasHetre && zone in listOf(ClimateZone.SEMI_OCEANIQUE, ClimateZone.CONTINENTALE)) {
            risques.add(RisqueClimatique(
                type = "Dépérissement du hêtre",
                description = "Le hêtre est très sensible aux sécheresses répétées. La limite méridionale de son aire va se contracter significativement.",
                niveau = NiveauAlerte.ELEVEE,
                essencesConcernees = listOf("Hêtre"),
                horizon = "2030–2050"
            ))
        }

        // Risque incendie (Méditerranée)
        if (zone == ClimateZone.MEDITERRANEENNE) {
            val inflammables = tiges.filter {
                it.essenceCode.uppercase().contains("PIN") || it.essenceCode.uppercase().contains("EUCALYPTUS")
            }
            if (inflammables.isNotEmpty()) {
                risques.add(RisqueClimatique(
                    type = "Risque incendie",
                    description = "Zone méditerranéenne : risque incendie élevé en été. Débroussaillement et cloisonnement à prévoir.",
                    niveau = NiveauAlerte.ELEVEE,
                    essencesConcernees = inflammables.map { it.essenceCode }.distinct(),
                    horizon = "Chaque été"
                ))
            }
        }

        // Risque orme / graphiose
        val hasOrme = tiges.any { it.essenceCode.uppercase().contains("ORME") }
        if (hasOrme) {
            risques.add(RisqueClimatique(
                type = "Graphiose de l'orme",
                description = "Maladie fongique transmise par scolytes, dévastateur en 1–5 ans. Surveillance obligatoire.",
                niveau = NiveauAlerte.CRITIQUE,
                essencesConcernees = listOf("Orme champêtre", "Orme de montagne"),
                horizon = "Immédiat"
            ))
        }

        // Risque engorgement / drainage
        if (stationResult?.risqueEngorgement == true) {
            risques.add(RisqueClimatique(
                type = "Engorgement hydrique",
                description = "Hydromorphie proche de la surface. Les épisodes de pluies intenses seront amplifiés par le CC.",
                niveau = NiveauAlerte.MODEREE,
                essencesConcernees = emptyList(),
                horizon = "Court terme"
            ))
        }

        return risques.sortedByDescending { it.niveau.ordinal }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Recommandations d'essences futures
    // ─────────────────────────────────────────────────────────────────────────

    private fun computeRecommandationsFutur(
        zone: ClimateZone,
        station: StationObservation?,
        bilan: List<EssenceBilan>
    ): List<EssenceRecommandation> {
        val recos = mutableListOf<EssenceRecommandation>()

        // Récupère les essences compatibles avec la zone future projetée
        val futureZone = when (zone) {
            ClimateZone.SEMI_OCEANIQUE  -> ClimateZone.MEDITERRANEENNE
            ClimateZone.CONTINENTALE    -> ClimateZone.SEMI_OCEANIQUE
            ClimateZone.MONTAGNARDE     -> ClimateZone.SEMI_OCEANIQUE
            ClimateZone.ATLANTIQUE      -> ClimateZone.SEMI_OCEANIQUE
            else -> zone
        }

        val essencesCodesPresentes = bilan.map { it.code }.toSet()

        AutecologyDatabase.species
            .filter { ess ->
                ess.climateChangeResilience >= 4 &&
                (futureZone in ess.optimalZones || futureZone in ess.acceptableZones) &&
                ess.code !in essencesCodesPresentes
            }
            .also { candidates ->
                // Filtrer par station si disponible
                val filtrees = if (station != null) {
                    candidates.filter { ess ->
                        val compat = StationDiagnosticEngine.evaluateCompatibility(ess, station)
                        compat.compatibility != CompatibilityLevel.INCOMPATIBLE
                    }
                } else candidates

                filtrees
                    .sortedByDescending { it.climateChangeResilience }
                    .take(8)
                    .forEach { ess ->
                        val priority = when {
                            ess.climateChangeResilience == 5 -> 1
                            ess.climateChangeResilience == 4 -> 2
                            else -> 3
                        }
                        val rationale = buildRecoRationale(ess, zone, futureZone, station)
                        recos.add(EssenceRecommandation(
                            nameFr          = ess.nameFr,
                            code            = ess.code,
                            rationale       = rationale,
                            priority        = priority,
                            minHa           = if (priority == 1) 0.15 else 0.10,
                            climateResilience = ess.climateChangeResilience
                        ))
                    }
            }

        return recos.sortedBy { it.priority }
    }

    private fun buildRecoRationale(
        ess: EssenceAutecology,
        currentZone: ClimateZone,
        futureZone: ClimateZone,
        station: StationObservation?
    ): String {
        val sb = StringBuilder()
        val driasP = DRIASDatabase.getProjection(currentZone)
        sb.append("Résilience climatique ${ess.climateChangeResilience}/5. ")
        if (futureZone in ess.optimalZones) sb.append("Optimal dans la zone projetée 2050 (${futureZone.labelFr}). ")
        if (futureZone in ess.acceptableZones) sb.append("Acceptable dans la zone projetée 2050. ")
        if (currentZone in ess.optimalZones) sb.append("Déjà optimal dans le contexte actuel. ")
        if (driasP.droughtRisk2050 in listOf(DRIASDatabase.DroughtRisk.FORT, DRIASDatabase.DroughtRisk.TRES_FORT)) {
            val isDroughtAdapted = DRIASDatabase.droughtResistantEssences(currentZone).any {
                it.contains(ess.nameFr, ignoreCase = true) || ess.nameFr.contains(it.substringBefore(" ("), ignoreCase = true)
            }
            if (isDroughtAdapted) sb.append("Résistante à la sécheresse (DRIAS ${currentZone.labelFr}). ")
        }
        if (ess.specificAlerts.isNotEmpty()) sb.append("Note : ${ess.specificAlerts.first()}")
        return sb.toString().trim()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Plan d'action sylvicole
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildPlanAction(
        tiges: List<Tige>,
        surfaceHa: Double,
        station: StationObservation?,
        stationResult: StationDiagnosticEngine.StationResult?,
        ripisylveScore: RipisylveScore?,
        bilan: List<EssenceBilan>,
        risques: List<RisqueClimatique>,
        zone: ClimateZone
    ): List<ActionSylvicole> {
        val actions = mutableListOf<ActionSylvicole>()
        val nHa = if (surfaceHa > 0) tiges.size / surfaceHa else 0.0

        // Éclaircie urgente si forte densité
        if (nHa > 2000) {
            actions.add(ActionSylvicole(
                titre       = "Éclaircie urgente",
                description = "Densité excessive (${nHa.roundToInt()} tiges/ha). Objectif : descendre sous 1200 tiges/ha pour réduire la compétition et améliorer la stabilité face aux tempêtes.",
                urgence     = Urgence.IMMEDIATE,
                domaine     = DomaineSylvicole.ECLAIRCIE
            ))
        } else if (nHa > 1200) {
            actions.add(ActionSylvicole(
                titre       = "Éclaircie planifiée",
                description = "Densité soutenue (${nHa.roundToInt()} tiges/ha). Une éclaircie à court terme améliorera la vigueur individuelle et la résistance aux aléas climatiques.",
                urgence     = Urgence.COURT_TERME,
                domaine     = DomaineSylvicole.ECLAIRCIE
            ))
        }

        // Diversification si monoculture
        val essencesCount = bilan.size
        if (essencesCount == 1) {
            actions.add(ActionSylvicole(
                titre       = "Diversification spécifique urgente",
                description = "Monoculture détectée (${bilan.firstOrNull()?.nameFr ?: "essence unique"}). Introduire ≥ 2 essences d'avenir pour réduire la vulnérabilité systémique.",
                urgence     = Urgence.COURT_TERME,
                domaine     = DomaineSylvicole.REBOISEMENT
            ))
        }

        // Essences à risque critique
        bilan.filter { it.futureZoneCompatibility == ZoneFutureCompatibility.CRITICAL }.forEach { ess ->
            actions.add(ActionSylvicole(
                titre       = "Remplacement progressif de ${ess.nameFr}",
                description = "${ess.nameFr} est classé critique pour l'horizon 2050. Initier un renouvellement progressif lors de la prochaine coupe.",
                urgence     = Urgence.MOYEN_TERME,
                domaine     = DomaineSylvicole.REBOISEMENT
            ))
        }

        // Ripisylve dégradée
        ripisylveScore?.let { rip ->
            if (rip.scoreTotal < 40) {
                actions.add(ActionSylvicole(
                    titre       = "Restauration ripisylve urgente",
                    description = "Score ripisylve ${rip.scoreTotal}/100 — ripisylve dégradée. Replanter les zones ouvertes, lutter contre les invasives, favoriser les strates.",
                    urgence     = Urgence.IMMEDIATE,
                    domaine     = DomaineSylvicole.RIPISYLVE
                ))
            } else if (rip.scoreTotal < 60) {
                actions.add(ActionSylvicole(
                    titre       = "Amélioration de la ripisylve",
                    description = "Score ${rip.scoreTotal}/100. Des actions ciblées (invasives, microhabitats, continuité) peuvent significativement améliorer la fonctionnalité écologique.",
                    urgence     = Urgence.COURT_TERME,
                    domaine     = DomaineSylvicole.RIPISYLVE
                ))
            } else Unit
        }

        // Station contrainte forte
        stationResult?.let {
            if (it.contrainteHydrique == StationDiagnosticEngine.Contrainte.FORTE ||
                it.contrainteHydrique == StationDiagnosticEngine.Contrainte.TRES_FORTE) {
                actions.add(ActionSylvicole(
                    titre       = "Gestion du stress hydrique",
                    description = "Station à contrainte hydrique forte. Privilégier des essences xérophiles. Limiter la densité en hauteur pour réduire l'évapotranspiration.",
                    urgence     = Urgence.COURT_TERME,
                    domaine     = DomaineSylvicole.SOLS
                ))
            }
        }

        // Chalarose / graphiose immédiates
        risques.filter { it.niveau == NiveauAlerte.CRITIQUE }.forEach { risque ->
            actions.add(ActionSylvicole(
                titre       = "Surveillance sanitaire : ${risque.type}",
                description = "Risque critique identifié. Mettre en place un plan de surveillance et prévoir les interventions précoces (abattage des arbres atteints).",
                urgence     = Urgence.IMMEDIATE,
                domaine     = DomaineSylvicole.PROTECTION
            ))
        }

        // Biodiversité / gros bois
        val grosBois = tiges.count { it.diamCm >= 40 }
        if (grosBois < 3 && tiges.size > 20) {
            actions.add(ActionSylvicole(
                titre       = "Conservation des gros bois",
                description = "Peu de gros bois identifiés ($grosBois tiges ≥40 cm). Conserver les arbres de gros diamètre comme arbres-habitat (IBP).",
                urgence     = Urgence.LONG_TERME,
                domaine     = DomaineSylvicole.BIODIVERSITE
            ))
        }

        // Élancement : risque mécanique — résineux uniquement
        val slendernesses = tiges.filter { isResineuxCode(it.essenceCode) }.mapNotNull { t ->
            t.hauteurM?.let { h -> if (t.diamCm > 0) (h * 100.0) / t.diamCm else null }
        }
        if (slendernesses.isNotEmpty() && slendernesses.average() > 110) {
            actions.add(ActionSylvicole(
                titre       = "Élancement excessif — risque tempête",
                description = "Élancement moyen > 110 : peuplement instable face aux tempêtes. Éclaircie forte recommandée pour améliorer l'enracinement et la stabilité mécanique.",
                urgence     = Urgence.COURT_TERME,
                domaine     = DomaineSylvicole.ECLAIRCIE
            ))
        }

        // Sol calcaire + essences calcifuges
        station?.let { st ->
            val isCalcaire = st.testHcl == com.forestry.counter.domain.model.station.TestHCl.TRES_FORT ||
                st.testHcl == com.forestry.counter.domain.model.station.TestHCl.FORT
            if (isCalcaire) {
                val calcifuges = bilan.filter { ess ->
                    AutecologyDatabase.getByCodeOrName(ess.code)?.let { a ->
                        a.minTrophic >= 4 // essences évitant les sols calcaires/basiques
                    } ?: false
                }
                if (calcifuges.isNotEmpty()) {
                    actions.add(ActionSylvicole(
                        titre       = "Inadaptation sol calcaire × essences calcifuges",
                        description = "Sol calcaire détecté (test HCl positif) avec ${calcifuges.joinToString { it.nameFr }} présent(s). Envisager remplacement progressif par des essences calcicoles.",
                        urgence     = Urgence.MOYEN_TERME,
                        domaine     = DomaineSylvicole.REBOISEMENT
                    ))
                }
            }
            // Sol superficiel : recommander essences superficielles
            if (st.profondeurSolCm != null && st.profondeurSolCm < 30 && tiges.size > 10) {
                actions.add(ActionSylvicole(
                    titre       = "Sol très superficiel — limiter les conifères profonds",
                    description = "Profondeur sol ${st.profondeurSolCm} cm. Privilégier des essences à enracinement superficiel (chêne pubescent, pin sylvestre, genev-rier).",
                    urgence     = Urgence.MOYEN_TERME,
                    domaine     = DomaineSylvicole.SOLS
                ))
            }
        }

        return actions.sortedBy { it.urgence.ordinal }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Alertes sanitaires
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildAlertesSanitaires(
        tiges: List<Tige>,
        zone: ClimateZone,
        @Suppress("UNUSED_PARAMETER") stationResult: StationDiagnosticEngine.StationResult?
    ): List<AlerteSanitaire> {
        val alertes = mutableListOf<AlerteSanitaire>()
        val codes = tiges.map { it.essenceCode.uppercase() }.toSet()
        val seenTitles = mutableSetOf<String>()

        // ── Enrichissement via BioClimaticRiskDatabase ──────────────────────
        codes.forEach { code ->
            val profile = BioClimaticRiskDatabase.getProfileByCode(code) ?: return@forEach
            profile.risks
                .filter { risk ->
                    zone in risk.affectedZones &&
                    risk.severity in listOf(
                        BioClimaticRiskDatabase.RiskSeverity.CRITICAL,
                        BioClimaticRiskDatabase.RiskSeverity.HIGH
                    ) &&
                    risk.probability2050 in listOf(
                        BioClimaticRiskDatabase.RiskProbability.CERTAIN,
                        BioClimaticRiskDatabase.RiskProbability.PROBABLE
                    )
                }
                .forEach { risk ->
                    val titre = risk.name
                    if (seenTitles.add(titre)) {
                        val signesDesc = if (risk.earlyWarnings.isNotEmpty()) {
                            " Signes : ${risk.earlyWarnings.take(3).joinToString("; ")}."
                        } else ""
                        val niveauAlerte = when (risk.severity) {
                            BioClimaticRiskDatabase.RiskSeverity.CRITICAL -> NiveauAlerte.CRITIQUE
                            BioClimaticRiskDatabase.RiskSeverity.HIGH     -> NiveauAlerte.ELEVEE
                            else                                           -> NiveauAlerte.MODEREE
                        }
                        alertes.add(AlerteSanitaire(
                            titre              = titre,
                            description        = risk.description + signesDesc,
                            niveau             = niveauAlerte,
                            essencesConcernees = listOf(profile.essenceNameFr)
                        ))
                    }
                }
        }

        // ── Graphiose de l'orme (non dans BioClimaticRiskDatabase) ──────────
        if (codes.any { it.contains("ORME") } && seenTitles.add("Graphiose de l'orme")) {
            alertes.add(AlerteSanitaire(
                titre              = "Graphiose de l'orme",
                description        = "Ophiostoma ulmi/novo-ulmi — maladie vasculaire transmise par scolytes. Abattre les arbres malades sans délai.",
                niveau             = NiveauAlerte.CRITIQUE,
                essencesConcernees = listOf("Orme champêtre", "Orme de montagne")
            ))
        }

        // ── Risque incendie méditerranéen ────────────────────────────────────
        if (zone == ClimateZone.MEDITERRANEENNE && seenTitles.add("Risque incendie estival")) {
            alertes.add(AlerteSanitaire(
                titre              = "Risque incendie estival",
                description        = "Maintenir le débroussaillement obligatoire. Créer des coupures de combustible. Éviter les travaux forestiers pendant les alertes feu.",
                niveau             = NiveauAlerte.ELEVEE,
                essencesConcernees = emptyList()
            ))
        }

        // ── Pin sylvestre en plaine (risque dépérissement) ──────────────────
        if (codes.any { it.contains("PIN_SYLVESTRE") } &&
            zone in listOf(ClimateZone.SEMI_OCEANIQUE, ClimateZone.CONTINENTALE) &&
            seenTitles.add("Dépérissement du pin sylvestre")) {
            alertes.add(AlerteSanitaire(
                titre              = "Dépérissement du pin sylvestre",
                description        = "Les études INRAE projettent 30–50% de mortalité en plaine d'ici 2050. " +
                    "Envisager une conversion progressive vers chêne sessile ou pubescent.",
                niveau             = NiveauAlerte.ELEVEE,
                essencesConcernees = listOf("Pin sylvestre")
            ))
        }

        // ── Ï¯dium du chêne ──────────────────────────────────────────────────
        if (codes.any { it.contains("CHENE") } && seenTitles.add("Ï¯dium du chêne")) {
            alertes.add(AlerteSanitaire(
                titre              = "Ï¯dium du chêne (Erysiphe alphitoides)",
                description        = "Champignon foliaire ubiquiste affectant les chênes en régénération. " +
                    "Surveiller les jeunes plants. Le stress hydrique amplifie les infections.",
                niveau             = NiveauAlerte.MODEREE,
                essencesConcernees = listOf("Chêne sessile", "Chêne pédonculé", "Chêne pubescent")
            ))
        }

        // ── Processionnaire du pin (zones chaudes) ───────────────────────────
        if (codes.any { it.contains("PIN") } &&
            zone in listOf(ClimateZone.MEDITERRANEENNE, ClimateZone.SEMI_OCEANIQUE) &&
            seenTitles.add("Processionnaire du pin")) {
            alertes.add(AlerteSanitaire(
                titre              = "Processionnaire du pin (Thaumetopoea pityocampa)",
                description        = "Ravageur en expansion vers le nord (CC). Défoliations répétées affaiblissent les pins. " +
                    "Signes : nids soyeux en haut des couronnes en hiver. Risque santé humaine/animale.",
                niveau             = NiveauAlerte.ELEVEE,
                essencesConcernees = listOf("Pin sylvestre", "Pin maritime", "Pin d'Alep")
            ))
        }

        // ── Platane + maladie corticale ──────────────────────────────────────
        if (codes.any { it.contains("PLATANE") } && seenTitles.add("Chancre coloré du platane")) {
            alertes.add(AlerteSanitaire(
                titre              = "Chancre coloré du platane (Ceratocystis platani)",
                description        = "Maladie vasculaire mortelle, inscrite au catalogue des organismes réglementés. " +
                    "Tout abattage doit être réalisé avec précautions pour éviter la propagation.",
                niveau             = NiveauAlerte.CRITIQUE,
                essencesConcernees = listOf("Platane")
            ))
        }

        return alertes.sortedByDescending { it.niveau.ordinal }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Synthèse textuelle
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildSynthese(
        score: ResilienceScore,
        zone: ClimateZone,
        risques: List<RisqueClimatique>,
        plan: List<ActionSylvicole>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("Zone bioclimatique : ${zone.labelFr}. ${score.labelFr} (score global ${score.global}/100).")
        if (risques.isNotEmpty()) {
            val topRisque = risques.first()
            sb.appendLine("Risque principal : ${topRisque.type} — ${topRisque.niveau.label}.")
        }
        val urgent = plan.filter { it.urgence == Urgence.IMMEDIATE }
        if (urgent.isNotEmpty()) {
            sb.appendLine("Action(s) urgente(s) : ${urgent.joinToString("; ") { it.titre }}.")
        }
        sb.append("Confiance basée sur ${if (score.dendro != null && score.station != null) "données dendro + station" else "données partielles"}.")
        return sb.toString().trim()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Sources de données
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildDataSources(
        tiges: List<Tige>,
        station: StationObservation?,
        ripisylve: RipisylveObservation?,
        lat: Double?,
        lon: Double?
    ): DataSources {
        var completeness = 0
        if (tiges.isNotEmpty()) completeness += 35
        if (station != null) completeness += 30
        if (ripisylve != null) completeness += 15
        if (lat != null && lon != null) completeness += 10
        if (tiges.any { it.hauteurM != null }) completeness += 5
        if (station?.phEstime != null) completeness += 3
        if (station?.especesIndicatrices?.isNotEmpty() == true) completeness += 2
        return DataSources(
            hasDendro    = tiges.isNotEmpty(),
            hasStation   = station != null,
            hasRipisylve = ripisylve != null,
            hasGPS       = lat != null && lon != null,
            nbTiges      = tiges.size,
            completeness = completeness.coerceIn(0, 100)
        )
    }
}

data class RisqueClimatique(
    val type: String,
    val description: String,
    val niveau: SuperCorrelateurEngine.NiveauAlerte,
    val essencesConcernees: List<String>,
    val horizon: String
)
