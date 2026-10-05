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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Park
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.forestry.counter.R
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.domain.model.Foret
import com.forestry.counter.presentation.theme.Elevation
import com.forestry.counter.presentation.theme.GsDisplayFont
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.Hairline
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
 * En-tête d'accueil « Canopée ».
 *
 * Un titre serif large posé sur un voile de papier translucide (la photo de
 * fond reste visible autour), puis un seul bandeau-bilan : les deux chiffres
 * clés partagent la même surface plutôt que deux tuiles jumelles.
 */
@Composable
private fun HomeHeader(state: HomeUiState.Success) {
    Column(
        modifier = Modifier.padding(
            start = Space.screenH,
            end = Space.screenH,
            top = Space.xl,
            bottom = Space.xs,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
            shape = GsShape.lg,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Space.xxs),
                modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm),
            ) {
                Text(
                    text = stringResource(R.string.home_greeting),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        StatsBanner(state)
    }
}

/**
 * Bandeau-bilan : dégradé épicéa, deux grands chiffres serif séparés par un
 * filet. Le nombre porte l'information ; l'icône n'est qu'un repère discret.
 */
@Composable
private fun StatsBanner(state: HomeUiState.Success) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val brush = remember(primary) {
        Brush.linearGradient(listOf(primary, lerp(primary, Color.Black, 0.22f)))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GsShape.xl)
            .background(brush)
            .padding(vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatCell(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Forest,
            value = state.foretCount,
            label = stringResource(R.string.home_stat_forests),
            color = onPrimary,
        )
        Box(
            modifier = Modifier
                .width(Hairline.width)
                .height(Space.xl)
                .background(onPrimary.copy(alpha = 0.28f)),
        )
        StatCell(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Park,
            value = state.parcelleCount,
            label = stringResource(R.string.home_stat_parcelles),
            color = onPrimary,
        )
    }
}

@Composable
private fun StatCell(
    icon: ImageVector,
    value: Int,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.xxs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xxs),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(Space.md),
                tint = color.copy(alpha = 0.8f),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    // Surtitre : petites capitales espacées, sur un voile de papier cadré au
    // texte (la section pose directement sur la photo de fond).
    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
        shape = GsShape.pill,
        modifier = Modifier.padding(horizontal = Space.screenH),
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.xxs),
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
        border = BorderStroke(Hairline.width, MaterialTheme.colorScheme.outlineVariant),
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
                shape = GsShape.pill,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(Space.sm)
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
        border = BorderStroke(Hairline.width, MaterialTheme.colorScheme.outlineVariant),
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
                style = MaterialTheme.typography.titleLarge,
                fontFamily = GsDisplayFont,
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ),
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
        border = BorderStroke(Hairline.width, MaterialTheme.colorScheme.outlineVariant),
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
            // Monogramme : l'initiale en serif identifie la forêt d'un coup
            // d'œil, mieux qu'une icône identique sur chaque ligne.
            Surface(
                shape = GsShape.md,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(Touch.min),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = foret.nom.trim().firstOrNull()?.uppercase() ?: "·",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = GsDisplayFont,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
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
