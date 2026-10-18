package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.forestry.counter.data.local.entity.SurveyContextEntity

@Dao
interface SurveyContextDao {
    @Query("SELECT * FROM survey_contexts WHERE sessionId = :sessionId")
    suspend fun getBySession(sessionId: String): SurveyContextEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(context: SurveyContextEntity)

    /** Une révision périmée ou scellée retourne zéro sans remplacer la déclaration sauvegardée. */
    @Query("""
        UPDATE survey_contexts
        SET revision = revision + 1, payloadJson = :payloadJson,
            updatedAt = :updatedAt, updatedBy = :updatedBy
        WHERE sessionId = :sessionId AND revision = :expectedRevision
          AND revision < 2147483647 AND sealedPayloadJson IS NULL
    """)
    suspend fun compareAndSet(
        sessionId: String,
        expectedRevision: Int,
        payloadJson: String,
        updatedAt: Long,
        updatedBy: String,
    ): Int

    /** Capture atomique des octets stockés, sans réencodage du brouillon lors de la clôture. */
    @Query("""
        UPDATE survey_contexts SET sealedPayloadJson = payloadJson
        WHERE sessionId = :sessionId AND revision = :expectedRevision
          AND sealedPayloadJson IS NULL
    """)
    suspend fun seal(sessionId: String, expectedRevision: Int): Int
}
