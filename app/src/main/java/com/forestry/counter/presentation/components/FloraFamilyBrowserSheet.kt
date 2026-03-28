package com.forestry.counter.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forestry.counter.domain.usecase.florist.*

// ══════════════════════════════════════════════════════════════════════════════
//  FloraFamilyBrowserSheet — 3e porte de saisie floristique
//
//  Permet de sélectionner des espèces par :
//  - navigation par familles botaniques
//  - listes d'espèces fréquentes par contexte (milieu)
//  - filtres par strate végétale
//  - indicateurs visuels de valeur écologique
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloraFamilyBrowserSheet(
    selectedIds: List<String>,
    contextMilieu: TypeMilieu? = null,
    onSpeciesToggled: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var activeCategory by remember { mutableStateOf<BrowserCategory>(BrowserCategory.FREQUENT) }
    var activeStrateFilter by remember { mutableStateOf<StrateVegetale?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle       = {
            Column(
                modifier            = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HorizontalDivider(
                    modifier  = Modifier.width(36.dp).clip(RoundedCornerShape(2.dp)),
                    thickness = 4.dp,
                    color     = MaterialTheme.colorScheme.outlineVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Ajouter une espèce",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (selectedIds.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                "${selectedIds.size} sélectionnée${if (selectedIds.size > 1) "s" else ""}",
                                style    = MaterialTheme.typography.labelSmall,
                                color    = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.9f)
                .padding(bottom = 16.dp)
        ) {
            // ── Recherche rapide ───────────────────────────────────────────
            OutlinedTextField(
                value       = searchQuery,
                onValueChange = { searchQuery = it },
                modifier    = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                placeholder = { Text("Chercher dans la liste…") },
                leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty())
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, null, modifier = Modifier.size(16.dp))
                        }
                },
                singleLine  = true,
                shape       = RoundedCornerShape(12.dp)
            )

            // ── Catégories ─────────────────────────────────────────────────
            LazyRow(
                modifier            = Modifier.fillMaxWidth(),
                contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(browserCategories(contextMilieu)) { cat ->
                    FilterChip(
                        selected  = activeCategory == cat,
                        onClick   = { activeCategory = cat },
                        label     = { Text(cat.labelFr, style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = {
                            Icon(cat.icon, null, modifier = Modifier.size(14.dp))
                        }
                    )
                }
            }

            // ── Filtre strate ──────────────────────────────────────────────
            if (activeCategory == BrowserCategory.BY_STRATE) {
                LazyRow(
                    modifier            = Modifier.fillMaxWidth(),
                    contentPadding      = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        FilterChip(
                            selected = activeStrateFilter == null,
                            onClick  = { activeStrateFilter = null },
                            label    = { Text("Toutes", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    items(StrateVegetale.values()) { strate ->
                        FilterChip(
                            selected = activeStrateFilter == strate,
                            onClick  = { activeStrateFilter = if (activeStrateFilter == strate) null else strate },
                            label    = { Text(strate.labelFr.split(" ").first(), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            // ── Liste d'espèces ────────────────────────────────────────────
            val species = remember(activeCategory, activeStrateFilter, contextMilieu, searchQuery) {
                buildSpeciesList(activeCategory, activeStrateFilter, contextMilieu, searchQuery)
            }

            if (species.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, null,
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Aucune espèce trouvée",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier            = Modifier.fillMaxSize(),
                    contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(species, key = { it.id }) { sp ->
                        SpeciesBrowserRow(
                            espece     = sp,
                            isSelected = sp.id in selectedIds,
                            onToggle   = { onSpeciesToggled(sp.id) }
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Ligne d'espèce dans le browser
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SpeciesBrowserRow(
    espece: EspeceVegetale,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
             else Color.Transparent

    Surface(
        color    = bg,
        shape    = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Indicateur sélection
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) Icon(Icons.Default.Check, null,
                    tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    espece.taxonomie.nomFrancais,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Text(
                    espece.taxonomie.nomScientifique,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Badges indicateurs écologiques
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (espece.valeurIndicatrice.indicateurHydromorphie) {
                    EcoBadge("H", Color(0xFF1565C0), "Hygrophyte")
                }
                if (espece.valeurIndicatrice.indicateurPerturbation) {
                    EcoBadge("P", Color(0xFFE65100), "Perturbation")
                }
                if (espece.valeurIndicatrice.indicateurFertilisation) {
                    EcoBadge("N", Color(0xFF4CAF50), "Nitrophile")
                }
                StrateCircle(espece.classification.strateVegetale)
            }
        }
    }
}

@Composable
private fun EcoBadge(letter: String, color: Color, contentDesc: String) {
    Box(
        modifier         = Modifier.size(18.dp).background(color.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(letter, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
}

@Composable
private fun StrateCircle(strate: StrateVegetale) {
    val color = when (strate) {
        StrateVegetale.ARBRE        -> Color(0xFF1B5E20)
        StrateVegetale.ARBUSTE      -> Color(0xFF388E3C)
        StrateVegetale.SOUS_ARBUSTE -> Color(0xFF66BB6A)
        StrateVegetale.HERBACEE     -> Color(0xFF8BC34A)
        StrateVegetale.LIANE        -> Color(0xFF558B2F)
        StrateVegetale.MOUSSE       -> Color(0xFFA5D6A7)
    }
    Box(
        modifier         = Modifier.size(10.dp).background(color, CircleShape),
        contentAlignment = Alignment.Center
    ) {}
}

// ─────────────────────────────────────────────────────────────────────────────
//  Catégories de navigation
// ─────────────────────────────────────────────────────────────────────────────

enum class BrowserCategory(
    val labelFr: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    FREQUENT(   "Fréquentes",    Icons.Default.Star),
    BY_STRATE(  "Par strate",    Icons.Default.Layers),
    HYGROPHYTES("Hygrophytes",   Icons.Default.WaterDrop),
    INVASIVES(  "Invasives",     Icons.Default.Warning),
    INDICATORS( "Indicatrices",  Icons.Default.Analytics)
}

private fun browserCategories(context: TypeMilieu?): List<BrowserCategory> {
    val base = mutableListOf(
        BrowserCategory.FREQUENT,
        BrowserCategory.BY_STRATE,
        BrowserCategory.INDICATORS
    )
    if (context in listOf(TypeMilieu.ZONE_HUMIDE, TypeMilieu.RIPISYLVE, TypeMilieu.TOURBIERE)) {
        base.add(1, BrowserCategory.HYGROPHYTES)
    }
    base += BrowserCategory.INVASIVES
    return base
}

private fun buildSpeciesList(
    category: BrowserCategory,
    strateFilter: StrateVegetale?,
    contextMilieu: TypeMilieu?,
    query: String
): List<EspeceVegetale> {
    var species = when (category) {
        BrowserCategory.FREQUENT    -> FloristDatabase.species.sortedByDescending { it.importanceForestiere.roleForestier.ordinal }
        BrowserCategory.BY_STRATE   -> if (strateFilter != null)
            FloristDatabase.species.filter { it.classification.strateVegetale == strateFilter }
            else FloristDatabase.species
        BrowserCategory.HYGROPHYTES -> FloristDatabase.species.filter { it.valeurIndicatrice.indicateurHydromorphie }
        BrowserCategory.INVASIVES   -> FloristDatabase.species.filter {
            it.classification.statutInvasif in listOf(StatutInvasif.ENVAHISSANTE, StatutInvasif.ENVAHISSANTE_POTENTIELLE)
        }
        BrowserCategory.INDICATORS  -> FloristDatabase.species.filter {
            it.valeurIndicatrice.indicateurPerturbation || it.valeurIndicatrice.indicateurFertilisation
        }
    }

    // Surclasser les espèces cohérentes avec le contexte milieu
    if (contextMilieu != null) {
            val contextSpecies = FloristDatabase.findIndicatrices(contextMilieu).map { it.id }.toSet()
        species = species.sortedByDescending { if (it.id in contextSpecies) 1 else 0 }
    }

    // Filtre recherche texte
    if (query.isNotBlank()) {
        val q = query.lowercase()
        species = species.filter {
            it.taxonomie.nomFrancais.lowercase().contains(q) ||
            it.taxonomie.nomScientifique.lowercase().contains(q) ||
            it.taxonomie.nomsVernaculaires.any { v -> v.lowercase().contains(q) }
        }
    }

    return species.take(120)
}

