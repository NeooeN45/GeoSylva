package com.forestry.counter.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/** La finalité d'un nouveau relevé est indépendante de son cycle de collecte. */
@Serializable
enum class SurveyPurpose { INVENTORY, MARKING, COMBINED }

/** Population explicitement décrite, jamais déduite des seules tiges saisies. */
@Serializable
enum class SurveyPopulationScope { ALL_STEMS, MARKED_STEMS, REMAINING_STEMS }

/**
 * Une déclaration partielle est sauvegardable avant de connaître le protocole ou la population.
 * Les champs absents ne qualifient ni les mesures, ni leur représentativité, ni un calcul.
 * La surface déclarée est horizontale et réellement inventoriée en m², pas cadastrale ou cible.
 * Ce contexte séparé n'est pas ajouté aux projections historiques tige/session/snapshot/transport.
 */
@Serializable
data class SurveyContextDraft(
    val schemaVersion: Int = 1,
    val purpose: SurveyPurpose? = null,
    val populationReference: String? = null,
    val populationScope: SurveyPopulationScope? = null,
    val protocolReference: String? = null,
    val protocolVersion: String? = null,
    val sourceReference: String? = null,
    val inventoryComplete: Boolean? = null,
    val inventoriedHorizontalAreaM2: Double? = null,
    val areaSourceReference: String? = null,
) {
    init {
        require(schemaVersion == 1) { "SURVEY_CONTEXT_UNSUPPORTED_VERSION" }
        validateReference(populationReference)
        validateReference(protocolReference)
        validateReference(sourceReference)
        validateReference(areaSourceReference)
        validateReference(protocolVersion, 100)
        require(inventoriedHorizontalAreaM2 == null ||
            (inventoriedHorizontalAreaM2.isFinite() && inventoriedHorizontalAreaM2 > 0.0)) {
            "SURVEY_CONTEXT_INVALID_HORIZONTAL_AREA"
        }
    }
}

/** Révision et scellement concernent cette déclaration, pas la révision historique de campagne. */
data class SurveyContextRecord(
    val sessionId: String,
    val revision: Int,
    val draft: SurveyContextDraft,
    val createdAt: Long,
    val createdBy: String,
    val updatedAt: Long,
    val updatedBy: String,
    val copiedFromSessionId: String? = null,
    val isSealed: Boolean = false,
) {
    init {
        validateReference(sessionId)
        validateReference(createdBy)
        validateReference(updatedBy)
        require(sessionId.isNotBlank() && createdBy.isNotBlank() && updatedBy.isNotBlank()) {
            "SURVEY_CONTEXT_MISSING_PROVENANCE"
        }
        validateReference(copiedFromSessionId)
        require(revision > 0 && createdAt >= 0L && updatedAt >= 0L) { "SURVEY_CONTEXT_INVALID_REVISION" }
    }
}

/** Codec V1 strict : aucune donnée scientifique inconnue complétée, aucun contenu ancien réécrit. */
object SurveyContextCodec {
    // V1 : 1 300 caractères de références, soit 7 800 au pire après échappement JSON,
    // plus moins de 1 000 caractères pour les clés et autres valeurs bornées.
    private const val MAX_PAYLOAD_CHARACTERS = 16 * 1024
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        allowSpecialFloatingPointValues = false
    }

    fun encode(draft: SurveyContextDraft): String = json.encodeToString(draft)

    fun decode(payloadJson: String): SurveyContextDraft {
        require(payloadJson.length <= MAX_PAYLOAD_CHARACTERS) { "SURVEY_CONTEXT_PAYLOAD_TOO_LARGE" }
        val payload = json.parseToJsonElement(payloadJson) as? JsonObject
            ?: throw IllegalArgumentException("SURVEY_CONTEXT_INVALID_PAYLOAD")
        val version = payload["schemaVersion"] as? JsonPrimitive
        require(version != null && !version.isString && version.content == "1") {
            "SURVEY_CONTEXT_UNSUPPORTED_VERSION"
        }
        payload["inventoriedHorizontalAreaM2"]?.takeUnless { it == JsonNull }?.let { value ->
            val number = value as? JsonPrimitive
            require(number != null && !number.isString && number.doubleOrNull != null) {
                "SURVEY_CONTEXT_INVALID_HORIZONTAL_AREA"
            }
        }
        payload["inventoryComplete"]?.takeUnless { it == JsonNull }?.let { value ->
            val boolean = value as? JsonPrimitive
            require(boolean != null && !boolean.isString && boolean.booleanOrNull != null) {
                "SURVEY_CONTEXT_INVALID_COMPLETENESS"
            }
        }
        listOf("populationReference", "protocolReference", "protocolVersion", "sourceReference",
            "areaSourceReference").forEach { field ->
            payload[field]?.takeUnless { it == JsonNull }?.let { value ->
                require(value is JsonPrimitive && value.isString) { "SURVEY_CONTEXT_INVALID_REFERENCE" }
            }
        }
        listOf("purpose", "populationScope").forEach { field ->
            payload[field]?.takeUnless { it == JsonNull }?.let { value ->
                require(value is JsonPrimitive && value.isString) { "SURVEY_CONTEXT_INVALID_ENUM_TYPE" }
            }
        }
        return json.decodeFromString<SurveyContextDraft>(payloadJson)
    }
}

private fun validateReference(value: String?, maxLength: Int = 300) {
    require(value == null || (value.isNotBlank() && value.length <= maxLength)) {
        "SURVEY_CONTEXT_INVALID_REFERENCE"
    }
}
