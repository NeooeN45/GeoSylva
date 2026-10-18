package com.forestry.counter.domain.wood

import java.util.Collections
import java.util.UUID

/**
 * Immutable lineage projection of an observation, not a persistence entity.
 * A root is an independent measurement (revision 1, no correction target/reason).
 * A correction adds a new ID and keeps its predecessor's exact payload intact.
 */
data class WoodObservationRecord(
    val observation: RawWoodObservation,
    val rootObservationId: UUID,
    val observationRevision: Int,
    val correctionOfObservationId: UUID? = null,
    val correctionReason: String? = null,
) {
    val observationId: UUID get() = observation.observationId
}

/**
 * Pure BOIS-01 rules for one physical object's observations and explicit selection.
 *
 * The repository must load this projection under canonical owner/scope access and
 * recheck parents, archive state, identity/epoch and SQL CAS in its transaction.
 * These in-memory revision checks grant no access and provide no concurrency lock.
 * Actor, commit time, durable command receipts and selection audit belong there.
 * No operation creates a stock movement, commercial quantity or payment.
 */
class WoodObservationHistory private constructor(
    val physicalObjectId: UUID,
    val kind: WoodObjectKind,
    val objectRevision: Int,
    records: List<WoodObservationRecord>,
    val effectiveObservationId: UUID?,
) {
    val records: List<WoodObservationRecord> = Collections.unmodifiableList(ArrayList(records))
    private val byId = this.records.associateBy { it.observationId }
    private val correctedIds = this.records.mapNotNull { it.correctionOfObservationId }.toSet()

    val effectiveSelection: WoodEffectiveSelection
        get() = effectiveObservationId?.let { id ->
            val selected = byId.getValue(id)
            if (id in correctedIds) WoodEffectiveSelection.Superseded(selected)
            else WoodEffectiveSelection.Current(selected)
        } ?: WoodEffectiveSelection.None

    /** At most one candidate for CURRENT_STATE; calculation qualification is a separate step. */
    val currentStateObservation: WoodObservationRecord?
        get() = (effectiveSelection as? WoodEffectiveSelection.Current)?.record

    /** Remeasurement is a new independent root, even when values are identical. */
    fun addObservation(
        observation: RawWoodObservation,
        expectedObjectRevision: Int,
        makeEffective: Boolean,
    ): WoodHistoryResult {
        checkObjectRevision(expectedObjectRevision)?.let { return it }
        checkNewObservation(observation)?.let { return it }
        val record = WoodObservationRecord(observation, observation.observationId, 1)
        return accepted(
            records + record,
            if (makeEffective) observation.observationId else effectiveObservationId,
        )
    }

    /**
     * Only an exact head can be corrected. There is deliberately no makeEffective
     * argument: correcting the selected observation leaves that pointer SUPERSEDED.
     * The caller must issue a separate explicit selection to resume CURRENT_STATE.
     */
    fun correctObservation(
        observation: RawWoodObservation,
        targetObservationId: UUID,
        expectedTargetObservationRevision: Int,
        expectedObjectRevision: Int,
        reason: String,
    ): WoodHistoryResult {
        checkObjectRevision(expectedObjectRevision)?.let { return it }
        checkNewObservation(observation)?.let { return it }
        val target = byId[targetObservationId]
            ?: return rejected(WoodHistoryError.OBSERVATION_NOT_FOUND, targetObservationId)
        if (target.observationRevision != expectedTargetObservationRevision) {
            return rejected(WoodHistoryError.OBSERVATION_REVISION_CONFLICT, targetObservationId)
        }
        if (targetObservationId in correctedIds) {
            return rejected(WoodHistoryError.OBSERVATION_SUPERSEDED, targetObservationId)
        }
        checkReason(reason, observation.observationId)?.let { return it }
        if (target.observationRevision == Int.MAX_VALUE) {
            return rejected(WoodHistoryError.REVISION_EXHAUSTED, targetObservationId)
        }
        val correction = WoodObservationRecord(
            observation = observation,
            rootObservationId = target.rootObservationId,
            observationRevision = target.observationRevision + 1,
            correctionOfObservationId = targetObservationId,
            correctionReason = reason,
        )
        return accepted(records + correction, effectiveObservationId)
    }

    fun selectEffectiveObservation(
        observationId: UUID,
        expectedObjectRevision: Int,
    ): WoodHistoryResult {
        checkObjectRevision(expectedObjectRevision)?.let { return it }
        if (observationId !in byId) return rejected(WoodHistoryError.OBSERVATION_NOT_FOUND, observationId)
        if (observationId in correctedIds) return rejected(WoodHistoryError.OBSERVATION_SUPERSEDED, observationId)
        return accepted(records, observationId)
    }

    private fun accepted(records: List<WoodObservationRecord>, selectedId: UUID?): WoodHistoryResult =
        WoodHistoryResult.Accepted(
            WoodObservationHistory(physicalObjectId, kind, objectRevision + 1, records, selectedId),
        )

    private fun checkObjectRevision(expected: Int): WoodHistoryResult.Rejected? = when {
        expected != objectRevision -> rejected(WoodHistoryError.OBJECT_REVISION_CONFLICT)
        objectRevision == Int.MAX_VALUE -> rejected(WoodHistoryError.REVISION_EXHAUSTED)
        else -> null
    }

    private fun checkNewObservation(observation: RawWoodObservation): WoodHistoryResult.Rejected? = when {
        observation.physicalObjectId != physicalObjectId ->
            rejected(WoodHistoryError.OBJECT_MISMATCH, observation.observationId)
        observation.measurement.kind != kind -> rejected(WoodHistoryError.KIND_MISMATCH, observation.observationId)
        observation.observationId in byId -> rejected(WoodHistoryError.DUPLICATE_OBSERVATION_ID, observation.observationId)
        else -> null
    }

    companion object {
        const val MAX_CORRECTION_REASON_CODE_POINTS = 5_000

        /**
         * Validate a repository projection before use. Input order and raw values are
         * preserved; neither dates nor list order determine an effective observation.
         * A selection of an old, now corrected observation is valid historical state.
         */
        fun restore(
            physicalObjectId: UUID,
            kind: WoodObjectKind,
            objectRevision: Int,
            records: List<WoodObservationRecord>,
            effectiveObservationId: UUID?,
        ): WoodHistoryResult {
            if (objectRevision < 1) return rejected(WoodHistoryError.INVALID_OBJECT_REVISION)
            val frozenRecords = ArrayList(records)
            val byId = linkedMapOf<UUID, WoodObservationRecord>()
            for (record in frozenRecords) {
                val id = record.observationId
                if (record.observation.physicalObjectId != physicalObjectId) {
                    return rejected(WoodHistoryError.OBJECT_MISMATCH, id)
                }
                if (record.observation.measurement.kind != kind) return rejected(WoodHistoryError.KIND_MISMATCH, id)
                if (byId.put(id, record) != null) return rejected(WoodHistoryError.DUPLICATE_OBSERVATION_ID, id)
                if (record.observationRevision < 1) return rejected(WoodHistoryError.INVALID_OBSERVATION_REVISION, id)
            }
            for (record in frozenRecords) {
                val target = record.correctionOfObservationId
                if (target != null && target !in byId) {
                    return rejected(WoodHistoryError.CORRECTION_TARGET_NOT_FOUND, record.observationId)
                }
                if (record.rootObservationId !in byId) return rejected(WoodHistoryError.ROOT_NOT_FOUND, record.observationId)
            }
            // Iterative and linear: malformed imported cycles cannot overflow the stack.
            val visited = hashSetOf<UUID>()
            for (record in frozenRecords) {
                val path = linkedSetOf<UUID>()
                var cursor: UUID? = record.observationId
                while (cursor != null && cursor !in visited) {
                    if (!path.add(cursor)) return rejected(WoodHistoryError.CORRECTION_CYCLE, cursor)
                    cursor = byId.getValue(cursor).correctionOfObservationId
                }
                visited.addAll(path)
            }
            val correctedIds = hashSetOf<UUID>()
            for (record in frozenRecords) {
                val id = record.observationId
                val targetId = record.correctionOfObservationId
                if (targetId == null) {
                    if (record.rootObservationId != id || record.observationRevision != 1 || record.correctionReason != null) {
                        return rejected(WoodHistoryError.INVALID_ROOT, id)
                    }
                } else {
                    if (!correctedIds.add(targetId)) return rejected(WoodHistoryError.CORRECTION_FORK, id)
                    checkReason(record.correctionReason, id)?.let { return it }
                    val target = byId.getValue(targetId)
                    if (record.rootObservationId != target.rootObservationId) return rejected(WoodHistoryError.ROOT_MISMATCH, id)
                    val root = byId.getValue(record.rootObservationId)
                    if (root.correctionOfObservationId != null) return rejected(WoodHistoryError.INVALID_ROOT, id)
                    if (target.observationRevision == Int.MAX_VALUE || record.observationRevision != target.observationRevision + 1) {
                        return rejected(WoodHistoryError.INVALID_REVISION_SEQUENCE, id)
                    }
                }
            }
            if (effectiveObservationId != null && effectiveObservationId !in byId) {
                return rejected(WoodHistoryError.EFFECTIVE_OBSERVATION_NOT_FOUND, effectiveObservationId)
            }
            return WoodHistoryResult.Accepted(
                WoodObservationHistory(physicalObjectId, kind, objectRevision, frozenRecords, effectiveObservationId),
            )
        }

        private fun checkReason(reason: String?, observationId: UUID): WoodHistoryResult.Rejected? = when {
            reason == null || reason.all { it.isWhitespace() || it == '\u0085' } ->
                rejected(WoodHistoryError.CORRECTION_REASON_REQUIRED, observationId)
            reason.codePointCount(0, reason.length) > MAX_CORRECTION_REASON_CODE_POINTS ->
                rejected(WoodHistoryError.CORRECTION_REASON_TOO_LONG, observationId)
            else -> null
        }

        private fun rejected(code: WoodHistoryError, observationId: UUID? = null) =
            WoodHistoryResult.Rejected(WoodHistoryDiagnostic(code, observationId))
    }
}
