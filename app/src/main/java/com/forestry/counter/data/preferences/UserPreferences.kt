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
        val RIPISYLVE_TUTORIAL_COMPLETED = booleanPreferencesKey("ripisylve_tutorial_completed")
    }

    // IBP onboarding
    val ibpOnboardingSeen: Flow<Boolean> = dataStore.data.map { prefs -> prefs[IBP_ONBOARDING_SEEN] ?: false }
    suspend fun setIbpOnboardingSeen() { dataStore.edit { it[IBP_ONBOARDING_SEEN] = true } }

    val stationTutorialCompleted: Flow<Boolean> = dataStore.data.map { prefs -> prefs[STATION_TUTORIAL_COMPLETED] ?: false }
    suspend fun setStationTutorialCompleted(completed: Boolean) { dataStore.edit { it[STATION_TUTORIAL_COMPLETED] = completed } }

    val ripisylveTutorialCompleted: Flow<Boolean> = dataStore.data.map { prefs -> prefs[RIPISYLVE_TUTORIAL_COMPLETED] ?: false }
    suspend fun setRipisylveTutorialCompleted(completed: Boolean) { dataStore.edit { it[RIPISYLVE_TUTORIAL_COMPLETED] = completed } }

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
        prefs[ACCENT_COLOR] ?: "#4CAF50" // Default green
    }

    suspend fun setAccentColor(color: String) {
        dataStore.edit { prefs ->
            prefs[ACCENT_COLOR] = color
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
        prefs[DYNAMIC_COLOR_ENABLED] ?: true
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[DYNAMIC_COLOR_ENABLED] = enabled
        }
    }

    // App language ("system", "fr", "en", ...)
    val appLanguage: Flow<String> = dataStore.data.map { prefs ->
        prefs[APP_LANGUAGE] ?: "system"
    }

    suspend fun setAppLanguage(tag: String) {
        dataStore.edit { prefs ->
            prefs[APP_LANGUAGE] = tag
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
        prefs[MAP_LAST_LAYER_KEY] ?: "PLAN_IGN"
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
    private fun martelageVhaAvantKey(scopeKey: String) = doublePreferencesKey("martelage_vha_avant_$scopeKey")
    private fun martelageTargetVolumeKey(scopeKey: String) = doublePreferencesKey("martelage_target_volume_$scopeKey")
    private fun martelageTargetRevenueKey(scopeKey: String) = doublePreferencesKey("martelage_target_revenue_$scopeKey")

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

    fun martelageVhaAvantFlow(scopeKey: String): Flow<Double?> = dataStore.data.map { prefs ->
        prefs[martelageVhaAvantKey(scopeKey)]
    }

    suspend fun setMartelageVhaAvant(scopeKey: String, value: Double?) {
        dataStore.edit { prefs ->
            val key = martelageVhaAvantKey(scopeKey)
            if (value == null) prefs.remove(key) else prefs[key] = value
        }
    }

    fun martelageTargetVolumeFlow(scopeKey: String): Flow<Double?> = dataStore.data.map { prefs ->
        prefs[martelageTargetVolumeKey(scopeKey)]
    }

    suspend fun setMartelageTargetVolume(scopeKey: String, value: Double?) {
        dataStore.edit { prefs ->
            val key = martelageTargetVolumeKey(scopeKey)
            if (value == null) prefs.remove(key) else prefs[key] = value
        }
    }

    fun martelageTargetRevenueFlow(scopeKey: String): Flow<Double?> = dataStore.data.map { prefs ->
        prefs[martelageTargetRevenueKey(scopeKey)]
    }

    suspend fun setMartelageTargetRevenue(scopeKey: String, value: Double?) {
        dataStore.edit { prefs ->
            val key = martelageTargetRevenueKey(scopeKey)
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
