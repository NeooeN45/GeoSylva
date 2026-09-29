package com.forestry.counter.presentation.screens.forestry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.forestry.counter.domain.calculation.MartelageStats
import com.forestry.counter.presentation.components.AutomatedDataDisclaimer
import com.forestry.counter.presentation.theme.Elevation
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.Space

/**
 * Écran Schémas du Hub Martelage.
 *
 * Affiche les diagrammes du peuplement (triangle G, distribution des
 * diamètres, structure verticale, position des tiges). En attente de la
 * librairie Vico pour le rendu graphique final.
 *
 * Contenu intermédiaire : réutilise [TypelogiqueRapideCard] (outil terrain
 * rapide) et [ClassDistributionCard] (distribution diamètres), en attendant
 * le rendu Vico planifié dans le lot suivant.
 *
 * Spec : §7 de docs/superpowers/specs/2026-09-02-martelage-hub-redesign-design.md
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MartelageSchemasScreen(
    stats: MartelageStats,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Schémas") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Space.screenH),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            item { Spacer(Modifier.height(Space.xs)) }

            // ── Outil de saisie terrain rapide (triangle G) ──
            item { TypelogiqueRapideCard(prefilledGPerHa = stats.gPerHa) }

            // ── Distribution par classe de diamètre ──
            if (stats.classDistribution.isNotEmpty()) {
                item { ClassDistributionCard(classDistribution = stats.classDistribution) }
            }

            // ── Biodiversité (Shannon/Piélou) ──
            stats.biodiversity?.let { bio ->
                item { BiodiversityCard(bio = bio) }
            }

            // ── Placeholder Vico ──
            item { VicoComingSoonBanner() }

            item { AutomatedDataDisclaimer() }
            item { Spacer(Modifier.height(Space.lg)) }
        }
    }
}

/** Encart informatif — diagrammes Vico prévus dans le lot suivant. */
@Composable
private fun VicoComingSoonBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = GsShape.lg,
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = Elevation.flat,
    ) {
        Column(
            modifier = Modifier.padding(Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Text(
                text = "Diagrammes professionnels — à venir",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = "Les graphiques interactifs (triangle G Vico, histogramme des diamètres, " +
                    "structure verticale Hdom/Lorey) sont planifiés dans le lot suivant, " +
                    "après intégration de la librairie Vico (Apache 2.0, offline-first).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Start,
            )
        }
    }
}
