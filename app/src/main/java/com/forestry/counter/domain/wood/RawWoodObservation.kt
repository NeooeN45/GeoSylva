package com.forestry.counter.domain.wood

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/** Structural errors prevent saving a payload; incomplete measurements do not. */
enum class WoodStructureError {
    INVALID_UNICODE,
    CODE_TOO_LONG,
    RAW_TEXT_TOO_LONG,
    UNIT_TOO_LONG,
    PROTOCOL_TOO_LONG,
    OBSERVATION_AND_OBJECT_ID_IDENTICAL,
    INCOMPLETE_OBSERVED_TIME,
    UNKNOWN_TIME_ZONE,
    INVALID_TIME_OFFSET,
    INCONSISTENT_TIME_OFFSET,
}

class WoodStructureException(val code: WoodStructureError) : IllegalArgumentException(code.name)

/** The exact entered text is retained, including an unknown unit or invalid number. */
data class RawWoodDimension(val rawText: String?, val unit: String?) {
    init {
        checkWoodLength(rawText, 128, WoodStructureError.RAW_TEXT_TOO_LONG)
        checkWoodLength(unit, 32, WoodStructureError.UNIT_TOO_LONG)
    }
}

/** An observed time is either absent or a complete, consistent triple. */
data class WoodObservedTime(val instant: Instant, val zoneId: String, val offsetSeconds: Int) {
    init {
        if (zoneId !in ZoneId.getAvailableZoneIds()) {
            throw WoodStructureException(WoodStructureError.UNKNOWN_TIME_ZONE)
        }
        val offset = try {
            ZoneOffset.ofTotalSeconds(offsetSeconds)
        } catch (_: java.time.DateTimeException) {
            throw WoodStructureException(WoodStructureError.INVALID_TIME_OFFSET)
        }
        if (ZoneId.of(zoneId).rules.getOffset(instant) != offset) {
            throw WoodStructureException(WoodStructureError.INCONSISTENT_TIME_OFFSET)
        }
    }

    companion object {
        fun fromParts(instant: Instant?, zoneId: String?, offsetSeconds: Int?): WoodObservedTime? {
            if (instant == null && zoneId == null && offsetSeconds == null) return null
            if (instant == null || zoneId == null || offsetSeconds == null) {
                throw WoodStructureException(WoodStructureError.INCOMPLETE_OBSERVED_TIME)
            }
            return WoodObservedTime(instant, zoneId, offsetSeconds)
        }
    }
}

enum class WoodObjectKind { PILE, LOG }

/**
 * Payloads contain values only, with no mutable collection or default convention.
 * Unknown codes are kept for diagnostics rather than converted to a known method.
 */
sealed interface RawWoodMeasurement {
    val kind: WoodObjectKind

    data class Pile(
        val length: RawWoodDimension? = null,
        val width: RawWoodDimension? = null,
        val meanHeight: RawWoodDimension? = null,
        val shapeCode: String? = null,
        val protocolReference: String? = null,
        /** Declared piece length; never substituted for the depth of the pile. */
        val pieceLength: RawWoodDimension? = null,
    ) : RawWoodMeasurement {
        override val kind: WoodObjectKind get() = WoodObjectKind.PILE

        init {
            checkWoodLength(protocolReference, 300, WoodStructureError.PROTOCOL_TOO_LONG)
            checkWoodLength(shapeCode, 64, WoodStructureError.CODE_TOO_LONG)
        }

        companion object {
            const val RECTANGULAR_ENVELOPE = "RECTANGULAR_ENVELOPE"
        }
    }

    data class Log(
        val length: RawWoodDimension? = null,
        val startDiameter: RawWoodDimension? = null,
        val middleDiameter: RawWoodDimension? = null,
        val endDiameter: RawWoodDimension? = null,
        /** Exact codes from RoundwoodVolumeMethod.code: huber, smalian, newton. */
        val methodCode: String? = null,
        /** Exact BarkConvention names: OVER_BARK or UNDER_BARK. */
        val barkConventionCode: String? = null,
    ) : RawWoodMeasurement {
        override val kind: WoodObjectKind get() = WoodObjectKind.LOG

        init {
            checkWoodLength(methodCode, 64, WoodStructureError.CODE_TOO_LONG)
            checkWoodLength(barkConventionCode, 64, WoodStructureError.CODE_TOO_LONG)
        }
    }
}

/**
 * One observation of an independently identified physical object. Remeasuring
 * creates another observation; this payload creates no stock or payable quantity.
 * Persistence, authorization, revisions and effective selection belong to the
 * repository layer and are intentionally not inferred by this calculation core.
 */
data class RawWoodObservation(
    val observationId: UUID,
    val physicalObjectId: UUID,
    val recordedAt: Instant,
    val measurement: RawWoodMeasurement,
    val observedTime: WoodObservedTime? = null,
) {
    init {
        if (observationId == physicalObjectId) {
            throw WoodStructureException(WoodStructureError.OBSERVATION_AND_OBJECT_ID_IDENTICAL)
        }
    }
}

internal fun checkWoodLength(value: String?, limit: Int, error: WoodStructureError) {
    if (value != null && !hasValidWoodUnicode(value)) throw WoodStructureException(WoodStructureError.INVALID_UNICODE)
    if (value != null && value.codePointCount(0, value.length) > limit) {
        throw WoodStructureException(error)
    }
}

/** Reject malformed UTF-16 instead of replacing characters while writing UTF-8. */
internal fun hasValidWoodUnicode(value: String): Boolean {
    var index = 0
    while (index < value.length) {
        val char = value[index++]
        if (Character.isHighSurrogate(char)) {
            if (index >= value.length || !Character.isLowSurrogate(value[index++])) return false
        } else if (Character.isLowSurrogate(char)) return false
    }
    return true
}
