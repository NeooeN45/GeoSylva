package com.forestry.counter.domain.calculation

/**
 * Alias d'essences partagés entre ForestryCalculator et TarifCalculator.
 *
 * Source unique de vérité pour la résolution des codes d'essence.
 * Évite les incohérences de prix (C-PRIX-1) où un code "CHENE" trouvé par
 * TarifCalculator n'était pas reconnu par ForestryCalculator.priceFor().
 */
object EssenceAliases {

    private val aliases: Map<String, List<String>> = mapOf(
        "HETRE" to listOf("HETRE", "HETRE_COMMUN"),
        "HETRE_COMMUN" to listOf("HETRE_COMMUN", "HETRE"),
        "DOUGLAS" to listOf("DOUGLAS", "DOUGLAS_VERT"),
        "DOUGLAS_VERT" to listOf("DOUGLAS_VERT", "DOUGLAS"),
        "CHENE" to listOf("CHENE", "CH_SESSILE", "CH_PEDONCULE"),
        "CH_SESSILE" to listOf("CH_SESSILE", "CHENE"),
        "CH_PEDONCULE" to listOf("CH_PEDONCULE", "CHENE"),
        "PEUPLIER" to listOf("PEUPLIER", "PEUPLIER_HYBR", "PEUPLIER_NOIR"),
        "PEUPLIER_TREMB" to listOf("PEUPLIER_TREMB", "TREMBLE"),
        "TREMBLE" to listOf("TREMBLE", "PEUPLIER_TREMB"),
        "BOULEAU" to listOf("BOULEAU", "BOUL_VERRUQ", "BOUL_PUBESC"),
        "ERABLE" to listOf("ERABLE", "ERABLE_SYC", "ERABLE_PLANE", "ERABLE_CHAMP"),
        "AULNE" to listOf("AULNE", "AULNE_GLUT", "AULNE_BLANC"),
        "ORME" to listOf("ORME", "ORME_CHAMP", "ORME_LISSE", "ORME_MONT"),
        "SAULE" to listOf("SAULE", "SAULE_BLANC", "SAULE_FRAGILE", "SAULE_MARSAULT"),
        "TILLEUL" to listOf("TILLEUL", "TIL_PET_FEUIL", "TIL_GR_FEUIL"),
        "PIN" to listOf("PIN", "PIN_SYLVESTRE", "PIN_MARITIME", "PIN_NOIR_AUTR", "PIN_LARICIO"),
        "PIN_SYLVESTRE" to listOf("PIN_SYLVESTRE", "PIN"),
        "MELEZE" to listOf("MELEZE", "MEL_EUROPE", "MEL_HYBRIDE", "MEL_JAPON"),
        "ALISIER" to listOf("ALISIER", "ALISIER_TORM", "ALISIER_BLANC"),
        "FRENE" to listOf("FRENE", "FRENE_ELEVE", "FRENE_OXYPHYLLE", "FRENE_FLEURS"),
        "SAPIN" to listOf("SAPIN", "SAPIN_PECTINE", "SAPIN_NORDMANN", "SAPIN_GRANDIS"),
        "SAPIN_PECTINE" to listOf("SAPIN_PECTINE", "SAPIN"),
        "EPICEA" to listOf("EPICEA", "EPICEA_COMMUN", "EPICEA_SITKA", "EPICEA_OMORIKA"),
        "EPICEA_COMMUN" to listOf("EPICEA_COMMUN", "EPICEA"),
        "CEDRE" to listOf("CEDRE", "CEDRE_ATLAS", "CEDRE_LIBAN"),
        "CYPRES" to listOf("CYPRES", "CYPRES_PROVENCE", "CYPRES_CHAUVE"),
        "GENEVRIER" to listOf("GENEVRIER", "GENEVRIER_CADE", "GENEVRIER_PHENICIE"),
        "EUCALYPTUS" to listOf("EUCALYPTUS", "EUCALYPTUS_GUNNII", "EUCALYPTUS_GLOBULUS"),
        "CORNOUILLER" to listOf("CORNOUILLER", "CORNOUILLER_MALE", "CORNOUILLER_SANG"),
        "VIORNE" to listOf("VIORNE", "VIORNE_LANTANE", "VIORNE_OBIER"),
        "NOYER" to listOf("NOYER", "NOYER_COMMUN", "NOYER_NOIR"),
        "SORBIER" to listOf("SORBIER", "SORB_OISEL", "SORBIER_DOMESTIQUE")
    )

    /**
     * Retourne la liste des codes candidats pour un code d'essence donné.
     * Le code original est toujours inclus en premier.
     */
    fun candidates(code: String): List<String> {
        val up = code.trim().uppercase()
        return aliases[up] ?: listOf(up)
    }
}
