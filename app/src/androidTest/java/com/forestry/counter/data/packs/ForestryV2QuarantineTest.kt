package com.forestry.counter.data.packs

import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Genuine source samples + ephemeral test signature, cache only. No pack activation. */
@RunWith(AndroidJUnit4::class)
class ForestryV2QuarantineTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val files = mutableListOf<File>()
    private fun bytes(name: String) = instrumentation.context.assets.open("forestry-v2-testonly/$name").use { it.readBytes() }
    private fun fixture(): Pair<File, AdministrativeManifest> {
        val file = File.createTempFile("forestry-v2-TESTONLY-", ".sqlite", instrumentation.targetContext.cacheDir).also { files += it; it.writeBytes(bytes("artifact.pack")) }
        val contract = AdministrativePackContract(Base64.getEncoder().encodeToString(bytes("signing-public-key.der")))
        val signed = Json.decodeFromString<SignedManifestDto>(bytes("manifest.json").toString(Charsets.UTF_8)).metadata()
        return file to contract.verify(signed)
    }
    @After fun cleanup() { files.forEach { assertTrue(it.delete() || !it.exists()) } }
    private fun mutate(file: File, block: (SQLiteDatabase) -> Unit) = SQLiteDatabase.openDatabase(file.absolutePath, null,
        SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use(block)
    private fun rejected(sql: String) {
        val (file, manifest) = fixture()
        mutate(file) { it.execSQL(sql) }
        assertTrue(runCatching { OfflineForestryLayerStore.validateSQLite(file, manifest) }.isFailure)
    }
    @Test fun real_android_sqlite_verifies_signed_originals_and_excludes_both_invalid_contours() {
        val (file, manifest) = fixture()
        val contract = AdministrativePackContract(Base64.getEncoder().encodeToString(bytes("signing-public-key.der")))
        val signed = Json.decodeFromString<SignedManifestDto>(bytes("manifest.json").toString(Charsets.UTF_8)).metadata()
        val started = SystemClock.elapsedRealtime()
        contract.validateArtifact(file, contract.descriptor(signed, null))
        android.util.Log.i("ForestryV2Test", "bounded_real_fixture_validation_ms=${SystemClock.elapsedRealtime() - started}")
        mutate(file) { database ->
            database.rawQuery("SELECT source_feature_id FROM quarantined_features ORDER BY source_feature_id", null).use { cursor ->
                val ids = mutableListOf<String>(); while (cursor.moveToNext()) ids += cursor.getString(0)
                assertEquals(listOf("F09773W:13", "F09811I:141"), ids)
            }
            database.rawQuery("SELECT count(*) FROM features JOIN feature_bounds USING(id)", null).use { assertTrue(it.moveToFirst()); assertEquals(12, it.getInt(0)) }
            database.rawQuery("SELECT count(*) FROM features WHERE source_feature_id IN ('F09773W:13','F09811I:141')", null).use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            database.rawQuery("SELECT geometry_json FROM features WHERE source_feature_id='F09817O'", null).use {
                assertTrue(it.moveToFirst())
                val geometry = com.forestry.counter.domain.location.ParcelGeometry.fromGeoJson(it.getString(0))
                assertTrue(geometry.areaSquareMetres > 0)
            }
        }
        assertEquals(2, manifest.quarantinedCount)
    }
    @Test fun corrupted_original_hash_is_rejected() = rejected("UPDATE quarantined_features SET original_feature_sha256='0000'")
    @Test fun source_identity_substitution_is_rejected() = rejected("UPDATE quarantined_features SET source_feature_id='OTHER' WHERE id=1")
    @Test fun unsupported_quarantine_reason_is_rejected() = rejected("UPDATE quarantined_features SET reason_code='ignore_unknown'")
    @Test fun extra_unqualified_schema_object_is_rejected() = rejected("CREATE TABLE hidden_payload(value TEXT)")
    @Test fun territorial_label_must_be_numeric_wgs84_and_inside_the_official_boundary() {
        for (label in listOf("""{"type":"Point","coordinates":["x",999]}""", """{"type":"Point","coordinates":[0,0]}""")) {
            val (file, manifest) = fixture()
            mutate(file) { it.execSQL("UPDATE pack_metadata SET value=? WHERE key='territory_label'", arrayOf(label)) }
            assertTrue(runCatching { OfflineForestryLayerStore.validateSQLite(file, manifest) }.isFailure)
        }
    }
    @Test fun valid_contour_cannot_be_quarantined_even_with_a_matching_original_checksum() {
        val (file, manifest) = fixture()
        mutate(file) { database ->
            val original = database.rawQuery("SELECT original_feature_json FROM quarantined_features LIMIT 1", null).use { assertTrue(it.moveToFirst()); Json.parseToJsonElement(it.getString(0)).jsonObject }
            val valid = Json.parseToJsonElement("""{"coordinates":[[[-0.5,46],[-0.49,46],[-0.49,46.01],[-0.5,46.01],[-0.5,46]]],"type":"Polygon"}""")
            val changed = JsonObject(original.toMutableMap().apply { this["geometry"] = valid })
            val canonical = com.forestry.counter.capsule.json.CanonicalJson.encode(com.forestry.counter.capsule.json.StrictJsonParser.parse(changed.toString().toByteArray())).toString(Charsets.UTF_8)
            val hash = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString("") { "%02x".format(it) }
            database.execSQL("UPDATE quarantined_features SET original_feature_json=?,original_feature_sha256=?", arrayOf(canonical, hash))
        }
        assertTrue(runCatching { OfflineForestryLayerStore.validateSQLite(file, manifest) }.isFailure)
    }
}
