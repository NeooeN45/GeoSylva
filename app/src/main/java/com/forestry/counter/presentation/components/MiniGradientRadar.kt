package com.forestry.counter.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ══════════════════════════════════════════════════════════════════════════════
//  MiniGradientRadar — Radar / toile d'araignée des gradients écologiques
//  Compact, lisible au soleil, adapté à un affichage sur mobile terrain.
// ══════════════════════════════════════════════════════════════════════════════

data class GradientAxis(
    val label: String,
    val shortLabel: String,
    val value: Float,     // valeur normalisée 0.0–1.0
    val maxValue: Float,  // valeur max brute (pour affichage)
    val rawValue: Double, // valeur brute
    val color: Color
)

@Composable
fun MiniGradientRadar(
    axes: List<GradientAxis>,
    modifier: Modifier = Modifier,
    size: Float = 140f,
    showLabels: Boolean = true,
    title: String? = null
) {
    if (axes.isEmpty()) return

    val primary    = MaterialTheme.colorScheme.primary
    val surface    = MaterialTheme.colorScheme.surfaceVariant
    val onSurface  = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = onSurface,
                modifier = Modifier.padding(bottom = 4.dp))
        }

        Canvas(modifier = Modifier.size(size.dp)) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val radius = min(cx, cy) * 0.72f
            val n = axes.size
            val angleStep = (2 * Math.PI / n).toFloat()
            val startAngle = (-Math.PI / 2).toFloat()

            // ── Grille (niveaux 20%, 40%, 60%, 80%, 100%) ────────────────────
            listOf(0.2f, 0.4f, 0.6f, 0.8f, 1.0f).forEach { level ->
                val path = Path()
                for (i in 0 until n) {
                    val angle = startAngle + i * angleStep
                    val r = radius * level
                    val x = cx + r * cos(angle)
                    val y = cy + r * sin(angle)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, color = surface, style = Stroke(width = 1.dp.toPx()))
            }

            // ── Axes radiaux ──────────────────────────────────────────────────
            for (i in 0 until n) {
                val angle = startAngle + i * angleStep
                drawLine(
                    color = surface,
                    start = Offset(cx, cy),
                    end   = Offset(cx + radius * cos(angle), cy + radius * sin(angle)),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // ── Polygone des valeurs (remplissage semi-transparent) ───────────
            val fillPath = Path()
            axes.forEachIndexed { i, axis ->
                val angle = startAngle + i * angleStep
                val r = radius * axis.value.coerceIn(0f, 1f)
                val x = cx + r * cos(angle)
                val y = cy + r * sin(angle)
                if (i == 0) fillPath.moveTo(x, y) else fillPath.lineTo(x, y)
            }
            fillPath.close()
            drawPath(fillPath, color = primary.copy(alpha = 0.18f))
            drawPath(fillPath, color = primary, style = Stroke(width = 2.dp.toPx()))

            // ── Points de données ─────────────────────────────────────────────
            axes.forEachIndexed { i, axis ->
                val angle = startAngle + i * angleStep
                val r = radius * axis.value.coerceIn(0f, 1f)
                val x = cx + r * cos(angle)
                val y = cy + r * sin(angle)
                drawCircle(color = axis.color, radius = 4.dp.toPx(), center = Offset(x, y))
                drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(x, y))
            }
        }

        // ── Légende compacte ─────────────────────────────────────────────────
        if (showLabels) {
            Spacer(Modifier.height(6.dp))
            val chunked = axes.chunked(2)
            chunked.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { axis ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Box(Modifier.size(8.dp).background(axis.color, RoundedCornerShape(2.dp)))
                            Text(
                                "${axis.shortLabel} ${String.format("%.1f", axis.rawValue)}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Constructeur depuis GradientResult ──────────────────────────────────────

fun gradientAxesFromResult(
    result: com.forestry.counter.domain.usecase.florist.GradientInferenceEngine.GradientResult
): List<GradientAxis> = listOf(
    GradientAxis(
        "Humidité", "H", (result.hydrique / 7.0).toFloat(), 7f, result.hydrique,
        Color(0xFF1565C0)
    ),
    GradientAxis(
        "Fertilité", "N", (result.trophique / 6.0).toFloat(), 6f, result.trophique,
        Color(0xFF2E7D32)
    ),
    GradientAxis(
        "Acidité", "R", (result.aciditeScore / 5.0).toFloat(), 5f, result.aciditeScore,
        Color(0xFF7B1FA2)
    ),
    GradientAxis(
        "Lumière", "L", (result.lumiere / 5.0).toFloat(), 5f, result.lumiere,
        Color(0xFFF9A825)
    )
)

// ─── Radar simple pour gradients manuels station (échelle 1–5) ───────────────

fun gradientAxesManual(
    hydrique: Int, trophique: Int, lumineux: Int, humique: Int
): List<GradientAxis> = listOf(
    GradientAxis("Hydrique",  "H", hydrique  / 5f, 5f, hydrique.toDouble(),  Color(0xFF1565C0)),
    GradientAxis("Trophique", "T", trophique / 5f, 5f, trophique.toDouble(), Color(0xFF2E7D32)),
    GradientAxis("Lumière",   "L", lumineux  / 5f, 5f, lumineux.toDouble(),  Color(0xFFF9A825)),
    GradientAxis("Humus",     "Hu", humique  / 5f, 5f, humique.toDouble(),   Color(0xFF6D4C41))
)
