package com.forestry.counter.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration47To48Test {
    @Test fun encrypted_migration_preserves_existing_snapshots_without_claiming_historical_measurements() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "TEST-cartographic-47-48.db"
        context.deleteDatabase(name)
        net.sqlcipher.database.SQLiteDatabase.loadLibs(context)
        fun factory() = net.sqlcipher.database.SupportFactory("TEST-only-cartographic-migration".toByteArray(), null, false)
        try {
            val helper = MigrationTestHelper(instrumentation, "com.forestry.counter.data.local.ForestryDatabase", factory())
            helper.createDatabase(name, 47).use { db ->
                db.execSQL("INSERT INTO field_entries(id,ownerKey,targetKind,targetId,payload,sha256,recordedAt,author) VALUES('TEST-old','LOCAL','FOREST','TEST-forest','TEST unchanged old bytes','TEST hash',10,'anonymous')")
            }
            helper.runMigrationsAndValidate(name, 48, true, CartographicMeasurementMigrations.MIGRATION_47_48).use { db ->
                db.query("SELECT payload, sha256 FROM field_entries WHERE id='TEST-old'").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("TEST unchanged old bytes", it.getString(0))
                    assertEquals("TEST hash", it.getString(1))
                }
                for (table in listOf("cartographic_measurements", "cartographic_store_identity")) {
                    db.query("SELECT count(*) FROM $table").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
                }
                db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            }
            val room = Room.databaseBuilder(context, ForestryDatabase::class.java, name)
                .openHelperFactory(factory()).addMigrations(*DatabaseMigrations.ALL).build()
            try { assertEquals(48, room.openHelper.writableDatabase.version) } finally { room.close() }
        } finally { context.deleteDatabase(name) }
    }
}
