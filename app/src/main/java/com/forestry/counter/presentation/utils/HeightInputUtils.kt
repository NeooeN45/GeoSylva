package com.forestry.counter.presentation.utils

private val HEIGHT_RANGE = 0.5..80.0
private val HEIGHT_VALUE = Regex("(?:[0-9]+(?:[.,][0-9]+)?|[.,][0-9]+)")
private val HEIGHT_WHITESPACE = Regex("[\\p{javaWhitespace}\\p{Zs}]+")

/**
 * Lit des hauteurs en mètres, avec virgule ou point décimal, sans dépendre de la langue du téléphone.
 * Les mesures sont séparées par un point-virgule ou des espaces, y compris les retours à la ligne.
 * Une saisie ambiguë, incomplète ou hors plage rend toute la moyenne indisponible.
 */
fun parseHeightInputMean(input: String): Pair<Double?, Int> {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null to 0

    val values = mutableListOf<Double>()
    for (group in trimmed.split(';')) {
        val measurements = group.trim()
        if (measurements.isEmpty()) return null to 0
        for (token in measurements.split(HEIGHT_WHITESPACE)) {
            if (!HEIGHT_VALUE.matches(token)) return null to 0
            val value = token.replace(',', '.').toDoubleOrNull() ?: return null to 0
            if (!value.isFinite() || value !in HEIGHT_RANGE) return null to 0
            values += value
        }
    }
    return values.average() to values.size
}

/** Signale une erreur de saisie sans confondre une hauteur inconnue, laissée vide, avec une erreur. */
fun hasInvalidHeightInput(inputs: Iterable<String>): Boolean =
    inputs.any { it.isNotBlank() && parseHeightInputMean(it).first == null }
