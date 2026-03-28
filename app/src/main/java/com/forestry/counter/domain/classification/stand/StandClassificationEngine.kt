package com.forestry.counter.domain.classification.stand

import com.forestry.counter.domain.calculation.SanitySeverity
import com.forestry.counter.presentation.screens.forestry.MartelageStats

/**
 * Moteur de classification automatique des peuplements forestiers.
 *
 * Détermine automatiquement le type de peuplement à partir des données
 * dendrométriques de [MartelageStats]. Lorsque les données sont insuffisantes,
 * génère une liste de [ClassificationQuestion] à poser à l'utilisateur.
 *
 * Workflow :
 *   1. Appel à [classify] avec les réponses utilisateur vides → obtenir les questions
 *   2. Afficher les questions, recueillir les réponses
 *   3. Appel à [classify] avec les réponses → résultat complet
 */
object StandClassificationEngine {

    // ─── Constantes de décision ───────────────────────────────────────────────

    private const val CV_EQUIENNE_MAX     = 15.0
    private const val CV_QUASI_EQ_MAX     = 25.0
    private const val CV_IRREG_SIMPLE_MAX = 40.0
    private const val CV_IRREGULIER_MAX   = 55.0

    private const val CONF_HIGH   = 0.85f
    private const val CONF_MEDIUM = 0.65f
    private const val CONF_LOW    = 0.45f

    // ─── Point d'entrée principal ─────────────────────────────────────────────

    /**
     * Classifie le peuplement à partir de [stats] et des [userAnswers].
     *
     * @param stats       Statistiques dendrométriques calculées par l'app
     * @param userAnswers Map questionId → indexRéponse (de 0 à n-1), peut être vide
     * @return Résultat complet, incluant les questions encore non répondues si besoin
     */
    fun classify(
        stats: MartelageStats,
        userAnswers: Map<String, Int> = emptyMap()
    ): StandClassificationResult {

        // ── Réponses utilisateur ──
        val hasCepeesAns  = userAnswers["has_cepees"]      ?: -1
        val hasReservesAns= userAnswers["has_reserves"]    ?: -1
        val hasRegenAns   = userAnswers["has_regeneration"]?: -1
        val isPlantAns    = userAnswers["is_plantation"]   ?: -1
        val ecoZoneAns    = userAnswers["eco_zone"]        ?: -1

        val hasCepees         = hasCepeesAns  >= 1
        val hasMajorityCepees = hasCepeesAns  == 2
        val hasReserves       = hasReservesAns >= 1
        val hasGoodReserves   = hasReservesAns == 2
        val hasRegen          = hasRegenAns   >= 1
        val isPlantation      = isPlantAns    == 1
        val isEnrichissement  = isPlantAns    == 2

        // ── Données dendrométriques ──
        val cv    = stats.cvDiam ?: 25.0
        val dg    = stats.dg ?: stats.dm ?: 30.0
        val nPerHa = stats.nPerHa
        val gPerHa = stats.gPerHa
        val hRatio = if (stats.hLorey != null && stats.meanH != null && stats.meanH > 0)
                         stats.hLorey / stats.meanH else null

        // ── Essence dominante (avant triangle pour déterminer seuils CNPF feuillu/résineux) ──
        val dominantEssence = stats.perEssence.maxByOrNull { it.gPct }
        val dominantEssenceCode = dominantEssence?.essenceCode ?: "INCONNU"
        val dominantEssenceName = dominantEssence?.essenceName ?: "Inconnue"
        val isResineux = isResineuxEssence(dominantEssenceCode)

        // ── Triangle des structures (seuils CNPF conformes feuillu/résineux) ──
        val diamRatio = computeDiameterRatio(stats, isResineux)

        // ── Confidence tracking ──
        var confidence = CONF_HIGH
        val unanswered = mutableListOf<ClassificationQuestion>()

        // ── Questions nécessaires ──
        if (hasCepeesAns < 0 && looksLikeTaillis(nPerHa, dg, cv)) {
            unanswered += StandTypologyDatabase.QUESTION_CEPEES
            confidence -= 0.12f
        }
        if (hasReservesAns < 0 && hasCepees) {
            unanswered += StandTypologyDatabase.QUESTION_RESERVES
            confidence -= 0.08f
        }
        if (hasRegenAns < 0 && dg > 35) {
            unanswered += StandTypologyDatabase.QUESTION_REGENERATION
            confidence -= 0.05f
        }
        if (isPlantAns < 0 && isLikelyPlantation(cv, stats)) {
            unanswered += StandTypologyDatabase.QUESTION_PLANTATION
            confidence -= 0.08f
        }
        if (ecoZoneAns < 0) {
            unanswered += StandTypologyDatabase.QUESTION_ZONE
            confidence -= 0.05f
        }

        // ── Composition ──
        val composition = detectComposition(stats)

        // ── Mode de traitement ──
        val treatmentMode = StandTypologyDatabase.treatmentModeFromData(
            cvDiam = cv,
            hasCepees = hasCepees,
            nPerHa = nPerHa,
            gPerHa = gPerHa,
            dominantEssenceIsResineux = isResineux,
            hasReserveAboveTaillis = hasReserves,
            userConfirmedTaillis = hasMajorityCepees
        )

        // ── Structure d'âge ──
        val ageStructure = StandTypologyDatabase.ageStructureFromCvDiam(cv, hasCepees)

        // ── Structure verticale ──
        val verticalStructure = StandTypologyDatabase.verticalStructureFromHRatio(hRatio, cv)

        // ── Origine ──
        val origin = detectOrigin(isPlantation, isEnrichissement, hasCepees, composition)

        // ── Stade de développement ──
        val stage = DevelopmentStage.fromDg(dg)

        // ── Type écologique ──
        val ecologicalType = detectEcologicalType(ecoZoneAns, stats)

        // ── Dynamique ──
        val dynamic = StandTypologyDatabase.dynamicFromStage(stage, hasRegen)

        // ── État de perturbation ──
        val disturbance = detectDisturbance(stats)

        // ── Objectif suggéré ──
        val suggestedObjective = suggestObjective(treatmentMode, stage, isResineux, composition)

        // ── Programme de gestion ──
        val program = StandTypologyDatabase.managementProgram(
            treatmentMode, dominantEssenceCode, stage, nPerHa, gPerHa, suggestedObjective
        )

        // ── Diagnostic ──
        val diagnosis = StandTypologyDatabase.buildDiagnosis(
            treatmentMode, ageStructure, verticalStructure, stage, composition, origin,
            dynamic, disturbance, diamRatio?.trianglePosition(), cv, gPerHa, nPerHa,
            dominantEssenceName, diamRatio
        )

        return StandClassificationResult(
            treatmentMode        = treatmentMode,
            ageStructure         = ageStructure,
            verticalStructure    = verticalStructure,
            composition          = composition,
            origin               = origin,
            developmentStage     = stage,
            ecologicalType       = ecologicalType,
            dynamic              = dynamic,
            disturbanceState     = disturbance,
            suggestedObjective   = suggestedObjective,
            diameterRatio        = diamRatio,
            confidence           = confidence.coerceIn(0.20f, 1.0f),
            missingDataQuestions = unanswered,
            diagnosis            = diagnosis,
            managementProgram    = program
        )
    }

    // ─── Détection composition ────────────────────────────────────────────────

    private fun detectComposition(stats: MartelageStats): StandComposition {
        if (stats.perEssence.isEmpty()) return StandComposition.MELANGE_PIED_A_PIED
        val dominant = stats.perEssence.maxByOrNull { it.gPct } ?: return StandComposition.MELANGE_PIED_A_PIED
        val dominantPct = dominant.gPct
        val isResD = isResineuxEssence(dominant.essenceCode)
        val second = stats.perEssence.filter { it.essenceCode != dominant.essenceCode }.maxByOrNull { it.gPct }
        val secondPct = second?.gPct ?: 0.0
        val secondIsRes = second?.let { isResineuxEssence(it.essenceCode) } ?: false
        return when {
            dominantPct >= 80.0 && isResD  -> StandComposition.PUR_RESINEUX
            dominantPct >= 80.0 && !isResD -> StandComposition.PUR_FEUILLU
            // Mélange résino-feuillu avec dominante claire
            isResD != secondIsRes && dominantPct >= 60.0 -> StandComposition.MELANGE_PIED_A_PIED
            // Deux essences de type résineuses ou feuillues
            isResD == secondIsRes && dominantPct >= 60.0 && secondPct >= 20.0 ->
                if (isResD) StandComposition.PUR_RESINEUX else StandComposition.PUR_FEUILLU
            // Mélange équilibré → sans GPS on classe en pied-à-pied par défaut
            else -> StandComposition.MELANGE_PIED_A_PIED
        }
    }

    // ─── Calcul triangle des structures ──────────────────────────────────────

    private fun computeDiameterRatio(stats: MartelageStats, isResineux: Boolean): DiameterCategoryRatio? {
        if (stats.classDistribution.isEmpty()) return null
        // Seules les tiges précomptables (DHP > 17.5 cm, soit diamClass ≥ 18)
        val precomptable = stats.classDistribution.filter { it.diamClass >= 18 }
        val total = precomptable.sumOf { it.n }.toDouble()
        if (total <= 0) return null

        val pbN: Int
        val bmN: Int
        val gbN: Int
        val tgbN: Int
        if (isResineux) {
            // Résineux : PB 17.5–27.5, BM 27.5–42.5, GB 42.5–62.5, TGB > 62.5
            pbN  = precomptable.filter { it.diamClass in 18..27  }.sumOf { it.n }
            bmN  = precomptable.filter { it.diamClass in 28..42  }.sumOf { it.n }
            gbN  = precomptable.filter { it.diamClass in 43..62  }.sumOf { it.n }
            tgbN = precomptable.filter { it.diamClass >= 63 }.sumOf { it.n }
        } else {
            // Feuillus : PB 17.5–27.5, BM 27.5–47.5, GB 47.5–67.5, TGB > 67.5
            pbN  = precomptable.filter { it.diamClass in 18..27  }.sumOf { it.n }
            bmN  = precomptable.filter { it.diamClass in 28..47  }.sumOf { it.n }
            gbN  = precomptable.filter { it.diamClass in 48..67  }.sumOf { it.n }
            tgbN = precomptable.filter { it.diamClass >= 68 }.sumOf { it.n }
        }
        return DiameterCategoryRatio(
            pbPct  = pbN  / total * 100,
            bmPct  = bmN  / total * 100,
            gbPct  = gbN  / total * 100,
            tgbPct = tgbN / total * 100
        )
    }

    // ─── Heuristiques ─────────────────────────────────────────────────────────

    private fun looksLikeTaillis(nPerHa: Double, dg: Double, cv: Double): Boolean =
        (nPerHa > 1500 && dg < 20.0 && cv > 20.0) ||
        (nPerHa > 2500 && dg < 15.0)  // taillis très dense à faibles diamètres

    private fun isLikelyPlantation(cv: Double, stats: MartelageStats): Boolean =
        (cv < 12.0 && stats.perEssence.size <= 2 && (stats.dm ?: 0.0) > 15.0) ||
        (cv < 8.0 && stats.nPerHa > 100.0)  // très faible CV = structure très régulière

    private fun isResineuxEssence(code: String): Boolean {
        val up = code.uppercase()
        return up.contains("PIN") || up.contains("SAPIN") || up.contains("EPICEA") ||
               up.contains("DOUGLAS") || up.contains("MELEZE") || up.contains("CEDRE") ||
               up.contains("IF") || up.contains("GENEVRIER") || up.contains("CYPRES") ||
               up.contains("TSUGA") || up.contains("THUYA") || up.contains("SEQUOIA") ||
               up.contains("CRYPTOMERE")
    }

    // ─── Origine ──────────────────────────────────────────────────────────────

    private fun detectOrigin(
        isPlantation: Boolean, isEnrichissement: Boolean,
        hasCepees: Boolean, comp: StandComposition
    ): StandOrigin = when {
        hasCepees -> StandOrigin.REJETS_SOUCHE
        isPlantation && comp in listOf(StandComposition.PUR_FEUILLU, StandComposition.PUR_RESINEUX) ->
            StandOrigin.PLANTATION_MONO
        isPlantation -> StandOrigin.PLANTATION_MELANGEE
        isEnrichissement -> StandOrigin.ENRICHISSEMENT
        else -> StandOrigin.REGENERATION_NATURELLE
    }

    // ─── Type écologique ──────────────────────────────────────────────────────

    private fun detectEcologicalType(ecoZoneAns: Int, stats: MartelageStats): EcologicalType {
        // Si réponse utilisateur disponible
        if (ecoZoneAns >= 0) return when (ecoZoneAns) {
            0 -> EcologicalType.PLAINE
            1 -> EcologicalType.MONTAGNARDE
            2 -> EcologicalType.MEDITERRANEENNE
            3 -> EcologicalType.RIPISYLVE
            else -> EcologicalType.INCONNU
        }
        // Essais heuristiques sur les essences
        val codes = stats.perEssence.map { it.essenceCode.uppercase() }
        return when {
            codes.any { it.contains("AULNE") || it.contains("SAULE") || it.contains("PEUPLIER") } ->
                EcologicalType.RIPISYLVE
            codes.any { it.contains("PIN_ALEP") || it.contains("CHENE_VERT") || it.contains("CHENE_LIEGE") || it.contains("GENEVRIER") } ->
                EcologicalType.MEDITERRANEENNE
            codes.any { it.contains("SAPIN") || it.contains("EPICEA") || it.contains("MELEZE") || it.contains("HETRE_COMMUN") } ->
                EcologicalType.MONTAGNARDE
            else -> EcologicalType.PLAINE
        }
    }

    // ─── Perturbation ─────────────────────────────────────────────────────────

    private fun detectDisturbance(stats: MartelageStats): DisturbanceState {
        val nTotal = stats.nTotal.toDouble().coerceAtLeast(1.0)
        val bio = stats.biodiversity
        val dyingPct = (bio?.dyingTreeCount ?: 0) / nTotal * 100
        val deadPct  = (bio?.deadTreeCount  ?: 0) / nTotal * 100
        val hasErrors = stats.sanityWarnings.any { it.severity == SanitySeverity.ERROR }
        // Élancement = H_m × 100 / D_cm (formula correcte, seuil > 90 = risque modéré)
        val slenderness = if (stats.dg != null && stats.meanH != null && stats.dg > 0)
            stats.meanH * 100.0 / stats.dg else null
        val hasHighSlenderness = slenderness != null && slenderness > 100.0
        val hasModerateSlenderness = slenderness != null && slenderness > 90.0
        // Ratio V/G anormal = signal de perturbation (hauteurs sous-estimées ou tarif inadapté)
        val hasVGAnomaly = stats.ratioVG != null && (stats.ratioVG < 3.0 || stats.ratioVG > 25.0)

        return when {
            dyingPct > 20 || (deadPct > 15 && bio?.deadTreeCount?.let { it > 5 } == true) ->
                DisturbanceState.DEPERISSANT
            dyingPct > 8 || hasErrors || hasHighSlenderness ->
                DisturbanceState.STRESSE
            deadPct > 5 || hasModerateSlenderness || hasVGAnomaly ->
                DisturbanceState.PERTURBE
            else -> DisturbanceState.SAIN
        }
    }

    // ─── Objectif suggéré ─────────────────────────────────────────────────────

    private fun suggestObjective(
        mode: TreatmentMode,
        stage: DevelopmentStage,
        isResineux: Boolean,
        comp: StandComposition
    ): ManagementObjective = when {
        mode in listOf(TreatmentMode.FUTAIE_JARDINEE, TreatmentMode.FUTAIE_JARDINEE_GROUPES) ->
            ManagementObjective.MULTIFONCTIONNEL
        mode in listOf(TreatmentMode.TAILLIS_SIMPLE, TreatmentMode.TAILLIS_DENSE) ->
            ManagementObjective.BOIS_ENERGIE
        stage == DevelopmentStage.FUTAIE_SURANNEE ->
            ManagementObjective.BIODIVERSITE
        isResineux && stage in listOf(DevelopmentStage.JEUNE_FUTAIE, DevelopmentStage.FUTAIE_ADULTE) ->
            ManagementObjective.BOIS_OEUVRE
        comp in listOf(StandComposition.PUR_FEUILLU, StandComposition.MELANGE_PIED_A_PIED) ->
            ManagementObjective.BOIS_OEUVRE
        else -> ManagementObjective.MULTIFONCTIONNEL
    }

    // ─── Résumé court ─────────────────────────────────────────────────────────

    /**
     * Génère une description combinée courte du peuplement (une phrase).
     */
    fun shortLabel(result: StandClassificationResult): String {
        val comp = when (result.composition) {
            StandComposition.PUR_FEUILLU, StandComposition.PUR_RESINEUX -> "pur"
            StandComposition.MELANGE_PIED_A_PIED -> "mélangé pied à pied"
            StandComposition.MELANGE_BOUQUETS -> "mélangé par bouquets"
            else -> "mélangé"
        }
        return "${result.treatmentMode.label} $comp — ${result.developmentStage.label}"
    }

    /**
     * Indique si une classification partielle peut être affichée même sans toutes les réponses.
     */
    fun hasEnoughForPartialResult(stats: MartelageStats): Boolean =
        stats.nTotal >= 5 && stats.gPerHa > 0.0

    /**
     * Retourne les questions strictement nécessaires avant toute classification.
     * Si aucune, la classification peut se faire automatiquement.
     */
    fun requiredQuestions(stats: MartelageStats): List<ClassificationQuestion> {
        val q = mutableListOf<ClassificationQuestion>()
        val cv    = stats.cvDiam ?: 25.0
        val dg    = stats.dg ?: stats.dm ?: 20.0
        val nPerHa = stats.nPerHa
        if (looksLikeTaillis(nPerHa, dg, cv)) q += StandTypologyDatabase.QUESTION_CEPEES
        if (isLikelyPlantation(cv, stats)) q += StandTypologyDatabase.QUESTION_PLANTATION
        q += StandTypologyDatabase.QUESTION_ZONE
        return q
    }
}
