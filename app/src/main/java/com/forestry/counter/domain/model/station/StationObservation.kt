package com.forestry.counter.domain.model.station

/**
 * Observation stationnelle complète.
 * Combinaison de données de terrain, données dendro et données géographiques.
 */
data class StationObservation(
    val id: String = "",
    val parcelleId: String = "",
    val observerName: String = "",
    val observationDate: Long = System.currentTimeMillis(),

    // ── Statut et Médias ──
    val isDraft: Boolean = true,
    val photos: List<DiagnosticPhoto> = emptyList(),

    // ── Géolocalisation ──
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeM: Double? = null,
    val commune: String = "",

    // ── Topographie ──
    val pentePct: Double? = null,
    val exposition: Exposition = Exposition.INCONNUE,
    val positionTopo: PositionTopo = PositionTopo.INCONNUE,
    val distanceCourseauM: Double? = null,

    // ── Pédologie ──
    val profondeurSolCm: Int? = null,
    val texture: TextureSol = TextureSol.INCONNUE,
    val pierrosite: Pierrosite = Pierrosite.FAIBLE,
    val hydromorphieProfondeurCm: Int? = null,        // profondeur d'apparition
    val humus: TypeHumus = TypeHumus.INCONNU,
    val phEstime: Double? = null,
    val testHcl: TestHCl = TestHCl.NEGATIF,
    val drainage: Drainage = Drainage.NORMAL,
    val rocheMere: String = "",

    // ── Gradients écologiques (échelle 1–5) ──
    val gradientHydrique: Int = 3,       // 1=très sec, 5=très humide
    val gradientTrophique: Int = 3,      // 1=oligotrophe, 5=eutrophe
    val gradientLumineux: Int = 3,       // 1=très ombragé, 5=plein soleil
    val gradientHumique: Int = 3,        // 1=mor, 5=mull calcique

    // ── Végétation indicatrice ──
    val especesIndicatrices: List<String> = emptyList(),
    val especesXerophiles: Boolean = false,
    val especesMesophiles: Boolean = false,
    val especesHygrophiles: Boolean = false,

    // ── Notes libres ──
    val notes: String = "",

    // ── Profil pédologique multi-horizons ──
    val horizons: List<SoilHorizon> = emptyList(),

    // ── Flore par strates (relevé botanique structuré) ──
    val floraEntries: List<FloraEntry> = emptyList(),

    // ── Biodiversité ──
    val biodiversite: BiodiversiteData = BiodiversiteData(),

    // ── Peuplement qualitatif ──
    val peuplement: PeuplementDescription = PeuplementDescription()
)

data class SoilHorizon(
    val label: String = "",          // ex: "A", "B", "BC", "C"
    val depthFromCm: Int = 0,
    val depthToCm: Int = 30,
    val texture: TextureSol = TextureSol.INCONNUE,
    val couleurMunsell: String = "",  // ex: "10YR 4/4"
    val structure: String = "",       // ex: "Grumeleuse", "Polyédrique"
    val notes: String = "",
    val elemsGrossiersPct: Int = 0,          // % éléments grossiers
    val hclTest: TestHCl = TestHCl.NEGATIF,  // réaction HCl sur l'horizon
    val hydromorphieSigns: Boolean = false,  // traces de rouille / gley
    val racines: DensiteRacines = DensiteRacines.MODEREE
)

data class DiagnosticPhoto(
    val uri: String,
    val legend: String = "",
    val type: String = "Général" // ex: Paysage, Sol, Point dur
)

enum class Exposition(val labelFr: String, val azimut: Int?) {
    N("Nord", 0), NE("Nord-Est", 45), E("Est", 90), SE("Sud-Est", 135),
    S("Sud", 180), SO("Sud-Ouest", 225), O("Ouest", 270), NO("Nord-Ouest", 315),
    PLAT("Plat / Replat", null), INCONNUE("Inconnue", null)
}

enum class PositionTopo(val labelFr: String) {
    CRETE("Crête / Sommet"),
    HAUT_VERSANT("Haut de versant"),
    MI_VERSANT("Mi-versant"),
    BAS_VERSANT("Bas de versant"),
    VALLON("Fond de vallon / Talweg"),
    PLAINE("Plaine / Plateau"),
    INCONNUE("Inconnue")
}

enum class TextureSol(val labelFr: String) {
    ARGILEUSE("Argileuse"),
    LIMONEUSE("Limoneuse"),
    SABLEUSE("Sableuse"),
    ARGILO_LIMONEUSE("Argilo-limoneuse"),
    ARGILO_SABLEUSE("Argilo-sableuse"),
    LIMONO_SABLEUSE("Limono-sableuse"),
    GRAVELEUSE("Graveleuse / Caillouteuse"),
    INCONNUE("Inconnue")
}

enum class Pierrosite(val labelFr: String) {
    NULLE("Nulle"), FAIBLE("Faible (< 10 %)"), MOYENNE("Moyenne (10–30 %)"),
    FORTE("Forte (30–60 %)"), TRES_FORTE("Très forte (> 60 %)")
}

enum class TypeHumus(val labelFr: String) {
    MULL_CALCIQUE("Mull calcique"), MULL("Mull"), MULL_MODER("Mull-Moder"),
    MODER("Moder"), MOR("Mor"), ANMOOR("Anmoor / Tourbe"),
    INCONNU("Inconnu")
}

enum class TestHCl(val labelFr: String) {
    TRES_FORT("Très fort (effervescence vive)"),
    FORT("Fort (effervescence notable)"),
    FAIBLE("Faible (bulles discrètes)"),
    NEGATIF("Négatif (pas d'effervescence)")
}

enum class Drainage(val labelFr: String) {
    EXCESSIF("Excessif"), BON("Bon"), NORMAL("Normal"),
    IMPARFAIT("Imparfait"), MAUVAIS("Mauvais"), TRES_MAUVAIS("Très mauvais / Engorgé")
}

// ─────────────────────────────────────────────────────────────────────────────
//  Relevé botanique par strates
// ─────────────────────────────────────────────────────────────────────────────

enum class StrateVegetale(val labelFr: String, val shortLabel: String) {
    ARBORESCENTE("Arborescente (> 7 m)", "Arb."),
    ARBUSTIVE("Arbustive (1–7 m)", "Arbu."),
    HERBACEE("Herbacée (< 1 m)", "Herb."),
    MUSCALE_SEMIS("Muscicole / Semis", "Musc.")
}

enum class AbondanceDominance(val notation: String, val labelFr: String) {
    TRACE("+", "Traces (présence rare)"),
    UN("1", "Individus rares, recouvrement < 5 %"),
    DEUX("2", "Peu abondant, recouvrement 5–25 %"),
    TROIS("3", "Abondant, recouvrement 25–50 %"),
    QUATRE("4", "Très abondant, recouvrement 50–75 %"),
    CINQ("5", "Dominant, recouvrement > 75 %")
}

data class FloraEntry(
    val speciesId: String,
    val displayName: String = "",
    val strate: StrateVegetale = StrateVegetale.HERBACEE,
    val abondance: AbondanceDominance = AbondanceDominance.UN
)

// ─────────────────────────────────────────────────────────────────────────────
//  Racines par horizon
// ─────────────────────────────────────────────────────────────────────────────

enum class DensiteRacines(val labelFr: String) {
    NULLE("Nulle"),
    RARE("Rare"),
    MODEREE("Modérée"),
    ABONDANTE("Abondante"),
    TRES_ABONDANTE("Très abondante")
}

// ─────────────────────────────────────────────────────────────────────────────
//  Biodiversité
// ─────────────────────────────────────────────────────────────────────────────

enum class MicroHabitat(val labelFr: String) {
    CAVITES("Cavités"),
    FISSURES("Fissures d'écorce"),
    ECORCE_DECOL("Décollements d'écorce"),
    CHAMPIGNONS("Champignons lignicoles"),
    LIERRE("Lierre grimpant"),
    BOIS_MORT_VOL("Bois mort volumineux au sol"),
    LOGES_PICS("Loges de pics"),
    NIDS("Nids / plateformes")
}

data class BiodiversiteData(
    val boisMortSolVolM3: Double? = null,     // volume estimé bois mort au sol (m³/ha)
    val boisMortDeboutNb: Int? = null,        // nb chandelles/stipes morts debout (/ha)
    val microHabitats: Set<MicroHabitat> = emptySet(),
    val tracesGibier: Boolean = false,
    val notesGibier: String = "",
    val notesBiodiversite: String = ""
)

// ─────────────────────────────────────────────────────────────────────────────
//  Description qualitative du peuplement
// ─────────────────────────────────────────────────────────────────────────────

enum class TypeForet(val labelFr: String) {
    PUBLIQUE("Forêt publique (domaniale / communale)"),
    PRIVEE("Forêt privée"),
    INCONNUE("Inconnue")
}

enum class RegimeSylvicole(val labelFr: String) {
    FUTAIE_REGULIERE("Futaie régulière"),
    FUTAIE_IRREGULIERE("Futaie irrégulière / Jardinage"),
    TAILLIS("Taillis simple"),
    TAILLIS_FUTAIE("Taillis sous futaie"),
    INCONNUE("Inconnu")
}

enum class StructureVerticale(val labelFr: String) {
    MONOSTRATE("Monostrate"),
    BISTRATE("Bistrate"),
    PLURISTRATE("Pluristrate"),
    INCONNUE("Inconnue")
}

enum class EtatSanitaire(val labelFr: String) {
    BON("Bon"),
    MOYEN("Moyen"),
    MEDIOCRE("Médiocre"),
    CRITIQUE("Critique")
}

enum class RegenerationNaturelle(val labelFr: String) {
    NULLE("Nulle"),
    FAIBLE("Faible"),
    BONNE("Bonne"),
    ABONDANTE("Abondante"),
    INCONNUE("Inconnue")
}

data class PeuplementDescription(
    val typeForet: TypeForet = TypeForet.INCONNUE,
    val regimeSylvicole: RegimeSylvicole = RegimeSylvicole.INCONNUE,
    val ageEstimeAns: Int? = null,
    val structureVerticale: StructureVerticale = StructureVerticale.INCONNUE,
    val etatSanitaire: EtatSanitaire = EtatSanitaire.BON,
    val regeneration: RegenerationNaturelle = RegenerationNaturelle.INCONNUE,
    val notesPeuplement: String = ""
)
