package com.forestry.counter.data.local

import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.data.local.entity.HierarchySyncAssociationEntity
import com.forestry.counter.data.mapper.toEntity
import com.forestry.counter.data.mapper.toParcelleEntity
import com.forestry.counter.data.mapper.toPlacetteEntity
import com.forestry.counter.data.repository.InventaireSessionRepositoryImpl
import com.forestry.counter.data.repository.ForetRepositoryImpl
import com.forestry.counter.data.repository.ParcelleRepositoryImpl
import com.forestry.counter.data.repository.PlacetteRepositoryImpl
import com.forestry.counter.data.service.MetadataService
import com.forestry.counter.data.sync.ParcelMutationJournal
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.data.sync.hierarchy.BusinessMutationGuard
import com.forestry.counter.domain.model.InventaireSession
import com.forestry.counter.domain.model.MartelageClock
import com.forestry.counter.domain.model.MartelageClockReading
import com.forestry.counter.domain.model.MartelageStates
import com.forestry.counter.domain.model.Foret
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.presentation.screens.forestry.ForestDetailUiState
import com.forestry.counter.presentation.screens.forestry.ForestDetailViewModel
import com.forestry.counter.presentation.screens.home.HomeUiState
import com.forestry.counter.presentation.screens.home.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real, isolated Room rows and production repositories; no product fixture or private database access. */
@RunWith(AndroidJUnit4::class)
class ForestDetailRoomReadTest {
    private lateinit var db: ForestryDatabase
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, ForestryDatabase::class.java).build()
    }
    @After fun teardown() { db.close() }

    @Test fun guest_home_and_nested_forest_detail_read_the_same_unlinked_local_forest() = runBlocking { exercise(null) }
    @Test fun account_home_and_nested_detail_recover_after_association_and_keep_B_masked() = runBlocking { exercise(A) }

    @Test fun forest_delete_refuses_scoped_and_legacy_active_sessions_preserves_closed_snapshots_and_authority() = runBlocking {
        withTimeout(20_000L) {
            val legacyForestId = "room-detail-legacy-forest"
            val legacyParcelId = "room-detail-legacy-parcel"
            val forest = Foret(FOREST, "TEST forest", "TEST owner", null, null, null, null,
                null, null, null, null)
            db.foretDao().insert(forest.toEntity())
            db.foretDao().insert(forest.copy(foretId = legacyForestId).toEntity())
            val parent = Parcelle(id = PARCEL, forestId = null, foretId = FOREST, name = "TEST parcel", surfaceHa = 1.0,
                shape = null, slopePct = null, aspect = null, access = null, altitudeM = null, objectifType = null,
                objectifVal = null, tolerancePct = null, samplingMode = null, sampleAreaM2 = null, targetSpeciesCsv = null,
                srid = 4326, remarks = null)
            db.parcelleDao().insertParcelle(parent.toParcelleEntity())
            db.parcelleDao().insertParcelle(parent.copy(id = legacyParcelId, foretId = null, forestId = legacyForestId).toParcelleEntity())
            db.placetteDao().insertNewPlacettes(listOf(Placette("room-delete-plot", PARCEL, "TEST plot", "CIRC",
                10.0, 314.159, "POINT (2 46)").toPlacetteEntity()))
            val identity = BusinessReadIdentityFixture().apply { login(A) }
            val metadata = MetadataService { identity.session.value?.accountId }
            val guard = BusinessMutationGuard(identity)::capture
            val access = BusinessReadAccess(identity, db.parcelSyncDao())
            val forests = ForetRepositoryImpl(db.foretDao(), metadata, db.parcelSyncDao(), db.parcelSyncOperationDao(), guard, access)
            val sessions = InventaireSessionRepositoryImpl(db.inventaireSessionDao(), metadata,
                MartelageClock { MartelageClockReading(1000, 1000, 7) }, guard, access)
            db.hierarchySyncDao().insertAssociation(HierarchySyncAssociationEntity(FOREST, A, ORIGIN, 10L, null, null, null, null))
            val running = sessions.startForPlacette(PARCEL, "room-delete-plot")
            assertEquals(1, db.foretDao().countActiveSessionsForForest(FOREST))
            assertTrue(runCatching { forests.deleteById(FOREST) }.isFailure)
            assertNotNull(db.foretDao().getById(FOREST))
            sessions.pauseMartelage(running.sessionId)
            assertTrue(runCatching { forests.deleteById(FOREST) }.isFailure)
            val closed = sessions.closeMartelage(running.sessionId, null)
            assertEquals(0, db.foretDao().countActiveSessionsForForest(FOREST))
            identity.login("TEST-account-B")
            assertTrue(runCatching { forests.deleteById(FOREST) }.isFailure)
            assertNotNull(db.foretDao().getById(FOREST))
            identity.login(A)
            forests.deleteById(FOREST)
            assertNull(db.foretDao().getById(FOREST))
            assertEquals(closed.closedSnapshotJson, db.inventaireSessionDao().getById(closed.sessionId)!!.closedSnapshotJson)
            assertEquals(closed.sessionId, sessions.getClosedSnapshot(closed.sessionId)!!.sessionId)
            val legacy = InventaireSession("TEST-legacy-null-active", legacyParcelId, MartelageStates.TYPE,
                100L, null, null, null, null, null, null, martelageState = MartelageStates.PAUSED,
                campaignId = "TEST-legacy-null-active", activeDurationMs = 75L)
            db.inventaireSessionDao().insert(legacy.toEntity())
            val untouched = db.inventaireSessionDao().getById(legacy.sessionId)
            assertEquals(1, db.foretDao().countActiveSessionsForForest(legacyForestId))
            assertTrue(runCatching { forests.deleteById(legacyForestId) }.isFailure)
            assertNotNull(db.foretDao().getById(legacyForestId))
            assertEquals(untouched, db.inventaireSessionDao().getById(legacy.sessionId))
            assertNull(db.inventaireSessionDao().getById(legacy.sessionId)!!.placetteId)
            assertNotNull(db.parcelleDao().getParcelleById(legacyParcelId))
        }
    }

    private suspend fun exercise(actor: String?) = withTimeout(20_000L) {
        val forest = Foret(FOREST, "Room test forest", "Test owner", "room-owner@example.invalid", null, null, null,
            null, null, null, null, createdAt = 1L, updatedAt = 2L)
        val parcel = Parcelle(id = PARCEL, forestId = null, foretId = FOREST, name = "Room test parcel", surfaceHa = 1.0,
            shape = null, slopePct = null, aspect = null, access = null, altitudeM = null, objectifType = null,
            objectifVal = null, tolerancePct = null, samplingMode = null, sampleAreaM2 = null, targetSpeciesCsv = null,
            srid = null, remarks = null, createdAt = 1L, updatedAt = 2L)
        db.foretDao().insert(forest.toEntity())
        db.parcelleDao().insertParcelle(parcel.toParcelleEntity())
        db.placetteDao().insertPlacette(Placette("room-detail-plot", PARCEL, "Room test plot", "ENTIRE", null,
            10_000.0, null, createdAt = 1L, updatedAt = 2L).toPlacetteEntity())
        val identity = BusinessReadIdentityFixture().apply { login(actor) }
        val access = BusinessReadAccess(identity, db.parcelSyncDao())
        val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        access.startObserving(observerScope)
        val metadata = MetadataService { identity.session.value?.accountId }
        val forests = ForetRepositoryImpl(db.foretDao(), metadata, db.parcelSyncDao(), db.parcelSyncOperationDao(), readAccess = access)
        val parcels = ParcelleRepositoryImpl(db.parcelleDao(), metadata,
            ParcelMutationJournal(db.parcelleDao(), db.parcelSyncDao(), db.parcelSyncOperationDao(), ORIGIN,
                { identity.session.value?.accountId }), db.tigeDao(), db.placetteDao(), access)
        val plots = PlacetteRepositoryImpl(db.placetteDao(), metadata, readAccess = access)
        val store = ViewModelStore()
        val home = withContext(Dispatchers.Main) { HomeViewModel(forests, parcels).also { store.put("home", it) } }
        val detail = withContext(Dispatchers.Main) { ForestDetailViewModel(forests, parcels, FOREST, access).also { store.put("detail", it) } }
        suspend fun assertVisible() {
            val dashboard = home.uiState.first { it is HomeUiState.Success && it.foretCount == 1 } as HomeUiState.Success
            assertEquals(FOREST, dashboard.recentForets.single().foretId)
            val state = detail.uiState.first { it is ForestDetailUiState.Success &&
                it.accessSnapshot?.let(access::isCurrent) == true && it.parcelles.map { row -> row.id } == listOf(PARCEL)
            } as ForestDetailUiState.Success
            assertEquals("room-owner@example.invalid", state.foret.proprietaireEmail)
            assertEquals(listOf(PARCEL), state.parcelles.map { it.id })
            assertEquals(listOf("room-detail-plot"), plots.getPlacettesByParcelle(PARCEL).first().map { it.id })
            assertTrue(detail.isCurrentState())
        }
        try {
            assertVisible()
            // Independent, guarded Room subscriptions must not permanently invalidate the detail.
            repeat(4) {
                assertEquals(FOREST, forests.getAll().first().single().foretId)
                assertEquals(PARCEL, parcels.getAllParcelles().first().single().id)
                assertNotNull(forests.getById(FOREST))
                assertVisible()
            }
            if (actor != null) {
                val beforeAssociation = access.capture()
                db.hierarchySyncDao().insertAssociation(HierarchySyncAssociationEntity(FOREST, A, ORIGIN, 10L, null, null, null, null))
                // Establish the committed authority epoch before awaiting detail recovery, rather
                // than accepting an old Success that Room has not invalidated yet.
                val committedAssociation = access.capture()
                assertFalse(access.isCurrent(beforeAssociation))
                assertTrue(access.revalidate(committedAssociation))
                detail.uiState.first { state -> state is ForestDetailUiState.Success && state.accessSnapshot?.let(access::isCurrent) == true }
                assertVisible()
                identity.login("fixture-B")
                detail.uiState.first { it == ForestDetailUiState.NotFound }
                home.uiState.first { it is HomeUiState.Success && it.foretCount == 0 }
                assertNull(forests.getById(FOREST))
                identity.login(null)
                detail.uiState.first { it == ForestDetailUiState.NotFound }
                assertNull(forests.getById(FOREST))
                identity.login(A)
                assertVisible()
            }
            assertNotNull(db.foretDao().getById(FOREST))
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            observerScope.cancel()
        }
    }
    private companion object {
        const val FOREST = "33333333-3333-4333-8333-333333333333"
        const val PARCEL = "room-detail-parcel"
        const val A = "room-detail-account-A"
        const val ORIGIN = "https://room-detail.invalid/api/v1"
    }
}
