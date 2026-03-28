package com.forestry.counter.domain

import com.forestry.counter.domain.usecase.pack.RegionalPackContent
import org.junit.Assert.*
import org.junit.Test

class RegionalPackContentTest {

    // ── forRegion ─────────────────────────────────────────────────────────────

    @Test
    fun `forRegion null retourne le national par defaut`() {
        val content = RegionalPackContent.forRegion(null)
        assertEquals("FR", content.regionCode)
        assertTrue(content.essencesPrioritaires.isNotEmpty())
    }

    @Test
    fun `forRegion inconnu retourne le national par defaut`() {
        val content = RegionalPackContent.forRegion("99")
        assertEquals("FR", content.regionCode)
    }

    @Test
    fun `Grand Est contient sapin pectiné parmi essences prioritaires`() {
        val content = RegionalPackContent.forRegion("44")
        assertTrue(
            "Grand Est doit contenir Sapin pectiné",
            content.essencesPrioritaires.any { it.contains("Sapin", ignoreCase = true) }
        )
    }

    @Test
    fun `Auvergne Rhone Alpes contient meleze parmi essences prioritaires`() {
        val content = RegionalPackContent.forRegion("84")
        assertTrue(
            "ARA doit contenir Mélèze",
            content.essencesPrioritaires.any { it.contains("Mélèze", ignoreCase = true) }
        )
    }

    @Test
    fun `Nouvelle Aquitaine contient pin maritime`() {
        val content = RegionalPackContent.forRegion("75")
        assertTrue(
            "Nouvelle-Aquitaine doit contenir Pin maritime",
            content.essencesPrioritaires.any { it.contains("maritime", ignoreCase = true) }
        )
    }

    @Test
    fun `PACA a risques climatiques incendie`() {
        val content = RegionalPackContent.forRegion("93")
        assertTrue(
            "PACA doit mentionner risque incendie",
            content.risquesClimatiquesRegionaux.any { it.contains("incendie", ignoreCase = true) }
        )
    }

    @Test
    fun `Corse a altitude montagnard min inferieure a 800`() {
        val content = RegionalPackContent.forRegion("94")
        assertTrue(
            "Corse altitudeMontagnardMin doit être <= 800, obtenu ${content.altitudeMontagnardMin}",
            content.altitudeMontagnardMin <= 800
        )
    }

    @Test
    fun `Bretagne a risques tempetes`() {
        val content = RegionalPackContent.forRegion("53")
        assertTrue(
            "Bretagne doit mentionner tempêtes",
            content.risquesClimatiquesRegionaux.any { it.contains("tempête", ignoreCase = true) }
        )
    }

    // ── resolveEssencesPrioritaires ───────────────────────────────────────────

    @Test
    fun `resolveEssencesPrioritaires avec region et dept retourne liste distincte`() {
        val essences = RegionalPackContent.resolveEssencesPrioritaires("44", "88")
        assertTrue("Doit contenir des essences", essences.isNotEmpty())
        assertEquals("Pas de doublons", essences.distinct().size, essences.size)
    }

    @Test
    fun `resolveEssencesPrioritaires sans region retourne national`() {
        val essences = RegionalPackContent.resolveEssencesPrioritaires(null, null)
        assertTrue("Doit contenir les essences nationales", essences.isNotEmpty())
        assertTrue("Chêne pédonculé doit être présent",
            essences.any { it.contains("pédonculé", ignoreCase = true) })
    }

    @Test
    fun `resolveEssencesPrioritaires ne depasse pas 20 elements`() {
        val essences = RegionalPackContent.resolveEssencesPrioritaires("84", "73")
        assertTrue("Doit retourner au max 20 essences, obtenu ${essences.size}", essences.size <= 20)
    }

    // ── resolveSpeciesIndicatrices ────────────────────────────────────────────

    @Test
    fun `resolveSpeciesIndicatrices null retourne la liste nationale`() {
        val species = RegionalPackContent.resolveSpeciesIndicatrices(null)
        assertTrue("Doit contenir des espèces indicatrices nationales", species.isNotEmpty())
    }

    @Test
    fun `resolveSpeciesIndicatrices Grand Est contient myrtille`() {
        val species = RegionalPackContent.resolveSpeciesIndicatrices("44")
        assertTrue(
            "Grand Est doit contenir Myrtille",
            species.any { it.contains("Myrtille", ignoreCase = true) }
        )
    }

    @Test
    fun `resolveSpeciesIndicatrices retourne liste distincte`() {
        val species = RegionalPackContent.resolveSpeciesIndicatrices("84")
        assertEquals("Pas de doublons", species.distinct().size, species.size)
    }

    // ── SRGSReference ─────────────────────────────────────────────────────────

    @Test
    fun `Regions cles ont une reference SRGS non nulle`() {
        listOf("44", "84", "75", "76", "27", "53").forEach { code ->
            val content = RegionalPackContent.forRegion(code)
            assertNotNull(
                "Région $code doit avoir une référence SRGS",
                content.srgsReference
            )
        }
    }
}
