package com.forestry.counter.domain.classification.stand

import com.forestry.counter.domain.calculation.MartelageStats
import kotlin.math.roundToInt

data class DiameterCategoryRatio(
    val pbPct: Double,
    val bmPct: Double,
    val gbPct: Double,
    val tgbPct: Double
) {
    val gbTgbPct: Double get() = gbPct + tgbPct

    fun cnpfStructureCode(): Int = when {
        tgbPct >= 10 -> 4
        gbPct + tgbPct >= 30 -> 3
        bmPct >= 40 -> 2
        else -> 1
    }
}

object StandTypologyDatabase {
    fun computeClassCapital(gPerHa: Double): Int = when {
        gPerHa < 10 -> 1
        gPerHa < 20 -> 2
        gPerHa < 30 -> 3
        gPerHa < 40 -> 4
        else -> 5
    }

    fun classifyFromStats(stats: MartelageStats): StandClassification {
        val gPerHa = stats.gPerHa
        val nPerHa = stats.nPerHa
        val capital = computeClassCapital(gPerHa)
        val code = "F$capital"
        val label = when (capital) {
            1 -> "Peuplement clair"
            2 -> "Peuplement peu dense"
            3 -> "Peuplement normal"
            4 -> "Peuplement dense"
            else -> "Peuplement très dense"
        }
        val advice = when (capital) {
            1 -> "Peuplement sous-exploité. Favoriser la régénération naturelle."
            2 -> "Densité insuffisante. Pas d'éclaircie recommandée."
            3 -> "Sylviculture dynamique recommandée. Éclaircie possible."
            4 -> "Éclaircie forte recommandée pour libérer les arbres d'avenir."
            else -> "Éclaircie urgente — risque instabilité et dépérissement."
        }
        return StandClassification(code = code, capital = capital, label = label, advice = advice, gPerHa = gPerHa, nPerHa = nPerHa)
    }
}

data class StandClassification(
    val code: String,
    val capital: Int,
    val label: String,
    val advice: String,
    val gPerHa: Double,
    val nPerHa: Double
)

object StandClassificationCache {
    var lastStats: MartelageStats? = null
    var lastParcelleId: String? = null
    var lastClassification: StandClassification? = null
}
