package com.quintessences.geosylva.cartography.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Shared visible status; callers supply localized, factual text and real actions. */
@Composable
fun JourneyStatusCard(title: String, detail: String?, active: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), shadowElevation = 4.dp,
        color = if (active) Color(0xFF0964DC) else MaterialTheme.colorScheme.surface,
        contentColor = if (active) Color.White else MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
