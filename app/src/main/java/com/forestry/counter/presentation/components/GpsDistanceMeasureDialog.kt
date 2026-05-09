package com.forestry.counter.presentation.components

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.forestry.counter.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Mini-dialogue 2 étapes pour mesurer la distance avec le GPS :
 *  1. Marcher jusqu'au pied de l'arbre → enregistrer position 1
 *  2. Revenir au point de mesure → enregistrer position 2
 *  3. Calcul automatique de la distance horizontale
 */
@Composable
fun GpsDistanceMeasureDialog(
    onResult: (distanceM: Double) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val hasPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    var pos1 by remember { mutableStateOf<Location?>(null) }
    var pos2 by remember { mutableStateOf<Location?>(null) }
    var loading by remember { mutableStateOf(false) }
    var accuracy by remember { mutableStateOf<Float?>(null) }

    val step = when {
        pos1 == null -> 1
        pos2 == null -> 2
        else -> 3
    }

    val distanceM: Double? = remember(pos1, pos2) {
        val p1 = pos1; val p2 = pos2
        if (p1 != null && p2 != null) {
            val results = FloatArray(1)
            Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results)
            results[0].toDouble()
        } else null
    }

    fun requestLocation(onLocation: (Location) -> Unit) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        loading = true
        accuracy = null
        val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) { loading = false; return }

        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                accuracy = loc.accuracy
                if (loc.accuracy <= 15f) {
                    loading = false
                    onLocation(loc)
                    lm.removeUpdates(this)
                }
            }
            @Deprecated("Deprecated in API 29")
            override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
        }
        lm.requestLocationUpdates(provider, 0L, 0f, listener)

        scope.launch {
            delay(15_000L) // timeout 15 s
            if (loading) {
                loading = false
                // Fallback : prendre la dernière position connue
                val last = lm.getLastKnownLocation(provider)
                if (last != null) {
                    accuracy = last.accuracy
                    onLocation(last)
                }
                lm.removeUpdates(listener)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.GpsFixed, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.gps_distance_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!hasPermission) {
                    Text(
                        stringResource(R.string.gps_distance_no_permission),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    return@Column
                }

                // Indicateur étapes
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StepDot(done = pos1 != null, active = step == 1, number = 1)
                    Divider(modifier = Modifier.width(20.dp).height(1.dp))
                    StepDot(done = pos2 != null, active = step == 2, number = 2)
                    Divider(modifier = Modifier.width(20.dp).height(1.dp))
                    StepDot(done = distanceM != null, active = step == 3, number = 3)
                }

                // Description de l'étape courante
                val description = when (step) {
                    1 -> stringResource(R.string.gps_distance_step1)
                    2 -> stringResource(R.string.gps_distance_step2)
                    else -> null
                }
                description?.let {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (step == 1) Icons.Default.Forest else Icons.Default.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Résultat distance
                distanceM?.let { d ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                stringResource(R.string.gps_distance_result_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                String.format("%.1f m", d),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Précision GPS
                accuracy?.let { acc ->
                    Text(
                        stringResource(R.string.gps_distance_accuracy, acc.toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (acc <= 10f) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Indicateur de chargement GPS
                if (loading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            stringResource(R.string.gps_distance_waiting),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Bouton enregistrer position
                if (step in 1..2 && !loading) {
                    FilledTonalButton(
                        onClick = {
                            if (step == 1) requestLocation { pos1 = it }
                            else requestLocation { pos2 = it }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            if (step == 1) Icons.Default.Forest else Icons.Default.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (step == 1) stringResource(R.string.gps_distance_record1)
                            else stringResource(R.string.gps_distance_record2)
                        )
                    }
                }

                // Recap positions
                if (pos1 != null) {
                    Text(
                        "📍 " + stringResource(R.string.gps_distance_pos1_recorded,
                            String.format("%.5f", pos1!!.latitude),
                            String.format("%.5f", pos1!!.longitude)),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2E7D32)
                    )
                }
            }
        },
        confirmButton = {
            if (distanceM != null) {
                Button(onClick = { onResult(distanceM) }) {
                    Text(stringResource(R.string.gps_distance_use))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun StepDot(done: Boolean, active: Boolean, number: Int) {
    val color = when {
        done   -> Color(0xFF2E7D32)
        active -> MaterialTheme.colorScheme.primary
        else   -> MaterialTheme.colorScheme.outline
    }
    Surface(
        shape = androidx.compose.foundation.shape.CircleShape,
        color = if (done || active) color else color.copy(alpha = 0.2f),
        modifier = Modifier.size(24.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (done) Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp), tint = Color.White)
            else Text("$number", style = MaterialTheme.typography.labelSmall, color = if (active) Color.White else color)
        }
    }
}
