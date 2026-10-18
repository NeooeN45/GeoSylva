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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.forestry.counter.R
import com.forestry.counter.domain.model.HeightNature
import com.forestry.counter.domain.model.MeasurementOrigin
import com.forestry.counter.domain.model.ObservationMeasurementBasis
import com.forestry.counter.domain.model.ObservationQualificationDraft
import com.forestry.counter.domain.model.ObservationSupport
import com.forestry.counter.domain.model.PositionOrigin
import com.forestry.counter.presentation.screens.forestry.ObservationDiameterConventionChoice
import com.forestry.counter.presentation.screens.forestry.ObservationQualificationFormState
import eightbitlab.com.blurview.BlurView
import eightbitlab.com.blurview.RenderEffectBlur
import eightbitlab.com.blurview.RenderScriptBlur
import java.text.DateFormat
import java.text.DecimalFormatSymbols
import java.util.Date

enum class ObservationQualificationDialogError { CONFLICT, UNAVAILABLE, SAVE_FAILED }

/**
 * Déclaration liée aux valeurs affichées, distincte d'une mesure ou d'un calcul.
 * Le parent fournit une clé compte/génération/observation/rechargement et garde les droits.
 * Un conflit conserve le brouillon ; seul un rechargement explicite remplace cet état.
 */
@Composable
fun ObservationQualificationDialog(
    stateKey: String,
    basis: ObservationMeasurementBasis,
    initialDraft: ObservationQualificationDraft,
    readOnly: Boolean,
    busy: Boolean,
    error: ObservationQualificationDialogError?,
    stale: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (ObservationQualificationDraft) -> Unit,
    onReload: () -> Unit,
) {
    var form by rememberSaveable(stateKey, saver = qualificationFormSaver(stateKey)) {
        mutableStateOf(ObservationQualificationFormState.fromDraft(initialDraft))
    }
    var diameterExpanded by rememberSaveable(stateKey) { mutableStateOf(false) }
    var heightExpanded by rememberSaveable(stateKey) { mutableStateOf(false) }
    val validation = form.validation()
    val fieldsEnabled = !readOnly && !busy
    val needsReload = error == ObservationQualificationDialogError.CONFLICT ||
        error == ObservationQualificationDialogError.UNAVAILABLE

    QualificationDialogPanel(
        busy = busy,
        onDismiss = onDismiss,
        actions = {
            if (needsReload) {
                TextButton(onClick = onReload, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("qualification_reload")) {
                    Text(stringResource(R.string.observation_qualification_reload))
                }
            }
            if (!readOnly) {
                Button(onClick = { form.toDraft()?.let(onSave) },
                    enabled = !busy && !needsReload && validation.isValid,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("qualification_save")) {
                    Text(stringResource(R.string.observation_qualification_save))
                }
            }
            TextButton(onClick = onDismiss, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("qualification_close")) {
                Text(stringResource(R.string.observation_qualification_close))
            }
        },
    ) {
        Text(stringResource(R.string.observation_qualification_title),
            style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        QualificationHint(stringResource(if (readOnly) R.string.observation_qualification_read_only
            else R.string.observation_qualification_description))
        RawMeasurementSummary(basis)
        if (busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag("qualification_progress"))
            QualificationHint(stringResource(R.string.observation_qualification_saving))
        }
        if (stale) QualificationNotice(stringResource(if (readOnly) R.string.observation_qualification_stale_read_only
            else R.string.observation_qualification_stale), "qualification_stale")
        error?.let { failure ->
            Text(stringResource(when (failure) {
                ObservationQualificationDialogError.CONFLICT -> R.string.observation_qualification_conflict
                ObservationQualificationDialogError.UNAVAILABLE -> R.string.observation_qualification_unavailable
                ObservationQualificationDialogError.SAVE_FAILED -> R.string.observation_qualification_save_failed
            }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("qualification_error").semantics { liveRegion = LiveRegionMode.Polite })
        }
        Text(stringResource(R.string.observation_qualification_support_title), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        QualificationHint(stringResource(R.string.observation_qualification_support_hint))
        SupportChoices(form.support, fieldsEnabled, "support", onSelect = { form = form.withSupport(it) })
        if (form.support == ObservationSupport.GROUP_REFERENCE) {
            QualificationNotice(stringResource(R.string.observation_qualification_collective_hint), "qualification_collective_hint")
        }
        QualificationSection(
            title = stringResource(R.string.observation_qualification_diameter_title),
            summary = originLabel(form.diameterOrigin), expanded = diameterExpanded, tag = "diameter",
            onToggle = { diameterExpanded = !diameterExpanded },
        ) {
            FieldHeading(stringResource(R.string.observation_qualification_origin_title))
            OriginChoices(form.diameterOrigin, fieldsEnabled, "diameter_origin",
                onSelect = { form = form.withDiameterOrigin(it) })
            FieldHeading(stringResource(R.string.observation_qualification_convention_title))
            QualificationHint(stringResource(R.string.observation_qualification_convention_hint))
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QualificationChoice(form.diameterConventionChoice == null, fieldsEnabled,
                    stringResource(R.string.observation_qualification_unknown), "diameter_convention_unknown") {
                    form = form.copy(diameterConventionChoice = null)
                }
                ObservationDiameterConventionChoice.entries.forEach { choice ->
                    QualificationChoice(form.diameterConventionChoice == choice, fieldsEnabled,
                        conventionLabel(choice), "diameter_convention_${choice.name}") {
                        form = form.copy(diameterConventionChoice = choice)
                    }
                }
            }
            if (form.diameterConventionChoice == ObservationDiameterConventionChoice.OTHER) {
                QualificationReferenceField(form.diameterOtherConventionReference,
                    { form = form.copy(diameterOtherConventionReference = it) },
                    stringResource(R.string.observation_qualification_other_convention), "diameter_other_convention",
                    fieldsEnabled, validation.diameterOtherConventionReferenceTooLong)
            }
            QualificationReferenceField(form.diameterMethodReference, { form = form.copy(diameterMethodReference = it) },
                stringResource(R.string.observation_qualification_method), "diameter_method", fieldsEnabled,
                validation.diameterMethodReferenceTooLong)
            QualificationReferenceField(form.diameterSourceReference, { form = form.copy(diameterSourceReference = it) },
                stringResource(R.string.observation_qualification_source), "diameter_source", fieldsEnabled,
                validation.diameterSourceReferenceTooLong)
            form.diameterObservedAt?.let { DeclaredDate(it) }
        }
        QualificationSection(
            title = stringResource(R.string.observation_qualification_height_title),
            summary = heightNatureLabel(form.heightNature), expanded = heightExpanded, tag = "height",
            onToggle = { heightExpanded = !heightExpanded },
        ) {
            if (basis.hauteurM == null) QualificationHint(stringResource(R.string.observation_qualification_height_missing_hint))
            FieldHeading(stringResource(R.string.observation_qualification_height_nature_title))
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null, HeightNature.TOTAL, HeightNature.MERCHANTABLE).forEach { nature ->
                    QualificationChoice(form.heightNature == nature, fieldsEnabled, heightNatureLabel(nature),
                        "height_nature_${nature?.name ?: "unknown"}") { form = form.withHeightNature(nature) }
                }
            }
            FieldHeading(stringResource(R.string.observation_qualification_origin_title))
            OriginChoices(form.heightOrigin, fieldsEnabled, "height_origin", onSelect = { form = form.withHeightOrigin(it) })
            FieldHeading(stringResource(R.string.observation_qualification_height_support_title))
            SupportChoices(form.heightSupport, fieldsEnabled, "height_support", onSelect = { form = form.withHeightSupport(it) })
            if (form.heightSupport == ObservationSupport.GROUP_REFERENCE) {
                QualificationHint(stringResource(R.string.observation_qualification_collective_hint))
            }
            FieldHeading(stringResource(R.string.observation_qualification_pairing_title))
            QualificationHint(stringResource(R.string.observation_qualification_pairing_hint))
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null, false, true).forEach { paired ->
                    QualificationChoice(form.heightPairedWithDiameter == paired,
                        fieldsEnabled && (paired != true || form.canPairHeightWithDiameter), pairingLabel(paired),
                        "height_pairing_${paired?.toString() ?: "unknown"}") { form = form.withHeightPairing(paired) }
                }
            }
            if (!form.canPairHeightWithDiameter) QualificationHint(stringResource(R.string.observation_qualification_pairing_unavailable))
            QualificationReferenceField(form.heightMethodReference, { form = form.copy(heightMethodReference = it) },
                stringResource(R.string.observation_qualification_method), "height_method", fieldsEnabled,
                validation.heightMethodReferenceTooLong)
            if (form.heightOrigin == MeasurementOrigin.ESTIMATED) {
                QualificationHint(stringResource(R.string.observation_qualification_estimate_method_hint))
            }
            QualificationReferenceField(form.heightSourceReference, { form = form.copy(heightSourceReference = it) },
                stringResource(R.string.observation_qualification_source), "height_source", fieldsEnabled,
                validation.heightSourceReferenceTooLong)
            form.heightObservedAt?.let { DeclaredDate(it) }
        }
        if (validation.invalidPairing) {
            QualificationNotice(stringResource(R.string.observation_qualification_pairing_unavailable), "qualification_invalid_pairing")
        }
        form.position?.let { position ->
            QualificationHint(stringResource(R.string.observation_qualification_position_preserved, positionOriginLabel(position.origin)))
        }
        QualificationHint(stringResource(R.string.observation_qualification_scientific_limit))
    }
}

/**
 * Les inputs seuls ne sont pas revérifiés à la restauration. Le Saver porte sur l'état entier :
 * sous Compose 1.7, stateSaver envelopperait un refus null dans MutableState(null).
 * Un refus du Saver extérieur rappelle donc l'initialisation du formulaire courant.
 */
private fun qualificationFormSaver(stateKey: String) = listSaver<MutableState<ObservationQualificationFormState>, String>(
    save = { listOf(stateKey) + it.value.toSaveableValues() },
    restore = { values ->
        if (values.firstOrNull() == stateKey) {
            ObservationQualificationFormState.fromSaveableValues(values.drop(1))?.let { mutableStateOf(it) }
        } else null
    },
)

/** Reprend le viewport qualifié de SurveyDialogPanel, sans toucher à son API privée. */
@Composable
private fun QualificationDialogPanel(
    busy: Boolean,
    onDismiss: () -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val host = LocalView.current.rootView.findViewById<ViewGroup>(android.R.id.content)
    val overlayColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.20f).toArgb()
    Dialog(onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !busy,
            dismissOnClickOutside = !busy, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                BlurView(context).apply {
                    if (host != null) {
                        setupWith(host, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) RenderEffectBlur()
                            else RenderScriptBlur(context))
                            .setFrameClearDrawable(host.background).setBlurRadius(16f)
                            .setBlurAutoUpdate(true).setOverlayColor(overlayColor)
                    }
                }
            })
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)))
            // safeDrawing et IME consomment leurs insets ; le clavier ne déplace pas les actions hors écran.
            Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.Center) {
                Surface(modifier = Modifier.padding(16.dp).widthIn(max = 420.dp).fillMaxWidth()
                    .testTag("qualification_dialog_panel"), shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), tonalElevation = 6.dp, shadowElevation = 12.dp) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Le titre défile aussi afin de réserver un espace utile aux commandes fixes.
                        Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                        HorizontalDivider()
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp), content = actions)
                    }
                }
            }
        }
    }
}

@Composable
private fun RawMeasurementSummary(basis: ObservationMeasurementBasis) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.observation_qualification_raw_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.observation_qualification_observation_id, basis.tigeId),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("qualification_raw_id"))
            Text(stringResource(R.string.observation_qualification_raw_diameter, decimalText(basis.diamCm)),
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("qualification_raw_diameter"))
            Text(basis.hauteurM?.let { stringResource(R.string.observation_qualification_raw_height, decimalText(it)) }
                ?: stringResource(R.string.observation_qualification_raw_height_missing),
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("qualification_raw_height"))
            QualificationHint(stringResource(R.string.observation_qualification_raw_hint))
        }
    }
}

/** Affichage seulement : les chiffres bruts et leurs unités ne sont jamais réécrits. */
private fun decimalText(value: Double): String = value.toString()
    .replace('.', DecimalFormatSymbols.getInstance().decimalSeparator)

@Composable
private fun SupportChoices(selected: ObservationSupport?, enabled: Boolean, prefix: String, onSelect: (ObservationSupport?) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(null, ObservationSupport.INDIVIDUAL_TREE, ObservationSupport.GROUP_REFERENCE).forEach { support ->
            QualificationChoice(selected == support, enabled, supportLabel(support), "${prefix}_${support?.name ?: "unknown"}") {
                onSelect(support)
            }
        }
    }
}

@Composable
private fun OriginChoices(selected: MeasurementOrigin?, enabled: Boolean, prefix: String, onSelect: (MeasurementOrigin?) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(null, MeasurementOrigin.MEASURED, MeasurementOrigin.ESTIMATED, MeasurementOrigin.ASSIGNED_REFERENCE).forEach { origin ->
            QualificationChoice(selected == origin, enabled, originLabel(origin), "${prefix}_${origin?.name ?: "unknown"}") {
                onSelect(origin)
            }
        }
    }
}

@Composable
private fun QualificationChoice(selected: Boolean, enabled: Boolean, label: String, suffix: String, onSelect: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("qualification_$suffix").clip(shape)
        .border(BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant), shape)
        .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
        .padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Un seul nœud radio cliquable : le bouton visuel n'ajoute pas de commande TalkBack.
        RadioButton(selected = selected, onClick = null, enabled = enabled, modifier = Modifier.clearAndSetSemantics { })
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun QualificationSection(title: String, summary: String, expanded: Boolean, tag: String,
    onToggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val expansionState = stringResource(if (expanded) R.string.observation_qualification_expanded else R.string.observation_qualification_collapsed)
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("qualification_section_$tag")
                .clickable(onClick = onToggle).semantics { stateDescription = expansionState }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    QualificationHint(summary)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            if (expanded) Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
private fun QualificationReferenceField(value: String, onChange: (String) -> Unit, label: String, suffix: String,
    enabled: Boolean, invalid: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(value = value, onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().testTag("qualification_$suffix").semantics { contentDescription = label },
            enabled = enabled, isError = invalid, minLines = 1, maxLines = 3,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
            supportingText = { Text(stringResource(if (invalid) R.string.observation_qualification_reference_too_long
                else R.string.observation_qualification_optional)) })
    }
}

@Composable
private fun FieldHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun QualificationHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun QualificationNotice(text: String, tag: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(text, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().padding(12.dp).testTag(tag).semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
private fun DeclaredDate(value: Long) {
    QualificationHint(stringResource(R.string.observation_qualification_declared_date,
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(value))))
}

@Composable
private fun supportLabel(support: ObservationSupport?): String = stringResource(when (support) {
    null -> R.string.observation_qualification_unknown
    ObservationSupport.INDIVIDUAL_TREE -> R.string.observation_qualification_individual
    ObservationSupport.GROUP_REFERENCE -> R.string.observation_qualification_group
})

@Composable
private fun originLabel(origin: MeasurementOrigin?): String = stringResource(when (origin) {
    null -> R.string.observation_qualification_unknown
    MeasurementOrigin.MEASURED -> R.string.observation_qualification_measured
    MeasurementOrigin.ESTIMATED -> R.string.observation_qualification_estimated
    MeasurementOrigin.ASSIGNED_REFERENCE -> R.string.observation_qualification_assigned
})

@Composable
private fun heightNatureLabel(nature: HeightNature?): String = stringResource(when (nature) {
    null -> R.string.observation_qualification_unknown
    HeightNature.TOTAL -> R.string.observation_qualification_total_height
    HeightNature.MERCHANTABLE -> R.string.observation_qualification_merchantable_height
})

@Composable
private fun conventionLabel(choice: ObservationDiameterConventionChoice): String = stringResource(when (choice) {
    ObservationDiameterConventionChoice.BREAST_HEIGHT_1_30_M -> R.string.observation_qualification_dbh_130
    ObservationDiameterConventionChoice.BREAST_HEIGHT_1_50_M -> R.string.observation_qualification_dbh_150
    ObservationDiameterConventionChoice.OTHER -> R.string.observation_qualification_other
})

@Composable
private fun pairingLabel(paired: Boolean?): String = stringResource(when (paired) {
    null -> R.string.observation_qualification_unknown
    false -> R.string.observation_qualification_pairing_no
    true -> R.string.observation_qualification_pairing_yes
})

@Composable
private fun positionOriginLabel(origin: PositionOrigin?): String = stringResource(when (origin) {
    null -> R.string.observation_qualification_unknown
    PositionOrigin.CAPTURED_FOR_OBSERVATION -> R.string.observation_qualification_position_captured
    PositionOrigin.REUSED_POINT -> R.string.observation_qualification_position_reused
    PositionOrigin.MANUALLY_LOCATED -> R.string.observation_qualification_position_manual
})
