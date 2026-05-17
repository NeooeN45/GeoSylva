package com.forestry.counter.presentation.screens.forestry

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TarifDocumentationScreen(
    onNavigateBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Bases de données", "Tarifs", "Formules")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Documentation Tarifs") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { i, title ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(title) })
                }
            }
            when (selectedTab) {
                0 -> DatabasesTab()
                1 -> TarifsTab()
                2 -> FormulasTab()
            }
        }
    }
}

@Composable
private fun DatabasesTab() {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DocCard(
                title = "IFN — Inventaire Forestier National",
                subtitle = "Base de données cubages nationaux",
                color = Color(0xFF1565C0),
                content = "Tarifs de cubage établis par l'IGN sur la base des inventaires IFN couvrant l'ensemble du territoire métropolitain. Utilisés pour les essences non couvertes par des tarifs locaux."
            )
        }
        item {
            DocCard(
                title = "Schaeffer 1 entrée",
                subtitle = "Volume par diamètre seul",
                color = Color(0xFF2E7D32),
                content = "Tarif 1 entrée basé uniquement sur le diamètre à 1,30 m. Précision ±15–25%. Recommandé quand les hauteurs ne sont pas disponibles. Valable pour peuplements homogènes."
            )
        }
        item {
            DocCard(
                title = "Schaeffer 2 entrées",
                subtitle = "Volume par diamètre et hauteur",
                color = Color(0xFF6A1B9A),
                content = "Tarif 2 entrées intégrant diamètre ET hauteur. Précision ±8–12%. Recommandé pour les évaluations précises de cubage. Nécessite la mesure des hauteurs dominantes."
            )
        }
        item {
            DocCard(
                title = "Algan (Allométrie régionale)",
                subtitle = "Equations alloméetriques INRAE",
                color = Color(0xFFE65100),
                content = "Équations allométriques développées par l'INRAE pour les principales essences forestières françaises. Intègrent la variabilité régionale et la forme des arbres."
            )
        }
        item {
            DocCard(
                title = "Chaudé (1991)",
                subtitle = "Tarifs groupes d'essences",
                color = Color(0xFF37474F),
                content = "Tarifs par groupes d'essences établis par Pierre Chaudé. Largement utilisés en sylviculture privée française. Disponibles pour les principales essences de production."
            )
        }
    }
}

@Composable
private fun TarifsTab() {
    data class TarifEntry(
        val num: Int, val name: String, val essences: String,
        val entrees: String, val precision: String, val source: String
    )
    val tarifs = listOf(
        TarifEntry(1, "Schaeffer 1e — Douglas", "Douglas", "D", "±20%", "IFN"),
        TarifEntry(2, "Schaeffer 2e — Douglas", "Douglas", "D, H", "±10%", "IFN"),
        TarifEntry(3, "Algan — Chêne sessile", "Chêne sessile, pédonculé", "D, H", "±12%", "INRAE"),
        TarifEntry(4, "Algan — Hêtre", "Hêtre", "D, H", "±11%", "INRAE"),
        TarifEntry(5, "Chaudé — Épicéa", "Épicéa commun", "D", "±18%", "Chaudé 1991"),
        TarifEntry(6, "Chaudé — Pin sylvestre", "Pin sylvestre", "D", "±18%", "Chaudé 1991"),
        TarifEntry(7, "IFN rapide", "Toutes essences", "D", "±25%", "IFN"),
        TarifEntry(8, "IFN lent", "Toutes essences (lent)", "D", "±22%", "IFN"),
        TarifEntry(9, "FGH — Feuillu gros H", "Chênes, hêtre GB", "D, H", "±8%", "ONF"),
        TarifEntry(10, "Schaeffer — Sapin", "Sapin pectiné", "D, H", "±10%", "IFN"),
    )
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tarifs) { t ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("n°${t.num}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(t.name, fontWeight = FontWeight.SemiBold)
                        Text(t.essences, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(t.entrees, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                        Text(t.precision, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun FormulasTab() {
    data class FormulaEntry(val name: String, val formula: String, val desc: String)
    val formulas = listOf(
        FormulaEntry("Surface terrière", "G = π/4 × (D/100)²", "G en m² pour D en cm"),
        FormulaEntry("G/ha", "G/ha = (N/ha) × (π/4) × Dg²/10000", "Avec Dg en cm"),
        FormulaEntry("Diamètre quadratique", "Dg = √(4G/(π×N))", "En cm, G en m²"),
        FormulaEntry("Volume Schaeffer 1e", "V = a + b×G", "a, b = coeff. tarif par essence"),
        FormulaEntry("Volume Schaeffer 2e", "V = a + b×G×H", "H hauteur dominante en m"),
        FormulaEntry("Élancement H/D", "e = H×100/D", "H en m, D en cm — stable si <85"),
        FormulaEntry("H Lorey", "HL = Σ(Hi×Gi) / ΣGi", "Hauteur pondérée par surface terrière"),
        FormulaEntry("Indice Shannon", "H' = -Σ(pi × ln(pi))", "pi = proportion de chaque essence"),
        FormulaEntry("Équidistribution Piélou", "J = H' / ln(S)", "S = nombre total d'essences"),
        FormulaEntry("Densité relative", "Dr = N/Nref × 100", "Nref = densité de référence CNPF"),
    )
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(formulas) { f ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(f.name, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            f.formula,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(f.desc, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun DocCard(title: String, subtitle: String, color: Color, content: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(4.dp, 40.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = color
                ) {}
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = color)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(content, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
