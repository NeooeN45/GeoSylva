package com.forestry.counter.domain.calculation.reception

import java.math.BigDecimal
import java.util.Collections
import kotlin.math.PI

/**
 * Noyau embarqué sans accès Android, réseau, base de données ou catalogue.
 * Les formules sont géométriques : elles ne qualifient pas un protocole de
 * réception, une région, une précision terrain ou une transaction.
 */
object HarvestedWoodCalculator {
    const val CONTRACT_VERSION = "wood-reception-volume-1"
    const val METHOD_VERSION = "1.0.0"

    fun calculate(measurement: HarvestedWoodMeasurement): HarvestedWoodVolumeResult =
        when (measurement) {
            is LogMeasurement -> calculateLog(measurement)
            is RectangularStackMeasurement -> calculateStack(measurement)
        }

    /**
     * Accepte plusieurs méthodes de grumes avec la même convention d'écorce.
     * Refuse un mélange grumes/piles ou de protocoles de piles. Chaque entrée
     * est recalculée ; aucun volume précédemment fourni n'est pris pour acquis.
     */
    fun aggregate(measurements: List<HarvestedWoodMeasurement>): HarvestedWoodVolumeAggregate {
        require(measurements.isNotEmpty()) { "L'agrégat exige au moins une mesure." }
        val results = measurements.toList().map(::calculate)
        val first = results.first()
        require(results.all { it.kind == first.kind }) {
            "Les volumes de grumes et les enveloppes de piles ne peuvent pas être additionnés."
        }
        require(results.all { it.barkConvention == first.barkConvention }) {
            "Les conventions sur et sous écorce ne peuvent pas être mélangées."
        }
        require(results.all { it.protocolReference == first.protocolReference }) {
            "Les références de protocole des piles doivent être identiques."
        }
        val total = positiveOutput(sumNonnegative(results.map { it.volumeM3 }), "Somme des volumes")
        return HarvestedWoodVolumeAggregate(
            first.kind, total, first.barkConvention, first.protocolReference,
            Collections.unmodifiableList(ArrayList(results)),
        )
    }

    private fun calculateLog(input: LogMeasurement): HarvestedWoodVolumeResult {
        val length = finiteMeasurement(input.lengthM, "Longueur", allowZero = false)
        val section = when (input.method) {
            RoundwoodVolumeMethod.HUBER -> {
                require(input.startDiameterCm == null && input.endDiameterCm == null) {
                    "Huber accepte uniquement le diamètre à mi-longueur."
                }
                sectionArea(required(input.middleDiameterCm, "Diamètre médian"),
                    "Diamètre médian", allowZero = false)
            }
            RoundwoodVolumeMethod.SMALIAN -> {
                require(input.middleDiameterCm == null) {
                    "Smalian accepte uniquement les diamètres des deux extrémités."
                }
                val start = sectionArea(required(input.startDiameterCm, "Diamètre de début"),
                    "Diamètre de début", allowZero = true)
                val end = sectionArea(required(input.endDiameterCm, "Diamètre de fin"),
                    "Diamètre de fin", allowZero = true)
                positiveOutput(sumNonnegative(listOf(start / 2.0, end / 2.0)),
                    "Section moyenne de Smalian")
            }
            RoundwoodVolumeMethod.NEWTON -> {
                val start = sectionArea(required(input.startDiameterCm, "Diamètre de début"),
                    "Diamètre de début", allowZero = true)
                val middle = sectionArea(required(input.middleDiameterCm, "Diamètre médian"),
                    "Diamètre médian", allowZero = true)
                val end = sectionArea(required(input.endDiameterCm, "Diamètre de fin"),
                    "Diamètre de fin", allowZero = true)
                // Pondérer avant de sommer évite le débordement de 4 × section médiane.
                positiveOutput(sumNonnegative(listOf(start / 6.0, middle * (2.0 / 3.0), end / 6.0)),
                    "Section moyenne de Newton")
            }
        }
        val volume = positiveOutput(length * section, "Volume géométrique de la grume")
        return HarvestedWoodVolumeResult(input, HarvestedWoodVolumeKind.GEOMETRIC_LOG_M3,
            input.method.code, volume, input.barkConvention, null)
    }

    private fun calculateStack(input: RectangularStackMeasurement): HarvestedWoodVolumeResult {
        // U+0085 est blanc pour le contrat Python, contrairement à Kotlin isBlank.
        require(input.protocolReference.any { !it.isWhitespace() && it != '\u0085' }) {
            "La référence du protocole de pile est obligatoire."
        }
        require(input.protocolReference.codePointCount(0, input.protocolReference.length) <= 300) {
            "La référence du protocole de pile est limitée à 300 caractères."
        }
        val length = finiteMeasurement(input.lengthM, "Longueur de pile", allowZero = false)
        val width = finiteMeasurement(input.widthM, "Largeur de pile", allowZero = false)
        val height = finiteMeasurement(input.meanHeightM, "Hauteur moyenne de pile", allowZero = false)
        // Un intermédiaire invalide est refusé, même si un autre ordre de calcul serait représentable.
        val base = positiveOutput(length * width, "Surface de l'enveloppe de pile")
        val volume = positiveOutput(base * height, "Volume apparent de l'enveloppe de pile")
        return HarvestedWoodVolumeResult(input, HarvestedWoodVolumeKind.STACKED_ENVELOPE_M3,
            "rectangular-envelope", volume, null, input.protocolReference)
    }

    private fun required(value: Double?, name: String): Double =
        requireNotNull(value) { "$name est obligatoire pour cette méthode." }

    private fun finiteMeasurement(value: Double, name: String, allowZero: Boolean): Double {
        require(value.isFinite() && (if (allowZero) value >= 0.0 else value > 0.0)) {
            "$name doit être fini et ${if (allowZero) "positif ou nul" else "strictement positif"}."
        }
        return value
    }

    private fun sectionArea(diameterCm: Double, name: String, allowZero: Boolean): Double {
        val diameter = finiteMeasurement(diameterCm, name, allowZero)
        if (diameter == 0.0) return 0.0
        val radiusM = diameter / 200.0
        // Conversion cm → m avant la section ; aucun diamètre à 1,30 m n'est substitué.
        return positiveOutput(PI * radiusM * radiusM, "Section circulaire de $name")
    }

    private fun positiveOutput(value: Double, name: String): Double {
        require(value.isFinite() && value > 0.0) {
            "$name n'est pas représentable comme nombre fini strictement positif."
        }
        return value
    }

    /**
     * Somme exacte des valeurs binaires fournies, puis une seule conversion.
     * BigDecimal(value) préserve le Double exact ; valueOf(value) ne le ferait
     * pas. Aucune précision supplémentaire des mesures n'en est déduite.
     */
    private fun sumNonnegative(values: List<Double>): Double {
        var sum = BigDecimal.ZERO
        for (value in values) {
            require(value.isFinite() && value >= 0.0) { "Une valeur de somme est invalide." }
            sum = sum.add(BigDecimal(value))
        }
        val total = sum.toDouble()
        require(total.isFinite() && total >= 0.0) { "La somme n'est pas représentable." }
        return total
    }
}
