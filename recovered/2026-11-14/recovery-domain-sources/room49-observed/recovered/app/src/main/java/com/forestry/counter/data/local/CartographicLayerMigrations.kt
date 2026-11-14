package com.forestry.counter.data.local

import androidx.sqlite.db.SupportSQLiteDatabase

object CartographicLayerMigrations {
    fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `cartographic_layers` (`id` TEXT NOT NULL, `authorityKey` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `payloadSha256` TEXT NOT NULL, `version` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `cartographic_layer_operations` (`id` TEXT NOT NULL, `layerId` TEXT NOT NULL, `authorityKey` TEXT NOT NULL, `commandSha256` TEXT NOT NULL, `resultJson` TEXT NOT NULL, PRIMARY KEY(`id`))")
    }
}
