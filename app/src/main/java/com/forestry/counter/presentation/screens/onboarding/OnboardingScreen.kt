package com.forestry.counter.presentation.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EmojiNature
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forestry.counter.R
import kotlinx.coroutines.launch

private enum class PageCategory(val labelFr: String, val color: Color) {
    WELCOME("Bienvenue", Color(0xFF2E7D32)),
    INVENTORY("Inventaire", Color(0xFF00695C)),
    ANALYSIS("Analyse", Color(0xFF1565C0)),
    ECOLOGY("Écologie", Color(0xFF4527A0)),
    EXPORT("Export", Color(0xFF6A1B9A)),
    PRIVACY("RGPD", Color(0xFF37474F))
}

private data class OnboardingPage(
    val icon: ImageVector,
    val titleRes: Int,
    val descRes: Int,
    val bulletResIds: List<Int> = emptyList(),
    val accentColor: Color = Color(0xFF4CAF50),
    val category: PageCategory = PageCategory.INVENTORY,
    val isHero: Boolean = false
)

private val PAGES = listOf(
    OnboardingPage(
        Icons.Default.Park,
        R.string.onboarding_welcome_title,
        R.string.onboarding_welcome_desc,
        listOf(R.string.onboarding_welcome_b1, R.string.onboarding_welcome_b2, R.string.onboarding_welcome_b3),
        Color(0xFF2E7D32), PageCategory.WELCOME, isHero = true
    ),
    OnboardingPage(
        Icons.Default.Forest,
        R.string.onboarding_forest_title,
        R.string.onboarding_forest_desc,
        listOf(R.string.onboarding_forest_b1, R.string.onboarding_forest_b2, R.string.onboarding_forest_b3),
        Color(0xFF388E3C), PageCategory.INVENTORY
    ),
    OnboardingPage(
        Icons.Default.Straighten,
        R.string.onboarding_measure_title,
        R.string.onboarding_measure_desc,
        listOf(R.string.onboarding_measure_b1, R.string.onboarding_measure_b2, R.string.onboarding_measure_b3),
        Color(0xFF00695C), PageCategory.INVENTORY
    ),
    OnboardingPage(
        Icons.Default.Height,
        R.string.onboarding_height_title,
        R.string.onboarding_height_desc,
        listOf(R.string.onboarding_height_b1, R.string.onboarding_height_b2, R.string.onboarding_height_b3),
        Color(0xFF006064), PageCategory.INVENTORY
    ),
    OnboardingPage(
        Icons.Default.GpsFixed,
        R.string.onboarding_gps_title,
        R.string.onboarding_gps_desc,
        listOf(R.string.onboarding_gps_b1, R.string.onboarding_gps_b2, R.string.onboarding_gps_b3),
        Color(0xFF0277BD), PageCategory.INVENTORY
    ),
    OnboardingPage(
        Icons.Default.Map,
        R.string.onboarding_map_title,
        R.string.onboarding_map_desc,
        listOf(R.string.onboarding_map_b1, R.string.onboarding_map_b2, R.string.onboarding_map_b3),
        Color(0xFF1565C0), PageCategory.ANALYSIS
    ),
    OnboardingPage(
        Icons.Default.BarChart,
        R.string.onboarding_synthesis_title,
        R.string.onboarding_synthesis_desc,
        listOf(R.string.onboarding_synthesis_b1, R.string.onboarding_synthesis_b2, R.string.onboarding_synthesis_b3),
        Color(0xFFE65100), PageCategory.ANALYSIS
    ),
    OnboardingPage(
        Icons.Default.Science,
        R.string.onboarding_marking_title,
        R.string.onboarding_marking_desc,
        listOf(R.string.onboarding_marking_b1, R.string.onboarding_marking_b2, R.string.onboarding_marking_b3),
        Color(0xFFC62828), PageCategory.ANALYSIS
    ),
    OnboardingPage(
        Icons.Default.Terrain,
        R.string.onboarding_station_title,
        R.string.onboarding_station_desc,
        listOf(R.string.onboarding_station_b1, R.string.onboarding_station_b2, R.string.onboarding_station_b3),
        Color(0xFF4E342E), PageCategory.ECOLOGY
    ),
    OnboardingPage(
        Icons.Default.WbSunny,
        R.string.onboarding_corr_title,
        R.string.onboarding_corr_desc,
        listOf(R.string.onboarding_corr_b1, R.string.onboarding_corr_b2, R.string.onboarding_corr_b3),
        Color(0xFFEF6C00), PageCategory.ECOLOGY
    ),
    OnboardingPage(
        Icons.Default.Eco,
        R.string.onboarding_flora_title,
        R.string.onboarding_flora_desc,
        listOf(R.string.onboarding_flora_b1, R.string.onboarding_flora_b2, R.string.onboarding_flora_b3),
        Color(0xFF2E7D32), PageCategory.ECOLOGY
    ),
    OnboardingPage(
        Icons.Default.EmojiNature,
        R.string.onboarding_ibp_title,
        R.string.onboarding_ibp_desc,
        listOf(R.string.onboarding_ibp_b1, R.string.onboarding_ibp_b2, R.string.onboarding_ibp_b3),
        Color(0xFF1B5E20), PageCategory.ECOLOGY
    ),
    OnboardingPage(
        Icons.Default.PictureAsPdf,
        R.string.onboarding_export_title,
        R.string.onboarding_export_desc,
        listOf(R.string.onboarding_export_b1, R.string.onboarding_export_b2, R.string.onboarding_export_b3),
        Color(0xFF4527A0), PageCategory.EXPORT
    ),
    OnboardingPage(
        Icons.Default.Security,
        R.string.onboarding_privacy_title,
        R.string.onboarding_privacy_desc,
        listOf(R.string.onboarding_privacy_b1, R.string.onboarding_privacy_b2, R.string.onboarding_privacy_b3),
        Color(0xFF37474F), PageCategory.PRIVACY
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { PAGES.size })
    val scope = rememberCoroutineScope()
    val currentPage = PAGES[pagerState.currentPage]
    val isLastPage = pagerState.currentPage == PAGES.size - 1
    val context = LocalContext.current
    var showDeclineDialog by remember { mutableStateOf(false) }

    val bgColor by animateColorAsState(
        targetValue = currentPage.accentColor.copy(alpha = 0.06f),
        animationSpec = tween(600, easing = EaseInOutCubic),
        label = "bgColor"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Ambient glow background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(bgColor, Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar: category chip + skip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedContent(
                    targetState = currentPage.category,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                    label = "categoryChip"
                ) { cat ->
                    Surface(
                        shape = CircleShape,
                        color = cat.color.copy(alpha = 0.12f),
                        modifier = Modifier.border(
                            width = 1.dp,
                            color = cat.color.copy(alpha = 0.35f),
                            shape = CircleShape
                        )
                    ) {
                        Text(
                            text = cat.labelFr,
                            style = MaterialTheme.typography.labelSmall,
                            color = cat.color,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
                AnimatedVisibility(
                    visible = !isLastPage,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200))
                ) {
                    TextButton(onClick = onComplete) {
                        Text(
                            stringResource(R.string.onboarding_skip),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Progress bar continue
            LinearProgressIndicator(
                progress = { (pagerState.currentPage + 1).toFloat() / PAGES.size },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(3.dp)
                    .clip(CircleShape),
                color = currentPage.accentColor,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) { pageIdx ->
                val offsetFraction = (pagerState.currentPage - pageIdx) + pagerState.currentPageOffsetFraction
                PageContent(
                    page = PAGES[pageIdx],
                    pageOffset = offsetFraction
                )
            }

            // Dot indicators (segmented by category)
            Row(
                modifier = Modifier.padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PAGES.forEachIndexed { index, page ->
                    val isSelected = pagerState.currentPage == index
                    val isPast = index < pagerState.currentPage
                    val dotWidth by animateDpAsState(
                        targetValue = when {
                            isSelected -> 24.dp
                            else -> 7.dp
                        },
                        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                        label = "dotWidth_$index"
                    )
                    val dotColor = when {
                        isSelected -> currentPage.accentColor
                        isPast -> currentPage.accentColor.copy(alpha = 0.45f)
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    }
                    Box(
                        modifier = Modifier
                            .height(7.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }

            // Page counter
            Text(
                text = "${pagerState.currentPage + 1} / ${PAGES.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // CTA Button
            Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                Button(
                    onClick = {
                        if (isLastPage) {
                            onComplete()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = currentPage.accentColor),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    AnimatedContent(
                        targetState = isLastPage,
                        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                        label = "ctaText"
                    ) { last ->
                        Text(
                            text = if (last) stringResource(R.string.onboarding_accept)
                            else stringResource(R.string.onboarding_next),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Decline button — visible only on the RGPD consent page (last page)
            AnimatedVisibility(
                visible = isLastPage,
                enter = fadeIn(tween(250)),
                exit = fadeOut(tween(150))
            ) {
                TextButton(
                    onClick = { showDeclineDialog = true },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_decline),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Decline confirmation dialog
    if (showDeclineDialog) {
        AlertDialog(
            onDismissRequest = { showDeclineDialog = false },
            title = { Text(stringResource(R.string.onboarding_decline)) },
            text = { Text(stringResource(R.string.onboarding_decline_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeclineDialog = false
                    (context as? Activity)?.finishAffinity()
                }) {
                    Text(stringResource(R.string.onboarding_decline))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeclineDialog = false }) {
                    Text(stringResource(R.string.onboarding_accept))
                }
            }
        )
    }
}

@Composable
private fun PageContent(page: OnboardingPage, pageOffset: Float = 0f) {
    val absOffset = kotlin.math.abs(pageOffset)
    val scale by animateFloatAsState(
        targetValue = 1f - (absOffset * 0.08f).coerceIn(0f, 0.08f),
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "pageScale"
    )
    val alpha by animateFloatAsState(
        targetValue = 1f - (absOffset * 0.55f).coerceIn(0f, 0.55f),
        animationSpec = tween(280),
        label = "pageAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Hero icon with layered glow rings
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(156.dp)) {
            // Outer glow ring
            Box(
                modifier = Modifier
                    .size(156.dp)
                    .clip(CircleShape)
                    .background(page.accentColor.copy(alpha = 0.06f))
                    .border(1.dp, page.accentColor.copy(alpha = 0.12f), CircleShape)
            )
            // Middle ring
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(page.accentColor.copy(alpha = 0.10f))
                    .border(1.dp, page.accentColor.copy(alpha = 0.2f), CircleShape)
            )
            // Inner filled circle
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                page.accentColor.copy(alpha = 0.28f),
                                page.accentColor.copy(alpha = 0.14f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                    tint = page.accentColor
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        if (page.isHero) {
            HeroWelcomeContent(page)
        } else {
            StandardPageContent(page)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun HeroWelcomeContent(page: OnboardingPage) {
    Text(
        text = "GeoSylva",
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.ExtraBold,
        color = page.accentColor,
        textAlign = TextAlign.Center,
        letterSpacing = (-0.5).sp
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(page.titleRes).substringAfter(" "),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(14.dp))
    Text(
        text = stringResource(page.descRes),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 26.sp
    )
    Spacer(modifier = Modifier.height(24.dp))
    // Feature badges row
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth()
    ) {
        page.bulletResIds.forEach { resId ->
            FeaturePill(text = stringResource(resId), accentColor = page.accentColor)
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    // Version badge
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Text(
            text = "Version 2.1 · Offline-first · CNPF",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun FeaturePill(text: String, accentColor: Color) {
    val truncated = if (text.length > 22) text.take(20) + "…" else text
    Surface(
        shape = CircleShape,
        color = accentColor.copy(alpha = 0.10f),
        modifier = Modifier.border(1.dp, accentColor.copy(alpha = 0.25f), CircleShape)
    ) {
        Text(
            text = truncated,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            maxLines = 1
        )
    }
}

@Composable
private fun StandardPageContent(page: OnboardingPage) {
    Text(
        text = stringResource(page.titleRes),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onBackground,
        lineHeight = 34.sp
    )
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = stringResource(page.descRes),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 25.sp
    )
    if (page.bulletResIds.isNotEmpty()) {
        Spacer(modifier = Modifier.height(20.dp))
        FeatureCard(page = page)
    }
}

@Composable
private fun FeatureCard(page: OnboardingPage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Brush.linearGradient(
                colors = listOf(
                    page.accentColor.copy(alpha = 0.3f),
                    page.accentColor.copy(alpha = 0.08f)
                )
            )
        )
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            page.bulletResIds.forEachIndexed { index, resId ->
                Row(verticalAlignment = Alignment.Top) {
                    // Numbered badge
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(page.accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = page.accentColor,
                            fontSize = 10.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(resId),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
