package com.forestry.counter.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration44To45Test {
    @Test fun encrypted_migration_preserves_existing_business_records_and_opens_room() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val name="TEST-field-44-45.db"
        context.deleteDatabase(name)
        net.sqlcipher.database.SQLiteDatabase.loadLibs(context)
        fun factory()=net.sqlcipher.database.SupportFactory("TEST-only-field-key".toByteArray(),null,false)
        try {
            val helper=MigrationTestHelper(instrumentation,"com.forestry.counter.data.local.ForestryDatabase",factory())
            helper.createDatabase(name,44).use {db ->
                db.execSQL("INSERT INTO forets(foretId,nom,proprietaireNom,createdAt,updatedAt) VALUES('legacy-forest','TEST retained forest','TEST owner',10,20)")
            }
            helper.runMigrationsAndValidate(name,45,true,FieldMigrations.MIGRATION_44_45).use {db ->
                db.query("SELECT nom FROM forets WHERE foretId='legacy-forest'").use {assertTrue(it.moveToFirst());assertEquals("TEST retained forest",it.getString(0))}
                db.query("PRAGMA foreign_key_check").use {assertFalse(it.moveToFirst())}
            }
            val room=Room.databaseBuilder(context,ForestryDatabase::class.java,name).openHelperFactory(factory()).addMigrations(*DatabaseMigrations.ALL).build()
            try {assertEquals(45,room.openHelper.writableDatabase.version)} finally {room.close()}
        } finally {context.deleteDatabase(name)}
    }
}
