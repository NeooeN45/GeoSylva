package com.forestry.counter.presentation.screens.packs.offline

import com.forestry.counter.data.cartography.CartographicScopeAuthority
import com.forestry.counter.data.local.entity.OfflineMapPackEntity
import com.forestry.counter.data.packs.offline.*
import com.forestry.counter.domain.repository.CartographicStoreResult
import com.forestry.counter.domain.usecase.maps.MapDataScope
import com.quintessences.geosylva.cartography.mapbox.offline.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Application-owned transfer coordinator. Screens are only observers of this common service. */
class OfflineMapPackController(
    private val repository: OfflineMapPackRepository,
    private val scope: CoroutineScope,
    network: Flow<OfflinePackNetwork>,
) {
    data class State(
        val authorityKey: String? = null,
        val packs: List<OfflineMapPackEntity> = emptyList(),
        val planning: Boolean = false,
        val plan: OfflinePackPlan? = null,
        val removal: OfflinePackRemoval? = null,
        val running: Set<String> = emptySet(),
        val progress: Map<String, Pair<OfflineTransferStage, OfflineResourceProgress>> = emptyMap(),
        val wifiOnly: Boolean = true,
        val connected: Boolean = false,
        val wifi: Boolean = false,
        val freeBytes: Long? = null,
        val error: Boolean = false,
        val removed: Boolean = false,
    )
    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable.asStateFlow()
    private var token: [REDACTED_SECRET] = null
    private var generation = 0L
    private var planningJob: Job? = null
    private val transfers = mutableMapOf<String, Job>()
    private val desiredActive = mutableSetOf<String>()
    private var refreshJob: Job? = null
    private var activeScope: MapDataScope? = null
    init {
        scope.launch { repository.accessChanges().collect {
            val access = token
            if (access != null && !repository.isCurrent(access)) invalidate()
        } }
        scope.launch { network.collect { networkState ->
            mutable.update { it.copy(connected = networkState.connected, wifi = networkState.wifi) }
            if (!networkAllowed()) {
                transfers.keys.toList().forEach { id -> stop(id, waiting = true) }
            } else desiredActive.toList().forEach { id -> start(id) }
        } }
    }
    /** A new scope never adopts a previous editor's selection or pending confirmation. */
    fun activate(dataScope: MapDataScope) {
        if (activeScope == dataScope && token?.let(repository::isCurrent) == true) { refresh(); return }
        invalidate()
        activeScope = dataScope
        val version = generation
        scope.launch {
            when (val captured = repository.capture(dataScope)) {
                is CartographicStoreResult.Success -> if (version == generation) {
                    token = [REDACTED_SECRET]
                    mutable.update { it.copy(authorityKey = captured.value.authorityKey) }
                    refresh()
                }
                is CartographicStoreResult.Failure -> if (version == generation) mutable.update { it.copy(error = true) }
            }
        }
    }
    fun refresh() {
        val access = token ?: return
        refreshJob?.cancel()
        refreshJob = scope.launch {
            try {
                // Never convert an active transfer to a restored pause during an inventory refresh.
                if (transfers.isEmpty()) {
                    val packs = repository.list(access)
                    if (current(access)) mutable.update { it.copy(packs = packs, freeBytes = repository.freeBytes(), error = false) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (current(access)) mutable.update { it.copy(error = true) } }
        }
    }
    fun prepare(input: OfflinePackInput) {
        val access = token ?: return
        planningJob?.cancel()
        mutable.update { it.copy(planning = true, plan = null, error = false, removed = false) }
        planningJob = scope.launch {
            try {
                val plan = repository.prepare(input, access)
                if (current(access)) mutable.update { it.copy(plan = plan) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (current(access)) mutable.update { it.copy(error = true) } }
            finally { if (current(access)) mutable.update { it.copy(planning = false) } }
        }
    }
    fun dismissPlan() { planningJob?.cancel(); mutable.update { it.copy(plan = null, planning = false) } }
    fun confirm() {
        val access = token ?: return
        val plan = mutable.value.plan ?: return
        mutable.update { it.copy(plan = null, planning = true) }
        scope.launch {
            try {
                val pack = repository.confirm(plan, access)
                if (current(access)) {
                    mutable.update { it.copy(packs = (listOf(pack) + it.packs).distinctBy { row -> row.id }) }
                    desiredActive += pack.id
                    if (networkAllowed()) start(pack.id) else stop(pack.id, waiting = true)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (current(access)) mutable.update { it.copy(error = true) } }
            finally { if (current(access)) mutable.update { it.copy(planning = false) } }
        }
    }
    fun wifiOnly(value: Boolean) {
        mutable.update { it.copy(wifiOnly = value) }
        if (!networkAllowed()) transfers.keys.toList().forEach { stop(it, waiting = true) }
        else desiredActive.toList().forEach(::start)
    }
    fun resume(id: String) {
        if (mutable.value.packs.none { it.id == id }) return
        desiredActive += id
        if (networkAllowed()) start(id) else stop(id, waiting = true)
    }
    fun pause(id: String) { desiredActive -= id; stop(id, waiting = false) }
    private fun stop(id: String, waiting: Boolean) {
        val access = token ?: return
        val job = transfers.remove(id)
        job?.cancel()
        mutable.update { it.copy(running = it.running - id) }
        scope.launch {
            job?.join()
            if (current(access) && (waiting && id in desiredActive || !waiting && id !in desiredActive)) {
                runCatching { repository.mark(id, if (waiting) OfflinePackStatus.WAITING_WIFI else OfflinePackStatus.PAUSED, access) }
                    .onSuccess(::replace)
            }
        }
    }
    private fun start(id: String) {
        val access = token ?: return
        if (id in transfers || id !in desiredActive || !networkAllowed()) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                repository.transfer(id, access) { stage, progress ->
                    // SDK tile callbacks run on its worker: StateFlow is safe; mutations remain in this scope.
                    if (current(access)) mutable.update { it.copy(progress = it.progress + (id to (stage to progress))) }
                }
                desiredActive -= id
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                desiredActive -= id
                if (current(access)) mutable.update { it.copy(error = true) }
            } finally {
                if (transfers[id] === currentCoroutineContext()[Job]) {
                    transfers.remove(id)
                    mutable.update { it.copy(running = it.running - id) }
                    if (current(access)) refresh()
                }
            }
        }
        transfers[id] = job
        mutable.update { it.copy(running = it.running + id) }
        job.start()
    }
    fun prepareRemoval(id: String) {
        val access = token ?: return
        scope.launch { try {
            val removal = repository.prepareRemoval(id, access)
            if (current(access)) mutable.update { it.copy(removal = removal, error = false) }
        } catch (_: Exception) { if (current(access)) mutable.update { it.copy(error = true) } } }
    }
    fun dismissRemoval() { mutable.update { it.copy(removal = null) } }
    fun removeConfirmed() {
        val access = token ?: return
        val plan = mutable.value.removal ?: return
        mutable.update { it.copy(removal = null) }
        scope.launch { try {
            repository.remove(plan, access)
            if (current(access)) { mutable.update { it.copy(removed = true) }; refresh() }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (current(access)) mutable.update { it.copy(error = true) } } }
    }
    fun reactivate(id: String) {
        val access = token ?: return
        scope.launch { try { replace(repository.verify(id, access)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (current(access)) mutable.update { it.copy(error = true) } } }
    }
    fun retain(id: String) {
        val access = token ?: return
        scope.launch { try { replace(repository.mark(id, OfflinePackStatus.RETAINED, access)) }
            catch (_: Exception) { if (current(access)) mutable.update { it.copy(error = true) } } }
    }
    /** Called before explicit local purge; never destroys a cache or any business data. */
    fun invalidate() {
        generation++
        token = null
        activeScope = null
        planningJob?.cancel(); refreshJob?.cancel()
        transfers.values.toList().forEach(Job::cancel)
        transfers.clear(); desiredActive.clear()
        mutable.update { State(wifiOnly = it.wifiOnly, connected = it.connected, wifi = it.wifi) }
    }
    private fun replace(row: OfflineMapPackEntity) { mutable.update { it.copy(packs = it.packs.map { old -> if (old.id == row.id) row else old }) } }
    private fun current(access: CartographicScopeAuthority.Token) = token === access && repository.isCurrent(access)
    private fun networkAllowed() = mutable.value.connected && (!mutable.value.wifiOnly || mutable.value.wifi)
}
