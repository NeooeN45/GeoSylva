package com.forestry.counter.presentation.screens.forestry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.presentation.theme.Elevation
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.Space

/**
 * Écran Carte du Hub Martelage.
 *
 * Carte plein écran affichant les tiges de la placette/parcelle géolocalisées,
 * la délimitation de la parcelle ([Parcelle.geometrieIgnWkt]) et en option
 * le fond cadastre/ONF (WMS, nécessite connexion).
 *
 * Implémentation initiale : placeholder en attente du câblage MapLibre
 * spécifique à ce contexte (les tiges GPS + géométrie de parcelle).
 * La carte de recherche globale ([MapRechercheScreen]) réutilise déjà
 * [MapRenderers] et [MapLayers] — le même pattern sera appliqué ici.
 *
 * Spec : §8 de docs/superpowers/specs/2026-09-02-martelage-hub-redesign-design.md
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MartelageCarteScreen(
    tiges: List<Tige>,
    parcelle: Parcelle?,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Carte") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Space.lg),
                shape = GsShape.lg,
                color = MaterialTheme.colorScheme.secondaryContainer,
                tonalElevation = Elevation.flat,
            ) {
                Column(
                    modifier = Modifier.padding(Space.lg),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Carte de la placette — à venir",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        textAlign = TextAlign.Center,
                    )
                    val tigresGeo = tiges.count { !it.gpsWkt.isNullOrBlank() }
                    Text(
                        text = "$tigresGeo tige${if (tigresGeo > 1) "s" else ""} géolocalisée${if (tigresGeo > 1) "s" else ""}" +
                            (parcelle?.geometrieIgnWkt?.let { "\nDélimitation parcelle disponible" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Le câblage MapLibre avec couche tiges + délimitation parcelle + " +
                            "fond cadastre optionnel est planifié dans le lot suivant.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
