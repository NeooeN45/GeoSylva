package com.forestry.counter.presentation.components

import android.os.Build
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.forestry.counter.R
import com.forestry.counter.domain.model.SurveyContextDraft
import com.forestry.counter.domain.model.SurveyPopulationScope
import com.forestry.counter.domain.model.SurveyPurpose
import com.forestry.counter.presentation.screens.forestry.SurveyContextFormState
import com.forestry.counter.presentation.screens.forestry.SurveyContextUiError
import eightbitlab.com.blurview.BlurView
import eightbitlab.com.blurview.RenderEffectBlur
import eightbitlab.com.blurview.RenderScriptBlur

/** Déclare uniquement la finalité ; aucune population, surface ou complétude n'est présumée. */
@Composable
fun SurveyStartDialog(
    busy: Boolean,
    failed: Boolean,
    onDismiss: () -> Unit,
    onStart: (SurveyContextDraft) -> Unit,
) {
    var purposeName by rememberSaveable { mutableStateOf<String?>(null) }
    val purpose = SurveyPurpose.entries.firstOrNull { it.name == purposeName }
    SurveyDialogPanel(
        title = stringResource(R.string.survey_preparation_start_title),
        busy = busy,
        onDismiss = onDismiss,
        actions = {
            Button(
                onClick = { purpose?.let { onStart(SurveyContextDraft(purpose = it)) } },
                enabled = purpose != null && !busy && !failed,
                modifier = Modifier.fillMaxWidth().testTag("survey_start_confirm"),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.survey_preparation_start_action), Modifier.padding(start = 8.dp))
            }
            TextButton(
                onClick = onDismiss,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("survey_start_consult"),
            ) { Text(stringResource(R.string.survey_preparation_consult_action)) }
        },
    ) {
        Text(stringResource(R.string.survey_preparation_start_description), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.survey_preparation_objective), style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() })
        PurposeChoices(
            selected = purpose,
            enabled = !busy && !failed,
            allowUnknown = false,
            onSelect = { purposeName = it?.name },
        )
        if (purpose == SurveyPurpose.COMBINED) {
            ContextHint(stringResource(R.string.survey_preparation_combined_limit))
        }
        ContextHint(stringResource(R.string.survey_preparation_start_steps))
        if (failed) ContextErrorText(stringResource(R.string.survey_preparation_start_failed))
    }
}

/** Éditeur déclaratif sans accès DB ; stateKey appartient au compte et à la génération du parent. */
@Composable
fun SurveyContextDialog(
    stateKey: String,
    initialDraft: SurveyContextDraft,
    readOnly: Boolean,
    busy: Boolean,
    error: SurveyContextUiError?,
    onDismiss: () -> Unit,
    onSave: (SurveyContextDraft) -> Unit,
    onReload: () -> Unit,
) {
    var form by rememberSaveable(stateKey, stateSaver = SurveyContextFormSaver) {
        mutableStateOf(SurveyContextFormState.fromDraft(initialDraft))
    }
    var objectiveExpanded by rememberSaveable(stateKey) { mutableStateOf(false) }
    var populationExpanded by rememberSaveable(stateKey) { mutableStateOf(false) }
    var protocolExpanded by rememberSaveable(stateKey) { mutableStateOf(false) }
    var realizationExpanded by rememberSaveable(stateKey) { mutableStateOf(false) }
    val validation = form.validation()
    val fieldsEnabled = !readOnly && !busy
    val needsReload = error == SurveyContextUiError.CONFLICT || error == SurveyContextUiError.UNAVAILABLE

    SurveyDialogPanel(
        title = stringResource(R.string.survey_preparation_context_title),
        busy = busy,
        onDismiss = onDismiss,
        actions = {
            if (needsReload) {
                TextButton(
                    onClick = onReload,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("survey_context_reload"),
                ) { Text(stringResource(R.string.survey_preparation_reload_action)) }
            }
            if (!readOnly) {
                Button(
                    onClick = { form.toDraft()?.let(onSave) },
                    enabled = !busy && !needsReload && validation.isValid,
                    modifier = Modifier.fillMaxWidth().testTag("survey_context_confirm"),
                ) { Text(stringResource(R.string.survey_preparation_save_action)) }
            }
            TextButton(
                onClick = onDismiss,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("survey_context_close"),
            ) { Text(stringResource(R.string.survey_preparation_close_action)) }
        },
    ) {
        ContextHint(stringResource(if (readOnly) R.string.survey_preparation_context_read_only
            else R.string.survey_preparation_context_description))
        error?.let {
            ContextErrorText(stringResource(when (it) {
                SurveyContextUiError.CONFLICT -> R.string.survey_preparation_conflict
                SurveyContextUiError.UNAVAILABLE -> R.string.survey_preparation_unavailable
                SurveyContextUiError.SAVE_FAILED -> R.string.survey_preparation_save_failed
            }))
        }
        ContextSection(
            title = stringResource(R.string.survey_preparation_objective),
            summary = purposeLabel(form.purpose),
            tag = "survey_section_objective",
            expanded = objectiveExpanded,
            onToggle = { objectiveExpanded = !objectiveExpanded },
        ) {
            PurposeChoices(form.purpose, fieldsEnabled, allowUnknown = true, onSelect = { form = form.copy(purpose = it) })
            if (form.purpose == SurveyPurpose.COMBINED) {
                ContextHint(stringResource(R.string.survey_preparation_combined_limit))
            }
        }
        ContextSection(
            title = stringResource(R.string.survey_preparation_population),
            summary = populationScopeLabel(form.populationScope),
            tag = "survey_section_population",
            expanded = populationExpanded,
            onToggle = { populationExpanded = !populationExpanded },
        ) {
            ContextHint(stringResource(R.string.survey_preparation_population_hint))
            ReferenceField(
                value = form.populationReference,
                onChange = { form = form.copy(populationReference = it) },
                label = stringResource(R.string.survey_preparation_population_reference),
                tag = "survey_population_reference",
                enabled = fieldsEnabled,
                invalid = validation.populationReferenceTooLong,
            )
            Text(stringResource(R.string.survey_preparation_population_scope), style = MaterialTheme.typography.titleSmall)
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceRow(
                    selected = form.populationScope == null,
                    enabled = fieldsEnabled,
                    label = populationScopeLabel(null),
                    tag = "survey_scope_unknown",
                    onSelect = { form = form.copy(populationScope = null) },
                )
                SurveyPopulationScope.entries.forEach { scope ->
                    ChoiceRow(
                        selected = form.populationScope == scope,
                        enabled = fieldsEnabled,
                        label = populationScopeLabel(scope),
                        tag = "survey_scope_${scope.name}",
                        onSelect = { form = form.copy(populationScope = scope) },
                    )
                }
            }
        }
        ContextSection(
            title = stringResource(R.string.survey_preparation_protocol),
            summary = stringResource(if (form.protocolReference.isBlank()) R.string.survey_preparation_unknown
                else R.string.survey_preparation_reference_known),
            tag = "survey_section_protocol",
            expanded = protocolExpanded,
            onToggle = { protocolExpanded = !protocolExpanded },
        ) {
            ContextHint(stringResource(R.string.survey_preparation_protocol_hint))
            ReferenceField(form.protocolReference, { form = form.copy(protocolReference = it) },
                stringResource(R.string.survey_preparation_protocol_reference), "survey_protocol_reference", fieldsEnabled,
                validation.protocolReferenceTooLong)
            ReferenceField(form.protocolVersion, { form = form.copy(protocolVersion = it) },
                stringResource(R.string.survey_preparation_protocol_version), "survey_protocol_version", fieldsEnabled,
                validation.protocolVersionTooLong, maxLength = SurveyContextFormState.VERSION_MAX_LENGTH)
            ReferenceField(form.sourceReference, { form = form.copy(sourceReference = it) },
                stringResource(R.string.survey_preparation_source_reference), "survey_source_reference", fieldsEnabled,
                validation.sourceReferenceTooLong)
        }
        ContextSection(
            title = stringResource(R.string.survey_preparation_realization),
            summary = if (validation.invalidHorizontalArea) stringResource(R.string.survey_preparation_to_check)
                else completenessLabel(form.inventoryComplete),
            tag = "survey_section_realization",
            expanded = realizationExpanded,
            onToggle = { realizationExpanded = !realizationExpanded },
        ) {
            Text(stringResource(R.string.survey_preparation_completeness), style = MaterialTheme.typography.titleSmall)
            ContextHint(stringResource(R.string.survey_preparation_completeness_hint))
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null, false, true).forEach { complete ->
                    ChoiceRow(
                        selected = form.inventoryComplete == complete,
                        enabled = fieldsEnabled,
                        label = completenessLabel(complete),
                        tag = "survey_complete_${when (complete) { null -> "unknown"; false -> "incomplete"; true -> "complete" }}",
                        onSelect = { form = form.copy(inventoryComplete = complete) },
                    )
                }
            }
            val areaLabel = stringResource(R.string.survey_preparation_area_label)
            Text(areaLabel, style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = form.inventoriedHorizontalAreaInput,
                onValueChange = { form = form.copy(inventoriedHorizontalAreaInput = it) },
                enabled = fieldsEnabled,
                isError = validation.invalidHorizontalArea,
                modifier = Modifier.fillMaxWidth().testTag("survey_area_input").semantics { contentDescription = areaLabel },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                suffix = { Text(stringResource(R.string.survey_preparation_square_metres)) },
                supportingText = {
                    Text(stringResource(if (validation.invalidHorizontalArea) R.string.survey_preparation_area_invalid
                        else R.string.survey_preparation_optional))
                },
            )
            ContextHint(stringResource(R.string.survey_preparation_area_hint))
            ReferenceField(form.areaSourceReference, { form = form.copy(areaSourceReference = it) },
                stringResource(R.string.survey_preparation_area_source), "survey_area_source_reference", fieldsEnabled,
                validation.areaSourceReferenceTooLong)
        }
    }
}

private val SurveyContextFormSaver = listSaver<SurveyContextFormState, String>(
    save = { form -> listOf(
        form.purpose?.name.orEmpty(), form.populationReference, form.populationScope?.name.orEmpty(),
        form.protocolReference, form.protocolVersion, form.sourceReference,
        form.inventoryComplete?.toString().orEmpty(), form.inventoriedHorizontalAreaInput, form.areaSourceReference,
    ) },
    restore = { values ->
        if (values.size != 9) null else SurveyContextFormState(
            purpose = SurveyPurpose.entries.firstOrNull { it.name == values[0] },
            populationReference = values[1],
            populationScope = SurveyPopulationScope.entries.firstOrNull { it.name == values[2] },
            protocolReference = values[3],
            protocolVersion = values[4],
            sourceReference = values[5],
            inventoryComplete = values[6].toBooleanStrictOrNull(),
            inventoriedHorizontalAreaInput = values[7],
            areaSourceReference = values[8],
        )
    },
)

@Composable
private fun SurveyDialogPanel(
    title: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Fond flouté réel sur API 28 également, comme le démarrage historique de la placette.
    val host = LocalView.current.rootView.findViewById<ViewGroup>(android.R.id.content)
    val overlayColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.20f).toArgb()
    Dialog(
        onDismissRequest = onDismiss,
        // Sur API < 31, Compose configure ainsi ADJUST_RESIZE au lieu de laisser Android
        // déplacer la fenêtre entière au-dessus du clavier et cacher les actions.
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !busy,
            dismissOnClickOutside = !busy, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    BlurView(context).apply {
                        if (host != null) {
                            setupWith(host, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) RenderEffectBlur()
                                else RenderScriptBlur(context))
                                .setFrameClearDrawable(host.background)
                                .setBlurRadius(16f)
                                .setBlurAutoUpdate(true)
                                .setOverlayColor(overlayColor)
                        }
                    }
                },
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)))
            // Les insets sont consommés par ces modificateurs : safeDrawing et IME ne
            // s'additionnent pas deux fois. Le fond flouté continue d'occuper toute la fenêtre.
            Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.padding(16.dp).widthIn(max = 420.dp).fillMaxWidth()
                        .testTag("survey_dialog_panel"),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    tonalElevation = 6.dp,
                    shadowElevation = 12.dp,
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(
                            Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // Avec le clavier ou une grande police, le titre participe au
                            // défilement pour réserver la place des commandes de sauvegarde/fermeture.
                            Text(title, style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.semantics { heading() })
                            content()
                        }
                        HorizontalDivider()
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp), content = actions)
                    }
                }
            }
        }
    }
}

@Composable
private fun PurposeChoices(
    selected: SurveyPurpose?,
    enabled: Boolean,
    allowUnknown: Boolean,
    onSelect: (SurveyPurpose?) -> Unit,
) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (allowUnknown) {
            ChoiceRow(selected == null, enabled, purposeLabel(null), "survey_purpose_unknown", onSelect = { onSelect(null) })
        }
        SurveyPurpose.entries.forEach { purpose ->
            ChoiceRow(
                selected = selected == purpose,
                enabled = enabled,
                label = purposeLabel(purpose),
                tag = "survey_purpose_${purpose.name}",
                description = stringResource(when (purpose) {
                    SurveyPurpose.INVENTORY -> R.string.survey_preparation_inventory_description
                    SurveyPurpose.MARKING -> R.string.survey_preparation_marking_description
                    SurveyPurpose.COMBINED -> R.string.survey_preparation_combined_description
                }),
                onSelect = { onSelect(purpose) },
            )
        }
    }
}

@Composable
private fun ChoiceRow(
    selected: Boolean,
    enabled: Boolean,
    label: String,
    tag: String,
    description: String? = null,
    onSelect: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag).clip(shape)
            .border(BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant), shape)
            .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled, modifier = Modifier.clearAndSetSemantics { })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            description?.let { ContextHint(it) }
        }
    }
}

@Composable
private fun ContextSection(
    title: String,
    summary: String,
    tag: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag(tag).clickable(onClick = onToggle).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    ContextHint(summary)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            if (expanded) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            }
        }
    }
}

@Composable
private fun ReferenceField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    tag: String,
    enabled: Boolean,
    invalid: Boolean,
    maxLength: Int = SurveyContextFormState.REFERENCE_MAX_LENGTH,
) {
    // Libellé permanent au-dessus du champ : pas de texte flottant tronqué avec une grande police.
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().testTag(tag).semantics { contentDescription = label },
            enabled = enabled,
            isError = invalid,
            minLines = 1,
            maxLines = 3,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
            supportingText = {
                Text(if (invalid) stringResource(R.string.survey_preparation_reference_too_long, maxLength)
                    else stringResource(R.string.survey_preparation_optional))
            },
        )
    }
}

@Composable
private fun ContextHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ContextErrorText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
private fun purposeLabel(purpose: SurveyPurpose?): String = stringResource(when (purpose) {
    SurveyPurpose.INVENTORY -> R.string.survey_preparation_inventory
    SurveyPurpose.MARKING -> R.string.survey_preparation_marking
    SurveyPurpose.COMBINED -> R.string.survey_preparation_combined
    null -> R.string.survey_preparation_unknown
})

@Composable
private fun populationScopeLabel(scope: SurveyPopulationScope?): String = stringResource(when (scope) {
    SurveyPopulationScope.ALL_STEMS -> R.string.survey_preparation_scope_all
    SurveyPopulationScope.MARKED_STEMS -> R.string.survey_preparation_scope_marked
    SurveyPopulationScope.REMAINING_STEMS -> R.string.survey_preparation_scope_remaining
    null -> R.string.survey_preparation_scope_unknown
})

@Composable
private fun completenessLabel(complete: Boolean?): String = stringResource(when (complete) {
    null -> R.string.survey_preparation_complete_unknown
    false -> R.string.survey_preparation_incomplete
    true -> R.string.survey_preparation_complete
})
