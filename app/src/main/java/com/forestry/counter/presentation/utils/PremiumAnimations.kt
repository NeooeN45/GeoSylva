package com.forestry.counter.presentation.utils

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────
// 1. PRESS SCALE – spring physics on tap
// ─────────────────────────────────────────────

/**
 * Applies a spring-physics press-scale effect. Pass [interactionSource] if the
 * composable already owns one (e.g. Button); otherwise a new one is created.
 */
fun Modifier.pressScale(
    pressedScale: Float = 0.94f,
    interactionSource: MutableInteractionSource? = null
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
}

// ─────────────────────────────────────────────
// 2. GLOW CARD MODIFIER – ambient halo shadow
// ─────────────────────────────────────────────

/**
 * Draws a soft radial glow behind the composable. Best used with a transparent
 * card container so the glow bleeds through edges.
 */
fun Modifier.ambientGlow(
    color: Color,
    radius: Float = 80f,
    alpha: Float = 0.18f
): Modifier = drawBehind {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = size.maxDimension * 0.7f
        ),
        radius = size.maxDimension * 0.7f
    )
}

// ─────────────────────────────────────────────
// 3. SHIMMER – loading skeleton effect
// ─────────────────────────────────────────────

/**
 * Applies an animated shimmer sweep to any composable.
 */
fun Modifier.shimmerEffect(
    shimmerColor: Color = Color.White,
    baseColor: Color? = null
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerX by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )
    drawWithContent {
        drawContent()
        val brushColors = listOf(
            shimmerColor.copy(alpha = 0f),
            shimmerColor.copy(alpha = 0.35f),
            shimmerColor.copy(alpha = 0f)
        )
        val startX = shimmerX * size.width
        drawRect(
            brush = Brush.linearGradient(
                colors = brushColors,
                start = Offset(startX - size.width * 0.3f, 0f),
                end = Offset(startX + size.width * 0.3f, size.height)
            )
        )
    }
}

// ─────────────────────────────────────────────
// 4. STAGGER ENTRANCE – animated list items
// ─────────────────────────────────────────────

/**
 * Wraps [content] in a staggered fade+slide entrance animation.
 * [index] drives the delay: index * [staggerMs] ms.
 */
@Composable
fun StaggerEntrance(
    index: Int,
    staggerMs: Int = 60,
    offsetY: Int = 28,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(minOf(index * staggerMs, 300).toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(320, easing = FastOutSlowInEasing)) +
                slideInVertically(tween(320, easing = FastOutSlowInEasing)) { offsetY }
    ) {
        content()
    }
}

// ─────────────────────────────────────────────
// 5. ANIMATED COUNTER – count-up number text
// ─────────────────────────────────────────────

/**
 * Displays an integer value with a smooth count-up animation.
 */
@Composable
fun AnimatedCounter(
    targetValue: Int,
    durationMs: Int = 900,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineMedium,
    fontWeight: FontWeight = FontWeight.Bold,
    color: Color = Color.Unspecified,
    suffix: String = ""
) {
    val animatedValue by animateIntAsState(
        targetValue = targetValue,
        animationSpec = tween(durationMs, easing = FastOutSlowInEasing),
        label = "counter"
    )
    Text(
        text = "$animatedValue$suffix",
        style = style,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier
    )
}

/**
 * Float variant — for decimal values like volumes or revenues.
 */
@Composable
fun AnimatedFloatCounter(
    targetValue: Float,
    durationMs: Int = 900,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineMedium,
    fontWeight: FontWeight = FontWeight.Bold,
    color: Color = Color.Unspecified,
    format: (Float) -> String = { "%.1f".format(it) }
) {
    val animatedValue by animateFloatAsState(
        targetValue = targetValue,
        animationSpec = tween(durationMs, easing = FastOutSlowInEasing),
        label = "floatCounter"
    )
    Text(
        text = format(animatedValue),
        style = style,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier
    )
}

// ─────────────────────────────────────────────
// 6. SCREEN ENTRANCE – slide+fade for Scaffold content
// ─────────────────────────────────────────────

/**
 * Wraps the content column of a screen with a one-shot fade+slide-up entrance.
 */
@Composable
fun ScreenEntrance(
    delayMs: Int = 80,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400, easing = FastOutSlowInEasing)) +
                slideInVertically(tween(400, easing = FastOutSlowInEasing)) { 32 }
    ) {
        content()
    }
}

// ─────────────────────────────────────────────
// 7. DIAGONAL GRADIENT BRUSH helper
// ─────────────────────────────────────────────

/** Creates a consistent diagonal gradient used on premium cards. */
fun diagonalGradient(colors: List<Color>): Brush =
    Brush.linearGradient(
        colors = colors,
        start = Offset.Zero,
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

// ─────────────────────────────────────────────
// 8. PULSING INDICATOR DOT
// ─────────────────────────────────────────────

/**
 * A tiny pulsing dot — useful for "live" or "recording" indicators.
 */
@Composable
fun PulsingDot(
    color: Color,
    sizeDp: Dp = 10.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotScale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .background(color = color, shape = RoundedCornerShape(50))
            .then(Modifier.padding(sizeDp / 2))
    )
}

// ─────────────────────────────────────────────
// 9. REVEAL PROGRESS BAR – animated from 0 to target
// ─────────────────────────────────────────────

@Composable
fun RevealProgressBar(
    targetFraction: Float,
    color: Color,
    trackColor: Color,
    height: Dp = 8.dp,
    cornerRadius: Dp = 4.dp,
    durationMs: Int = 1000,
    modifier: Modifier = Modifier
) {
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMs, easing = FastOutSlowInEasing),
        label = "revealProgress"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedFraction)
                .height(height)
                .background(
                    Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.7f)))
                )
        )
    }
}
