package com.forestry.counter.domain.calculation

import com.forestry.counter.domain.model.HeightNature
import com.forestry.counter.domain.model.MeasurementOrigin
import com.forestry.counter.domain.model.ObservationMeasurementBasis
import com.forestry.counter.domain.model.ObservationQualificationRead
import com.forestry.counter.domain.model.ObservationQualificationStatus
import com.forestry.counter.domain.model.ObservationSupport
import com.forestry.counter.domain.model.Tige
import java.util.Collections

/** Interprétation technique explicite ; aucune référence libre n'est analysée comme convention DBH. */
enum class IndividualSlendernessDiameterConvention(val measurementHeightM: Double) {
    BREAST_HEIGHT_1_30_M(1.30),
    BREAST_HEIGHT_1_50_M(1.50),
}

/**
 * Un futur appelant/registre doit fournir cette interprétation de la référence exacte déclarée.
 * Elle documente la convention de diamètre visée, sans vérifier son application sur le terrain.
 * Une convention null reste non résolue ; ce type n'est pas un catalogue ou standard adopté.
 */
data class IndividualSlendernessConventionBinding(
    val reference: String,
    val convention: IndividualSlendernessDiameterConvention?,
)

enum class IndividualSlendernessAvailability { AVAILABLE, UNAVAILABLE }

enum class IndividualSlendernessReason(val code: String) {
    OBSERVATION_IDENTITY_MISSING("observation_identity_missing"),
    OBSERVATION_DELETED("observation_deleted"),
    SESSION_MISSING("session_missing"),
    PLOT_MISSING("plot_missing"),
    QUALIFICATION_MISSING("qualification_missing"),
    QUALIFICATION_IDENTITY_MISMATCH("qualification_identity_mismatch"),
    QUALIFICATION_SCOPE_MISMATCH("qualification_scope_mismatch"),
    QUALIFICATION_STALE("qualification_stale"),
    INVALID_DIAMETER("invalid_diameter"),
    HEIGHT_MISSING("height_missing"),
    INVALID_HEIGHT("invalid_height"),
    MEASUREMENT_BASIS_MISMATCH("measurement_basis_mismatch"),
    INDIVIDUAL_SUPPORT_REQUIRED("individual_support_required"),
    DIAMETER_QUALIFICATION_MISSING("diameter_qualification_missing"),
    MEASURED_DIAMETER_REQUIRED("measured_diameter_required"),
    DIAMETER_CONVENTION_MISSING("diameter_convention_missing"),
    CONVENTION_UNRESOLVED("convention_unresolved"),
    CONVENTION_MISMATCH("convention_mismatch"),
    DIAMETER_SOURCE_MISSING("diameter_source_missing"),
    HEIGHT_QUALIFICATION_MISSING("height_qualification_missing"),
    INDIVIDUAL_HEIGHT_REQUIRED("individual_height_required"),
    TOTAL_HEIGHT_REQUIRED("total_height_required"),
    HEIGHT_PAIRING_REQUIRED("height_pairing_required"),
    HEIGHT_ORIGIN_UNSUPPORTED("height_origin_unsupported"),
    HEIGHT_SOURCE_MISSING("height_source_missing"),
    HEIGHT_ESTIMATION_METHOD_MISSING("height_estimation_method_missing"),
    NUMERIC_DOMAIN_ERROR("numeric_domain_error"),
}

data class IndividualSlendernessFormulaReference(
    val organism: String,
    val authors: List<String>,
    val title: String,
    val edition: String,
    val pdfPage: Int,
    val url: String,
    val sourceSha256: String,
    val expression: String,
    val methodVersion: String,
)

/** Observation exacte présentée au calcul, valeurs ensuite refusées comprises. */
data class IndividualSlendernessObservationReference(
    val id: String,
    val parcelleId: String,
    val placetteId: String?,
    val sessionId: String?,
    val version: Int,
    val timestamp: Long,
    val diameterCm: Double,
    val heightM: Double?,
    val deletedAt: Long?,
    val rawBasisFingerprint: String?,
)

/** La déclaration complète conserve révision, auteurs, dates, état brut, saisie et filiation de copie. */
data class IndividualSlendernessProvenance(
    val observation: IndividualSlendernessObservationReference,
    val qualification: ObservationQualificationRead?,
    val qualificationBasisFingerprint: String?,
    val heightOrigin: MeasurementOrigin?,
    val conventionBinding: IndividualSlendernessConventionBinding?,
    val formula: IndividualSlendernessFormulaReference,
)

data class IndividualSlendernessResult internal constructor(
    val availability: IndividualSlendernessAvailability,
    /** H/D est sans dimension ; null signifie indisponible, jamais un zéro inventé. */
    val ratio: Double?,
    val reason: IndividualSlendernessReason?,
    val provenance: IndividualSlendernessProvenance,
) {
    init {
        require(if (availability == IndividualSlendernessAvailability.AVAILABLE) {
            ratio != null && ratio.isFinite() && ratio > 0.0 && reason == null
        } else {
            ratio == null && reason != null
        }) { "INDIVIDUAL_SLENDERNESS_INVALID_RESULT" }
    }
}

/**
 * H/D descriptif pur d'un arbre explicitement appairé, jamais un volume ou indicateur de peuplement.
 *
 * CURRENT décrit seulement l'ancrage : l'état brut complet est vérifié indépendamment ici.
 * Les hauteurs mesurées et estimées restent distinguées ; aucune n'autorise un seuil de stabilité.
 * Aucun repli sur les anciens champs Tige.source ou Tige.coefficientElancement.
 *
 * Ce noyau ne contrôle ni base de données, ni droits, ni présence effective des parents, ni compte.
 * Son futur appelant doit obtenir observation/déclaration via les dépôts canoniques et maintenir
 * le même compte/génération autorisé et la portée des parents vivants. Posséder ces objets n'accorde
 * aucun accès. Aucun appelant existant, UI, résultat persistant, snapshot ou transport HTTP raccordé.
 */
object IndividualSlendernessCalculator {
    private val formula = IndividualSlendernessFormulaReference(
        organism = "CNPF / e-for-OWN",
        authors = Collections.unmodifiableList(listOf("L. Molines", "E. Sevrin", "F.-X. Valengin")),
        title = "La futaie régulière : diagnostic et gestions envisagées",
        edition = "juin 2019",
        pdfPage = 6,
        url = "https://www.cnpf.fr/sites/socle/files/2024-05/" +
            "13.c_La_futaie_reguliere_diagnostic_et_gestions_envisagees.pdf",
        sourceSha256 = "c8755e7f8d4782b273affc9469217b8903aaa8bc529e6ac01cedc9249146984f",
        expression = "100 * height_m / diameter_cm",
        methodVersion = "individual-slenderness-1.0.0",
    )

    fun calculate(
        observation: Tige,
        qualification: ObservationQualificationRead?,
        conventionBinding: IndividualSlendernessConventionBinding? = null,
    ): IndividualSlendernessResult {
        val basis = qualification?.record?.payload?.basis
        val draft = qualification?.record?.payload?.draft
        val rawFingerprint = try {
            ObservationMeasurementBasis.capture(observation).fingerprint()
        } catch (_: IllegalArgumentException) {
            null
        }
        val provenance = IndividualSlendernessProvenance(
            observation = IndividualSlendernessObservationReference(
                observation.id, observation.parcelleId, observation.placetteId,
                observation.sessionId, observation.version, observation.timestamp,
                observation.diamCm, observation.hauteurM, observation.deletedAt, rawFingerprint,
            ),
            qualification = qualification,
            qualificationBasisFingerprint = basis?.fingerprint(),
            heightOrigin = draft?.height?.origin,
            conventionBinding = conventionBinding,
            formula = formula,
        )
        fun unavailable(reason: IndividualSlendernessReason) = IndividualSlendernessResult(
            IndividualSlendernessAvailability.UNAVAILABLE, null, reason, provenance,
        )
        if (observation.id.isBlank() || observation.parcelleId.isBlank()) {
            return unavailable(IndividualSlendernessReason.OBSERVATION_IDENTITY_MISSING)
        }
        if (observation.deletedAt != null) {
            return unavailable(IndividualSlendernessReason.OBSERVATION_DELETED)
        }
        if (observation.sessionId.isNullOrBlank()) {
            return unavailable(IndividualSlendernessReason.SESSION_MISSING)
        }
        if (observation.placetteId.isNullOrBlank()) {
            return unavailable(IndividualSlendernessReason.PLOT_MISSING)
        }
        if (qualification == null || basis == null || draft == null) {
            return unavailable(IndividualSlendernessReason.QUALIFICATION_MISSING)
        }
        if (qualification.record.tigeId != observation.id || basis.tigeId != observation.id) {
            return unavailable(IndividualSlendernessReason.QUALIFICATION_IDENTITY_MISMATCH)
        }
        if (basis.parcelleId != observation.parcelleId ||
            basis.placetteId != observation.placetteId || basis.sessionId != observation.sessionId) {
            return unavailable(IndividualSlendernessReason.QUALIFICATION_SCOPE_MISMATCH)
        }
        if (qualification.status != ObservationQualificationStatus.CURRENT) {
            return unavailable(IndividualSlendernessReason.QUALIFICATION_STALE)
        }
        if (!observation.diamCm.isFinite() || observation.diamCm <= 0.0) {
            return unavailable(IndividualSlendernessReason.INVALID_DIAMETER)
        }
        val height = observation.hauteurM
            ?: return unavailable(IndividualSlendernessReason.HEIGHT_MISSING)
        if (!height.isFinite() || height <= 0.0) {
            return unavailable(IndividualSlendernessReason.INVALID_HEIGHT)
        }
        if (!basis.matches(observation)) {
            return unavailable(IndividualSlendernessReason.MEASUREMENT_BASIS_MISMATCH)
        }
        if (draft.support != ObservationSupport.INDIVIDUAL_TREE) {
            return unavailable(IndividualSlendernessReason.INDIVIDUAL_SUPPORT_REQUIRED)
        }
        val diameter = draft.diameter
            ?: return unavailable(IndividualSlendernessReason.DIAMETER_QUALIFICATION_MISSING)
        if (diameter.origin != MeasurementOrigin.MEASURED) {
            return unavailable(IndividualSlendernessReason.MEASURED_DIAMETER_REQUIRED)
        }
        val conventionReference = diameter.conventionReference
            ?: return unavailable(IndividualSlendernessReason.DIAMETER_CONVENTION_MISSING)
        if (conventionBinding?.convention == null || conventionBinding.reference.isBlank()) {
            return unavailable(IndividualSlendernessReason.CONVENTION_UNRESOLVED)
        }
        if (conventionBinding.reference != conventionReference) {
            return unavailable(IndividualSlendernessReason.CONVENTION_MISMATCH)
        }
        if (diameter.sourceReference == null) {
            return unavailable(IndividualSlendernessReason.DIAMETER_SOURCE_MISSING)
        }
        val heightQualification = draft.height
            ?: return unavailable(IndividualSlendernessReason.HEIGHT_QUALIFICATION_MISSING)
        if (heightQualification.support != ObservationSupport.INDIVIDUAL_TREE) {
            return unavailable(IndividualSlendernessReason.INDIVIDUAL_HEIGHT_REQUIRED)
        }
        if (heightQualification.nature != HeightNature.TOTAL) {
            return unavailable(IndividualSlendernessReason.TOTAL_HEIGHT_REQUIRED)
        }
        if (heightQualification.pairedWithDiameter != true) {
            return unavailable(IndividualSlendernessReason.HEIGHT_PAIRING_REQUIRED)
        }
        if (heightQualification.origin != MeasurementOrigin.MEASURED &&
            heightQualification.origin != MeasurementOrigin.ESTIMATED) {
            return unavailable(IndividualSlendernessReason.HEIGHT_ORIGIN_UNSUPPORTED)
        }
        if (heightQualification.sourceReference == null) {
            return unavailable(IndividualSlendernessReason.HEIGHT_SOURCE_MISSING)
        }
        if (heightQualification.origin == MeasurementOrigin.ESTIMATED &&
            heightQualification.methodReference == null) {
            return unavailable(IndividualSlendernessReason.HEIGHT_ESTIMATION_METHOD_MISSING)
        }

        // Mettre les mantisses binaires à l'échelle avant de restaurer leur exposant. Leur quotient
        // et le facteur 100 restent représentables, même pour des entrées sous-normales ; seul le
        // résultat final peut subir un sous-flux/débordement. Ni 100*height ni height/diameter ne
        // sont évalués sur les mesures brutes.
        val heightExponent = Math.getExponent(height)
        val diameterExponent = Math.getExponent(observation.diamCm)
        val scaledHeight = Math.scalb(height, -heightExponent)
        val scaledDiameter = Math.scalb(observation.diamCm, -diameterExponent)
        val ratio = Math.scalb(
            (scaledHeight / scaledDiameter) * 100.0,
            heightExponent - diameterExponent,
        )
        if (!ratio.isFinite() || ratio <= 0.0) {
            return unavailable(IndividualSlendernessReason.NUMERIC_DOMAIN_ERROR)
        }
        return IndividualSlendernessResult(
            IndividualSlendernessAvailability.AVAILABLE, ratio, null, provenance,
        )
    }
}
