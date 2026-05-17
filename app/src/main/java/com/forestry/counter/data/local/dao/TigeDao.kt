package com.forestry.counter.data.local.dao

import androidx.room.*
import com.forestry.counter.data.local.entity.TigeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TigeDao {
    @Query("SELECT * FROM tiges ORDER BY timestamp ASC")
    fun getAllTiges(): Flow<List<TigeEntity>>
    @Query("SELECT * FROM tiges WHERE parcelleOwnerId = :parcelleId ORDER BY timestamp ASC")
    fun getTigesByParcelle(parcelleId: String): Flow<List<TigeEntity>>

    @Query("SELECT * FROM tiges WHERE placetteOwnerId = :placetteId ORDER BY timestamp ASC")
    fun getTigesByPlacette(placetteId: String): Flow<List<TigeEntity>>

    @Query("SELECT * FROM tiges WHERE tigeId = :id")
    fun getTigeByIdFlow(id: String): Flow<TigeEntity?>

    @Query("SELECT * FROM tiges WHERE tigeId = :id")
    suspend fun getTigeById(id: String): TigeEntity?

    @Query("""
        SELECT * FROM tiges 
        WHERE parcelleOwnerId = :parcelleId 
          AND ((:placetteId IS NULL AND placetteOwnerId IS NULL) OR placetteOwnerId = :placetteId)
          AND essenceCode = :essenceCode 
          AND diamCm = :diamCm 
        ORDER BY timestamp DESC 
        LIMIT 1
    """)
    suspend fun getLatestMatching(
        parcelleId: String,
        placetteId: String?,
        essenceCode: String,
        diamCm: Double
    ): TigeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTige(entity: TigeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTiges(entities: List<TigeEntity>)

    @Update
    suspend fun updateTige(entity: TigeEntity)

    @Delete
    suspend fun deleteTige(entity: TigeEntity)

    @Query("DELETE FROM tiges WHERE tigeId = :id")
    suspend fun deleteTigeById(id: String)

    @Query("DELETE FROM tiges WHERE parcelleOwnerId = :parcelleId")
    suspend fun deleteTigesByParcelle(parcelleId: String)

    @Query("DELETE FROM tiges WHERE placetteOwnerId = :placetteId")
    suspend fun deleteTigesByPlacette(placetteId: String)

    @Query("DELETE FROM tiges WHERE placetteOwnerId = :placetteId AND essenceCode = :essenceCode")
    suspend fun deleteTigesByPlacetteAndEssence(placetteId: String, essenceCode: String)

    @Query("""
        UPDATE tiges
        SET classeKraft = :classeKraft,
            etatSanitaire = :etatSanitaire,
            vigueur = :vigueur,
            origine = :origine,
            isTigeHabitat = :isTigeHabitat
        WHERE tigeId = :tigeId
    """)
    suspend fun updateTigeSylviculture(
        tigeId: String,
        classeKraft: Int?,
        etatSanitaire: String?,
        vigueur: String?,
        origine: String?,
        isTigeHabitat: Boolean
    ): Int
}
