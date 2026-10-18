package com.forestry.counter.domain.wood

import java.util.UUID

/** Stable domain failures, independent of UI text and repository access decisions. */
enum class WoodHistoryError {
    INVALID_OBJECT_REVISION,
    INVALID_OBSERVATION_REVISION,
    OBJECT_MISMATCH,
    KIND_MISMATCH,
    DUPLICATE_OBSERVATION_ID,
    OBSERVATION_NOT_FOUND,
    ROOT_NOT_FOUND,
    INVALID_ROOT,
    ROOT_MISMATCH,
    CORRECTION_TARGET_NOT_FOUND,
    CORRECTION_CYCLE,
    CORRECTION_FORK,
    CORRECTION_REASON_REQUIRED,
    CORRECTION_REASON_TOO_LONG,
    INVALID_REVISION_SEQUENCE,
    EFFECTIVE_OBSERVATION_NOT_FOUND,
    OBJECT_REVISION_CONFLICT,
    OBSERVATION_REVISION_CONFLICT,
    OBSERVATION_SUPERSEDED,
    REVISION_EXHAUSTED,
}

data class WoodHistoryDiagnostic(
    val code: WoodHistoryError,
    val observationId: UUID? = null,
)

sealed interface WoodHistoryResult {
    /** A new value only: nothing has been committed to storage. */
    data class Accepted(val history: WoodObservationHistory) : WoodHistoryResult

    data class Rejected(val diagnostic: WoodHistoryDiagnostic) : WoodHistoryResult
}

/** An exact selection, never the latest timestamp or an automatically substituted correction. */
sealed interface WoodEffectiveSelection {
    object None : WoodEffectiveSelection

    /** Current describes the lineage only; this observation may still be uncalculable. */
    data class Current(val record: WoodObservationRecord) : WoodEffectiveSelection

    data class Superseded(val record: WoodObservationRecord) : WoodEffectiveSelection
}
