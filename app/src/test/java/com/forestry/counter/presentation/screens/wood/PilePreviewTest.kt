package com.forestry.counter.presentation.screens.wood

import com.forestry.counter.domain.wood.*
import org.junit.Assert.*
import org.junit.Test

class PilePreviewTest {
    @Test fun `preview uses the common calculator and ignores an incomplete optional date`() {
        val form = WoodManualForm(WoodObjectKind.PILE, length = "10", depth = "2", height = "1,5",
            shape = true, protocol = "TEST measured envelope", observedDate = "02/11/")
        val result = WoodObservationCalculator.calculate(pilePreview(form)) as WoodObservationCalculation.Calculated
        assertEquals(30.0, result.result.volumeM3, 0.0)
        assertNull(result.observation.observedTime)
        assertEquals("1,5", (result.observation.measurement as RawWoodMeasurement.Pile).meanHeight?.rawText)
    }

    @Test fun `missing method does not turn valid dimensions into a volume`() {
        val form = WoodManualForm(WoodObjectKind.PILE, length = "10", depth = "2", height = "1.5")
        val result = WoodObservationCalculator.calculate(pilePreview(form)) as WoodObservationCalculation.Uncalculable
        assertTrue(result.diagnostics.any { it.field == WoodField.SHAPE })
        assertTrue(result.diagnostics.any { it.field == WoodField.PROTOCOL })
    }

    @Test fun `editing clears the old result instead of retaining the previous volume`() {
        val complete = WoodManualForm(WoodObjectKind.PILE, length = "10", depth = "2", height = "1.5", shape = true, protocol = "TEST")
        assertTrue(WoodObservationCalculator.calculate(pilePreview(complete)) is WoodObservationCalculation.Calculated)
        assertTrue(WoodObservationCalculator.calculate(pilePreview(complete.copy(height = ""))) is WoodObservationCalculation.Uncalculable)
    }
}
