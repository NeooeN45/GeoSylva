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
    val notes: String = ""
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
