package com.forestry.counter.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Toutes les migrations Room de l'application, extraites de ForestryCounterApplication
 * pour faciliter la maintenance et la testabilité.
 */
object DatabaseMigrations {

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
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN forestOwnerId TEXT") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN tolerancePct REAL") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN samplingMode TEXT") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN sampleAreaM2 REAL") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN targetSpeciesCsv TEXT") } catch (_: Throwable) {}
            try { db.execSQL("CREATE INDEX IF NOT EXISTS index_parcelles_forestOwnerId ON parcelles(forestOwnerId)") } catch (_: Throwable) {}
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try { db.execSQL("ALTER TABLE placettes ADD COLUMN name TEXT") } catch (_: Throwable) {}
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try { db.execSQL("ALTER TABLE tiges ADD COLUMN numero INTEGER") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE tiges ADD COLUMN categorie TEXT") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE tiges ADD COLUMN qualite INTEGER") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE tiges ADD COLUMN defauts TEXT") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE tiges ADD COLUMN photoUri TEXT") } catch (_: Throwable) {}
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try { db.execSQL("ALTER TABLE essences ADD COLUMN colorHex TEXT") } catch (_: Throwable) {}
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN shape TEXT") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN slopePct REAL") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN aspect TEXT") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN access TEXT") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN altitudeM REAL") } catch (_: Throwable) {}
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try { db.execSQL("ALTER TABLE tiges ADD COLUMN qualiteDetail TEXT") } catch (_: Throwable) {}
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try { db.execSQL("ALTER TABLE essences ADD COLUMN densiteBois REAL") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE essences ADD COLUMN qualiteTypique TEXT") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE essences ADD COLUMN typeCoupePreferee TEXT") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE essences ADD COLUMN usageBois TEXT") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE essences ADD COLUMN vitesseCroissance TEXT") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE essences ADD COLUMN hauteurMaxM REAL") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE essences ADD COLUMN diametreMaxCm REAL") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE essences ADD COLUMN toleranceOmbre TEXT") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE essences ADD COLUMN remarques TEXT") } catch (_: Throwable) {}
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
            try { db.execSQL("ALTER TABLE ibp_evaluations ADD COLUMN growthConditions TEXT NOT NULL DEFAULT 'LOWLAND'") } catch (_: Throwable) {}
        }
    }

    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try { db.execSQL("ALTER TABLE ibp_evaluations ADD COLUMN ibpMode TEXT NOT NULL DEFAULT 'COMPLET'") } catch (_: Throwable) {}
        }
    }

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try { db.execSQL("ALTER TABLE ibp_evaluations ADD COLUMN latitude REAL") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE ibp_evaluations ADD COLUMN longitude REAL") } catch (_: Throwable) {}
        }
    }

    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS ripisylve_diagnostics (
                    id TEXT NOT NULL PRIMARY KEY,
                    parcelleId TEXT NOT NULL,
                    observerName TEXT NOT NULL DEFAULT '',
                    observationDate INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    latitude REAL,
                    longitude REAL,
                    altitudeM REAL,
                    sectionLengthM REAL NOT NULL DEFAULT 50.0,
                    sectionNotes TEXT NOT NULL DEFAULT '',
                    continuitePct REAL NOT NULL DEFAULT 0.0,
                    largeurMode TEXT NOT NULL DEFAULT 'UNE_RANGEE',
                    strateHerbacee INTEGER NOT NULL DEFAULT 0,
                    strateArbustive INTEGER NOT NULL DEFAULT 0,
                    strateArborescente INTEGER NOT NULL DEFAULT 0,
                    nbEspecesObservees INTEGER NOT NULL DEFAULT 0,
                    especesObserveesCsv TEXT NOT NULL DEFAULT '',
                    diamAutoFromDendro INTEGER NOT NULL DEFAULT 0,
                    hasTresPetitBois INTEGER NOT NULL DEFAULT 0,
                    hasPetitBois INTEGER NOT NULL DEFAULT 0,
                    hasMoyenBois INTEGER NOT NULL DEFAULT 0,
                    hasGrosBois INTEGER NOT NULL DEFAULT 0,
                    microhabitatCavites INTEGER NOT NULL DEFAULT 0,
                    microhabitatFissures INTEGER NOT NULL DEFAULT 0,
                    microhabitatDecollementEcorce INTEGER NOT NULL DEFAULT 0,
                    microhabitatLierre INTEGER NOT NULL DEFAULT 0,
                    microhabitatBoisMort INTEGER NOT NULL DEFAULT 0,
                    microhabitatCheveluRacinaire INTEGER NOT NULL DEFAULT 0,
                    santeSanitaire TEXT NOT NULL DEFAULT '',
                    santeDegatsCastor INTEGER NOT NULL DEFAULT 0,
                    inadapteesMode TEXT NOT NULL DEFAULT 'ABSENCE',
                    stabiliteBerge TEXT NOT NULL DEFAULT '',
                    stabiliteErosion INTEGER NOT NULL DEFAULT 0,
                    stabilitePietinement INTEGER NOT NULL DEFAULT 0,
                    notes TEXT NOT NULL DEFAULT ''
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_ripisylve_parcelleId ON ripisylve_diagnostics(parcelleId)")
        }
    }

    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS station_diagnostics (
                    id TEXT PRIMARY KEY NOT NULL,
                    parcelleId TEXT NOT NULL,
                    observerName TEXT NOT NULL,
                    observationDate INTEGER NOT NULL,
                    latitude REAL,
                    longitude REAL,
                    altitudeM REAL,
                    commune TEXT NOT NULL,
                    pentePct REAL,
                    exposition TEXT NOT NULL,
                    positionTopo TEXT NOT NULL,
                    distanceCourseauM REAL,
                    profondeurSolCm INTEGER,
                    texture TEXT NOT NULL,
                    pierrosite TEXT NOT NULL,
                    hydromorphieProfondeurCm INTEGER,
                    humus TEXT NOT NULL,
                    phEstime REAL,
                    testHcl TEXT NOT NULL,
                    drainage TEXT NOT NULL,
                    rocheMere TEXT NOT NULL,
                    gradientHydrique INTEGER NOT NULL,
                    gradientTrophique INTEGER NOT NULL,
                    gradientLumineux INTEGER NOT NULL,
                    gradientHumique INTEGER NOT NULL,
                    especesIndicatricesJson TEXT NOT NULL,
                    especesXerophiles INTEGER NOT NULL,
                    especesMesophiles INTEGER NOT NULL,
                    especesHygrophiles INTEGER NOT NULL,
                    notes TEXT NOT NULL
                )
            """.trimIndent())
        }
    }

    val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try { db.execSQL("ALTER TABLE tiges ADD COLUMN destination TEXT") } catch (_: Throwable) {}
        }
    }

    /**
     * Recrée ripisylve_diagnostics avec le schéma exact de RipisylveEntity :
     * - Supprime les clauses DEFAULT héritées de MIGRATION_15_16 (Room valide defaultValue='undefined')
     * - Ajoute les nouvelles colonnes : isDraft, microhabitatChampignons, microhabitatTresGrosBois,
     *   sanitairePct, invasivesPct, invasivesCsv, stabilitePct, globalNotes, photosJson
     * - Supprime les colonnes obsolètes : microhabitatLierre, microhabitatCheveluRacinaire,
     *   santeSanitaire, santeDegatsCastor, stabiliteBerge, stabiliteErosion, stabilitePietinement, notes
     */
    val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS ripisylve_diagnostics_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    parcelleId TEXT NOT NULL,
                    observerName TEXT NOT NULL,
                    observationDate INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    isDraft INTEGER NOT NULL,
                    latitude REAL,
                    longitude REAL,
                    altitudeM REAL,
                    sectionLengthM REAL NOT NULL,
                    sectionNotes TEXT NOT NULL,
                    continuitePct REAL NOT NULL,
                    largeurMode TEXT NOT NULL,
                    strateHerbacee INTEGER NOT NULL,
                    strateArbustive INTEGER NOT NULL,
                    strateArborescente INTEGER NOT NULL,
                    nbEspecesObservees INTEGER NOT NULL,
                    especesObserveesCsv TEXT NOT NULL,
                    diamAutoFromDendro INTEGER NOT NULL,
                    hasTresPetitBois INTEGER NOT NULL,
                    hasPetitBois INTEGER NOT NULL,
                    hasMoyenBois INTEGER NOT NULL,
                    hasGrosBois INTEGER NOT NULL,
                    microhabitatCavites INTEGER NOT NULL,
                    microhabitatFissures INTEGER NOT NULL,
                    microhabitatDecollementEcorce INTEGER NOT NULL,
                    microhabitatChampignons INTEGER NOT NULL,
                    microhabitatBoisMort INTEGER NOT NULL,
                    microhabitatTresGrosBois INTEGER NOT NULL,
                    sanitairePct REAL NOT NULL,
                    invasivesPct REAL NOT NULL,
                    invasivesCsv TEXT NOT NULL,
                    inadapteesMode TEXT NOT NULL,
                    stabilitePct REAL NOT NULL,
                    globalNotes TEXT NOT NULL,
                    photosJson TEXT NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO ripisylve_diagnostics_new (
                    id, parcelleId, observerName, observationDate, createdAt, updatedAt,
                    isDraft, latitude, longitude, altitudeM,
                    sectionLengthM, sectionNotes, continuitePct, largeurMode,
                    strateHerbacee, strateArbustive, strateArborescente,
                    nbEspecesObservees, especesObserveesCsv, diamAutoFromDendro,
                    hasTresPetitBois, hasPetitBois, hasMoyenBois, hasGrosBois,
                    microhabitatCavites, microhabitatFissures, microhabitatDecollementEcorce,
                    microhabitatChampignons, microhabitatBoisMort, microhabitatTresGrosBois,
                    sanitairePct, invasivesPct, invasivesCsv,
                    inadapteesMode, stabilitePct, globalNotes, photosJson
                )
                SELECT
                    id, parcelleId, observerName, observationDate, createdAt, updatedAt,
                    0,
                    latitude, longitude, altitudeM,
                    sectionLengthM, sectionNotes, continuitePct, largeurMode,
                    strateHerbacee, strateArbustive, strateArborescente,
                    nbEspecesObservees, especesObserveesCsv, diamAutoFromDendro,
                    hasTresPetitBois, hasPetitBois, hasMoyenBois, hasGrosBois,
                    microhabitatCavites, microhabitatFissures, microhabitatDecollementEcorce,
                    0, microhabitatBoisMort, 0,
                    0.0, 0.0, '',
                    inadapteesMode, 0.0, notes, ''
                FROM ripisylve_diagnostics
            """.trimIndent())
            db.execSQL("DROP TABLE ripisylve_diagnostics")
            db.execSQL("ALTER TABLE ripisylve_diagnostics_new RENAME TO ripisylve_diagnostics")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_ripisylve_parcelleId ON ripisylve_diagnostics(parcelleId)")
        }
    }

    /**
     * Recrée station_diagnostics (ajout isDraft + photosJson manquants) et
     * ibp_evaluations (suppression des clauses DEFAULT incompatibles avec Room).
     */
    val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // ── station_diagnostics ──────────────────────────────────────────────
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS station_diagnostics_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    parcelleId TEXT NOT NULL,
                    observerName TEXT NOT NULL,
                    observationDate INTEGER NOT NULL,
                    isDraft INTEGER NOT NULL,
                    photosJson TEXT NOT NULL,
                    latitude REAL,
                    longitude REAL,
                    altitudeM REAL,
                    commune TEXT NOT NULL,
                    pentePct REAL,
                    exposition TEXT NOT NULL,
                    positionTopo TEXT NOT NULL,
                    distanceCourseauM REAL,
                    profondeurSolCm INTEGER,
                    texture TEXT NOT NULL,
                    pierrosite TEXT NOT NULL,
                    hydromorphieProfondeurCm INTEGER,
                    humus TEXT NOT NULL,
                    phEstime REAL,
                    testHcl TEXT NOT NULL,
                    drainage TEXT NOT NULL,
                    rocheMere TEXT NOT NULL,
                    gradientHydrique INTEGER NOT NULL,
                    gradientTrophique INTEGER NOT NULL,
                    gradientLumineux INTEGER NOT NULL,
                    gradientHumique INTEGER NOT NULL,
                    especesIndicatricesJson TEXT NOT NULL,
                    especesXerophiles INTEGER NOT NULL,
                    especesMesophiles INTEGER NOT NULL,
                    especesHygrophiles INTEGER NOT NULL,
                    notes TEXT NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO station_diagnostics_new (
                    id, parcelleId, observerName, observationDate,
                    isDraft, photosJson,
                    latitude, longitude, altitudeM, commune,
                    pentePct, exposition, positionTopo, distanceCourseauM,
                    profondeurSolCm, texture, pierrosite, hydromorphieProfondeurCm,
                    humus, phEstime, testHcl, drainage, rocheMere,
                    gradientHydrique, gradientTrophique, gradientLumineux, gradientHumique,
                    especesIndicatricesJson, especesXerophiles, especesMesophiles,
                    especesHygrophiles, notes
                )
                SELECT
                    id, parcelleId, observerName, observationDate,
                    0, '[]',
                    latitude, longitude, altitudeM, commune,
                    pentePct, exposition, positionTopo, distanceCourseauM,
                    profondeurSolCm, texture, pierrosite, hydromorphieProfondeurCm,
                    humus, phEstime, testHcl, drainage, rocheMere,
                    gradientHydrique, gradientTrophique, gradientLumineux, gradientHumique,
                    especesIndicatricesJson, especesXerophiles, especesMesophiles,
                    especesHygrophiles, notes
                FROM station_diagnostics
            """.trimIndent())
            db.execSQL("DROP TABLE station_diagnostics")
            db.execSQL("ALTER TABLE station_diagnostics_new RENAME TO station_diagnostics")

            // ── ibp_evaluations ──────────────────────────────────────────────────
            // Recrée la table sans clauses DEFAULT (les MIGRATION_11_12..14_15 les avaient ajoutées)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS ibp_evaluations_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    placetteId TEXT NOT NULL,
                    parcelleId TEXT NOT NULL,
                    observationDate INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    evaluatorName TEXT NOT NULL,
                    answersJson TEXT NOT NULL,
                    globalNote TEXT NOT NULL,
                    growthConditions TEXT NOT NULL,
                    ibpMode TEXT NOT NULL,
                    latitude REAL,
                    longitude REAL
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO ibp_evaluations_new (
                    id, placetteId, parcelleId, observationDate, createdAt, updatedAt,
                    evaluatorName, answersJson, globalNote,
                    growthConditions, ibpMode,
                    latitude, longitude
                )
                SELECT
                    id, placetteId, parcelleId, observationDate, createdAt, updatedAt,
                    evaluatorName, answersJson, globalNote,
                    growthConditions, ibpMode,
                    latitude, longitude
                FROM ibp_evaluations
            """.trimIndent())
            db.execSQL("DROP TABLE ibp_evaluations")
            db.execSQL("ALTER TABLE ibp_evaluations_new RENAME TO ibp_evaluations")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_ibp_placetteId ON ibp_evaluations(placetteId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_ibp_parcelleId ON ibp_evaluations(parcelleId)")
        }
    }

    /**
     * v21 : table FTS flora (recherche pleine-texte offline) + cache contextes GPS.
     * RÈGLE : pas de DEFAULT dans les CREATE TABLE — colonnes NOT NULL gérées en Kotlin.
     */
    val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // FTS4 virtual table — Room ne génère pas le CREATE automatiquement pour FTS
            db.execSQL("""
                CREATE VIRTUAL TABLE IF NOT EXISTS flora_fts
                USING fts4(
                    species_id TEXT NOT NULL,
                    nom_francais TEXT NOT NULL,
                    nom_scientifique TEXT NOT NULL,
                    vernaculaires TEXT NOT NULL,
                    synonymes TEXT NOT NULL,
                    type_milieu TEXT NOT NULL,
                    strate TEXT NOT NULL
                )
            """.trimIndent())

            // Cache contextes GPS
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS gps_context_cache (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    lat_key REAL NOT NULL,
                    lon_key REAL NOT NULL,
                    region_code TEXT NOT NULL,
                    dept_code TEXT NOT NULL,
                    altitude_approx_m REAL NOT NULL,
                    topo_hint TEXT NOT NULL,
                    zone_humide_prob REAL NOT NULL,
                    pack_id_active TEXT NOT NULL,
                    computed_at INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
                CREATE UNIQUE INDEX IF NOT EXISTS index_gps_context_latlon
                ON gps_context_cache(lat_key, lon_key)
            """.trimIndent())
        }
    }

    /**
     * v22 : ajout colonne horizonsJson pour profil pédologique multi-horizons.
     * @ColumnInfo(defaultValue = "[]") présent sur l'entity → DEFAULT autorisé ici.
     */
    val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE station_diagnostics ADD COLUMN horizonsJson TEXT NOT NULL DEFAULT '[]'")
        }
    }

    /**
     * v23 : ajout 3 colonnes JSON station_diagnostics :
     * - floraEntriesJson : relevé botanique par strates (FloraEntry)
     * - biodiversiteJson : bois mort, micro-habitats, faune
     * - peuplementJson   : description qualitative du peuplement
     * @ColumnInfo(defaultValue=...) présent sur l'entity → DEFAULT autorisé.
     */
    val MIGRATION_22_23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE station_diagnostics ADD COLUMN floraEntriesJson TEXT NOT NULL DEFAULT '[]'")
            db.execSQL("ALTER TABLE station_diagnostics ADD COLUMN biodiversiteJson TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE station_diagnostics ADD COLUMN peuplementJson TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_23_24 = object : Migration(23, 24) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN commune TEXT") } catch (_: Throwable) {}
        }
    }

    /**
     * v25 : enrichissement cache GPS avec données MNT SRTM (pente, exposition)
     * et climatiques OpenMeteo (précip, temp moy, type climatique).
     * @ColumnInfo(defaultValue=...) présent sur l’entity → DEFAULT autorisé.
     */
    val MIGRATION_24_25 = object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try { db.execSQL("ALTER TABLE gps_context_cache ADD COLUMN slope_pct INTEGER NOT NULL DEFAULT -1") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE gps_context_cache ADD COLUMN aspect_deg INTEGER NOT NULL DEFAULT -1") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE gps_context_cache ADD COLUMN aspect_label TEXT NOT NULL DEFAULT ''") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE gps_context_cache ADD COLUMN precip_mm_an INTEGER NOT NULL DEFAULT -1") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE gps_context_cache ADD COLUMN temp_moy_c REAL NOT NULL DEFAULT -99") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE gps_context_cache ADD COLUMN climate_type TEXT NOT NULL DEFAULT 'INCONNU'") } catch (_: Throwable) {}
        }
    }

    /**
     * v26 : données pédologiques embarquées (EmbeddedSoilService) dans le cache GPS.
     * @ColumnInfo(defaultValue=...) présent sur l’entity → DEFAULT autorisé.
     */
    val MIGRATION_25_26 = object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try { db.execSQL("ALTER TABLE gps_context_cache ADD COLUMN soil_ph REAL NOT NULL DEFAULT -1") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE gps_context_cache ADD COLUMN soil_rum_mm INTEGER NOT NULL DEFAULT -1") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE gps_context_cache ADD COLUMN soil_texture TEXT NOT NULL DEFAULT 'INCONNUE'") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE gps_context_cache ADD COLUMN soil_drainage TEXT NOT NULL DEFAULT 'NORMAL'") } catch (_: Throwable) {}
        }
    }

    /** Liste ordonnée de toutes les migrations pour Room.databaseBuilder */
    val ALL = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
        MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
        MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
        MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21,
        MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25,
        MIGRATION_25_26
    )
}
