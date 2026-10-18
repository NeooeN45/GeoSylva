package com.forestry.counter.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.data.local.dao.InventaireSessionDao
import com.forestry.counter.data.local.entity.InventaireSessionEntity
import com.forestry.counter.data.sync.hierarchy.BusinessMutationGuard
import com.forestry.counter.data.mapper.toParcelleEntity
import com.forestry.counter.data.mapper.toPlacetteEntity
import com.forestry.counter.data.repository.InventaireSessionRepositoryImpl
import com.forestry.counter.data.repository.MartelageGpsTraceRecorder
import com.forestry.counter.data.service.MetadataService
import com.forestry.counter.domain.model.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Mesures synthétiques explicitement TEST ONLY, enregistrées dans une base Room séparée. */
@RunWith(AndroidJUnit4::class)
class MartelageGpsTraceRoomTest {
    private lateinit var db: ForestryDatabase
    private lateinit var sessions: InventaireSessionRepositoryImpl
    private lateinit var recorder: MartelageGpsTraceRecorder
    private val clock = TestClock()
    private var account: String? = "TEST-account-A"
    private var generation = 1
    private val metadata = MetadataService { account }
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            ForestryDatabase::class.java).build()
        sessions = InventaireSessionRepositoryImpl(db.inventaireSessionDao(), metadata, clock)
        recorder = MartelageGpsTraceRecorder(db.inventaireSessionDao(), metadata, clock) { capturedAccount ->
            val capturedGeneration = generation
            { account == capturedAccount && generation == capturedGeneration }
        }
        db.parcelleDao().insertParcelle(Parcelle(id = "TEST-parcel", forestId = null, name = "TEST parcel",
            surfaceHa = 1.0, shape = null, slopePct = null, aspect = null, access = null, altitudeM = null,
            objectifType = null, objectifVal = null, tolerancePct = null, samplingMode = null,
            sampleAreaM2 = null, targetSpeciesCsv = null, srid = 4326, remarks = null,
            createdAt = 100, updatedAt = 100).toParcelleEntity())
        db.placetteDao().insertNewPlacettes(listOf("TEST-plot-A", "TEST-plot-B").map {
            Placette(it, "TEST-parcel", it, "CIRC", 10.0, 314.159, "POINT (2 46)", 100, 100).toPlacetteEntity()
        })
    }
    @After fun cleanup() { db.close() }
    @Test fun independent_plots_and_pause_segments_are_retained_without_joining() = runBlocking {
        withTimeout(15_000) {
            val a = sessions.startForPlacette("TEST-parcel", "TEST-plot-A")
            val b = sessions.startForPlacette("TEST-parcel", "TEST-plot-B")
            clock.reading = clock.reading.copy(elapsedRealtimeMs = 1200)
            assertTrue(recorder.append(a.sessionId, point()))
            assertNull(sessions.getById(b.sessionId)!!.traceJson)
            assertFalse(recorder.append(a.sessionId, point()))
            sessions.pauseMartelage(a.sessionId)
            assertTrue(runCatching { recorder.append(a.sessionId, point().copy(elapsedRealtimeMs = 1150)) }.isFailure)
            clock.reading = clock.reading.copy(elapsedRealtimeMs = 2000)
            sessions.resumeMartelage(a.sessionId)
            clock.reading = clock.reading.copy(elapsedRealtimeMs = 2300)
            assertTrue(recorder.append(a.sessionId, point().copy(elapsedRealtimeMs = 2200, segmentId = "7:2000:2100")))
            val trace = MartelageGpsTraceCodec.decode(sessions.getById(a.sessionId)!!.traceJson!!)
            assertEquals(2, trace.points.size)
            assertEquals(2, trace.points.map { it.segmentId }.distinct().size)
            assertNull(sessions.getById(b.sessionId)!!.traceJson)
        }
    }
    @Test fun concurrent_close_captures_exact_trace_and_refuses_late_positions() = runBlocking {
        withTimeout(15_000) {
            val session = sessions.startForPlacette("TEST-parcel", "TEST-plot-A")
            clock.reading = clock.reading.copy(elapsedRealtimeMs = 1200)
            coroutineScope {
                listOf(async(Dispatchers.IO) { sessions.closeMartelage(session.sessionId, null) },
                    async(Dispatchers.IO) { runCatching { recorder.append(session.sessionId, point()) } }).awaitAll()
            }
            val stored = sessions.getById(session.sessionId)!!
            val frozen = sessions.getClosedSnapshot(session.sessionId)!!
            assertEquals(stored.traceJson, frozen.traceJson)
            assertTrue(runCatching { recorder.append(session.sessionId, point().copy(elapsedRealtimeMs = 1150)) }.isFailure)
            assertEquals(stored.traceJson, sessions.getById(session.sessionId)!!.traceJson)
        }
    }
    @Test fun correction_does_not_duplicate_the_previous_route() = runBlocking {
        withTimeout(15_000) {
            val session = sessions.startForPlacette("TEST-parcel", "TEST-plot-A")
            clock.reading = clock.reading.copy(elapsedRealtimeMs = 1200)
            recorder.append(session.sessionId, point())
            val closed = sessions.closeMartelage(session.sessionId, null)
            val corrected = sessions.createCorrection(session.sessionId)
            assertNull(corrected.traceJson)
            assertEquals(closed.traceJson, sessions.getById(session.sessionId)!!.traceJson)
        }
    }
    @Test fun stale_service_authority_and_post_write_epoch_change_roll_back() = runBlocking {
        withTimeout(15_000) {
            val session = sessions.startForPlacette("TEST-parcel", "TEST-plot-A")
            clock.reading = clock.reading.copy(elapsedRealtimeMs = 1200)
            assertTrue(runCatching { recorder.append(session.sessionId, point()) { false } }.isFailure)
            assertNull(sessions.getById(session.sessionId)!!.traceJson)
            var calls = 0
            assertTrue(runCatching { recorder.append(session.sessionId, point()) { ++calls == 1 } }.isFailure)
            assertNull(sessions.getById(session.sessionId)!!.traceJson)
        }
    }
    @Test fun stale_cached_measurement_and_reboot_cannot_enter_trace() = runBlocking {
        withTimeout(15_000) {
            val session = sessions.startForPlacette("TEST-parcel", "TEST-plot-A")
            clock.reading = clock.reading.copy(elapsedRealtimeMs = 1200)
            assertTrue(runCatching { recorder.append(session.sessionId, point().copy(elapsedRealtimeMs = 900)) }.isFailure)
            clock.reading = clock.reading.copy(bootCount = 8)
            assertTrue(runCatching { recorder.append(session.sessionId, point()) }.isFailure)
            assertNull(sessions.getById(session.sessionId)!!.traceJson)
        }
    }
    @Test fun guest_login_and_logout_during_trace_write_roll_back_the_original_guest_transaction() = runBlocking {
        withTimeout(15_000) {
            val identity = BusinessReadIdentityFixture()
            val guestMetadata = MetadataService { identity.session.value?.accountId }
            val mutationGuard = BusinessMutationGuard(identity)::capture
            val guestSessions = InventaireSessionRepositoryImpl(db.inventaireSessionDao(), guestMetadata, clock, mutationGuard)
            val running = guestSessions.startForPlacette("TEST-parcel", "TEST-plot-A")
            val before = db.inventaireSessionDao().getById(running.sessionId)!!
            val switchingDao = object : InventaireSessionDao by db.inventaireSessionDao() {
                override suspend fun update(session: InventaireSessionEntity) {
                    db.inventaireSessionDao().update(session)
                    identity.login("TEST-account-A")
                    identity.login(null)
                }
            }
            val changingRecorder = MartelageGpsTraceRecorder(switchingDao, guestMetadata, clock, mutationGuard)
            clock.reading = clock.reading.copy(elapsedRealtimeMs = 1200)
            assertTrue(runCatching { changingRecorder.append(running.sessionId, point()) }.isFailure)
            assertNull(identity.session.value)
            assertEquals(before, db.inventaireSessionDao().getById(running.sessionId))
            assertNull(guestSessions.getById(running.sessionId)!!.traceJson)
            val freshRecorder = MartelageGpsTraceRecorder(db.inventaireSessionDao(), guestMetadata, clock, mutationGuard)
            assertTrue(freshRecorder.append(running.sessionId, point()))
            assertNotNull(guestSessions.getById(running.sessionId)!!.traceJson)
        }
    }

    private fun point() = MartelageGpsPoint(46.0, 2.0, 1500, 1100, 5.5, null, "7:1000:1050")
    private class TestClock : MartelageClock {
        var reading = MartelageClockReading(1000, 1000, 7)
        override fun read() = reading
    }
}
