package com.forestry.counter.domain.classification.stand

// ═══════════════════════════════════════════════════════════════════════════
// Modèles de données — Système de Classification des Peuplements Forestiers
// Inspiré des référentiels IGN / ONF / CNPF adaptés à GeoSylva.
// ═══════════════════════════════════════════════════════════════════════════

// ── 1. Mode de traitement sylvicole ─────────────────────────────────────────
enum class TreatmentMode(val label: String, val shortCode: String) {
    FUTAIE_REGULIERE("Futaie régulière", "FR"),
    FUTAIE_REGULIERE_PAR_PARCELLES("Futaie régulière par parcelles", "FRP"),
    FUTAIE_IRREGULIERE("Futaie irrégulière", "FI"),
    FUTAIE_IRREGULIERE_BOUQUETS("Futaie irrégulière par bouquets", "FIB"),
    FUTAIE_JARDINEE("Futaie jardinée", "FJ"),
    FUTAIE_JARDINEE_GROUPES("Futaie jardinée par groupes", "FJG"),
    FUTAIE_CONVERSION("Futaie en conversion", "FC"),
    FUTAIE_ENRICHIE("Futaie enrichie", "FE"),
    FUTAIE_MELANGEE("Futaie mélangée structurée", "FM"),
    TAILLIS_SIMPLE("Taillis simple", "TS"),
    TAILLIS_DENSE("Taillis dense", "TD"),
    TAILLIS_CLAIR("Taillis clair", "TC"),
    TAILLIS_SOUS_COUVERT("Taillis sous couvert", "TSC"),
    TAILLIS_CONVERSION("Taillis en conversion", "TCo"),
    TAILLIS_VIEILLISSANT("Taillis vieillissant", "TV"),
    TAILLIS_SOUS_FUTAIE("Taillis sous futaie", "TSF"),
    TAILLIS_SOUS_FUTAIE_RICHE("Taillis sous futaie riche en réserves", "TSFR"),
    TAILLIS_SOUS_FUTAIE_PAUVRE("Taillis sous futaie pauvre en réserves", "TSFP"),
    TAILLIS_SOUS_FUTAIE_CONVERSION("Taillis sous futaie en conversion", "TSFCo"),
    INCONNU("Indéterminé", "?")
}

// ── 2. Structure d'âge ───────────────────────────────────────────────────────
enum class AgeStructure(val label: String) {
    EQUIENNE("Structure équienne"),
    QUASI_EQUIENNE("Structure quasi-équienne"),
    REGULIERE_STRATIFIEE("Structure régulière stratifiée"),
    IRREGULIERE_SIMPLE("Structure irrégulière simple"),
    IRREGULIERE_COMPLEXE("Structure irrégulière complexe"),
    IRREGULIERE_EQUILIBREE("Structure irrégulière équilibrée (jardinée)"),
    IRREGULIERE_DESEQUILIBREE("Structure irrégulière déséquilibrée"),
}

// ── 3. Structure verticale ───────────────────────────────────────────────────
enum class VerticalStructure(val label: String) {
    MONOSTRATIFIEE("Monostratifiée"),
    BISTRATIFIEE("Bistratifiée"),
    PLURISTRATIFIEE("Pluristratifiée"),
    MOSAIC("Mosaïque verticale")
}

// ── 4. Composition en essences ───────────────────────────────────────────────
enum class StandComposition(val label: String) {
    PUR_FEUILLU("Pur feuillu"),
    PUR_RESINEUX("Pur résineux"),
    MELANGE_PIED_A_PIED("Mélange pied à pied"),
    MELANGE_BOUQUETS("Mélange par bouquets"),
    MELANGE_PARQUETS("Mélange par parquets"),
    MELANGE_ETAGES("Mélange par étages"),
    MELANGE_MOSAIC("Mélange en mosaïque")
}

// ── 5. Origine ────────────────────────────────────────────────────────────────
enum class StandOrigin(val label: String) {
    REGENERATION_NATURELLE("Régénération naturelle"),
    REGENERATION_NATURELLE_MELANGEE("Régénération naturelle mélangée"),
    COLONISATION_SPONTANEE("Colonisation spontanée"),
    PLANTATION_MONO("Plantation monospécifique"),
    PLANTATION_MELANGEE("Plantation mélangée"),
    REBOISEMENT("Reboisement"),
    ENRICHISSEMENT("Enrichissement sous couvert"),
    REJETS_SOUCHE("Rejets de souche (taillis)"),
    INCONNU("Origine indéterminée")
}

// ── 6. Stade de développement ────────────────────────────────────────────────
enum class DevelopmentStage(val label: String, val dgMinCm: Double, val dgMaxCm: Double) {
    SEMIS("Semis / régénération", 0.0, 2.5),
    FOURRE("Fourré", 2.5, 7.5),
    GAULIS("Gaulis", 7.5, 17.5),
    PERCHIS("Perchis", 17.5, 22.5),
    JEUNE_FUTAIE("Jeune futaie", 22.5, 32.5),
    FUTAIE_ADULTE("Futaie adulte", 32.5, 52.5),
    FUTAIE_MURE("Futaie mûre", 52.5, 72.5),
    FUTAIE_SURANNEE("Futaie surannée", 72.5, 500.0);

    companion object {
        fun fromDg(dg: Double): DevelopmentStage =
            entries.firstOrNull { dg >= it.dgMinCm && dg < it.dgMaxCm } ?: FUTAIE_ADULTE
    }
}

// ── 7. Type écologique ────────────────────────────────────────────────────────
enum class EcologicalType(val label: String) {
    RIPISYLVE("Ripisylve"),
    ALLUVIALE("Forêt alluviale"),
    VERSANT("Forêt de versant"),
    MONTAGNARDE("Forêt montagnarde"),
    MEDITERRANEENNE("Forêt méditerranéenne"),
    PLAINE("Forêt de plaine"),
    HUMIDE("Forêt humide"),
    SECHE("Forêt sèche"),
    INCONNU("Type indéterminé")
}

// ── 8. Dynamique ──────────────────────────────────────────────────────────────
enum class StandDynamic(val label: String) {
    REGENERATION("En régénération"),
    INSTALLATION("En installation"),
    CROISSANCE("En croissance active"),
    MATURATION("En maturation"),
    SENESCENCE("En sénescence"),
    CONVERSION("En conversion sylvicole"),
    TRANSITION_ECOLOGIQUE("En transition écologique"),
}

// ── 9. État de perturbation ───────────────────────────────────────────────────
enum class DisturbanceState(val label: String) {
    SAIN("Peuplement sain"),
    STRESSE("Peuplement stressé"),
    DEPERISSANT("Peuplement dépérissant"),
    PERTURBE("Peuplement perturbé"),
    ENDOMMAGE("Peuplement endommagé"),
    POST_TEMPETE("Post-tempête"),
    POST_INCENDIE("Post-incendie"),
    POST_EXPLOITATION("Post-exploitation récente")
}

// ── 10. Objectif de gestion ───────────────────────────────────────────────────
enum class ManagementObjective(val label: String) {
    BOIS_OEUVRE("Production de bois d'œuvre"),
    BOIS_INDUSTRIE("Production de bois d'industrie"),
    BOIS_ENERGIE("Production de bois énergie"),
    MULTIFONCTIONNEL("Gestion multifonctionnelle"),
    PROTECTION_ECOLOGIQUE("Protection écologique"),
    BIODIVERSITE("Conservation de biodiversité"),
    PAYSAGER("Gestion paysagère")
}

// ── Position dans le triangle des structures ──────────────────────────────────
enum class StructureTrianglePosition(val label: String, val code: String) {
    PB("Petit bois dominant", "PB"),
    BM("Bois moyen dominant", "BM"),
    GB("Gros bois dominant", "GB"),
    PB_BM("Petit et moyen bois", "PM"),
    BM_GB("Moyen et gros bois", "MG"),
    PB_GB("Petit et gros bois — bimodal", "PG"),
    EQUILIBRE("Mélange complet équilibré", "PBG")
}

// ── Catégories de diamètre (triangle CNPF) — seuils feuillus : 17.5/27.5/47.5/67.5
//                                              seuils résineux : 17.5/27.5/42.5/62.5
data class DiameterCategoryRatio(
    val pbPct: Double,   // 17.5–27.5 cm : Petits Bois
    val bmPct: Double,   // 27.5–47.5 cm (feuillus) ou 27.5–42.5 (résineux) : Bois Moyen
    val gbPct: Double,   // 47.5–67.5 cm (feuillus) ou 42.5–62.5 (résineux) : Gros Bois
    val tgbPct: Double = 0.0  // >67.5 cm (feuillus) ou >62.5 (résineux) : Très Gros Bois
) {
    /** Pourcentage combiné GB + TGB (axe Y du triangle des structures). */
    val gbTgbPct: Double get() = gbPct + tgbPct

    /** Numéro de structure CNPF (1–9) conforme à la clé des structures SRGS/CNPF. */
    fun cnpfStructureCode(): Int = when {
        gbTgbPct >= 50.0 -> if (tgbPct >= gbPct) 9 else 8
        gbTgbPct > 20.0  -> when {
            bmPct <= 25.0             -> 5
            pbPct >= 25.0             -> 6
            else                      -> 7
        }
        // gbTgbPct ≤ 20%
        bmPct > 50.0                  -> 4
        pbPct > 50.0 && gbTgbPct <= 5.0 -> 1
        pbPct > 50.0                  -> 2
        else                          -> 3
    }

    fun trianglePosition(): StructureTrianglePosition {
        val dom = maxOf(pbPct, bmPct, gbTgbPct)
        return when {
            dom == pbPct    && pbPct    > 60 -> StructureTrianglePosition.PB
            dom == bmPct    && bmPct    > 60 -> StructureTrianglePosition.BM
            dom == gbTgbPct && gbTgbPct > 60 -> StructureTrianglePosition.GB
            pbPct > 35 && bmPct > 35         -> StructureTrianglePosition.PB_BM
            bmPct > 35 && gbTgbPct > 35      -> StructureTrianglePosition.BM_GB
            pbPct > 25 && gbTgbPct > 25 && bmPct < 30 -> StructureTrianglePosition.PB_GB
            else                             -> StructureTrianglePosition.EQUILIBRE
        }
    }
}

// ── Question posée à l'utilisateur quand les données sont insuffisantes ───────
data class ClassificationQuestion(
    val id: String,
    val text: String,
    val hint: String = "",
    val options: List<String>,
    val defaultIndex: Int = 0
)

// ── Programme de gestion — une intervention ──────────────────────────────────
data class Intervention(
    val timing: String,
    val type: String,
    val objective: String,
    val actions: List<String>,
    val intensityPct: IntRange?,
    val expectedNhaAfter: IntRange? = null
)

// ── Régions SRGS (Schéma Régional de Gestion Sylvicole) ──────────────────────
enum class SRGSRegion(val labelFr: String, val code: String) {
    NORMANDIE("Normandie", "NRM"),
    BRETAGNE("Bretagne", "BRE"),
    PAYS_DE_LA_LOIRE("Pays de la Loire", "PDL"),
    CENTRE_VAL_DE_LOIRE("Centre-Val de Loire", "CVL"),
    ILE_DE_FRANCE("Île-de-France", "IDF"),
    HAUTS_DE_FRANCE("Hauts-de-France", "HDF"),
    GRAND_EST("Grand Est", "GDE"),
    BOURGOGNE_FC("Bourgogne-Franche-Comté", "BFC"),
    AUVERGNE_RA("Auvergne-Rhône-Alpes", "ARA"),
    NOUVELLE_AQUITAINE("Nouvelle-Aquitaine", "NAQ"),
    OCCITANIE("Occitanie", "OCC"),
    PACA("Provence-Alpes-Côte d'Azur", "PAC"),
    CORSE("Corse", "COR"),
    NATIONAL("France (générique)", "NAT")
}

// ── Programme de gestion complet ─────────────────────────────────────────────
data class ManagementProgram(
    val objectiveLabel: String,
    val targetDiamCm: IntRange?,
    val targetAgeAns: IntRange?,
    val targetNha: IntRange?,
    val qualityCible: String,
    val densityTrajectory: List<Pair<String, IntRange>>,
    val interventions: List<Intervention>,
    val srgsRegion: SRGSRegion = SRGSRegion.NATIONAL,
    val srgsItineraire: String = "",
    val srgsNotes: List<String> = emptyList()
)

// ── Explication et diagnostic ─────────────────────────────────────────────────
data class StandDiagnosis(
    val standTypeLabel: String,
    val standTypeCode: String,
    /** Code CNPF formel : ex. "F12", "M22", "T14" (Préfixe + ClassCapital + Structure). */
    val cnpfTypeCode: String,
    /** Phrase explicable justifiant le type CNPF avec les valeurs de G, PB%, GB%. */
    val cnpfTypeExplanation: String,
    val whyClassified: List<String>,
    val ecologicalMeaning: String,
    val sylviculturalImplications: List<String>,
    val risks: List<String>,
    val advantages: List<String>,
    val martelageTips: List<String>
)

// ── Résultat complet de classification ────────────────────────────────────────
data class StandClassificationResult(
    val treatmentMode: TreatmentMode,
    val ageStructure: AgeStructure,
    val verticalStructure: VerticalStructure,
    val composition: StandComposition,
    val origin: StandOrigin,
    val developmentStage: DevelopmentStage,
    val ecologicalType: EcologicalType,
    val dynamic: StandDynamic,
    val disturbanceState: DisturbanceState,
    val suggestedObjective: ManagementObjective,
    val diameterRatio: DiameterCategoryRatio?,
    val confidence: Float,
    val missingDataQuestions: List<ClassificationQuestion>,
    val diagnosis: StandDiagnosis,
    val managementProgram: ManagementProgram
)
