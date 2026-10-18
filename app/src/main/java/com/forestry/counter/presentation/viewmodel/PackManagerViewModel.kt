package com.forestry.counter.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forestry.counter.BuildConfig
import com.forestry.counter.network.SecureHttpClient
import com.forestry.counter.domain.model.pack.GeoPackDescriptor
import com.forestry.counter.domain.model.pack.PackLevel
import com.forestry.counter.data.packs.AdministrativePackContent
import com.forestry.counter.data.packs.PackCatalogIssue
import com.forestry.counter.domain.usecase.pack.PackArtifactIdentity
import com.forestry.counter.domain.usecase.pack.PackManager
import com.forestry.counter.domain.usecase.pack.RegionalPackSelection
import com.forestry.counter.domain.usecase.pack.IncompleteRegionalPackException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Actions de l'écran Packs. Le gestionnaire reste l'autorité d'installation.
 *
 * Une fermeture du ViewModel interrompt le transfert : le fragment vérifiable
 * reste sur disque pour une reprise explicite. Ce n'est pas un job d'arrière-plan.
 */
class PackManagerViewModel(private val manager: PackManager) : ViewModel() {
    /** Statut produit indépendant des messages techniques conservés par le gestionnaire. */
    enum class CatalogMessage {
        LOADING, NOT_CONFIGURED, AUTH_REQUIRED, NETWORK, INVALID_RESPONSE,
        STORAGE, SERVER, CACHE, CURRENT, EMPTY, UNAVAILABLE
    }

    companion object {
        fun catalogMessage(state: PackManager.PackState, refreshFailed: Boolean = false): CatalogMessage = when {
            state.catalogRefreshing -> CatalogMessage.LOADING
            state.catalogIssue == PackCatalogIssue.NOT_CONFIGURED -> CatalogMessage.NOT_CONFIGURED
            state.catalogIssue == PackCatalogIssue.AUTH_REQUIRED -> CatalogMessage.AUTH_REQUIRED
            state.catalogIssue == PackCatalogIssue.NETWORK -> CatalogMessage.NETWORK
            state.catalogIssue == PackCatalogIssue.INVALID_RESPONSE -> CatalogMessage.INVALID_RESPONSE
            state.catalogIssue == PackCatalogIssue.STORAGE -> CatalogMessage.STORAGE
            state.catalogIssue == PackCatalogIssue.SERVER -> CatalogMessage.SERVER
            refreshFailed || state.catalogError != null -> CatalogMessage.UNAVAILABLE
            state.catalogFromCache -> CatalogMessage.CACHE
            state.allPacks.any { it.signedManifest != null } -> CatalogMessage.CURRENT
            else -> CatalogMessage.EMPTY
        }

        fun isAllowedDownloadUrl(url: String?): Boolean = url != null && (
            SecureHttpClient.isSafeRemoteHttpsUrl(url) ||
                BuildConfig.DEBUG && SecureHttpClient.isSafeLocalDebugUrl(url)
            )
    }

    val packState = manager.packState

    data class ActionState(
        val running: Set<String> = emptySet(),
        val errors: Map<String, String> = emptyMap(),
        val planning: Boolean = false,
        val plan: DownloadPlan? = null,
        val planError: String? = null,
        val incompleteRegion: Boolean = false,
        val downloadingSelection: Boolean = false,
        val loadingPreview: Boolean = false,
        val preview: AdministrativePackContent? = null,
        val previewError: String? = null,
        val catalogRefreshFailed: Boolean = false
    )

    private val _actionState = MutableStateFlow(ActionState())
    val actionState = _actionState.asStateFlow()
    private val jobs = mutableMapOf<String, Job>()
    private var catalogJob: Job? = null
    private var planningJob: Job? = null
    private var selectionJob: Job? = null
    private var previewJob: Job? = null
    private var planningGeneration = 0L
    private var previewGeneration = 0L

    /** Archives JSON conservées directement : le transfert devient le fichier final. */
    data class DownloadPlan(
        val packs: List<GeoPackDescriptor>,
        val archiveBytes: Long,
        val availableBytes: Long,
        val requestedPackIds: Set<String> = emptySet(),
        val coveredPacks: List<GeoPackDescriptor> = packs,
        val includedDepartmentCount: Int = 0
    ) {
        val fits: Boolean get() = archiveBytes <= availableBytes
    }

    fun refreshCatalog() {
        if (catalogJob?.isActive == true) return
        _actionState.update { it.copy(catalogRefreshFailed = false) }
        catalogJob = viewModelScope.launch {
            try {
                manager.refreshCatalog()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Le gestionnaire publie le statut réseau/auth/cache avant de
                // relancer ; ne pas laisser une erreur attendue fermer l’écran.
                if (packState.value.catalogError == null) {
                    _actionState.update { it.copy(catalogRefreshFailed = true) }
                }
            }
        }
    }

    fun prepareDownload(packIds: Set<String>) {
        planningJob?.cancel()
        val generation = ++planningGeneration
        _actionState.update { it.copy(planning = true, plan = null, planError = null, incompleteRegion = false) }
        planningJob = viewModelScope.launch {
            try {
                val state = packState.value
                val requested = resolveSelection(state, packIds)
                val packs = requested.distinctBy(::identity).filter { pack ->
                    state.activePacks.none {
                        it.id == pack.id && it.version == pack.version &&
                            it.checksum?.lowercase() == pack.checksum?.lowercase()
                    }
                }
                require(packs.all {
                    it.contentKind in setOf("administrative_boundaries", "forestry_layers") &&
                        isAllowedDownloadUrl(it.downloadUrl) &&
                        it.archiveBytes?.let { bytes -> bytes > 0L } == true
                }) { "Les volumes ou la distribution de ces packs ne sont pas disponibles." }
                val archiveBytes = packs.fold(0L) { total, pack ->
                    Math.addExact(total, requireNotNull(pack.archiveBytes))
                }
                val availableBytes = manager.actualAvailableBytes()
                require(availableBytes >= 0L) { "L’espace libre ne peut pas être mesuré." }
                val includesRegion = requested.any { it.level == PackLevel.REGIONAL }
                val regionalIds = requested.filter { it.level == PackLevel.REGIONAL }.map { it.id }.toSet()
                if (generation == planningGeneration) _actionState.update { it.copy(plan = DownloadPlan(
                    packs, archiveBytes, availableBytes, packIds.toSet(), requested,
                    if (includesRegion) requested.count { it.level == PackLevel.DEPARTEMENTAL && it.parentId in regionalIds } else 0
                )) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == planningGeneration) _actionState.update { it.copy(
                    planError = error.message ?: "Le téléchargement ne peut pas être préparé.",
                    incompleteRegion = error is IncompleteRegionalPackException
                ) }
            } finally {
                if (generation == planningGeneration) _actionState.update { it.copy(planning = false) }
            }
        }
    }

    fun dismissPlan() {
        ++planningGeneration
        planningJob?.cancel()
        _actionState.update { it.copy(plan = null, planError = null, planning = false, incompleteRegion = false) }
    }

    fun confirmDownload() {
        if (selectionJob?.isActive == true) return
        val plan = _actionState.value.plan ?: return
        if (!plan.fits || plan.packs.isEmpty()) return
        _actionState.update { it.copy(downloadingSelection = true, planError = null, incompleteRegion = false) }
        selectionJob = viewModelScope.launch {
            try {
                // Le dialogue n’est pas une réservation : vérifier à nouveau le
                // catalogue et le volume cible avant toute installation.
                val currentSelection = resolveSelection(packState.value, plan.requestedPackIds)
                require(currentSelection.size == plan.coveredPacks.size && plan.coveredPacks.all { expected ->
                    currentSelection.any { current ->
                        identity(current) == identity(expected) && current.archiveBytes == expected.archiveBytes
                    }
                }) { "Le catalogue a changé. Préparez une nouvelle confirmation." }
                val currentPending = currentSelection.filter { pack ->
                    packState.value.activePacks.none { active ->
                        active.id == pack.id && active.version == pack.version &&
                            active.checksum?.lowercase() == pack.checksum?.lowercase()
                    }
                }
                require(currentPending.map(::identity).toSet() == plan.packs.map(::identity).toSet()) {
                    "Les packs actifs ont changé. Préparez une nouvelle confirmation."
                }
                require(manager.actualAvailableBytes() >= plan.archiveBytes) {
                    "L’espace libre a diminué. Préparez une nouvelle confirmation."
                }
                _actionState.update { it.copy(plan = null) }
                for (pack in plan.packs) {
                    launchAction(pack.id) { manager.installPack(pack.id, identity(pack)) }?.join()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _actionState.update { it.copy(plan = null,
                    planError = error.message ?: "Le téléchargement ne peut pas démarrer.",
                    incompleteRegion = error is IncompleteRegionalPackException
                ) }
            } finally {
                _actionState.update { it.copy(downloadingSelection = false) }
            }
        }
    }

    fun interruptSelection() {
        selectionJob?.cancel()
        jobs.values.toList().forEach { it.cancel() }
    }

    private suspend fun resolveSelection(state: PackManager.PackState, requestedIds: Set<String>): List<GeoPackDescriptor> {
        require(!state.catalogRefreshing) { "Le catalogue est en cours d’actualisation. Réessayez après la mise à jour." }
        val hasRegion = state.allPacks.any { it.id in requestedIds && it.level == PackLevel.REGIONAL }
        val coverage = if (hasRegion) manager.administrativeDepartmentsByRegion() else emptyMap()
        require(!packState.value.catalogRefreshing && packState.value.allPacks == state.allPacks && packState.value.activePacks == state.activePacks) {
            "Le catalogue ou les packs actifs ont changé. Préparez une nouvelle confirmation."
        }
        return RegionalPackSelection.resolve(state.allPacks, requestedIds, coverage)
    }

    fun previewInstalledPack(packId: String) {
        previewJob?.cancel()
        val generation = ++previewGeneration
        _actionState.update { it.copy(loadingPreview = true, preview = null, previewError = null) }
        previewJob = viewModelScope.launch {
            try {
                val content = manager.readActiveAdministrativePack(packId)
                if (generation == previewGeneration) _actionState.update { it.copy(preview = content) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == previewGeneration) _actionState.update { it.copy(previewError = error.message ?: "Le pack installé ne peut pas être lu.") }
            } finally {
                if (generation == previewGeneration) _actionState.update { it.copy(loadingPreview = false) }
            }
        }
    }

    fun closePreview() {
        ++previewGeneration
        previewJob?.cancel()
        _actionState.update { it.copy(preview = null, loadingPreview = false, previewError = null) }
    }

    private fun identity(pack: GeoPackDescriptor): PackArtifactIdentity {
        val checksum = pack.checksum?.trim()?.lowercase()
        require(!pack.version.isBlank() && checksum?.matches(Regex("[a-f0-9]{64}")) == true) {
            "L’identité vérifiable du pack n’est pas disponible."
        }
        return PackArtifactIdentity(pack.id, pack.version, requireNotNull(checksum))
    }

    fun installPack(packId: String) = launchAction(packId) { manager.installPack(packId) }

    fun deactivatePack(packId: String) = launchAction(packId) { manager.uninstallPack(packId) }

    fun reactivatePack(packId: String, version: String) =
        launchAction(packId) { manager.reactivatePack(packId, version) }

    fun interrupt(packId: String) {
        jobs[packId]?.cancel()
    }

    private fun launchAction(packId: String, action: suspend () -> Unit): Job? {
        // Appels et nettoyage confinés au dispatcher principal du ViewModel.
        if (packId in jobs) return null
        _actionState.update {
            it.copy(running = it.running + packId, errors = it.errors - packId)
        }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _actionState.update {
                    it.copy(errors = it.errors + (packId to (error.message ?: "Échec de l'action")))
                }
            }
        }
        jobs[packId] = job
        // L'annulation peut précéder la première exécution du corps de la coroutine.
        job.invokeOnCompletion {
            jobs.remove(packId)
            _actionState.update { it.copy(running = it.running - packId) }
        }
        job.start()
        return job
    }
}
