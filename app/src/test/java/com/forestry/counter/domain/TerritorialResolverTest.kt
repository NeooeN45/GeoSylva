package com.forestry.counter.domain

import com.forestry.counter.domain.usecase.territory.TerritorialResolver
import org.junit.Assert.*
import org.junit.Test

class TerritorialResolverTest {

    // ── Détection département ──────────────────────────────────────────────────

    @Test
    fun `Paris centre doit retourner dept 75 region 11`() {
        val result = TerritorialResolver.resolve(48.8566, 2.3522)
        assertEquals("75", result.deptCode)
        assertEquals("11", result.regionCode)
    }

    @Test
    fun `Marseille doit retourner dept 13 region 93`() {
        val result = TerritorialResolver.resolve(43.2965, 5.3698)
        assertEquals("13", result.deptCode)
        assertEquals("93", result.regionCode)
    }

    @Test
    fun `Lyon doit retourner dept 69 region 84`() {
        val result = TerritorialResolver.resolve(45.7640, 4.8357)
        assertEquals("69", result.deptCode)
        assertEquals("84", result.regionCode)
    }

    @Test
    fun `Strasbourg doit retourner dept 67 region 44`() {
        val result = TerritorialResolver.resolve(48.5734, 7.7521)
        assertEquals("67", result.deptCode)
        assertEquals("44", result.regionCode)
    }

    @Test
    fun `Rennes doit retourner dept 35 region 53`() {
        val result = TerritorialResolver.resolve(48.1173, -1.6778)
        assertEquals("35", result.deptCode)
        assertEquals("53", result.regionCode)
    }

    @Test
    fun `Coordonnees hors France retournent null dept et region`() {
        val result = TerritorialResolver.resolve(51.5074, -0.1278) // Londres
        assertNull(result.deptCode)
        assertNull(result.regionCode)
    }

    @Test
    fun `Score de confiance eleve pour grands centres urbains`() {
        val result = TerritorialResolver.resolve(48.8566, 2.3522) // Paris
        assertTrue(
            "Score confiance devrait être > 50 pour Paris, obtenu ${result.confidenceGps}",
            result.confidenceGps > 50
        )
    }

    @Test
    fun `Score de confiance faible pour point distant de tous centroides`() {
        val result = TerritorialResolver.resolve(51.0, -0.5) // hors France
        assertTrue(
            "Score confiance devrait être < 50 hors France, obtenu ${result.confidenceGps}",
            result.confidenceGps < 50
        )
    }

    // ── Interpolation altitude ────────────────────────────────────────────────

    @Test
    fun `Altitude Alpes elevee`() {
        val alt = TerritorialResolver.interpolateAltitude(45.9237, 6.8694) // Chamonix
        assertTrue("Altitude Chamonix devrait être > 800 m, obtenu $alt", alt > 800.0)
    }

    @Test
    fun `Altitude plaine Beauce basse`() {
        val alt = TerritorialResolver.interpolateAltitude(48.4, 1.7) // Beauce
        assertTrue("Altitude Beauce devrait être < 300 m, obtenu $alt", alt < 300.0)
    }

    @Test
    fun `Altitude Pyrenees elevee`() {
        val alt = TerritorialResolver.interpolateAltitude(42.8, 0.1) // Pyrénées centrales
        assertTrue("Altitude Pyrénées devrait être > 600 m, obtenu $alt", alt > 600.0)
    }

    @Test
    fun `Altitude ne doit pas etre negative`() {
        for (lat in listOf(43.0, 46.0, 48.0, 50.0)) {
            for (lon in listOf(-2.0, 1.0, 4.0, 7.0)) {
                val alt = TerritorialResolver.interpolateAltitude(lat, lon)
                assertTrue("Altitude ne doit pas être négative pour ($lat, $lon)", alt >= 0.0)
            }
        }
    }

    // ── Résultats complets ────────────────────────────────────────────────────

    @Test
    fun `Resolve retourne toujours un TerritorialResult non null`() {
        val cases = listOf(
            Pair(48.8566, 2.3522),
            Pair(43.2965, 5.3698),
            Pair(47.0, -2.0),
            Pair(0.0, 0.0)
        )
        cases.forEach { (lat, lon) ->
            val result = TerritorialResolver.resolve(lat, lon)
            assertNotNull("resolve($lat, $lon) ne doit pas retourner null", result)
        }
    }
}
