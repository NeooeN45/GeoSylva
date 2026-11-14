package com.forestry.counter.domain.usecase.maps

import com.forestry.counter.domain.model.Tige
import org.junit.Assert.*
import org.junit.Test

class MapTreeDisplayTest {
    @Test fun `species counts remain scope-wide and colours survive filters and input order`() {
        val trees = (1..18).map { tree("oak-$it", "OAK") } +
            (1..12).map { tree("beech-$it", "BEECH") } +
            (1..6).map { tree("pine-$it", "PINE") } +
            (1..12).map { tree("missing-$it", "OAK", wkt = null) }
        val all = project(trees)
        val hidden = project(trees.reversed(), hidden = setOf(" pine "))
        assertEquals(MapTreeDisplayCounts(48, 36, 12, 6, 30, 0, 0, 30), hidden.counts)
        assertEquals(30, hidden.visibleKeys.size)
        assertEquals(mapOf("OAK" to 18, "BEECH" to 12, "PINE" to 6), hidden.species.associate { it.code to it.locatedCount })
        assertEquals(all.species.associate { it.code to it.colorArgb }, hidden.species.associate { it.code to it.colorArgb })
    }

    @Test fun `accuracy and absent coordinates have distinct denominators and disabling restores only selected species`() {
        val trees = (1..24).map { tree("good-$it", accuracy = 5.0) } +
            (1..4).map { tree("wide-$it", accuracy = 15.0) } +
            listOf(tree("unknown", accuracy = null), tree("zero", accuracy = 0.0)) +
            (1..6).map { tree("hidden-$it", "PINE", accuracy = 30.0) } +
            (1..12).map { tree("missing-$it", wkt = null) }
        val filtered = project(trees, setOf("PINE"), true, 8f)
        assertEquals(MapTreeDisplayCounts(48, 36, 12, 6, 30, 4, 2, 24), filtered.counts)
        val restored = project(trees, setOf("PINE"), false, 8f)
        assertEquals(30, restored.visible.size)
        assertEquals(6, restored.counts.speciesHidden)
        assertEquals(2, restored.counts.unknownAccuracy)
        assertTrue(restored.visible.none { it.tree.essenceCode == "PINE" })
        assertEquals(trees.size, 48) // No record is mutated or removed.
    }

    @Test fun `zero invalid and missing precisions stay unknown instead of becoming perfect fixes`() {
        val trees = listOf(null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).mapIndexed { i, accuracy -> tree("t$i", accuracy = accuracy) }
        val filtered = project(trees, enabled = true)
        assertEquals(5, filtered.counts.unknownAccuracy)
        assertEquals(0, filtered.counts.aboveThreshold)
        assertTrue(filtered.emptyBecauseOfAccuracy)
        assertEquals(5, project(trees, enabled = false).visible.size)
    }

    @Test fun `empty filter reason excludes empty data hidden species and disabled layer`() {
        val poor = listOf(tree("a", accuracy = 40.0), tree("b", accuracy = null))
        assertTrue(project(poor, enabled = true, threshold = 3f).emptyBecauseOfAccuracy)
        assertFalse(project(emptyList(), enabled = true).emptyBecauseOfAccuracy)
        assertFalse(project(poor, hidden = setOf("OAK"), enabled = true).emptyBecauseOfAccuracy)
        assertFalse(MapTreeDisplay.project(poor, emptyList(), emptySet(), true, 3f, false).emptyBecauseOfAccuracy)
    }

    @Test fun `stored thresholds over forty remain effective and equality is included`() {
        val trees = listOf(tree("fifty", accuracy = 50.0), tree("hundred", accuracy = 100.0))
        assertEquals(listOf("fifty"), project(trees, enabled = true, threshold = 50f).visible.map { it.tree.id })
        assertEquals(2, project(trees, enabled = true, threshold = 100f).visible.size)
    }

    @Test fun `identity includes parent and invalid coordinates are counted only as without coordinates`() {
        val records = listOf(tree("same", parcel = "one"), tree("same", parcel = "two"),
            tree("same", parcel = "one"), tree("bad", wkt = "POINT (181 45)"),
            tree("origin", wkt = "POINT (0 0)"), tree("malformed", wkt = "invalid"))
        val result = project(records)
        assertEquals(5, result.counts.total)
        assertEquals(2, result.counts.withoutCoordinates)
        assertEquals(setOf(MapTreeKey("same", "one"), MapTreeKey("same", "two"), MapTreeKey("origin", "parcel")), result.visibleKeys)
    }

    @Test fun `configured colours and normalised species codes remain deterministic`() {
        assertEquals(0xFF336699.toInt(), MapTreeDisplay.speciesColor("oak", "#336699"))
        assertEquals(MapTreeDisplay.speciesColor("OAK"), MapTreeDisplay.speciesColor(" oak ", "broken"))
    }

    private fun project(trees: List<Tige>, hidden: Set<String> = emptySet(), enabled: Boolean = false, threshold: Float = 8f) =
        MapTreeDisplay.project(trees, emptyList(), hidden, enabled, threshold)

    private fun tree(id: String, species: String = "OAK", accuracy: Double? = 5.0,
        wkt: String? = "POINT (5 45)", parcel: String = "parcel") = Tige(
        id = id, parcelleId = parcel, placetteId = null, essenceCode = species, diamCm = 40.0,
        hauteurM = 22.0, gpsWkt = wkt, precisionM = accuracy, altitudeM = null,
        note = null, produit = null, fCoef = null, valueEur = null)
}
