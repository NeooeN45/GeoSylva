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
import java.math.BigDecimal
import java.math.MathContext
import org.junit.Assert.*
import org.junit.Test

class IndividualSlendernessTest {
    private val tree = Tige(
        id = "TEST-TREE", parcelleId = "TEST-PARCEL", placetteId = "TEST-PLOT",
        essenceCode = "TEST-OAK", diamCm = 40.0, hauteurM = 20.0,
        gpsWkt = "POINT(0 0)", precisionM = 3.0, altitudeM = 100.0, timestamp = 1000,
        note = null, produit = null, fCoef = null, valueEur = null, sessionId = "TEST-SESSION",
        source = "TEST legacy source does not qualify measures", version = 4,
        coefficientElancement = 999.0,
    )
    private val binding = IndividualSlendernessConventionBinding(
        "TEST explicit diameter protocol",
        IndividualSlendernessDiameterConvention.BREAST_HEIGHT_1_30_M,
    )
    private val diameter = DiameterQualificationDraft(
        origin = MeasurementOrigin.MEASURED, conventionReference = binding.reference,
        methodReference = "TEST caliper", sourceReference = "TEST diameter capture",
        observedAt = 900,
    )
    private val height = HeightQualificationDraft(
        nature = HeightNature.TOTAL, origin = MeasurementOrigin.MEASURED,
        support = ObservationSupport.INDIVIDUAL_TREE, pairedWithDiameter = true,
        methodReference = "TEST hypsometer", sourceReference = "TEST height capture",
        observedAt = 901,
    )
    private val draft = ObservationQualificationDraft(
        support = ObservationSupport.INDIVIDUAL_TREE,
        diameter = diameter, height = height,
        position = PositionQualificationDraft(
            origin = PositionOrigin.REUSED_POINT, sourceReference = "TEST plot point",
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
            createdAt = 1001, createdBy = "TEST-CREATOR", updatedAt = 1002,
            updatedBy = "TEST-EDITOR",
        ),
        status = status,
    )

    private fun calculate(
        observation: Tige = tree,
        declaration: ObservationQualificationDraft = draft,
        convention: IndividualSlendernessConventionBinding? = binding,
    ) = IndividualSlendernessCalculator.calculate(observation, read(observation, declaration), convention)

    private fun refused(result: IndividualSlendernessResult, reason: IndividualSlendernessReason) {
        assertEquals(IndividualSlendernessAvailability.UNAVAILABLE, result.availability)
        assertEquals(reason, result.reason)
        assertNull(result.ratio)
    }

    @Test fun `one measured total tree gives dimensionless 50 without using its legacy ratio`() {
        val result = calculate()
        assertEquals(IndividualSlendernessAvailability.AVAILABLE, result.availability)
        assertEquals(50.0, result.ratio!!, 0.0)
        assertNull(result.reason)
        assertEquals(MeasurementOrigin.MEASURED, result.provenance.heightOrigin)
        assertEquals(1.30, result.provenance.conventionBinding!!.convention!!.measurementHeightM, 0.0)
    }

    @Test fun `CNPF page six published examples 18 metres with 20 and 30 centimetres give 90 and 60`() {
        for ((diameterCm, expected) in listOf(20.0 to 90.0, 30.0 to 60.0)) {
            assertEquals(expected, calculate(tree.copy(diamCm = diameterCm, hauteurM = 18.0)).ratio!!, 0.0)
        }
    }

    @Test fun `formula identifies exact publication edition page and archived bytes`() {
        val formula = calculate().provenance.formula
        assertEquals("CNPF / e-for-OWN", formula.organism)
        assertEquals(listOf("L. Molines", "E. Sevrin", "F.-X. Valengin"), formula.authors)
        assertEquals("La futaie régulière : diagnostic et gestions envisagées", formula.title)
        assertEquals("juin 2019", formula.edition)
        assertEquals(6, formula.pdfPage)
        assertEquals("https://www.cnpf.fr/sites/socle/files/2024-05/" +
            "13.c_La_futaie_reguliere_diagnostic_et_gestions_envisagees.pdf", formula.url)
        assertEquals("c8755e7f8d4782b273affc9469217b8903aaa8bc529e6ac01cedc9249146984f", formula.sourceSha256)
        assertEquals("100 * height_m / diameter_cm", formula.expression)
        assertEquals("individual-slenderness-1.0.0", formula.methodVersion)
    }

    @Test fun `formula authors cannot be mutated through a returned list to corrupt later provenance`() {
        val authors = calculate().provenance.formula.authors
        try {
            (authors as MutableList<String>)[0] = "TEST invalid replacement"
            fail("Formula authors must be immutable")
        } catch (_: UnsupportedOperationException) {
            assertEquals(listOf("L. Molines", "E. Sevrin", "F.-X. Valengin"),
                calculate().provenance.formula.authors)
        }
    }

    @Test fun `record basis draft authors times revision and copy lineage are retained without mutation`() {
        val qualification = read()
        val beforeTree = tree.copy()
        val beforeRead = qualification.copy()
        val result = IndividualSlendernessCalculator.calculate(tree, qualification, binding)
        assertEquals(beforeTree, tree)
        assertEquals(beforeRead, qualification)
        assertSame(qualification, result.provenance.qualification)
        assertSame(binding, result.provenance.conventionBinding)
        assertEquals(qualification.record.payload.basis.fingerprint(),
            result.provenance.observation.rawBasisFingerprint)
        assertEquals(qualification.record.payload.basis.fingerprint(),
            result.provenance.qualificationBasisFingerprint)
        assertEquals(tree.id, result.provenance.observation.id)
        assertEquals(tree.parcelleId, result.provenance.observation.parcelleId)
        assertEquals(tree.placetteId, result.provenance.observation.placetteId)
        assertEquals(tree.sessionId, result.provenance.observation.sessionId)
        assertEquals(tree.version, result.provenance.observation.version)
        assertEquals(tree.timestamp, result.provenance.observation.timestamp)
        assertEquals(tree.diamCm, result.provenance.observation.diameterCm, 0.0)
        assertEquals(tree.hauteurM, result.provenance.observation.heightM)
    }

    @Test fun `legacy numeric height source and precomputed coefficient cannot replace qualification`() {
        val result = IndividualSlendernessCalculator.calculate(tree, null, binding)
        refused(result, IndividualSlendernessReason.QUALIFICATION_MISSING)
        assertNull(result.provenance.qualification)
        assertNull(result.provenance.heightOrigin)
        assertEquals(ObservationMeasurementBasis.capture(tree).fingerprint(),
            result.provenance.observation.rawBasisFingerprint)
    }

    @Test fun `deleted observation is refused even when its unmodified basis remains current`() {
        val deleted = tree.copy(deletedAt = 2000)
        val result = IndividualSlendernessCalculator.calculate(deleted, read(), binding)
        refused(result, IndividualSlendernessReason.OBSERVATION_DELETED)
        assertEquals(2000L, result.provenance.observation.deletedAt)
    }

    @Test fun `session must be present and nonblank`() {
        for (session in listOf(null, "", " ")) {
            refused(IndividualSlendernessCalculator.calculate(tree.copy(sessionId = session), read(), binding),
                IndividualSlendernessReason.SESSION_MISSING)
        }
    }

    @Test fun `plot must be present and nonblank`() {
        for (plot in listOf(null, "", " ")) {
            refused(IndividualSlendernessCalculator.calculate(tree.copy(placetteId = plot), read(), binding),
                IndividualSlendernessReason.PLOT_MISSING)
        }
    }

    @Test fun `empty tree or parcel identity is refused`() {
        for (row in listOf(tree.copy(id = ""), tree.copy(parcelleId = " "))) {
            refused(IndividualSlendernessCalculator.calculate(row, read(), binding),
                IndividualSlendernessReason.OBSERVATION_IDENTITY_MISSING)
        }
    }

    @Test fun `qualification for a different observation cannot be applied to the same numeric measurements`() {
        val other = read(tree.copy(id = "TEST-OTHER"))
        val result = IndividualSlendernessCalculator.calculate(tree, other, binding)
        refused(result, IndividualSlendernessReason.QUALIFICATION_IDENTITY_MISMATCH)
        assertSame(other, result.provenance.qualification)
        assertNotEquals(result.provenance.observation.rawBasisFingerprint,
            result.provenance.qualificationBasisFingerprint)
    }

    @Test fun `same identity moved between parcel plot or session cannot reuse its old qualification`() {
        for (row in listOf(tree.copy(parcelleId = "TEST-OTHER"),
            tree.copy(placetteId = "TEST-OTHER"), tree.copy(sessionId = "TEST-OTHER"))) {
            refused(IndividualSlendernessCalculator.calculate(row, read(), binding),
                IndividualSlendernessReason.QUALIFICATION_SCOPE_MISMATCH)
        }
    }

    @Test fun `STALE declaration is refused independently of a currently matching basis`() {
        refused(IndividualSlendernessCalculator.calculate(tree,
            read(status = ObservationQualificationStatus.STALE), binding),
            IndividualSlendernessReason.QUALIFICATION_STALE)
    }

    @Test fun `CURRENT flag does not bypass complete measurement basis matching`() {
        val changed = listOf(
            tree.copy(diamCm = 41.0), tree.copy(hauteurM = 21.0), tree.copy(essenceCode = "TEST-PINE"),
            tree.copy(originTigeId = "TEST-ORIGINAL"), tree.copy(gpsWkt = "POINT(1 1)"),
            tree.copy(precisionM = 4.0), tree.copy(altitudeM = 101.0), tree.copy(timestamp = 1003),
            tree.copy(version = 5),
        )
        val qualification = read()
        for (row in changed) {
            refused(IndividualSlendernessCalculator.calculate(row, qualification, binding),
                IndividualSlendernessReason.MEASUREMENT_BASIS_MISMATCH)
        }
    }

    @Test fun `return to previous numeric values with a new version does not reactivate a declaration`() {
        refused(IndividualSlendernessCalculator.calculate(tree.copy(version = 6), read(), binding),
            IndividualSlendernessReason.MEASUREMENT_BASIS_MISMATCH)
    }

    @Test fun `unknown or collective observation support is not an individual tree`() {
        for (support in listOf(null, ObservationSupport.GROUP_REFERENCE)) {
            refused(calculate(declaration = draft.copy(support = support,
                height = height.copy(pairedWithDiameter = null))),
                IndividualSlendernessReason.INDIVIDUAL_SUPPORT_REQUIRED)
        }
    }

    @Test fun `absent diameter declaration does not gain provenance from legacy source`() {
        refused(calculate(declaration = draft.copy(diameter = null)),
            IndividualSlendernessReason.DIAMETER_QUALIFICATION_MISSING)
    }

    @Test fun `unknown estimated or assigned diameter cannot replace a measured DBH`() {
        for (origin in listOf(null, MeasurementOrigin.ESTIMATED, MeasurementOrigin.ASSIGNED_REFERENCE)) {
            refused(calculate(declaration = draft.copy(diameter = diameter.copy(origin = origin),
                height = height.copy(pairedWithDiameter = null))),
                IndividualSlendernessReason.MEASURED_DIAMETER_REQUIRED)
        }
    }

    @Test fun `missing diameter convention reference is refused despite a caller binding`() {
        refused(calculate(declaration = draft.copy(diameter = diameter.copy(conventionReference = null))),
            IndividualSlendernessReason.DIAMETER_CONVENTION_MISSING)
    }

    @Test fun `nonnull free text diameter convention does not resolve itself`() {
        val legacy = read(declaration = draft.copy(diameter = diameter.copy(
            conventionReference = "DBH 1.30 m measured cm")))
        refused(IndividualSlendernessCalculator.calculate(tree, legacy),
            IndividualSlendernessReason.CONVENTION_UNRESOLVED)
    }

    @Test fun `unresolved or blank caller binding cannot establish breast height`() {
        for (convention in listOf(null, binding.copy(convention = null), binding.copy(reference = " "))) {
            refused(calculate(convention = convention), IndividualSlendernessReason.CONVENTION_UNRESOLVED)
        }
    }

    @Test fun `binding must refer to exact declared reference without trimming or case inference`() {
        for (reference in listOf("TEST OTHER protocol", binding.reference + " ", binding.reference.lowercase())) {
            val provided = binding.copy(reference = reference)
            val result = calculate(convention = provided)
            refused(result, IndividualSlendernessReason.CONVENTION_MISMATCH)
            assertSame(provided, result.provenance.conventionBinding)
        }
    }

    @Test fun `explicit 1 point 50 metre breast height remains distinguishable from 1 point 30 metre`() {
        val alternative = binding.copy(convention = IndividualSlendernessDiameterConvention.BREAST_HEIGHT_1_50_M)
        val result = calculate(convention = alternative)
        assertEquals(50.0, result.ratio!!, 0.0)
        assertEquals(1.50, result.provenance.conventionBinding!!.convention!!.measurementHeightM, 0.0)
    }

    @Test fun `diameter source is required even when the legacy observation has a source`() {
        refused(calculate(declaration = draft.copy(diameter = diameter.copy(sourceReference = null))),
            IndividualSlendernessReason.DIAMETER_SOURCE_MISSING)
    }

    @Test fun `absent height declaration cannot use the existing raw height`() {
        refused(calculate(declaration = draft.copy(height = null)),
            IndividualSlendernessReason.HEIGHT_QUALIFICATION_MISSING)
    }

    @Test fun `unknown and collective height support cannot be promoted to individual height`() {
        for (support in listOf(null, ObservationSupport.GROUP_REFERENCE)) {
            refused(calculate(declaration = draft.copy(height = height.copy(
                support = support, pairedWithDiameter = null))),
                IndividualSlendernessReason.INDIVIDUAL_HEIGHT_REQUIRED)
        }
    }

    @Test fun `commercial or unknown height cannot become total height`() {
        for (nature in listOf(null, HeightNature.MERCHANTABLE)) {
            refused(calculate(declaration = draft.copy(height = height.copy(
                nature = nature, pairedWithDiameter = null))),
                IndividualSlendernessReason.TOTAL_HEIGHT_REQUIRED)
        }
    }

    @Test fun `pairing must be declared true rather than inferred from numeric presence`() {
        for (paired in listOf(null, false)) {
            refused(calculate(declaration = draft.copy(height = height.copy(pairedWithDiameter = paired))),
                IndividualSlendernessReason.HEIGHT_PAIRING_REQUIRED)
        }
    }

    @Test fun `unknown height origin remains unsupported even with valid pairing`() {
        refused(calculate(declaration = draft.copy(height = height.copy(origin = null))),
            IndividualSlendernessReason.HEIGHT_ORIGIN_UNSUPPORTED)
    }

    @Test fun `assigned reference height never becomes an individually paired height`() {
        refused(calculate(declaration = draft.copy(height = height.copy(
            origin = MeasurementOrigin.ASSIGNED_REFERENCE, pairedWithDiameter = false))),
            IndividualSlendernessReason.HEIGHT_PAIRING_REQUIRED)
    }

    @Test fun `height source cannot be supplied by legacy Tige source`() {
        refused(calculate(declaration = draft.copy(height = height.copy(sourceReference = null))),
            IndividualSlendernessReason.HEIGHT_SOURCE_MISSING)
    }

    @Test fun `estimated total height needs an explicit method and preserves estimated origin`() {
        val estimated = draft.copy(height = height.copy(origin = MeasurementOrigin.ESTIMATED,
            methodReference = "TEST visual estimate protocol"))
        val result = calculate(declaration = estimated)
        assertEquals(50.0, result.ratio!!, 0.0)
        assertEquals(MeasurementOrigin.ESTIMATED, result.provenance.heightOrigin)
        assertEquals("TEST visual estimate protocol",
            result.provenance.qualification!!.record.payload.draft.height!!.methodReference)
    }

    @Test fun `estimated height without method remains unavailable and its declaration is retained`() {
        val declaration = draft.copy(height = height.copy(origin = MeasurementOrigin.ESTIMATED,
            methodReference = null))
        val result = calculate(declaration = declaration)
        refused(result, IndividualSlendernessReason.HEIGHT_ESTIMATION_METHOD_MISSING)
        assertEquals(declaration, result.provenance.qualification!!.record.payload.draft)
        assertEquals(MeasurementOrigin.ESTIMATED, result.provenance.heightOrigin)
    }

    @Test fun `estimated height still requires a source`() {
        refused(calculate(declaration = draft.copy(height = height.copy(
            origin = MeasurementOrigin.ESTIMATED, sourceReference = null))),
            IndividualSlendernessReason.HEIGHT_SOURCE_MISSING)
    }

    @Test fun `measured height does not invent an estimation method when none was declared`() {
        val result = calculate(declaration = draft.copy(height = height.copy(methodReference = null)))
        assertEquals(50.0, result.ratio!!, 0.0)
        assertNull(result.provenance.qualification!!.record.payload.draft.height!!.methodReference)
    }

    @Test fun `missing height is unavailable rather than legacy coefficient or zero`() {
        refused(calculate(tree.copy(hauteurM = null)), IndividualSlendernessReason.HEIGHT_MISSING)
    }

    @Test fun `nonfinite nonpositive diameters are refused and attempted values remain in provenance`() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, -0.0, -1.0)) {
            val row = tree.copy(diamCm = value)
            val result = IndividualSlendernessCalculator.calculate(row, read(), binding)
            refused(result, IndividualSlendernessReason.INVALID_DIAMETER)
            assertEquals(value.toBits(), result.provenance.observation.diameterCm.toBits())
            if (!value.isFinite() || value < 0.0) assertNull(result.provenance.observation.rawBasisFingerprint)
        }
    }

    @Test fun `nonfinite nonpositive heights are refused without returning false zero`() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, -0.0, -1.0)) {
            val result = IndividualSlendernessCalculator.calculate(tree.copy(hauteurM = value), read(), binding)
            refused(result, IndividualSlendernessReason.INVALID_HEIGHT)
            assertEquals(value.toBits(), result.provenance.observation.heightM!!.toBits())
        }
    }

    @Test fun `very large matching values produce 100 without overflowing height multiplication`() {
        for (value in listOf(1e308, Double.MAX_VALUE)) {
            assertEquals(100.0, calculate(tree.copy(diamCm = value, hauteurM = value)).ratio!!, 0.0)
        }
    }

    @Test fun `finite upper boundary is not misclassified as numeric overflow`() {
        val result = calculate(tree.copy(diamCm = 100.0, hauteurM = Double.MAX_VALUE))
        assertEquals(Double.MAX_VALUE.toBits(), result.ratio!!.toBits())
    }

    @Test fun `subnormal height quotient can remain representable after metre conversion`() {
        val result = calculate(tree.copy(diamCm = 10.0, hauteurM = Double.MIN_VALUE))
        assertEquals((Double.MIN_VALUE * 10.0).toBits(), result.ratio!!.toBits())
    }

    @Test fun `matching smallest subnormal values produce 100 rather than false underflow`() {
        assertEquals(100.0, calculate(tree.copy(diamCm = Double.MIN_VALUE,
            hauteurM = Double.MIN_VALUE)).ratio!!, 0.0)
    }

    @Test fun `unrepresentable final overflow or underflow is explicitly a numeric domain error`() {
        for (row in listOf(tree.copy(diamCm = Double.MIN_VALUE, hauteurM = Double.MAX_VALUE),
            tree.copy(diamCm = Double.MAX_VALUE, hauteurM = Double.MIN_VALUE))) {
            val result = calculate(row)
            refused(result, IndividualSlendernessReason.NUMERIC_DOMAIN_ERROR)
            assertEquals("numeric_domain_error", result.reason!!.code)
            assertNotNull(result.provenance.observation.rawBasisFingerprint)
        }
    }

    @Test fun `scaled ratio agrees with independent decimal arithmetic across numerical exponents`() {
        val values = listOf(Double.MIN_VALUE, java.lang.Double.MIN_NORMAL, 1e-200, 1.0, 1e200, Double.MAX_VALUE)
        for (heightM in values) for (diameterCm in values) {
            val expected = BigDecimal(heightM).multiply(BigDecimal(100))
                .divide(BigDecimal(diameterCm), MathContext.DECIMAL128).toDouble()
            val result = calculate(tree.copy(diamCm = diameterCm, hauteurM = heightM))
            if (!expected.isFinite() || expected <= 0.0) {
                refused(result, IndividualSlendernessReason.NUMERIC_DOMAIN_ERROR)
            } else {
                assertEquals("H=$heightM D=$diameterCm", expected, result.ratio!!, Math.ulp(expected) * 4.0)
            }
        }
    }

    @Test fun `sealed matching declaration remains a descriptive read with its exact seal state`() {
        val qualification = read().let { it.copy(record = it.record.copy(isSealed = true)) }
        val result = IndividualSlendernessCalculator.calculate(tree, qualification, binding)
        assertEquals(50.0, result.ratio!!, 0.0)
        assertTrue(result.provenance.qualification!!.record.isSealed)
    }
}
