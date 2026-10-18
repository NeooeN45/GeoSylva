package com.forestry.counter.presentation.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forestry.counter.R
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.Motion
import com.forestry.counter.presentation.theme.Space

/**
 * Métier déclaré au premier lancement : les préférences existantes restent
 * l'autorité de sauvegarde. Ce choix ne crée aucune affiliation d'organisation.
 */
@Composable
fun ProfessionSelectionScreen(
    onComplete: (code: String, customText: String?) -> Unit,
    isSaving: Boolean = false,
    saveFailed: Boolean = false,
    initialCode: String? = null,
    initialCustomText: String? = null,
) {
    var selectedCode by rememberSaveable(initialCode) { mutableStateOf(initialCode) }
    var customText by rememberSaveable(initialCustomText) { mutableStateOf(initialCustomText.orEmpty()) }
    val isOther = selectedCode == ProfessionOption.OTHER_CODE
    val canContinue = selectedCode != null && (!isOther || customText.isNotBlank())
    val fontScale = LocalDensity.current.fontScale

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.forest_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Surface(
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = Space.screenH),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(top = Space.lg, bottom = Space.md),
                ) {
                    Text(
                        text = stringResource(R.string.profession_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.profession_choice_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.xs),
                    )

                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        // Des titres sans hauteur imposée : une colonne si le
                        // texte agrandi ou la largeur rendent deux cartes étroites.
                        val columns = if (maxWidth >= 300.dp && fontScale < 1.3f) 2 else 1
                        Column(
                            modifier = Modifier
                                .padding(top = Space.lg)
                                .selectableGroup(),
                            verticalArrangement = Arrangement.spacedBy(Space.sm),
                        ) {
                            PROFESSION_OPTIONS.chunked(columns).forEach { rowOptions ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    rowOptions.forEach { option ->
                                        ProfessionCard(
                                            option = option,
                                            selected = selectedCode == option.code,
                                            enabled = !isSaving,
                                            onClick = {
                                                if (!isSaving) selectedCode = option.code
                                            },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    if (rowOptions.size < columns) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = isOther,
                        enter = fadeIn(Motion.springSnappy()) + expandVertically(Motion.springSnappy()),
                        exit = fadeOut(Motion.springSnappy()) + shrinkVertically(Motion.springSnappy()),
                    ) {
                        OutlinedTextField(
                            value = customText,
                            onValueChange = { customText = it },
                            enabled = !isSaving,
                            label = { Text(stringResource(R.string.profession_custom_label)) },
                            placeholder = { Text(stringResource(R.string.profession_custom_placeholder)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Space.md),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            ),
                            shape = GsShape.md,
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = GsShape.md,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Space.md),
                    ) {
                        Column(modifier = Modifier.padding(Space.md)) {
                            Text(
                                text = stringResource(R.string.profession_organisation_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(R.string.profession_privacy_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Space.xs),
                            )
                        }
                    }
                }

                if (saveFailed) {
                    Text(
                        stringResource(R.string.first_entry_profession_save_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = Space.sm),
                    )
                }
                Button(
                    onClick = {
                        if (isSaving || !canContinue) return@Button
                        val code = selectedCode ?: return@Button
                        onComplete(code, if (isOther) customText.trim() else null)
                    },
                    enabled = canContinue && !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Space.md)
                        .heightIn(min = 52.dp),
                    shape = GsShape.pill,
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(Space.sm))
                    }
                    Text(stringResource(R.string.continue_label))
                }
            }
        }
    }
}

@Composable
private fun ProfessionCard(
    option: ProfessionOption,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) Motion.PRESS_SCALE else 1f,
        animationSpec = Motion.springSnappy(),
        label = "professionCardScale",
    )

    Card(
        modifier = modifier
            .scale(scale)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
        shape = GsShape.lg,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 112.dp)
                .padding(Space.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                if (selected) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(22.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
            Text(
                text = stringResource(option.labelRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.sm),
            )
        }
    }
}
