package com.forestry.counter.data.local

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Toutes les migrations Room de l'application, extraites de ForestryCounterApplication
 * pour faciliter la maintenance et la testabilité.
 */
object DatabaseMigrations {

    private const val TAG = "DBMigrations"

    private fun SupportSQLiteDatabase.execSafe(sql: String, migration: String) {
        try {
            execSQL(sql)
        } catch (e: Throwable) {
            Log.w(TAG, "Migration $migration: failed to execute [$sql]", e)
        }
    }

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE counters ADD COLUMN decimalPlaces INTEGER")
            db.execSQL("ALTER TABLE counters ADD COLUMN initialValue REAL")
            db.execSQL("ALTER TABLE counters ADD COLUMN resetValue REAL")
            db.execSQL("ALTER TABLE counters ADD COLUMN soundEnabled INTEGER")
            db.execSQL("ALTER TABLE counters ADD COLUMN vibrationEnabled INTEGER")
            db.execSQL("ALTER TABLE counters ADD COLUMN vibrationIntensity INTEGER")
            db.execSQL("ALTER TABLE counters ADD COLUMN targetAction TEXT")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_counters_groupOwnerId ON counters(groupOwnerId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_counters_name ON counters(name)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_counters_sortIndex ON counters(sortIndex)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_counters_groupOwnerId_sortIndex ON counters(groupOwnerId, sortIndex)")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS parcelles (parcelleId TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, surfaceHa REAL, objectifType TEXT, objectifVal REAL, srid INTEGER, remarks TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_parcelles_name ON parcelles(name)")

            db.execSQL("CREATE TABLE IF NOT EXISTS placettes (placetteId TEXT NOT NULL PRIMARY KEY, parcelleOwnerId TEXT NOT NULL, type TEXT, rayonM REAL, surfaceM2 REAL, centerWkt TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN KEY(parcelleOwnerId) REFERENCES parcelles(parcelleId) ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_placettes_parcelleOwnerId ON placettes(parcelleOwnerId)")

            db.execSQL("CREATE TABLE IF NOT EXISTS essences (code TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, categorie TEXT, densiteBoite REAL)")

            db.execSQL("CREATE TABLE IF NOT EXISTS parameters (key TEXT NOT NULL PRIMARY KEY, valueJson TEXT NOT NULL, updatedAt INTEGER NOT NULL)")

            db.execSQL("CREATE TABLE IF NOT EXISTS tiges (tigeId TEXT NOT NULL PRIMARY KEY, parcelleOwnerId TEXT NOT NULL, placetteOwnerId TEXT, essenceCode TEXT NOT NULL, diamCm REAL NOT NULL, hauteurM REAL, gpsWkt TEXT, precisionM REAL, altitudeM REAL, timestamp INTEGER NOT NULL, note TEXT, produit TEXT, fCoef REAL, valueEur REAL, FOREIGN KEY(parcelleOwnerId) REFERENCES parcelles(parcelleId) ON DELETE CASCADE, FOREIGN KEY(placetteOwnerId) REFERENCES placettes(placetteId) ON DELETE SET NULL, FOREIGN KEY(essenceCode) REFERENCES essences(code) ON DELETE RESTRICT)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_tiges_parcelleOwnerId ON tiges(parcelleOwnerId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_tiges_placetteOwnerId ON tiges(placetteOwnerId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_tiges_essenceCode ON tiges(essenceCode)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_tiges_diamCm ON tiges(diamCm)")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSafe("ALTER TABLE parcelles ADD COLUMN forestOwnerId TEXT", "4→5")
            db.execSafe("ALTER TABLE parcelles ADD COLUMN tolerancePct REAL", "4→5")
            db.execSafe("ALTER TABLE parcelles ADD COLUMN samplingMode TEXT", "4→5")
            db.execSafe("ALTER TABLE parcelles ADD COLUMN sampleAreaM2 REAL", "4→5")
            db.execSafe("ALTER TABLE parcelles ADD COLUMN targetSpeciesCsv TEXT", "4→5")
            db.execSafe("CREATE INDEX IF NOT EXISTS index_parcelles_forestOwnerId ON parcelles(forestOwnerId)", "4→5")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSafe("ALTER TABLE placettes ADD COLUMN name TEXT", "5→6")
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSafe("ALTER TABLE tiges ADD COLUMN numero INTEGER", "6→7")
            db.execSafe("ALTER TABLE tiges ADD COLUMN categorie TEXT", "6→7")
            db.execSafe("ALTER TABLE tiges ADD COLUMN qualite INTEGER", "6→7")
            db.execSafe("ALTER TABLE tiges ADD COLUMN defauts TEXT", "6→7")
            db.execSafe("ALTER TABLE tiges ADD COLUMN photoUri TEXT", "6→7")
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSafe("ALTER TABLE essences ADD COLUMN colorHex TEXT", "7→8")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSafe("ALTER TABLE parcelles ADD COLUMN shape TEXT", "8→9")
            db.execSafe("ALTER TABLE parcelles ADD COLUMN slopePct REAL", "8→9")
            db.execSafe("ALTER TABLE parcelles ADD COLUMN aspect TEXT", "8→9")
            db.execSafe("ALTER TABLE parcelles ADD COLUMN access TEXT", "8→9")
            db.execSafe("ALTER TABLE parcelles ADD COLUMN altitudeM REAL", "8→9")
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSafe("ALTER TABLE tiges ADD COLUMN qualiteDetail TEXT", "9→10")
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSafe("ALTER TABLE essences ADD COLUMN densiteBois REAL", "10→11")
            db.execSafe("ALTER TABLE essences ADD COLUMN qualiteTypique TEXT", "10→11")
            db.execSafe("ALTER TABLE essences ADD COLUMN typeCoupePreferee TEXT", "10→11")
            db.execSafe("ALTER TABLE essences ADD COLUMN usageBois TEXT", "10→11")
            db.execSafe("ALTER TABLE essences ADD COLUMN vitesseCroissance TEXT", "10→11")
            db.execSafe("ALTER TABLE essences ADD COLUMN hauteurMaxM REAL", "10→11")
            db.execSafe("ALTER TABLE essences ADD COLUMN diametreMaxCm REAL", "10→11")
            db.execSafe("ALTER TABLE essences ADD COLUMN toleranceOmbre TEXT", "10→11")
            db.execSafe("ALTER TABLE essences ADD COLUMN remarques TEXT", "10→11")
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS ibp_evaluations (
                    id TEXT NOT NULL PRIMARY KEY,
                    placetteId TEXT NOT NULL,
                    parcelleId TEXT NOT NULL,
                    observationDate INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    evaluatorName TEXT NOT NULL DEFAULT '',
                    answersJson TEXT NOT NULL DEFAULT '{}',
                    globalNote TEXT NOT NULL DEFAULT ''
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_ibp_placetteId ON ibp_evaluations(placetteId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_ibp_parcelleId ON ibp_evaluations(parcelleId)")
        }
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSafe("ALTER TABLE ibp_evaluations ADD COLUMN growthConditions TEXT NOT NULL DEFAULT 'LOWLAND'", "12→13")
        }
    }

    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSafe("ALTER TABLE ibp_evaluations ADD COLUMN ibpMode TEXT NOT NULL DEFAULT 'COMPLET'", "13→14")
        }
    }

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSafe("ALTER TABLE ibp_evaluations ADD COLUMN latitude REAL", "14→15")
            db.execSafe("ALTER TABLE ibp_evaluations ADD COLUMN longitude REAL", "14→15")
        }
    }

    /** Liste ordonnée de toutes les migrations pour Room.databaseBuilder */
    val ALL = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
        MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
        MIGRATION_13_14, MIGRATION_14_15
    )
}
