package com.quintessences.geosylva.cartography.ui.measurement

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Establish a hit target above the map without cancelling child buttons or text-field gestures. */
internal fun Modifier.blockMapTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent()
    }
}

internal object MeasureUi {
    val paper = Color(0xFFFFFEFF)
    val ink = Color(0xFF0B286B)
    val secondary = Color(0xFF6663AC)
    val green = Color(0xFF00652C)
    val paleGreen = Color(0xFFD6EBD8)
    val lavender = Color(0xFFE7E4F7)
    val border = Color(0xFFDEDEE5)
    val error = Color(0xFF912F38)
    val edge = 12.dp
    val gap = 12.dp
    val touch = 48.dp
    val capsule = RoundedCornerShape(28.dp)
    val panel = RoundedCornerShape(28.dp)
    val command = RoundedCornerShape(18.dp)
}

@Composable
internal fun MeasurementGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val start = Offset(size.width * 0.25f, size.height * 0.75f)
        val end = Offset(size.width * 0.75f, size.height * 0.25f)
        val radius = size.minDimension * 0.15f
        val stroke = 1.8.dp.toPx()
        drawLine(MeasureUi.ink, start, end, strokeWidth = stroke, cap = StrokeCap.Round)
        listOf(start, end).forEach {
            drawCircle(MeasureUi.lavender, radius, it)
            drawCircle(MeasureUi.ink, radius, it, style = Stroke(stroke))
        }
    }
}

@Composable
internal fun MeasureInfo(description: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(MeasureUi.touch)) {
        Icon(Icons.Outlined.Info, description, tint = MeasureUi.ink, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun MapCommand(icon: ImageVector, description: String, enabled: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = CircleShape, color = MeasureUi.paper,
        contentColor = MeasureUi.ink, shadowElevation = 2.dp, modifier = modifier.size(MeasureUi.touch)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, Modifier.size(24.dp)) }
    }
}

@Composable
internal fun <T> MeasureChoices(choices: List<Pair<T, String>>, selected: T, enabled: Boolean = true,
    onSelected: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(MeasureUi.command).background(MeasureUi.paleGreen).selectableGroup()) {
        choices.forEach { (choice, label) ->
            val isSelected = selected == choice
            Box(Modifier.weight(1f).clip(MeasureUi.command)
                .background(if (isSelected) MeasureUi.green else Color.Transparent)
                .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton,
                    onClick = { onSelected(choice) })
                .heightIn(min = MeasureUi.touch).padding(horizontal = 4.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center) {
                Text(label, color = if (isSelected) MeasureUi.paper else MeasureUi.ink,
                    fontSize = 18.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
internal fun MeasureAction(label: String, modifier: Modifier = Modifier, icon: ImageVector? = null,
    enabled: Boolean = true, primary: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = MeasureUi.touch),
        shape = MeasureUi.command, colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) MeasureUi.green else MeasureUi.paleGreen,
            contentColor = if (primary) MeasureUi.paper else MeasureUi.green,
            disabledContainerColor = MeasureUi.paleGreen.copy(alpha = 0.65f),
            disabledContentColor = MeasureUi.ink.copy(alpha = 0.5f))) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(it, null, Modifier.size(24.dp)) }
            Text(label, fontSize = 18.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        }
    }
}
