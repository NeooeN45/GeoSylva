package com.forestry.counter.domain.model

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** Une ligne groupée ou de référence ne désigne pas nécessairement un arbre physique. */
@Serializable
enum class ObservationSupport { INDIVIDUAL_TREE, GROUP_REFERENCE }

/** Nature déclarée de la hauteur, sans transformation de la mesure enregistrée. */
@Serializable
enum class HeightNature { TOTAL, MERCHANTABLE }

/** Origine déclarée de la mesure, jamais déduite d'une valeur numérique seule. */
@Serializable
enum class MeasurementOrigin { MEASURED, ESTIMATED, ASSIGNED_REFERENCE }

/** Un point réutilisé n'est pas une nouvelle capture GPS de l'observation. */
@Serializable
enum class PositionOrigin { CAPTURED_FOR_OBSERVATION, REUSED_POINT, MANUALLY_LOCATED }

@Serializable
data class DiameterQualificationDraft(
    val origin: MeasurementOrigin? = null,
    val conventionReference: String? = null,
    val methodReference: String? = null,
    val sourceReference: String? = null,
    val observedAt: Long? = null,
) {
    init {
        validateQualificationReference(conventionReference)
        validateQualificationReference(methodReference)
        validateQualificationReference(sourceReference)
        validateQualificationTime(observedAt)
    }
}

@Serializable
data class HeightQualificationDraft(
    val nature: HeightNature? = null,
    val origin: MeasurementOrigin? = null,
    val support: ObservationSupport? = null,
    val pairedWithDiameter: Boolean? = null,
    val methodReference: String? = null,
    val sourceReference: String? = null,
    val observedAt: Long? = null,
) {
    init {
        validateQualificationReference(methodReference)
        validateQualificationReference(sourceReference)
        validateQualificationTime(observedAt)
        require(pairedWithDiameter != true ||
            (nature == HeightNature.TOTAL && support == ObservationSupport.INDIVIDUAL_TREE &&
                origin != MeasurementOrigin.ASSIGNED_REFERENCE)) {
            "OBSERVATION_QUALIFICATION_INVALID_PAIRING"
        }
    }
}

@Serializable
data class PositionQualificationDraft(
    val origin: PositionOrigin? = null,
    val methodReference: String? = null,
    val sourceReference: String? = null,
    val capturedAt: Long? = null,
) {
    init {
        validateQualificationReference(methodReference)
        validateQualificationReference(sourceReference)
        validateQualificationTime(capturedAt)
    }
}

/**
 * Déclaration partielle, distincte des mesures brutes et de leur validation scientifique.
 * Aucun champ inconnu n'est rempli automatiquement et aucun calcul n'est autorisé par ce modèle.
 * Un appariement individuel exige une hauteur totale rattachée explicitement au même arbre.
 */
@Serializable
data class ObservationQualificationDraft(
    val support: ObservationSupport? = null,
    val diameter: DiameterQualificationDraft? = null,
    val height: HeightQualificationDraft? = null,
    val position: PositionQualificationDraft? = null,
) {
    init {
        require(height?.pairedWithDiameter != true ||
            (support == ObservationSupport.INDIVIDUAL_TREE &&
                diameter?.origin != MeasurementOrigin.ASSIGNED_REFERENCE)) {
            "OBSERVATION_QUALIFICATION_INVALID_PAIRING"
        }
    }
}

/**
 * État brut exact auquel la déclaration est liée, sans qualification inférée.
 * La version invalide aussi une déclaration après édition puis retour aux mêmes valeurs.
 * Les zéros sont conservés sans être présentés comme mesures admissibles pour un calcul.
 */
@Serializable
data class ObservationMeasurementBasis(
    val tigeId: String,
    val parcelleId: String,
    val placetteId: String?,
    val sessionId: String?,
    val originTigeId: String?,
    val essenceCode: String,
    val diamCm: Double,
    val hauteurM: Double?,
    val gpsWkt: String?,
    val precisionM: Double?,
    val altitudeM: Double?,
    val timestamp: Long,
    val observationVersion: Int,
) {
    init {
        validateQualificationReference(tigeId)
        validateQualificationReference(parcelleId)
        validateQualificationReference(placetteId)
        validateQualificationReference(sessionId)
        validateQualificationReference(originTigeId)
        validateQualificationReference(essenceCode)
        validateQualificationReference(gpsWkt, 4096)
        require(tigeId.isNotBlank() && parcelleId.isNotBlank() && essenceCode.isNotBlank()) {
            "OBSERVATION_QUALIFICATION_MISSING_IDENTITY"
        }
        require(diamCm.isFinite() && diamCm >= 0.0) { "OBSERVATION_QUALIFICATION_INVALID_DIAMETER" }
        require(hauteurM == null || (hauteurM.isFinite() && hauteurM >= 0.0)) {
            "OBSERVATION_QUALIFICATION_INVALID_HEIGHT"
        }
        require(precisionM == null || (precisionM.isFinite() && precisionM >= 0.0)) {
            "OBSERVATION_QUALIFICATION_INVALID_PRECISION"
        }
        require(altitudeM == null || altitudeM.isFinite()) { "OBSERVATION_QUALIFICATION_INVALID_ALTITUDE" }
        validateQualificationTime(timestamp)
        require(observationVersion > 0) { "OBSERVATION_QUALIFICATION_INVALID_OBSERVATION_VERSION" }
    }

    /** Projection V1 explicitement ordonnée, indépendante de futurs champs de Tige. */
    fun fingerprint(): String {
        val projection = JsonObject(linkedMapOf(
            "tigeId" to JsonPrimitive(tigeId),
            "parcelleId" to JsonPrimitive(parcelleId),
            "placetteId" to nullableString(placetteId),
            "sessionId" to nullableString(sessionId),
            "originTigeId" to nullableString(originTigeId),
            "essenceCode" to JsonPrimitive(essenceCode),
            "diamCm" to JsonPrimitive(diamCm),
            "hauteurM" to nullableNumber(hauteurM),
            "gpsWkt" to nullableString(gpsWkt),
            "precisionM" to nullableNumber(precisionM),
            "altitudeM" to nullableNumber(altitudeM),
            "timestamp" to JsonPrimitive(timestamp),
            "observationVersion" to JsonPrimitive(observationVersion),
        ))
        val digest = MessageDigest.getInstance("SHA-256").digest(projection.toString().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    /** Une observation désormais invalide ne peut pas rendre la déclaration courante. */
    fun matches(tige: Tige): Boolean = try {
        fingerprint() == capture(tige).fingerprint()
    } catch (_: IllegalArgumentException) {
        false
    }

    companion object {
        fun capture(tige: Tige): ObservationMeasurementBasis = ObservationMeasurementBasis(
            tigeId = tige.id,
            parcelleId = tige.parcelleId,
            placetteId = tige.placetteId,
            sessionId = tige.sessionId,
            originTigeId = tige.originTigeId,
            essenceCode = tige.essenceCode,
            diamCm = tige.diamCm,
            hauteurM = tige.hauteurM,
            gpsWkt = tige.gpsWkt,
            precisionM = tige.precisionM,
            altitudeM = tige.altitudeM,
            timestamp = tige.timestamp,
            observationVersion = tige.version,
        )
    }
}

/** Filiation capturée par le dépôt lors d'une correction, pas renseignée par le formulaire. */
@Serializable
data class ObservationQualificationCopySource(
    val tigeId: String,
    val revision: Int,
    val updatedAt: Long,
    val updatedBy: String,
) {
    init {
        validateQualificationReference(tigeId)
        validateQualificationReference(updatedBy)
        require(tigeId.isNotBlank() && updatedBy.isNotBlank() && revision > 0) {
            "OBSERVATION_QUALIFICATION_INVALID_COPY_SOURCE"
        }
        validateQualificationTime(updatedAt)
    }
}

/** Enveloppe locale V1 ; les projections historiques et le transport restent inchangés. */
@Serializable
data class ObservationQualificationPayload(
    val schemaVersion: Int = 1,
    val basis: ObservationMeasurementBasis,
    val draft: ObservationQualificationDraft,
    val copiedFrom: ObservationQualificationCopySource? = null,
) {
    init {
        require(schemaVersion == 1) { "OBSERVATION_QUALIFICATION_UNSUPPORTED_VERSION" }
    }
}

data class ObservationQualificationRecord(
    val tigeId: String,
    val revision: Int,
    val payload: ObservationQualificationPayload,
    val createdAt: Long,
    val createdBy: String,
    val updatedAt: Long,
    val updatedBy: String,
    val isSealed: Boolean = false,
) {
    init {
        validateQualificationReference(tigeId)
        validateQualificationReference(createdBy)
        validateQualificationReference(updatedBy)
        require(tigeId.isNotBlank() && tigeId == payload.basis.tigeId &&
            createdBy.isNotBlank() && updatedBy.isNotBlank()) {
            "OBSERVATION_QUALIFICATION_INVALID_RECORD_IDENTITY"
        }
        require(revision > 0) { "OBSERVATION_QUALIFICATION_INVALID_REVISION" }
        validateQualificationTime(createdAt)
        validateQualificationTime(updatedAt)
    }
}

/** CURRENT signifie seulement que la déclaration vise toujours le même état brut. */
enum class ObservationQualificationStatus { CURRENT, STALE }

data class ObservationQualificationRead(
    val record: ObservationQualificationRecord,
    val status: ObservationQualificationStatus,
) {
    val currentDraft: ObservationQualificationDraft?
        get() = record.payload.draft.takeIf { status == ObservationQualificationStatus.CURRENT }
}

/** Codec strict : aucun nombre ou booléen cité n'est converti implicitement. */
object ObservationQualificationCodec {
    private const val MAX_PAYLOAD_CHARACTERS = 32 * 1024
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        allowSpecialFloatingPointValues = false
    }

    fun encode(payload: ObservationQualificationPayload): String {
        val encoded = json.encodeToString(payload)
        require(encoded.length < MAX_PAYLOAD_CHARACTERS) { "OBSERVATION_QUALIFICATION_PAYLOAD_TOO_LARGE" }
        return encoded
    }

    fun decode(payloadJson: String): ObservationQualificationPayload {
        require(payloadJson.length < MAX_PAYLOAD_CHARACTERS) { "OBSERVATION_QUALIFICATION_PAYLOAD_TOO_LARGE" }
        val payload = requireObject(json.parseToJsonElement(payloadJson))
        val version = payload["schemaVersion"] as? JsonPrimitive
        require(version != null && !version.isString && version.content == "1") {
            "OBSERVATION_QUALIFICATION_UNSUPPORTED_VERSION"
        }
        val basis = requireObject(payload["basis"])
        requireStrings(basis, "tigeId", "parcelleId", "placetteId", "sessionId", "originTigeId", "essenceCode", "gpsWkt")
        requireNumbers(basis, "diamCm", "hauteurM", "precisionM", "altitudeM")
        requireIntegers(basis, "timestamp", "observationVersion")
        val draft = requireObject(payload["draft"])
        requireStrings(draft, "support")
        optionalObject(draft, "diameter")?.let { diameter ->
            requireStrings(diameter, "origin", "conventionReference", "methodReference", "sourceReference")
            requireIntegers(diameter, "observedAt")
        }
        optionalObject(draft, "height")?.let { height ->
            requireStrings(height, "nature", "origin", "support", "methodReference", "sourceReference")
            requireIntegers(height, "observedAt")
            height["pairedWithDiameter"]?.takeUnless { it == JsonNull }?.let { value ->
                val boolean = value as? JsonPrimitive
                require(boolean != null && !boolean.isString && boolean.booleanOrNull != null) {
                    "OBSERVATION_QUALIFICATION_INVALID_BOOLEAN_TYPE"
                }
            }
        }
        optionalObject(draft, "position")?.let { position ->
            requireStrings(position, "origin", "methodReference", "sourceReference")
            requireIntegers(position, "capturedAt")
        }
        optionalObject(payload, "copiedFrom")?.let { copiedFrom ->
            requireStrings(copiedFrom, "tigeId", "updatedBy")
            requireIntegers(copiedFrom, "revision", "updatedAt")
        }
        return json.decodeFromString<ObservationQualificationPayload>(payloadJson)
    }

    private fun requireObject(value: JsonElement?): JsonObject = value as? JsonObject
        ?: throw IllegalArgumentException("OBSERVATION_QUALIFICATION_INVALID_OBJECT_TYPE")

    private fun optionalObject(parent: JsonObject, field: String): JsonObject? =
        parent[field]?.takeUnless { it == JsonNull }?.let(::requireObject)

    private fun requireStrings(parent: JsonObject, vararg fields: String) {
        fields.forEach { field ->
            parent[field]?.takeUnless { it == JsonNull }?.let { value ->
                require(value is JsonPrimitive && value.isString) {
                    "OBSERVATION_QUALIFICATION_INVALID_STRING_TYPE"
                }
            }
        }
    }

    private fun requireNumbers(parent: JsonObject, vararg fields: String) {
        fields.forEach { field ->
            parent[field]?.takeUnless { it == JsonNull }?.let { value ->
                val number = value as? JsonPrimitive
                require(number != null && !number.isString && number.doubleOrNull?.isFinite() == true) {
                    "OBSERVATION_QUALIFICATION_INVALID_NUMBER_TYPE"
                }
            }
        }
    }

    private fun requireIntegers(parent: JsonObject, vararg fields: String) {
        fields.forEach { field ->
            parent[field]?.takeUnless { it == JsonNull }?.let { value ->
                val integer = value as? JsonPrimitive
                require(integer != null && !integer.isString && integer.longOrNull != null) {
                    "OBSERVATION_QUALIFICATION_INVALID_INTEGER_TYPE"
                }
            }
        }
    }
}

private fun validateQualificationReference(value: String?, maxLength: Int = 300) {
    require(value == null || (value.isNotBlank() && value.length <= maxLength)) {
        "OBSERVATION_QUALIFICATION_INVALID_REFERENCE"
    }
}

private fun validateQualificationTime(value: Long?) {
    require(value == null || value >= 0L) { "OBSERVATION_QUALIFICATION_INVALID_TIMESTAMP" }
}

private fun nullableString(value: String?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull

private fun nullableNumber(value: Double?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull
