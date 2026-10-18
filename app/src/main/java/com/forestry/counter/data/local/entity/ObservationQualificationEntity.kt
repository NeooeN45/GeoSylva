package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.forestry.counter.domain.model.ObservationQualificationCodec
import com.forestry.counter.domain.model.ObservationQualificationRecord

/** Déclarations additionnelles liées à une observation, sans qualifier les anciennes tiges. */
@Entity(
    tableName = "observation_qualifications",
    foreignKeys = [
        ForeignKey(
            entity = TigeEntity::class,
            parentColumns = ["tigeId"],
            childColumns = ["tigeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(name = "index_observation_qualifications_tigeId", value = ["tigeId"])],
)
data class ObservationQualificationEntity(
    @PrimaryKey val tigeId: String,
    val revision: Int,
    val payloadJson: String,
    val sealedPayloadJson: String? = null,
    val createdAt: Long,
    val createdBy: String,
    val updatedAt: Long,
    val updatedBy: String,
) {
    init {
        require(revision > 0 && createdAt >= 0L && updatedAt >= 0L) {
            "OBSERVATION_QUALIFICATION_INVALID_REVISION"
        }
    }

    /** Refuse une divergence du gel ou de l'observation au lieu de réinterpréter les octets. */
    fun toRecord(): ObservationQualificationRecord {
        require(sealedPayloadJson == null || sealedPayloadJson == payloadJson) {
            "OBSERVATION_QUALIFICATION_SEALED_PAYLOAD_MISMATCH"
        }
        val payload = ObservationQualificationCodec.decode(sealedPayloadJson ?: payloadJson)
        require(tigeId == payload.basis.tigeId) { "OBSERVATION_QUALIFICATION_TIGE_MISMATCH" }
        return ObservationQualificationRecord(
            tigeId = tigeId,
            revision = revision,
            payload = payload,
            createdAt = createdAt,
            createdBy = createdBy,
            updatedAt = updatedAt,
            updatedBy = updatedBy,
            isSealed = sealedPayloadJson != null,
        )
    }
}
