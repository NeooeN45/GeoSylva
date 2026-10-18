package com.forestry.counter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.forestry.counter.data.local.entity.ObservationQualificationEntity

@Dao
interface ObservationQualificationDao {
    @Query("SELECT * FROM observation_qualifications WHERE tigeId = :tigeId")
    suspend fun getByTige(tigeId: String): ObservationQualificationEntity?

    /** Inclut les tiges supprimées logiquement pour que l'appelant contrôle leur état. */
    @Query("""
        SELECT qualifications.* FROM observation_qualifications AS qualifications
        INNER JOIN tiges ON tiges.tigeId = qualifications.tigeId
        WHERE tiges.sessionId = :sessionId ORDER BY qualifications.tigeId
    """)
    suspend fun getBySession(sessionId: String): List<ObservationQualificationEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(qualification: ObservationQualificationEntity)

    /** Une révision périmée, maximale ou scellée ne remplace aucune déclaration. */
    @Query("""
        UPDATE observation_qualifications
        SET revision = revision + 1, payloadJson = :payloadJson,
            updatedAt = :updatedAt, updatedBy = :updatedBy
        WHERE tigeId = :tigeId AND revision = :expectedRevision
          AND revision < 2147483647 AND sealedPayloadJson IS NULL
    """)
    suspend fun compareAndSet(
        tigeId: String,
        expectedRevision: Int,
        payloadJson: String,
        updatedAt: Long,
        updatedBy: String,
    ): Int

    /** Capture les octets sauvegardés dans la transaction de clôture, sans réencodage. */
    @Query("""
        UPDATE observation_qualifications SET sealedPayloadJson = payloadJson
        WHERE tigeId = :tigeId AND revision = :expectedRevision
          AND sealedPayloadJson IS NULL
    """)
    suspend fun seal(tigeId: String, expectedRevision: Int): Int
}
