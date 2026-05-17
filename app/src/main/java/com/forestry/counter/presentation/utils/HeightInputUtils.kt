package com.forestry.counter.presentation.utils

private val HEIGHT_RANGE = 0.5..80.0

fun parseHeightInputMean(input: String): Pair<Double?, Int> {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null to 0
    val values = trimmed.split(",", ";", " ")
        .mapNotNull { it.trim().replace(",", ".").toDoubleOrNull() }
        .filter { it in HEIGHT_RANGE }
    if (values.isEmpty()) return null to 0
    return values.average() to values.size
}
