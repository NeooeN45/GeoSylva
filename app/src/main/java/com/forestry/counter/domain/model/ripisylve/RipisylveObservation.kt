package com.forestry.counter.domain.model.ripisylve

/**
 * Observation de terrain pour le diagnostic de ripisylve.
 * Basé sur l'indice CRPF Hauts-de-France (Forêt-Entreprise n°242, 2018).
 * Score possible : -20 à 100 points.
 */
data class RipisylveObservation(
    val id: String = "",
    val parcelleId: String = "",
    val observerName: String = "",
    val observationDate: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    // ── Localisation ──
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeM: Double? = null,
    val sectionLengthM: Double = 50.0,          // longueur de la section étudiée (50m par défaut)
    val sectionNotes: String = "",

    // ── Critère 1 : Continuité (0-30 pts) ──
    // Pourcentage de couverture linéaire par houppiers
    val continuitePct: Double = 0.0,            // 0-100 %

    // ── Critère 2 : Largeur (0-20 pts) ──
    val largeurMode: LargeurMode = LargeurMode.UNE_RANGEE,

    // ── Critère 3 : Nombre de strates (0-20 pts) ──
    val strateHerbacee: Boolean = false,        // h ≤ 70 cm, ≥ 25% recouvrement
    val strateArbustive: Boolean = false,       // 70 cm < h < 7 m
    val strateArborescente: Boolean = false,    // h ≥ 7 m

    // ── Critère 4 : Diversité spécifique (0-10 pts) ──
    val nbEspecesObservees: Int = 0,
    val especesObservees: List<String> = emptyList(),   // codes ou noms libres

    // ── Critère 5 : Classes de diamètre (0-10 pts) ──
    // Peut être auto-calculé depuis les données dendro
    val diamAutoFromDendro: Boolean = false,
    val hasTresPetitBois: Boolean = false,      // d ≤ 7 cm
    val hasPetitBois: Boolean = false,          // 7 < d < 20 cm
    val hasMoyenBois: Boolean = false,          // 20 ≤ d < 40 cm
    val hasGrosBois: Boolean = false,           // d ≥ 40 cm

    // ── Critère 6 : Microhabitats (0-10 pts) ──
    val microhabitatCavites: Boolean = false,
    val microhabitatFissures: Boolean = false,
    val microhabitatDecollementEcorce: Boolean = false,
    val microhabitatChampignons: Boolean = false,
    val microhabitatBoisMort: Boolean = false,  // bois mort sur pied/sol d ≥ 30 cm
    val microhabitatTresGrosBois: Boolean = false, // d ≥ 70 cm

    // ── Critère 7 : État sanitaire (0 à -20 pts) ──
    val sanitairePct: Double = 0.0,             // % recouvrement atteint

    // ── Critère 8 : Espèces invasives (0 à -20 pts) ──
    val invasivesPct: Double = 0.0,             // % recouvrement
    val invasivesIdentifiees: List<String> = emptyList(),

    // ── Critère 9 : Espèces inadaptées (0 à -10 pts) ──
    val inadapteesMode: InadapteesMode = InadapteesMode.ABSENCE,

    // ── Critère 10 : Stabilité (0 à -20 pts) ──
    val stabilitePct: Double = 0.0,             // % arbres penchés ou affouillement

    // ── Champ libre ──
    val globalNotes: String = "",
    val photoUris: List<String> = emptyList()
)

enum class LargeurMode(val label: String, val points: Int) {
    UNE_RANGEE("1 rangée (< 1.5 m)", 0),
    MOYENNE("1.5 m ≤ l < 5 m", 10),
    LARGE("l ≥ 5 m", 20)
}

enum class InadapteesMode(val label: String, val points: Int) {
    ABSENCE("Absence", 0),
    FAIBLE("1–3 pieds ou r < 25 %", -5),
    FORT("≥ 4 pieds ou r ≥ 25 %", -10)
}
