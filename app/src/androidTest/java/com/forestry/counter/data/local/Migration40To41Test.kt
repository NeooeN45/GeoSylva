package com.forestry.counter.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.data.local.entity.SurveyContextEntity
import com.forestry.counter.domain.model.SurveyContextCodec
import com.forestry.counter.domain.model.SurveyContextDraft
import com.forestry.counter.domain.model.SurveyPurpose
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

/** Migration réelle et DAO SQLite : pas de requalification des données ou requêtes historiques. */
@RunWith(AndroidJUnit4::class)
class Migration40To41Test {
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
    fun should_leave_legacy_sessions_trees_sealed_bytes_and_queued_requests_unchanged() = runBlocking {
        val before = helper.createDatabase(DB_NAME, 40).use { db ->
            db.execSQL("INSERT INTO parcelles(parcelleId,name,surfaceHa,createdAt,updatedAt) VALUES('TEST-parent','TEST parcel',2.5,10,20)")
            db.execSQL("INSERT INTO essences(code,name) VALUES('TEST-oak','TEST oak')")
            db.execSQL("""
                INSERT INTO inventaire_sessions
                (sessionId,parcelleId,typeSession,dateDebut,dateFin,createdAt,martelageState,
                    campaignId,activeDurationMs,closedSnapshotJson,auteur,source,version)
                VALUES('TEST-closed','TEST-parent','MARTELAGE',100,200,100,'CLOSED',
                    'TEST-campaign',75,?,'TEST-author','manual',9)
            """.trimIndent(), arrayOf(SEALED_BYTES))
            db.execSQL("""
                INSERT INTO inventaire_sessions(sessionId,parcelleId,typeSession,dateDebut,createdAt)
                VALUES('TEST-legacy','TEST-parent','UNKNOWN_OLD_TYPE',120,120)
            """.trimIndent())
            db.execSQL("""
                INSERT INTO tiges
                (tigeId,uuid,parcelleOwnerId,sessionId,essenceCode,diamCm,hauteurM,gpsWkt,
                    precisionM,timestamp,isTigeHabitat,auteur,source,version)
                VALUES('TEST-tree','TEST-tree-uuid','TEST-parent','TEST-closed','TEST-oak',37.5,
                    18.25,'POINT(2.5 46.1)',12.5,150,0,'TEST-author','gps',4)
            """.trimIndent())
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
        helper.runMigrationsAndValidate(DB_NAME, 41, true, SurveyContextMigrations.MIGRATION_40_41).close()
        val current = open()
        try {
            val db = current.openHelper.writableDatabase
            LEGACY_TABLES.forEach { table -> assertEquals(table, before.getValue(table), rows(db, table)) }
            assertEquals(0, count(db, "survey_contexts"))
            assertNull(current.surveyContextDao().getBySession("TEST-closed"))
            assertNull(current.surveyContextDao().getBySession("TEST-legacy"))
            assertEquals(SEALED_BYTES, requireNotNull(current.inventaireSessionDao().getById("TEST-closed")).closedSnapshotJson)
            assertEquals("UNKNOWN_OLD_TYPE", requireNotNull(current.inventaireSessionDao().getById("TEST-legacy")).typeSession)
            db.query("PRAGMA foreign_key_check").use { cursor -> assertFalse(cursor.moveToFirst()) }
        } finally { current.close() }
    }

    @Test
    fun should_roundtrip_cas_seal_exact_bytes_and_enforce_parent_cascade_after_migration() = runBlocking {
        helper.createDatabase(DB_NAME, 40).use { db ->
            db.execSQL("INSERT INTO parcelles(parcelleId,name,createdAt,updatedAt) VALUES('TEST-parent','TEST parcel',10,20)")
            db.execSQL("""
                INSERT INTO inventaire_sessions(sessionId,parcelleId,typeSession,dateDebut,createdAt)
                VALUES('TEST-session','TEST-parent','MARTELAGE',100,100)
            """.trimIndent())
        }
        helper.runMigrationsAndValidate(DB_NAME, 41, true, SurveyContextMigrations.MIGRATION_40_41).close()
        val current = open()
        try {
            val dao = current.surveyContextDao()
            assertNull(dao.getBySession("TEST-session"))
            val initial = """ { "schemaVersion":1, "inventoryComplete":false } """
            dao.insert(SurveyContextEntity(
                sessionId = "TEST-session", revision = 1, payloadJson = initial,
                createdAt = 100, createdBy = "TEST-creator", updatedAt = 100, updatedBy = "TEST-creator",
                copiedFromSessionId = "TEST-purged-original",
            ))
            val loaded = requireNotNull(dao.getBySession("TEST-session"))
            assertEquals(initial, loaded.payloadJson)
            assertNull(loaded.sealedPayloadJson)
            assertEquals(false, loaded.toRecord().draft.inventoryComplete)
            assertNull(loaded.toRecord().draft.purpose)
            assertEquals("TEST-purged-original", loaded.toRecord().copiedFromSessionId)

            expectConstraintFailure { dao.insert(loaded.copy(updatedBy = "TEST-duplicate")) }
            assertEquals(loaded, dao.getBySession("TEST-session"))
            expectConstraintFailure { dao.insert(loaded.copy(sessionId = "TEST-missing-parent")) }

            val next = SurveyContextCodec.encode(SurveyContextDraft(purpose = SurveyPurpose.COMBINED))
            assertEquals(1, dao.compareAndSet("TEST-session", 1, next, 200, "TEST-editor"))
            val edited = requireNotNull(dao.getBySession("TEST-session"))
            assertEquals(2, edited.revision)
            assertEquals(100L, edited.createdAt)
            assertEquals("TEST-creator", edited.createdBy)
            assertEquals("TEST-editor", edited.updatedBy)
            assertEquals(200L, edited.updatedAt)
            assertEquals(0, dao.compareAndSet("TEST-session", 1, initial, 300, "TEST-stale"))
            assertEquals(edited, dao.getBySession("TEST-session"))
            assertEquals(0, dao.seal("TEST-session", 1))
            assertEquals(1, dao.seal("TEST-session", 2))
            val sealed = requireNotNull(dao.getBySession("TEST-session"))
            assertEquals(next, sealed.sealedPayloadJson)
            assertTrue(sealed.toRecord().isSealed)
            assertEquals(0, dao.compareAndSet("TEST-session", 2, initial, 300, "TEST-late"))
            assertEquals(0, dao.seal("TEST-session", 2))
            assertEquals(sealed, dao.getBySession("TEST-session"))

            current.openHelper.writableDatabase.execSQL("DELETE FROM inventaire_sessions WHERE sessionId = 'TEST-session'")
            assertNull(dao.getBySession("TEST-session"))
            current.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
                assertFalse(cursor.moveToFirst())
            }
        } finally { current.close() }
    }

    private fun open() = Room.databaseBuilder(context, ForestryDatabase::class.java, DB_NAME)
        .addMigrations(*DatabaseMigrations.ALL).build()

    private suspend fun expectConstraintFailure(block: suspend () -> Unit) {
        try {
            block()
            fail("La contrainte SQL devait refuser cette insertion TEST")
        } catch (_: SQLiteConstraintException) {
            // La ligne déjà présente ou l'absence du parent empêchent l'insertion.
        }
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

    private companion object {
        const val DB_NAME = "TEST-survey-context-migration-40-41.db"
        const val SEALED_BYTES = "{ \"schemaVersion\":1, \"TEST\":\"opaque sealed bytes\" }"
        const val PARCEL_REQUEST = "{ \"TEST\":\"opaque parcel request\", \"height\":18.2500 }"
        const val HIERARCHY_REQUEST = "{ \"TEST\":\"opaque hierarchy request\", \"nullable\":null }"
        val LEGACY_TABLES = listOf("inventaire_sessions", "tiges", "parcel_sync_operations", "hierarchy_sync_operations")
    }
}
