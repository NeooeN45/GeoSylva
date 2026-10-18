package com.forestry.counter.domain.journey

/** Parcours d'interface ; ce choix ne confère aucun droit sur les données. */
enum class ProfessionJourney { MANAGEMENT, OPERATIONS }

/**
 * Codes de métier stables. Les parcours spécialisés propriétaires, enseignement et
 * écologie restent à développer ; leur rattachement initial préserve la gestion.
 * Le code historique ONF/CNPF ne permet pas de choisir un organisme à la place de l'utilisateur.
 */
enum class ProfessionProfile(
    val code: String?,
    val journey: ProfessionJourney?,
    val missionsRelevant: Boolean = false,
) {
    ONF("forestier_onf", ProfessionJourney.MANAGEMENT, true),
    CNPF("forestier_cnpf", ProfessionJourney.MANAGEMENT, true),
    DDT("forestier_ddt", ProfessionJourney.MANAGEMENT, true),
    EXPERT("expert_forestier", ProfessionJourney.MANAGEMENT, true),
    LOGGING_CONTRACTOR("exploitant_forestier", ProfessionJourney.OPERATIONS, true),
    WORKSITE_SUPERVISOR("conducteur_travaux_forestiers", ProfessionJourney.OPERATIONS, true),
    // Le besoin de Missions du commerce seul reste à confirmer.
    TIMBER_TRADE("commerce_bois", ProfessionJourney.OPERATIONS),
    TECHNICIAN("technicien_forestier", ProfessionJourney.MANAGEMENT, true),
    OWNER("proprietaire_forestier", ProfessionJourney.MANAGEMENT),
    STUDENT("etudiant", ProfessionJourney.MANAGEMENT),
    RESEARCHER_TEACHER("chercheur_enseignant", ProfessionJourney.MANAGEMENT),
    OTHER("autre", null),
    LEGACY_PUBLIC_FORESTER("forestier_onf_cnpf", ProfessionJourney.MANAGEMENT, true),
    UNKNOWN(null, null),
}

object ProfessionJourneyResolver {
    /** Valeur inconnue ou texte libre : aucune inférence de métier ou de droits. */
    fun resolve(code: String?): ProfessionProfile =
        ProfessionProfile.entries.firstOrNull { it.code != null && it.code == code }
            ?: ProfessionProfile.UNKNOWN
}
