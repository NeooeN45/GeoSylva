package com.forestry.counter.security

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.data.local.DatabaseMigrations
import com.forestry.counter.data.local.ForestryDatabase
import java.io.File
import java.security.MessageDigest
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** All files/keys here are synthetic and isolated from the application's database. */
@RunWith(AndroidJUnit4::class)
class DatabaseKeyRecoveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val base = instrumentation.targetContext
    private val dbName = "TEST-key-recovery.db"
    private val key = ByteArray(32) { (it * 13 + 0x81).toByte() }.apply {
        this[0] = 0; this[1] = 0xff.toByte(); this[2] = 0x80.toByte(); this[3] = '\''.code.toByte()
    }
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getDatabasePath(name: String): File {
            check(name == ForestryDatabase.DATABASE_NAME)
            return base.getDatabasePath(dbName)
        }
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            base.getSharedPreferences("TEST-key-recovery-$name", mode)
    }
    private val file get() = base.getDatabasePath(dbName)

    @Before fun setup() {
        SQLiteDatabase.loadLibs(base)
        cleanup()
    }
    @After fun cleanup() {
        base.deleteDatabase(dbName)
        file.parentFile?.listFiles()?.filter { it.name.startsWith(".$dbName.key-recovery") }?.forEach { it.delete() }
        context.getSharedPreferences("geosylva_database_security", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun historical_room_build_cleared_the_factory_key_before_first_open() {
        val caller = key.copyOf()
        val room = Room.databaseBuilder(base, ForestryDatabase::class.java, dbName)
            .openHelperFactory(SupportFactory(caller)).addMigrations(*DatabaseMigrations.ALL).build()
        caller.fill(0)
        try {
            room.openHelper.writableDatabase.execSQL("INSERT INTO forets(foretId,nom,proprietaireNom,createdAt,updatedAt) VALUES('TEST','TEST original path','TEST',1,1)")
        } finally { room.close() }
        assertFalse(canRead(key))
        assertTrue(canRead(ByteArray(32)))
    }

    @Test fun production_factory_owns_key_until_lazy_open_and_reopens_with_real_key() {
        val caller = key.copyOf()
        val room = ForestryDatabase.createDatabase(context, DatabaseMigrations.ALL, caller)
        caller.fill(0) // Same timing as Application.onCreate.
        try {
            room.openHelper.writableDatabase.execSQL("INSERT INTO forets(foretId,nom,proprietaireNom,createdAt,updatedAt) VALUES('TEST','TEST protected','TEST',1,1)")
        } finally { room.close() }
        assertTrue(canRead(key))
        val hash = hash(file)
        assertFalse(canRead(ByteArray(32)))
        assertEquals(hash, hash(file))
        val reopened = ForestryDatabase.createDatabase(context, DatabaseMigrations.ALL, key.copyOf())
        try {
            reopened.openHelper.writableDatabase.query("SELECT nom FROM forets WHERE foretId='TEST'").use {
                assertTrue(it.moveToFirst()); assertEquals("TEST protected", it.getString(0))
            }
        } finally { reopened.close() }
    }

    @Test fun legacy_45_recovery_preserves_observations_stock_photo_and_room_migration() {
        legacy()
        assertEquals(LegacySqlCipherRecovery.Result.RECOVERED_LEGACY_KEY,
            LegacySqlCipherRecovery.prepare(context, ForestryDatabase.DATABASE_NAME, key))
        assertTrue(context.getSharedPreferences("geosylva_database_security", Context.MODE_PRIVATE)
            .getBoolean("legacy_zero_key_recovered", false))
        assertFalse(canRead(ByteArray(32)))
        assertTrue(canRead(key))
        assertPayloads(key)
        val room = ForestryDatabase.createDatabase(context, DatabaseMigrations.ALL, key.copyOf())
        try {
            room.openHelper.writableDatabase.query("SELECT COUNT(*) FROM field_entries").use {
                assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0))
            }
            room.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { room.close() }
        assertEquals(LegacySqlCipherRecovery.Result.CURRENT_KEY,
            LegacySqlCipherRecovery.prepare(context, ForestryDatabase.DATABASE_NAME, key))
        assertTrue(context.getSharedPreferences("geosylva_database_security", Context.MODE_PRIVATE)
            .getBoolean("legacy_zero_key_recovered", false))
    }

    @Test fun wrong_real_key_preserves_encrypted_file_byte_for_byte() {
        legacy(key)
        val before = hash(file)
        reject { LegacySqlCipherRecovery.prepare(context, ForestryDatabase.DATABASE_NAME, ByteArray(32) { 7 }) }
        assertEquals(before, hash(file))
        assertPayloads(key)
    }

    @Test fun unknown_database_encrypted_with_zero_key_is_not_modified() {
        SQLiteDatabase.openOrCreateDatabase(file.absolutePath, ByteArray(32), null, null,
            LegacySqlCipherRecovery.preserveOnCorruption).use { db ->
            db.execSQL("CREATE TABLE unrelated(value TEXT)")
            db.execSQL("INSERT INTO unrelated VALUES('TEST unrelated owner')")
            db.version = 45
        }
        val before = hash(file)
        reject { LegacySqlCipherRecovery.prepare(context, ForestryDatabase.DATABASE_NAME, key) }
        assertEquals(before, hash(file))
        open(ByteArray(32)).use { db ->
            db.rawQuery("SELECT value FROM unrelated", emptyArray<String>()).use {
                assertTrue(it.moveToFirst()); assertEquals("TEST unrelated owner", it.getString(0))
            }
        }
    }

    @Test fun unknown_room_identity_and_plaintext_are_preserved() {
        legacy()
        open(ByteArray(32), SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("UPDATE room_master_table SET identity_hash='TEST unknown schema'")
        }
        var before = hash(file)
        reject { LegacySqlCipherRecovery.prepare(context, ForestryDatabase.DATABASE_NAME, key) }
        assertEquals(before, hash(file))
        base.deleteDatabase(dbName)
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use {
            it.execSQL("CREATE TABLE plaintext(value TEXT)")
            it.execSQL("INSERT INTO plaintext VALUES('TEST preserved')")
        }
        before = hash(file)
        reject { LegacySqlCipherRecovery.prepare(context, ForestryDatabase.DATABASE_NAME, key) }
        assertEquals(before, hash(file))
    }

    @Test fun interruption_before_atomic_install_preserves_original_and_retry_recovers() {
        legacy()
        reject { LegacySqlCipherRecovery.prepare(context, ForestryDatabase.DATABASE_NAME, key) { phase ->
            if (phase == LegacySqlCipherRecovery.Phase.COPY_VERIFIED) error("TEST simulated interruption")
        } }
        assertPayloads(ByteArray(32))
        assertFalse(canRead(key))
        assertEquals(LegacySqlCipherRecovery.Result.RECOVERED_LEGACY_KEY,
            LegacySqlCipherRecovery.prepare(context, ForestryDatabase.DATABASE_NAME, key))
        assertPayloads(key)
    }

    @Test fun interruption_after_atomic_install_reopens_with_true_key_on_next_start() {
        legacy()
        reject { LegacySqlCipherRecovery.prepare(context, ForestryDatabase.DATABASE_NAME, key) { phase ->
            if (phase == LegacySqlCipherRecovery.Phase.COPY_INSTALLED) error("TEST simulated interruption")
        } }
        assertPayloads(key)
        assertFalse(canRead(ByteArray(32)))
        assertEquals(LegacySqlCipherRecovery.Result.CURRENT_KEY,
            LegacySqlCipherRecovery.prepare(context, ForestryDatabase.DATABASE_NAME, key))
        assertTrue(context.getSharedPreferences("geosylva_database_security", Context.MODE_PRIVATE)
            .getBoolean("legacy_zero_key_recovered", false))
        assertFalse(context.getSharedPreferences("geosylva_database_security", Context.MODE_PRIVATE)
            .getBoolean("legacy_zero_key_recovery_pending", true))
    }

    private fun legacy(password: ByteArray = ByteArray(32)) {
        val helper = MigrationTestHelper(instrumentation, "com.forestry.counter.data.local.ForestryDatabase",
            SupportFactory(password.copyOf(), null, false))
        helper.createDatabase(dbName, 45).use { db ->
            db.execSQL("INSERT INTO forets(foretId,nom,proprietaireNom,createdAt,updatedAt) VALUES('TEST','TEST conserved forest','TEST',1,1)")
            db.execSQL("INSERT INTO field_entries(id,ownerKey,targetKind,targetId,payload,sha256,recordedAt,author) VALUES('TEST-base','LOCAL','WOOD_OBJECT','TEST-object','TEST base 100 apparent m3','TEST',1,'anonymous')")
            db.execSQL("INSERT INTO field_entries(id,ownerKey,targetKind,targetId,payload,sha256,recordedAt,author) VALUES('TEST-removal','LOCAL','WOOD_OBJECT','TEST-object','TEST removal 25 apparent m3','TEST',2,'anonymous')")
            db.execSQL("INSERT INTO field_photos(id,ownerKey,targetKind,targetId,caption,mime,bytes,sha256) VALUES('TEST-photo','LOCAL','WOOD_OBJECT','TEST-object','TEST photo','image/jpeg',131072,'TEST')")
            db.execSQL("INSERT INTO field_photo_chunks(photoId,part,bytes) VALUES('TEST-photo',0,?)", arrayOf(ByteArray(131072) { (it * 17).toByte() }))
            db.execSQL("INSERT INTO flora_fts(speciesId,nomFrancais,nomScientifique) VALUES('TEST-flora','TEST chêne','TEST Quercus')")
        }
    }

    private fun assertPayloads(password: ByteArray) = open(password).use { db ->
        db.rawQuery("SELECT nom FROM forets WHERE foretId='TEST'", emptyArray<String>()).use {
            assertTrue(it.moveToFirst()); assertEquals("TEST conserved forest", it.getString(0))
        }
        db.rawQuery("SELECT payload FROM field_entries ORDER BY recordedAt", emptyArray<String>()).use {
            assertTrue(it.moveToNext()); assertEquals("TEST base 100 apparent m3", it.getString(0))
            assertTrue(it.moveToNext()); assertEquals("TEST removal 25 apparent m3", it.getString(0))
            assertFalse(it.moveToNext())
        }
        db.rawQuery("SELECT bytes FROM field_photo_chunks WHERE photoId='TEST-photo'", emptyArray<String>()).use {
            assertTrue(it.moveToFirst()); assertArrayEquals(ByteArray(131072) { (it * 17).toByte() }, it.getBlob(0))
        }
        db.rawQuery("SELECT speciesId FROM flora_fts WHERE flora_fts MATCH 'chêne'", emptyArray<String>()).use {
            assertTrue(it.moveToFirst()); assertEquals("TEST-flora", it.getString(0))
        }
    }

    private fun open(password: ByteArray, flags: Int = SQLiteDatabase.OPEN_READONLY): SQLiteDatabase =
        SQLiteDatabase.openDatabase(file.absolutePath, password, null, flags, null, LegacySqlCipherRecovery.preserveOnCorruption)
    private fun canRead(password: ByteArray): Boolean = try {
        open(password).use { it.rawQuery("SELECT count(*) FROM sqlite_master", emptyArray<String>()).use { cursor -> cursor.moveToFirst() } }
    } catch (_: android.database.sqlite.SQLiteException) { false }
    private fun reject(action: () -> Unit) {
        try { action(); fail("Expected recovery to fail closed") } catch (_: IllegalStateException) { }
    }
    private fun hash(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()
}
