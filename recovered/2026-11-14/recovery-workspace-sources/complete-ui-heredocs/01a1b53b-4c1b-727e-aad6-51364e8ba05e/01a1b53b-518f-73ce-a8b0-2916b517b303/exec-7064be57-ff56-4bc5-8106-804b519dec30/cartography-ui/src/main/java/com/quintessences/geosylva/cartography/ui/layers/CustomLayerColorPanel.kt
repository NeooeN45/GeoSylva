package com.quintessences.geosylva.cartography.ui.layers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quintessences.geosylva.cartography.ui.R
import kotlin.math.roundToInt

/** Shared strict RGBA picker: previews are callbacks only; storage occurs on the host's explicit Use/Save. */
@Composable
fun CustomLayerColorPanel(
    currentArgb: Long,
    input: String,
    valid: Boolean,
    onInput: (String) -> Unit,
    onCancel: () -> Unit,
    onUse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val parsed = parseLayerColor(input)
    val effective = parsed ?: currentArgb
    val hsv = remember(effective) { FloatArray(3).also { android.graphics.Color.colorToHSV(effective.toInt(), it) } }
    var hue by remember(currentArgb) { mutableFloatStateOf(hsv[0]) }
    LaunchedEffect(effective) { if (hsv[1] > 0f && hsv[2] > 0f) hue = hsv[0] }
    val alpha = ((effective ushr 24) and 0xFF).toFloat() / 255f
    fun emit(h: Float = hue, s: Float = hsv[1], v: Float = hsv[2], a: Float = alpha) {
        val color = android.graphics.Color.HSVToColor((a.coerceIn(0f, 1f) * 255).roundToInt(), floatArrayOf(h, s, v))
        onInput("#%08X".format(color.toLong() and 0xFFFFFFFFL))
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val satLabel = stringResource(R.string.layer_saturation)
        val brightnessLabel = stringResource(R.string.layer_brightness)
        val latestEmit by rememberUpdatedState<(Offset, Float, Float) -> Unit>({ point, width, height ->
            emit(s = (point.x / width).coerceIn(0f, 1f), v = (1f - point.y / height).coerceIn(0f, 1f))
        })
        Canvas(Modifier.fillMaxWidth().height(102.dp).clip(RoundedCornerShape(14.dp))
            .semantics { contentDescription = "$satLabel · $brightnessLabel" }
            .pointerInput(Unit) { detectTapGestures { latestEmit(it, size.width.toFloat(), size.height.toFloat()) } }
            .pointerInput(Unit) { detectDragGestures(onDragStart = { latestEmit(it, size.width.toFloat(), size.height.toFloat()) }) { change, _ ->
                change.consume(); latestEmit(change.position, size.width.toFloat(), size.height.toFloat())
            } }) {
            val full = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
            drawRect(Brush.horizontalGradient(listOf(Color.White, full)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(hsv[1] * size.width, (1f - hsv[2]) * size.height), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        }
        ColorSlider(stringResource(R.string.layer_hue), hue / 360f, { hue = it * 360f; emit(h = hue) })
        // Separate controls make the two-dimensional swatch accessible to screen readers and keyboard users.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) { ColorSlider(satLabel, hsv[1], { emit(s = it) }) }
            Column(Modifier.weight(1f)) { ColorSlider(brightnessLabel, hsv[2], { emit(v = it) }) }
        }
        ColorSlider(stringResource(R.string.layer_alpha), alpha, { emit(a = it) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Swatch(currentArgb, stringResource(R.string.layer_current))
            Swatch(effective, stringResource(R.string.layer_new))
        }
        OutlinedTextField(input, onInput, Modifier.fillMaxWidth(), singleLine = true, isError = !valid,
            label = { Text(stringResource(R.string.layer_color_code)) },
            supportingText = { Text(stringResource(if (valid) R.string.layer_color_hint else R.string.layer_color_invalid)) },
            shape = RoundedCornerShape(12.dp))
        TwoActions(stringResource(R.string.layer_cancel), onCancel, stringResource(R.string.layer_use), onUse, valid && parsed != null)
    }
}
@Composable private fun ColorSlider(label: String, value: Float, onValue: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.widthIn(min = 58.dp), fontSize = 10.sp)
        Slider(value.coerceIn(0f, 1f), onValue, Modifier.weight(1f).heightIn(min = 36.dp).semantics { contentDescription = label })
    }
}
@Composable private fun Swatch(argb: Long, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(25.dp).clip(RoundedCornerShape(6.dp)).background(Color(argb)))
        Text(label, Modifier.padding(start = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
fun parseLayerColor(value: String): Long? {
    val hex = value.trim().removePrefix("#")
    if (hex.length !in listOf(6, 8) || hex.any { it !in "0123456789abcdefABCDEF" }) return null
    return hex.toLongOrNull(16)?.let { if (hex.length == 6) 0xFF000000 or it else it }
}
