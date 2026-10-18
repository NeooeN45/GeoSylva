package com.forestry.counter.domain.wood

import com.forestry.counter.domain.model.WoodDepotLocation
import com.forestry.counter.domain.model.WoodDepotOwnerScope
import java.util.UUID

enum class WoodContractError {
    INVALID_TEXT, INVALID_OWNER, INVALID_ID, IDENTICAL_IDS, INVALID_REVISION, KIND_MISMATCH,
    INVALID_INITIAL_SELECTION, MALFORMED_DOCUMENT, UNSUPPORTED_VERSION, NON_CANONICAL_DOCUMENT,
    DOCUMENT_TOO_LARGE, HASH_MISMATCH,
}

class WoodContractException(val code: WoodContractError) : IllegalArgumentException(code.name)

/** A declaration in the command, not an authorization. The repository must verify it. */
data class WoodOwner(val scope: WoodDepotOwnerScope, val accountId: String? = null) {
    init {
        if (scope == WoodDepotOwnerScope.LOCAL_DEVICE && accountId != null ||
            scope == WoodDepotOwnerScope.PERSONAL && accountId == null) {
            throw WoodContractException(WoodContractError.INVALID_OWNER)
        }
        woodText(accountId, 200, required = scope == WoodDepotOwnerScope.PERSONAL)
    }
}

/** User declarations only: a species or quality label is not a verified reference or grade. */
data class WoodObjectHeader(
    val name: String,
    val tag: String? = null,
    val description: String? = null,
    val declaredOrigin: String? = null,
    val declaredSpecies: String? = null,
    val declaredQuality: String? = null,
    val location: WoodDepotLocation? = null,
) {
    init {
        woodText(name, 200, required = true)
        woodText(tag, 200)
        woodText(description, 5_000)
        woodText(declaredOrigin, 1_000)
        woodText(declaredSpecies, 300)
        woodText(declaredQuality, 1_000)
    }
}

/**
 * A snapshot of the observation's declarations. It never follows later header edits.
 * Recorded time and author are supplied by the future repository at commit, not
 * inferred from EXIF, the header or a retry clock. Unknown observed time stays null.
 */
data class WoodObservationPayload(
    val measurement: RawWoodMeasurement,
    val observedTime: WoodObservedTime? = null,
    val description: String? = null,
    val declaredOrigin: String? = null,
    val declaredSpecies: String? = null,
    val declaredQuality: String? = null,
    val location: WoodDepotLocation? = null,
) {
    init {
        woodText(description, 5_000)
        woodText(declaredOrigin, 1_000)
        woodText(declaredSpecies, 300)
        woodText(declaredQuality, 1_000)
    }
}

/** Freeze this identity and content before the first attempt; retries use the same values. */
data class WoodCommandContext(
    val commandId: UUID,
    val depotId: UUID,
    val objectId: UUID,
    val owner: WoodOwner,
    val actorId: String,
) {
    init {
        woodText(actorId, 200, required = true)
        distinctWoodIds(commandId, depotId, objectId)
    }
}

/**
 * Immutable intents only. No token, entitlement, access generation, clock of retry,
 * stock operation or commercial quantity can be embedded here. None writes data.
 */
sealed interface WoodObjectCommand {
    val context: WoodCommandContext

    data class Create(
        override val context: WoodCommandContext,
        val kind: WoodObjectKind,
        val header: WoodObjectHeader,
        val firstObservation: NewWoodObservation? = null,
        val makeEffective: Boolean = false,
    ) : WoodObjectCommand {
        init {
            if (firstObservation == null && makeEffective) {
                throw WoodContractException(WoodContractError.INVALID_INITIAL_SELECTION)
            }
            firstObservation?.let {
                observationIds(context, it.id)
                if (it.payload.measurement.kind != kind) throw WoodContractException(WoodContractError.KIND_MISMATCH)
            }
        }
    }

    data class UpdateHeader(
        override val context: WoodCommandContext,
        val expectedObjectRevision: Int,
        val header: WoodObjectHeader,
    ) : WoodObjectCommand { init { woodRevision(expectedObjectRevision) } }

    data class AddObservation(
        override val context: WoodCommandContext,
        val expectedObjectRevision: Int,
        val observation: NewWoodObservation,
        val makeEffective: Boolean,
    ) : WoodObjectCommand {
        init { woodRevision(expectedObjectRevision); observationIds(context, observation.id) }
    }

    /** Correcting never selects the replacement automatically. */
    data class CorrectObservation(
        override val context: WoodCommandContext,
        val expectedObjectRevision: Int,
        val targetObservationId: UUID,
        val expectedObservationRevision: Int,
        val correction: NewWoodObservation,
        val reason: String,
    ) : WoodObjectCommand {
        init {
            woodRevision(expectedObjectRevision)
            woodRevision(expectedObservationRevision)
            observationIds(context, targetObservationId, correction.id)
            woodText(reason, 5_000, required = true)
        }
    }

    data class SelectEffective(
        override val context: WoodCommandContext,
        val expectedObjectRevision: Int,
        val observationId: UUID,
    ) : WoodObjectCommand {
        init { woodRevision(expectedObjectRevision); observationIds(context, observationId) }
    }

    data class Archive(
        override val context: WoodCommandContext,
        val expectedObjectRevision: Int,
    ) : WoodObjectCommand { init { woodRevision(expectedObjectRevision) } }
}

data class NewWoodObservation(val id: UUID, val payload: WoodObservationPayload)

internal fun woodText(value: String?, limit: Int, required: Boolean = false) {
    if ((required && (value == null || value.all { it.isWhitespace() || it == '\u0085' })) ||
        (value != null && (!hasValidWoodUnicode(value) || value.codePointCount(0, value.length) > limit))) {
        throw WoodContractException(WoodContractError.INVALID_TEXT)
    }
}

private fun woodRevision(revision: Int) {
    if (revision < 1) throw WoodContractException(WoodContractError.INVALID_REVISION)
}

private fun observationIds(context: WoodCommandContext, vararg ids: UUID) =
    distinctWoodIds(context.commandId, context.depotId, context.objectId, *ids)

private fun distinctWoodIds(vararg ids: UUID) {
    if (ids.toSet().size != ids.size) throw WoodContractException(WoodContractError.IDENTICAL_IDS)
}
