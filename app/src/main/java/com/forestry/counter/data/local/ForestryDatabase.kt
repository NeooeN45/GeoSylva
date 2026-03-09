package com.forestry.counter.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.forestry.counter.data.local.dao.CounterDao
import com.forestry.counter.data.local.dao.FormulaDao
import com.forestry.counter.data.local.dao.GroupDao
import com.forestry.counter.data.local.dao.GroupVariableDao
import com.forestry.counter.data.local.dao.ParcelleDao
import com.forestry.counter.data.local.dao.PlacetteDao
import com.forestry.counter.data.local.dao.EssenceDao
import com.forestry.counter.data.local.dao.TigeDao
import com.forestry.counter.data.local.dao.IbpEvaluationDao
import com.forestry.counter.data.local.dao.ParameterDao
import com.forestry.counter.data.local.dao.RipisylveDao
import com.forestry.counter.data.local.entity.CounterEntity
import com.forestry.counter.data.local.entity.IbpEvaluationEntity
import com.forestry.counter.data.local.entity.RipisylveEntity
import com.forestry.counter.data.local.entity.FormulaEntity
import com.forestry.counter.data.local.entity.GroupEntity
import com.forestry.counter.data.local.entity.GroupVariableEntity
import com.forestry.counter.data.local.entity.ParcelleEntity
import com.forestry.counter.data.local.entity.PlacetteEntity
import com.forestry.counter.data.local.entity.EssenceEntity
import com.forestry.counter.data.local.entity.TigeEntity
import com.forestry.counter.data.local.entity.ParameterEntity

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
        RipisylveEntity::class
    ],
    version = 16,
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
    abstract fun ripisylveDao(): RipisylveDao

    companion object {
        const val DATABASE_NAME = "forestry_counter.db"
    }
}
