package com.forestry.counter.domain.repository

import com.forestry.counter.domain.wood.*
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface WoodObjectAccess { val accountId: String? }

data class SavedWoodObject(
    val id: UUID, val depotId: UUID, val kind: WoodObjectKind, val header: WoodObjectHeader,
    val revision: Int, val createdAt: Long, val updatedAt: Long, val archivedAt: Long?,
)
data class SavedWoodObservation(
    val record: WoodObservationRecord, val payload: WoodObservationPayload, val recordedBy: String,
    val recordedZoneId: String, val recordedOffsetSeconds: Int,
)
data class WoodObjectDetails(
    val item: SavedWoodObject, val observations: List<SavedWoodObservation>, val history: WoodObservationHistory,
)
data class WoodCommandReceipt(
    val commandId: UUID, val objectId: UUID, val appliedObjectRevision: Int,
    val createdObservationId: UUID?, val effectiveObservationId: UUID?, val appliedAt: Long,
)
enum class WoodRepositoryError { ACCESS_CHANGED, UNAVAILABLE, CONFLICT, CORRUPT_DATA, INVALID_COMMAND, UNKNOWN_RESULT }
class WoodRepositoryException(val code: WoodRepositoryError) : IllegalStateException(code.name)

/** Local saved observations; no implicit stock mutation, sale or server acknowledgement. */
interface WoodObjectRepository {
    fun captureAccess(): WoodObjectAccess
    fun isAccessCurrent(access: WoodObjectAccess): Boolean
    fun accessChanges(): Flow<Unit>
    suspend fun list(depotId: UUID, access: WoodObjectAccess): List<SavedWoodObject>
    suspend fun details(depotId: UUID, objectId: UUID, access: WoodObjectAccess): WoodObjectDetails
    suspend fun execute(command: WoodObjectCommand, access: WoodObjectAccess): WoodCommandReceipt
}
