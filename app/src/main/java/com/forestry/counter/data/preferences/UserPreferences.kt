package com.forestry.counter.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.core.doublePreferencesKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesManager(private val context: Context) {

    private val dataStore = context.dataStore
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        // Theme preferences
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val CONTAINER_ACCENT_COLOR = stringPreferencesKey("container_accent_color")
        val CARD_ACCENT_COLOR = stringPreferencesKey("card_accent_color")
        val BACKGROUND_TYPE = stringPreferencesKey("background_type")
        val BACKGROUND_IMAGE_ENABLED = booleanPreferencesKey("background_image_enabled")
        val BACKGROUND_IMAGE_URI = stringPreferencesKey("background_image_uri")
        val BACKGROUND_COLOR = stringPreferencesKey("background_color")
        val BACKGROUND_GRADIENT_START = stringPreferencesKey("background_gradient_start")
        val BACKGROUND_GRADIENT_END = stringPreferencesKey("background_gradient_end")

        // Display preferences
        val FONT_SIZE = stringPreferencesKey("font_size")
        val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val HAPTIC_INTENSITY = intPreferencesKey("haptic_intensity")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
        val GLASS_BLUR_ENABLED = booleanPreferencesKey("glass_blur_enabled")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val LANGUAGE_SUGGESTION_ANSWERED = booleanPreferencesKey("language_suggestion_answered")
        val TILT_DEG = floatPreferencesKey("tilt_deg")
        val PRESS_SCALE = floatPreferencesKey("press_scale")
        val HALO_ALPHA = floatPreferencesKey("halo_alpha")
        val HALO_WIDTH_DP = intPreferencesKey("halo_width_dp")
        val BLUR_RADIUS = floatPreferencesKey("blur_radius")
        val BLUR_OVERLAY_ALPHA = floatPreferencesKey("blur_overlay_alpha")
        val ANIM_DURATION_SHORT = intPreferencesKey("anim_duration_short")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val GPS_CAPTURE_MODE = stringPreferencesKey("gps_capture_mode")
        val GPS_MAX_ACCEPTABLE_PRECISION_M = floatPreferencesKey("gps_max_acceptable_precision_m")
        val MAP_LAST_LAYER_KEY = stringPreferencesKey("map_last_layer_key")
        val MAP_SHOW_LEGEND = booleanPreferencesKey("map_show_legend")
        val MAP_ONLY_RELIABLE_GPS = booleanPreferencesKey("map_only_reliable_gps")
        val MAP_RELIABLE_GPS_THRESHOLD_M = floatPreferencesKey("map_reliable_gps_threshold_m")

        // Data preferences
        val CSV_SEPARATOR = stringPreferencesKey("csv_separator")
        val CSV_ENCODING = stringPreferencesKey("csv_encoding")
        val DEFAULT_EXPORT_FORMAT = stringPreferencesKey("default_export_format")
        val DEFAULT_IMPORT_MODE = stringPreferencesKey("default_import_mode")

        // Privacy preferences
        val CRASH_LOGS_ENABLED = booleanPreferencesKey("crash_logs_enabled")

        // Height reminder preferences
        val HEIGHT_PROMPT_SNOOZE_UNTIL_MS = longPreferencesKey("height_prompt_snooze_until_ms")

        // Backup preferences
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val BACKUP_FREQUENCY_DAYS = intPreferencesKey("backup_frequency_days")
        val BACKUP_PATH = stringPreferencesKey("backup_path")

        // Tree height measurement
        val PHONE_HEIGHT_M = floatPreferencesKey("phone_height_m")
        val SKIP_WAIST_WARNING = booleanPreferencesKey("skip_waist_warning")

        // Onboarding
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val IBP_ONBOARDING_SEEN = booleanPreferencesKey("ibp_onboarding_seen")
        val STATION_TUTORIAL_COMPLETED = booleanPreferencesKey("station_tutorial_completed")

        // Métier de l'utilisateur — choisi juste après connexion, avant
        // l'onboarding. Stocké localement (offline-first) ; PENDING_SYNC
        // marque une valeur pas encore remontée au serveur GSIE, pour le
        // jour où l'API d'identité exposera ce champ.
        val USER_PROFESSION = stringPreferencesKey("user_profession")
        val USER_PROFESSION_CUSTOM_TEXT = stringPreferencesKey("user_profession_custom_text")
        val PROFESSION_SELECTION_COMPLETED = booleanPreferencesKey("profession_selection_completed")
        val PROFESSION_PENDING_SYNC = booleanPreferencesKey("profession_pending_sync")

        // Packs départementaux sélectionnés lors du wizard d'onboarding
        val SELECTED_DEPARTMENTS = stringSetPreferencesKey("selected_departments")

        // Visite guidée (coachmarks) des 5 onglets principaux — se déclenche
        // une fois, juste après la réponse aux autorisations GPS/caméra/
        // galerie qui suivent l'onboarding.
        val COACH_MARK_TOUR_PENDING = booleanPreferencesKey("coach_mark_tour_pending")
        val COACH_MARK_TOUR_COMPLETED = booleanPreferencesKey("coach_mark_tour_completed")

        // Options développeur locales — ne confèrent aucun rôle GSIE
        val DEVELOPER_MODE_ENABLED = booleanPreferencesKey("developer_mode_enabled")
    }

    // IBP onboarding
    val ibpOnboardingSeen: Flow<Boolean> = dataStore.data.map { prefs -> prefs[IBP_ONBOARDING_SEEN] ?: false }
    suspend fun setIbpOnboardingSeen() { dataStore.edit { it[IBP_ONBOARDING_SEEN] = true } }

    // Station tutorial
    val stationTutorialCompleted: Flow<Boolean> = dataStore.data.map { prefs -> prefs[STATION_TUTORIAL_COMPLETED] ?: false }
    suspend fun setStationTutorialCompleted(completed: Boolean) { dataStore.edit { it[STATION_TUTORIAL_COMPLETED] = completed } }

    // Theme preferences
    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.valueOf(prefs[THEME_MODE] ?: ThemeMode.SYSTEM.name)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[THEME_MODE] = mode.name
        }
    }

    val accentColor: Flow<String> = dataStore.data.map { prefs ->
        prefs[ACCENT_COLOR] ?: "#2D5F3F" // Vert forêt profond — couleur de marque GeoSylva
    }

    suspend fun setAccentColor(color: String) {
        dataStore.edit { prefs ->
            prefs[ACCENT_COLOR] = color
        }
    }

    // Couleur des blocs mis en avant (ex. tuiles de statistiques) — distincte
    // de l'accent des boutons/icônes (ACCENT_COLOR). `null` = dérivée du
    // thème par défaut (PrimaryVariant / PrimaryVariantDark, Color.kt).
    val containerAccentColor: Flow<String?> = dataStore.data.map { prefs ->
        prefs[CONTAINER_ACCENT_COLOR]
    }

    suspend fun setContainerAccentColor(color: String?) {
        dataStore.edit { prefs ->
            if (color == null) prefs.remove(CONTAINER_ACCENT_COLOR) else prefs[CONTAINER_ACCENT_COLOR] = color
        }
    }

    // Couleur des cartes et menus (surfaceContainerHigh) — le "verre foncé"
    // signalé en thème sombre sur les lignes Thème/Langue/Taille de police
    // de la page Apparence, entre autres. `null` = dérivée du thème.
    val cardAccentColor: Flow<String?> = dataStore.data.map { prefs ->
        prefs[CARD_ACCENT_COLOR]
    }

    suspend fun setCardAccentColor(color: String?) {
        dataStore.edit { prefs ->
            if (color == null) prefs.remove(CARD_ACCENT_COLOR) else prefs[CARD_ACCENT_COLOR] = color
        }
    }

    val backgroundType: Flow<BackgroundType> = dataStore.data.map { prefs ->
        BackgroundType.valueOf(prefs[BACKGROUND_TYPE] ?: BackgroundType.SOLID.name)
    }

    suspend fun setBackgroundType(type: BackgroundType) {
        dataStore.edit { prefs ->
            prefs[BACKGROUND_TYPE] = type.name
        }
    }

    val backgroundImageEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[BACKGROUND_IMAGE_ENABLED] ?: true
    }

    suspend fun setBackgroundImageEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[BACKGROUND_IMAGE_ENABLED] = enabled
        }
    }

    val backgroundImageUri: Flow<String?> = dataStore.data.map { prefs ->
        prefs[BACKGROUND_IMAGE_URI]
    }

    suspend fun setBackgroundImageUri(uri: String?) {
        dataStore.edit { prefs ->
            if (uri == null) prefs.remove(BACKGROUND_IMAGE_URI) else prefs[BACKGROUND_IMAGE_URI] = uri
        }
    }

    val backgroundColor: Flow<String> = dataStore.data.map { prefs ->
        prefs[BACKGROUND_COLOR] ?: "#FFFFFF"
    }

    suspend fun setBackgroundColor(color: String) {
        dataStore.edit { prefs ->
            prefs[BACKGROUND_COLOR] = color
        }
    }

    // Display preferences
    val fontSize: Flow<FontSize> = dataStore.data.map { prefs ->
        FontSize.valueOf(prefs[FONT_SIZE] ?: FontSize.MEDIUM.name)
    }

    suspend fun setFontSize(size: FontSize) {
        dataStore.edit { prefs ->
            prefs[FONT_SIZE] = size.name
        }
    }

    val animationsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[ANIMATIONS_ENABLED] ?: true
    }

    suspend fun setAnimationsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[ANIMATIONS_ENABLED] = enabled
        }
    }

    val hapticEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[HAPTIC_ENABLED] ?: true
    }

    suspend fun setHapticEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[HAPTIC_ENABLED] = enabled
        }
    }

    val dynamicColorEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        // Désactivé par défaut : Material You dériverait la palette du fond
        // d'écran de l'utilisateur et effacerait l'identité GeoSylva.
        prefs[DYNAMIC_COLOR_ENABLED] ?: false
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[DYNAMIC_COLOR_ENABLED] = enabled
        }
    }

    // App language ("system", "fr", "en", ...)
    val appLanguage: Flow<String> = dataStore.data.map { prefs ->
        // GeoSylva est une application française : le français s'applique par
        // défaut, quel que soit le réglage de l'appareil. La traduction
        // anglaise reste accessible via « Langue » dans les réglages, et le
        // choix de l'utilisateur prime toujours sur ce défaut.
        prefs[APP_LANGUAGE] ?: "fr"
    }

    suspend fun setAppLanguage(tag: String) {
        dataStore.edit { prefs ->
            prefs[APP_LANGUAGE] = tag
        }
    }

    /**
     * La suggestion de langue régionale (« votre téléphone est localisé en
     * France, passer en français ? ») ne doit apparaître qu'une fois — que
     * l'utilisateur accepte ou refuse. Ce drapeau est distinct de
     * [appLanguage] : refuser la suggestion ne change pas la langue, mais ne
     * doit plus jamais redéclencher la question.
     */
    val languageSuggestionAnswered: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[LANGUAGE_SUGGESTION_ANSWERED] ?: false
    }

    suspend fun setLanguageSuggestionAnswered(answered: Boolean) {
        dataStore.edit { prefs ->
            prefs[LANGUAGE_SUGGESTION_ANSWERED] = answered
        }
    }

    val glassBlurEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[GLASS_BLUR_ENABLED] ?: false
    }

    suspend fun setGlassBlurEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[GLASS_BLUR_ENABLED] = enabled
        }
    }

    val tiltDeg: Flow<Float> = dataStore.data.map { prefs ->
        prefs[TILT_DEG] ?: 2f
    }

    suspend fun setTiltDeg(value: Float) {
        dataStore.edit { prefs ->
            prefs[TILT_DEG] = value.coerceIn(0f, 8f)
        }
    }

    val pressScale: Flow<Float> = dataStore.data.map { prefs ->
        prefs[PRESS_SCALE] ?: 0.96f
    }

    suspend fun setPressScale(value: Float) {
        dataStore.edit { prefs ->
            prefs[PRESS_SCALE] = value.coerceIn(0.9f, 1.0f)
        }
    }

    val haloAlpha: Flow<Float> = dataStore.data.map { prefs ->
        prefs[HALO_ALPHA] ?: 0.35f
    }

    suspend fun setHaloAlpha(value: Float) {
        dataStore.edit { prefs ->
            prefs[HALO_ALPHA] = value.coerceIn(0f, 0.8f)
        }
    }

    val haloWidthDp: Flow<Int> = dataStore.data.map { prefs ->
        prefs[HALO_WIDTH_DP] ?: 2
    }

    suspend fun setHaloWidthDp(value: Int) {
        dataStore.edit { prefs ->
            prefs[HALO_WIDTH_DP] = value.coerceIn(0, 6)
        }
    }

    val blurRadius: Flow<Float> = dataStore.data.map { prefs ->
        prefs[BLUR_RADIUS] ?: 16f
    }

    suspend fun setBlurRadius(value: Float) {
        dataStore.edit { prefs ->
            prefs[BLUR_RADIUS] = value.coerceIn(0f, 30f)
        }
    }

    val blurOverlayAlpha: Flow<Float> = dataStore.data.map { prefs ->
        prefs[BLUR_OVERLAY_ALPHA] ?: 0.6f
    }

    suspend fun setBlurOverlayAlpha(value: Float) {
        dataStore.edit { prefs ->
            prefs[BLUR_OVERLAY_ALPHA] = value.coerceIn(0f, 0.85f)
        }
    }

    val animDurationShort: Flow<Int> = dataStore.data.map { prefs ->
        prefs[ANIM_DURATION_SHORT] ?: 120
    }

    suspend fun setAnimDurationShort(value: Int) {
        dataStore.edit { prefs ->
            prefs[ANIM_DURATION_SHORT] = value.coerceIn(60, 240)
        }
    }

    // Keep screen on
    val keepScreenOn: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEEP_SCREEN_ON] ?: false
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEEP_SCREEN_ON] = enabled
        }
    }

    val gpsCaptureMode: Flow<GpsCaptureMode> = dataStore.data.map { prefs ->
        runCatching {
            GpsCaptureMode.valueOf(prefs[GPS_CAPTURE_MODE] ?: GpsCaptureMode.STANDARD.name)
        }.getOrElse { GpsCaptureMode.STANDARD }
    }

    suspend fun setGpsCaptureMode(mode: GpsCaptureMode) {
        dataStore.edit { prefs ->
            prefs[GPS_CAPTURE_MODE] = mode.name
        }
    }

    val gpsMaxAcceptablePrecisionM: Flow<Float> = dataStore.data.map { prefs ->
        (prefs[GPS_MAX_ACCEPTABLE_PRECISION_M] ?: 15f).coerceIn(3f, 100f)
    }

    suspend fun setGpsMaxAcceptablePrecisionM(value: Float) {
        dataStore.edit { prefs ->
            prefs[GPS_MAX_ACCEPTABLE_PRECISION_M] = value.coerceIn(3f, 100f)
        }
    }

    val mapLastLayerKey: Flow<String> = dataStore.data.map { prefs ->
        // OSM Standard (OpenStreetMap, couverture mondiale, thème clair) —
        // couverture mondiale comme ESRI Satellite mais fond clair par défaut,
        // sans dépendance à une clé API. Le fond vert de repli (#EFF5EC) couvre
        // les zones sans tuile si le GPS est hors couverture au premier lancement.
        prefs[MAP_LAST_LAYER_KEY] ?: "OSM_STANDARD"
    }

    suspend fun setMapLastLayerKey(layerKey: String) {
        dataStore.edit { prefs ->
            prefs[MAP_LAST_LAYER_KEY] = layerKey
        }
    }

    val mapShowLegend: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[MAP_SHOW_LEGEND] ?: false
    }

    suspend fun setMapShowLegend(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[MAP_SHOW_LEGEND] = show
        }
    }

    val mapOnlyReliableGps: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[MAP_ONLY_RELIABLE_GPS] ?: false
    }

    suspend fun setMapOnlyReliableGps(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[MAP_ONLY_RELIABLE_GPS] = enabled
        }
    }

    val mapReliableGpsThresholdM: Flow<Float> = dataStore.data.map { prefs ->
        (prefs[MAP_RELIABLE_GPS_THRESHOLD_M] ?: 8f).coerceIn(3f, 100f)
    }

    suspend fun setMapReliableGpsThresholdM(value: Float) {
        dataStore.edit { prefs ->
            prefs[MAP_RELIABLE_GPS_THRESHOLD_M] = value.coerceIn(3f, 100f)
        }
    }

    val soundEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SOUND_ENABLED] ?: true
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[SOUND_ENABLED] = enabled
        }
    }

    val hapticIntensity: Flow<Int> = dataStore.data.map { prefs ->
        prefs[HAPTIC_INTENSITY] ?: 2
    }

    suspend fun setHapticIntensity(intensity: Int) {
        dataStore.edit { prefs ->
            prefs[HAPTIC_INTENSITY] = intensity.coerceIn(1, 3)
        }
    }

    private fun martelageSurfaceKey(scopeKey: String) = doublePreferencesKey("martelage_surface_$scopeKey")
    private fun martelageHoKey(scopeKey: String) = doublePreferencesKey("martelage_ho_$scopeKey")
    private fun martelageHeightsKey(scopeKey: String) = stringPreferencesKey("martelage_heights_$scopeKey")
    private fun martelageNhaAvantKey(scopeKey: String) = doublePreferencesKey("martelage_nha_avant_$scopeKey")
    private fun martelageGhaAvantKey(scopeKey: String) = doublePreferencesKey("martelage_gha_avant_$scopeKey")

    fun martelageSurfaceFlow(scopeKey: String): Flow<Double?> = dataStore.data.map { prefs ->
        prefs[martelageSurfaceKey(scopeKey)]
    }

    suspend fun setMartelageSurface(scopeKey: String, value: Double?) {
        dataStore.edit { prefs ->
            val key = martelageSurfaceKey(scopeKey)
            if (value == null) prefs.remove(key) else prefs[key] = value
        }
    }

    fun martelageHoFlow(scopeKey: String): Flow<Double?> = dataStore.data.map { prefs ->
        prefs[martelageHoKey(scopeKey)]
    }

    suspend fun setMartelageHo(scopeKey: String, value: Double?) {
        dataStore.edit { prefs ->
            val key = martelageHoKey(scopeKey)
            if (value == null) prefs.remove(key) else prefs[key] = value
        }
    }

    fun martelageNhaAvantFlow(scopeKey: String): Flow<Double?> = dataStore.data.map { prefs ->
        prefs[martelageNhaAvantKey(scopeKey)]
    }

    suspend fun setMartelageNhaAvant(scopeKey: String, value: Double?) {
        dataStore.edit { prefs ->
            val key = martelageNhaAvantKey(scopeKey)
            if (value == null) prefs.remove(key) else prefs[key] = value
        }
    }

    fun martelageGhaAvantFlow(scopeKey: String): Flow<Double?> = dataStore.data.map { prefs ->
        prefs[martelageGhaAvantKey(scopeKey)]
    }

    suspend fun setMartelageGhaAvant(scopeKey: String, value: Double?) {
        dataStore.edit { prefs ->
            val key = martelageGhaAvantKey(scopeKey)
            if (value == null) prefs.remove(key) else prefs[key] = value
        }
    }

    @Serializable
    data class MartelageHeightsSnapshot(
        val data: Map<String, Map<Int, Double>> = emptyMap()
    )

    fun martelageHeightsFlow(scopeKey: String): Flow<Map<String, Map<Int, Double>>> =
        dataStore.data.map { prefs ->
            val raw = prefs[martelageHeightsKey(scopeKey)] ?: return@map emptyMap()
            runCatching { json.decodeFromString<MartelageHeightsSnapshot>(raw).data }
                .getOrElse { emptyMap() }
        }

    suspend fun setMartelageHeights(scopeKey: String, heights: Map<String, Map<Int, Double>>) {
        dataStore.edit { prefs ->
            val key = martelageHeightsKey(scopeKey)
            if (heights.isEmpty()) {
                prefs.remove(key)
            } else {
                val snapshot = MartelageHeightsSnapshot(heights)
                prefs[key] = json.encodeToString(snapshot)
            }
        }
    }

    // ── Sessions de martelage ──────────────────────────────────────────────
    private fun martelageSessionsKey(placetteId: String) = stringPreferencesKey("martelage_sessions_$placetteId")

    @Serializable
    data class MartelageSessionRecord(
        val startMs: Long = 0L,
        val endMs: Long = 0L,
        val stemsAdded: Int = 0,
        val totalStemsAtEnd: Int = 0,
        val categoryBreakdown: Map<String, Int> = emptyMap(),
        val essenceCodes: List<String> = emptyList(),
    )

    @Serializable
    private data class MartelageSessionsSnapshot(val records: List<MartelageSessionRecord> = emptyList())

    fun martelageSessionsFlow(placetteId: String): Flow<List<MartelageSessionRecord>> =
        dataStore.data.map { prefs ->
            val raw = prefs[martelageSessionsKey(placetteId)] ?: return@map emptyList()
            runCatching { json.decodeFromString<MartelageSessionsSnapshot>(raw).records }.getOrElse { emptyList() }
        }

    suspend fun addMartelageSession(placetteId: String, record: MartelageSessionRecord) {
        dataStore.edit { prefs ->
            val key = martelageSessionsKey(placetteId)
            val existing = prefs[key]?.let {
                runCatching { json.decodeFromString<MartelageSessionsSnapshot>(it).records }.getOrElse { emptyList() }
            }.orEmpty()
            prefs[key] = json.encodeToString(MartelageSessionsSnapshot(existing + record))
        }
    }

    // Data preferences
    val csvSeparator: Flow<String> = dataStore.data.map { prefs ->
        prefs[CSV_SEPARATOR] ?: ","
    }

    suspend fun setCsvSeparator(separator: String) {
        dataStore.edit { prefs ->
            prefs[CSV_SEPARATOR] = separator
        }
    }

    val csvEncoding: Flow<String> = dataStore.data.map { prefs ->
        prefs[CSV_ENCODING] ?: "UTF-8"
    }

    suspend fun setCsvEncoding(encoding: String) {
        dataStore.edit { prefs ->
            prefs[CSV_ENCODING] = encoding
        }
    }

    // Backup preferences
    val autoBackupEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[AUTO_BACKUP_ENABLED] ?: false
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[AUTO_BACKUP_ENABLED] = enabled
        }
    }

    // Crash logs preference
    val crashLogsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[CRASH_LOGS_ENABLED] ?: false
    }

    suspend fun setCrashLogsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[CRASH_LOGS_ENABLED] = enabled
        }
    }

    fun groupCardHeightFlow(groupId: String): Flow<Int> = dataStore.data.map { prefs ->
        val key = intPreferencesKey("group_card_height_${groupId}")
        prefs[key] ?: 140
    }

    suspend fun setGroupCardHeight(groupId: String, heightDp: Int) {
        dataStore.edit { prefs ->
            val key = intPreferencesKey("group_card_height_${groupId}")
            prefs[key] = heightDp.coerceIn(120, 260)
        }
    }

    val backupFrequencyDays: Flow<Int> = dataStore.data.map { prefs ->
        prefs[BACKUP_FREQUENCY_DAYS] ?: 7
    }

    suspend fun setBackupFrequencyDays(days: Int) {
        dataStore.edit { prefs ->
            prefs[BACKUP_FREQUENCY_DAYS] = days
        }
    }

    val backupPath: Flow<String?> = dataStore.data.map { prefs ->
        prefs[BACKUP_PATH]
    }

    suspend fun setBackupPath(uri: String?) {
        dataStore.edit { prefs ->
            if (uri == null) prefs.remove(BACKUP_PATH) else prefs[BACKUP_PATH] = uri
        }
    }

    val heightPromptSnoozeUntilMs: Flow<Long> = dataStore.data.map { prefs ->
        prefs[HEIGHT_PROMPT_SNOOZE_UNTIL_MS] ?: 0L
    }

    suspend fun setHeightPromptSnoozeUntilMs(epochMs: Long) {
        dataStore.edit { prefs ->
            if (epochMs <= 0L) {
                prefs.remove(HEIGHT_PROMPT_SNOOZE_UNTIL_MS)
            } else {
                prefs[HEIGHT_PROMPT_SNOOZE_UNTIL_MS] = epochMs
            }
        }
    }

    suspend fun snoozeHeightPromptForHours(hours: Int) {
        val safeHours = hours.coerceAtLeast(1)
        val until = System.currentTimeMillis() + safeHours * 60L * 60L * 1000L
        setHeightPromptSnoozeUntilMs(until)
    }

    suspend fun clearAllPreferences() {
        dataStore.edit { it.clear() }
    }

    // Placette essence order persistence (CSV codes)
    fun essenceOrderFlow(placetteId: String): Flow<List<String>> = dataStore.data.map { prefs ->
        val key = stringPreferencesKey("placette_essence_order_${placetteId}")
        prefs[key]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    }

    suspend fun setEssenceOrder(placetteId: String, order: List<String>) {
        dataStore.edit { prefs ->
            val key = stringPreferencesKey("placette_essence_order_${placetteId}")
            if (order.isEmpty()) prefs.remove(key) else prefs[key] = order.joinToString(",")
        }
    }

    // Tree height measurement
    val phoneHeightM: Flow<Float> = dataStore.data.map { prefs ->
        (prefs[PHONE_HEIGHT_M] ?: 1.5f).coerceIn(0.3f, 2.5f)
    }

    suspend fun setPhoneHeightM(value: Float) {
        dataStore.edit { prefs ->
            prefs[PHONE_HEIGHT_M] = value.coerceIn(0.3f, 2.5f)
        }
    }

    val skipWaistWarning: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SKIP_WAIST_WARNING] ?: false
    }

    suspend fun setSkipWaistWarning(skip: Boolean) {
        dataStore.edit { prefs ->
            prefs[SKIP_WAIST_WARNING] = skip
        }
    }

    // Onboarding
    val onboardingCompleted: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[ONBOARDING_COMPLETED] ?: false
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { prefs ->
            prefs[ONBOARDING_COMPLETED] = completed
        }
    }

    /**
     * Termine l'onboarding et programme sa visite guidée dans une transaction
     * unique. Aucun état intermédiaire « onboarding terminé sans visite » ne
     * peut ainsi être observé si la composition ou le processus est interrompu.
     */
    suspend fun completeOnboardingAndScheduleCoachMarkTour() {
        dataStore.edit { prefs ->
            prefs[ONBOARDING_COMPLETED] = true
            prefs[COACH_MARK_TOUR_PENDING] = true
            prefs[COACH_MARK_TOUR_COMPLETED] = false
        }
    }

    // ── Métier de l'utilisateur ──────────────────────────────────────────
    // `userProfession` porte un code stable (ex. "forestier_onf") pour les
    // choix de la liste prédéfinie, ou "autre" quand seul le texte libre
    // est renseigné (`userProfessionCustomText`). `professionPendingSync`
    // reste à `true` tant qu'aucun canal de synchronisation serveur
    // n'existe côté GSIE — mis à `false` par ce futur appel, pas avant.

    val userProfession: Flow<String?> = dataStore.data.map { prefs ->
        prefs[USER_PROFESSION]
    }

    val userProfessionCustomText: Flow<String?> = dataStore.data.map { prefs ->
        prefs[USER_PROFESSION_CUSTOM_TEXT]
    }

    val professionSelectionCompleted: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PROFESSION_SELECTION_COMPLETED] ?: false
    }

    val professionPendingSync: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PROFESSION_PENDING_SYNC] ?: false
    }

    suspend fun setUserProfession(code: String, customText: String? = null) {
        dataStore.edit { prefs ->
            prefs[USER_PROFESSION] = code
            if (customText != null) {
                prefs[USER_PROFESSION_CUSTOM_TEXT] = customText
            } else {
                prefs.remove(USER_PROFESSION_CUSTOM_TEXT)
            }
            prefs[PROFESSION_SELECTION_COMPLETED] = true
            prefs[PROFESSION_PENDING_SYNC] = true
        }
    }

    suspend fun setProfessionSelectionCompleted(completed: Boolean) {
        dataStore.edit { prefs ->
            prefs[PROFESSION_SELECTION_COMPLETED] = completed
        }
    }

    // ── Packs départementaux ─────────────────────────────────────────────

    val selectedDepartments: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[SELECTED_DEPARTMENTS] ?: emptySet()
    }

    suspend fun setSelectedDepartments(codes: Set<String>) {
        dataStore.edit { prefs -> prefs[SELECTED_DEPARTMENTS] = codes }
    }

    // ── Visite guidée (coachmarks) ───────────────────────────────────────
    val coachMarkTourPending: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[COACH_MARK_TOUR_PENDING] ?: false
    }

    val coachMarkTourCompleted: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[COACH_MARK_TOUR_COMPLETED] ?: false
    }

    suspend fun setCoachMarkTourPending(pending: Boolean) {
        dataStore.edit { prefs -> prefs[COACH_MARK_TOUR_PENDING] = pending }
    }

    suspend fun setCoachMarkTourCompleted(completed: Boolean) {
        dataStore.edit { prefs ->
            prefs[COACH_MARK_TOUR_COMPLETED] = completed
            prefs[COACH_MARK_TOUR_PENDING] = false
        }
    }

    val developerModeEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[DEVELOPER_MODE_ENABLED] ?: false
    }

    suspend fun setDeveloperModeEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[DEVELOPER_MODE_ENABLED] = enabled
        }
    }
}

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

enum class BackgroundType {
    SOLID, GRADIENT, IMAGE
}

enum class FontSize(val scale: Float) {
    SMALL(0.85f),
    MEDIUM(1.0f),
    LARGE(1.15f)
}

enum class GpsCaptureMode {
    FAST,
    STANDARD,
    PRECISE
}
