package com.forestry.counter.domain.calculation

import com.forestry.counter.domain.model.HeightNature
import com.forestry.counter.domain.model.MeasurementOrigin
import com.forestry.counter.domain.model.ObservationMeasurementBasis
import com.forestry.counter.domain.model.ObservationQualificationRead
import com.forestry.counter.domain.model.ObservationQualificationRecord
import com.forestry.counter.domain.model.ObservationQualificationStatus
import com.forestry.counter.domain.model.ObservationSupport
import com.forestry.counter.domain.model.Tige
import java.util.Collections

/** Convention explicitement résolue par l'appelant ; aucun texte libre n'est interprété. */
enum class CubageDiameterConvention(val measurementHeightM: Double) {
    BREAST_HEIGHT_1_30_M(1.30),
    BREAST_HEIGHT_1_50_M(1.50),
}

enum class CubageHeightRequirement { NONE, TOTAL_INDIVIDUAL }

/** Origines admises par la méthode, à résoudre explicitement lorsqu'elle consomme une hauteur. */
enum class CubageHeightOriginPolicy { MEASURED_ONLY, MEASURED_OR_ESTIMATED }

/** Lie la référence exacte de la déclaration à une convention, sans certifier sa mesure. */
data class CubageDiameterConventionBinding(
    val reference: String,
    val convention: CubageDiameterConvention?,
)

/**
 * Description immuable fournie par un futur registre, jamais une méthode réelle activée ici.
 * Identifiant, version, référence, empreinte et localisation dans le document sont conservés
 * littéralement. Leur présence ne prouve ni authenticité, ni science, ni licence.
 * Le numéro est explicite si requis ; sa plage applicable appartient au domaine du tarif,
 * pas à une borne universelle inventée par ce noyau. Les champs null restent non résolus.
 */
data class CubageMethodBinding(
    val id: String,
    val version: String,
    val sourceReference: String?,
    val sourceSha256: String?,
    val sourceLocator: String?,
    val diameterConvention: CubageDiameterConvention?,
    val heightRequirement: CubageHeightRequirement?,
    val heightOriginPolicy: CubageHeightOriginPolicy?,
    val requiresTariffNumber: Boolean,
    val tariffNumber: Int? = null,
    val requiresCalibration: Boolean,
    val calibrationReference: String? = null,
)

enum class CubageApplicabilityStatus { IN_DOMAIN, OUTSIDE_DOMAIN, UNKNOWN }

/**
 * Résolution du domaine fournie par l'appelant et attachée à la méthode COMPLÈTE et à l'état
 * brut exact et à la déclaration complète. Les versions contexte/source ne remplacent pas leur
 * examen scientifique réel ; l'appelant doit réévaluer tout contexte extérieur modifié.
 * Le contexte doit porter sur le lieu du relevé/parcelle ; aucun GPS du téléphone n'est consulté.
 */
data class CubageApplicabilityBinding(
    val methodBinding: CubageMethodBinding,
    val qualificationRecord: ObservationQualificationRecord,
    val observationBasisFingerprint: String,
    val parcelleId: String,
    val placetteId: String,
    val sessionId: String,
    val status: CubageApplicabilityStatus,
    val contextReference: String?,
    val contextVersion: String?,
    val sourceReference: String?,
    val sourceVersion: String?,
    val sourceSha256: String?,
    val sourceLocator: String?,
)

enum class CubageInputAvailability { ACCEPTED_INPUTS, UNAVAILABLE }

enum class CubageInputReason(val code: String) {
    OBSERVATION_IDENTITY_MISSING("observation_identity_missing"),
    OBSERVATION_DELETED("observation_deleted"),
    SESSION_MISSING("session_missing"),
    PLOT_MISSING("plot_missing"),
    QUALIFICATION_MISSING("qualification_missing"),
    QUALIFICATION_IDENTITY_MISMATCH("qualification_identity_mismatch"),
    QUALIFICATION_SCOPE_MISMATCH("qualification_scope_mismatch"),
    QUALIFICATION_STALE("qualification_stale"),
    INVALID_DIAMETER("invalid_diameter"),
    MEASUREMENT_BASIS_MISMATCH("measurement_basis_mismatch"),
    INDIVIDUAL_SUPPORT_REQUIRED("individual_support_required"),
    DIAMETER_QUALIFICATION_MISSING("diameter_qualification_missing"),
    MEASURED_DIAMETER_REQUIRED("measured_diameter_required"),
    DIAMETER_CONVENTION_MISSING("diameter_convention_missing"),
    CONVENTION_UNRESOLVED("convention_unresolved"),
    CONVENTION_REFERENCE_MISMATCH("convention_reference_mismatch"),
    DIAMETER_SOURCE_MISSING("diameter_source_missing"),
    METHOD_MISSING("method_missing"),
    METHOD_IDENTITY_MISSING("method_identity_missing"),
    METHOD_SOURCE_MISSING("method_source_missing"),
    METHOD_SOURCE_FINGERPRINT_INVALID("method_source_fingerprint_invalid"),
    METHOD_SOURCE_LOCATOR_MISSING("method_source_locator_missing"),
    METHOD_REQUIREMENTS_UNRESOLVED("method_requirements_unresolved"),
    METHOD_DIAMETER_CONVENTION_MISMATCH("method_diameter_convention_mismatch"),
    TARIFF_NUMBER_MISSING("tariff_number_missing"),
    CALIBRATION_MISSING("calibration_missing"),
    APPLICABILITY_MISSING("applicability_missing"),
    APPLICABILITY_METHOD_MISMATCH("applicability_method_mismatch"),
    APPLICABILITY_QUALIFICATION_MISMATCH("applicability_qualification_mismatch"),
    APPLICABILITY_SCOPE_MISMATCH("applicability_scope_mismatch"),
    APPLICABILITY_BASIS_MISMATCH("applicability_basis_mismatch"),
    APPLICABILITY_CONTEXT_MISSING("applicability_context_missing"),
    APPLICABILITY_SOURCE_MISSING("applicability_source_missing"),
    APPLICABILITY_SOURCE_FINGERPRINT_INVALID("applicability_source_fingerprint_invalid"),
    APPLICABILITY_SOURCE_LOCATOR_MISSING("applicability_source_locator_missing"),
    APPLICABILITY_UNKNOWN("applicability_unknown"),
    OUTSIDE_METHOD_DOMAIN("outside_method_domain"),
    HEIGHT_MISSING("height_missing"),
    INVALID_HEIGHT("invalid_height"),
    HEIGHT_QUALIFICATION_MISSING("height_qualification_missing"),
    INDIVIDUAL_HEIGHT_REQUIRED("individual_height_required"),
    TOTAL_HEIGHT_REQUIRED("total_height_required"),
    HEIGHT_PAIRING_REQUIRED("height_pairing_required"),
    HEIGHT_ORIGIN_UNSUPPORTED("height_origin_unsupported"),
    HEIGHT_ORIGIN_NOT_ALLOWED_BY_METHOD("height_origin_not_allowed_by_method"),
    HEIGHT_SOURCE_MISSING("height_source_missing"),
    HEIGHT_ESTIMATION_METHOD_MISSING("height_estimation_method_missing"),
}

/** Données techniques conditionnelles ; heightM null signifie non consommée, jamais zéro. */
data class CubageAcceptedInputs internal constructor(
    val diameterCm: Double,
    val heightM: Double?,
    val diameterConvention: CubageDiameterConvention,
    val heightOrigin: MeasurementOrigin?,
) {
    init {
        require(diameterCm.isFinite() && diameterCm > 0.0)
        require(if (heightM == null) heightOrigin == null else {
            heightM.isFinite() && heightM > 0.0 &&
                (heightOrigin == MeasurementOrigin.MEASURED || heightOrigin == MeasurementOrigin.ESTIMATED)
        })
    }
}

/** Observation refusée comprise ; filiation, références héritées, dates et auteurs conservés. */
data class CubageInputProvenance(
    val observation: Tige,
    val rawBasisFingerprint: String?,
    val qualification: ObservationQualificationRead?,
    val qualificationBasisFingerprint: String?,
    val methodBinding: CubageMethodBinding?,
    val diameterConventionBinding: CubageDiameterConventionBinding?,
    val applicability: CubageApplicabilityBinding?,
    val kernelVersion: String,
)

data class CubageInputResult internal constructor(
    val availability: CubageInputAvailability,
    val inputs: CubageAcceptedInputs?,
    val reason: CubageInputReason?,
    val provenance: CubageInputProvenance,
) {
    init {
        require(if (availability == CubageInputAvailability.ACCEPTED_INPUTS) {
            inputs != null && reason == null
        } else {
            inputs == null && reason != null
        }) { "CUBAGE_INPUT_INVALID_RESULT" }
    }
}

/**
 * Contrôle pur préalable des entrées, SANS volume, formule, catalogue, repli ou activation.
 * ACCEPTED_INPUTS est conditionnel aux résolutions de l'appelant, jamais une certification
 * de domaine réel, source, science, droits ou licence. CURRENT ne suffit pas : les treize
 * champs de l'état brut sont revérifiés indépendamment. Une référence de classe n'est pas
 * une mesure individuelle, même copiée dans Tige.hauteurM.
 * TOTAL ne certifie pas une HTOT axiale IGN pour un arbre penché : le futur appelant doit résoudre
 * le protocole compatible avec la méthode, sans assimiler hauteur verticale et longueur axiale.
 *
 * L'appelant reste responsable des lectures canoniques, parents vivants, droits, compte et
 * génération, puis de la résolution documentée de la méthode et du territoire de la parcelle.
 * Posséder ces objets ne donne aucun accès. Ce noyau ne lit ni Room, ni réseau, ni GPS courant,
 * ne modifie aucune mesure, et n'est raccordé à aucun rapport, snapshot, UI ou transport.
 */
object CubageInputAdmissibility {
    const val VERSION = "cubage-input-admissibility-1.0.0"
    private val sha256 = Regex("[0-9a-fA-F]{64}")

    fun inspect(
        observation: Tige,
        qualification: ObservationQualificationRead?,
        methodBinding: CubageMethodBinding?,
        diameterConventionBinding: CubageDiameterConventionBinding?,
        applicability: CubageApplicabilityBinding?,
    ): CubageInputResult {
        val basis = qualification?.record?.payload?.basis
        val draft = qualification?.record?.payload?.draft
        val rawFingerprint = try {
            ObservationMeasurementBasis.capture(observation).fingerprint()
        } catch (_: IllegalArgumentException) {
            null
        }
        val provenance = CubageInputProvenance(
            observation = observation.copy(defauts = observation.defauts?.let {
                Collections.unmodifiableList(ArrayList(it))
            }),
            rawBasisFingerprint = rawFingerprint,
            qualification = qualification,
            qualificationBasisFingerprint = basis?.fingerprint(),
            methodBinding = methodBinding,
            diameterConventionBinding = diameterConventionBinding,
            applicability = applicability,
            kernelVersion = VERSION,
        )
        fun unavailable(reason: CubageInputReason) = CubageInputResult(
            CubageInputAvailability.UNAVAILABLE, null, reason, provenance,
        )
        if (observation.id.isBlank() || observation.parcelleId.isBlank() || observation.essenceCode.isBlank()) {
            return unavailable(CubageInputReason.OBSERVATION_IDENTITY_MISSING)
        }
        if (observation.deletedAt != null) return unavailable(CubageInputReason.OBSERVATION_DELETED)
        if (observation.sessionId.isNullOrBlank()) return unavailable(CubageInputReason.SESSION_MISSING)
        if (observation.placetteId.isNullOrBlank()) return unavailable(CubageInputReason.PLOT_MISSING)
        if (qualification == null || basis == null || draft == null) {
            return unavailable(CubageInputReason.QUALIFICATION_MISSING)
        }
        if (qualification.record.tigeId != observation.id || basis.tigeId != observation.id) {
            return unavailable(CubageInputReason.QUALIFICATION_IDENTITY_MISMATCH)
        }
        if (basis.parcelleId != observation.parcelleId || basis.placetteId != observation.placetteId ||
            basis.sessionId != observation.sessionId) {
            return unavailable(CubageInputReason.QUALIFICATION_SCOPE_MISMATCH)
        }
        if (qualification.status != ObservationQualificationStatus.CURRENT) {
            return unavailable(CubageInputReason.QUALIFICATION_STALE)
        }
        if (!observation.diamCm.isFinite() || observation.diamCm <= 0.0) {
            return unavailable(CubageInputReason.INVALID_DIAMETER)
        }
        if (!basis.matches(observation)) return unavailable(CubageInputReason.MEASUREMENT_BASIS_MISMATCH)
        if (draft.support != ObservationSupport.INDIVIDUAL_TREE) {
            return unavailable(CubageInputReason.INDIVIDUAL_SUPPORT_REQUIRED)
        }
        val diameter = draft.diameter
            ?: return unavailable(CubageInputReason.DIAMETER_QUALIFICATION_MISSING)
        if (diameter.origin != MeasurementOrigin.MEASURED) {
            return unavailable(CubageInputReason.MEASURED_DIAMETER_REQUIRED)
        }
        val conventionReference = diameter.conventionReference
            ?: return unavailable(CubageInputReason.DIAMETER_CONVENTION_MISSING)
        if (diameterConventionBinding?.convention == null || diameterConventionBinding.reference.isBlank()) {
            return unavailable(CubageInputReason.CONVENTION_UNRESOLVED)
        }
        if (diameterConventionBinding.reference != conventionReference) {
            return unavailable(CubageInputReason.CONVENTION_REFERENCE_MISMATCH)
        }
        if (diameter.sourceReference.isNullOrBlank()) {
            return unavailable(CubageInputReason.DIAMETER_SOURCE_MISSING)
        }
        if (methodBinding == null) return unavailable(CubageInputReason.METHOD_MISSING)
        if (methodBinding.id.isBlank() || methodBinding.version.isBlank()) {
            return unavailable(CubageInputReason.METHOD_IDENTITY_MISSING)
        }
        if (methodBinding.sourceReference.isNullOrBlank()) return unavailable(CubageInputReason.METHOD_SOURCE_MISSING)
        if (methodBinding.sourceSha256 == null || !sha256.matches(methodBinding.sourceSha256)) {
            return unavailable(CubageInputReason.METHOD_SOURCE_FINGERPRINT_INVALID)
        }
        if (methodBinding.sourceLocator.isNullOrBlank()) {
            return unavailable(CubageInputReason.METHOD_SOURCE_LOCATOR_MISSING)
        }
        if (methodBinding.diameterConvention == null || methodBinding.heightRequirement == null ||
            (methodBinding.heightRequirement == CubageHeightRequirement.TOTAL_INDIVIDUAL &&
                methodBinding.heightOriginPolicy == null)) {
            return unavailable(CubageInputReason.METHOD_REQUIREMENTS_UNRESOLVED)
        }
        if (methodBinding.diameterConvention != diameterConventionBinding.convention) {
            return unavailable(CubageInputReason.METHOD_DIAMETER_CONVENTION_MISMATCH)
        }
        if (methodBinding.requiresTariffNumber && methodBinding.tariffNumber == null) {
            return unavailable(CubageInputReason.TARIFF_NUMBER_MISSING)
        }
        if (methodBinding.requiresCalibration && methodBinding.calibrationReference.isNullOrBlank()) {
            return unavailable(CubageInputReason.CALIBRATION_MISSING)
        }
        if (applicability == null) return unavailable(CubageInputReason.APPLICABILITY_MISSING)
        if (applicability.methodBinding != methodBinding) {
            return unavailable(CubageInputReason.APPLICABILITY_METHOD_MISMATCH)
        }
        if (applicability.qualificationRecord != qualification.record) {
            return unavailable(CubageInputReason.APPLICABILITY_QUALIFICATION_MISMATCH)
        }
        if (applicability.parcelleId != observation.parcelleId ||
            applicability.placetteId != observation.placetteId || applicability.sessionId != observation.sessionId) {
            return unavailable(CubageInputReason.APPLICABILITY_SCOPE_MISMATCH)
        }
        if (applicability.observationBasisFingerprint != rawFingerprint) {
            return unavailable(CubageInputReason.APPLICABILITY_BASIS_MISMATCH)
        }
        if (applicability.contextReference.isNullOrBlank() || applicability.contextVersion.isNullOrBlank()) {
            return unavailable(CubageInputReason.APPLICABILITY_CONTEXT_MISSING)
        }
        if (applicability.sourceReference.isNullOrBlank() || applicability.sourceVersion.isNullOrBlank()) {
            return unavailable(CubageInputReason.APPLICABILITY_SOURCE_MISSING)
        }
        if (applicability.sourceSha256 == null || !sha256.matches(applicability.sourceSha256)) {
            return unavailable(CubageInputReason.APPLICABILITY_SOURCE_FINGERPRINT_INVALID)
        }
        if (applicability.sourceLocator.isNullOrBlank()) {
            return unavailable(CubageInputReason.APPLICABILITY_SOURCE_LOCATOR_MISSING)
        }
        when (applicability.status) {
            CubageApplicabilityStatus.UNKNOWN -> return unavailable(CubageInputReason.APPLICABILITY_UNKNOWN)
            CubageApplicabilityStatus.OUTSIDE_DOMAIN -> return unavailable(CubageInputReason.OUTSIDE_METHOD_DOMAIN)
            CubageApplicabilityStatus.IN_DOMAIN -> Unit
        }
        var heightM: Double? = null
        var heightOrigin: MeasurementOrigin? = null
        if (methodBinding.heightRequirement == CubageHeightRequirement.TOTAL_INDIVIDUAL) {
            heightM = observation.hauteurM ?: return unavailable(CubageInputReason.HEIGHT_MISSING)
            if (!heightM.isFinite() || heightM <= 0.0) return unavailable(CubageInputReason.INVALID_HEIGHT)
            val height = draft.height ?: return unavailable(CubageInputReason.HEIGHT_QUALIFICATION_MISSING)
            if (height.support != ObservationSupport.INDIVIDUAL_TREE) {
                return unavailable(CubageInputReason.INDIVIDUAL_HEIGHT_REQUIRED)
            }
            if (height.nature != HeightNature.TOTAL) return unavailable(CubageInputReason.TOTAL_HEIGHT_REQUIRED)
            if (height.pairedWithDiameter != true) return unavailable(CubageInputReason.HEIGHT_PAIRING_REQUIRED)
            if (height.origin != MeasurementOrigin.MEASURED && height.origin != MeasurementOrigin.ESTIMATED) {
                return unavailable(CubageInputReason.HEIGHT_ORIGIN_UNSUPPORTED)
            }
            if (height.origin == MeasurementOrigin.ESTIMATED &&
                methodBinding.heightOriginPolicy == CubageHeightOriginPolicy.MEASURED_ONLY) {
                return unavailable(CubageInputReason.HEIGHT_ORIGIN_NOT_ALLOWED_BY_METHOD)
            }
            if (height.sourceReference.isNullOrBlank()) return unavailable(CubageInputReason.HEIGHT_SOURCE_MISSING)
            if (height.origin == MeasurementOrigin.ESTIMATED && height.methodReference.isNullOrBlank()) {
                return unavailable(CubageInputReason.HEIGHT_ESTIMATION_METHOD_MISSING)
            }
            heightOrigin = height.origin
        }
        return CubageInputResult(
            CubageInputAvailability.ACCEPTED_INPUTS,
            CubageAcceptedInputs(observation.diamCm, heightM, methodBinding.diameterConvention, heightOrigin),
            null,
            provenance,
        )
    }
}
