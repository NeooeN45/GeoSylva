package com.forestry.counter.presentation.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.forestry.counter.R

/**
 * Détecte les noms par défaut générés en anglais ("Parcel 1", "Plot 1")
 * et les remplace par la version localisée selon la locale courante.
 *
 * Les noms par défaut sont persistés en base à la création via
 * context.getString(), donc si l'utilisateur créait la parcelle/placette
 * en locale EN, le nom reste "Parcel 1" même après passage en français.
 * Ce helper corrige ce problème à l'affichage.
 */
@Composable
fun localizeDefaultName(name: String?): String {
    if (name.isNullOrBlank()) return name ?: ""

    // Pattern "Parcel <nombre>" → Parcelle %1$d
    val parcelMatch = PARCEL_DEFAULT_REGEX.matchEntire(name)
    if (parcelMatch != null) {
        val index = parcelMatch.groupValues[1].toIntOrNull() ?: return name
        return stringResource(R.string.parcelle_default_name_format, index)
    }

    // Pattern "Plot <nombre>" → Placette %1$d
    val plotMatch = PLOT_DEFAULT_REGEX.matchEntire(name)
    if (plotMatch != null) {
        val index = plotMatch.groupValues[1].toIntOrNull() ?: return name
        return stringResource(R.string.placette_default_name_format, index)
    }

    return name
}

private val PARCEL_DEFAULT_REGEX = Regex("Parcel (\\d+)")
private val PLOT_DEFAULT_REGEX = Regex("Plot (\\d+)")
