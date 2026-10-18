package com.forestry.counter.data.local

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.data.repository.WoodDepotRepositoryImpl
import com.forestry.counter.domain.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Dedicated TEST database and synthetic identity; no user database, tokens or settings. */
@RunWith(AndroidJUnit4::class)
class WoodDepotRoomTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: ForestryDatabase
    private lateinit var identity: BusinessReadIdentityFixture
    private var time = 100L

    @Before fun setup() {
        context.deleteDatabase(DB_NAME)
        db = open()
        identity = BusinessReadIdentityFixture()
        time = 100
    }

    @After fun cleanup() { db.close(); context.deleteDatabase(DB_NAME) }

    @Test fun empty_depot_lists_have_no_placeholder_and_real_guest_dossier_survives_reopen() = runBlocking {
        var repo = repository()
        assertTrue(repo.observeDepots().first().isEmpty())
        val saved = repo.createDepot(ID, draft(), repo.captureAccess())
        assertNull(saved.draft.location)
        assertEquals(WoodDepotOwnerScope.LOCAL_DEVICE, saved.ownerScope)
        assertNull(saved.ownerAccountId)
        assertEquals("anonymous", saved.createdBy)
        assertEquals(1, saved.revision)
        db.close()
        db = open()
        repo = repository()
        assertEquals(saved, repo.getDepot(ID, repo.captureAccess()))
        assertEquals(listOf(saved), repo.observeDepots().first())
    }

    @Test fun guest_A_and_B_remain_separate_without_adopting_local_dossiers_on_login() = runBlocking {
        val repo = repository()
        val guest = repo.createDepot(ID, draft(), repo.captureAccess())
        identity.login("TEST-A")
        val a = repo.createDepot(ID_A, draft().copy(name = "TEST A"), repo.captureAccess())
        assertEquals("TEST-A", a.createdBy)
        assertEquals(WoodDepotOwnerScope.PERSONAL, a.ownerScope)
        assertNull(repo.getDepot(ID, repo.captureAccess()))
        assertEquals(listOf(a), repo.observeDepots().first())
        identity.login("TEST-B")
        val b = repo.createDepot(ID_B, draft().copy(name = "TEST B"), repo.captureAccess())
        assertNull(repo.getDepot(ID_A, repo.captureAccess()))
        assertEquals(listOf(b), repo.observeDepots().first())
        refusal("WOOD_DEPOT_UNAVAILABLE") { repo.updateDepot(ID_A, 1, draft(), repo.captureAccess()) }
        refusal("WOOD_DEPOT_CREATE_CONFLICT") { repo.createDepot(ID_A, draft(), repo.captureAccess()) }
        identity.login(null)
        assertEquals(listOf(guest), repo.observeDepots().first())
        assertNull(repo.getDepot(ID_A, repo.captureAccess()))
        identity.login("TEST-A")
        assertEquals(a, repo.getDepot(ID_A, repo.captureAccess()))
        assertEquals("TEST-A", db.woodDepotDao().getByIdIncludingDeleted(ID_A)?.ownerAccountId)
    }

    @Test fun create_retry_is_idempotent_even_after_edit_and_never_overwrites_a_conflicting_request() = runBlocking {
        val repo = repository()
        val access = repo.captureAccess()
        val initial = repo.createDepot(ID, draft(), access)
        time = 200
        assertEquals(initial, repo.createDepot(ID, draft(), access))
        val changed = repo.updateDepot(ID, 1, draft().copy(name = "TEST corrected"), access)
        assertEquals(2, changed.revision)
        assertEquals(100L, changed.createdAt)
        assertEquals(200L, changed.updatedAt)
        assertEquals(changed, repo.createDepot(ID, draft(), access))
        refusal("WOOD_DEPOT_CREATE_CONFLICT") { repo.createDepot(ID, draft().copy(notes = "TEST different"), access) }
        assertEquals(changed, repo.getDepot(ID, access))
    }

    @Test fun stale_revisions_cannot_edit_or_delete_and_soft_deletion_retains_the_original_dossier() = runBlocking {
        val repo = repository()
        val access = repo.captureAccess()
        repo.createDepot(ID, draft(), access)
        val updated = repo.updateDepot(ID, 1, draft().copy(location = WoodDepotLocation(46.0, -0.4, 9.0, WoodDepotLocationSource.GPS)), access)
        refusal("WOOD_DEPOT_REVISION_CONFLICT") { repo.updateDepot(ID, 1, draft(), access) }
        refusal("WOOD_DEPOT_REVISION_CONFLICT") { repo.deleteDepot(ID, 1, access) }
        assertEquals(updated, repo.getDepot(ID, access))
        time = 300
        val deleted = repo.deleteDepot(ID, 2, access)
        assertEquals(3, deleted.revision)
        assertEquals(300L, deleted.deletedAt)
        assertEquals(updated.draft, deleted.draft)
        assertNull(repo.getDepot(ID, access))
        assertTrue(repo.observeDepots().first().isEmpty())
        assertEquals(3, db.woodDepotDao().getByIdIncludingDeleted(ID)?.revision)
        refusal("WOOD_DEPOT_CREATE_CONFLICT") { repo.createDepot(ID, draft(), access) }
        refusal("WOOD_DEPOT_UNAVAILABLE") { repo.updateDepot(ID, 3, draft(), access) }
    }

    @Test fun old_generations_and_foreign_repository_guards_never_rebind_to_the_current_account() = runBlocking {
        val repo = repository()
        val guestAccess = repo.captureAccess()
        identity.login(null)
        assertFalse(repo.isAccessCurrent(guestAccess))
        refusal("WOOD_DEPOT_ACCESS_CHANGED") { repo.createDepot(ID, draft(), guestAccess) }
        identity.login("TEST-A")
        val access = repo.captureAccess()
        val saved = repo.createDepot(ID_A, draft(), access)
        identity.login(null)
        identity.login("TEST-A")
        assertFalse(repo.isAccessCurrent(access))
        refusal("WOOD_DEPOT_ACCESS_CHANGED") { repo.getDepot(ID_A, access) }
        refusal("WOOD_DEPOT_ACCESS_CHANGED") { repo.updateDepot(ID_A, 1, draft(), access) }
        refusal("WOOD_DEPOT_ACCESS_CHANGED") { repo.deleteDepot(ID_A, 1, access) }
        val other = repository()
        val foreign = other.captureAccess()
        assertFalse(repo.isAccessCurrent(foreign))
        refusal("WOOD_DEPOT_ACCESS_CHANGED") { repo.getDepot(ID_A, foreign) }
        assertEquals(saved, repo.getDepot(ID_A, repo.captureAccess()))
    }

    @Test fun identity_changed_inside_create_edit_or_delete_rolls_back_the_real_transaction() = runBlocking {
        identity.login("TEST-A")
        val repo = repository()
        val access = repo.captureAccess()
        val saved = repo.createDepot(ID_A, draft(), access)
        for (operation in listOf("create", "update", "delete")) {
            identity.login("TEST-A")
            val changing = WoodDepotRepositoryImpl(db, identity) {
                identity.login("TEST-B")
                400L
            }
            val pending = changing.captureAccess()
            refusal("WOOD_DEPOT_ACCESS_CHANGED") {
                when (operation) {
                    "create" -> changing.createDepot(ID_B, draft(), pending)
                    "update" -> changing.updateDepot(ID_A, 1, draft().copy(name = "TEST late"), pending)
                    else -> changing.deleteDepot(ID_A, 1, pending)
                }
            }
            assertNull(db.woodDepotDao().getByIdIncludingDeleted(ID_B))
            identity.login("TEST-A")
            assertEquals(saved, repo.getDepot(ID_A, repo.captureAccess()))
        }
    }

    @Test fun queued_read_cannot_expose_A_data_after_identity_changes_while_waiting_for_SQL() = runBlocking {
        withTimeout(10_000) {
            identity.login("TEST-A")
            val repo = repository()
            val access = repo.captureAccess()
            val saved = repo.createDepot(ID_A, draft(), access)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val blocking = launch {
                db.withTransaction { entered.complete(Unit); release.await() }
            }
            entered.await()
            val reading = async(start = CoroutineStart.UNDISPATCHED) {
                runCatching { repo.getDepot(ID_A, access) }
            }
            identity.login("TEST-B")
            release.complete(Unit)
            blocking.join()
            assertEquals("WOOD_DEPOT_ACCESS_CHANGED", reading.await().exceptionOrNull()?.message)
            identity.login("TEST-A")
            assertEquals(saved, repo.getDepot(ID_A, repo.captureAccess()))
        }
    }

    @Test fun observer_clears_captured_rows_on_login_and_guest_records_are_preserved_not_reassigned() = runBlocking {
        withTimeout(10_000) {
            val repo = repository()
            val guest = repo.createDepot(ID, draft(), repo.captureAccess())
            val seen = CompletableDeferred<Unit>()
            val cleared = CompletableDeferred<Unit>()
            val job = launch {
                repo.observeDepots().collect { rows ->
                    if (rows == listOf(guest)) seen.complete(Unit)
                    if (seen.isCompleted && rows.isEmpty()) cleared.complete(Unit)
                }
            }
            try {
                seen.await()
                identity.login("TEST-A")
                cleared.await()
                assertNull(repo.getDepot(ID, repo.captureAccess()))
                assertEquals("LOCAL_DEVICE", db.woodDepotDao().getByIdIncludingDeleted(ID)?.ownerScope)
                assertNull(db.woodDepotDao().getByIdIncludingDeleted(ID)?.ownerAccountId)
            } finally { job.cancel(); job.join() }
        }
    }

    @Test fun explicit_physical_purge_removes_active_and_deleted_dossiers() = runBlocking {
        val repo = repository()
        val access = repo.captureAccess()
        repo.createDepot(ID, draft(), access)
        repo.createDepot(ID_B, draft().copy(name = "TEST second"), access)
        repo.deleteDepot(ID, 1, access)
        db.withTransaction { db.woodDepotDao().hardDeleteAll() }
        assertNull(db.woodDepotDao().getByIdIncludingDeleted(ID))
        assertNull(db.woodDepotDao().getByIdIncludingDeleted(ID_B))
        assertTrue(repo.observeDepots().first().isEmpty())
    }

    @Test fun observer_rereads_current_revision_and_tombstone_after_update_and_delete_notifications() = runBlocking {
        withTimeout(10_000) {
            val repo = repository()
            val access = repo.captureAccess()
            val initial = repo.createDepot(ID, draft(), access)
            val first = CompletableDeferred<Unit>()
            val updated = CompletableDeferred<Unit>()
            val deleted = CompletableDeferred<Unit>()
            val job = launch {
                repo.observeDepots().collect { records ->
                    if (records == listOf(initial)) first.complete(Unit)
                    if (records.singleOrNull()?.revision == 2 && records.single().draft.name == "TEST changed") {
                        updated.complete(Unit)
                    }
                    if (updated.isCompleted && records.isEmpty()) deleted.complete(Unit)
                }
            }
            try {
                first.await()
                repo.updateDepot(ID, 1, draft().copy(name = "TEST changed"), access)
                updated.await()
                repo.deleteDepot(ID, 2, access)
                deleted.await()
                assertTrue(db.woodDepotDao().getActive("LOCAL_DEVICE", null).isEmpty())
                assertEquals(3, db.woodDepotDao().getByIdIncludingDeleted(ID)?.revision)
            } finally { job.cancel(); job.join() }
        }
    }

    @Test fun maximum_revision_cannot_overflow_into_a_negative_or_reusable_revision() = runBlocking {
        val repo = repository()
        val access = repo.captureAccess()
        repo.createDepot(ID, draft(), access)
        db.openHelper.writableDatabase.execSQL("UPDATE wood_depots SET revision = 2147483647 WHERE depotId = ?", arrayOf(ID))
        val before = repo.getDepot(ID, access)
        refusal("WOOD_DEPOT_REVISION_CONFLICT") { repo.updateDepot(ID, Int.MAX_VALUE, draft().copy(name = "TEST overflow"), access) }
        refusal("WOOD_DEPOT_REVISION_CONFLICT") { repo.deleteDepot(ID, Int.MAX_VALUE, access) }
        assertEquals(before, repo.getDepot(ID, access))
        assertNull(db.woodDepotDao().getByIdIncludingDeleted(ID)?.deletedAt)
    }

    private fun repository() = WoodDepotRepositoryImpl(db, identity) { time }
    private fun draft() = WoodDepotDraft("TEST dépôt indépendant", WoodDepotKind.ROADSIDE, "TEST accès déclaré")
    private fun open() = Room.databaseBuilder(context, ForestryDatabase::class.java, DB_NAME)
        .addMigrations(*DatabaseMigrations.ALL).build()

    private suspend fun refusal(message: String, block: suspend () -> Unit) {
        try { block(); fail("TEST operation must fail closed: $message") }
        catch (error: IllegalStateException) { assertEquals(message, error.message) }
    }

    private companion object {
        const val DB_NAME = "TEST-wood-depot-local-repository.db"
        const val ID = "a0000000-0000-0000-0000-000000000001"
        const val ID_A = "a0000000-0000-0000-0000-000000000002"
        const val ID_B = "a0000000-0000-0000-0000-000000000003"
    }
}
