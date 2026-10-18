package com.forestry.counter.presentation.screens.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.forestry.counter.R
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.SurfaceBright
import com.forestry.counter.presentation.theme.Space
import com.forestry.counter.presentation.theme.Touch
import com.forestry.counter.presentation.viewmodel.LoginInputIssue
import com.forestry.counter.presentation.viewmodel.LoginUiState
import com.forestry.counter.presentation.viewmodel.RegistrationStep

/** Inscription native ; saisies et étape restent dans le ViewModel d’identité. */
@Composable
internal fun RegistrationForm(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordConfirmationChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onBackToSignIn: () -> Unit,
    onSubmit: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val step = state.registrationStep
    val stepNumber = step.ordinal + 1
    val enabled = !state.isSubmitting

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.screenH)
            .padding(top = Space.md, bottom = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                onClick = {
                    focusManager.clearFocus()
                    if (step == RegistrationStep.EMAIL) onBackToSignIn() else onPrevious()
                },
                enabled = enabled,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(Space.md),
                )
                Spacer(Modifier.size(Space.xs))
                Text(
                    stringResource(
                        if (step == RegistrationStep.EMAIL) R.string.registration_back_to_login
                        else R.string.registration_back,
                    ),
                )
            }
            Text(
                text = stringResource(R.string.registration_step_count, stepNumber),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        LinearProgressIndicator(
            progress = { stepNumber / 3f },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
        )

        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(
                text = stringResource(R.string.registration_account_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(
                    when (step) {
                        RegistrationStep.EMAIL -> R.string.registration_email_title
                        RegistrationStep.PASSWORD -> R.string.registration_password_title
                        RegistrationStep.PROFILE -> R.string.registration_profile_title
                    },
                ),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(
                    when (step) {
                        RegistrationStep.EMAIL -> R.string.registration_email_description
                        RegistrationStep.PASSWORD -> R.string.registration_password_description
                        RegistrationStep.PROFILE -> R.string.registration_profile_description
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        key(step) {
            when (step) {
                RegistrationStep.EMAIL -> {
                    val issue = if (state.inputIssue == LoginInputIssue.EMAIL) {
                        stringResource(R.string.registration_email_error)
                    } else null
                    OutlinedTextField(
                        value = state.email,
                        onValueChange = onEmailChange,
                        label = { Text(stringResource(R.string.identity_email)) },
                        supportingText = if (issue != null) { { Text(issue) } } else null,
                        isError = issue != null,
                        enabled = enabled,
                        singleLine = true,
                        shape = GsShape.md,
                        colors = registrationFieldColors(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            autoCorrect = false,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(onNext = {
                            if (enabled) {
                                focusManager.clearFocus()
                                onNext()
                            }
                        }),
                        modifier = Modifier.fillMaxWidth().testTag("registration-email"),
                    )
                }

                RegistrationStep.PASSWORD -> {
                    val confirmationFocus = remember { FocusRequester() }
                    val passwordIssue = if (state.inputIssue == LoginInputIssue.PASSWORD_LENGTH) {
                        stringResource(R.string.registration_password_error)
                    } else null
                    RegistrationPasswordField(
                        value = state.password,
                        onValueChange = onPasswordChange,
                        label = stringResource(R.string.identity_password),
                        supportingText = passwordIssue ?: stringResource(R.string.identity_password_hint),
                        isError = passwordIssue != null,
                        enabled = enabled,
                        imeAction = ImeAction.Next,
                        onImeAction = { confirmationFocus.requestFocus() },
                        modifier = Modifier.testTag("registration-password"),
                    )
                    val confirmationIssue = if (state.inputIssue == LoginInputIssue.PASSWORD_CONFIRMATION) {
                        stringResource(R.string.registration_confirmation_error)
                    } else null
                    RegistrationPasswordField(
                        value = state.passwordConfirmation,
                        onValueChange = onPasswordConfirmationChange,
                        label = stringResource(R.string.identity_password_confirmation),
                        supportingText = confirmationIssue,
                        isError = confirmationIssue != null,
                        enabled = enabled,
                        imeAction = ImeAction.Done,
                        onImeAction = {
                            focusManager.clearFocus()
                            onNext()
                        },
                        modifier = Modifier
                            .focusRequester(confirmationFocus)
                            .testTag("registration-password-confirmation"),
                    )
                }

                RegistrationStep.PROFILE -> {
                    val issue = if (state.inputIssue == LoginInputIssue.DISPLAY_NAME) {
                        stringResource(R.string.registration_display_name_error)
                    } else null
                    OutlinedTextField(
                        value = state.displayName,
                        onValueChange = onDisplayNameChange,
                        label = { Text(stringResource(R.string.identity_display_name)) },
                        supportingText = if (issue != null) { { Text(issue) } } else null,
                        isError = issue != null,
                        enabled = enabled,
                        singleLine = true,
                        shape = GsShape.md,
                        colors = registrationFieldColors(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (enabled) {
                                focusManager.clearFocus()
                                onSubmit()
                            }
                        }),
                        modifier = Modifier.fillMaxWidth().testTag("registration-display-name"),
                    )
                    Surface(
                        shape = GsShape.md,
                        color = SurfaceBright.copy(alpha = 0.32f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(Space.md),
                            verticalArrangement = Arrangement.spacedBy(Space.xxs),
                        ) {
                            Text(
                                text = stringResource(R.string.registration_email_summary),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = state.email.trim(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }

        state.error?.takeIf { state.inputIssue == null }?.let { error ->
            IdentityErrorBanner(
                error = error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        Button(
            onClick = {
                focusManager.clearFocus()
                if (step == RegistrationStep.PROFILE) onSubmit() else onNext()
            },
            enabled = enabled,
            shape = GsShape.pill,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Touch.field)
                .testTag("registration-continue"),
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Space.lg),
                    strokeWidth = Space.xxs / 2,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(Space.sm))
            }
            Text(
                text = stringResource(
                    when {
                        state.isSubmitting -> R.string.registration_creating
                        step == RegistrationStep.PROFILE -> R.string.identity_create_account
                        else -> R.string.registration_continue
                    },
                ),
                style = MaterialTheme.typography.titleSmall,
            )
        }

        if (step == RegistrationStep.PROFILE) {
            Text(
                text = stringResource(R.string.registration_data_information),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = onPrivacyPolicy,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.registration_privacy_policy))
        }
    }
}

@Composable
private fun RegistrationPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String?,
    isError: Boolean,
    enabled: Boolean,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Visibilité temporaire ; mots de passe et confirmations ne sont jamais sauvegardés dans l’état UI.
    var visible by remember { mutableStateOf(false) }
    val action = { if (enabled) onImeAction() }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = if (supportingText != null) { { Text(supportingText) } } else null,
        isError = isError,
        enabled = enabled,
        singleLine = true,
        shape = GsShape.md,
        colors = registrationFieldColors(),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrect = false,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onNext = { action() }, onDone = { action() }),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }, enabled = enabled) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = stringResource(
                        if (visible) R.string.identity_hide_password else R.string.identity_show_password,
                    ),
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/** Champs translucides intégrés au verre qui couvre toute la page. */
@Composable
private fun registrationFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.62f),
    focusedContainerColor = SurfaceBright.copy(alpha = 0.52f),
    unfocusedContainerColor = SurfaceBright.copy(alpha = 0.38f),
    disabledContainerColor = SurfaceBright.copy(alpha = 0.28f),
    errorContainerColor = SurfaceBright.copy(alpha = 0.52f),
)
