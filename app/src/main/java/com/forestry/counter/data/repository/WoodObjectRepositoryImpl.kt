package com.forestry.counter.data.repository

import androidx.room.withTransaction
import com.forestry.counter.data.local.ForestryDatabase
import com.forestry.counter.data.local.entity.*
import com.forestry.counter.data.sync.hierarchy.BusinessMutationGuard
import com.forestry.counter.domain.model.WoodDepotOwnerScope
import com.forestry.counter.domain.repository.*
import com.forestry.counter.domain.wood.*
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** All mutations, their receipt and their revision check commit in one Room transaction. */
class WoodObjectRepositoryImpl(
    private val database: ForestryDatabase,
    private val identity: IdentityRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : WoodObjectRepository {
    private val dao = database.woodObjectDao()
    private val gate = database.woodDataGate
    private val guard = BusinessMutationGuard(identity)
    private val accessOwner = Any()
    private class Access(val owner: Any, override val accountId: String?, val epoch: Long, val current: () -> Boolean) : WoodObjectAccess {
        val scope = if (accountId == null) WoodDepotOwnerScope.LOCAL_DEVICE else WoodDepotOwnerScope.PERSONAL
        val actor = accountId ?: "anonymous"
    }

    override fun captureAccess(): WoodObjectAccess {
        val account = identity.session.value?.accountId
        val epoch = try { gate.capture() } catch (_: IllegalStateException) { fail(WoodRepositoryError.ACCESS_CHANGED) }
        return Access(accessOwner, account, epoch, guard.capture(account)).also { current(it) }
    }
    override fun isAccessCurrent(access: WoodObjectAccess): Boolean =
        (access as? Access)?.let { it.owner === accessOwner && gate.isCurrent(it.epoch) && it.current() } == true
    override fun accessChanges(): Flow<Unit> = combine(identity.session, identity.sessionGeneration, gate.state) { _, _, _ -> Unit }

    private fun current(access: WoodObjectAccess): Access {
        if (!isAccessCurrent(access)) fail(WoodRepositoryError.ACCESS_CHANGED)
        return access as Access
    }
    private suspend fun <T> transaction(access: Access, block: suspend () -> T): T = try {
        gate.withAccess(access.epoch) {
            database.withTransaction {
                current(access)
                val result = block()
                current(access)
                result
            }
        }
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (e: IllegalStateException) {
        if (!isAccessCurrent(access)) fail(WoodRepositoryError.ACCESS_CHANGED)
        throw e
    }

    override suspend fun list(depotId: UUID, access: WoodObjectAccess): List<SavedWoodObject> {
        val a = current(access)
        val result = transaction(a) {
            parent(depotId, a, false)
            dao.objectsInDepot(depotId.toString()).map { row -> owned(row, a); row.saved() }
        }
        current(a)
        return result
    }
    override suspend fun details(depotId: UUID, objectId: UUID, access: WoodObjectAccess): WoodObjectDetails {
        val a = current(access)
        val result = transaction(a) {
            val depot = parent(depotId, a, false)
            val row = objectRow(depotId, objectId, a)
            details(row).copy(depotName = depot.name)
        }
        current(a)
        return result
    }

    override suspend fun execute(command: WoodObjectCommand, access: WoodObjectAccess): WoodCommandReceipt {
        val a = current(access)
        val c = command.context
        if (c.owner != WoodOwner(a.scope, a.accountId) || c.actorId != a.actor) fail(WoodRepositoryError.INVALID_COMMAND)
        val frozen = WoodContractCodec.freezeCommand(command)
        val result = transaction(a) {
            val previous = dao.receipt(c.commandId.toString())
            if (previous != null) {
                if (previous.ownerScope != a.scope.name || previous.ownerAccountId != a.accountId ||
                    previous.commandJson != frozen.text || previous.commandSha256 != frozen.sha256 ||
                    previous.objectId != c.objectId.toString() || previous.depotId != c.depotId.toString() || previous.actorId != a.actor) {
                    fail(WoodRepositoryError.CONFLICT)
                }
                // Return the original receipt even if a later operation changed or archived the object.
                previous.receipt()
            } else {
                parent(c.depotId, a, true)
                val time = clock().also { require(it >= 0) }
                var added: NewWoodObservation? = null
                var addedRecord: WoodObservationRecord? = null
                var selected: UUID? = null
                val revision: Int
                if (command is WoodObjectCommand.Create) {
                    if (dao.getObject(c.objectId.toString()) != null) fail(WoodRepositoryError.CONFLICT)
                    val header = WoodContractCodec.freezeHeader(command.header)
                    dao.insertObject(WoodObjectEntity(c.objectId.toString(), c.depotId.toString(), a.scope.name, a.accountId,
                        command.kind.name, header.text, header.sha256, 1, time, a.actor, time, a.actor, null))
                    added = command.firstObservation
                    addedRecord = added?.let { WoodObservationRecord(it.raw(c.objectId, time), it.id, 1) }
                    selected = added?.id?.takeIf { command.makeEffective }
                    revision = 1
                } else {
                    val row = objectRow(c.depotId, c.objectId, a)
                    if (row.archivedAt != null) fail(WoodRepositoryError.UNAVAILABLE)
                    for (link in database.fieldDao().sitesFor("WOOD_OBJECT", row.objectId)) {
                        val site=database.fieldDao().site(link.siteId) ?: fail(WoodRepositoryError.UNAVAILABLE)
                        if (site.ownerKey != (a.accountId?.let { "ACCOUNT:$it" } ?: "LOCAL") || site.archived) fail(WoodRepositoryError.UNAVAILABLE)
                    }
                    val old = details(row)
                    val expected = when (command) {
                        is WoodObjectCommand.UpdateHeader -> command.expectedObjectRevision
                        is WoodObjectCommand.AddObservation -> command.expectedObjectRevision
                        is WoodObjectCommand.CorrectObservation -> command.expectedObjectRevision
                        is WoodObjectCommand.SelectEffective -> command.expectedObjectRevision
                        is WoodObjectCommand.Archive -> command.expectedObjectRevision
                        else -> fail(WoodRepositoryError.INVALID_COMMAND)
                    }
                    if (row.revision != expected || row.revision == Int.MAX_VALUE) fail(WoodRepositoryError.CONFLICT)
                    var history = old.history
                    when (command) {
                        is WoodObjectCommand.AddObservation -> {
                            added = command.observation
                            history = accepted(history.addObservation(added.raw(c.objectId, time), expected, command.makeEffective))
                        }
                        is WoodObjectCommand.CorrectObservation -> {
                            added = command.correction
                            history = accepted(history.correctObservation(added.raw(c.objectId, time), command.targetObservationId,
                                command.expectedObservationRevision, expected, command.reason))
                        }
                        is WoodObjectCommand.SelectEffective -> history = accepted(history.selectEffectiveObservation(command.observationId, expected))
                        else -> Unit
                    }
                    addedRecord = added?.let { new -> history.records.single { it.observationId == new.id } }
                    selected = history.effectiveObservationId
                    val header = if (command is WoodObjectCommand.UpdateHeader) WoodContractCodec.freezeHeader(command.header)
                        else WoodContractCodec.freezeHeader(old.item.header)
                    if (dao.advance(row.objectId, expected, header.text, header.sha256, time, a.actor,
                        time.takeIf { command is WoodObjectCommand.Archive }) != 1) fail(WoodRepositoryError.CONFLICT)
                    revision = expected + 1
                }
                if (added != null && addedRecord != null) {
                    if (dao.observations(c.objectId.toString()).any { it.observationId == added.id.toString() }) fail(WoodRepositoryError.CONFLICT)
                    val payload = WoodContractCodec.freezePayload(added.payload)
                    val zone = ZoneId.systemDefault()
                    val offset = zone.rules.getOffset(Instant.ofEpochMilli(time)).totalSeconds
                    dao.insertObservation(WoodObservationEntity(added.id.toString(), c.objectId.toString(),
                        addedRecord.rootObservationId.toString(), addedRecord.observationRevision,
                        addedRecord.correctionOfObservationId?.toString(), addedRecord.correctionReason,
                        payload.text, payload.sha256, time, zone.id, offset, a.actor))
                }
                // A correction does not rewrite the selection or its original selection audit.
                val explicitSelection = command is WoodObjectCommand.SelectEffective ||
                    command is WoodObjectCommand.Create && command.makeEffective ||
                    command is WoodObjectCommand.AddObservation && command.makeEffective
                if (selected != null && explicitSelection) dao.select(WoodEffectiveObservationEntity(c.objectId.toString(),
                    selected.toString(), time, a.actor, c.commandId.toString()))
                val receipt = WoodCommandEntity(c.commandId.toString(), c.depotId.toString(), c.objectId.toString(), a.scope.name,
                    a.accountId, a.actor, frozen.text, frozen.sha256, revision, added?.id?.toString(), selected?.toString(), time)
                dao.insertCommand(receipt)
                receipt.receipt()
            }
        }
        // Identity changed after commit: do not claim rollback. An identical retry can retrieve its receipt.
        if (!isAccessCurrent(a)) fail(WoodRepositoryError.UNKNOWN_RESULT)
        return result
    }

    private suspend fun parent(depotId: UUID, a: Access, live: Boolean): WoodDepotEntity {
        val row = database.woodDepotDao().getByIdIncludingDeleted(depotId.toString())
        if (row == null || row.ownerScope != a.scope.name || row.ownerAccountId != a.accountId || live && row.deletedAt != null) {
            fail(WoodRepositoryError.UNAVAILABLE)
        }
        return row
    }
    private fun owned(row: WoodObjectEntity, a: Access) {
        if (row.ownerScope != a.scope.name || row.ownerAccountId != a.accountId) fail(WoodRepositoryError.UNAVAILABLE)
    }
    private suspend fun objectRow(depotId: UUID, objectId: UUID, a: Access): WoodObjectEntity {
        val row = dao.getObject(objectId.toString()) ?: fail(WoodRepositoryError.UNAVAILABLE)
        owned(row, a)
        if (row.depotId != depotId.toString()) fail(WoodRepositoryError.UNAVAILABLE)
        return row
    }
    private fun WoodObjectEntity.saved(): SavedWoodObject = try {
        check(revision > 0)
        SavedWoodObject(UUID.fromString(objectId), UUID.fromString(depotId), WoodObjectKind.valueOf(kind),
            WoodContractCodec.readHeader(headerJson.toByteArray(Charsets.UTF_8), headerSha256), revision, createdAt, updatedAt, archivedAt)
    } catch (_: IllegalArgumentException) { fail(WoodRepositoryError.CORRUPT_DATA) }

    private suspend fun details(row: WoodObjectEntity): WoodObjectDetails {
        val item = row.saved()
        val observations = dao.observations(row.objectId).map { o ->
            val payload = try { WoodContractCodec.readPayload(o.payloadJson.toByteArray(Charsets.UTF_8), o.payloadSha256) }
                catch (_: IllegalArgumentException) { fail(WoodRepositoryError.CORRUPT_DATA) }
            val raw = RawWoodObservation(UUID.fromString(o.observationId), item.id, Instant.ofEpochMilli(o.recordedAt),
                payload.measurement, payload.observedTime)
            SavedWoodObservation(WoodObservationRecord(raw, UUID.fromString(o.rootObservationId), o.observationRevision,
                o.correctionOfObservationId?.let(UUID::fromString), o.correctionReason), payload, o.recordedBy, o.recordedZoneId, o.recordedOffsetSeconds)
        }
        val selection = dao.effective(row.objectId)?.observationId?.let(UUID::fromString)
        val history = when (val restored = WoodObservationHistory.restore(item.id, item.kind, item.revision,
            observations.map { it.record }, selection)) {
            is WoodHistoryResult.Accepted -> restored.history
            is WoodHistoryResult.Rejected -> fail(WoodRepositoryError.CORRUPT_DATA)
        }
        return WoodObjectDetails(item, observations.toList(), history)
    }
    private fun accepted(result: WoodHistoryResult): WoodObservationHistory = when (result) {
        is WoodHistoryResult.Accepted -> result.history
        is WoodHistoryResult.Rejected -> fail(WoodRepositoryError.CONFLICT)
    }
    private fun NewWoodObservation.raw(objectId: UUID, time: Long) = RawWoodObservation(id, objectId,
        Instant.ofEpochMilli(time), payload.measurement, payload.observedTime)
    private fun WoodCommandEntity.receipt() = WoodCommandReceipt(UUID.fromString(commandId), UUID.fromString(objectId),
        appliedObjectRevision, createdObservationId?.let(UUID::fromString), effectiveObservationId?.let(UUID::fromString), appliedAt)
    private fun fail(code: WoodRepositoryError): Nothing = throw WoodRepositoryException(code)
}
