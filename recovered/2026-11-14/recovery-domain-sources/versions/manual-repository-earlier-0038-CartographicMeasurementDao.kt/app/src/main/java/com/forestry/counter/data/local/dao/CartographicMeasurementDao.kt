package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.forestry.counter.data.local.entity.CartographicMeasurementEntity
import com.forestry.counter.data.local.entity.CartographicStoreIdentityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CartographicMeasurementDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: CartographicMeasurementEntity)

    @Query("SELECT * FROM cartographic_measurements WHERE id = :id")
    suspend fun byId(id: String): CartographicMeasurementEntity?

    @Query("SELECT * FROM cartographic_measurements WHERE ownerKey = :ownerKey AND scopeKind = :scopeKind AND scopeId = :scopeId ORDER BY recordedAt DESC, id ASC")
    suspend fun inScope(ownerKey: String, scopeKind: String, scopeId: String): List<CartographicMeasurementEntity>

    /** Authority wake-up signals only; callers revalidate the original captured access. */
    @Query("SELECT placetteId, parcelleOwnerId, deletedAt FROM placettes ORDER BY placetteId")
    fun observePlotParents(): Flow<List<CartographicPlotParent>>

    @Query("DELETE FROM cartographic_measurements")
    suspend fun deleteAll()

    @Query("SELECT * FROM cartographic_store_identity WHERE singletonId = 1")
    suspend fun storeIdentity(): CartographicStoreIdentityEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStoreIdentity(row: CartographicStoreIdentityEntity)

    @Query("DELETE FROM cartographic_store_identity")
    suspend fun deleteStoreIdentity()
}

data class CartographicPlotParent(val placetteId: String, val parcelleOwnerId: String, val deletedAt: Long?)
