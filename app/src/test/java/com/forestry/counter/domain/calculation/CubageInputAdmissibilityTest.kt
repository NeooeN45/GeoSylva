package com.forestry.counter.domain.calculation

import com.forestry.counter.domain.model.DiameterQualificationDraft
import com.forestry.counter.domain.model.HeightNature
import com.forestry.counter.domain.model.HeightQualificationDraft
import com.forestry.counter.domain.model.MeasurementOrigin
import com.forestry.counter.domain.model.ObservationMeasurementBasis
import com.forestry.counter.domain.model.ObservationQualificationCopySource
import com.forestry.counter.domain.model.ObservationQualificationDraft
import com.forestry.counter.domain.model.ObservationQualificationPayload
import com.forestry.counter.domain.model.ObservationQualificationRead
import com.forestry.counter.domain.model.ObservationQualificationRecord
import com.forestry.counter.domain.model.ObservationQualificationStatus
import com.forestry.counter.domain.model.ObservationSupport
import com.forestry.counter.domain.model.PositionOrigin
import com.forestry.counter.domain.model.PositionQualificationDraft
import com.forestry.counter.domain.model.Tige
import org.junit.Assert.*
import org.junit.Test

/** All bindings below are TEST fixtures, never a real tariff, source or adopted domain. */
class CubageInputAdmissibilityTest {
    private val tree = Tige(
        id = "TEST-TREE", parcelleId = "TEST-PARCEL", placetteId = "TEST-PLOT",
        essenceCode = "TEST-OAK", diamCm = 40.0, hauteurM = 20.0,
        gpsWkt = "POINT(0 0)", precisionM = 3.0, altitudeM = 100.0, timestamp = 1000,
        note = "TEST raw note", produit = "TEST product", fCoef = 0.9, valueEur = 250.0,
        defauts = listOf("TEST defect"), sessionId = "TEST-SESSION", version = 4,
        source = "TEST legacy source does not qualify anything", coefficientElancement = 999.0,
    )
    private val convention = CubageDiameterConventionBinding(
        reference = "TEST exact diameter convention", convention = CubageDiameterConvention.BREAST_HEIGHT_1_30_M,
    )
    private val method = CubageMethodBinding(
        id = "TEST method", version = "TEST version 1", sourceReference = "TEST document",
        sourceSha256 = "a".repeat(64), sourceLocator = "TEST edition/page/table",
        diameterConvention = CubageDiameterConvention.BREAST_HEIGHT_1_30_M,
        heightRequirement = CubageHeightRequirement.TOTAL_INDIVIDUAL,
        heightOriginPolicy = CubageHeightOriginPolicy.MEASURED_OR_ESTIMATED,
        requiresTariffNumber = true, tariffNumber = 7,
        requiresCalibration = true, calibrationReference = "TEST calibration revision 3",
    )
    private val diameter = DiameterQualificationDraft(
        origin = MeasurementOrigin.MEASURED, conventionReference = convention.reference,
        methodReference = "TEST caliper", sourceReference = "TEST diameter source  ", observedAt = 900,
    )
    private val height = HeightQualificationDraft(
        nature = HeightNature.TOTAL, origin = MeasurementOrigin.MEASURED,
        support = ObservationSupport.INDIVIDUAL_TREE, pairedWithDiameter = true,
        methodReference = "TEST hypsometer", sourceReference = "TEST height source", observedAt = 901,
    )
    private val draft = ObservationQualificationDraft(
        support = ObservationSupport.INDIVIDUAL_TREE, diameter = diameter, height = height,
        position = PositionQualificationDraft(
            origin = PositionOrigin.REUSED_POINT, sourceReference = "TEST inherited plot GPS",
        ),
    )

    private fun read(
        observation: Tige = tree,
        declaration: ObservationQualificationDraft = draft,
        status: ObservationQualificationStatus = ObservationQualificationStatus.CURRENT,
    ) = ObservationQualificationRead(
        record = ObservationQualificationRecord(
            tigeId = observation.id, revision = 7,
            payload = ObservationQualificationPayload(
                basis = ObservationMeasurementBasis.capture(observation), draft = declaration,
                copiedFrom = ObservationQualificationCopySource("TEST-ORIGINAL", 2, 800, "TEST-AUTHOR"),
            ),
            createdAt = 1001, createdBy = "TEST-CREATOR", updatedAt = 1002, updatedBy = "TEST-EDITOR",
        ),
        status = status,
    )

    private fun domain(
        observation: Tige = tree,
        boundMethod: CubageMethodBinding = method,
        qualificationRecord: ObservationQualificationRecord = read(observation).record,
    ) = CubageApplicabilityBinding(
        methodBinding = boundMethod,
        qualificationRecord = qualificationRecord,
        observationBasisFingerprint = ObservationMeasurementBasis.capture(observation).fingerprint(),
        parcelleId = observation.parcelleId, placetteId = observation.placetteId!!, sessionId = observation.sessionId!!,
        status = CubageApplicabilityStatus.IN_DOMAIN,
        contextReference = "TEST parcel environmental context", contextVersion = "TEST context revision 5",
        sourceReference = "TEST domain document", sourceVersion = "TEST source edition 2",
        sourceSha256 = "b".repeat(64), sourceLocator = "TEST domain table 9",
    )

    private fun inspect(
        observation: Tige = tree,
        declaration: ObservationQualificationDraft = draft,
        boundMethod: CubageMethodBinding? = method,
        boundConvention: CubageDiameterConventionBinding? = convention,
        qualification: ObservationQualificationRead? = read(observation, declaration),
        applicability: CubageApplicabilityBinding? = domain(observation, boundMethod ?: method,
            qualification?.record ?: read(observation).record),
    ) = CubageInputAdmissibility.inspect(observation, qualification, boundMethod, boundConvention, applicability)

    private fun refused(result: CubageInputResult, reason: CubageInputReason) {
        assertEquals(CubageInputAvailability.UNAVAILABLE, result.availability)
        assertEquals(reason, result.reason)
        assertNull(result.inputs)
    }

    private fun accepted(result: CubageInputResult): CubageAcceptedInputs {
        assertEquals(CubageInputAvailability.ACCEPTED_INPUTS, result.availability)
        assertNull(result.reason)
        assertNotNull(result.inputs)
        return result.inputs!!
    }

    @Test fun `measured individual total inputs retain dimensions and origin without calculating volume`() {
        val inputs = accepted(inspect())
        assertEquals(40.0, inputs.diameterCm, 0.0)
        assertEquals(20.0, inputs.heightM!!, 0.0)
        assertEquals(MeasurementOrigin.MEASURED, inputs.heightOrigin)
        assertEquals(CubageDiameterConvention.BREAST_HEIGHT_1_30_M, inputs.diameterConvention)
    }

    @Test fun `estimated total height remains estimated with its declared method and source`() {
        val estimated = draft.copy(height = height.copy(
            origin = MeasurementOrigin.ESTIMATED, methodReference = "TEST estimation model revision 2",
            sourceReference = "TEST estimate source",
        ))
        val result = inspect(declaration = estimated)
        assertEquals(MeasurementOrigin.ESTIMATED, accepted(result).heightOrigin)
        assertEquals(estimated.height, result.provenance.qualification!!.record.payload.draft.height)
    }

    @Test fun `legacy source height coefficient and price do not replace missing qualification`() {
        val result = inspect(qualification = null)
        refused(result, CubageInputReason.QUALIFICATION_MISSING)
        assertEquals(tree.source, result.provenance.observation.source)
        assertEquals(tree.hauteurM, result.provenance.observation.hauteurM)
        assertEquals(tree.valueEur, result.provenance.observation.valueEur)
    }

    @Test fun `missing tree parcel or essence identity is unavailable`() {
        for (row in listOf(tree.copy(id = " "), tree.copy(parcelleId = ""), tree.copy(essenceCode = ""))) {
            refused(inspect(row, qualification = read(), applicability = domain()),
                CubageInputReason.OBSERVATION_IDENTITY_MISSING)
        }
    }

    @Test fun `deleted observations cannot be accepted even with unchanged thirteen field basis`() {
        val result = inspect(tree.copy(deletedAt = 2000), qualification = read(), applicability = domain())
        refused(result, CubageInputReason.OBSERVATION_DELETED)
        assertEquals(2000L, result.provenance.observation.deletedAt)
    }

    @Test fun `missing or blank session is unavailable`() {
        for (session in listOf(null, "", " ")) {
            refused(inspect(tree.copy(sessionId = session), qualification = read(), applicability = domain()),
                CubageInputReason.SESSION_MISSING)
        }
    }

    @Test fun `missing or blank plot is unavailable`() {
        for (plot in listOf(null, "", " ")) {
            refused(inspect(tree.copy(placetteId = plot), qualification = read(), applicability = domain()),
                CubageInputReason.PLOT_MISSING)
        }
    }

    @Test fun `qualification from another tree cannot qualify identical numeric dimensions`() {
        val other = read(tree.copy(id = "TEST-OTHER-TREE"))
        refused(inspect(qualification = other), CubageInputReason.QUALIFICATION_IDENTITY_MISMATCH)
    }

    @Test fun `reassignment to another parcel plot or session makes old qualification unusable`() {
        for (row in listOf(tree.copy(parcelleId = "TEST-OTHER"), tree.copy(placetteId = "TEST-OTHER"),
            tree.copy(sessionId = "TEST-OTHER"))) {
            refused(inspect(row, qualification = read(), applicability = domain()),
                CubageInputReason.QUALIFICATION_SCOPE_MISMATCH)
        }
    }

    @Test fun `STALE cannot be accepted even with matching basis and declared in domain`() {
        refused(inspect(qualification = read(status = ObservationQualificationStatus.STALE)),
            CubageInputReason.QUALIFICATION_STALE)
    }

    @Test fun `CURRENT does not bypass any of the thirteen raw basis fields`() {
        val rows = listOf(
            tree.copy(id = "TEST-OTHER"), tree.copy(parcelleId = "TEST-OTHER"),
            tree.copy(placetteId = "TEST-OTHER"), tree.copy(sessionId = "TEST-OTHER"),
            tree.copy(originTigeId = "TEST-ORIGIN"), tree.copy(essenceCode = "TEST-PINE"),
            tree.copy(diamCm = 41.0), tree.copy(hauteurM = 21.0), tree.copy(gpsWkt = "POINT(1 1)"),
            tree.copy(precisionM = 4.0), tree.copy(altitudeM = 101.0), tree.copy(timestamp = 1001),
            tree.copy(version = 5),
        )
        for ((index, row) in rows.withIndex()) {
            val result = inspect(row, qualification = read(), applicability = domain())
            assertEquals(CubageInputAvailability.UNAVAILABLE, result.availability)
            assertEquals(when (index) {
                0 -> CubageInputReason.QUALIFICATION_IDENTITY_MISMATCH
                1, 2, 3 -> CubageInputReason.QUALIFICATION_SCOPE_MISMATCH
                else -> CubageInputReason.MEASUREMENT_BASIS_MISMATCH
            }, result.reason)
            assertNull(result.inputs)
        }
    }

    @Test fun `returning to old measurements with new observation revision does not restore applicability`() {
        refused(inspect(tree.copy(version = 6), qualification = read(), applicability = domain()),
            CubageInputReason.MEASUREMENT_BASIS_MISMATCH)
    }

    @Test fun `zero negative infinite or NaN diameters do not become valid from current declaration`() {
        for (diam in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            refused(inspect(tree.copy(diamCm = diam), qualification = read(), applicability = domain()),
                CubageInputReason.INVALID_DIAMETER)
        }
    }

    @Test fun `unknown or collective support is refused even if numeric values resemble a tree`() {
        for (support in listOf(null, ObservationSupport.GROUP_REFERENCE)) {
            refused(inspect(declaration = draft.copy(support = support,
                height = height.copy(pairedWithDiameter = null))), CubageInputReason.INDIVIDUAL_SUPPORT_REQUIRED)
        }
    }

    @Test fun `missing diameter declaration stays unavailable`() {
        refused(inspect(declaration = draft.copy(diameter = null)), CubageInputReason.DIAMETER_QUALIFICATION_MISSING)
    }

    @Test fun `estimated assigned and unknown diameters cannot stand in for measured DBH`() {
        for (origin in listOf(null, MeasurementOrigin.ESTIMATED, MeasurementOrigin.ASSIGNED_REFERENCE)) {
            refused(inspect(declaration = draft.copy(diameter = diameter.copy(origin = origin),
                height = height.copy(pairedWithDiameter = null))), CubageInputReason.MEASURED_DIAMETER_REQUIRED)
        }
    }

    @Test fun `missing diameter convention declaration is not inferred from caller binding`() {
        refused(inspect(declaration = draft.copy(diameter = diameter.copy(conventionReference = null))),
            CubageInputReason.DIAMETER_CONVENTION_MISSING)
    }

    @Test fun `unresolved convention does not interpret a well known textual label`() {
        val labelled = draft.copy(diameter = diameter.copy(conventionReference = "DBH 1.30 m"))
        for (binding in listOf(null, convention.copy(convention = null), convention.copy(reference = " "))) {
            refused(inspect(declaration = labelled, boundConvention = binding), CubageInputReason.CONVENTION_UNRESOLVED)
        }
    }

    @Test fun `declared and resolved convention references must match literally without trimming or case inference`() {
        for (reference in listOf(convention.reference + " ", convention.reference.lowercase(), "TEST other protocol")) {
            refused(inspect(boundConvention = convention.copy(reference = reference)),
                CubageInputReason.CONVENTION_REFERENCE_MISMATCH)
        }
    }

    @Test fun `diameter without source cannot be made admissible by a method document`() {
        refused(inspect(declaration = draft.copy(diameter = diameter.copy(sourceReference = null))),
            CubageInputReason.DIAMETER_SOURCE_MISSING)
    }

    @Test fun `DBH at one metre fifty is not converted to method requirement one metre thirty`() {
        refused(inspect(boundConvention = convention.copy(convention = CubageDiameterConvention.BREAST_HEIGHT_1_50_M)),
            CubageInputReason.METHOD_DIAMETER_CONVENTION_MISMATCH)
    }

    @Test fun `DBH at one metre fifty is accepted only when method explicitly requires same convention`() {
        val matching = method.copy(diameterConvention = CubageDiameterConvention.BREAST_HEIGHT_1_50_M)
        val inputs = accepted(inspect(boundMethod = matching,
            boundConvention = convention.copy(convention = CubageDiameterConvention.BREAST_HEIGHT_1_50_M)))
        assertEquals(CubageDiameterConvention.BREAST_HEIGHT_1_50_M, inputs.diameterConvention)
        assertEquals(40.0, inputs.diameterCm, 0.0)
    }

    @Test fun `method is never selected implicitly from species or numeric dimensions`() {
        refused(inspect(boundMethod = null), CubageInputReason.METHOD_MISSING)
    }

    @Test fun `method identity and version must be explicit`() {
        for (binding in listOf(method.copy(id = " "), method.copy(version = ""))) {
            refused(inspect(boundMethod = binding), CubageInputReason.METHOD_IDENTITY_MISSING)
        }
    }

    @Test fun `method document reference is required independently of its digest`() {
        for (reference in listOf(null, "", " ")) {
            refused(inspect(boundMethod = method.copy(sourceReference = reference)), CubageInputReason.METHOD_SOURCE_MISSING)
        }
    }

    @Test fun `method SHA256 must be a complete hexadecimal digest without normalization`() {
        for (digest in listOf(null, "", "a".repeat(63), "g".repeat(64), "a".repeat(64) + " ")) {
            refused(inspect(boundMethod = method.copy(sourceSha256 = digest)),
                CubageInputReason.METHOD_SOURCE_FINGERPRINT_INVALID)
        }
        val uppercase = method.copy(sourceSha256 = "AB".repeat(32))
        assertEquals(uppercase.sourceSha256, inspect(boundMethod = uppercase).also { accepted(it) }.provenance.methodBinding!!.sourceSha256)
    }

    @Test fun `method edition page or table locator is not fabricated`() {
        refused(inspect(boundMethod = method.copy(sourceLocator = null)), CubageInputReason.METHOD_SOURCE_LOCATOR_MISSING)
    }

    @Test fun `method diameter and height requirements cannot remain unresolved`() {
        for (binding in listOf(method.copy(diameterConvention = null), method.copy(heightRequirement = null),
            method.copy(heightOriginPolicy = null))) {
            refused(inspect(boundMethod = binding), CubageInputReason.METHOD_REQUIREMENTS_UNRESOLVED)
        }
    }

    @Test fun `required tariff number is not recommended from a default or a height`() {
        refused(inspect(boundMethod = method.copy(tariffNumber = null)), CubageInputReason.TARIFF_NUMBER_MISSING)
    }

    @Test fun `number is optional only when method explicitly does not require it`() {
        accepted(inspect(boundMethod = method.copy(requiresTariffNumber = false, tariffNumber = null)))
    }

    @Test fun `required calibration cannot be omitted or replaced by a blank reference`() {
        for (reference in listOf(null, "", " ")) {
            refused(inspect(boundMethod = method.copy(calibrationReference = reference)), CubageInputReason.CALIBRATION_MISSING)
        }
    }

    @Test fun `calibration is optional only when method explicitly does not require it`() {
        accepted(inspect(boundMethod = method.copy(requiresCalibration = false, calibrationReference = null)))
    }

    @Test fun `missing domain resolution is not guessed from location or species`() {
        refused(inspect(applicability = null), CubageInputReason.APPLICABILITY_MISSING)
    }

    @Test fun `unknown and outside domain are unavailable despite complete method and measurements`() {
        refused(inspect(applicability = domain().copy(status = CubageApplicabilityStatus.UNKNOWN)),
            CubageInputReason.APPLICABILITY_UNKNOWN)
        refused(inspect(applicability = domain().copy(status = CubageApplicabilityStatus.OUTSIDE_DOMAIN)),
            CubageInputReason.OUTSIDE_METHOD_DOMAIN)
    }

    @Test fun `same method ID and version do not conceal modified source or requirements`() {
        val modifications = listOf(
            method.copy(sourceReference = "TEST other source"), method.copy(sourceSha256 = "c".repeat(64)),
            method.copy(sourceLocator = "TEST another page"),
            method.copy(diameterConvention = CubageDiameterConvention.BREAST_HEIGHT_1_50_M),
            method.copy(heightRequirement = CubageHeightRequirement.NONE),
            method.copy(heightOriginPolicy = CubageHeightOriginPolicy.MEASURED_ONLY),
        )
        for (changed in modifications) {
            refused(inspect(applicability = domain(boundMethod = changed)), CubageInputReason.APPLICABILITY_METHOD_MISMATCH)
        }
    }

    @Test fun `tariff number changed under same method identity requires a new domain binding`() {
        refused(inspect(boundMethod = method.copy(tariffNumber = 20), applicability = domain()),
            CubageInputReason.APPLICABILITY_METHOD_MISMATCH)
    }

    @Test fun `calibration changed under same method identity requires a new domain binding`() {
        refused(inspect(boundMethod = method.copy(calibrationReference = "TEST calibration revision 4"), applicability = domain()),
            CubageInputReason.APPLICABILITY_METHOD_MISMATCH)
    }

    @Test fun `domain method equality includes whether tariff number and calibration are required`() {
        for (changed in listOf(method.copy(requiresTariffNumber = false), method.copy(requiresCalibration = false))) {
            refused(inspect(applicability = domain(boundMethod = changed)), CubageInputReason.APPLICABILITY_METHOD_MISMATCH)
        }
    }

    @Test fun `domain tied to another parcel plot or session cannot be reused`() {
        for (changed in listOf(domain().copy(parcelleId = "TEST-OTHER"), domain().copy(placetteId = "TEST-OTHER"),
            domain().copy(sessionId = "TEST-OTHER"))) {
            refused(inspect(applicability = changed), CubageInputReason.APPLICABILITY_SCOPE_MISMATCH)
        }
    }

    @Test fun `domain must match actual complete basis even when qualification is fresh`() {
        val otherContext = domain(tree.copy(altitudeM = 101.0), qualificationRecord = read().record)
        refused(inspect(applicability = otherContext), CubageInputReason.APPLICABILITY_BASIS_MISMATCH)
    }

    @Test fun `changing qualification alone cannot reuse prior domain decision on identical raw numbers`() {
        val oldDomain = domain()
        val declarations = listOf(
            draft.copy(height = height.copy(origin = MeasurementOrigin.ESTIMATED, methodReference = "TEST estimate method")),
            draft.copy(height = height.copy(sourceReference = "TEST different height source")),
            draft.copy(height = height.copy(nature = HeightNature.MERCHANTABLE, pairedWithDiameter = null)),
            draft.copy(diameter = diameter.copy(methodReference = "TEST different diameter method")),
            draft.copy(position = draft.position!!.copy(sourceReference = "TEST changed position provenance")),
        )
        for (declaration in declarations) {
            val result = inspect(declaration = declaration, applicability = oldDomain)
            refused(result, CubageInputReason.APPLICABILITY_QUALIFICATION_MISMATCH)
            assertEquals(oldDomain.observationBasisFingerprint, result.provenance.rawBasisFingerprint)
        }
    }

    @Test fun `domain qualification equality includes revision authors dates sealed state and lineage`() {
        val original = read()
        val records = listOf(
            original.record.copy(revision = 8), original.record.copy(createdAt = 1000),
            original.record.copy(createdBy = "TEST other creator"), original.record.copy(updatedAt = 1003),
            original.record.copy(updatedBy = "TEST other editor"), original.record.copy(isSealed = true),
            original.record.copy(payload = original.record.payload.copy(copiedFrom = null)),
        )
        for (record in records) {
            refused(inspect(qualification = original.copy(record = record), applicability = domain()),
                CubageInputReason.APPLICABILITY_QUALIFICATION_MISMATCH)
        }
    }

    @Test fun `equivalent immutable copies of complete method and record remain usable`() {
        val qualification = read()
        val equivalentDomain = domain(boundMethod = method.copy(), qualificationRecord = qualification.record.copy())
        accepted(inspect(qualification = qualification, applicability = equivalentDomain))
    }

    @Test fun `domain context and revision cannot remain unknown`() {
        for (binding in listOf(domain().copy(contextReference = null), domain().copy(contextVersion = " "))) {
            refused(inspect(applicability = binding), CubageInputReason.APPLICABILITY_CONTEXT_MISSING)
        }
    }

    @Test fun `domain source and source version are both required`() {
        for (binding in listOf(domain().copy(sourceReference = null), domain().copy(sourceVersion = ""))) {
            refused(inspect(applicability = binding), CubageInputReason.APPLICABILITY_SOURCE_MISSING)
        }
    }

    @Test fun `domain source digest must identify exact document bytes`() {
        for (digest in listOf(null, "x".repeat(64), "a".repeat(63))) {
            refused(inspect(applicability = domain().copy(sourceSha256 = digest)),
                CubageInputReason.APPLICABILITY_SOURCE_FINGERPRINT_INVALID)
        }
    }

    @Test fun `domain document locator cannot be omitted`() {
        refused(inspect(applicability = domain().copy(sourceLocator = null)),
            CubageInputReason.APPLICABILITY_SOURCE_LOCATOR_MISSING)
    }

    @Test fun `required height cannot be supplied by an existing height declaration alone`() {
        refused(inspect(tree.copy(hauteurM = null)), CubageInputReason.HEIGHT_MISSING)
    }

    @Test fun `zero height is raw data but cannot satisfy a total individual height requirement`() {
        refused(inspect(tree.copy(hauteurM = 0.0)), CubageInputReason.INVALID_HEIGHT)
    }

    @Test fun `negative infinite or NaN height cannot pass complete basis validation`() {
        for (height in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            refused(inspect(tree.copy(hauteurM = height), qualification = read(), applicability = domain()),
                CubageInputReason.MEASUREMENT_BASIS_MISMATCH)
        }
    }

    @Test fun `numeric total height with no declared nature does not qualify itself`() {
        refused(inspect(declaration = draft.copy(height = null)), CubageInputReason.HEIGHT_QUALIFICATION_MISSING)
    }

    @Test fun `unknown and collective reference heights cannot be duplicated into individual inputs`() {
        for (support in listOf(null, ObservationSupport.GROUP_REFERENCE)) {
            refused(inspect(declaration = draft.copy(height = height.copy(support = support, pairedWithDiameter = null))),
                CubageInputReason.INDIVIDUAL_HEIGHT_REQUIRED)
        }
    }

    @Test fun `merchantable or unknown nature cannot substitute total height`() {
        for (nature in listOf(null, HeightNature.MERCHANTABLE)) {
            refused(inspect(declaration = draft.copy(height = height.copy(nature = nature, pairedWithDiameter = null))),
                CubageInputReason.TOTAL_HEIGHT_REQUIRED)
        }
    }

    @Test fun `same numeric dimensions do not prove pairing on the same physical tree`() {
        for (paired in listOf(null, false)) {
            refused(inspect(declaration = draft.copy(height = height.copy(pairedWithDiameter = paired))),
                CubageInputReason.HEIGHT_PAIRING_REQUIRED)
        }
    }

    @Test fun `unknown height origin cannot silently become measured`() {
        refused(inspect(declaration = draft.copy(height = height.copy(origin = null))),
            CubageInputReason.HEIGHT_ORIGIN_UNSUPPORTED)
    }

    @Test fun `assigned class reference is refused rather than treated as measured tree height`() {
        val assigned = draft.copy(height = height.copy(
            origin = MeasurementOrigin.ASSIGNED_REFERENCE, pairedWithDiameter = null,
        ))
        val result = inspect(declaration = assigned)
        assertEquals(CubageInputAvailability.UNAVAILABLE, result.availability)
        assertNull(result.inputs)
        assertEquals(MeasurementOrigin.ASSIGNED_REFERENCE, result.provenance.qualification!!.record.payload.draft.height!!.origin)
    }

    @Test fun `height without source cannot gain one from the diameter or method document`() {
        refused(inspect(declaration = draft.copy(height = height.copy(sourceReference = null))),
            CubageInputReason.HEIGHT_SOURCE_MISSING)
    }

    @Test fun `estimated total height needs its own explicit estimation method`() {
        refused(inspect(declaration = draft.copy(height = height.copy(origin = MeasurementOrigin.ESTIMATED, methodReference = null))),
            CubageInputReason.HEIGHT_ESTIMATION_METHOD_MISSING)
    }

    @Test fun `measured only method refuses estimated height even with explicit estimation source and method`() {
        val estimated = draft.copy(height = height.copy(origin = MeasurementOrigin.ESTIMATED,
            sourceReference = "TEST estimate source", methodReference = "TEST estimate method"))
        refused(inspect(declaration = estimated, boundMethod = method.copy(heightOriginPolicy = CubageHeightOriginPolicy.MEASURED_ONLY)),
            CubageInputReason.HEIGHT_ORIGIN_NOT_ALLOWED_BY_METHOD)
    }

    @Test fun `measured only method accepts a measured total height without relabelling its origin`() {
        val result = inspect(boundMethod = method.copy(heightOriginPolicy = CubageHeightOriginPolicy.MEASURED_ONLY))
        assertEquals(MeasurementOrigin.MEASURED, accepted(result).heightOrigin)
    }

    @Test fun `estimated height accepted only with explicit policy never erases missing source or estimation method`() {
        val estimated = height.copy(origin = MeasurementOrigin.ESTIMATED, methodReference = "TEST estimation method")
        refused(inspect(declaration = draft.copy(height = estimated.copy(sourceReference = null))),
            CubageInputReason.HEIGHT_SOURCE_MISSING)
        refused(inspect(declaration = draft.copy(height = estimated.copy(methodReference = null))),
            CubageInputReason.HEIGHT_ESTIMATION_METHOD_MISSING)
        assertEquals(MeasurementOrigin.ESTIMATED, accepted(inspect(declaration = draft.copy(height = estimated))).heightOrigin)
    }

    @Test fun `one entry method accepts a missing height and no height declaration`() {
        val singleEntry = method.copy(heightRequirement = CubageHeightRequirement.NONE)
        val result = inspect(tree.copy(hauteurM = null), draft.copy(height = null), singleEntry)
        val inputs = accepted(result)
        assertNull(inputs.heightM)
        assertNull(inputs.heightOrigin)
        assertNull(result.provenance.observation.hauteurM)
    }

    @Test fun `one entry method never needs or consumes an unused height origin policy`() {
        for (policy in listOf(null, CubageHeightOriginPolicy.MEASURED_ONLY, CubageHeightOriginPolicy.MEASURED_OR_ESTIMATED)) {
            val single = method.copy(heightRequirement = CubageHeightRequirement.NONE, heightOriginPolicy = policy)
            val result = inspect(tree.copy(hauteurM = null), draft.copy(height = null), single)
            assertNull(accepted(result).heightM)
            assertNull(result.inputs!!.heightOrigin)
            assertEquals(policy, result.provenance.methodBinding!!.heightOriginPolicy)
        }
    }

    @Test fun `one entry method does not consume an available merchantable height`() {
        val merchantable = draft.copy(height = height.copy(nature = HeightNature.MERCHANTABLE, pairedWithDiameter = null))
        val result = inspect(declaration = merchantable, boundMethod = method.copy(heightRequirement = CubageHeightRequirement.NONE))
        assertNull(accepted(result).heightM)
        assertNull(result.inputs!!.heightOrigin)
        assertEquals(20.0, result.provenance.observation.hauteurM!!, 0.0)
        assertEquals(HeightNature.MERCHANTABLE, result.provenance.qualification!!.record.payload.draft.height!!.nature)
    }

    @Test fun `one entry method preserves but does not consume an assigned class height`() {
        val assigned = draft.copy(height = height.copy(support = ObservationSupport.GROUP_REFERENCE,
            origin = MeasurementOrigin.ASSIGNED_REFERENCE, pairedWithDiameter = null))
        val result = inspect(declaration = assigned, boundMethod = method.copy(heightRequirement = CubageHeightRequirement.NONE))
        assertNull(accepted(result).heightM)
        assertEquals(assigned.height, result.provenance.qualification!!.record.payload.draft.height)
    }

    @Test fun `one entry method does not turn an unused zero height into an input value`() {
        val result = inspect(tree.copy(hauteurM = 0.0), draft.copy(height = null),
            method.copy(heightRequirement = CubageHeightRequirement.NONE))
        assertNull(accepted(result).heightM)
        assertEquals(0.0, result.provenance.observation.hauteurM!!, 0.0)
    }

    @Test fun `one entry method still requires measured sourced diameter number and calibration`() {
        val single = method.copy(heightRequirement = CubageHeightRequirement.NONE)
        refused(inspect(boundMethod = single.copy(tariffNumber = null)), CubageInputReason.TARIFF_NUMBER_MISSING)
        refused(inspect(boundMethod = single.copy(calibrationReference = null)), CubageInputReason.CALIBRATION_MISSING)
        refused(inspect(declaration = draft.copy(diameter = diameter.copy(sourceReference = null)), boundMethod = single),
            CubageInputReason.DIAMETER_SOURCE_MISSING)
        refused(inspect(boundMethod = single, applicability = domain(boundMethod = single).copy(status = CubageApplicabilityStatus.UNKNOWN)),
            CubageInputReason.APPLICABILITY_UNKNOWN)
    }

    @Test fun `one entry method still validates exact raw basis including an unused height`() {
        val single = method.copy(heightRequirement = CubageHeightRequirement.NONE)
        refused(inspect(tree.copy(hauteurM = 21.0), boundMethod = single, qualification = read(),
            applicability = domain(boundMethod = single)), CubageInputReason.MEASUREMENT_BASIS_MISMATCH)
    }

    @Test fun `provenance retains full observation declaration authors lineage and exact inherited references`() {
        val qualification = read()
        val applicability = domain()
        val result = inspect(qualification = qualification, applicability = applicability)
        accepted(result)
        assertEquals(tree, result.provenance.observation)
        assertSame(qualification, result.provenance.qualification)
        assertSame(method, result.provenance.methodBinding)
        assertSame(convention, result.provenance.diameterConventionBinding)
        assertSame(applicability, result.provenance.applicability)
        assertEquals(ObservationMeasurementBasis.capture(tree).fingerprint(), result.provenance.rawBasisFingerprint)
        assertEquals(qualification.record.payload.basis.fingerprint(), result.provenance.qualificationBasisFingerprint)
        assertEquals("TEST diameter source  ", result.provenance.qualification!!.record.payload.draft.diameter!!.sourceReference)
        assertEquals("TEST-CREATOR", result.provenance.qualification!!.record.createdBy)
        assertEquals("TEST-EDITOR", result.provenance.qualification!!.record.updatedBy)
        assertEquals("TEST-ORIGINAL", result.provenance.qualification!!.record.payload.copiedFrom!!.tigeId)
        assertEquals("TEST inherited plot GPS", result.provenance.qualification!!.record.payload.draft.position!!.sourceReference)
        assertEquals("cubage-input-admissibility-1.0.0", result.provenance.kernelVersion)
    }

    @Test fun `refused result preserves unresolved bindings and complete rejected input rather than filling defaults`() {
        val unbound = method.copy(sourceReference = null)
        val result = inspect(boundMethod = unbound)
        refused(result, CubageInputReason.METHOD_SOURCE_MISSING)
        assertSame(unbound, result.provenance.methodBinding)
        assertEquals(tree, result.provenance.observation)
        assertNull(result.provenance.methodBinding!!.sourceReference)
    }

    @Test fun `caller mutable defects list cannot alter a returned provenance snapshot`() {
        val mutable = mutableListOf("TEST original defect")
        val input = tree.copy(defauts = mutable)
        val result = inspect(input)
        accepted(result)
        mutable[0] = "TEST later caller change"
        assertEquals(listOf("TEST original defect"), result.provenance.observation.defauts)
        try {
            (result.provenance.observation.defauts as MutableList<String>)[0] = "TEST illegal provenance change"
            fail("Returned provenance defects must be immutable")
        } catch (_: UnsupportedOperationException) {
            assertEquals(listOf("TEST original defect"), result.provenance.observation.defauts)
        }
    }

    @Test fun `inspection changes neither measurements nor qualification or domain objects`() {
        val qualification = read()
        val applicability = domain()
        val originalTree = tree.copy()
        val originalQualification = qualification.copy()
        val originalDomain = applicability.copy()
        inspect(qualification = qualification, applicability = applicability)
        assertEquals(originalTree, tree)
        assertEquals(originalQualification, qualification)
        assertEquals(originalDomain, applicability)
    }
}
