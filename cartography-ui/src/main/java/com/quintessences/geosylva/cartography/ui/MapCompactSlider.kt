package com.quintessences.geosylva.cartography.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection

/** Thin decoration only: Material's slider retains its keyboard, drag and range semantics. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapCompactSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    label: String,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val activeColor = MapUiTokens.ink.copy(alpha = if (enabled) 1f else 0.38f)
    val inactiveColor = MapUiTokens.outline.copy(alpha = if (enabled) 1f else 0.6f)
    Slider(value = value, onValueChange = onValueChange, valueRange = valueRange,
        enabled = enabled, onValueChangeFinished = onValueChangeFinished,
        modifier = Modifier.fillMaxWidth().heightIn(min = MapUiTokens.touch)
            .semantics { contentDescription = label },
        thumb = {
            // Material3 measures its progress/gesture node from max(thumb, track),
            // inside requiredSizeIn. Reserve48dp here as well as outside the Slider;
            // the visible thumb remains20dp and the already reserved row stays48dp.
            Box(Modifier.size(MapUiTokens.sliderThumb, MapUiTokens.touch), contentAlignment = Alignment.Center) {
                Box(Modifier.size(MapUiTokens.sliderThumb).clip(CircleShape).background(activeColor))
            }
        },
        track = {
            Canvas(Modifier.fillMaxWidth().height(MapUiTokens.sliderTrack)) {
                val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                val start = Offset(if (rtl) size.width else 0f, size.height / 2f)
                val end = Offset(if (rtl) 0f else size.width, size.height / 2f)
                val activeEnd = Offset(if (rtl) size.width * (1f - fraction) else size.width * fraction, size.height / 2f)
                drawLine(inactiveColor, start, end, size.height, StrokeCap.Round)
                if (fraction > 0f) drawLine(activeColor, start, activeEnd, size.height, StrokeCap.Round)
            }
        })
}
