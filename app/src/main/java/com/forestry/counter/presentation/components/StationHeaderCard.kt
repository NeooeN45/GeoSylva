package com.forestry.counter.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forestry.counter.R
import com.forestry.counter.domain.usecase.station.StationDiagnosticEngine

// ─────────────────────────────────────────────────────────────────────────────
//  StationHeaderCard — Header premium
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StationHeaderCard(
    parcelleId: String,
    commune: String,
    gpsLat: Double?,
    gpsLon: Double?,
    altitudeM: Double?,
    diagResult: StationDiagnosticEngine.StationResult,
    isDraft: Boolean,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onAddPhoto: () -> Unit,
    onSaveDraft: () -> Unit,
    onFinalize: () -> Unit,
    isReadyToFinalize: Boolean,
    modifier: Modifier = Modifier
) {
    val confColor by animateColorAsState(
        targetValue = when (diagResult.confidence) {
            StationDiagnosticEngine.DiagConfidenceStation.FORTE   -> StationDiagColors.forestGreen
            StationDiagnosticEngine.DiagConfidenceStation.MOYENNE -> StationDiagColors.ochrePrimary
            else                                                  -> StationDiagColors.conflictRed
        },
        animationSpec = tween(500),
        label = "conf_color"
    )

    Box(modifier = modifier.fillMaxWidth()) {
        // gradient background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            confColor.copy(alpha = 0.18f),
                            StationDiagColors.background
                        )
                    )
                )
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Top nav row ─────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = StationDiagColors.textPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (commune.isNotBlank()) commune else stringResource(R.string.stationheader_parcelle_format, parcelleId),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = StationDiagColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (gpsLat != null && gpsLon != null) {
                        Text(
                            text = stringResource(R.string.stationheader_gps_coords_format, gpsLat, gpsLon, altitudeM?.toInt()?.let { stringResource(R.string.stationheader_altitude_m_format, it) } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = StationDiagColors.textSecondary,
                            fontSize = 10.sp
                        )
                    } else {
                        Text(stringResource(R.string.stationheader_gps_not_captured), style = MaterialTheme.typography.labelSmall, color = StationDiagColors.toVerify, fontSize = 10.sp)
                    }
                }
                if (isDraft) {
                    Surface(
                        color = StationDiagColors.ochrePrimary.copy(alpha = 0.15f),
                        shape = StationDiagShapes.badge
                    ) {
                        Text(
                            stringResource(R.string.stationheader_draft_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = StationDiagColors.ochrePrimary,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 9.sp
                        )
                    }
                }
            }

            // ── Station type + score ─────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.stationheader_forest_station),
                        style = MaterialTheme.typography.labelSmall,
                        color = confColor.copy(alpha = 0.7f),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 9.sp,
                        letterSpacing = 1.sp
                    )
                    Text(
                        diagResult.typeStation,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = confColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Confidence ring badge
                ConfidenceRingBadge(diagResult.confidence, confColor)
            }

            // ── Quick actions bar ────────────────────────────────────────────
            QuickActionBar(
                onAddPhoto = onAddPhoto,
                onExport = onExport,
                onSaveDraft = onSaveDraft,
                onFinalize = onFinalize,
                isReadyToFinalize = isReadyToFinalize,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Confidence ring badge circulaire
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConfidenceRingBadge(
    confidence: StationDiagnosticEngine.DiagConfidenceStation,
    color: Color
) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .border(2.dp, color.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = when (confidence) {
                    StationDiagnosticEngine.DiagConfidenceStation.FORTE   -> Icons.Default.Verified
                    StationDiagnosticEngine.DiagConfidenceStation.MOYENNE -> Icons.Default.RadioButtonChecked
                    else                                                  -> Icons.Default.HelpOutline
                },
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Text(
                confidence.labelFr,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 8.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  QuickActionBar — barre d'actions rapides horizontale
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun QuickActionBar(
    onAddPhoto: () -> Unit,
    onExport: () -> Unit,
    onSaveDraft: () -> Unit,
    onFinalize: () -> Unit,
    isReadyToFinalize: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = StationDiagColors.surface.copy(alpha = 0.92f),
        shape = StationDiagShapes.card,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuickAction(Icons.Default.PhotoCamera,  stringResource(R.string.stationheader_action_photo),    StationDiagColors.forestGreen, onAddPhoto)
            QuickAction(Icons.Default.PictureAsPdf, stringResource(R.string.stationheader_action_export),   StationDiagColors.waterBlue,   onExport)
            QuickAction(Icons.Default.Save,         stringResource(R.string.stationheader_action_draft),StationDiagColors.soilBrown,   onSaveDraft)

            // Finaliser — bouton mis en avant
            Surface(
                onClick = onFinalize,
                color = if (isReadyToFinalize) StationDiagColors.forestGreen else StationDiagColors.divider,
                shape = StationDiagShapes.chip,
                modifier = Modifier.height(36.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        if (isReadyToFinalize) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        null,
                        tint = if (isReadyToFinalize) Color.White else StationDiagColors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        stringResource(R.string.stationheader_finalize),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isReadyToFinalize) Color.White else StationDiagColors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clip(StationDiagShapes.cardSmall)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(color.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}
