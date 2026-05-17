package com.forestry.counter.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.data.local.database.AppDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    @Throws(IOException::class)
    fun migrateAll() {
        // Create earliest version of the database
        helper.createDatabase(TEST_DB, 1).apply {
            close()
        }

        // Test migrations 1->2
        helper.runMigrationsAndValidate(TEST_DB, 2, true, DatabaseMigrations.MIGRATION_1_2)
        
        // Test migrations 2->3
        helper.runMigrationsAndValidate(TEST_DB, 3, true, DatabaseMigrations.MIGRATION_2_3)
        
        // Test migrations 3->4
        helper.runMigrationsAndValidate(TEST_DB, 4, true, DatabaseMigrations.MIGRATION_3_4)
        
        // Test migrations 4->5
        helper.runMigrationsAndValidate(TEST_DB, 5, true, DatabaseMigrations.MIGRATION_4_5)
        
        // Test migrations 5->6
        helper.runMigrationsAndValidate(TEST_DB, 6, true, DatabaseMigrations.MIGRATION_5_6)
        
        // Test migrations 6->7
        helper.runMigrationsAndValidate(TEST_DB, 7, true, DatabaseMigrations.MIGRATION_6_7)
        
        // Test migrations 7->8
        helper.runMigrationsAndValidate(TEST_DB, 8, true, DatabaseMigrations.MIGRATION_7_8)
        
        // Test migrations 8->9
        helper.runMigrationsAndValidate(TEST_DB, 9, true, DatabaseMigrations.MIGRATION_8_9)
        
        // Test migrations 9->10
        helper.runMigrationsAndValidate(TEST_DB, 10, true, DatabaseMigrations.MIGRATION_9_10)
        
        // Test migrations 10->11
        helper.runMigrationsAndValidate(TEST_DB, 11, true, DatabaseMigrations.MIGRATION_10_11)
        
        // Test migrations 11->12
        helper.runMigrationsAndValidate(TEST_DB, 12, true, DatabaseMigrations.MIGRATION_11_12)
        
        // Test migrations 12->13
        helper.runMigrationsAndValidate(TEST_DB, 13, true, DatabaseMigrations.MIGRATION_12_13)
        
        // Test migrations 13->14
        helper.runMigrationsAndValidate(TEST_DB, 14, true, DatabaseMigrations.MIGRATION_13_14)
        
        // Test migrations 14->15
        helper.runMigrationsAndValidate(TEST_DB, 15, true, DatabaseMigrations.MIGRATION_14_15)
    }

    @Test
    @Throws(IOException::class)
    fun testMigration1_2_countersColumns() {
        var db: SupportSQLiteDatabase = helper.createDatabase(TEST_DB, 1)
        
        // Insert some test data in version 1
        db.execSQL("INSERT INTO counters (counterId, name, value, step, min, max, targetValue, tags, groupOwnerId, sortIndex, createdAt, updatedAt) VALUES ('test1', 'Counter1', 10, 1, 0, 100, 50, 'tag1', 'group1', 0, 123456789, 123456789)")
        db.close()

        // Run migration
        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, DatabaseMigrations.MIGRATION_1_2)
        
        // Verify new columns exist and have default values
        db.query("SELECT decimalPlaces, initialValue, resetValue, soundEnabled, vibrationEnabled, vibrationIntensity, targetAction FROM counters WHERE counterId = 'test1'").apply {
            moveToFirst()
            assert(getColumnCount() == 7)
            close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testMigration3_4_forestryTables() {
        var db: SupportSQLiteDatabase = helper.createDatabase(TEST_DB, 3)
        db.close()

        // Run migration
        db = helper.runMigrationsAndValidate(TEST_DB, 4, true, DatabaseMigrations.MIGRATION_3_4)
        
        // Verify forestry tables exist
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('parcelles', 'placettes', 'essences', 'parameters', 'tiges')").apply {
            assert(count >= 5)
            close()
        }
    }
}
