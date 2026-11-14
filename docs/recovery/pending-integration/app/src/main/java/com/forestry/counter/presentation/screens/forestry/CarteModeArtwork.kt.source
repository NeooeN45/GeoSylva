package com.forestry.counter.presentation.screens.forestry

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.forestry.counter.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class CarteModeArtwork(val preview: ImageBitmap, val badge: ImageBitmap)

/**
 * Illustrations de la référence 21 approuvée, conservée sans retouche. Seules
 * ces six régions sont décodées hors du thread UI, sans cadre de téléphone ni
 * texte d'interface. Les titres, aides et actions restent des composants natifs.
 * Aucun moteur cartographique ni accès aux données métier n'est chargé ici.
 */
@Composable
internal fun rememberCarteModeArtwork(): Map<CarteMode, CarteModeArtwork> {
    val resources = LocalContext.current.resources
    val artwork by produceState<Map<CarteMode, CarteModeArtwork>>(emptyMap(), resources) {
        value = withContext(Dispatchers.IO) { decodeArtwork(resources) }
    }
    return artwork
}

@Suppress("DEPRECATION") // Cet overload couvre tout le minSdk 26.
private fun decodeArtwork(resources: Resources): Map<CarteMode, CarteModeArtwork> =
    resources.openRawResource(R.raw.carte_modes_artwork_21).use { stream ->
        val decoder = requireNotNull(BitmapRegionDecoder.newInstance(stream, false))
        try {
            fun region(left: Int, top: Int, right: Int, bottom: Int): ImageBitmap =
                requireNotNull(decoder.decodeRegion(Rect(left, top, right, bottom), BitmapFactory.Options()))
                    .asImageBitmap()
            mapOf(
                CarteMode.MAPS to CarteModeArtwork(
                    region(94, 270, 794, 520), region(123, 534, 233, 644),
                ),
                CarteMode.RECHERCHE to CarteModeArtwork(
                    region(94, 683, 794, 933), region(123, 944, 233, 1054),
                ),
                CarteMode.LIBRE to CarteModeArtwork(
                    region(94, 1090, 794, 1340), region(123, 1350, 233, 1460),
                ),
            )
        } finally {
            decoder.recycle()
        }
    }
