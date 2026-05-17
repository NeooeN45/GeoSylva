package com.forestry.counter.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
// import com.forestry.counter.security.DatabaseEncryptionService — désactivé (SQLCipher commenté)
import com.forestry.counter.data.local.dao.AlerteSanitaireDao
import com.forestry.counter.data.local.dao.ArbreHabitatDao
import com.forestry.counter.data.local.dao.CounterDao
import com.forestry.counter.data.local.dao.DiagnosticSylvicoleDao
import com.forestry.counter.data.local.dao.FertiliteEssenceSerDao
import com.forestry.counter.data.local.dao.ForetDao
import com.forestry.counter.data.local.dao.FormulaDao
import com.forestry.counter.data.local.dao.GroupDao
import com.forestry.counter.data.local.dao.GroupVariableDao
import com.forestry.counter.data.local.dao.InventaireSessionDao
import com.forestry.counter.data.local.dao.ObservationFloreDao
import com.forestry.counter.data.local.dao.ParcelleDao
import com.forestry.counter.data.local.dao.PlacetteDao
import com.forestry.counter.data.local.dao.EssenceDao
import com.forestry.counter.data.local.dao.ProjectionClimatiqueSerDao
import com.forestry.counter.data.local.dao.StationEnvironnementaleDao
import com.forestry.counter.data.local.dao.TigeDao
import com.forestry.counter.data.local.dao.IbpEvaluationDao
import com.forestry.counter.data.local.dao.ParameterDao
import com.forestry.counter.data.local.dao.ValeurFonciereDao
import com.forestry.counter.data.local.entity.AlerteSanitaireEntity
import com.forestry.counter.data.local.entity.ArbreHabitatEntity
import com.forestry.counter.data.local.entity.CounterEntity
import com.forestry.counter.data.local.entity.DiagnosticSylvicoleEntity
import com.forestry.counter.data.local.entity.FertiliteEssenceSerEntity
import com.forestry.counter.data.local.entity.ForetEntity
import com.forestry.counter.data.local.entity.IbpEvaluationEntity
import com.forestry.counter.data.local.entity.InventaireSessionEntity
import com.forestry.counter.data.local.entity.FormulaEntity
import com.forestry.counter.data.local.entity.GroupEntity
import com.forestry.counter.data.local.entity.GroupVariableEntity
import com.forestry.counter.data.local.entity.ObservationFloreEntity
import com.forestry.counter.data.local.entity.ParcelleEntity
import com.forestry.counter.data.local.entity.PlacetteEntity
import com.forestry.counter.data.local.entity.ProjectionClimatiqueSerEntity
import com.forestry.counter.data.local.entity.StationEnvironnementaleEntity
import com.forestry.counter.data.local.entity.EssenceEntity
import com.forestry.counter.data.local.entity.TigeEntity
import com.forestry.counter.data.local.entity.ParameterEntity
import com.forestry.counter.data.local.entity.ValeurFonciereEntity
import com.forestry.counter.data.local.dao.FloraFtsDao
import com.forestry.counter.data.local.entity.FloraFtsEntity
import com.forestry.counter.data.local.entity.GpsContextCacheEntity
import com.forestry.counter.data.local.dao.RipisylveDao
import com.forestry.counter.data.local.entity.RipisylveEntity
import com.forestry.counter.data.local.dao.StationDao
import com.forestry.counter.data.local.entity.StationEntity

@Database(
    entities = [
        GroupEntity::class,
        CounterEntity::class,
        FormulaEntity::class,
        GroupVariableEntity::class,
        ParcelleEntity::class,
        PlacetteEntity::class,
        EssenceEntity::class,
        TigeEntity::class,
        ParameterEntity::class,
        IbpEvaluationEntity::class,
        ForetEntity::class,
        InventaireSessionEntity::class,
        StationEnvironnementaleEntity::class,
        DiagnosticSylvicoleEntity::class,
        ObservationFloreEntity::class,
        ArbreHabitatEntity::class,
        ValeurFonciereEntity::class,
        AlerteSanitaireEntity::class,
        FertiliteEssenceSerEntity::class,
        ProjectionClimatiqueSerEntity::class,
        FloraFtsEntity::class,
        GpsContextCacheEntity::class,
        RipisylveEntity::class,
        StationEntity::class
        // Temporairement désactivé pour résoudre KSP
        // DataCorrelationEntity::class,
        // DataInterpretationEntity::class,
        // EntityRelationEntity::class,
        // AdvancedCalculationEntity::class
    ],
    version = 29,
    exportSchema = true
)
abstract class ForestryDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun counterDao(): CounterDao
    abstract fun formulaDao(): FormulaDao
    abstract fun groupVariableDao(): GroupVariableDao
    abstract fun parcelleDao(): ParcelleDao
    abstract fun placetteDao(): PlacetteDao
    abstract fun essenceDao(): EssenceDao
    abstract fun tigeDao(): TigeDao
    abstract fun parameterDao(): ParameterDao
    abstract fun ibpEvaluationDao(): IbpEvaluationDao
    abstract fun foretDao(): ForetDao
    abstract fun inventaireSessionDao(): InventaireSessionDao
    abstract fun stationEnvironnementaleDao(): StationEnvironnementaleDao
    abstract fun diagnosticSylvicoleDao(): DiagnosticSylvicoleDao
    abstract fun observationFloreDao(): ObservationFloreDao
    abstract fun arbreHabitatDao(): ArbreHabitatDao
    abstract fun valeurFonciereDao(): ValeurFonciereDao
    abstract fun alerteSanitaireDao(): AlerteSanitaireDao
    abstract fun fertiliteEssenceSerDao(): FertiliteEssenceSerDao
    abstract fun projectionClimatiqueSerDao(): ProjectionClimatiqueSerDao
    abstract fun floraFtsDao(): FloraFtsDao
    abstract fun ripisylveDao(): RipisylveDao
    abstract fun stationDao(): StationDao
    // DataCorrelationDao, DataInterpretationDao, EntityRelationDao, AdvancedCalculationDao
    // temporairement désactivés — entités commentées en attente résolution KSP

    companion object {
        const val DATABASE_NAME = "forestry_counter.db"
        
        /**
         * Crée une instance de la base de données chiffrée avec SQLCipher.
         * 
         * @param context Contexte de l'application
         * @param migrations Liste des migrations à appliquer
         * @return Instance de ForestryDatabase chiffrée
         */
        // createEncryptedDatabase désactivé — SQLCipher commenté dans DatabaseEncryptionService
        // fun createEncryptedDatabase(context: Context, migrations: Array<Migration>): ForestryDatabase
        
        /**
         * Crée une instance de la base de données non chiffrée (pour compatibilité).
         * @deprecated Utiliser createEncryptedDatabase() pour la sécurité
         */
        @Deprecated("Utiliser createEncryptedDatabase() pour la sécurité")
        fun createDatabase(context: Context, migrations: Array<Migration>): ForestryDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                ForestryDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(*migrations)
                .build()
        }
    }
}
