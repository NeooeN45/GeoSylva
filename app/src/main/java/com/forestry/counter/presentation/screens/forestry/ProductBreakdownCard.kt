package com.forestry.counter.presentation.screens.forestry

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forestry.counter.R
import com.forestry.counter.domain.calculation.ProductBreakdownRow
import com.forestry.counter.domain.calculation.pricing.PricingResult
import java.text.NumberFormat
import java.util.Locale

/**
 * Carte synthèse affichant la ventilation par produit avec prix estimés.
 * Affichée dans MartelageScreen après la VolumeCard.
 *
 * Inclut un rapport de calcul professionnel (breakdown des 8 coefficients)
 * accessible en expandant chaque produit.
 */
@Composable
fun ProductBreakdownCard(
    essenceName: String,
    quality: String?,
    rows: List<ProductBreakdownRow>,
    modifier: Modifier = Modifier
) {
    if (rows.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val fmt = remember { NumberFormat.getCurrencyInstance(Locale.FRANCE) }
    val totalEur = rows.sumOf { it.totalEur }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ---- Header row (always visible) ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.product_breakdown_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = essenceName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        quality?.let { q ->
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.product_qualite, q),
                                style = MaterialTheme.typography.bodySmall,
                                color = when (q.uppercase().firstOrNull()) {
                                    'A' -> MaterialTheme.colorScheme.primary
                                    'B' -> MaterialTheme.colorScheme.secondary
                                    'C' -> MaterialTheme.colorScheme.tertiary
                                    'D' -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Compact total + expand button
                Text(
                    text = fmt.format(totalEur),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }

            // ---- Expanded detail ----
            if (expanded) {
                Spacer(Modifier.height(8.dp))

                // Column header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.product_col_product), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.2f))
                    Text(stringResource(R.string.product_col_vol), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.8f))
                    Text(stringResource(R.string.label_eur_per_m3), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.7f))
                    Text(stringResource(R.string.product_col_total), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.9f), fontWeight = FontWeight.Medium)
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                // Product rows
                rows.forEach { row ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = productDisplayName(row.product),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1.2f)
                            )
                            Text(
                                text = String.format(Locale.FRANCE, "%.2f", row.volumeM3),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(0.8f)
                            )
                            Text(
                                text = String.format(Locale.FRANCE, "%.0f", row.pricePerM3),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(0.7f)
                            )
                            Text(
                                text = fmt.format(row.totalEur),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(0.9f)
                            )
                        }

                        // Rapport de calcul professionnel (si disponible)
                        row.pricingReport?.let { report ->
                            PricingReportSection(report = report, modifier = Modifier.padding(start = 12.dp, top = 2.dp, end = 4.dp))
                        }
                    }
                }

                // Total bar
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.total_estimated_value),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AttachMoney,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = fmt.format(totalEur),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

/**
 * Section "Détail du calcul" affichant le breakdown transparent des 8 coefficients.
 * Lisible par tous : chaque ligne montre le coefficient (×) et le prix intermédiaire en €.
 */
@Composable
private fun PricingReportSection(
    report: PricingResult,
    modifier: Modifier = Modifier
) {
    val b = report.breakdown
    val fmtEur = remember { NumberFormat.getCurrencyInstance(Locale.FRANCE) }

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
        Text(
            text = "Détail du calcul",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )

        // Prix de référence
        CoefficientRow(
            label = "Prix de référence",
            detail = b.basePriceSource,
            value = fmtEur.format(b.basePricePerM3),
            isEuro = true
        )

        // Qualité
        CoefficientRow(
            label = "Qualité (${report.let { it.breakdown.qualitySource.take(20) }})",
            detail = "×${"%.2f".format(b.qualityCoefficient)}",
            value = fmtEur.format(b.basePricePerM3 * b.qualityCoefficient),
            isEuro = true
        )

        // Défauts (si > 0)
        if (b.defectDepreciation > 0.001) {
            CoefficientRow(
                label = "Défauts (${(b.defectDepreciation * 100).toInt()}%)",
                detail = "×${"%.2f".format(b.defectNetCoefficient)}",
                value = fmtEur.format(b.basePricePerM3 * b.qualityCoefficient * b.defectNetCoefficient),
                isEuro = true
            )
        }

        // Région
        if (b.regionalCoefficient != 1.0) {
            CoefficientRow(
                label = "Région (${b.regionalSource.take(15)})",
                detail = "×${"%.2f".format(b.regionalCoefficient)}",
                value = null,
                isEuro = false
            )
        }

        // Accessibilité
        if (b.accessibilityCoefficient != 1.0) {
            CoefficientRow(
                label = "Accessibilité",
                detail = "×${"%.2f".format(b.accessibilityCoefficient)}",
                value = null,
                isEuro = false
            )
        }

        // Position
        if (b.positionCoefficient != 1.0) {
            CoefficientRow(
                label = "Position",
                detail = "×${"%.2f".format(b.positionCoefficient)}",
                value = null,
                isEuro = false
            )
        }

        // Saison
        if (b.seasonCoefficient != 1.0) {
            CoefficientRow(
                label = "Saison",
                detail = "×${"%.2f".format(b.seasonCoefficient)}",
                value = null,
                isEuro = false
            )
        }

        // Certification
        if (b.certificationCoefficient != 1.0) {
            CoefficientRow(
                label = "Certification",
                detail = "×${"%.2f".format(b.certificationCoefficient)}",
                value = null,
                isEuro = false
            )
        }

        // Lot
        if (b.lotSizeCoefficient != 1.0) {
            CoefficientRow(
                label = "Volume du lot",
                detail = "×${"%.2f".format(b.lotSizeCoefficient)}",
                value = null,
                isEuro = false
            )
        }

        // Prix final
        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Prix final",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = fmtEur.format(report.finalPricePerM3) + "/m³",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Warnings
        if (report.warnings.isNotEmpty()) {
            report.warnings.forEach { w ->
                Text(
                    text = "⚠ $w",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun CoefficientRow(
    label: String,
    detail: String,
    value: String?,
    isEuro: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(60.dp)
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(80.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        } else {
            Spacer(Modifier.width(80.dp))
        }
    }
}

private fun productDisplayName(code: String): String = when (code.uppercase()) {
    "BO" -> "Bois d'œuvre"
    "BI" -> "Bois industrie"
    "BCH" -> "Bois de chauffage"
    "BE" -> "Bois d'énergie"
    "PATE" -> "Pâte / trituration"
    "MERAIN" -> "Merrain"
    "TRANCHAGE" -> "Tranchage"
    "SCIAGE_Q" -> "Sciage qualité"
    "GRUME_L" -> "Grume longue"
    "POTEAU" -> "Poteau"
    "SCIAGE_S" -> "Sciage standard"
    "PALETTE" -> "Palette / emballage"
    else -> code
}
