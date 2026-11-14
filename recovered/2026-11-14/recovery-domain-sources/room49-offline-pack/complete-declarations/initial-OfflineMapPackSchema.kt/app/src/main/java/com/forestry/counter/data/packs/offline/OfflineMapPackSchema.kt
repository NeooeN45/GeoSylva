package com.forestry.counter.data.packs.offline

import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive Room migration helper, called by the application's single version migration. */
object OfflineMapPackSchema {
    fun create(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `offline_map_packs` (`id` TEXT NOT NULL, `authorityKey` TEXT NOT NULL, `ownerKey` TEXT NOT NULL, `scopeKind` TEXT NOT NULL, `scopeId` TEXT NOT NULL, `name` TEXT NOT NULL, `geometryJson` TEXT NOT NULL, `minZoom` INTEGER NOT NULL, `maxZoom` INTEGER NOT NULL, `pixelRatio` REAL NOT NULL, `styleUri` TEXT NOT NULL, `extraTilesetsJson` TEXT NOT NULL, `datasetsJson` TEXT NOT NULL, `territoriesJson` TEXT NOT NULL, `definitionHash` TEXT NOT NULL, `nativeRegionId` TEXT NOT NULL, `localVersion` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `status` TEXT NOT NULL, `stage` TEXT, `completedResources` INTEGER NOT NULL, `requiredResources` INTEGER NOT NULL, `resourceBytes` INTEGER NOT NULL, `validatedAt` INTEGER, `expiresAt` INTEGER, `issue` TEXT, PRIMARY KEY(`id`))""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_offline_map_packs_authorityKey_createdAt` ON `offline_map_packs` (`authorityKey`, `createdAt`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_offline_map_packs_nativeRegionId` ON `offline_map_packs` (`nativeRegionId`)")
    }
}
