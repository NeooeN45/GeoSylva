package com.forestry.counter.presentation.screens.forestry

import com.forestry.counter.domain.model.DiameterQualificationDraft
import com.forestry.counter.domain.model.HeightNature
import com.forestry.counter.domain.model.HeightQualificationDraft
import com.forestry.counter.domain.model.MeasurementOrigin
import com.forestry.counter.domain.model.ObservationQualificationDraft
import com.forestry.counter.domain.model.ObservationSupport
import com.forestry.counter.domain.model.PositionOrigin
import com.forestry.counter.domain.model.PositionQualificationDraft

/** Choix déclaratifs locaux de l'UI, sans catalogue scientifique ni résolution de convention. */
enum class ObservationDiameterConventionChoice(val localReference: String?) {
    BREAST_HEIGHT_1_30_M("geosylva:diameter-convention:breast-height-1.30m:v1"),
    BREAST_HEIGHT_1_50_M("geosylva:diameter-convention:breast-height-1.50m:v1"),
    OTHER(null),
}

/**
 * Saisie progressive d'une déclaration ; aucune valeur brute, date ou source n'est inventée.
 * Les références restent littérales. Dates et position héritées sont conservées, sans champ d'édition.
 * La présence de conteneurs vides hérités reste distincte de leur absence.
 */
data class ObservationQualificationFormState(
    val support: ObservationSupport? = null,
    val diameterOrigin: MeasurementOrigin? = null,
    val diameterConventionChoice: ObservationDiameterConventionChoice? = null,
    val diameterOtherConventionReference: String = "",
    val diameterMethodReference: String = "",
    val diameterSourceReference: String = "",
    val heightNature: HeightNature? = null,
    val heightOrigin: MeasurementOrigin? = null,
    val heightSupport: ObservationSupport? = null,
    val heightPairedWithDiameter: Boolean? = null,
    val heightMethodReference: String = "",
    val heightSourceReference: String = "",
    val diameterObservedAt: Long? = null,
    val heightObservedAt: Long? = null,
    val diameterDeclarationPresent: Boolean = false,
    val heightDeclarationPresent: Boolean = false,
    val position: PositionQualificationDraft? = null,
) {
    /** Déclaration d'appariement autorisée ; cela ne certifie pas la mesure sur le terrain. */
    val canPairHeightWithDiameter: Boolean
        get() = support == ObservationSupport.INDIVIDUAL_TREE &&
            diameterOrigin != MeasurementOrigin.ASSIGNED_REFERENCE &&
            heightNature == HeightNature.TOTAL && heightSupport == ObservationSupport.INDIVIDUAL_TREE &&
            (heightOrigin == MeasurementOrigin.MEASURED || heightOrigin == MeasurementOrigin.ESTIMATED)

    fun withSupport(value: ObservationSupport?): ObservationQualificationFormState =
        copy(support = value).withoutIncompatiblePairing()

    fun withDiameterOrigin(value: MeasurementOrigin?): ObservationQualificationFormState =
        copy(diameterOrigin = value).withoutIncompatiblePairing()

    fun withHeightNature(value: HeightNature?): ObservationQualificationFormState =
        copy(heightNature = value).withoutIncompatiblePairing()

    fun withHeightOrigin(value: MeasurementOrigin?): ObservationQualificationFormState =
        copy(heightOrigin = value).withoutIncompatiblePairing()

    fun withHeightSupport(value: ObservationSupport?): ObservationQualificationFormState =
        copy(heightSupport = value).withoutIncompatiblePairing()

    fun withHeightPairing(value: Boolean?): ObservationQualificationFormState =
        copy(heightPairedWithDiameter = value).withoutIncompatiblePairing()

    fun validation(): ObservationQualificationFormValidation = ObservationQualificationFormValidation(
        diameterOtherConventionReferenceTooLong = diameterConventionChoice == ObservationDiameterConventionChoice.OTHER &&
            diameterOtherConventionReference.referenceTooLong(),
        diameterMethodReferenceTooLong = diameterMethodReference.referenceTooLong(),
        diameterSourceReferenceTooLong = diameterSourceReference.referenceTooLong(),
        heightMethodReferenceTooLong = heightMethodReference.referenceTooLong(),
        heightSourceReferenceTooLong = heightSourceReference.referenceTooLong(),
        invalidDiameterObservedAt = diameterObservedAt?.let { it < 0 } == true,
        invalidHeightObservedAt = heightObservedAt?.let { it < 0 } == true,
        invalidPairing = heightPairedWithDiameter == true && !canPairHeightWithDiameter,
    )

    /** Les champs inconnus restent null ; une incompatibilité bloque la déclaration complète. */
    fun toDraft(): ObservationQualificationDraft? {
        if (!validation().isValid) return null
        val conventionReference = when (diameterConventionChoice) {
            null -> null
            ObservationDiameterConventionChoice.OTHER -> diameterOtherConventionReference.knownReference()
            else -> diameterConventionChoice.localReference
        }
        val diameterMethod = diameterMethodReference.knownReference()
        val diameterSource = diameterSourceReference.knownReference()
        val heightMethod = heightMethodReference.knownReference()
        val heightSource = heightSourceReference.knownReference()
        val hasDiameter = diameterDeclarationPresent || diameterOrigin != null || conventionReference != null ||
            diameterMethod != null || diameterSource != null || diameterObservedAt != null
        val hasHeight = heightDeclarationPresent || heightNature != null || heightOrigin != null ||
            heightSupport != null || heightPairedWithDiameter != null || heightMethod != null ||
            heightSource != null || heightObservedAt != null
        return ObservationQualificationDraft(
            support = support,
            diameter = if (hasDiameter) DiameterQualificationDraft(
                origin = diameterOrigin, conventionReference = conventionReference,
                methodReference = diameterMethod, sourceReference = diameterSource,
                observedAt = diameterObservedAt,
            ) else null,
            height = if (hasHeight) HeightQualificationDraft(
                nature = heightNature, origin = heightOrigin, support = heightSupport,
                pairedWithDiameter = heightPairedWithDiameter, methodReference = heightMethod,
                sourceReference = heightSource, observedAt = heightObservedAt,
            ) else null,
            position = position,
        )
    }

    /** Sauvegarde locale d'UI, même d'une saisie invalide ; aucun format de transport ou archive. */
    fun toSaveableValues(): List<String> = listOf(
        support?.name.orEmpty(), diameterOrigin?.name.orEmpty(), diameterConventionChoice?.name.orEmpty(),
        diameterOtherConventionReference, diameterMethodReference, diameterSourceReference,
        heightNature?.name.orEmpty(), heightOrigin?.name.orEmpty(), heightSupport?.name.orEmpty(),
        heightPairedWithDiameter?.toString().orEmpty(), heightMethodReference, heightSourceReference,
        diameterObservedAt?.toString().orEmpty(), heightObservedAt?.toString().orEmpty(),
        diameterDeclarationPresent.toString(), heightDeclarationPresent.toString(),
        (position != null).toString(), position?.origin?.name.orEmpty(),
        position?.methodReference.orEmpty(), position?.sourceReference.orEmpty(),
        position?.capturedAt?.toString().orEmpty(),
    )

    private fun withoutIncompatiblePairing(): ObservationQualificationFormState =
        if (heightPairedWithDiameter == true && !canPairHeightWithDiameter) {
            copy(heightPairedWithDiameter = null)
        } else this

    companion object {
        const val REFERENCE_MAX_LENGTH = 300
        private const val SAVEABLE_FIELD_COUNT = 21

        /** Aucune normalisation silencieuse d'une ancienne déclaration au chargement. */
        fun fromDraft(draft: ObservationQualificationDraft): ObservationQualificationFormState {
            val conventionReference = draft.diameter?.conventionReference
            val knownConvention = ObservationDiameterConventionChoice.entries.firstOrNull {
                it.localReference != null && it.localReference == conventionReference
            }
            return ObservationQualificationFormState(
                support = draft.support,
                diameterOrigin = draft.diameter?.origin,
                diameterConventionChoice = knownConvention ?: conventionReference?.let {
                    ObservationDiameterConventionChoice.OTHER
                },
                diameterOtherConventionReference = if (knownConvention == null) conventionReference.orEmpty() else "",
                diameterMethodReference = draft.diameter?.methodReference.orEmpty(),
                diameterSourceReference = draft.diameter?.sourceReference.orEmpty(),
                heightNature = draft.height?.nature,
                heightOrigin = draft.height?.origin,
                heightSupport = draft.height?.support,
                heightPairedWithDiameter = draft.height?.pairedWithDiameter,
                heightMethodReference = draft.height?.methodReference.orEmpty(),
                heightSourceReference = draft.height?.sourceReference.orEmpty(),
                diameterObservedAt = draft.diameter?.observedAt,
                heightObservedAt = draft.height?.observedAt,
                diameterDeclarationPresent = draft.diameter != null,
                heightDeclarationPresent = draft.height != null,
                position = draft.position,
            )
        }

        /** Types et nombre de champs stricts ; un état invalide n'est pas remplacé par des défauts. */
        fun fromSaveableValues(values: List<String>): ObservationQualificationFormState? {
            if (values.size != SAVEABLE_FIELD_COUNT) return null
            return try {
                val hasPosition = values[16].requiredBoolean()
                if (!hasPosition && values.subList(17, 21).any { it.isNotEmpty() }) return null
                ObservationQualificationFormState(
                    support = values[0].optionalEnum<ObservationSupport>(),
                    diameterOrigin = values[1].optionalEnum<MeasurementOrigin>(),
                    diameterConventionChoice = values[2].optionalEnum<ObservationDiameterConventionChoice>(),
                    diameterOtherConventionReference = values[3],
                    diameterMethodReference = values[4],
                    diameterSourceReference = values[5],
                    heightNature = values[6].optionalEnum<HeightNature>(),
                    heightOrigin = values[7].optionalEnum<MeasurementOrigin>(),
                    heightSupport = values[8].optionalEnum<ObservationSupport>(),
                    heightPairedWithDiameter = values[9].optionalBoolean(),
                    heightMethodReference = values[10],
                    heightSourceReference = values[11],
                    diameterObservedAt = values[12].optionalLong(),
                    heightObservedAt = values[13].optionalLong(),
                    diameterDeclarationPresent = values[14].requiredBoolean(),
                    heightDeclarationPresent = values[15].requiredBoolean(),
                    position = if (hasPosition) PositionQualificationDraft(
                        origin = values[17].optionalEnum<PositionOrigin>(),
                        methodReference = values[18].takeUnless(String::isEmpty),
                        sourceReference = values[19].takeUnless(String::isEmpty),
                        capturedAt = values[20].optionalLong(),
                    ) else null,
                )
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

data class ObservationQualificationFormValidation(
    val diameterOtherConventionReferenceTooLong: Boolean = false,
    val diameterMethodReferenceTooLong: Boolean = false,
    val diameterSourceReferenceTooLong: Boolean = false,
    val heightMethodReferenceTooLong: Boolean = false,
    val heightSourceReferenceTooLong: Boolean = false,
    val invalidDiameterObservedAt: Boolean = false,
    val invalidHeightObservedAt: Boolean = false,
    val invalidPairing: Boolean = false,
) {
    val isValid: Boolean
        get() = !diameterOtherConventionReferenceTooLong && !diameterMethodReferenceTooLong &&
            !diameterSourceReferenceTooLong && !heightMethodReferenceTooLong && !heightSourceReferenceTooLong &&
            !invalidDiameterObservedAt && !invalidHeightObservedAt && !invalidPairing
}

private fun String.knownReference(): String? = takeUnless(String::isBlank)

private fun String.referenceTooLong(): Boolean = isNotBlank() &&
    length > ObservationQualificationFormState.REFERENCE_MAX_LENGTH

private inline fun <reified T : Enum<T>> String.optionalEnum(): T? =
    if (isEmpty()) null else enumValueOf<T>(this)

private fun String.optionalBoolean(): Boolean? = when (this) {
    "" -> null
    "true" -> true
    "false" -> false
    else -> throw IllegalArgumentException("OBSERVATION_FORM_INVALID_BOOLEAN")
}

private fun String.requiredBoolean(): Boolean = optionalBoolean()
    ?: throw IllegalArgumentException("OBSERVATION_FORM_MISSING_BOOLEAN")

private fun String.optionalLong(): Long? {
    if (isEmpty()) return null
    require(Regex("-?(?:0|[1-9][0-9]*)").matches(this)) { "OBSERVATION_FORM_INVALID_TIMESTAMP" }
    return toLong()
}
