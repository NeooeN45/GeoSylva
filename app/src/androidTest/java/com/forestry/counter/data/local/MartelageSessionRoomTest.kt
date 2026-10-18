package com.forestry.counter.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.data.local.entity.EssenceEntity
import com.forestry.counter.data.mapper.toEntity
import com.forestry.counter.data.mapper.toPlacetteEntity
import com.forestry.counter.data.mapper.toPlacette
import com.forestry.counter.data.mapper.toParcelleEntity
import com.forestry.counter.data.mapper.toParcelle
import com.forestry.counter.data.mapper.toTige
import com.forestry.counter.data.mapper.toTigeEntity
import com.forestry.counter.data.repository.InventaireSessionRepositoryImpl
import com.forestry.counter.data.repository.TigeRepositoryImpl
import com.forestry.counter.data.repository.PlacetteRepositoryImpl
import com.forestry.counter.data.repository.ParcelleRepositoryImpl
import com.forestry.counter.data.sync.ParcelMutationJournal
import com.forestry.counter.data.service.MetadataService
import com.forestry.counter.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room transactions in a dedicated TEST database, never the user's SQLCipher database. */
@RunWith(AndroidJUnit4::class)
class MartelageSessionRoomTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: ForestryDatabase
    private val clock = TestClock()
    private val metadata = MetadataService { "TEST-account" }
    private lateinit var sessions: InventaireSessionRepositoryImpl
    private lateinit var trees: TigeRepositoryImpl

    @Before fun setup() = runBlocking {
        context.deleteDatabase(DB_NAME)
        clock.reading = MartelageClockReading(1_000L, 500L, 7)
        database = open()
        bindRepositories()
        database.parcelleDao().insertParcelle(parcel().toParcelleEntity())
        database.placetteDao().insertNewPlacettes(listOf(plot("TEST-plot").toPlacetteEntity(), plot("TEST-plot-2").toPlacetteEntity()))
        database.essenceDao().insertEssence(EssenceEntity("TEST-oak", "TEST oak", null, null, null))
    }
    @After fun cleanup() { database.close(); context.deleteDatabase(DB_NAME) }

    @Test fun concurrent_start_creates_one_active_campaign() = runBlocking {
        withTimeout(15_000L) {
            val results = coroutineScope {
                listOf(async(Dispatchers.IO) { runCatching { sessions.startForPlacette("TEST-parcel", "TEST-plot") } },
                    async(Dispatchers.IO) { runCatching { sessions.startForPlacette("TEST-parcel", "TEST-plot") } }).awaitAll()
            }
            assertEquals(1, results.count { it.isSuccess })
            assertEquals(1, database.inventaireSessionDao().getActiveForParcelle("TEST-parcel").size)
        }
    }

    @Test fun old_unassigned_trees_are_not_claimed_by_start() = runBlocking {
        withTimeout(15_000L) {
            database.tigeDao().insertTige(tree("TEST-old").toTigeEntity())
            val session = sessions.startForPlacette("TEST-parcel", "TEST-plot")
            trees.insertTige(tree("TEST-new"))
            assertNull(database.tigeDao().getTigeById("TEST-old")!!.sessionId)
            assertEquals(session.sessionId, database.tigeDao().getTigeById("TEST-new")!!.sessionId)
        }
    }

    @Test fun paused_session_rejects_insert_and_mutations_until_resume() = runBlocking {
        withTimeout(15_000L) {
            val session = sessions.startForPlacette("TEST-parcel", "TEST-plot")
            trees.insertTige(tree("TEST-one"))
            clock.advance(2_000L)
            val paused = sessions.pauseMartelage(session.sessionId)
            assertEquals(2_000L, paused.activeDurationMs)
            clock.advance(10_000L)
            assertTrue(runCatching { trees.insertTige(tree("TEST-two")) }.isFailure)
            assertFalse(trees.setTigeHeight("TEST-one", 30.0))
            assertFalse(trees.deleteLatest("TEST-parcel", "TEST-plot", "TEST-oak", 30.0))
            assertEquals(paused, sessions.pauseMartelage(session.sessionId))
            sessions.resumeMartelage(session.sessionId)
            clock.advance(500L)
            val closed = sessions.closeMartelage(session.sessionId, null)
            assertEquals(2_500L, closed.activeDurationMs)
            assertNull(database.tigeDao().getTigeById("TEST-two"))
            assertNull(database.tigeDao().getTigeById("TEST-one")!!.hauteurM)
        }
    }

    @Test fun running_timer_survives_real_database_reopen_on_same_boot() = runBlocking {
        withTimeout(15_000L) {
            val session = sessions.startForPlacette("TEST-parcel", "TEST-plot")
            clock.advance(2_750L)
            database.close()
            database = open()
            bindRepositories()
            sessions.reconcileForPlacette("TEST-parcel", "TEST-plot")
            assertEquals(MartelageStates.RUNNING, sessions.getById(session.sessionId)!!.martelageState)
            assertEquals(2_750L, sessions.getById(session.sessionId)!!.durationAt(clock.read()))
            assertEquals(2_750L, sessions.closeMartelage(session.sessionId, null).activeDurationMs)
        }
    }

    @Test fun reboot_pauses_safely_and_blocks_late_measurements() = runBlocking {
        withTimeout(15_000L) {
            val session = sessions.startForPlacette("TEST-parcel", "TEST-plot")
            trees.insertTige(tree("TEST-one"))
            clock.advance(1_000L)
            sessions.pauseMartelage(session.sessionId)
            sessions.resumeMartelage(session.sessionId)
            clock.reading = MartelageClockReading(200_000L, 20L, 8)
            assertFalse(trees.setTigeGps("TEST-one", "POINT (2 46)", 5.0, null))
            sessions.reconcileForPlacette("TEST-parcel", "TEST-plot")
            val recovered = sessions.getById(session.sessionId)!!
            assertEquals(MartelageStates.PAUSED, recovered.martelageState)
            assertTrue(recovered.recoveryRequired)
            assertEquals(1_000L, recovered.activeDurationMs)
            sessions.resumeMartelage(session.sessionId)
            clock.advance(250L)
            assertEquals(1_250L, sessions.closeMartelage(session.sessionId, null).activeDurationMs)
        }
    }

    @Test fun close_and_gps_are_serialized_and_closed_snapshot_never_changes() = runBlocking {
        withTimeout(15_000L) {
            val session = sessions.startForPlacette("TEST-parcel", "TEST-plot")
            trees.insertTige(tree("TEST-one"))
            coroutineScope {
                listOf(async(Dispatchers.IO) { sessions.closeMartelage(session.sessionId, null) },
                    async(Dispatchers.IO) { trees.setTigeGps("TEST-one", "POINT (2 46)", 5.0, null) }).awaitAll()
            }
            val snapshot = sessions.getClosedSnapshot(session.sessionId)!!
            val stored = database.tigeDao().getTigeById("TEST-one")!!.gpsWkt
            assertEquals(stored, snapshot.tiges.single().gpsWkt)
            assertFalse(trees.setTigeGps("TEST-one", "POINT (3 47)", 1.0, null))
            assertEquals(snapshot, sessions.getClosedSnapshot(session.sessionId))
        }
    }

    @Test fun concurrent_insert_and_close_cannot_leave_an_unsealed_tree() = runBlocking {
        withTimeout(15_000L) {
            val session = sessions.startForPlacette("TEST-parcel", "TEST-plot")
            coroutineScope {
                listOf(async(Dispatchers.IO) { sessions.closeMartelage(session.sessionId, null) },
                    async(Dispatchers.IO) { runCatching { trees.insertTige(tree("TEST-racing")) } }).awaitAll()
            }
            assertEquals(database.inventaireSessionDao().getLiveTrees(session.sessionId).map { it.tigeId },
                sessions.getClosedSnapshot(session.sessionId)!!.tiges.map { it.id })
            assertTrue(runCatching { trees.insertTige(tree("TEST-after")) }.isFailure)
        }
    }

    @Test fun duplicate_tree_batch_is_atomic_without_replacement() = runBlocking {
        withTimeout(15_000L) {
            val session = sessions.startForPlacette("TEST-parcel", "TEST-plot")
            trees.insertTige(tree("TEST-existing"))
            assertTrue(runCatching { trees.insertTiges(listOf(tree("TEST-first"), tree("TEST-existing"))) }.isFailure)
            assertNull(database.tigeDao().getTigeById("TEST-first"))
            assertEquals(session.sessionId, database.tigeDao().getTigeById("TEST-existing")!!.sessionId)
            assertEquals(1, database.inventaireSessionDao().getLiveTrees(session.sessionId).size)
        }
    }

    @Test fun correction_clones_new_ids_and_keeps_sealed_previous_revision() = runBlocking {
        withTimeout(15_000L) {
            val first = sessions.startForPlacette("TEST-parcel", "TEST-plot")
            trees.insertTige(tree("TEST-original").copy(hauteurM = 18.75, numero = 21))
            sessions.closeMartelage(first.sessionId, null)
            val old = sessions.getClosedSnapshot(first.sessionId)!!
            val correction = sessions.createCorrection(first.sessionId)
            assertEquals(first.campaignId, correction.campaignId)
            assertEquals(2, correction.revisionNumber)
            assertEquals(first.sessionId, correction.previousSessionId)
            assertEquals(MartelageStates.PAUSED, correction.martelageState)
            val copy = database.inventaireSessionDao().getLiveTrees(correction.sessionId).single()
            assertNotEquals("TEST-original", copy.tigeId)
            assertEquals("TEST-original", copy.originTigeId)
            assertEquals(18.75, copy.hauteurM!!, 0.0)
            assertEquals(21, copy.numero)
            assertFalse(trees.setTigeHeight(copy.tigeId, 22.0))
            sessions.resumeMartelage(correction.sessionId)
            assertTrue(trees.setTigeHeight(copy.tigeId, 22.0))
            assertTrue(trees.deleteLatest("TEST-parcel", "TEST-plot", "TEST-oak", 30.0))
            assertNotNull(database.tigeDao().getTigeById("TEST-original"))
            assertEquals(old, sessions.getClosedSnapshot(first.sessionId))
            sessions.closeMartelage(correction.sessionId, null)
            assertTrue(runCatching { sessions.createCorrection(first.sessionId) }.isFailure)
        }
    }

    @Test fun repeated_close_preserves_first_payload_and_duration() = runBlocking {
        withTimeout(15_000L) {
            val session = sessions.startForPlacette("TEST-parcel", "TEST-plot")
            trees.insertTige(tree("TEST-one"))
            clock.advance(1_250L)
            val first = sessions.closeMartelage(session.sessionId, null)
            clock.advance(99_000L)
            assertEquals(first, sessions.closeMartelage(session.sessionId, null))
            assertEquals(1_250L, sessions.getClosedSnapshot(session.sessionId)!!.activeDurationMs)
            assertTrue(runCatching { sessions.update(first.copy(remarques = "Should not alter closure")) }.isFailure)
        }
    }

    @Test fun stale_calculation_refuses_close_without_modifying_session_or_trees() = runBlocking {
        withTimeout(15_000L) {
            val session = sessions.startForPlacette("TEST-parcel", "TEST-plot")
            trees.insertTige(tree("TEST-one"))
            val fingerprint = fingerprint(session.sessionId)
            trees.setTigeHeight("TEST-one", 25.0)
            val calculation = calculation(fingerprint)
            assertTrue(runCatching { sessions.closeMartelage(session.sessionId, calculation) }.isFailure)
            assertEquals(MartelageStates.RUNNING, sessions.getById(session.sessionId)!!.martelageState)
            assertNull(sessions.getClosedSnapshot(session.sessionId))
        }
    }

    @Test fun actual_calculation_is_sealed_and_read_back_after_reopen() = runBlocking {
        withTimeout(15_000L) {
            val session = sessions.startForPlacette("TEST-parcel", "TEST-plot")
            trees.insertTige(tree("TEST-one"))
            val calculation = calculation(fingerprint(session.sessionId))
            sessions.closeMartelage(session.sessionId, calculation)
            database.close(); database = open(); bindRepositories()
            val snapshot = sessions.getClosedSnapshot(session.sessionId)!!
            assertEquals(calculation, snapshot.calculation)
            assertEquals(listOf("TEST-one"), snapshot.tiges.map { it.id })
            assertNull(snapshot.parcelle.slopePct)
        }
    }

    @Test fun legacy_unknown_type_remains_nullable_and_generic_update_cannot_bypass_managed_state() = runBlocking {
        withTimeout(15_000L) {
            val legacy = InventaireSession(
                sessionId = "TEST-legacy", parcelleId = "TEST-parcel", typeSession = "UNKNOWN_OLD_TYPE",
                dateDebut = 30L, dateFin = null, operateurNom = null, methode = null,
                intensiteEchantillonnagePct = null, objectifSession = null, remarques = null,
            )
            database.inventaireSessionDao().insert(legacy.toEntity())
            val managed = sessions.startForPlacette("TEST-parcel", "TEST-plot")
            assertNull(sessions.getById(legacy.sessionId)!!.martelageState)
            assertTrue(runCatching { sessions.insert(managed.copy(sessionId = "TEST-bypass")) }.isFailure)
            assertEquals(2, sessions.getByParcelle("TEST-parcel").first().size)
        }
    }

    @Test fun two_plots_have_independent_running_clocks_pause_and_snapshot_scopes() = runBlocking {
        withTimeout(15_000L) {
            val first = sessions.startForPlacette("TEST-parcel", "TEST-plot")
            clock.advance(200L)
            val second = sessions.startForPlacette("TEST-parcel", "TEST-plot-2")
            trees.insertTige(tree("TEST-first"))
            trees.insertTige(tree("TEST-second").copy(placetteId = "TEST-plot-2"))
            clock.advance(300L)
            val paused = sessions.pauseMartelage(first.sessionId)
            assertEquals(500L, paused.activeDurationMs)
            assertEquals(MartelageStates.RUNNING, sessions.getById(second.sessionId)!!.martelageState)
            assertFalse(trees.setTigeHeight("TEST-first", 14.0))
            assertTrue(trees.setTigeHeight("TEST-second", 17.0))
            clock.advance(400L)
            sessions.closeMartelage(first.sessionId, null)
            val snapshot = sessions.getClosedSnapshot(first.sessionId)!!
            assertEquals(2, snapshot.schemaVersion)
            assertEquals("TEST-plot", snapshot.placetteId)
            assertEquals(listOf("TEST-plot"), snapshot.placettes.map { it.id })
            assertEquals(listOf("TEST-first"), snapshot.tiges.map { it.id })
            assertEquals(500L, snapshot.activeDurationMs)
            assertEquals(700L, sessions.getById(second.sessionId)!!.durationAt(clock.read()))
            assertTrue(trees.setTigeHeight("TEST-second", 18.0))
            assertEquals(listOf(first.sessionId), sessions.getByPlacette("TEST-plot").first().map { it.sessionId })
            assertEquals(listOf(second.sessionId), sessions.getByPlacette("TEST-plot-2").first().map { it.sessionId })
        }
    }

    @Test fun concurrent_start_on_different_plots_succeeds_without_a_parcel_singleton() = runBlocking {
        withTimeout(15_000L) {
            val values = coroutineScope {
                listOf(async(Dispatchers.IO) { sessions.startForPlacette("TEST-parcel", "TEST-plot") },
                    async(Dispatchers.IO) { sessions.startForPlacette("TEST-parcel", "TEST-plot-2") }).awaitAll()
            }
            assertEquals(2, values.map { it.sessionId }.distinct().size)
            assertEquals(2, database.inventaireSessionDao().getActiveForParcelle("TEST-parcel").size)
            assertEquals(1, database.inventaireSessionDao().getActiveForPlacette("TEST-parcel", "TEST-plot").size)
            assertEquals(1, database.inventaireSessionDao().getActiveForPlacette("TEST-parcel", "TEST-plot-2").size)
        }
    }

    @Test fun correction_clones_only_three_own_plot_stems_and_preserves_other_plot_cycle() = runBlocking {
        withTimeout(15_000L) {
            val first = sessions.startForPlacette("TEST-parcel", "TEST-plot")
            val second = sessions.startForPlacette("TEST-parcel", "TEST-plot-2")
            trees.insertTiges((1..3).map { tree("TEST-own-$it").copy(hauteurM = 10.0 + it) })
            trees.insertTige(tree("TEST-other").copy(placetteId = "TEST-plot-2"))
            sessions.closeMartelage(first.sessionId, null)
            val sealed = sessions.getById(first.sessionId)!!.closedSnapshotJson
            val revision = sessions.createCorrection(first.sessionId)
            val copies = database.inventaireSessionDao().getLiveTrees(revision.sessionId)
            assertEquals(3, copies.size)
            assertTrue(copies.all { it.placetteOwnerId == "TEST-plot" })
            assertEquals(setOf("TEST-own-1", "TEST-own-2", "TEST-own-3"), copies.map { it.originTigeId }.toSet())
            assertTrue(copies.none { it.tigeId in setOf("TEST-own-1", "TEST-own-2", "TEST-own-3") })
            assertEquals(MartelageStates.PAUSED, revision.martelageState)
            assertNull(revision.traceJson)
            assertEquals(sealed, sessions.getById(first.sessionId)!!.closedSnapshotJson)
            assertEquals(second.sessionId, database.tigeDao().getTigeById("TEST-other")!!.sessionId)
            assertTrue(trees.setTigeHeight("TEST-other", 22.0))
            sessions.resumeMartelage(revision.sessionId)
            trees.deleteTigesBySessionAndEssence(revision.sessionId, "TEST-oak")
            assertTrue(database.inventaireSessionDao().getLiveTrees(revision.sessionId).isEmpty())
            assertEquals(3, sessions.getClosedSnapshot(first.sessionId)!!.tiges.size)
            assertNotNull(database.tigeDao().getTigeById("TEST-other"))
        }
    }

    @Test fun foreign_plot_parent_and_unscoped_new_campaign_are_refused() = runBlocking {
        withTimeout(15_000L) {
            database.parcelleDao().insertParcelle(parcel().copy(id = "TEST-foreign-parcel").toParcelleEntity())
            database.placetteDao().insertNewPlacettes(listOf(plot("TEST-foreign-plot").copy(parcelleId = "TEST-foreign-parcel").toPlacetteEntity()))
            assertTrue(runCatching { sessions.startForPlacette("TEST-parcel", "TEST-foreign-plot") }.isFailure)
            assertTrue(runCatching { sessions.startForPlacette("TEST-parcel", "TEST-missing") }.isFailure)
            assertTrue(runCatching { sessions.startMartelage("TEST-parcel") }.isFailure)
            assertTrue(database.inventaireSessionDao().getActiveForParcelle("TEST-parcel").isEmpty())
        }
    }

    @Test fun legacy_null_scope_is_not_claimed_and_cannot_resume_close_or_clone() = runBlocking {
        withTimeout(15_000L) {
            val scoped = sessions.startForPlacette("TEST-parcel", "TEST-plot")
            val old = scoped.copy(sessionId = "TEST-legacy-null", placetteId = null,
                campaignId = "TEST-old-campaign", martelageState = MartelageStates.PAUSED,
                segmentStartedElapsedMs = null, segmentBootCount = null)
            database.inventaireSessionDao().insert(old.toEntity())
            database.tigeDao().insertTige(tree("TEST-old-null").copy(placetteId = null, sessionId = old.sessionId).toTigeEntity())
            assertTrue(runCatching { sessions.resumeMartelage(old.sessionId) }.isFailure)
            assertTrue(runCatching { sessions.closeMartelage(old.sessionId, null) }.isFailure)
            assertFalse(trees.setTigeHeight("TEST-old-null", 100.0))
            trees.insertTige(tree("TEST-new-scoped"))
            assertEquals(scoped.sessionId, database.tigeDao().getTigeById("TEST-new-scoped")!!.sessionId)
            assertNull(sessions.getById(old.sessionId)!!.placetteId)
            assertNull(database.tigeDao().getTigeById("TEST-old-null")!!.hauteurM)
            val second = sessions.startForPlacette("TEST-parcel", "TEST-plot-2")
            assertEquals("TEST-plot-2", second.placetteId)
        }
    }

    @Test fun recovery_is_scoped_to_one_plot_and_reboot_never_adds_work_time() = runBlocking {
        withTimeout(15_000L) {
            val first = sessions.startForPlacette("TEST-parcel", "TEST-plot")
            val second = sessions.startForPlacette("TEST-parcel", "TEST-plot-2")
            clock.reading = MartelageClockReading(90_000L, 5L, 8)
            sessions.reconcileForPlacette("TEST-parcel", "TEST-plot")
            assertEquals(MartelageStates.PAUSED, sessions.getById(first.sessionId)!!.martelageState)
            assertTrue(sessions.getById(first.sessionId)!!.recoveryRequired)
            assertEquals(0L, sessions.getById(first.sessionId)!!.activeDurationMs)
            assertEquals(MartelageStates.RUNNING, sessions.getById(second.sessionId)!!.martelageState)
            assertEquals(0L, sessions.getById(second.sessionId)!!.durationAt(clock.read()))
        }
    }

    @Test fun legacy_v1_snapshot_and_calculation_remain_byte_identical_and_read_only() = runBlocking {
        withTimeout(15_000L) {
            val oldTree = tree("TEST-legacy-sealed").copy(sessionId = "TEST-legacy-closed")
            val oldPlot = plot("TEST-plot")
            val calc = calculation(MartelageSnapshotFingerprint.computeLegacyV1(parcel(), listOf(oldPlot), listOf(oldTree)))
            val snapshot = MartelageSnapshotV1(
                sessionId = "TEST-legacy-closed", parcelleId = "TEST-parcel", campaignId = "TEST-legacy-campaign",
                revisionNumber = 1, previousSessionId = null, startedAt = 100L, closedAt = 200L,
                activeDurationMs = 50L, auteur = "TEST-account", parcelle = parcel(),
                placettes = listOf(oldPlot), tiges = listOf(oldTree), calculation = calc,
            )
            val encoded = Json.parseToJsonElement(MartelageSnapshotCodec.encode(snapshot)).jsonObject
            val oldPlots = JsonArray(encoded.getValue("placettes").jsonArray.map {
                JsonObject(it.jsonObject.filterKeys { key -> key != "samplingPlanJson" && key != "footprintGeoJson" })
            })
            val originalBytes = JsonObject(encoded.filterKeys { it != "placetteId" && it != "traceJson" }
                .toMutableMap().apply { put("placettes", oldPlots) }).toString()
            val legacy = InventaireSession(
                sessionId = snapshot.sessionId, parcelleId = snapshot.parcelleId, typeSession = MartelageStates.TYPE,
                dateDebut = 100L, dateFin = 200L, operateurNom = null, methode = null,
                intensiteEchantillonnagePct = null, objectifSession = null, remarques = null,
                martelageState = MartelageStates.CLOSED, campaignId = snapshot.campaignId,
                activeDurationMs = 50L, closedSnapshotJson = originalBytes,
            )
            database.inventaireSessionDao().insert(legacy.toEntity())
            assertEquals(snapshot, sessions.getClosedSnapshot(legacy.sessionId))
            assertTrue(runCatching { sessions.createCorrection(legacy.sessionId) }.isFailure)
            assertEquals(originalBytes, database.inventaireSessionDao().getById(legacy.sessionId)!!.closedSnapshotJson)
            assertNull(database.inventaireSessionDao().getById(legacy.sessionId)!!.placetteId)
        }
    }

    @Test fun close_refuses_an_injected_tree_from_another_plot_without_sealing_it() = runBlocking {
        withTimeout(15_000L) {
            val running = sessions.startForPlacette("TEST-parcel", "TEST-plot")
            database.tigeDao().insertTige(tree("TEST-invalid-link")
                .copy(placetteId = "TEST-plot-2", sessionId = running.sessionId).toTigeEntity())
            assertTrue(runCatching { sessions.closeMartelage(running.sessionId, null) }.isFailure)
            assertEquals(MartelageStates.RUNNING, sessions.getById(running.sessionId)!!.martelageState)
            assertNull(sessions.getById(running.sessionId)!!.closedSnapshotJson)
        }
    }

    @Test fun active_plot_delete_is_refused_while_closed_snapshot_remains_available_after_plot_delete() = runBlocking {
        withTimeout(15_000L) {
            val plotRepository = PlacetteRepositoryImpl(database.placetteDao(), metadata)
            val running = sessions.startForPlacette("TEST-parcel", "TEST-plot")
            trees.insertTige(tree("TEST-protected"))
            assertTrue(runCatching { plotRepository.deletePlacette("TEST-plot") }.isFailure)
            assertNotNull(database.placetteDao().getPlacetteById("TEST-plot"))
            sessions.pauseMartelage(running.sessionId)
            assertTrue(runCatching { plotRepository.deletePlacette("TEST-plot") }.isFailure)
            assertTrue(runCatching { plotRepository.deletePlacettesByParcelle("TEST-parcel") }.isFailure)
            assertNotNull(database.placetteDao().getPlacetteById("TEST-plot-2"))
            plotRepository.deletePlacette("TEST-plot-2")
            assertNull(database.placetteDao().getPlacetteById("TEST-plot-2"))
            val closed = sessions.closeMartelage(running.sessionId, null)
            plotRepository.deletePlacette("TEST-plot")
            assertNull(database.placetteDao().getPlacetteById("TEST-plot"))
            assertEquals(closed.closedSnapshotJson, sessions.getById(running.sessionId)!!.closedSnapshotJson)
            assertEquals(listOf("TEST-protected"), sessions.getClosedSnapshot(running.sessionId)!!.tiges.map { it.id })
        }
    }

    @Test fun parcel_and_bulk_delete_preflight_refuse_active_children_without_partial_tombstones_or_journal() = runBlocking {
        withTimeout(15_000L) {
            val idleParcel = parcel().copy(id = "TEST-idle-parcel")
            database.parcelleDao().insertParcelle(idleParcel.toParcelleEntity())
            database.placetteDao().insertNewPlacettes(listOf(plot("TEST-idle-plot").copy(parcelleId = idleParcel.id).toPlacetteEntity()))
            database.tigeDao().insertTige(tree("TEST-idle-tree").copy(parcelleId = idleParcel.id, placetteId = "TEST-idle-plot").toTigeEntity())
            val running = sessions.startForPlacette("TEST-parcel", "TEST-plot")
            trees.insertTige(tree("TEST-active-tree"))
            val repository = parcelRepository()
            assertTrue(runCatching { repository.deleteParcelle("TEST-parcel") }.isFailure)
            assertTrue(runCatching { repository.deleteAllParcelles() }.isFailure)
            assertNotNull(database.parcelleDao().getParcelleById("TEST-parcel"))
            assertNotNull(database.parcelleDao().getParcelleById(idleParcel.id))
            assertNotNull(database.placetteDao().getPlacetteById("TEST-plot"))
            assertNotNull(database.placetteDao().getPlacetteById("TEST-idle-plot"))
            assertNotNull(database.tigeDao().getTigeById("TEST-active-tree"))
            assertNotNull(database.tigeDao().getTigeById("TEST-idle-tree"))
            assertEquals(MartelageStates.RUNNING, sessions.getById(running.sessionId)!!.martelageState)
            assertTrue(database.parcelSyncOperationDao().getAllForParcel(null, "TEST-parcel").isEmpty())
            assertTrue(database.parcelSyncOperationDao().getAllForParcel(null, idleParcel.id).isEmpty())
        }
    }

    @Test fun null_scoped_legacy_active_session_blocks_parent_bulk_deletion_without_becoming_plot_scoped() = runBlocking {
        withTimeout(15_000L) {
            val legacy = InventaireSession("TEST-old-active", "TEST-parcel", MartelageStates.TYPE,
                100L, null, null, null, null, null, null, martelageState = MartelageStates.PAUSED,
                campaignId = "TEST-old-active", activeDurationMs = 75L)
            database.inventaireSessionDao().insert(legacy.toEntity())
            assertEquals(1, database.placetteDao().countActiveSessionsForParcel("TEST-parcel"))
            assertEquals(0, database.placetteDao().countActiveSessionsForPlot("TEST-parcel", "TEST-plot"))
            assertTrue(runCatching { parcelRepository().deleteParcelle("TEST-parcel") }.isFailure)
            assertTrue(runCatching { parcelRepository().deleteAllParcelles() }.isFailure)
            assertTrue(runCatching { PlacetteRepositoryImpl(database.placetteDao(), metadata)
                .deletePlacettesByParcelle("TEST-parcel") }.isFailure)
            assertNotNull(database.parcelleDao().getParcelleById("TEST-parcel"))
            assertNotNull(database.placetteDao().getPlacetteById("TEST-plot"))
            assertNotNull(database.placetteDao().getPlacetteById("TEST-plot-2"))
            assertEquals(legacy, sessions.getById(legacy.sessionId))
        }
    }

    private fun parcelRepository(): ParcelleRepositoryImpl {
        val journal = ParcelMutationJournal(database.parcelleDao(), database.parcelSyncDao(),
            database.parcelSyncOperationDao(), null, { "TEST-account" })
        return ParcelleRepositoryImpl(database.parcelleDao(), metadata, journal, database.tigeDao(), database.placetteDao())
    }

    private suspend fun fingerprint(sessionId: String): String {
        val parent = database.parcelleDao().getParcelleById("TEST-parcel")!!
        return MartelageSnapshotFingerprint.compute(
            parent.toParcelle(),
            listOf(database.inventaireSessionDao().getLivePlot("TEST-parcel", "TEST-plot")!!.toPlacette()), database.inventaireSessionDao().getLiveTrees(sessionId).map { row ->
                row.toTige()
            },
        )
    }
    private fun calculation(fingerprint: String) = MartelageCalculationSnapshotV1(
        generatedAt = clock.reading.wallTimeMs, engineVersion = "TEST-engine-v1",
        parameters = buildJsonObject { put("source", "TEST-actual-input") },
        results = buildJsonObject { put("measuredTreeCount", 1) }, sourceFingerprint = fingerprint,
    )
    private fun open() = Room.databaseBuilder(context, ForestryDatabase::class.java, DB_NAME)
        .addMigrations(*DatabaseMigrations.ALL).build()
    private fun bindRepositories() {
        sessions = InventaireSessionRepositoryImpl(database.inventaireSessionDao(), metadata, clock)
        trees = TigeRepositoryImpl(database.tigeDao(), metadata, clock)
    }
    private fun tree(id: String) = Tige(
        id = id, parcelleId = "TEST-parcel", placetteId = "TEST-plot", essenceCode = "TEST-oak", diamCm = 30.0,
        hauteurM = null, gpsWkt = null, precisionM = null, altitudeM = null, note = null,
        produit = null, fCoef = null, valueEur = null, timestamp = 300L,
    )
    private fun plot(id: String) = Placette(
        id = id, parcelleId = "TEST-parcel", name = "TEST plot", type = "CIRC",
        rayonM = 10.0, surfaceM2 = 314.159, centerWkt = "POINT (2.2 46.2)", createdAt = 100L, updatedAt = 100L,
    )
    private fun parcel() = Parcelle(
        id = "TEST-parcel", forestId = null, name = "TEST parcel", surfaceHa = 1.25, shape = null,
        slopePct = null, aspect = null, access = null, altitudeM = null,
        objectifType = null, objectifVal = null, tolerancePct = null, samplingMode = null,
        sampleAreaM2 = null, targetSpeciesCsv = null, srid = 4326, remarks = null, createdAt = 100L, updatedAt = 100L,
    )
    private class TestClock : MartelageClock {
        var reading = MartelageClockReading(1_000L, 500L, 7)
        override fun read() = reading
        fun advance(delta: Long) { reading = reading.copy(wallTimeMs = reading.wallTimeMs + delta, elapsedRealtimeMs = reading.elapsedRealtimeMs + delta) }
    }
    private companion object { const val DB_NAME = "TEST-martelage-campaigns.db" }
}
