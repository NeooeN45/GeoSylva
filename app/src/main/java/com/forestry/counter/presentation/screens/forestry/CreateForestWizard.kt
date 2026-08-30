package com.forestry.counter.presentation.screens.forestry

import com.forestry.counter.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.Space

/**
 * Wizard de création de forêt — 4 étapes.
 *
 * Étape 1 : Identité (nom, département(s) via multi-sélection)
 * Étape 2 : Propriétaire (nom, email, gestionnaire)
 * Étape 3 : Gestion (type via chips, objectif via chips, PSG, remarques)
 * Étape 4 : Couleur (palette pour la carte de la forêt dans la liste)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateForestWizard(
    viewModel: ForestFormViewModel,
    onNavigateBack: () -> Unit,
    onCreated: () -> Unit,
    isEditing: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val form by viewModel.formState.collectAsStateWithLifecycle()
    val isSaved by viewModel.isSaved.collectAsStateWithLifecycle()
    var currentStep by remember { mutableIntStateOf(0) }
    val totalSteps = 4

    LaunchedEffect(isSaved) {
        if (isSaved) onCreated()
    }

    if (isSaved) return

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditing) stringResource(R.string.edit_forest_title, currentStep + 1, totalSteps)
                        else stringResource(R.string.create_forest_title, currentStep + 1, totalSteps)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Space.screenH)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            LinearProgressIndicator(
                progress = { (currentStep + 1f) / totalSteps },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.xs),
            )

            when (currentStep) {
                0 -> StepIdentity(
                    form = form,
                    onNomChange = viewModel::updateNom,
                    onDepartementsChange = viewModel::updateDepartements,
                )
                1 -> StepOwner(
                    form = form,
                    onProprietaireNomChange = viewModel::updateProprietaireNom,
                    onProprietaireEmailChange = viewModel::updateProprietaireEmail,
                    onGestionnaireNomChange = viewModel::updateGestionnaireNom,
                )
                2 -> StepManagement(
                    form = form,
                    onTypeForetChange = viewModel::updateTypeForet,
                    onObjectifGestionChange = viewModel::updateObjectifGestion,
                    onPsgNumeroChange = viewModel::updatePsgNumero,
                    onRemarquesChange = viewModel::updateRemarques,
                )
                3 -> StepColor(
                    selected = form.color,
                    onSelect = viewModel::updateColor,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Space.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (currentStep > 0) {
                    OutlinedButton(onClick = { currentStep-- }) {
                        Text(stringResource(R.string.previous))
                    }
                } else {
                    TextButton(onClick = onNavigateBack) { Text(stringResource(R.string.cancel)) }
                }

                if (currentStep < totalSteps - 1) {
                    val canAdvance = when (currentStep) {
                        0 -> form.step1Valid
                        1 -> form.step2Valid
                        2 -> form.step3Valid
                        else -> form.step4Valid
                    }
                    Button(
                        onClick = { currentStep++ },
                        enabled = canAdvance,
                    ) {
                        Text(stringResource(R.string.next))
                        Icon(Icons.Filled.NavigateNext, contentDescription = null)
                    }
                } else {
                    Button(
                        onClick = viewModel::save,
                        enabled = form.isValid,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Text(
                            if (isEditing) stringResource(R.string.save)
                            else stringResource(R.string.create)
                        )
                    }
                }
            }
        }
    }
}

// ─── En-tête de chaque étape ─────────────────────────────────────────────────

@Composable
private fun StepHeader(icon: ImageVector, title: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = GsShape.md,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = GsShape.sm,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .padding(Space.xs)
                        .size(24.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

// ─── Sélecteur de chips générique ────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipSelector(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Space.xs),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            verticalArrangement = Arrangement.spacedBy(Space.xxs),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelect(if (selected == option) "" else option) },
                    label = { Text(option) },
                    leadingIcon = if (selected == option) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                )
            }
        }
    }
}

// ─── Étape 1 : Identité ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepIdentity(
    form: CreateForestFormState,
    onNomChange: (String) -> Unit,
    onDepartementsChange: (List<String>) -> Unit,
) {
    StepHeader(
        icon = Icons.Filled.Forest,
        title = stringResource(R.string.create_forest_step_identity),
    )

    OutlinedTextField(
        value = form.nom,
        onValueChange = onNomChange,
        label = { Text(stringResource(R.string.create_forest_name)) },
        leadingIcon = { Icon(Icons.Filled.Forest, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    DepartementMultiPicker(
        selected = form.departements,
        onSelectionChange = onDepartementsChange,
    )
}

// ─── Sélecteur multi-département ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DepartementMultiPicker(
    selected: List<String>,
    onSelectionChange: (List<String>) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Affichage des sélections courantes
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        OutlinedTextField(
            value = if (selected.isEmpty()) "" else selected.joinToString(", "),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.create_forest_department)) },
            leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { showDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDialog = true },
            placeholder = { Text(stringResource(R.string.create_forest_departments_hint)) },
            minLines = if (selected.size > 2) 2 else 1,
        )
        if (selected.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalArrangement = Arrangement.spacedBy(Space.xxs),
            ) {
                selected.forEach { dept ->
                    FilterChip(
                        selected = true,
                        onClick = { onSelectionChange(selected - dept) },
                        label = { Text(dept.substringBefore(" -").trim()) },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                        },
                    )
                }
            }
        }
    }

    if (showDialog) {
        val filtered = remember(searchQuery) {
            DEPARTEMENTS_FRANCE.filter { dept ->
                searchQuery.isBlank() || dept.contains(searchQuery, ignoreCase = true)
            }
        }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.create_forest_department)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Rechercher…") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.height(320.dp),
                    ) {
                        items(filtered.size) { idx ->
                            val dept = filtered[idx]
                            val isChecked = selected.contains(dept)
                            ListItem(
                                headlineContent = { Text(dept, style = MaterialTheme.typography.bodyMedium) },
                                leadingContent = {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            onSelectionChange(
                                                if (checked) selected + dept else selected - dept
                                            )
                                        },
                                    )
                                },
                                modifier = Modifier.clickable {
                                    onSelectionChange(
                                        if (isChecked) selected - dept else selected + dept
                                    )
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showDialog = false; searchQuery = "" }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false; searchQuery = "" }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

// ─── Étape 2 : Propriétaire ──────────────────────────────────────────────────

@Composable
private fun StepOwner(
    form: CreateForestFormState,
    onProprietaireNomChange: (String) -> Unit,
    onProprietaireEmailChange: (String) -> Unit,
    onGestionnaireNomChange: (String) -> Unit,
) {
    StepHeader(
        icon = Icons.Filled.AccountCircle,
        title = stringResource(R.string.create_forest_step_owner),
    )

    OutlinedTextField(
        value = form.proprietaireNom,
        onValueChange = onProprietaireNomChange,
        label = { Text(stringResource(R.string.create_forest_owner_name)) },
        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.proprietaireEmail ?: "",
        onValueChange = onProprietaireEmailChange,
        label = { Text(stringResource(R.string.create_forest_owner_email)) },
        leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.gestionnaireNom ?: "",
        onValueChange = onGestionnaireNomChange,
        label = { Text(stringResource(R.string.create_forest_manager_name)) },
        leadingIcon = { Icon(Icons.Filled.ManageAccounts, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

// ─── Étape 3 : Gestion ───────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepManagement(
    form: CreateForestFormState,
    onTypeForetChange: (String) -> Unit,
    onObjectifGestionChange: (String) -> Unit,
    onPsgNumeroChange: (String) -> Unit,
    onRemarquesChange: (String) -> Unit,
) {
    StepHeader(
        icon = Icons.Filled.Tune,
        title = stringResource(R.string.create_forest_step_management),
    )

    ChipSelector(
        label = stringResource(R.string.create_forest_type),
        options = TYPES_FORET,
        selected = form.typeForet,
        onSelect = onTypeForetChange,
    )

    ChipSelector(
        label = stringResource(R.string.create_forest_objective),
        options = OBJECTIFS_GESTION,
        selected = form.objectifGestion,
        onSelect = onObjectifGestionChange,
    )

    OutlinedTextField(
        value = form.psgNumero ?: "",
        onValueChange = onPsgNumeroChange,
        label = { Text(stringResource(R.string.create_forest_psg_number)) },
        leadingIcon = { Icon(Icons.Filled.Article, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.remarques ?: "",
        onValueChange = onRemarquesChange,
        label = { Text(stringResource(R.string.create_forest_remarks)) },
        leadingIcon = { Icon(Icons.Filled.Notes, contentDescription = null) },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
}

// ─── Étape 4 : Couleur ────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepColor(
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    StepHeader(
        icon = Icons.Filled.Palette,
        title = stringResource(R.string.create_forest_color_title),
    )

    Text(
        text = stringResource(R.string.create_forest_color_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Space.xs),
    )

    Spacer(Modifier.height(Space.xs))

    // Aperçu de la carte avec la couleur choisie
    val cleanedColor = selected?.trim()
    val previewColor = cleanedColor?.let { c ->
        try { Color(android.graphics.Color.parseColor(c)) } catch (_: Exception) { null }
    }
    Surface(
        color = previewColor ?: MaterialTheme.colorScheme.surface,
        shape = GsShape.md,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        tonalElevation = 4.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(Space.md),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = "Ma forêt",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (previewColor != null) {
                    if (previewColor.luminance() > 0.4f) Color.Black else Color.White
                } else MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    Spacer(Modifier.height(Space.sm))

    // Grille de couleurs
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Option automatique (sans couleur)
        val autoSelected = cleanedColor.isNullOrBlank()
        Surface(
            modifier = Modifier
                .size(52.dp)
                .clickable { onSelect(null) },
            color = MaterialTheme.colorScheme.surface,
            shape = CircleShape,
            border = BorderStroke(
                if (autoSelected) 2.5.dp else 1.dp,
                if (autoSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            ),
            tonalElevation = if (autoSelected) 6.dp else 0.dp,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Auto", style = MaterialTheme.typography.labelSmall)
            }
        }

        COLOR_PALETTE.forEach { (hex, label) ->
            val col = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { return@forEach }
            val isSelected = cleanedColor.equals(hex, ignoreCase = true)
            Surface(
                modifier = Modifier
                    .size(52.dp)
                    .clickable { onSelect(hex) },
                color = col,
                shape = CircleShape,
                border = if (isSelected) BorderStroke(2.5.dp, MaterialTheme.colorScheme.onSurface) else null,
                tonalElevation = if (isSelected) 8.dp else 0.dp,
                shadowElevation = if (isSelected) 6.dp else 2.dp,
            ) {
                if (isSelected) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = if (col.luminance() > 0.4f) Color.Black else Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(Space.xs))

    // Champ hex pour couleur personnalisée
    var hexInput by remember { mutableStateOf(cleanedColor ?: "") }
    OutlinedTextField(
        value = hexInput,
        onValueChange = { raw ->
            hexInput = raw
            val cleaned = raw.trim().let { if (it.startsWith("#")) it else "#$it" }
            if (cleaned.matches(Regex("^#(?i)[0-9A-F]{6}$"))) onSelect(cleaned.uppercase())
        },
        label = { Text(stringResource(R.string.color_hex_optional)) },
        leadingIcon = { Icon(Icons.Filled.Palette, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("#2E7D32") },
    )
}

// ─── Données statiques ────────────────────────────────────────────────────────

private val TYPES_FORET = listOf(
    "Futaie régulière",
    "Futaie irrégulière",
    "Taillis",
    "Taillis sous futaie",
    "Forêt mixte",
)

private val OBJECTIFS_GESTION = listOf(
    "Production de bois",
    "Conservation",
    "Accueil du public",
    "Protection des sols",
    "Chasse",
    "Mixte",
)

/** Palette organisée par familles de couleurs, avec labels accessibles. */
private val COLOR_PALETTE = listOf(
    // Verts forêt
    "#1B5E20" to "Vert sombre",
    "#2E7D32" to "Vert forêt",
    "#388E3C" to "Vert",
    "#4CAF50" to "Vert clair",
    "#81C784" to "Vert pâle",
    "#A5D6A7" to "Vert très pâle",
    // Teals
    "#004D40" to "Teal sombre",
    "#00695C" to "Teal",
    "#26A69A" to "Teal clair",
    "#80CBC4" to "Teal pâle",
    // Bleus
    "#0D47A1" to "Bleu nuit",
    "#1565C0" to "Bleu marine",
    "#1976D2" to "Bleu",
    "#42A5F5" to "Bleu ciel",
    "#90CAF9" to "Bleu pâle",
    // Violets
    "#4A148C" to "Violet sombre",
    "#7B1FA2" to "Violet",
    "#AB47BC" to "Violet clair",
    // Oranges / automne
    "#E65100" to "Orange sombre",
    "#F57C00" to "Orange",
    "#FFB74D" to "Orange clair",
    "#FDD835" to "Jaune",
    // Rouges / bruns
    "#B71C1C" to "Rouge sombre",
    "#E53935" to "Rouge",
    "#8D6E63" to "Brun",
    "#6D4C41" to "Brun sombre",
    // Gris / neutres
    "#546E7A" to "Bleu-gris",
    "#78909C" to "Gris-bleu",
    "#607D8B" to "Gris acier",
)

private val DEPARTEMENTS_FRANCE = listOf(
    "01 - Ain", "02 - Aisne", "03 - Allier", "04 - Alpes-de-Haute-Provence",
    "05 - Hautes-Alpes", "06 - Alpes-Maritimes", "07 - Ardèche", "08 - Ardennes",
    "09 - Ariège", "10 - Aube", "11 - Aude", "12 - Aveyron",
    "13 - Bouches-du-Rhône", "14 - Calvados", "15 - Cantal", "16 - Charente",
    "17 - Charente-Maritime", "18 - Cher", "19 - Corrèze", "2A - Corse-du-Sud",
    "2B - Haute-Corse", "21 - Côte-d'Or", "22 - Côtes-d'Armor", "23 - Creuse",
    "24 - Dordogne", "25 - Doubs", "26 - Drôme", "27 - Eure",
    "28 - Eure-et-Loir", "29 - Finistère", "30 - Gard", "31 - Haute-Garonne",
    "32 - Gers", "33 - Gironde", "34 - Hérault", "35 - Ille-et-Vilaine",
    "36 - Indre", "37 - Indre-et-Loire", "38 - Isère", "39 - Jura",
    "40 - Landes", "41 - Loir-et-Cher", "42 - Loire", "43 - Haute-Loire",
    "44 - Loire-Atlantique", "45 - Loiret", "46 - Lot", "47 - Lot-et-Garonne",
    "48 - Lozère", "49 - Maine-et-Loire", "50 - Manche", "51 - Marne",
    "52 - Haute-Marne", "53 - Mayenne", "54 - Meurthe-et-Moselle", "55 - Meuse",
    "56 - Morbihan", "57 - Moselle", "58 - Nièvre", "59 - Nord",
    "60 - Oise", "61 - Orne", "62 - Pas-de-Calais", "63 - Puy-de-Dôme",
    "64 - Pyrénées-Atlantiques", "65 - Hautes-Pyrénées", "66 - Pyrénées-Orientales",
    "67 - Bas-Rhin", "68 - Haut-Rhin", "69 - Rhône", "70 - Haute-Saône",
    "71 - Saône-et-Loire", "72 - Sarthe", "73 - Savoie", "74 - Haute-Savoie",
    "75 - Paris", "76 - Seine-Maritime", "77 - Seine-et-Marne", "78 - Yvelines",
    "79 - Deux-Sèvres", "80 - Somme", "81 - Tarn", "82 - Tarn-et-Garonne",
    "83 - Var", "84 - Vaucluse", "85 - Vendée", "86 - Vienne",
    "87 - Haute-Vienne", "88 - Vosges", "89 - Yonne", "90 - Territoire de Belfort",
    "91 - Essonne", "92 - Hauts-de-Seine", "93 - Seine-Saint-Denis", "94 - Val-de-Marne",
    "95 - Val-d'Oise",
    "971 - Guadeloupe", "972 - Martinique", "973 - Guyane",
    "974 - La Réunion", "976 - Mayotte",
)
