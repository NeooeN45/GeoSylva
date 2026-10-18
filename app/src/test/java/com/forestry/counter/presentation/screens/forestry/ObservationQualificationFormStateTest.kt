package com.forestry.counter.presentation.screens.forestry

import com.forestry.counter.domain.model.DiameterQualificationDraft
import com.forestry.counter.domain.model.HeightNature
import com.forestry.counter.domain.model.HeightQualificationDraft
import com.forestry.counter.domain.model.MeasurementOrigin
import com.forestry.counter.domain.model.ObservationQualificationDraft
import com.forestry.counter.domain.model.ObservationSupport
import com.forestry.counter.domain.model.PositionOrigin
import com.forestry.counter.domain.model.PositionQualificationDraft
import org.junit.Assert.*
import org.junit.Test

// Reconstructed on 2026-10-15: missing Saver test tails rebuilt from the recovered contract.
class ObservationQualificationFormStateTest {
    private val position = PositionQualificationDraft(
        origin = PositionOrigin.REUSED_POINT, methodReference = " TEST position method \n",
        sourceReference = "TEST local position source", capturedAt = 333,
    )
    private val paired = ObservationQualificationDraft(
        support = ObservationSupport.INDIVIDUAL_TREE,
        diameter = DiameterQualificationDraft(
            origin = MeasurementOrigin.MEASURED, conventionReference = " TEST external convention \n",
            methodReference = " TEST diameter method ", sourceReference = "TEST diameter source",
            observedAt = 111,
        ),
        height = HeightQualificationDraft(
            nature = HeightNature.TOTAL, origin = MeasurementOrigin.MEASURED,
            support = ObservationSupport.INDIVIDUAL_TREE, pairedWithDiameter = true,
            methodReference = " TEST height method ", sourceReference = " TEST height source \n",
            observedAt = 222,
        ),
        position = position,
    )
    private val pairedForm = ObservationQualificationFormState.fromDraft(paired)

    @Test fun `new form has no support origin convention pairing dates or position defaults`() {
        val form = ObservationQualificationFormState()
        assertTrue(form.validation().isValid)
        assertFalse(form.canPairHeightWithDiameter)
        assertEquals(ObservationQualificationDraft(), form.toDraft())
        assertNull(form.support)
        assertNull(form.diameterConventionChoice)
        assertNull(form.diameterObservedAt)
        assertNull(form.heightObservedAt)
        assertNull(form.position)
    }

    @Test fun `every explicit support changes only that declaration without inventing measurements`() {
        (ObservationSupport.entries + listOf(null)).forEach { support ->
            val draft = ObservationQualificationFormState().withSupport(support).toDraft()
            assertEquals(ObservationQualificationDraft(support = support), draft)
        }
    }

    @Test fun `complete declaration roundtrips literal references position and all inherited dates`() {
        assertEquals(paired, pairedForm.toDraft())
        assertSame(position, pairedForm.position)
        assertEquals(111L, pairedForm.diameterObservedAt)
        assertEquals(222L, pairedForm.heightObservedAt)
        assertEquals(ObservationDiameterConventionChoice.OTHER, pairedForm.diameterConventionChoice)
        assertEquals(" TEST external convention \n", pairedForm.diameterOtherConventionReference)
        assertTrue(pairedForm.canPairHeightWithDiameter)
    }

    @Test fun `empty inherited subrecords remain distinct from absent subrecords`() {
        val variants = listOf(
            ObservationQualificationDraft(),
            ObservationQualificationDraft(diameter = DiameterQualificationDraft()),
            ObservationQualificationDraft(height = HeightQualificationDraft()),
            ObservationQualificationDraft(position = PositionQualificationDraft()),
            ObservationQualificationDraft(diameter = DiameterQualificationDraft(),
                height = HeightQualificationDraft(), position = PositionQualificationDraft()),
        )
        variants.forEach { draft ->
            val form = ObservationQualificationFormState.fromDraft(draft)
            assertEquals(draft, form.toDraft())
            assertEquals(form, ObservationQualificationFormState.fromSaveableValues(form.toSaveableValues()))
        }
    }

    @Test fun `partial legacy declarations and zero timestamps retain unknowns rather than filling fields`() {
        val draft = ObservationQualificationDraft(
            diameter = DiameterQualificationDraft(observedAt = 0),
            height = HeightQualificationDraft(sourceReference = "TEST only known source"),
            position = PositionQualificationDraft(capturedAt = 0),
        )
        val form = ObservationQualificationFormState.fromDraft(draft)
        assertEquals(draft, form.toDraft())
        assertNull(form.support)
        assertNull(form.heightNature)
        assertNull(form.heightOrigin)
        assertNull(form.heightPairedWithDiameter)
        assertEquals(0L, form.diameterObservedAt)
        assertNull(form.heightObservedAt)
    }

    @Test fun `unknown legacy conventions remain other with their exact characters`() {
        val references = listOf("DBH", "DBH 1.30 m", " TEST convention \n", "Écorce / pente : TEST",
            "geosylva:diameter-convention:breast-height-1.30m:v1 ")
        references.forEach { reference ->
            val draft = ObservationQualificationDraft(diameter = DiameterQualificationDraft(
                conventionReference = reference))
            val form = ObservationQualificationFormState.fromDraft(draft)
            assertEquals(ObservationDiameterConventionChoice.OTHER, form.diameterConventionChoice)
            assertEquals(reference, form.diameterOtherConventionReference)
            assertEquals(draft, form.toDraft())
        }
    }

    @Test fun `explicit local DBH selections produce stable distinct references with no method or source`() {
        val cases = listOf(
            ObservationDiameterConventionChoice.BREAST_HEIGHT_1_30_M to
                "geosylva:diameter-convention:breast-height-1.30m:v1",
            ObservationDiameterConventionChoice.BREAST_HEIGHT_1_50_M to
                "geosylva:diameter-convention:breast-height-1.50m:v1",
        )
        cases.forEach { (choice, reference) ->
            val form = ObservationQualificationFormState(diameterConventionChoice = choice)
            val draft = requireNotNull(form.toDraft())
            val diameter = requireNotNull(draft.diameter)
            assertEquals(reference, diameter.conventionReference)
            assertNull(diameter.origin)
            assertNull(diameter.methodReference)
            assertNull(diameter.sourceReference)
            assertNull(diameter.observedAt)
            assertEquals(choice, ObservationQualificationFormState.fromDraft(draft).diameterConventionChoice)
        }
    }

    @Test fun `switching other known and unknown convention choices never erases retained literal input`() {
        val known = pairedForm.copy(diameterConventionChoice = ObservationDiameterConventionChoice.BREAST_HEIGHT_1_30_M)
        assertEquals(ObservationDiameterConventionChoice.BREAST_HEIGHT_1_30_M.localReference,
            known.toDraft()?.diameter?.conventionReference)
        assertEquals(paired.diameter?.conventionReference, known.diameterOtherConventionReference)
        assertEquals(paired, known.copy(diameterConventionChoice = ObservationDiameterConventionChoice.OTHER).toDraft())
        assertNull(known.copy(diameterConventionChoice = null).toDraft()?.diameter?.conventionReference)
    }

    @Test fun `changing outer support clears incompatible true pairing while keeping all other facts`() {
        listOf(null, ObservationSupport.GROUP_REFERENCE).forEach { support ->
            val changed = pairedForm.withSupport(support)
            assertNull(changed.heightPairedWithDiameter)
            val actual = requireNotNull(changed.toDraft())
            assertEquals(paired.copy(support = support, height = paired.height?.copy(pairedWithDiameter = null)), actual)
            assertSame(position, actual.position)
        }
    }

    @Test fun `changing height nature to merchantable or unknown clears pairing without erasing provenance`() {
        listOf(null, HeightNature.MERCHANTABLE).forEach { nature ->
            val changed = pairedForm.withHeightNature(nature)
            assertNull(changed.heightPairedWithDiameter)
            assertEquals(paired.copy(height = paired.height?.copy(nature = nature, pairedWithDiameter = null)),
                changed.toDraft())
        }
    }

    @Test fun `changing height support to collective or unknown clears pairing without promoting a reference`() {
        listOf(null, ObservationSupport.GROUP_REFERENCE).forEach { support ->
            val changed = pairedForm.withHeightSupport(support)
            assertNull(changed.heightPairedWithDiameter)
            assertEquals(paired.copy(height = paired.height?.copy(support = support, pairedWithDiameter = null)),
                changed.toDraft())
        }
    }

    @Test fun `assigned or unknown height origin clears true pairing and retains the old source and method`() {
        listOf(null, MeasurementOrigin.ASSIGNED_REFERENCE).forEach { origin ->
            val changed = pairedForm.withHeightOrigin(origin)
            assertNull(changed.heightPairedWithDiameter)
            assertEquals(paired.copy(height = paired.height?.copy(origin = origin, pairedWithDiameter = null)),
                changed.toDraft())
        }
    }

    @Test fun `assigned diameter clears true pairing while measured estimated and unknown do not invent one`() {
        val assigned = pairedForm.withDiameterOrigin(MeasurementOrigin.ASSIGNED_REFERENCE)
        assertNull(assigned.heightPairedWithDiameter)
        assertEquals(paired.copy(diameter = paired.diameter?.copy(origin = MeasurementOrigin.ASSIGNED_REFERENCE),
            height = paired.height?.copy(pairedWithDiameter = null)), assigned.toDraft())
        listOf(null, MeasurementOrigin.MEASURED, MeasurementOrigin.ESTIMATED).forEach { origin ->
            val fresh = ObservationQualificationFormState().withDiameterOrigin(origin)
            assertNull(fresh.heightPairedWithDiameter)
            assertNull(fresh.toDraft()?.diameter?.observedAt)
        }
    }

    @Test fun `measured and estimated total heights may retain an explicitly paired individual declaration`() {
        listOf(MeasurementOrigin.MEASURED, MeasurementOrigin.ESTIMATED).forEach { origin ->
            val changed = pairedForm.withHeightOrigin(origin)
            assertTrue(changed.canPairHeightWithDiameter)
            assertEquals(true, changed.heightPairedWithDiameter)
            assertEquals(paired.copy(height = paired.height?.copy(origin = origin)), changed.toDraft())
        }
    }

    @Test fun `becoming eligible for pairing does not automatically assert pairing`() {
        val form = ObservationQualificationFormState()
            .withSupport(ObservationSupport.INDIVIDUAL_TREE)
            .withHeightNature(HeightNature.TOTAL)
            .withHeightOrigin(MeasurementOrigin.MEASURED)
            .withHeightSupport(ObservationSupport.INDIVIDUAL_TREE)
        assertTrue(form.canPairHeightWithDiameter)
        assertNull(form.heightPairedWithDiameter)
        assertNull(form.toDraft()?.diameter)
        assertNull(form.toDraft()?.height?.pairedWithDiameter)
    }

    @Test fun `pairing true is only accepted when all individual total height conditions are declared`() {
        assertNull(ObservationQualificationFormState().withHeightPairing(true).heightPairedWithDiameter)
        val accepted = pairedForm.withHeightPairing(null).withHeightPairing(true)
        assertEquals(paired, accepted.toDraft())
    }

    @Test fun `negative or unknown pairing remains distinct from true in any valid partial declaration`() {
        listOf(null, false).forEach { pairing ->
            val form = ObservationQualificationFormState().withHeightPairing(pairing)
            assertEquals(pairing, form.heightPairedWithDiameter)
            assertTrue(form.validation().isValid)
            assertEquals(pairing, form.toDraft()?.height?.pairedWithDiameter)
            if (pairing == null) assertNull(form.toDraft()?.height)
        }
    }

    @Test fun `legacy true pairing with unknown origin is retained on load but blocks saving until resolved`() {
        val legacy = paired.copy(height = paired.height?.copy(origin = null))
        val form = ObservationQualificationFormState.fromDraft(legacy)
        assertEquals(true, form.heightPairedWithDiameter)
        assertNull(form.heightOrigin)
        assertTrue(form.validation().invalidPairing)
        assertNull(form.toDraft())
        assertEquals(form, ObservationQualificationFormState.fromSaveableValues(form.toSaveableValues()))
        assertEquals(paired, form.withHeightOrigin(MeasurementOrigin.MEASURED).toDraft())
        assertEquals(legacy.copy(height = legacy.height?.copy(pairedWithDiameter = null)),
            form.withHeightPairing(null).toDraft())
    }

    @Test fun `all direct type combinations either yield a legal draft or explicitly block invalid pairing`() {
        (ObservationSupport.entries + listOf(null)).forEach { support ->
            (MeasurementOrigin.entries + listOf(null)).forEach { diameterOrigin ->
                (HeightNature.entries + listOf(null)).forEach { nature ->
                    (MeasurementOrigin.entries + listOf(null)).forEach { heightOrigin ->
                        (ObservationSupport.entries + listOf(null)).forEach { heightSupport ->
                            listOf(null, false, true).forEach { paired ->
                                val form = ObservationQualificationFormState(support = support,
                                    diameterOrigin = diameterOrigin, heightNature = nature,
                                    heightOrigin = heightOrigin, heightSupport = heightSupport,
                                    heightPairedWithDiameter = paired)
                                val allowed = paired != true || (support == ObservationSupport.INDIVIDUAL_TREE &&
                                    diameterOrigin != MeasurementOrigin.ASSIGNED_REFERENCE && nature == HeightNature.TOTAL &&
                                    heightSupport == ObservationSupport.INDIVIDUAL_TREE &&
                                    heightOrigin in listOf(MeasurementOrigin.MEASURED, MeasurementOrigin.ESTIMATED))
                                assertEquals(allowed, form.validation().isValid)
                                assertEquals(allowed, form.toDraft() != null)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun `blank text fields remain unknown and never create source or method defaults`() {
        val blank = " \t\n".repeat(101)
        val form = ObservationQualificationFormState(
            diameterConventionChoice = ObservationDiameterConventionChoice.OTHER,
            diameterOtherConventionReference = blank, diameterMethodReference = blank,
            diameterSourceReference = blank, heightMethodReference = blank, heightSourceReference = blank,
        )
        assertTrue(form.validation().isValid)
        assertEquals(ObservationQualificationDraft(), form.toDraft())
    }

    @Test fun `exact reference length limit accepts 300 characters including retained surrounding whitespace`() {
        val reference = " " + "x".repeat(298) + " "
        val form = ObservationQualificationFormState(
            diameterConventionChoice = ObservationDiameterConventionChoice.OTHER,
            diameterOtherConventionReference = reference, diameterMethodReference = reference,
            diameterSourceReference = reference, heightMethodReference = reference, heightSourceReference = reference,
        )
        assertTrue(form.validation().isValid)
        val draft = requireNotNull(form.toDraft())
        assertEquals(reference, draft.diameter?.conventionReference)
        assertEquals(reference, draft.diameter?.methodReference)
        assertEquals(reference, draft.diameter?.sourceReference)
        assertEquals(reference, draft.height?.methodReference)
        assertEquals(reference, draft.height?.sourceReference)
    }

    @Test fun `every overlong reference blocks the whole declaration without silently truncating input`() {
        val reference = "TEST".repeat(76)
        val forms = listOf(
            ObservationQualificationFormState(diameterConventionChoice = ObservationDiameterConventionChoice.OTHER,
                diameterOtherConventionReference = reference),
            ObservationQualificationFormState(diameterMethodReference = reference),
            ObservationQualificationFormState(diameterSourceReference = reference),
            ObservationQualificationFormState(heightMethodReference = reference),
            ObservationQualificationFormState(heightSourceReference = reference),
        )
        forms.forEach { form ->
            assertFalse(form.validation().isValid)
            assertNull(form.toDraft())
            assertEquals(form, ObservationQualificationFormState.fromSaveableValues(form.toSaveableValues()))
        }
        assertTrue(forms[0].validation().diameterOtherConventionReferenceTooLong)
        assertTrue(forms[1].validation().diameterMethodReferenceTooLong)
        assertTrue(forms[2].validation().diameterSourceReferenceTooLong)
        assertTrue(forms[3].validation().heightMethodReferenceTooLong)
        assertTrue(forms[4].validation().heightSourceReferenceTooLong)
    }

    @Test fun `unused other convention input remains preserved but cannot invalidate a selected known convention`() {
        val form = ObservationQualificationFormState(
            diameterConventionChoice = ObservationDiameterConventionChoice.BREAST_HEIGHT_1_30_M,
            diameterOtherConventionReference = "x".repeat(301),
        )
        assertTrue(form.validation().isValid)
        assertEquals(ObservationDiameterConventionChoice.BREAST_HEIGHT_1_30_M.localReference,
            form.toDraft()?.diameter?.conventionReference)
        assertTrue(form.copy(diameterConventionChoice = ObservationDiameterConventionChoice.OTHER)
            .validation().diameterOtherConventionReferenceTooLong)
    }

    @Test fun `invalid inherited dates block saving rather than being replaced with current time`() {
        listOf(ObservationQualificationFormState(diameterObservedAt = -1),
            ObservationQualificationFormState(heightObservedAt = -1)).forEach { form ->
            assertFalse(form.validation().isValid)
            assertNull(form.toDraft())
            assertEquals(form, ObservationQualificationFormState.fromSaveableValues(form.toSaveableValues()))
        }
    }

    @Test fun `editing one reference preserves position date and every other field`() {
        val changed = pairedForm.copy(diameterSourceReference = " TEST new source ")
        assertEquals(paired.copy(diameter = paired.diameter?.copy(sourceReference = " TEST new source ")),
            changed.toDraft())
        assertEquals(paired, pairedForm.toDraft())
        assertSame(position, changed.position)
    }

    @Test fun `saveable state roundtrips literal position metadata partial inputs and container presence`() {
        val state = pairedForm.copy(heightSourceReference = " TEST pending edit \n")
        val restored = ObservationQualificationFormState.fromSaveableValues(state.toSaveableValues())
        assertEquals(state, restored)
        assertEquals(state.toDraft(), restored?.toDraft())
        assertEquals(position, restored?.position)
    }

    @Test fun `saveable state rejects wrong size unknown enums and coerced boolean or date strings`() {
        val values = pairedForm.toSaveableValues()
        assertNull(ObservationQualificationFormState.fromSaveableValues(values.dropLast(1)))
        assertNull(ObservationQualificationFormState.fromSaveableValues(values + "TEST extra"))
        val changes = listOf(0 to "individual_tree", 1 to "UNKNOWN", 2 to "DBH", 6 to "total",
            7 to "measured", 8 to "individual_tree", 9 to "TRUE", 12 to "1.5",
            13 to "1e3", 14 to "", 15 to "1", 16 to "False", 17 to "UNKNOWN", 20 to " 1")
        changes.forEach { (index, invalid) ->
            val changed = values.toMutableList().apply { this[index] = invalid }
            assertNull("Invalid saveable field $index must not be coerced", ObservationQualificationFormState.fromSaveableValues(changed))
        }
    }

    @Test fun `saveable position absence cannot conceal references or an invalid position record`() {
        val absent = ObservationQualificationFormState().toSaveableValues()
        for (index in 17..20) {
            val hidden = absent.toMutableList().apply { this[index] = "TEST hidden metadata" }
            assertNull(ObservationQualificationFormState.fromSaveableValues(hidden))
        }
        val present = pairedForm.toSaveableValues()
        listOf(20 to "-1", 20 to "9223372036854775808", 18 to "x".repeat(301),
            19 to "x".repeat(301)).forEach { (index, invalid) ->
            val changed = present.toMutableList().apply { this[index] = invalid }
            assertNull(ObservationQualificationFormState.fromSaveableValues(changed))
        }
        assertEquals(ObservationQualificationFormState(), ObservationQualificationFormState.fromSaveableValues(absent))
    }
}
