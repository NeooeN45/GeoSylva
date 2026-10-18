package com.forestry.counter.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.data.mapper.toPlacette
import com.forestry.counter.data.mapper.toPlacetteEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Actual nullable scope migration; old sessions, measurements and sealed bytes are never attributed. */
@RunWith(AndroidJUnit4::class)
class Migration39To40Test {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var helper: MigrationTestHelper

    @Before fun setup() {
        context.deleteDatabase(DB_NAME)
        helper = MigrationTestHelper(instrumentation, "com.forestry.counter.data.local.ForestryDatabase",
            FrameworkSQLiteOpenHelperFactory())
    }
    @After fun cleanup() { context.deleteDatabase(DB_NAME) }

    @Test fun legacy_null_scope_closed_bytes_and_two_plot_measurements_are_unchanged() = runBlocking {
        val sealedBytes = "{\"schemaVersion\":1,\"TEST\":\"opaque preserved bytes\"}"
        helper.createDatabase(DB_NAME, 39).use { db ->
            db.execSQL("INSERT INTO parcelles(parcelleId,name,surfaceHa,createdAt,updatedAt) VALUES('TEST-parent','TEST parcel',2.5,10,20)")
            db.execSQL("INSERT INTO essences(code,name) VALUES('TEST-oak','TEST oak')")
            db.execSQL("""INSERT INTO inventaire_sessions
                (sessionId,parcelleId,typeSession,dateDebut,dateFin,createdAt,martelageState,campaignId,activeDurationMs,closedSnapshotJson,auteur,source,version)
                VALUES('TEST-old-closed','TEST-parent','MARTELAGE',100,200,100,'CLOSED','TEST-campaign',75,?,'TEST-author','manual',9)""".trimIndent(), arrayOf(sealedBytes))
            db.execSQL("""INSERT INTO inventaire_sessions(sessionId,parcelleId,typeSession,dateDebut,createdAt)
                VALUES('TEST-unknown','TEST-parent','UNKNOWN_OLD_TYPE',120,120)""".trimIndent())
            (1..2).forEach { number ->
                db.execSQL("INSERT INTO placettes(placetteId,parcelleOwnerId,name,type,rayonM,surfaceM2,createdAt,updatedAt) VALUES(?, 'TEST-parent','TEST plot','CIRC',10,314.159,10,20)", arrayOf("TEST-plot-$number"))
                db.execSQL("""INSERT INTO tiges(tigeId,parcelleOwnerId,placetteOwnerId,sessionId,essenceCode,diamCm,hauteurM,timestamp,isTigeHabitat)
                    VALUES(?, 'TEST-parent', ?, 'TEST-old-closed','TEST-oak',37.5,18.25,150,0)""".trimIndent(), arrayOf("TEST-tree-$number", "TEST-plot-$number"))
            }
            db.execSQL("INSERT INTO tiges(tigeId,parcelleOwnerId,essenceCode,diamCm,timestamp,isTigeHabitat) VALUES('TEST-unassigned','TEST-parent','TEST-oak',20,170,0)")
        }
        val current = open()
        try {
            val closed = current.inventaireSessionDao().getById("TEST-old-closed")!!
            assertNull(closed.placetteId)
            assertNull(closed.traceJson)
            assertEquals(sealedBytes, closed.closedSnapshotJson)
            assertEquals(75L, closed.activeDurationMs)
            assertEquals(9, closed.version)
            assertEquals("TEST-author", closed.auteur)
            assertNull(current.inventaireSessionDao().getById("TEST-unknown")!!.martelageState)
            assertNull(current.inventaireSessionDao().getById("TEST-unknown")!!.placetteId)
            assertNull(current.tigeDao().getTigeById("TEST-unassigned")!!.sessionId)
            (1..2).forEach { number ->
                val plot = current.placetteDao().getPlacetteById("TEST-plot-$number")!!
                assertNull(plot.samplingPlanJson)
                assertNull(plot.footprintGeoJson)
                val tree = current.tigeDao().getTigeById("TEST-tree-$number")!!
                assertEquals("TEST-old-closed", tree.sessionId)
                assertEquals("TEST-plot-$number", tree.placetteOwnerId)
                assertEquals(37.5, tree.diamCm, 0.0)
                assertEquals(18.25, tree.hauteurM!!, 0.0)
            }
            current.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { current.close() }
    }

    @Test fun sampling_json_and_real_geometry_roundtrip_after_migration_without_legacy_backfill() = runBlocking {
        helper.createDatabase(DB_NAME, 39).use { db ->
            db.execSQL("INSERT INTO parcelles(parcelleId,name,createdAt,updatedAt) VALUES('TEST-parent','TEST parcel',10,20)")
            db.execSQL("INSERT INTO placettes(placetteId,parcelleOwnerId,createdAt,updatedAt) VALUES('TEST-plot','TEST-parent',10,20)")
        }
        val current = open()
        try {
            val original = current.placetteDao().getPlacetteById("TEST-plot")!!.toPlacette()
            val changed = original.copy(samplingPlanJson = "{\"schemaVersion\":1,\"placement\":\"MANUAL\"}",
                footprintGeoJson = "{\"type\":\"Polygon\",\"coordinates\":[[[2,46],[2.001,46],[2,46.001],[2,46]]]}")
            current.placetteDao().updatePlacette(changed.toPlacetteEntity())
            assertEquals(changed, current.placetteDao().getPlacetteById("TEST-plot")!!.toPlacette())
            current.openHelper.writableDatabase.query("SELECT COUNT(*) FROM inventaire_sessions").use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
            }
        } finally { current.close() }
    }

    private fun open() = Room.databaseBuilder(context, ForestryDatabase::class.java, DB_NAME)
        .addMigrations(*DatabaseMigrations.ALL).build()
    private companion object { const val DB_NAME = "TEST-martelage-migration-39-40.db" }
}
