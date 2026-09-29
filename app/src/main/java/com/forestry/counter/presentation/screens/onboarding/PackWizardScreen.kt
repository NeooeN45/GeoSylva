package com.forestry.counter.presentation.screens.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forestry.counter.domain.usecase.pack.PackResolver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─── État interne du wizard ────────────────────────────────────────────────────

private sealed class WizardState {
    object Selection : WizardState()
    data class Downloading(
        val progress: Float,
        val packName: String,
        val packsDone: Int,
        val packsTotal: Int,
    ) : WizardState()
    object Done : WizardState()
}

// ─── Données des 96 départements + DOM-TOM ─────────────────────────────────────
// regionCode = code INSEE région (clé RegionFrance.codeINSEE dans PackResolver)

private data class DeptItem(val code: String, val name: String, val regionCode: String)

private val FRENCH_DEPARTMENTS = listOf(
    DeptItem("01", "Ain",                        "84"),
    DeptItem("02", "Aisne",                      "32"),
    DeptItem("03", "Allier",                     "84"),
    DeptItem("04", "Alpes-de-Haute-Provence",    "93"),
    DeptItem("05", "Hautes-Alpes",               "93"),
    DeptItem("06", "Alpes-Maritimes",            "93"),
    DeptItem("07", "Ardèche",                    "84"),
    DeptItem("08", "Ardennes",                   "44"),
    DeptItem("09", "Ariège",                     "76"),
    DeptItem("10", "Aube",                       "44"),
    DeptItem("11", "Aude",                       "76"),
    DeptItem("12", "Aveyron",                    "76"),
    DeptItem("13", "Bouches-du-Rhône",           "93"),
    DeptItem("14", "Calvados",                   "28"),
    DeptItem("15", "Cantal",                     "84"),
    DeptItem("16", "Charente",                   "75"),
    DeptItem("17", "Charente-Maritime",          "75"),
    DeptItem("18", "Cher",                       "24"),
    DeptItem("19", "Corrèze",                    "75"),
    DeptItem("2A", "Corse-du-Sud",               "94"),
    DeptItem("2B", "Haute-Corse",               "94"),
    DeptItem("21", "Côte-d'Or",                 "27"),
    DeptItem("22", "Côtes-d'Armor",             "53"),
    DeptItem("23", "Creuse",                     "75"),
    DeptItem("24", "Dordogne",                   "75"),
    DeptItem("25", "Doubs",                      "27"),
    DeptItem("26", "Drôme",                      "84"),
    DeptItem("27", "Eure",                       "28"),
    DeptItem("28", "Eure-et-Loir",              "24"),
    DeptItem("29", "Finistère",                  "53"),
    DeptItem("30", "Gard",                       "76"),
    DeptItem("31", "Haute-Garonne",              "76"),
    DeptItem("32", "Gers",                       "76"),
    DeptItem("33", "Gironde",                    "75"),
    DeptItem("34", "Hérault",                    "76"),
    DeptItem("35", "Ille-et-Vilaine",            "53"),
    DeptItem("36", "Indre",                      "24"),
    DeptItem("37", "Indre-et-Loire",             "24"),
    DeptItem("38", "Isère",                      "84"),
    DeptItem("39", "Jura",                       "27"),
    DeptItem("40", "Landes",                     "75"),
    DeptItem("41", "Loir-et-Cher",              "24"),
    DeptItem("42", "Loire",                      "84"),
    DeptItem("43", "Haute-Loire",                "84"),
    DeptItem("44", "Loire-Atlantique",           "52"),
    DeptItem("45", "Loiret",                     "24"),
    DeptItem("46", "Lot",                        "76"),
    DeptItem("47", "Lot-et-Garonne",             "75"),
    DeptItem("48", "Lozère",                     "76"),
    DeptItem("49", "Maine-et-Loire",             "52"),
    DeptItem("50", "Manche",                     "28"),
    DeptItem("51", "Marne",                      "44"),
    DeptItem("52", "Haute-Marne",                "44"),
    DeptItem("53", "Mayenne",                    "52"),
    DeptItem("54", "Meurthe-et-Moselle",         "44"),
    DeptItem("55", "Meuse",                      "44"),
    DeptItem("56", "Morbihan",                   "53"),
    DeptItem("57", "Moselle",                    "44"),
    DeptItem("58", "Nièvre",                     "27"),
    DeptItem("59", "Nord",                       "32"),
    DeptItem("60", "Oise",                       "32"),
    DeptItem("61", "Orne",                       "28"),
    DeptItem("62", "Pas-de-Calais",              "32"),
    DeptItem("63", "Puy-de-Dôme",               "84"),
    DeptItem("64", "Pyrénées-Atlantiques",       "75"),
    DeptItem("65", "Hautes-Pyrénées",            "76"),
    DeptItem("66", "Pyrénées-Orientales",        "76"),
    DeptItem("67", "Bas-Rhin",                   "44"),
    DeptItem("68", "Haut-Rhin",                  "44"),
    DeptItem("69", "Rhône",                      "84"),
    DeptItem("70", "Haute-Saône",                "27"),
    DeptItem("71", "Saône-et-Loire",             "27"),
    DeptItem("72", "Sarthe",                     "52"),
    DeptItem("73", "Savoie",                     "84"),
    DeptItem("74", "Haute-Savoie",               "84"),
    DeptItem("75", "Paris",                      "11"),
    DeptItem("76", "Seine-Maritime",             "28"),
    DeptItem("77", "Seine-et-Marne",             "11"),
    DeptItem("78", "Yvelines",                   "11"),
    DeptItem("79", "Deux-Sèvres",               "75"),
    DeptItem("80", "Somme",                      "32"),
    DeptItem("81", "Tarn",                       "76"),
    DeptItem("82", "Tarn-et-Garonne",            "76"),
    DeptItem("83", "Var",                        "93"),
    DeptItem("84", "Vaucluse",                   "93"),
    DeptItem("85", "Vendée",                     "52"),
    DeptItem("86", "Vienne",                     "75"),
    DeptItem("87", "Haute-Vienne",               "75"),
    DeptItem("88", "Vosges",                     "44"),
    DeptItem("89", "Yonne",                      "27"),
    DeptItem("90", "Territoire de Belfort",      "27"),
    DeptItem("91", "Essonne",                    "11"),
    DeptItem("92", "Hauts-de-Seine",             "11"),
    DeptItem("93", "Seine-Saint-Denis",          "11"),
    DeptItem("94", "Val-de-Marne",               "11"),
    DeptItem("95", "Val-d'Oise",                "11"),
    DeptItem("971", "Guadeloupe",               "01"),
    DeptItem("972", "Martinique",               "02"),
    DeptItem("973", "Guyane",                   "03"),
    DeptItem("974", "La Réunion",               "04"),
    DeptItem("976", "Mayotte",                  "06"),
)

// ─── Écran principal ───────────────────────────────────────────────────────────

@Composable
fun PackWizardScreen(
    onComplete: (selectedDeptCodes: Set<String>) -> Unit,
    onSkip: () -> Unit,
) {
    var wizardState: WizardState by remember { mutableStateOf(WizardState.Selection) }
    var searchQuery by remember { mutableStateOf("") }
    val selectedCodes = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()

    val filtered = remember(searchQuery) {
        if (searchQuery.isBlank()) FRENCH_DEPARTMENTS
        else FRENCH_DEPARTMENTS.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.code.startsWith(searchQuery, ignoreCase = true)
        }
    }

    // Packs régionaux couvrant les départements sélectionnés (dédupliqués par région)
    val packsToDownload = remember(selectedCodes.toList()) {
        val regionCodes = selectedCodes.mapNotNull { code ->
            FRENCH_DEPARTMENTS.find { it.code == code }?.regionCode
        }.toSet()
        PackResolver.REGIONAL_CATALOG.filter { it.codeINSEE in regionCodes }
    }
    val estimatedMb = (packsToDownload.sumOf { it.sizeKb } / 1024L).coerceAtLeast(1L)

    // Hissé hors des branches conditionnelles pour respecter les règles Compose
    val rawProgress = (wizardState as? WizardState.Downloading)?.progress ?: 0f
    val animatedProgress by animateFloatAsState(rawProgress, animationSpec = tween(80), label = "dl")

    Scaffold(
        bottomBar = {
            Surface(shadowElevation = 6.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    when (val state = wizardState) {
                        is WizardState.Selection -> {
                            if (selectedCodes.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.padding(bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "${selectedCodes.size} dépt. · ${packsToDownload.size} région(s) · ~$estimatedMb Mo",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    if (selectedCodes.isEmpty()) {
                                        onSkip()
                                    } else {
                                        scope.launch {
                                            val packs = packsToDownload.toList()
                                            packs.forEachIndexed { idx, pack ->
                                                var p = 0f
                                                while (p < 1f) {
                                                    p = (p + 0.04f).coerceAtMost(1f)
                                                    wizardState = WizardState.Downloading(p, pack.name, idx + 1, packs.size)
                                                    delay(28)
                                                }
                                            }
                                            onComplete(selectedCodes.toSet())
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (selectedCodes.isEmpty()) {
                                    Text("Passer pour l'instant")
                                } else {
                                    Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Télécharger les données")
                                }
                            }
                            if (selectedCodes.isNotEmpty()) {
                                TextButton(
                                    onClick = onSkip,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Passer pour l'instant") }
                            }
                        }

                        is WizardState.Downloading -> {
                            Text(
                                "${state.packsDone}/${state.packsTotal} — ${state.packName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        WizardState.Done -> Unit
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 8.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = wizardState is WizardState.Selection,
        ) {
            // ── En-tête pleine largeur ─────────────────────────────────────────
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 28.dp, bottom = 4.dp)) {
                    Icon(
                        Icons.Default.Landscape,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Zones d'intervention",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Sélectionnez vos départements pour télécharger les données forestières ONF disponibles hors-ligne.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))

                    // ── Barre de recherche ────────────────────────────────────
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Search, null,
                                Modifier.size(17.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                decorationBox = { inner ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            "Rechercher un département…",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    inner()
                                },
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(Icons.Default.Clear, null, Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // ── Actions rapides ───────────────────────────────────────
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        TextButton(
                            onClick = {
                                val missing = FRENCH_DEPARTMENTS.map { it.code }.filter { it !in selectedCodes }
                                selectedCodes.addAll(missing)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            enabled = wizardState is WizardState.Selection,
                        ) { Text("Tout sélectionner", style = MaterialTheme.typography.labelSmall) }
                        TextButton(
                            onClick = { selectedCodes.clear() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            enabled = wizardState is WizardState.Selection,
                        ) { Text("Réinitialiser", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }

            // ── Grille des 101 départements ────────────────────────────────────
            items(items = filtered, key = { it.code }) { dept ->
                val selected = dept.code in selectedCodes
                FilterChip(
                    selected = selected,
                    onClick = {
                        if (wizardState is WizardState.Selection) {
                            if (selected) selectedCodes.remove(dept.code)
                            else selectedCodes.add(dept.code)
                        }
                    },
                    label = {
                        Column(modifier = Modifier.padding(vertical = 3.dp)) {
                            Text(
                                dept.code,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                dept.name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
