package com.forestry.counter.presentation.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Park
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.forestry.counter.R
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.domain.model.Foret
import com.forestry.counter.presentation.theme.Elevation
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.Space
import com.forestry.counter.presentation.theme.Touch

/**
 * Tableau de bord d'accueil.
 *
 * Registre consultation : texture de fond discrète, densité aérée, chiffres
 * mis en avant. L'écran répond à une seule question — « où en suis-je, et que
 * puis-je faire maintenant ».
 *
 * La version précédente affichait deux tuiles, un titre « Forêts récentes »
 * suivi de rien quand la base était vide, et une entrée « Carte — À venir »
 * qui ne menait nulle part. Un premier lancement se soldait donc par un écran
 * aux deux tiers vide, sans aucune action possible.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToExplorer: () -> Unit,
    onNavigateToForet: (String) -> Unit,
    onCreateForest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val s = state) {
        is HomeUiState.Loading -> LoadingState(modifier)
        is HomeUiState.Success -> HomeContent(
            state = s,
            onNavigateToExplorer = onNavigateToExplorer,
            onNavigateToForet = onNavigateToForet,
            onCreateForest = onCreateForest,
            modifier = modifier,
        )
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.home_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState.Success,
    onNavigateToExplorer: () -> Unit,
    onNavigateToForet: (String) -> Unit,
    onCreateForest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val preferencesManager = LocalContext.current.let { remember(it) { UserPreferencesManager(it) } }
    val backgroundImageEnabled by preferencesManager.backgroundImageEnabled.collectAsStateWithLifecycle(initialValue = true)
    val backgroundImageUri by preferencesManager.backgroundImageUri.collectAsStateWithLifecycle(initialValue = null)

    Box(modifier = modifier.fillMaxSize()) {
        if (backgroundImageEnabled) {
            val uriString = backgroundImageUri
            if (uriString != null) {
                AsyncImage(
                    model = uriString,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.forest_background),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            // Photo plein écran, sans dégradé qui l'efface vers le bas — même
            // traitement que Groupes/Martelage/Parcelles/Placettes (registre
            // consultation, doctrine deux registres). Un essai précédent
            // assombrissait la moitié basse en un aplat uni (« carré noir »)
            // et, en clair, la lavait d'un voile blanchâtre : la lisibilité du
            // texte hors carte repose sur les cartes elles-mêmes, pas sur un
            // scrim qui masque la photo.
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            item { HomeHeader(state) }

        item {
            SectionTitle(stringResource(R.string.home_quick_access))
        }

        item {
            QuickAccessCard(
                icon = Icons.Filled.GridView,
                title = stringResource(R.string.home_explore),
                subtitle = stringResource(R.string.home_explore_sub),
                onClick = onNavigateToExplorer,
            )
        }

        // L'entrée « Carte » a été retirée tant qu'elle n'ouvre rien : un
        // raccourci qui ne mène nulle part coûte plus qu'il ne rassure.

            if (state.recentForets.isEmpty()) {
                item { EmptyForestsCard(onCreateForest) }
            } else {
                item { SectionTitle(stringResource(R.string.home_recent_forests)) }
                items(state.recentForets, key = { it.foretId }) { foret ->
                    RecentForetCard(foret) { onNavigateToForet(foret.foretId) }
                }
            }
        }
    }
}

/**
 * En-tête d'accueil.
 *
 * La photo de fond est portée par [HomeContent] (registre consultation,
 * pleine page comme sur les autres écrans) : ce composant ne pose plus que
 * le texte, en confiance sur le dégradé de légibilité déjà posé derrière.
 */
@Composable
private fun HomeHeader(state: HomeUiState.Success) {
    Box {
        Column(
            modifier = Modifier.padding(
                start = Space.screenH,
                end = Space.screenH,
                top = Space.xl,
                bottom = Space.md,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            // Fondu localisé au seul bloc de texte — pas la pleine page (voir
            // la note plus haut) : sur une photo claire, "Bonjour" devient
            // illisible sans un minimum de contraste derrière. Un pavé
            // arrondi cadré au texte (même traitement que "Accès rapide"
            // plus bas) plutôt qu'un dégradé radial, qui rendait un bord
            // rectangulaire dur au lieu d'un fondu.
            Surface(
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                shape = GsShape.md,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Space.xxs),
                    modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.xs),
                ) {
                    Text(
                        text = stringResource(R.string.home_greeting),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.home_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                StatTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Forest,
                    value = state.foretCount,
                    label = stringResource(R.string.home_stat_forests),
                )
                StatTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Park,
                    value = state.parcelleCount,
                    label = stringResource(R.string.home_stat_parcelles),
                )
            }
        }
    }
}

/**
 * Tuile de chiffre.
 *
 * Le nombre porte l'information, pas l'icône : il occupe donc la plus grande
 * taille de l'écran, aligné à gauche pour que deux tuiles côte à côte se
 * lisent d'un seul balayage du regard.
 */
@Composable
private fun StatTile(
    icon: ImageVector,
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = GsShape.lg,
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = Elevation.flat,
    ) {
        Column(
            modifier = Modifier.padding(Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(Space.lg),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    // Fondu léger, cadré au texte (pas pleine largeur) : "Accès rapide"
    // pose directement sur la photo, sans carte opaque pour le porter.
    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
        shape = GsShape.sm,
        modifier = Modifier.padding(horizontal = Space.screenH),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = Space.xs, vertical = Space.xxs),
        )
    }
}

@Composable
private fun QuickAccessCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = GsShape.md,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screenH)
            .heightIn(min = Touch.field),
    ) {
        Row(
            modifier = Modifier.padding(Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Surface(
                shape = GsShape.sm,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(Space.xs)
                        .size(Space.lg),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * État vide.
 *
 * Ce n'est pas un message d'absence, c'est une invitation : il dit ce qu'il
 * manque, pourquoi c'est utile, et porte l'action qui le résout. Un premier
 * lancement ne doit jamais aboutir à un écran sans issue.
 */
@Composable
private fun EmptyForestsCard(onCreateForest: () -> Unit) {
    Card(
        shape = GsShape.lg,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screenH),
    ) {
        Column(
            modifier = Modifier.padding(Space.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Icon(
                imageVector = Icons.Filled.Forest,
                contentDescription = null,
                modifier = Modifier.size(Space.xl),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.home_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.home_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.xxs))
            Button(
                onClick = onCreateForest,
                shape = GsShape.pill,
                modifier = Modifier.heightIn(min = Touch.min),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(Space.md),
                )
                Spacer(Modifier.size(Space.xs))
                Text(stringResource(R.string.home_empty_action))
            }
        }
    }
}

@Composable
private fun RecentForetCard(foret: Foret, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = GsShape.md,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screenH)
            .heightIn(min = Touch.field),
    ) {
        Row(
            modifier = Modifier.padding(Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Surface(
                shape = GsShape.sm,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.clip(GsShape.sm),
            ) {
                Icon(
                    imageVector = Icons.Filled.Forest,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(Space.xs)
                        .size(Space.lg),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = foret.nom,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = foret.proprietaireNom,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
