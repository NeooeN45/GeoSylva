package com.forestry.counter.presentation.screens.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.forestry.counter.data.preferences.FontSize
import com.forestry.counter.data.preferences.GpsCaptureMode
import com.forestry.counter.data.preferences.ThemeMode
import com.forestry.counter.data.preferences.UserPreferencesManager
import kotlinx.coroutines.launch
import com.forestry.counter.R
import com.forestry.counter.presentation.theme.AccentGreen
import com.forestry.counter.presentation.theme.AccentBlue
import com.forestry.counter.presentation.theme.AccentTeal
import com.forestry.counter.presentation.theme.AccentOrange
import com.forestry.counter.presentation.theme.AccentPurple
import com.forestry.counter.presentation.theme.AccentRed
import com.forestry.counter.presentation.components.AppMiniDialog
import com.forestry.counter.data.logging.CrashLogger
import com.forestry.counter.domain.usecase.export.ExportDataUseCase
import com.forestry.counter.domain.repository.ParameterRepository
import com.forestry.counter.domain.repository.TigeRepository
import com.forestry.counter.domain.repository.EssenceRepository
import com.forestry.counter.domain.repository.ParcelleRepository
import com.forestry.counter.domain.repository.PlacetteRepository
import com.forestry.counter.domain.parameters.ParameterKeys
import com.forestry.counter.domain.model.ParameterItem
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.calculation.ForestryCalculator
import com.forestry.counter.domain.calculation.tarifs.TarifMethod
import com.forestry.counter.domain.calculation.tarifs.TarifSelection
import com.forestry.counter.domain.calculation.tarifs.TarifCalculator
import com.forestry.counter.domain.usecase.export.QgisExportHelper
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.builtins.ListSerializer
import com.forestry.counter.domain.calculation.PriceEntry
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import java.util.concurrent.TimeUnit
import com.forestry.counter.data.work.BackupWorker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.BorderStroke
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Data
import androidx.core.os.LocaleListCompat
import com.forestry.counter.BuildConfig
import com.forestry.counter.data.work.PriceSyncWorker
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    preferencesManager: UserPreferencesManager,
    exportDataUseCase: ExportDataUseCase,
    parameterRepository: ParameterRepository,
    tigeRepository: TigeRepository? = null,
    essenceRepository: EssenceRepository? = null,
    forestryCalculator: ForestryCalculator? = null,
    parcelleRepository: ParcelleRepository? = null,
    placetteRepository: PlacetteRepository? = null,
    offlineTileManager: com.forestry.counter.domain.location.OfflineTileManager? = null,
    onNavigateToPriceTablesEditor: () -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    fun xmlEscape(s: String): String = buildString {
        s.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '\"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }

    fun csvCell(value: String?): String {
        val v = value ?: ""
        if (v.isEmpty()) return ""
        val cleaned = v.replace("\r\n", "\n").replace("\r", "\n")
        val mustQuote = cleaned.contains(';') || cleaned.contains('\n') || cleaned.contains('"')
        return if (!mustQuote) cleaned else "\"" + cleaned.replace("\"", "\"\"") + "\""
    }
    val placeholderDash = stringResource(R.string.placeholder_dash)
    val versionName = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: placeholderDash
        } catch (e: Exception) { placeholderDash }
    }

    val versionDisplay = remember(versionName) {
        val buildId = BuildConfig.BUILD_ID
        if (buildId.isNotBlank()) "$versionName ($buildId)" else versionName
    }

    

    val themeMode by preferencesManager.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val fontSize by preferencesManager.fontSize.collectAsState(initial = FontSize.MEDIUM)
    val animationsEnabled by preferencesManager.animationsEnabled.collectAsState(initial = true)
    val hapticEnabled by preferencesManager.hapticEnabled.collectAsState(initial = true)
    val csvSeparator by preferencesManager.csvSeparator.collectAsState(initial = ",")
    val accentColor by preferencesManager.accentColor.collectAsState(initial = "#4CAF50")
    val dynamicColorEnabled by preferencesManager.dynamicColorEnabled.collectAsState(initial = true)
    val backgroundImageEnabled by preferencesManager.backgroundImageEnabled.collectAsState(initial = true)
    val soundEnabled by preferencesManager.soundEnabled.collectAsState(initial = true)
    val hapticLevel by preferencesManager.hapticIntensity.collectAsState(initial = 2)
    val appLanguage by preferencesManager.appLanguage.collectAsState(initial = "system")
    val keepScreenOn by preferencesManager.keepScreenOn.collectAsState(initial = false)
    val mapOnlyReliableGps by preferencesManager.mapOnlyReliableGps.collectAsState(initial = false)
    val mapReliableGpsThresholdM by preferencesManager.mapReliableGpsThresholdM.collectAsState(initial = 8f)
    var showCsvDialog by remember { mutableStateOf(false) }

    // Tarif de cubage
    var showTarifDialog by remember { mutableStateOf(false) }
    var showTarifNumeroDialog by remember { mutableStateOf(false) }
    var currentTarifMethod by remember { mutableStateOf(TarifMethod.ALGAN) }
    var currentTarifNumero by remember { mutableStateOf<Int?>(null) }
    var tarifNumeroInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        forestryCalculator?.let { calc ->
            val sel = calc.loadTarifSelection()
            currentTarifMethod = TarifMethod.fromCode(sel?.method ?: "") ?: TarifMethod.ALGAN
            currentTarifNumero = sel?.schaefferNumero ?: sel?.ifnNumero
        }
    }

    if (showTarifDialog) {
        AlertDialog(
            onDismissRequest = { showTarifDialog = false },
            title = { Text(stringResource(R.string.tarif_select_dialog_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TarifMethod.entries.forEach { method ->
                        val labelRes = when (method) {
                            TarifMethod.SCHAEFFER_1E -> R.string.tarif_method_schaeffer_1e
                            TarifMethod.SCHAEFFER_2E -> R.string.tarif_method_schaeffer_2e
                            TarifMethod.ALGAN -> R.string.tarif_method_algan
                            TarifMethod.IFN_RAPIDE -> R.string.tarif_method_ifn_rapide
                            TarifMethod.IFN_LENT -> R.string.tarif_method_ifn_lent
                            TarifMethod.FGH -> R.string.tarif_method_fgh
                            TarifMethod.COEF_FORME -> R.string.tarif_method_coef_forme
                        }
                        val descRes = when (method) {
                            TarifMethod.SCHAEFFER_1E -> R.string.tarif_method_schaeffer_1e_desc
                            TarifMethod.SCHAEFFER_2E -> R.string.tarif_method_schaeffer_2e_desc
                            TarifMethod.ALGAN -> R.string.tarif_method_algan_desc
                            TarifMethod.IFN_RAPIDE -> R.string.tarif_method_ifn_rapide_desc
                            TarifMethod.IFN_LENT -> R.string.tarif_method_ifn_lent_desc
                            TarifMethod.FGH -> R.string.tarif_method_fgh_desc
                            TarifMethod.COEF_FORME -> R.string.tarif_method_coef_forme_desc
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentTarifMethod = method
                                    currentTarifNumero = null
                                    scope.launch {
                                        forestryCalculator?.saveTarifSelection(
                                            TarifSelection(method = method.code)
                                        )
                                        snackbarHostState.showSnackbar(context.getString(R.string.settings_tarif_saved))
                                    }
                                    showTarifDialog = false
                                    // Si le tarif a des numéros, ouvrir le dialogue numéro
                                    if (TarifCalculator.availableTarifNumbers(method) != null) {
                                        showTarifNumeroDialog = true
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentTarifMethod == method,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(labelRes),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(descRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (method.entrees == 1) {
                                    Text(
                                        text = stringResource(R.string.tarif_no_height_required),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTarifDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    if (showTarifNumeroDialog) {
        val range = TarifCalculator.availableTarifNumbers(currentTarifMethod)
        if (range != null) {
            AlertDialog(
                onDismissRequest = { showTarifNumeroDialog = false },
                title = { Text(stringResource(R.string.tarif_numero_dialog_title)) },
                text = {
                    Column {
                        Text(
                            stringResource(R.string.tarif_numero_range_format, range.first, range.last),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = tarifNumeroInput,
                            onValueChange = { tarifNumeroInput = it.filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.settings_tarif_numero_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val num = tarifNumeroInput.toIntOrNull()
                        if (num != null && num in range) {
                            currentTarifNumero = num
                            scope.launch {
                                val sel = when (currentTarifMethod) {
                                    TarifMethod.SCHAEFFER_1E, TarifMethod.SCHAEFFER_2E ->
                                        TarifSelection(method = currentTarifMethod.code, schaefferNumero = num)
                                    TarifMethod.IFN_RAPIDE, TarifMethod.IFN_LENT ->
                                        TarifSelection(method = currentTarifMethod.code, ifnNumero = num)
                                    else -> TarifSelection(method = currentTarifMethod.code)
                                }
                                forestryCalculator?.saveTarifSelection(sel)
                                snackbarHostState.showSnackbar(context.getString(R.string.settings_tarif_saved))
                            }
                        }
                        showTarifNumeroDialog = false
                    }) {
                        Text(stringResource(R.string.save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTarifNumeroDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
    BackHandler(enabled = showCsvDialog) {
        if (showCsvDialog) showCsvDialog = false
        else onNavigateBack()
    }

    // Backup launcher (manual)
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val res = exportDataUseCase.exportToZip(uri)
                snackbarHostState.showSnackbar(
                    res.fold(
                        { context.getString(R.string.settings_backup_created) },
                        { e -> context.getString(R.string.settings_backup_failed, e.message ?: "") }
                    )
                )
            }
        }
    }

    val backgroundImagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }
            scope.launch {
                preferencesManager.setBackgroundImageEnabled(true)
                preferencesManager.setBackgroundImageUri(uri.toString())
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Appearance Section
            SettingsSection(title = stringResource(R.string.appearance)) {
                SettingsItem(
                    icon = Icons.Default.Palette,
                    title = stringResource(R.string.theme),
                    subtitle = when (themeMode) {
                        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                        ThemeMode.DARK -> stringResource(R.string.theme_dark)
                        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                    }
                ) {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.theme)) },
                            supportingContent = { 
                                Text(when (themeMode) {
                                    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                    ThemeMode.DARK -> stringResource(R.string.theme_dark)
                                    ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                })
                            },
                            leadingContent = {
                                Icon(Icons.Default.Palette, contentDescription = null)
                            },
                            modifier = Modifier.clickable { expanded = true }
                        )

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.theme_light)) },
                                onClick = {
                                    scope.launch {
                                        preferencesManager.setThemeMode(ThemeMode.LIGHT)
                                    }
                                    expanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.theme_dark)) },
                                onClick = {
                                    scope.launch {
                                        preferencesManager.setThemeMode(ThemeMode.DARK)
                                    }
                                    expanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.theme_system)) },
                                onClick = {
                                    scope.launch {
                                        preferencesManager.setThemeMode(ThemeMode.SYSTEM)
                                    }
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                SettingsItem(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.language),
                    subtitle = when (appLanguage) {
                        "system" -> stringResource(R.string.system)
                        "fr" -> stringResource(R.string.french)
                        "en" -> stringResource(R.string.english)
                        else -> appLanguage
                    }
                ) {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.language)) },
                            supportingContent = { Text(when (appLanguage) { "system" -> stringResource(R.string.system); "fr" -> stringResource(R.string.french); "en" -> stringResource(R.string.english); else -> appLanguage }) },
                            leadingContent = { Icon(Icons.Default.Language, contentDescription = null) },
                            modifier = Modifier.clickable { expanded = true }
                        )
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.system)) }, onClick = {
                                scope.launch { preferencesManager.setAppLanguage("system") }
                                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                                expanded = false
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.french)) }, onClick = {
                                scope.launch { preferencesManager.setAppLanguage("fr") }
                                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fr"))
                                expanded = false
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.english)) }, onClick = {
                                scope.launch { preferencesManager.setAppLanguage("en") }
                                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                                expanded = false
                            })
                        }
                    }
                }

                SettingsItem(
                    icon = Icons.Default.FormatSize,
                    title = stringResource(R.string.font_size),
                    subtitle = when (fontSize) {
                        FontSize.SMALL -> stringResource(R.string.small)
                        FontSize.MEDIUM -> stringResource(R.string.normal_size)
                        FontSize.LARGE -> stringResource(R.string.large)
                    }
                ) {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.font_size)) },
                            supportingContent = { 
                                Text(when (fontSize) {
                                    FontSize.SMALL -> stringResource(R.string.small)
                                    FontSize.MEDIUM -> stringResource(R.string.normal_size)
                                    FontSize.LARGE -> stringResource(R.string.large)
                                })
                            },
                            leadingContent = {
                                Icon(Icons.Default.FormatSize, contentDescription = null)
                            },
                            modifier = Modifier.clickable { expanded = true }
                        )

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.small)) },
                                onClick = {
                                    scope.launch {
                                        preferencesManager.setFontSize(FontSize.SMALL)
                                    }
                                    expanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.normal_size)) },
                                onClick = {
                                    scope.launch {
                                        preferencesManager.setFontSize(FontSize.MEDIUM)
                                    }
                                    expanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.large)) },
                                onClick = {
                                    scope.launch {
                                        preferencesManager.setFontSize(FontSize.LARGE)
                                    }
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                // Dynamic Color toggle
                ListItem(
                    headlineContent = { Text(stringResource(R.string.dynamic_color)) },
                    supportingContent = { Text(stringResource(R.string.use_system_palette)) },
                    leadingContent = { Icon(Icons.Default.ColorLens, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = dynamicColorEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch { preferencesManager.setDynamicColorEnabled(enabled) }
                            }
                        )
                    }
                )

                // Forest background image toggle
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_background_image)) },
                    supportingContent = { Text(stringResource(R.string.settings_background_image_desc)) },
                    leadingContent = { Icon(Icons.Default.Image, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = backgroundImageEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch { preferencesManager.setBackgroundImageEnabled(enabled) }
                            }
                        )
                    }
                )

                Text(
                    text = stringResource(R.string.settings_background_choice),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {
                            scope.launch {
                                preferencesManager.setBackgroundImageEnabled(true)
                                preferencesManager.setBackgroundImageUri(null)
                            }
                        },
                        label = { Text(stringResource(R.string.settings_background_default_forest)) },
                        leadingIcon = { Icon(Icons.Default.Forest, contentDescription = null) }
                    )
                    AssistChip(
                        onClick = {
                            backgroundImagePickerLauncher.launch(arrayOf("image/*"))
                        },
                        label = { Text(stringResource(R.string.settings_background_personal_photo)) },
                        leadingIcon = { Icon(Icons.Default.Photo, contentDescription = null) }
                    )
                }

                // Accent Color chips (neon green/teal palette) with selection ring
                Text(text = stringResource(R.string.accent_color), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                var showMoreAccent by remember { mutableStateOf(false) }
                FlowRow(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val options = listOf(
                        "#00E676", "#00C853", "#64FFDA", "#1DE9B6", "#A5FF8B", "#B9F6CA", "#69F0AE", "#18FFFF", "#00BFA5", "#00FF88",
                        "#00FF66", "#00FFA2", "#00FFC6", "#66FF99", "#33FFAA", "#00FFD1", "#11FFEE", "#22E3B3", "#49FFDF", "#5BFFB5",
                        "#C6FF00", "#AEEA00", "#00BCD4", "#00E5FF", "#2979FF", "#00B0FF", "#7C4DFF", "#E040FB", "#FFC400", "#FFAB00", "#FF5252", "#FF6E6E"
                    )
                    val list = if (showMoreAccent) options else options.take(10)
                    list.forEach { hex ->
                        val col = try { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { MaterialTheme.colorScheme.primary }
                        val selected = accentColor.equals(hex, true)
                        Surface(
                            modifier = Modifier
                                .size(32.dp)
                                .clickable { scope.launch { preferencesManager.setAccentColor(hex) } },
                            color = col,
                            shape = MaterialTheme.shapes.small,
                            border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        ) {}
                    }
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    TextButton(onClick = { showMoreAccent = !showMoreAccent }) { Text(stringResource(if (showMoreAccent) R.string.less_colors else R.string.more_colors)) }
                }

                // Keep screen on
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_keep_screen_on)) },
                    supportingContent = { Text(stringResource(R.string.settings_keep_screen_on_desc)) },
                    leadingContent = { Icon(Icons.Default.ScreenLockPortrait, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = keepScreenOn,
                            onCheckedChange = { enabled -> scope.launch { preferencesManager.setKeepScreenOn(enabled) } }
                        )
                    }
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_map_only_reliable_gps)) },
                    supportingContent = { Text(stringResource(R.string.settings_map_only_reliable_gps_desc)) },
                    leadingContent = { Icon(Icons.Default.Map, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = mapOnlyReliableGps,
                            onCheckedChange = { enabled ->
                                scope.launch { preferencesManager.setMapOnlyReliableGps(enabled) }
                            }
                        )
                    }
                )

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = stringResource(
                            R.string.settings_map_reliable_gps_threshold_format,
                            mapReliableGpsThresholdM
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = mapReliableGpsThresholdM,
                        onValueChange = { value ->
                            scope.launch { preferencesManager.setMapReliableGpsThresholdM(value) }
                        },
                        valueRange = 3f..40f,
                        steps = 36
                    )
                    Text(
                        text = stringResource(R.string.settings_map_reliable_gps_threshold_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                
            }

            HorizontalDivider()

            // Tarifs de cubage
            SettingsSection(title = stringResource(R.string.settings_section_tarifs)) {
                val tarifLabelRes = when (currentTarifMethod) {
                    TarifMethod.SCHAEFFER_1E -> R.string.tarif_method_schaeffer_1e
                    TarifMethod.SCHAEFFER_2E -> R.string.tarif_method_schaeffer_2e
                    TarifMethod.ALGAN -> R.string.tarif_method_algan
                    TarifMethod.IFN_RAPIDE -> R.string.tarif_method_ifn_rapide
                    TarifMethod.IFN_LENT -> R.string.tarif_method_ifn_lent
                    TarifMethod.FGH -> R.string.tarif_method_fgh
                    TarifMethod.COEF_FORME -> R.string.tarif_method_coef_forme
                }
                val currentLabel = stringResource(tarifLabelRes)
                val subtitle = if (currentTarifNumero != null) {
                    stringResource(R.string.settings_tarif_current_format, currentLabel) +
                        " — " + stringResource(R.string.settings_tarif_numero_format, currentTarifNumero!!)
                } else {
                    stringResource(R.string.settings_tarif_current_format, currentLabel)
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_tarif_method)) },
                    supportingContent = { Text(subtitle) },
                    leadingContent = { Icon(Icons.Default.Calculate, contentDescription = null) },
                    modifier = Modifier.clickable { showTarifDialog = true }
                )

                // Numéro de tarif (si applicable)
                val range = TarifCalculator.availableTarifNumbers(currentTarifMethod)
                if (range != null) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_tarif_numero_label)) },
                        supportingContent = {
                            Text(
                                if (currentTarifNumero != null) {
                                    stringResource(R.string.settings_tarif_numero_format, currentTarifNumero!!)
                                } else {
                                    stringResource(R.string.tarif_numero_range_format, range.first, range.last)
                                }
                            )
                        },
                        leadingContent = { Icon(Icons.Default.Tag, contentDescription = null) },
                        modifier = Modifier.clickable {
                            tarifNumeroInput = currentTarifNumero?.toString() ?: ""
                            showTarifNumeroDialog = true
                        }
                    )
                }

                // Info hauteur requise ou non
                ListItem(
                    headlineContent = {
                        Text(
                            if (TarifCalculator.requiresHeight(currentTarifMethod)) {
                                stringResource(R.string.tarif_requires_height)
                            } else {
                                stringResource(R.string.tarif_no_height_required)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (TarifCalculator.requiresHeight(currentTarifMethod)) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    },
                    leadingContent = {
                        Icon(
                            if (TarifCalculator.requiresHeight(currentTarifMethod)) Icons.Default.Height else Icons.Default.Speed,
                            contentDescription = null,
                            tint = if (TarifCalculator.requiresHeight(currentTarifMethod)) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                )
            }

            HorizontalDivider()

            // Produits & Prix
            SettingsSection(title = stringResource(R.string.settings_section_products_prices)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_edit_price_tables)) },
                    supportingContent = { Text(stringResource(R.string.settings_edit_price_tables_desc)) },
                    leadingContent = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                    modifier = Modifier.clickable { onNavigateToPriceTablesEditor() }
                )
            }

            HorizontalDivider()

            // Carte hors-ligne
            if (offlineTileManager != null) {
                var cacheStats by remember { mutableStateOf<Pair<Int, Long>?>(null) }
                var showClearDialog by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        cacheStats = offlineTileManager.cacheStats()
                    }
                }

                SettingsSection(title = stringResource(R.string.settings_section_offline_map)) {
                    val stats = cacheStats
                    val tileCount = stats?.first ?: 0
                    val sizeMb = stats?.second?.let { String.format("%.1f", it / 1_048_576.0) } ?: "0"

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.offline_cache_title)) },
                        supportingContent = {
                            Text(
                                if (tileCount > 0) stringResource(R.string.offline_cache_stats_format, tileCount, sizeMb)
                                else stringResource(R.string.offline_cache_empty)
                            )
                        },
                        leadingContent = { Icon(Icons.Default.CloudDownload, contentDescription = null) }
                    )

                    if (tileCount > 0) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.offline_cache_clear)) },
                            supportingContent = { Text(stringResource(R.string.offline_cache_clear_desc)) },
                            leadingContent = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            modifier = Modifier.clickable { showClearDialog = true }
                        )
                    }
                }

                if (showClearDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearDialog = false },
                        confirmButton = {
                            Button(
                                onClick = {
                                    scope.launch {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            offlineTileManager.clearCache()
                                        }
                                        cacheStats = 0 to 0L
                                        showClearDialog = false
                                        snackbarHostState.showSnackbar(context.getString(R.string.offline_cache_cleared))
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) { Text(stringResource(R.string.delete)) }
                        },
                        dismissButton = {
                            FilledTonalButton(onClick = { showClearDialog = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                        },
                        icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                        title = { Text(stringResource(R.string.offline_cache_clear)) },
                        text = { Text(stringResource(R.string.offline_cache_clear_confirm)) }
                    )
                }

                HorizontalDivider()
            }


            // Exports Forestry
            if (tigeRepository != null && forestryCalculator != null) {
                SettingsSection(title = stringResource(R.string.settings_section_forestry_exports)) {
                    var exportScope by remember { mutableStateOf("PROJECT") }
                    var selectedParcelleId by remember { mutableStateOf<String?>(null) }
                    var selectedPlacetteId by remember { mutableStateOf<String?>(null) }
                    var parcelles by remember { mutableStateOf<List<Parcelle>>(emptyList()) }
                    var placettes by remember { mutableStateOf<List<Placette>>(emptyList()) }

                    LaunchedEffect(parcelleRepository) {
                        if (parcelleRepository != null) {
                            parcelles = parcelleRepository.getAllParcelles().first()
                        } else {
                            parcelles = emptyList()
                        }
                    }

                    LaunchedEffect(selectedParcelleId, placetteRepository) {
                        if (selectedParcelleId != null && placetteRepository != null) {
                            placettes = placetteRepository.getPlacettesByParcelle(selectedParcelleId!!).first()
                        } else {
                            placettes = emptyList()
                        }
                    }

                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(stringResource(R.string.settings_export_scope), style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = exportScope == "PROJECT",
                                onClick = {
                                    exportScope = "PROJECT"
                                },
                                label = { Text(stringResource(R.string.settings_export_scope_project)) }
                            )
                            FilterChip(
                                selected = exportScope == "PARCELLE",
                                onClick = {
                                    exportScope = "PARCELLE"
                                },
                                label = { Text(stringResource(R.string.settings_export_scope_parcelle)) }
                            )
                            FilterChip(
                                selected = exportScope == "PLACETTE",
                                onClick = {
                                    exportScope = "PLACETTE"
                                },
                                label = { Text(stringResource(R.string.settings_export_scope_placette)) }
                            )
                        }

                        if (exportScope == "PARCELLE" || exportScope == "PLACETTE") {
                            val selectedParcelleName = parcelles.firstOrNull { it.id == selectedParcelleId }?.name
                            var parcelleMenuExpanded by remember { mutableStateOf(false) }
                            Box {
                                ListItem(
                                    headlineContent = { Text(stringResource(R.string.settings_export_parcelle)) },
                                    supportingContent = { Text(selectedParcelleName ?: stringResource(R.string.settings_export_choose_parcelle)) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { parcelleMenuExpanded = true }
                                )
                                DropdownMenu(
                                    expanded = parcelleMenuExpanded,
                                    onDismissRequest = { parcelleMenuExpanded = false }
                                ) {
                                    parcelles.forEach { p ->
                                        DropdownMenuItem(
                                            text = { Text(p.name) },
                                            onClick = {
                                                selectedParcelleId = p.id
                                                selectedPlacetteId = null
                                                parcelleMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (exportScope == "PLACETTE" && selectedParcelleId != null) {
                            val selectedPlacetteName = placettes.firstOrNull { it.id == selectedPlacetteId }?.name
                            var placetteMenuExpanded by remember { mutableStateOf(false) }
                            Box {
                                ListItem(
                                    headlineContent = { Text(stringResource(R.string.settings_export_placette)) },
                                    supportingContent = { Text(selectedPlacetteName ?: stringResource(R.string.settings_export_choose_placette)) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { placetteMenuExpanded = true }
                                )
                                DropdownMenu(
                                    expanded = placetteMenuExpanded,
                                    onDismissRequest = { placetteMenuExpanded = false }
                                ) {
                                    placettes.forEach { pl ->
                                        DropdownMenuItem(
                                            text = { Text(pl.name ?: pl.id.take(8)) },
                                            onClick = {
                                                selectedPlacetteId = pl.id
                                                placetteMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
                            if (uri != null) {
                                scope.launch {
                                    val tiges = when (exportScope) {
                                        "PROJECT" -> tigeRepository.getAllTiges().first()
                                        "PARCELLE" -> {
                                            val pid = selectedParcelleId
                                            if (pid == null) {
                                                snackbarHostState.showSnackbar(context.getString(R.string.settings_select_parcelle_for_export))
                                                return@launch
                                            }
                                            tigeRepository.getTigesByParcelle(pid).first()
                                        }
                                        "PLACETTE" -> {
                                            val plid = selectedPlacetteId
                                            if (plid == null) {
                                                snackbarHostState.showSnackbar(context.getString(R.string.settings_select_placette_for_export))
                                                return@launch
                                            }
                                            tigeRepository.getTigesByPlacette(plid).first()
                                        }
                                        else -> tigeRepository.getAllTiges().first()
                                    }
                                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { w ->
                                        w.write("id;parcelle_id;placette_id;essence;diam_cm;hauteur_m;V_m3;lat;lon;alt;altitude_m;precision_m;timestamp;numero;categorie;qualite;produit;f_coef;value_eur;note;defauts;photo_uri;gps_wkt\n")
                                        for (t in tiges) {
                                            val v = forestryCalculator.volumeForTige(t)
                                            val (lon,lat,alt) = parseWktPointZ(t.gpsWkt)
                                            val line = listOf(
                                                csvCell(t.id),
                                                csvCell(t.parcelleId),
                                                csvCell(t.placetteId),
                                                csvCell(t.essenceCode),
                                                String.format(Locale.US, "%.2f", t.diamCm),
                                                t.hauteurM?.let { String.format(Locale.US, "%.2f", it) } ?: "",
                                                v?.let { String.format(Locale.US, "%.4f", it) } ?: "",
                                                lat?.let { String.format(Locale.US, "%.7f", it) } ?: "",
                                                lon?.let { String.format(Locale.US, "%.7f", it) } ?: "",
                                                alt?.let { String.format(Locale.US, "%.2f", it) } ?: "",
                                                t.altitudeM?.let { String.format(Locale.US, "%.2f", it) } ?: "",
                                                t.precisionM?.let { String.format(Locale.US, "%.2f", it) } ?: "",
                                                t.timestamp.toString(),
                                                t.numero?.toString() ?: "",
                                                csvCell(t.categorie),
                                                t.qualite?.toString() ?: "",
                                                csvCell(t.produit),
                                                t.fCoef?.let { String.format(Locale.US, "%.3f", it) } ?: "",
                                                t.valueEur?.let { String.format(Locale.US, "%.2f", it) } ?: "",
                                                csvCell(t.note),
                                                csvCell(t.defauts?.joinToString(",")),
                                                csvCell(t.photoUri),
                                                csvCell(t.gpsWkt)
                                            ).joinToString(";")
                                            w.write(line + "\n")
                                        }
                                    }
                                    snackbarHostState.showSnackbar(context.getString(R.string.settings_export_csv_tiges_done))
                                }
                            }
                        }
                        Button(onClick = { exportCsv.launch("tiges.csv") }) { Text(stringResource(R.string.settings_export_csv_tiges)) }

                        val exportGeo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/geo+json")) { uri ->
                            if (uri != null) {
                                scope.launch {
                                    val tigesAll = when (exportScope) {
                                        "PROJECT" -> tigeRepository.getAllTiges().first()
                                        "PARCELLE" -> {
                                            val pid = selectedParcelleId
                                            if (pid == null) {
                                                snackbarHostState.showSnackbar(context.getString(R.string.settings_select_parcelle_for_export))
                                                return@launch
                                            }
                                            tigeRepository.getTigesByParcelle(pid).first()
                                        }
                                        "PLACETTE" -> {
                                            val plid = selectedPlacetteId
                                            if (plid == null) {
                                                snackbarHostState.showSnackbar(context.getString(R.string.settings_select_placette_for_export))
                                                return@launch
                                            }
                                            tigeRepository.getTigesByPlacette(plid).first()
                                        }
                                        else -> tigeRepository.getAllTiges().first()
                                    }
                                    val essencesAll = essenceRepository?.getAllEssences()?.first() ?: emptyList()
                                    val (geojson, count) = QgisExportHelper.buildGeoJson(
                                        tiges = tigesAll,
                                        essences = essencesAll
                                    )
                                    if (count == 0) {
                                        snackbarHostState.showSnackbar(context.getString(R.string.export_qgis_no_gps_stems))
                                        return@launch
                                    }
                                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(geojson) }
                                    snackbarHostState.showSnackbar(context.getString(R.string.export_qgis_geojson_done, count))
                                }
                            }
                        }
                        Button(onClick = { exportGeo.launch("tiges.geojson") }) { Text(stringResource(R.string.settings_export_geojson)) }

                        val exportGpx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
                            if (uri != null) {
                                scope.launch {
                                    val tigesAll = when (exportScope) {
                                        "PROJECT" -> tigeRepository.getAllTiges().first()
                                        "PARCELLE" -> {
                                            val pid = selectedParcelleId
                                            if (pid == null) {
                                                snackbarHostState.showSnackbar(context.getString(R.string.settings_select_parcelle_for_export))
                                                return@launch
                                            }
                                            tigeRepository.getTigesByParcelle(pid).first()
                                        }
                                        "PLACETTE" -> {
                                            val plid = selectedPlacetteId
                                            if (plid == null) {
                                                snackbarHostState.showSnackbar(context.getString(R.string.settings_select_placette_for_export))
                                                return@launch
                                            }
                                            tigeRepository.getTigesByPlacette(plid).first()
                                        }
                                        else -> tigeRepository.getAllTiges().first()
                                    }
                                    val sb = StringBuilder()
                                    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\" creator=\"GéoSylva\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
                                    tigesAll.forEach { t ->
                                        val (lon,lat,alt) = parseWktPointZ(t.gpsWkt)
                                        if (lat != null && lon != null) {
                                            val diamClass = t.diamCm.toInt()
                                            val name = xmlEscape("${t.essenceCode}-${diamClass}cm-${t.id.take(6)}")
                                            val cmt = xmlEscape(
                                                buildString {
                                                    append("id=${t.id};")
                                                    append("parcelle_id=${t.parcelleId};")
                                                    append("placette_id=${t.placetteId ?: ""};")
                                                    append("essence=${t.essenceCode};")
                                                    append("diam_cm=${t.diamCm};")
                                                    append("diam_class_cm=${diamClass};")
                                                    append("hauteur_m=${t.hauteurM ?: ""};")
                                                    append("precision_m=${t.precisionM ?: ""};")
                                                    append("categorie=${t.categorie ?: ""};")
                                                    append("qualite=${t.qualite ?: ""};")
                                                    append("numero=${t.numero ?: ""};")
                                                    append("note=${t.note ?: ""};")
                                                    append("produit=${t.produit ?: ""};")
                                                    append("f_coef=${t.fCoef ?: ""};")
                                                    append("value_eur=${t.valueEur ?: ""};")
                                                    append("defauts=${t.defauts?.joinToString(",") ?: ""};")
                                                    append("photo_uri=${t.photoUri ?: ""};")
                                                    append("timestamp=${t.timestamp}")
                                                }.trimEnd(';')
                                            )
                                            sb.append("<wpt lat=\"${lat}\" lon=\"${lon}\">")
                                            sb.append("<name>${name}</name>")
                                            if (alt != null) sb.append("<ele>${alt}</ele>")
                                            if (cmt.isNotBlank()) sb.append("<cmt>${cmt}</cmt>")
                                            sb.append("</wpt>\n")
                                        }
                                    }
                                    sb.append("</gpx>")
                                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(sb.toString()) }
                                    snackbarHostState.showSnackbar(context.getString(R.string.settings_export_gpx_done))
                                }
                            }
                        }
                        Button(onClick = { exportGpx.launch("tiges.gpx") }) { Text(stringResource(R.string.settings_export_gpx)) }

                        val exportExcelForestry = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
                            if (uri != null) {
                                scope.launch {
                                    val tigesAll = when (exportScope) {
                                        "PROJECT" -> tigeRepository.getAllTiges().first()
                                        "PARCELLE" -> {
                                            val pid = selectedParcelleId
                                            if (pid == null) {
                                                snackbarHostState.showSnackbar(context.getString(R.string.settings_select_parcelle_for_export))
                                                return@launch
                                            }
                                            tigeRepository.getTigesByParcelle(pid).first()
                                        }
                                        "PLACETTE" -> {
                                            val plid = selectedPlacetteId
                                            if (plid == null) {
                                                snackbarHostState.showSnackbar(context.getString(R.string.settings_select_placette_for_export))
                                                return@launch
                                            }
                                            tigeRepository.getTigesByPlacette(plid).first()
                                        }
                                        else -> tigeRepository.getAllTiges().first()
                                    }

                                    if (tigesAll.isEmpty()) {
                                        snackbarHostState.showSnackbar(context.getString(R.string.settings_no_tige_for_scope))
                                        return@launch
                                    }

                                    val classes = forestryCalculator.diameterClasses()
                                    val parcellesAll = parcelleRepository?.getAllParcelles()?.first() ?: emptyList()
                                    val parcelleById = parcellesAll.associateBy { it.id }
                                    val placetteIds = tigesAll.mapNotNull { it.placetteId }.distinct()
                                    val placettesAll = if (placetteRepository != null) {
                                        placetteIds.mapNotNull { id -> placetteRepository.getPlacetteById(id).first() }
                                    } else emptyList()
                                    val placetteById = placettesAll.associateBy { it.id }
                                    val essencesAll = essenceRepository?.getAllEssences()?.first() ?: emptyList()
                                    val essenceByCode = essencesAll.associateBy { it.code }

                                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                                        XSSFWorkbook().use { workbook ->
                                            val sheet = workbook.createSheet("Forestry")
                                            val header = sheet.createRow(0)
                                            val headers = listOf(
                                                "ParcelleID",
                                                "Parcelle",
                                                "PlacetteID",
                                                "Placette",
                                                "EssenceCode",
                                                "Essence",
                                                "DiamClass_cm",
                                                "N_tiges",
                                                "H_mean_m",
                                                "V_sum_m3",
                                                "Value_sum_EUR",
                                                "N_total",
                                                "V_total_m3",
                                                "Dm_weighted_cm",
                                                "H_mean_total_m"
                                            )
                                            headers.forEachIndexed { idx, title ->
                                                header.createCell(idx).setCellValue(title)
                                            }

                                            var rowIndex = 1
                                            val grouped = tigesAll.groupBy { Triple(it.parcelleId, it.placetteId, it.essenceCode) }
                                            for ((key, groupTiges) in grouped) {
                                                val (parcelleId, placetteId, essenceCode) = key
                                                val (perClass, totals) = forestryCalculator.synthesisForEssence(
                                                    essenceCode = essenceCode,
                                                    classes = classes,
                                                    tiges = groupTiges
                                                )
                                                val parcelleName = parcelleById[parcelleId]?.name ?: ""
                                                val placetteName = placetteId?.let { id -> placetteById[id]?.name ?: "" } ?: ""
                                                val essenceName = essenceByCode[essenceCode]?.name ?: ""

                                                perClass.filter { it.count > 0 }.forEach { cs ->
                                                    val row = sheet.createRow(rowIndex++)
                                                    row.createCell(0).setCellValue(parcelleId)
                                                    row.createCell(1).setCellValue(parcelleName)
                                                    row.createCell(2).setCellValue(placetteId ?: "")
                                                    row.createCell(3).setCellValue(placetteName)
                                                    row.createCell(4).setCellValue(essenceCode)
                                                    row.createCell(5).setCellValue(essenceName)
                                                    row.createCell(6).setCellValue(cs.diamClass.toDouble())
                                                    row.createCell(7).setCellValue(cs.count.toDouble())
                                                    cs.hMean?.let { row.createCell(8).setCellValue(it) }
                                                    cs.vSum?.let { row.createCell(9).setCellValue(it) }
                                                    cs.valueSumEur?.let { row.createCell(10).setCellValue(it) }
                                                    row.createCell(11).setCellValue(totals.nTotal.toDouble())
                                                    totals.vTotal?.let { row.createCell(12).setCellValue(it) }
                                                    totals.dmWeighted?.let { row.createCell(13).setCellValue(it) }
                                                    totals.hMean?.let { row.createCell(14).setCellValue(it) }
                                                }
                                            }

                                            headers.indices.forEach { sheet.autoSizeColumn(it) }
                                            workbook.write(outputStream)
                                        }
                                    }

                                    snackbarHostState.showSnackbar(context.getString(R.string.settings_export_excel_forestry_done))
                                }
                            }
                        }
                        Button(onClick = { exportExcelForestry.launch("forestry.xlsx") }) { Text(stringResource(R.string.settings_export_excel_forestry)) }
                    }

                    val exportProfile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                        if (uri != null) {
                            scope.launch {
                                val params = parameterRepository.getAllParameters().first()
                                val essences = essenceRepository?.getAllEssences()?.first() ?: emptyList()
                                val paramsJson = buildString {
                                    append('[')
                                    params.forEachIndexed { i, p ->
                                        if (i > 0) append(',')
                                        append('{')
                                        append("\"key\":"); append(Json.encodeToString(p.key)); append(',')
                                        append("\"valueJson\":"); append(Json.encodeToString(p.valueJson))
                                        append('}')
                                    }
                                    append(']')
                                }
                                val essencesJson = buildString {
                                    append('[')
                                    essences.forEachIndexed { i, e ->
                                        if (i > 0) append(',')
                                        append('{')
                                        append("\"code\":"); append(Json.encodeToString(e.code)); append(',')
                                        append("\"name\":"); append(Json.encodeToString(e.name)); append(',')
                                        append("\"categorie\":"); append(e.categorie?.let { Json.encodeToString(it) } ?: "null"); append(',')
                                        append("\"densiteBoite\":"); append(e.densiteBoite?.toString() ?: "null")
                                        append('}')
                                    }
                                    append(']')
                                }
                                val json = "{" + "\"parameters\":" + paramsJson + ",\"essences\":" + essencesJson + "}"
                                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                                snackbarHostState.showSnackbar(context.getString(R.string.settings_export_profile_json_done))
                            }
                        }
                    }
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { exportProfile.launch("profil.json") }) { Text(stringResource(R.string.settings_export_profile_json)) }
                    }
                }
            }

            // Interaction Section
            SettingsSection(title = stringResource(R.string.settings_section_interaction)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.animations)) },
                    supportingContent = { Text(stringResource(R.string.settings_animations_desc)) },
                    leadingContent = {
                        Icon(Icons.Default.Settings, contentDescription = null)
                    },
                    trailingContent = {
                        Switch(
                            checked = animationsEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    preferencesManager.setAnimationsEnabled(enabled)
                                }
                            }
                        )
                    }
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.sound)) },
                    supportingContent = { Text(stringResource(R.string.settings_sound_desc)) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = soundEnabled,
                            onCheckedChange = { enabled -> scope.launch { preferencesManager.setSoundEnabled(enabled) } }
                        )
                    }
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.haptic_feedback)) },
                    supportingContent = { Text(stringResource(R.string.settings_haptic_desc)) },
                    leadingContent = {
                        Icon(Icons.Default.Vibration, contentDescription = null)
                    },
                    trailingContent = {
                        Switch(
                            checked = hapticEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    preferencesManager.setHapticEnabled(enabled)
                                }
                            }
                        )
                    }
                )

                // Haptic intensity slider (1..3)
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.settings_haptic_intensity_label, hapticLevel))
                    Slider(
                        value = hapticLevel.toFloat(),
                        onValueChange = { v -> scope.launch { preferencesManager.setHapticIntensity(v.toInt().coerceIn(1,3)) } },
                        valueRange = 1f..3f,
                        steps = 1
                    )
                }
            }

            HorizontalDivider()

            // Data Section
            SettingsSection(title = stringResource(R.string.settings_section_data)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_csv_separator)) },
                    supportingContent = { Text(stringResource(R.string.settings_csv_current, csvSeparator)) },
                    leadingContent = {
                        Icon(Icons.Default.Settings, contentDescription = null)
                    },
                    modifier = Modifier.clickable { showCsvDialog = true }
                )
            }

            HorizontalDivider()

            // Privacy Section
            SettingsSection(title = stringResource(R.string.settings_section_privacy)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.no_ads)) },
                    supportingContent = { Text(stringResource(R.string.settings_no_ads_desc)) },
                    leadingContent = {
                        Icon(Icons.Default.Block, contentDescription = null)
                    }
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.no_tracking)) },
                    supportingContent = { Text(stringResource(R.string.settings_no_tracking_desc)) },
                    leadingContent = {
                        Icon(Icons.Default.PrivacyTip, contentDescription = null)
                    }
                )

                val crashLogsEnabled by preferencesManager.crashLogsEnabled.collectAsState(initial = false)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_crash_logs_title)) },
                    supportingContent = { Text(stringResource(R.string.settings_crash_logs_desc)) },
                    leadingContent = { Icon(Icons.Default.BugReport, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = crashLogsEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    preferencesManager.setCrashLogsEnabled(enabled)
                                    CrashLogger.enabled = enabled
                                }
                            }
                        )
                    }
                )

                var showCrashLog by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_view_latest_crash_log)) },
                    supportingContent = { Text(stringResource(R.string.settings_view_latest_crash_log_desc)) },
                    leadingContent = { Icon(Icons.Default.Description, contentDescription = null) },
                    modifier = Modifier.clickable { showCrashLog = true }
                )

                if (showCrashLog) {
                    val text = CrashLogger.latestLog(context) ?: stringResource(R.string.settings_no_crash_logs_found)
                    AppMiniDialog(
                        onDismissRequest = { showCrashLog = false },
                        animationsEnabled = animationsEnabled,
                        icon = Icons.Default.Description,
                        title = stringResource(R.string.settings_latest_crash_log_title),
                        confirmText = stringResource(R.string.close),
                        dismissText = stringResource(R.string.clear_logs),
                        onConfirm = { showCrashLog = false },
                        onDismiss = {
                            CrashLogger.clearLogs(context)
                            showCrashLog = false
                        }
                    ) {
                        Text(text)
                    }
                }

                // Export crash logs
                val exportLatestLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
                    if (uri != null) {
                        val ok = CrashLogger.exportLatest(context, uri)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (ok) context.getString(R.string.settings_exported_latest_log)
                                else context.getString(R.string.settings_no_log_to_export)
                            )
                        }
                    }
                }
                val exportAllLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
                    if (uri != null) {
                        val ok = CrashLogger.exportAllZip(context, uri)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (ok) context.getString(R.string.settings_exported_logs_zip)
                                else context.getString(R.string.settings_no_logs_to_export)
                            )
                        }
                    }
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_export_latest_crash_log)) },
                    supportingContent = { Text(stringResource(R.string.settings_export_latest_crash_log_desc)) },
                    leadingContent = { Icon(Icons.Default.Upload, contentDescription = null) },
                    modifier = Modifier.clickable {
                        val ts = java.text.SimpleDateFormat("yyyyMMdd-HHmm").format(java.util.Date())
                        exportLatestLauncher.launch("crash-latest-${ts}.txt")
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_export_all_crash_logs)) },
                    supportingContent = { Text(stringResource(R.string.settings_export_all_crash_logs_desc)) },
                    leadingContent = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                    modifier = Modifier.clickable {
                        val ts = java.text.SimpleDateFormat("yyyyMMdd-HHmm").format(java.util.Date())
                        exportAllLauncher.launch("crash-logs-${ts}.zip")
                    }
                )
            }

            HorizontalDivider()

            // Backup Section
            SettingsSection(title = stringResource(R.string.settings_section_backups)) {
                val autoBackupEnabled by preferencesManager.autoBackupEnabled.collectAsState(initial = false)
                val backupDays by preferencesManager.backupFrequencyDays.collectAsState(initial = 7)

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_automatic_backups)) },
                    supportingContent = { Text(stringResource(R.string.settings_automatic_backups_desc)) },
                    leadingContent = { Icon(Icons.Default.Save, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = autoBackupEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    preferencesManager.setAutoBackupEnabled(enabled)
                                    val wm = WorkManager.getInstance(context)
                                    if (enabled) {
                                        val constraints = Constraints.Builder()
                                            .setRequiresBatteryNotLow(true)
                                            .build()
                                        val req = PeriodicWorkRequestBuilder<BackupWorker>(backupDays.toLong().coerceAtLeast(1), TimeUnit.DAYS)
                                            .setConstraints(constraints)
                                            .build()
                                        wm.enqueueUniquePeriodicWork("auto-backup", ExistingPeriodicWorkPolicy.UPDATE, req)
                                    } else {
                                        wm.cancelUniqueWork("auto-backup")
                                    }
                                }
                            }
                        )
                    }
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_backup_frequency)) },
                    supportingContent = { Text(stringResource(R.string.settings_backup_frequency_desc, backupDays)) },
                    leadingContent = { Icon(Icons.Default.Schedule, contentDescription = null) },
                    modifier = Modifier.clickable {
                        // Simple cycle through 1, 7, 30
                        val next = when (backupDays) { 1 -> 7; 7 -> 30; else -> 1 }
                        scope.launch {
                            preferencesManager.setBackupFrequencyDays(next)
                            val wm = WorkManager.getInstance(context)
                            val constraints = Constraints.Builder()
                                .setRequiresBatteryNotLow(true)
                                .build()
                            val req = PeriodicWorkRequestBuilder<BackupWorker>(next.toLong().coerceAtLeast(1), TimeUnit.DAYS)
                                .setConstraints(constraints)
                                .build()
                            wm.enqueueUniquePeriodicWork("auto-backup", ExistingPeriodicWorkPolicy.UPDATE, req)
                        }
                    }
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_manual_backup)) },
                    supportingContent = { Text(stringResource(R.string.settings_manual_backup_desc)) },
                    leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
                    modifier = Modifier.clickable {
                        val ts = java.text.SimpleDateFormat("yyyyMMdd-HHmm").format(java.util.Date())
                        backupLauncher.launch("ForestryBackup-$ts.zip")
                    }
                )
            }

            // About Section
            SettingsSection(title = stringResource(R.string.settings_section_about)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.version)) },
                    supportingContent = { Text(versionDisplay) },
                    leadingContent = {
                        Icon(Icons.Default.Info, contentDescription = null)
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showCsvDialog) {
        AppMiniDialog(
            onDismissRequest = { showCsvDialog = false },
            animationsEnabled = animationsEnabled,
            icon = Icons.Default.Settings,
            title = stringResource(R.string.settings_csv_separator_dialog_title),
            confirmText = stringResource(R.string.close),
            onConfirm = { showCsvDialog = false }
        ) {
            Column {
                @Composable
                fun item(label: String, value: String) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showCsvDialog = false
                                scope.launch { preferencesManager.setCsvSeparator(value) }
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        RadioButton(selected = csvSeparator == value, onClick = {
                            showCsvDialog = false
                            scope.launch { preferencesManager.setCsvSeparator(value) }
                        })
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
                item(stringResource(R.string.settings_csv_option_comma), ",")
                item(stringResource(R.string.settings_csv_option_semicolon), ";")
                item(stringResource(R.string.settings_csv_option_tab), "\t")
            }
        }
    }

}

private fun parseWktPointZ(wkt: String?): Triple<Double?, Double?, Double?> =
    com.forestry.counter.domain.location.WktUtils.parsePointZ(wkt)

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
    }
}

@Suppress("unused")
@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    content()
}
