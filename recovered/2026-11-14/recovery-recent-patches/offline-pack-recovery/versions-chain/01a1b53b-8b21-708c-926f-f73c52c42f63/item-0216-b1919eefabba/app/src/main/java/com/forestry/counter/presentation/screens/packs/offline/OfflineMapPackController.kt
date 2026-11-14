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
@OptIn(kotlinx.coroutines.FlowPreview::class)
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
        val removal: List<OfflinePackRemoval> = emptyList(),
        val running: Set<String> = emptySet(),
        val progress: Map<String, Pair<OfflineTransferStage, OfflineResourceProgress>> = emptyMap(),
        val wifiOnly: Boolean = true,
        val connected: Boolean = false,
        val wifi: Boolean = false,
        val freeBytes: Long? = null,
        val tileStoreBytes: Long? = null,
        val error: Boolean = false,
        val removed: Boolean = false,
    )
    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable.asStateFlow()
    @Volatile private var token: CartographicScopeAuthority.Token? = null
    private var generation = 0L
    private var planningJob: Job? = null
    private val transfers = mutableMapOf<String, Job>()
    private val stopping = mutableMapOf<String, Job>()
    private val desiredActive = mutableSetOf<String>()
    private var refreshJob: Job? = null
    private var activeScope: MapDataScope? = null
    private val actionJobs = mutableSetOf<Job>()
    init {
        scope.launch { repository.accessChanges().collect {
            val access = token
            if (access != null && !repository.isCurrent(access)) invalidate()
        } }
        scope.launch {
            mutable.map { it.progress }.distinctUntilChanged().sample(1_000).collect { progress ->
                val access = token
                if (access != null && current(access)) {
                    // Tracked separately from this permanent observer so purge can join every write.
                    actionLaunch {
                        for ((id, value) in progress) if (current(access) && id in transfers) {
                            try { repository.saveProgress(id, value.first, value.second, access) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { if (current(access)) mutable.update { it.copy(error = true) } }
                        }
                    }.join()
                }
            }
        }
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
        actionLaunch {
            when (val captured = repository.capture(dataScope)) {
                is CartographicStoreResult.Success -> if (version == generation) {
                    token = captured.value
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
        refreshJob = actionLaunch {
            try {
                // Never convert an active transfer to a restored pause during an inventory refresh.
                if (transfers.isEmpty()) {
                    val packs = repository.list(access)
                    val tileBytes = repository.tileStoreBytes()
                    if (current(access)) mutable.update { it.copy(packs = packs, freeBytes = repository.freeBytes(), tileStoreBytes = tileBytes, error = false) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (current(access)) mutable.update { it.copy(error = true) } }
        }
    }
    fun prepare(input: OfflinePackInput) {
        val access = token ?: return
        planningJob?.cancel()
        mutable.update { it.copy(planning = true, plan = null, error = false, removed = false) }
        planningJob = actionLaunch {
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
        actionLaunch {
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
        job?.let { stopping[id] = it; it.cancel() }
        mutable.update { it.copy(running = it.running - id) }
        actionLaunch {
            job?.join()
            if (job != null && stopping[id] === job) stopping.remove(id)
            mutable.value.progress[id]?.let { (stage, progress) -> if (current(access)) runCatching { repository.saveProgress(id, stage, progress, access) } }
            if (current(access) && id !in transfers && (waiting && id in desiredActive || !waiting && id !in desiredActive)) {
                runCatching { repository.mark(id, if (waiting) OfflinePackStatus.WAITING_WIFI else OfflinePackStatus.PAUSED, access) }
                    .onSuccess(::replace)
            }
            if (current(access) && networkAllowed() && id in desiredActive) start(id)
        }
    }
    private fun start(id: String) {
        val access = token ?: return
        if (id in transfers || id in stopping || id !in desiredActive || !networkAllowed()) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val verified = repository.transfer(id, access, mutable.value.wifiOnly) { stage, progress ->
                    // SDK tile callbacks run on its worker: StateFlow is safe; mutations remain in this scope.
                    if (current(access)) mutable.update { it.copy(progress = it.progress + (id to (stage to progress))) }
                }
                if (current(access)) replace(verified)
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
    fun prepareRemoval(id: String) = prepareRemoval(setOf(id))
    fun prepareRemoval(ids: Set<String>) {
        val access = token ?: return
        if (ids.isEmpty()) return
        desiredActive.removeAll(ids)
        actionLaunch { try {
            val removal = ids.sorted().map { repository.prepareRemoval(it, access) }
            if (current(access)) mutable.update { it.copy(removal = removal, error = false) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (current(access)) mutable.update { it.copy(error = true) } } }
    }
    fun dismissRemoval() { mutable.update { it.copy(removal = emptyList()) } }
    fun removeConfirmed() {
        val access = token ?: return
        val plans = mutable.value.removal.takeIf { it.isNotEmpty() } ?: return
        mutable.update { it.copy(removal = emptyList()) }
        actionLaunch { try {
            // Revalidate the entire reviewed selection before the first native removal.
            require(plans.map { repository.prepareRemoval(it.id, access) } == plans)
            for (plan in plans) repository.remove(plan, access)
            if (current(access)) mutable.update { it.copy(removed = true) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (current(access)) mutable.update { it.copy(error = true) } }
        finally { if (current(access)) refresh() } }
    }
    fun open(id: String, onReady: () -> Unit) {
        val access = token ?: return
        actionLaunch { try {
            val checked = repository.verify(id, access)
            if (current(access)) {
                replace(checked)
                if (checked.status == OfflinePackStatus.READY.name) onReady()
                else mutable.update { it.copy(error = true) }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (current(access)) mutable.update { it.copy(error = true) } } }
    }
    fun reactivate(id: String) {
        val access = token ?: return
        actionLaunch { try { replace(repository.verify(id, access)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (current(access)) mutable.update { it.copy(error = true) } } }
    }
    fun retain(id: String) {
        val access = token ?: return
        actionLaunch { try { replace(repository.mark(id, OfflinePackStatus.RETAINED, access)) }
            catch (_: Exception) { if (current(access)) mutable.update { it.copy(error = true) } } }
    }
    suspend fun stopForPurge() = quiesce()
    /** Purge sequencing: reject callbacks immediately, then await every known native operation. */
    suspend fun quiesce() = withContext(Dispatchers.Main.immediate) {
        val pending = (transfers.values + stopping.values + actionJobs + listOfNotNull(planningJob, refreshJob)).distinct()
        invalidate()
        pending.joinAll()
    }
    /** Called before explicit local purge; never destroys a cache or any business data. */
    fun invalidate() {
        generation++
        token = null
        activeScope = null
        planningJob?.cancel(); refreshJob?.cancel()
        actionJobs.toList().forEach(Job::cancel); actionJobs.clear()
        transfers.values.toList().forEach(Job::cancel)
        transfers.clear(); stopping.clear(); desiredActive.clear()
        mutable.update { State(wifiOnly = it.wifiOnly, connected = it.connected, wifi = it.wifi) }
    }
    private fun actionLaunch(block: suspend CoroutineScope.() -> Unit): Job {
        val job = scope.launch(start = CoroutineStart.LAZY, block = block)
        actionJobs += job
        job.invokeOnCompletion { actionJobs -= job }
        job.start()
        return job
    }
    private fun replace(row: OfflineMapPackEntity) { mutable.update { it.copy(packs = it.packs.map { old -> if (old.id == row.id) row else old }) } }
    private fun current(access: CartographicScopeAuthority.Token) = token === access && repository.isCurrent(access)
    private fun networkAllowed() = mutable.value.connected && (!mutable.value.wifiOnly || mutable.value.wifi)
}
