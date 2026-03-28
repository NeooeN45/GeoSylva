package com.forestry.counter.presentation.screens.forestry

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forestry.counter.domain.calculation.tarifs.TarifCategory
import com.forestry.counter.domain.calculation.tarifs.TarifMethod

/**
 * Écran de documentation sur les méthodes de cubage, formules de calcul et bases de données.
 * Accessible depuis les Paramètres.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TarifDocumentationScreen(onNavigateBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Tarifs", "Formules", "Bases de données")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Documentation cubage",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Tarifs, formules & bases de données",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Onglets
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            when (selectedTab) {
                0 -> TarifsTab()
                1 -> FormulesTab()
                2 -> BasesDedonneesTab()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Onglet 1 — Tarifs
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TarifsTab() {
    var filterCategory by remember { mutableStateOf<TarifCategory?>(null) }
    var expandedMethod by remember { mutableStateOf<TarifMethod?>(null) }

    val methods = remember(filterCategory) {
        if (filterCategory == null) TarifMethod.entries.toList()
        else TarifMethod.entries.filter { it.category == filterCategory }
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Filtres catégorie
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.FilterList, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(null, TarifCategory.UNIVERSEL, TarifCategory.REGIONAL, TarifCategory.SPECIALISE).forEach { cat ->
                    FilterChip(
                        selected = filterCategory == cat,
                        onClick = { filterCategory = cat },
                        label = {
                            Text(
                                cat?.label ?: "Tous",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp
                            )
                        }
                    )
                }
            }
        }

        // Compteur
        item {
            Text(
                "${methods.size} méthode(s) — cliquez pour détails",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // Liste des méthodes
        items(methods) { method ->
            TarifDocCard(
                method = method,
                isExpanded = expandedMethod == method,
                onToggle = { expandedMethod = if (expandedMethod == method) null else method }
            )
        }

        // Note de bas de page
        item {
            DocInfoCard(
                icon = Icons.Default.Info,
                title = "Précision des tarifs",
                content = buildString {
                    appendLine("★★★★★ (5) — Tarif officiel calibré sur données IFN massives ou études FCBA/CRPF spécifiques à l'essence.")
                    appendLine("★★★★☆ (4) — Tarif régional bien calibré ou générique robuste (Algan par essence).")
                    appendLine("★★★☆☆ (3) — Méthode générale acceptable. Schaeffer 2 entrées, Chaudé, IFN rapide.")
                    appendLine("★★☆☆☆ (2) — Approximatif. 1 entrée seulement ou coefficient de forme générique.")
                    appendLine("★☆☆☆☆ (1) — Estimation grossière.")
                }
            )
        }
    }
}

@Composable
private fun TarifDocCard(method: TarifMethod, isExpanded: Boolean, onToggle: () -> Unit) {
    val elevation by animateDpAsState(if (isExpanded) 4.dp else 1.dp, label = "elev")
    val bgColor by animateColorAsState(
        if (isExpanded) MaterialTheme.colorScheme.primaryContainer.copy(0.15f)
        else MaterialTheme.colorScheme.surface,
        label = "bg"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation, RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Fiabilité circle
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            when (method.reliability) {
                                5 -> MaterialTheme.colorScheme.primary.copy(0.2f)
                                4 -> MaterialTheme.colorScheme.tertiary.copy(0.2f)
                                3 -> MaterialTheme.colorScheme.secondary.copy(0.15f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${method.reliability}★",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when (method.reliability) {
                            5 -> MaterialTheme.colorScheme.primary
                            4 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontSize = 10.sp
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            method.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        // Badge catégorie
                        val (badgeTxt, badgeColor) = when (method.category) {
                            TarifCategory.SPECIALISE -> "Spécialisé" to MaterialTheme.colorScheme.primaryContainer
                            TarifCategory.REGIONAL   -> "Régional"   to MaterialTheme.colorScheme.tertiaryContainer
                            TarifCategory.UNIVERSEL  -> "Universel"  to MaterialTheme.colorScheme.surfaceVariant
                        }
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(badgeColor)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(badgeTxt, fontSize = 9.sp, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (method.entrees == 1) "1 entrée (D seul)" else "2 entrées (D + H)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (method.entrees == 2) MaterialTheme.colorScheme.primary.copy(0.8f)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                        method.regionLabel?.let { region ->
                            Text("·", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                region,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary.copy(0.8f),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Détails expandés
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider()
                    // Description complète
                    Text(
                        method.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Essences concernées
                    if (method.specializedEssences.isNotEmpty()) {
                        DocPropertyRow(
                            label = "Essences ciblées",
                            value = method.specializedEssences.joinToString(", ")
                        )
                    }
                    // Fiabilité étoiles
                    DocPropertyRow(
                        label = "Fiabilité",
                        value = "★".repeat(method.reliability) + "☆".repeat(5 - method.reliability) + " (${method.reliability}/5)"
                    )
                    // Région
                    method.regionLabel?.let { region ->
                        DocPropertyRow(label = "Zone géographique", value = region)
                    }
                    // Entrées requises
                    DocPropertyRow(
                        label = "Mesures nécessaires",
                        value = if (method.entrees == 2)
                            "Diamètre (D130) + Hauteur totale (H)"
                        else
                            "Diamètre (D130) uniquement"
                    )
                    // Code interne
                    DocPropertyRow(label = "Code interne", value = method.code, mono = true)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Onglet 2 — Formules
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun FormulesTab() {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            FormulaCard(
                icon = Icons.Default.Calculate,
                title = "Algan / Spécialisés CRPF/FCBA",
                subtitle = "V = a × D^b × H^c",
                content = buildString {
                    appendLine("Formule puissance à 2 entrées (diamètre + hauteur).")
                    appendLine()
                    appendLine("• D = diamètre à 1,30 m (cm)")
                    appendLine("• H = hauteur totale (m)")
                    appendLine("• a, b, c = coefficients par essence/région")
                    appendLine()
                    appendLine("Les tarifs spécialisés (CRPF NA, FCBA, ONF) utilisent")
                    appendLine("cette même formule avec des coefficients calibrés sur")
                    appendLine("des mesures de terrain spécifiques à chaque essence")
                    appendLine("ou chaque région forestière.")
                }
            )
        }
        item {
            FormulaCard(
                icon = Icons.Default.Calculate,
                title = "Schaeffer 1 entrée",
                subtitle = "V = a + b × C₁₃₀²",
                content = buildString {
                    appendLine("Tarif à 1 entrée basé sur la circonférence (Schaeffer, 1949).")
                    appendLine()
                    appendLine("• C₁₃₀ = circonférence à 1,30 m (m)")
                    appendLine("• a, b = coefficients du tarif (n° 1–16)")
                    appendLine()
                    appendLine("Simple, rapide, mais moins précis sans hauteur.")
                    appendLine("Utiliser le n° de tarif recommandé pour l'essence.")
                }
            )
        }
        item {
            FormulaCard(
                icon = Icons.Default.Calculate,
                title = "Schaeffer 2 entrées",
                subtitle = "V = a + b × C₁₃₀² × H",
                content = buildString {
                    appendLine("Tarif à 2 entrées (Schaeffer, 1949).")
                    appendLine()
                    appendLine("• C₁₃₀ = circonférence à 1,30 m (m)")
                    appendLine("• H = hauteur totale (m)")
                    appendLine("• a, b = coefficients (n° 1–8)")
                    appendLine()
                    appendLine("Méthode historique, bonne précision pour futaies tempérées.")
                }
            )
        }
        item {
            FormulaCard(
                icon = Icons.Default.Calculate,
                title = "Tarifs IFN Rapide (1E)",
                subtitle = "V = a₀ + a₁D + a₂D²",
                content = buildString {
                    appendLine("36 tarifs à 1 entrée de l'Inventaire Forestier National.")
                    appendLine()
                    appendLine("• D = diamètre à 1,30 m (cm)")
                    appendLine("• a₀, a₁, a₂ = coefficients du tarif n° 1–36")
                    appendLine()
                    appendLine("Le numéro de tarif est recommandé par essence.")
                    appendLine("Utiliser sans mesure de hauteur (peuplements hétérogènes).")
                }
            )
        }
        item {
            FormulaCard(
                icon = Icons.Default.Calculate,
                title = "Tarifs IFN Lent (2E)",
                subtitle = "V = a₀ + a₁D² + a₂D²H",
                content = buildString {
                    appendLine("Tables IFN à 2 entrées — référence nationale française.")
                    appendLine()
                    appendLine("• D = diamètre à 1,30 m (cm)")
                    appendLine("• H = hauteur totale (m)")
                    appendLine("• a₀, a₁, a₂ = coefficients (n° 1–8)")
                    appendLine()
                    appendLine("Méthode de référence. Coefficients calibrés sur les")
                    appendLine("données massives des inventaires IFN (millions d'arbres).")
                    appendLine("Précision maximale parmi les tarifs universels.")
                }
            )
        }
        item {
            FormulaCard(
                icon = Icons.Default.Calculate,
                title = "FGH (coef. forme explicite)",
                subtitle = "V = F × G × H",
                content = buildString {
                    appendLine("Méthode basée sur la section transversale.")
                    appendLine()
                    appendLine("• F = coefficient de forme (0,4–0,6 typique)")
                    appendLine("• G = surface terrière = π/4 × (D/100)² (m²)")
                    appendLine("• H = hauteur totale (m)")
                    appendLine()
                    appendLine("Permet de saisir F manuellement selon la station.")
                    appendLine("Utile pour des peuplements atypiques ou de la recherche.")
                }
            )
        }
        item {
            FormulaCard(
                icon = Icons.Default.Calculate,
                title = "Coefficient de forme",
                subtitle = "V = G × H × f",
                content = buildString {
                    appendLine("Variante simplifiée de FGH.")
                    appendLine()
                    appendLine("• G = surface terrière (m²)")
                    appendLine("• H = hauteur totale (m)")
                    appendLine("• f = coefficient de forme/décroissance par essence")
                    appendLine("  (Pardé & Bouchon 1988)")
                    appendLine()
                    appendLine("Estimation rapide mais moins précise.")
                }
            )
        }
        item {
            FormulaCard(
                icon = Icons.Default.Calculate,
                title = "Chaudé (arbres sur pied / taillis)",
                subtitle = "V = a × C^b",
                content = buildString {
                    appendLine("Tarif à décroissances variables (Pierre Chaudé, 1991).")
                    appendLine()
                    appendLine("• C₁₃₀ = circonférence à 1,30 m (décimètres !)")
                    appendLine("• a, b = coefficients par classe sylvicole")
                    appendLine()
                    appendLine("Classes futaie : F1 (peupliers), F2 (hêtre/ch. taillis),")
                    appendLine("F3 (ch. futaie/méz./mél.), F4 (résineux à forte croissance).")
                    appendLine()
                    appendLine("Classes taillis : T1 (divers), T2 (châtaignier), T3 (chênes).")
                    appendLine()
                    appendLine("Ne nécessite pas de mesure de hauteur.")
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Onglet 3 — Bases de données
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun BasesDedonneesTab() {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            DocDbCard(
                icon = Icons.Default.Storage,
                title = "Base Algan",
                subtitle = "Coefficients par essence — embarqué hors-ligne",
                content = buildString {
                    appendLine("Coefficients a, b, c pour V = a × D^b × H^c.")
                    appendLine("Source : Algan (1958), complété Pardé & Bouchon (1988).")
                    appendLine()
                    appendLine("Contenu :")
                    appendLine("• ~80 essences françaises et d'introduction")
                    appendLine("• Feuillus : Hêtre, Chênes (sessile, pédonculé, pubescent,")
                    appendLine("  vert, liège, tauzin), Charme, Ormes, Frênes, Érables,")
                    appendLine("  Châtaignier, Robinier, Peupliers, Aulnes, Bouleaux,")
                    appendLine("  Saules, Tilleuls, Merisier, Noyer, Platane, Alisier...")
                    appendLine("• Résineux : Pin sylvestre, Pin maritime, Douglas vert,")
                    appendLine("  Sapin pectiné, Épicéa commun, Mélèzes, Cèdres,")
                    appendLine("  Pins divers, Sequoia, Thuya, Cyprès...")
                    appendLine()
                    appendLine("Usage : tarif ALGAN dans le menu de cubage.")
                }
            )
        }
        item {
            DocDbCard(
                icon = Icons.Default.Storage,
                title = "Base IFN Rapide (1 entrée)",
                subtitle = "36 tarifs — Inventaire Forestier National",
                content = buildString {
                    appendLine("Tarifs à 1 entrée de l'Inventaire Forestier National.")
                    appendLine("V = a₀ + a₁D + a₂D²")
                    appendLine()
                    appendLine("36 numéros de tarif, chaque essence/groupe d'essences")
                    appendLine("ayant un numéro recommandé.")
                    appendLine()
                    appendLine("Mapping essence → n° : intégré dans l'application.")
                    appendLine("En l'absence de mapping, n° 4 appliqué par défaut.")
                }
            )
        }
        item {
            DocDbCard(
                icon = Icons.Default.Storage,
                title = "Base IFN Lent (2 entrées)",
                subtitle = "8 tarifs — Inventaire Forestier National",
                content = buildString {
                    appendLine("Tables à 2 entrées (D + H) de l'IFN.")
                    appendLine("V = a₀ + a₁D² + a₂D²H")
                    appendLine()
                    appendLine("8 numéros de tarif correspondant à différents groupes")
                    appendLine("d'essences et types de peuplements.")
                    appendLine()
                    appendLine("Ces tarifs sont basés sur les mesures de millions d'arbres")
                    appendLine("lors des campagnes d'inventaire IFN — référence nationale.")
                }
            )
        }
        item {
            DocDbCard(
                icon = Icons.Default.Science,
                title = "Base Tarifs Spécialisés CRPF/FCBA",
                subtitle = "12 tarifs régionaux/par essence — embarqué hors-ligne",
                content = buildString {
                    appendLine("Tarifs V = a × D^b × H^c avec coefficients calibrés spécifiquement.")
                    appendLine()
                    appendLine("Tarifs disponibles :")
                    appendLine("• CRPF NA — Pin maritime (Landes de Gascogne)")
                    appendLine("  Calibré : AFOCEL 1985, CRPF Nouvelle-Aquitaine 1996")
                    appendLine()
                    appendLine("• FCBA — Douglas vert (toutes régions)")
                    appendLine("  Calibré : FCBA (ex-AFOCEL/CTBA), rapport 2012")
                    appendLine()
                    appendLine("• FCBA — Peuplier hybride plantation")
                    appendLine("  Calibré : FCBA/CTBA, clones I-214, Beaupré, Soligo")
                    appendLine()
                    appendLine("• ONF — Hêtre futaie régulière")
                    appendLine("  Source : ONF, Guide sylvicole du hêtre (2006)")
                    appendLine()
                    appendLine("• CRPF AURA — Sapin/Épicéa Alpes (800–1800 m)")
                    appendLine("• CRPF Grand Est — Épicéa Vosges (400–1000 m)")
                    appendLine("• CRPF — Châtaignier (NA + Occitanie)")
                    appendLine("• CRPF AURA — Pin sylvestre Massif Central")
                    appendLine("• CRPF — Pin Laricio (Corse + continent)")
                    appendLine("• CRPF — Robinier faux-acacia (IDF + Centre)")
                    appendLine("• CRPF — Chêne atlantique (NA + Centre)")
                    appendLine("• FCBA/CIRAD — Eucalyptus plantation (SO France)")
                }
            )
        }
        item {
            DocDbCard(
                icon = Icons.Default.Storage,
                title = "Base Chaudé (arbres + taillis)",
                subtitle = "Tables Pierre Chaudé 1991 — embarqué hors-ligne",
                content = buildString {
                    appendLine("Tarif à décroissances variables — V = a × C^b")
                    appendLine("C₁₃₀ en décimètres.")
                    appendLine()
                    appendLine("Classes futaie (F1–F4) :")
                    appendLine("• F1 : Peupliers et bois exotiques à forte croissance")
                    appendLine("• F2 : Hêtre, chêne en taillis-sous-futaie, charme")
                    appendLine("• F3 : Chêne en futaie, mézéréon, mélèze")
                    appendLine("• F4 : Résineux (Douglas, sapin, épicéa, mélèzes, cèdres)")
                    appendLine()
                    appendLine("Classes taillis (T1–T3) :")
                    appendLine("• T1 : Divers feuillus, charme, bouleau, tremble")
                    appendLine("• T2 : Châtaignier")
                    appendLine("• T3 : Chêne sessile, pédonculé et autres chênes")
                }
            )
        }
        item {
            DocDbCard(
                icon = Icons.Default.BookmarkBorder,
                title = "Base Florule française",
                subtitle = "Essences forestières françaises — embarqué hors-ligne",
                content = buildString {
                    appendLine("Liste de référence des essences forestières françaises")
                    appendLine("avec codes normalisés utilisés dans l'application.")
                    appendLine()
                    appendLine("Contenu :")
                    appendLine("• Feuillus et résineux indigènes")
                    appendLine("• Essences d'introduction courantes (reboisement)")
                    appendLine("• Codes IFN harmonisés")
                    appendLine("• Nom scientifique + nom commun")
                    appendLine()
                    appendLine("Source : IFN France, INRAE, CNPF.")
                }
            )
        }
        item {
            DocDbCard(
                icon = Icons.Default.LocationOn,
                title = "Index géographique TerritorialResolver",
                subtitle = "Départements, régions, altitudes — embarqué hors-ligne",
                content = buildString {
                    appendLine("Base embarquée pour résolution géographique par GPS.")
                    appendLine()
                    appendLine("Contenu :")
                    appendLine("• 101 départements français (métropole + DOM)")
                    appendLine("• 18 régions administratives")
                    appendLine("• Centroïdes et boîtes englobantes")
                    appendLine("• 52 points de référence d'altitude pour interpolation IDW")
                    appendLine()
                    appendLine("Usage : auto-remplissage des champs département,")
                    appendLine("région, altitude estimée depuis les coordonnées GPS.")
                    appendLine()
                    appendLine("Méthode : Inverse Distance Weighting (IDW) pour l'altitude.")
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Composants utilitaires
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DocPropertyRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(130.dp),
            fontSize = 11.sp
        )
        Text(
            value,
            style = if (mono) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = if (mono) 10.sp else 12.sp
        )
    }
}

@Composable
private fun FormulaCard(icon: ImageVector, title: String, subtitle: String, content: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                content.trimEnd(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun DocDbCard(icon: ImageVector, title: String, subtitle: String, content: String) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        content.trimEnd(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DocInfoCard(icon: ImageVector, title: String, content: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.tertiary)
            Column {
                Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    content.trimEnd(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
            }
        }
    }
}
