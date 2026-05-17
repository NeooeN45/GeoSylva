package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.forestry.counter.data.local.entity.EntityRelationEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO pour les relations entre entités.
 * Permet de gérer les relations complexes entre différentes entités forestières.
 */
@Dao
interface EntityRelationDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelation(relation: EntityRelationEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelations(relations: List<EntityRelationEntity>)
    
    @Update
    suspend fun updateRelation(relation: EntityRelationEntity)
    
    @Query("SELECT * FROM entity_relations WHERE relationId = :relationId")
    suspend fun getRelationById(relationId: String): EntityRelationEntity?
    
    @Query("SELECT * FROM entity_relations WHERE sourceEntityId = :entityId")
    suspend fun getRelationsBySourceEntity(entityId: String): List<EntityRelationEntity>
    
    @Query("SELECT * FROM entity_relations WHERE targetEntityId = :entityId")
    suspend fun getRelationsByTargetEntity(entityId: String): List<EntityRelationEntity>
    
    @Query("SELECT * FROM entity_relations WHERE sourceEntityId = :entityId OR targetEntityId = :entityId")
    suspend fun getRelationsByEntity(entityId: String): List<EntityRelationEntity>
    
    @Query("SELECT * FROM entity_relations WHERE relationType = :type")
    suspend fun getRelationsByType(type: String): List<EntityRelationEntity>
    
    @Query("SELECT * FROM entity_relations WHERE sourceEntityType = :entityType")
    suspend fun getRelationsBySourceType(entityType: String): List<EntityRelationEntity>
    
    @Query("SELECT * FROM entity_relations WHERE targetEntityType = :entityType")
    suspend fun getRelationsByTargetType(entityType: String): List<EntityRelationEntity>
    
    @Query("SELECT * FROM entity_relations WHERE relationStrength >= :minStrength")
    suspend fun getStrongRelations(minStrength: Double): List<EntityRelationEntity>
    
    @Query("SELECT * FROM entity_relations WHERE isActive = 1")
    suspend fun getActiveRelations(): List<EntityRelationEntity>
    
    @Query("SELECT * FROM entity_relations WHERE validFrom <= :currentTime AND (validUntil IS NULL OR validUntil > :currentTime)")
    suspend fun getValidRelations(currentTime: Long): List<EntityRelationEntity>
    
    @Query("DELETE FROM entity_relations WHERE relationId = :relationId")
    suspend fun deleteRelation(relationId: String)
    
    @Query("DELETE FROM entity_relations WHERE sourceEntityId = :entityId OR targetEntityId = :entityId")
    suspend fun deleteRelationsByEntity(entityId: String)
    
    @Query("DELETE FROM entity_relations WHERE validUntil < :currentTime")
    suspend fun deleteExpiredRelations(currentTime: Long)
    
    @Query("UPDATE entity_relations SET isActive = 0 WHERE relationId = :relationId")
    suspend fun deactivateRelation(relationId: String)
    
    @Query("SELECT COUNT(*) FROM entity_relations")
    suspend fun getRelationCount(): Int
    
    @Query("SELECT * FROM entity_relations ORDER BY relationStrength DESC LIMIT :limit")
    suspend fun getTopRelations(limit: Int): List<EntityRelationEntity>
}
