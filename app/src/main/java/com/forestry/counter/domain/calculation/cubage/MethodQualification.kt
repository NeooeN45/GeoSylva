package com.forestry.counter.domain.calculation.cubage

/**
 * Statut de qualification d'une méthode de cubage (docs/00_ARCHITECTURE_V2_DOCUMENTATION_INDEX.md §6).
 * Le nom de la méthode ne suffit jamais à la faire passer pour "officielle" —
 * c'est ce statut, explicite, qui porte cette information.
 */
enum class MethodQualification {
    /** Formule géométrique exacte, vérifiée contre un vecteur de référence indépendant. */
    QUALIFIED,

    /** Coefficients empiriques sourcés, mais l'implémentation ne peut pas garantir
     *  l'absence de repli silencieux (essence proche, tarif par défaut, etc.). */
    EXPERIMENTAL,

    /** Aucun vecteur de référence indépendant n'existe encore pour cette méthode. */
    UNQUALIFIED
}
