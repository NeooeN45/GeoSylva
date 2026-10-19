package com.forestry.counter.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration43To44Test {
    @Test fun encrypted_database_migrates_from_42_through_44_and_reopens() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "TEST-encrypted-wood-42-44.db"
        net.sqlcipher.database.SQLiteDatabase.loadLibs(context)
        fun factory() = net.sqlcipher.database.SupportFactory("TEST-only-disposable-wood-key".toByteArray(), null, false)
        context.deleteDatabase(name)
        try {
            val helper = MigrationTestHelper(instrumentation, "com.forestry.counter.data.local.ForestryDatabase", factory())
            helper.createDatabase(name, 42).use { db ->
                db.execSQL("INSERT INTO forets(foretId,nom,proprietaireNom,createdAt,updatedAt) VALUES('TEST-preserved','TEST forest','TEST owner',10,20)")
            }
            helper.runMigrationsAndValidate(name, 44, true, WoodDepotMigrations.MIGRATION_42_43, WoodObjectMigrations.MIGRATION_43_44).use { db ->
                db.query("SELECT nom FROM forets WHERE foretId='TEST-preserved'").use { cursor ->
                    assertTrue(cursor.moveToFirst()); assertEquals("TEST forest", cursor.getString(0))
                }
                db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            }
            val encrypted = Room.databaseBuilder(context, ForestryDatabase::class.java, name).openHelperFactory(factory())
                .addMigrations(*DatabaseMigrations.ALL).build()
            try {
                assertEquals(46, encrypted.openHelper.writableDatabase.version)
                encrypted.openHelper.writableDatabase.query("SELECT count(*) FROM wood_objects").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            } finally { encrypted.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun additive_migration_retains_depot_and_adds_empty_wood_tables() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "TEST-migrate-wood-43-44.db"
        context.deleteDatabase(name)
        try {
            val helper = MigrationTestHelper(instrumentation, "com.forestry.counter.data.local.ForestryDatabase", FrameworkSQLiteOpenHelperFactory())
            helper.createDatabase(name, 43).use { db ->
                db.execSQL("""INSERT INTO wood_depots(depotId,name,kind,ownerScope,creationFingerprint,revision,createdAt,createdBy,updatedAt,updatedBy)
                    VALUES ('b0000000-0000-0000-0000-000000000001','TEST retained','ROADSIDE','LOCAL_DEVICE','TEST opaque',7,100,'anonymous',200,'anonymous')""")
            }
            helper.runMigrationsAndValidate(name, 44, true, WoodObjectMigrations.MIGRATION_43_44).use { db ->
                db.query("SELECT name,revision,creationFingerprint FROM wood_depots").use { c ->
                    assertTrue(c.moveToFirst()); assertEquals("TEST retained", c.getString(0)); assertEquals(7, c.getInt(1)); assertEquals("TEST opaque", c.getString(2))
                }
                for (table in listOf("wood_objects", "wood_observations", "wood_effective_observations", "wood_commands")) {
                    db.query("SELECT count(*) FROM $table").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
                }
                db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            }
            val current = Room.databaseBuilder(context, ForestryDatabase::class.java, name).addMigrations(*DatabaseMigrations.ALL).build()
            try { assertEquals(46, current.openHelper.writableDatabase.version) } finally { current.close() }
        } finally { context.deleteDatabase(name) }
    }
}
