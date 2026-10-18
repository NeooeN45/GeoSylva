package com.forestry.counter.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.data.mapper.toDomain
import com.forestry.counter.data.mapper.toEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real SQLite + exported Room schema validation; execution requires a device/emulator. */
@RunWith(AndroidJUnit4::class)
class Migration35To36Test {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var helper: MigrationTestHelper

    @Before fun setup() {
        context.deleteDatabase(DB_NAME)
        helper = MigrationTestHelper(
            instrumentation,
            "com.forestry.counter.data.local.ForestryDatabase",
            FrameworkSQLiteOpenHelperFactory(),
        )
    }

    @After fun cleanup() { context.deleteDatabase(DB_NAME) }

    @Test fun legacyForestAndDescendantsSurviveValidatedMigrationWithoutInventedGeometry() {
        helper.createDatabase(DB_NAME, 35).use { database ->
            database.execSQL("""INSERT INTO forets
                (foretId, uuid, nom, proprietaireNom, createdAt, updatedAt, auteur, source, version)
                VALUES ('TEST-forest', 'TEST-uuid', 'TEST forest', 'TEST owner', 1000, 2000, 'TEST-author', 'manual', 7)""".trimIndent())
            database.execSQL("""INSERT INTO parcelles
                (parcelleId, foretId, name, surfaceHa, createdAt, updatedAt)
                VALUES ('TEST-parcel', 'TEST-forest', 'TEST parcel', 3.5, 1000, 2000)""".trimIndent())
            database.execSQL("""INSERT INTO placettes
                (placetteId, parcelleOwnerId, name, createdAt, updatedAt)
                VALUES ('TEST-plot', 'TEST-parcel', 'TEST plot', 1000, 2000)""".trimIndent())
        }
        helper.runMigrationsAndValidate(DB_NAME, 36, true, DatabaseMigrations.MIGRATION_35_36).use { database ->
            database.query("SELECT * FROM forets WHERE foretId = 'TEST-forest'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("TEST forest", cursor.getString(cursor.getColumnIndexOrThrow("nom")))
                assertEquals("TEST-uuid", cursor.getString(cursor.getColumnIndexOrThrow("uuid")))
                assertEquals("TEST-author", cursor.getString(cursor.getColumnIndexOrThrow("auteur")))
                assertEquals("manual", cursor.getString(cursor.getColumnIndexOrThrow("source")))
                assertEquals(7, cursor.getInt(cursor.getColumnIndexOrThrow("version")))
                listOf("shape", "sourceForestId", "sourceForestDataset").forEach {
                    assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow(it)))
                }
                assertFalse(cursor.moveToNext())
            }
            database.query("SELECT foretId, surfaceHa FROM parcelles WHERE parcelleId = 'TEST-parcel'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("TEST-forest", cursor.getString(0))
                assertEquals(3.5, cursor.getDouble(1), 0.0)
            }
            database.query("SELECT parcelleOwnerId FROM placettes WHERE placetteId = 'TEST-plot'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("TEST-parcel", cursor.getString(0))
            }
            database.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }

    @Test fun sourceGeometryPersistsThroughRoomReopenAndSourceCanBackSeparateUserDossiers() = runBlocking {
        helper.createDatabase(DB_NAME, 35).close()
        helper.runMigrationsAndValidate(DB_NAME, 36, true, DatabaseMigrations.MIGRATION_35_36).use { database ->
            listOf("TEST-dossier-A", "TEST-dossier-B").forEach { id ->
                database.execSQL("""INSERT INTO forets
                    (foretId, nom, proprietaireNom, createdAt, updatedAt, shape, sourceForestId, sourceForestDataset)
                    VALUES (?, 'TEST forest', 'TEST owner', 1000, 2000, ?, 'TEST-official-id', 'TEST-source-dataset')""".trimIndent(),
                    arrayOf(id, SHAPE))
            }
        }
        val database = Room.databaseBuilder(context, ForestryDatabase::class.java, DB_NAME)
            .addMigrations(*DatabaseMigrations.ALL).build()
        try {
            val first = database.foretDao().getById("TEST-dossier-A")!!
            val second = database.foretDao().getById("TEST-dossier-B")!!
            assertEquals(SHAPE, first.shape)
            assertEquals("TEST-official-id", first.sourceForestId)
            assertEquals(first.sourceForestId, second.sourceForestId)
            assertEquals("TEST-source-dataset", first.sourceForestDataset)
            assertNull(first.uuid)
            val enriched = first.toDomain().withMetadata("TEST-user", "manual", 2).toEntity()
            database.foretDao().update(enriched)
        } finally {
            database.close()
        }
        val reopened = Room.databaseBuilder(context, ForestryDatabase::class.java, DB_NAME)
            .addMigrations(*DatabaseMigrations.ALL).build()
        try {
            val saved = reopened.foretDao().getById("TEST-dossier-A")!!
            assertEquals(SHAPE, saved.shape)
            assertEquals("TEST-official-id", saved.sourceForestId)
            assertEquals("TEST-source-dataset", saved.sourceForestDataset)
            assertEquals("manual", saved.source)
            assertEquals(2, reopened.foretDao().getAllNow().size)
        } finally {
            reopened.close()
        }
    }

    private companion object {
        const val DB_NAME = "TEST-forest-geometry-migration-35-36.db"
        const val SHAPE = "POLYGON ((2 46, 2.01 46, 2.01 46.01, 2 46.01, 2 46))"
    }
}
