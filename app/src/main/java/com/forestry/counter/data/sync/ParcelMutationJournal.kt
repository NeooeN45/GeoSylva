package com.forestry.counter.data.sync

import com.forestry.counter.data.local.dao.ParcelleDao
import com.forestry.counter.data.local.dao.ParcelSyncDao
import com.forestry.counter.data.local.dao.ParcelSyncOperationDao
import com.forestry.counter.data.local.entity.ParcelleEntity
import com.forestry.counter.data.local.entity.ParcelSyncEntity
import com.forestry.counter.data.local.entity.ParcelSyncOperationEntity
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Immutable requests are written inside the same Room transaction as the business mutation. */
class ParcelMutationJournal(
    private val parcelleDao: ParcelleDao,
    private val syncDao: ParcelSyncDao,
    private val operationDao: ParcelSyncOperationDao,
    private val apiOrigin: String?,
    private val accountProvider: () -> String?,
    private val onCommitted: () -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
    private val newOperationId: () -> String = { UUID.randomUUID().toString() },
    private val mutationGuardFactory: ((String?) -> (() -> Boolean))? = null,
) {
    /** A captured author never changes to the next login while Room is suspended. */
    suspend fun mutate(parcelId: String, operation: String, destinationForestId: String? = null, businessWrite: suspend (String?) -> Unit) {
        val capturedAccount = accountProvider()
        val current = mutationGuardFactory?.invoke(capturedAccount) ?: { accountProvider() == capturedAccount }
        operationDao.runJournalTransaction {
            check(current()) { "MUTATION_SESSION_CHANGED" }
            ensureMutationAllowed(parcelId, capturedAccount, destinationForestId)
            businessWrite(capturedAccount)
            check(current()) { "MUTATION_SESSION_CHANGED" }
            val saved = checkNotNull(parcelleDao.getParcelleByIdAny(parcelId)) { "PARCEL_NOT_AVAILABLE" }
            ensureMutationAllowed(parcelId, capturedAccount, destinationForestId)
            appendMutation(saved, operation)
            check(current()) { "MUTATION_SESSION_CHANGED" }
        }
        // Work scheduling is a wake-up only. Failure cannot undo a committed durable request.
        notifyCommitted()
    }

    suspend fun deleteAll(businessDelete: suspend (List<ParcelleEntity>, Long) -> Unit) {
        val capturedAccount = accountProvider()
        val current = mutationGuardFactory?.invoke(capturedAccount) ?: { accountProvider() == capturedAccount }
        operationDao.runJournalTransaction {
            check(current()) { "MUTATION_SESSION_CHANGED" }
            val parcels = parcelleDao.getAllParcellesNow()
            parcels.forEach { ensureMutationAllowed(it.parcelleId, capturedAccount) }
            val now = clock()
            businessDelete(parcels, now)
            check(current()) { "MUTATION_SESSION_CHANGED" }
            parcels.forEach { before ->
                val deleted = checkNotNull(parcelleDao.getParcelleByIdAny(before.parcelleId))
                appendMutation(deleted, DELETE, now)
            }
            check(current()) { "MUTATION_SESSION_CHANGED" }
        }
        notifyCommitted()
    }

    private fun notifyCommitted() {
        try {
            onCommitted()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The request is already committed. Application/session wake-up can resume it later.
        }
    }

    private suspend fun ensureMutationAllowed(parcelId: String, capturedAccount: String?, destinationForestId: String? = null) {
        val accounts = (syncDao.getAccountIdsForParcel(parcelId) + syncDao.getHierarchyAccountsForParcel(parcelId) +
            (destinationForestId?.let { syncDao.getHierarchyAccountsForForest(it) } ?: emptyList())).distinct()
        check(accounts.size <= 1 && (accounts.isEmpty() || capturedAccount != null && accounts.single() == capturedAccount)) {
            "PARCEL_LINKED_TO_ANOTHER_ACCOUNT"
        }
    }

    /** Caller already holds the Room transaction. No identity/token lookup selects this destination. */
    private suspend fun appendMutation(parcel: ParcelleEntity, operation: String, now: Long = clock()) {
        val accounts = syncDao.getAccountIdsForParcel(parcel.parcelleId)
        check(accounts.size <= 1) { "PARCEL_LINKED_TO_ANOTHER_ACCOUNT" }
        val accountId = accounts.singleOrNull()
        val checkpoint = accountId?.let { syncDao.get(it, parcel.parcelleId) }
        val previous = accountId?.let { operationDao.getLastActiveForParcel(it, parcel.parcelleId) }
        val legacy = checkpoint != null && checkpoint.state != SYNCED && previous == null
        val state = when {
            accountId == null -> LOCAL_ONLY
            legacy -> BLOCKED_LEGACY
            checkpoint?.state == CONFLICT || previous?.state == CONFLICT ||
                (previous?.state == ERROR && previous.nextAttemptAt == Long.MAX_VALUE) ||
                previous?.state?.startsWith("BLOCKED_") == true -> BLOCKED_PREDECESSOR
            apiOrigin.isNullOrBlank() -> BLOCKED_API_ORIGIN
            else -> PENDING
        }
        // Once a chain exists, configuration changes never retarget its snapshots to another API.
        val destinationOrigin = if (previous != null) previous.apiOrigin else apiOrigin
        val frozenState = if (accountId != null && destinationOrigin.isNullOrBlank() && state == PENDING) {
            BLOCKED_API_ORIGIN
        } else state
        val base = if (previous == null || previous.state == SYNCED) {
            listOfNotNull(checkpoint?.serverVersion, previous?.ackVersion).maxOrNull()
        } else previous.expectedServerVersion
        // A newer pull checkpoint may supersede the last ACK without fabricating a journal operation.
        val predecessor = previous?.takeIf { it.state != SYNCED || it.ackVersion == base }?.operationId
        operationDao.append(freeze(
            parcel, operation, accountId, destinationOrigin, base, predecessor, frozenState, now, newOperationId(),
        ))
    }

    /** Explicit first association and its initial snapshot are committed together. */
    suspend fun selectInTransaction(accountId: String, parcel: ParcelleEntity, now: Long): Boolean {
        check(parcel.deletedAt == null) { "PARCEL_NOT_AVAILABLE" }
        val accounts = syncDao.getAccountIdsForParcel(parcel.parcelleId)
        val owners = (accounts + syncDao.getHierarchyAccountsForParcel(parcel.parcelleId)).distinct()
        check(owners.none { it != accountId }) { "PARCEL_LINKED_TO_ANOTHER_ACCOUNT" }
        if (accountId in accounts) return false
        val operation = freeze(
            parcel, UPSERT, accountId, apiOrigin, null, null,
            if (apiOrigin.isNullOrBlank()) BLOCKED_API_ORIGIN else PENDING,
            now, newOperationId(),
        )
        val inserted = syncDao.insertIfAbsent(listOf(ParcelSyncEntity(
            accountId = accountId, parcelId = parcel.parcelleId, operation = UPSERT,
            operationId = operation.operationId, state = PENDING, serverVersion = null,
            retryCount = 0, queuedAt = now, lastAttemptAt = null, lastSuccessAt = null,
            nextAttemptAt = now, lastErrorCode = null,
        )))
        if (inserted.none { it != -1L }) return false
        operationDao.append(operation)
        return true
    }

    companion object {
        const val UPSERT = "UPSERT"
        const val DELETE = "DELETE"
        const val PERSONAL_ACCOUNT = "PERSONAL_ACCOUNT"
        const val LOCAL_ONLY = "LOCAL_ONLY"
        const val PENDING = "PENDING"
        const val SYNCING = "SYNCING"
        const val SYNCED = "SYNCED"
        const val ERROR = "ERROR"
        const val CONFLICT = "CONFLICT"
        const val SUPERSEDED = "SUPERSEDED"
        const val BLOCKED_LEGACY = "BLOCKED_LEGACY"
        const val BLOCKED_PREDECESSOR = "BLOCKED_PREDECESSOR"
        const val BLOCKED_API_ORIGIN = "BLOCKED_API_ORIGIN"
        // This representation is stable at rest. Retrofit may omit null JSON members but never rereads business data.
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

        fun fingerprint(parcel: ParcelleEntity): String = sha256(
            json.encodeToString(parcel.toSyncPayload()) + "|deletedAt=" + parcel.deletedAt,
        )

        internal fun freeze(
            parcel: ParcelleEntity,
            operation: String,
            accountId: String?,
            apiOrigin: String?,
            baseVersion: Int?,
            predecessor: String?,
            state: String,
            now: Long,
            operationId: String,
            legacyReferenceJson: String? = null,
        ): ParcelSyncOperationEntity {
            require(operation == UPSERT || operation == DELETE)
            require(baseVersion == null || baseVersion in 1 until Int.MAX_VALUE)
            val timestamp = Instant.ofEpochMilli(if (operation == DELETE) now else parcel.updatedAt).toString()
            val request = if (operation == DELETE) {
                json.encodeToString(ParcelDeleteRequestDto(operationId, baseVersion, timestamp))
            } else {
                json.encodeToString(ParcelUpsertRequestDto(operationId, baseVersion, timestamp, parcel.toSyncPayload()))
            }
            return ParcelSyncOperationEntity(
                operationId = operationId, parcelId = parcel.parcelleId, originAccountId = accountId,
                destinationKind = accountId?.let { PERSONAL_ACCOUNT }, apiOrigin = apiOrigin.takeIf { accountId != null },
                operation = operation, requestJson = request, baseVersion = baseVersion,
                expectedServerVersion = if (accountId != null) (baseVersion ?: 0) + 1 else null,
                predecessorOperationId = predecessor, createdAt = now, legacyReferenceJson = legacyReferenceJson,
                payloadFingerprint = fingerprint(parcel), state = state, nextAttemptAt = now,
            )
        }

        internal fun upsertRequest(operation: ParcelSyncOperationEntity): ParcelUpsertRequestDto =
            json.decodeFromString<ParcelUpsertRequestDto>(operation.requestJson).also {
                check(operation.operation == UPSERT && it.operationId == operation.operationId &&
                    it.baseVersion == operation.baseVersion) { "INVALID_JOURNAL_REQUEST" }
            }

        internal fun deleteRequest(operation: ParcelSyncOperationEntity): ParcelDeleteRequestDto =
            json.decodeFromString<ParcelDeleteRequestDto>(operation.requestJson).also {
                check(operation.operation == DELETE && it.operationId == operation.operationId &&
                    it.baseVersion == operation.baseVersion) { "INVALID_JOURNAL_REQUEST" }
            }

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
