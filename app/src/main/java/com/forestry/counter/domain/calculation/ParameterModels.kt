package com.forestry.counter.domain.calculation

import kotlinx.serialization.Serializable

@Serializable
data class CoefVolumeRange(
    val essence: String,
    val min: Int,
    val max: Int,
    val f: Double,
    val method: String? = null // ex: "LENT" ou "RAPIDE" (optionnel pour compatibilité)
)

@Serializable
data class HeightDefaultRange(
    val essence: String,
    val min: Int,
    val max: Int,
    val h: Double
)

@Serializable
data class HeightModeEntry(
    val essence: String,
    val diamClass: Int,
    val mode: String,          // DEFAULT | FIXED | SAMPLES
    val fixed: Double? = null  // required if mode == FIXED
)

data class ClassSynthesis(
    val diamClass: Int,
    val count: Int,
    val hMean: Double?,
    val vSum: Double?,
    val valueSumEur: Double? = null
)

data class SynthesisTotals(
    val nTotal: Int,
    val dmWeighted: Double?,
    val hMean: Double?,
    val vTotal: Double?,
    val volumeCompletenessPct: Double = 100.0,
    val volumeComputedCount: Int = 0,
    val volumeExpectedCount: Int = 0
)

@Serializable
data class ProductRule(
    val essence: String? = null, // null or "*" = wildcard
    val min: Int? = null,
    val max: Int? = null,
    val minQuality: Int? = null,
    val maxQuality: Int? = null,
    val requiresDefect: String? = null,
    val excludesDefect: String? = null,
    val product: String // BO, BI, BCh, PATE
)

@Serializable
data class PriceEntry(
    val essence: String,
    val product: String,
    val min: Int,
    val max: Int,
    val eurPerM3: Double,
    val quality: String? = null, // "A", "B", "C", "D" or null = all qualities
    // ── Champs de traçabilité (ajoutés pour le moteur pro) ──
    val source: String = "",          // Source du prix (ex: "ONF 2025", "FBF 2024", "curated")
    val region: String = "",          // Code GRECO (A-L) ou "NATIONAL"
    val year: Int = 2025,             // Année de référence du prix
    val unit: String = "EUR/m3",      // Unité (EUR/m3 sur pied, EUR/m3 bord route, EUR/stere)
    val updatedAt: String = ""        // Date ISO de dernière mise à jour (ex: "2025-01-15")
)
