package com.forestry.counter.presentation.screens.forestry

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forestry.counter.R
import com.forestry.counter.domain.calculation.RecordedDendrometry
import com.forestry.counter.domain.calculation.RecordedDendrometryStatus
import com.forestry.counter.domain.calculation.SynthesisReport
import java.text.NumberFormat
import java.util.Locale

/** Analysis destinations are uses of a dossier, not account roles or profession permissions. */
enum class SynthesisSection(@StringRes val title: Int, @StringRes val explanation: Int) {
    MANAGEMENT(R.string.analysis_section_management, R.string.analysis_section_management_description),
    WOOD(R.string.analysis_section_wood, R.string.analysis_section_wood_description),
    ECOLOGY(R.string.analysis_section_ecology, R.string.analysis_section_ecology_description),
}

@Composable
fun SynthesisSectionSelector(
    selected: SynthesisSection,
    onSelected: (SynthesisSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectionState = stringResource(R.string.analysis_selected_section, stringResource(selected.title))
    val contentDensity = LocalDensity.current
    val fontScale = contentDensity.fontScale
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.analysis_section_label), modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 600.dp && fontScale <= 1.4f) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SynthesisSection.entries.forEach { section ->
                        val buttonModifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            .testTag(sectionTag(section)).semantics { this.selected = section == selected }
                        if (section == selected) {
                            FilledTonalButton(onClick = { onSelected(section) }, modifier = buttonModifier,
                                shape = RoundedCornerShape(16.dp)) { Text(stringResource(section.title)) }
                        } else {
                            OutlinedButton(onClick = { onSelected(section) }, modifier = buttonModifier,
                                shape = RoundedCornerShape(16.dp)) { Text(stringResource(section.title)) }
                        }
                    }
                }
            } else {
                OutlinedButton(onClick = { expanded = true }, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        .testTag("synthesis_section_picker").semantics { stateDescription = selectionState }) {
                    Text(stringResource(selected.title), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Default.ExpandMore, contentDescription = null)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
                    modifier = Modifier.widthIn(min = 240.dp, max = 420.dp)) {
                    // A popup has its own Android owner; retain the parent's accessible text scale.
                    CompositionLocalProvider(LocalDensity provides contentDensity) {
                        SynthesisSection.entries.forEach { section ->
                            DropdownMenuItem(
                                text = { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(stringResource(section.title), modifier = Modifier.fillMaxWidth(),
                                        fontWeight = FontWeight.SemiBold)
                                    Text(stringResource(section.explanation), modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } },
                                leadingIcon = { Icon(when (section) {
                                    SynthesisSection.MANAGEMENT -> Icons.Default.Forest
                                    SynthesisSection.WOOD -> Icons.Default.BarChart
                                    SynthesisSection.ECOLOGY -> Icons.Default.Eco
                                }, contentDescription = null) },
                                onClick = { expanded = false; onSelected(section) },
                                modifier = Modifier.heightIn(min = 64.dp).testTag(sectionTag(section))
                                    .semantics { this.selected = section == selected },
                            )
                        }
                    }
                }
            }
        }
        Text(stringResource(selected.explanation), modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun sectionTag(section: SynthesisSection) =
    "synthesis_section_" + section.name.lowercase(Locale.ROOT)

/** Display only the verified projection of the selected report; never recover from current rows. */
@Composable
fun DendrometryOverviewCard(
    data: RecordedDendrometry,
    modifier: Modifier = Modifier,
    onShowSources: (() -> Unit)? = null,
) {
    var explained by remember(data) { mutableStateOf<DendrometryMetric?>(null) }
    AnalysisCard(stringResource(R.string.analysis_dendrometry_title),
        modifier.testTag("synthesis_dendrometry_overview")) {
        Text(stringResource(R.string.analysis_recorded_scope), modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (data.status != RecordedDendrometryStatus.AVAILABLE) {
            ProjectionUnavailable()
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 600.dp && LocalDensity.current.fontScale <= 1.4f) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        DendrometryMetric.entries.forEach { metric ->
                            DendrometryMetricValue(metric, data, Modifier.weight(1f)) { explained = metric }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DendrometryMetric.entries.forEachIndexed { index, metric ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            DendrometryMetricValue(metric, data) { explained = metric }
                        }
                    }
                }
            }
            Text(stringResource(R.string.analysis_valid_diameters, integerText(data.validDiameterCount), integerText(data.recordedCount)),
                modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (data.diameterBasisUnknown) {
                Text(stringResource(R.string.analysis_diameter_basis_unknown), modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    explained?.let { metric ->
        DendrometryMetricHelp(
            metric = metric,
            data = data,
            onDismiss = { explained = null },
            onShowSources = onShowSources?.let { openSources ->
                {
                    explained = null
                    openSources()
                }
            },
        )
    }
}

private enum class DendrometryMetric(
    @StringRes val label: Int,
    @StringRes val definition: Int,
    @StringRes val formula: Int,
) {
    COUNT(R.string.analysis_count_label, R.string.analysis_help_count_definition, R.string.analysis_help_count_formula),
    BASAL(R.string.analysis_basal_label, R.string.analysis_help_basal_definition, R.string.analysis_help_basal_formula),
    QUADRATIC(R.string.analysis_dg_label, R.string.analysis_help_quadratic_definition, R.string.analysis_help_quadratic_formula),
}

@Composable
private fun DendrometryMetric.formattedValue(data: RecordedDendrometry): String = when (this) {
    DendrometryMetric.COUNT -> integerText(data.recordedCount)
    DendrometryMetric.BASAL ->
        data.basalAreaM2?.let { stringResource(R.string.analysis_basal_value, formatSynthesisNumber(it, 3)) }
            ?: stringResource(R.string.analysis_missing_value)
    DendrometryMetric.QUADRATIC ->
        data.quadraticDiameterCm?.let { stringResource(R.string.analysis_diameter_value, formatSynthesisNumber(it, 1)) }
            ?: stringResource(R.string.analysis_missing_value)
}

@Composable
private fun DendrometryMetricValue(
    metric: DendrometryMetric,
    data: RecordedDendrometry,
    modifier: Modifier = Modifier,
    onExplain: () -> Unit,
) {
    val label = stringResource(metric.label)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AnalysisMetric(label, metric.formattedValue(data), Modifier.weight(1f))
        IconButton(
            onClick = onExplain,
            modifier = Modifier.heightIn(min = 48.dp).testTag("synthesis_metric_help_${metric.name.lowercase(Locale.ROOT)}"),
        ) {
            Icon(Icons.Outlined.Info, stringResource(R.string.analysis_help_description, label))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DendrometryMetricHelp(
    metric: DendrometryMetric,
    data: RecordedDendrometry,
    onDismiss: () -> Unit,
    onShowSources: (() -> Unit)?,
) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.8f
    val contentDensity = LocalDensity.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        CompositionLocalProvider(LocalDensity provides contentDensity) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp).testTag("synthesis_metric_help_sheet"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(metric.label), Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall)
                Text(metric.formattedValue(data), style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary)
                Text(stringResource(metric.definition))
                Text(stringResource(metric.formula), style = MaterialTheme.typography.titleSmall)
                if (metric != DendrometryMetric.COUNT) {
                    Text(stringResource(R.string.analysis_valid_diameters,
                        integerText(data.validDiameterCount), integerText(data.recordedCount)))
                    if (data.validDiameterCount == 0) Text(stringResource(R.string.analysis_help_no_diameters))
                    if (data.diameterBasisUnknown) Text(stringResource(R.string.analysis_diameter_basis_unknown))
                }
                HorizontalDivider()
                Text(stringResource(R.string.analysis_help_record_source), style = MaterialTheme.typography.bodySmall)
                if (onShowSources != null) {
                    TextButton(
                        onClick = onShowSources,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("synthesis_metric_help_sources"),
                    ) { Text(stringResource(R.string.analysis_help_view_sources)) }
                }
                FilledTonalButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.analysis_protocol_help_close))
                }
            }
        }
    }
}

/** Exact stored diameters are categorical frequencies, not invented rounded inventory bins. */
@Composable
fun DiameterDistributionCard(data: RecordedDendrometry, modifier: Modifier = Modifier) {
    AnalysisCard(stringResource(R.string.analysis_distribution_title),
        modifier.testTag("synthesis_diameter_distribution")) {
        if (data.status != RecordedDendrometryStatus.AVAILABLE) {
            ProjectionUnavailable()
        } else if (data.diameterDistribution.isEmpty()) {
            Text(stringResource(R.string.analysis_distribution_empty), modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val displayed = data.diameterDistribution.take(16)
            val lines = displayed.map { entry ->
                stringResource(R.string.analysis_frequency_row, formatSynthesisNumber(entry.diameterCm, 2), entry.count)
            }
            val graphDescription = stringResource(R.string.analysis_distribution_accessible, lines.joinToString("; "))
            val barColor = MaterialTheme.colorScheme.primary
            val axisColor = MaterialTheme.colorScheme.outline
            val maximum = displayed.maxOf { it.count }.toFloat()
            Text(stringResource(R.string.analysis_distribution_scope), modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (displayed.size < data.diameterDistribution.size) {
                Text(stringResource(R.string.analysis_distribution_partial, displayed.size, data.diameterDistribution.size),
                    modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = graphDescription }) {
                val step = size.width / displayed.size
                val baseline = size.height - 2.dp.toPx()
                drawLine(axisColor, Offset(0f, baseline), Offset(size.width, baseline), 1.dp.toPx())
                displayed.forEachIndexed { index, entry ->
                    val height = (baseline - 8.dp.toPx()).coerceAtLeast(0f) * (entry.count / maximum)
                    drawRect(barColor, Offset(step * index + step * 0.2f, baseline - height),
                        Size(step * 0.6f, height))
                }
            }
            Text(stringResource(R.string.analysis_distribution_axes), modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            lines.forEach { line -> Text(line, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/** Legacy heights have neither total/commercial convention nor a qualified measurement protocol. */
@Composable
fun UnqualifiedHeightIndicatorsCard(
    measuredHeightCount: Int,
    observationCount: Int,
    modifier: Modifier = Modifier,
    animationsEnabled: Boolean = false,
    onExplainMissing: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    AnalysisCard(stringResource(R.string.analysis_height_title),
        modifier.then(if (animationsEnabled) Modifier.animateContentSize() else Modifier)) {
        Text(stringResource(R.string.analysis_height_coverage, measuredHeightCount, observationCount),
            modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        AnalysisMetric(stringResource(R.string.analysis_hd_label), stringResource(R.string.analysis_unavailable),
            Modifier.testTag("synthesis_hd_unavailable"))
        Text(stringResource(R.string.analysis_hd_missing), modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        AnalysisMetric(stringResource(R.string.analysis_spacing_label), stringResource(R.string.analysis_unavailable),
            Modifier.testTag("synthesis_spacing_unavailable"))
        Text(stringResource(R.string.analysis_spacing_missing), modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onExplainMissing != null) {
            TextButton(onClick = onExplainMissing, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.analysis_explain_measurements), modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center)
            }
        } else OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp)) {
            Text(stringResource(if (expanded) R.string.analysis_hide_requirements else R.string.analysis_show_requirements),
                Modifier.weight(1f))
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
        }
        if (expanded) {
            Text(stringResource(R.string.analysis_hd_formula), modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.analysis_height_requirements), modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.analysis_stand_requirements), modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun HeightProtocolHelpDialog(
    recordedHeightCount: Int,
    observationCount: Int,
    onDismiss: () -> Unit,
) {
    val contentDensity = LocalDensity.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            CompositionLocalProvider(LocalDensity provides contentDensity) {
                Text(stringResource(R.string.analysis_protocol_help_title), modifier = Modifier.fillMaxWidth())
            }
        },
        text = {
            // Dialog contents also use a separate Android owner and must keep the chosen text scale.
            CompositionLocalProvider(LocalDensity provides contentDensity) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.analysis_height_coverage, recordedHeightCount, observationCount),
                        modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.analysis_protocol_help_paired), modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.analysis_protocol_help_stand), modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.analysis_protocol_help_convention), modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            CompositionLocalProvider(LocalDensity provides contentDensity) {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.analysis_protocol_help_close), modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center)
                }
            }
        },
    )
}

@Composable
fun WoodQualityCoverageCard(report: SynthesisReport, modifier: Modifier = Modifier) {
    AnalysisCard(stringResource(R.string.analysis_quality_title), modifier.testTag("synthesis_wood_quality")) {
        AnalysisMetric(stringResource(R.string.analysis_quality_recorded),
            stringResource(R.string.analysis_coverage_value, report.qualityCount, report.observationCount))
        Text(stringResource(R.string.analysis_quality_limit), modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(stringResource(R.string.analysis_products_missing), modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EcologicalObservationCard(report: SynthesisReport, modifier: Modifier = Modifier) {
    AnalysisCard(stringResource(R.string.analysis_ecology_title), modifier.testTag("synthesis_ecological_observations")) {
        Text(stringResource(R.string.analysis_ecology_scope), modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        AnalysisMetric(stringResource(R.string.analysis_locations_recorded),
            stringResource(R.string.analysis_coverage_value, report.locatedCount, report.observationCount))
        if (report.categories.isEmpty()) {
            Text(stringResource(R.string.analysis_categories_empty), modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium)
        } else {
            report.categories.toSortedMap().forEach { (category, count) ->
                val label = when (category.uppercase(Locale.ROOT)) {
                    "DEPERISSANT" -> stringResource(R.string.special_tree_dying)
                    "ARBRE_BIO" -> stringResource(R.string.special_tree_bio)
                    "MORT" -> stringResource(R.string.special_tree_dead)
                    "PARASITE" -> stringResource(R.string.special_tree_parasite)
                    else -> category
                }
                Text(stringResource(R.string.analysis_category_count, label, count), modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(stringResource(R.string.analysis_station_missing), modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.analysis_ecology_limit), modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AnalysisCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, Modifier.fillMaxWidth().semantics { heading() }, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun AnalysisMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProjectionUnavailable() {
    Text(stringResource(R.string.analysis_projection_unavailable), modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun integerText(value: Int?): String =
    value?.let { NumberFormat.getIntegerInstance(Locale.getDefault()).format(it) }
        ?: stringResource(R.string.analysis_missing_value)
