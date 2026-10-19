package com.forestry.counter.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration45To46Test {
    @Test fun encrypted_migration_preserves_initial_stock_without_inventing_receipts() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val name="TEST-receipt-45-46.db";context.deleteDatabase(name)
        net.sqlcipher.database.SQLiteDatabase.loadLibs(context)
        fun factory()=net.sqlcipher.database.SupportFactory("TEST-only-receipt-migration-key".toByteArray(),null,false)
        try {
            val helper=MigrationTestHelper(instrumentation,"com.forestry.counter.data.local.ForestryDatabase",factory())
            helper.createDatabase(name,45).use {db ->
                db.execSQL("INSERT INTO field_worksites(id,ownerKey,payload,sha256,revision,archived,updatedAt) VALUES('TEST-site','LOCAL','TEST retained payload','TEST hash',3,0,10)")
                db.execSQL("INSERT INTO field_entries(id,ownerKey,targetKind,targetId,payload,sha256,recordedAt,author) VALUES('TEST-entry','LOCAL','WOOD_OBJECT','TEST-wood','TEST historical initial stock','TEST hash',10,'anonymous')")
            }
            helper.runMigrationsAndValidate(name,46,true,ReceiptMigrations.MIGRATION_45_46).use {db ->
                db.query("SELECT payload FROM field_entries WHERE id='TEST-entry'").use {assertTrue(it.moveToFirst());assertEquals("TEST historical initial stock",it.getString(0))}
                db.query("SELECT COUNT(*) FROM field_receipts").use {assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0))}
                db.query("PRAGMA foreign_key_check").use {assertFalse(it.moveToFirst())}
            }
            val room=Room.databaseBuilder(context,ForestryDatabase::class.java,name).openHelperFactory(factory()).addMigrations(*DatabaseMigrations.ALL).build()
            try {assertEquals(46,room.openHelper.writableDatabase.version)} finally {room.close()}
        } finally {context.deleteDatabase(name)}
    }
}
