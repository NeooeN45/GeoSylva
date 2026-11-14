package com.forestry.counter.data.local

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.data.cartography.CartographicScopeAuthority
import com.forestry.counter.data.repository.CartographicTraceRepositoryImpl
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.domain.location.trace.*
import com.forestry.counter.domain.repository.*
import com.forestry.counter.domain.usecase.maps.MapDataScope
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Real Room 49/SQLCipher, real authority, synthetic fixes and a disposable TEST database only. */
@RunWith(AndroidJUnit4::class)
class CartographicTraceRepositoryRoomTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "TEST-cartographic-traces-${UUID.randomUUID()}.db"
    private lateinit var db: ForestryDatabase
    private lateinit var identity: BusinessReadIdentityFixture
    private lateinit var repository: CartographicTraceRepositoryImpl
    private lateinit var access: CartographicMeasurementAccess

    @Before fun before() = runBlocking {
        net.sqlcipher.database.SQLiteDatabase.loadLibs(context)
        identity = BusinessReadIdentityFixture()
        db = open()
        repository = newRepository()
        access = success(repository.captureAccess(MapDataScope.Free))
        assertEquals(49, db.openHelper.writableDatabase.version)
    }
    @After fun after() {
        if (::db.isInitialized) db.close()
        context.deleteDatabase(databaseName)
    }
    private fun open() = Room.databaseBuilder(context, ForestryDatabase::class.java, databaseName)
        .openHelperFactory(net.sqlcipher.database.SupportFactory("TEST-only-trace-journal".toByteArray()))
        .addMigrations(*DatabaseMigrations.ALL).build()
    private fun newRepository() = CartographicTraceRepositoryImpl(db.cartographicTraceDao(),
        CartographicScopeAuthority(db, identity, BusinessReadAccess(identity, db.parcelSyncDao())), clock = { 123_000L })

    @Test fun append_prefix_survives_encrypted_reopen_and_explicit_resume_commits_separate_portions() = runBlocking {
        val id = id()
        val recorder = SegmentedTraceRecorder(id, policy)
        val token = [REDACTED_SECRET]
        val firstSnapshot = recorder.accept(token, fix(45.1700, BASE + 1_000_000_000, "TEST-boot-A"), BASE + 1_000_000_000).snapshot
        val name = "TEST / nord \"chêne\""
        val first = success(repository.writeDraft(id, "  $name  ", firstSnapshot, 0, null, access))
        val immutablePrefix = db.cartographicTraceDao().journal(id).filter { it.kind == "POINT" || it.kind == "EVENT" }
        val secondSnapshot = recorder.accept(token, fix(45.1701, BASE + 2_000_000_000, "TEST-boot-A"), BASE + 2_000_000_000).snapshot
        val second = success(repository.writeDraft(id, name, secondSnapshot, first.revision, null, access))
        val rows = db.cartographicTraceDao().journal(id)
        assertEquals(2, rows.count { it.kind == "POINT" })
        immutablePrefix.forEach { old -> assertEquals(old, rows.single { it.kind == old.kind && it.ordinal == old.ordinal }) }
        assertTrue(requireNotNull(db.cartographicTraceDao().byId(id)).payloadJson.length <= 4_096)
        val oldAccess = access
        val originalPartition = access.restorationKey
        db.close()
        val header = ByteArray(16)
        context.getDatabasePath(databaseName).inputStream().use { assertEquals(16, it.read(header)) }
        assertNotEquals("SQLite format 3\u0000", String(header, Charsets.ISO_8859_1))
        db = open()
        repository = newRepository()
        access = success(repository.captureAccess(MapDataScope.Free))
        assertEquals(originalPartition, access.restorationKey)
        failure(CartographicStoreError.ACCESS_CHANGED, repository.read(id, oldAccess))
        val restored = success(repository.read(id, access))
        assertEquals(second, restored)
        assertEquals(name, restored.name)
        assertEquals(secondSnapshot, restored.snapshot)

        // A reboot uses a new monotonic domain; recovery never starts acquisition itself.
        val resumed = SegmentedTraceRecorder.restoreStopped(restored.snapshot)
        val review = resumed.snapshot(10_000_000_000)
        assertEquals(TraceCapturePhase.STOPPED_DRAFT, review.phase)
        assertEquals(restored.snapshot.portions, review.portions)
        val reviewDraft = success(repository.writeDraft(id, name, review, restored.revision, null, access))
        val next = requireNotNull(resumed.resume(20_000_000_000))
        resumed.accept(next, fix(45.1750, 21_000_000_000, "TEST-boot-B"), 21_000_000_000)
        resumed.accept(next, fix(45.1751, 22_000_000_000, "TEST-boot-B"), 22_000_000_000)
        val stopped = resumed.stop(30_000_000_000)
        val finalDraft = success(repository.writeDraft(id, name, stopped, reviewDraft.revision, null, access))
        assertEquals(4, db.cartographicTraceDao().journal(id).count { it.kind == "POINT" })
        val saved = success(repository.save(id, name, stopped, finalDraft.revision, null, access))
        assertTrue(saved.saved)
        assertEquals(stopped, saved.snapshot)
        assertEquals(2, saved.snapshot.portions.size)
        assertTrue("The unobserved >500 m passage must not enter the SI length", saved.snapshot.lengthM < 100)
        assertNull(saved.snapshot.overallTraceUncertaintyM)
        assertEquals(listOf("TEST-boot-A", "TEST-boot-B"), saved.snapshot.portions.map { it.points.first().clockDomainId })
        assertEquals("EPSG:4326", saved.coordinateReferenceSystem)
        assertEquals(SphericalTraceDistance.METHOD_ID, saved.methodId)
        assertTrue(db.cartographicTraceDao().journal(id).isEmpty())
        assertEquals(saved, success(repository.read(id, access)))
        assertEquals(listOf(saved), success(repository.list(access)))
        db.close()
        db = open()
        repository = newRepository()
        access = success(repository.captureAccess(MapDataScope.Free))
        assertEquals(saved, success(repository.read(id, access)))
        assertTrue(db.cartographicTraceDao().journal(id).isEmpty())
    }

    @Test fun expected_revision_conflicts_leave_journal_intact_and_identical_retries_keep_one_saved_identity() = runBlocking {
        val id = id()
        val snapshot = stoppedSnapshot(id)
        val draft = success(repository.writeDraft(id, "  ", snapshot, 0, null, access))
        val journal = db.cartographicTraceDao().journal(id)
        assertEquals(draft, success(repository.writeDraft(id, null, snapshot, 0, null, access)))
        assertEquals(journal, db.cartographicTraceDao().journal(id))
        failure(CartographicStoreError.CONFLICT, repository.writeDraft(id, "TEST competing name", snapshot, 0, null, access))
        failure(CartographicStoreError.CONFLICT, repository.save(id, null, snapshot, 0, null, access))
        assertEquals(journal, db.cartographicTraceDao().journal(id))
        assertEquals(draft, success(repository.read(id, access)))
        val saved = success(repository.save(id, null, snapshot, draft.revision, null, access))
        val immutable = db.cartographicTraceDao().byId(id)
        assertNull(saved.name)
        assertEquals(saved, success(repository.save(id, " ", snapshot, 0, null, access)))
        failure(CartographicStoreError.CONFLICT, repository.save(id, "TEST overwrite", snapshot, saved.revision, null, access))
        assertEquals(immutable, db.cartographicTraceDao().byId(id))
        assertEquals(1, db.cartographicTraceDao().inScope(access.restorationKey).size)
        assertTrue(db.cartographicTraceDao().journal(id).isEmpty())
    }

    @Test fun truncated_point_journal_refuses_restore_retry_and_commit_without_publishing_success() = runBlocking {
        val id = id()
        val snapshot = stoppedSnapshot(id)
        val draft = success(repository.writeDraft(id, "TEST journal truncated", snapshot, 0, null, access))
        val originalRow = db.cartographicTraceDao().byId(id)
        db.openHelper.writableDatabase.execSQL(
            "DELETE FROM cartographic_trace_journal WHERE traceId=? AND kind='POINT' AND ordinal=0", arrayOf(id))
        failure(CartographicStoreError.CORRUPT, repository.read(id, access))
        failure(CartographicStoreError.CORRUPT, repository.list(access))
        failure(CartographicStoreError.CORRUPT, repository.writeDraft(id, draft.name, snapshot, draft.revision, null, access))
        failure(CartographicStoreError.CORRUPT, repository.save(id, draft.name, snapshot, draft.revision, null, access))
        assertEquals(originalRow, db.cartographicTraceDao().byId(id))
        assertFalse(requireNotNull(db.cartographicTraceDao().byId(id)).saved)
        assertEquals(1, db.cartographicTraceDao().journal(id).count { it.kind == "POINT" })
    }

    @Test fun revoked_accounts_and_purge_identity_deny_old_tokens_without_reassigning_trace_ownership() = runBlocking {
        val localId = id()
        success(repository.writeDraft(localId, "TEST local", stoppedSnapshot(localId), 0, null, access))
        val local = access
        identity.login("TEST-account-A")
        failure(CartographicStoreError.ACCESS_CHANGED, repository.read(localId, local))
        val deniedId = id()
        failure(CartographicStoreError.ACCESS_CHANGED, repository.writeDraft(deniedId, null, stoppedSnapshot(deniedId), 0, null, local))
        assertNull(db.cartographicTraceDao().byId(deniedId))
        access = success(repository.captureAccess(MapDataScope.Free))
        failure(CartographicStoreError.UNAVAILABLE, repository.read(localId, access))
        val accountId = id()
        val accountDraft = success(repository.writeDraft(accountId, "TEST account-A", stoppedSnapshot(accountId), 0, null, access))
        val accountA = access
        identity.login("TEST-account-B")
        failure(CartographicStoreError.ACCESS_CHANGED, repository.read(accountId, accountA))
        val accountB = success(repository.captureAccess(MapDataScope.Free))
        failure(CartographicStoreError.UNAVAILABLE, repository.read(accountId, accountB))
        assertTrue(success(repository.list(accountB)).isEmpty())
        identity.login("TEST-account-A")
        access = success(repository.captureAccess(MapDataScope.Free))
        assertEquals(accountDraft, success(repository.read(accountId, access)))
        val beforePurge = access
        db.woodDataGate.purge { db.withTransaction {
            db.cartographicTraceDao().deleteAll()
            db.cartographicMeasurementDao().deleteStoreIdentity()
        } }
        assertFalse(repository.isCurrent(beforePurge))
        failure(CartographicStoreError.ACCESS_CHANGED, repository.read(accountId, beforePurge))
        assertNull(db.cartographicTraceDao().byId(accountId))
        assertTrue(db.cartographicTraceDao().journal(accountId).isEmpty())
        assertTrue(db.cartographicTraceDao().journal(localId).isEmpty())
        access = success(repository.captureAccess(MapDataScope.Free))
        assertNotEquals(beforePurge.restorationKey, access.restorationKey)
        assertTrue(success(repository.list(access)).isEmpty())
    }

    private fun stoppedSnapshot(id: String): TraceCaptureSnapshot {
        val recorder = SegmentedTraceRecorder(id, policy)
        val token = [REDACTED_SECRET]
        recorder.accept(token, fix(45.1700, BASE + 1_000_000_000, "TEST-boot-A"), BASE + 1_000_000_000)
        recorder.accept(token, fix(45.1701, BASE + 2_000_000_000, "TEST-boot-A"), BASE + 2_000_000_000)
        return recorder.stop(BASE + 3_000_000_000)
    }
    private fun fix(latitude: Double, nanos: Long, boot: String) = TraceFix(latitude, 5.4900, 4.0, nanos,
        TracePositionSource.PHONE_LOCATION, "TEST-fused-fixture", 1_700_000_000_000L, 900.0, clockDomainId = boot)
    private fun id() = UUID.randomUUID().toString()
    private fun <T> success(result: CartographicStoreResult<T>): T = when (result) {
        is CartographicStoreResult.Success -> result.value
        is CartographicStoreResult.Failure -> throw AssertionError("Unexpected ${result.reason}")
    }
    private fun failure(expected: CartographicStoreError, result: CartographicStoreResult<*>) {
        assertEquals(CartographicStoreResult.Failure(expected), result)
    }
    private val policy = TraceCapturePolicy(10.0, 15_000_000_000, 0.0, 20_000_000_000)
    companion object { private const val BASE = 100_000_000_000L }
}
