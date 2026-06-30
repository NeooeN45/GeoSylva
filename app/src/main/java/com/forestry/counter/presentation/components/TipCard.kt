package com.forestry.counter.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forestry.counter.R

/**
 * Global set of dismissed tip keys — persists across recompositions for the
 * entire application session.  Once the user taps the close button, the tip
 * will not reappear until the app process is restarted.
 */
private val dismissedTips = mutableSetOf<String>()

/**
 * Carte de conseil contextuel non-intrusive, affichée une seule fois par session.
 * Peut être fermée par l'utilisateur.
 *
 * @param tipKey Clé unique pour savoir si le tip a déjà été fermé dans cette session
 * @param title Titre court du conseil
 * @param message Message détaillé
 * @param icon Icône (défaut: ampoule)
 */
@Composable
fun TipCard(
    tipKey: String,
    title: String,
    message: String,
    icon: ImageVector = Icons.Default.Lightbulb,
    modifier: Modifier = Modifier
) {
    // Use process-level set so dismissal survives recomposition and navigation
    var dismissed by remember(tipKey) { mutableStateOf(tipKey in dismissedTips) }

    AnimatedVisibility(
        visible = !dismissed,
        enter = expandVertically(tween(300)),
        exit = shrinkVertically(tween(200))
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(top = 2.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                    )
                }
                IconButton(
                    onClick = { dismissedTips.add(tipKey); dismissed = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.tip_close),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
