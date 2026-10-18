package com.forestry.counter.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.forestry.counter.R
import com.forestry.counter.presentation.utils.hasInvalidHeightInput
import com.forestry.counter.presentation.utils.parseHeightInputMean

/** Saisie d'une référence de hauteur de classe, distincte d'une mesure individuelle qualifiée. */
@Composable
fun HeightClassInputField(
    value: String,
    onValueChange: (String) -> Unit,
    missingCount: Int,
    recordedCount: Int,
    tarifRequiresHeight: Boolean,
    modifier: Modifier = Modifier
) {
    val (mean, count) = remember(value) { parseHeightInputMean(value) }
    val invalidInput = remember(value) { hasInvalidHeightInput(listOf(value)) }
    val validInput = mean != null

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        isError = invalidInput || (missingCount > 0 && !validInput && tarifRequiresHeight),
        label = { Text(stringResource(R.string.height)) },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done
        ),
        placeholder = { Text(stringResource(R.string.placeholder_height_short)) },
        suffix = { Text(stringResource(R.string.unit_m)) },
        supportingText = {
            val parts = mutableListOf<String>()
            if (invalidInput) parts += stringResource(R.string.height_input_invalid)
            if (missingCount > 0) parts += stringResource(R.string.height_n_missing_format, missingCount)
            if (recordedCount > 0) parts += stringResource(R.string.height_n_recorded_format, recordedCount)
            if (count > 1 && mean != null) {
                parts += stringResource(R.string.height_mean_computed_format, mean, count)
            }
            if (parts.isNotEmpty()) Text(parts.joinToString(" · "))
        },
        singleLine = true
    )
}
