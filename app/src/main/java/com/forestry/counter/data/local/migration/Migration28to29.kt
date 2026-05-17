package com.forestry.counter.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS station_diagnostics (
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
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS ripisylve_observation (
                id TEXT NOT NULL PRIMARY KEY,
                parcelleId TEXT NOT NULL,
                observerName TEXT NOT NULL,
                observationDate INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                isDraft INTEGER NOT NULL,
                photosJson TEXT NOT NULL,
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
                globalNotes TEXT NOT NULL
            )
        """.trimIndent())
    }
}
