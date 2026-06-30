package com.forestry.counter.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("PRAGMA foreign_keys = OFF")

        rebuildParcelles(database)
        rebuildTiges(database)
        rebuildIbpEvaluations(database)
        rebuildArbresHabitat(database)
        rebuildObservationsFlore(database)

        database.execSQL("PRAGMA foreign_keys = ON")
    }

    private fun rebuildParcelles(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE `parcelles_temp` AS SELECT * FROM `parcelles`")
        db.execSQL("DROP TABLE IF EXISTS `parcelles`")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `parcelles` (
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
                FOREIGN KEY(`forestOwnerId`) REFERENCES `forets`(`foretId`) ON DELETE SET NULL,
                FOREIGN KEY(`foretId`) REFERENCES `forets`(`foretId`) ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `parcelles` (
                `parcelleId`, `forestOwnerId`, `foretId`, `name`, `surfaceHa`, `shape`,
                `slopePct`, `aspect`, `access`, `altitudeM`, `objectifType`, `objectifVal`,
                `tolerancePct`, `samplingMode`, `sampleAreaM2`, `targetSpeciesCsv`, `srid`,
                `remarks`, `codeInseeCommune`, `nomCommune`, `sectionCadastrale`,
                `numeroCadastral`, `contenanceCadastraleHa`, `geometrieIgnWkt`,
                `natureCadastraleCode`, `localisationMode`, `codeSer`, `nomSer`,
                `createdAt`, `updatedAt`
            )
            SELECT
                `parcelleId`, `forestOwnerId`, `foretId`, `name`, `surfaceHa`, `shape`,
                `slopePct`, `aspect`, `access`, `altitudeM`, `objectifType`, `objectifVal`,
                `tolerancePct`, `samplingMode`, `sampleAreaM2`, `targetSpeciesCsv`, `srid`,
                `remarks`, `codeInseeCommune`, `nomCommune`, `sectionCadastrale`,
                `numeroCadastral`, `contenanceCadastraleHa`, `geometrieIgnWkt`,
                `natureCadastraleCode`, `localisationMode`, `codeSer`, `nomSer`,
                `createdAt`, `updatedAt`
            FROM `parcelles_temp`
        """.trimIndent())
        db.execSQL("DROP TABLE `parcelles_temp`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_parcelles_name` ON `parcelles`(`name`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_parcelles_forestOwnerId` ON `parcelles`(`forestOwnerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_parcelles_foretId` ON `parcelles`(`foretId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_parcelles_codeInsee` ON `parcelles`(`codeInseeCommune`)")
    }

    private fun rebuildTiges(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE `tiges_temp` AS SELECT * FROM `tiges`")
        db.execSQL("DROP TABLE IF EXISTS `tiges`")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `tiges` (
                `tigeId` TEXT NOT NULL,
                `parcelleOwnerId` TEXT NOT NULL,
                `placetteOwnerId` TEXT,
                `sessionId` TEXT,
                `essenceCode` TEXT NOT NULL,
                `diamCm` REAL NOT NULL,
                `hauteurM` REAL,
                `gpsWkt` TEXT,
                `precisionM` REAL,
                `altitudeM` REAL,
                `timestamp` INTEGER NOT NULL,
                `note` TEXT,
                `produit` TEXT,
                `fCoef` REAL,
                `valueEur` REAL,
                `numero` INTEGER,
                `categorie` TEXT,
                `qualite` INTEGER,
                `defauts` TEXT,
                `photoUri` TEXT,
                `qualiteDetail` TEXT,
                `classeKraft` INTEGER,
                `etatSanitaire` TEXT,
                `vigueur` TEXT,
                `origine` TEXT,
                `typeCoupe` TEXT,
                `biomasseFusTonnes` REAL,
                `carboneFusTonnes` REAL,
                `coefficientElancement` REAL,
                `houppierM` REAL,
                `houppierPct` REAL,
                `isTigeHabitat` INTEGER NOT NULL,
                PRIMARY KEY(`tigeId`),
                FOREIGN KEY(`parcelleOwnerId`) REFERENCES `parcelles`(`parcelleId`) ON DELETE CASCADE,
                FOREIGN KEY(`placetteOwnerId`) REFERENCES `placettes`(`placetteId`) ON DELETE SET NULL,
                FOREIGN KEY(`essenceCode`) REFERENCES `essences`(`code`) ON DELETE RESTRICT,
                FOREIGN KEY(`sessionId`) REFERENCES `inventaire_sessions`(`sessionId`) ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `tiges` (
                `tigeId`, `parcelleOwnerId`, `placetteOwnerId`, `sessionId`, `essenceCode`,
                `diamCm`, `hauteurM`, `gpsWkt`, `precisionM`, `altitudeM`, `timestamp`,
                `note`, `produit`, `fCoef`, `valueEur`, `numero`, `categorie`, `qualite`,
                `defauts`, `photoUri`, `qualiteDetail`, `classeKraft`, `etatSanitaire`,
                `vigueur`, `origine`, `typeCoupe`, `biomasseFusTonnes`, `carboneFusTonnes`,
                `coefficientElancement`, `houppierM`, `houppierPct`, `isTigeHabitat`
            )
            SELECT
                `tigeId`, `parcelleOwnerId`, `placetteOwnerId`, `sessionId`, `essenceCode`,
                `diamCm`, `hauteurM`, `gpsWkt`, `precisionM`, `altitudeM`, `timestamp`,
                `note`, `produit`, `fCoef`, `valueEur`, `numero`, `categorie`, `qualite`,
                `defauts`, `photoUri`, `qualiteDetail`, `classeKraft`, `etatSanitaire`,
                `vigueur`, `origine`, `typeCoupe`, `biomasseFusTonnes`, `carboneFusTonnes`,
                `coefficientElancement`, `houppierM`, `houppierPct`, `isTigeHabitat`
            FROM `tiges_temp`
        """.trimIndent())
        db.execSQL("DROP TABLE `tiges_temp`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tiges_parcelleOwnerId` ON `tiges`(`parcelleOwnerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tiges_placetteOwnerId` ON `tiges`(`placetteOwnerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tiges_essenceCode` ON `tiges`(`essenceCode`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tiges_diamCm` ON `tiges`(`diamCm`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tiges_sessionId` ON `tiges`(`sessionId`)")
    }

    private fun rebuildIbpEvaluations(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE `ibp_evaluations_temp` AS SELECT * FROM `ibp_evaluations`")
        db.execSQL("DROP TABLE IF EXISTS `ibp_evaluations`")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `ibp_evaluations` (
                `id` TEXT NOT NULL,
                `placetteId` TEXT NOT NULL,
                `parcelleId` TEXT NOT NULL,
                `observationDate` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `evaluatorName` TEXT NOT NULL DEFAULT '',
                `answersJson` TEXT NOT NULL DEFAULT '{}',
                `globalNote` TEXT NOT NULL DEFAULT '',
                `growthConditions` TEXT NOT NULL DEFAULT 'LOWLAND',
                `ibpMode` TEXT NOT NULL DEFAULT 'COMPLET',
                `latitude` REAL,
                `longitude` REAL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`placetteId`) REFERENCES `placettes`(`placetteId`) ON DELETE CASCADE,
                FOREIGN KEY(`parcelleId`) REFERENCES `parcelles`(`parcelleId`) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `ibp_evaluations` (
                `id`, `placetteId`, `parcelleId`, `observationDate`, `createdAt`,
                `updatedAt`, `evaluatorName`, `answersJson`, `globalNote`,
                `growthConditions`, `ibpMode`, `latitude`, `longitude`
            )
            SELECT
                `id`, `placetteId`, `parcelleId`, `observationDate`, `createdAt`,
                `updatedAt`, `evaluatorName`, `answersJson`, `globalNote`,
                `growthConditions`, `ibpMode`, `latitude`, `longitude`
            FROM `ibp_evaluations_temp`
        """.trimIndent())
        db.execSQL("DROP TABLE `ibp_evaluations_temp`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ibp_placetteId` ON `ibp_evaluations`(`placetteId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ibp_parcelleId` ON `ibp_evaluations`(`parcelleId`)")
    }

    private fun rebuildArbresHabitat(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE `arbres_habitat_temp` AS SELECT * FROM `arbres_habitat`")
        db.execSQL("DROP TABLE IF EXISTS `arbres_habitat`")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `arbres_habitat` (
                `arbreHabitatId` TEXT NOT NULL,
                `parcelleId` TEXT NOT NULL,
                `placetteId` TEXT,
                `sessionId` TEXT,
                `tigeId` TEXT,
                `essenceCode` TEXT NOT NULL,
                `diamCm` REAL NOT NULL,
                `hauteurM` REAL,
                `gpsWkt` TEXT,
                `cavitesBranches` INTEGER NOT NULL DEFAULT 0,
                `cavitesTronc` INTEGER NOT NULL DEFAULT 0,
                `logenBois` INTEGER NOT NULL DEFAULT 0,
                `ecorceDecolleeM2` REAL,
                `epiphytesM2` REAL,
                `bioticBoss` INTEGER NOT NULL DEFAULT 0,
                `dendrothelme` INTEGER NOT NULL DEFAULT 0,
                `lianes` INTEGER NOT NULL DEFAULT 0,
                `fissures` INTEGER NOT NULL DEFAULT 0,
                `boisMortSurPied` INTEGER NOT NULL DEFAULT 0,
                `boisMortSolM3` REAL,
                `treemScore` INTEGER,
                `classeDiamHabitat` TEXT,
                `isArbreVivant` INTEGER NOT NULL DEFAULT 1,
                `isArbreRemarquable` INTEGER NOT NULL DEFAULT 0,
                `remarques` TEXT,
                `dateObservation` INTEGER NOT NULL,
                PRIMARY KEY(`arbreHabitatId`),
                FOREIGN KEY(`parcelleId`) REFERENCES `parcelles`(`parcelleId`) ON DELETE CASCADE,
                FOREIGN KEY(`placetteId`) REFERENCES `placettes`(`placetteId`) ON DELETE SET NULL,
                FOREIGN KEY(`sessionId`) REFERENCES `inventaire_sessions`(`sessionId`) ON DELETE SET NULL,
                FOREIGN KEY(`tigeId`) REFERENCES `tiges`(`tigeId`) ON DELETE SET NULL,
                FOREIGN KEY(`essenceCode`) REFERENCES `essences`(`code`) ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `arbres_habitat` (
                `arbreHabitatId`, `parcelleId`, `placetteId`, `sessionId`, `tigeId`,
                `essenceCode`, `diamCm`, `hauteurM`, `gpsWkt`, `cavitesBranches`,
                `cavitesTronc`, `logenBois`, `ecorceDecolleeM2`, `epiphytesM2`,
                `bioticBoss`, `dendrothelme`, `lianes`, `fissures`, `boisMortSurPied`,
                `boisMortSolM3`, `treemScore`, `classeDiamHabitat`, `isArbreVivant`,
                `isArbreRemarquable`, `remarques`, `dateObservation`
            )
            SELECT
                `arbreHabitatId`, `parcelleId`, `placetteId`, `sessionId`, `tigeId`,
                `essenceCode`, `diamCm`, `hauteurM`, `gpsWkt`, `cavitesBranches`,
                `cavitesTronc`, `logenBois`, `ecorceDecolleeM2`, `epiphytesM2`,
                `bioticBoss`, `dendrothelme`, `lianes`, `fissures`, `boisMortSurPied`,
                `boisMortSolM3`, `treemScore`, `classeDiamHabitat`, `isArbreVivant`,
                `isArbreRemarquable`, `remarques`, `dateObservation`
            FROM `arbres_habitat_temp`
        """.trimIndent())
        db.execSQL("DROP TABLE `arbres_habitat_temp`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_arbre_hab_parcelleId` ON `arbres_habitat`(`parcelleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_arbre_hab_placetteId` ON `arbres_habitat`(`placetteId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_arbre_hab_sessionId` ON `arbres_habitat`(`sessionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_arbre_hab_tigeId` ON `arbres_habitat`(`tigeId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_arbre_hab_essenceCode` ON `arbres_habitat`(`essenceCode`)")
    }

    private fun rebuildObservationsFlore(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE `observations_flore_temp` AS SELECT * FROM `observations_flore`")
        db.execSQL("DROP TABLE IF EXISTS `observations_flore`")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `observations_flore` (
                `observationId` TEXT NOT NULL,
                `parcelleId` TEXT NOT NULL,
                `placetteId` TEXT,
                `sessionId` TEXT,
                `codeEspece` TEXT NOT NULL,
                `nomScientifique` TEXT NOT NULL,
                `nomCommun` TEXT,
                `abundanceDominance` TEXT NOT NULL,
                `strate` TEXT NOT NULL,
                `sociabilite` INTEGER,
                `indicateurEllenbergL` INTEGER,
                `indicateurEllenbergT` INTEGER,
                `indicateurEllenbergR` INTEGER,
                `indicateurEllenbergF` INTEGER,
                `indicateurEllenbergN` INTEGER,
                `isEspeceProtegee` INTEGER NOT NULL DEFAULT 0,
                `isEspeceIndicatrice` INTEGER NOT NULL DEFAULT 0,
                `dateSaisie` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`observationId`),
                FOREIGN KEY(`parcelleId`) REFERENCES `parcelles`(`parcelleId`) ON DELETE CASCADE,
                FOREIGN KEY(`placetteId`) REFERENCES `placettes`(`placetteId`) ON DELETE SET NULL,
                FOREIGN KEY(`sessionId`) REFERENCES `inventaire_sessions`(`sessionId`) ON DELETE SET NULL,
                FOREIGN KEY(`codeEspece`) REFERENCES `essences`(`code`) ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `observations_flore` (
                `observationId`, `parcelleId`, `placetteId`, `sessionId`, `codeEspece`,
                `nomScientifique`, `nomCommun`, `abundanceDominance`, `strate`,
                `sociabilite`, `indicateurEllenbergL`, `indicateurEllenbergT`,
                `indicateurEllenbergR`, `indicateurEllenbergF`, `indicateurEllenbergN`,
                `isEspeceProtegee`, `isEspeceIndicatrice`, `dateSaisie`, `createdAt`
            )
            SELECT
                `observationId`, `parcelleId`, `placetteId`, `sessionId`, `codeEspece`,
                `nomScientifique`, `nomCommun`, `abundanceDominance`, `strate`,
                `sociabilite`, `indicateurEllenbergL`, `indicateurEllenbergT`,
                `indicateurEllenbergR`, `indicateurEllenbergF`, `indicateurEllenbergN`,
                `isEspeceProtegee`, `isEspeceIndicatrice`, `dateSaisie`, `createdAt`
            FROM `observations_flore_temp`
        """.trimIndent())
        db.execSQL("DROP TABLE `observations_flore_temp`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_flore_parcelleId` ON `observations_flore`(`parcelleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_flore_placetteId` ON `observations_flore`(`placetteId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_flore_sessionId` ON `observations_flore`(`sessionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_flore_codeEspece` ON `observations_flore`(`codeEspece`)")
    }
}
