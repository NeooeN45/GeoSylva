package com.forestry.counter.domain.usecase.confidence

import com.forestry.counter.domain.model.ripisylve.RipisylveObservation
import com.forestry.counter.domain.model.ripisylve.RipisylveScore
import com.forestry.counter.domain.model.station.Drainage
import com.forestry.counter.domain.model.station.StationObservation
import com.forestry.counter.domain.model.station.TextureSol
import com.forestry.counter.domain.model.station.TypeHumus
import com.forestry.counter.domain.usecase.correlateur.CorrelationEngine
import com.forestry.counter.domain.usecase.florist.GradientInferenceEngine
import kotlin.math.roundToInt

// ══════════════════════════════════════════════════════════════════════════════
//  ConfidenceEngine — Calcul réel du niveau de confiance diagnostique
//
//  Chaque score de confiance est calculé à partir de critères mesurables :
//    - complétude des données saisies
//    - cohérence inter-sources
//    - convergence des indicateurs
//    - présence de contradictions
//    - richesse du cortège
//    - qualité de la localisation
//
//  PRINCIPE : la confiance ne peut JAMAIS être forte par défaut.
//  Elle doit être gagnée par la donnée.
// ══════════════════════════════════════════════════════════════════════════════

object ConfidenceEngine {

    // ─── Types de sortie ─────────────────────────────────────────────────────

    enum class ConfidenceLevel(
        val labelFr: String,
        val shortLabel: String,
        val score: Int,       // 0–100
        val colorHex: Long
    ) {
        FORTE(      "Forte confiance",          "Forte",   100, 0xFF2E7D32),
        BONNE(      "Bonne confiance",          "Bonne",    75, 0xFF43A047),
        MOYENNE(    "Confiance moyenne",        "Moyenne",  50, 0xFFF9A825),
        FAIBLE(     "Confiance faible",         "Faible",   25, 0xFFEF6C00),
        INSUFFISANTE("Données insuffisantes",   "Insuf.",    0, 0xFFC62828)
    }

    data class ConfidenceReport(
        val level: ConfidenceLevel,
        val score: Int,                          // 0–100
        val pointsForts: List<String>,           // ce qui renforce la confiance
        val pointsFaibles: List<String>,         // ce qui affaiblit la confiance
        val donnéesManquantes: List<String>,     // ce qu'il faudrait saisir
        val contradictions: List<String>,        // conflits détectés
        val conseilAmelioration: String          // conseil synthétique
    ) {
        fun isReliable(): Boolean = score >= 60
    }

    data class ConfidenceCriterion(
        val label: String,
        val points: Int,
        val maxPoints: Int,
        val detail: String = ""
    )

    // ─── Station ─────────────────────────────────────────────────────────────

    /**
     * Calcule la confiance d'un diagnostic stationnel.
     *
     * Critères (total 100 pts max) :
     *  GPS               : 15 pts
     *  Photos            : 15 pts (3+ = max)
     *  Pédologie         : 20 pts (profondeur, texture, humus, pH, HCl, drainage)
     *  Topographie       : 10 pts (pente, exposition, position topo)
     *  Gradients         : 15 pts (H, T, L renseignés != valeur neutre 3)
     *  Cortège flore     : 15 pts (espèces indicatrices)
     *  Cohérence interne : 10 pts (gradients convergents)
     */
    fun computeStationConfidence(
        station: StationObservation,
        floraGradients: GradientInferenceEngine.GradientResult? = null,
        correlReport: CorrelationEngine.CorrelationReport? = null
    ): ConfidenceReport {
        val criteria = mutableListOf<ConfidenceCriterion>()
        val forts = mutableListOf<String>()
        val faibles = mutableListOf<String>()
        val manquants = mutableListOf<String>()
        val contradictions = mutableListOf<String>()

        // GPS (15 pts)
        val gpsPoints = when {
            station.latitude != null && station.longitude != null && station.altitudeM != null -> 15
            station.latitude != null && station.longitude != null -> 10
            station.commune.isNotBlank() -> 4
            else -> 0
        }
        criteria += ConfidenceCriterion("Géolocalisation", gpsPoints, 15)
        if (gpsPoints == 15) forts += "GPS complet avec altitude"
        else if (gpsPoints == 0) manquants += "Géolocalisation GPS absente"
        else faibles += "GPS partiel (altitude manquante)"

        // Photos (15 pts)
        val photoPoints = (station.photos.size.coerceAtMost(3) * 5)
        criteria += ConfidenceCriterion("Photos", photoPoints, 15)
        when {
            station.photos.size >= 3 -> forts += "${station.photos.size} photos terrain"
            station.photos.size >= 1 -> faibles += "Peu de photos (${station.photos.size}/3)"
            else -> manquants += "Aucune photo terrain"
        }

        // Pédologie (20 pts)
        var pedoScore = 0
        if (station.profondeurSolCm != null) { pedoScore += 4; forts += "Profondeur sol mesurée" }
        else manquants += "Profondeur du sol"
        if (station.texture != TextureSol.INCONNUE) { pedoScore += 4; forts += "Texture identifiée" }
        else manquants += "Texture du sol"
        if (station.humus != TypeHumus.INCONNU) { pedoScore += 4; forts += "Type d'humus identifié" }
        else manquants += "Type d'humus"
        if (station.phEstime != null) pedoScore += 4
        if (station.drainage != Drainage.NORMAL) pedoScore += 2  // != valeur par défaut = renseigné
        if (station.hydromorphieProfondeurCm != null) { pedoScore += 2; forts += "Profondeur hydromorphie mesurée" }
        criteria += ConfidenceCriterion("Pédologie", pedoScore.coerceAtMost(20), 20)

        // Topographie (10 pts)
        var topoScore = 0
        if (station.pentePct != null) topoScore += 4
        if (station.exposition.name != "INCONNUE") topoScore += 3
        if (station.positionTopo.name != "INCONNUE") { topoScore += 3; forts += "Position topographique renseignée" }
        else manquants += "Position topographique"
        criteria += ConfidenceCriterion("Topographie", topoScore, 10)

        // Gradients écologiques (15 pts)
        val gradientsRenseignes = listOf(
            station.gradientHydrique != 3,
            station.gradientTrophique != 3,
            station.gradientLumineux != 3
        ).count { it }
        val gradientPoints = gradientsRenseignes * 5
        criteria += ConfidenceCriterion("Gradients observés", gradientPoints, 15)
        if (gradientsRenseignes == 3) forts += "3 gradients écologiques renseignés"
        else if (gradientsRenseignes == 0) manquants += "Gradients écologiques (H, T, L) non modifiés de la valeur neutre"

        // Cortège flore (15 pts)
        val nbEspeces = station.especesIndicatrices.size
        val floraPoints = when {
            nbEspeces >= 6 -> 15
            nbEspeces >= 3 -> 10
            nbEspeces >= 1 -> 5
            else -> 0
        }
        criteria += ConfidenceCriterion("Cortège floristique", floraPoints, 15,
            "$nbEspeces espèce(s) renseignée(s)")
        if (nbEspeces >= 6) forts += "Cortège floristique riche ($nbEspeces espèces)"
        else if (nbEspeces < 3) manquants += "Enrichir le cortège (actuellement $nbEspeces esp.)"

        // Cohérence interne (10 pts)
        var coherencePoints = 10
        if (floraGradients != null) {
            val nbContradictions = floraGradients.conflits.size
            coherencePoints = when {
                nbContradictions == 0 && floraGradients.nbTaxonsAnalysables >= 5 -> 10
                nbContradictions == 0 -> 7
                nbContradictions <= 2 -> 5
                else -> 2
            }
            if (nbContradictions > 0) {
                faibles += "${nbContradictions} conflit(s) interne(s) dans le cortège floristique"
                floraGradients.conflits.forEach { c -> contradictions += c.description }
            }
        } else if (nbEspeces < 3) {
            coherencePoints = 3
            faibles += "Cohérence non calculable : cortège trop pauvre"
        }
        criteria += ConfidenceCriterion("Cohérence interne", coherencePoints, 10)

        // Contradictions inter-sources (malus)
        if (correlReport != null) {
            correlReport.alertesCritiques.forEach { contradictions += it }
            if (correlReport.contradictions.size > 2) faibles += "${correlReport.contradictions.size} contradictions inter-sources"
        }

        val rawScore = criteria.sumOf { it.points }
        val malus = (correlReport?.contradictions?.size ?: 0) * 5
        val finalScore = (rawScore - malus).coerceIn(0, 100)

        val level = toLevel(finalScore)
        return ConfidenceReport(
            level = level,
            score = finalScore,
            pointsForts = forts.distinct(),
            pointsFaibles = faibles.distinct(),
            donnéesManquantes = manquants.distinct(),
            contradictions = contradictions.distinct(),
            conseilAmelioration = buildStationAdvice(finalScore, manquants, faibles)
        )
    }

    // ─── Ripisylve ────────────────────────────────────────────────────────────

    /**
     * Calcule la confiance d'un diagnostic ripisylve.
     *
     * Critères (total 100 pts max) :
     *  GPS               : 10 pts
     *  Photos            : 20 pts (3+ = max)
     *  Critères structure: 30 pts (nb strates, classes diam, microhabitats)
     *  Menaces renseignées: 15 pts (sanitaire, invasives, stabilité)
     *  Cortège flore     : 15 pts
     *  Cohérence         : 10 pts
     */
    fun computeRipisylveConfidence(
        obs: RipisylveObservation,
        score: RipisylveScore
    ): ConfidenceReport {
        val forts = mutableListOf<String>()
        val faibles = mutableListOf<String>()
        val manquants = mutableListOf<String>()
        val contradictions = mutableListOf<String>()
        var total = 0

        // GPS (10 pts)
        val gpsOk = obs.latitude != null && obs.longitude != null
        total += if (gpsOk) { forts += "GPS tronçon localisé"; 10 } else { manquants += "GPS du tronçon"; 0 }

        // Photos (20 pts)
        val photoPts = (obs.photos.size.coerceAtMost(4) * 5)
        total += photoPts
        when {
            obs.photos.size >= 3 -> forts += "${obs.photos.size} photos du tronçon"
            obs.photos.size >= 1 -> faibles += "Photos insuffisantes (${obs.photos.size}/3 min)"
            else -> manquants += "Photos du tronçon (min. 3 requises pour validation)"
        }

        // Structure végétale (30 pts)
        var structPts = 0
        structPts += score.nbStrates * 5      // 0, 5, 10, 15
        if (score.nbStrates == 3) forts += "Structure tristratiée"
        else if (score.nbStrates < 2) faibles += "Structure appauvrie (${score.nbStrates} strate(s))"
        structPts += score.nbClassesDiam * 4  // 0, 4, 8, 12, 16
        if (score.nbClassesDiam >= 3) forts += "Diversité de diamètres"
        else if (score.nbClassesDiam < 2) manquants += "Classes de diamètre peu renseignées"
        if (obs.continuitePct > 0.0) structPts += 2
        else manquants += "Continuité boisée (%) non renseignée"
        total += structPts.coerceAtMost(30)

        // Menaces (15 pts)
        val menacesCriterions = listOf(
            obs.sanitairePct > 0,
            obs.invasivesPct > 0,
            obs.stabilitePct > 0,
            obs.inadapteesMode.name != "ABSENCE" // renseigné même si positif
        ).count { it }
        val menacesPts = (menacesCriterions * 4).coerceAtMost(15)
        total += menacesPts
        if (menacesCriterions >= 3) forts += "Pressions/menaces bien évaluées"
        else manquants += "Compléter les indicateurs de pression (sanitaire, invasives, stabilité)"

        // Cortège flore ripisylve (15 pts)
        val nbFlore = obs.especesObservees.size
        val florePts = when {
            nbFlore >= 5 -> 15
            nbFlore >= 3 -> 10
            nbFlore >= 1 -> 5
            else -> 0
        }
        total += florePts
        if (nbFlore >= 5) forts += "Cortège ripisylve riche ($nbFlore espèces)"
        else manquants += "Enrichir le cortège ripisylve ($nbFlore esp.)"

        // Cohérence score (10 pts)
        val cohPts = when {
            score.nbMicrohabitats >= 2 && score.nbStrates >= 2 -> 10
            score.scorePenalite < -10 && obs.photos.isEmpty() -> 3  // pénalités sans preuves photo
            score.scoreTotal > 0 -> 7
            else -> 4
        }
        total += cohPts
        if (score.scorePenalite < -10 && obs.photos.size < 3) {
            contradictions += "Pénalités importantes (${score.scorePenalite} pts) sans photos de validation"
        }

        val finalScore = total.coerceIn(0, 100)
        return ConfidenceReport(
            level = toLevel(finalScore),
            score = finalScore,
            pointsForts = forts.distinct(),
            pointsFaibles = faibles.distinct(),
            donnéesManquantes = manquants.distinct(),
            contradictions = contradictions.distinct(),
            conseilAmelioration = buildRipisylveAdvice(finalScore, manquants)
        )
    }

    // ─── Inférence floristique ────────────────────────────────────────────────

    /**
     * Traduit un [GradientInferenceEngine.ConfidenceGradient] en [ConfidenceReport].
     */
    fun computeFloraConfidence(
        result: GradientInferenceEngine.GradientResult
    ): ConfidenceReport {
        val forts = mutableListOf<String>()
        val faibles = mutableListOf<String>()
        val manquants = mutableListOf<String>()
        val contradictions = mutableListOf<String>()

        if (result.nbTaxonsAnalysables >= 8) forts += "${result.nbTaxonsAnalysables} taxons analysables"
        else if (result.nbTaxonsAnalysables < 3) manquants += "Ajouter des espèces indicatrices (min. 3)"

        if (result.cohérenceInterne == GradientInferenceEngine.Coherence.FORTE) {
            forts += "Cortège cohérent"
        } else if (result.cohérenceInterne == GradientInferenceEngine.Coherence.CONTRADICTOIRE) {
            faibles += "Contradictions fortes dans le cortège"
            result.especesContradictoires.forEach { contradictions += it }
        }
        if (result.conflits.isNotEmpty()) {
            result.conflits.forEach { c -> contradictions += "${c.gradient} : ${c.description}" }
        }
        if (result.especesTirantVersSec.size > 2 && result.especesTirantVersFrais.size > 2) {
            faibles += "Espèces xérophiles et hygrophiles coexistentes — vérifier micro-variation"
        }

        val score = when (result.confidenceLevel) {
            GradientInferenceEngine.ConfidenceGradient.HAUTE       -> 85
            GradientInferenceEngine.ConfidenceGradient.MOYENNE     -> 60
            GradientInferenceEngine.ConfidenceGradient.FAIBLE      -> 35
            GradientInferenceEngine.ConfidenceGradient.INSUFFISANTE -> 10
        }
        return ConfidenceReport(
            level = toLevel(score),
            score = score,
            pointsForts = forts.distinct(),
            pointsFaibles = faibles.distinct(),
            donnéesManquantes = manquants.distinct(),
            contradictions = contradictions.distinct(),
            conseilAmelioration = if (score < 60)
                "Ajouter des espèces herbacées indicatrices (meilleures pour les gradients Ellenberg)"
            else "Cortège de bonne qualité — confirmer avec les données pédologiques terrain"
        )
    }

    // ─── Corrélations ─────────────────────────────────────────────────────────

    /**
     * Confiance d'un rapport de corrélation croisée.
     */
    fun computeCorrelationConfidence(
        report: CorrelationEngine.CorrelationReport,
        nbSourcesDisponibles: Int   // 2 = station+flore, 3 = +ripisylve, 4 = +GPS
    ): ConfidenceReport {
        val forts = mutableListOf<String>()
        val faibles = mutableListOf<String>()
        val manquants = mutableListOf<String>()

        var score = when (nbSourcesDisponibles) {
            4 -> 80
            3 -> 65
            2 -> 50
            else -> 20
        }

        val confirmRate = if (report.confirmations.size + report.contradictions.size > 0)
            report.confirmations.size.toFloat() / (report.confirmations.size + report.contradictions.size)
        else 0.5f

        score = (score * confirmRate).toInt()
            .plus(report.confirmations.size * 3)
            .minus(report.contradictions.size * 5)
            .minus(report.alertesCritiques.size * 8)
            .coerceIn(0, 100)

        if (nbSourcesDisponibles >= 3) forts += "$nbSourcesDisponibles sources corrélées"
        else if (nbSourcesDisponibles < 3) manquants += "Ajouter données ripisylve ou GPS pour enrichir la corrélation"

        if (report.confirmations.isNotEmpty()) forts += "${report.confirmations.size} confirmation(s) croisée(s)"
        if (report.alertesCritiques.isNotEmpty()) faibles += "${report.alertesCritiques.size} alerte(s) critique(s)"
        if (report.contradictions.size > 2) faibles += "${report.contradictions.size} contradictions inter-sources"

        return ConfidenceReport(
            level = toLevel(score),
            score = score,
            pointsForts = forts,
            pointsFaibles = faibles,
            donnéesManquantes = manquants,
            contradictions = report.alertesCritiques,
            conseilAmelioration = when {
                score >= 75 -> "Corrélation robuste — les sources se confirment mutuellement"
                score >= 50 -> "Corrélation partielle — compléter les données pour renforcer les conclusions"
                else -> "Corrélation faible — des contradictions importantes limitent la fiabilité des déductions"
            }
        )
    }

    // ─── Recommandations essences ─────────────────────────────────────────────

    /**
     * Confiance d'une recommandation d'essences.
     * Dépend de la fiabilité de la zone climatique détectée et du cortège flore.
     */
    fun computeRecommendationConfidence(
        zoneDetectionConfidence: Int,      // 0–100 : confidence de ClimateZone.detect
        floraScore: Int,                   // score FloraConfidence
        stationScore: Int,                 // score StationConfidence
        hasDepartmentPack: Boolean
    ): ConfidenceReport {
        val forts = mutableListOf<String>()
        val faibles = mutableListOf<String>()
        val manquants = mutableListOf<String>()

        val score = ((zoneDetectionConfidence * 0.4 + floraScore * 0.35 + stationScore * 0.25)
            + if (hasDepartmentPack) 10 else 0).toInt().coerceIn(0, 100)

        if (zoneDetectionConfidence >= 70) forts += "Zone bioclimatique fiable"
        else faibles += "Zone bioclimatique approximative (GPS ou altitude manquants)"

        if (floraScore >= 60) forts += "Cortège floristique validant la zone"
        else manquants += "Enrichir le cortège pour mieux cibler les essences"

        if (hasDepartmentPack) forts += "Pack départemental actif — données locales"
        else manquants += "Pack régional/départemental pour des recommandations localisées"

        return ConfidenceReport(
            level = toLevel(score),
            score = score,
            pointsForts = forts,
            pointsFaibles = faibles,
            donnéesManquantes = manquants,
            contradictions = emptyList(),
            conseilAmelioration = when {
                score >= 75 -> "Recommandations d'essences fiables pour ce contexte"
                score >= 50 -> "Recommandations indicatives — compléter GPS et cortège pour affiner"
                else -> "Recommandations à titre indicatif uniquement — localisation ou flore insuffisantes"
            }
        )
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    fun toLevel(score: Int): ConfidenceLevel = when {
        score >= 85 -> ConfidenceLevel.FORTE
        score >= 65 -> ConfidenceLevel.BONNE
        score >= 45 -> ConfidenceLevel.MOYENNE
        score >= 25 -> ConfidenceLevel.FAIBLE
        else        -> ConfidenceLevel.INSUFFISANTE
    }

    private fun buildStationAdvice(score: Int, manquants: List<String>, faibles: List<String>): String {
        if (score >= 85) return "Diagnostic stationnel complet et fiable."
        val top = (manquants + faibles).take(2)
        return if (top.isEmpty()) "Enrichir les données pour consolider le diagnostic."
        else "Priorité : ${top.joinToString(" ; ")}."
    }

    private fun buildRipisylveAdvice(score: Int, manquants: List<String>): String {
        if (score >= 80) return "Diagnostic ripisylve bien documenté."
        return if (manquants.isNotEmpty())
            "À compléter : ${manquants.take(2).joinToString(" ; ")}."
        else "Enrichir les observations de terrain."
    }
}
