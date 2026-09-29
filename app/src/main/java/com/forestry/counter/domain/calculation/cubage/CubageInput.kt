package com.forestry.counter.domain.calculation.cubage

/**
 * Entrée d'une méthode de cubage V2 (moteur additif, voir docs/CUBAGE_OFFLINE_FIRST.md).
 *
 * Deux familles d'entrée distinctes, non interchangeables : un arbre sur pied
 * (mesuré à D130 + hauteur totale) n'est pas une grume/billon (mesuré par
 * longueur + diamètres à une ou plusieurs sections). Une [CubageMethod] ne
 * déclare qu'un seul type d'entrée compatible.
 */
sealed interface CubageInput

/**
 * Arbre sur pied : diamètre à 1m30 (D130) et hauteur totale, comme utilisé
 * par [com.forestry.counter.domain.calculation.tarifs.TarifCalculator].
 */
data class StandingTreeMeasurement(
    val essenceCode: String,
    val diamCm: Double,
    val hauteurM: Double? = null,
    val tarifNumero: Int? = null,
    val coefFormOverride: Double? = null
) : CubageInput

/**
 * Grume ou billon mesuré par longueur et diamètre(s) à une ou plusieurs
 * sections (base, milieu, sommet selon la méthode). Un diamètre non fourni
 * est `null`, jamais 0 — 0 est une valeur mesurée, pas une valeur absente.
 */
data class LogSegmentMeasurement(
    val lengthM: Double,
    val diameterBaseM: Double? = null,
    val diameterMidM: Double? = null,
    val diameterTopM: Double? = null
) : CubageInput
