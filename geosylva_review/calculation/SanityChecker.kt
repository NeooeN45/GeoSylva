package com.forestry.counter.domain.calculation

import com.forestry.counter.domain.model.Tige
import kotlin.math.PI
import kotlin.math.pow

/**
 * Garde-fou : vérifie la cohérence des données d'entrée et des résultats
 * de calcul forestier pour détecter erreurs de saisie ou résultats aberrants.
 *
 * Bornes basées sur les extrêmes réels de la dendrométrie française :
 * - Diamètre : 0.5 cm (semis) → 300 cm (séquoia exceptionnel)
 * - Hauteur : 0.5 m → 65 m (douglas/séquoia exceptionnels)
 * - Volume unitaire max ≈ 30 m³ (séquoia géant)
 * - G/ha typique forêt française : 5–80 m²/ha
 * - V/ha typique : 50–1200 m³/ha
 * - N/ha typique : 20–5000 tiges/ha
 */
object SanityChecker {

    // ── Bornes d'entrée ──
    private const val DIAM_MIN_CM = 0.5
    private const val DIAM_MAX_CM = 300.0
    private const val DIAM_WARN_MAX_CM = 150.0
    private const val HEIGHT_MIN_M = 0.5
    private const val HEIGHT_MAX_M = 65.0
    private const val HEIGHT_WARN_MAX_M = 50.0
    private const val COEF_FORME_MIN = 0.15
    private const val COEF_FORME_MAX = 0.85

    // ── Bornes par arbre ──
    private const val VOL_TREE_MAX_M3 = 30.0
    private const val VOL_TREE_WARN_M3 = 15.0
    private const val REVENUE_TREE_MAX_EUR = 50_000.0

    // ── Bornes agrégées par hectare ──
    private const val G_HA_WARN_LOW = 1.0
    private const val G_HA_WARN_HIGH = 80.0
    private const val G_HA_ERROR_HIGH = 150.0
    private const val V_HA_WARN_HIGH = 1200.0
    private const val V_HA_ERROR_HIGH = 3000.0
    private const val N_HA_WARN_HIGH = 5000.0
    private const val N_HA_ERROR_HIGH = 20_000.0
    private const val REVENUE_HA_WARN_HIGH = 100_000.0

    // ── Cohérence hauteur / diamètre (élancement) ──
    // Ratio H(m) / D(cm) : typique 0.4–1.0 ; instable > 1.0 ; extrême > 1.5
    private const val HD_RATIO_WARN = 1.2
    private const val HD_RATIO_ERROR = 2.0

    private fun isResineuxCode(code: String): Boolean {
        val up = code.uppercase()
        return up.contains("PIN") || up.contains("SAPIN") || up.contains("EPICEA") ||
               up.contains("DOUGLAS") || up.contains("MELEZE") || up.contains("CEDRE") ||
               up.contains("SEQUOIA") || up.contains("THUYA") || up.contains("CYPR") ||
               up.contains("ÉPICÉA") || up.contains("MÉLÈZE") || up.contains("CÈDRE")
    }

    // ── Cohérence volume / surface terrière ──
    // Ratio V/G (= hauteur de forme) : typiquement 8–25 m
    private const val VG_RATIO_WARN_LOW = 3.0
    private const val VG_RATIO_WARN_HIGH = 35.0

    // ══════════════════════════════════════════════════
    // API publique
    // ══════════════════════════════════════════════════

    /**
     * Vérifie une tige individuelle (données d'entrée).
     */
    fun checkTige(tige: Tige): List<SanityWarning> {
        val w = mutableListOf<SanityWarning>()

        // Diamètre
        if (tige.diamCm <= 0.0) {
            w += SanityWarning(SanitySeverity.ERROR, SanityDomain.INPUT,
                "diam_zero", tige.id)
        } else if (tige.diamCm < DIAM_MIN_CM) {
            w += SanityWarning(SanitySeverity.ERROR, SanityDomain.INPUT,
                "diam_too_small", tige.id, tige.diamCm)
        } else if (tige.diamCm > DIAM_MAX_CM) {
            w += SanityWarning(SanitySeverity.ERROR, SanityDomain.INPUT,
                "diam_too_large", tige.id, tige.diamCm)
        } else if (tige.diamCm > DIAM_WARN_MAX_CM) {
            w += SanityWarning(SanitySeverity.WARNING, SanityDomain.INPUT,
                "diam_very_large", tige.id, tige.diamCm)
        }

        // Hauteur
        tige.hauteurM?.let { h ->
            if (h <= 0.0) {
                w += SanityWarning(SanitySeverity.ERROR, SanityDomain.INPUT,
                    "height_zero", tige.id)
            } else if (h < HEIGHT_MIN_M) {
                w += SanityWarning(SanitySeverity.ERROR, SanityDomain.INPUT,
                    "height_too_small", tige.id, h)
            } else if (h > HEIGHT_MAX_M) {
                w += SanityWarning(SanitySeverity.ERROR, SanityDomain.INPUT,
                    "height_too_large", tige.id, h)
            } else if (h > HEIGHT_WARN_MAX_M) {
                w += SanityWarning(SanitySeverity.WARNING, SanityDomain.INPUT,
                    "height_very_large", tige.id, h)
            }
        }

        // Cohérence H/D (élancement) — uniquement pour résineux (feuillus ont une forme naturellement plus trapue)
        val h = tige.hauteurM
        if (h != null && h > 0.0 && tige.diamCm > 0.0 && isResineuxCode(tige.essenceCode)) {
            val ratio = h / tige.diamCm  // H(m) / D(cm)
            if (ratio > HD_RATIO_ERROR) {
                w += SanityWarning(SanitySeverity.ERROR, SanityDomain.INPUT,
                    "hd_ratio_extreme", tige.id, ratio)
            } else if (ratio > HD_RATIO_WARN) {
                w += SanityWarning(SanitySeverity.WARNING, SanityDomain.INPUT,
                    "hd_ratio_high", tige.id, ratio)
            }
        }

        // Coefficient de forme
        tige.fCoef?.let { f ->
            if (f < COEF_FORME_MIN || f > COEF_FORME_MAX) {
                w += SanityWarning(SanitySeverity.WARNING, SanityDomain.INPUT,
                    "coef_forme_out_of_range", tige.id, f)
            }
        }

        return w
    }

    /**
     * Vérifie le volume calculé pour un arbre.
     */
    fun checkTreeVolume(tigeId: String, diamCm: Double, volumeM3: Double): List<SanityWarning> {
        val w = mutableListOf<SanityWarning>()

        if (volumeM3 < 0.0) {
            w += SanityWarning(SanitySeverity.ERROR, SanityDomain.VOLUME,
                "volume_negative", tigeId, volumeM3)
        } else if (volumeM3 > VOL_TREE_MAX_M3) {
            w += SanityWarning(SanitySeverity.ERROR, SanityDomain.VOLUME,
                "volume_tree_extreme", tigeId, volumeM3)
        } else if (volumeM3 > VOL_TREE_WARN_M3) {
            w += SanityWarning(SanitySeverity.WARNING, SanityDomain.VOLUME,
                "volume_tree_very_large", tigeId, volumeM3)
        }

        // Ratio volume / surface terrière de l'arbre  (= "hauteur de forme")
        if (diamCm > 0.0 && volumeM3 > 0.0) {
            val g = PI / 4.0 * (diamCm / 100.0).pow(2)
            val hf = volumeM3 / g
            if (hf > 60.0) { // hauteur de forme > 60m = impossible
                w += SanityWarning(SanitySeverity.ERROR, SanityDomain.VOLUME,
                    "volume_vs_diam_incoherent", tigeId, hf)
            }
        }

        return w
    }

    /**
     * Vérifie le revenu calculé pour un arbre.
     */
    fun checkTreeRevenue(tigeId: String, revenueEur: Double): List<SanityWarning> {
        val w = mutableListOf<SanityWarning>()
        if (revenueEur < 0.0) {
            w += SanityWarning(SanitySeverity.ERROR, SanityDomain.REVENUE,
                "revenue_negative", tigeId, revenueEur)
        } else if (revenueEur > REVENUE_TREE_MAX_EUR) {
            w += SanityWarning(SanitySeverity.WARNING, SanityDomain.REVENUE,
                "revenue_tree_extreme", tigeId, revenueEur)
        }
        return w
    }

    /**
     * Vérifie les agrégats par hectare d'une synthèse martelage.
     */
    fun checkAggregates(
        nPerHa: Double,
        gPerHa: Double,
        vPerHa: Double,
        revenuePerHa: Double?,
        surfaceHa: Double,
        ratioVG: Double?
    ): List<SanityWarning> {
        val w = mutableListOf<SanityWarning>()

        // Surface
        if (surfaceHa <= 0.0) {
            w += SanityWarning(SanitySeverity.ERROR, SanityDomain.AGGREGATE,
                "surface_zero")
        } else if (surfaceHa < 0.001) { // < 10 m²
            w += SanityWarning(SanitySeverity.WARNING, SanityDomain.AGGREGATE,
                "surface_very_small", value = surfaceHa)
        }

        // N/ha
        if (nPerHa > N_HA_ERROR_HIGH) {
            w += SanityWarning(SanitySeverity.ERROR, SanityDomain.AGGREGATE,
                "n_ha_extreme", value = nPerHa)
        } else if (nPerHa > N_HA_WARN_HIGH) {
            w += SanityWarning(SanitySeverity.WARNING, SanityDomain.AGGREGATE,
                "n_ha_very_high", value = nPerHa)
        }

        // G/ha
        if (gPerHa > G_HA_ERROR_HIGH) {
            w += SanityWarning(SanitySeverity.ERROR, SanityDomain.AGGREGATE,
                "g_ha_extreme", value = gPerHa)
        } else if (gPerHa > G_HA_WARN_HIGH) {
            w += SanityWarning(SanitySeverity.WARNING, SanityDomain.AGGREGATE,
                "g_ha_very_high", value = gPerHa)
        } else if (gPerHa > 0.0 && gPerHa < G_HA_WARN_LOW) {
            w += SanityWarning(SanitySeverity.INFO, SanityDomain.AGGREGATE,
                "g_ha_very_low", value = gPerHa)
        }

        // V/ha
        if (vPerHa > V_HA_ERROR_HIGH) {
            w += SanityWarning(SanitySeverity.ERROR, SanityDomain.AGGREGATE,
                "v_ha_extreme", value = vPerHa)
        } else if (vPerHa > V_HA_WARN_HIGH) {
            w += SanityWarning(SanitySeverity.WARNING, SanityDomain.AGGREGATE,
                "v_ha_very_high", value = vPerHa)
        }

        // V/G ratio (hauteur de forme moyenne)
        ratioVG?.let { r ->
            if (r < VG_RATIO_WARN_LOW) {
                w += SanityWarning(SanitySeverity.WARNING, SanityDomain.AGGREGATE,
                    "vg_ratio_low", value = r)
            } else if (r > VG_RATIO_WARN_HIGH) {
                w += SanityWarning(SanitySeverity.WARNING, SanityDomain.AGGREGATE,
                    "vg_ratio_high", value = r)
            }
        }

        // Revenu/ha
        revenuePerHa?.let { r ->
            if (r < 0.0) {
                w += SanityWarning(SanitySeverity.ERROR, SanityDomain.REVENUE,
                    "revenue_ha_negative", value = r)
            } else if (r > REVENUE_HA_WARN_HIGH) {
                w += SanityWarning(SanitySeverity.WARNING, SanityDomain.REVENUE,
                    "revenue_ha_very_high", value = r)
            }
        }

        return w
    }

    /**
     * Vérifie un lot complet de tiges (données d'entrée).
     * Retourne uniquement les alertes les plus pertinentes (pas de flood).
     */
    fun checkAllTiges(tiges: List<Tige>): List<SanityWarning> {
        val w = mutableListOf<SanityWarning>()
        var inputErrors = 0
        val maxDetailedAlerts = 10 // limiter le bruit

        for (t in tiges) {
            val alerts = checkTige(t)
            if (alerts.isNotEmpty()) {
                inputErrors++
                if (w.size < maxDetailedAlerts) {
                    w.addAll(alerts)
                }
            }
        }

        if (inputErrors > maxDetailedAlerts) {
            w += SanityWarning(SanitySeverity.WARNING, SanityDomain.INPUT,
                "many_input_errors", value = inputErrors.toDouble())
        }

        // Vérifier les doublons potentiels (même essence + même diamètre + même placette dans les 60s)
        val potentialDupes = tiges
            .groupBy { Triple(it.placetteId, it.essenceCode.uppercase(), it.diamCm) }
            .filter { it.value.size > 1 }
            .flatMap { (key, dupes) ->
                val sorted = dupes.sortedBy { it.timestamp }
                sorted.zipWithNext().filter { (a, b) -> b.timestamp - a.timestamp < 60_000 }
                    .map { (a, _) -> a }
            }

        if (potentialDupes.isNotEmpty()) {
            w += SanityWarning(SanitySeverity.INFO, SanityDomain.INPUT,
                "potential_duplicates", value = potentialDupes.size.toDouble())
        }

        return w
    }
}

// ══════════════════════════════════════════════════
// Modèles
// ══════════════════════════════════════════════════

enum class SanitySeverity { INFO, WARNING, ERROR }

enum class SanityDomain { INPUT, VOLUME, REVENUE, AGGREGATE }

data class SanityWarning(
    val severity: SanitySeverity,
    val domain: SanityDomain,
    val code: String,
    val tigeId: String? = null,
    val value: Double? = null
)
