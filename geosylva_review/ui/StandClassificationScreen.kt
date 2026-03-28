package com.forestry.counter.presentation.screens.forestry

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forestry.counter.domain.classification.stand.*

// ═══════════════════════════════════════════════════════════════════════════
// ÉCRAN PRINCIPAL — Classification des peuplements forestiers
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Cache léger pour transmettre les stats de la MartelageScreen à StandClassificationScreen
 * sans sérialisation complexe dans les arguments de navigation.
 */
object StandClassificationCache {
    var lastStats: MartelageStats? = null
    var lastParcelleId: String? = null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandClassificationScreen(
    parcelleId: String,
    onNavigateBack: () -> Unit
) {
    val stats = remember(parcelleId) {
        StandClassificationCache.lastStats.takeIf { StandClassificationCache.lastParcelleId == parcelleId }
    }
    var result by remember { mutableStateOf<StandClassificationResult?>(null) }
    var userAnswers by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showQuestionDialog by remember { mutableStateOf(false) }
    var pendingQuestions by remember { mutableStateOf<List<ClassificationQuestion>>(emptyList()) }
    var tempAnswers by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    // Lancement de la classification initiale
    LaunchedEffect(stats, userAnswers) {
        if (stats == null) return@LaunchedEffect
        val r = StandClassificationEngine.classify(stats, userAnswers)
        result = r
        if (r.missingDataQuestions.isNotEmpty() && userAnswers.isEmpty()) {
            pendingQuestions = r.missingDataQuestions
            tempAnswers = emptyMap()
            showQuestionDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Classification du peuplement", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        result?.let {
                            Text(
                                it.treatmentMode.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    if (result?.missingDataQuestions?.isNotEmpty() == true) {
                        IconButton(onClick = {
                            pendingQuestions = result!!.missingDataQuestions
                            tempAnswers = userAnswers
                            showQuestionDialog = true
                        }) {
                            Icon(Icons.Default.QuestionMark, contentDescription = "Compléter les données", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        if (stats == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Aucune donnée disponible pour classifier le peuplement.", textAlign = TextAlign.Center)
            }
            return@Scaffold
        }

        val r = result
        if (r == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // ── Bandeau confiance ──
            ConfidenceBanner(r.confidence, r.missingDataQuestions.size) {
                pendingQuestions = r.missingDataQuestions
                tempAnswers = userAnswers
                showQuestionDialog = true
            }

            // ── Onglets ──
            TabRow(selectedTabIndex = selectedTab) {
                listOf("Diagnostic", "Programme", "Martelage").forEachIndexed { i, label ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i },
                        text = { Text(label, style = MaterialTheme.typography.labelMedium) })
                }
            }

            when (selectedTab) {
                0 -> DiagnosticTab(r)
                1 -> ManagementProgramTab(r)
                2 -> MartelageAidTab(r)
            }
        }
    }

    // ── Dialogue questions utilisateur ──
    if (showQuestionDialog && pendingQuestions.isNotEmpty()) {
        QuestionsDialog(
            questions = pendingQuestions,
            initialAnswers = tempAnswers,
            onConfirm = { answers ->
                userAnswers = userAnswers + answers
                showQuestionDialog = false
            },
            onDismiss = { showQuestionDialog = false }
        )
    }
}

// ── Bandeau de confiance ──────────────────────────────────────────────────────

@Composable
private fun ConfidenceBanner(confidence: Float, unansweredCount: Int, onCompleteData: () -> Unit) {
    val pct = (confidence * 100).toInt()
    val color = when {
        pct >= 80 -> Color(0xFF2E7D32)
        pct >= 60 -> Color(0xFFF57C00)
        else      -> Color(0xFFC62828)
    }
    Surface(color = color.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (pct >= 80) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null, tint = color, modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Confiance : $pct%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
                if (unansweredCount > 0)
                    Text("$unansweredCount question(s) supplémentaire(s) amélioreraient la précision",
                        style = MaterialTheme.typography.bodySmall, color = color)
            }
            if (unansweredCount > 0)
                TextButton(onClick = onCompleteData) {
                    Text("Compléter", style = MaterialTheme.typography.labelSmall, color = color)
                }
        }
    }
}

// ── Onglet 0 — Diagnostic ─────────────────────────────────────────────────────

@Composable
private fun DiagnosticTab(r: StandClassificationResult) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // ── Code CNPF formel (F/M/T/R + ClassCapital + Structure 1-9) ──
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            r.diagnosis.cnpfTypeCode,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSecondary
                        )
                    }
                    Column {
                        Text("Code CNPF / SRGS", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                        Text("Régime × Capital G × Structure 1–9",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f))
                Text(
                    r.diagnosis.cnpfTypeExplanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        // Type identifié
        ClassificationCard(
            title = "Peuplement identifié",
            icon = Icons.Default.Forest,
            iconColor = Color(0xFF2E7D32),
            badge = r.treatmentMode.shortCode
        ) {
            Text(r.diagnosis.standTypeLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            DimensionRow("Mode de traitement", r.treatmentMode.label)
            DimensionRow("Structure d'âge", r.ageStructure.label)
            DimensionRow("Structure verticale", r.verticalStructure.label)
            DimensionRow("Composition", r.composition.label)
            DimensionRow("Origine", r.origin.label)
            DimensionRow("Stade de développement", r.developmentStage.label)
            DimensionRow("Dynamique", r.dynamic.label)
            DimensionRow("État sanitaire", r.disturbanceState.label)
            DimensionRow("Type écologique", r.ecologicalType.label)
        }

        // Triangle des structures
        r.diameterRatio?.let { ratio ->
            val tri = ratio.trianglePosition()
            ClassificationCard("Triangle des structures", Icons.Default.AccountTree, Color(0xFF1565C0)) {
                TriangleWidget(ratio)
                Spacer(Modifier.height(6.dp))
                DimensionRow("Position (sémantique)", tri.label)
                DimensionRow("Structure CNPF (1–9)", ratio.cnpfStructureCode().toString())
                DimensionRow("Petits bois — PB (17.5–27.5cm)", "%.0f%%".format(ratio.pbPct))
                DimensionRow("Bois moyen — BM", "%.0f%%".format(ratio.bmPct))
                DimensionRow("Gros bois — GB", "%.0f%%".format(ratio.gbPct))
                if (ratio.tgbPct > 0.0)
                    DimensionRow("Très gros bois — TGB (>67.5cm)", "%.0f%%".format(ratio.tgbPct))
                DimensionRow("GB + TGB cumulés (axe Y triangle)", "%.0f%%".format(ratio.gbTgbPct))
            }
        }

        // Pourquoi cette classification
        ClassificationCard("Pourquoi cette classification ?", Icons.Default.Help, Color(0xFF6A1B9A)) {
            r.diagnosis.whyClassified.forEach { reason ->
                BulletRow("→", reason, Color(0xFF6A1B9A))
            }
        }

        // Signification écologique
        ClassificationCard("Signification écologique", Icons.Default.Eco, Color(0xFF1B5E20)) {
            Text(r.diagnosis.ecologicalMeaning, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Avantages
        if (r.diagnosis.advantages.isNotEmpty()) {
            ClassificationCard("Atouts du peuplement", Icons.Default.ThumbUp, Color(0xFF2E7D32)) {
                r.diagnosis.advantages.forEach { BulletRow("✓", it, Color(0xFF2E7D32)) }
            }
        }

        // Risques
        ClassificationCard("Risques identifiés", Icons.Default.Warning, Color(0xFFE65100)) {
            r.diagnosis.risks.forEach { BulletRow("⚠", it, Color(0xFFE65100)) }
        }

        // Implications sylvicoles
        ClassificationCard("Implications sylvicoles", Icons.Default.Build, Color(0xFF1565C0)) {
            r.diagnosis.sylviculturalImplications.forEach { BulletRow("•", it, MaterialTheme.colorScheme.onSurface) }
        }

        // Objectif suggéré
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Objectif de gestion suggéré", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(r.suggestedObjective.label, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Onglet 1 — Programme de gestion ──────────────────────────────────────────

@Composable
private fun ManagementProgramTab(r: StandClassificationResult) {
    val p = r.managementProgram
    val scroll = rememberScrollState()
    var selectedRegion by remember { mutableStateOf<SRGSRegion?>(null) }
    var showRegionDropdown by remember { mutableStateOf(false) }

    val stats = StandClassificationCache.lastStats
    val gPerHa = stats?.gPerHa ?: 20.0
    val nPerHa = (stats?.nTotal?.toDouble() ?: 200.0)
    val dominantEssence = ""  // available via future integration

    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // ── Sélecteur région SRGS ──
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Map, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("Itinéraire SRGS régional", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    Box {
                        OutlinedButton(onClick = { showRegionDropdown = true }, modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                            Text(selectedRegion?.code ?: "Région…", style = MaterialTheme.typography.labelSmall)
                            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(expanded = showRegionDropdown, onDismissRequest = { showRegionDropdown = false }) {
                            SRGSRegion.entries.forEach { reg ->
                                DropdownMenuItem(
                                    text = { Text("${reg.code} — ${reg.labelFr}", style = MaterialTheme.typography.bodySmall) },
                                    onClick = { selectedRegion = reg; showRegionDropdown = false }
                                )
                            }
                        }
                    }
                }
                if (selectedRegion != null) {
                    val itineraire = StandTypologyDatabase.srgsItineraireFor(
                        selectedRegion, r.treatmentMode, dominantEssence, gPerHa, nPerHa
                    )
                    Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(8.dp)) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(color = Color(0xFF2E7D32), shape = RoundedCornerShape(4.dp)) {
                                    Text(selectedRegion!!.code,
                                        Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Text(selectedRegion!!.labelFr, style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold, color = Color(0xFF1B5E20))
                            }
                            Text(itineraire, style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF1B5E20).copy(alpha = 0.85f))
                        }
                    }
                } else {
                    Text("Sélectionnez votre région forestière pour obtenir les préconisations du Schéma Régional de Gestion Sylvicole.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        val isIrregulier = r.treatmentMode in setOf(
            TreatmentMode.FUTAIE_JARDINEE, TreatmentMode.FUTAIE_JARDINEE_GROUPES,
            TreatmentMode.FUTAIE_IRREGULIERE, TreatmentMode.FUTAIE_IRREGULIERE_BOUQUETS
        )

        // Objectif
        ClassificationCard("Objectif de production", Icons.Default.Stars, Color(0xFF1565C0)) {
            Text(p.objectiveLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            if (!isIrregulier) {
                p.targetDiamCm?.let { DimensionRow("Diamètre d'exploitabilité", "${it.first}–${it.last} cm") }
                p.targetAgeAns?.let { DimensionRow("Âge d'exploitabilité", "${it.first}–${it.last} ans") }
                p.targetNha?.let { DimensionRow("Densité finale cible", "${it.first}–${it.last} t/ha") }
            }
            DimensionRow("Qualité cible", p.qualityCible)
            if (isIrregulier) {
                Spacer(Modifier.height(4.dp))
                Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp)) {
                    Text(
                        "Peuplement irrégulier : pas de diamètre/âge/densité cible fixe — gestion continue par prélèvements périodiques.",
                        Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // Trajectoire des densités
        if (p.densityTrajectory.isNotEmpty()) {
            ClassificationCard("Trajectoire de la densité", Icons.Default.Timeline, Color(0xFF37474F)) {
                p.densityTrajectory.forEachIndexed { i, (label, range) ->
                    val isLast = i == p.densityTrajectory.lastIndex
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(
                            if (isLast) Color(0xFF1565C0) else Color(0xFF90CAF9), RoundedCornerShape(5.dp)))
                        Spacer(Modifier.width(8.dp))
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal)
                        Text("${range.first}–${range.last} t/ha",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isLast) Color(0xFF1565C0) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal)
                    }
                    if (!isLast) {
                        Box(Modifier.padding(start = 4.dp).size(width = 2.dp, height = 12.dp)
                            .background(Color(0xFF90CAF9)))
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        // Interventions
        p.interventions.forEachIndexed { idx, interv ->
            InterventionCard(idx + 1, interv)
        }
    }
}

@Composable
private fun InterventionCard(num: Int, interv: Intervention) {
    var expanded by remember { mutableStateOf(num == 1) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFF1565C0),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(32.dp)
                ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("$num", color = Color.White, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge)
                }}
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(interv.type, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(interv.timing, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                }
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(start = 54.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Objectif", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(interv.objective, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text("Actions", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    interv.actions.forEach { BulletRow("→", it, MaterialTheme.colorScheme.onSurface) }
                    interv.intensityPct?.let {
                        Spacer(Modifier.height(4.dp))
                        Surface(color = Color(0xFFE3F2FD), shape = RoundedCornerShape(6.dp)) {
                            Text("Intensité : ${it.first}–${it.last} %",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                    interv.expectedNhaAfter?.let {
                        Text("Densité après : ${it.first}–${it.last} t/ha",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ── Onglet 2 — Aide au martelage ──────────────────────────────────────────────

@Composable
private fun MartelageAidTab(r: StandClassificationResult) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ClassificationCard("Conseils de martelage", Icons.Default.Carpenter, Color(0xFF4CAF50)) {
            r.diagnosis.martelageTips.forEach { BulletRow("🪓", it, MaterialTheme.colorScheme.onSurface) }
        }

        // Arbres à marquer vs conserver
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text("Arbres à marquer (abattre)", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                Spacer(Modifier.height(6.dp))
                listOf(
                    "Arbres dominés sans perspective d'avenir",
                    "Tiges difformes, fourchues ou à mauvaise conformation",
                    "Arbres dépérissants ou fortement parasités",
                    "Concurrents directs des arbres d'avenir sélectionnés",
                    when (r.treatmentMode) {
                        TreatmentMode.TAILLIS_SOUS_FUTAIE, TreatmentMode.TAILLIS_SOUS_FUTAIE_CONVERSION ->
                            "Cépées et brins concurrents des réserves"
                        TreatmentMode.FUTAIE_JARDINEE ->
                            "Arbres atteignant le diamètre d'exploitabilité"
                        else -> "Arbres à faible perspective de valorisation"
                    }
                ).forEach { BulletRow("✗", it, Color(0xFFC62828)) }

                Spacer(Modifier.height(10.dp))
                Text("Arbres à conserver", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                Spacer(Modifier.height(6.dp))
                listOf(
                    "Arbres d'avenir : fût droit, couronne équilibrée, bonne vigueur",
                    "Arbres dominants en bonne santé",
                    "Porte-graines pour la régénération naturelle",
                    "Arbres à cavités et bois mort sur pied (biodiversité)",
                    when (r.treatmentMode) {
                        TreatmentMode.FUTAIE_JARDINEE -> "Représentants de toutes les classes de diamètre"
                        TreatmentMode.TAILLIS_SOUS_FUTAIE, TreatmentMode.TAILLIS_SOUS_FUTAIE_RICHE ->
                            "80–120 réserves/ha, bien réparties spatialement"
                        else -> "Arbres diversifiants (autres essences, formes atypiques non gênantes)"
                    }
                ).forEach { BulletRow("✓", it, Color(0xFF2E7D32)) }
            }
        }

        // Explication pédagogique
        ClassificationCard("Comprendre la décision", Icons.Default.School, Color(0xFF37474F)) {
            val pedagogyText = buildString {
                append("Ce peuplement est classé comme **${r.treatmentMode.label}** ")
                append("en raison de ${r.diagnosis.whyClassified.firstOrNull()?.lowercase() ?: "sa structure dendrométrique"}.\n\n")
                append("La présence de ${r.ageStructure.label.lowercase()} ")
                append("et une ${r.verticalStructure.label.lowercase()} ")
                append("confirment ce type. ")
                when (r.dynamic) {
                    StandDynamic.MATURATION -> append("Le peuplement est en phase de maturation : c'est la période idéale pour orienter la production vers les meilleures tiges.")
                    StandDynamic.CROISSANCE -> append("Le peuplement est en pleine croissance : les éclaircies régulières sont déterminantes pour la qualité finale.")
                    StandDynamic.REGENERATION -> append("La régénération active offre l'opportunité de renouveler progressivement le peuplement.")
                    StandDynamic.SENESCENCE -> append("Le peuplement est en sénescence : des enjeux biodiversité importants justifient de conserver des îlots de vieillissement.")
                    else -> {}
                }
            }
            Text(pedagogyText.replace("**", ""), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Dialogue de questions ─────────────────────────────────────────────────────

@Composable
private fun QuestionsDialog(
    questions: List<ClassificationQuestion>,
    initialAnswers: Map<String, Int>,
    onConfirm: (Map<String, Int>) -> Unit,
    onDismiss: () -> Unit
) {
    var answers by remember { mutableStateOf(initialAnswers) }
    val scroll = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Préciser le type de peuplement", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Ces questions permettent d'améliorer la classification automatique.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                questions.forEach { q ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(q.text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        if (q.hint.isNotBlank())
                            Text(q.hint, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        q.options.forEachIndexed { i, opt ->
                            val selected = answers[q.id] == i
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RadioButton(selected = selected, onClick = {
                                    answers = answers + (q.id to i)
                                })
                                Text(opt, style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(answers) }) { Text("Appliquer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Plus tard") }
        }
    )
}

// ── Widget triangle des structures ────────────────────────────────────────────

@Composable
private fun TriangleWidget(ratio: DiameterCategoryRatio) {
    val bars = buildList {
        add(Triple("PB", ratio.pbPct, Color(0xFF90CAF9)))
        add(Triple("BM", ratio.bmPct, Color(0xFF1565C0)))
        add(Triple("GB", ratio.gbPct, Color(0xFF0D47A1)))
        if (ratio.tgbPct > 0.0) add(Triple("TGB", ratio.tgbPct, Color(0xFF002171)))
    }
    val maxPct = bars.maxOf { it.second }.coerceAtLeast(1.0)
    Row(Modifier.fillMaxWidth().height(70.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom) {
        bars.forEach { (label, pct, color) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%.0f%%".format(pct), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Box(Modifier.fillMaxWidth().height((pct / maxPct * 44).toInt().coerceAtLeast(4).dp)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(color))
                Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
    }
}

// ── Composables utilitaires ───────────────────────────────────────────────────

@Composable
private fun ClassificationCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    badge: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                badge?.let {
                    Surface(color = iconColor, shape = RoundedCornerShape(6.dp)) {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = Color.White,
                            fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun DimensionRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
    }
}

@Composable
private fun BulletRow(bullet: String, text: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Text(bullet, style = MaterialTheme.typography.bodySmall, color = color,
            modifier = Modifier.padding(end = 6.dp, top = 1.dp))
        Text(text, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// CARTE BANNIÈRE — Entrée depuis MartelageScreen (Tab 2 Analyse)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
internal fun StandClassificationBannerCard(
    stats: MartelageStats,
    onNavigateToClassification: () -> Unit
) {
    val quickResult = remember(stats) {
        if (StandClassificationEngine.hasEnoughForPartialResult(stats))
            StandClassificationEngine.classify(stats)
        else null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountTree, contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Classification du peuplement", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold)
                    Text("Diagnostic sylvicole automatique", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary)
            }

            quickResult?.let { r ->
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("🌲", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            StandClassificationEngine.shortLabel(r),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(8.dp))
                        val pct = (r.confidence * 100).toInt()
                        Text("($pct%)", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } ?: run {
                Spacer(Modifier.height(6.dp))
                Text("Ajoutez au moins 5 tiges pour lancer le diagnostic.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(8.dp))
            Button(onClick = onNavigateToClassification, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Ouvrir le diagnostic complet")
            }
        }
    }
}
