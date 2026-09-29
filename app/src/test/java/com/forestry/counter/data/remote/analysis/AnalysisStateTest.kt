package com.forestry.counter.data.remote.analysis

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisStateTest {
    @Test
    fun pendingAndRunningAreNotTerminal() {
        assertFalse(AnalysisState.fromWire("pending").isTerminal)
        assertFalse(AnalysisState.fromWire("running").isTerminal)
    }

    @Test
    fun partialCompletedFailedAndExpiredAreTerminal() {
        assertTrue(AnalysisState.fromWire("partial").isTerminal)
        assertTrue(AnalysisState.fromWire("completed").isTerminal)
        assertTrue(AnalysisState.fromWire("failed").isTerminal)
        assertTrue(AnalysisState.fromWire("expired").isTerminal)
    }

    @Test
    fun unknownServerStateFailsClosed() {
        assertTrue(AnalysisState.fromWire("unexpected").isTerminal)
    }
}
