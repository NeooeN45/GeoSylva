package com.forestry.counter.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
import com.forestry.counter.data.local.dao.ParcelSyncDao
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
import com.forestry.counter.data.local.entity.ParcelSyncEntity
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
import com.forestry.counter.data.local.dao.DataCorrelationDao
import com.forestry.counter.data.local.dao.DataInterpretationDao
import com.forestry.counter.data.local.dao.EntityRelationDao
import com.forestry.counter.data.local.dao.AdvancedCalculationDao
import com.forestry.counter.data.local.entity.StationEntity
import com.forestry.counter.data.local.entity.DataCorrelationEntity
import com.forestry.counter.data.local.entity.DataInterpretationEntity
import com.forestry.counter.data.local.entity.EntityRelationEntity
import com.forestry.counter.data.local.entity.AdvancedCalculationEntity
import com.forestry.counter.data.local.entity.PermanentTreeEntity
import com.forestry.counter.data.local.entity.TreeObservationEntity
import com.forestry.counter.data.local.entity.MeasurementEntity
import com.forestry.counter.data.local.entity.EvidenceEntity
import com.forestry.counter.data.local.entity.CalculationRunEntity
import com.forestry.counter.data.local.entity.UnitEntity
import com.forestry.counter.data.local.entity.EventLogEntity
import com.forestry.counter.data.local.entity.ProjectEntity
import com.forestry.counter.data.local.entity.ProjectForestCrossRef
import com.forestry.counter.data.local.dao.PermanentTreeDao
import com.forestry.counter.data.local.dao.TreeObservationDao
import com.forestry.counter.data.local.dao.MeasurementDao
import com.forestry.counter.data.local.dao.EvidenceDao
import com.forestry.counter.data.local.dao.CalculationRunDao
import com.forestry.counter.data.local.dao.UnitDao
import com.forestry.counter.data.local.dao.EventLogDao
import com.forestry.counter.data.local.dao.ProjectDao

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
        com.forestry.counter.data.local.entity.SurveyContextEntity::class,
        com.forestry.counter.data.local.entity.ObservationQualificationEntity::class,
        com.forestry.counter.data.local.entity.WoodDepotEntity::class,
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
        StationEntity::class,
        DataCorrelationEntity::class,
        DataInterpretationEntity::class,
        EntityRelationEntity::class,
        AdvancedCalculationEntity::class,
        ParcelSyncEntity::class,
        com.forestry.counter.data.local.entity.ParcelSyncOperationEntity::class,
        com.forestry.counter.data.local.entity.HierarchySyncAssociationEntity::class,
        com.forestry.counter.data.local.entity.HierarchySyncOperationEntity::class,
        // Lot 1 — Contrat universel de données (GEOSYLVA-003 §7.6)
        PermanentTreeEntity::class,
        TreeObservationEntity::class,
        MeasurementEntity::class,
        EvidenceEntity::class,
        CalculationRunEntity::class,
        UnitEntity::class,
        EventLogEntity::class,
        ProjectEntity::class,
        com.forestry.counter.data.local.entity.WoodObjectEntity::class,
        com.forestry.counter.data.local.entity.WoodObservationEntity::class,
        com.forestry.counter.data.local.entity.WoodEffectiveObservationEntity::class,
        com.forestry.counter.data.local.entity.WoodCommandEntity::class,
        com.forestry.counter.data.local.entity.FieldWorksiteEntity::class,
        com.forestry.counter.data.local.entity.FieldSiteLinkEntity::class,
        com.forestry.counter.data.local.entity.FieldEntryEntity::class,
        com.forestry.counter.data.local.entity.FieldDraftEntity::class,
        com.forestry.counter.data.local.entity.FieldPhotoEntity::class,
        com.forestry.counter.data.local.entity.FieldPhotoChunkEntity::class,
        com.forestry.counter.data.local.entity.FieldReceiptEntity::class,
        com.forestry.counter.data.local.entity.FieldReceiptVersionEntity::class,
        com.forestry.counter.data.local.entity.FieldReceiptCreditEntity::class,
        com.forestry.counter.data.local.entity.FieldReceiptCommandEntity::class,

        ProjectForestCrossRef::class
    ],
    version = 46,
    exportSchema = true
)
@TypeConverters(EnumConverters::class)
abstract class ForestryDatabase : RoomDatabase() {
    abstract fun receiptDao(): com.forestry.counter.data.local.dao.ReceiptDao
    abstract fun fieldDao(): com.forestry.counter.data.local.dao.FieldDao
    val woodDataGate = com.forestry.counter.data.repository.WoodDataAccessGate()
    abstract fun woodObjectDao(): com.forestry.counter.data.local.dao.WoodObjectDao
    abstract fun woodDepotDao(): com.forestry.counter.data.local.dao.WoodDepotDao
    abstract fun groupDao(): GroupDao
    abstract fun counterDao(): CounterDao
    abstract fun formulaDao(): FormulaDao
    abstract fun groupVariableDao(): GroupVariableDao
    abstract fun parcelleDao(): ParcelleDao
    abstract fun parcelSyncDao(): ParcelSyncDao
    abstract fun parcelSyncOperationDao(): com.forestry.counter.data.local.dao.ParcelSyncOperationDao
    abstract fun hierarchySyncDao(): com.forestry.counter.data.local.dao.HierarchySyncDao
    abstract fun placetteDao(): PlacetteDao
    abstract fun essenceDao(): EssenceDao
    abstract fun tigeDao(): TigeDao
    abstract fun parameterDao(): ParameterDao
    abstract fun ibpEvaluationDao(): IbpEvaluationDao
    abstract fun foretDao(): ForetDao
    abstract fun inventaireSessionDao(): InventaireSessionDao
    abstract fun surveyContextDao(): com.forestry.counter.data.local.dao.SurveyContextDao
    abstract fun observationQualificationDao(): com.forestry.counter.data.local.dao.ObservationQualificationDao
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
    abstract fun dataCorrelationDao(): DataCorrelationDao
    abstract fun dataInterpretationDao(): DataInterpretationDao
    abstract fun entityRelationDao(): EntityRelationDao
    abstract fun advancedCalculationDao(): AdvancedCalculationDao
    // Lot 1 — Contrat universel de données
    abstract fun permanentTreeDao(): PermanentTreeDao
    abstract fun treeObservationDao(): TreeObservationDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun evidenceDao(): EvidenceDao
    abstract fun calculationRunDao(): CalculationRunDao
    abstract fun unitDao(): UnitDao
    abstract fun eventLogDao(): EventLogDao
    abstract fun projectDao(): ProjectDao

    companion object {
        const val DATABASE_NAME = "forestry_counter.db"

        /**
         * Crée une instance chiffrée de la base de données via SQLCipher.
         * La clé est gérée par DatabaseEncryptionService (Android Keystore).
         *
         * RGPD compliance: Article 32 — security of processing.
         * Personal data (parcel ownership, GPS tracks) must be encrypted at rest.
         *
         * @param context Contexte de l'application
         * @param migrations Liste des migrations à appliquer
         * @param passphrase Clé SQLCipher (depuis DatabaseEncryptionService)
         * @return Instance chiffrée de ForestryDatabase
         */
        fun createDatabase(
            context: Context,
            migrations: Array<Migration>,
            passphrase: ByteArray
        ): ForestryDatabase {
            // The factory owns a copy until the first lazy opening and preserves legacy data.
            val factory = com.forestry.counter.security.SecureSqlCipherOpenHelperFactory(passphrase)
            return Room.databaseBuilder(
                context.applicationContext,
                ForestryDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(factory)
                .addMigrations(*migrations)
                .build()
        }
    }
}
