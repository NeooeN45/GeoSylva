package com.forestry.counter.data.local

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.data.repository.*
import com.forestry.counter.domain.model.*
import com.forestry.counter.domain.repository.*
import com.forestry.counter.domain.wood.*
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic TEST wood only, stored in a dedicated file; never touches the app database. */
@RunWith(AndroidJUnit4::class)
class WoodObjectRoomTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: ForestryDatabase
    private lateinit var identity: BusinessReadIdentityFixture
    private lateinit var repository: WoodObjectRepositoryImpl
    private lateinit var access: WoodObjectAccess
    private var time = 1000L
    @Before fun setup() = runBlocking<Unit> {
        context.deleteDatabase(DB)
        db = open()
        identity = BusinessReadIdentityFixture()
        repository = repo()
        access = repository.captureAccess()
        val depots = WoodDepotRepositoryImpl(db, identity)
        depots.createDepot(DEPOT.toString(), WoodDepotDraft("TEST dépôt", WoodDepotKind.ROADSIDE), depots.captureAccess())
    }
    @After fun close() { db.close(); context.deleteDatabase(DB) }
    private fun open() = Room.databaseBuilder(context, ForestryDatabase::class.java, DB).addMigrations(*DatabaseMigrations.ALL).build()
    private fun repo() = WoodObjectRepositoryImpl(db, identity) { time }
    private fun c(id: UUID = OBJECT) = WoodCommandContext(UUID.randomUUID(), DEPOT, id, WoodOwner(WoodDepotOwnerScope.LOCAL_DEVICE), "anonymous")
    private suspend fun create() = repository.execute(WoodObjectCommand.Create(c(), WoodObjectKind.PILE,
        WoodObjectHeader("TEST pile 01", tag = "TEST P-1", declaredSpecies = "Chêne")), access)
    private fun observation(value: String = "12,50") = NewWoodObservation(UUID.randomUUID(), WoodObservationPayload(
        RawWoodMeasurement.Pile(RawWoodDimension(value, "m"), RawWoodDimension("2", "m"), RawWoodDimension("1,20", "m"),
            RawWoodMeasurement.Pile.RECTANGULAR_ENVELOPE, "TEST enveloppe, moyenne de trois hauteurs", RawWoodDimension("2,00", "m")),
        WoodObservedTime(Instant.parse("2026-10-18T10:00:00Z"), "Europe/Paris", 7200),
        description = "TEST côté route", declaredQuality = "TEST déclaré", declaredOrigin = "TEST parcelle"))
    private suspend fun expect(code: WoodRepositoryError, block: suspend () -> Unit) {
        try { block(); fail("Expected $code") } catch (error: WoodRepositoryException) { assertEquals(code, error.code) }
    }

    @Test fun encrypted_observations_and_selection_survive_reopen_and_can_be_purged() = runBlocking {
        val name = "TEST-encrypted-wood-objects.db"
        context.deleteDatabase(name)
        net.sqlcipher.database.SQLiteDatabase.loadLibs(context)
        fun openEncrypted() = Room.databaseBuilder(context, ForestryDatabase::class.java, name)
            .openHelperFactory(net.sqlcipher.database.SupportFactory("TEST-only-wood-records-key".toByteArray()))
            .addMigrations(*DatabaseMigrations.ALL).build()
        var encrypted = openEncrypted()
        val initial = observation()
        try {
            val depots = WoodDepotRepositoryImpl(encrypted, identity)
            depots.createDepot(DEPOT.toString(), WoodDepotDraft("TEST encrypted", WoodDepotKind.ROADSIDE), depots.captureAccess())
            val writer = WoodObjectRepositoryImpl(encrypted, identity)
            writer.execute(WoodObjectCommand.Create(c(), WoodObjectKind.PILE, WoodObjectHeader("TEST encrypted pile"), initial, true), writer.captureAccess())
            encrypted.close(); encrypted = openEncrypted()
            val reader = WoodObjectRepositoryImpl(encrypted, identity)
            val saved = reader.details(DEPOT, OBJECT, reader.captureAccess())
            assertEquals(initial.payload, saved.observations.single().payload)
            assertEquals(initial.id, saved.history.currentStateObservation?.observationId)
            encrypted.woodDataGate.purge {
                encrypted.withTransaction {
                    encrypted.woodObjectDao().deleteCommands(); encrypted.woodObjectDao().deleteSelections()
                    encrypted.woodObjectDao().deleteObservations(); encrypted.woodObjectDao().deleteObjects()
                    encrypted.woodDepotDao().hardDeleteAll()
                }
            }
            assertTrue(encrypted.woodObjectDao().objectsInDepot(DEPOT.toString()).isEmpty())
            encrypted.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { encrypted.close(); context.deleteDatabase(name) }
    }

    @Test fun raw_measurements_dates_and_receipt_survive_close_reopen_without_new_stock() = runBlocking {
        create()
        val raw = observation()
        val command = WoodObjectCommand.AddObservation(c(), 1, raw, false)
        val receipt = repository.execute(command, access)
        assertEquals(2, receipt.appliedObjectRevision)
        assertNull(receipt.effectiveObservationId)
        db.close(); db = open(); repository = repo(); access = repository.captureAccess()
        val details = repository.details(DEPOT, OBJECT, access)
        assertEquals(raw.payload, details.observations.single().payload)
        assertEquals(1000L, details.observations.single().record.observation.recordedAt.toEpochMilli())
        assertEquals(1, repository.list(DEPOT, access).size)
        assertTrue(details.history.effectiveSelection is WoodEffectiveSelection.None)
        val calculated = WoodObservationCalculator.calculate(details.observations.single().record.observation) as WoodObservationCalculation.Calculated
        assertEquals(30.0, calculated.result.volumeM3, 1e-10)
        time = 5000
        repository.execute(WoodObjectCommand.UpdateHeader(c(), 2, details.item.header.copy(name = "TEST renommé")), access)
        assertEquals(receipt, repository.execute(command, access))
        assertEquals(1, repository.details(DEPOT, OBJECT, access).observations.size)
    }

    @Test fun incomplete_observation_is_saved_without_invented_volume() = runBlocking {
        create()
        repository.execute(WoodObjectCommand.AddObservation(c(), 1, NewWoodObservation(UUID.randomUUID(),
            WoodObservationPayload(RawWoodMeasurement.Pile(length = RawWoodDimension("2,", "m")))), false), access)
        val saved = repository.details(DEPOT, OBJECT, access).observations.single()
        assertNull(saved.payload.observedTime)
        assertEquals("2,", (saved.payload.measurement as RawWoodMeasurement.Pile).length?.rawText)
        assertTrue(WoodObservationCalculator.calculate(saved.record.observation) is WoodObservationCalculation.Uncalculable)
    }

    @Test fun remeasurements_remain_one_object_and_correction_requires_explicit_selection() = runBlocking {
        create()
        val first = observation()
        repository.execute(WoodObjectCommand.AddObservation(c(), 1, first, true), access)
        val second = observation("8")
        repository.execute(WoodObjectCommand.AddObservation(c(), 2, second, false), access)
        val corrected = observation("11,50")
        repository.execute(WoodObjectCommand.CorrectObservation(c(), 3, first.id, 1, corrected, "TEST erreur de longueur"), access)
        var d = repository.details(DEPOT, OBJECT, access)
        assertEquals(1, repository.list(DEPOT, access).size)
        assertEquals(3, d.observations.size)
        assertEquals(first.payload, d.observations.first { it.record.observationId == first.id }.payload)
        assertTrue(d.history.effectiveSelection is WoodEffectiveSelection.Superseded)
        assertNull(d.history.currentStateObservation)
        expect(WoodRepositoryError.CONFLICT) { repository.execute(WoodObjectCommand.SelectEffective(c(), 4, first.id), access) }
        repository.execute(WoodObjectCommand.SelectEffective(c(), 4, corrected.id), access)
        d = repository.details(DEPOT, OBJECT, access)
        assertEquals(corrected.id, d.history.currentStateObservation?.observationId)
    }

    @Test fun stale_revision_and_changed_content_on_retry_do_not_overwrite() = runBlocking {
        create()
        val cmd = WoodObjectCommand.AddObservation(c(), 1, observation(), false)
        repository.execute(cmd, access)
        expect(WoodRepositoryError.CONFLICT) { repository.execute(cmd.copy(observation = observation("5")), access) }
        expect(WoodRepositoryError.CONFLICT) { repository.execute(WoodObjectCommand.AddObservation(c(), 1, observation("7"), false), access) }
        assertEquals(1, repository.details(DEPOT, OBJECT, access).observations.size)
    }

    @Test fun owner_and_generation_are_checked_and_archives_reject_new_writes() = runBlocking {
        create()
        identity.login("TEST-other")
        expect(WoodRepositoryError.ACCESS_CHANGED) { repository.list(DEPOT, access) }
        val other = repository.captureAccess()
        expect(WoodRepositoryError.UNAVAILABLE) { repository.list(DEPOT, other) }
        expect(WoodRepositoryError.INVALID_COMMAND) { repository.execute(WoodObjectCommand.Archive(c(), 1), other) }
        identity.login(null); access = repository.captureAccess()
        val archived = WoodObjectCommand.Archive(c(), 1)
        val receipt = repository.execute(archived, access)
        assertNotNull(repository.details(DEPOT, OBJECT, access).item.archivedAt)
        expect(WoodRepositoryError.UNAVAILABLE) { repository.execute(WoodObjectCommand.AddObservation(c(), 2, observation(), false), access) }
        assertEquals(receipt, repository.execute(archived, access))
    }

    @Test fun identity_change_inside_transaction_rolls_back_without_receipt() = runBlocking {
        val changed = WoodObjectRepositoryImpl(db, identity) { identity.login("TEST-other"); 2000L }
        val before = changed.captureAccess()
        val command = WoodObjectCommand.Create(c(), WoodObjectKind.PILE, WoodObjectHeader("TEST rollback"))
        expect(WoodRepositoryError.ACCESS_CHANGED) { changed.execute(command, before) }
        assertNull(db.woodObjectDao().getObject(OBJECT.toString()))
        assertNull(db.woodObjectDao().receipt(command.context.commandId.toString()))
    }

    @Test fun purge_invalidates_queued_writes_and_deletes_lineages_in_foreign_key_order() = runBlocking {
        create()
        val first = observation()
        repository.execute(WoodObjectCommand.AddObservation(c(), 1, first, true), access)
        repository.execute(WoodObjectCommand.CorrectObservation(c(), 2, first.id, 1, observation("9"), "TEST correction"), access)
        val lockEntered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val holder = launch { db.woodDataGate.withAccess(db.woodDataGate.capture()) { lockEntered.complete(Unit); release.await() } }
        lockEntered.await()
        val queued = async { runCatching { repository.execute(WoodObjectCommand.AddObservation(c(), 3, observation(), false), access) } }
        yield()
        val purge = launch(start = CoroutineStart.UNDISPATCHED) { db.woodDataGate.purge {
            db.withTransaction {
                db.woodObjectDao().deleteCommands(); db.woodObjectDao().deleteSelections()
                db.woodObjectDao().deleteObservations(); db.woodObjectDao().deleteObjects(); db.woodDepotDao().hardDeleteAll()
            }
        } }
        assertFalse(repository.isAccessCurrent(access))
        release.complete(Unit); holder.join(); purge.join()
        assertEquals(WoodRepositoryError.ACCESS_CHANGED, (queued.await().exceptionOrNull() as WoodRepositoryException).code)
        assertTrue(db.woodObjectDao().objectsInDepot(DEPOT.toString()).isEmpty())
        db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }

    @Test fun altered_stored_bytes_fail_closed() = runBlocking {
        create()
        repository.execute(WoodObjectCommand.AddObservation(c(), 1, observation(), false), access)
        db.openHelper.writableDatabase.execSQL("UPDATE wood_observations SET payloadJson='{}'")
        expect(WoodRepositoryError.CORRUPT_DATA) { repository.details(DEPOT, OBJECT, access) }
    }

    @Test fun archived_depot_keeps_history_but_blocks_new_observations() = runBlocking {
        create()
        val depotRepo = WoodDepotRepositoryImpl(db, identity)
        depotRepo.deleteDepot(DEPOT.toString(), 1, depotRepo.captureAccess())
        assertEquals("TEST pile 01", repository.details(DEPOT, OBJECT, access).item.header.name)
        expect(WoodRepositoryError.UNAVAILABLE) { repository.execute(WoodObjectCommand.AddObservation(c(), 1, observation(), false), access) }
    }

    companion object {
        private const val DB = "TEST-wood-objects.db"
        private val DEPOT = UUID.fromString("b0000000-0000-0000-0000-000000000001")
        private val OBJECT = UUID.fromString("b0000000-0000-0000-0000-000000000002")
    }
}
