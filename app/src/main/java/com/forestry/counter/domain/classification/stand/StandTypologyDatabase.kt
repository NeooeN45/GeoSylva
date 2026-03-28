package com.forestry.counter.domain.classification.stand

/**
 * Référentiel de typologies forestières pour la classification automatique.
 * Inspiré des classifications IGN, ONF et CNPF (France).
 * Contient les seuils dendrométriques, explications, risques et programmes de gestion.
 */
object StandTypologyDatabase {

    /**
     * S'assure que la trajectoire de densité est strictement décroissante.
     * Chaque étape est plafonnée à la valeur de l'étape précédente.
     * Les étapes redondantes (aucune réduction) sont supprimées.
     */
    private fun sanitizeDensityTrajectory(
        initial: Int,
        raw: List<Pair<String, IntRange>>
    ): List<Pair<String, IntRange>> {
        val result = mutableListOf<Pair<String, IntRange>>()
        var prevMax = initial
        for ((label, range) in raw) {
            val cappedMax = range.last.coerceAtMost(prevMax)
            val cappedMin = range.first.coerceAtMost(cappedMax)
            if (result.isEmpty() || cappedMax < prevMax) {
                result += label to cappedMin..cappedMax
                prevMax = cappedMin
            }
        }
        return result
    }

    // ── Seuils CV(D) pour la structure d'âge ─────────────────────────────────
    fun ageStructureFromCvDiam(cv: Double, hasCepees: Boolean): AgeStructure = when {
        hasCepees && cv > 30.0       -> AgeStructure.IRREGULIERE_COMPLEXE
        cv < 15.0                    -> AgeStructure.EQUIENNE
        cv < 25.0                    -> AgeStructure.QUASI_EQUIENNE
        cv < 35.0                    -> AgeStructure.IRREGULIERE_SIMPLE
        cv < 50.0                    -> AgeStructure.IRREGULIERE_COMPLEXE
        else                         -> AgeStructure.IRREGULIERE_EQUILIBREE
    }

    // ── Structure verticale depuis H Lorey / H moy ────────────────────────────
    fun verticalStructureFromHRatio(hLoreyOverHmoy: Double?, cvDiam: Double?): VerticalStructure {
        val hr = hLoreyOverHmoy ?: 1.0
        val cv = cvDiam ?: 0.0
        return when {
            hr > 1.25 || cv > 50.0  -> VerticalStructure.PLURISTRATIFIEE
            hr > 1.10 || cv > 30.0  -> VerticalStructure.BISTRATIFIEE
            hr > 1.05               -> VerticalStructure.BISTRATIFIEE
            else                    -> VerticalStructure.MONOSTRATIFIEE
        }
    }

    // ── Mode de traitement ────────────────────────────────────────────────────
    fun treatmentModeFromData(
        cvDiam: Double?,
        hasCepees: Boolean,
        nPerHa: Double,
        gPerHa: Double,
        dominantEssenceIsResineux: Boolean,
        hasReserveAboveTaillis: Boolean,
        userConfirmedTaillis: Boolean
    ): TreatmentMode {
        val cv = cvDiam ?: 25.0
        return when {
            userConfirmedTaillis && hasReserveAboveTaillis ->
                if (gPerHa > 15 && nPerHa < 400) TreatmentMode.TAILLIS_SOUS_FUTAIE_RICHE
                else TreatmentMode.TAILLIS_SOUS_FUTAIE
            userConfirmedTaillis -> when {
                nPerHa > 5000 -> TreatmentMode.TAILLIS_DENSE
                nPerHa < 1000 -> TreatmentMode.TAILLIS_CLAIR
                else          -> TreatmentMode.TAILLIS_SIMPLE
            }
            hasCepees && !hasReserveAboveTaillis -> TreatmentMode.TAILLIS_CONVERSION
            cv < 20.0 -> if (dominantEssenceIsResineux) TreatmentMode.FUTAIE_REGULIERE
                         else TreatmentMode.FUTAIE_REGULIERE
            cv < 35.0 -> TreatmentMode.FUTAIE_IRREGULIERE
            cv < 55.0 -> TreatmentMode.FUTAIE_IRREGULIERE_BOUQUETS
            else      -> TreatmentMode.FUTAIE_JARDINEE
        }
    }

    // ── Dynamique ─────────────────────────────────────────────────────────────
    fun dynamicFromStage(stage: DevelopmentStage, hasRegeneration: Boolean): StandDynamic = when {
        hasRegeneration && stage in listOf(DevelopmentStage.FUTAIE_ADULTE, DevelopmentStage.FUTAIE_MURE) ->
            StandDynamic.MATURATION
        stage == DevelopmentStage.SEMIS || stage == DevelopmentStage.FOURRE -> StandDynamic.REGENERATION
        stage == DevelopmentStage.GAULIS || stage == DevelopmentStage.PERCHIS -> StandDynamic.INSTALLATION
        stage == DevelopmentStage.JEUNE_FUTAIE -> StandDynamic.CROISSANCE
        stage == DevelopmentStage.FUTAIE_ADULTE -> StandDynamic.MATURATION
        stage == DevelopmentStage.FUTAIE_MURE -> StandDynamic.MATURATION
        stage == DevelopmentStage.FUTAIE_SURANNEE -> StandDynamic.SENESCENCE
        else -> StandDynamic.CROISSANCE
    }

    // ── Programme de gestion par contexte ─────────────────────────────────────
    fun managementProgram(
        treatmentMode: TreatmentMode,
        dominantEssenceCode: String,
        stage: DevelopmentStage,
        nPerHa: Double,
        gPerHa: Double,
        objective: ManagementObjective
    ): ManagementProgram {
        val essUp = dominantEssenceCode.uppercase()
        return when {
            // Chêne en futaie régulière → production bois d'oeuvre
            (essUp.contains("CH_") || essUp == "CHATAIGNIER") &&
            treatmentMode in listOf(TreatmentMode.FUTAIE_REGULIERE, TreatmentMode.FUTAIE_CONVERSION,
                TreatmentMode.TAILLIS_SOUS_FUTAIE_CONVERSION) ->
                cheneBourdOeuvreProgram(nPerHa, stage)

            // Hêtre
            essUp == "HETRE_COMMUN" && treatmentMode == TreatmentMode.FUTAIE_REGULIERE ->
                hetreBourdOeuvreProgram(nPerHa, stage)

            // Douglas / Sapin — résineux à croissance rapide
            essUp.contains("DOUGLAS") || essUp.contains("SAPIN") ->
                douglasResineuxProgram(nPerHa, stage)

            // Pin (maritimus, sylvestris…) — objectif mixte ou industrie
            essUp.contains("PIN_") ->
                pinProgram(nPerHa, stage)

            // Futaie jardinée (sapin-épicéa-hêtre en montagne)
            treatmentMode == TreatmentMode.FUTAIE_JARDINEE ->
                jardineProgram(gPerHa)

            // Taillis simple → conversion ou bois énergie
            treatmentMode in listOf(TreatmentMode.TAILLIS_SIMPLE, TreatmentMode.TAILLIS_DENSE,
                TreatmentMode.TAILLIS_VIEILLISSANT) ->
                taillisProgram(nPerHa, stage)

            // Taillis sous futaie → conversion
            treatmentMode in listOf(TreatmentMode.TAILLIS_SOUS_FUTAIE,
                TreatmentMode.TAILLIS_SOUS_FUTAIE_CONVERSION) ->
                tailisSousFutaieConversionProgram(nPerHa, gPerHa)

            else -> genericProgram(nPerHa, gPerHa, stage, objective)
        }
    }

    // ── Clé ClassCapital CNPF (G en m²/ha → code dizaines) ────────────────────────
    fun computeClassCapital(gPerHa: Double): String = when {
        gPerHa < 2.0  -> "00"
        gPerHa < 5.0  -> "0"
        gPerHa < 10.0 -> "1"
        gPerHa < 15.0 -> "2"
        gPerHa < 20.0 -> "3"
        gPerHa < 25.0 -> "4"
        gPerHa < 30.0 -> "5"
        else          -> "6"
    }

    // ── Préfixe de régime CNPF (F/M/T/R) ───────────────────────────────────
    fun computeRegimPrefix(mode: TreatmentMode, stage: DevelopmentStage): String = when {
        stage in listOf(DevelopmentStage.SEMIS, DevelopmentStage.FOURRE,
                        DevelopmentStage.GAULIS, DevelopmentStage.PERCHIS) &&
        mode == TreatmentMode.FUTAIE_REGULIERE -> "R"
        mode in listOf(TreatmentMode.TAILLIS_SOUS_FUTAIE, TreatmentMode.TAILLIS_SOUS_FUTAIE_RICHE,
                       TreatmentMode.TAILLIS_SOUS_FUTAIE_PAUVRE, TreatmentMode.TAILLIS_SOUS_FUTAIE_CONVERSION,
                       TreatmentMode.TAILLIS_CONVERSION, TreatmentMode.TAILLIS_SOUS_COUVERT) -> "M"
        mode in listOf(TreatmentMode.TAILLIS_SIMPLE, TreatmentMode.TAILLIS_DENSE,
                       TreatmentMode.TAILLIS_CLAIR, TreatmentMode.TAILLIS_VIEILLISSANT) -> "T"
        else -> "F"
    }

    // ── Code CNPF complet (ex. F12, M22, T14) ────────────────────────────────
    fun buildCNPFTypeCode(
        gPerHa: Double,
        diamRatio: DiameterCategoryRatio?,
        mode: TreatmentMode,
        stage: DevelopmentStage
    ): String {
        val prefix = computeRegimPrefix(mode, stage)
        val capital = computeClassCapital(gPerHa)
        val structure = diamRatio?.cnpfStructureCode()?.toString() ?: "?"
        return "$prefix$capital$structure"
    }

    // ── Explication lisible du code CNPF ───────────────────────────────────────
    fun buildCNPFExplanation(
        cnpfCode: String,
        gPerHa: Double,
        diamRatio: DiameterCategoryRatio?
    ): String {
        if (diamRatio == null) return "Code $cnpfCode (données triangle non disponibles)."
        val capital = computeClassCapital(gPerHa)
        val structure = diamRatio.cnpfStructureCode()
        val capitalDesc = when (capital) {
            "00" -> "G < 2 m²/ha (très faible)"
            "0"  -> "2 ≤ G < 5 m²/ha (faible)"
            "1"  -> "5 ≤ G < 10 m²/ha"
            "2"  -> "10 ≤ G < 15 m²/ha"
            "3"  -> "15 ≤ G < 20 m²/ha"
            "4"  -> "20 ≤ G < 25 m²/ha"
            "5"  -> "25 ≤ G < 30 m²/ha"
            else -> "G ≥ 30 m²/ha (très fort)"
        }
        val structureDesc = when (structure) {
            1 -> "PB dominants (PB > 50%, GB/TGB ≤ 5%)"
            2 -> "PB dominants + GB épars (PB > 50%, GB/TGB ≤ 20%)"
            3 -> "PB + BM dominants (GB/TGB ≤ 20%, PB ≤ 50%)"
            4 -> "BM dominants (BM > 50%)"
            5 -> "PB + GB dominants (20% < GB/TGB < 50%, BM ≤ 25%)"
            6 -> "Sans catégorie dominante (BM > 25%, PB ≥ 25%)"
            7 -> "BM + GB dominants (PB < 25%)"
            8 -> "GB dominants (GB/TGB ≥ 50%, GB ≥ TGB)"
            9 -> "TGB dominants (GB/TGB ≥ 50%, TGB > GB)"
            else -> "Structure indéterminée"
        }
        return "Type $cnpfCode : G = ${String.format("%.1f", gPerHa)} m²/ha → classe capital $capital ($capitalDesc), " +
               "structure $structure ($structureDesc). " +
               "PB = ${diamRatio.pbPct.toInt()}%, BM = ${diamRatio.bmPct.toInt()}%, " +
               "GB+TGB = ${diamRatio.gbTgbPct.toInt()}%."
    }

    // ── Diagnostic textuel ────────────────────────────────────────────────────
    fun buildDiagnosis(
        treatmentMode: TreatmentMode,
        ageStructure: AgeStructure,
        verticalStructure: VerticalStructure,
        stage: DevelopmentStage,
        composition: StandComposition,
        origin: StandOrigin,
        dynamic: StandDynamic,
        disturbanceState: DisturbanceState,
        trianglePos: StructureTrianglePosition?,
        cvDiam: Double?,
        gPerHa: Double,
        nPerHa: Double,
        dominantEssenceName: String,
        diamRatio: DiameterCategoryRatio? = null
    ): StandDiagnosis {
        val typeLabel = buildTypeLabel(treatmentMode, composition, stage)
        val typeCode = buildTypeCode(trianglePos, treatmentMode, composition)
        val cnpfCode = buildCNPFTypeCode(gPerHa, diamRatio, treatmentMode, stage)
        val cnpfExpl = buildCNPFExplanation(cnpfCode, gPerHa, diamRatio)
        val whyClassified = buildWhyClassified(treatmentMode, ageStructure, cvDiam, nPerHa, gPerHa, origin, verticalStructure)
        val ecologicalMeaning = ecologicalMeaningFor(treatmentMode, dynamic, disturbanceState)
        val implications = sylviculturalImplications(treatmentMode, stage, gPerHa, nPerHa)
        val risks = risksFor(treatmentMode, stage, disturbanceState, gPerHa, nPerHa)
        val advantages = advantagesFor(treatmentMode, composition, dynamic)
        val martelage = martelageTipsFor(treatmentMode, stage, gPerHa, nPerHa, dominantEssenceName)
        return StandDiagnosis(typeLabel, typeCode, cnpfCode, cnpfExpl, whyClassified, ecologicalMeaning, implications, risks, advantages, martelage)
    }

    // ── Constructeurs d'étiquettes ─────────────────────────────────────────────
    private fun buildTypeLabel(mode: TreatmentMode, comp: StandComposition, stage: DevelopmentStage): String {
        val compLabel = when (comp) {
            StandComposition.PUR_FEUILLU, StandComposition.PUR_RESINEUX -> "pur"
            StandComposition.MELANGE_PIED_A_PIED -> "mélangé pied à pied"
            StandComposition.MELANGE_BOUQUETS -> "mélangé par bouquets"
            else -> "mélangé"
        }
        return "${mode.label} $compLabel — ${stage.label}"
    }

    private fun buildTypeCode(tri: StructureTrianglePosition?, mode: TreatmentMode, comp: StandComposition): String {
        val t = tri?.code ?: "?"
        val m = mode.shortCode
        return "$m-$t"
    }

    private fun buildWhyClassified(
        mode: TreatmentMode, age: AgeStructure, cv: Double?, nPerHa: Double,
        gPerHa: Double, origin: StandOrigin, vert: VerticalStructure
    ): List<String> {
        val reasons = mutableListOf<String>()
        cv?.let {
            reasons += "Coefficient de variation des diamètres = ${it.toInt()}% → ${age.label}"
        }
        when (mode) {
            TreatmentMode.FUTAIE_JARDINEE ->
                reasons += "Distribution continue des diamètres sur plusieurs classes → structure jardinée probable"
            TreatmentMode.TAILLIS_SIMPLE, TreatmentMode.TAILLIS_DENSE ->
                reasons += "Présence de cépées et forte densité (${nPerHa.toInt()} t/ha) → taillis caractéristique"
            TreatmentMode.TAILLIS_SOUS_FUTAIE ->
                reasons += "Association de rejets de souche et de réserves semencières → taillis sous futaie"
            TreatmentMode.FUTAIE_REGULIERE ->
                reasons += "Faible variation des diamètres et densité modérée (${gPerHa.toInt()} m²/ha) → futaie régulière"
            else -> {}
        }
        if (vert == VerticalStructure.PLURISTRATIFIEE)
            reasons += "Rapport H Lorey / H moyenne élevé → plusieurs strates verticales identifiées"
        if (origin == StandOrigin.PLANTATION_MONO)
            reasons += "Composition monospécifique et régularité spatiale → plantation d'origine probable"
        return reasons.ifEmpty { listOf("Classification basée sur les données dendrométriques disponibles") }
    }

    private fun ecologicalMeaningFor(mode: TreatmentMode, dyn: StandDynamic, dist: DisturbanceState): String = when {
        dist == DisturbanceState.DEPERISSANT ->
            "Le peuplement présente des signes de dépérissement. Un diagnostic sanitaire approfondi est recommandé avant toute intervention sylvicole."
        dist == DisturbanceState.STRESSE ->
            "Des facteurs de stress (élancement excessif, densité, sécheresse probable) affectent la vigueur du peuplement. Une éclaircie sanitaire est à envisager."
        mode == TreatmentMode.FUTAIE_JARDINEE ->
            "Structure la plus résiliente écologiquement : diversité d'âges et de tailles, régénération continue, stabilité microclimatique forte."
        mode == TreatmentMode.FUTAIE_REGULIERE && dyn == StandDynamic.MATURATION ->
            "Peuplement en phase productive. La surface terrière atteinte témoigne d'une bonne croissance. L'éclaircie régule la compétition et oriente la production."
        mode in listOf(TreatmentMode.TAILLIS_SIMPLE, TreatmentMode.TAILLIS_DENSE) ->
            "Peuplement issu de rejets de souche. Pousse rapide mais bois de petit diamètre. Potentiel élevé pour la biomasse énergie, limité pour le bois d'oeuvre."
        mode == TreatmentMode.TAILLIS_SOUS_FUTAIE ->
            "Système traditionnel associant rejets et réserves. Conversion progressive vers la futaie possible par sélection des réserves."
        else -> "Peuplement forestier dont la structure et la composition définissent les potentialités sylvicoles et écologiques."
    }

    private fun sylviculturalImplications(mode: TreatmentMode, stage: DevelopmentStage, gPerHa: Double, nPerHa: Double): List<String> {
        val imp = mutableListOf<String>()
        when (mode) {
            TreatmentMode.FUTAIE_REGULIERE -> {
                if (gPerHa > 30) imp += "Surface terrière élevée : éclaircie nécessaire pour maintenir la vigueur des tiges d'avenir"
                if (stage == DevelopmentStage.PERCHIS || stage == DevelopmentStage.JEUNE_FUTAIE)
                    imp += "Stade critique : les éclaircies doivent être régulières (tous les 8–12 ans) pour structurer le peuplement"
            }
            TreatmentMode.FUTAIE_JARDINEE -> {
                imp += "Gestion pied à pied : martelage individuel privilégié sur les vieux arbres et les tiges dominées"
                imp += "Maintien du ratio Petits/Gros bois pour une structure jardinée équilibrée"
                if (gPerHa < 20) imp += "Surface terrière trop faible pour une futaie jardinée typique : risque d'envahissement par la végétation"
            }
            TreatmentMode.TAILLIS_SOUS_FUTAIE, TreatmentMode.TAILLIS_SOUS_FUTAIE_CONVERSION -> {
                imp += "Sélection des réserves lors de chaque passage (conserver 80–120 réserves/ha de bonne conformation)"
                imp += "Suppression progressive des cépées concurrentes des réserves"
            }
            TreatmentMode.TAILLIS_SIMPLE, TreatmentMode.TAILLIS_DENSE -> {
                imp += "Rotation courte : 20–30 ans pour le bois énergie, 40–60 ans pour le bois d'industrie"
                if (nPerHa > 4000) imp += "Densité très élevée : dépressage recommandé pour concentrer la croissance"
            }
            else -> imp += "Éclaircie adaptée à l'essence dominante et au stade de développement"
        }
        return imp
    }

    private fun risksFor(mode: TreatmentMode, stage: DevelopmentStage, dist: DisturbanceState, gPerHa: Double, nPerHa: Double): List<String> {
        val risks = mutableListOf<String>()
        if (dist != DisturbanceState.SAIN) risks += dist.label
        if (gPerHa > 40) risks += "Risque de chablis : surface terrière très élevée → arbres concurrents et mal enracinés"
        if (stage == DevelopmentStage.PERCHIS && nPerHa > 2000) risks += "Risque d'élancement excessif : peuplement dense au stade perchis"
        if (mode == TreatmentMode.FUTAIE_REGULIERE)
            risks += "Sensibilité aux tempêtes lors de la mise à découvert après éclaircie"
        if (mode in listOf(TreatmentMode.TAILLIS_SIMPLE, TreatmentMode.TAILLIS_DENSE))
            risks += "Sensibilité à la sécheresse prolongée et aux pathogènes xylophages"
        return risks.ifEmpty { listOf("Aucun risque majeur identifié sur la base des données disponibles") }
    }

    private fun advantagesFor(mode: TreatmentMode, comp: StandComposition, dyn: StandDynamic): List<String> {
        val adv = mutableListOf<String>()
        if (comp != StandComposition.PUR_FEUILLU && comp != StandComposition.PUR_RESINEUX)
            adv += "Mélange d'essences : résilience accrue face aux bioagresseurs et au changement climatique"
        when (mode) {
            TreatmentMode.FUTAIE_JARDINEE -> {
                adv += "Structure la plus stable face aux aléas climatiques et sanitaires"
                adv += "Régénération continue sans coupe rase"
                adv += "Haute valeur biodiversité (bois mort, îlots, strates)"}
            TreatmentMode.FUTAIE_IRREGULIERE, TreatmentMode.FUTAIE_IRREGULIERE_BOUQUETS ->
                adv += "Bonne résistance au vent par rapport à la futaie régulière"
            TreatmentMode.TAILLIS_SOUS_FUTAIE -> {
                adv += "Bonne protection des sols par le taillis"
                adv += "Production multiple (bois énergie du taillis + bois d'oeuvre des réserves)"}
            else -> {}
        }
        if (dyn == StandDynamic.MATURATION) adv += "Peuplement en pleine production, volumes élevés atteignables"
        return adv.ifEmpty { listOf("Peuplement forestier avec un potentiel de production et de gestion durable") }
    }

    private fun martelageTipsFor(mode: TreatmentMode, stage: DevelopmentStage, gPerHa: Double, nPerHa: Double, essence: String): List<String> {
        val tips = mutableListOf<String>()
        tips += when (stage) {
            DevelopmentStage.PERCHIS, DevelopmentStage.JEUNE_FUTAIE ->
                "Marquer en priorité les tiges dominées et les arbres à mauvaise conformation (branches basses, fourches)"
            DevelopmentStage.FUTAIE_ADULTE ->
                "Favoriser les arbres d'avenir (1 ou 2 par maille naturelle) ; supprimer leurs concurrents directs"
            DevelopmentStage.FUTAIE_MURE, DevelopmentStage.FUTAIE_SURANNEE ->
                "Martelage de renouvellement : préparer la régénération (ouvertures 0.1–0.2 ha), conserver les arbres porte-graines"
            else -> "Adapter le martelage à l'objectif de gestion défini"
        }
        if (gPerHa > 35) tips += "Prélèvement recommandé : 20–25% de la surface terrière pour rester en zone de sécurité stabilité"
        if (gPerHa < 15) tips += "Surface terrière faible : intervention légère (<15%) pour ne pas déstabiliser le couvert"
        when (mode) {
            TreatmentMode.FUTAIE_JARDINEE ->
                tips += "Martelage jardinatoire : supprimer en priorité les tiges de plus grand diamètre arrivées à maturité"
            TreatmentMode.TAILLIS_SOUS_FUTAIE, TreatmentMode.TAILLIS_SOUS_FUTAIE_CONVERSION ->
                tips += "Sélectionner 1 brin par cépée de bonne conformation comme futur réserve ; balancer les autres"
            else -> {}
        }
        return tips
    }

    // ── Programmes de gestion spécifiques ─────────────────────────────────────

    fun cheneBourdOeuvreProgram(nPerHa: Double, stage: DevelopmentStage): ManagementProgram {
        val n = nPerHa.toInt()
        val interventions = buildList {
            if (stage.ordinal <= DevelopmentStage.FUTAIE_ADULTE.ordinal) {
                add(Intervention("Année 0", "Éclaircie de conversion",
                    "Réduire la concurrence, dégager les tiges d'avenir",
                    listOf("Suppression des brins dominés", "Élimination des arbres mal conformés", "Suppression des cépées concurrentes"),
                    15..20, expectedNhaAfter = (n * 80 / 100)..(n * 85 / 100)))
                add(Intervention("Année 8–10", "Éclaircie d'amélioration",
                    "Favoriser les arbres d'avenir sélectionnés",
                    listOf("Suppression des concurrents directs des arbres d'avenir", "Maintien des arbres diversifiants"),
                    12..18, expectedNhaAfter = 250..320))
                add(Intervention("Année 15–18", "Éclaircie de structuration",
                    "Renforcer la hiérarchie et préparer la futaie adulte",
                    listOf("Sélection définitive des arbres d'avenir (80–100/ha)", "Suppression des arbres déformés restants"),
                    10..15, expectedNhaAfter = 200..260))
            }
            if (stage.ordinal >= DevelopmentStage.FUTAIE_ADULTE.ordinal) {
                add(Intervention("Selon G/ha (>28 m²/ha)", "Éclaircie productive",
                    "Maintenir la vigueur et concentrer la production sur les meilleurs arbres",
                    listOf("Prélèvement de 20–25% de la G/ha", "Suppression des arbres déprimants et parasités"),
                    18..25, expectedNhaAfter = 140..180))
            }
        }
        return ManagementProgram(
            objectiveLabel = "Production de bois d'œuvre de chêne — qualité sciage/tranchage",
            targetDiamCm = 70..80,
            targetAgeAns = 180..220,
            targetNha = 120..160,
            qualityCible = "Fût droit ≥ 6 m, sans nœuds, première billette ≥ 4 m",
            densityTrajectory = sanitizeDensityTrajectory(n, listOf("Départ" to n..n, "Après 1re éclaircie" to 300..340, "Après 2e" to 240..270, "Après 3e" to 200..235, "Final" to 130..160)),
            interventions = interventions
        )
    }

    fun hetreBourdOeuvreProgram(nPerHa: Double, stage: DevelopmentStage): ManagementProgram {
        val n = nPerHa.toInt()
        return ManagementProgram(
            objectiveLabel = "Production de bois d'œuvre de hêtre — sciage/déroulage",
            targetDiamCm = 55..65,
            targetAgeAns = 120..150,
            targetNha = 100..140,
            qualityCible = "Fût droit, sans nœuds gênants, grume ≥ 5 m",
            densityTrajectory = sanitizeDensityTrajectory(n, listOf("Départ" to n..n, "Après 1re éclaircie" to 350..450, "Final" to 100..140)),
            interventions = listOf(
                Intervention("Année 0", "Dépressage / 1re éclaircie",
                    "Sélectionner les tiges d'avenir, éliminer les fourches et dominés",
                    listOf("Supprimer les doublets et tiges difformes", "Maintenir 400–500 t/ha après passage"),
                    20..30, expectedNhaAfter = (n * 70 / 100)..(n * 75 / 100)),
                Intervention("Tous les 8–10 ans", "Éclaircies successives",
                    "Concentrer la croissance sur 100–120 arbres d'avenir/ha",
                    listOf("Prélèvement 15–20% de la G", "Favoriser la qualité de fût"),
                    15..20, expectedNhaAfter = 100..140)
            )
        )
    }

    fun douglasResineuxProgram(nPerHa: Double, stage: DevelopmentStage): ManagementProgram {
        val n = nPerHa.toInt()
        return ManagementProgram(
            objectiveLabel = "Production intensive — Douglas/Sapin : bois d'œuvre structural",
            targetDiamCm = 50..60,
            targetAgeAns = 50..65,
            targetNha = 200..300,
            qualityCible = "Grumes rectilignes ≥ 5 m, cernes réguliers < 8 mm",
            densityTrajectory = sanitizeDensityTrajectory(n, listOf("Départ" to n..n, "1re éclaircie" to 800..1000, "2e" to 400..600, "3e" to 250..350, "Final" to 200..280)),
            interventions = listOf(
                Intervention("Année 8–12", "1re éclaircie systématique",
                    "Dégager la cime des tiges d'avenir, réduire la concurrence racinaire",
                    listOf("Prélèvement 1 tige sur 2–3", "Suppression des tiges dominées et fourchues"),
                    30..40, expectedNhaAfter = 800..1000),
                Intervention("Année 15–18", "2e éclaircie sélective",
                    "Sélectionner les 400–600 meilleures tiges",
                    listOf("Supprimer les arbres à mauvaise forme", "Éliminer les concurrents des arbres d'avenir"),
                    25..35, expectedNhaAfter = 400..600),
                Intervention("Année 22–28", "3e éclaircie productive",
                    "Récolte des bois de demi-transformation",
                    listOf("Prélèvement 20% G/ha", "Maintien de 250–350 t/ha"),
                    18..25, expectedNhaAfter = 250..350)
            )
        )
    }

    fun pinProgram(nPerHa: Double, stage: DevelopmentStage): ManagementProgram {
        val n = nPerHa.toInt()
        return ManagementProgram(
            objectiveLabel = "Production mixte — Pin : bois de trituration / bois d'industrie",
            targetDiamCm = 30..45,
            targetAgeAns = 40..70,
            targetNha = 300..500,
            qualityCible = "Tiges bien conformées, sans chandelles ; valorisation bois industrie ou pâte",
            densityTrajectory = sanitizeDensityTrajectory(n, listOf("Départ" to n..n, "1re éclaircie" to 700..1000, "Final" to 300..500)),
            interventions = listOf(
                Intervention("Année 8–10", "Éclaircie systématique + sélective",
                    "Réduire les risques d'incendie et de pathogènes par aération",
                    listOf("Prélèvement 30–40% tiges", "Élimination des arbres malades, fourchereux"),
                    30..40, expectedNhaAfter = 700..1000),
                Intervention("Tous les 8 ans", "Éclaircies progressives",
                    "Maintenir vigueur et qualité sanitaire",
                    listOf("Prélèvement 20–25% G", "Débroussaillement en parallèle si zone à risque"),
                    20..25, expectedNhaAfter = 300..500)
            )
        )
    }

    fun jardineProgram(gPerHa: Double): ManagementProgram = ManagementProgram(
        objectiveLabel = "Gestion jardinatoire — maintien de la structure irrégulière équilibrée",
        targetDiamCm = null,
        targetAgeAns = null,
        targetNha = null,
        qualityCible = "Distribution de Liocourt maintenue (q = 1.2–1.4 par classe de 5 cm)",
        densityTrajectory = listOf(
            "G actuelle" to (gPerHa.toInt() - 2)..(gPerHa.toInt() + 2),
            "Après coupe (−15–25% G)" to (gPerHa * 0.75).toInt()..(gPerHa * 0.85).toInt(),
            "Retour à l'équilibre" to (gPerHa.toInt() - 2)..(gPerHa.toInt() + 2)
        ),
        interventions = listOf(
            Intervention("Tous les 8–12 ans", "Coupe jardinatoire pied à pied",
                "Prélever sur les gros bois arrivés à maturité, favoriser la régénération",
                listOf("Marquer les arbres ≥ diamètre d'exploitabilité", "Supprimer les tiges malades et mal conformées",
                    "Ouvrir des clairières de 0.05–0.10 ha pour la régénération"),
                15..25, expectedNhaAfter = null)
        )
    )

    fun taillisProgram(nPerHa: Double, stage: DevelopmentStage): ManagementProgram {
        val n = nPerHa.toInt()
        return ManagementProgram(
            objectiveLabel = "Gestion du taillis — valorisation bois énergie / conversion progressive",
            targetDiamCm = 15..25,
            targetAgeAns = 20..35,
            targetNha = null,
            qualityCible = "Billons bois énergie ou industrie ; sélection des brinées pour conversion futaie",
            densityTrajectory = listOf("Départ" to n..n, "Après dépressage" to (n / 2)..(n * 2 / 3)),
            interventions = listOf(
                Intervention("Année 0", "Dépressage (si densité > 4000 t/ha)",
                    "Concentrer la croissance sur les meilleurs brins par cépée",
                    listOf("Conserver 2–3 brins par cépée", "Supprimer les brins malades et déformés"),
                    30..50, expectedNhaAfter = (n / 2)..(n * 2 / 3)),
                Intervention("Coupe rase cyclique (rotation 20–30 ans)", "Exploitation du taillis",
                    "Récolte et renouvellement par rejets de souche",
                    listOf("Coupe à blanc par bandes ou plaquettes de < 1 ha", "Exportation totale ou partielle selon contexte sol"),
                    100..100, expectedNhaAfter = null)
            )
        )
    }

    fun tailisSousFutaieConversionProgram(nPerHa: Double, gPerHa: Double): ManagementProgram {
        val n = nPerHa.toInt()
        return ManagementProgram(
            objectiveLabel = "Conversion du taillis sous futaie en futaie régulière",
            targetDiamCm = 50..70,
            targetAgeAns = 80..120,
            targetNha = 150..200,
            qualityCible = "Réserves bien conformées de qualité sciage, sans concurrence taillis",
            densityTrajectory = listOf("Départ réserves" to 80..150, "Après 1re conversion" to 120..160, "Objectif final futaie" to 150..200),
            interventions = listOf(
                Intervention("Passage 1 (maintenant)", "Sélection des réserves et nettoiement du taillis",
                    "Dégager les réserves à potentiel, supprimer les cépées concurrentes",
                    listOf("Conserver 80–120 réserves/ha à bonne conformation", "Balancer les cépées dans un rayon de 2 m autour de chaque réserve"),
                    40..60, expectedNhaAfter = (n * 40 / 100)..(n * 55 / 100)),
                Intervention("Tous les 8–10 ans", "Éclaircie de conversion",
                    "Favoriser les meilleures réserves, éliminer le taillis résiduel",
                    listOf("Suppression progressive du taillis autour des réserves", "Favoriser la régénération naturelle sous les fenêtres"),
                    15..25, expectedNhaAfter = 150..200)
            )
        )
    }

    private fun genericProgram(nPerHa: Double, gPerHa: Double, stage: DevelopmentStage, objective: ManagementObjective): ManagementProgram {
        val n = nPerHa.toInt()
        return ManagementProgram(
            objectiveLabel = objective.label,
            targetDiamCm = null,
            targetAgeAns = null,
            targetNha = (n * 60 / 100)..(n * 80 / 100),
            qualityCible = "À définir selon l'essence et les marchés locaux",
            densityTrajectory = listOf("Départ" to n..n, "Après intervention" to (n * 60 / 100)..(n * 80 / 100)),
            interventions = listOf(
                Intervention("À planifier", "Éclaircie adaptée au contexte",
                    "Réduire la compétition et orienter la production",
                    listOf("Prélèvement 15–25% G/ha selon la densité et le stade"),
                    15..25, expectedNhaAfter = (n * 60 / 100)..(n * 80 / 100))
            )
        )
    }

    // ── Questions utilisateur pour données manquantes ─────────────────────────
    val QUESTION_CEPEES = ClassificationQuestion(
        id = "has_cepees",
        text = "Observe-t-on des cépées (plusieurs tiges issues d'une même souche) dans le peuplement ?",
        hint = "Caractéristique principale du taillis et taillis sous futaie",
        options = listOf("Non / peu (<10% des tiges)", "Oui, présentes (<50% des tiges)", "Oui, majoritaires (>50% des tiges)"),
        defaultIndex = 0
    )

    val QUESTION_RESERVES = ClassificationQuestion(
        id = "has_reserves",
        text = "Existe-t-il des arbres de grand diamètre au-dessus du taillis (réserves / baliveaux) ?",
        hint = "Indique un taillis sous futaie",
        options = listOf("Non, pas de réserves visibles", "Oui, quelques réserves isolées (<80/ha)", "Oui, réserves nombreuses et bien réparties (>80/ha)"),
        defaultIndex = 0
    )

    val QUESTION_REGENERATION = ClassificationQuestion(
        id = "has_regeneration",
        text = "Y a-t-il de la régénération naturelle visible en sous-étage (semis, fougères, herbacées) ?",
        hint = "Présence de jeunes plants de < 1.30 m",
        options = listOf("Non", "Oui, localement (<20% de la surface)", "Oui, abondante (>20% de la surface)"),
        defaultIndex = 0
    )

    val QUESTION_PLANTATION = ClassificationQuestion(
        id = "is_plantation",
        text = "Le peuplement est-il issu d'une plantation (alignement régulier visible, espèces exotiques) ?",
        hint = "Exemples : Douglas en rangs, Épicéas en lignes, Peupliers en quinconce",
        options = listOf("Non — peuplement naturel ou incertain", "Oui — rangées régulières visibles", "Partiellement — enrichissement sous couvert"),
        defaultIndex = 0
    )

    val QUESTION_ZONE = ClassificationQuestion(
        id = "eco_zone",
        text = "Quel est le contexte géographique de la parcelle ?",
        hint = "Pour l'identification du type écologique",
        options = listOf("Plaine ou colline (<600 m)", "Montagne (600–1200 m)", "Zone méditerranéenne", "Zone alluviale / ripisylve"),
        defaultIndex = 0
    )

    val ALL_QUESTIONS = listOf(QUESTION_CEPEES, QUESTION_RESERVES, QUESTION_REGENERATION, QUESTION_PLANTATION, QUESTION_ZONE)

    // ── Itinéraires SRGS régionaux ─────────────────────────────────────────────
    /**
     * Retourne l'itinéraire SRGS régional pour un peuplement donné.
     * Basé sur les préconisations des SRGS de chaque région forestière.
     * region = null → itinéraire national générique.
     */
    fun srgsItineraireFor(
        region: SRGSRegion?,
        mode: TreatmentMode,
        dominantEssence: String,
        gPerHa: Double,
        nPerHa: Double
    ): String {
        val r = region ?: SRGSRegion.NATIONAL
        val essLower = dominantEssence.lowercase()
        val isChene = essLower.contains("chêne") || essLower.contains("chene")
        val isHetre = essLower.contains("hêtre") || essLower.contains("hetre")
        val isPin   = essLower.contains("pin")
        val isDouglas = essLower.contains("douglas") || essLower.contains("sapin")

        // Itinéraires régionaux principaux
        return when (r) {
            SRGSRegion.NORMANDIE -> when {
                isHetre && mode == TreatmentMode.FUTAIE_REGULIERE ->
                    "SRGS Normandie — Hêtraie normande : futaie régulière à longue révolution (130–160 ans). " +
                    "1re éclaircie à 15 m de hauteur dominante. Objectif 70–80 t/ha à maturité. " +
                    "Maintien de gros semenciers lors des coupes définitives. Favoriser régénération naturelle par trouées de 20–40 ares."
                isChene ->
                    "SRGS Normandie — Chênaie : objectif bois d'œuvre qualité avec 80–100 arbres d'avenir/ha. " +
                    "Éclaircies tous les 8–10 ans jusqu'à diamètre d'exploitabilité 70–75 cm. " +
                    "Particularité normande : sols profonds argilo-limoneux favorables à l'élagage naturel."
                else ->
                    "SRGS Normandie : privilégier les essences locales (hêtre, chêne sessile/pédonculé). " +
                    "Éviter les résineux en plaine. Maintenir des lisières diversifiées."
            }
            SRGSRegion.GRAND_EST -> when {
                isDouglas ->
                    "SRGS Grand Est — Douglas / Sapin pectiné : densité initiale 1100–1300 t/ha. " +
                    "1re éclaircie à 1000 t/ha (H0 ≈ 10 m), puis tous les 5–8 ans. Objectif 50–60 cm à 55–65 ans. " +
                    "Régions vosgiennes : adapter la sylviculture aux risques de tempêtes (pas de coupes trop fortes)."
                isHetre ->
                    "SRGS Grand Est — Hêtraie vosgienne : révolution 120–150 ans, diamètre cible 60–70 cm. " +
                    "Favoriser mélange avec sapin et épicéa en altitude. Régénération sous abri sur 25–35 ans."
                else ->
                    "SRGS Grand Est : priorité aux mélanges chêne-hêtre en plaine, hêtre-sapin en montagne. " +
                    "Maintenir le capital biodiversité (arbres sénescents, bois mort ≥ 5 arbres/ha)."
            }
            SRGSRegion.BOURGOGNE_FC -> when {
                isChene ->
                    "SRGS Bourgogne-FC — Chênaie : région à fort potentiel bois d'œuvre qualité (sciage/tranchage). " +
                    "Objectif 80–100 arbres d'avenir/ha, diamètre cible 70–80 cm, révolution 180–200 ans. " +
                    "Maintien des vieux bois et ilôts de sénescence requis (≥ 3 arbres/ha ≥ 60 cm)."
                isHetre ->
                    "SRGS Bourgogne-FC — Hêtraie : sylviculture proche de la nature en montagne. " +
                    "Coupes de jardinage en forêts irrégulières, futaie régulière en plaine. " +
                    "Attention au risque de dépérissement en zones de plaine (sécheresse croissante)."
                else ->
                    "SRGS Bourgogne-FC : favoriser le maintien des essences climaciques locales. " +
                    "Conversion des taillis sous futaie vers la futaie à encourager sur sols profonds."
            }
            SRGSRegion.NOUVELLE_AQUITAINE -> when {
                isPin ->
                    "SRGS Nouvelle-Aquitaine — Pin maritime (Landes) : sylviculture intensive avec rotation 40–50 ans. " +
                    "Densité initiale 1111 t/ha (3×3 m). 2 éclaircies à 7 ans et 15 ans. " +
                    "Objectif 30–35 cm à 40 ans. Gestion après tempête : reboisement immédiat obligatoire (PEFC)."
                isChene ->
                    "SRGS Nouvelle-Aquitaine — Chêne pédonculé / Périgord : futaie régulière ou jardinée. " +
                    "Valorisation bois d'œuvre et trufficulture en zone calcaire. " +
                    "Maintien des vieux chênes têtards dans les bocages."
                else ->
                    "SRGS Nouvelle-Aquitaine : région à fort potentiel pin maritime et chêne. " +
                    "Adaptation requise face au risque croissant d'incendies (pare-feux, débroussaillement)."
            }
            SRGSRegion.OCCITANIE -> when {
                isPin ->
                    "SRGS Occitanie — Pin sylvestre / pin noir : attention au risque incendie et processionnaire. " +
                    "Éclaircies fortes précoces pour améliorer la résistance à la sécheresse. " +
                    "Favoriser le mélange avec le chêne pubescent sur les versants bien exposés."
                isChene ->
                    "SRGS Occitanie — Chêne pubescent et vert : peuplements souvent peu productifs mais à haute valeur écologique. " +
                    "Sylviculture légère, maintien du couvert sur sols calcaires. Valorisation en bois énergie si déprimage nécessaire."
                else ->
                    "SRGS Occitanie : régénération naturelle à favoriser, maintenir diversité essences locales. " +
                    "Attention aux risques incendie (DFCI) et aux espèces invasives."
            }
            SRGSRegion.AUVERGNE_RA -> when {
                isDouglas ->
                    "SRGS Auvergne-RA — Douglas : itinéraire standard Massif Central. Densité initiale 1100–1300 t/ha. " +
                    "1re éclaircie H0 ≈ 8–10 m (700–800 t/ha). Objectif 55 cm à 60 ans. " +
                    "Vigilance sur la stabilité mécanique (vent) après les premières éclaircies."
                isHetre ->
                    "SRGS Auvergne-RA — Hêtraie de montagne (Massif Central / Alpes) : révolution 120–140 ans. " +
                    "Mélange avec le sapin et l'épicéa recommandé en altitude. " +
                    "Futaie irrégulière à encourager sur les versants instables."
                else ->
                    "SRGS Auvergne-RA : diversifier les essences pour réduire la vulnérabilité climatique. " +
                    "Chêne pubescent et hêtre en mélange sur les versants. Douglas en reboisement de compensation."
            }
            SRGSRegion.ILE_DE_FRANCE -> when {
                isChene ->
                    "SRGS Île-de-France — Chênaie de plaine (Fontainebleau, Rambouillet) : futaie régulière à objectif bois d'œuvre. " +
                    "Éclaircies fortes pour assurer la stabilité des futaies adultes (G cible 20–25 m²/ha). " +
                    "Maintien de vieux arbres îlots de sénescence (arrêté de protection ≥ 5/ha)."
                isHetre ->
                    "SRGS Île-de-France — Hêtraie : itinéraire futaie régulière, objectif 60–65 cm à 130 ans. " +
                    "Coupes de régénération progressives sous couvert de semenciers."
                else ->
                    "SRGS Île-de-France : forêts sous forte pression sociale (accueil public). " +
                    "Maintenir des forêts stables et diversifiées. Éviter les coupes rases visibles."
            }
            else -> {
                // Itinéraire national générique selon mode et densité
                when (mode) {
                    TreatmentMode.FUTAIE_JARDINEE ->
                        "Itinéraire SRGS (national) — Futaie jardinée : coupes jardinatoires tous les 8–12 ans, " +
                        "prélèvement 15–20% G/ha sur les classes supérieures. Maintien distribution de Liocourt (q = 1.2–1.4)."
                    TreatmentMode.FUTAIE_REGULIERE ->
                        "Itinéraire SRGS (national) — Futaie régulière : éclaircies systématiques dès H0 > 10 m, " +
                        "prélèvement 20–25% G/ha, maintien G/ha entre 15 et 30 m²/ha selon essence et région."
                    TreatmentMode.TAILLIS_SOUS_FUTAIE, TreatmentMode.TAILLIS_SOUS_FUTAIE_CONVERSION ->
                        "Itinéraire SRGS (national) — TSF / Conversion : sélection 80–120 réserves/ha à chaque passage. " +
                        "Nettoiement du taillis autour des réserves. Durée de conversion : 40–80 ans."
                    else ->
                        "Itinéraire SRGS (${ r.labelFr}) : consultez le document régional disponible auprès du CNPF de votre région."
                }
            }
        }
    }

    /**
     * Applique les ajustements SRGS régionaux sur un programme de gestion générique.
     * Enrichit le programme avec l'itinéraire régional et les notes SRGS.
     */
    fun withRegion(
        program: ManagementProgram,
        region: SRGSRegion?,
        mode: TreatmentMode,
        dominantEssence: String,
        gPerHa: Double,
        nPerHa: Double
    ): ManagementProgram {
        if (region == null) return program
        val itineraire = srgsItineraireFor(region, mode, dominantEssence, gPerHa, nPerHa)
        val notes = mutableListOf<String>()
        notes += "Région SRGS : ${region.labelFr} (${region.code})"
        if (itineraire.isNotBlank()) notes += "Itinéraire régional disponible"
        return program.copy(
            srgsRegion = region,
            srgsItineraire = itineraire,
            srgsNotes = notes
        )
    }
}
