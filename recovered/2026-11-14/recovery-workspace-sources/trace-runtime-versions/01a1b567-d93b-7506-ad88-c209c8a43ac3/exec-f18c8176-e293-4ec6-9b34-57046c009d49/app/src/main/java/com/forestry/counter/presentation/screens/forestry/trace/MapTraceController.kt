package com.forestry.counter.presentation.screens.forestry.trace

import android.content.Context
import android.location.Location
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import com.forestry.counter.data.location.trace.MapTraceForegroundService
import com.forestry.counter.domain.location.trace.*
import com.forestry.counter.domain.repository.*
import com.forestry.counter.domain.usecase.maps.MapDataScope
import com.quintessences.geosylva.cartography.ui.trace.TraceScreen
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** A process-wide controller. Opening a screen never adopts an old service or authority. */
class MapTraceController(
    context: Context,
    private val repository: CartographicTraceRepository,
    private val work: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val nanoTime: () -> Long = SystemClock::elapsedRealtimeNanos,
    private val source: TraceSourceControl = AndroidTraceSourceControl(context.applicationContext),
) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(MapTraceState())
    val state: StateFlow<MapTraceState> = mutable.asStateFlow()
    private val phone = MutableStateFlow<Location?>(null)
    val currentPhoneLocation: StateFlow<Location?> = phone.asStateFlow()
    private val sourceClaim = MutableStateFlow(false)
    /** Includes a pending durable start/resume, so the host can release its GPS subscription first. */
    val sourceActive: StateFlow<Boolean> = sourceClaim.asStateFlow()
    @Volatile private var access: CartographicMeasurementAccess? = null
    @Volatile private var sourceToken: TraceCaptureToken? = null
    private var recorder: SegmentedTraceRecorder? = null
    private var revision = 0L
    private var originId: String? = null
    private var authorityGeneration = 0L
    private var pendingSave: Pair<String, TraceCaptureSnapshot>? = null
    private var pendingDraft: Pair<String, TraceCaptureSnapshot>? = null
    private val bootId = runCatching { Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT).toString() }
        .getOrElse { UUID.randomUUID().toString() }

    init {
        work.launch {
            repository.accessChanges().collect {
                mutex.withLock {
                    val captured = access ?: return@withLock
                    if (!repository.isCurrent(captured) || repository.list(captured) is CartographicStoreResult.Failure) invalidate()
                }
            }
        }
        work.launch {
            while (isActive) {
                delay(1_000)
                mutex.withLock {
                    val captured = access
                    if (captured != null && !repository.isCurrent(captured)) { invalidate(); return@withLock }
                    val activeSource = sourceToken
                    if (activeSource != null && !source.permitted()) {
                        sourceFailureLocked(activeSource)
                        mutable.update { it.copy(issue = TraceRuntimeIssue.PERMISSION) }
                        return@withLock
                    }
                    val now = nanoTime()
                    mutable.update { it.copy(nowNanos = now) }
                    val r = recorder ?: return@withLock
                    val snapshot = r.snapshot(now)
                    val token = sourceToken
                    val last = snapshot.lastReceivedFix
                    if (token != null && (last == null || now - last.fixElapsedRealtimeNanos > snapshot.capturePolicy.maximumFixAgeNanos)) {
                        r.reportUnavailable(token, now, TraceRejectionReason.STALE_FIX)
                    }
                    val freshSnapshot = r.snapshot(now)
                    if (freshSnapshot.phase != TraceCapturePhase.RECORDING) phone.value = null
                    mutable.update { it.copy(snapshot = freshSnapshot, nowNanos = now) }
                }
            }
        }
    }

    fun open(scope: MapDataScope) = launchLocked {
        if (access != null && state.value.scope == scope && repository.isCurrent(requireNotNull(access))) {
            mutable.update { it.copy(visible = true) }
            refreshLibrary(requireNotNull(access))
            return@launchLocked
        }
        stopSource()
        recorder?.let { r -> if (access != null && repository.isCurrent(requireNotNull(access))) {
            val stopped = r.stop(nanoTime())
            persist(stopped)
        } }
        access = null
        recorder = null
        revision = 0
        originId = null
        pendingSave = null
        pendingDraft = null
        val generation = ++authorityGeneration
        mutable.value = MapTraceState(scope = scope, visible = true, busy = true)
        when (val result = repository.captureAccess(scope)) {
            is CartographicStoreResult.Success -> {
                if (generation != authorityGeneration) return@launchLocked
                access = result.value
                when (val records = repository.list(result.value)) {
                    is CartographicStoreResult.Success -> mutable.update { it.copy(busy = false, authorised = true, library = records.value) }
                    is CartographicStoreResult.Failure -> invalidate()
                }
            }
            is CartographicStoreResult.Failure -> mutable.update { it.copy(busy = false, issue = TraceRuntimeIssue.ACCESS) }
        }
    }

    /** Scope changes stop the old source even when another tool replaces the trace UI. */
    fun contextChanged(scope: MapDataScope) = launchLocked {
        if (state.value.scope != null && state.value.scope != scope) {
            stopSource()
            recorder?.let { r -> if (access?.let(repository::isCurrent) == true) persist(r.stop(nanoTime())) }
            access = null
            recorder = null
            ++authorityGeneration
            mutable.value = MapTraceState()
        }
    }

    /** Waits for in-flight writes, then invalidates without entering the purge gate again. */
    suspend fun stopForPurge() = mutex.withLock { invalidate() }

    fun hide() = launchLocked {
        if (sourceToken != null) {
            stopSource()
            recorder?.let { persist(it.stop(nanoTime())) }
            mutable.update { it.copy(screen = TraceScreen.REVIEW) }
        }
        mutable.update { it.copy(visible = false) }
    }
    fun setPreviewLocation(location: Location?) {
        if (sourceToken != null) return
        val fix = location?.let(::toFix)
        mutable.update { it.copy(previewFix = fix, nowNanos = nanoTime()) }
    }
    fun configure(policy: TraceCapturePolicy) = launchLocked {
        if (state.value.screen != TraceScreen.PREPARATION || recorder != null) return@launchLocked
        mutable.update { it.copy(policy = policy) }
    }

    fun start() = launchLocked {
        if (!ready() || recorder != null || state.value.busy) return@launchLocked
        if (!source.permitted()) { mutable.update { it.copy(issue = TraceRuntimeIssue.PERMISSION) }; return@launchLocked }
        val id = UUID.randomUUID().toString()
        val r = SegmentedTraceRecorder(id, state.value.policy)
        recorder = r
        revision = 0
        originId = null
        pendingSave = null
        val now = nanoTime()
        val token = r.start(now) ?: return@launchLocked
        sourceClaim.value = true
        mutable.update { it.copy(snapshot = r.snapshot(now), screen = TraceScreen.CAPTURE, name = "", selected = null, issue = null) }
        if (!persist(r.snapshot(now))) {
            stopSource()
            if (access == null) return@launchLocked
            val stopped = r.stop(nanoTime())
            mutable.update { it.copy(snapshot = stopped, screen = TraceScreen.REVIEW) }
            return@launchLocked
        }
        sourceToken = token
        if (!source.start(token)) sourceFailureLocked(token)
    }

    fun pause(expectedToken: TraceCaptureToken? = null) = launchLocked {
        if (expectedToken != null && expectedToken != sourceToken) return@launchLocked
        if (!ready() || pendingSave != null) return@launchLocked
        val r = recorder ?: return@launchLocked
        stopSource()
        val snapshot = r.pause(nanoTime())
        mutable.update { it.copy(snapshot = snapshot, screen = TraceScreen.CAPTURE) }
        persist(snapshot)
    }
    fun finish(expectedToken: TraceCaptureToken? = null) = launchLocked {
        if (expectedToken != null && expectedToken != sourceToken) return@launchLocked
        if (!ready() || pendingSave != null) return@launchLocked
        val r = recorder ?: return@launchLocked
        stopSource()
        val snapshot = r.stop(nanoTime())
        mutable.update { it.copy(snapshot = snapshot, screen = TraceScreen.REVIEW, issue = null) }
        persist(snapshot)
    }
    fun resume() = launchLocked {
        if (!ready() || pendingSave != null || state.value.selected?.saved == true) return@launchLocked
        if (!source.permitted()) { mutable.update { it.copy(issue = TraceRuntimeIssue.PERMISSION) }; return@launchLocked }
        val r = recorder ?: return@launchLocked
        val token = r.resume(nanoTime()) ?: return@launchLocked
        sourceClaim.value = true
        mutable.update { it.copy(snapshot = r.snapshot(nanoTime()), screen = TraceScreen.CAPTURE, issue = null) }
        if (!persist(r.snapshot(nanoTime()))) {
            stopSource()
            if (access == null) return@launchLocked
            val stopped = r.stop(nanoTime())
            mutable.update { it.copy(snapshot = stopped, screen = TraceScreen.REVIEW) }
            return@launchLocked
        }
        sourceToken = token
        if (!source.start(token)) sourceFailureLocked(token)
    }
    fun reviewSave() = launchLocked {
        if (!ready()) return@launchLocked
        val snapshot = recorder?.snapshot(nanoTime()) ?: return@launchLocked
        if (snapshot.phase == TraceCapturePhase.STOPPED_DRAFT && snapshot.portions.any { it.points.size >= 2 }) mutable.update { it.copy(screen = TraceScreen.SAVE) }
        else mutable.update { it.copy(issue = TraceRuntimeIssue.NO_POINTS) }
    }
    fun cancelSave() = launchLocked {
        if (pendingSave == null && !state.value.busy) mutable.update { it.copy(screen = TraceScreen.REVIEW) }
    }
    fun nameChanged(name: String) = launchLocked {
        if (pendingSave == null && !state.value.busy && state.value.screen == TraceScreen.SAVE) mutable.update { it.copy(name = name.take(121), issue = null) }
    }
    fun save() = launchLocked {
        if (!ready() || state.value.busy) return@launchLocked
        val r = recorder ?: return@launchLocked
        val captured = access ?: return@launchLocked
        val name = state.value.name.trim()
        if (pendingSave == null && (name.length > 120 || name.any { it.isISOControl() })) {
            mutable.update { it.copy(issue = TraceRuntimeIssue.INVALID_INPUT) }
            return@launchLocked
        }
        val pending = pendingSave ?: (state.value.name to r.snapshot(nanoTime())).also { pendingSave = it }
        if (!persist(pending.second)) { pendingSave = null; return@launchLocked }
        mutable.update { it.copy(busy = true, issue = null, saveUncertain = true) }
        when (val result = repository.save(r.sessionId, pending.first, pending.second, revision, originId, captured)) {
            is CartographicStoreResult.Success -> {
                // Success is displayed only after a separate authorised read confirms the committed identity.
                when (val read = repository.read(result.value.id, captured)) {
                    is CartographicStoreResult.Success -> if (read.value.saved && read.value.snapshot == result.value.snapshot) {
                        revision = read.value.revision
                        pendingSave = null
                        recorder = null
                        mutable.update { it.copy(busy = false, selected = read.value, snapshot = read.value.snapshot,
                            screen = TraceScreen.SAVED, saveUncertain = false, name = read.value.name.orEmpty()) }
                        refreshLibrary(captured)
                    } else mutable.update { it.copy(busy = false, issue = TraceRuntimeIssue.SAVE_UNCERTAIN) }
                    is CartographicStoreResult.Failure -> mutable.update { it.copy(busy = false, issue = TraceRuntimeIssue.SAVE_UNCERTAIN) }
                }
            }
            is CartographicStoreResult.Failure -> {
                if (result.reason in setOf(CartographicStoreError.ACCESS_CHANGED, CartographicStoreError.UNAVAILABLE)) invalidate()
                else if (result.reason == CartographicStoreError.INVALID_INPUT) {
                    pendingSave = null
                    mutable.update { it.copy(busy = false, saveUncertain = false, issue = TraceRuntimeIssue.INVALID_INPUT) }
                } else mutable.update { it.copy(busy = false, issue = TraceRuntimeIssue.SAVE_UNCERTAIN) }
            }
        }
    }

    /** Explicit recovery/read: no background GPS begins, even if the durable phase was recording. */
    fun selectDocument(id: String) = launchLocked {
        if (!ready() || sourceToken != null || pendingSave != null) return@launchLocked
        val pendingRecorder = recorder
        if (pendingDraft != null && pendingRecorder != null && !persist(pendingRecorder.snapshot(nanoTime()))) return@launchLocked
        val captured = access ?: return@launchLocked
        when (val read = repository.read(id, captured)) {
            is CartographicStoreResult.Success -> {
                val doc = read.value
                originId = doc.sourceTraceId
                revision = doc.revision
                if (doc.saved) {
                    recorder = null
                    mutable.update { it.copy(selected = doc, snapshot = doc.snapshot, name = doc.name.orEmpty(), screen = TraceScreen.SAVED, issue = null) }
                } else {
                    recorder = SegmentedTraceRecorder.restoreStopped(doc.snapshot)
                    val snapshot = requireNotNull(recorder).snapshot(nanoTime())
                    mutable.update { it.copy(selected = null, snapshot = snapshot, name = doc.name.orEmpty(), screen = TraceScreen.REVIEW, issue = TraceRuntimeIssue.RECOVERED) }
                    persist(snapshot)
                }
            }
            is CartographicStoreResult.Failure -> mutable.update { it.copy(issue = TraceRuntimeIssue.STORE) }
        }
    }
    fun newTrace() = launchLocked {
        if (!ready() || sourceToken != null || pendingSave != null) return@launchLocked
        val stopped = recorder?.stop(nanoTime())
        if (stopped != null && !persist(stopped)) return@launchLocked
        recorder = null
        revision = 0
        originId = null
        mutable.update { it.copy(screen = TraceScreen.PREPARATION, selected = null, snapshot = null, name = "", issue = null) }
    }
    fun duplicate() = launchLocked {
        if (!ready() || state.value.busy) return@launchLocked
        val captured = access ?: return@launchLocked
        val selected = state.value.selected ?: return@launchLocked
        val read = repository.read(selected.id, captured) as? CartographicStoreResult.Success ?: return@launchLocked
        val original = read.value
        if (!original.saved) return@launchLocked
        val id = UUID.randomUUID().toString()
        recorder = SegmentedTraceRecorder.restoreStopped(original.snapshot, id)
        revision = 0
        originId = original.id
        mutable.update { it.copy(selected = null, snapshot = requireNotNull(recorder).snapshot(nanoTime()),
            name = original.name.orEmpty(), screen = TraceScreen.SAVE, issue = null) }
        persist(requireNotNull(recorder).snapshot(nanoTime()))
    }
    fun details() = launchLocked { if (ready() && state.value.snapshot != null) mutable.update { it.copy(screen = TraceScreen.DETAILS) } }
    fun backDetails() = launchLocked { mutable.update { it.copy(screen = if (it.selected?.saved == true) TraceScreen.SAVED else TraceScreen.REVIEW) } }
    fun selectPortion(id: String) = launchLocked {
        if (state.value.snapshot?.portions?.any { it.id == id } == true) mutable.update { it.copy(selectedPortionId = id) }
    }

    /** Called by the source with the exact token captured when its callback was installed. */
    fun acceptLocation(token: TraceCaptureToken, location: Location) = launchLocked {
        acceptFixLocked(token, toFix(location), location)
    }
    /** Internal deterministic boundary for source-contract tests; production uses Android Location. */
    internal fun acceptFix(token: TraceCaptureToken, fix: TraceFix) = launchLocked {
        acceptFixLocked(token, fix, null)
    }
    private suspend fun acceptFixLocked(token: TraceCaptureToken, fix: TraceFix, location: Location?) {
        if (token != sourceToken || !ready()) return
        val r = recorder ?: return
        val now = nanoTime()
        val result = r.accept(token, fix, now)
        if (result !is TraceFixResult.Ignored && !persist(result.snapshot)) {
            stopSource()
            if (access == null) return
            val stopped = r.stop(nanoTime())
            mutable.update { it.copy(snapshot = stopped, screen = TraceScreen.REVIEW, issue = TraceRuntimeIssue.STORE) }
        } else {
            phone.value = if (location != null && (result is TraceFixResult.Accepted || result is TraceFixResult.Ignored && result.reason == TraceIgnoredReason.BELOW_MINIMUM_DISTANCE)) Location(location) else null
            mutable.update { it.copy(snapshot = result.snapshot, nowNanos = now, issue = null) }
        }
    }
    fun sourceUnavailable(token: TraceCaptureToken) = launchLocked {
        if (token != sourceToken || !ready()) return@launchLocked
        val result = recorder?.reportUnavailable(token, nanoTime()) ?: return@launchLocked
        phone.value = null
        mutable.update { it.copy(snapshot = result.snapshot) }
        if (!persist(result.snapshot)) {
            stopSource()
            if (access != null) recorder?.let { r -> mutable.update { it.copy(snapshot = r.stop(nanoTime()), screen = TraceScreen.REVIEW, issue = TraceRuntimeIssue.STORE) } }
        }
    }
    fun sourceStopped(token: TraceCaptureToken) = launchLocked {
        if (token == sourceToken) sourceFailureLocked(token)
    }
    fun sourceIsCurrent(token: TraceCaptureToken): Boolean = sourceToken == token && access?.let(repository::isCurrent) == true
    fun currentSourceToken(): TraceCaptureToken? = sourceToken?.takeIf(::sourceIsCurrent)
    fun authorityIsCurrent(): Boolean = access?.let(repository::isCurrent) == true
    fun needsLocationPermission(): Boolean = !source.permitted()
    fun dismissIssue() = launchLocked { mutable.update { it.copy(issue = null) } }
    fun refreshLibrary() = launchLocked { access?.let { refreshLibrary(it) } }

    suspend fun exportPreview(id: String, expectedRevision: Long): CartographicStoreResult<String> = mutex.withLock {
        val captured = access ?: return@withLock CartographicStoreResult.Failure(CartographicStoreError.ACCESS_CHANGED)
        when (val read = repository.read(id, captured)) {
            is CartographicStoreResult.Success -> if (read.value.saved && read.value.revision == expectedRevision)
                CartographicStoreResult.Success(TraceGeoJsonExport.encode(read.value)) else CartographicStoreResult.Failure(CartographicStoreError.CONFLICT)
            is CartographicStoreResult.Failure -> read
        }
    }

    private suspend fun sourceFailureLocked(token: TraceCaptureToken) {
        if (token != sourceToken) return
        stopSource()
        recorder?.let { r ->
            val stopped = r.stop(nanoTime())
            mutable.update { it.copy(snapshot = stopped, screen = TraceScreen.REVIEW, issue = TraceRuntimeIssue.INTERRUPTED) }
            persist(stopped)
        }
    }
    private fun stopSource() {
        val token = sourceToken
        sourceToken = null
        phone.value = null
        try { if (token != null) source.stop(token) }
        finally { sourceClaim.value = false }
    }
    private suspend fun ready(): Boolean {
        val captured = access ?: return false
        if (!repository.isCurrent(captured)) { invalidate(); return false }
        return state.value.authorised
    }
    private suspend fun persist(snapshot: TraceCaptureSnapshot): Boolean {
        val captured = access ?: return false
        val previousPending = pendingDraft
        if (previousPending != null) {
            when (val confirmed = repository.writeDraft(previousPending.second.sessionId, previousPending.first, previousPending.second, revision, originId, captured)) {
                is CartographicStoreResult.Success -> { revision = confirmed.value.revision; pendingDraft = null }
                is CartographicStoreResult.Failure -> {
                    if (confirmed.reason in setOf(CartographicStoreError.ACCESS_CHANGED, CartographicStoreError.UNAVAILABLE)) invalidate()
                    else mutable.update { it.copy(issue = TraceRuntimeIssue.STORE) }
                    return false
                }
            }
        }
        val pending = state.value.name to snapshot
        pendingDraft = pending
        when (val stored = repository.writeDraft(snapshot.sessionId, pending.first, snapshot, revision, originId, captured)) {
            is CartographicStoreResult.Success -> { revision = stored.value.revision; pendingDraft = null; return true }
            is CartographicStoreResult.Failure -> {
                if (stored.reason in setOf(CartographicStoreError.ACCESS_CHANGED, CartographicStoreError.UNAVAILABLE)) invalidate()
                else mutable.update { it.copy(issue = TraceRuntimeIssue.STORE) }
                return false
            }
        }
    }
    private suspend fun refreshLibrary(captured: CartographicMeasurementAccess) {
        val result = repository.list(captured)
        if (result is CartographicStoreResult.Success) mutable.update { it.copy(library = result.value) }
    }
    private fun invalidate() {
        stopSource()
        access = null
        recorder = null
        pendingSave = null
        pendingDraft = null
        revision = 0
        originId = null
        ++authorityGeneration
        mutable.value = MapTraceState(scope = state.value.scope, visible = state.value.visible, issue = TraceRuntimeIssue.ACCESS)
    }
    private fun launchLocked(block: suspend () -> Unit) { work.launch { mutex.withLock { block() } } }
    private fun toFix(location: Location): TraceFix {
        @Suppress("DEPRECATION")
        val mock = if (Build.VERSION.SDK_INT >= 31) location.isMock else location.isFromMockProvider
        return TraceFix(location.latitude, location.longitude,
            location.accuracy.toDouble().takeIf { location.hasAccuracy() }, location.elapsedRealtimeNanos,
            TracePositionSource.PHONE_LOCATION, location.provider, location.time.takeIf { it > 0 },
            location.altitude.takeIf { location.hasAltitude() }, mock, bootId)
    }
}

data class MapTraceState(
    val scope: MapDataScope? = null,
    val visible: Boolean = false,
    val authorised: Boolean = false,
    val screen: TraceScreen = TraceScreen.PREPARATION,
    val snapshot: TraceCaptureSnapshot? = null,
    val selected: CartographicTraceDocument? = null,
    val library: List<CartographicTraceDocument> = emptyList(),
    val name: String = "",
    val policy: TraceCapturePolicy = TraceCapturePolicy(10.0, 15_000_000_000L, 2.0, 20_000_000_000L),
    val previewFix: TraceFix? = null,
    val nowNanos: Long = 0,
    val selectedPortionId: String? = null,
    val busy: Boolean = false,
    val saveUncertain: Boolean = false,
    val issue: TraceRuntimeIssue? = null,
)
enum class TraceRuntimeIssue { ACCESS, PERMISSION, INTERRUPTED, RECOVERED, STORE, SAVE_UNCERTAIN, INVALID_INPUT, NO_POINTS }
interface TraceSourceControl {
    fun permitted(): Boolean
    fun start(token: TraceCaptureToken): Boolean
    fun stop(token: TraceCaptureToken)
}
private class AndroidTraceSourceControl(private val context: Context) : TraceSourceControl {
    override fun permitted() = MapTraceForegroundService.hasPermission(context)
    override fun start(token: TraceCaptureToken) = MapTraceForegroundService.start(context, token)
    override fun stop(token: TraceCaptureToken) = MapTraceForegroundService.stop(context, token)
}
