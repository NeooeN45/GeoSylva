package com.forestry.counter.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The real 36→37 step runs through ALL; Room validates the current final schema after later steps. */
@RunWith(AndroidJUnit4::class)
class Migration36To37Test {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var helper: MigrationTestHelper

    @Before fun setup() {
        context.deleteDatabase(DB_NAME)
        helper = MigrationTestHelper(instrumentation, "com.forestry.counter.data.local.ForestryDatabase",
            FrameworkSQLiteOpenHelperFactory())
    }
    @After fun cleanup() { context.deleteDatabase(DB_NAME) }

    @Test fun legacy_types_measurements_metadata_and_null_session_links_survive_migration() = runBlocking {
        helper.createDatabase(DB_NAME, 36).use { db ->
            db.execSQL("INSERT INTO parcelles (parcelleId,name,surfaceHa,createdAt,updatedAt) VALUES ('TEST-parent','TEST parcel',2.5,10,20)")
            db.execSQL("INSERT INTO essences (code,name) VALUES ('TEST-oak','TEST oak')")
            db.execSQL("""INSERT INTO inventaire_sessions
                (sessionId,parcelleId,typeSession,dateDebut,dateFin,createdAt,auteur,source,version)
                VALUES ('TEST-legacy','TEST-parent','UNKNOWN_OLD_TYPE',100,200,100,'TEST-author','import',7)""".trimIndent())
            db.execSQL("""INSERT INTO tiges
                (tigeId,uuid,parcelleOwnerId,sessionId,essenceCode,diamCm,hauteurM,timestamp,isTigeHabitat,auteur,source,version)
                VALUES ('TEST-linked','TEST-uuid','TEST-parent','TEST-legacy','TEST-oak',37.5,18.25,150,0,'TEST-author','gps',4)""".trimIndent())
            db.execSQL("""INSERT INTO tiges
                (tigeId,parcelleOwnerId,essenceCode,diamCm,timestamp,isTigeHabitat)
                VALUES ('TEST-unassigned','TEST-parent','TEST-oak',22.5,170,0)""".trimIndent())
        }
        val current = Room.databaseBuilder(context, ForestryDatabase::class.java, DB_NAME)
            .addMigrations(*DatabaseMigrations.ALL).build()
        try {
            val old = current.inventaireSessionDao().getById("TEST-legacy")!!
            assertEquals("UNKNOWN_OLD_TYPE", old.typeSession)
            assertNull(old.martelageState)
            assertNull(old.campaignId)
            assertNull(old.closedSnapshotJson)
            assertEquals(0L, old.activeDurationMs)
            assertEquals(1, old.revisionNumber)
            assertFalse(old.recoveryRequired)
            assertEquals(100L, old.dateDebut)
            assertEquals(200L, old.dateFin)
            assertEquals(7, old.version)
            assertEquals("TEST-author", old.auteur)
            assertEquals("import", old.source)
            val linked = current.tigeDao().getTigeById("TEST-linked")!!
            assertEquals("TEST-legacy", linked.sessionId)
            assertEquals("TEST-uuid", linked.uuid)
            assertEquals(37.5, linked.diamCm, 0.0)
            assertEquals(18.25, linked.hauteurM!!, 0.0)
            assertEquals(4, linked.version)
            assertNull(linked.originTigeId)
            assertNull(current.tigeDao().getTigeById("TEST-unassigned")!!.sessionId)
            assertTrue(current.inventaireSessionDao().getActiveForParcelle("TEST-parent").isEmpty())
            current.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { current.close() }
    }

    @Test fun nullable_lifecycle_does_not_fabricate_a_historical_campaign_on_empty_migration() = runBlocking {
        helper.createDatabase(DB_NAME, 36).close()
        val current = Room.databaseBuilder(context, ForestryDatabase::class.java, DB_NAME)
            .addMigrations(*DatabaseMigrations.ALL).build()
        try {
            assertTrue(current.tigeDao().getAllTigesNow().isEmpty())
            current.openHelper.writableDatabase.query("SELECT COUNT(*) FROM inventaire_sessions").use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
            }
        } finally { current.close() }
    }
    private companion object { const val DB_NAME = "TEST-martelage-migration-36-37.db" }
}
