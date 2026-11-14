package com.forestry.counter.data.local.dao

import androidx.room.*
import com.forestry.counter.data.local.entity.CartographicLayerEntity
import com.forestry.counter.data.local.entity.CartographicLayerOperationEntity

@Dao
interface CartographicLayerDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: CartographicLayerEntity)
    @Query("SELECT * FROM cartographic_layers WHERE id = :id")
    suspend fun byId(id: String): CartographicLayerEntity?
    @Query("SELECT * FROM cartographic_layers WHERE authorityKey = :authorityKey AND deletedAt IS NULL ORDER BY id")
    suspend fun inScope(authorityKey: String): List<CartographicLayerEntity>
    /** Update only; a delayed response can never recreate a deleted layer. */
    @Query("UPDATE cartographic_layers SET payloadJson = :payloadJson, payloadSha256 = :sha256, version = :newVersion WHERE id = :id AND authorityKey = :authorityKey AND version = :expectedVersion AND deletedAt IS NULL")
    suspend fun update(id: String, authorityKey: String, expectedVersion: Long, payloadJson: String, sha256: String, newVersion: Long): Int
    @Query("UPDATE cartographic_layers SET deletedAt = :deletedAt, version = version + 1 WHERE id = :id AND authorityKey = :authorityKey AND version = :expectedVersion AND deletedAt IS NULL")
    suspend fun delete(id: String, authorityKey: String, expectedVersion: Long, deletedAt: Long): Int
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOperation(row: CartographicLayerOperationEntity)
    @Query("SELECT * FROM cartographic_layer_operations WHERE id = :id")
    suspend fun operation(id: String): CartographicLayerOperationEntity?
    @Query("SELECT id, version, deletedAt FROM cartographic_layers ORDER BY id")
    fun observeVersions(): kotlinx.coroutines.flow.Flow<List<CartographicLayerVersion>>
    @Query("DELETE FROM cartographic_layer_operations")
    suspend fun deleteAllOperations()
    @Query("DELETE FROM cartographic_layers")
    suspend fun deleteAll()
}

data class CartographicLayerVersion(val id: String, val version: Long, val deletedAt: Long?)
