package com.forestry.counter.presentation.screens.field

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.forestry.counter.BuildConfig
import com.forestry.counter.R
import com.forestry.counter.domain.field.*
import com.forestry.counter.presentation.components.maps.MapboxRenderStatusOverlay
import com.forestry.counter.presentation.screens.maps.MapsRoutePresentationTheme
import com.forestry.counter.presentation.screens.maps.MapsRouteUi
import com.mapbox.geojson.Point
import com.quintessences.geosylva.cartography.mapbox.*
import java.util.UUID

/** Maps 15 opts in explicitly; every other journal keeps its existing presentation. */
@Composable
internal fun MapsAccessEntryPresentation(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    MapsRoutePresentationTheme(enabled = true) {
        val parent = MaterialTheme.colorScheme
        val scheme = remember(parent) { parent.copy(
            background = Color.White, onBackground = MapsRouteUi.ink,
            surface = Color.White, onSurface = MapsRouteUi.ink,
            surfaceDim = Color.White, surfaceBright = Color.White, surfaceTint = Color.Transparent,
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
            surfaceContainer = Color.White, surfaceContainerHigh = Color.White,
            surfaceContainerHighest = Color.White,
        ) }
        MaterialTheme(colorScheme = scheme) {
            CompositionLocalProvider(LocalContentColor provides MapsRouteUi.ink, content = content)
        }
    }
}

internal val FIELD_MAPS_ACCESS_POINT_KEY = MapFeatureKey("field-maps-access", "selected-point")

/** Native callbacks remain observable by the host; they never acknowledge a synthetic map image. */
internal data class FieldMapsAccessMapObserver(
    val status: (MapRenderStatus) -> Unit = {},
    val overlays: (MapOverlayStatus) -> Unit = {},
)

/** Same native renderer, scoped to the authorized journal target. No route or observer fix is fabricated. */
@Composable
private fun FieldMapsAccessMapPreview(
    target: FieldTarget,
    context: FieldMapsAccessContext,
    observer: FieldMapsAccessMapObserver,
    reload: Int,
    modifier: Modifier = Modifier,
) {
    val scope = remember(target, context) { "field-maps-access-${UUID.randomUUID()}" }
    val scene = remember(scope, context) {
        MapOverlayScene(scope, 1, listOf(MapOverlayFeature(FIELD_MAPS_ACCESS_POINT_KEY,
            Point.fromLngLat(context.longitude, context.latitude),
            MapOverlayStyle(colorHex = "#006CFF", pointRadius = 9.0, pointSymbol = MapOverlayPointSymbol.PIN,
                strokeWidth = 1.5, strokeColorHex = "#FFFFFF"), selectable = false)))
    }
    Box(modifier.clip(RoundedCornerShape(14.dp)).testTag("field_maps_access_map")) {
        key(scope, reload) {
            SharedMapboxCanvas(
                accessToken = BuildConfig.MAPBOX_PUBLIC_ACCESS_TOKEN,
                style = MapboxStyles.Satellite,
                initialCamera = MapCameraPosition(context.longitude, context.latitude, 15.0, pitch = 35.0),
                modifier = Modifier.fillMaxSize(), overlays = scene,
                relief = MapRelief(enabled = true), scaleBarVisible = false,
                // Leave room for the SDK's expanded native attribution touch target.
                contentPadding = PaddingValues(start = 4.dp, top = 4.dp, end = 4.dp, bottom = 16.dp),
                onStatusChanged = observer.status,
                onOverlayStatusChanged = observer.overlays,
            )
        }
    }
}

@Composable
internal fun MapsAccessContextDetails(context: FieldMapsAccessContext) {
    Text(context.label, style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.retained_maps_coordinates, context.longitude.toString(), context.latitude.toString()),
        style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.maps_access_point_help), style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun MapsAccessLabel(label: Int, help: Int) {
    var showing by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        IconButton({ showing = true }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Info, stringResource(R.string.maps_access_explain, stringResource(label)), Modifier.size(19.dp))
        }
    }
    if (showing) AlertDialog(onDismissRequest = { showing = false },
        title = { Text(stringResource(label)) }, text = { Text(stringResource(help)) },
        confirmButton = { TextButton({ showing = false }) { Text(stringResource(R.string.retained_close)) } })
}

@Composable
private fun MapsAccessContextCard(
    target: FieldTarget, targetTitle: String, context: FieldMapsAccessContext,
    saved: Boolean, observer: FieldMapsAccessMapObserver,
) {
    var details by remember { mutableStateOf(false) }
    var reload by remember(target, context) { mutableStateOf(0) }
    var status by remember(target, context) { mutableStateOf<MapRenderStatus>(MapRenderStatus.Loading("satellite")) }
    var overlayFailed by remember(target, context) { mutableStateOf(false) }
    val previewObserver = FieldMapsAccessMapObserver(
        status = { status = it; observer.status(it) },
        overlays = {
            observer.overlays(it)
            overlayFailed = when (it) {
                is MapOverlayStatus.Failed -> true
                is MapOverlayStatus.Applied -> false
                is MapOverlayStatus.Pending -> overlayFailed
            }
        },
    )
    OutlinedCard(shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MapsRouteUi.border),
        modifier = Modifier.fillMaxWidth().testTag("field_maps_access_context")) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Shared attribution starts 96dp after the logo and keeps its native 48dp touch target.
            // 160dp also reserves both horizontal insets; retain the approved thumbnail aspect ratio.
            FieldMapsAccessMapPreview(target, context, previewObserver, reload, Modifier.width(160.dp).height(100.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(targetTitle, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    IconButton({ details = true }, modifier = Modifier.size(48.dp).testTag("field_maps_access_context_info")) {
                        Icon(Icons.Default.Info, stringResource(R.string.maps_access_point_label), Modifier.size(20.dp))
                    }
                }
                Text(context.label, style = MaterialTheme.typography.bodyMedium, color = MapsRouteUi.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(if (saved) R.string.field_form_draft_saved else R.string.field_draft_pending),
                    style = MaterialTheme.typography.labelSmall, color = MapsRouteUi.secondary)
            }
        }
        MapboxRenderStatusOverlay(
            status = if (overlayFailed) MapRenderStatus.Failed(MapFailureReason.SOURCE) else status,
            onRetry = { overlayFailed = false; status = MapRenderStatus.Loading("satellite"); reload++ },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (details) AlertDialog(onDismissRequest = { details = false },
        title = { Text(stringResource(R.string.maps_access_point_label)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(targetTitle, style = MaterialTheme.typography.titleMedium)
            MapsAccessContextDetails(context)
        } },
        confirmButton = { TextButton({ details = false }, modifier = Modifier.testTag("field_maps_access_context_close")) {
            Text(stringResource(R.string.retained_close))
        } })
}

@Composable
private fun MapsAccessEditableRow(icon: ImageVector, label: Int, help: Int, value: String, enabled: Boolean, tag: String, onEdit: () -> Unit) {
    HorizontalDivider(color = MapsRouteUi.border)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(28.dp), tint = MapsRouteUi.blue)
        Spacer(Modifier.width(10.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            MapsAccessLabel(label, help)
        }
        TextButton(onEdit, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).testTag(tag)) {
            Text(value.ifBlank { stringResource(R.string.maps_access_fill) })
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

/** Shared draft, save, photo and access guards; only the arrangement of the ACCESS fields changes. */
@Composable
internal fun MapsAccessEntryEditor(
    form: FieldEntryForm, target: FieldTarget, targetTitle: String, entries: List<FieldEntry>,
    saved: Boolean, enabled: Boolean, change: (FieldEntryForm.() -> FieldEntryForm) -> Unit,
    observer: FieldMapsAccessMapObserver, photos: @Composable () -> Unit,
) {
    val context = checkNotNull(form.mapsAccessContext)
    val titleDescription = stringResource(R.string.maps_access_form_title)
    val conditionDescription = stringResource(R.string.maps_access_condition)
    var editVehicle by remember { mutableStateOf(false) }
    var editDate by remember { mutableStateOf(false) }
    var more by remember(form.correctionOf) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MapsAccessContextCard(target, targetTitle, context, saved, observer)
        form.correctionOf?.let { originalId ->
            entries.firstOrNull { it.id == originalId }?.let { original ->
                OutlinedCard {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.retained_original_entry), style = MaterialTheme.typography.labelLarge)
                        Text(original.form.title); Text(original.form.notes)
                        original.form.mapsAccessContext?.let { MapsAccessContextDetails(it) }
                    }
                }
            }
        }
        Column {
            MapsAccessLabel(R.string.maps_access_form_title, R.string.maps_access_form_title_help)
            OutlinedTextField(form.title, { value -> change { copy(title = value) } }, enabled = enabled,
                modifier = Modifier.fillMaxWidth().testTag("field_name").semantics { contentDescription = titleDescription }, singleLine = true,
                shape = RoundedCornerShape(12.dp))
        }
        Column {
            MapsAccessLabel(R.string.maps_access_condition, R.string.maps_access_condition_help)
            OutlinedTextField(form.notes, { value -> change { copy(notes = value) } }, enabled = enabled,
                modifier = Modifier.fillMaxWidth().testTag("field_notes").semantics { contentDescription = conditionDescription }, minLines = 2,
                shape = RoundedCornerShape(12.dp))
        }
        MapsAccessEditableRow(Icons.Default.DirectionsCar, R.string.maps_access_vehicle, R.string.maps_access_vehicle_help,
            form.vehicle, enabled, "field_maps_access_vehicle") { editVehicle = true }
        MapsAccessEditableRow(Icons.Default.CalendarMonth, R.string.maps_access_date, R.string.retained_time_help,
            form.observed, enabled, "field_maps_access_date") { editDate = true }
        HorizontalDivider(color = MapsRouteUi.border)
        photos()
        HorizontalDivider(color = MapsRouteUi.border)
        TextButton({ more = !more }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("field_other_info")) {
            Text(stringResource(R.string.maps_access_extras), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
        if (more) {
            FieldText(R.string.field_restriction, form.restriction, enabled) { change { copy(restriction = it) } }
            Text(stringResource(R.string.field_location_manual), style = MaterialTheme.typography.labelMedium)
            FieldText(R.string.field_lat, form.latitude, enabled) { change { copy(latitude = it, locationSource = "MANUAL", accuracyM = "") } }
            FieldText(R.string.field_lon, form.longitude, enabled) { change { copy(longitude = it, locationSource = "MANUAL", accuracyM = "") } }
            MapsAccessContextDetails(context)
        }
        if (form.correctionOf != null) {
            FieldText(R.string.field_reason, form.correctionReason, enabled, tag = "field_correction_reason") { change { copy(correctionReason = it) } }
            Text(stringResource(R.string.retained_kept_original), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (editVehicle) AlertDialog(onDismissRequest = { editVehicle = false },
        title = { Text(stringResource(R.string.maps_access_vehicle)) },
        text = { FieldText(R.string.field_vehicle, form.vehicle, enabled, tag = "field_vehicle") { change { copy(vehicle = it) } } },
        confirmButton = { TextButton({ editVehicle = false }) { Text(stringResource(R.string.retained_close)) } })
    if (editDate) AlertDialog(onDismissRequest = { editDate = false },
        title = { Text(stringResource(R.string.retained_date)) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val parts = form.observed.split(' ', limit = 2)
                val date = parts.getOrElse(0) { "" }; val time = parts.getOrElse(1) { "" }
                OutlinedTextField(date, { value -> change { copy(observed = joinedObservedDate(value, time)) } }, enabled = enabled,
                    label = { Text(stringResource(R.string.retained_date_part)) }, modifier = Modifier.fillMaxWidth().testTag("field_observed_date"))
                OutlinedTextField(time, { value -> change { copy(observed = joinedObservedDate(date, value)) } }, enabled = enabled,
                    label = { Text(stringResource(R.string.retained_time_part)) }, modifier = Modifier.fillMaxWidth().testTag("field_observed_time"))
                FieldText(R.string.field_zone, form.zone, enabled) { change { copy(zone = it) } }
            }
        }, confirmButton = { TextButton({ editDate = false }) { Text(stringResource(R.string.retained_close)) } })
}
