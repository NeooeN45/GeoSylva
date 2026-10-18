package com.forestry.counter.domain.wood

import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WoodObservationHistoryTest {
    private val objectId = id(100)
    private val time = Instant.parse("2026-10-15T12:00:00Z")

    @Test
    fun firstObservationDoesNotBecomeEffectiveUnlessExplicitlyRequested() {
        val original = emptyHistory()
        val observation = observation(1)
        val history = accepted(original.addObservation(observation, 1, makeEffective = false))

        assertEquals(2, history.objectRevision)
        assertEquals(WoodEffectiveSelection.None, history.effectiveSelection)
        assertNull(history.currentStateObservation)
        assertNull(history.effectiveObservationId)
        assertEquals(1, history.records.size)
        assertTrue(original.records.isEmpty())
    }

    @Test
    fun anIncompleteObservationCanBeExplicitlySelectedWithoutClaimingACalculatedQuantity() {
        val history = accepted(emptyHistory().addObservation(observation(1), 1, makeEffective = true))

        assertTrue(history.effectiveSelection is WoodEffectiveSelection.Current)
        val selected = history.currentStateObservation!!
        assertEquals(id(1), selected.observationId)
        assertTrue(WoodObservationCalculator.calculate(selected.observation) is WoodObservationCalculation.Uncalculable)
    }

    @Test
    fun remeasurementCreatesAnIndependentRootAndDoesNotChangeTheExistingSelection() {
        val first = selectedFirst()
        val second = observation(2).copy(recordedAt = time.plusSeconds(86400))
        val history = accepted(first.addObservation(second, first.objectRevision, makeEffective = false))

        assertEquals(id(1), history.effectiveObservationId)
        assertEquals(listOf(id(1), id(2)), history.records.map { it.rootObservationId })
        assertEquals(listOf(1, 1), history.records.map { it.observationRevision })
        assertTrue(history.records.all { it.correctionOfObservationId == null })
        assertEquals(1, first.records.size)
    }

    @Test
    fun deliberateRemeasurementSelectionReplacesThePointerWithoutDeletingHistory() {
        val first = selectedFirst()
        val history = accepted(first.addObservation(observation(2), first.objectRevision, makeEffective = true))

        assertEquals(id(2), history.currentStateObservation!!.observationId)
        assertEquals(2, history.records.size)
        assertEquals(id(1), first.currentStateObservation!!.observationId)
    }

    @Test
    fun correctingAnEffectiveMeasurementKeepsItsExactPointerButExcludesItFromCurrentState() {
        val firstRaw = observation(1, pile(" 3,20 "))
        val first = accepted(emptyHistory().addObservation(firstRaw, 1, makeEffective = true))
        val correctionRaw = observation(2, pile("3,70"))
        val history = accepted(first.correctObservation(correctionRaw, id(1), 1, first.objectRevision, "  Largeur relue  "))

        assertEquals(id(1), history.effectiveObservationId)
        assertTrue(history.effectiveSelection is WoodEffectiveSelection.Superseded)
        assertNull(history.currentStateObservation)
        assertSame(firstRaw, history.records.first().observation)
        assertEquals(" 3,20 ", (history.records.first().observation.measurement as RawWoodMeasurement.Pile).width!!.rawText)
        assertEquals("  Largeur relue  ", history.records.last().correctionReason)
        assertEquals(id(1), history.records.last().rootObservationId)
        assertEquals(id(1), history.records.last().correctionOfObservationId)
        assertEquals(2, history.records.last().observationRevision)
        assertTrue(first.effectiveSelection is WoodEffectiveSelection.Current)
    }

    @Test
    fun choosingTheCorrectionIsASeparateVersionedAction() {
        val corrected = correctedFirst()
        val chosen = accepted(corrected.selectEffectiveObservation(id(2), corrected.objectRevision))

        assertEquals(corrected.objectRevision + 1, chosen.objectRevision)
        assertEquals(id(2), chosen.currentStateObservation!!.observationId)
        assertEquals(corrected.records, chosen.records)
        assertTrue(corrected.effectiveSelection is WoodEffectiveSelection.Superseded)
    }

    @Test
    fun anOldIndependentRootMayBeCorrectedWhileAnotherRemeasurementRemainsEffective() {
        val first = selectedFirst()
        val remeasured = accepted(first.addObservation(observation(2), first.objectRevision, makeEffective = true))
        val corrected = accepted(remeasured.correctObservation(observation(3), id(1), 1, remeasured.objectRevision, "Erreur de saisie"))

        assertEquals(id(2), corrected.currentStateObservation!!.observationId)
        assertEquals(id(1), corrected.records.last().rootObservationId)
        assertEquals(2, corrected.records.last().observationRevision)
    }

    @Test
    fun correctionChainsKeepTheRootAndRequireTheExactUncorrectedHead() {
        val firstCorrection = correctedFirst()
        val secondCorrection = accepted(firstCorrection.correctObservation(
            observation(3), id(2), 2, firstCorrection.objectRevision, "Protocole corrigé",
        ))

        assertEquals(listOf(1, 2, 3), secondCorrection.records.map { it.observationRevision })
        assertTrue(secondCorrection.records.all { it.rootObservationId == id(1) })
        assertEquals(id(2), secondCorrection.records.last().correctionOfObservationId)
        assertNull(secondCorrection.currentStateObservation)
        rejected(WoodHistoryError.OBSERVATION_SUPERSEDED, secondCorrection.correctObservation(
            observation(4), id(1), 1, secondCorrection.objectRevision, "Ancienne version",
        ))
        assertEquals(3, secondCorrection.records.size)
    }

    @Test
    fun aSupersededObservationCannotBeSelectedAgain() {
        val history = correctedFirst()
        rejected(WoodHistoryError.OBSERVATION_SUPERSEDED, history.selectEffectiveObservation(id(1), history.objectRevision))
        assertNull(history.currentStateObservation)
    }

    @Test
    fun staleObjectRevisionRejectsEveryMutationAndPreservesTheDraftAndState() {
        val history = selectedFirst()
        val draft = observation(2, pile("4,20"))
        rejected(WoodHistoryError.OBJECT_REVISION_CONFLICT, history.addObservation(draft, 1, true))
        rejected(WoodHistoryError.OBJECT_REVISION_CONFLICT, history.correctObservation(draft, id(1), 1, 1, "Mesure corrigée"))
        rejected(WoodHistoryError.OBJECT_REVISION_CONFLICT, history.selectEffectiveObservation(id(1), 1))

        assertEquals("4,20", (draft.measurement as RawWoodMeasurement.Pile).width!!.rawText)
        assertEquals(1, history.records.size)
        assertEquals(2, history.objectRevision)
    }

    @Test
    fun theCorrectionAlsoRequiresTheExactObservationRevision() {
        val history = correctedFirst()
        rejected(WoodHistoryError.OBSERVATION_REVISION_CONFLICT, history.correctObservation(
            observation(3), id(2), 1, history.objectRevision, "Mesure corrigée",
        ))
    }

    @Test
    fun observationsFromAnotherPhysicalObjectAreRejectedByAppendCorrectionAndRestore() {
        val history = selectedFirst()
        val foreign = observation(2).copy(physicalObjectId = id(200))
        rejected(WoodHistoryError.OBJECT_MISMATCH, history.addObservation(foreign, history.objectRevision, false))
        rejected(WoodHistoryError.OBJECT_MISMATCH, history.correctObservation(foreign, id(1), 1, history.objectRevision, "Correction"))
        rejected(WoodHistoryError.OBJECT_MISMATCH, restore(listOf(root(foreign))))
    }

    @Test
    fun aPileCannotBecomeALogThroughAppendCorrectionOrRestore() {
        val history = selectedFirst()
        val log = observation(2, RawWoodMeasurement.Log())
        rejected(WoodHistoryError.KIND_MISMATCH, history.addObservation(log, history.objectRevision, false))
        rejected(WoodHistoryError.KIND_MISMATCH, history.correctObservation(log, id(1), 1, history.objectRevision, "Correction"))
        rejected(WoodHistoryError.KIND_MISMATCH, restore(listOf(root(log))))
    }

    @Test
    fun missingTargetsAndSelectionsAreTypedFailuresInsteadOfFallbacks() {
        val history = selectedFirst()
        rejected(WoodHistoryError.OBSERVATION_NOT_FOUND, history.correctObservation(
            observation(2), id(999), 1, history.objectRevision, "Correction",
        ))
        rejected(WoodHistoryError.OBSERVATION_NOT_FOUND, history.selectEffectiveObservation(id(999), history.objectRevision))
        rejected(WoodHistoryError.EFFECTIVE_OBSERVATION_NOT_FOUND, restore(history.records, selectedId = id(999)))
    }

    @Test
    fun duplicateIdsNeverOverwriteAnObservationEvenWithIdenticalPayload() {
        val history = selectedFirst()
        val sameId = observation(1)
        rejected(WoodHistoryError.DUPLICATE_OBSERVATION_ID, history.addObservation(sameId, history.objectRevision, true))
        rejected(WoodHistoryError.DUPLICATE_OBSERVATION_ID, history.correctObservation(sameId, id(1), 1, history.objectRevision, "Correction"))
        rejected(WoodHistoryError.DUPLICATE_OBSERVATION_ID, restore(listOf(root(sameId), root(sameId))))
        assertEquals(1, history.records.size)
    }

    @Test
    fun correctionsRequireANonBlankReasonAndNeverTruncateIt() {
        val history = selectedFirst()
        for (reason in listOf("", " \t\r\n", "\u0085", "\u00a0")) {
            rejected(WoodHistoryError.CORRECTION_REASON_REQUIRED, history.correctObservation(
                observation(2), id(1), 1, history.objectRevision, reason,
            ))
        }
        val longest = "🌲".repeat(5_000)
        val corrected = accepted(history.correctObservation(observation(2), id(1), 1, history.objectRevision, longest))
        assertEquals(longest, corrected.records.last().correctionReason)
        rejected(WoodHistoryError.CORRECTION_REASON_TOO_LONG, history.correctObservation(
            observation(2), id(1), 1, history.objectRevision, longest + "!",
        ))
    }

    @Test
    fun restoredCorrectionMustHaveAKnownParentAndRoot() {
        val first = root(observation(1))
        val correction = correction(2, 1, 1, 2)
        rejected(WoodHistoryError.CORRECTION_TARGET_NOT_FOUND, restore(listOf(first, correction.copy(correctionOfObservationId = id(999)))))
        rejected(WoodHistoryError.ROOT_NOT_FOUND, restore(listOf(first, correction.copy(rootObservationId = id(999)))))
    }

    @Test
    fun selfCorrectionAndLongerCyclesAreRejectedDuringRestore() {
        val self = correction(1, 1, 1, 1)
        rejected(WoodHistoryError.CORRECTION_CYCLE, restore(listOf(self)))

        val first = correction(1, 1, 3, 1)
        val second = correction(2, 1, 1, 2)
        val third = correction(3, 1, 2, 3)
        rejected(WoodHistoryError.CORRECTION_CYCLE, restore(listOf(first, second, third)))
    }

    @Test
    fun competingCorrectionsCannotBeRestoredAsOneLinearHistory() {
        rejected(WoodHistoryError.CORRECTION_FORK, restore(listOf(
            root(observation(1)), correction(2, 1, 1, 2), correction(3, 1, 1, 2),
        )))
    }

    @Test
    fun correctionCannotSwitchToAnotherIndependentRoot() {
        rejected(WoodHistoryError.ROOT_MISMATCH, restore(listOf(
            root(observation(1)), root(observation(2)), correction(3, 2, 1, 2),
        )))
    }

    @Test
    fun rootsAndCorrectionRevisionsAreValidatedInsteadOfRepaired() {
        val first = root(observation(1))
        rejected(WoodHistoryError.INVALID_ROOT, restore(listOf(first.copy(observationRevision = 2))))
        rejected(WoodHistoryError.INVALID_ROOT, restore(listOf(first.copy(correctionReason = "not a correction"))))
        rejected(WoodHistoryError.INVALID_OBSERVATION_REVISION, restore(listOf(first.copy(observationRevision = 0))))
        rejected(WoodHistoryError.INVALID_REVISION_SEQUENCE, restore(listOf(first, correction(2, 1, 1, 3))))
        rejected(WoodHistoryError.CORRECTION_REASON_REQUIRED, restore(listOf(first, correction(2, 1, 1, 2).copy(correctionReason = null))))
    }

    @Test
    fun restoringAnExistingSupersededSelectionPreservesItForHistory() {
        val restored = accepted(restore(listOf(root(observation(1)), correction(2, 1, 1, 2)), selectedId = id(1)))
        assertEquals(id(1), restored.effectiveObservationId)
        assertTrue(restored.effectiveSelection is WoodEffectiveSelection.Superseded)
        assertNull(restored.currentStateObservation)
    }

    @Test
    fun inputOrderingAndTimestampDoNotDetermineSelectionOrLineage() {
        val first = root(observation(1).copy(recordedAt = time.plusSeconds(1000)))
        val second = correction(2, 1, 1, 2)
        val remeasurement = root(observation(3).copy(recordedAt = time.minusSeconds(1000)))
        val history = accepted(restore(listOf(second, remeasurement, first), selectedId = id(3)))

        assertEquals(listOf(id(2), id(3), id(1)), history.records.map { it.observationId })
        assertEquals(id(3), history.currentStateObservation!!.observationId)
        assertTrue(accepted(restore(listOf(second, first), selectedId = null)).effectiveSelection is WoodEffectiveSelection.None)
    }

    @Test
    fun recordsAreDefensivelyCopiedAndCannotBeMutatedThroughAListCast() {
        val input = mutableListOf(root(observation(1)))
        val history = accepted(restore(input, selectedId = id(1)))
        input.clear()
        assertEquals(1, history.records.size)
        try {
            (history.records as MutableList<WoodObservationRecord>).clear()
            fail("Expected an immutable history")
        } catch (_: UnsupportedOperationException) {
            assertEquals(id(1), history.currentStateObservation!!.observationId)
        }
    }

    @Test
    fun invalidOrExhaustedAggregateRevisionsDoNotWrapAround() {
        rejected(WoodHistoryError.INVALID_OBJECT_REVISION, restore(emptyList(), revision = 0))
        val history = accepted(restore(listOf(root(observation(1))), revision = Int.MAX_VALUE, selectedId = id(1)))
        rejected(WoodHistoryError.REVISION_EXHAUSTED, history.addObservation(observation(2), Int.MAX_VALUE, false))
        rejected(WoodHistoryError.REVISION_EXHAUSTED, history.correctObservation(observation(2), id(1), 1, Int.MAX_VALUE, "Correction"))
        rejected(WoodHistoryError.REVISION_EXHAUSTED, history.selectEffectiveObservation(id(1), Int.MAX_VALUE))
        assertEquals(Int.MAX_VALUE, history.objectRevision)
    }

    @Test
    fun selectedCorrectionUsesItsOwnRawMeasurementsAndLeavesPreviousCalculationReproducible() {
        val first = accepted(emptyHistory().addObservation(observation(1, pile("3,2")), 1, true))
        val before = WoodObservationCalculator.calculate(first.currentStateObservation!!.observation) as WoodObservationCalculation.Calculated
        val corrected = accepted(first.correctObservation(observation(2, pile("4")), id(1), 1, first.objectRevision, "Largeur corrigée"))
        val selected = accepted(corrected.selectEffectiveObservation(id(2), corrected.objectRevision))
        val after = WoodObservationCalculator.calculate(selected.currentStateObservation!!.observation) as WoodObservationCalculation.Calculated

        assertEquals(6.4, before.result.volumeM3, 0.000001)
        assertEquals(8.0, after.result.volumeM3, 0.000001)
        val historical = WoodObservationCalculator.calculate(selected.records.first().observation) as WoodObservationCalculation.Calculated
        assertEquals(before.result.volumeM3, historical.result.volumeM3, 0.0)
        assertEquals(before.result.input, historical.result.input)
        assertEquals(before.result.contractVersion, historical.result.contractVersion)
    }

    private fun emptyHistory() = accepted(restore(emptyList(), revision = 1))

    private fun selectedFirst() = accepted(emptyHistory().addObservation(observation(1), 1, makeEffective = true))

    private fun correctedFirst(): WoodObservationHistory {
        val first = selectedFirst()
        return accepted(first.correctObservation(observation(2), id(1), 1, first.objectRevision, "Diamètre corrigé"))
    }

    private fun restore(
        records: List<WoodObservationRecord>,
        revision: Int = 10,
        selectedId: UUID? = null,
    ) = WoodObservationHistory.restore(objectId, WoodObjectKind.PILE, revision, records, selectedId)

    private fun root(observation: RawWoodObservation) = WoodObservationRecord(observation, observation.observationId, 1)

    private fun correction(number: Long, root: Long, target: Long, revision: Int) = WoodObservationRecord(
        observation(number), id(root), revision, id(target), "Correction du relevé",
    )

    private fun observation(number: Long, measurement: RawWoodMeasurement = RawWoodMeasurement.Pile()) =
        RawWoodObservation(id(number), objectId, time, measurement)

    private fun pile(width: String) = RawWoodMeasurement.Pile(
        length = RawWoodDimension("2", "m"),
        width = RawWoodDimension(width, "m"),
        meanHeight = RawWoodDimension("1", "m"),
        shapeCode = RawWoodMeasurement.Pile.RECTANGULAR_ENVELOPE,
        protocolReference = "Protocole déclaré TEST",
    )

    private fun accepted(result: WoodHistoryResult): WoodObservationHistory {
        assertTrue("Expected accepted, got $result", result is WoodHistoryResult.Accepted)
        return (result as WoodHistoryResult.Accepted).history
    }

    private fun rejected(expected: WoodHistoryError, result: WoodHistoryResult) {
        assertTrue("Expected rejected, got $result", result is WoodHistoryResult.Rejected)
        assertEquals(expected, (result as WoodHistoryResult.Rejected).diagnostic.code)
    }

    private fun id(number: Long) = UUID(0L, number)
}
