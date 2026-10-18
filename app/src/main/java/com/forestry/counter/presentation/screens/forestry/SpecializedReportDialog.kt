package com.forestry.counter.presentation.screens.forestry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.forestry.counter.R
import com.forestry.counter.domain.usecase.export.IllustratedSynthesisDocument
import com.forestry.counter.domain.usecase.export.SpecializedCostBasis
import com.forestry.counter.domain.usecase.export.SpecializedCostLine
import com.forestry.counter.domain.usecase.export.SpecializedCostUnit
import com.forestry.counter.domain.usecase.export.SpecializedReportType
import com.forestry.counter.domain.usecase.export.SpecializedSynthesisDocument
import com.forestry.counter.domain.usecase.export.buildSpecializedCostData
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

/** Transient user input; it never writes prices, operations or costs into a martelage snapshot. */
internal data class SpecializedCostDraft(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val quantity: String = "",
    val unit: SpecializedCostUnit = SpecializedCostUnit.ITEM,
    val unitPriceEur: String = "",
    val provenance: String = "",
    val priceDate: String = "",
    val basis: SpecializedCostBasis = SpecializedCostBasis.USER_ESTIMATE,
) {
    fun toCostLineOrNull(): SpecializedCostLine? = runCatching {
        SpecializedCostLine(
            id = id,
            label = label.trim(),
            quantity = if (unit == SpecializedCostUnit.FIXED_JOB) "1" else specializedFormDecimal(quantity),
            unit = unit,
            unitPriceEur = specializedFormDecimal(unitPriceEur),
            provenance = provenance.trim(),
            priceDate = priceDate.trim().takeIf(String::isNotEmpty),
            basis = basis,
        )
    }.getOrNull()
}

/** A comma is accepted as a decimal separator; an empty field remains unknown, never zero. */
internal fun specializedFormDecimal(value: String): String? = value.trim().replace(',', '.')
    .takeIf(String::isNotEmpty)

/** Caller owns the captured read guard and destroys this dialog when its scope changes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpecializedReportDialog(
    illustrated: IllustratedSynthesisDocument,
    onDismiss: () -> Unit,
    onExport: (SpecializedSynthesisDocument) -> Unit,
) {
    var selected by remember(illustrated) { mutableStateOf<SpecializedReportType?>(null) }
    var drafts by remember(illustrated) { mutableStateOf(emptyList<SpecializedCostDraft>()) }
    val scroll = rememberScrollState()
    LaunchedEffect(selected) { scroll.scrollTo(0) }
    val report = illustrated.document.report
    val type = selected
    val lines = drafts.mapNotNull(SpecializedCostDraft::toCostLineOrNull)
    val input = remember(illustrated, selected, drafts) {
        if (type == null || (type == SpecializedReportType.COSTS && lines.size != drafts.size)) null
        else runCatching { SpecializedSynthesisDocument(type, illustrated,
            if (type == SpecializedReportType.COSTS) lines else emptyList()) }.getOrNull()
    }
    val costs = remember(illustrated, selected, drafts) {
        if (type != SpecializedReportType.COSTS) null else runCatching {
            buildSpecializedCostData(SpecializedSynthesisDocument(type, illustrated, lines))
        }.getOrNull()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false,
        dismissOnClickOutside = false,
    )) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Scaffold(
                modifier = Modifier.imePadding(),
                containerColor = MaterialTheme.colorScheme.surface,
                topBar = {
                    TopAppBar(title = { Text(stringResource(R.string.specialized_form_title),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
                        navigationIcon = { IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        } })
                },
                bottomBar = {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider()
                        Text(stringResource(R.string.specialized_form_archive_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { input?.let(onExport) }, enabled = input != null,
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(if (type == null) R.string.specialized_form_choose
                                else R.string.specialized_form_export), Modifier.padding(start = 8.dp, top = 5.dp, bottom = 5.dp))
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 660.dp).fillMaxWidth().verticalScroll(scroll)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (type == null) {
                            Text(stringResource(R.string.specialized_form_heading),
                                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.specialized_form_intro),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(specializedFormTitle(type)), Modifier.weight(1f),
                                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { selected = null }) { Text(stringResource(R.string.specialized_form_change)) }
                            }
                        }
                        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.50f))) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp))
                                    Text(stringResource(R.string.specialized_form_frozen),
                                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                }
                                Text(report.parcelName, style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold)
                                report.selectedPlotName?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                Text(stringResource(R.string.specialized_form_scope,
                                    report.revisionNumber ?: 0, report.observationCount),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (type == null) {
                            SpecializedReportType.entries.forEach { choice -> ReportTypeCard(choice) { selected = choice } }
                        } else {
                            Text(stringResource(specializedFormDescription(type)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            when (type) {
                                SpecializedReportType.REVENUE -> ReportInformationCard(
                                    R.string.specialized_form_revenue_data, R.string.specialized_form_revenue_limits)
                                SpecializedReportType.ENVIRONMENTAL -> ReportInformationCard(
                                    R.string.specialized_form_environment_data, R.string.specialized_form_environment_limits)
                                SpecializedReportType.COSTS -> {
                                    Text(stringResource(R.string.specialized_form_cost_entry_note),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    drafts.forEachIndexed { index, draft -> key(draft.id) {
                                        CostDraftCard(index + 1, draft,
                                            onChange = { updated -> drafts = drafts.map { if (it.id == updated.id) updated else it } },
                                            onDelete = { drafts = drafts.filterNot { it.id == draft.id } })
                                    } }
                                    OutlinedButton(onClick = { drafts = drafts + SpecializedCostDraft() },
                                        enabled = drafts.size < 100, modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp)) {
                                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                        Text(stringResource(R.string.specialized_form_add_cost), Modifier.padding(start = 8.dp))
                                    }
                                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f))) {
                                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(stringResource(R.string.specialized_form_declared_total),
                                                style = MaterialTheme.typography.titleSmall)
                                            Text(costs?.totalCents?.let(::formatSpecializedFormMoney)
                                                ?: stringResource(R.string.specialized_form_unknown),
                                                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                            Text(stringResource(R.string.specialized_form_cost_coverage,
                                                costs?.computedCount ?: 0, drafts.size), style = MaterialTheme.typography.bodySmall)
                                            Text(stringResource(if (input == null && drafts.isNotEmpty())
                                                R.string.specialized_form_cost_invalid else R.string.specialized_form_cost_total_note),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (input == null && drafts.isNotEmpty()) MaterialTheme.colorScheme.error
                                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportTypeCard(type: SpecializedReportType, onChoose: () -> Unit) {
    Card(onClick = onChoose, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f))) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top) {
            Icon(when (type) {
                SpecializedReportType.REVENUE -> Icons.Default.AttachMoney
                SpecializedReportType.COSTS -> Icons.Default.ReceiptLong
                SpecializedReportType.ENVIRONMENTAL -> Icons.Default.Eco
            }, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(specializedFormTitle(type)), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(stringResource(specializedFormDescription(type)), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ReportInformationCard(title: Int, body: Int) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CostDraftCard(number: Int, draft: SpecializedCostDraft,
    onChange: (SpecializedCostDraft) -> Unit, onDelete: () -> Unit) {
    var expandedUnit by remember { mutableStateOf(false) }
    val decimalPattern = remember { Regex("[0-9]{1,18}(?:\\.[0-9]{1,6})?") }
    val quantityInvalid = specializedFormDecimal(draft.quantity)?.let { !decimalPattern.matches(it) } ?: false
    val priceInvalid = specializedFormDecimal(draft.unitPriceEur)?.let { !decimalPattern.matches(it) } ?: false
    val dateInvalid = draft.priceDate.isNotBlank() && runCatching {
        require(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(draft.priceDate.trim()))
        java.time.LocalDate.parse(draft.priceDate.trim())
    }.isFailure
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.specialized_form_cost_number, number),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, stringResource(R.string.specialized_form_remove_cost, number))
                }
            }
            OutlinedTextField(value = draft.label, onValueChange = { onChange(draft.copy(label = it.take(500))) },
                label = { Text(stringResource(R.string.specialized_form_operation)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = if (draft.unit == SpecializedCostUnit.FIXED_JOB) "1" else draft.quantity,
                    onValueChange = { onChange(draft.copy(quantity = it.take(40))) },
                    enabled = draft.unit != SpecializedCostUnit.FIXED_JOB,
                    label = { Text(stringResource(R.string.specialized_form_quantity)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = quantityInvalid,
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp))
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { expandedUnit = true }, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)) {
                        Text(stringResource(specializedFormUnit(draft.unit)), Modifier.padding(vertical = 8.dp))
                    }
                    DropdownMenu(expanded = expandedUnit, onDismissRequest = { expandedUnit = false }) {
                        SpecializedCostUnit.entries.forEach { unit ->
                            DropdownMenuItem(text = { Text(stringResource(specializedFormUnit(unit))) }, onClick = {
                                expandedUnit = false
                                onChange(draft.copy(unit = unit))
                            })
                        }
                    }
                }
            }
            OutlinedTextField(value = draft.unitPriceEur, onValueChange = { onChange(draft.copy(unitPriceEur = it.take(40))) },
                label = { Text(stringResource(R.string.specialized_form_unit_price)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = priceInvalid,
                suffix = { Text("€") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = draft.basis == SpecializedCostBasis.USER_ESTIMATE,
                    onClick = { onChange(draft.copy(basis = SpecializedCostBasis.USER_ESTIMATE)) },
                    label = { Text(stringResource(R.string.specialized_form_estimate)) })
                FilterChip(selected = draft.basis == SpecializedCostBasis.USER_QUOTATION,
                    onClick = { onChange(draft.copy(basis = SpecializedCostBasis.USER_QUOTATION)) },
                    label = { Text(stringResource(R.string.specialized_form_quotation)) })
            }
            OutlinedTextField(value = draft.provenance, onValueChange = { onChange(draft.copy(provenance = it.take(2_000))) },
                label = { Text(stringResource(R.string.specialized_form_provenance)) }, maxLines = 3,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            OutlinedTextField(value = draft.priceDate, onValueChange = { onChange(draft.copy(priceDate = it.take(10))) },
                label = { Text(stringResource(R.string.specialized_form_price_date)) },
                placeholder = { Text(stringResource(R.string.specialized_form_date_placeholder)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), singleLine = true,
                isError = dateInvalid, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            if (quantityInvalid || priceInvalid || dateInvalid) Text(stringResource(R.string.specialized_form_value_invalid),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

internal fun specializedFormTitle(type: SpecializedReportType): Int = when (type) {
    SpecializedReportType.REVENUE -> R.string.specialized_form_revenue
    SpecializedReportType.COSTS -> R.string.specialized_form_costs
    SpecializedReportType.ENVIRONMENTAL -> R.string.specialized_form_environment
}

private fun specializedFormDescription(type: SpecializedReportType): Int = when (type) {
    SpecializedReportType.REVENUE -> R.string.specialized_form_revenue_description
    SpecializedReportType.COSTS -> R.string.specialized_form_costs_description
    SpecializedReportType.ENVIRONMENTAL -> R.string.specialized_form_environment_description
}

private fun specializedFormUnit(unit: SpecializedCostUnit): Int = when (unit) {
    SpecializedCostUnit.HOUR -> R.string.specialized_form_unit_hour
    SpecializedCostUnit.DAY -> R.string.specialized_form_unit_day
    SpecializedCostUnit.CUBIC_METRE -> R.string.specialized_form_unit_volume
    SpecializedCostUnit.HECTARE -> R.string.specialized_form_unit_hectare
    SpecializedCostUnit.KILOMETRE -> R.string.specialized_form_unit_kilometre
    SpecializedCostUnit.ITEM -> R.string.specialized_form_unit_item
    SpecializedCostUnit.FIXED_JOB -> R.string.specialized_form_unit_fixed
}

private fun formatSpecializedFormMoney(cents: Long): String = NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
    currency = java.util.Currency.getInstance("EUR")
}.format(BigDecimal.valueOf(cents, 2))
