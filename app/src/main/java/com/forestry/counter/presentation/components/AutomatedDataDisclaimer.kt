package com.forestry.counter.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.forestry.counter.presentation.theme.Elevation
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.Space

/**
 * Bandeau de mise en garde standard — affiché en bas de chaque écran
 * Martelage produisant des résultats calculés automatiquement.
 *
 * Spec : docs/superpowers/specs/2026-09-02-martelage-hub-redesign-design.md §12.
 */
@Composable
fun AutomatedDataDisclaimer(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = GsShape.sm,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = Elevation.flat,
    ) {
        Text(
            text = "Ces données sont calculées automatiquement par l'application. " +
                "Comme pour tout système automatisé, vérifiez-les avant toute " +
                "décision engageant la parcelle.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(Space.sm),
        )
    }
}
