package com.forestry.counter.presentation.utils

import java.util.Locale

/**
 * Formateur centralisé pour les valeurs dendrométriques et forestières.
 * Remplace les formateurs dupliqués (formatCm, formatM2, formatM3, etc.)
 * dispersés dans les écrans.
 */
object NumberFormatter {

    // ── Diamètres et hauteurs (cm) ────────────────────────────────────────────

    fun cm(value: Double): String = "%.1f".format(value)

    fun cm(value: Double, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%.1f", value)

    // ── Surfaces (m²) ─────────────────────────────────────────────────────────

    fun m2(value: Double): String = "%.3f".format(value)

    fun m2Ha(value: Double): String = "%.2f".format(value)

    // ── Volumes (m³) ──────────────────────────────────────────────────────────

    fun m3(value: Double): String = "%.2f".format(value)

    fun m3Ha(value: Double): String = "%.2f".format(value)

    // ── Poids (tonnes) ────────────────────────────────────────────────────────

    fun tonnes(value: Double): String = "%.2f".format(value)

    // ── Densité (tiges/ha) ────────────────────────────────────────────────────

    fun stemsPerHa(value: Double): String = "%.0f".format(value)

    fun int(value: Int): String = value.toString()

    // ── Pourcentages ──────────────────────────────────────────────────────────

    fun percent(value: Double, decimals: Int = 0): String =
        "%.${decimals}f%%".format(value)

    // ── Surfaces (ha) ─────────────────────────────────────────────────────────

    fun ha(value: Double, decimals: Int = 4): String =
        "%.${decimals}f".format(value)

    // ── Distances (m / km) ────────────────────────────────────────────────────

    fun meters(value: Double): String = "%.1f m".format(value)

    fun kilometers(value: Double): String = "%.3f km".format(value)

    fun distance(meters: Double): String =
        if (meters >= 1000.0) kilometers(meters / 1000.0) else this.meters(meters)

    // ── Coordonnées (degrés décimaux) ─────────────────────────────────────────

    fun latLon(value: Double): String = "%.7f".format(value)

    // ── Angles (degrés) ───────────────────────────────────────────────────────

    fun degrees(value: Double): String = "%.1f°".format(value)
}
