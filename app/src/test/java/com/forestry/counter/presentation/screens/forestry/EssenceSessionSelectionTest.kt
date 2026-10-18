package com.forestry.counter.presentation.screens.forestry

import com.forestry.counter.domain.model.InventaireSession
import com.forestry.counter.domain.model.MartelageStates
import com.forestry.counter.domain.model.Tige
import org.junit.Assert.*
import org.junit.Test

class EssenceSessionSelectionTest {
    private fun session(id: String, state: String?, created: Long = 1, revision: Int = 1) = InventaireSession(
        sessionId = id, parcelleId = "parcel", typeSession = MartelageStates.TYPE,
        dateDebut = created, dateFin = null, operateurNom = null, methode = null,
        intensiteEchantillonnagePct = null, objectifSession = null, remarques = null,
        createdAt = created, martelageState = state, revisionNumber = revision,
    )
    private fun tree(id: String, sessionId: String?) = Tige(
        id = id, parcelleId = "parcel", placetteId = "plot", essenceCode = "CHP", diamCm = 30.0,
        hauteurM = null, gpsWkt = null, precisionM = null, altitudeM = null,
        note = null, produit = null, fCoef = null, valueEur = null, sessionId = sessionId,
    )

    @Test fun activeCampaignExcludesLegacyAndEveryClosedRevision() {
        val selected = selectEssenceSession(listOf(
            session("old", MartelageStates.CLOSED, 10), session("running", MartelageStates.RUNNING, 2),
            session("legacy", null),
        ), "parcel")
        assertTrue(selected.writable)
        assertEquals("running", selected.session?.sessionId)
        assertEquals(listOf("current"), selected.selectTiges(listOf(
            tree("current", "running"), tree("old", "old"), tree("legacy", "legacy"), tree("null", null),
            tree("other-parent", "running").copy(parcelleId = "other"),
        )).map { it.id })
    }

    @Test fun pausedCampaignRetainsItsCountsAndRefusesEntry() {
        val selected = selectEssenceSession(listOf(session("paused", MartelageStates.PAUSED)), "parcel")
        assertFalse(selected.writable)
        assertEquals(listOf("current"), selected.selectTiges(listOf(tree("current", "paused"))).map { it.id })
    }

    @Test fun latestClosedRevisionIsDisplayedWithoutCombiningEarlierCounts() {
        val selected = selectEssenceSession(listOf(
            session("v1", MartelageStates.CLOSED, 1), session("v2", MartelageStates.CLOSED, 2, 2),
        ), "parcel")
        assertFalse(selected.writable)
        assertEquals("v2", selected.session?.sessionId)
        assertEquals(listOf("corrected"), selected.selectTiges(listOf(
            tree("first", "v1"), tree("corrected", "v2"),
        )).map { it.id })
    }

    @Test fun legacyDoesNotClaimAnUnknownSessionOrRelabelObservations() {
        val rows = listOf(tree("without", null), tree("known", "legacy"), tree("unknown", "other"))
        val selected = selectEssenceSession(listOf(session("legacy", null)), "parcel")
        assertFalse(selected.managed)
        assertTrue(selected.writable)
        assertEquals(listOf("without", "known"), selected.selectTiges(rows).map { it.id })
        assertEquals("legacy", rows[1].sessionId)
        assertEquals(listOf("without"), selectEssenceSession(emptyList(), "parcel").selectTiges(rows).map { it.id })
    }

    @Test fun recoveryAndInconsistentSessionsFailClosed() {
        assertFalse(selectEssenceSession(listOf(
            session("recover", MartelageStates.RUNNING).copy(recoveryRequired = true),
        ), "parcel").writable)
        for (sessions in listOf(
            listOf(session("first", MartelageStates.RUNNING), session("second", MartelageStates.PAUSED)),
            listOf(session("unknown", "FUTURE_STATE")),
            listOf(session("wrong", MartelageStates.RUNNING).copy(typeSession = "OTHER")),
        )) {
            val selected = selectEssenceSession(sessions, "parcel")
            assertTrue(selected.inconsistent)
            assertFalse(selected.writable)
            assertTrue(selected.selectTiges(listOf(tree("hidden", null))).isEmpty())
        }
    }

    @Test fun deletedAndForeignSessionsCannotSelectOrAuthorizeACampaign() {
        val selected = selectEssenceSession(listOf(
            session("deleted", MartelageStates.RUNNING).copy(deletedAt = 1),
            session("foreign", MartelageStates.RUNNING).copy(parcelleId = "other"),
        ), "parcel")
        assertFalse(selected.managed)
        assertEquals(listOf("legacy"), selected.selectTiges(listOf(
            tree("legacy", null), tree("deleted", null).copy(deletedAt = 1), tree("foreign", "foreign"),
            tree("foreign-legacy", null).copy(parcelleId = "other"),
        )).map { it.id })
    }

    @Test fun twoPlotsInOneParcelSelectTheirIndependentActiveSessions() {
        val sessions = listOf(
            session("plot-a-run", MartelageStates.RUNNING).copy(placetteId = "plot-a"),
            session("plot-b-pause", MartelageStates.PAUSED).copy(placetteId = "plot-b"),
        )
        val a = selectEssenceSession(sessions, "parcel", "plot-a")
        val b = selectEssenceSession(sessions, "parcel", "plot-b")
        assertFalse(a.inconsistent)
        assertTrue(a.writable)
        assertEquals("plot-a-run", a.session?.sessionId)
        assertFalse(b.inconsistent)
        assertFalse(b.writable)
        assertEquals("plot-b-pause", b.session?.sessionId)
    }

    @Test fun aNewPlotCannotAdoptASiblingOrPreviousParcelCampaign() {
        val selected = selectEssenceSession(listOf(
            session("old-parcel", MartelageStates.RUNNING),
            session("sibling", MartelageStates.RUNNING).copy(placetteId = "other-plot"),
        ), "parcel", "plot")
        assertFalse(selected.writable)
        assertFalse(selected.managed)
        assertNull(selected.session)
        assertEquals(listOf("legacy-local"), selected.selectTiges(listOf(
            tree("legacy-local", null), tree("old-parcel", "old-parcel"),
            tree("sibling", "sibling").copy(placetteId = "other-plot"),
        )).map { it.id })
    }

    @Test fun aPlotRevisionNeverIncludesEarlierRevisionsOrForeignPlotRows() {
        val selected = selectEssenceSession(listOf(
            session("own-v1", MartelageStates.CLOSED, 1).copy(placetteId = "plot"),
            session("own-v2", MartelageStates.CLOSED, 2, 2).copy(placetteId = "plot"),
            session("sibling-newer", MartelageStates.CLOSED, 3).copy(placetteId = "other-plot"),
        ), "parcel", "plot")
        assertEquals("own-v2", selected.session?.sessionId)
        assertFalse(selected.writable)
        assertEquals(listOf("corrected"), selected.selectTiges(listOf(
            tree("original", "own-v1"), tree("corrected", "own-v2"),
            tree("misassigned", "own-v2").copy(placetteId = "other-plot"),
        )).map { it.id })
    }

    @Test fun incompatibleActiveSessionsFailClosedOnlyInsideTheirOwnPlot() {
        val sessions = listOf(
            session("a-run", MartelageStates.RUNNING).copy(placetteId = "plot-a"),
            session("a-pause", MartelageStates.PAUSED).copy(placetteId = "plot-a"),
            session("b-run", MartelageStates.RUNNING).copy(placetteId = "plot-b"),
        )
        assertTrue(selectEssenceSession(sessions, "parcel", "plot-a").inconsistent)
        assertFalse(selectEssenceSession(sessions, "parcel", "plot-a").writable)
        assertTrue(selectEssenceSession(sessions, "parcel", "plot-b").writable)
        assertFalse(selectEssenceSession(sessions, "parcel", "plot-b").inconsistent)
    }
}
