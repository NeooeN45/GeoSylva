package com.forestry.counter.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.data.local.entity.*
import com.forestry.counter.data.mapper.*
import com.forestry.counter.data.repository.ForetRepositoryImpl
import com.forestry.counter.data.repository.PlacetteRepositoryImpl
import com.forestry.counter.data.repository.InventaireSessionRepositoryImpl
import com.forestry.counter.data.repository.GroupRepositoryImpl
import com.forestry.counter.data.repository.ParcelleRepositoryImpl
import com.forestry.counter.data.service.MetadataService
import com.forestry.counter.data.sync.ParcelMutationJournal
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.domain.model.*
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
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

@RunWith(AndroidJUnit4::class)
class BusinessReadAccessRoomTest {
    private lateinit var db: ForestryDatabase
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(context(), ForestryDatabase::class.java).build() }
    @After fun teardown() { db.close() }

    @Test fun linked_forest_and_parcel_are_masked_to_B_and_logout_while_unlinked_local_data_survives() = runBlocking {
        withTimeout(TIMEOUT) {
            insertFixture()
            db.hierarchySyncDao().insertAssociation(association())
            db.foretDao().insert(forest().copy(foretId = "local-forest", nom = "Unlinked local forest").toEntity())
            db.parcelleDao().insertParcelle(parcel().copy(id = "local-parcel", foretId = "local-forest", forestId = null).toParcelleEntity())
            val identity = BusinessReadIdentityFixture()
            val access = BusinessReadAccess(identity, db.parcelSyncDao())
            val metadata = MetadataService { identity.session.value?.accountId }
            val forests = ForetRepositoryImpl(db.foretDao(), metadata, db.parcelSyncDao(), db.parcelSyncOperationDao(), readAccess = access)
            val parcels = ParcelleRepositoryImpl(db.parcelleDao(), metadata, journal(identity), db.tigeDao(), db.placetteDao(), access)
            for (actor in listOf(A, "B", null, A)) {
                identity.login(actor)
                val visibleForests = if (actor == A) setOf(FOREST, "local-forest") else setOf("local-forest")
                val visibleParcels = if (actor == A) setOf(PARCEL, "local-parcel") else setOf("local-parcel")
                assertEquals(visibleForests, forests.getAll().first { it.map { row -> row.foretId }.toSet() == visibleForests }.map { it.foretId }.toSet())
                assertEquals(visibleParcels, parcels.getAllParcelles().first { it.map { row -> row.id }.toSet() == visibleParcels }.map { it.id }.toSet())
                assertEquals(actor == A, forests.getById(FOREST) != null)
                assertNotNull(forests.getById("local-forest"))
            }
            assertEquals("owner@example.invalid", db.foretDao().getById(FOREST)?.proprietaireEmail)
            assertEquals(2, db.parcelleDao().getAllParcellesNow().size)
            assertNotNull(db.inventaireSessionDao().getById(SESSION)?.closedSnapshotJson)
        }
    }

    @Test fun first_subscription_returns_actual_local_parent_plot_and_session_before_any_loading_sentinel() = runBlocking {
        withTimeout(TIMEOUT) {
            insertFixture()
            val identity = BusinessReadIdentityFixture()
            val access = BusinessReadAccess(identity, db.parcelSyncDao())
            val metadata = MetadataService { identity.session.value?.accountId }
            val parcels = ParcelleRepositoryImpl(db.parcelleDao(), metadata, journal(identity), db.tigeDao(), db.placetteDao(), access)
            val plots = PlacetteRepositoryImpl(db.placetteDao(), metadata, readAccess = access)
            val sessions = InventaireSessionRepositoryImpl(db.inventaireSessionDao(), metadata, readAccess = access)
            for (actor in listOf(null, A, "B")) {
                identity.login(actor)
                assertEquals(PARCEL, parcels.getParcelleById(PARCEL).first()?.id)
                assertEquals(listOf(PARCEL), parcels.getAllParcelles().first().map { it.id })
                assertEquals("plot-A", plots.getPlacetteById("plot-A").first()?.id)
                assertEquals(listOf("plot-A"), plots.getPlacettesByParcelle(PARCEL).first().map { it.id })
                assertEquals(listOf(SESSION), sessions.getByParcelle(PARCEL).first().map { it.sessionId })
            }
            db.hierarchySyncDao().insertAssociation(association())
            identity.login("B")
            assertNull(parcels.getParcelleById(PARCEL).first())
            assertNull(plots.getPlacetteById("plot-A").first())
            assertTrue(sessions.getByParcelle(PARCEL).first().isEmpty())
            assertEquals(PARCEL, db.parcelleDao().getParcelleByIdAny(PARCEL)?.parcelleId)
        }
    }

    @Test fun association_updates_invalidate_read_results_and_synchronous_guards_without_business_edits() = runBlocking {
        withTimeout(TIMEOUT) {
            insertFixture()
            val identity = BusinessReadIdentityFixture().apply { login("B") }
            val access = BusinessReadAccess(identity, db.parcelSyncDao())
            val watching = access.startObserving(this)
            val metadata = MetadataService { identity.session.value?.accountId }
            val forests = ForetRepositoryImpl(db.foretDao(), metadata, db.parcelSyncDao(), db.parcelSyncOperationDao(), readAccess = access)
            val seen = CompletableDeferred<Unit>()
            val hidden = CompletableDeferred<Unit>()
            val reading = launch {
                forests.getAll().collect { rows ->
                    if (rows.any { it.foretId == FOREST }) seen.complete(Unit)
                    else if (seen.isCompleted) hidden.complete(Unit)
                }
            }
            try {
                seen.await()
                val before = access.capture()
                db.hierarchySyncDao().insertAssociation(association())
                hidden.await()
                assertFalse(access.revalidate(before))
                assertFalse(access.canReadForest(FOREST))
                assertFalse(access.canReadParcelle(PARCEL))
                assertEquals("Forest A", db.foretDao().getById(FOREST)?.nom)
            } finally { reading.cancel(); watching.cancel() }
        }
    }

    @Test fun legacy_and_immutable_operation_owners_remain_authoritative_for_groups_and_parent_moves() = runBlocking {
        withTimeout(TIMEOUT) {
            insertFixture()
            db.parcelSyncDao().upsert(checkpoint())
            val identity = BusinessReadIdentityFixture().apply { login("B") }
            val access = BusinessReadAccess(identity, db.parcelSyncDao())
            assertFalse(access.canReadForest(FOREST))
            assertFalse(access.canReadForest(GROUP))
            assertFalse(access.canReadParcelle(PARCEL))
            db.parcelSyncDao().hardDeleteAll()
            db.parcelSyncOperationDao().append(ParcelMutationJournal.freeze(
                parcel().toParcelleEntity(), "UPSERT", A, ORIGIN, 1, null, "SYNCED", 100L, UUID.randomUUID().toString(),
            ))
            assertFalse(access.canReadForest(GROUP))
            assertFalse(access.canReadParcelle(PARCEL))
            db.parcelleDao().updateParcelle(parcel().copy(forestId = "moved-group").toParcelleEntity())
            val fresh = access.capture()
            assertFalse(access.canReadForest("moved-group", fresh))
            assertFalse(access.canReadParcelle(PARCEL, fresh))
        }
    }

    @Test fun suspended_forest_read_discards_same_actor_relogin_even_with_identical_session_metadata() = runBlocking {
        withTimeout(TIMEOUT) {
            insertFixture(); db.hierarchySyncDao().insertAssociation(association())
            val identity = BusinessReadIdentityFixture().apply { login(A) }
            val access = BusinessReadAccess(identity, db.parcelSyncDao())
            val loaded = CompletableDeferred<Unit>()
            val resume = CompletableDeferred<Unit>()
            val delegate = db.foretDao()
            val delayed = object : com.forestry.counter.data.local.dao.ForetDao by delegate {
                override suspend fun getById(id: String): ForetEntity? {
                    val result = delegate.getById(id)
                    loaded.complete(Unit); resume.await(); return result
                }
            }
            val repo = ForetRepositoryImpl(delayed, MetadataService { identity.session.value?.accountId },
                db.parcelSyncDao(), db.parcelSyncOperationDao(), readAccess = access)
            val reading = async { repo.getById(FOREST) }
            loaded.await(); val prior = identity.session.value
            identity.login(A); assertEquals(prior, identity.session.value)
            resume.complete(Unit)
            assertNull(reading.await())
            assertNotNull(db.foretDao().getById(FOREST))
        }
    }

    @Test fun foreign_legacy_group_reads_copy_and_delete_are_refused_without_new_rows() = runBlocking {
        withTimeout(TIMEOUT) {
            insertFixture(); db.parcelSyncDao().upsert(checkpoint())
            val identity = BusinessReadIdentityFixture().apply { login("B") }
            val access = BusinessReadAccess(identity, db.parcelSyncDao())
            val groups = groups(access)
            assertNull(groups.getGroupById(GROUP).first())
            assertTrue(groups.getAllGroups().first().isEmpty())
            assertNotNull(runCatching { groups.duplicateGroup(GROUP) }.exceptionOrNull())
            assertNotNull(runCatching { groups.deleteGroup(GROUP) }.exceptionOrNull())
            assertEquals(listOf(GROUP), db.groupDao().getAllGroups().first().map { it.groupId })
            assertEquals(listOf(PARCEL), db.parcelleDao().getAllParcellesNow().map { it.parcelleId })
            assertEquals(listOf(TREE), db.tigeDao().getAllTigesNow().map { it.tigeId })
        }
    }

    @Test fun same_owner_can_duplicate_legacy_group_without_claiming_the_original_session() = runBlocking {
        withTimeout(TIMEOUT) {
            insertFixture(); db.parcelSyncDao().upsert(checkpoint())
            val identity = BusinessReadIdentityFixture().apply { login(A) }
            val access = BusinessReadAccess(identity, db.parcelSyncDao())
            val original = db.inventaireSessionDao().getById(SESSION)
            val copyId = groups(access).duplicateGroup(GROUP)
            assertTrue(copyId.isNotEmpty() && copyId != GROUP)
            val copied = db.parcelleDao().getParcellesByForest(copyId).first().single()
            val copiedTrees = db.tigeDao().getTigesByParcelle(copied.parcelleId).first()
            assertEquals(1, copiedTrees.size)
            assertNull(copiedTrees.single().sessionId)
            assertNull(copiedTrees.single().originTigeId)
            assertEquals(original, db.inventaireSessionDao().getById(SESSION))
            assertEquals(2, db.groupDao().getAllGroups().first().size)
        }
    }

    private suspend fun insertFixture() {
        db.groupDao().insertGroup(GroupEntity(GROUP, "Legacy private group"))
        db.foretDao().insert(forest().toEntity())
        db.parcelleDao().insertParcelle(parcel().toParcelleEntity())
        db.essenceDao().insertEssence(EssenceEntity("FIXTURE", "Fixture species", null, null, null))
        db.inventaireSessionDao().insert(InventaireSessionEntity(SESSION, PARCEL, "MARTELAGE", 100L, 200L,
            null, null, null, null, null, martelageState = "CLOSED", campaignId = UUID.randomUUID().toString(),
            closedSnapshotJson = "{\"fixture\":true}"))
        db.placetteDao().insertPlacette(Placette("plot-A", PARCEL, "Private plot", "CIRC", 10.0, 314.0, null, 100L, 200L).toPlacetteEntity())
        db.tigeDao().insertTige(Tige(TREE, PARCEL, "plot-A", "FIXTURE", 30.0, null, null, null, null,
            100L, null, null, null, null, sessionId = SESSION, originTigeId = TREE).toTigeEntity())
    }
    private fun groups(access: BusinessReadAccess) = GroupRepositoryImpl(db, db.groupDao(), db.counterDao(), db.formulaDao(),
        db.groupVariableDao(), db.parcelleDao(), db.placetteDao(), db.tigeDao(), access)
    private fun journal(identity: BusinessReadIdentityFixture) = ParcelMutationJournal(db.parcelleDao(), db.parcelSyncDao(),
        db.parcelSyncOperationDao(), ORIGIN, { identity.session.value?.accountId })
    private fun association() = HierarchySyncAssociationEntity(FOREST, A, ORIGIN, 100L, null, null, null, null)
    private fun checkpoint() = ParcelSyncEntity(A, PARCEL, "UPSERT", "fixture-op", "SYNCED", 1, 0, 100L, null, 100L, 100L, null)
    private fun forest() = Foret(FOREST, "Forest A", "Owner A", "owner@example.invalid", null, null, null, null, null, null, null, 100L, 200L)
    private fun parcel() = Parcelle(id = PARCEL, forestId = GROUP, foretId = FOREST, name = "Parcel A", surfaceHa = 1.0,
        shape = null, slopePct = null, aspect = null, access = null, altitudeM = null, objectifType = null, objectifVal = null,
        tolerancePct = null, samplingMode = null, sampleAreaM2 = null, targetSpeciesCsv = null, srid = null, remarks = null,
        createdAt = 100L, updatedAt = 200L)
    private fun context(): Context = InstrumentationRegistry.getInstrumentation().targetContext
    private companion object {
        const val A = "fixture-account-A"; const val FOREST = "11111111-1111-4111-8111-111111111111"
        const val GROUP = "legacy-group-A"; const val PARCEL = "parcel-A"; const val SESSION = "session-A"; const val TREE = "tree-A"
        const val ORIGIN = "https://fixture.invalid/api/v1"; const val TIMEOUT = 20_000L
    }
}
