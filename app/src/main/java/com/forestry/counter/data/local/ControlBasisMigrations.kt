package com.forestry.counter.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object ControlBasisMigrations {
    val MIGRATION_46_47 = object : Migration(46, 47) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Aucun contrôle ancien n'est promu en référence historique figée.
            db.execSQL("CREATE TABLE IF NOT EXISTS field_control_bases (controlEntryId TEXT NOT NULL PRIMARY KEY, ownerKey TEXT NOT NULL, objectId TEXT NOT NULL, depotId TEXT NOT NULL, payload TEXT NOT NULL, sha256 TEXT NOT NULL, FOREIGN KEY(controlEntryId) REFERENCES field_entries(id) ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_field_control_bases_ownerKey_objectId ON field_control_bases(ownerKey,objectId)")
        }
    }
}
