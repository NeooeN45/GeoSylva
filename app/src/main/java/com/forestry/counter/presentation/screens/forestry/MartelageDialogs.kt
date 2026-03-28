package com.forestry.counter.presentation.screens.forestry

import androidx.activity.result.ActivityResultLauncher
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.forestry.counter.R
import com.forestry.counter.domain.calculation.tarifs.TarifCalculator
import com.forestry.counter.domain.calculation.tarifs.TarifCategory
import com.forestry.counter.domain.calculation.tarifs.TarifMethod
import com.forestry.counter.domain.calculation.tarifs.TarifSelection
import com.forestry.counter.domain.model.Tige
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dialogue adaptatif de sélection de la méthode de cubage.
 * Classe les tarifs par pertinence selon les essences de la placette.
 * Affiche un badge "Spécialisé" et une suggestion automatique.
 */
@Composable
internal fun TarifMethodDialog(
    currentMethod: TarifMethod,
    currentNumero: Int?,
    essenceCodes: List<String> = emptyList(),
    onConfirm: (TarifMethod, Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMethod by remember { mutableStateOf(currentMethod) }
    var selectedNumero by remember { mutableStateOf(currentNumero) }
    var dismissedSuggestion by remember { mutableStateOf(false) }
    var expandedDescription by remember { mutableStateOf<TarifMethod?>(null) }

    val up = remember(essenceCodes) { essenceCodes.map { it.trim().uppercase() } }
    val suggestedMethod = remember(up) { TarifMethod.suggestFor(up) }
    val rankedMethods  = remember(up) { TarifMethod.rankedFor(up) }

    val showSuggestionBanner = !dismissedSuggestion &&
        suggestedMethod.specializedEssences.isNotEmpty() &&
        suggestedMethod != selectedMethod

    val availableRange = TarifCalculator.availableTarifNumbers(selectedMethod)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ─ Header ────────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.martelage_cubage_method),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (up.isNotEmpty()) {
                            Text(
                                "${up.size} essence(s) en placette — classement adapté",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }

                // ─ Bannière suggestion ──────────────────────────────────
                AnimatedVisibility(visible = showSuggestionBanner) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome, null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Tarif conseillé pour vos essences",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    suggestedMethod.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            TextButton(
                                onClick = {
                                    selectedMethod = suggestedMethod
                                    selectedNumero = null
                                    dismissedSuggestion = true
                                }
                            ) { Text("Appliquer") }
                            IconButton(onClick = { dismissedSuggestion = true }) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // ─ Liste des tarifs ────────────────────────────────────────
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val specialized = rankedMethods.filter { it.specializedEssences.isNotEmpty() }
                    val universal   = rankedMethods.filter { it.specializedEssences.isEmpty() }

                    if (specialized.isNotEmpty()) {
                        item {
                            TarifSectionHeader(
                                title = "⭐ Spécialisés — précision maximale",
                                subtitle = "Calibrés sur vos essences ou votre région"
                            )
                        }
                        items(specialized) { method ->
                            TarifMethodRow(
                                method = method,
                                isSelected = selectedMethod == method,
                                isSuggested = method == suggestedMethod,
                                essenceCodes = up,
                                isExpanded = expandedDescription == method,
                                onToggleExpand = {
                                    expandedDescription = if (expandedDescription == method) null else method
                                },
                                onSelect = {
                                    selectedMethod = method
                                    selectedNumero = null
                                }
                            )
                        }
                        item { HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp)) }
                    }

                    item {
                        TarifSectionHeader(
                            title = "📊 Tarifs universels",
                            subtitle = "Applicables à toutes essences et régions"
                        )
                    }
                    items(universal) { method ->
                        TarifMethodRow(
                            method = method,
                            isSelected = selectedMethod == method,
                            isSuggested = false,
                            essenceCodes = up,
                            isExpanded = expandedDescription == method,
                            onToggleExpand = {
                                expandedDescription = if (expandedDescription == method) null else method
                            },
                            onSelect = {
                                selectedMethod = method
                                selectedNumero = null
                            }
                        )
                    }
                }

                // ─ Sélecteur de numéro de tarif (Schaeffer / IFN) ────────────
                if (availableRange != null) {
                    HorizontalDivider()
                    var numeroInput by remember(selectedMethod) {
                        mutableStateOf(selectedNumero?.toString() ?: "")
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.settings_tarif_numero_label),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = numeroInput,
                            onValueChange = { v ->
                                numeroInput = v
                                selectedNumero = v.toIntOrNull()?.coerceIn(availableRange)
                            },
                            modifier = Modifier.width(100.dp),
                            label = { Text("${availableRange.first}–${availableRange.last}") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            singleLine = true
                        )
                    }
                }

                // ─ Boutons de validation ──────────────────────────────────
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        selectedMethod.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.width(4.dp))
                    FilledTonalButton(onClick = { onConfirm(selectedMethod, selectedNumero) }) {
                        Text(stringResource(R.string.validate))
                    }
                }
            }
        }
    }
}

@Composable
private fun TarifSectionHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 8.dp, bottom = 2.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TarifMethodRow(
    method: TarifMethod,
    isSelected: Boolean,
    isSuggested: Boolean,
    essenceCodes: List<String>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelect: () -> Unit
) {
    val isSpecialized = method.specializedEssences.isNotEmpty()
    val matchesEssence = isSpecialized &&
        essenceCodes.any { e -> method.specializedEssences.contains(e) }
    val coversAll = isSpecialized &&
        essenceCodes.isNotEmpty() &&
        essenceCodes.all { e -> method.specializedEssences.contains(e) }

    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            isSuggested -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.20f)
            else -> Color.Transparent
        }, label = "tarifBg"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .then(
                if (isSelected) Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    RoundedCornerShape(10.dp)
                ) else Modifier
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        method.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // Badge suggéré
                    if (isSuggested) {
                        Icon(
                            Icons.Default.AutoAwesome, null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    // Badge spécialisé
                    if (isSpecialized) {
                        val badgeColor = when {
                            coversAll -> MaterialTheme.colorScheme.primaryContainer
                            matchesEssence -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        val badgeText = when {
                            coversAll   -> "Spécialisé ✓"
                            matchesEssence -> "Partiel"
                            else -> "Spécialisé"
                        }
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(badgeColor)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1E / 2E badge
                    Text(
                        if (method.entrees == 1) "1 entrée" else "2 entrées",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (method.entrees == 2)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                    Text("·", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    // Fiabilité en étoiles
                    Text(
                        "★".repeat(method.reliability) + "☆".repeat(5 - method.reliability),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontSize = 10.sp
                    )
                    // Région
                    method.regionLabel?.let { region ->
                        Text("·", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                        Text(
                            region,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 10.sp
                        )
                    }
                }
            }
            // Bouton expand description
            IconButton(
                onClick = onToggleExpand,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // Description développée
        AnimatedVisibility(visible = isExpanded) {
            Text(
                method.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 4.dp, end = 8.dp)
            )
        }
    }
}

/**
 * Dialogue de choix du format d'export QGIS (GeoJSON / CSV XY).
 */
@Composable
internal fun ExportQgisDialog(
    tigesInScope: List<Tige>,
    scopeKey: String,
    onDismiss: () -> Unit,
    onPlayClick: () -> Unit,
    exportGeoJsonLauncher: ActivityResultLauncher<String>,
    exportCsvXyLauncher: ActivityResultLauncher<String>,
    exportShapefileLauncher: ActivityResultLauncher<String>? = null,
    exportCsvMartelageLauncher: ActivityResultLauncher<String>? = null,
    exportPdfLauncher: ActivityResultLauncher<String>? = null,
    exportXlsxLauncher: ActivityResultLauncher<String>? = null,
    exportGpkgLauncher: ActivityResultLauncher<String>? = null,
    viewScopeName: String? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        title = { Text(stringResource(R.string.export)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val gpsCount = remember(tigesInScope) {
                    tigesInScope.count { !it.gpsWkt.isNullOrBlank() }
                }
                Text(
                    stringResource(R.string.gps_satellites_format, gpsCount).replace("Sat", "GPS"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // ── Synthèse martelage ──
                if (exportCsvMartelageLauncher != null) {
                    Text(
                        stringResource(R.string.pdf_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    FilledTonalButton(
                        onClick = {
                            onPlayClick()
                            onDismiss()
                            val ts = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                            val vsName = viewScopeName?.lowercase(Locale.getDefault()) ?: "all"
                            exportCsvMartelageLauncher.launch("martelage-${scopeKey}-${vsName}-${ts}.csv")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CSV " + stringResource(R.string.pdf_title))
                    }
                }

                if (exportPdfLauncher != null) {
                    FilledTonalButton(
                        onClick = {
                            onPlayClick()
                            onDismiss()
                            val ts = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                            exportPdfLauncher.launch("synthese-${scopeKey}-${ts}.pdf")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.export_pdf))
                    }
                }

                if (exportXlsxLauncher != null) {
                    FilledTonalButton(
                        onClick = {
                            onPlayClick()
                            onDismiss()
                            val ts = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                            val vsName = viewScopeName?.lowercase(Locale.getDefault()) ?: "all"
                            exportXlsxLauncher.launch("martelage-${scopeKey}-${vsName}-${ts}.xlsx")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Excel / XLSX (élaboré)")
                    }
                }

                // ── Données spatiales ──
                if (gpsCount > 0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        "QGIS / SIG",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    FilledTonalButton(
                        onClick = {
                            onPlayClick()
                            onDismiss()
                            val ts = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                            exportGeoJsonLauncher.launch("tiges-${scopeKey}-${ts}.geojson")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.export_qgis_geojson))
                            Text(
                                stringResource(R.string.export_qgis_geojson_desc),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            onPlayClick()
                            onDismiss()
                            val ts = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                            exportCsvXyLauncher.launch("tiges-${scopeKey}-${ts}.csv")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.export_qgis_csv_xy))
                            Text(
                                stringResource(R.string.export_qgis_csv_xy_desc),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (exportShapefileLauncher != null) {
                        FilledTonalButton(
                            onClick = {
                                onPlayClick()
                                onDismiss()
                                val ts = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                                exportShapefileLauncher.launch("tiges-${scopeKey}-${ts}.shp.zip")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(stringResource(R.string.export_shapefile))
                                Text(
                                    stringResource(R.string.export_shapefile_desc),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    if (exportGpkgLauncher != null) {
                        FilledTonalButton(
                            onClick = {
                                onPlayClick()
                                onDismiss()
                                val ts = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                                exportGpkgLauncher.launch("martelage-${scopeKey}-${ts}.gpkg")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("GeoPackage (.gpkg)")
                                Text(
                                    "QGIS / QFIELD compat. — Points GPS + attributs + stats",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}
