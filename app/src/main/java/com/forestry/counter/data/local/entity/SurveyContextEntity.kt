package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.forestry.counter.domain.model.SurveyContextCodec
import com.forestry.counter.domain.model.SurveyContextRecord

/** Table additionnelle : aucune ligne ni déclaration fabriquée pour les anciennes sessions. */
@Entity(
    tableName = "survey_contexts",
    foreignKeys = [
        ForeignKey(
            entity = InventaireSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(name = "index_survey_contexts_sessionId", value = ["sessionId"])],
)
data class SurveyContextEntity(
    @PrimaryKey val sessionId: String,
    val revision: Int,
    val payloadJson: String,
    val sealedPayloadJson: String? = null,
    val createdAt: Long,
    val createdBy: String,
    val updatedAt: Long,
    val updatedBy: String,
    /** La provenance survit à la suppression de la révision précédente ; aucune clé étrangère ici. */
    val copiedFromSessionId: String? = null,
) {
    init {
        require(revision > 0 && createdAt >= 0L && updatedAt >= 0L) { "SURVEY_CONTEXT_INVALID_REVISION" }
    }

    fun toRecord(): SurveyContextRecord {
        require(sealedPayloadJson == null || sealedPayloadJson == payloadJson) {
            "SURVEY_CONTEXT_SEALED_PAYLOAD_MISMATCH"
        }
        return SurveyContextRecord(
            sessionId = sessionId,
            revision = revision,
            draft = SurveyContextCodec.decode(sealedPayloadJson ?: payloadJson),
            createdAt = createdAt,
            createdBy = createdBy,
            updatedAt = updatedAt,
            updatedBy = updatedBy,
            copiedFromSessionId = copiedFromSessionId,
            isSealed = sealedPayloadJson != null,
        )
    }
}
