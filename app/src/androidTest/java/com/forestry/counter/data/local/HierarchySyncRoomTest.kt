package com.forestry.counter.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.data.local.entity.*
import com.forestry.counter.data.mapper.*
import com.forestry.counter.data.sync.hierarchy.*
import com.forestry.counter.data.sync.ParcelMutationJournal
import com.forestry.counter.data.repository.ForetRepositoryImpl
import com.forestry.counter.data.repository.ParcelleRepositoryImpl
import com.forestry.counter.data.service.MetadataService
import com.forestry.counter.domain.model.*
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room/SQLite on synthetic dossiers, never a pilot database or SQLCipher key. */
@RunWith(AndroidJUnit4::class)
class HierarchySyncRoomTest {
    @get:Rule val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), "com.forestry.counter.data.local.ForestryDatabase",
        FrameworkSQLiteOpenHelperFactory(),
    )
    private lateinit var database: ForestryDatabase
    private val files = mutableListOf<String>()
    @Before fun setup() { database = Room.inMemoryDatabaseBuilder(context(), ForestryDatabase::class.java).build() }
    @After fun teardown() { database.close(); files.forEach { context().deleteDatabase(it) } }

    @Test fun migration_adds_empty_hierarchy_tables_without_activating_or_reassigning_existing_dossiers() {
        val name = fileName("migration")
        val before = migrationHelper.createDatabase(name, 36).use { old ->
            DatabaseMigrations.MIGRATION_36_37.migrate(old)
            ParcelJournalMigrations.MIGRATION_37_38.migrate(old)
            old.version = 38
            old.execSQL("""
                INSERT INTO forets (foretId,nom,proprietaireNom,createdAt,updatedAt,version,shape,sourceForestId,sourceForestDataset)
                VALUES ('$FOREST','Synthetic forest','',100,200,1,'POLYGON ((0 0,1 0,1 1,0 0))','fixture-source','fixture-dataset')
            """.trimIndent())
            old.query("SELECT * FROM forets").use { cursor ->
                check(cursor.moveToFirst()); (0 until cursor.columnCount).map { if (cursor.isNull(it)) null else cursor.getString(it) }
            }
        }
        migrationHelper.runMigrationsAndValidate(name, 39, true, HierarchySyncMigrations.MIGRATION_38_39).use { migrated ->
            migrated.query("SELECT * FROM forets").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(before, (0 until cursor.columnCount).map { if (cursor.isNull(it)) null else cursor.getString(it) })
            }
            listOf("hierarchy_sync_associations", "hierarchy_sync_operations").forEach { table ->
                migrated.query("SELECT COUNT(*) FROM $table").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            }
        }
    }

    @Test fun forest_and_parcel_upserts_preserve_the_complete_closed_hierarchy() = runBlocking {
        withTimeout(TIMEOUT) {
            val fixture = insertFixture()
            val capture = capture()
            val forestUpdate = fixture.forest.copy(nom = "Edited parent name", updatedAt = 300L)
            database.foretDao().insert(forestUpdate)
            database.parcelleDao().insertParcelle(fixture.parcel.copy(name = "Edited parcel name", updatedAt = 300L))
            assertEquals(FOREST, database.parcelleDao().getParcelleByIdAny(PARCEL)?.foretId)
            assertEquals(fixture.session, database.inventaireSessionDao().getById(SESSION))
            assertEquals(listOf(fixture.plot), database.hierarchySyncDao().plots(FOREST))
            assertEquals(listOf(fixture.tree), database.hierarchySyncDao().trees(FOREST))
            val updated = capture()
            assertEquals(1, updated.dossier.parcelles.size)
            assertEquals(1, updated.dossier.placettes.size)
            assertEquals(1, updated.dossier.sessions.size)
            assertEquals(1, updated.dossier.tiges.size)
            assertArrayEquals(capture.dossier.sessions.single().closedSnapshotJson?.toByteArray(Charsets.UTF_8), updated.dossier.sessions.single().closedSnapshotJson?.toByteArray(Charsets.UTF_8))
            assertEquals("Edited parent name", updated.dossier.forest.nom)
        }
    }

    @Test fun coherent_capture_holds_the_transaction_while_another_writer_waits() = runBlocking {
        withTimeout(TIMEOUT) {
            val fixture = insertFixture()
            val captured = CompletableDeferred<HierarchyCapture>()
            val release = CompletableDeferred<Unit>()
            val read = async {
                database.hierarchySyncDao().transaction {
                    captured.complete(captureHierarchy(database.hierarchySyncDao(), FOREST))
                    release.await()
                }
            }
            val first = captured.await()
            val writeStarted = CompletableDeferred<Unit>()
            val write = async {
                writeStarted.complete(Unit)
                database.foretDao().update(fixture.forest.copy(nom = "After transaction", updatedAt = 400L))
            }
            writeStarted.await()
            assertFalse(write.isCompleted)
            assertEquals("Synthetic forest", first.dossier.forest.nom)
            assertEquals(fixture.session.closedSnapshotJson, first.dossier.sessions.single().closedSnapshotJson)
            release.complete(Unit)
            read.await(); write.await()
            assertEquals("After transaction", capture().dossier.forest.nom)
        }
    }

    @Test fun duplicate_operation_rolls_back_activation_and_business_changes() = runBlocking {
        withTimeout(TIMEOUT) {
            val fixture = insertFixture()
            val operation = frozen(capture())
            database.hierarchySyncDao().append(operation)
            val failure = runCatching {
                database.hierarchySyncDao().transaction {
                    database.hierarchySyncDao().insertAssociation(association())
                    database.foretDao().update(fixture.forest.copy(nom = "Should roll back"))
                    database.hierarchySyncDao().append(operation)
                }
            }.exceptionOrNull()
            assertNotNull(failure)
            assertNull(database.hierarchySyncDao().association(FOREST))
            assertEquals(fixture.forest, database.hierarchySyncDao().forest(FOREST))
            assertEquals(operation.requestJson, database.hierarchySyncDao().operation(operation.operationId)?.requestJson)
        }
    }

    @Test fun reopened_outbox_retains_its_frozen_wire_and_only_explicit_purge_removes_it() = runBlocking {
        withTimeout(TIMEOUT) {
            val name = fileName("reopen")
            val first = Room.databaseBuilder(context(), ForestryDatabase::class.java, name).build()
            val stored = try {
                first.foretDao().insert(forestFixture())
                var captured: HierarchyCapture? = null
                first.hierarchySyncDao().transaction { captured = captureHierarchy(first.hierarchySyncDao(), FOREST) }
                val operation = frozen(checkNotNull(captured))
                first.hierarchySyncDao().transaction {
                    first.hierarchySyncDao().insertAssociation(association())
                    first.hierarchySyncDao().append(operation)
                }
                checkNotNull(first.hierarchySyncDao().operation(operation.operationId))
            } finally { first.close() }
            val reopened = Room.databaseBuilder(context(), ForestryDatabase::class.java, name).build()
            try {
                assertEquals(stored, reopened.hierarchySyncDao().operation(stored.operationId))
                reopened.foretDao().deleteById(FOREST, 500L)
                assertEquals(stored, reopened.hierarchySyncDao().operation(stored.operationId))
                assertNotNull(reopened.hierarchySyncDao().association(FOREST))
                reopened.hierarchySyncDao().transaction {
                    assertEquals(1, reopened.hierarchySyncDao().hardDeleteOperations())
                    assertEquals(1, reopened.hierarchySyncDao().hardDeleteAssociations())
                    reopened.foretDao().hardDeleteAll()
                }
                assertNull(reopened.hierarchySyncDao().operation(stored.operationId))
                assertNull(reopened.hierarchySyncDao().association(FOREST))
                assertNull(reopened.hierarchySyncDao().forest(FOREST))
            } finally { reopened.close() }
        }
    }

    @Test fun a_name_only_edit_invalidates_the_business_observer_even_when_counts_are_equal() = runBlocking {
        withTimeout(TIMEOUT) {
            val fixture = insertFixture()
            val first = CompletableDeferred<Unit>()
            val emissions = mutableListOf<Long>()
            val observing = launch {
                database.hierarchySyncDao().observeContentInvalidations().take(2).collect { count ->
                    emissions += count; first.complete(Unit)
                }
            }
            first.await()
            database.foretDao().update(fixture.forest.copy(nom = "New name without count change"))
            observing.join()
            assertEquals(2, emissions.size)
            assertEquals(emissions.first(), emissions.last())
        }
    }

    @Test fun coherent_room_capture_keeps_more_than_256_real_tree_rows() = runBlocking {
        withTimeout(TIMEOUT) {
            val fixture = insertFixture()
            val extra = (2..300).map { index -> fixture.tree.copy(tigeId = "tree-$index", sessionId = null) }
            database.tigeDao().insertTiges(extra)
            val captured = capture()
            assertEquals(300, captured.dossier.tiges.size)
            assertEquals(300, captured.dossier.tiges.map { it.id }.distinct().size)
            assertEquals(fixture.session.closedSnapshotJson, captured.dossier.sessions.single().closedSnapshotJson)
        }
    }

    @Test fun hierarchy_only_ownership_refuses_foreign_and_logged_out_parent_mutations_without_outbox() = runBlocking {
        withTimeout(TIMEOUT) {
            val fixture = insertFixture()
            database.hierarchySyncDao().insertAssociation(association())
            assertTrue(database.parcelSyncDao().getAccountIdsForParcel(PARCEL).isEmpty())
            for (actor in listOf("other-account", null)) {
                val metadata = MetadataService { actor }
                val journal = journal { actor }
                val forests = ForetRepositoryImpl(database.foretDao(), metadata, database.parcelSyncDao(), database.parcelSyncOperationDao())
                val parcels = ParcelleRepositoryImpl(database.parcelleDao(), metadata, journal, database.tigeDao(), database.placetteDao())
                assertEquals("FOREST_LINKED_TO_ANOTHER_ACCOUNT", runCatching {
                    forests.update(fixture.forest.toDomain().copy(nom = "Denied", auteur = "fixture-account"))
                }.exceptionOrNull()?.message)
                assertEquals("FOREST_LINKED_TO_ANOTHER_ACCOUNT", runCatching { forests.deleteById(FOREST) }.exceptionOrNull()?.message)
                assertEquals("PARCEL_LINKED_TO_ANOTHER_ACCOUNT", runCatching {
                    parcels.insertParcelle(fixture.parcel.copy(parcelleId = "new-parcel", auteur = "fixture-account").toParcelle())
                }.exceptionOrNull()?.message)
                assertEquals("PARCEL_LINKED_TO_ANOTHER_ACCOUNT", runCatching {
                    parcels.updateParcelle(fixture.parcel.copy(name = "Denied", auteur = "fixture-account").toParcelle())
                }.exceptionOrNull()?.message)
                assertEquals("PARCEL_LINKED_TO_ANOTHER_ACCOUNT", runCatching { parcels.deleteParcelle(PARCEL) }.exceptionOrNull()?.message)
            }
            assertEquals(fixture.forest, database.foretDao().getByIdAny(FOREST))
            assertEquals(fixture.parcel, database.parcelleDao().getParcelleByIdAny(PARCEL))
            assertNull(database.parcelleDao().getParcelleByIdAny("new-parcel"))
            assertEquals(fixture.session, database.inventaireSessionDao().getById(SESSION))
            assertEquals(listOf(fixture.plot), database.hierarchySyncDao().plots(FOREST))
            assertEquals(listOf(fixture.tree), database.hierarchySyncDao().trees(FOREST))
            assertNoOperations()
        }
    }

    @Test fun moving_a_parcel_checks_both_original_and_destination_forest_associations() = runBlocking {
        withTimeout(TIMEOUT) {
            val fixture = insertFixture()
            val destinationId = UUID.randomUUID().toString()
            database.foretDao().insert(forestFixture().copy(foretId = destinationId, nom = "Other workspace"))
            database.hierarchySyncDao().insertAssociation(association())
            database.hierarchySyncDao().insertAssociation(association().copy(forestId = destinationId, accountId = "other-account"))
            val parcels = ParcelleRepositoryImpl(database.parcelleDao(), MetadataService { "fixture-account" },
                journal { "fixture-account" }, database.tigeDao(), database.placetteDao())
            val failure = runCatching {
                parcels.updateParcelle(fixture.parcel.copy(foretId = destinationId).toParcelle())
            }.exceptionOrNull()
            assertEquals("PARCEL_LINKED_TO_ANOTHER_ACCOUNT", failure?.message)
            assertEquals(fixture.parcel, database.parcelleDao().getParcelleByIdAny(PARCEL))
            assertTrue(database.hierarchySyncDao().parcels(destinationId).isEmpty())
            assertEquals(fixture.session, database.inventaireSessionDao().getById(SESSION))
            assertEquals(listOf(fixture.tree), database.hierarchySyncDao().trees(FOREST))
            assertNoOperations()
        }
    }

    @Test fun session_generation_change_after_business_write_rolls_back_parent_and_outbox_in_real_sqlite() = runBlocking {
        withTimeout(TIMEOUT) {
            val fixture = insertFixture()
            database.hierarchySyncDao().insertAssociation(association())
            var generation = 1
            val journal = ParcelMutationJournal(database.parcelleDao(), database.parcelSyncDao(), database.parcelSyncOperationDao(),
                "https://fixture.invalid/api/v1", { "fixture-account" }, mutationGuardFactory = {
                    val capturedGeneration = generation
                    val stillCurrent: () -> Boolean = { generation == capturedGeneration }
                    stillCurrent
                })
            val failure = runCatching {
                journal.mutate(PARCEL, "UPSERT", FOREST) {
                    database.parcelleDao().updateParcelle(fixture.parcel.copy(name = "Uncommitted change"))
                    generation += 1 // Same account identifier, different authenticated generation.
                }
            }.exceptionOrNull()
            assertEquals("MUTATION_SESSION_CHANGED", failure?.message)
            assertEquals(fixture.parcel, database.parcelleDao().getParcelleByIdAny(PARCEL))
            assertEquals(fixture.session, database.inventaireSessionDao().getById(SESSION))
            assertNoOperations()
        }
    }

    @Test fun same_actor_preserves_source_and_children_without_implicitly_activating_leaf_transport() = runBlocking {
        withTimeout(TIMEOUT) {
            val fixture = insertFixture()
            database.hierarchySyncDao().insertAssociation(association())
            val actor = "fixture-account"
            val metadata = MetadataService { actor }
            val forests = ForetRepositoryImpl(database.foretDao(), metadata, database.parcelSyncDao(), database.parcelSyncOperationDao())
            val parcels = ParcelleRepositoryImpl(database.parcelleDao(), metadata, journal { actor }, database.tigeDao(), database.placetteDao())
            forests.update(fixture.forest.toDomain().copy(nom = "Own edit", auteur = "spoofed-author", source = "official-source"))
            parcels.updateParcelle(fixture.parcel.copy(name = "Own parcel edit", auteur = "spoofed-author", source = "official-source").toParcelle())
            assertEquals(actor, database.foretDao().getById(FOREST)?.auteur)
            assertEquals(actor, database.parcelleDao().getParcelleByIdAny(PARCEL)?.auteur)
            assertEquals("official-source", database.parcelleDao().getParcelleByIdAny(PARCEL)?.source)
            assertEquals(fixture.session, database.inventaireSessionDao().getById(SESSION))
            assertEquals(listOf(fixture.plot), database.hierarchySyncDao().plots(FOREST))
            assertEquals(listOf(fixture.tree), database.hierarchySyncDao().trees(FOREST))
            assertTrue(database.parcelSyncDao().getAccountIdsForParcel(PARCEL).isEmpty())
            val operation = database.parcelSyncOperationDao().getAllForParcel(null, PARCEL).single()
            assertEquals("LOCAL_ONLY", operation.state)
            assertNull(operation.originAccountId)
            assertNull(operation.apiOrigin)
            assertEquals("Own parcel edit", ParcelMutationJournal.upsertRequest(operation).parcel.name)
            assertEquals(fixture.session.closedSnapshotJson, capture().dossier.sessions.single().closedSnapshotJson)
        }
    }

    private fun journal(account: () -> String?) = ParcelMutationJournal(database.parcelleDao(), database.parcelSyncDao(),
        database.parcelSyncOperationDao(), "https://fixture.invalid/api/v1", account)

    private suspend fun assertNoOperations() {
        assertTrue(database.parcelSyncOperationDao().getAllForParcel(null, PARCEL).isEmpty())
        assertTrue(database.parcelSyncOperationDao().getAllForParcel(null, "new-parcel").isEmpty())
        assertNull(database.hierarchySyncDao().tail(FOREST, "fixture-account"))
    }

    private data class Fixture(val forest: ForetEntity, val parcel: ParcelleEntity, val plot: PlacetteEntity,
        val session: InventaireSessionEntity, val tree: TigeEntity)

    private suspend fun insertFixture(): Fixture {
        val forest = forestFixture()
        val parcel = Parcelle(
            id = PARCEL, forestId = null, foretId = FOREST, name = "Synthetic parcel", surfaceHa = 1.0,
            shape = null, slopePct = null, aspect = null, access = null, altitudeM = null,
            objectifType = null, objectifVal = null, tolerancePct = null, samplingMode = null,
            sampleAreaM2 = null, targetSpeciesCsv = null, srid = null, remarks = null, createdAt = 100L, updatedAt = 200L,
        )
        val plot = Placette(PLOT, PARCEL, "Synthetic plot", "CIRC", 10.0, Math.PI * 100, null, 100L, 200L)
        val tree = Tige(TREE, PARCEL, PLOT, "FIXTURE", 30.0, null, null, null, null,
            timestamp = 100L, note = null, produit = null, fCoef = null, valueEur = null, sessionId = SESSION)
        val snapshot = " \n" + MartelageSnapshotCodec.encode(MartelageSnapshotV1(
            sessionId = SESSION, parcelleId = PARCEL, campaignId = CAMPAIGN, revisionNumber = 1,
            previousSessionId = null, startedAt = 100L, closedAt = 200L, activeDurationMs = 50L,
            auteur = "fixture-account", parcelle = parcel, placettes = listOf(plot), tiges = listOf(tree),
        )) + "\n "
        val session = InventaireSessionEntity(SESSION, PARCEL, "MARTELAGE", 100L, 200L,
            null, null, null, null, null, createdAt = 100L, martelageState = "CLOSED",
            activeDurationMs = 50L, campaignId = CAMPAIGN, closedSnapshotJson = snapshot)
        database.foretDao().insert(forest)
        database.parcelleDao().insertParcelle(parcel.toParcelleEntity())
        database.essenceDao().insertEssence(EssenceEntity("FIXTURE", "Synthetic species", null, null, null))
        database.inventaireSessionDao().insert(session)
        val plotEntity = plot.toPlacetteEntity().copy(sessionId = SESSION)
        database.placetteDao().insertPlacette(plotEntity)
        database.tigeDao().insertTige(tree.toTigeEntity())
        return Fixture(forest, parcel.toParcelleEntity(), plotEntity, session, tree.toTigeEntity())
    }

    private suspend fun capture(): HierarchyCapture {
        var value: HierarchyCapture? = null
        database.hierarchySyncDao().transaction { value = captureHierarchy(database.hierarchySyncDao(), FOREST) }
        return checkNotNull(value)
    }
    private fun frozen(captured: HierarchyCapture): HierarchySyncOperationEntity {
        val operationId = UUID.randomUUID().toString()
        val request = hierarchyJson.encodeToString(HierarchyMutationDto(operationId, null, Instant.ofEpochMilli(1000L).toString(), captured.dossier))
        return HierarchySyncOperationEntity(operationId = operationId, forestId = FOREST, accountId = "fixture-account",
            apiOrigin = "https://fixture.invalid/api/v1", requestJson = request, requestSha256 = hierarchySha256(request),
            dossierSha256 = captured.fingerprint, baseVersion = null, expectedServerVersion = 1, predecessorOperationId = null,
            createdAt = 1000L, containsLocalPhotos = false, state = "PENDING", nextAttemptAt = 1000L)
    }
    private fun association() = HierarchySyncAssociationEntity(FOREST, "fixture-account", "https://fixture.invalid/api/v1", 1000L, null, null, null, null)
    private fun fileName(type: String) = "hierarchy-sync-$type-${UUID.randomUUID()}.db".also { files += it }
    private fun context(): Context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun forestFixture() = Foret(FOREST, "Synthetic forest", "", null, null, null, null, null, null, null, null, 100L, 200L).toEntity()
    private companion object {
        const val FOREST = "11111111-1111-4111-8111-111111111111"
        const val CAMPAIGN = "22222222-2222-4222-8222-222222222222"
        const val PARCEL = "p1"; const val PLOT = "pl1"; const val SESSION = "s1"; const val TREE = "t1"
        const val TIMEOUT = 20_000L
    }
}
