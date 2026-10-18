package com.forestry.counter.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.data.local.dao.InventaireSessionDao
import com.forestry.counter.data.local.dao.TigeDao
import com.forestry.counter.data.local.entity.EssenceEntity
import com.forestry.counter.data.local.entity.HierarchySyncAssociationEntity
import com.forestry.counter.data.local.entity.InventaireSessionEntity
import com.forestry.counter.data.mapper.toEntity
import com.forestry.counter.data.mapper.toParcelleEntity
import com.forestry.counter.data.mapper.toPlacetteEntity
import com.forestry.counter.data.mapper.toTigeEntity
import com.forestry.counter.data.repository.InventaireSessionRepositoryImpl
import com.forestry.counter.data.repository.PlacetteRepositoryImpl
import com.forestry.counter.data.repository.TigeRepositoryImpl
import com.forestry.counter.data.service.MetadataService
import com.forestry.counter.data.sync.hierarchy.BusinessMutationGuard
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.domain.model.Foret
import com.forestry.counter.domain.model.MartelageClock
import com.forestry.counter.domain.model.MartelageClockReading
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.model.Tige
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room plus a token-free TEST identity. Product sessions/databases are never read or injected. */
@RunWith(AndroidJUnit4::class)
class HierarchyChildReadIsolationRoomTest {
    private lateinit var database: ForestryDatabase
    private val identity = BusinessReadIdentityFixture()
    private val metadata = MetadataService { identity.session.value?.accountId }
    private val clock = object : MartelageClock {
        override fun read() = MartelageClockReading(1_000L, 500L, 7)
    }
    private val mutationGuard get() = BusinessMutationGuard(identity)::capture
    private lateinit var access: BusinessReadAccess
    private lateinit var linkedSession: String
    private lateinit var offlineSession: String
    private val sessions get() = sessionRepository()
    private val trees get() = TigeRepositoryImpl(database.tigeDao(), metadata, clock, mutationGuard, access)
    private val plots get() = PlacetteRepositoryImpl(database.placetteDao(), metadata, mutationGuard, access)

    @Before fun setup() = runBlocking {
        identity.login(ACCOUNT_A)
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, ForestryDatabase::class.java,
        ).build()
        listOf(FOREST, OFFLINE_FOREST).forEach { id ->
            database.foretDao().insert(Foret(
                foretId = id, nom = "TEST forest", proprietaireNom = "TEST",
                proprietaireEmail = null, gestionnaireNom = null, typeForet = null,
                objectifGestion = null, psgNumero = null, psgDateExpiration = null,
                departement = null, remarques = null,
            ).toEntity())
        }
        database.parcelleDao().insertParcelle(parcel(PARCEL, FOREST).toParcelleEntity())
        database.parcelleDao().insertParcelle(parcel(OFFLINE_PARCEL, OFFLINE_FOREST).toParcelleEntity())
        database.essenceDao().insertEssence(EssenceEntity("TEST-oak", "TEST oak", null, null, null))
        database.hierarchySyncDao().insertAssociation(association(FOREST, ACCOUNT_A))
        database.placetteDao().insertNewPlacettes(listOf(
            plot(PLOT, PARCEL).copy(auteur = ACCOUNT_B).toPlacetteEntity(),
            plot(OFFLINE_PLOT, OFFLINE_PARCEL).toPlacetteEntity(),
        ))
        access = BusinessReadAccess(identity, database.parcelSyncDao())
        val writer = InventaireSessionRepositoryImpl(database.inventaireSessionDao(), metadata, clock, mutationGuard)
        val treeWriter = TigeRepositoryImpl(database.tigeDao(), metadata, clock, mutationGuard)
        linkedSession = writer.startForPlacette(PARCEL, PLOT).sessionId
        treeWriter.insertTige(tree(TREE, PARCEL, PLOT))
        writer.closeMartelage(linkedSession, null)
        offlineSession = writer.startForPlacette(OFFLINE_PARCEL, OFFLINE_PLOT).sessionId
        treeWriter.insertTige(tree(OFFLINE_TREE, OFFLINE_PARCEL, OFFLINE_PLOT))
        writer.closeMartelage(offlineSession, null)
        // A historical null-session observation remains unassigned; its author cannot grant read access.
        database.tigeDao().insertTige(tree(LEGACY_TREE, PARCEL, PLOT)
            .copy(sessionId = null, auteur = ACCOUNT_B).toTigeEntity())
    }

    @After fun cleanup() { database.close() }

    @Test fun foreign_account_and_logout_mask_every_child_read_without_author_payload_permissions() = runBlocking {
        withTimeout(TIMEOUT) {
            assertEquals(3, stable(trees.getAllTiges()).size)
            assertNotNull(sessions.getClosedSnapshot(linkedSession))
            for (account in listOf(ACCOUNT_B, null)) {
                identity.login(account)
                assertEquals(listOf(OFFLINE_TREE), stable(trees.getAllTiges()).map { it.id })
                assertTrue(stable(trees.getTigesByParcelle(PARCEL)).isEmpty())
                assertTrue(stable(trees.getTigesByPlacette(PLOT)).isEmpty())
                assertNull(stable(trees.getTigeById(TREE)))
                assertNull(stable(trees.getTigeById(LEGACY_TREE)))
                assertTrue(stable(plots.getPlacettesByParcelle(PARCEL)).isEmpty())
                assertNull(stable(plots.getPlacetteById(PLOT)))
                assertTrue(stable(sessions.getByParcelle(PARCEL)).isEmpty())
                assertNull(sessions.getById(linkedSession))
                assertNull(sessions.getLatestByType(PARCEL, "MARTELAGE"))
                assertNull(sessions.getClosedSnapshot(linkedSession))
            }
            // No row was removed, reassigned or modified to enforce read access.
            assertEquals(3, database.tigeDao().getAllTigesNow().size)
            assertEquals(ACCOUNT_B, database.placetteDao().getPlacetteById(PLOT)?.auteur)
        }
    }

    @Test fun continuing_flow_clears_owner_rows_on_account_switch_and_logout_without_child_writes() = runBlocking {
        withTimeout(TIMEOUT) {
            val changes = Channel<List<Tige>>(Channel.UNLIMITED)
            val job = launch { trees.getAllTiges().collect { changes.send(it) } }
            try {
                while (changes.receive().none { it.id == TREE }) { /* Wait for the authorized Room value. */ }
                identity.login(ACCOUNT_B)
                while (changes.receive().any { it.id == TREE }) { /* Account switch must clear the old value. */ }
                assertEquals(listOf(OFFLINE_TREE), stable(trees.getAllTiges()).map { it.id })
                identity.login(null)
                assertEquals(listOf(OFFLINE_TREE), stable(trees.getAllTiges()).map { it.id })
                identity.login(ACCOUNT_A)
                while (changes.receive().none { it.id == TREE }) { /* Original owner can read again. */ }
            } finally {
                job.cancel()
                job.join()
                changes.close()
            }
        }
    }

    @Test fun guest_keeps_unlinked_local_plots_trees_sessions_and_closed_measurements_readable() = runBlocking {
        withTimeout(TIMEOUT) {
            identity.login(null)
            assertEquals(OFFLINE_TREE, stable(trees.getTigeById(OFFLINE_TREE))?.id)
            assertEquals(OFFLINE_TREE, stable(trees.getTigesByPlacette(OFFLINE_PLOT)).single().id)
            assertEquals(OFFLINE_PLOT, stable(plots.getPlacetteById(OFFLINE_PLOT))?.id)
            assertEquals(offlineSession, stable(sessions.getByParcelle(OFFLINE_PARCEL)).single().sessionId)
            assertEquals(offlineSession, sessions.getById(offlineSession)?.sessionId)
            assertEquals(offlineSession, sessions.getLatestByType(OFFLINE_PARCEL, "MARTELAGE")?.sessionId)
            assertEquals(OFFLINE_TREE, sessions.getClosedSnapshot(offlineSession)?.tiges?.single()?.id)
        }
    }

    @Test fun closed_snapshot_rejects_same_account_reauthentication_after_the_dao_read() = runBlocking {
        withTimeout(TIMEOUT) {
            val dao = interceptSessionRead { identity.login(null); identity.login(ACCOUNT_A) }
            assertNull(sessionRepository(dao).getClosedSnapshot(linkedSession))
            assertEquals(ACCOUNT_A, identity.session.value?.accountId)
            assertNotNull(sessions.getClosedSnapshot(linkedSession))
        }
    }

    @Test fun suspended_session_reads_reject_account_change_before_returning_a_row() = runBlocking {
        withTimeout(TIMEOUT) {
            assertNull(sessionRepository(interceptSessionRead { identity.login(ACCOUNT_B) }).getById(linkedSession))
            identity.login(ACCOUNT_A)
            val dao = object : InventaireSessionDao by database.inventaireSessionDao() {
                override suspend fun getLatestByType(parcelleId: String, type: String): InventaireSessionEntity? {
                    val row = database.inventaireSessionDao().getLatestByType(parcelleId, type)
                    identity.login(null)
                    return row
                }
            }
            assertNull(sessionRepository(dao).getLatestByType(PARCEL, "MARTELAGE"))
        }
    }

    @Test fun closed_snapshot_rejects_a_new_foreign_forest_association_during_read() = runBlocking {
        withTimeout(TIMEOUT) {
            assertNotNull(sessions.getClosedSnapshot(offlineSession))
            val dao = interceptSessionRead {
                database.hierarchySyncDao().insertAssociation(association(OFFLINE_FOREST, ACCOUNT_B))
            }
            assertNull(sessionRepository(dao).getClosedSnapshot(offlineSession))
            assertNull(sessions.getClosedSnapshot(offlineSession))
            assertTrue(stable(trees.getTigesByParcelle(OFFLINE_PARCEL)).isEmpty())
        }
    }

    @Test fun delayed_tree_flow_cannot_expose_owner_rows_after_logout() = runBlocking {
        withTimeout(TIMEOUT) {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val dao = object : TigeDao by database.tigeDao() {
                override fun getAllTiges() = flow {
                    val rows = database.tigeDao().getAllTigesNow()
                    entered.complete(Unit)
                    release.await()
                    emit(rows)
                }
            }
            val repository = TigeRepositoryImpl(dao, metadata, clock, mutationGuard, access)
            val read = async { repository.getAllTiges().first() }
            entered.await()
            identity.login(null)
            release.complete(Unit)
            assertTrue(read.await().none { it.id == TREE })
            assertEquals(3, database.tigeDao().getAllTigesNow().size)
        }
    }

    private fun sessionRepository(dao: InventaireSessionDao = database.inventaireSessionDao()) =
        InventaireSessionRepositoryImpl(dao, metadata, clock, mutationGuard, access)

    private fun interceptSessionRead(afterRead: suspend () -> Unit) =
        object : InventaireSessionDao by database.inventaireSessionDao() {
            override suspend fun getById(id: String): InventaireSessionEntity? {
                val row = database.inventaireSessionDao().getById(id)
                afterRead()
                return row
            }
        }

    /** First value is the actual authorized SQLite result, never a loading sentinel. */
    private suspend fun <T> stable(source: Flow<T>): T = source.first()

    private fun association(forest: String, account: String) = HierarchySyncAssociationEntity(
        forestId = forest, accountId = account, apiOrigin = "https://test.invalid", activatedAt = 100L,
        lastCapturedHash = null, serverVersion = null, lastSuccessAt = null, lastErrorCode = null,
    )

    private fun parcel(id: String, forest: String) = Parcelle(
        id = id, forestId = null, foretId = forest, name = "TEST parcel", surfaceHa = 1.25,
        shape = "POLYGON ((2 46, 3 46, 3 47, 2 47, 2 46))", slopePct = null,
        aspect = null, access = null, altitudeM = null, objectifType = null, objectifVal = null,
        tolerancePct = null, samplingMode = null, sampleAreaM2 = null, targetSpeciesCsv = null,
        srid = 4326, remarks = null,
    )

    private fun plot(id: String, parent: String) = Placette(
        id = id, parcelleId = parent, name = "TEST plot", type = "ENTIRE_PARCEL",
        rayonM = null, surfaceM2 = 12_500.0, centerWkt = null,
    )

    private fun tree(id: String, parent: String, plot: String) = Tige(
        id = id, parcelleId = parent, placetteId = plot, essenceCode = "TEST-oak", diamCm = 30.0,
        hauteurM = null, gpsWkt = null, precisionM = null, altitudeM = null,
        note = null, produit = null, fCoef = null, valueEur = null, timestamp = 300L,
    )

    private companion object {
        const val TIMEOUT = 20_000L
        const val ACCOUNT_A = "TEST-read-owner-a"
        const val ACCOUNT_B = "TEST-read-other-b"
        const val FOREST = "TEST-read-linked-forest"
        const val OFFLINE_FOREST = "TEST-read-offline-forest"
        const val PARCEL = "TEST-read-linked-parcel"
        const val OFFLINE_PARCEL = "TEST-read-offline-parcel"
        const val PLOT = "TEST-read-linked-plot"
        const val OFFLINE_PLOT = "TEST-read-offline-plot"
        const val LEGACY_TREE = "TEST-read-null-session-tree"
        const val TREE = "TEST-read-linked-tree"
        const val OFFLINE_TREE = "TEST-read-offline-tree"
    }
}
