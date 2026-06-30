package com.forestry.counter.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 31 → 32 : Retire la contrainte FOREIGN KEY incorrecte sur
 * `parcelles.forestOwnerId` qui référençait `forets.foretId`.
 *
 * **Contexte** : `forestOwnerId` stocke un `groupId` (table `groups`),
 * pas un `foretId` (table `forets`). La FK empêchait la création de
 * parcelles depuis l'écran Groups (qui utilise GroupEntity comme forêt).
 *
 * **Procédure SQLite** (impossible de modifier une FK directement) :
 * 1. Créer `parcelles_new` sans la FK sur `forestOwnerId`
 * 2. Copier les données existantes
 * 3. Drop l'ancienne table
 * 4. Renommer la nouvelle
 * 5. Recréer les index
 */
val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Créer la nouvelle table sans la FK sur forestOwnerId
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `parcelles_new` (
                `parcelleId` TEXT NOT NULL,
                `forestOwnerId` TEXT,
                `foretId` TEXT,
                `name` TEXT NOT NULL,
                `surfaceHa` REAL,
                `shape` TEXT,
                `slopePct` REAL,
                `aspect` TEXT,
                `access` TEXT,
                `altitudeM` REAL,
                `objectifType` TEXT,
                `objectifVal` REAL,
                `tolerancePct` REAL,
                `samplingMode` TEXT,
                `sampleAreaM2` REAL,
                `targetSpeciesCsv` TEXT,
                `srid` INTEGER,
                `remarks` TEXT,
                `codeInseeCommune` TEXT,
                `nomCommune` TEXT,
                `sectionCadastrale` TEXT,
                `numeroCadastral` TEXT,
                `contenanceCadastraleHa` REAL,
                `geometrieIgnWkt` TEXT,
                `natureCadastraleCode` TEXT,
                `localisationMode` TEXT,
                `codeSer` TEXT,
                `nomSer` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`parcelleId`),
                FOREIGN KEY(`foretId`) REFERENCES `forets`(`foretId`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )

        // 2. Copier les données
        db.execSQL(
            """
            INSERT INTO `parcelles_new` (
                `parcelleId`, `forestOwnerId`, `foretId`, `name`, `surfaceHa`,
                `shape`, `slopePct`, `aspect`, `access`, `altitudeM`,
                `objectifType`, `objectifVal`, `tolerancePct`, `samplingMode`,
                `sampleAreaM2`, `targetSpeciesCsv`, `srid`, `remarks`,
                `codeInseeCommune`, `nomCommune`, `sectionCadastrale`,
                `numeroCadastral`, `contenanceCadastraleHa`, `geometrieIgnWkt`,
                `natureCadastraleCode`, `localisationMode`, `codeSer`, `nomSer`,
                `createdAt`, `updatedAt`
            )
            SELECT
                `parcelleId`, `forestOwnerId`, `foretId`, `name`, `surfaceHa`,
                `shape`, `slopePct`, `aspect`, `access`, `altitudeM`,
                `objectifType`, `objectifVal`, `tolerancePct`, `samplingMode`,
                `sampleAreaM2`, `targetSpeciesCsv`, `srid`, `remarks`,
                `codeInseeCommune`, `nomCommune`, `sectionCadastrale`,
                `numeroCadastral`, `contenanceCadastraleHa`, `geometrieIgnWkt`,
                `natureCadastraleCode`, `localisationMode`, `codeSer`, `nomSer`,
                `createdAt`, `updatedAt`
            FROM `parcelles`
            """.trimIndent()
        )

        // 3. Drop ancienne table
        db.execSQL("DROP TABLE IF EXISTS `parcelles`")

        // 4. Renommer
        db.execSQL("ALTER TABLE `parcelles_new` RENAME TO `parcelles`")

        // 5. Recréer les index
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_parcelles_name` ON `parcelles`(`name`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_parcelles_forestOwnerId` ON `parcelles`(`forestOwnerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_parcelles_foretId` ON `parcelles`(`foretId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_parcelles_codeInsee` ON `parcelles`(`codeInseeCommune`)")
    }
}
