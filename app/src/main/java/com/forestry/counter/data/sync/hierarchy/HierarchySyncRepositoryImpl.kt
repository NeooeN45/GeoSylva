package com.forestry.counter.data.sync.hierarchy

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.forestry.counter.data.local.dao.HierarchySyncDao
import com.forestry.counter.data.local.entity.HierarchySyncAssociationEntity
import com.forestry.counter.data.local.entity.HierarchySyncOperationEntity
import com.forestry.counter.data.remote.identity.IdentitySessionSnapshot
import com.forestry.counter.data.remote.identity.IdentitySessionStore
import com.forestry.counter.data.remote.identity.JwtSessionDecoder
import com.forestry.counter.domain.model.*
import com.forestry.counter.domain.repository.HierarchySyncRepository
import com.forestry.counter.domain.repository.IdentityRepository
import com.forestry.counter.domain.repository.IdentitySessionGuard
import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
internal class HierarchySyncRepositoryImpl(
    context: Context,
    private val dao: HierarchySyncDao,
    private val identity: IdentityRepository,
    private val sessionStore: IdentitySessionStore,
    private val api: HierarchySyncApiService?,
    private val apiOrigin: String?,
    private val scheduleWork: (() -> Unit)? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : HierarchySyncRepository {
    private val appContext = context.applicationContext
    private val recovery = linkedMapOf<String, RecoveryBinding>()

    override fun observeSummary(): Flow<HierarchySyncSummary> = identity.session.flatMapLatest { session ->
        if (session == null) flowOf(HierarchySyncSummary()) else combine(
            dao.observeAssociations(session.accountId), dao.observeOperations(session.accountId),
        ) { associations, operations ->
            val states = operations.associate { it.forestId to it.state }.toMutableMap()
            associations.filter { it.lastErrorCode != null }.forEach { if (states[it.forestId] != CONFLICT) states[it.forestId] = ERROR }
            HierarchySyncSummary(
                activated = associations.size, pending = states.values.count { it == PENDING },
                synced = associations.count { it.serverVersion != null && states[it.forestId] == SYNCED },
                conflicts = states.values.count { it == CONFLICT }, errors = states.values.count { it == ERROR },
            )
        }
    }

    override suspend fun loadSelection(): Result<HierarchySyncSelection> = attempt {
        val bound = captureSession()
        val candidates = mutableListOf<HierarchySyncCandidate>()
        dao.transaction {
            requireCurrent(bound)
            dao.forests().filter { it.deletedAt == null }.forEach { forest ->
                val association = dao.association(forest.foretId)
                val foreign = association?.accountId?.let { it != bound.accountId } == true ||
                    dao.parcelAccounts(forest.foretId).any { it != bound.accountId }
                requireCurrent(bound)
                if (foreign) candidates += HierarchySyncCandidate(forest.foretId, "", 0, 0, 0, 0, false, true, false)
                else {
                    val counts = dao.counts(forest.foretId)
                    val tail = dao.tail(forest.foretId, bound.accountId)
                    candidates += HierarchySyncCandidate(
                        forest.foretId, forest.nom, counts.parcels, counts.plots, counts.sessions, counts.trees,
                        association != null, false, counts.localPhotos > 0, tail?.state, association?.lastErrorCode ?: tail?.lastErrorCode,
                    )
                }
                requireCurrent(bound)
            }
        }
        HierarchySyncSelection(bound.accountId, bound.guard, candidates)
    }

    override suspend fun activateSelected(selection: HierarchySyncSelection, forestIds: Set<String>): Result<Int> = attempt {
        val bound = captureSession(selection.accountId, selection.sessionGuard)
        check(forestIds.all { id -> selection.forests.any { it.forestId == id && !it.blockedByOtherAccount } }) { "FOREST_NOT_SELECTABLE" }
        check(!apiOrigin.isNullOrBlank() && api != null) { "HIERARCHY_API_NOT_CONFIGURED" }
        var inserted = 0
        dao.transaction {
            requireCurrent(bound)
            forestIds.sorted().forEach { id ->
                val association = dao.association(id)
                check(association == null || association.accountId == bound.accountId) { "FOREST_LINKED_TO_ANOTHER_ACCOUNT" }
                check(dao.parcelAccounts(id).none { it != bound.accountId }) { "PARCEL_LINKED_TO_ANOTHER_ACCOUNT" }
                if (association == null) {
                    val captured = captureHierarchy(dao, id)
                    check(captured.dossier.forest.deletedAt == null) { "FOREST_NOT_AVAILABLE" }
                    requireCurrent(bound)
                    val consent = HierarchySyncAssociationEntity(id, bound.accountId, checkNotNull(apiOrigin), clock(), null, null, null, null)
                    dao.insertAssociation(consent)
                    appendCapture(consent, captured)
                    inserted += 1
                }
                requireCurrent(bound)
            }
        }
        if (inserted > 0) schedule()
        inserted
    }

    override suspend fun captureActivated(expected: IdentitySessionGuard?): Int {
        if (identity.session.value == null) return 0
        val bound = if (expected == null) captureSession() else captureSession(expected.accountId, expected)
        val associations = dao.associations(bound.accountId)
        requireCurrent(bound)
        var appended = 0
        for (association in associations) {
            try {
                dao.transaction {
                    requireCurrent(bound)
                    val current = requireAssociation(bound, association.forestId)
                    val captured = captureHierarchy(dao, association.forestId)
                    requireCurrent(bound)
                    if (current.lastCapturedHash != captured.fingerprint) {
                        appendCapture(current, captured)
                        appended += 1
                    }
                    requireCurrent(bound)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (expired: SessionExpired) { throw expired }
            catch (error: Exception) {
                requireCurrent(bound)
                dao.captureError(association.forestId, bound.accountId, error.message?.take(100) ?: "CAPTURE_FAILED")
            }
        }
        if (appended > 0 || dao.pendingCount(bound.accountId) > 0) schedule()
        return appended
    }

    private suspend fun appendCapture(
        association: HierarchySyncAssociationEntity, captured: HierarchyCapture,
        explicitBase: Int? = null, isRecovery: Boolean = false, remoteJson: String? = null,
    ) {
        val tail = if (isRecovery) null else dao.tail(association.forestId, association.accountId)
        val base = if (isRecovery) explicitBase else if (tail == null || tail.state == SYNCED) {
            listOfNotNull(association.serverVersion, tail?.ackVersion).maxOrNull()
        } else tail.expectedServerVersion
        check(base == null || base in 1 until Int.MAX_VALUE) { "SERVER_VERSION_EXHAUSTED" }
        val state = when {
            association.apiOrigin != apiOrigin -> "BLOCKED_API_ORIGIN"
            tail?.state == CONFLICT || tail?.state?.startsWith("BLOCKED_") == true ||
                (tail?.state == ERROR && tail.nextAttemptAt == Long.MAX_VALUE) -> "BLOCKED_PREDECESSOR"
            else -> PENDING
        }
        val now = clock()
        val operationId = newId()
        val mutation = HierarchyMutationDto(operationId, base, Instant.ofEpochMilli(now).toString(), captured.dossier)
        val request = hierarchyJson.encodeToString(mutation)
        check(request.toByteArray(Charsets.UTF_8).size <= HIERARCHY_MAX_BYTES) { "DOSSIER_TOO_LARGE" }
        val predecessor = tail?.takeIf { it.state != SYNCED || it.ackVersion == base }?.operationId
        dao.append(HierarchySyncOperationEntity(
            operationId = operationId, forestId = association.forestId, accountId = association.accountId,
            apiOrigin = association.apiOrigin, requestJson = request, requestSha256 = hierarchySha256(request),
            dossierSha256 = captured.fingerprint, baseVersion = base, expectedServerVersion = (base ?: 0) + 1,
            predecessorOperationId = predecessor, createdAt = now, containsLocalPhotos = captured.containsLocalPhotos,
            recoveryRemoteJson = remoteJson, state = state, nextAttemptAt = now,
        ))
        check(dao.recordCapture(association.forestId, association.accountId, captured.fingerprint) == 1)
    }

    override suspend fun processPending(expected: IdentitySessionGuard?): HierarchySyncProcessResult = try { processGuarded(expected) }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: SessionExpired) { HierarchySyncProcessResult(shouldRetry = true) }

    private suspend fun processGuarded(expected: IdentitySessionGuard?): HierarchySyncProcessResult {
        if (identity.session.value == null) return HierarchySyncProcessResult()
        val scope = SessionScope(if (expected == null) captureSession() else captureSession(expected.accountId, expected))
        val ready = dao.ready(scope.bound.accountId, clock(), clock() - LEASE_MS, 1)
        requireCurrent(scope.bound)
        var synced = 0; var conflicts = 0; var errors = 0; var shouldRetry = false
        for (operation in ready) {
            val lease = newId()
            requireCurrent(scope.bound)
            if (dao.claim(operation.operationId, scope.bound.accountId, operation.state, operation.attemptId, operation.lastAttemptAt, lease, clock()) != 1) continue
            val claimed = operation.copy(state = SYNCING, attemptId = lease)
            try {
                requireCurrent(scope.bound)
                check(claimed.accountId == scope.bound.accountId && claimed.apiOrigin == apiOrigin && api != null) { "API_ORIGIN_UNAVAILABLE_OR_CHANGED" }
                check(hierarchySha256(claimed.requestJson) == claimed.requestSha256) { "FROZEN_REQUEST_HASH_MISMATCH" }
                requireAssociation(scope.bound, claimed.forestId)
                val current = dao.operation(claimed.operationId)
                requireCurrent(scope.bound)
                if (current == null || current.state != SYNCING || current.attemptId != lease) continue
                check(current.requestJson == claimed.requestJson && current.requestSha256 == claimed.requestSha256) { "FROZEN_REQUEST_CHANGED" }
                val service = checkNotNull(api)
                // A previous attempt may have committed before its response was lost. Retrieve
                // that immutable server receipt first; only a real 404 permits exact replay.
                val recovered = if (operation.retryCount > 0 || operation.state == SYNCING) {
                    authenticated(scope) { auth -> service.receipt(auth, claimed.operationId) }
                } else null
                val response = if (recovered == null || recovered.code() == 404) {
                    authenticated(scope) { auth -> service.publish(auth, claimed.forestId, claimed.requestJson.toRequestBody(JSON_MEDIA)) }
                } else recovered
                when {
                    response.isSuccessful && response.body()?.status == "conflict" -> {
                        val body = checkNotNull(response.body())
                        check(validReceipt(claimed, body)) { "INVALID_SERVER_RECEIPT" }
                        fail(claimed, CONFLICT, Long.MAX_VALUE, body.reason ?: "VERSION_CONFLICT", hierarchyJson.encodeToString(body))
                        conflicts += 1
                    }
                    response.isSuccessful -> {
                        val body = response.body()
                        check(body != null && body.status == "accepted" && validReceipt(claimed, body) &&
                            body.serverVersion == claimed.expectedServerVersion) { "INVALID_SERVER_RECEIPT" }
                        dao.transaction {
                            requireCurrent(scope.bound)
                            requireAssociation(scope.bound, claimed.forestId)
                            if (dao.acknowledge(claimed.operationId, claimed.accountId, lease, claimed.expectedServerVersion,
                                    body.payloadSha256, hierarchyJson.encodeToString(body)) == 1) {
                                dao.checkpoint(claimed.forestId, claimed.accountId, claimed.expectedServerVersion, clock())
                                requireCurrent(scope.bound)
                                synced += 1
                            }
                        }
                    }
                    response.code() == 409 -> {
                        val body = response.errorBody()?.string()?.let { raw ->
                            runCatching { hierarchyJson.decodeFromString<HierarchyReceiptDto>(raw) }.getOrNull()
                        }
                        if (body != null && body.status == "conflict" && validReceipt(claimed, body)) {
                            fail(claimed, CONFLICT, Long.MAX_VALUE, body.reason ?: "VERSION_CONFLICT", hierarchyJson.encodeToString(body))
                            conflicts += 1
                        } else { fail(claimed, ERROR, Long.MAX_VALUE, "OPERATION_ID_REUSE_OR_INVALID_CONFLICT"); errors += 1 }
                    }
                    response.code() in listOf(408, 425, 429) || response.code() >= 500 -> {
                        retry(claimed, "HTTP_${response.code()}"); errors += 1; shouldRetry = true
                    }
                    else -> { fail(claimed, ERROR, Long.MAX_VALUE, "HTTP_${response.code()}"); errors += 1 }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: SessionExpired) { retry(claimed, "SESSION_CHANGED"); shouldRetry = true; break }
            catch (_: IOException) { retry(claimed, "NETWORK_UNAVAILABLE"); errors += 1; shouldRetry = true }
            catch (error: Exception) { fail(claimed, ERROR, Long.MAX_VALUE, error.message?.take(100) ?: "INVALID_RESPONSE"); errors += 1 }
        }
        if (dao.pendingCount(scope.bound.accountId) > 0) shouldRetry = true
        return HierarchySyncProcessResult(synced, conflicts, errors, shouldRetry)
    }

    private fun validReceipt(operation: HierarchySyncOperationEntity, receipt: HierarchyReceiptDto): Boolean =
        receipt.operationId == operation.operationId && receipt.forestId == operation.forestId &&
            receipt.payloadSha256.matches(Regex("[a-f0-9]{64}")) &&
            receipt.requestSha256 == operation.requestSha256

    private suspend fun retry(operation: HierarchySyncOperationEntity, code: String) {
        val delay = (30_000L * (1L shl (operation.retryCount + 1).coerceAtMost(7))).coerceAtMost(3_600_000L)
        fail(operation, ERROR, clock() + delay, code, retryCount = operation.retryCount + 1)
    }

    private suspend fun fail(operation: HierarchySyncOperationEntity, state: String, deadline: Long, code: String,
        receipt: String? = null, retryCount: Int = operation.retryCount) {
        dao.transaction {
            if (dao.fail(operation.operationId, operation.accountId, checkNotNull(operation.attemptId), state, retryCount,
                    deadline, code, receipt) == 1 && (state == CONFLICT || deadline == Long.MAX_VALUE)) {
                dao.blockFollowing(operation.forestId, operation.accountId, operation.sequence, code)
            }
        }
    }

    override suspend fun listRemote(): Result<List<HierarchyRemoteMetadata>> = attempt {
        val scope = SessionScope(captureSession())
        val service = checkNotNull(api) { "HIERARCHY_API_NOT_CONFIGURED" }
        val items = linkedMapOf<String, HierarchyRemoteMetadata>()
        var total: Int? = null; var size: Int? = null
        for (page in 1..1000) {
            val response = authenticated(scope) { service.list(it, page, 100) }
            check(response.isSuccessful) { "HTTP_${response.code()}" }
            val body = checkNotNull(response.body())
            check(body.page == page && body.size in 1..100 && body.total >= 0 && body.items.size <= body.size)
            check((total == null || total == body.total) && (size == null || size == body.size))
            total = body.total; size = body.size
            body.items.forEach {
                check(it.serverVersion > 0 && items.put(it.forestId, HierarchyRemoteMetadata(it.forestId, it.serverVersion, it.name)) == null)
            }
            if (page.toLong() * body.size >= body.total) {
                check(items.size == body.total) { "INCOMPLETE_REMOTE_LIST" }
                return@attempt items.values.toList()
            }
            check(body.items.size == body.size) { "INCOMPLETE_REMOTE_LIST" }
        }
        error("REMOTE_PAGE_LIMIT_REACHED")
    }

    override suspend fun getRemote(forestId: String): Result<HierarchyRemoteDossier?> = attempt {
        fetchRemote(SessionScope(captureSession()), forestId)
    }

    private suspend fun fetchRemote(scope: SessionScope, forestId: String): HierarchyRemoteDossier? {
        val service = checkNotNull(api) { "HIERARCHY_API_NOT_CONFIGURED" }
        val response = authenticated(scope) { service.get(it, forestId) }
        if (response.code() == 404) return null
        check(response.isSuccessful) { "HTTP_${response.code()}" }
        val body = checkNotNull(response.body())
        check(body.forestId == forestId && body.serverVersion > 0)
        val json = body.dossier.toString()
        check(json.toByteArray(Charsets.UTF_8).size <= HIERARCHY_MAX_BYTES) { "REMOTE_DOSSIER_TOO_LARGE" }
        return HierarchyRemoteDossier(forestId, body.serverVersion, body.serverUpdatedAt, json)
    }

    override suspend fun prepareRecovery(forestId: String): Result<HierarchyRecoveryPreview> = attempt {
        val scope = SessionScope(captureSession())
        var captured: HierarchyCapture? = null
        var tailId: String? = null
        dao.transaction {
            requireAssociation(scope.bound, forestId)
            captured = captureHierarchy(dao, forestId)
            tailId = dao.tail(forestId, scope.bound.accountId)?.operationId
            requireCurrent(scope.bound)
        }
        val remote = fetchRemote(scope, forestId)
        requireCurrent(scope.bound)
        val snapshot = checkNotNull(captured)
        val token = newId()
        synchronized(recovery) {
            while (recovery.size >= 20) recovery.remove(recovery.keys.first())
            recovery[token] = RecoveryBinding(scope.bound, snapshot.fingerprint, tailId, remote)
        }
        HierarchyRecoveryPreview(token, forestId, snapshot.dossier.forest.nom, remote, snapshot.containsLocalPhotos)
    }

    override suspend fun confirmRepublishLocal(preview: HierarchyRecoveryPreview): Result<Unit> = attempt {
        val binding = synchronized(recovery) { recovery[preview.token] } ?: error("RECOVERY_PREVIEW_EXPIRED")
        val bound = captureSession(binding.bound.accountId, binding.bound.guard)
        dao.transaction {
            val association = requireAssociation(bound, preview.forestId)
            check(association.apiOrigin == apiOrigin) { "API_ORIGIN_CHANGED" }
            val captured = captureHierarchy(dao, preview.forestId)
            val tail = dao.tail(preview.forestId, bound.accountId)
            check(captured.fingerprint == binding.fingerprint && tail?.operationId == binding.tailId && preview.remote == binding.remote) {
                "RECOVERY_PREVIEW_CHANGED"
            }
            requireCurrent(bound)
            dao.supersede(preview.forestId, bound.accountId)
            appendCapture(association, captured, binding.remote?.serverVersion, true, binding.remote?.dossierJson)
            requireCurrent(bound)
        }
        synchronized(recovery) { recovery.remove(preview.token) }
        schedule()
    }

    /** Observer is a catch-up mechanism; intermediate events before process death are not fabricated. */
    fun startObserving(scope: CoroutineScope): Job = scope.launch {
        merge(dao.observeContentInvalidations().map { Unit }, identity.session.map { Unit }).debounce(200).collect {
            try { captureActivated() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Immutable stored work remains available at the next guarded wake-up. */ }
        }
    }

    private suspend fun requireAssociation(bound: BoundSession, forestId: String): HierarchySyncAssociationEntity {
        requireCurrent(bound)
        val association = checkNotNull(dao.association(forestId)) { "FOREST_NOT_ACTIVATED" }
        check(association.accountId == bound.accountId) { "FOREST_LINKED_TO_ANOTHER_ACCOUNT" }
        check(dao.parcelAccounts(forestId).none { it != bound.accountId }) { "PARCEL_LINKED_TO_ANOTHER_ACCOUNT" }
        requireCurrent(bound)
        return association
    }

    private data class BoundSession(val accountId: String, val guard: IdentitySessionGuard, val snapshot: IdentitySessionSnapshot) {
        fun authorization(): String = "Bearer ${snapshot.tokens?.accessToken ?: throw SessionExpired()}"
    }
    private class SessionScope(var bound: BoundSession)
    private class SessionExpired : IllegalStateException("HIERARCHY_SESSION_CHANGED")
    private data class RecoveryBinding(val bound: BoundSession, val fingerprint: String, val tailId: String?, val remote: HierarchyRemoteDossier?)

    private fun captureSession(account: String? = identity.session.value?.accountId, guard: IdentitySessionGuard? = null): BoundSession =
        synchronized(sessionStore.coordinator.monitor) {
            val accountId = account ?: throw SessionExpired()
            val reference = guard ?: identity.captureSessionGuard(accountId).getOrElse { throw SessionExpired() }
            val snapshot = sessionStore.snapshot() ?: throw SessionExpired()
            if (!identity.isSessionGuardCurrent(reference) || reference.accountId != accountId ||
                identity.session.value?.accountId != accountId || !sessionStore.isCurrent(snapshot) ||
                snapshot.tokens?.let { JwtSessionDecoder.decode(it.accessToken)?.accountId } != accountId) throw SessionExpired()
            BoundSession(accountId, reference, snapshot)
        }

    private suspend fun requireCurrent(bound: BoundSession) {
        coroutineContext.ensureActive()
        synchronized(sessionStore.coordinator.monitor) {
            if (!sessionStore.isCurrent(bound.snapshot) || !identity.isSessionGuardCurrent(bound.guard) ||
                identity.session.value?.accountId != bound.accountId) throw SessionExpired()
        }
    }

    private suspend fun <T> authenticated(scope: SessionScope, call: suspend (String) -> Response<T>): Response<T> {
        requireCurrent(scope.bound)
        var result = call(scope.bound.authorization())
        requireCurrent(scope.bound)
        if (result.code() == 401) {
            val child = identity.refreshSession(scope.bound.guard).getOrElse { throw SessionExpired() }
            scope.bound = captureSession(scope.bound.accountId, child)
            requireCurrent(scope.bound)
            result = call(scope.bound.authorization())
            requireCurrent(scope.bound)
        }
        return result
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try { Result.success(block()) }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { Result.failure(error) }

    private fun schedule() {
        try {
            scheduleWork?.let { it(); return }
            val request = OneTimeWorkRequestBuilder<HierarchySyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(appContext).enqueueUniqueWork("geosylva-hierarchy-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* A scheduling failure cannot undo committed outbox data. */ }
    }

    private companion object {
        const val PENDING = "PENDING"; const val SYNCING = "SYNCING"; const val SYNCED = "SYNCED"
        const val ERROR = "ERROR"; const val CONFLICT = "CONFLICT"; const val LEASE_MS = 600_000L
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
