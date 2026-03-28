package com.forestry.counter.domain.usecase.pack

import android.content.Context
import android.content.SharedPreferences
import com.forestry.counter.domain.model.pack.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gestionnaire de packs GeoSylva.
 *
 * Responsabilités :
 * - inventaire des packs installés / disponibles
 * - simulation de téléchargement (TODO : implémenter vraie API remote)
 * - cache disque des métadonnées
 * - flag feature pour activation progressive des modules
 * - API propre pour le PackManagerScreen
 *
 * OFFLINE-FIRST : fonctionne sans réseau avec les packs déjà installés.
 */
class PackManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME     = "geosylva_packs"
        private const val KEY_INSTALLED  = "installed_pack_ids"
        private const val KEY_VERSIONS   = "pack_versions"

        @Volatile private var instance: PackManager? = null
        fun getInstance(context: Context): PackManager =
            instance ?: synchronized(this) {
                instance ?: PackManager(context.applicationContext).also { instance = it }
            }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ─── État observable ──────────────────────────────────────────────────────

    private val _packState = MutableStateFlow(loadPackState())
    val packState: StateFlow<PackState> = _packState.asStateFlow()

    data class PackState(
        val allPacks: List<GeoPackDescriptor>,
        val downloadProgress: Map<String, Float> = emptyMap(), // packId → 0.0–1.0
        val lastError: String? = null
    ) {
        val installed: List<GeoPackDescriptor>
            get() = allPacks.filter { it.status in listOf(PackStatus.INSTALLED, PackStatus.EMBEDDED) }
        val available: List<GeoPackDescriptor>
            get() = allPacks.filter { it.status == PackStatus.AVAILABLE }
        val byLevel: Map<PackLevel, List<GeoPackDescriptor>>
            get() = allPacks.groupBy { it.level }
    }

    // ─── Chargement initial ───────────────────────────────────────────────────

    private fun loadPackState(): PackState {
        val installedIds = loadInstalledIds()
        val versions     = loadStoredVersions()

        val allPacks = buildAllPacksList(installedIds, versions)
        return PackState(allPacks)
    }

    private fun buildAllPacksList(
        installedIds: Set<String>,
        versions: Map<String, String>
    ): List<GeoPackDescriptor> {
        val result = mutableListOf<GeoPackDescriptor>()

        // Socle national (toujours embarqué)
        result += PackResolver.EMBEDDED_NATIONAL_PACK

        // Packs régionaux (catalogue officiel, état installé ou disponible)
        PackResolver.REGIONAL_CATALOG.forEach { desc ->
            val isInstalled = desc.id in installedIds
            val storedVersion = versions[desc.id]
            val hasUpdate = isInstalled && storedVersion != null && storedVersion != desc.version
            result += desc.copy(
                status = when {
                    !isInstalled -> PackStatus.AVAILABLE
                    hasUpdate    -> PackStatus.UPDATE_PENDING
                    else         -> PackStatus.INSTALLED
                }
            )
        }

        return result
    }

    // ─── API publique ─────────────────────────────────────────────────────────

    /**
     * Contexte territorial actif pour des coordonnées GPS.
     */
    fun getContextFor(lat: Double?, lon: Double?): TerritorialContext =
        PackResolver.inferTerritorialContext(lat, lon, _packState.value.installed)

    /**
     * Installe (simule) un pack — déclenche le préchargement.
     * TODO : remplacer simulation par vraie API de téléchargement + Room insert.
     */
    suspend fun installPack(packId: String, onProgress: (Float) -> Unit = {}) {
        val pack = _packState.value.allPacks.find { it.id == packId } ?: return
        if (pack.status == PackStatus.EMBEDDED) return

        // Simuler progression
        updateDownloadProgress(packId, 0f)
        var progress = 0f
        while (progress < 1f) {
            progress = (progress + 0.1f).coerceAtMost(1f)
            updateDownloadProgress(packId, progress)
            onProgress(progress)
            kotlinx.coroutines.delay(80)
        }

        // Marquer comme installé
        val updatedIds = loadInstalledIds() + packId
        saveInstalledIds(updatedIds)
        val updatedVersions = loadStoredVersions() + (packId to pack.version)
        saveStoredVersions(updatedVersions)

        updateDownloadProgress(packId, -1f) // -1 = terminé
        refreshState()
    }

    /**
     * Désinstalle un pack régional ou départemental.
     * Le socle national ne peut pas être désinstallé.
     */
    fun uninstallPack(packId: String) {
        val pack = _packState.value.allPacks.find { it.id == packId } ?: return
        if (pack.level == PackLevel.SOCLE_NATIONAL) return

        val updatedIds = loadInstalledIds() - packId
        saveInstalledIds(updatedIds)
        refreshState()
    }

    /**
     * Précharge automatiquement les packs autour d'une position GPS.
     * Utile au démarrage de l'app pour anticiper le travail hors réseau.
     */
    suspend fun preloadForLocation(lat: Double, lon: Double) {
        val context = PackResolver.inferTerritorialContext(lat, lon, emptyList())
        val toInstall = mutableListOf<String>()

        context.regionCode?.let { code ->
            val regionPack = _packState.value.available.find {
                it.level == PackLevel.REGIONAL && it.codeINSEE == code
            }
            regionPack?.let { toInstall += it.id }
        }
        context.deptCode?.let { code ->
            val deptPack = _packState.value.available.find {
                it.level == PackLevel.DEPARTEMENTAL && it.codeINSEE == code
            }
            deptPack?.let { toInstall += it.id }
        }

        toInstall.forEach { packId -> installPack(packId) }
    }

    /**
     * Vérifie les feature flags actifs pour un pack donné.
     * Permet activation progressive des modules.
     */
    fun isFeatureEnabled(featureKey: String, packId: String? = null): Boolean {
        val packs = if (packId != null)
            _packState.value.allPacks.filter { it.id == packId }
        else
            _packState.value.installed
        return packs.any { it.metaInfo.featureFlags[featureKey] == true }
    }

    /**
     * Taille totale des packs installés en Mo.
     */
    fun installedSizeMb(): Float =
        _packState.value.installed
            .filterNot { it.level == PackLevel.SOCLE_NATIONAL }
            .sumOf { it.sizeKb }
            .toFloat() / 1024f

    // ─── Persistance ─────────────────────────────────────────────────────────

    private fun loadInstalledIds(): Set<String> {
        val json = prefs.getString(KEY_INSTALLED, "[]") ?: "[]"
        val arr = JSONArray(json)
        return (0 until arr.length()).map { arr.getString(it) }.toSet()
    }

    private fun saveInstalledIds(ids: Set<String>) {
        val arr = JSONArray(ids.toList())
        prefs.edit().putString(KEY_INSTALLED, arr.toString()).apply()
    }

    private fun loadStoredVersions(): Map<String, String> {
        val json = prefs.getString(KEY_VERSIONS, "{}") ?: "{}"
        val obj = JSONObject(json)
        return obj.keys().asSequence().associateWith { obj.getString(it) }
    }

    private fun saveStoredVersions(versions: Map<String, String>) {
        val obj = JSONObject()
        versions.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_VERSIONS, obj.toString()).apply()
    }

    // ─── Helpers internes ────────────────────────────────────────────────────

    private fun updateDownloadProgress(packId: String, progress: Float) {
        val current = _packState.value
        val updated = if (progress < 0) {
            current.copy(downloadProgress = current.downloadProgress - packId)
        } else {
            current.copy(downloadProgress = current.downloadProgress + (packId to progress))
        }
        _packState.value = updated
    }

    private fun refreshState() {
        _packState.value = loadPackState().copy(
            downloadProgress = _packState.value.downloadProgress
        )
    }

    /**
     * Résumé texte du contexte actif — utile pour les écrans diagnostic.
     */
    fun contextSummary(lat: Double?, lon: Double?): String {
        val ctx = getContextFor(lat, lon)
        return when (ctx.appliedLevel) {
            PackLevel.DEPARTEMENTAL -> "Pack départemental actif (${ctx.deptCode})"
            PackLevel.REGIONAL      -> "Pack régional actif (${ctx.regionCode})"
            PackLevel.SOCLE_NATIONAL -> "Socle national (aucun pack local installé)"
        }
    }
}
