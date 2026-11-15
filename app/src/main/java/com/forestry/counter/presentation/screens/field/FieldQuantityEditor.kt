package com.forestry.counter.presentation.screens.field

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.forestry.counter.R
import com.forestry.counter.domain.field.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneId

/** Presentation of the shared quantity form; the model and repository still validate every write. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FieldQuantityEditor(
    form: FieldEntryForm,
    entries: List<FieldEntry>,
    enabled: Boolean,
    showValidation: Boolean,
    change: (FieldEntryForm.() -> FieldEntryForm) -> Unit,
    photos: @Composable () -> Unit,
) {
    var panel by rememberSaveable(form.kind, form.correctionOf) { mutableStateOf<String?>(null) }
    val accent = fieldQuantityPalette(form.kind)
    val remaining = form.kind == FieldEntryKind.REMAINING
    val unknown = remaining && form.remainder == FieldRemainder.UNKNOWN
    val ledgers = fieldLedgerChoices(entries)
    val quantityError = showValidation && !unknown && !quantityLooksValid(form)
    val dateError = showValidation && !dateLooksValid(form)
    val ledgerError = showValidation && !unknown && (form.unit == null || form.convention.isBlank() || form.convention.length > 500)
    val detailsError = showValidation && (form.title.isBlank() || form.title.length > 200 || form.notes.length > 5000 ||
        listOf(form.counterparty, form.destination, form.documentReference, form.passageReference).any { it.length > 500 } ||
        !coordinatesLookValid(form) ||
        form.correctionOf != null && (form.correctionReason.isBlank() || form.correctionReason.length > 500))

    // Reveal hidden invalid fields once. Clearing the VM error on typing must not close or reopen a sheet.
    LaunchedEffect(showValidation) {
        if (showValidation) {
            panel = when {
                detailsError -> DETAILS
                dateError -> DATE
                remaining && form.remainder == null -> REMAINDER
                ledgerError -> LEDGER
                else -> panel
            }
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (remaining) Text(stringResource(R.string.field_q_control_no_adjustment),
            modifier = Modifier.testTag("field_control_no_adjustment"),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (remaining) {
            QuantityHeading(R.string.field_remainder, "remainder", { panel = HELP_REMAINDER })
            OutlinedButton(
                onClick = { panel = REMAINDER },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("field_remainder"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent.ink, containerColor = accent.surface),
                border = BorderStroke(1.dp,
                    if (showValidation && form.remainder == null) MaterialTheme.colorScheme.error else accent.ink.copy(alpha = .55f)),
            ) {
                Text(form.remainder?.let { quantityRemainderLabel(it) } ?: stringResource(R.string.field_q_choose),
                    Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Icon(Icons.Default.ExpandMore, null)
            }
        }

        if (!unknown) {
            QuantityHeading(quantityLabel(form.kind), "quantity", { panel = HELP_QUANTITY })
            OutlinedTextField(
                value = form.quantity,
                onValueChange = { value -> change { copy(quantity = value) } },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp).testTag("field_quantity"),
                placeholder = { Text(stringResource(R.string.field_q_quantity_placeholder), style = MaterialTheme.typography.bodyLarge) },
                suffix = { form.unit?.let { Text(unitLabel(it), style = MaterialTheme.typography.bodyMedium) } },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                shape = RoundedCornerShape(16.dp),
                isError = quantityError,
                supportingText = if (quantityError) {{ Text(stringResource(R.string.field_q_error_quantity)) }} else null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = accent.surface,
                    unfocusedContainerColor = accent.surface,
                    disabledContainerColor = accent.surface,
                    focusedTextColor = accent.ink,
                    unfocusedTextColor = accent.ink,
                    focusedBorderColor = accent.ink,
                    unfocusedBorderColor = accent.ink.copy(alpha = .6f),
                    cursorColor = accent.ink,
                ),
            )
        }

        QuantityOpenRow(
            title = dateLabel(form.kind),
            value = form.observed.ifBlank { stringResource(R.string.field_q_required) },
            detail = form.zone,
            icon = Icons.Default.CalendarToday,
            tag = "field_quantity_date",
            isError = dateError,
            onOpen = { panel = DATE },
        )
        if (!unknown) {
            val ledgerSummary = form.unit?.let { unit ->
                val label = unitLabel(unit)
                if (ledgers.size > 1 && form.convention.isNotBlank()) "$label · ${form.convention}" else label
            } ?: stringResource(R.string.field_q_required)
            QuantityOpenRow(
                title = R.string.field_q_ledger,
                value = ledgerSummary,
                detail = if (form.unit != null && form.convention.isBlank()) stringResource(R.string.field_q_convention_missing) else "",
                icon = Icons.Default.Layers,
                tag = "field_quantity_ledger",
                isError = ledgerError,
                onOpen = { panel = LEDGER },
            )
        }
        QuantityOpenRow(
            title = R.string.field_q_details,
            value = stringResource(if (detailsError) R.string.field_q_details_invalid else R.string.field_q_details_summary),
            icon = Icons.Default.Description,
            tag = "field_quantity_details",
            isError = detailsError,
            onOpen = { panel = DETAILS },
        )
    }

    // A frozen/retrying editor remains readable. Its owner removes this entire component on access loss.
    val activePanel = panel
    if (activePanel != null) {
        val density = LocalDensity.current
        val maximumHeight = LocalConfiguration.current.screenHeightDp.dp * .9f
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val close = { focus.clearFocus(); keyboard?.hide(); panel = null }
        ModalBottomSheet(
            onDismissRequest = close,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            modifier = Modifier.testTag("field_quantity_sheet"),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            CompositionLocalProvider(LocalDensity provides density) {
                Column(Modifier.fillMaxWidth().heightIn(max = maximumHeight).imePadding()
                    .navigationBarsPadding().padding(horizontal = 20.dp)) {
                    Text(stringResource(panelTitle(activePanel, form.kind)),
                        Modifier.fillMaxWidth().padding(bottom = 16.dp).semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (activePanel) {
                            DATE -> {
                                QuantityPanelField(R.string.field_date, form.observed, enabled, "field_date",
                                    if (dateError) R.string.field_q_error_date else null) { value -> change { copy(observed = value) } }
                                QuantityPanelField(R.string.field_zone, form.zone, enabled, "field_zone",
                                    if (dateError) R.string.field_q_error_date else null) { value -> change { copy(zone = value) } }
                                val zone = runCatching { ZoneId.of(form.zone) }.getOrNull()
                                TextButton(onClick = {
                                    if (zone != null) change { copy(observed = LocalDateTime.now(zone).format(FieldEntryForm.dateFormat)) }
                                }, enabled = enabled && zone != null, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text(stringResource(R.string.field_now))
                                }
                                Text(stringResource(R.string.field_q_help_date), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            LEDGER -> {
                                if (form.kind != FieldEntryKind.INITIAL_STOCK && ledgers.isNotEmpty()) {
                                    FieldChoice(R.string.field_ledger_existing,
                                        form.unit?.let { it to form.convention }, ledgers,
                                        { "${unitLabel(it.first)} · ${it.second}" }, enabled && form.correctionOf == null) { selected ->
                                        change { copy(unit = selected.first, convention = selected.second) }
                                    }
                                }
                                FieldChoice(R.string.field_unit, form.unit, FieldQuantityUnit.entries, { unitLabel(it) }, enabled) { selected ->
                                    change { copy(unit = selected) }
                                }
                                QuantityPanelField(R.string.field_convention, form.convention, enabled, "field_convention",
                                    if (ledgerError && (form.convention.isBlank() || form.convention.length > 500)) R.string.field_q_error_convention else null) { value ->
                                    change { copy(convention = value) }
                                }
                                Text(stringResource(R.string.field_q_help_ledger), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DETAILS -> {
                                FieldChoice(R.string.field_type, form.kind, FieldEntryKind.entries, { kindLabel(it) },
                                    enabled && form.correctionOf == null) { selected -> change { selectFieldEntryKind(selected) } }
                                QuantityPanelField(R.string.field_name, form.title, enabled, "field_name",
                                    if (showValidation && (form.title.isBlank() || form.title.length > 200)) R.string.field_q_error_title else null) { value ->
                                    change { copy(title = value) }
                                }
                                if (form.correctionOf != null) {
                                    QuantityPanelField(R.string.field_reason, form.correctionReason, enabled, "field_correction_reason",
                                        if (showValidation && (form.correctionReason.isBlank() || form.correctionReason.length > 500)) R.string.field_q_error_reason else null) { value ->
                                        change { copy(correctionReason = value) }
                                    }
                                }
                                FieldText(R.string.field_counterparty, form.counterparty, enabled) { value -> change { copy(counterparty = value) } }
                                FieldText(R.string.field_destination, form.destination, enabled) { value -> change { copy(destination = value) } }
                                FieldText(R.string.field_document, form.documentReference, enabled) { value -> change { copy(documentReference = value) } }
                                FieldText(R.string.field_passage, form.passageReference, enabled) { value -> change { copy(passageReference = value) } }
                                FieldText(R.string.field_notes, form.notes, enabled) { value -> change { copy(notes = value) } }
                                Text(stringResource(R.string.field_location_manual), style = MaterialTheme.typography.labelLarge)
                                FieldText(R.string.field_lat, form.latitude, enabled) { value ->
                                    change { copy(latitude = value, locationSource = "MANUAL", accuracyM = "") }
                                }
                                FieldText(R.string.field_lon, form.longitude, enabled) { value ->
                                    change { copy(longitude = value, locationSource = "MANUAL", accuracyM = "") }
                                }
                                photos()
                            }
                            REMAINDER -> FieldRemainder.entries.forEach { candidate ->
                                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                    .selectable(selected = form.remainder == candidate, enabled = enabled, role = Role.RadioButton,
                                        onClick = {
                                            change { copy(remainder = candidate, quantity = when (candidate) {
                                                FieldRemainder.UNKNOWN -> ""
                                                FieldRemainder.EMPTY -> "0"
                                                else -> quantity
                                            }) }
                                            panel = null
                                        }).padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = form.remainder == candidate, onClick = null, enabled = enabled)
                                    Text(quantityRemainderLabel(candidate), Modifier.weight(1f).padding(start = 12.dp))
                                }
                            }
                            HELP_REMAINDER -> Text(stringResource(R.string.field_q_help_remainder))
                            HELP_QUANTITY -> Text(stringResource(quantityHelp(form.kind)))
                        }
                    }
                    Button(onClick = close, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .padding(bottom = 12.dp).testTag("field_quantity_sheet_done")) {
                        Text(stringResource(R.string.field_q_sheet_done))
                    }
                }
            }
        }
    }
}

private const val DATE = "date"
private const val LEDGER = "ledger"
private const val DETAILS = "details"
private const val REMAINDER = "remainder"
private const val HELP_QUANTITY = "help_quantity"
private const val HELP_REMAINDER = "help_remainder"

internal data class FieldQuantityPalette(
    val ink: Color,
    val surface: Color,
    val stockInk: Color,
    val stockSurface: Color,
    val stockOutline: Color,
)

@Composable
internal fun fieldQuantityPalette(kind: FieldEntryKind): FieldQuantityPalette {
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    val (ink, surface) = when (kind) {
        FieldEntryKind.REMOVAL -> if (dark) Color(0xFFFFBAA1) to Color(0xFF3E2B25)
            else Color(0xFF80513D) to Color(0xFFF5EBE5)
        FieldEntryKind.REMAINING -> if (dark) Color(0xFFF0CD7F) to Color(0xFF3D3320)
            else Color(0xFF7A5B27) to Color(0xFFF8F0DD)
        else -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer.copy(alpha = .35f)
    }
    return FieldQuantityPalette(ink, surface,
        stockInk = if (dark) Color(0xFFAECFE0) else Color(0xFF355C70),
        stockSurface = if (dark) Color(0xFF20333D) else Color(0xFFEEF3F6),
        stockOutline = if (dark) Color(0xFF517487) else Color(0xFFCBDDE7))
}

@Composable
private fun QuantityHeading(@StringRes label: Int, helpTag: String, onHelp: () -> Unit) {
    val title = stringResource(label)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold)
        IconButton(onClick = onHelp, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .testTag("field_quantity_help_$helpTag")) {
            Icon(Icons.Outlined.Info, stringResource(R.string.field_q_help_open, title), Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuantityOpenRow(
    @StringRes title: Int,
    value: String,
    icon: ImageVector,
    tag: String,
    onOpen: () -> Unit,
    detail: String = "",
    isError: Boolean = false,
) {
    OutlinedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth().testTag(tag),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(value, style = MaterialTheme.typography.bodyMedium,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun QuantityPanelField(@StringRes label: Int, value: String, enabled: Boolean, tag: String,
    @StringRes error: Int? = null, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, enabled = enabled, label = { Text(stringResource(label)) },
        modifier = Modifier.fillMaxWidth().testTag(tag), shape = RoundedCornerShape(12.dp),
        isError = error != null,
        supportingText = if (error != null) {{ Text(stringResource(error)) }} else null)
}

@Composable
private fun quantityRemainderLabel(remainder: FieldRemainder): String = stringResource(when (remainder) {
    FieldRemainder.PARTIAL -> R.string.field_partial
    FieldRemainder.BOTTOM -> R.string.field_bottom
    FieldRemainder.EMPTY -> R.string.field_empty
    FieldRemainder.UNKNOWN -> R.string.field_unknown
})

@StringRes private fun quantityLabel(kind: FieldEntryKind): Int = when (kind) {
    FieldEntryKind.INITIAL_STOCK -> R.string.field_q_quantity_initial
    FieldEntryKind.REMOVAL -> R.string.field_q_quantity_removal
    FieldEntryKind.REMAINING -> R.string.field_q_quantity_remaining
    else -> R.string.field_quantity
}

@StringRes private fun dateLabel(kind: FieldEntryKind): Int = when (kind) {
    FieldEntryKind.INITIAL_STOCK -> R.string.field_q_date_initial
    FieldEntryKind.REMOVAL -> R.string.field_q_date_removal
    FieldEntryKind.REMAINING -> R.string.field_q_date_remaining
    else -> R.string.field_date
}

@StringRes private fun quantityHelp(kind: FieldEntryKind): Int = when (kind) {
    FieldEntryKind.INITIAL_STOCK -> R.string.field_q_help_initial
    FieldEntryKind.REMOVAL -> R.string.field_q_help_removal
    FieldEntryKind.REMAINING -> R.string.field_q_help_observed
    else -> R.string.field_stock_help
}

@StringRes private fun panelTitle(panel: String, kind: FieldEntryKind): Int = when (panel) {
    DATE -> dateLabel(kind)
    LEDGER -> R.string.field_q_ledger
    DETAILS -> R.string.field_q_details
    REMAINDER, HELP_REMAINDER -> R.string.field_remainder
    else -> quantityLabel(kind)
}

/** Only determines which UI fields to highlight; it is never a replacement for model validation. */
private fun dateLooksValid(form: FieldEntryForm): Boolean = form.observed.isNotBlank() &&
    form.observed.length <= 32 && form.zone.length <= 100 && runCatching { form.observedInstant() }.getOrNull() != null

private fun coordinatesLookValid(form: FieldEntryForm): Boolean {
    if (form.latitude.isBlank() && form.longitude.isBlank()) return true
    return runCatching {
        FieldEntryForm.decimal(form.latitude) in BigDecimal("-90")..BigDecimal("90") &&
            FieldEntryForm.decimal(form.longitude) in BigDecimal("-180")..BigDecimal("180")
    }.getOrDefault(false)
}

private fun quantityLooksValid(form: FieldEntryForm): Boolean {
    val value = runCatching { FieldEntryForm.decimal(form.quantity) }.getOrNull() ?: return false
    if (value < BigDecimal.ZERO || form.unit == FieldQuantityUnit.PIECE && value.stripTrailingZeros().scale() > 0) return false
    return when (form.kind) {
        FieldEntryKind.REMOVAL, FieldEntryKind.INCOMING -> value > BigDecimal.ZERO
        FieldEntryKind.REMAINING -> if (form.remainder == FieldRemainder.EMPTY) value.compareTo(BigDecimal.ZERO) == 0 else value > BigDecimal.ZERO
        else -> true
    }
}
