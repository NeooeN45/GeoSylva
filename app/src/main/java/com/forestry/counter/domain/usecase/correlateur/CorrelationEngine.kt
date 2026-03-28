package com.forestry.counter.domain.usecase.correlateur

import com.forestry.counter.domain.model.ripisylve.RipisylveObservation
import com.forestry.counter.domain.model.ripisylve.RipisylveFonctionnalite
import com.forestry.counter.domain.model.station.StationObservation
import com.forestry.counter.domain.model.station.Drainage
import com.forestry.counter.domain.model.station.PositionTopo
import com.forestry.counter.domain.usecase.florist.GradientInferenceEngine
import com.forestry.counter.domain.usecase.florist.FloristDatabase
import com.forestry.counter.domain.usecase.ripisylve.RipisylveScorer

/**
 * Moteur de corrélation croisée GPS/flore/station/ripisylve.
 *
 * RÔLE : valider la cohérence interne des données multi-sources d'une parcelle et
 *        enrichir chaque diagnostic avec les informations des autres sources.
 *
 * DISTINCT de SuperCorrelateurEngine.kt qui est le moteur global de synthèse sylvicole.
 * CorrelationEngine se concentre sur la cohérence terrain locale (data quality + intelligence).
 */
object CorrelationEngine {

    // ─── Modèles de sortie ────────────────────────────────────────────────────

    data class CorrelationReport(
        val coherenceGlobale: CoherenceGlobale,
        val confirmations: List<CorrelationFact>,    // éléments confirmés entre sources
        val contradictions: List<CorrelationFact>,   // contradictions détectées
        val enrichissements: List<CorrelationFact>,  // infos déduites d'une source vers l'autre
        val alertesCritiques: List<String>,          // alertes prioritaires
        val scoreCoherence: Float,                   // 0.0–1.0
        val syntheseTextuelle: String,
        /** Données identifiées comme manquantes pour améliorer la corrélation */
        val donneesManquantes: List<DonneesManquantes> = emptyList(),
        /** Conclusion principale structurée : ce qui est sûr, ce qui est ambigu, ce qui manque */
        val conclusionPrincipale: ConclusionCorrelation = ConclusionCorrelation.vide()
    )

    data class ConclusionCorrelation(
        val pointsCertains: List<String>,    // ce que les sources confirment sans ambiguïté
        val pointsAmbigus: List<String>,     // ce qui est discordant et nécessite investigation
        val pointsManquants: List<String>,   // données absentes qui limiteraient la conclusion
        val recommandationTerrain: String    // action terrain prioritaire déduite
    ) {
        companion object {
            fun vide() = ConclusionCorrelation(emptyList(), emptyList(), emptyList(), "")
        }
    }

    data class CorrelationFact(
        val source1: DataSource,
        val source2: DataSource,
        val description: String,
        val type: FactType,
        val severite: Severite = Severite.MODERE,
        val conseil: String = "",
        /** Chaîne de raisonnement explicite : quelles valeurs, pourquoi cette conclusion */
        val raisonnement: String = ""
    )

    data class DonneesManquantes(
        val source: DataSource,
        val champ: String,
        val impactSiManquant: String
    )

    enum class DataSource(val labelFr: String) {
        STATION("Station"),
        FLORE("Flore"),
        RIPISYLVE("Ripisylve"),
        PEUPLEMENT("Peuplement"),
        GPS("GPS/Topographie")
    }

    enum class FactType(val labelFr: String) {
        CONFIRMATION("Confirmation croisée"),
        CONTRADICTION("Contradiction"),
        ENRICHISSEMENT("Enrichissement")
    }

    enum class Severite(val labelFr: String) {
        CRITIQUE("Critique"), MODERE("Modéré"), INFO("Info")
    }

    enum class CoherenceGlobale(val labelFr: String, val color: String) {
        TRES_COHERENT("Très cohérent — toutes les sources s'accordent",       "#2E7D32"),
        COHERENT("Cohérent — accord global avec nuances mineures",            "#8BC34A"),
        TENSIONS("Tensions — quelques contradictions à investiguer",          "#F9A825"),
        INCOHERENT("Incohérent — contradictions significatives détectées",    "#E65100"),
        INSUFFISANT("Données insuffisantes pour corréler",                    "#9E9E9E")
    }

    // ─── Analyse principale ───────────────────────────────────────────────────

    /**
     * Corrèle les données disponibles d'une parcelle.
     * Toutes les entrées sont optionnelles — seules les combinaisons disponibles sont traitées.
     */
    fun correlate(
        station: StationObservation? = null,
        floraGradients: GradientInferenceEngine.GradientResult? = null,
        ripisylve: RipisylveObservation? = null,
        floraIds: List<String> = emptyList()
    ): CorrelationReport {
        val confirmations   = mutableListOf<CorrelationFact>()
        val contradictions  = mutableListOf<CorrelationFact>()
        val enrichissements = mutableListOf<CorrelationFact>()
        val alertes         = mutableListOf<String>()

        // ── Corrélations Station ↔ Flore ───────────────────────────────────
        if (station != null && floraGradients != null && floraGradients.nbTaxonsAnalysables >= 3) {
            correlateStationFlore(station, floraGradients, confirmations, contradictions, enrichissements, alertes)
        }

        // ── Corrélations Station ↔ Ripisylve ──────────────────────────────
        if (station != null && ripisylve != null) {
            correlateStationRipisylve(station, ripisylve, confirmations, contradictions, enrichissements, alertes)
        }

        // ── Corrélations Flore ↔ Ripisylve ────────────────────────────────
        if (floraIds.isNotEmpty() && ripisylve != null) {
            correlateFloreRipisylve(floraIds, ripisylve, confirmations, contradictions, enrichissements, alertes)
        }

        // ── Corrélations GPS/Topo ↔ Station ───────────────────────────────
        if (station != null) {
            correlateGpsStation(station, confirmations, enrichissements, alertes)
        }

        val totalFacts = confirmations.size + contradictions.size + enrichissements.size
        val coherenceGlobale = computeCoherenceGlobale(confirmations, contradictions, totalFacts)
        val score = computeCoherenceScore(confirmations, contradictions, totalFacts)
        val manquants = detectMissingData(station, floraGradients, ripisylve, floraIds)
        val conclusion = buildConclusion(confirmations, contradictions, enrichissements, manquants)

        return CorrelationReport(
            coherenceGlobale      = coherenceGlobale,
            confirmations         = confirmations,
            contradictions        = contradictions,
            enrichissements       = enrichissements,
            alertesCritiques      = alertes,
            scoreCoherence        = score,
            syntheseTextuelle     = buildSynthese(coherenceGlobale, confirmations, contradictions, alertes),
            donneesManquantes     = manquants,
            conclusionPrincipale  = conclusion
        )
    }

    // ─── Station ↔ Flore ─────────────────────────────────────────────────────

    private fun correlateStationFlore(
        station: StationObservation,
        flore: GradientInferenceEngine.GradientResult,
        confirmations: MutableList<CorrelationFact>,
        contradictions: MutableList<CorrelationFact>,
        enrichissements: MutableList<CorrelationFact>,
        alertes: MutableList<String>
    ) {
        val stH = station.gradientHydrique.toDouble()
        val fH  = flore.hydrique
        val diffH = kotlin.math.abs(fH - stH)

        when {
            diffH <= 1.0 -> confirmations += CorrelationFact(
                source1 = DataSource.STATION,
                source2 = DataSource.FLORE,
                description = "Gradient hydrique cohérent : station=$stH, flore=%.1f".format(fH),
                type = FactType.CONFIRMATION,
                raisonnement = "Station note H=$stH/5, flore indique H=%.1f/7 (Ellenberg) — écart ≤ 1 classe, concordance satisfaisante.".format(fH)
            )
            diffH in 1.0..2.0 -> enrichissements += CorrelationFact(
                source1 = DataSource.FLORE,
                source2 = DataSource.STATION,
                description = "Gradient hydrique légèrement discordant (Δ=%.1f) — la flore suggère %.1f, station indique %.0f".format(diffH, fH, stH),
                type = FactType.ENRICHISSEMENT,
                conseil = "Revoir la notation de drainage ou observer plus précisément la nappe",
                raisonnement = "Écart Δ=%.1f entre flore (H=%.1f) et station (H=%.0f). Peut s'expliquer par microsite, sol hétérogène ou cortège mixte.".format(diffH, fH, stH)
            )
            else -> {
                contradictions += CorrelationFact(
                    source1 = DataSource.STATION,
                    source2 = DataSource.FLORE,
                    description = "Gradient hydrique contradictoire : station=%.0f vs flore=%.1f (Δ=%.1f)".format(stH, fH, diffH),
                    type = FactType.CONTRADICTION,
                    severite = Severite.MODERE,
                    conseil = "Investiguer : microsites, nappe perchée, ou cortège floristique atypique",
                    raisonnement = "Contradiction forte (Δ=%.1f > 2) : la flore indique H=%.1f mais la station note H=%.0f. Causes possibles : nappe perchée non visible en surface, remblai récent, espèces relictuelles d'un état antérieur du sol.".format(diffH, fH, stH)
                )
                if (diffH > 3.0) alertes += "Contradiction hydrique forte (Δ${diffH.toInt()}) entre flore et station"
            }
        }

        // Trophique
        val stT = station.gradientTrophique.toDouble()
        val fT  = flore.trophique
        val diffT = kotlin.math.abs(fT - stT)
        when {
            diffT <= 1.0 -> confirmations += CorrelationFact(
                DataSource.STATION, DataSource.FLORE,
                "Gradient trophique cohérent : station=$stT, flore=%.1f".format(fT),
                FactType.CONFIRMATION,
                raisonnement = "N station=%.0f, N flore=%.1f (Ellenberg) — accord entre l'analyse de sol et le cortège végétal.".format(stT, fT)
            )
            diffT > 2.0  -> contradictions += CorrelationFact(
                DataSource.STATION, DataSource.FLORE,
                "Gradient trophique discordant : station=%.0f vs flore=%.1f (Δ=%.1f)".format(stT, fT, diffT),
                FactType.CONTRADICTION, Severite.MODERE,
                "Possible amendement historique ou cortège perturbé",
                raisonnement = "N station=%.0f mais flore indique N=%.1f. Hypothèses : amendement passé (chaulage, fertilisation), cortège hérité d'un état antérieur, ou erreur de notation trophique.".format(stT, fT)
            )
        }

        // Probabilité hydromorphie ↔ drainage station
        if (flore.probabiliteHydromorphie > 0.40 &&
            station.drainage in listOf(Drainage.BON, Drainage.EXCESSIF)) {
            contradictions += CorrelationFact(
                DataSource.FLORE, DataSource.STATION,
                "Flore hygrophile (${(flore.probabiliteHydromorphie * 100).toInt()}% hygrophytes) mais drainage station = ${station.drainage.labelFr}",
                FactType.CONTRADICTION, Severite.MODERE,
                "Vérifier la présence de taches rouille en profondeur (hydromorphie temporaire)"
            )
        }

        // Hydromorphie ↔ profondeur taches
        if (flore.probabiliteHydromorphie > 0.50 && station.hydromorphieProfondeurCm == null) {
            enrichissements += CorrelationFact(
                DataSource.FLORE, DataSource.STATION,
                "Cortège à dominante hygrophile — profondeur d'hydromorphie non renseignée dans la station",
                FactType.ENRICHISSEMENT,
                conseil = "Observer les taches rouille dans le profil de sol"
            )
        }
        if (station.hydromorphieProfondeurCm != null && flore.probabiliteHydromorphie > 0.30) {
            confirmations += CorrelationFact(
                DataSource.STATION, DataSource.FLORE,
                "Hydromorphie confirmée par flore (${(flore.probabiliteHydromorphie * 100).toInt()}% hygrophytes) et profil sol (taches à ${station.hydromorphieProfondeurCm} cm)",
                FactType.CONFIRMATION
            )
        }

        // Perturbation
        if (flore.probabilitePerturbation > 0.35) {
            enrichissements += CorrelationFact(
                DataSource.FLORE, DataSource.STATION,
                "Flore perturbée (${(flore.probabilitePerturbation * 100).toInt()}% indicateurs) — vérifier anthropisation récente",
                FactType.ENRICHISSEMENT, Severite.MODERE,
                "Recenser les travaux récents (coupe rase, remblai, fertilisation)"
            )
        }
    }

    // ─── Station ↔ Ripisylve ──────────────────────────────────────────────────

    private fun correlateStationRipisylve(
        station: StationObservation,
        ripisylve: RipisylveObservation,
        confirmations: MutableList<CorrelationFact>,
        contradictions: MutableList<CorrelationFact>,
        enrichissements: MutableList<CorrelationFact>,
        alertes: MutableList<String>
    ) {
        val distCours = station.distanceCourseauM
        val hasCours  = distCours != null && distCours < 100.0
        val ripScore  = RipisylveScorer.score(ripisylve, emptyList())

        // Distance cours d'eau ↔ contexte ripisylve
        if (hasCours && distCours != null && distCours < 30.0) {
            confirmations += CorrelationFact(
                DataSource.GPS, DataSource.RIPISYLVE,
                "Station proche d'un cours d'eau (${distCours.toInt()} m) — diagnostic ripisylve pertinent et cohérent",
                FactType.CONFIRMATION
            )
        }
        if (!hasCours && ripisylve.continuitePct > 50.0) {
            contradictions += CorrelationFact(
                DataSource.STATION, DataSource.RIPISYLVE,
                "Diagnostic ripisylve avec bonne continuité mais station éloignée de tout cours d'eau",
                FactType.CONTRADICTION, Severite.INFO,
                "Vérifier le contexte : cours d'eau temporaire, fossé, ou distance mal renseignée"
            )
        }

        // Hydromorphie station ↔ score ripisylve
        if (station.hydromorphieProfondeurCm != null && station.hydromorphieProfondeurCm!! < 40
            && ripScore.fonctionnalite in listOf(RipisylveFonctionnalite.BONNE, RipisylveFonctionnalite.TRES_BONNE)) {
            confirmations += CorrelationFact(
                DataSource.STATION, DataSource.RIPISYLVE,
                "Hydromorphie proche de la surface (${station.hydromorphieProfondeurCm} cm) — ripisylve fonctionnelle cohérente",
                FactType.CONFIRMATION
            )
        }

        // Espèces invasives ripisylve ↔ flore station
        if (ripisylve.invasivesPct > 30.0) {
            enrichissements += CorrelationFact(
                DataSource.RIPISYLVE, DataSource.STATION,
                "Forte pression invasives en ripisylve (${ripisylve.invasivesPct.toInt()}%) — peut affecter la flore indicatrice de station",
                FactType.ENRICHISSEMENT, Severite.MODERE,
                "Les espèces indicatrices du sous-bois sont potentiellement masquées par les invasives"
            )
            if (ripisylve.invasivesPct > 50.0)
                alertes += "Invasion sévère en ripisylve (${ripisylve.invasivesPct.toInt()}%) — cortège floristique de station peu fiable"
        }

        // Drainage station ↔ stabilité berges ripisylve
        if (station.drainage == Drainage.MAUVAIS && ripisylve.stabilitePct > 30.0) {
            confirmations += CorrelationFact(
                DataSource.STATION, DataSource.RIPISYLVE,
                "Drainage mauvais et instabilité berges concordants — dynamique d'hydromorphie active",
                FactType.CONFIRMATION, Severite.MODERE
            )
        }
    }

    // ─── Flore ↔ Ripisylve ────────────────────────────────────────────────────

    private fun correlateFloreRipisylve(
        floraIds: List<String>,
        ripisylve: RipisylveObservation,
        confirmations: MutableList<CorrelationFact>,
        contradictions: MutableList<CorrelationFact>,
        enrichissements: MutableList<CorrelationFact>,
        alertes: MutableList<String>
    ) {
        val hygrophytes = floraIds.count {
            FloristDatabase.findById(it)?.valeurIndicatrice?.indicateurHydromorphie == true
        }
        val totalFlore = floraIds.size

        // Hygrophytes ↔ strate herbacée ripisylve
        if (hygrophytes >= 3 && ripisylve.strateHerbacee) {
            confirmations += CorrelationFact(
                DataSource.FLORE, DataSource.RIPISYLVE,
                "$hygrophytes hygrophytes observées — strate herbacée ripisylve présente et cohérente",
                FactType.CONFIRMATION
            )
        }
        if (hygrophytes >= 2 && !ripisylve.strateHerbacee && totalFlore >= 5) {
            enrichissements += CorrelationFact(
                DataSource.FLORE, DataSource.RIPISYLVE,
                "Flore hygrophile dominante mais strate herbacée non cochée en ripisylve",
                FactType.ENRICHISSEMENT, Severite.INFO,
                "Vérifier la strate herbacée : présence de cariçaies, reine des prés, etc."
            )
        }

        // Espèces invasives en flore ↔ % invasives ripisylve
        val invasivesFlore = floraIds.mapNotNull { FloristDatabase.findById(it) }
            .count { it.classification.statutInvasif.name.contains("ENVAHISSANTE") }
        if (invasivesFlore >= 1 && ripisylve.invasivesPct < 10.0) {
            enrichissements += CorrelationFact(
                DataSource.FLORE, DataSource.RIPISYLVE,
                "Espèces invasives identifiées en flore mais % ripisylve probablement sous-estimé",
                FactType.ENRICHISSEMENT, Severite.MODERE,
                "Réévaluer le recouvrement des invasives en ripisylve"
            )
        }
        if (invasivesFlore >= 2 && ripisylve.invasivesPct > 25.0) {
            confirmations += CorrelationFact(
                DataSource.FLORE, DataSource.RIPISYLVE,
                "Pression invasives confirmée par les deux sources ($invasivesFlore esp. invasives en flore, ${ripisylve.invasivesPct.toInt()}% en ripisylve)",
                FactType.CONFIRMATION, Severite.MODERE
            )
            alertes += "Pression invasives confirmée par flore + ripisylve — plan de lutte urgent"
        }
    }

    // ─── GPS/Topo ↔ Station ───────────────────────────────────────────────────

    private fun correlateGpsStation(
        station: StationObservation,
        confirmations: MutableList<CorrelationFact>,
        enrichissements: MutableList<CorrelationFact>,
        alertes: MutableList<String>
    ) {
        val alt = station.altitudeM

        // Position topo ↔ gradient hydrique
        when (station.positionTopo) {
            PositionTopo.VALLON, PositionTopo.BAS_VERSANT -> {
                if (station.gradientHydrique >= 3) {
                    confirmations += CorrelationFact(
                        DataSource.GPS, DataSource.STATION,
                        "Position topographique basse (${station.positionTopo.labelFr}) cohérente avec gradient hydrique = ${station.gradientHydrique}",
                        FactType.CONFIRMATION
                    )
                }
            }
            PositionTopo.CRETE -> {
                if (station.gradientHydrique <= 2) {
                    confirmations += CorrelationFact(
                        DataSource.GPS, DataSource.STATION,
                        "Position de crête cohérente avec gradient hydrique faible = ${station.gradientHydrique}",
                        FactType.CONFIRMATION
                    )
                } else if (station.gradientHydrique >= 4) {
                    enrichissements += CorrelationFact(
                        DataSource.GPS, DataSource.STATION,
                        "Gradient hydrique élevé (${station.gradientHydrique}) en position de crête — inhabituel",
                        FactType.ENRICHISSEMENT, Severite.MODERE,
                        "Vérifier : roche mère imperméable, suintement, ou erreur de position topo"
                    )
                }
            }
            else -> Unit
        }

        // Altitude ↔ essences compatibles
        if (alt != null && alt > 1200) {
            alertes += "Altitude subalpine (${alt.toInt()} m) — peu d'essences forestières calibrées pour cette zone"
        }

        // GPS absent = confiance station réduite
        if (station.latitude == null) {
            enrichissements += CorrelationFact(
                DataSource.GPS, DataSource.STATION,
                "Coordonnées GPS manquantes — localisation de la station non vérifiable",
                FactType.ENRICHISSEMENT, Severite.INFO,
                "Capturer le GPS pour ancrer le diagnostic dans l'espace"
            )
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun computeCoherenceGlobale(
        confirmations: List<CorrelationFact>,
        contradictions: List<CorrelationFact>,
        total: Int
    ): CoherenceGlobale {
        if (total < 2) return CoherenceGlobale.INSUFFISANT
        val critiques = contradictions.count { it.severite == Severite.CRITIQUE }
        val moderes   = contradictions.count { it.severite == Severite.MODERE }
        return when {
            critiques > 0 || moderes >= 3 -> CoherenceGlobale.INCOHERENT
            moderes >= 2                  -> CoherenceGlobale.TENSIONS
            moderes == 1 || contradictions.size == 1 -> CoherenceGlobale.COHERENT
            contradictions.isEmpty()      -> CoherenceGlobale.TRES_COHERENT
            else                          -> CoherenceGlobale.COHERENT
        }
    }

    private fun computeCoherenceScore(
        confirmations: List<CorrelationFact>,
        contradictions: List<CorrelationFact>,
        total: Int
    ): Float {
        if (total == 0) return 0.5f
        val critWeight = contradictions.fold(0) { acc, f ->
            acc + when (f.severite) { Severite.CRITIQUE -> 3; Severite.MODERE -> 2; else -> 1 }
        }
        val confirmW = confirmations.size.toFloat()
        return (confirmW / (confirmW + critWeight + 1)).coerceIn(0f, 1f)
    }

    private fun buildSynthese(
        coherence: CoherenceGlobale,
        confirmations: List<CorrelationFact>,
        contradictions: List<CorrelationFact>,
        alertes: List<String>
    ): String = buildString {
        append(coherence.labelFr).append(".")
        if (alertes.isNotEmpty()) {
            append(" ⚠ Alertes : ")
            append(alertes.joinToString(" | "))
            append(".")
        }
        if (confirmations.isNotEmpty())
            append(" ${confirmations.size} confirmation(s) croisée(s) : ${confirmations.joinToString(" / ") { it.description.take(60) }}.")
        if (contradictions.isNotEmpty())
            append(" ${contradictions.size} contradiction(s) : ${contradictions.joinToString(" / ") { it.description.take(80) }}.")
    }

    // ─── Données manquantes ───────────────────────────────────────────────────

    private fun detectMissingData(
        station: StationObservation?,
        floraGradients: GradientInferenceEngine.GradientResult?,
        ripisylve: RipisylveObservation?,
        floraIds: List<String>
    ): List<DonneesManquantes> = buildList {
        if (station == null)
            add(DonneesManquantes(DataSource.STATION, "Diagnostic station complet",
                "Impossible de corréler sans données de station (drainage, trophique, hydromorphie)"))
        else {
            if (station.latitude == null)
                add(DonneesManquantes(DataSource.GPS, "Coordonnées GPS",
                    "La localisation empêche le recoupement altitudinal et topographique"))
            if (station.hydromorphieProfondeurCm == null)
                add(DonneesManquantes(DataSource.STATION, "Profondeur taches rouille",
                    "Sans profondeur d'hydromorphie, impossible de confirmer l'engorgement par le sol"))
            if (station.texture == com.forestry.counter.domain.model.station.TextureSol.INCONNUE)
                add(DonneesManquantes(DataSource.STATION, "Texture principale du sol",
                    "La texture conditionne le drainage réel et la disponibilité en eau"))
        }
        if (floraGradients == null || floraIds.size < 3)
            add(DonneesManquantes(DataSource.FLORE, "Cortège floristique (≥ 3 espèces indicatrices)",
                "Trop peu d'espèces pour calculer des gradients fiables (minimum 3 recommandé)"))
        if (ripisylve == null)
            add(DonneesManquantes(DataSource.RIPISYLVE, "Diagnostic ripisylve",
                "Absent : impossible de croiser avec l'état de la ripisylve riveraine"))
    }

    // ─── Conclusion structurée ────────────────────────────────────────────────

    private fun buildConclusion(
        confirmations: List<CorrelationFact>,
        contradictions: List<CorrelationFact>,
        enrichissements: List<CorrelationFact>,
        manquants: List<DonneesManquantes>
    ): ConclusionCorrelation {
        val certains = confirmations.map { "[${it.source1.labelFr}↔${it.source2.labelFr}] ${it.description}" }

        val ambigus = contradictions.map { fact ->
            val base = "[${fact.source1.labelFr}↔${fact.source2.labelFr}] ${fact.description}"
            if (fact.raisonnement.isNotBlank()) "$base — ${fact.raisonnement}" else base
        }

        val pointsManquants = manquants.map { "${it.source.labelFr} / ${it.champ} : ${it.impactSiManquant}" }

        val recommandation = when {
            contradictions.any { it.severite == Severite.CRITIQUE } ->
                "URGENT : Investiguer les contradictions critiques avant toute préconisation sylvicole."
            contradictions.size >= 2 ->
                "Terrain prioritaire : vérifier ${contradictions.map { it.champ() }.distinct().joinToString(", ")} pour lever les ambiguïtés."
            manquants.isNotEmpty() && confirmations.isEmpty() ->
                "Données insuffisantes : compléter ${manquants.first().champ} pour obtenir un diagnostic corrélé."
            confirmations.size >= 3 && contradictions.isEmpty() ->
                "Sources cohérentes : le diagnostic intégré est fiable. Procéder aux préconisations sylvicoles."
            enrichissements.isNotEmpty() ->
                "${enrichissements.first().description.take(100)} — à vérifier sur le terrain."
            else ->
                "Corrélation partielle : collecter davantage de données pour consolider le diagnostic."
        }

        return ConclusionCorrelation(certains, ambigus, pointsManquants, recommandation)
    }

    private fun CorrelationFact.champ(): String = when {
        description.contains("hydrique", ignoreCase = true)   -> "gradient hydrique"
        description.contains("trophique", ignoreCase = true)  -> "gradient trophique"
        description.contains("invasiv", ignoreCase = true)    -> "invasives"
        description.contains("hydromorphie", ignoreCase = true) -> "hydromorphie"
        else -> description.take(30)
    }
}
