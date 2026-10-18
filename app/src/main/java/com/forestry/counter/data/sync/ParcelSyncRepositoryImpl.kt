package com.forestry.counter.data.sync

import android.content.Context
import com.forestry.counter.BuildConfig
import com.forestry.counter.data.local.dao.ParcelSyncOperationDao
import com.forestry.counter.data.local.entity.ParcelSyncOperationEntity
import com.forestry.counter.data.local.entity.ParcelleEntity
import com.forestry.counter.domain.model.ParcelSyncRecoveryPreview
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.forestry.counter.data.local.dao.ParcelleDao
import com.forestry.counter.data.local.dao.ParcelSyncDao
import com.forestry.counter.data.local.entity.ParcelSyncEntity
import com.forestry.counter.data.remote.identity.EncryptedIdentitySessionStore
import com.forestry.counter.data.remote.identity.IdentitySessionSnapshot
import com.forestry.counter.data.remote.identity.IdentitySessionStore
import com.forestry.counter.data.remote.identity.JwtSessionDecoder
import com.forestry.counter.data.work.ParcelSyncWorker
import com.forestry.counter.domain.model.ParcelSyncProcessResult
import com.forestry.counter.domain.model.ParcelSyncPullResult
import com.forestry.counter.domain.model.ParcelSyncSummary
import com.forestry.counter.domain.model.ParcelSyncSelection
import com.forestry.counter.domain.model.ParcelSyncCandidate
import com.forestry.counter.domain.repository.IdentityRepository
import com.forestry.counter.domain.repository.IdentitySessionGuard
import com.forestry.counter.domain.repository.ParcelSyncRepository
import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import retrofit2.Response
import kotlin.coroutines.coroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
internal class ParcelSyncRepositoryImpl(
    context: Context,
    private val syncDao: ParcelSyncDao,
    private val parcelleDao: ParcelleDao,
    private val operationDao: ParcelSyncOperationDao,
    private val mutationJournal: ParcelMutationJournal,
    private val apiOrigin: String?,
    private val identityRepository: IdentityRepository,
    private val api: ParcelSyncApiService?,
    private val sessionStore: IdentitySessionStore,
    private val activationStore: ParcelSyncActivationStore,
    private val scheduleWork: (() -> Unit)? = null,
) : ParcelSyncRepository {
    private val appContext = context.applicationContext

    private val recoveryPreviews = linkedMapOf<String, RecoverySnapshot>()

    override fun observeSummary(): Flow<ParcelSyncSummary> =
        identityRepository.session.flatMapLatest { session ->
            if (session == null) flowOf(ParcelSyncSummary())
            else combine(syncDao.observeCheckpointCounts(session.accountId), operationDao.observeCounts(session.accountId)) { checkpoints, operations ->
                ParcelSyncSummary(
                    pending = operations.pending, syncing = operations.syncing, synced = checkpoints.synced,
                    conflicts = checkpoints.conflicts + operations.conflicts,
                    errors = checkpoints.errors + operations.errors,
                    lastSuccessAt = listOfNotNull(checkpoints.lastSuccessAt, operations.lastSuccessAt).maxOrNull(),
                    recoveryRequired = checkpoints.errors,
                )
            }
        }

    override suspend fun loadSelection(): Result<ParcelSyncSelection> = try {
        val accountId = identityRepository.session.value?.accountId ?: throw SessionBoundaryException()
        synchronized(recoveryPreviews) { recoveryPreviews.clear() }
        val scope = SessionScope(captureSession(accountId))
        val parcels = parcelleDao.getAllParcellesNow().toMutableList()
        requireCurrent(scope.session)
        // Deleted legacy folders also need a visible recovery path for their tombstones.
        val legacyRows = syncDao.getLegacyRecoverable(accountId)
        requireCurrent(scope.session)
        legacyRows.forEach { row ->
            if (parcels.none { it.parcelleId == row.parcelId }) {
                parcelleDao.getParcelleByIdAny(row.parcelId)?.let { parcels += it }
                requireCurrent(scope.session)
            }
        }
        val remote = if (legacyRows.isEmpty()) emptyMap() else try {
            readRecoveryServerState(scope)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SessionBoundaryException) {
            throw SessionBoundaryException()
        } catch (_: Exception) {
            null // Visible but unavailable: never infer an absent server record from a failed read.
        }
        val candidates = parcels.map { parcel ->
            val accounts = syncDao.getAccountIdsForParcel(parcel.parcelleId)
            requireCurrent(scope.session)
            val ownershipAccounts = (accounts + syncDao.getHierarchyAccountsForParcel(parcel.parcelleId)).distinct()
            requireCurrent(scope.session)
            val blocked = ownershipAccounts.any { it != accountId }
            val row = if (accountId in accounts) syncDao.get(accountId, parcel.parcelleId) else null
            requireCurrent(scope.session)
            val recoverable = !blocked && row != null && isLegacyRecoverable(row)
            requireCurrent(scope.session)
            val preview = if (recoverable && remote != null) {
                val tail = operationDao.getLastActiveForParcel(accountId, parcel.parcelleId)
                requireCurrent(scope.session)
                val token = UUID.randomUUID().toString()
                val item = remote[parcel.parcelleId]
                val snapshot = RecoverySnapshot(
                    accountId, scope.session.guard, checkNotNull(row), ParcelMutationJournal.fingerprint(parcel),
                    tail?.operationId, item,
                )
                synchronized(recoveryPreviews) {
                    recoveryPreviews[token] = snapshot
                }
                ParcelSyncRecoveryPreview(
                    token, parcel.name, parcel.updatedAt, item?.parcel?.name,
                    item?.serverUpdatedAt, item?.serverVersion, parcel.deletedAt != null,
                )
            } else null
            ParcelSyncCandidate(
                parcelId = parcel.parcelleId, name = if (blocked) "" else parcel.name,
                surfaceHa = parcel.surfaceHa.takeUnless { blocked }, commune = parcel.nomCommune.takeUnless { blocked },
                isLinked = accountId in accounts, blockedByOtherAccount = blocked,
                recoveryRequired = recoverable, recoveryPreview = preview,
                recoveryUnavailable = recoverable && preview == null, isDeleted = parcel.deletedAt != null,
            )
        }
        val missingLegacy = legacyRows.filter { row -> parcels.none { it.parcelleId == row.parcelId } }.map { row ->
            val accounts = (syncDao.getAccountIdsForParcel(row.parcelId) +
                syncDao.getHierarchyAccountsForParcel(row.parcelId)).distinct()
            requireCurrent(scope.session)
            ParcelSyncCandidate(
                row.parcelId, "", null, null, true, accounts.any { it != accountId },
                recoveryRequired = true, recoveryUnavailable = true, isDeleted = true,
            )
        }
        requireCurrent(scope.session)
        Result.success(ParcelSyncSelection(accountId, scope.session.guard, candidates + missingLegacy))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    override suspend fun enqueueSelected(selection: ParcelSyncSelection, parcelIds: Set<String>): Result<Int> = try {
        val session = captureSession(selection.accountId, selection.sessionGuard)
        requireCurrent(session)
        check(parcelIds.all { id -> selection.parcels.any { it.parcelId == id } }) { "PARCEL_NOT_PROPOSED" }
        if (parcelIds.isEmpty()) Result.success(0) else {
            check(parcelIds.all { id -> selection.parcels.single { it.parcelId == id }.let {
                !it.blockedByOtherAccount && (!it.recoveryRequired || it.recoveryPreview != null)
            } }) { "PARCEL_NOT_SELECTABLE" }
            check(activationStore.enable(session.accountId)) { "ACTIVATION_STORAGE_UNAVAILABLE" }
            requireCurrent(session)
            var count = 0
            operationDao.runJournalTransaction {
                for (id in parcelIds) {
                    requireCurrent(session)
                    val candidate = selection.parcels.single { it.parcelId == id }
                    check(!candidate.blockedByOtherAccount) { ERROR_FOREIGN_ASSOCIATION }
                    val accounts = syncDao.getAccountIdsForParcel(id)
                    requireCurrent(session)
                    val owners = (accounts + syncDao.getHierarchyAccountsForParcel(id)).distinct()
                    requireCurrent(session)
                    check(owners.none { it != session.accountId }) { ERROR_FOREIGN_ASSOCIATION }
                    val parcel = checkNotNull(parcelleDao.getParcelleByIdAny(id)) { "PARCEL_NOT_AVAILABLE" }
                    requireCurrent(session)
                    if (candidate.recoveryRequired) {
                        recoverLegacyInTransaction(session, candidate, parcel)
                        count += 1
                    } else if (session.accountId !in accounts) {
                        check(!candidate.isLinked && parcel.deletedAt == null) { "PARCEL_NOT_AVAILABLE" }
                        if (mutationJournal.selectInTransaction(session.accountId, parcel, System.currentTimeMillis())) count += 1
                    }
                    // Existing nonlegacy associations remain intact, including pending/error/conflict.
                }
                requireCurrent(session)
            }
            requireCurrent(session)
            if (count > 0) schedule()
            Result.success(count)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    /** Compatibility callbacks only wake stored work. They cannot fabricate a post-commit payload. */
    override suspend fun enqueueUpsert(parcelleId: String) { resumeStoredWork() }
    override suspend fun enqueueDelete(parcelleId: String) { resumeStoredWork() }

    suspend fun resumeStoredWork() {
        val account = identityRepository.session.value?.accountId ?: return
        if (operationDao.getPendingCountForAccount(account) > 0) schedule()
    }

    override suspend fun processPending(): ParcelSyncProcessResult {
        val current = identityRepository.session.value ?: return EMPTY_RESULT
        val scope = try { SessionScope(captureSession(current.accountId)) }
        catch (_: SessionBoundaryException) { return ParcelSyncProcessResult(0, 0, 0, true) }
        val now = System.currentTimeMillis()
        val ready = operationDao.getReady(current.accountId, now, now - STALE_SYNC_DELAY_MS, BATCH_SIZE)
        try { requireCurrent(scope.session) }
        catch (_: SessionBoundaryException) { return ParcelSyncProcessResult(0, 0, 0, true) }
        var synchronized = 0
        var conflicts = 0
        var errors = 0
        var shouldRetry = false
        for (operation in ready) {
            try {
                requireCurrent(scope.session)
                if (operation.originAccountId != scope.session.accountId) throw ParcelAssociationException(ERROR_FOREIGN_ASSOCIATION)
                val attemptedAt = System.currentTimeMillis()
                val attemptId = UUID.randomUUID().toString()
                val claimed = operationDao.claimIfCurrent(
                    operation.operationId, scope.session.accountId, operation.state, operation.lastAttemptAt,
                    operation.attemptId, attemptedAt, attemptId,
                )
                requireCurrent(scope.session)
                if (claimed == 0) continue
                val owned = operation.copy(state = STATE_SYNCING, lastAttemptAt = attemptedAt, attemptId = attemptId)
                when (synchronize(api, owned, scope)) {
                    SyncItemResult.SUCCESS -> synchronized += 1
                    SyncItemResult.CONFLICT -> conflicts += 1
                    SyncItemResult.RETRY -> { errors += 1; shouldRetry = true }
                    SyncItemResult.ERROR -> errors += 1
                    SyncItemResult.SKIPPED -> Unit
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SessionBoundaryException) {
                // A claimed row remains durable; stale lease recovery retries it under its original account.
                shouldRetry = true
                break
            } catch (_: ParcelAssociationException) {
                errors += 1 // Never send a global-table collision under another account.
            }
        }
        // A successor becomes eligible only after its predecessor's validated receipt.
        if (operationDao.getReady(current.accountId, System.currentTimeMillis(), now - STALE_SYNC_DELAY_MS, 1).isNotEmpty()) schedule()
        // Future retry deadlines and a nonstale in-flight lease need a durable wake-up too.
        // Permanent errors and blocked/conflicted descendants are excluded by this query.
        if (operationDao.getPendingCountForAccount(current.accountId) > 0) shouldRetry = true
        return ParcelSyncProcessResult(synchronized, conflicts, errors, shouldRetry)
    }

    private suspend fun isLegacyRecoverable(row: ParcelSyncEntity): Boolean =
        row.state != STATE_SYNCED && row.state != STATE_CONFLICT && operationDao.getByOperationId(row.operationId) == null

    private suspend fun readRecoveryServerState(scope: SessionScope): Map<String, ParcelSyncResponseDto> {
        val service = api ?: throw IOException(ERROR_API_NOT_CONFIGURED)
        val result = linkedMapOf<String, ParcelSyncResponseDto>()
        var expectedTotal: Int? = null
        var expectedSize: Int? = null
        for (page in 1..MAX_PULL_PAGES) {
            val body = fetchPullPage(service, page, scope)
            check(body.page == page && body.size > 0 && body.total >= 0 && body.items.size <= body.size) { "INVALID_RECOVERY_PAGE" }
            check((expectedTotal == null || expectedTotal == body.total) && (expectedSize == null || expectedSize == body.size)) {
                "INCONSISTENT_RECOVERY_PAGE"
            }
            expectedTotal = body.total
            expectedSize = body.size
            body.items.forEach { item ->
                validateServerItem(item)
                val previous = result.put(item.clientId, item)
                check(previous == null) { "INCONSISTENT_RECOVERY_PAGE" }
            }
            if (page.toLong() * body.size >= body.total) {
                check(result.size == body.total) { "INCOMPLETE_RECOVERY_PAGE" }
                return result
            }
            check(body.items.size == body.size) { "INCOMPLETE_RECOVERY_PAGE" }
            check(body.items.isNotEmpty()) { "INCOMPLETE_RECOVERY_PAGE" }
        }
        throw IOException("RECOVERY_PAGE_LIMIT_REACHED")
    }

    private suspend fun recoverLegacyInTransaction(
        session: BoundSession,
        candidate: ParcelSyncCandidate,
        parcel: ParcelleEntity,
    ) {
        val token = checkNotNull(candidate.recoveryPreview).token
        val preview = synchronized(recoveryPreviews) { recoveryPreviews[token] }
            ?: error("RECOVERY_PREVIEW_EXPIRED")
        check(preview.accountId == session.accountId && preview.guard === session.guard) { "RECOVERY_PREVIEW_EXPIRED" }
        requireCurrent(session)
        val checkpoint = checkNotNull(syncDao.get(session.accountId, parcel.parcelleId))
        requireCurrent(session)
        check(checkpoint == preview.legacy && isLegacyRecoverable(checkpoint)) { "RECOVERY_PREVIEW_CHANGED" }
        val tail = operationDao.getLastActiveForParcel(session.accountId, parcel.parcelleId)
        requireCurrent(session)
        check(tail?.operationId == preview.lastOperationId && ParcelMutationJournal.fingerprint(parcel) == preview.localFingerprint) {
            "RECOVERY_PREVIEW_CHANGED"
        }
        check(checkpoint.operation != OPERATION_DELETE || parcel.deletedAt != null) { "LEGACY_DELETE_REQUIRES_REVIEW" }
        check(!apiOrigin.isNullOrBlank()) { ERROR_API_NOT_CONFIGURED }
        val now = System.currentTimeMillis()
        val operation = ParcelMutationJournal.freeze(
            parcel, if (parcel.deletedAt != null) OPERATION_DELETE else OPERATION_UPSERT,
            session.accountId, apiOrigin, preview.remote?.serverVersion, null, STATE_PENDING, now,
            UUID.randomUUID().toString(), legacyReference(checkpoint),
        )
        // Only explicit confirmation retires the old unknown requests; their recorded snapshots stay retained.
        operationDao.supersedeOutstandingForParcel(session.accountId, parcel.parcelleId, now)
        operationDao.append(operation)
        syncDao.upsert(checkpoint.copy(
            operation = operation.operation, operationId = operation.operationId,
            state = STATE_PENDING, serverVersion = operation.baseVersion, retryCount = 0, queuedAt = now,
            lastAttemptAt = null, nextAttemptAt = now, lastErrorCode = null,
        ))
        requireCurrent(session)
    }

    private fun legacyReference(row: ParcelSyncEntity): String = buildJsonObject {
        put("accountId", row.accountId); put("parcelId", row.parcelId); put("operation", row.operation)
        put("operationId", row.operationId); put("state", row.state); put("serverVersion", row.serverVersion)
        put("retryCount", row.retryCount); put("queuedAt", row.queuedAt); put("lastAttemptAt", row.lastAttemptAt)
        put("lastSuccessAt", row.lastSuccessAt); put("nextAttemptAt", row.nextAttemptAt); put("lastErrorCode", row.lastErrorCode)
    }.toString()

    private data class RecoverySnapshot(
        val accountId: String,
        val guard: IdentitySessionGuard,
        val legacy: ParcelSyncEntity,
        val localFingerprint: String,
        val lastOperationId: String?,
        val remote: ParcelSyncResponseDto?,
    )

    override suspend fun pull(): Result<ParcelSyncPullResult> {
        val accountId = identityRepository.session.value?.accountId
            ?: return Result.failure(IllegalStateException("Aucun compte connecté"))
        if (!activationStore.isEnabled(accountId)) {
            return Result.failure(IllegalStateException("Synchronisation non activée"))
        }
        val service = api ?: return Result.failure(IllegalStateException(ERROR_API_NOT_CONFIGURED))
        return try {
            val scope = SessionScope(captureSession(accountId))
            var page = 1
            var pagesFetched = 0
            var inserted = 0
            var updated = 0
            var deleted = 0
            var skippedLocalDirty = 0
            var skippedOtherAccount = 0
            var expectedTotal: Int? = null
            var expectedSize: Int? = null
            val seenIds = mutableSetOf<String>()
            var complete = false
            while (pagesFetched < MAX_PULL_PAGES) {
                val body = fetchPullPage(service, page, scope)
                check(body.page == page && body.size > 0 && body.total >= 0 && body.items.size <= body.size) {
                    "INVALID_PULL_PAGE"
                }
                check((expectedTotal == null || expectedTotal == body.total) &&
                    (expectedSize == null || expectedSize == body.size)) { "INCONSISTENT_PULL_PAGE" }
                expectedTotal = body.total
                expectedSize = body.size
                body.items.forEach { item ->
                    validateServerItem(item)
                    check(seenIds.add(item.clientId)) { "INCONSISTENT_PULL_PAGE" }
                }
                val finalPage = page.toLong() * body.size >= body.total
                check(if (finalPage) seenIds.size == body.total else body.items.size == body.size) {
                    "INCOMPLETE_PULL_PAGE"
                }
                pagesFetched += 1
                body.items.forEach { item ->
                    when (mergeFromServer(scope.session, item)) {
                        MergeOutcome.INSERTED -> inserted += 1
                        MergeOutcome.UPDATED -> updated += 1
                        MergeOutcome.DELETED -> deleted += 1
                        MergeOutcome.SKIPPED_LOCAL_DIRTY -> skippedLocalDirty += 1
                        MergeOutcome.SKIPPED_OTHER_ACCOUNT -> skippedOtherAccount += 1
                        MergeOutcome.NOOP -> {}
                    }
                }
                if (finalPage) {
                    complete = true
                    break
                }
                page += 1
            }
            check(complete) { "PULL_PAGE_LIMIT_REACHED" }
            requireCurrent(scope.session)
            Result.success(ParcelSyncPullResult(inserted, updated, deleted, skippedLocalDirty, pagesFetched, skippedOtherAccount))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun validateServerItem(item: ParcelSyncResponseDto) {
        check(item.serverVersion > 0 && item.clientId.isNotBlank()) { "INVALID_SERVER_PARCEL" }
        check((item.status == "active" && item.parcel != null) ||
            (item.status == PARCEL_STATUS_DELETED && item.parcel == null)) { "INVALID_SERVER_PARCEL" }
    }

    private suspend fun fetchPullPage(
        service: ParcelSyncApiService,
        page: Int,
        scope: SessionScope,
    ): GeoSylvaParcelPageDto {
        requireCurrent(scope.session)
        var response = service.list(scope.session.authorizationHeader(), page, PULL_PAGE_SIZE)
        requireCurrent(scope.session)
        if (response.code() == 401) {
            refreshSession(scope)
            requireCurrent(scope.session)
            response = service.list(scope.session.authorizationHeader(), page, PULL_PAGE_SIZE)
            requireCurrent(scope.session)
        }
        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code()} lors de la recuperation des parcelles")
        }
        return response.body() ?: throw IOException("Reponse vide lors de la recuperation des parcelles")
    }

    /**
     * Fusionne une parcelle serveur en local — jamais si une modification
     * locale n'a pas encore ete synchronisee avec succes (le local gagne,
     * voir doc [com.forestry.counter.domain.repository.ParcelSyncRepository.pull]).
     */
    private suspend fun mergeFromServer(session: BoundSession, item: ParcelSyncResponseDto): MergeOutcome {
        requireCurrent(session)
        var outcome = MergeOutcome.NOOP
        syncDao.withParcelMergeTransaction {
            outcome = mergeFromServerInTransaction(session, item)
        }
        requireCurrent(session)
        return outcome
    }

    private suspend fun mergeFromServerInTransaction(session: BoundSession, item: ParcelSyncResponseDto): MergeOutcome {
        val accountId = session.accountId
        requireCurrent(session)
        val accounts = (syncDao.getAccountIdsForParcel(item.clientId) +
            syncDao.getHierarchyAccountsForParcel(item.clientId)).distinct()
        requireCurrent(session)
        if (accounts.any { it != accountId }) return MergeOutcome.SKIPPED_OTHER_ACCOUNT
        val queued = syncDao.get(accountId, item.clientId)
        requireCurrent(session)
        val existing = parcelleDao.getParcelleByIdAny(item.clientId)
        requireCurrent(session)
        val priorOperation = operationDao.getLastActiveForParcel(accountId, item.clientId)
        requireCurrent(session)
        if (priorOperation?.apiOrigin != null && priorOperation.apiOrigin != apiOrigin) {
            throw ParcelAssociationException("API_ORIGIN_CHANGED")
        }
        // A GET begun before a completed push must not roll back either business data or its checkpoint.
        if (queued?.serverVersion != null && item.serverVersion < queued.serverVersion) return MergeOutcome.NOOP
        if (priorOperation?.state == STATE_SYNCED && priorOperation.ackVersion == item.serverVersion &&
            !matchesReceipt(priorOperation, item)
        ) return MergeOutcome.SKIPPED_LOCAL_DIRTY
        // An existing unassociated local folder is never claimed or overwritten by a server collision.
        if (queued == null && existing != null) return MergeOutcome.SKIPPED_LOCAL_DIRTY
        val outcome = decideMergeOutcome(
            isLocalDirty = (queued != null && queued.state != STATE_SYNCED) || operationDao.hasOutstanding(accountId, item.clientId),
            isTombstone = item.status == PARCEL_STATUS_DELETED,
            existsLocally = existing != null,
            isAlreadyDeletedLocally = existing?.deletedAt != null,
        )
        when (outcome) {
            MergeOutcome.SKIPPED_LOCAL_DIRTY, MergeOutcome.SKIPPED_OTHER_ACCOUNT -> Unit
            MergeOutcome.NOOP -> recordSynced(session, item)
            MergeOutcome.DELETED -> {
                parcelleDao.deleteParcelleById(item.clientId, parseServerTimestamp(item.serverUpdatedAt))
                recordSynced(session, item)
            }
            MergeOutcome.INSERTED -> {
                val entity = item.toParcelleEntity(existing) ?: return MergeOutcome.NOOP
                parcelleDao.insertParcelle(entity)
                recordSynced(session, item)
            }
            MergeOutcome.UPDATED -> {
                val entity = item.toParcelleEntity(existing) ?: return MergeOutcome.NOOP
                parcelleDao.updateParcelle(entity)
                recordSynced(session, item)
            }
        }
        return outcome
    }

    /** Aligne la file d'attente locale sur l'etat serveur connu apres un pull reussi. */
    private suspend fun recordSynced(session: BoundSession, item: ParcelSyncResponseDto) {
        requireCurrent(session)
        val now = System.currentTimeMillis()
        syncDao.upsert(
            ParcelSyncEntity(
                accountId = session.accountId,
                parcelId = item.clientId,
                operation = OPERATION_UPSERT,
                operationId = UUID.randomUUID().toString(),
                state = STATE_SYNCED,
                serverVersion = item.serverVersion,
                retryCount = 0,
                queuedAt = now,
                lastAttemptAt = now,
                lastSuccessAt = now,
                nextAttemptAt = now,
                lastErrorCode = null,
            )
        )
        requireCurrent(session)
    }

    private fun parseServerTimestamp(serverUpdatedAt: String?): Long =
        serverUpdatedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            ?: System.currentTimeMillis()

    private suspend fun synchronize(
        service: ParcelSyncApiService?,
        operation: ParcelSyncOperationEntity,
        scope: SessionScope,
    ): SyncItemResult = try {
        if (service == null || apiOrigin.isNullOrBlank() || operation.apiOrigin != apiOrigin) {
            recordFailure(operation, ParcelMutationJournal.BLOCKED_API_ORIGIN, operation.retryCount, Long.MAX_VALUE, "API_ORIGIN_UNAVAILABLE_OR_CHANGED")
            SyncItemResult.ERROR
        } else {
            var response = send(service, operation, scope.session)
            requireCurrent(scope.session)
            if (response.code() == 401) {
                refreshSession(scope)
                response = send(service, operation, scope.session)
                requireCurrent(scope.session)
            }
            handleResponse(operation, response, scope.session)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: SessionBoundaryException) {
        markRetry(operation, "SESSION_CHANGED")
        SyncItemResult.RETRY
    } catch (blocked: ParcelAssociationException) {
        markPermanentError(operation, blocked.message.orEmpty())
        SyncItemResult.ERROR
    } catch (_: IOException) {
        markRetry(operation, "NETWORK_UNAVAILABLE")
        SyncItemResult.RETRY
    } catch (_: Exception) {
        markPermanentError(operation, "INVALID_SERVER_RESPONSE")
        SyncItemResult.ERROR
    }

    private suspend fun send(
        service: ParcelSyncApiService,
        operation: ParcelSyncOperationEntity,
        session: BoundSession,
    ): Response<ParcelSyncResponseDto> {
        requireCurrent(session)
        requireExclusiveAssociation(session, operation.parcelId)
        check(operation.originAccountId == session.accountId &&
            operation.destinationKind == ParcelMutationJournal.PERSONAL_ACCOUNT && operation.apiOrigin == apiOrigin) { "INVALID_JOURNAL_DESTINATION" }
        val current = operationDao.getByOperationId(operation.operationId)
        requireCurrent(session)
        requireExclusiveAssociation(session, operation.parcelId)
        check(current?.state == STATE_SYNCING && current.attemptId == operation.attemptId) { "JOURNAL_LEASE_CHANGED" }
        // Never consult the mutable business parcel here. Replay exactly this request tuple.
        return when (operation.operation) {
            OPERATION_UPSERT -> service.upsert(session.authorizationHeader(), operation.parcelId, ParcelMutationJournal.upsertRequest(operation))
            OPERATION_DELETE -> service.delete(session.authorizationHeader(), operation.parcelId, ParcelMutationJournal.deleteRequest(operation))
            else -> error("INVALID_JOURNAL_OPERATION")
        }
    }

    private suspend fun handleResponse(
        operation: ParcelSyncOperationEntity,
        response: Response<ParcelSyncResponseDto>,
        session: BoundSession,
    ): SyncItemResult {
        requireCurrent(session)
        if (response.isSuccessful) {
            val body = response.body()
            if (body == null || !matchesReceipt(operation, body)) {
                markPermanentError(operation, "INVALID_SERVER_RECEIPT")
                return SyncItemResult.ERROR
            }
            var accepted = false
            operationDao.runJournalTransaction {
                requireCurrent(session)
                requireExclusiveAssociation(session, operation.parcelId)
                val changed = operationDao.markSuccessIfClaim(
                    operation.operationId, session.accountId, checkNotNull(operation.attemptId), body.serverVersion, System.currentTimeMillis(),
                )
                requireCurrent(session)
                if (changed > 0) {
                    val checkpoint = checkNotNull(syncDao.get(session.accountId, operation.parcelId)) { "PARCEL_NOT_LINKED" }
                    requireCurrent(session)
                    syncDao.upsert(checkpoint.copy(
                        operation = operation.operation, operationId = operation.operationId, state = STATE_SYNCED,
                        serverVersion = body.serverVersion, retryCount = 0, lastSuccessAt = System.currentTimeMillis(),
                        nextAttemptAt = System.currentTimeMillis(), lastErrorCode = null,
                    ))
                    requireCurrent(session)
                    accepted = true
                }
            }
            requireCurrent(session)
            return if (accepted) SyncItemResult.SUCCESS else SyncItemResult.SKIPPED
        }
        return when (classifySyncHttpFailure(response.code())) {
            SyncFailureAction.CONFLICT -> {
                recordFailure(operation, STATE_CONFLICT, operation.retryCount, Long.MAX_VALUE, "SYNC_VERSION_CONFLICT")
                SyncItemResult.CONFLICT
            }
            SyncFailureAction.RETRY -> { markRetry(operation, "HTTP_${response.code()}"); SyncItemResult.RETRY }
            SyncFailureAction.REFRESH_SESSION, SyncFailureAction.PERMANENT_ERROR -> {
                markPermanentError(operation, "HTTP_${response.code()}"); SyncItemResult.ERROR
            }
        }
    }

    private fun matchesReceipt(operation: ParcelSyncOperationEntity, body: ParcelSyncResponseDto): Boolean {
        if (body.clientId != operation.parcelId || body.serverVersion != operation.expectedServerVersion) return false
        val clientTime = body.clientUpdatedAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return false
        return if (operation.operation == OPERATION_DELETE) {
            val request = ParcelMutationJournal.deleteRequest(operation)
            body.status == PARCEL_STATUS_DELETED && body.parcel == null && clientTime == Instant.parse(request.clientUpdatedAt)
        } else {
            val request = ParcelMutationJournal.upsertRequest(operation)
            body.status == "active" && body.parcel == request.parcel && clientTime == Instant.parse(request.clientUpdatedAt)
        }
    }

    private suspend fun markRetry(operation: ParcelSyncOperationEntity, code: String) {
        val retryCount = operation.retryCount + 1
        val delay = (MIN_RETRY_DELAY_MS * (1L shl retryCount.coerceAtMost(6))).coerceAtMost(MAX_RETRY_DELAY_MS)
        recordFailure(operation, STATE_ERROR, retryCount, System.currentTimeMillis() + delay, code)
    }

    private suspend fun markPermanentError(operation: ParcelSyncOperationEntity, code: String) {
        recordFailure(operation, STATE_ERROR, operation.retryCount, Long.MAX_VALUE, code)
    }

    private suspend fun recordFailure(
        operation: ParcelSyncOperationEntity,
        state: String,
        retryCount: Int,
        nextAttemptAt: Long,
        code: String,
    ) {
        val accountId = checkNotNull(operation.originAccountId)
        val attemptId = operation.attemptId ?: return
        operationDao.runJournalTransaction {
            val changed = operationDao.markFailureIfClaim(operation.operationId, accountId, attemptId, state, retryCount, nextAttemptAt, code)
            if (changed > 0 && (state == STATE_CONFLICT || state.startsWith("BLOCKED_") || nextAttemptAt == Long.MAX_VALUE)) {
                operationDao.blockFollowing(accountId, operation.parcelId, operation.sequence, code)
            }
        }
    }

    private class SessionBoundaryException : IllegalStateException("Session de synchronisation périmée")

    private class ParcelAssociationException(code: String) : IllegalStateException(code)

    private suspend fun requireExclusiveAssociation(session: BoundSession, parcelId: String) {
        requireCurrent(session)
        val accounts = syncDao.getAccountIdsForParcel(parcelId)
        val owners = (accounts + syncDao.getHierarchyAccountsForParcel(parcelId)).distinct()
        requireCurrent(session)
        if (owners.any { it != session.accountId }) throw ParcelAssociationException(ERROR_FOREIGN_ASSOCIATION)
        if (session.accountId !in accounts) throw ParcelAssociationException("PARCEL_NOT_LINKED")
    }

    private data class BoundSession(
        val accountId: String,
        val guard: IdentitySessionGuard,
        val snapshot: IdentitySessionSnapshot,
    ) {
        fun authorizationHeader(): String =
            "Bearer ${snapshot.tokens?.accessToken ?: throw SessionBoundaryException()}"
    }

    private class SessionScope(var session: BoundSession)

    private fun captureSession(
        accountId: String,
        guard: IdentitySessionGuard? = null,
    ): BoundSession = synchronized(sessionStore.coordinator.monitor) {
        val reference = guard ?: identityRepository.captureSessionGuard(accountId)
            .getOrElse { throw SessionBoundaryException() }
        val snapshot = sessionStore.snapshot() ?: throw SessionBoundaryException()
        if (reference.accountId != accountId ||
            !identityRepository.isSessionGuardCurrent(reference) ||
            identityRepository.session.value?.accountId != accountId ||
            snapshot.tokens?.let { JwtSessionDecoder.decode(it.accessToken)?.accountId } != accountId ||
            !sessionStore.isCurrent(snapshot)
        ) throw SessionBoundaryException()
        BoundSession(accountId, reference, snapshot)
    }

    private suspend fun requireCurrent(session: BoundSession) {
        coroutineContext.ensureActive()
        synchronized(sessionStore.coordinator.monitor) {
            if (!sessionStore.isCurrent(session.snapshot) ||
                !identityRepository.isSessionGuardCurrent(session.guard) ||
                identityRepository.session.value?.accountId != session.accountId
            ) throw SessionBoundaryException()
        }
    }

    private suspend fun refreshSession(scope: SessionScope) {
        requireCurrent(scope.session)
        val refreshed = identityRepository.refreshSession(scope.session.guard)
            .getOrElse { throw SessionBoundaryException() }
        // La référence enfant provient du commit de renouvellement. Une connexion A
        // ultérieure ne peut pas être adoptée sur la seule correspondance de compte.
        coroutineContext.ensureActive()
        scope.session = captureSession(scope.session.accountId, refreshed)
    }

    private fun schedule() {
        scheduleWork?.let { it(); return }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<ParcelSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    private enum class SyncItemResult { SUCCESS, CONFLICT, RETRY, ERROR, SKIPPED }

    private companion object {
        const val OPERATION_UPSERT = "UPSERT"
        const val OPERATION_DELETE = "DELETE"
        const val STATE_PENDING = "PENDING"
        const val STATE_SYNCING = "SYNCING"
        const val STATE_SYNCED = "SYNCED"
        const val STATE_CONFLICT = "CONFLICT"
        const val STATE_ERROR = "ERROR"
        const val ERROR_API_NOT_CONFIGURED = "API_NOT_CONFIGURED"
        const val ERROR_FOREIGN_ASSOCIATION = "PARCEL_LINKED_TO_ANOTHER_ACCOUNT"
        const val UNIQUE_WORK_NAME = "geosylva-parcel-sync"
        const val BATCH_SIZE = 50
        const val MIN_RETRY_DELAY_MS = 15_000L
        const val MAX_RETRY_DELAY_MS = 60 * 60 * 1_000L
        const val STALE_SYNC_DELAY_MS = 10 * 60 * 1_000L
        const val PULL_PAGE_SIZE = 200
        const val MAX_PULL_PAGES = 200
        val EMPTY_RESULT = ParcelSyncProcessResult(0, 0, 0, false)
    }
}

object ParcelSyncRepositoryFactory {
    suspend fun resumeStoredWork(repository: ParcelSyncRepository) {
        (repository as? ParcelSyncRepositoryImpl)?.resumeStoredWork()
    }

    fun create(
        context: Context,
        syncDao: ParcelSyncDao,
        parcelleDao: ParcelleDao,
        operationDao: ParcelSyncOperationDao,
        mutationJournal: ParcelMutationJournal,
        identityRepository: IdentityRepository,
    ): ParcelSyncRepository = ParcelSyncRepositoryImpl(
        context = context,
        syncDao = syncDao,
        parcelleDao = parcelleDao,
        operationDao = operationDao,
        mutationJournal = mutationJournal,
        apiOrigin = BuildConfig.GSIE_API_BASE_URL.trim().trimEnd('/').takeIf { it.isNotBlank() },
        identityRepository = identityRepository,
        api = ParcelSyncApiFactory.create(context),
        sessionStore = EncryptedIdentitySessionStore(context),
        activationStore = ParcelSyncActivationStore(context),
    )
}
