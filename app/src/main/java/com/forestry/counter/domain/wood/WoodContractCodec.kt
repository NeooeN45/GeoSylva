package com.forestry.counter.domain.wood

import com.forestry.counter.domain.model.WoodDepotLocation
import com.forestry.counter.domain.model.WoodDepotOwnerScope
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A value snapshot. Byte arrays handed to callers are independent copies. Not a signature. */
class FrozenWoodDocument internal constructor(val text: String) {
    val sha256: String = woodSha256(text.toByteArray(Charsets.UTF_8))
    fun utf8Bytes(): ByteArray = text.toByteArray(Charsets.UTF_8)
}

/**
 * GeoSylva's local v1 representation, not a FORLOG protocol or general JSON import.
 * Field order, explicit nulls and exact strings define the canonical bytes. On
 * reread, reject alternate/ambiguous representations instead of dropping fields,
 * accepting duplicate keys or normalizing original readings silently.
 */
object WoodContractCodec {
    const val MAX_DOCUMENT_BYTES = 131_072
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
    }

    fun freezeHeader(header: WoodObjectHeader): FrozenWoodDocument =
        freeze(json.encodeToString(HeaderEnvelope(header = header.toWire())))

    fun readHeader(bytes: ByteArray, expectedSha256: String): WoodObjectHeader = checked {
        val text = checkedText(bytes, expectedSha256)
        val wire = json.decodeFromString<HeaderEnvelope>(text)
        version(wire.schemaVersion, wire.format, "wood-object-header")
        val header = wire.header.toDomain()
        requireCanonical(text, freezeHeader(header))
        header
    }

    fun freezePayload(payload: WoodObservationPayload): FrozenWoodDocument =
        freeze(json.encodeToString(payload.toWire()))

    fun readPayload(bytes: ByteArray, expectedSha256: String): WoodObservationPayload = checked {
        val text = checkedText(bytes, expectedSha256)
        val wire = json.decodeFromString<PayloadWire>(text)
        val payload = wire.toDomain()
        requireCanonical(text, freezePayload(payload))
        payload
    }

    fun freezeCommand(command: WoodObjectCommand): FrozenWoodDocument =
        freeze(json.encodeToString(command.toWire()))

    fun readCommand(bytes: ByteArray, expectedSha256: String): WoodObjectCommand = checked {
        val text = checkedText(bytes, expectedSha256)
        val wire = json.decodeFromString<CommandWire>(text)
        val command = wire.toDomain()
        requireCanonical(text, freezeCommand(command))
        command
    }

    private fun freeze(text: String): FrozenWoodDocument {
        if (text.toByteArray(Charsets.UTF_8).size > MAX_DOCUMENT_BYTES) {
            throw WoodContractException(WoodContractError.DOCUMENT_TOO_LARGE)
        }
        return FrozenWoodDocument(text)
    }

    private fun checkedText(input: ByteArray, expectedSha256: String): String {
        if (input.size > MAX_DOCUMENT_BYTES) throw WoodContractException(WoodContractError.DOCUMENT_TOO_LARGE)
        val bytes = input.copyOf()
        if (!Regex("[0-9a-f]{64}").matches(expectedSha256) || woodSha256(bytes) != expectedSha256) {
            throw WoodContractException(WoodContractError.HASH_MISMATCH)
        }
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            throw WoodContractException(WoodContractError.MALFORMED_DOCUMENT)
        }
    }

    private fun requireCanonical(text: String, frozen: FrozenWoodDocument) {
        if (text != frozen.text) throw WoodContractException(WoodContractError.NON_CANONICAL_DOCUMENT)
    }

    private inline fun <T> checked(block: () -> T): T = try {
        block()
    } catch (error: WoodContractException) {
        throw error
    } catch (_: java.time.DateTimeException) {
        throw WoodContractException(WoodContractError.MALFORMED_DOCUMENT)
    } catch (_: IllegalArgumentException) {
        // Includes serializer failures and invalid domain values, never echoes the source document.
        throw WoodContractException(WoodContractError.MALFORMED_DOCUMENT)
    }
}

private fun woodSha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

private fun uuid(text: String): UUID {
    val value = try { UUID.fromString(text) } catch (_: IllegalArgumentException) {
        throw WoodContractException(WoodContractError.INVALID_ID)
    }
    if (value.toString() != text) throw WoodContractException(WoodContractError.INVALID_ID)
    return value
}

private fun version(actual: Int, format: String, expectedFormat: String) {
    if (actual != 1 || format != expectedFormat) throw WoodContractException(WoodContractError.UNSUPPORTED_VERSION)
}

@Serializable
private data class DimensionWire(val rawText: String?, val unit: String?) {
    fun toDomain() = RawWoodDimension(rawText, unit)
}
private fun RawWoodDimension.toWire() = DimensionWire(rawText, unit)

@Serializable
private data class TimeWire(val instant: String, val zoneId: String, val offsetSeconds: Int) {
    fun toDomain() = WoodObservedTime(Instant.parse(instant), zoneId, offsetSeconds)
}
private fun WoodObservedTime.toWire() = TimeWire(instant.toString(), zoneId, offsetSeconds)

@Serializable
private data class MeasurementWire(
    val kind: String,
    val pileLength: DimensionWire? = null,
    val pileDepth: DimensionWire? = null,
    val meanPileHeight: DimensionWire? = null,
    val pieceLength: DimensionWire? = null,
    val shapeCode: String? = null,
    val protocolReference: String? = null,
    val segmentLength: DimensionWire? = null,
    val diameterAtStart: DimensionWire? = null,
    val diameterAtMiddle: DimensionWire? = null,
    val diameterAtEnd: DimensionWire? = null,
    val methodCode: String? = null,
    val barkConventionCode: String? = null,
) {
    fun toDomain(): RawWoodMeasurement = when (WoodObjectKind.valueOf(kind)) {
        WoodObjectKind.PILE -> RawWoodMeasurement.Pile(pileLength?.toDomain(), pileDepth?.toDomain(),
            meanPileHeight?.toDomain(), shapeCode, protocolReference, pieceLength?.toDomain())
        WoodObjectKind.LOG -> RawWoodMeasurement.Log(segmentLength?.toDomain(), diameterAtStart?.toDomain(),
            diameterAtMiddle?.toDomain(), diameterAtEnd?.toDomain(), methodCode, barkConventionCode)
    }
}
private fun RawWoodMeasurement.toWire() = when (this) {
    is RawWoodMeasurement.Pile -> MeasurementWire(kind.name, pileLength = length?.toWire(),
        pileDepth = width?.toWire(), meanPileHeight = meanHeight?.toWire(), pieceLength = pieceLength?.toWire(),
        shapeCode = shapeCode, protocolReference = protocolReference)
    is RawWoodMeasurement.Log -> MeasurementWire(kind.name, segmentLength = length?.toWire(),
        diameterAtStart = startDiameter?.toWire(), diameterAtMiddle = middleDiameter?.toWire(),
        diameterAtEnd = endDiameter?.toWire(), methodCode = methodCode, barkConventionCode = barkConventionCode)
}

@Serializable
private data class PayloadWire(
    val format: String,
    val schemaVersion: Int,
    val parserVersion: String,
    val measurement: MeasurementWire,
    val observedTime: TimeWire? = null,
    val description: String? = null,
    val declaredOrigin: String? = null,
    val declaredSpecies: String? = null,
    val declaredQuality: String? = null,
    val location: WoodDepotLocation? = null,
) {
    fun toDomain(): WoodObservationPayload {
        version(schemaVersion, format, "wood-observation-payload")
        if (parserVersion != WoodRawDecimalParser.CONTRACT_VERSION) {
            throw WoodContractException(WoodContractError.UNSUPPORTED_VERSION)
        }
        return WoodObservationPayload(measurement.toDomain(), observedTime?.toDomain(), description,
            declaredOrigin, declaredSpecies, declaredQuality, location)
    }
}
private fun WoodObservationPayload.toWire() = PayloadWire("wood-observation-payload", 1,
    WoodRawDecimalParser.CONTRACT_VERSION, measurement.toWire(), observedTime?.toWire(), description,
    declaredOrigin, declaredSpecies, declaredQuality, location)

@Serializable
private data class HeaderWire(
    val name: String,
    val tag: String? = null,
    val description: String? = null,
    val declaredOrigin: String? = null,
    val declaredSpecies: String? = null,
    val declaredQuality: String? = null,
    val location: WoodDepotLocation? = null,
) {
    fun toDomain() = WoodObjectHeader(name, tag, description, declaredOrigin, declaredSpecies, declaredQuality, location)
}
private fun WoodObjectHeader.toWire() = HeaderWire(name, tag, description, declaredOrigin, declaredSpecies, declaredQuality, location)

@Serializable
private data class ContextWire(
    val commandId: String,
    val depotId: String,
    val objectId: String,
    val ownerScope: WoodDepotOwnerScope,
    val ownerAccountId: String?,
    val actorId: String,
) {
    fun toDomain() = WoodCommandContext(uuid(commandId), uuid(depotId), uuid(objectId),
        WoodOwner(ownerScope, ownerAccountId), actorId)
}
private fun WoodCommandContext.toWire() = ContextWire(commandId.toString(), depotId.toString(), objectId.toString(),
    owner.scope, owner.accountId, actorId)

@Serializable
private data class NewObservationWire(val observationId: String, val payload: PayloadWire) {
    fun toDomain() = NewWoodObservation(uuid(observationId), payload.toDomain())
}
private fun NewWoodObservation.toWire() = NewObservationWire(id.toString(), payload.toWire())

@Serializable
private data class CommandWire(
    val format: String = "wood-object-command",
    val schemaVersion: Int = 1,
    val action: String,
    val context: ContextWire,
    val kind: WoodObjectKind? = null,
    val header: HeaderWire? = null,
    val expectedObjectRevision: Int? = null,
    val observation: NewObservationWire? = null,
    val makeEffective: Boolean? = null,
    val targetObservationId: String? = null,
    val expectedObservationRevision: Int? = null,
    val reason: String? = null,
    val selectedObservationId: String? = null,
) {
    fun toDomain(): WoodObjectCommand {
        version(schemaVersion, format, "wood-object-command")
        val c = context.toDomain()
        return when (action) {
            "CREATE" -> WoodObjectCommand.Create(c, requireNotNull(kind), requireNotNull(header).toDomain(),
                observation?.toDomain(), requireNotNull(makeEffective))
            "UPDATE_HEADER" -> WoodObjectCommand.UpdateHeader(c, requireNotNull(expectedObjectRevision), requireNotNull(header).toDomain())
            "ADD_OBSERVATION" -> WoodObjectCommand.AddObservation(c, requireNotNull(expectedObjectRevision),
                requireNotNull(observation).toDomain(), requireNotNull(makeEffective))
            "CORRECT_OBSERVATION" -> WoodObjectCommand.CorrectObservation(c, requireNotNull(expectedObjectRevision),
                uuid(requireNotNull(targetObservationId)), requireNotNull(expectedObservationRevision),
                requireNotNull(observation).toDomain(), requireNotNull(reason))
            "SELECT_EFFECTIVE" -> WoodObjectCommand.SelectEffective(c, requireNotNull(expectedObjectRevision),
                uuid(requireNotNull(selectedObservationId)))
            "ARCHIVE" -> WoodObjectCommand.Archive(c, requireNotNull(expectedObjectRevision))
            else -> throw WoodContractException(WoodContractError.UNSUPPORTED_VERSION)
        }
    }
}
private fun WoodObjectCommand.toWire(): CommandWire {
    val c = context.toWire()
    return when (this) {
        is WoodObjectCommand.Create -> CommandWire(action = "CREATE", context = c, kind = kind,
            header = header.toWire(), observation = firstObservation?.toWire(), makeEffective = makeEffective)
        is WoodObjectCommand.UpdateHeader -> CommandWire(action = "UPDATE_HEADER", context = c,
            header = header.toWire(), expectedObjectRevision = expectedObjectRevision)
        is WoodObjectCommand.AddObservation -> CommandWire(action = "ADD_OBSERVATION", context = c,
            expectedObjectRevision = expectedObjectRevision, observation = observation.toWire(), makeEffective = makeEffective)
        is WoodObjectCommand.CorrectObservation -> CommandWire(action = "CORRECT_OBSERVATION", context = c,
            expectedObjectRevision = expectedObjectRevision, observation = correction.toWire(),
            targetObservationId = targetObservationId.toString(), expectedObservationRevision = expectedObservationRevision, reason = reason)
        is WoodObjectCommand.SelectEffective -> CommandWire(action = "SELECT_EFFECTIVE", context = c,
            expectedObjectRevision = expectedObjectRevision, selectedObservationId = observationId.toString())
        is WoodObjectCommand.Archive -> CommandWire(action = "ARCHIVE", context = c, expectedObjectRevision = expectedObjectRevision)
    }
}

@Serializable
private data class HeaderEnvelope(
    val format: String = "wood-object-header",
    val schemaVersion: Int = 1,
    val header: HeaderWire,
)
