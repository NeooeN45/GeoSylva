package com.forestry.counter.presentation.screens.forestry

import com.forestry.counter.domain.model.SurveyContextDraft
import com.forestry.counter.domain.model.SurveyPopulationScope
import com.forestry.counter.domain.model.SurveyPurpose
import java.math.BigDecimal

enum class SurveyContextUiError { CONFLICT, UNAVAILABLE, SAVE_FAILED }

/** Valeurs saisies conservées telles quelles ; une information vide reste inconnue. */
data class SurveyContextFormState(
    val purpose: SurveyPurpose? = null,
    val populationReference: String = "",
    val populationScope: SurveyPopulationScope? = null,
    val protocolReference: String = "",
    val protocolVersion: String = "",
    val sourceReference: String = "",
    val inventoryComplete: Boolean? = null,
    val inventoriedHorizontalAreaInput: String = "",
    val areaSourceReference: String = "",
) {
    fun validation(): SurveyContextFormValidation = SurveyContextFormValidation(
        populationReferenceTooLong = populationReference.isReferenceTooLong(REFERENCE_MAX_LENGTH),
        protocolReferenceTooLong = protocolReference.isReferenceTooLong(REFERENCE_MAX_LENGTH),
        protocolVersionTooLong = protocolVersion.isReferenceTooLong(VERSION_MAX_LENGTH),
        sourceReferenceTooLong = sourceReference.isReferenceTooLong(REFERENCE_MAX_LENGTH),
        areaSourceReferenceTooLong = areaSourceReference.isReferenceTooLong(REFERENCE_MAX_LENGTH),
        invalidHorizontalArea = inventoriedHorizontalAreaInput.isNotBlank() &&
            parseInventoriedHorizontalArea(inventoriedHorizontalAreaInput) == null,
    )

    /** Valider une déclaration ne qualifie ni son protocole, ni sa représentativité scientifique. */
    fun toDraft(): SurveyContextDraft? {
        if (!validation().isValid) return null
        return SurveyContextDraft(
            purpose = purpose,
            populationReference = populationReference.knownReferenceOrNull(),
            populationScope = populationScope,
            protocolReference = protocolReference.knownReferenceOrNull(),
            protocolVersion = protocolVersion.knownReferenceOrNull(),
            sourceReference = sourceReference.knownReferenceOrNull(),
            inventoryComplete = inventoryComplete,
            inventoriedHorizontalAreaM2 = parseInventoriedHorizontalArea(inventoriedHorizontalAreaInput),
            areaSourceReference = areaSourceReference.knownReferenceOrNull(),
        )
    }

    companion object {
        const val REFERENCE_MAX_LENGTH = 300
        const val VERSION_MAX_LENGTH = 100

        fun fromDraft(draft: SurveyContextDraft): SurveyContextFormState = SurveyContextFormState(
            purpose = draft.purpose,
            populationReference = draft.populationReference.orEmpty(),
            populationScope = draft.populationScope,
            protocolReference = draft.protocolReference.orEmpty(),
            protocolVersion = draft.protocolVersion.orEmpty(),
            sourceReference = draft.sourceReference.orEmpty(),
            inventoryComplete = draft.inventoryComplete,
            // La forme décimale garde même les petites/grandes valeurs héritées, sans exposant implicite.
            inventoriedHorizontalAreaInput = draft.inventoriedHorizontalAreaM2?.let {
                BigDecimal.valueOf(it).stripTrailingZeros().toPlainString()
            }.orEmpty(),
            areaSourceReference = draft.areaSourceReference.orEmpty(),
        )
    }
}

data class SurveyContextFormValidation(
    val populationReferenceTooLong: Boolean = false,
    val protocolReferenceTooLong: Boolean = false,
    val protocolVersionTooLong: Boolean = false,
    val sourceReferenceTooLong: Boolean = false,
    val areaSourceReferenceTooLong: Boolean = false,
    val invalidHorizontalArea: Boolean = false,
) {
    val isValid: Boolean
        get() = !populationReferenceTooLong && !protocolReferenceTooLong && !protocolVersionTooLong &&
            !sourceReferenceTooLong && !areaSourceReferenceTooLong && !invalidHorizontalArea
}

private val horizontalAreaDecimal = Regex("(?:[0-9]+(?:[.,][0-9]+)?|[.,][0-9]+)")

/** Surface horizontale réellement inventoriée en m² ; aucune surface cartographique n'est déduite. */
fun parseInventoriedHorizontalArea(input: String): Double? {
    val candidate = input.trim()
    if (candidate.isEmpty() || !horizontalAreaDecimal.matches(candidate)) return null
    return candidate.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
}

// Les références non vides, y compris leurs espaces, restent celles de l'utilisateur ou de l'archive.
private fun String.knownReferenceOrNull(): String? = takeUnless { it.isBlank() }

private fun String.isReferenceTooLong(maxLength: Int): Boolean = isNotBlank() && length > maxLength
