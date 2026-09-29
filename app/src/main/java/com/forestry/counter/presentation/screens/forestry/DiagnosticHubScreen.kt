package com.forestry.counter.presentation.screens.forestry

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiNature
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Water
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forestry.counter.R
import com.forestry.counter.domain.model.Foret
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.repository.ForetRepository
import com.forestry.counter.domain.repository.ParcelleRepository
import com.forestry.counter.domain.repository.PlacetteRepository
import kotlinx.coroutines.flow.flowOf

// ── Types de diagnostics disponibles ────────────────────────────────────────
enum class DiagnosticType(
    val labelRes: Int,
    val descRes: Int,
    val icon: ImageVector,
) {
    IBP(R.string.diag_type_ibp, R.string.diag_type_ibp_desc, Icons.Default.EmojiNature),
    STATION(R.string.diag_type_station, R.string.diag_type_station_desc, Icons.Default.Science),
    RIPISYLVE(R.string.diag_type_ripisylve, R.string.diag_type_ripisylve_desc, Icons.Default.Water),
    SYLVICOLE(R.string.diag_type_sylvicole, R.string.diag_type_sylvicole_desc, Icons.Default.Forest),
}

private enum class WizardStep(val labelRes: Int) {
    TYPE(R.string.diag_wizard_step_type),
    FORET(R.string.diag_wizard_step_foret),
    PARCELLES(R.string.diag_wizard_step_parcelles),
    PLACETTES(R.string.diag_wizard_step_placettes),
    SUMMARY(R.string.diag_wizard_step_summary),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticHubScreen(
    foretRepository: ForetRepository,
    parcelleRepository: ParcelleRepository,
    placetteRepository: PlacetteRepository,
    onNavigateBack: () -> Unit,
    onLaunchIbp: (parcelleId: String, placetteId: String) -> Unit,
    onLaunchStation: (parcelleId: String) -> Unit,
    onLaunchRipisylve: (parcelleId: String) -> Unit,
    onLaunchSylvicole: (parcelleId: String) -> Unit,
) {
    var currentStep by remember { mutableStateOf(WizardStep.TYPE) }
    var selectedType by remember { mutableStateOf<DiagnosticType?>(null) }
    var selectedForet by remember { mutableStateOf<Foret?>(null) }
    val selectedParcelles = remember { mutableStateListOf<Parcelle>() }
    val selectedPlacettes = remember { mutableStateListOf<Placette>() }

    val allForets by foretRepository.getAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val parcellesFlow = remember(selectedForet) {
        selectedForet?.let { parcelleRepository.getParcellesByForest(it.foretId) } ?: flowOf(emptyList())
    }
    val allParcelles by parcellesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    // Placettes combinées pour toutes les parcelles sélectionnées
    // Approche simple : on charge toutes les placettes de la première parcelle
    // sélectionnée puis on les montre groupées
    val placettesFlow = remember(selectedParcelles.toList()) {
        when {
            selectedParcelles.isEmpty() -> flowOf(emptyList())
            else -> placetteRepository.getPlacettesByParcelle(selectedParcelles.first().id)
        }
    }
    val placettesFirstParcelle by placettesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val stepProgress = (currentStep.ordinal + 1).toFloat() / WizardStep.entries.size.toFloat()

    fun navigateBack() {
        when (currentStep) {
            WizardStep.TYPE -> onNavigateBack()
            WizardStep.FORET -> { currentStep = WizardStep.TYPE }
            WizardStep.PARCELLES -> { currentStep = WizardStep.FORET }
            WizardStep.PLACETTES -> { currentStep = WizardStep.PARCELLES }
            WizardStep.SUMMARY -> { currentStep = WizardStep.PLACETTES }
        }
    }

    fun navigateNext() {
        when (currentStep) {
            WizardStep.TYPE -> if (selectedType != null) currentStep = WizardStep.FORET
            WizardStep.FORET -> if (selectedForet != null) { selectedParcelles.clear(); currentStep = WizardStep.PARCELLES }
            WizardStep.PARCELLES -> if (selectedParcelles.isNotEmpty()) { selectedPlacettes.clear(); currentStep = WizardStep.PLACETTES }
            WizardStep.PLACETTES -> if (selectedPlacettes.isNotEmpty()) currentStep = WizardStep.SUMMARY
            WizardStep.SUMMARY -> launchDiagnostic(
                type = selectedType,
                parcelles = selectedParcelles.toList(),
                placettes = selectedPlacettes.toList(),
                onLaunchIbp = onLaunchIbp,
                onLaunchStation = onLaunchStation,
                onLaunchRipisylve = onLaunchRipisylve,
                onLaunchSylvicole = onLaunchSylvicole,
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.diag_hub_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(currentStep.labelRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { stepProgress },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (currentStep != WizardStep.TYPE) {
                        OutlinedButton(
                            onClick = { navigateBack() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.back))
                        }
                    }
                    val canProceed = when (currentStep) {
                        WizardStep.TYPE -> selectedType != null
                        WizardStep.FORET -> selectedForet != null
                        WizardStep.PARCELLES -> selectedParcelles.isNotEmpty()
                        WizardStep.PLACETTES -> selectedPlacettes.isNotEmpty()
                        WizardStep.SUMMARY -> true
                    }
                    Button(
                        onClick = { navigateNext() },
                        modifier = Modifier.weight(1f),
                        enabled = canProceed
                    ) {
                        if (currentStep == WizardStep.SUMMARY) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.diag_hub_launch))
                        } else {
                            Text(stringResource(R.string.diag_hub_next))
                        }
                    }
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "diag_wizard_step"
        ) { step ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (step) {
                    WizardStep.TYPE -> DiagTypeStep(
                        selected = selectedType,
                        onSelect = { selectedType = it }
                    )
                    WizardStep.FORET -> DiagForetStep(
                        forets = allForets,
                        selected = selectedForet,
                        onSelect = { selectedForet = it }
                    )
                    WizardStep.PARCELLES -> DiagParcellesStep(
                        parcelles = allParcelles,
                        selected = selectedParcelles,
                        onToggle = { parcelle ->
                            if (selectedParcelles.contains(parcelle)) selectedParcelles.remove(parcelle)
                            else selectedParcelles.add(parcelle)
                        }
                    )
                    WizardStep.PLACETTES -> DiagPlacettesStep(
                        parcelles = selectedParcelles.toList(),
                        placettesForFirst = placettesFirstParcelle,
                        placetteRepository = placetteRepository,
                        selected = selectedPlacettes,
                        onToggle = { placette ->
                            if (selectedPlacettes.contains(placette)) selectedPlacettes.remove(placette)
                            else selectedPlacettes.add(placette)
                        }
                    )
                    WizardStep.SUMMARY -> DiagSummaryStep(
                        type = selectedType,
                        foret = selectedForet,
                        parcelles = selectedParcelles.toList(),
                        placettes = selectedPlacettes.toList(),
                    )
                }
            }
        }
    }
}

// ── Étape 1 : type de diagnostic ─────────────────────────────────────────────
@Composable
private fun DiagTypeStep(
    selected: DiagnosticType?,
    onSelect: (DiagnosticType) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                stringResource(R.string.diag_hub_choose_type),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
        }
        items(DiagnosticType.entries) { type ->
            val isSelected = selected == type
            ElevatedCard(
                onClick = { onSelect(type) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                     else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.elevatedCardElevation(
                    defaultElevation = if (isSelected) 4.dp else 1.dp
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(
                            type.icon,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp).size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(type.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(type.descRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isSelected) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Étape 2 : forêt ──────────────────────────────────────────────────────────
@Composable
private fun DiagForetStep(
    forets: List<Foret>,
    selected: Foret?,
    onSelect: (Foret) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                stringResource(R.string.diag_hub_choose_foret),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
        }
        if (forets.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.diag_hub_no_forets),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(forets, key = { it.foretId }) { foret ->
            val isSelected = selected?.foretId == foret.foretId
            Card(
                onClick = { onSelect(foret) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                     else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Forest,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(foret.nom, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        if (foret.proprietaireNom.isNotBlank()) {
                            Text(foret.proprietaireNom, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Icon(
                        if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ── Étape 3 : parcelles (multi-select) ───────────────────────────────────────
@Composable
private fun DiagParcellesStep(
    parcelles: List<Parcelle>,
    selected: List<Parcelle>,
    onToggle: (Parcelle) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.diag_hub_choose_parcelles),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (selected.isNotEmpty()) {
                    BadgedBox(badge = { Badge { Text(selected.size.toString()) } }) {
                        Icon(Icons.Default.Park, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        items(parcelles, key = { it.id }) { parcelle ->
            val isSelected = selected.any { it.id == parcelle.id }
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(parcelle) },
                label = {
                    Column {
                        Text(parcelle.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        parcelle.surfaceHa?.let {
                            Text("${String.format("%.2f", it)} ha", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                leadingIcon = {
                    Icon(
                        if (isSelected) Icons.Default.CheckCircle else Icons.Default.Park,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (parcelles.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.diag_hub_no_parcelles),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Étape 4 : placettes (multi-select) ───────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiagPlacettesStep(
    parcelles: List<Parcelle>,
    placettesForFirst: List<Placette>,
    placetteRepository: PlacetteRepository,
    selected: List<Placette>,
    onToggle: (Placette) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.diag_hub_choose_placettes),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (selected.isNotEmpty()) {
                    BadgedBox(badge = { Badge { Text(selected.size.toString()) } }) {
                        Icon(Icons.Default.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        parcelles.forEachIndexed { idx, parcelle ->
            // Sous-titre parcelle
            item(key = "header-${parcelle.id}") {
                if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    parcelle.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
            // Placettes de la première parcelle (chargées en amont)
            // Les autres parcelles ont leurs placettes chargées via sous-composable
            if (idx == 0) {
                items(placettesForFirst, key = { "p-${it.id}" }) { placette ->
                    PlacetteSelectItem(placette = placette, isSelected = selected.any { it.id == placette.id }, onToggle = onToggle)
                }
                if (placettesForFirst.isEmpty()) {
                    item(key = "empty-0") {
                        Text(stringResource(R.string.diag_hub_no_placettes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                item(key = "extra-${parcelle.id}") {
                    ExtraParcelleSection(
                        parcelle = parcelle,
                        placetteRepository = placetteRepository,
                        selected = selected,
                        onToggle = onToggle
                    )
                }
            }
        }
    }
}

@Composable
private fun PlacetteSelectItem(placette: Placette, isSelected: Boolean, onToggle: (Placette) -> Unit) {
    FilterChip(
        selected = isSelected,
        onClick = { onToggle(placette) },
        label = {
            Text(
                placette.name?.takeIf { it.isNotBlank() } ?: placette.id.take(8),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(
                if (isSelected) Icons.Default.CheckCircle else Icons.Default.Map,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ExtraParcelleSection(
    parcelle: Parcelle,
    placetteRepository: PlacetteRepository,
    selected: List<Placette>,
    onToggle: (Placette) -> Unit,
) {
    val placettes by placetteRepository.getPlacettesByParcelle(parcelle.id)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        placettes.forEach { placette ->
            PlacetteSelectItem(placette = placette, isSelected = selected.any { it.id == placette.id }, onToggle = onToggle)
        }
        if (placettes.isEmpty()) {
            Text(stringResource(R.string.diag_hub_no_placettes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Étape 5 : récapitulatif ───────────────────────────────────────────────────
@Composable
private fun DiagSummaryStep(
    type: DiagnosticType?,
    foret: Foret?,
    parcelles: List<Parcelle>,
    placettes: List<Placette>,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                stringResource(R.string.diag_hub_summary_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (type != null) {
                        SummaryRow(
                            icon = type.icon,
                            label = stringResource(R.string.diag_hub_summary_type),
                            value = stringResource(type.labelRes)
                        )
                    }
                    if (foret != null) {
                        HorizontalDivider()
                        SummaryRow(icon = Icons.Default.Forest, label = stringResource(R.string.diag_hub_summary_foret), value = foret.nom)
                    }
                    HorizontalDivider()
                    SummaryRow(
                        icon = Icons.Default.Park,
                        label = stringResource(R.string.diag_hub_summary_parcelles),
                        value = parcelles.joinToString(", ") { it.name }
                    )
                    HorizontalDivider()
                    SummaryRow(
                        icon = Icons.Default.Map,
                        label = stringResource(R.string.diag_hub_summary_placettes),
                        value = stringResource(R.string.diag_hub_summary_placettes_count, placettes.size)
                    )
                }
            }
        }
        item {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    stringResource(R.string.diag_hub_summary_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

// ── Lancement du diagnostic ───────────────────────────────────────────────────
private fun launchDiagnostic(
    type: DiagnosticType?,
    parcelles: List<Parcelle>,
    placettes: List<Placette>,
    onLaunchIbp: (parcelleId: String, placetteId: String) -> Unit,
    onLaunchStation: (parcelleId: String) -> Unit,
    onLaunchRipisylve: (parcelleId: String) -> Unit,
    onLaunchSylvicole: (parcelleId: String) -> Unit,
) {
    val firstParcelle = parcelles.firstOrNull() ?: return
    val firstPlacette = placettes.firstOrNull()

    when (type) {
        DiagnosticType.IBP -> {
            val placetteId = firstPlacette?.id ?: return
            onLaunchIbp(firstParcelle.id, placetteId)
        }
        DiagnosticType.STATION -> onLaunchStation(firstParcelle.id)
        DiagnosticType.RIPISYLVE -> onLaunchRipisylve(firstParcelle.id)
        DiagnosticType.SYLVICOLE -> onLaunchSylvicole(firstParcelle.id)
        null -> {}
    }
}
