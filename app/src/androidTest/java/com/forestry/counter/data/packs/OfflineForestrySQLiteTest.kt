package com.forestry.counter.data.packs

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** SQLite réel, objets synthétiques exclusivement de test, jamais dans le registre des packs. */
@RunWith(AndroidJUnit4::class)
class OfflineForestrySQLiteTest {
    private val files = mutableListOf<File>()
    private val json = Json { encodeDefaults = true }
    private val geometry = """{"coordinates":[[[0.1,0.1],[0.9,0.1],[0.9,0.9],[0.1,0.9],[0.1,0.1]]],"type":"Polygon"}"""
    private val territory = """{"coordinates":[[[0,0],[1,0],[1,1],[0,1],[0,0]]],"type":"Polygon"}"""

    @After fun cleanup() { files.forEach { it.delete() } }

    private fun fixture(): Pair<File, AdministrativeManifest> {
        // Instrumentation runs under the target application's UID, not the test APK's.
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val file = File.createTempFile("forestry-protocol-test-", ".sqlite", cache).also { files += it }
        val layers = listOf(
            ForestryLayerManifest("cadastre", "ign-parcellaire-express-wfs-2026", "2026-10-03", "IGN / DGFiP — PCI Vecteur, édition juin 2026", "Licence Ouverte / Etalab 2.0", 1, "b".repeat(64), "complete"),
            ForestryLayerManifest("public_forests", "onf-public-forest-extents-wfs-2026", "2026-10-03", "Forêts publiques, © ONF Maisons-Alfort, 2026", "Licence Ouverte / Etalab 2.0", 1, "b".repeat(64), "complete"),
        )
        val manifest = AdministrativeManifest("test-forestry", "f1-test", "department:79", "Protocole de test", "75", "forestry_layers", "gsie.forestry.sqlite.v1", 1L, "a".repeat(64), 1, "2026-10-03T10:00:00Z", layers[0].sourceId, layers[0].sourceEdition, layers[0].attribution, layers[0].licence, 2, 0, "/api/v1/packs/test-forestry/versions/f1-test/artifact", layers)
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { database ->
            database.execSQL("PRAGMA application_id=${OfflineForestryLayerStore.APPLICATION_ID}")
            database.execSQL("PRAGMA user_version=1")
            database.execSQL("CREATE TABLE pack_metadata(key TEXT PRIMARY KEY NOT NULL,value TEXT NOT NULL)")
            database.execSQL("CREATE TABLE features(id INTEGER PRIMARY KEY,layer TEXT NOT NULL,source_id TEXT NOT NULL,source_feature_id TEXT NOT NULL,forest_id TEXT,name TEXT,search_name TEXT NOT NULL,geometry_json TEXT NOT NULL,geometry_sha256 TEXT NOT NULL,properties_json TEXT NOT NULL,UNIQUE(layer,source_id,source_feature_id))")
            database.execSQL("CREATE INDEX features_layer_name ON features(layer,search_name)")
            database.execSQL("CREATE INDEX features_forest ON features(layer,forest_id)")
            database.execSQL("CREATE TABLE feature_bounds(id INTEGER PRIMARY KEY,west REAL NOT NULL,east REAL NOT NULL,south REAL NOT NULL,north REAL NOT NULL)")
            database.execSQL("CREATE INDEX idx_feature_bounds_west_east ON feature_bounds(west,east,id)")
            database.execSQL("CREATE INDEX idx_feature_bounds_south_north ON feature_bounds(south,north,id)")
            val metadata = JsonObject(json.encodeToJsonElement(AdministrativeManifest.serializer(), manifest).jsonObject.toMutableMap().apply {
                remove("sha256"); remove("archive_bytes"); remove("created_at"); remove("quarantined_count")
                this["layers"] = JsonArray(manifest.layers.map { layer ->
                    JsonObject(json.encodeToJsonElement(ForestryLayerManifest.serializer(), layer).jsonObject.toMutableMap().apply { remove("quarantined_count") })
                })
            }).toString()
            for ((key, value) in mapOf("manifest" to metadata, "territory_geometry" to territory, "territory_label" to """{"coordinates":[0.5,0.5],"type":"Point"}""", "territory_name" to "Territoire de test", "territory_departments" to """["79"]""")) database.execSQL("INSERT INTO pack_metadata VALUES(?,?)", arrayOf(key, value))
            val hash = MessageDigest.getInstance("SHA-256").digest(geometry.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            database.execSQL("INSERT INTO features VALUES(1,?,?,?,?,?,?,?,?,?)", arrayOf("cadastre", layers[0].sourceId, "TEST_CADASTRE", null, "A 1", "a 1", geometry, hash, """{"code_dep":"79","idu":"TEST_CADASTRE","numero":"1","section":"A"}"""))
            database.execSQL("INSERT INTO features VALUES(2,?,?,?,?,?,?,?,?,?)", arrayOf("public_forests", layers[1].sourceId, "TEST_FOREST", "TEST_FOREST", "Forêt de test", "foret de test", geometry, hash, """{"cinse_dep":"79","iidtn_frt":"TEST_FOREST","llib_frt":"Forêt de test"}"""))
            database.execSQL("INSERT INTO feature_bounds VALUES(1,0.1,0.9,0.1,0.9)")
            database.execSQL("INSERT INTO feature_bounds VALUES(2,0.1,0.9,0.1,0.9)")
        }
        return file to manifest.copy(archiveBytes = file.length())
    }

    @Test fun androidSQLiteReadsPortableSpatialTableWithoutAnExtension() {
        val (file, manifest) = fixture()
        OfflineForestryLayerStore.validateSQLite(file, manifest)
        assertEquals("Territoire de test", OfflineForestryLayerStore.administrativeProjection(file, manifest).name)
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { database ->
            database.rawQuery("SELECT sql FROM sqlite_master WHERE name='feature_bounds'", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertFalse(cursor.getString(0).contains("VIRTUAL", ignoreCase = true))
                assertFalse(cursor.getString(0).contains("rtree", ignoreCase = true))
            }
            database.rawQuery("SELECT f.source_feature_id,f.geometry_json FROM features f JOIN feature_bounds b ON b.id=f.id WHERE b.west<=? AND b.east>=? AND b.south<=? AND b.north>=? ORDER BY f.id", arrayOf("0.6", "0.4", "0.6", "0.4")).use { cursor ->
                assertEquals(2, cursor.count)
                assertTrue(cursor.moveToFirst())
                assertEquals("TEST_CADASTRE", cursor.getString(0))
                assertEquals(geometry, cursor.getString(1))
            }
        }
    }

    @Test fun missingSpatialRowIsRejectedWithoutInventingLocation() {
        val (file, manifest) = fixture()
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { it.execSQL("DELETE FROM feature_bounds WHERE id=2") }
        assertTrue(runCatching { OfflineForestryLayerStore.validateSQLite(file, manifest.copy(archiveBytes = file.length())) }.isFailure)
    }

    @Test fun sourceIdentityChangeIsRejectedEvenWithAValidSqliteFile() {
        val (file, manifest) = fixture()
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { it.execSQL("UPDATE features SET source_feature_id='OTHER' WHERE id=1") }
        assertTrue(runCatching { OfflineForestryLayerStore.validateSQLite(file, manifest.copy(archiveBytes = file.length())) }.isFailure)
    }

    @Test fun missingSpatialIndexIsRejected() = rejectedMutation("DROP INDEX idx_feature_bounds_west_east")

    @Test fun spatialIndexWithWrongColumnsIsRejected() = rejectedMutation(
        "DROP INDEX idx_feature_bounds_south_north",
        "CREATE INDEX idx_feature_bounds_south_north ON feature_bounds(north,south,id)",
    )

    @Test fun unexpectedSpatialColumnIsRejected() = rejectedMutation("ALTER TABLE feature_bounds ADD COLUMN altitude REAL")

    @Test fun wrongSpatialColumnTypeIsRejected() = rejectedMutation(
        "DROP INDEX idx_feature_bounds_west_east",
        "DROP INDEX idx_feature_bounds_south_north",
        "ALTER TABLE feature_bounds RENAME TO old_bounds",
        "CREATE TABLE feature_bounds(id INTEGER PRIMARY KEY,west TEXT NOT NULL,east REAL NOT NULL,south REAL NOT NULL,north REAL NOT NULL)",
        "INSERT INTO feature_bounds SELECT * FROM old_bounds",
        "DROP TABLE old_bounds",
        "CREATE INDEX idx_feature_bounds_west_east ON feature_bounds(west,east,id)",
        "CREATE INDEX idx_feature_bounds_south_north ON feature_bounds(south,north,id)",
    )

    @Test fun mismatchedSpatialIdentityIsRejectedEvenWithEqualRowCounts() = rejectedMutation("UPDATE feature_bounds SET id=3 WHERE id=2")

    @Test fun invalidNumericBoundsAreRejected() {
        for (change in listOf("west='invalid'", "east=1e999", "north=91", "west=0.95", "west=0.09")) {
            rejectedMutation("UPDATE feature_bounds SET $change WHERE id=1")
        }
    }

    private fun rejectedMutation(vararg statements: String) {
        val (file, manifest) = fixture()
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { database ->
            statements.forEach { database.execSQL(it) }
        }
        assertTrue(runCatching { OfflineForestryLayerStore.validateSQLite(file, manifest.copy(archiveBytes = file.length())) }.isFailure)
    }
}
