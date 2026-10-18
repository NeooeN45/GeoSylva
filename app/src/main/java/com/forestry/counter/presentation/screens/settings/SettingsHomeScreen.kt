package com.forestry.counter.presentation.screens.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyColumnItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forestry.counter.R
import com.forestry.counter.presentation.components.CompactPageHeader
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.Motion
import com.forestry.counter.presentation.theme.Space
import com.forestry.counter.presentation.theme.Touch
import java.text.Normalizer
import java.util.Locale

/**
 * Point d'entrée des Réglages — recherche et catégories.
 *
 * L'ancien écran unique (onze sections empilées, ~1700 lignes) reste en
 * place tel quel : le réécrire entièrement d'un bloc aurait été le seul
 * moyen de le remplacer, avec un risque de régression bien réel sur des
 * flux déjà en production (export, tarifs, sauvegardes). Cet écran ajoute
 * ce qui manquait — un point d'entrée clair et une recherche — et navigue
 * vers la section demandée dans [SettingsScreen], qui y défile directement
 * (voir `sectionAnchors` / `targetSection`).
 */
private data class SettingsCategory(
    val id: String,
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: ImageVector,
    /** Clé de catégorie — "compte" route vers AccountScreen, les autres vers une sous-page Réglages filtrée. */
    val targetSection: String,
)

private val CATEGORIES = listOf(
    SettingsCategory(
        id = "metier",
        titleRes = R.string.settings_category_profession,
        subtitleRes = R.string.settings_category_profession_sub,
        icon = Icons.Filled.Person,
        targetSection = "metier",
    ),
    SettingsCategory(
        id = "compte",
        titleRes = R.string.settings_category_account,
        subtitleRes = R.string.settings_category_account_sub,
        icon = Icons.Filled.Person,
        targetSection = "compte",
    ),
    SettingsCategory(
        id = "apparence",
        titleRes = R.string.settings_category_appearance,
        subtitleRes = R.string.settings_category_appearance_sub,
        icon = Icons.Filled.Palette,
        targetSection = "apparence",
    ),
    SettingsCategory(
        id = "foret",
        titleRes = R.string.settings_category_forestry,
        subtitleRes = R.string.settings_category_forestry_sub,
        icon = Icons.Filled.Forest,
        targetSection = "foret",
    ),
    SettingsCategory(
        id = "interaction",
        titleRes = R.string.settings_category_interaction,
        subtitleRes = R.string.settings_category_interaction_sub,
        icon = Icons.Filled.TouchApp,
        targetSection = "interaction",
    ),
    SettingsCategory(
        id = "donnees",
        titleRes = R.string.settings_category_data,
        subtitleRes = R.string.settings_category_data_sub,
        icon = Icons.Filled.Backup,
        targetSection = "donnees",
    ),
    SettingsCategory(
        id = "packs",
        titleRes = R.string.packs_title,
        subtitleRes = R.string.settings_category_packs_sub,
        icon = Icons.Filled.Download,
        targetSection = "packs",
    ),
    SettingsCategory(
        id = "a_propos",
        titleRes = R.string.settings_category_about,
        subtitleRes = R.string.settings_category_about_sub,
        icon = Icons.Filled.Info,
        targetSection = "a_propos",
    ),
)

/** Une entrée cherchable — libellé affiché, mots-clés de correspondance, section cible. */
private data class SearchEntry(val labelRes: Int, val keywords: String, val sectionId: String)

private val SEARCH_INDEX = listOf(
    SearchEntry(R.string.settings_category_profession, "metier profession profil onf cnpf ddt etudiant chercheur", "metier"),
    SearchEntry(R.string.settings_account_title, "compte connexion identite quintessences google", "compte"),
    SearchEntry(R.string.theme, "theme sombre clair jour nuit apparence", "apparence"),
    SearchEntry(R.string.language, "langue francais anglais traduction", "apparence"),
    SearchEntry(R.string.font_size, "police taille texte lisibilite", "apparence"),
    SearchEntry(R.string.accent_color, "couleur accent palette", "apparence"),
    SearchEntry(R.string.settings_section_tarifs, "tarif cubage schaeffer algan ifn volume", "foret"),
    SearchEntry(R.string.settings_section_products_prices, "prix produit bareme vente", "foret"),
    SearchEntry(R.string.settings_section_offline_map, "carte hors ligne tuile telechargement gps", "foret"),
    SearchEntry(R.string.packs_title, "packs donnees regions departements hors ligne telechargement versions", "packs"),
    SearchEntry(R.string.settings_section_forestry_exports, "export csv geojson gpx excel", "foret"),
    SearchEntry(R.string.settings_section_interaction, "vibration haptique son animation", "interaction"),
    SearchEntry(R.string.settings_section_data, "donnees import stockage", "donnees"),
    SearchEntry(R.string.settings_section_privacy, "confidentialite rgpd suppression donnees personnelles", "donnees"),
    SearchEntry(R.string.settings_crash_logs_title, "journal crash erreur bug", "donnees"),
    SearchEntry(R.string.settings_section_backups, "sauvegarde automatique restauration", "donnees"),
    SearchEntry(R.string.settings_section_about, "a propos version application", "a_propos"),
)

/**
 * Distance d'édition bornée — tolère les fautes de frappe sans coût
 * excessif : un utilisateur qui tape "temz" doit quand même trouver "thème".
 */
private fun boundedLevenshtein(a: String, b: String, max: Int = 2): Int {
    if (kotlin.math.abs(a.length - b.length) > max) return max + 1
    val dp = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) dp[i][0] = i
    for (j in 0..b.length) dp[0][j] = j
    for (i in 1..a.length) {
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            dp[i][j] = minOf(
                dp[i - 1][j] + 1,
                dp[i][j - 1] + 1,
                dp[i - 1][j - 1] + cost,
            )
        }
    }
    return dp[a.length][b.length]
}

private fun normalize(s: String): String =
    Normalizer.normalize(s.lowercase(Locale.FRENCH), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

private fun matchesQuery(query: String, haystack: String): Boolean {
    val words = haystack.split(" ").filter { it.isNotBlank() }
    return words.any { word ->
        word.contains(query) || query.contains(word) ||
            (query.length >= 3 && boundedLevenshtein(query, word) <= 2)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHomeScreen(
    onNavigateToSection: (String) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) query = spoken
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CompactPageHeader(
                title = stringResource(R.string.settings),
                onBack = onNavigateBack,
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Pill compacte plutôt qu'un OutlinedTextField Material standard :
            // celui-ci réserve une hauteur pensée pour un libellé flottant
            // (~64dp) même réduit au seul placeholder — bien plus que
            // nécessaire pour une simple recherche, et perçu comme
            // disproportionné par rapport au reste de l'écran.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.screenH, vertical = Space.xs)
                    .height(48.dp),
                shape = GsShape.pill,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(Space.xs))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                stringResource(R.string.settings_search_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.cd_clear),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    IconButton(
                        modifier = Modifier.size(36.dp),
                        onClick = {
                            val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                )
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                            }
                            try {
                                voiceLauncher.launch(intent)
                            } catch (_: ActivityNotFoundException) {
                                // Aucun moteur de reconnaissance vocale installé — dégradation
                                // silencieuse, la recherche texte reste pleinement utilisable.
                            }
                        },
                    ) {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = stringResource(R.string.settings_search_voice_cd),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            AnimatedContent(
                targetState = query.isBlank(),
                label = "settingsHomeContent",
                transitionSpec = { fadeIn(Motion.springSnappy()) togetherWith fadeOut(Motion.springSnappy()) },
            ) { showCategories ->
                if (showCategories) {
                    CategoryGrid(onCategoryClick = { onNavigateToSection(it.targetSection) })
                } else {
                    SearchResults(
                        query = normalize(query),
                        onResultClick = onNavigateToSection,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryGrid(onCategoryClick: (SettingsCategory) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(Space.screenH),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(CATEGORIES, key = { it.id }) { category ->
            CategoryCard(category, onClick = { onCategoryClick(category) })
        }
    }
}

@Composable
private fun CategoryCard(category: SettingsCategory, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) Motion.PRESS_SCALE else 1f,
        animationSpec = Motion.springSnappy(),
        label = "categoryCardScale",
    )

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .scale(scale),
        shape = GsShape.lg,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        interactionSource = interactionSource,
    ) {
        Column(
            modifier = Modifier.padding(Space.md).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Column {
                Text(
                    text = stringResource(category.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(category.subtitleRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SearchResults(query: String, onResultClick: (String) -> Unit) {
    val results = remember(query) {
        SEARCH_INDEX.filter { entry -> matchesQuery(query, entry.keywords) }
    }
    if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.settings_search_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        lazyColumnItems(results, key = { it.labelRes.toString() + it.sectionId }) { entry ->
            ListItem(
                headlineContent = { Text(stringResource(entry.labelRes)) },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.xs)
                    .clickable { onResultClick(entry.sectionId) },
            )
        }
    }
}
