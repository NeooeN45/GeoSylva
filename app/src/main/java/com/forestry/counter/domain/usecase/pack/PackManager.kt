package com.forestry.counter.domain.usecase.pack

import android.content.Context
import com.forestry.counter.BuildConfig
import com.forestry.counter.ForestryCounterApplication
import com.forestry.counter.data.packs.*
import com.forestry.counter.domain.model.pack.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * Gestionnaire de packs GeoSylva.
 *
 * Responsabilités :
 * - inventaire des packs installés / disponibles
 * - téléchargement vérifié des packs quand une URL et un SHA-256 sont fournis
 * - cache disque des métadonnées
 * - flag feature pour activation progressive des modules
 * - API propre pour le PackManagerScreen
 *
 * OFFLINE-FIRST : fonctionne sans réseau avec les packs déjà installés.
 */
class PackManager(private val context: Context, onStoredSessionUpdated: () -> Unit = {}) {

    companion object {
        private const val PREFS_NAME     = "geosylva_packs"
        private const val KEY_INSTALLED  = "installed_pack_ids"
        private const val KEY_VERSIONS   = "pack_versions"
        private val operationLocks = ConcurrentHashMap<String, Mutex>()

        @Volatile private var instance: PackManager? = null
        fun getInstance(context: Context): PackManager =
            instance ?: synchronized(this) {
                instance ?: context.applicationContext.let { application ->
                    PackManager(application, onStoredSessionUpdated = {
                        (application as? ForestryCounterApplication)?.identityRepository?.reloadStoredSession()
                    }).also { instance = it }
                }
            }
    }

    private val prefs =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val store = PackInstallationStore(File(context.filesDir, "packs"))
    private val refreshMonitor = Any()
    private val contract = AdministrativePackContract(BuildConfig.GSIE_PACK_PUBLIC_KEY_DER_BASE64)
    private val repository = GsiePackRepository(context, contract, onStoredSessionUpdated)
    private val catalogCache = SignedPackCatalogCache(File(context.filesDir, "pack-catalog/catalog.json"))
    private val catalogMutex = Mutex()
    private var catalogLoadError: String? = null
    private var catalogFromCache = false
    @Volatile private var catalog: List<GeoPackDescriptor> = loadCatalog()

    // ─── État observable ──────────────────────────────────────────────────────

    private val _packState = MutableStateFlow(loadPackState())
    val packState: StateFlow<PackState> = _packState.asStateFlow()

    data class PackState(
        val allPacks: List<GeoPackDescriptor>,
        val downloadProgress: Map<String, Float> = emptyMap(), // packId → 0.0–1.0
        val lastError: String? = null,
        val activePacks: List<GeoPackDescriptor> = emptyList(),
        val diskUsageBytes: Long = 0L,
        val retainedPacks: List<GeoPackDescriptor> = emptyList(),
        val catalogRefreshing: Boolean = false,
        val catalogError: String? = null,
        val catalogIssue: PackCatalogIssue? = null,
        val catalogFromCache: Boolean = false,
        val availableStorageBytes: Long? = null
    ) {
        val installed: List<GeoPackDescriptor>
            get() = allPacks.filter { it.status == PackStatus.EMBEDDED } + activePacks
        val available: List<GeoPackDescriptor>
            get() = allPacks.filter { it.status == PackStatus.AVAILABLE }
        val byLevel: Map<PackLevel, List<GeoPackDescriptor>>
            get() = allPacks.groupBy { it.level }
    }

    // ─── Chargement initial ───────────────────────────────────────────────────

    private fun loadPackState(): PackState {
        return try {
            if (!store.hasRegistry()) {
                val installedIds = loadInstalledIds()
                val versions = loadStoredVersions()
                store.migrateLegacy(installedIds, versions, legacyDescriptors(installedIds, versions))
            }
            val activeErrors = mutableListOf<String>()
            val active = store.activePacks().filter { descriptor ->
                if (descriptor.contentKind !in setOf(AdministrativePackContract.CONTENT_KIND, OfflineForestryLayerStore.CONTENT_KIND)) true
                else runCatching {
                    val file = store.fileFor(descriptor.id, descriptor.version)
                    if (descriptor.contentKind == OfflineForestryLayerStore.CONTENT_KIND) OfflineForestryLayerStore.validateOnce(file, descriptor, contract)
                    else contract.validateArtifact(file, descriptor)
                }
                    .onFailure { activeErrors += "${descriptor.name} : ${it.message}" }.isSuccess
            }
            val retained = store.retainedPacks()
            PackState(
                buildAllPacksList(active, retained), activePacks = active,
                retainedPacks = retained, diskUsageBytes = store.storedSizeBytes(),
                lastError = activeErrors.takeIf { it.isNotEmpty() }?.joinToString("\n"),
                catalogError = catalogLoadError, catalogIssue = catalogLoadError?.let { PackCatalogIssue.INVALID_RESPONSE },
                catalogFromCache = catalogFromCache,
                availableStorageBytes = runCatching { context.filesDir.usableSpace }.getOrNull()
            )
        } catch (error: Exception) {
            // Conserve le registre et les fichiers ; aucune activation n'est devinée.
            PackState(
                buildAllPacksList(emptyList()),
                lastError = error.message,
                diskUsageBytes = runCatching { store.storedSizeBytes() }.getOrDefault(0L)
            )
        }
    }

    private fun buildAllPacksList(
        activePacks: List<GeoPackDescriptor>,
        retainedPacks: List<GeoPackDescriptor> = emptyList()
    ): List<GeoPackDescriptor> {
        val result = mutableListOf<GeoPackDescriptor>()

        // Socle national (toujours embarqué)
        result += PackResolver.EMBEDDED_NATIONAL_PACK

        // Publications réellement raccordées ; aucune entrée statique de démonstration.
        catalog.forEach { desc ->
            val active = activePacks.find { it.id == desc.id }
            val hasUpdate = active != null && active.version != desc.version
            result += desc.copy(
                installedVersion = active?.version,
                status = when {
                    active == null -> PackStatus.AVAILABLE
                    hasUpdate    -> PackStatus.UPDATE_PENDING
                    else         -> PackStatus.INSTALLED
                }
            )
        }
        // Une suppression du catalogue serveur ne supprime pas une version locale.
        result += activePacks.filter { active -> result.none { it.id == active.id } }
        retainedPacks.forEach { retained ->
            if (result.none { it.id == retained.id }) {
                // Retrouvable hors ligne, sans présenter une ancienne URL comme catalogue courant.
                result += retained.copy(status = PackStatus.AVAILABLE, installedVersion = null, downloadUrl = null)
            }
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
     * Démarre l'installation d'un pack GeoSylva.
     *
     * Le pack n'est marqué installé qu'après téléchargement complet et validation
     * du checksum SHA-256. Une URL ou un checksum manquant provoque une erreur
     * explicite : un pack ne peut plus être présenté comme installé par simulation.
     *
     * @param packId    Identifiant du pack (ex: `fr.region.11`, `fr.dept.75`)
     * @param onProgress Callback de progression [0.0 ; 1.0]
     */
    suspend fun installPack(packId: String, onProgress: (Float) -> Unit = {}) {
        operationLock(packId).withLock { installLocked(packId, onProgress) }
    }

    /** Identité confirmée par l'utilisateur ; un changement catalogue exige une nouvelle confirmation. */
    suspend fun installPack(packId: String, expectedIdentity: PackArtifactIdentity, onProgress: (Float) -> Unit = {}) {
        operationLock(packId).withLock {
            installLocked(packId, onProgress, expectedIdentity)
        }
    }

    private suspend fun installLocked(packId: String, onProgress: (Float) -> Unit, expectedIdentity: PackArtifactIdentity? = null) {
        val pack = _packState.value.allPacks.find { it.id == packId }
            ?: throw IllegalArgumentException("Aucun pack publié ou local ne correspond à $packId")
        require(expectedIdentity == null || expectedIdentity == PackArtifactIdentity(pack.id, pack.version, pack.checksum.orEmpty())) {
            "La version publiée a changé ; vérifiez de nouveau le téléchargement"
        }
        if (pack.status == PackStatus.EMBEDDED) return
        val url = pack.downloadUrl?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Aucune URL de téléchargement pour ${pack.id}")
        val expectedSha = pack.checksum?.trim()?.lowercase()?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
            ?: throw IllegalStateException("Checksum SHA-256 manquant ou invalide pour ${pack.id}")

        // Sécurité : HTTPS obligatoire. Le SHA-256 protège l'intégrité mais
        // pas la confidentialité de la source — un MITM sur HTTP pourrait
        // observer le flux. Validé ici (API publique) plutôt que dans
        // downloadWithChecksum (fonction pure testée avec MockWebServer HTTP).
        // La distribution signée n'autorise que l'origine GSIE configurée.
        // Les anciennes URL non signées ne deviennent pas des publications qualifiées.
        check(pack.contentKind in setOf(AdministrativePackContract.CONTENT_KIND, OfflineForestryLayerStore.CONTENT_KIND) && pack.signedManifest != null) {
            "Ce fichier ne possède pas de publication cartographique GSIE qualifiée"
        }
        contract.verify(pack.signedManifest)
        val target = store.fileFor(pack.id, pack.version)
        if (target.isFile) {
            withContext(Dispatchers.IO) {
                val reactivationContext = coroutineContext
                contract.validateArtifact(target, pack) { reactivationContext.ensureActive() }
                store.activate(pack, target) { reactivationContext.ensureActive() }
                refreshState()
            }
            onProgress(1f)
            return
        }
        checkDiskSpace(context.filesDir.usableSpace, pack.archiveBytes ?: error("Volume signé absent"), pack.id)
        updateDownloadProgress(packId, 0f)
        val partial = File(target.parentFile, "${target.name}.part")
        try {
            target.parentFile?.mkdirs()
            repository.download(pack, partial) { progress ->
                updateDownloadProgress(packId, progress)
                onProgress(progress)
            }
            withContext(Dispatchers.IO) {
                val installContext = coroutineContext
                contract.validateArtifact(partial, pack) { installContext.ensureActive() }
                installContext.ensureActive()
                store.activate(pack, partial) { installContext.ensureActive() }
                refreshState()
            }
        } catch (error: Throwable) {
            // Une coupure réseau (IOException) laisse le fragment `.part` et son
            // `.etag` en place : la prochaine tentative reprendra le
            // téléchargement au lieu de repartir de zéro. Toute autre erreur
            // (checksum invalide, espace disque, code HTTP) signale un état non
            // fiable pour la reprise -> nettoyage complet.
            if (error !is IOException && error !is CancellationException) {
                partial.delete()
                File(partial.parentFile, "${partial.name}.etag").delete()
            }
            _packState.update { current ->
                current.copy(lastError = if (error is CancellationException) null else error.message ?: "Échec du téléchargement")
            }
            throw error
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                val diskUsage = runCatching { store.storedSizeBytes() }.getOrNull()
                _packState.update { current ->
                    current.copy(
                        downloadProgress = current.downloadProgress - packId,
                        diskUsageBytes = diskUsage ?: current.diskUsageBytes
                    )
                }
            }
        }
    }

    /**
     * Désactive un pack régional ou départemental.
     * Le socle national ne peut pas être désinstallé.
     * Les fichiers restent conservés : des résultats peuvent citer ces versions.
     */
    suspend fun uninstallPack(packId: String) {
        operationLock(packId).withLock {
            val pack = _packState.value.allPacks.find { it.id == packId } ?: return@withLock
            if (pack.level == PackLevel.SOCLE_NATIONAL) return@withLock
            withContext(Dispatchers.IO) {
                store.deactivate(packId)
                refreshState()
            }
        }
    }

    /** Réactive une version conservée après vérification SHA-256, sans réseau. */
    suspend fun reactivatePack(packId: String, version: String) {
        operationLock(packId).withLock {
            withContext(Dispatchers.IO) {
                val retained = store.getRetained(packId, version)
                    ?: throw IOException("Version locale conservée introuvable")
                val reactivationContext = coroutineContext
                if (retained.contentKind in setOf(AdministrativePackContract.CONTENT_KIND, OfflineForestryLayerStore.CONTENT_KIND)) {
                    contract.validateArtifact(store.fileFor(packId, version), retained) { reactivationContext.ensureActive() }
                }
                store.activate(retained, store.fileFor(packId, version)) { reactivationContext.ensureActive() }
                refreshState()
            }
        }
    }

    private fun operationLock(packId: String): Mutex =
        operationLocks.computeIfAbsent("${context.filesDir.absolutePath}/$packId") { Mutex() }

    /**
     * Précharge automatiquement les packs autour d'une position GPS.
     * Utile au démarrage de l'app pour anticiper le travail hors réseau.
     */
    suspend fun preloadForLocation(lat: Double, lon: Double) {
        val context = PackResolver.inferTerritorialContext(lat, lon, emptyList())
        val toInstall = mutableListOf<String>()

        context.regionCode?.let { code ->
            val regionPack = _packState.value.available.find {
                it.level == PackLevel.REGIONAL && it.codeINSEE == code && !it.downloadUrl.isNullOrBlank()
            }
            regionPack?.let { toInstall += it.id }
        }
        context.deptCode?.let { code ->
            val deptPack = _packState.value.available.find {
                it.level == PackLevel.DEPARTEMENTAL && it.codeINSEE == code && !it.downloadUrl.isNullOrBlank()
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
            _packState.value.installed.filter { it.id == packId }
        else
            _packState.value.installed
        return packs.any { it.contentKind != AdministrativePackContract.CONTENT_KIND && it.metaInfo.featureFlags[featureKey] == true }
    }

    /**
     * Taille totale des packs installés en Mo.
     */
    fun installedSizeMb(): Float =
        store.activeSizeBytes().toFloat() / (1024f * 1024f)

    /** Taille réelle : versions actives, historiques conservées et fragments. */
    fun storedSizeBytes(): Long = store.storedSizeBytes()

    suspend fun actualAvailableBytes(): Long = withContext(Dispatchers.IO) {
        context.filesDir.usableSpace.also { require(it >= 0) { "Espace disponible inaccessible" } }
    }

    /** Couverture attendue issue du vrai référentiel IGN ; aucun pack n’est fabriqué ici. */
    suspend fun administrativeDepartmentsByRegion(): Map<String, Set<String>> = withContext(Dispatchers.IO) {
        context.assets.open(AdministrativeTerritoryCoverage.ASSET_PATH).use(AdministrativeTerritoryCoverage::read)
    }

    /** Les contours et les libellés viennent exclusivement du fichier installé vérifié. */
    suspend fun readActiveAdministrativePack(packId: String): AdministrativePackContent = withContext(Dispatchers.IO) {
        operationLock(packId).withLock {
            val descriptor = store.activePacks().find { it.id == packId && it.contentKind in setOf(AdministrativePackContract.CONTENT_KIND, OfflineForestryLayerStore.CONTENT_KIND) }
                ?: error("Pack cartographique actif introuvable")
            val readContext = coroutineContext
            val file = store.fileFor(descriptor.id, descriptor.version)
            if (descriptor.contentKind == OfflineForestryLayerStore.CONTENT_KIND) {
                val manifest = contract.verify(descriptor.signedManifest ?: error("Manifeste signé absent"))
                OfflineForestryLayerStore.validateOnce(file, descriptor, contract) { readContext.ensureActive() }
                OfflineForestryLayerStore.administrativeProjection(file, manifest)
            } else contract.read(file, descriptor) { readContext.ensureActive() }
        }
    }

    /** Publication réelle authentifiée, tous comptes compris ; aucune condition d'abonnement. */
    suspend fun refreshCatalog() = catalogMutex.withLock {
        _packState.update { it.copy(catalogRefreshing = true, catalogError = null, catalogIssue = null) }
        try {
            withContext(Dispatchers.IO) {
                val signed = repository.fetchCatalog()
                val verified = signed.map { metadata ->
                    contract.descriptor(metadata, repository.artifactUrl(contract.verify(metadata)))
                }
                try { catalogCache.write(signed) }
                catch (error: IOException) { throw PackCatalogException(PackCatalogIssue.STORAGE, "Écriture du catalogue local impossible", error) }
                catalog = verified
                catalogFromCache = false
                catalogLoadError = null
                refreshState()
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            val issue = (error as? PackCatalogException)?.issue ?: when (error) {
                is IOException -> PackCatalogIssue.NETWORK
                else -> PackCatalogIssue.INVALID_RESPONSE
            }
            _packState.update { it.copy(catalogError = error.message ?: "Catalogue GSIE indisponible", catalogIssue = issue) }
            throw error
        } finally { _packState.update { it.copy(catalogRefreshing = false) } }
    }

    private fun loadCatalog(): List<GeoPackDescriptor> = try {
        catalogCache.read().map { metadata ->
            contract.descriptor(metadata, repository.artifactUrl(contract.verify(metadata)))
        }.also {
            require(it.map { descriptor -> descriptor.id }.distinct().size == it.size && it.map { descriptor -> descriptor.territoryId }.distinct().size == it.size) {
                "Identités du catalogue local dupliquées"
            }
            catalogFromCache = it.isNotEmpty()
        }
    } catch (error: Exception) {
        // Ne supprime pas un cache illisible ou non approuvé.
        catalogLoadError = "Catalogue local non accepté : ${error.message}"
        emptyList()
    }

    // ─── Persistance ─────────────────────────────────────────────────────────

    private fun loadInstalledIds(): Set<String> {
        val json = prefs.getString(KEY_INSTALLED, "[]") ?: "[]"
        return Json.parseToJsonElement(json).jsonArray.map { it.jsonPrimitive.content }.toSet()
    }

    private fun loadStoredVersions(): Map<String, String> {
        val json = prefs.getString(KEY_VERSIONS, "{}") ?: "{}"
        return Json.parseToJsonElement(json).jsonObject.mapValues { it.value.jsonPrimitive.content }
    }

    /** Index de migration de fichiers existants, jamais un catalogue disponible. */
    private fun legacyDescriptors(
        installedIds: Set<String>,
        versions: Map<String, String>
    ): List<GeoPackDescriptor> = installedIds.mapNotNull { id ->
        val identity = Regex("fr\\.(region|dept)\\.([A-Za-z0-9]+)").matchEntire(id)
            ?: return@mapNotNull null
        val version = versions[id] ?: return@mapNotNull null
        val file = runCatching { store.fileFor(id, version) }.getOrNull()
            ?: return@mapNotNull null
        if (!file.isFile) return@mapNotNull null
        GeoPackDescriptor(
            id = id,
            level = if (identity.groupValues[1] == "region") PackLevel.REGIONAL else PackLevel.DEPARTEMENTAL,
            name = "Pack local hérité ($id)",
            codeINSEE = identity.groupValues[2],
            version = version,
            buildDate = "",
            sizeKb = file.length() / 1024L + if (file.length() % 1024L == 0L) 0L else 1L,
            status = PackStatus.INSTALLED,
            features = PackFeatures(),
            metaInfo = PackMetaInfo(
                author = "Inconnu", license = "Inconnue", minAppVersion = "",
                notes = "Fichier local hérité ; contenu et provenance historiques non qualifiés"
            )
        )
    }

    // ─── Helpers internes ────────────────────────────────────────────────────

    private fun updateDownloadProgress(packId: String, progress: Float) {
        _packState.update { current ->
            current.copy(downloadProgress = if (progress < 0) current.downloadProgress - packId
            else current.downloadProgress + (packId to progress))
        }
    }

    private fun refreshState() = synchronized(refreshMonitor) {
        val reloaded = loadPackState()
        _packState.update { current -> reloaded.copy(
            downloadProgress = current.downloadProgress,
            catalogRefreshing = current.catalogRefreshing,
            catalogError = current.catalogError ?: reloaded.catalogError,
            catalogIssue = current.catalogIssue ?: reloaded.catalogIssue
        ) }
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
