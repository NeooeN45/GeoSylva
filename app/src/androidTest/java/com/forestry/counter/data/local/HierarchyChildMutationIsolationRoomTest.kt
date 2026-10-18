package com.forestry.counter.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.data.local.dao.InventaireSessionDao
import com.forestry.counter.data.local.dao.PlacetteDao
import com.forestry.counter.data.local.dao.TigeDao
import com.forestry.counter.data.local.entity.EssenceEntity
import com.forestry.counter.data.local.entity.HierarchySyncAssociationEntity
import com.forestry.counter.data.local.entity.InventaireSessionEntity
import com.forestry.counter.data.local.entity.PlacetteEntity
import com.forestry.counter.data.local.entity.TigeEntity
import com.forestry.counter.data.mapper.toDomain
import com.forestry.counter.data.mapper.toEntity
import com.forestry.counter.data.mapper.toParcelleEntity
import com.forestry.counter.data.mapper.toPlacette
import com.forestry.counter.data.mapper.toPlacetteEntity
import com.forestry.counter.data.mapper.toTige
import com.forestry.counter.data.mapper.toTigeEntity
import com.forestry.counter.data.repository.InventaireSessionRepositoryImpl
import com.forestry.counter.data.repository.PlacetteRepositoryImpl
import com.forestry.counter.data.repository.TigeRepositoryImpl
import com.forestry.counter.data.service.MetadataService
import com.forestry.counter.domain.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Dedicated real in-memory Room database. TEST identities never enter a user dossier. */
@RunWith(AndroidJUnit4::class)
class HierarchyChildMutationIsolationRoomTest {
    private lateinit var database: ForestryDatabase
    private var account: String? = ACCOUNT_A
    private var epoch = 1
    private val metadata = MetadataService { account }
    private val clock = object : MartelageClock {
        override fun read() = MartelageClockReading(1_000L, 500L, 7)
    }
    private val guardFactory: (String?) -> (() -> Boolean) = { initiatingAccount ->
        val initiatingEpoch = epoch
        val isCurrent: () -> Boolean = { account == initiatingAccount && epoch == initiatingEpoch }
        isCurrent
    }
    private val sessions get() = InventaireSessionRepositoryImpl(database.inventaireSessionDao(), metadata, clock, guardFactory)
    private val trees get() = TigeRepositoryImpl(database.tigeDao(), metadata, clock, guardFactory)
    private val plots get() = PlacetteRepositoryImpl(database.placetteDao(), metadata, guardFactory)

    @Before fun setup() = runBlocking {
        account = ACCOUNT_A
        epoch = 1
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, ForestryDatabase::class.java,
        ).build()
        database.foretDao().insert(Foret(
            foretId = FOREST, nom = "TEST linked forest", proprietaireNom = "TEST",
            proprietaireEmail = null, gestionnaireNom = null, typeForet = null,
            objectifGestion = null, psgNumero = null, psgDateExpiration = null,
            departement = null, remarques = null,
        ).toEntity())
        database.parcelleDao().insertParcelle(parcel(PARCEL, FOREST).toParcelleEntity())
        database.parcelleDao().insertParcelle(parcel(OFFLINE_PARCEL, null).toParcelleEntity())
        database.essenceDao().insertEssence(EssenceEntity("TEST-oak", "TEST oak", null, null, null))
        database.hierarchySyncDao().insertAssociation(HierarchySyncAssociationEntity(
            forestId = FOREST, accountId = ACCOUNT_A, apiOrigin = "https://test.invalid",
            activatedAt = 100L, lastCapturedHash = null, serverVersion = null,
            lastSuccessAt = null, lastErrorCode = null,
        ))
        // Legacy/unassigned rows with misleading author metadata still belong to the linked parent.
        database.tigeDao().insertTige(tree("TEST-old").copy(auteur = ACCOUNT_B).toTigeEntity())
        database.placetteDao().insertNewPlacettes(listOf(plot("TEST-old-plot").copy(auteur = ACCOUNT_B).toPlacetteEntity()))
        database.inventaireSessionDao().insert(legacy("TEST-old-session").copy(auteur = ACCOUNT_B).toEntity())
    }
    @After fun cleanup() { database.close() }

    @Test fun forest_only_association_is_seen_by_all_child_daos_without_parcel_consent() = runBlocking {
        withTimeout(15_000L) {
            assertTrue(database.parcelSyncDao().getAccountIdsForParcel(PARCEL).isEmpty())
            assertEquals(listOf(ACCOUNT_A), database.inventaireSessionDao().getLinkedAccounts(PARCEL))
            assertEquals(listOf(ACCOUNT_A), database.tigeDao().getLinkedAccountsForMutation(PARCEL))
            assertEquals(listOf(ACCOUNT_A), database.placetteDao().getLinkedAccountsForCreation(PARCEL))
        }
    }

    @Test fun account_b_cannot_start_or_edit_legacy_sessions_with_an_author_a_payload() = runBlocking {
        withTimeout(15_000L) {
            account = ACCOUNT_B
            val before = database.inventaireSessionDao().getById("TEST-old-session")!!
            assertTrue(runCatching { sessions.startForPlacette(PARCEL, "TEST-old-plot") }.isFailure)
            assertTrue(runCatching { sessions.insert(legacy("TEST-new-session").copy(auteur = ACCOUNT_A)) }.isFailure)
            assertTrue(runCatching { sessions.update(before.toDomain().copy(remarques = "TEST forbidden", auteur = ACCOUNT_A)) }.isFailure)
            assertTrue(runCatching { sessions.deleteById(before.sessionId) }.isFailure)
            assertEquals(before, database.inventaireSessionDao().getById(before.sessionId))
            assertNull(database.inventaireSessionDao().getById("TEST-new-session"))
            assertTrue(database.inventaireSessionDao().getActiveForParcelle(PARCEL).isEmpty())
        }
    }

    @Test fun account_b_cannot_insert_update_delete_or_deliver_late_gps_to_an_unassigned_tree() = runBlocking {
        withTimeout(15_000L) {
            account = ACCOUNT_B
            val before = database.tigeDao().getTigeById("TEST-old")!!
            assertNull(before.sessionId)
            assertTrue(runCatching { trees.insertTige(tree("TEST-new").copy(auteur = ACCOUNT_A)) }.isFailure)
            assertTrue(runCatching { trees.updateTige(before.toTige().copy(hauteurM = 20.0, auteur = ACCOUNT_A)) }.isFailure)
            assertFalse(trees.setTigeGps(before.tigeId, "POINT (2.2 46.2)", 2.0, null))
            assertTrue(runCatching { trees.deleteTige(before.tigeId) }.isFailure)
            assertTrue(runCatching { trees.deleteTigesByParcelle(PARCEL) }.isFailure)
            assertEquals(before, database.tigeDao().getTigeById(before.tigeId))
            assertNull(database.tigeDao().getTigeById("TEST-new"))
        }
    }

    @Test fun account_b_cannot_insert_batch_update_or_delete_plots_even_with_author_a() = runBlocking {
        withTimeout(15_000L) {
            account = ACCOUNT_B
            val before = database.placetteDao().getPlacetteById("TEST-old-plot")!!
            assertTrue(runCatching { plots.insertPlacette(plot("TEST-new-plot").copy(auteur = ACCOUNT_A)) }.isFailure)
            assertTrue(runCatching { plots.insertPlacettesForParcelle(listOf(plot("TEST-whole").copy(auteur = ACCOUNT_A))) }.isFailure)
            assertTrue(runCatching { plots.updatePlacette(before.toPlacette().copy(name = "TEST forbidden", auteur = ACCOUNT_A)) }.isFailure)
            assertTrue(runCatching { plots.deletePlacette(before.placetteId) }.isFailure)
            assertTrue(runCatching { plots.deletePlacettesByParcelle(PARCEL) }.isFailure)
            assertEquals(before, database.placetteDao().getPlacetteById(before.placetteId))
            assertEquals(1, database.placetteDao().getAllPlacettesNow().size)
        }
    }

    @Test fun owner_a_can_edit_children_then_start_pause_resume_and_close_without_reassigning_old_trees() = runBlocking {
        withTimeout(15_000L) {
            assertTrue(trees.setTigeHeight("TEST-old", 17.0))
            plots.updatePlacette(database.placetteDao().getPlacetteById("TEST-old-plot")!!.toPlacette().copy(name = "TEST revised"))
            plots.insertPlacette(plot("TEST-extra-plot"))
            plots.deletePlacette("TEST-extra-plot")
            val legacy = sessions.getById("TEST-old-session")!!
            sessions.update(legacy.copy(remarques = "TEST owner revision"))
            sessions.deleteById(legacy.sessionId)
            val running = sessions.startForPlacette(PARCEL, "TEST-old-plot")
            trees.insertTige(tree("TEST-running").copy(placetteId = "TEST-old-plot"))
            assertEquals(running.sessionId, database.tigeDao().getTigeById("TEST-running")!!.sessionId)
            assertNull(database.tigeDao().getTigeById("TEST-old")!!.sessionId)
            sessions.pauseMartelage(running.sessionId)
            assertFalse(trees.setTigeHeight("TEST-running", 18.0))
            sessions.resumeMartelage(running.sessionId)
            assertTrue(trees.setTigeHeight("TEST-running", 18.0))
            sessions.closeMartelage(running.sessionId, null)
            assertFalse(trees.setTigeHeight("TEST-running", 19.0))
            assertNull(database.tigeDao().getTigeById("TEST-old")!!.sessionId)
            plots.deletePlacettesByParcelle(PARCEL)
            assertTrue(database.placetteDao().getAllPlacettesNow().isEmpty())
        }
    }

    @Test fun unauthenticated_guest_cannot_mutate_linked_children_but_can_work_on_unlinked_offline_dossier() = runBlocking {
        withTimeout(15_000L) {
            account = null
            assertTrue(runCatching { sessions.startForPlacette(PARCEL, "TEST-old-plot") }.isFailure)
            assertTrue(runCatching { trees.insertTige(tree("TEST-linked-guest")) }.isFailure)
            assertFalse(trees.setTigeHeight("TEST-old", 99.0))
            assertTrue(runCatching { plots.insertPlacette(plot("TEST-linked-plot")) }.isFailure)
            assertTrue(runCatching { plots.deletePlacettesByParcelle(PARCEL) }.isFailure)
            plots.insertPlacette(plot("TEST-offline-plot", OFFLINE_PARCEL))
            plots.updatePlacette(plot("TEST-offline-plot", OFFLINE_PARCEL).copy(name = "TEST guest revised"))
            val running = sessions.startForPlacette(OFFLINE_PARCEL, "TEST-offline-plot")
            trees.insertTige(tree("TEST-offline-tree", OFFLINE_PARCEL).copy(placetteId = "TEST-offline-plot"))
            assertEquals(running.sessionId, database.tigeDao().getTigeById("TEST-offline-tree")!!.sessionId)
            assertEquals("anonymous", database.tigeDao().getTigeById("TEST-offline-tree")!!.auteur)
            assertTrue(trees.setTigeHeight("TEST-offline-tree", 12.0))
            trees.deleteTige("TEST-offline-tree")
            sessions.closeMartelage(running.sessionId, null)
            plots.deletePlacette("TEST-offline-plot")
            assertTrue(database.placetteDao().getLinkedAccountsForCreation(OFFLINE_PARCEL).isEmpty())
        }
    }

    @Test fun same_account_reauthentication_after_each_write_rolls_back_session_tree_and_plot_transactions() = runBlocking {
        withTimeout(15_000L) {
            val sessionDao = object : InventaireSessionDao by database.inventaireSessionDao() {
                override suspend fun insert(session: InventaireSessionEntity) {
                    database.inventaireSessionDao().insert(session)
                    epoch++ // Opaque generation changes; account ID intentionally remains A.
                }
            }
            val sessionRepository = InventaireSessionRepositoryImpl(sessionDao, metadata, clock, guardFactory)
            assertTrue(runCatching { sessionRepository.startForPlacette(PARCEL, "TEST-old-plot") }.isFailure)
            assertTrue(database.inventaireSessionDao().getActiveForParcelle(PARCEL).isEmpty())

            val treeDao = object : TigeDao by database.tigeDao() {
                override suspend fun insertNewTiges(entities: List<TigeEntity>) {
                    database.tigeDao().insertNewTiges(entities)
                    epoch++
                }
            }
            val treeRepository = TigeRepositoryImpl(treeDao, metadata, clock, guardFactory)
            assertTrue(runCatching { treeRepository.insertTige(tree("TEST-epoch-tree")) }.isFailure)
            assertNull(database.tigeDao().getTigeById("TEST-epoch-tree"))

            val plotDao = object : PlacetteDao by database.placetteDao() {
                override suspend fun insertNewPlacettes(entities: List<PlacetteEntity>) {
                    database.placetteDao().insertNewPlacettes(entities)
                    epoch++
                }
            }
            val plotRepository = PlacetteRepositoryImpl(plotDao, metadata, guardFactory)
            assertTrue(runCatching { plotRepository.insertPlacette(plot("TEST-epoch-plot")) }.isFailure)
            assertNull(database.placetteDao().getPlacetteById("TEST-epoch-plot"))
            assertEquals(ACCOUNT_A, account)
        }
    }

    private fun parcel(id: String, forest: String?) = Parcelle(
        id = id, forestId = null, foretId = forest, name = "TEST parcel", surfaceHa = 1.25,
        shape = "POLYGON ((2 46, 3 46, 3 47, 2 47, 2 46))", slopePct = null,
        aspect = null, access = null, altitudeM = null, objectifType = null, objectifVal = null,
        tolerancePct = null, samplingMode = null, sampleAreaM2 = null, targetSpeciesCsv = null,
        srid = 4326, remarks = null,
    )
    private fun plot(id: String, parent: String = PARCEL) = Placette(
        id = id, parcelleId = parent, name = "TEST plot", type = "ENTIRE_PARCEL",
        rayonM = null, surfaceM2 = 12_500.0, centerWkt = null,
    )
    private fun tree(id: String, parent: String = PARCEL) = Tige(
        id = id, parcelleId = parent, placetteId = null, essenceCode = "TEST-oak", diamCm = 30.0,
        hauteurM = null, gpsWkt = null, precisionM = null, altitudeM = null,
        note = null, produit = null, fCoef = null, valueEur = null, timestamp = 300L,
    )
    private fun legacy(id: String) = InventaireSession(
        sessionId = id, parcelleId = PARCEL, typeSession = "TEST-legacy", dateDebut = 100L,
        dateFin = null, operateurNom = null, methode = null, intensiteEchantillonnagePct = null,
        objectifSession = null, remarques = null, createdAt = 100L,
    )
    private companion object {
        const val ACCOUNT_A = "TEST-owner-a"
        const val ACCOUNT_B = "TEST-other-b"
        const val FOREST = "TEST-linked-forest"
        const val PARCEL = "TEST-linked-parcel"
        const val OFFLINE_PARCEL = "TEST-offline-parcel"
    }
}
