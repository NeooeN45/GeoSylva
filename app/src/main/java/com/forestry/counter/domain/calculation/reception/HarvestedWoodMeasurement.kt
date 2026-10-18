package com.forestry.counter.domain.calculation.reception

/** Méthode géométrique choisie explicitement pour une grume mesurée. */
enum class RoundwoodVolumeMethod(val code: String) {
    HUBER("huber"),
    SMALIAN("smalian"),
    NEWTON("newton"),
}

/** Convention des diamètres ; aucune correction d'écorce n'est déduite. */
enum class BarkConvention { OVER_BARK, UNDER_BARK }

/** Deux grandeurs distinctes, même si leurs unités géométriques sont des m³. */
enum class HarvestedWoodVolumeKind { GEOMETRIC_LOG_M3, STACKED_ENVELOPE_M3 }

/** Entrées immuables ; leur validité est contrôlée par le calculateur. */
sealed interface HarvestedWoodMeasurement

/**
 * Longueur en mètres, diamètres en centimètres aux positions déclarées.
 * La convention d'écorce est obligatoire. Les sections inutilisées par la
 * méthode sont refusées, plutôt qu'ignorées ou utilisées en remplacement.
 */
data class LogMeasurement(
    val method: RoundwoodVolumeMethod,
    val lengthM: Double,
    val barkConvention: BarkConvention,
    val startDiameterCm: Double? = null,
    val middleDiameterCm: Double? = null,
    val endDiameterCm: Double? = null,
) : HarvestedWoodMeasurement

/**
 * Déclaration d'une enveloppe rectangulaire : longueur, largeur et hauteur
 * moyenne en mètres. La référence du protocole est fournie par l'appelant ;
 * son existence ne certifie ni son authenticité ni sa pertinence terrain.
 */
data class RectangularStackMeasurement(
    val lengthM: Double,
    val widthM: Double,
    val meanHeightM: Double,
    val protocolReference: String,
) : HarvestedWoodMeasurement

/** Résultat géométrique brut, sans arrondi commercial ni conversion de pile. */
class HarvestedWoodVolumeResult internal constructor(
    val input: HarvestedWoodMeasurement,
    val kind: HarvestedWoodVolumeKind,
    val methodCode: String,
    val volumeM3: Double,
    val barkConvention: BarkConvention?,
    val protocolReference: String?,
) {
    val methodVersion: String get() = HarvestedWoodCalculator.METHOD_VERSION
    val contractVersion: String get() = HarvestedWoodCalculator.CONTRACT_VERSION
}

/**
 * Somme homogène de résultats recalculés depuis les entrées.
 * [results] est une copie non modifiable ; ce total ne constitue pas un stock,
 * une quantité contractuelle ou une preuve de réception commerciale.
 */
class HarvestedWoodVolumeAggregate internal constructor(
    val kind: HarvestedWoodVolumeKind,
    val volumeM3: Double,
    val barkConvention: BarkConvention?,
    val protocolReference: String?,
    val results: List<HarvestedWoodVolumeResult>,
) {
    val methodVersion: String get() = HarvestedWoodCalculator.METHOD_VERSION
    val contractVersion: String get() = HarvestedWoodCalculator.CONTRACT_VERSION
}
