package com.forestry.counter.domain.calculation

import com.forestry.counter.domain.calculation.UnitCatalog.Code
import com.forestry.counter.domain.calculation.UnitCatalog.Dimension
import com.forestry.counter.domain.calculation.UnitCatalog.UnitDef

/**
 * Convertisseur d'unités — spec GEOSYLVA-003 §7.6.
 *
 * Convertit une valeur d'une unité vers une autre, à condition qu'elles
 * appartiennent à la même dimension. Les conversions se font via l'unité
 * de référence de la dimension (toBaseFactor).
 *
 * Exemples :
 *   convert(150.0, "cm", "m") = 1.5
 *   convert(1.0, "ha", "m2") = 10000.0
 *   convert(2.5, "t", "kg") = 2500.0
 */
object UnitConverter {

    /**
     * Convertit une valeur d'une unité vers une autre.
     *
     * @throws IllegalArgumentException si les unités n'existent pas ou
     *   n'appartiennent pas à la même dimension.
     */
    fun convert(value: Double, fromCode: String, toCode: String): Double {
        val from = UnitCatalog.get(fromCode)
            ?: throw IllegalArgumentException("Unité inconnue: $fromCode")
        val to = UnitCatalog.get(toCode)
            ?: throw IllegalArgumentException("Unité inconnue: $toCode")
        require(from.dimension == to.dimension) {
            "Conversion impossible entre dimensions différentes: ${from.dimension} → ${to.dimension}"
        }
        // Cas trivial : même unité
        if (from.code == to.code) return value
        // Conversion via l'unité de base
        val valueInBase = value * from.toBaseFactor
        return valueInBase / to.toBaseFactor
    }

    /** Convertit en tolérant les unités inconnues (retourne la valeur inchangée). */
    fun convertOrSame(value: Double, fromCode: String, toCode: String): Double {
        return try {
            convert(value, fromCode, toCode)
        } catch (e: IllegalArgumentException) {
            value
        }
    }

    /** Vérifie si deux unités sont compatibles (même dimension). */
    fun areCompatible(codeA: String, codeB: String): Boolean {
        val a = UnitCatalog.get(codeA) ?: return false
        val b = UnitCatalog.get(codeB) ?: return false
        return a.dimension == b.dimension
    }

    /** Retourne l'unité de référence d'une dimension. */
    fun baseUnit(dimension: String): UnitDef? = UnitCatalog.byDimension(dimension)
        .firstOrNull { it.code == it.baseUnitCode }

    /**
     * Formate une valeur avec son unité (symbole).
     * Ex: format(1.5, "m") = "1,5 m"
     */
    fun format(value: Double, code: String, decimals: Int = 2): String {
        val unit = UnitCatalog.get(code) ?: return "$value"
        val formatted = "%.${decimals}f".format(value).replace('.', ',')
        return "$formatted ${unit.symbol}"
    }
}
