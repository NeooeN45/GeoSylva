package com.forestry.counter.presentation.screens.field

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.forestry.counter.R

@Composable fun FieldJournalButton(onClick:()->Unit) {
    IconButton(onClick) {Icon(Icons.Default.Description,stringResource(R.string.field_title))}
}
