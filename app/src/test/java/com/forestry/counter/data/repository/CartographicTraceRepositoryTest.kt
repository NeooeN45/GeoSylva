package com.forestry.counter.data.repository

import com.forestry.counter.data.cartography.CartographicAuthorityFailure
import com.forestry.counter.data.cartography.CartographicScopeAuthority
import com.forestry.counter.data.local.dao.CartographicTraceDao
import com.forestry.counter.data.local.entity.CartographicTraceEntity
import com.forestry.counter.data.local.entity.CartographicTraceJournalEntity
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.domain.location.trace.*
import com.forestry.counter.domain.repository.*
import com.forestry.counter.domain.usecase.maps.MapDataScope
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CartographicTraceRepositoryTest {
    private val rows = linkedMapOf<String, CartographicTraceEntity>()
    private val journal = linkedMapOf<Triple<String, String, Int>, CartographicTraceJournalEntity>()
    private var writtenBytes = 0L
    private var maximumCheckpoint = 0
    private val dao = object : CartographicTraceDao {
        override suspend fun insert(row: CartographicTraceEntity) { check(row.id !in rows); rows[row.id] = row; countWrite(row) }
        override suspend fun update(row: CartographicTraceEntity): Int { if (row.id !in rows) return 0; rows[row.id] = row; countWrite(row); return 1 }
        override suspend fun byId(id: String) = rows[id]
        override suspend fun inScope(authorityKey: String) = rows.values.filter { it.authorityKey == authorityKey }
        override suspend fun appendJournal(rows: List<CartographicTraceJournalEntity>) {
            rows.forEach { row ->
                val key = Triple(row.traceId, row.kind, row.ordinal)
                check(key !in journal)
                journal[key] = row
                writtenBytes += row.payloadJson.toByteArray().size + row.payloadSha256.length
            }
        }
        override suspend fun updatePortion(row: CartographicTraceJournalEntity) {
            journal[Triple(row.traceId, row.kind, row.ordinal)] = row
            writtenBytes += row.payloadJson.toByteArray().size + row.payloadSha256.length
        }
        override suspend fun journal(id: String) = journal.values.filter { it.traceId == id }
        override suspend fun deleteJournal(id: String) { journal.keys.filter { it.first == id }.forEach(journal::remove) }
        override suspend fun deleteAllJournal() { journal.clear() }
        override suspend fun deleteRecords() { rows.clear() }
        private fun countWrite(row: CartographicTraceEntity) {
            writtenBytes += row.payloadJson.toByteArray().size + row.payloadSha256.length
            if (!row.saved) maximumCheckpoint = maxOf(maximumCheckpoint, row.payloadJson.length)
        }
    }
    private val authority = mockk<CartographicScopeAuthority>()
    private val scope = MapDataScope.Parcel("parcel")
    private var permitted = true
    private val access = token("a", "account-a")
    private val repository = CartographicTraceRepositoryImpl(dao, authority) { 123_000L }
    init {
        every { authority.requireCurrent(any()) } answers {
            if (!permitted) throw CartographicAuthorityFailure(CartographicStoreError.ACCESS_CHANGED)
        }
        every { authority.isCurrent(any()) } answers { permitted }
        coEvery { authority.result<Any?>(any()) } coAnswers {
            try { CartographicStoreResult.Success(firstArg<suspend () -> Any?>().invoke()) }
            catch (failure: CartographicAuthorityFailure) { CartographicStoreResult.Failure(failure.reason) }
        }
        coEvery { authority.transaction<Any?>(any(), any(), any()) } coAnswers {
            if (!permitted) throw CartographicAuthorityFailure(CartographicStoreError.ACCESS_CHANGED)
            thirdArg<suspend (CartographicScopeAuthority.Token) -> Any?>().invoke(firstArg())
        }
    }

    @Test fun `blank optional name commits once and identical retry rereads the same saved identity`() = runTest {
        val snapshot = snapshot(ID)
        val first = repository.save(ID, "   ", snapshot, 0, null, access) as CartographicStoreResult.Success
        assertNull(first.value.name)
        val again = repository.save(ID, null, snapshot, 0, null, access) as CartographicStoreResult.Success
        assertEquals(first.value, again.value)
        assertEquals(1, rows.size)
        assertEquals(1L, rows.getValue(ID).revision)
        assertTrue((repository.read(ID, access) as CartographicStoreResult.Success).value.saved)
        assertEquals(CartographicStoreResult.Failure(CartographicStoreError.CONFLICT), repository.save(ID, "changed", snapshot, 1, null, access))
    }

    @Test fun `draft revision mismatch cannot overwrite another snapshot`() = runTest {
        val initial = snapshot(ID)
        val draft = repository.writeDraft(ID, "a", initial, 0, null, access) as CartographicStoreResult.Success
        assertFalse(draft.value.saved)
        assertEquals(CartographicStoreResult.Failure(CartographicStoreError.CONFLICT), repository.writeDraft(ID, "b", initial, 0, null, access))
        assertEquals("a", (repository.read(ID, access) as CartographicStoreResult.Success).value.name)
    }

    @Test fun `owner and original parent fingerprint are mandatory on every read or commit`() = runTest {
        repository.save(ID, null, snapshot(ID), 0, null, access)
        assertEquals(CartographicStoreResult.Failure(CartographicStoreError.UNAVAILABLE), repository.read(ID, token("b", "account-b")))
        assertEquals(CartographicStoreResult.Failure(CartographicStoreError.UNAVAILABLE), repository.read(ID, token("moved-parent", "account-a")))
        permitted = false
        assertEquals(CartographicStoreResult.Failure(CartographicStoreError.ACCESS_CHANGED), repository.read(ID, access))
        assertEquals(CartographicStoreResult.Failure(CartographicStoreError.ACCESS_CHANGED), repository.save(OTHER, null, snapshot(OTHER), 0, null, access))
        assertEquals(1, rows.size)
    }

    @Test fun `copy has its own identity and source while the original stays byte for byte immutable`() = runTest {
        val original = repository.save(ID, "original", snapshot(ID), 0, null, access) as CartographicStoreResult.Success
        val originalRow = rows.getValue(ID)
        val copiedSnapshot = SegmentedTraceRecorder.restoreStopped(original.value.snapshot, OTHER).snapshot(1)
        val copy = repository.save(OTHER, "copy", copiedSnapshot, 0, ID, access) as CartographicStoreResult.Success
        assertEquals(ID, copy.value.sourceTraceId)
        assertEquals(originalRow, rows.getValue(ID))
        assertEquals(original.value.snapshot.portions.flatMap { it.points }, copy.value.snapshot.portions.flatMap { it.points })
    }

    @Test fun `invalid name and forged SI length are rejected and corrupt saved rows cannot be laundered`() = runTest {
        val snapshot = snapshot(ID)
        assertEquals(CartographicStoreResult.Failure(CartographicStoreError.INVALID_INPUT), repository.save(ID, "x\ny", snapshot, 0, null, access))
        val forged = snapshot.copy(portions = snapshot.portions.map { it.copy(lengthM = it.lengthM + 20) })
        assertEquals(CartographicStoreResult.Failure(CartographicStoreError.INVALID_INPUT), repository.save(ID, null, forged, 0, null, access))
        repository.save(ID, null, snapshot, 0, null, access)
        rows[ID] = rows.getValue(ID).copy(payloadJson = "{}")
        assertEquals(CartographicStoreResult.Failure(CartographicStoreError.CORRUPT), repository.read(ID, access))
        assertEquals(CartographicStoreResult.Failure(CartographicStoreError.CORRUPT), repository.save(ID, null, snapshot, 1, null, access))
    }

    @Test fun `long draft writes append deltas with bounded checkpoints and linear byte growth`() = runTest {
        val recorder = SegmentedTraceRecorder(ID, TraceCapturePolicy(10.0, 15_000_000_000, 0.0, 20_000_000_000))
        val token = requireNotNull(recorder.start(1_000))
        var revision = 0L
        var halfBytes = 0L
        repeat(1_000) { index ->
            val now = 1_001L + index
            val accepted = recorder.accept(token, fix(45.0 + index * 0.00001, now), now)
            val result = repository.writeDraft(ID, null, accepted.snapshot, revision, null, access) as CartographicStoreResult.Success
            revision = result.value.revision
            if (index == 499) halfBytes = writtenBytes
        }
        val draftBytes = writtenBytes
        assertTrue("Doubling points must approximately double storage writes, not quadruple them", draftBytes < halfBytes * 2.1)
        assertTrue(maximumCheckpoint <= TraceDraftJournal.MAX_CHECKPOINT_CHARACTERS)
        assertEquals(1_000, journal.values.count { it.kind == "POINT" })
        assertEquals(1_000, (repository.read(ID, access) as CartographicStoreResult.Success).value.snapshot.acceptedPointCount)
        val stopped = recorder.stop(3_000)
        val saved = repository.save(ID, null, stopped, revision, null, access) as CartographicStoreResult.Success
        assertEquals(1_000, saved.value.snapshot.acceptedPointCount)
        assertTrue(journal.isEmpty())
        assertTrue(writtenBytes - draftBytes <= rows.getValue(ID).payloadJson.toByteArray().size + 128)
    }

    @Test fun `mutating an accepted prefix or truncating the journal is refused without laundering it`() = runTest {
        val snapshot = snapshot(ID)
        val draft = repository.writeDraft(ID, null, snapshot, 0, null, access) as CartographicStoreResult.Success
        val modifiedRecorder = SegmentedTraceRecorder(ID, snapshot.capturePolicy)
        val token = requireNotNull(modifiedRecorder.start(1_000))
        modifiedRecorder.accept(token, fix(46.0, 1_001), 1_001)
        modifiedRecorder.accept(token, fix(46.001, 1_002), 1_002)
        val modified = modifiedRecorder.stop(1_003)
        assertEquals(CartographicStoreResult.Failure(CartographicStoreError.CONFLICT), repository.writeDraft(ID, null, modified, draft.value.revision, null, access))
        journal.remove(journal.keys.first { it.second == "POINT" })
        assertEquals(CartographicStoreResult.Failure(CartographicStoreError.CORRUPT), repository.read(ID, access))
        assertEquals(CartographicStoreResult.Failure(CartographicStoreError.CORRUPT), repository.save(ID, null, snapshot, draft.value.revision, null, access))
        assertFalse(rows.getValue(ID).saved)
    }

    @Test fun `capacity sized draft restores every point and commits its immutable snapshot once`() = runTest {
        val points = List(50_000) { index -> fix(45.0 + index * 0.000001, 1_001L + index) }
        val lengths = DoubleArray(points.size)
        for (index in 1 until points.size) lengths[index] = lengths[index - 1] + requireNotNull(SphericalTraceDistance.betweenM(points[index - 1], points[index]))
        val policy = TraceCapturePolicy(10.0, 15_000_000_000, 0.0, 20_000_000_000)
        fun capture(count: Int) = TraceCaptureSnapshot(ID, TraceCapturePhase.RECORDING, 1,
            listOf(TracePortion("$ID:portion:1", TraceBreakReason.INITIAL, points.take(count), lengths[count - 1])),
            listOf(TraceCaptureEvent(TraceEventType.STARTED, 1_000)), points[count - 1], null,
            count.toLong(), null, policy)
        val first = repository.writeDraft(ID, null, capture(25_000), 0, null, access) as CartographicStoreResult.Success
        val firstBytes = writtenBytes
        val second = repository.writeDraft(ID, null, capture(50_000), first.value.revision, null, access) as CartographicStoreResult.Success
        assertTrue("50 000 points retain linear journal writes: first=$firstBytes total=$writtenBytes", writtenBytes < firstBytes * 2.1)
        assertTrue(maximumCheckpoint <= TraceDraftJournal.MAX_CHECKPOINT_CHARACTERS)
        val restored = (repository.read(ID, access) as CartographicStoreResult.Success).value.snapshot
        assertEquals(points, restored.portions.single().points)
        val stopped = restored.copy(phase = TraceCapturePhase.STOPPED_DRAFT,
            events = restored.events + TraceCaptureEvent(TraceEventType.STOPPED, 51_002))
        val beforeSave = writtenBytes
        val saved = repository.save(ID, null, stopped, second.value.revision, null, access) as CartographicStoreResult.Success
        assertEquals(50_000, saved.value.snapshot.acceptedPointCount)
        assertEquals(stopped, (repository.read(ID, access) as CartographicStoreResult.Success).value.snapshot)
        assertTrue(journal.isEmpty())
        assertTrue(writtenBytes - beforeSave <= rows.getValue(ID).payloadJson.toByteArray().size + 128)
    }

    private fun token(fingerprint: String, account: String): CartographicScopeAuthority.Token = CartographicScopeAuthority.Token(
        Any(), BusinessReadAccess.AccessSnapshot(account, null, emptyList(), emptyList(), 0, 0), 1,
        scope, CartographicScopeAuthority.Binding("PARCEL", "parcel"), fingerprint)
    private fun snapshot(id: String): TraceCaptureSnapshot {
        val recorder = SegmentedTraceRecorder(id, TraceCapturePolicy(10.0, 15_000_000_000, 0.0, 20_000_000_000))
        val token = requireNotNull(recorder.start(1_000))
        recorder.accept(token, fix(45.0, 1_001), 1_001)
        recorder.accept(token, fix(45.001, 1_002), 1_002)
        return recorder.stop(1_003)
    }
    private fun fix(lat: Double, nanos: Long) = TraceFix(lat, 5.0, 4.0, nanos,
        TracePositionSource.PHONE_LOCATION, "fused", 123L, clockDomainId = "boot-a")
    companion object {
        private const val ID = "4c162c21-4241-43b5-a2e2-2f23a552e412"
        private const val OTHER = "d87d1132-d4f7-4881-b50c-fc4884191a50"
    }
}
