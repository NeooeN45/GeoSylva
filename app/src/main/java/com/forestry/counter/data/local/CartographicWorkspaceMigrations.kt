package com.forestry.counter.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive storage for the common cartographic workspace; existing rows remain untouched. */
object CartographicWorkspaceMigrations {
    val MIGRATION_48_49 = object : Migration(48, 49) {
        override fun migrate(db: SupportSQLiteDatabase) {
            CartographicLayerMigrations.migrate(db)
            com.forestry.counter.data.packs.offline.OfflineMapPackSchema.create(db)
            CartographicTraceMigrations.create(db)
        }
    }
}
