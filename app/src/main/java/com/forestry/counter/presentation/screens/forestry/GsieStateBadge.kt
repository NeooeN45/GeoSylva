package com.forestry.counter.presentation.screens.forestry

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DonutSmall
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.forestry.counter.domain.calculation.MartelageStats
import com.forestry.counter.domain.calculation.SanitySeverity
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.SemanticError
import com.forestry.counter.presentation.theme.SemanticSuccess
import com.forestry.counter.presentation.theme.SemanticWarning
import com.forestry.counter.presentation.theme.Space

/**
 * États d'affichage de la Synthèse V2 (docs/SYNTHESE_V2_SPECIFICATION.md §5).
 * Le document ne prescrit aucune couleur/icône — seule la sémantique des deux
 * états cités explicitement est normative : LOCAL_CALCULATED = "calculé dans
 * GeoSylva", PAS "vérifié par GSIE" ; SERVER_VERIFIED ne doit jamais masquer le
 * résultat local d'origine (donc jamais fusionné, voir MartelageSynthesisTab.kt).
 *
 * Aucune synchronisation réseau réelle n'existe encore dans GeoSylva : seuls
 * BLOCKED/LOCAL_ONLY/LOCAL_CALCULATED/PARTIAL/REVIEW_REQUIRED sont atteignables
 * aujourd'hui. Les 3 états serveur sont représentés mais toujours inertes.
 */
enum class GsieSyncState {
    LOCAL_ONLY,
    LOCAL_CALCULATED,
    SYNC_PENDING,
    SERVER_ACCEPTED,
    SERVER_VERIFIED,
    PARTIAL,
    REVIEW_REQUIRED,
    BLOCKED
}

/** Un état serveur n'est jamais atteint tant que la synchronisation GSIE n'est pas branchée. */
fun GsieSyncState.isServerState(): Boolean = this == GsieSyncState.SYNC_PENDING ||
    this == GsieSyncState.SERVER_ACCEPTED ||
    this == GsieSyncState.SERVER_VERIFIED

private data class GsieStateStyle(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val subtitle: String? = null
)

@Composable
private fun styleFor(state: GsieSyncState): GsieStateStyle = when (state) {
    GsieSyncState.BLOCKED -> GsieStateStyle(
        label = "Bloqué",
        icon = Icons.Default.Block,
        color = SemanticError
    )
    GsieSyncState.LOCAL_ONLY -> GsieStateStyle(
        label = "Local seulement",
        icon = Icons.Default.Smartphone,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    GsieSyncState.LOCAL_CALCULATED -> GsieStateStyle(
        label = "Calculé sur l'appareil",
        icon = Icons.Default.Calculate,
        color = SemanticSuccess,
        subtitle = "Non vérifié par GSIE"
    )
    GsieSyncState.PARTIAL -> GsieStateStyle(
        label = "Partiel",
        icon = Icons.Default.DonutSmall,
        color = SemanticWarning
    )
    GsieSyncState.REVIEW_REQUIRED -> GsieStateStyle(
        label = "À vérifier",
        icon = Icons.Default.Flag,
        color = SemanticWarning
    )
    GsieSyncState.SYNC_PENDING -> GsieStateStyle(
        label = "Synchronisation",
        icon = Icons.Default.CloudUpload,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        subtitle = "Non connecté — la synchronisation GSIE n'est pas encore activée dans cette version"
    )
    GsieSyncState.SERVER_ACCEPTED -> GsieStateStyle(
        label = "Reçu par GSIE",
        icon = Icons.Default.CloudDone,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        subtitle = "Non connecté — la synchronisation GSIE n'est pas encore activée dans cette version"
    )
    // Jamais vert : ne jamais suggérer une vérification serveur qui n'a pas eu lieu.
    GsieSyncState.SERVER_VERIFIED -> GsieStateStyle(
        label = "Vérifié par GSIE",
        icon = Icons.Default.VerifiedUser,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        subtitle = "Non connecté — la synchronisation GSIE n'est pas encore activée dans cette version"
    )
}

/**
 * Badge d'état pour un résultat de cubage (local ou serveur). Les états serveur
 * sont rendus grisés, en bordure pointillée, sans surface pleine — jamais
 * cliquables, jamais colorés comme un succès.
 */
@Composable
fun GsieStateBadge(state: GsieSyncState, modifier: Modifier = Modifier, compact: Boolean = false) {
    val style = styleFor(state)
    val inert = state.isServerState()
    val alpha = if (inert) 0.45f else 1f

    Surface(
        modifier = modifier,
        shape = GsShape.pill,
        color = if (inert) Color.Transparent else style.color.copy(alpha = 0.14f),
        border = if (inert) BorderStroke(1.dp, style.color.copy(alpha = alpha)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                style.icon,
                contentDescription = null,
                tint = style.color.copy(alpha = alpha),
                modifier = Modifier.padding(end = Space.xxs).size(Space.md)
            )
            Text(
                style.label,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                color = style.color.copy(alpha = alpha)
            )
        }
    }
}

/**
 * Sous-texte explicatif d'un badge d'état (si présent) — à afficher sous le badge,
 * jamais à l'intérieur (garde le badge compact et scannable).
 */
@Composable
fun gsieStateSubtitle(state: GsieSyncState): String? = styleFor(state).subtitle

/**
 * Résout l'état d'affichage à partir des données réellement disponibles
 * aujourd'hui — aucun champ fabriqué. Ne retourne jamais un état serveur (ceux-ci
 * ne sont utilisés que pour le badge figé "Vérification serveur GSIE").
 */
fun resolveGsieSyncState(
    stats: MartelageStats?,
    tigesEmpty: Boolean,
    missingParams: Boolean
): GsieSyncState {
    if (tigesEmpty || missingParams) return GsieSyncState.BLOCKED
    if (stats == null) return GsieSyncState.LOCAL_ONLY
    if (stats.sanityWarnings.any { it.severity == SanitySeverity.ERROR }) return GsieSyncState.REVIEW_REQUIRED
    if (!stats.volumeAvailable || stats.unpricedVolumeTotal > 0.0) return GsieSyncState.PARTIAL
    return GsieSyncState.LOCAL_CALCULATED
}
