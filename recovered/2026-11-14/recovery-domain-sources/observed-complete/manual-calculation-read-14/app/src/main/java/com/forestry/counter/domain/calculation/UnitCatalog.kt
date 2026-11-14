package com.forestry.counter.domain.calculation

/**
 * Catalogue d'unités de mesure — spec GEOSYLVA-003 §7.6.
 *
 * Définit les unités forestières standard, leur dimension et le facteur
 * de conversion vers l'unité de référence de chaque dimension.
 *
 * Les unités sont aussi persistées en base (UnitEntity) pour permettre
 * l'extension par packs QPIS (Lot 6). Ce catalogue sert de source de
 * vérité pour l'initialisation et les conversions hors-ligne.
 */
object UnitCatalog {

    /** Dimensions supportées. */
    object Dimension {
        const val LENGTH = "length"
        const val AREA = "area"
        const val VOLUME = "volume"
        const val VOLUME_PER_HA = "volume_per_ha"
        const val MASS = "mass"
        const val ANGLE = "angle"
        const val COUNT = "count"
        const val DIMENSIONLESS = "dimensionless"
    }

    /** Codes d'unités (stables — utilisés en base et dans les mesures). */
    object Code {
        // Length (base: m)
        const val M = "m"
        const val CM = "cm"
        const val MM = "mm"
        const val KM = "km"
        // Area (base: m2)
        const val M2 = "m2"
        const val HA = "ha"
        const val KM2 = "km2"
        // Volume (base: m3)
        const val M3 = "m3"
        const val DM3 = "dm3"
        const val L = "l"
        // Volume per ha (base: m3/ha)
        const val M3_PER_HA = "m3_ha"
        // Mass (base: kg)
        const val KG = "kg"
        const val T = "t"
        // Angle (base: deg)
        const val DEG = "deg"
        const val RAD = "rad"
        // Count (base: unit)
        const val UNIT = "unit"
        // Dimensionless
        const val RATIO = "ratio"
        const val PCT = "pct"
    }

    data class UnitDef(
        val code: String,
        val symbol: String,
        val nameFr: String,
        val dimension: String,
        val toBaseFactor: Double,
        val baseUnitCode: String,
        val description: String? = null
    )

    /** Toutes les unités du catalogue, triées par dimension puis code. */
    val ALL: List<UnitDef> = listOf(
        // Length (base: m)
        UnitDef(Code.M, "m", "mètre", Dimension.LENGTH, 1.0, Code.M, "Unité de longueur de référence"),
        UnitDef(Code.CM, "cm", "centimètre", Dimension.LENGTH, 0.01, Code.M, "Diamètres d'arbres"),
        UnitDef(Code.MM, "mm", "millimètre", Dimension.LENGTH, 0.001, Code.M),
        UnitDef(Code.KM, "km", "kilomètre", Dimension.LENGTH, 1000.0, Code.M),
        // Area (base: m2)
        UnitDef(Code.M2, "m²", "mètre carré", Dimension.AREA, 1.0, Code.M2, "Unité de surface de référence"),
        UnitDef(Code.HA, "ha", "hectare", Dimension.AREA, 10_000.0, Code.M2, "Surface forestière"),
        UnitDef(Code.KM2, "km²", "kilomètre carré", Dimension.AREA, 1_000_000.0, Code.M2),
        // Volume (base: m3)
        UnitDef(Code.M3, "m³", "mètre cube", Dimension.VOLUME, 1.0, Code.M3, "Volume de bois de référence"),
        UnitDef(Code.DM3, "dm³", "décimètre cube", Dimension.VOLUME, 0.001, Code.M3),
        UnitDef(Code.L, "L", "litre", Dimension.VOLUME, 0.001, Code.M3),
        // Volume per ha (base: m3/ha)
        UnitDef(Code.M3_PER_HA, "m³/ha", "mètre cube par hectare", Dimension.VOLUME_PER_HA, 1.0, Code.M3_PER_HA, "Volume à l'hectare"),
        // Mass (base: kg)
        UnitDef(Code.KG, "kg", "kilogramme", Dimension.MASS, 1.0, Code.KG, "Unité de masse de référence"),
        UnitDef(Code.T, "t", "tonne", Dimension.MASS, 1000.0, Code.KG, "Biomasse / carbone"),
        // Angle (base: deg)
        UnitDef(Code.DEG, "°", "degré", Dimension.ANGLE, 1.0, Code.DEG, "Azimut, pente"),
        UnitDef(Code.RAD, "rad", "radian", Dimension.ANGLE, 57.29577951308232, Code.DEG),
        // Count (base: unit)
        UnitDef(Code.UNIT, "u", "unité", Dimension.COUNT, 1.0, Code.UNIT, "Nombre de tiges, d'individus"),
        // Dimensionless
        UnitDef(Code.RATIO, "ratio", "ratio", Dimension.DIMENSIONLESS, 1.0, Code.RATIO),
        UnitDef(Code.PCT, "%", "pourcentage", Dimension.DIMENSIONLESS, 0.01, Code.RATIO, "Pourcentage (converti en ratio)")
    )

    /** Recherche une unité par code. */
    fun get(code: String): UnitDef? = ALL.firstOrNull { it.code == code }

    /** Toutes les unités d'une dimension. */
    fun byDimension(dimension: String): List<UnitDef> = ALL.filter { it.dimension == dimension }
}
