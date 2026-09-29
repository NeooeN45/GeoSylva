package com.forestry.counter.presentation.screens.forestry

import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.forestry.counter.R
import com.forestry.counter.domain.calculation.tarifs.TarifCalculator
import com.forestry.counter.domain.calculation.tarifs.TarifMethod
import com.forestry.counter.domain.calculation.tarifs.TarifSelection
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.Space
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dialogue de sélection de la méthode de cubage (Algan, Schaeffer, IFN…).
 */
@Composable
internal fun TarifMethodDialog(
    currentMethod: TarifMethod,
    currentNumero: Int?,
    onConfirm: (TarifMethod, Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMethod by remember { mutableStateOf(currentMethod) }
    var selectedNumero by remember { mutableStateOf(currentNumero) }
    val availableRange = TarifCalculator.availableTarifNumbers(selectedMethod)
    val needsNumero = availableRange != null

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedMethod, selectedNumero) }) {
                Text(stringResource(R.string.validate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        title = { Text(stringResource(R.string.martelage_cubage_method)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TarifMethod.entries.forEach { method ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(GsShape.sm)
                            .background(
                                if (selectedMethod == method) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                else Color.Transparent,
                                GsShape.sm
                            )
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedMethod == method,
                            onClick = {
                                selectedMethod = method
                                if (TarifCalculator.availableTarifNumbers(method) == null) {
                                    selectedNumero = null
                                } else if (selectedNumero == null) {
                                    selectedNumero = TarifCalculator.recommendedTarifNumero(method, "HETRE_COMMUN")
                                }
                            }
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(method.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (method.entrees == 1) "1 entrée (D seul)" else "2 entrées (D + H)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (needsNumero && availableRange != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        stringResource(R.string.settings_tarif_numero_label),
                        style = MaterialTheme.typography.titleSmall
                    )
                    var numeroInput by remember(selectedMethod) {
                        mutableStateOf(selectedNumero?.toString() ?: "")
                    }
                    OutlinedTextField(
                        value = numeroInput,
                        onValueChange = { v ->
                            numeroInput = v
                            selectedNumero = v.toIntOrNull()?.coerceIn(availableRange)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("${availableRange.first}–${availableRange.last}") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true
                    )
                }
            }
        }
    )
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
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = Space.xxs))
                Text(
                    "Chaque export conserve le statut de validation, les avertissements, les unités et la méthode utilisée — jamais retirés pour la lisibilité.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
