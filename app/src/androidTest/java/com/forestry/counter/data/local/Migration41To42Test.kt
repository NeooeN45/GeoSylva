package com.forestry.counter.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.data.local.entity.ObservationQualificationEntity
import com.forestry.counter.domain.model.ObservationMeasurementBasis
import com.forestry.counter.domain.model.ObservationQualificationCodec
import com.forestry.counter.domain.model.ObservationQualificationDraft
import com.forestry.counter.domain.model.ObservationQualificationPayload
import com.forestry.counter.domain.model.Tige
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Migration et transactions réelles, exclusivement dans une base dédiée aux données TEST. */
@RunWith(AndroidJUnit4::class)
class Migration41To42Test {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var helper: MigrationTestHelper

    @Before
    fun setup() {
        context.deleteDatabase(DB_NAME)
        helper = MigrationTestHelper(
            instrumentation,
            "com.forestry.counter.data.local.ForestryDatabase",
            FrameworkSQLiteOpenHelperFactory(),
        )
    }

    @After
    fun cleanup() { context.deleteDatabase(DB_NAME) }

    @Test
    fun should_preserve_sessions_measurements_contexts_sealed_bytes_and_queued_requests() = runBlocking {
        val before = helper.createDatabase(DB_NAME, 41).use { db ->
            insertLegacyFixtures(db)
            db.execSQL("""
                INSERT INTO survey_contexts
                (sessionId,revision,payloadJson,sealedPayloadJson,createdAt,createdBy,updatedAt,updatedBy,copiedFromSessionId)
                VALUES('TEST-session',7,?,?,100,'TEST-context-author',150,'TEST-context-editor','TEST-purged-source')
            """.trimIndent(), arrayOf(SEALED_CONTEXT_BYTES, SEALED_CONTEXT_BYTES))
            db.execSQL("""
                INSERT INTO parcel_sync_operations
                (operationId,parcelId,originAccountId,destinationKind,apiOrigin,operation,
                    requestJson,createdAt,state,retryCount,nextAttemptAt,legacyReferenceJson)
                VALUES('TEST-parcel-operation','TEST-parent','TEST-account','PERSONAL',
                    'https://example.invalid','UPSERT',?,150,'PENDING',2,0,?)
            """.trimIndent(), arrayOf(PARCEL_REQUEST, "{\"TEST\":\"opaque legacy reference\"}"))
            db.execSQL("""
                INSERT INTO hierarchy_sync_operations
                (operationId,forestId,accountId,apiOrigin,requestJson,requestSha256,dossierSha256,
                    expectedServerVersion,createdAt,containsLocalPhotos,state,retryCount,nextAttemptAt)
                VALUES('TEST-hierarchy-operation','TEST-forest','TEST-account','https://example.invalid',
                    ?,'TEST-request-sha','TEST-dossier-sha',1,160,0,'PENDING',3,0)
            """.trimIndent(), arrayOf(HIERARCHY_REQUEST))
            LEGACY_TABLES.associateWith { table -> rows(db, table) }
        }
        migrate()
        val current = open()
        try {
            val db = current.openHelper.writableDatabase
            LEGACY_TABLES.forEach { table -> assertEquals(table, before.getValue(table), rows(db, table)) }
            assertEquals(0, count(db, "observation_qualifications"))
            assertNull(current.observationQualificationDao().getByTige("TEST-tree"))
            assertTrue(current.observationQualificationDao().getBySession("TEST-session").isEmpty())
            assertEquals(SEALED_SNAPSHOT_BYTES,
                requireNotNull(current.inventaireSessionDao().getById("TEST-session")).closedSnapshotJson)
            assertEquals(SEALED_CONTEXT_BYTES,
                requireNotNull(current.surveyContextDao().getBySession("TEST-session")).sealedPayloadJson)
            assertEquals("UNKNOWN_OLD_TYPE",
                requireNotNull(current.inventaireSessionDao().getById("TEST-legacy")).typeSession)
            assertForeignKeysValid(db)
        } finally { current.close() }
    }

    @Test
    fun should_enforce_revision_and_seal_preserve_provenance_reopen_and_cascade() = runBlocking {
        createAndMigrate()
        var current = open()
        try {
            var dao = current.observationQualificationDao()
            val initial = entity()
            dao.insert(initial)
            assertEquals(initial, dao.getByTige("TEST-tree"))
            assertFalse(requireNotNull(dao.getByTige("TEST-tree")).toRecord().isSealed)
            expectConstraintFailure { dao.insert(initial.copy(updatedBy = "TEST-duplicate")) }
            expectConstraintFailure { dao.insert(entity("TEST-missing-parent")) }

            val next = payloadJson(height = 19.25)
            assertEquals(1, dao.compareAndSet("TEST-tree", 1, next, 200, "TEST-editor"))
            val edited = requireNotNull(dao.getByTige("TEST-tree"))
            assertEquals(2, edited.revision)
            assertEquals(100L, edited.createdAt)
            assertEquals("TEST-creator", edited.createdBy)
            assertEquals(200L, edited.updatedAt)
            assertEquals("TEST-editor", edited.updatedBy)
            assertEquals(0, dao.compareAndSet("TEST-tree", 1, initial.payloadJson, 300, "TEST-stale"))
            assertEquals(0, dao.seal("TEST-tree", 1))
            assertEquals(edited, dao.getByTige("TEST-tree"))
            assertEquals(1, dao.seal("TEST-tree", 2))
            val sealed = requireNotNull(dao.getByTige("TEST-tree"))
            assertEquals(next, sealed.sealedPayloadJson)
            assertTrue(sealed.toRecord().isSealed)
            assertEquals(0, dao.compareAndSet("TEST-tree", 2, initial.payloadJson, 300, "TEST-late"))
            assertEquals(0, dao.seal("TEST-tree", 2))
            assertEquals(sealed, dao.getByTige("TEST-tree"))

            current.close()
            current = open()
            dao = current.observationQualificationDao()
            assertEquals(sealed, dao.getByTige("TEST-tree"))
            assertEquals(sealed.toRecord(), requireNotNull(dao.getByTige("TEST-tree")).toRecord())
            current.openHelper.writableDatabase.execSQL("DELETE FROM tiges WHERE tigeId = 'TEST-tree'")
            assertNull(dao.getByTige("TEST-tree"))
            assertTrue(dao.getBySession("TEST-session").isEmpty())
            assertForeignKeysValid(current.openHelper.writableDatabase)
        } finally { current.close() }
    }

    @Test
    fun should_rollback_insert_edit_and_seal_with_the_surrounding_room_transaction() = runBlocking {
        createAndMigrate()
        val current = open()
        try {
            val dao = current.observationQualificationDao()
            val initial = entity()
            expectRollback {
                current.withTransaction {
                    dao.insert(initial)
                    throw RollbackTest()
                }
            }
            assertNull(dao.getByTige("TEST-tree"))
            dao.insert(initial)
            val beforeSession = rows(current.openHelper.writableDatabase, "inventaire_sessions")
            expectRollback {
                current.withTransaction {
                    assertEquals(1, dao.compareAndSet("TEST-tree", 1, payloadJson(height = 20.25), 200, "TEST-editor"))
                    assertEquals(1, dao.seal("TEST-tree", 2))
                    current.openHelper.writableDatabase.execSQL(
                        "UPDATE inventaire_sessions SET version = version + 1 WHERE sessionId = 'TEST-session'")
                    throw RollbackTest()
                }
            }
            assertEquals(initial, dao.getByTige("TEST-tree"))
            assertEquals(beforeSession, rows(current.openHelper.writableDatabase, "inventaire_sessions"))
            assertForeignKeysValid(current.openHelper.writableDatabase)
        } finally { current.close() }
    }

    @Test
    fun should_expose_deleted_rows_for_explicit_checks_and_refuse_corrupt_identity_or_sealed_bytes() = runBlocking {
        createAndMigrate()
        val current = open()
        try {
            val db = current.openHelper.writableDatabase
            insertTree(db, "TEST-a-tree", deletedAt = 250)
            insertTree(db, "TEST-z-tree")
            val dao = current.observationQualificationDao()
            dao.insert(entity())
            dao.insert(entity("TEST-a-tree"))
            dao.insert(entity("TEST-z-tree"))
            assertEquals(listOf("TEST-a-tree", "TEST-tree", "TEST-z-tree"),
                dao.getBySession("TEST-session").map { it.tigeId })
            assertTrue(dao.getBySession("TEST-legacy").isEmpty())

            val maximum = entity("TEST-z-tree").copy(revision = Int.MAX_VALUE)
            db.execSQL("UPDATE observation_qualifications SET revision = ? WHERE tigeId = 'TEST-z-tree'",
                arrayOf(Int.MAX_VALUE))
            assertEquals(0, dao.compareAndSet("TEST-z-tree", Int.MAX_VALUE, payloadJson("TEST-z-tree"), 300, "TEST-editor"))
            assertEquals(maximum, dao.getByTige("TEST-z-tree"))

            val mismatched = entity().copy(payloadJson = payloadJson("TEST-different-tree"))
            expectInvalidDeclaration { mismatched.toRecord() }
            val mismatchedSeal = entity().copy(sealedPayloadJson = payloadJson(height = 19.25))
            expectInvalidDeclaration { mismatchedSeal.toRecord() }
            assertForeignKeysValid(db)
        } finally { current.close() }
    }

    private fun createAndMigrate() {
        helper.createDatabase(DB_NAME, 41).use(::insertLegacyFixtures)
        migrate()
    }

    private fun migrate() {
        helper.runMigrationsAndValidate(DB_NAME, 42, true,
            ObservationQualificationMigrations.MIGRATION_41_42).close()
    }

    private fun open() = Room.databaseBuilder(context, ForestryDatabase::class.java, DB_NAME)
        .addMigrations(*DatabaseMigrations.ALL).build()

    private fun insertLegacyFixtures(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO parcelles(parcelleId,name,surfaceHa,createdAt,updatedAt) VALUES('TEST-parent','TEST parcel',2.5,10,20)")
        db.execSQL("INSERT INTO essences(code,name) VALUES('TEST-oak','TEST oak')")
        db.execSQL("""
            INSERT INTO inventaire_sessions
            (sessionId,parcelleId,typeSession,dateDebut,dateFin,createdAt,martelageState,
                campaignId,activeDurationMs,closedSnapshotJson,auteur,source,version)
            VALUES('TEST-session','TEST-parent','MARTELAGE',100,200,100,'CLOSED',
                'TEST-campaign',75,?,'TEST-author','manual',9)
        """.trimIndent(), arrayOf(SEALED_SNAPSHOT_BYTES))
        db.execSQL("""
            INSERT INTO inventaire_sessions(sessionId,parcelleId,typeSession,dateDebut,createdAt)
            VALUES('TEST-legacy','TEST-parent','UNKNOWN_OLD_TYPE',120,120)
        """.trimIndent())
        insertTree(db, "TEST-tree")
    }

    private fun insertTree(db: SupportSQLiteDatabase, id: String, deletedAt: Long? = null) {
        db.execSQL("""
            INSERT INTO tiges
            (tigeId,uuid,parcelleOwnerId,sessionId,essenceCode,diamCm,hauteurM,gpsWkt,
                precisionM,timestamp,isTigeHabitat,auteur,source,version,deletedAt)
            VALUES(?,?,'TEST-parent','TEST-session','TEST-oak',37.5,18.25,
                'POINT(2.5 46.1)',12.5,150,0,'TEST-author','gps',4,?)
        """.trimIndent(), arrayOf(id, "$id-uuid", deletedAt))
    }

    private fun entity(id: String = "TEST-tree") = ObservationQualificationEntity(
        tigeId = id, revision = 1, payloadJson = payloadJson(id),
        createdAt = 100, createdBy = "TEST-creator", updatedAt = 100, updatedBy = "TEST-creator",
    )

    private fun payloadJson(id: String = "TEST-tree", height: Double = 18.25): String {
        val observation = Tige(
            id = id, parcelleId = "TEST-parent", placetteId = null, essenceCode = "TEST-oak",
            diamCm = 37.5, hauteurM = height, gpsWkt = "POINT(2.5 46.1)", precisionM = 12.5,
            altitudeM = null, timestamp = 150, note = null, produit = null, fCoef = null,
            valueEur = null, sessionId = "TEST-session", auteur = "TEST-author", source = "gps", version = 4,
        )
        return ObservationQualificationCodec.encode(ObservationQualificationPayload(
            basis = ObservationMeasurementBasis.capture(observation), draft = ObservationQualificationDraft(),
        ))
    }

    private suspend fun expectConstraintFailure(block: suspend () -> Unit) {
        try {
            block()
            fail("La contrainte SQL devait refuser cette insertion TEST")
        } catch (_: SQLiteConstraintException) {
            // La ligne déjà présente ou l'absence de la tige empêchent l'insertion.
        }
    }

    private suspend fun expectRollback(block: suspend () -> Unit) {
        try {
            block()
            fail("La transaction TEST devait être annulée")
        } catch (_: RollbackTest) {
            // L'exception volontaire impose le retour aux données antérieures.
        }
    }

    private fun expectInvalidDeclaration(block: () -> Unit) {
        try {
            block()
            fail("La déclaration incohérente TEST devait être refusée")
        } catch (_: IllegalArgumentException) {
            // L'intégrité de l'identité et des octets gelés doit être conservée.
        }
    }

    private fun assertForeignKeysValid(db: SupportSQLiteDatabase) {
        db.query("PRAGMA foreign_key_check").use { cursor -> assertFalse(cursor.moveToFirst()) }
    }

    private fun count(db: SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun rows(db: SupportSQLiteDatabase, table: String): List<List<String?>> =
        db.query("SELECT * FROM `$table` ORDER BY rowid").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add((0 until cursor.columnCount).map { index ->
                        if (cursor.isNull(index)) null else cursor.getString(index)
                    })
                }
            }
        }

    private class RollbackTest : RuntimeException()

    private companion object {
        const val DB_NAME = "TEST-observation-qualification-migration-41-42.db"
        const val SEALED_SNAPSHOT_BYTES = "{ \"schemaVersion\":1, \"TEST\":\"opaque sealed bytes\" }"
        const val SEALED_CONTEXT_BYTES = " { \"schemaVersion\":1, \"inventoryComplete\":false } "
        const val PARCEL_REQUEST = "{ \"TEST\":\"opaque parcel request\", \"height\":18.2500 }"
        const val HIERARCHY_REQUEST = "{ \"TEST\":\"opaque hierarchy request\", \"nullable\":null }"
        val LEGACY_TABLES = listOf("inventaire_sessions", "tiges", "survey_contexts",
            "parcel_sync_operations", "hierarchy_sync_operations")
    }
}
