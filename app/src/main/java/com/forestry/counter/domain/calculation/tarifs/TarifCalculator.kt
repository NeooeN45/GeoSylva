package com.forestry.counter.domain.calculation.tarifs

import com.forestry.counter.domain.calculation.EssenceAliases
import kotlin.math.PI
import kotlin.math.pow

/**
 * Moteur de cubage unifié — calcule le volume d'un arbre selon la méthode
 * de tarif sélectionnée par l'utilisateur.
 *
 * Usage :
 *   val vol = TarifCalculator.computeVolume(
 *       method = TarifMethod.SCHAEFFER_1E,
 *       essenceCode = "HETRE_COMMUN",
 *       diamCm = 35.0,
 *       hauteurM = 22.0,
 *       tarifNumero = 7
 *   )
 *
 * Toutes les méthodes retournent le volume bois fort tige (m³).
 */
object TarifCalculator {

    /**
     * Point d'entrée principal : calcule le volume d'un arbre.
     *
     * @param method      Méthode de cubage choisie
     * @param essenceCode Code essence (ex: "HETRE_COMMUN")
     * @param diamCm      Diamètre à 1m30 en cm
     * @param hauteurM    Hauteur totale en m (nullable pour tarifs 1 entrée)
     * @param tarifNumero Numéro du tarif (Schaeffer n°1-16, IFN n°1-36, etc.)
     *                    Si null, le numéro recommandé par essence est utilisé.
     * @param coefFormOverride Coefficient de forme personnalisé (si COEF_FORME)
     * @return Volume en m³, ou null si données insuffisantes
     */
    fun computeVolume(
        method: TarifMethod,
        essenceCode: String,
        diamCm: Double,
        hauteurM: Double?,
        tarifNumero: Int? = null,
        coefFormOverride: Double? = null
    ): Double? {
        if (diamCm <= 0.0) return null

        return when (method) {
            TarifMethod.SCHAEFFER_1E -> volumeSchaeffer1E(essenceCode, diamCm, tarifNumero)
            TarifMethod.SCHAEFFER_2E -> {
                val h = hauteurM ?: return null
                volumeSchaeffer2E(essenceCode, diamCm, h, tarifNumero)
            }
            TarifMethod.ALGAN -> {
                val h = hauteurM ?: return null
                volumeAlgan(essenceCode, diamCm, h)
            }
            TarifMethod.IFN_RAPIDE -> volumeIfnRapide(essenceCode, diamCm, tarifNumero)
            TarifMethod.IFN_LENT -> {
                val h = hauteurM ?: return null
                volumeIfnLent(essenceCode, diamCm, h, tarifNumero)
            }
            TarifMethod.FGH -> {
                val h = hauteurM ?: return null
                volumeFgh(essenceCode, diamCm, h, coefFormOverride)
            }
            TarifMethod.COEF_FORME -> {
                val h = hauteurM ?: return null
                volumeCoefForme(essenceCode, diamCm, h, coefFormOverride)
            }
        }
    }

    /**
     * Vérifie si la méthode nécessite une hauteur.
     */
    fun requiresHeight(method: TarifMethod): Boolean = method.entrees == 2

    /**
     * Retourne le numéro de tarif recommandé pour une essence donnée.
     */
    fun recommendedTarifNumero(method: TarifMethod, essenceCode: String): Int? {
        val code = normalizeEssenceCode(essenceCode)
        return when (method) {
            TarifMethod.SCHAEFFER_1E -> 8 // tarif moyen par défaut
            TarifMethod.SCHAEFFER_2E -> 4
            TarifMethod.IFN_RAPIDE -> TarifData.essenceToIfnRapideNumero[code]
                ?: TarifData.essenceToIfnRapideNumero.values.let { it.sum() / it.size } // moyenne
            TarifMethod.IFN_LENT -> TarifData.essenceToIfnLentNumero[code] ?: 4
            else -> null
        }
    }

    /**
     * Retourne les numéros de tarifs disponibles pour une méthode.
     */
    fun availableTarifNumbers(method: TarifMethod): IntRange? = when (method) {
        TarifMethod.SCHAEFFER_1E -> 1..16
        TarifMethod.SCHAEFFER_2E -> 1..8
        TarifMethod.IFN_RAPIDE -> 1..36
        TarifMethod.IFN_LENT -> 1..8
        else -> null
    }

    /**
     * Retourne le coefficient de forme pour une essence (pour affichage/override).
     */
    fun defaultCoefForme(essenceCode: String): Double {
        val code = normalizeEssenceCode(essenceCode)
        return TarifData.coefsFormeParEssence.firstOrNull { it.essence.equals(code, true) }?.f
            ?: TarifData.coefsFormeParEssence.firstOrNull { it.essence == "*" }?.f
            ?: 0.45
    }

    /**
     * Retourne les coefficients Algan pour une essence (pour affichage).
     */
    fun alganCoefsFor(essenceCode: String): AlganCoefs? {
        val candidates = essenceCodeCandidates(essenceCode)
        for (c in candidates) {
            val found = TarifData.alganCoefs.firstOrNull { it.essence.equals(c, true) }
            if (found != null) return found
        }
        return null
    }

    // ─────────────────────────────────────────────────────
    // Implémentations privées
    // ─────────────────────────────────────────────────────

    private fun volumeSchaeffer1E(essenceCode: String, diamCm: Double, tarifNumero: Int?): Double? {
        val numero = tarifNumero
            ?: TarifData.essenceToIfnRapideNumero[normalizeEssenceCode(essenceCode)]?.let { (it / 2).coerceIn(1, 16) }
            ?: 8
        val coefs = TarifData.schaefferOneEntry.firstOrNull { it.numero == numero } ?: return null
        return coefs.volumeFromDiam(diamCm)
    }

    private fun volumeSchaeffer2E(essenceCode: String, diamCm: Double, hauteurM: Double, tarifNumero: Int?): Double? {
        val numero = tarifNumero
            ?: TarifData.essenceToIfnLentNumero[normalizeEssenceCode(essenceCode)]?.coerceIn(1, 8)
            ?: 4
        val coefs = TarifData.schaefferTwoEntry.firstOrNull { it.numero == numero } ?: return null
        return coefs.volumeFromDiam(diamCm, hauteurM)
    }

    private fun volumeAlgan(essenceCode: String, diamCm: Double, hauteurM: Double): Double? {
        val candidates = essenceCodeCandidates(essenceCode)
        for (c in candidates) {
            val coefs = TarifData.alganCoefs.firstOrNull { it.essence.equals(c, true) }
            if (coefs != null) return coefs.volume(diamCm, hauteurM)
        }
        // Fallback : choisir des coefficients de repli selon la famille d'essence.
        val fallback = fallbackAlganCoefs(essenceCode)
        return fallback?.volume(diamCm, hauteurM)
    }

    private fun fallbackAlganCoefs(essenceCode: String): AlganCoefs? {
        val up = normalizeEssenceCode(essenceCode)
        val looksConifer = listOf(
            "PIN", "SAPIN", "EPICEA", "DOUGLAS", "MELEZE", "CEDRE", "IF", "GENEV"
        ).any { up.contains(it) }

        val prioritized = if (looksConifer) {
            listOf("DOUGLAS_VERT", "EPICEA_COMMUN", "PIN_SYLVESTRE", "SAPIN_PECTINE")
        } else {
            listOf("HETRE_COMMUN", "CH_SESSILE", "CH_PEDONCULE", "CHARME")
        }

        prioritized.forEach { key ->
            TarifData.alganCoefs.firstOrNull { it.essence.equals(key, true) }?.let { return it }
        }

        return TarifData.alganCoefs.firstOrNull { it.essence == "HETRE_COMMUN" }
            ?: TarifData.alganCoefs.firstOrNull()
    }

    private fun volumeIfnRapide(essenceCode: String, diamCm: Double, tarifNumero: Int?): Double? {
        val code = normalizeEssenceCode(essenceCode)
        val numero = tarifNumero ?: TarifData.essenceToIfnRapideNumero[code] ?: return null
        val coefs = TarifData.ifnRapide.firstOrNull { it.numero == numero } ?: return null
        val v = coefs.volumeM3(diamCm)
        return if (v > 0.0) v else null
    }

    private fun volumeIfnLent(essenceCode: String, diamCm: Double, hauteurM: Double, tarifNumero: Int?): Double? {
        val code = normalizeEssenceCode(essenceCode)
        val numero = tarifNumero ?: TarifData.essenceToIfnLentNumero[code] ?: return null
        val coefs = TarifData.ifnLent.firstOrNull { it.numero == numero } ?: return null
        val v = coefs.volumeM3(diamCm, hauteurM)
        return if (v > 0.0) v else null
    }

    private fun volumeCoefForme(essenceCode: String, diamCm: Double, hauteurM: Double, override: Double?): Double {
        val f = override ?: defaultCoefForme(essenceCode)
        val g = PI / 4.0 * (diamCm / 100.0).pow(2.0)
        return g * hauteurM * f
    }

    private fun volumeFgh(essenceCode: String, diamCm: Double, hauteurM: Double, override: Double?): Double {
        val f = override ?: defaultCoefForme(essenceCode)
        val g = PI / 4.0 * (diamCm / 100.0).pow(2.0)
        return f * g * hauteurM
    }

    // ─────────────────────────────────────────────────────
    // Utilitaires pour alias d'essences
    // ─────────────────────────────────────────────────────

    private fun normalizeEssenceCode(code: String): String = code.trim().uppercase()

    private fun essenceCodeCandidates(code: String): List<String> {
        return EssenceAliases.candidates(code)
    }
}

/**
 * Utilitaire : calcul de la ventilation par produit pour un arbre.
 * Retourne une map produit → volume (m³).
 */
object DecoupeCalculator {

    /**
     * Ventile le volume total d'un arbre entre produits selon les règles de découpe.
     *
     * @param volumeTotal Volume total bois fort tige (m³)
     * @param essenceCode Code essence
     * @param categorie   Catégorie ("Feuillu", "Résineux", "Conifère")
     * @param diamCm      Diamètre en cm
     * @param customRules Règles personnalisées (si null, utilise les défauts)
     * @return Map de produit (code) → volume en m³
     */
    fun ventilerParProduit(
        volumeTotal: Double,
        essenceCode: String,
        categorie: String?,
        diamCm: Double,
        customRules: List<DecoupeRule>? = null
    ): Map<String, Double> {
        if (volumeTotal <= 0.0) return emptyMap()
        val d = diamCm.toInt()

        // 1) Chercher les règles spéciales par essence
        val essenceRules = (customRules ?: TarifData.decoupeSpeciales).filter { rule ->
            rule.essence.equals(essenceCode, ignoreCase = true) &&
                d >= rule.minDiam && d <= rule.maxDiam
        }
        if (essenceRules.isNotEmpty()) {
            return applyRules(volumeTotal, essenceRules)
        }

        // 2) Chercher les règles par catégorie
        val catRules = when (categorie?.lowercase()) {
            "feuillu" -> TarifData.decoupeDefautFeuillus
            "résineux", "resineux", "conifère", "conifere" -> TarifData.decoupeDefautResineux
            else -> TarifData.decoupeDefautFeuillus // fallback feuillus
        }.filter { d >= it.minDiam && d <= it.maxDiam }

        if (catRules.isNotEmpty()) {
            return applyRules(volumeTotal, catRules)
        }

        // 3) Fallback : tout en BO si D >= 40, sinon BI
        return if (d >= 40) mapOf("BO" to volumeTotal) else mapOf("BI" to volumeTotal)
    }

    private fun applyRules(volumeTotal: Double, rules: List<DecoupeRule>): Map<String, Double> {
        val totalPct = rules.sumOf { it.pctVolume }
        if (totalPct <= 0.0) return mapOf("BO" to volumeTotal)

        return rules.associate { rule ->
            rule.produit to volumeTotal * (rule.pctVolume / totalPct)
        }
    }
}
