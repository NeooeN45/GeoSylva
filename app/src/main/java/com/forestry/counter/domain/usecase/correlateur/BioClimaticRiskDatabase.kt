package com.forestry.counter.domain.usecase.correlateur

import com.forestry.counter.domain.model.ClimateZone

/**
 * Base de données des risques bioclimatiques par essence, zone et scénario climatique.
 *
 * Sources de référence :
 *  - GIEC AR6 (2021–2022) — projections climatiques France métropolitaine
 *  - INRAE / URFM — Vulnérabilité des essences forestières françaises au CC
 *  - DSF (Département de la Santé des Forêts) — bulletins de surveillance
 *  - ONF / CNPF — guides de sylviculture adaptée
 */
object BioClimaticRiskDatabase {

    // ─────────────────────────────────────────────────────────────────────────
    //  Modèles de données
    // ─────────────────────────────────────────────────────────────────────────

    data class ClimateScenario(
        val id: String,
        val name: String,
        val description: String,
        val horizonYear: Int,
        val tempRaiseDegC: Double,      // hausse température moyenne (°C)
        val precipChangePercent: Int    // variation précipitations estivales (%)
    )

    data class EssenceRiskProfile(
        val essenceCode: String,
        val essenceNameFr: String,
        val risks: List<ClimateRisk>,
        val adaptationMeasures: List<AdaptationMeasure>,
        val substituteEssences: List<String>    // codes des essences de remplacement
    )

    data class ClimateRisk(
        val category: RiskCategory,
        val name: String,
        val description: String,
        val affectedZones: Set<ClimateZone>,
        val probability2050: RiskProbability,
        val severity: RiskSeverity,
        val earlyWarnings: List<String>,
        val references: List<String> = emptyList()
    )

    data class AdaptationMeasure(
        val action: String,
        val rationale: String,
        val urgency: AdaptationUrgency,
        val costLevel: CostLevel = CostLevel.MODERATE
    )

    enum class RiskCategory(val labelFr: String) {
        DROUGHT("Sécheresse / déficit hydrique"),
        PEST("Ravageurs et insectes"),
        PATHOGEN("Maladies fongiques et bactériennes"),
        FIRE("Risque incendie"),
        STORM("Tempêtes et chablis"),
        FROST("Gelées tardives / précoces"),
        PHENOLOGY("Décalages phénologiques"),
        COMPETITION("Compétition accrue")
    }

    enum class RiskProbability(val label: String, val colorHex: Long) {
        CERTAIN("Quasi-certain (>90%)", 0xFFC62828),
        PROBABLE("Probable (60–90%)", 0xFFEF6C00),
        POSSIBLE("Possible (30–60%)", 0xFFF9A825),
        UNLIKELY("Peu probable (<30%)", 0xFF8BC34A)
    }

    enum class RiskSeverity(val label: String) {
        CRITICAL("Critique — mortalité massive possible"),
        HIGH("Élevée — dommages significatifs"),
        MODERATE("Modérée — impacts sur la productivité"),
        LOW("Faible — impacts limités")
    }

    enum class AdaptationUrgency(val label: String) {
        NOW("Immédiat — à mettre en œuvre maintenant"),
        SHORT("Court terme — 1 à 5 ans"),
        MEDIUM("Moyen terme — 5 à 15 ans"),
        LONG("Long terme — > 15 ans")
    }

    enum class CostLevel(val label: String) {
        LOW("Faible (< 500 €/ha)"),
        MODERATE("Modéré (500–2000 €/ha)"),
        HIGH("Élevé (> 2000 €/ha)")
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Scénarios climatiques (basés sur SSP/RCP GIEC AR6)
    // ─────────────────────────────────────────────────────────────────────────

    val scenarios: List<ClimateScenario> = listOf(
        ClimateScenario(
            id = "OPTIMISTIC_2050",
            name = "Scénario optimiste 2050 (SSP1-2.6)",
            description = "Forte réduction des émissions, neutralité carbone atteinte. +1.5°C en France d'ici 2050.",
            horizonYear = 2050,
            tempRaiseDegC = 1.5,
            precipChangePercent = -10
        ),
        ClimateScenario(
            id = "INTERMEDIATE_2050",
            name = "Scénario intermédiaire 2050 (SSP2-4.5)",
            description = "Réduction partielle des émissions. +2.0°C. Scénario le plus probable selon GIEC 2021.",
            horizonYear = 2050,
            tempRaiseDegC = 2.0,
            precipChangePercent = -20
        ),
        ClimateScenario(
            id = "PESSIMISTIC_2050",
            name = "Scénario pessimiste 2050 (SSP5-8.5)",
            description = "Aucune réduction significative. +3.0°C. Étés méditerranéens jusqu'en plaine.",
            horizonYear = 2050,
            tempRaiseDegC = 3.0,
            precipChangePercent = -35
        ),
        ClimateScenario(
            id = "INTERMEDIATE_2100",
            name = "Scénario intermédiaire 2100 (SSP2-4.5)",
            description = "+3.5°C en France. Zones bioclimatiques décalées de 300 km vers le nord.",
            horizonYear = 2100,
            tempRaiseDegC = 3.5,
            precipChangePercent = -30
        )
    )

    // ─────────────────────────────────────────────────────────────────────────
    //  Profils de risque par essence
    // ─────────────────────────────────────────────────────────────────────────

    private val ATL = ClimateZone.ATLANTIQUE
    private val SO  = ClimateZone.SEMI_OCEANIQUE
    private val CON = ClimateZone.CONTINENTALE
    private val MON = ClimateZone.MONTAGNARDE
    private val MED = ClimateZone.MEDITERRANEENNE

    val essenceRiskProfiles: List<EssenceRiskProfile> = listOf(

        // ── ÉPICÉA COMMUN ────────────────────────────────────────────────────
        EssenceRiskProfile(
            essenceCode = "EPICEA",
            essenceNameFr = "Épicéa commun",
            risks = listOf(
                ClimateRisk(
                    category = RiskCategory.PEST,
                    name = "Scolytes (Ips typographus)",
                    description = "Le bostryche typographe profite des sécheresses répétées pour coloniser " +
                        "les épicéas affaiblis. Pullulations massives observées en Europe centrale (2018–2022). " +
                        "En France, le risque est désormais systémique au-dessus de 500 m.",
                    affectedZones = setOf(SO, CON, MON),
                    probability2050 = RiskProbability.CERTAIN,
                    severity = RiskSeverity.CRITICAL,
                    earlyWarnings = listOf(
                        "Coulées de résine à la base du tronc",
                        "Sciure rouge-brun sous l'écorce",
                        "Aiguilles qui jaunissent puis rougissent",
                        "Galeries caractéristiques sous l'écorce"
                    ),
                    references = listOf("DSF Bulletin 2023", "INRAE Note technique épicéa 2022")
                ),
                ClimateRisk(
                    category = RiskCategory.DROUGHT,
                    name = "Stress hydrique estival",
                    description = "L'épicéa est très sensible au déficit hydrique estival. " +
                        "Sa survie à long terme est compromise dans les stations sous 800 m en zone semi-océanique.",
                    affectedZones = setOf(SO, CON, ATL),
                    probability2050 = RiskProbability.CERTAIN,
                    severity = RiskSeverity.CRITICAL,
                    earlyWarnings = listOf(
                        "Rameaux pendants en été",
                        "Réduction de la pousse annuelle",
                        "Jaunissement prématuré des aiguilles internes"
                    )
                )
            ),
            adaptationMeasures = listOf(
                AdaptationMeasure(
                    action = "Convertir progressivement les peuplements purs d'épicéa",
                    rationale = "Remplacer par des essences plus résilientes (douglas, mélèze, hêtre en altitude)",
                    urgency = AdaptationUrgency.NOW,
                    costLevel = CostLevel.HIGH
                ),
                AdaptationMeasure(
                    action = "Réduire la densité des peuplements d'épicéa",
                    rationale = "Arbres moins stressés = moins vulnérables aux scolytes",
                    urgency = AdaptationUrgency.NOW,
                    costLevel = CostLevel.MODERATE
                ),
                AdaptationMeasure(
                    action = "Surveiller mensuellement (mars–septembre)",
                    rationale = "Détection précoce des foyers de scolytes",
                    urgency = AdaptationUrgency.NOW,
                    costLevel = CostLevel.LOW
                )
            ),
            substituteEssences = listOf("DOUGLAS", "MELEZE", "SAPIN_PECTINE", "HETRE", "EPICEA_SERBIE")
        ),

        // ── FRÊNE COMMUN ─────────────────────────────────────────────────────
        EssenceRiskProfile(
            essenceCode = "FRENE",
            essenceNameFr = "Frêne commun",
            risks = listOf(
                ClimateRisk(
                    category = RiskCategory.PATHOGEN,
                    name = "Chalarose (Hymenoscyphus fraxineus)",
                    description = "Champignon asiatique introduit en Europe dans les années 1990. " +
                        "Cause des nécroses foliaires, des chancres et la mort des frênes. " +
                        "Présent dans toute la France, mortalité estimée à 70–90% des frênes à 10 ans.",
                    affectedZones = setOf(ATL, SO, CON, MON),
                    probability2050 = RiskProbability.CERTAIN,
                    severity = RiskSeverity.CRITICAL,
                    earlyWarnings = listOf(
                        "Chute prématurée des feuilles en été (juillet–août)",
                        "Nécroses brunes en tête et sur les rameaux",
                        "Chancres orangés à la base des branches",
                        "Forme 'tête de chat' des bourgeons atteints"
                    ),
                    references = listOf("Observatoire INRAE de la chalarose 2023")
                ),
                ClimateRisk(
                    category = RiskCategory.PEST,
                    name = "Agrile du frêne (Agrilus planipennis)",
                    description = "Coléoptère xylophage d'origine asiatique, potentiellement dévastateur. " +
                        "Non encore établi en France (2024) mais présent en Europe centrale.",
                    affectedZones = setOf(ATL, SO, CON),
                    probability2050 = RiskProbability.POSSIBLE,
                    severity = RiskSeverity.CRITICAL,
                    earlyWarnings = listOf(
                        "Trous de sortie en forme de D (diamètre ~4 mm)",
                        "Déclin rapide du houppier",
                        "Décollement de l'écorce avec galeries sinueuses"
                    )
                )
            ),
            adaptationMeasures = listOf(
                AdaptationMeasure(
                    action = "Ne plus planter de frêne commun en reboisement pur",
                    rationale = "Risque de chalarose quasi-certain",
                    urgency = AdaptationUrgency.NOW,
                    costLevel = CostLevel.LOW
                ),
                AdaptationMeasure(
                    action = "Exploiter les frênes adultes avant dépérissement",
                    rationale = "Valoriser la ressource disponible avant perte de valeur marchande",
                    urgency = AdaptationUrgency.SHORT,
                    costLevel = CostLevel.MODERATE
                ),
                AdaptationMeasure(
                    action = "Conserver les individus génétiquement résistants (5–10%)",
                    rationale = "Base génétique pour sélection future",
                    urgency = AdaptationUrgency.NOW,
                    costLevel = CostLevel.LOW
                )
            ),
            substituteEssences = listOf("ERABLE_SYCOMORE", "CHENE_SESSILE", "AULNE_GLUTINEUX", "TILLEUL", "MERISIER")
        ),

        // ── HÊTRE ────────────────────────────────────────────────────────────
        EssenceRiskProfile(
            essenceCode = "HETRE",
            essenceNameFr = "Hêtre",
            risks = listOf(
                ClimateRisk(
                    category = RiskCategory.DROUGHT,
                    name = "Dépérissement par sécheresses répétées",
                    description = "Le hêtre est très sensible aux sécheresses printanières et estivales. " +
                        "Depuis 2018, des épisodes de dépérissement massifs sont observés dans le sud de l'aire. " +
                        "La limite méridionale de son aire va se contracter de 200–300 km vers le nord d'ici 2050.",
                    affectedZones = setOf(SO, CON, MED),
                    probability2050 = RiskProbability.PROBABLE,
                    severity = RiskSeverity.HIGH,
                    earlyWarnings = listOf(
                        "Microphyllie (petites feuilles en été)",
                        "Roussissement prématuré du feuillage",
                        "Multiplication des champignons lignivores",
                        "Perte du houppier par le haut"
                    )
                ),
                ClimateRisk(
                    category = RiskCategory.PEST,
                    name = "Tordeuse du hêtre (Rhynchaenus fagi)",
                    description = "Les sécheresses favorisent les pullulations d'insectes xylophages sur hêtre affaibli.",
                    affectedZones = setOf(SO, CON),
                    probability2050 = RiskProbability.POSSIBLE,
                    severity = RiskSeverity.MODERATE,
                    earlyWarnings = listOf("Mines foliaires en juillet–août", "Défoliation partielle du houppier")
                )
            ),
            adaptationMeasures = listOf(
                AdaptationMeasure(
                    action = "Préférer le hêtre en mélange avec chêne sessile ou sapin",
                    rationale = "Réduction de la compétition hydrique intra-spécifique",
                    urgency = AdaptationUrgency.SHORT,
                    costLevel = CostLevel.MODERATE
                ),
                AdaptationMeasure(
                    action = "Favoriser le hêtre en altitude et exposition nord",
                    rationale = "Conditions plus fraîches et humides = meilleure survie",
                    urgency = AdaptationUrgency.MEDIUM,
                    costLevel = CostLevel.LOW
                )
            ),
            substituteEssences = listOf("CHENE_SESSILE", "ERABLE_SYCOMORE", "SAPIN_PECTINE", "DOUGLAS")
        ),

        // ── CHÊNE SESSILE ────────────────────────────────────────────────────
        EssenceRiskProfile(
            essenceCode = "CHENE_SESSILE",
            essenceNameFr = "Chêne sessile",
            risks = listOf(
                ClimateRisk(
                    category = RiskCategory.DROUGHT,
                    name = "Sécheresses extrêmes",
                    description = "Le chêne sessile résiste bien à la sécheresse ordinaire mais " +
                        "les sécheresses consécutives (comme 2018–2022) provoquent des dépérissements significatifs, " +
                        "notamment dans les stations superficielles.",
                    affectedZones = setOf(SO, CON, MED),
                    probability2050 = RiskProbability.PROBABLE,
                    severity = RiskSeverity.MODERATE,
                    earlyWarnings = listOf(
                        "Réduction du feuillage en cime",
                        "Brunissement des feuilles en juillet",
                        "Multiplication des chancres à Phytophthora"
                    )
                ),
                ClimateRisk(
                    category = RiskCategory.PEST,
                    name = "Processionnaire du chêne (Thaumetopoea processionea)",
                    description = "Progression spectaculaire vers le nord due au réchauffement. " +
                        "Présente jusqu'en Normandie et Bretagne depuis 2020. Défoliation annuelle.",
                    affectedZones = setOf(ATL, SO, CON, MED),
                    probability2050 = RiskProbability.CERTAIN,
                    severity = RiskSeverity.MODERATE,
                    earlyWarnings = listOf(
                        "Nids soyeux blancs sur les troncs en été",
                        "Défoliation partielle du houppier",
                        "Traces de chenilles processionnaires"
                    )
                )
            ),
            adaptationMeasures = listOf(
                AdaptationMeasure(
                    action = "Maintenir une densité modérée (400–600 tiges/ha à maturité)",
                    rationale = "Réduire la compétition pour l'eau",
                    urgency = AdaptationUrgency.SHORT,
                    costLevel = CostLevel.MODERATE
                ),
                AdaptationMeasure(
                    action = "Surveiller et traiter les foyers de processionnaire",
                    rationale = "Protection de la santé publique et de l'arbre",
                    urgency = AdaptationUrgency.NOW,
                    costLevel = CostLevel.LOW
                )
            ),
            substituteEssences = listOf("CHENE_PUBESCENT", "CHENE_ROUGE_AM", "ALISIER_TORMINAL")
        ),

        // ── DOUGLAS ──────────────────────────────────────────────────────────
        EssenceRiskProfile(
            essenceCode = "DOUGLAS",
            essenceNameFr = "Douglas",
            risks = listOf(
                ClimateRisk(
                    category = RiskCategory.PATHOGEN,
                    name = "Rhabdocline (Rhabdocline pseudotsugae)",
                    description = "Champignon foliaire favorisé par les printemps humides. " +
                        "Peut provoquer une défoliation importante mais rarement mortelle.",
                    affectedZones = setOf(ATL, SO, MON),
                    probability2050 = RiskProbability.POSSIBLE,
                    severity = RiskSeverity.MODERATE,
                    earlyWarnings = listOf(
                        "Taches jaunâtres sur aiguilles en mai–juin",
                        "Chute prématurée des aiguilles de l'année précédente"
                    )
                ),
                ClimateRisk(
                    category = RiskCategory.DROUGHT,
                    name = "Stress hydrique sur stations superficielles",
                    description = "Le douglas est sensible à la sécheresse sur sols peu profonds " +
                        "mais résiste bien sur sols profonds. À surveiller sur stations <40 cm.",
                    affectedZones = setOf(SO, CON, MED),
                    probability2050 = RiskProbability.POSSIBLE,
                    severity = RiskSeverity.LOW,
                    earlyWarnings = listOf("Rougissement des aiguilles internes", "Ralentissement de la pousse")
                )
            ),
            adaptationMeasures = listOf(
                AdaptationMeasure(
                    action = "Utiliser du matériel génétique d'origine côtière (Orégon coastal)",
                    rationale = "Meilleure résistance à la sécheresse que les provenances intérieures",
                    urgency = AdaptationUrgency.SHORT,
                    costLevel = CostLevel.LOW
                )
            ),
            substituteEssences = listOf("SAPIN_PECTINE", "EPICEA_SERBIE", "CHENE_SESSILE")
        ),

        // ── PIN SYLVESTRE ─────────────────────────────────────────────────────
        EssenceRiskProfile(
            essenceCode = "PIN_SYLVESTRE",
            essenceNameFr = "Pin sylvestre",
            risks = listOf(
                ClimateRisk(
                    category = RiskCategory.DROUGHT,
                    name = "Dépérissement massif en plaine",
                    description = "Le pin sylvestre est en fort déclin dans les zones basses (<400 m) " +
                        "de son aire française. Les études INRAE montrent une mortalité de 30–50% " +
                        "dans les peuplements de plaine d'ici 2050.",
                    affectedZones = setOf(SO, CON, MED),
                    probability2050 = RiskProbability.CERTAIN,
                    severity = RiskSeverity.HIGH,
                    earlyWarnings = listOf(
                        "Jaunissement puis rougissement du feuillage",
                        "Perte de la cime terminale",
                        "Coulées de résine excessives"
                    )
                ),
                ClimateRisk(
                    category = RiskCategory.FIRE,
                    name = "Risque incendie",
                    description = "Les peuplements de pin sylvestre en Sologne, Landes et Méditerranée " +
                        "sont des combustibles majeurs avec l'allongement des périodes sèches.",
                    affectedZones = setOf(ATL, SO, MED),
                    probability2050 = RiskProbability.PROBABLE,
                    severity = RiskSeverity.HIGH,
                    earlyWarnings = listOf("Litière épaisse non décomposée", "Végétation basse inflammable")
                )
            ),
            adaptationMeasures = listOf(
                AdaptationMeasure(
                    action = "Convertir progressivement vers chêne sessile et chêne pubescent",
                    rationale = "Essences plus résilientes dans les stations actuelles",
                    urgency = AdaptationUrgency.SHORT,
                    costLevel = CostLevel.MODERATE
                ),
                AdaptationMeasure(
                    action = "Maintenir le pin sylvestre uniquement en altitude (>600 m)",
                    rationale = "Conditions climatiques encore favorables",
                    urgency = AdaptationUrgency.MEDIUM,
                    costLevel = CostLevel.LOW
                )
            ),
            substituteEssences = listOf("CHENE_SESSILE", "CHENE_PUBESCENT", "CEDRE_ATLAS", "MELEZE", "DOUGLAS")
        ),

        // ── AULNE GLUTINEUX ───────────────────────────────────────────────────
        EssenceRiskProfile(
            essenceCode = "AULNE_GLUTINEUX",
            essenceNameFr = "Aulne glutineux",
            risks = listOf(
                ClimateRisk(
                    category = RiskCategory.PATHOGEN,
                    name = "Phytophthora de l'aulne (Phytophthora alni)",
                    description = "Oomycète pathogène des racines et du collet. " +
                        "Présent dans toute la France le long des cours d'eau. " +
                        "Mortalité progressive des aulnes en ripisylve, exacerbée par les étiages sévères.",
                    affectedZones = setOf(ATL, SO, CON, MON, MED),
                    probability2050 = RiskProbability.PROBABLE,
                    severity = RiskSeverity.HIGH,
                    earlyWarnings = listOf(
                        "Feuilles petites et chlorotiques",
                        "Brunissement et nécroses à la base du tronc",
                        "Exsudats noirs sur l'écorce",
                        "Dépérissement asymétrique du houppier"
                    ),
                    references = listOf("DSF Phytophthora aulne 2023")
                )
            ),
            adaptationMeasures = listOf(
                AdaptationMeasure(
                    action = "Diversifier les ripisylves avec saule, peuplier, orme résistant",
                    rationale = "Réduire la dépendance à l'aulne en ripisylve",
                    urgency = AdaptationUrgency.SHORT,
                    costLevel = CostLevel.MODERATE
                ),
                AdaptationMeasure(
                    action = "Conserver les individus indemnes comme semenciers",
                    rationale = "Possible résistance génétique partielle",
                    urgency = AdaptationUrgency.NOW,
                    costLevel = CostLevel.LOW
                )
            ),
            substituteEssences = listOf("SAULE_BLANC", "PEUPLIER_NOIR", "ORME_RESISTANT")
        ),

        // ── CHÂTAIGNIER ───────────────────────────────────────────────────────
        EssenceRiskProfile(
            essenceCode = "CHATAIGNIER",
            essenceNameFr = "Châtaignier",
            risks = listOf(
                ClimateRisk(
                    category = RiskCategory.PATHOGEN,
                    name = "Encre du châtaignier (Phytophthora cinnamomi)",
                    description = "Pathogène racinaire favorisé par les sols compactés et les excès d'humidité. " +
                        "Les températures hivernales douces accélèrent sa progression vers le nord.",
                    affectedZones = setOf(ATL, SO, MED),
                    probability2050 = RiskProbability.PROBABLE,
                    severity = RiskSeverity.HIGH,
                    earlyWarnings = listOf(
                        "Jaunissement et chute prématurée des feuilles",
                        "Exsudats noirs à la base du tronc",
                        "Décollement d'écorce avec lésions nécrotiques"
                    )
                ),
                ClimateRisk(
                    category = RiskCategory.PATHOGEN,
                    name = "Chancre du châtaignier (Cryphonectria parasitica)",
                    description = "Champignon introduit d'Asie, très présent en France. " +
                        "Forme des chancres sur les branches et tiges. " +
                        "La souche hypovirulente limite les dégâts dans certaines zones.",
                    affectedZones = setOf(ATL, SO, CON, MED),
                    probability2050 = RiskProbability.PROBABLE,
                    severity = RiskSeverity.MODERATE,
                    earlyWarnings = listOf(
                        "Chancres orangés sur l'écorce",
                        "Dépérissement des rameaux au-dessus du chancre",
                        "Pustules orangées (sporodochies) en été"
                    )
                )
            ),
            adaptationMeasures = listOf(
                AdaptationMeasure(
                    action = "Éviter le blessage des racines (passage d'engins)",
                    rationale = "Porte d'entrée majeure pour Phytophthora",
                    urgency = AdaptationUrgency.NOW,
                    costLevel = CostLevel.LOW
                ),
                AdaptationMeasure(
                    action = "Maintenir en taillis composé ou mélange avec chêne",
                    rationale = "Réduit la densité et donc l'humidité favorisant les pathogènes",
                    urgency = AdaptationUrgency.SHORT,
                    costLevel = CostLevel.MODERATE
                )
            ),
            substituteEssences = listOf("CHENE_SESSILE", "MERISIER", "NOYER_COMMUN")
        )
    )

    // ─────────────────────────────────────────────────────────────────────────
    //  API de requête
    // ─────────────────────────────────────────────────────────────────────────

    fun getProfileByCode(essenceCode: String): EssenceRiskProfile? {
        val q = essenceCode.trim().uppercase()
        return essenceRiskProfiles.find {
            it.essenceCode.uppercase() == q ||
            it.essenceCode.uppercase().contains(q) ||
            q.contains(it.essenceCode.uppercase())
        }
    }

    fun getRisksForZone(zone: ClimateZone): List<Pair<String, ClimateRisk>> {
        return essenceRiskProfiles.flatMap { profile ->
            profile.risks
                .filter { zone in it.affectedZones }
                .map { profile.essenceNameFr to it }
        }.sortedByDescending { (_, risk) -> risk.severity.ordinal }
    }

    fun getCriticalRisks(): List<Pair<EssenceRiskProfile, ClimateRisk>> {
        return essenceRiskProfiles.flatMap { profile ->
            profile.risks
                .filter { it.severity == RiskSeverity.CRITICAL || it.probability2050 == RiskProbability.CERTAIN }
                .map { profile to it }
        }
    }

    fun getSubstituteEssences(essenceCode: String): List<String> {
        return getProfileByCode(essenceCode)?.substituteEssences ?: emptyList()
    }
}
