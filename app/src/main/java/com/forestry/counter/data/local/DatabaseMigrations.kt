package com.forestry.counter.data.local

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.forestry.counter.data.local.migration.Migration15to26

/**
 * Toutes les migrations Room de l'application, extraites de ForestryCounterApplication
 * pour faciliter la maintenance et la testabilité.
 */
object DatabaseMigrations {

    private const val TAG = "DatabaseMigrations"

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
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN forestOwnerId TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN tolerancePct REAL") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN samplingMode TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN sampleAreaM2 REAL") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN targetSpeciesCsv TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("CREATE INDEX IF NOT EXISTS index_parcelles_forestOwnerId ON parcelles(forestOwnerId)") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try { db.execSQL("ALTER TABLE placettes ADD COLUMN name TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try { db.execSQL("ALTER TABLE tiges ADD COLUMN numero INTEGER") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE tiges ADD COLUMN categorie TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE tiges ADD COLUMN qualite INTEGER") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE tiges ADD COLUMN defauts TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE tiges ADD COLUMN photoUri TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try { db.execSQL("ALTER TABLE essences ADD COLUMN colorHex TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN shape TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN slopePct REAL") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN aspect TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN access TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN altitudeM REAL") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try { db.execSQL("ALTER TABLE tiges ADD COLUMN qualiteDetail TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try { db.execSQL("ALTER TABLE essences ADD COLUMN densiteBois REAL") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE essences ADD COLUMN qualiteTypique TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE essences ADD COLUMN typeCoupePreferee TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE essences ADD COLUMN usageBois TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE essences ADD COLUMN vitesseCroissance TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE essences ADD COLUMN hauteurMaxM REAL") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE essences ADD COLUMN diametreMaxCm REAL") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE essences ADD COLUMN toleranceOmbre TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE essences ADD COLUMN remarques TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
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
            try { db.execSQL("ALTER TABLE ibp_evaluations ADD COLUMN growthConditions TEXT NOT NULL DEFAULT 'LOWLAND'") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
        }
    }

    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try { db.execSQL("ALTER TABLE ibp_evaluations ADD COLUMN ibpMode TEXT NOT NULL DEFAULT 'COMPLET'") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
        }
    }

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try { db.execSQL("ALTER TABLE ibp_evaluations ADD COLUMN latitude REAL") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE ibp_evaluations ADD COLUMN longitude REAL") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
        }
    }

    val MIGRATION_26_27 = object : Migration(26, 27) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // ── Table forets ──────────────────────────────────────────────────────
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS forets (
                    foretId TEXT NOT NULL PRIMARY KEY,
                    nom TEXT NOT NULL,
                    proprietaireNom TEXT NOT NULL,
                    proprietaireEmail TEXT,
                    gestionnaireNom TEXT,
                    typeForet TEXT,
                    objectifGestion TEXT,
                    psgNumero TEXT,
                    psgDateExpiration INTEGER,
                    departement TEXT,
                    remarques TEXT,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_forets_proprietaireNom ON forets(proprietaireNom)")

            // ── Table inventaire_sessions ─────────────────────────────────────────
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS inventaire_sessions (
                    sessionId TEXT NOT NULL PRIMARY KEY,
                    parcelleId TEXT NOT NULL,
                    typeSession TEXT NOT NULL,
                    dateDebut INTEGER NOT NULL,
                    dateFin INTEGER,
                    operateurNom TEXT,
                    methode TEXT,
                    intensiteEchantillonnagePct REAL,
                    objectifSession TEXT,
                    remarques TEXT,
                    createdAt INTEGER NOT NULL,
                    FOREIGN KEY(parcelleId) REFERENCES parcelles(parcelleId) ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sessions_parcelleId ON inventaire_sessions(parcelleId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sessions_typeSession ON inventaire_sessions(typeSession)")

            // ── Table stations_environnementales ─────────────────────────────────
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS stations_environnementales (
                    stationId TEXT NOT NULL PRIMARY KEY,
                    parcelleId TEXT NOT NULL,
                    altitudeM REAL, slopePct REAL, aspectDeg REAL, aspectLabel TEXT,
                    soilPh REAL, soilRumMm REAL, soilRufMm REAL, soilTexture TEXT, soilDrainage TEXT,
                    soilProfondeurCm INTEGER, soilHydromorphieCm INTEGER, soilTypeWrb TEXT, soilPhTerrain REAL,
                    rumClasseBdgsf TEXT, profondeurSolClasse TEXT, phSolForestier REAL, cOrganiqueTha REAL,
                    typeWrbBdgsf TEXT, pierrositeClassePct TEXT,
                    rocheMere TEXT, lithologie TEXT, phIndicatif REAL,
                    tempMoyC REAL, tempMinJanvC REAL, tempMaxJuillC REAL, precipMmAn REAL,
                    precipEteMm REAL, etpMm REAL, joursGel INTEGER, joursSecs INTEGER,
                    ensoleilH REAL, climateType TEXT,
                    idhe REAL, spei6Score REAL, indiceProductivite INTEGER, scoreVulnCC2050 INTEGER,
                    codeSer TEXT, nomSer TEXT,
                    dvfPrixMedianEurM2 REAL, dvfNbTransactions INTEGER, dvfDateFetch INTEGER,
                    vulnerabiliteActuelle INTEGER, vulnerabilite2050 INTEGER,
                    natura2000Code TEXT, natura2000Nom TEXT,
                    znieffType1 INTEGER NOT NULL DEFAULT 0,
                    znieffType2 INTEGER NOT NULL DEFAULT 0,
                    isForetAncienne INTEGER NOT NULL DEFAULT 0,
                    risqueIncendieZone TEXT, risqueInondation TEXT,
                    surfaceCadastraleHa REAL, geometrieWkt TEXT, natureCadastraleCode TEXT,
                    sourceDataQualityJson TEXT, fetchedAt INTEGER,
                    FOREIGN KEY(parcelleId) REFERENCES parcelles(parcelleId) ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_station_parcelleId ON stations_environnementales(parcelleId)")

            // ── Table diagnostics_sylvicoles ──────────────────────────────────────
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS diagnostics_sylvicoles (
                    diagnosticId TEXT NOT NULL PRIMARY KEY,
                    parcelleId TEXT NOT NULL,
                    sessionId TEXT,
                    dateCreation INTEGER NOT NULL,
                    operateurNom TEXT,
                    scoreStation INTEGER, scorePeuplement INTEGER, scoreBiodiversite INTEGER,
                    scoreRisque INTEGER, scoreGlobal INTEGER,
                    gHa REAL, nHa INTEGER, vHa REAL, hoM REAL, hgM REAL, dgCm REAL,
                    siteIndex REAL, accroissementIg REAL, accroissementIv REAL,
                    biomasseTotalTonnes REAL, carboneTotalTonnes REAL,
                    essencesRecommandeesJson TEXT, essencesDeconseillees TEXT,
                    essencesVigilanceJson TEXT, risquesDetectesJson TEXT,
                    recommandationsSylvicolesJson TEXT, typeSylviculturePreco TEXT,
                    volumeEclairciePreco REAL, delaiInterventionAns INTEGER,
                    syntheseTextuelle TEXT, algoVersion TEXT NOT NULL,
                    dataSourcesJson TEXT, remarques TEXT,
                    updatedAt INTEGER NOT NULL,
                    FOREIGN KEY(parcelleId) REFERENCES parcelles(parcelleId) ON DELETE CASCADE,
                    FOREIGN KEY(sessionId) REFERENCES inventaire_sessions(sessionId) ON DELETE SET NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_diag_parcelleId ON diagnostics_sylvicoles(parcelleId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_diag_sessionId ON diagnostics_sylvicoles(sessionId)")

            // ── Table observations_flore ──────────────────────────────────────────
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS observations_flore (
                    observationId TEXT NOT NULL PRIMARY KEY,
                    parcelleId TEXT NOT NULL,
                    placetteId TEXT,
                    sessionId TEXT,
                    codeEspece TEXT NOT NULL,
                    nomScientifique TEXT NOT NULL,
                    nomCommun TEXT,
                    abundanceDominance TEXT NOT NULL,
                    strate TEXT NOT NULL,
                    sociabilite INTEGER,
                    indicateurEllenbergL INTEGER, indicateurEllenbergT INTEGER,
                    indicateurEllenbergR INTEGER, indicateurEllenbergF INTEGER,
                    indicateurEllenbergN INTEGER,
                    isEspeceProtegee INTEGER NOT NULL DEFAULT 0,
                    isEspeceIndicatrice INTEGER NOT NULL DEFAULT 0,
                    dateSaisie INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    FOREIGN KEY(parcelleId) REFERENCES parcelles(parcelleId) ON DELETE CASCADE,
                    FOREIGN KEY(placetteId) REFERENCES placettes(placetteId) ON DELETE SET NULL,
                    FOREIGN KEY(sessionId) REFERENCES inventaire_sessions(sessionId) ON DELETE SET NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_flore_parcelleId ON observations_flore(parcelleId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_flore_placetteId ON observations_flore(placetteId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_flore_sessionId ON observations_flore(sessionId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_flore_codeEspece ON observations_flore(codeEspece)")

            // ── Table arbres_habitat ──────────────────────────────────────────────
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS arbres_habitat (
                    arbreHabitatId TEXT NOT NULL PRIMARY KEY,
                    parcelleId TEXT NOT NULL,
                    placetteId TEXT,
                    sessionId TEXT,
                    tigeId TEXT,
                    essenceCode TEXT NOT NULL,
                    diamCm REAL NOT NULL,
                    hauteurM REAL,
                    gpsWkt TEXT,
                    cavitesBranches INTEGER NOT NULL DEFAULT 0,
                    cavitesTronc INTEGER NOT NULL DEFAULT 0,
                    logenBois INTEGER NOT NULL DEFAULT 0,
                    ecorceDecolleeM2 REAL,
                    epiphytesM2 REAL,
                    bioticBoss INTEGER NOT NULL DEFAULT 0,
                    dendrothelme INTEGER NOT NULL DEFAULT 0,
                    lianes INTEGER NOT NULL DEFAULT 0,
                    fissures INTEGER NOT NULL DEFAULT 0,
                    boisMortSurPied INTEGER NOT NULL DEFAULT 0,
                    boisMortSolM3 REAL,
                    treemScore INTEGER,
                    classeDiamHabitat TEXT,
                    isArbreVivant INTEGER NOT NULL DEFAULT 1,
                    isArbreRemarquable INTEGER NOT NULL DEFAULT 0,
                    remarques TEXT,
                    dateObservation INTEGER NOT NULL,
                    FOREIGN KEY(parcelleId) REFERENCES parcelles(parcelleId) ON DELETE CASCADE,
                    FOREIGN KEY(placetteId) REFERENCES placettes(placetteId) ON DELETE SET NULL,
                    FOREIGN KEY(sessionId) REFERENCES inventaire_sessions(sessionId) ON DELETE SET NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_arbre_hab_parcelleId ON arbres_habitat(parcelleId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_arbre_hab_placetteId ON arbres_habitat(placetteId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_arbre_hab_sessionId ON arbres_habitat(sessionId)")

            // ── Table valeurs_foncieres ───────────────────────────────────────────
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS valeurs_foncieres (
                    valeurId TEXT NOT NULL PRIMARY KEY,
                    parcelleId TEXT NOT NULL,
                    dateEstimation INTEGER NOT NULL,
                    valeurFonciereNuEurHa REAL,
                    sourceValeurFonciere TEXT,
                    prixMarcheRegionalEurHa REAL,
                    volumeCommercialisableM3 REAL,
                    valeurBoisSurPiedEur REAL,
                    carboneTotalTonnes REAL,
                    valeurCarboneLabelBcEur REAL,
                    valeurTotalePatrimoineEur REAL,
                    coutEclaircieEstimeEur REAL,
                    coutRenouvellementEstimeEur REAL,
                    revenuBrutAnnuelMoyenEur REAL,
                    eligiblePsg INTEGER NOT NULL DEFAULT 0,
                    eligibleDefiForet INTEGER NOT NULL DEFAULT 0,
                    eligibleIfiExoneration INTEGER NOT NULL DEFAULT 0,
                    eligibleDpa INTEGER NOT NULL DEFAULT 0,
                    alertesFiscalesJson TEXT,
                    remarques TEXT,
                    updatedAt INTEGER NOT NULL,
                    FOREIGN KEY(parcelleId) REFERENCES parcelles(parcelleId) ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_valeur_parcelleId ON valeurs_foncieres(parcelleId)")

            // ── Table alertes_sanitaires ──────────────────────────────────────────
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS alertes_sanitaires (
                    alerteId TEXT NOT NULL PRIMARY KEY,
                    parcelleId TEXT NOT NULL,
                    sessionId TEXT,
                    codePathogene TEXT NOT NULL,
                    nomPathogene TEXT NOT NULL,
                    niveauRisque TEXT NOT NULL,
                    nbTigesAtteintes INTEGER,
                    pctTigesAtteintes REAL,
                    essencesCiblesJson TEXT,
                    symptomesObservesJson TEXT,
                    recommandationsJson TEXT,
                    isOrganismeReglemente INTEGER NOT NULL DEFAULT 0,
                    dateDetection INTEGER NOT NULL,
                    isAlerteDsf INTEGER NOT NULL DEFAULT 0,
                    remarques TEXT,
                    FOREIGN KEY(parcelleId) REFERENCES parcelles(parcelleId) ON DELETE CASCADE,
                    FOREIGN KEY(sessionId) REFERENCES inventaire_sessions(sessionId) ON DELETE SET NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_alerte_parcelleId ON alertes_sanitaires(parcelleId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_alerte_sessionId ON alertes_sanitaires(sessionId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_alerte_niveauRisque ON alertes_sanitaires(niveauRisque)")

            // ── Table fertilite_essence_ser ───────────────────────────────────────
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS fertilite_essence_ser (
                    fertiliteId TEXT NOT NULL PRIMARY KEY,
                    essenceCode TEXT NOT NULL,
                    codeSer TEXT NOT NULL,
                    nomSer TEXT,
                    classeStation INTEGER NOT NULL,
                    hoRef100Ans REAL,
                    gMaxRef REAL,
                    accroissementRefM3HaAn REAL,
                    conditionsRequisesJson TEXT,
                    itineraireSylvicoleJson TEXT,
                    sourceGuide TEXT NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_fertilite_essence_ser ON fertilite_essence_ser(essenceCode, codeSer)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_fertilite_classeStation ON fertilite_essence_ser(classeStation)")

            // ── Table projections_climatiques_ser ─────────────────────────────────
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS projections_climatiques_ser (
                    projId TEXT NOT NULL PRIMARY KEY,
                    codeSer TEXT NOT NULL,
                    scenario TEXT NOT NULL,
                    horizon INTEGER NOT NULL,
                    deltaTMoyC REAL NOT NULL,
                    deltaTEteC REAL NOT NULL,
                    deltaPMmAn REAL NOT NULL,
                    deltaPEteMm REAL NOT NULL,
                    nbJoursChaudsSup INTEGER,
                    speiDelta REAL,
                    sourceGiec TEXT NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_proj_ser_scenario ON projections_climatiques_ser(codeSer, scenario, horizon)")

            // ── Extensions table parcelles ────────────────────────────────────────
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN foretId TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN codeInseeCommune TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN nomCommune TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN sectionCadastrale TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN numeroCadastral TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN contenanceCadastraleHa REAL") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN geometrieIgnWkt TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN natureCadastraleCode TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN localisationMode TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN codeSer TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE parcelles ADD COLUMN nomSer TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("CREATE INDEX IF NOT EXISTS index_parcelles_foretId ON parcelles(foretId)") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("CREATE INDEX IF NOT EXISTS index_parcelles_codeInsee ON parcelles(codeInseeCommune)") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }

            // ── Extensions table placettes ────────────────────────────────────────
            try { db.execSQL("ALTER TABLE placettes ADD COLUMN sessionId TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE placettes ADD COLUMN typeReleve TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE placettes ADD COLUMN referenceGpsWkt TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE placettes ADD COLUMN azimutRef REAL") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("CREATE INDEX IF NOT EXISTS index_placettes_sessionId ON placettes(sessionId)") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }

            // ── Extensions table tiges ────────────────────────────────────────────
            try { db.execSQL("ALTER TABLE tiges ADD COLUMN sessionId TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE tiges ADD COLUMN classeKraft INTEGER") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE tiges ADD COLUMN etatSanitaire TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE tiges ADD COLUMN vigueur TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE tiges ADD COLUMN origine TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE tiges ADD COLUMN typeCoupe TEXT") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE tiges ADD COLUMN biomasseFusTonnes REAL") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE tiges ADD COLUMN carboneFusTonnes REAL") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE tiges ADD COLUMN coefficientElancement REAL") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE tiges ADD COLUMN houppierM REAL") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE tiges ADD COLUMN houppierPct REAL") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("ALTER TABLE tiges ADD COLUMN isTigeHabitat INTEGER NOT NULL DEFAULT 0") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
            try { db.execSQL("CREATE INDEX IF NOT EXISTS index_tiges_sessionId ON tiges(sessionId)") } catch (e: Throwable) { Log.w(TAG, "Migration ALTER TABLE ignorée (colonne déjà existante ?): ${e.message}") }
        }
    }

    /** Liste ordonnée de toutes les migrations pour Room.databaseBuilder */
    val ALL = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
        MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
        MIGRATION_13_14, MIGRATION_14_15, Migration15to26, MIGRATION_26_27,
        com.forestry.counter.data.local.migration.MIGRATION_27_28,
        com.forestry.counter.data.local.migration.MIGRATION_28_29
    )
}
