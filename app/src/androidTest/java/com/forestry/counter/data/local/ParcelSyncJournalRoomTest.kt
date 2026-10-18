package com.forestry.counter.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.data.local.entity.ParcelleEntity
import com.forestry.counter.data.local.entity.EssenceEntity
import com.forestry.counter.data.local.entity.PlacetteEntity
import com.forestry.counter.data.local.entity.ParcelSyncEntity
import com.forestry.counter.data.local.entity.ParcelSyncOperationEntity
import com.forestry.counter.data.local.entity.InventaireSessionEntity
import com.forestry.counter.data.local.entity.TigeEntity
import com.forestry.counter.data.repository.ParcelleRepositoryImpl
import com.forestry.counter.data.service.MetadataService
import com.forestry.counter.data.sync.ParcelMutationJournal
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Room/SQLite transaction, migration and reopen tests on synthetic fixtures.
 * This suite uses genuine schemas 36 and 39 with the actual intermediate SQL migrations. It does not
 * validate SQLCipher, HTTP delivery, logout atomicity or organisation isolation.
 */
@RunWith(AndroidJUnit4::class)
class ParcelSyncJournalRoomTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        "com.forestry.counter.data.local.ForestryDatabase",
        FrameworkSQLiteOpenHelperFactory(),
    )

    private lateinit var database: ForestryDatabase
    private lateinit var queryExecutor: ExecutorService
    private lateinit var transactionExecutor: ExecutorService
    private val filesToDelete = mutableListOf<Pair<Context, String>>()

    @Before
    fun setup() {
        queryExecutor = Executors.newFixedThreadPool(2)
        transactionExecutor = Executors.newFixedThreadPool(2)
        database = Room.inMemoryDatabaseBuilder(testContext(), ForestryDatabase::class.java)
            .setQueryExecutor(queryExecutor)
            .setTransactionExecutor(transactionExecutor)
            .build()
    }

    @After
    fun teardown() {
        database.close()
        queryExecutor.shutdownNow()
        transactionExecutor.shutdownNow()
        filesToDelete.forEach { (context, name) -> context.deleteDatabase(name) }
    }

    @Test
    fun journal_migration_preserves_every_legacy_state_and_starts_with_no_fake_history() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = newFile(context, "migration")
        val states = listOf("PENDING", "SYNCING", "SYNCED", "ERROR", "CONFLICT")
        val queries = listOf(
            "SELECT * FROM parcel_sync_queue ORDER BY parcelId",
            "SELECT * FROM parcelles ORDER BY parcelleId",
            "SELECT * FROM forets ORDER BY foretId",
            "SELECT * FROM inventaire_sessions ORDER BY sessionId",
        )
        val before = migrationHelper.createDatabase(name, 36).use { old ->
            // Schemas 37/38 were never exported by KSP. Execute their real migrations
            // rather than inventing schema files, then validate the final genuine schema39.
            DatabaseMigrations.MIGRATION_36_37.migrate(old)
            old.version = 37
            old.execSQL(
                """
                INSERT INTO forets (foretId, nom, proprietaireNom, createdAt, updatedAt, version,
                    shape, sourceForestId, sourceForestDataset)
                VALUES ('fixture-forest', 'Fixture forest', '', 100, 200, 1,
                    'POLYGON ((0 0, 1 0, 1 1, 0 0))', 'fixture-source-id', 'fixture-dataset')
                """.trimIndent(),
            )
            states.forEachIndexed { index, state ->
                val parcelId = "legacy-$state"
                old.execSQL(
                    """
                    INSERT INTO parcelles (parcelleId, name, remarks, createdAt, updatedAt, deletedAt, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf(parcelId, "Legacy $state", "Échantillon\nconservé", 100L, 200L, if (index == 4) 300L else null, index + 1),
                )
                old.execSQL(
                    """
                    INSERT INTO parcel_sync_queue (
                        accountId, parcelId, operation, operationId, state, serverVersion,
                        retryCount, queuedAt, lastAttemptAt, lastSuccessAt, nextAttemptAt, lastErrorCode
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf(
                        ACCOUNT_A, parcelId, if (index == 4) "DELETE" else "UPSERT", UUID.randomUUID().toString(),
                        state, if (index == 0) null else index + 1, index, 100L + index,
                        if (index == 0) null else 150L + index, if (index == 2) 180L else null,
                        500L + index, if (index >= 3) "LEGACY_$state" else null,
                    ),
                )
            }
            old.execSQL(
                """
                UPDATE parcelles SET foretId = 'fixture-forest', shape = 'POLYGON ((0 0, 1 0, 1 1, 0 0))',
                    geometrieIgnWkt = 'POLYGON ((0 0, 1 0, 1 1, 0 0))', srid = 4326,
                    source = 'fixture-cartographic-reference' WHERE parcelleId = 'legacy-SYNCED'
                """.trimIndent(),
            )
            old.execSQL(
                """
                INSERT INTO inventaire_sessions (sessionId, parcelleId, typeSession, dateDebut, createdAt,
                    martelageState, activeDurationMs, campaignId, revisionNumber, closedSnapshotJson,
                    recoveryRequired, version)
                VALUES ('fixture-session', 'legacy-SYNCED', 'MARTELAGE', 100, 100,
                    'CLOSED', 12345, 'fixture-campaign', 2, '{"fixture":true}', 0, 1)
                """.trimIndent(),
            )
            queries.map { snapshot(old, it) }
        }

        migrationHelper.runMigrationsAndValidate(
            name, 39, true, ParcelJournalMigrations.MIGRATION_37_38, HierarchySyncMigrations.MIGRATION_38_39,
        ).use { migrated ->
            queries.forEachIndexed { index, query -> assertEquals(query, before[index], snapshot(migrated, query)) }
            migrated.query("SELECT COUNT(*) FROM parcel_sync_operations").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
            migrated.query("PRAGMA foreign_key_list(parcel_sync_operations)").use {
                assertFalse("Journal must survive parcel REPLACE and physical deletion", it.moveToFirst())
            }
        }
    }

    @Test
    fun parcel_upsert_and_bulk_upsert_preserve_session_plots_and_trees() = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            val children = insertChildren(PARCEL_ID, "Before parent edit")
            val session = InventaireSessionEntity(
                sessionId = "fixture-parent-session", parcelleId = PARCEL_ID, typeSession = "MARTELAGE",
                dateDebut = 100L, dateFin = null, operateurNom = null, methode = null,
                intensiteEchantillonnagePct = null, objectifSession = null, remarques = null,
                createdAt = 100L, martelageState = "PAUSED", activeDurationMs = 12345L,
                campaignId = "fixture-campaign", revisionNumber = 2,
            )
            database.inventaireSessionDao().insert(session)
            val firstUpdate = children.parcel.copy(name = "Single safe upsert", updatedAt = 200L)
            database.parcelleDao().insertParcelle(firstUpdate)
            assertEquals(session, database.inventaireSessionDao().getById(session.sessionId))
            assertEquals(listOf(children.placette), database.placetteDao().getPlacettesByParcelle(PARCEL_ID).first())
            assertEquals(listOf(children.tige), database.tigeDao().getTigesByParcelle(PARCEL_ID).first())
            val bulkUpdate = firstUpdate.copy(name = "Bulk safe upsert", updatedAt = 300L)
            database.parcelleDao().insertParcelles(listOf(bulkUpdate))
            assertEquals(bulkUpdate, database.parcelleDao().getParcelleByIdAny(PARCEL_ID))
            assertEquals(session, database.inventaireSessionDao().getById(session.sessionId))
            assertEquals(listOf(children.placette), database.placetteDao().getPlacettesByParcelle(PARCEL_ID).first())
            assertEquals(listOf(children.tige), database.tigeDao().getTigesByParcelle(PARCEL_ID).first())
        }
    }

    @Test
    fun duplicate_operation_id_rolls_back_business_write_and_does_not_schedule_work() = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            val original = parcel()
            database.parcelleDao().insertParcelle(original)
            database.parcelSyncDao().upsert(checkpoint(version = 4))
            val duplicateId = UUID.randomUUID().toString()
            database.parcelSyncOperationDao().append(operation(duplicateId).copy(
                state = "SYNCED", ackVersion = 5, ackAt = 100L,
            ))
            var scheduled = false
            val journal = coordinator(onCommitted = { scheduled = true }, newOperationId = { duplicateId })

            val failure = runCatching {
                journal.mutate(PARCEL_ID, "UPSERT") {
                    database.parcelleDao().updateParcelle(original.copy(name = "Must roll back", updatedAt = 200L))
                }
            }.exceptionOrNull()

            assertNotNull("ABORT must propagate out of the transaction", failure)
            assertEquals(original, database.parcelleDao().getParcelleByIdAny(PARCEL_ID))
            assertEquals(1, database.parcelSyncOperationDao().getAllForParcel(ACCOUNT_A, PARCEL_ID).size)
            assertEquals(checkpoint(version = 4), database.parcelSyncDao().get(ACCOUNT_A, PARCEL_ID))
            assertFalse(scheduled)
        }
    }

    @Test
    fun concurrent_business_mutations_commit_distinct_frozen_requests_in_fifo_order() = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            val original = parcel()
            database.parcelleDao().insertParcelle(original)
            database.parcelSyncDao().upsert(checkpoint(version = 4))
            val journal = coordinator()
            val firstWrite = CompletableDeferred<Unit>()
            val allowFirstCommit = CompletableDeferred<Unit>()
            val first = async(Dispatchers.Default) {
                journal.mutate(PARCEL_ID, "UPSERT") {
                    database.parcelleDao().updateParcelle(original.copy(name = "First", updatedAt = 200L))
                    firstWrite.complete(Unit)
                    allowFirstCommit.await()
                }
            }
            firstWrite.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) {
                journal.mutate(PARCEL_ID, "UPSERT") {
                    database.parcelleDao().updateParcelle(original.copy(name = "Second", updatedAt = 300L))
                }
            }
            try {
                assertNull(withTimeoutOrNull(BLOCKED_OBSERVATION_MS) { second.await(); true })
                assertFalse(second.isCompleted)
            } finally {
                allowFirstCommit.complete(Unit)
            }
            first.await()
            second.await()

            val operations = database.parcelSyncOperationDao().getAllForParcel(ACCOUNT_A, PARCEL_ID)
            assertEquals(2, operations.size)
            val one = operations[0]
            val two = operations[1]
            assertTrue(one.sequence < two.sequence)
            assertTrue(one.operationId != two.operationId)
            assertEquals("First", ParcelMutationJournal.upsertRequest(one).parcel.name)
            assertEquals("Second", ParcelMutationJournal.upsertRequest(two).parcel.name)
            assertEquals(4, one.baseVersion)
            assertEquals(5, one.expectedServerVersion)
            assertEquals(5, two.baseVersion)
            assertEquals(6, two.expectedServerVersion)
            assertEquals(one.operationId, two.predecessorOperationId)
            assertEquals(listOf(one.operationId), ready().map { it.operationId })
            assertEquals("Second", database.parcelleDao().getParcelleByIdAny(PARCEL_ID)?.name)
        }
    }

    @Test
    fun parcel_upsert_soft_delete_and_physical_delete_do_not_cascade_into_journal() = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            val original = parcel()
            database.parcelleDao().insertParcelle(original)
            database.parcelSyncDao().upsert(checkpoint(version = 4))
            coordinator().mutate(PARCEL_ID, "UPSERT") {
                database.parcelleDao().updateParcelle(original.copy(name = "Frozen", updatedAt = 200L))
            }
            val saved = checkNotNull(database.parcelSyncOperationDao().getLastForParcel(ACCOUNT_A, PARCEL_ID))

            database.parcelleDao().insertParcelle(original.copy(name = "Replacement", updatedAt = 300L))
            database.parcelleDao().deleteParcelle(PARCEL_ID, 400L)
            assertEquals(saved, database.parcelSyncOperationDao().getByOperationId(saved.operationId))
            database.parcelleDao().hardDeleteAll()
            assertNull(database.parcelleDao().getParcelleByIdAny(PARCEL_ID))
            assertEquals(saved, database.parcelSyncOperationDao().getByOperationId(saved.operationId))
            assertEquals("Frozen", ParcelMutationJournal.upsertRequest(saved).parcel.name)
        }
    }

    @Test
    fun stale_ack_cannot_update_receipt_or_checkpoint_even_when_attempt_timestamps_are_equal() = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            val dao = database.parcelSyncOperationDao()
            database.parcelleDao().insertParcelle(parcel())
            database.parcelSyncDao().upsert(checkpoint(version = 4))
            val id = UUID.randomUUID().toString()
            dao.append(operation(id))
            val initial = checkNotNull(dao.getByOperationId(id))
            val leaseOne = UUID.randomUUID().toString()
            val leaseTwo = UUID.randomUUID().toString()
            assertEquals(1, dao.claimIfCurrent(id, ACCOUNT_A, "PENDING", null, null, 150L, leaseOne))
            assertEquals(1, dao.claimIfCurrent(id, ACCOUNT_A, "SYNCING", 150L, leaseOne, 150L, leaseTwo))

            assertEquals(0, completeWithCheckpoint(id, leaseOne, 5, 200L))
            assertEquals(4, database.parcelSyncDao().get(ACCOUNT_A, PARCEL_ID)?.serverVersion)
            assertEquals("SYNCING", dao.getByOperationId(id)?.state)
            assertEquals(leaseTwo, dao.getByOperationId(id)?.attemptId)
            assertEquals(0, completeWithCheckpoint(id, leaseTwo, 6, 210L))
            assertEquals(4, database.parcelSyncDao().get(ACCOUNT_A, PARCEL_ID)?.serverVersion)
            assertEquals(1, completeWithCheckpoint(id, leaseTwo, 5, 220L))
            val completed = checkNotNull(dao.getByOperationId(id))
            assertEquals("SYNCED", completed.state)
            assertEquals(5, completed.ackVersion)
            assertEquals(220L, completed.ackAt)
            assertEquals(5, database.parcelSyncDao().get(ACCOUNT_A, PARCEL_ID)?.serverVersion)
            assertImmutableContent(initial, completed)
        }
    }

    @Test
    fun deferred_or_conflicted_head_blocks_successors_and_summary_counts_parcels() = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            val dao = database.parcelSyncOperationDao()
            val headId = UUID.randomUUID().toString()
            val childId = UUID.randomUUID().toString()
            dao.append(operation(headId).copy(state = "ERROR", nextAttemptAt = 500L))
            val head = checkNotNull(dao.getByOperationId(headId))
            dao.append(operation(childId).copy(baseVersion = 5, expectedServerVersion = 6, predecessorOperationId = headId))
            val child = checkNotNull(dao.getByOperationId(childId))
            val otherId = UUID.randomUUID().toString()
            dao.append(operation(otherId, parcelId = "other-parcel"))
            val foreignId = UUID.randomUUID().toString()
            dao.append(operation(foreignId).copy(originAccountId = ACCOUNT_B))
            assertEquals(listOf(otherId), dao.getReady(ACCOUNT_A, 200L, 0L, 20).map { it.operationId })
            assertEquals(listOf(foreignId), dao.getReady(ACCOUNT_B, 200L, 0L, 20).map { it.operationId })

            val lease = UUID.randomUUID().toString()
            assertEquals(1, dao.claimIfCurrent(headId, ACCOUNT_A, "ERROR", null, null, 500L, lease))
            dao.runJournalTransaction {
                assertEquals(1, dao.markFailureIfClaim(headId, ACCOUNT_A, lease, "CONFLICT", 1, Long.MAX_VALUE, "SYNC_VERSION_CONFLICT"))
                assertEquals(1, dao.blockFollowing(ACCOUNT_A, PARCEL_ID, head.sequence, "PREDECESSOR_CONFLICT"))
            }
            assertEquals("BLOCKED_PREDECESSOR", dao.getByOperationId(childId)?.state)
            assertImmutableContent(child, checkNotNull(dao.getByOperationId(childId)))
            assertEquals(listOf(otherId), dao.getReady(ACCOUNT_A, 1_000L, 900L, 20).map { it.operationId })
            repeat(2) { index ->
                dao.append(operation(UUID.randomUUID().toString(), parcelId = "acked-parcel").copy(
                    state = "SYNCED", ackVersion = 5 + index, ackAt = 700L + index,
                ))
            }
            val counts = dao.observeCounts(ACCOUNT_A).first()
            assertEquals(1, counts.conflicts)
            assertEquals(0, counts.errors)
            assertEquals(1, counts.pending)
            assertEquals(1, counts.synced)
            assertEquals(701L, counts.lastSuccessAt)
        }
    }

    @Test
    fun local_only_and_unknown_api_origin_are_retained_but_never_ready() = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            val dao = database.parcelSyncOperationDao()
            val localId = UUID.randomUUID().toString()
            dao.append(operation(localId).copy(
                originAccountId = null, destinationKind = null, apiOrigin = null,
                state = "LOCAL_ONLY", baseVersion = null, expectedServerVersion = null,
            ))
            val blockedId = UUID.randomUUID().toString()
            dao.append(operation(blockedId, parcelId = "unknown-origin").copy(
                apiOrigin = null, state = "BLOCKED_API_ORIGIN",
            ))
            assertEquals(localId, dao.getLastForParcel(null, PARCEL_ID)?.operationId)
            assertEquals(listOf(localId), dao.getAllForParcel(null, PARCEL_ID).map { it.operationId })
            assertFalse(dao.hasAnyOperations(ACCOUNT_A, PARCEL_ID))
            assertTrue(dao.getReady(ACCOUNT_A, 1_000L, 900L, 20).isEmpty())
            val counts = dao.observeCounts(ACCOUNT_A).first()
            assertEquals(1, counts.errors)
            assertEquals(1, counts.blocked)
            assertEquals(0, counts.pending)
        }
    }

    @Test
    fun repository_delete_rolls_back_children_and_parcel_when_journal_append_aborts() = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            val fixture = insertChildren(PARCEL_ID, "Original")
            database.parcelSyncDao().upsert(checkpoint(version = 4))
            val duplicateId = UUID.randomUUID().toString()
            database.parcelSyncOperationDao().append(operation(duplicateId).copy(
                state = "SYNCED", ackVersion = 5, ackAt = 100L,
            ))
            val saved = checkNotNull(database.parcelSyncOperationDao().getByOperationId(duplicateId))
            val repository = parcelRepository(coordinator(newOperationId = { duplicateId }))

            val failure = runCatching { repository.deleteParcelle(PARCEL_ID) }.exceptionOrNull()

            assertNotNull(failure)
            assertChildrenUnchanged(fixture)
            assertEquals(saved, database.parcelSyncOperationDao().getByOperationId(duplicateId))
            assertEquals(1, database.parcelSyncOperationDao().getAllForParcel(ACCOUNT_A, PARCEL_ID).size)
        }
    }

    @Test
    fun repository_delete_all_rolls_back_first_append_and_all_children_when_second_append_aborts() = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            val first = insertChildren(PARCEL_ID, "A first parcel")
            val second = insertChildren("second-parcel", "B second parcel")
            database.parcelSyncDao().upsert(checkpoint(version = 4))
            database.parcelSyncDao().upsert(checkpoint(version = 4).copy(parcelId = second.parcel.parcelleId))
            val firstId = UUID.randomUUID().toString()
            val duplicateId = UUID.randomUUID().toString()
            database.parcelSyncOperationDao().append(operation(duplicateId, second.parcel.parcelleId).copy(
                state = "SYNCED", ackVersion = 5, ackAt = 100L,
            ))
            val ids = listOf(firstId, duplicateId).iterator()
            val repository = parcelRepository(coordinator(newOperationId = { ids.next() }))

            val failure = runCatching { repository.deleteAllParcelles() }.exceptionOrNull()

            assertNotNull(failure)
            assertChildrenUnchanged(first)
            assertChildrenUnchanged(second)
            assertNull(database.parcelSyncOperationDao().getByOperationId(firstId))
            assertEquals(1, database.parcelSyncOperationDao().getAllForParcel(ACCOUNT_A, second.parcel.parcelleId).size)
            assertEquals(checkpoint(version = 4), database.parcelSyncDao().get(ACCOUNT_A, PARCEL_ID))
            assertEquals(checkpoint(version = 4).copy(parcelId = second.parcel.parcelleId),
                database.parcelSyncDao().get(ACCOUNT_A, second.parcel.parcelleId))
        }
    }

    @Test
    fun legacy_checkpoint_takes_summary_priority_and_wake_count_excludes_permanent_errors() = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            val original = parcel()
            database.parcelleDao().insertParcelle(original)
            database.parcelSyncDao().upsert(checkpoint(version = 4).copy(state = "ERROR"))
            coordinator().mutate(PARCEL_ID, "UPSERT") {
                database.parcelleDao().updateParcelle(original.copy(name = "Legacy local correction"))
            }
            val dao = database.parcelSyncOperationDao()
            assertEquals("BLOCKED_LEGACY", dao.getLastForParcel(ACCOUNT_A, PARCEL_ID)?.state)
            var checkpoints = database.parcelSyncDao().observeCheckpointCounts(ACCOUNT_A).first()
            var operations = dao.observeCounts(ACCOUNT_A).first()
            assertEquals(1, checkpoints.errors)
            assertEquals(0, operations.errors)
            assertEquals(1, database.parcelSyncDao().getLegacyRecoverable(ACCOUNT_A).size)

            database.parcelSyncDao().upsert(checkpoint(version = 4).copy(state = "CONFLICT"))
            coordinator().mutate(PARCEL_ID, "UPSERT") {
                database.parcelleDao().updateParcelle(original.copy(name = "Another local correction"))
            }
            checkpoints = database.parcelSyncDao().observeCheckpointCounts(ACCOUNT_A).first()
            operations = dao.observeCounts(ACCOUNT_A).first()
            assertEquals(1, checkpoints.conflicts)
            assertEquals(0, checkpoints.errors)
            assertEquals(0, operations.errors)
            assertEquals(0, operations.conflicts)
            assertTrue(database.parcelSyncDao().getLegacyRecoverable(ACCOUNT_A).isEmpty())

            dao.append(operation(UUID.randomUUID().toString(), "permanent-error").copy(
                state = "ERROR", nextAttemptAt = Long.MAX_VALUE,
            ))
            assertEquals(0, dao.getPendingCountForAccount(ACCOUNT_A))
            dao.append(operation(UUID.randomUUID().toString(), "future-retry").copy(
                state = "ERROR", nextAttemptAt = 5_000L,
            ))
            dao.append(operation(UUID.randomUUID().toString(), "active-lease").copy(
                state = "SYNCING", lastAttemptAt = 200L, attemptId = UUID.randomUUID().toString(),
            ))
            assertEquals(2, dao.getPendingCountForAccount(ACCOUNT_A))
            assertTrue(dao.getReady(ACCOUNT_A, 300L, 100L, 20).isEmpty())
        }
    }

    @Test
    fun reopen_preserves_frozen_request_and_explicit_purge_removes_the_payload() = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            val context = testContext()
            val name = newFile(context, "reopen")
            val saved = withFileDatabase(context, name) { file ->
                file.parcelleDao().insertParcelle(parcel())
                file.parcelSyncDao().upsert(checkpoint(version = 4))
                val journal = coordinator(file)
                journal.mutate(PARCEL_ID, "UPSERT") {
                    file.parcelleDao().updateParcelle(parcel().copy(name = "Persisted", updatedAt = 200L))
                }
                checkNotNull(file.parcelSyncOperationDao().getLastForParcel(ACCOUNT_A, PARCEL_ID))
            }
            withFileDatabase(context, name) { reopened ->
                assertEquals(saved, reopened.parcelSyncOperationDao().getByOperationId(saved.operationId))
                assertEquals("Persisted", reopened.parcelleDao().getParcelleByIdAny(PARCEL_ID)?.name)
                reopened.parcelSyncOperationDao().runJournalTransaction {
                    assertEquals(1, reopened.parcelSyncOperationDao().hardDeleteAll())
                    reopened.parcelSyncDao().hardDeleteAll()
                    reopened.parcelleDao().hardDeleteAll()
                }
                assertNull(reopened.parcelSyncOperationDao().getByOperationId(saved.operationId))
                assertTrue(reopened.parcelSyncDao().getAccountIdsForParcel(PARCEL_ID).isEmpty())
                assertNull(reopened.parcelleDao().getParcelleByIdAny(PARCEL_ID))
            }
        }
    }

    private fun coordinator(
        db: ForestryDatabase = database,
        onCommitted: () -> Unit = {},
        newOperationId: () -> String = { UUID.randomUUID().toString() },
    ) = ParcelMutationJournal(
        db.parcelleDao(), db.parcelSyncDao(), db.parcelSyncOperationDao(),
        API_ORIGIN, { ACCOUNT_A }, onCommitted, { 100L }, newOperationId,
    )

    private fun parcelRepository(journal: ParcelMutationJournal) = ParcelleRepositoryImpl(
        database.parcelleDao(), MetadataService { ACCOUNT_A }, journal,
        database.tigeDao(), database.placetteDao(),
    )

    private data class ChildrenFixture(
        val parcel: ParcelleEntity,
        val placette: PlacetteEntity,
        val tige: TigeEntity,
    )

    private suspend fun insertChildren(parcelId: String, name: String): ChildrenFixture {
        val parcel = parcel(parcelId).copy(name = name)
        val placette = PlacetteEntity(
            placetteId = "placette-$parcelId", parcelleOwnerId = parcelId,
            name = "Synthetic plot", type = null, rayonM = null, surfaceM2 = null,
            centerWkt = null, sessionId = null, typeReleve = null, referenceGpsWkt = null,
            azimutRef = null, createdAt = 100L, updatedAt = 100L,
        )
        val tige = TigeEntity(
            tigeId = "tige-$parcelId", parcelleOwnerId = parcelId, placetteOwnerId = placette.placetteId,
            sessionId = null, essenceCode = "FIXTURE-$parcelId", diamCm = 30.0, hauteurM = null,
            gpsWkt = null, precisionM = null, altitudeM = null, timestamp = 100L,
            note = null, produit = null, fCoef = null, valueEur = null, numero = null,
            categorie = null, qualite = null, defauts = null, photoUri = null, qualiteDetail = null,
            classeKraft = null, etatSanitaire = null, vigueur = null, origine = null, typeCoupe = null,
            biomasseFusTonnes = null, carboneFusTonnes = null, coefficientElancement = null,
            houppierM = null, houppierPct = null, isTigeHabitat = false,
        )
        database.parcelleDao().insertParcelle(parcel)
        database.essenceDao().insertEssence(EssenceEntity("FIXTURE-$parcelId", "Synthetic species", null, null, null))
        database.placetteDao().insertPlacette(placette)
        database.tigeDao().insertTige(tige)
        return ChildrenFixture(parcel, placette, tige)
    }

    private suspend fun assertChildrenUnchanged(fixture: ChildrenFixture) {
        assertEquals(fixture.parcel, database.parcelleDao().getParcelleByIdAny(fixture.parcel.parcelleId))
        assertEquals(listOf(fixture.placette), database.placetteDao().getPlacettesByParcelle(fixture.parcel.parcelleId).first())
        assertEquals(listOf(fixture.tige), database.tigeDao().getTigesByParcelle(fixture.parcel.parcelleId).first())
    }

    private suspend fun ready() = database.parcelSyncOperationDao().getReady(ACCOUNT_A, 1_000L, 0L, 20)

    private suspend fun completeWithCheckpoint(id: String, lease: String, version: Int, at: Long): Int {
        var updated = 0
        database.parcelSyncOperationDao().runJournalTransaction {
            updated = database.parcelSyncOperationDao().markSuccessIfClaim(id, ACCOUNT_A, lease, version, at)
            if (updated == 1) database.parcelSyncDao().recordSuccess(ACCOUNT_A, PARCEL_ID, id, version, at)
        }
        return updated
    }

    private fun assertImmutableContent(before: ParcelSyncOperationEntity, after: ParcelSyncOperationEntity) {
        assertEquals(before, after.copy(
            state = before.state, retryCount = before.retryCount,
            lastAttemptAt = before.lastAttemptAt, attemptId = before.attemptId,
            nextAttemptAt = before.nextAttemptAt, lastErrorCode = before.lastErrorCode,
            ackVersion = before.ackVersion, ackAt = before.ackAt,
        ))
    }

    private fun operation(id: String, parcelId: String = PARCEL_ID) = ParcelMutationJournal.freeze(
        parcel(parcelId), "UPSERT", ACCOUNT_A, API_ORIGIN, 4, null, "PENDING", 100L, id,
    )

    private fun checkpoint(version: Int) = ParcelSyncEntity(
        accountId = ACCOUNT_A, parcelId = PARCEL_ID, operation = "UPSERT",
        operationId = "fixture-legacy-checkpoint", state = "SYNCED", serverVersion = version,
        retryCount = 0, queuedAt = 100L, lastAttemptAt = null, lastSuccessAt = 100L,
        nextAttemptAt = 100L, lastErrorCode = null,
    )

    private fun parcel(id: String = PARCEL_ID) = ParcelleEntity(
        parcelleId = id, forestOwnerId = null, foretId = null, name = "Synthetic parcel",
        surfaceHa = null, shape = null, slopePct = null, aspect = null, access = null,
        altitudeM = null, objectifType = null, objectifVal = null, tolerancePct = null,
        samplingMode = null, sampleAreaM2 = null, targetSpeciesCsv = null, srid = null,
        remarks = null, codeInseeCommune = null, nomCommune = null, sectionCadastrale = null,
        numeroCadastral = null, contenanceCadastraleHa = null, geometrieIgnWkt = null,
        natureCadastraleCode = null, localisationMode = null, codeSer = null, nomSer = null,
        createdAt = 100L, updatedAt = 100L,
    )

    private fun fileDatabase(context: Context, name: String) =
        Room.databaseBuilder(context, ForestryDatabase::class.java, name)
            .setQueryExecutor(queryExecutor)
            .setTransactionExecutor(transactionExecutor)
            .addMigrations(*DatabaseMigrations.ALL)
            .build()

    private suspend fun <T> withFileDatabase(
        context: Context,
        name: String,
        block: suspend (ForestryDatabase) -> T,
    ): T {
        val file = fileDatabase(context, name)
        return try {
            block(file)
        } finally {
            file.close()
        }
    }

    private fun newFile(context: Context, suffix: String): String =
        "parcel-journal-$suffix-${UUID.randomUUID()}.db".also { filesToDelete += context to it }

    private fun testContext() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun snapshot(db: SupportSQLiteDatabase, query: String): List<List<String?>> =
        db.query(query).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add((0 until cursor.columnCount).map { column ->
                        if (cursor.isNull(column)) null else cursor.getString(column)
                    })
                }
            }
        }

    private companion object {
        const val ACCOUNT_A = "fixture-account-A"
        const val ACCOUNT_B = "fixture-account-B"
        const val PARCEL_ID = "fixture-parcel"
        const val API_ORIGIN = "https://fixture.invalid"
        const val TEST_TIMEOUT_MS = 10_000L
        const val BLOCKED_OBSERVATION_MS = 250L
    }
}
