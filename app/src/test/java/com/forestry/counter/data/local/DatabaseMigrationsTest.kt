package com.forestry.counter.data.local

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for DatabaseMigrations structure and completeness.
 *
 * Note: SQL execution is validated by Room's MigrationTestHelper in instrumented tests.
 * These tests verify that all migration objects are properly defined and cover the
 * full version range (1→15) without gaps.
 */
class DatabaseMigrationsTest {

    @Test
    fun `ALL array contains exactly 14 migrations`() {
        assertEquals(14, DatabaseMigrations.ALL.size)
    }

    @Test
    fun `migrations cover full version range 1 to 15`() {
        val migrations = DatabaseMigrations.ALL
        assertEquals(1, migrations.first().startVersion)
        assertEquals(15, migrations.last().endVersion)
    }

    @Test
    fun `migrations are contiguous with no gaps`() {
        val migrations = DatabaseMigrations.ALL
        for (i in 0 until migrations.size - 1) {
            assertEquals(
                "Gap between migration ${migrations[i].startVersion}→${migrations[i].endVersion} and next",
                migrations[i].endVersion,
                migrations[i + 1].startVersion
            )
        }
    }

    @Test
    fun `each migration increments by exactly 1`() {
        for (migration in DatabaseMigrations.ALL) {
            assertEquals(
                "Migration ${migration.startVersion}→${migration.endVersion} should increment by 1",
                migration.startVersion + 1,
                migration.endVersion
            )
        }
    }

    @Test
    fun `individual migration references match ALL array`() {
        val expected = listOf(
            DatabaseMigrations.MIGRATION_1_2,
            DatabaseMigrations.MIGRATION_2_3,
            DatabaseMigrations.MIGRATION_3_4,
            DatabaseMigrations.MIGRATION_4_5,
            DatabaseMigrations.MIGRATION_5_6,
            DatabaseMigrations.MIGRATION_6_7,
            DatabaseMigrations.MIGRATION_7_8,
            DatabaseMigrations.MIGRATION_8_9,
            DatabaseMigrations.MIGRATION_9_10,
            DatabaseMigrations.MIGRATION_10_11,
            DatabaseMigrations.MIGRATION_11_12,
            DatabaseMigrations.MIGRATION_12_13,
            DatabaseMigrations.MIGRATION_13_14,
            DatabaseMigrations.MIGRATION_14_15
        )
        assertArrayEquals(expected.toTypedArray(), DatabaseMigrations.ALL)
    }
}
