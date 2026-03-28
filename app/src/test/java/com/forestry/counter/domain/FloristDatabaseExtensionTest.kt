package com.forestry.counter.domain

import com.forestry.counter.domain.usecase.florist.FloristDatabase
import com.forestry.counter.domain.usecase.florist.IndicHumidite
import com.forestry.counter.domain.usecase.florist.IndicAcidite
import com.forestry.counter.domain.usecase.florist.IndicFertilite
import com.forestry.counter.domain.usecase.florist.TypeMilieu
import org.junit.Assert.*
import org.junit.Test

class FloristDatabaseExtensionTest {

    // ── Comptage global ───────────────────────────────────────────────────────

    @Test
    fun `La base comporte au moins 40 especes au total`() {
        assertTrue(
            "FloristDatabase.species doit avoir >= 40 espèces, obtenu ${FloristDatabase.species.size}",
            FloristDatabase.species.size >= 40
        )
    }

    @Test
    fun `Pas de doublons d ID dans la base`() {
        val ids = FloristDatabase.species.map { it.id }
        assertEquals(
            "Tous les IDs doivent être uniques",
            ids.distinct().size,
            ids.size
        )
    }

    // ── Espèces de l'extension présentes ─────────────────────────────────────

    @Test
    fun `Galium odoratum est présent`() {
        val sp = FloristDatabase.findById("GALIUM_ODORATUM")
        assertNotNull("GALIUM_ODORATUM manquant", sp)
        assertEquals("Aspérule odorante", sp!!.taxonomie.nomFrancais)
    }

    @Test
    fun `Ranunculus repens est présent et marqué hydromorphie`() {
        val sp = FloristDatabase.findById("RANUNCULUS_REPENS")
        assertNotNull("RANUNCULUS_REPENS manquant", sp)
        assertTrue(
            "Renoncule rampante doit indiquer hydromorphie",
            sp!!.valeurIndicatrice.indicateurHydromorphie
        )
    }

    @Test
    fun `Juncus effusus est présent et indicateur compaction`() {
        val sp = FloristDatabase.findById("JUNCUS_EFFUSUS")
        assertNotNull("JUNCUS_EFFUSUS manquant", sp)
        assertTrue(
            "Jonc épars doit indiquer compaction",
            sp!!.valeurIndicatrice.indicateurCompaction
        )
    }

    @Test
    fun `Pseudotsuga menziesii est présent`() {
        assertNotNull("PSEUDOTSUGA_MENZIESII manquant", FloristDatabase.findById("PSEUDOTSUGA_MENZIESII"))
    }

    @Test
    fun `Larix decidua est présent`() {
        assertNotNull("LARIX_DECIDUA manquant", FloristDatabase.findById("LARIX_DECIDUA"))
    }

    @Test
    fun `Pinus pinaster est présent`() {
        assertNotNull("PINUS_PINASTER manquant", FloristDatabase.findById("PINUS_PINASTER"))
    }

    @Test
    fun `Quercus pubescens est présent et basophile`() {
        val sp = FloristDatabase.findById("QUERCUS_PUBESCENS")
        assertNotNull("QUERCUS_PUBESCENS manquant", sp)
        assertEquals(IndicAcidite.BASOPHILE, sp!!.valeurIndicatrice.ellenbergR)
    }

    @Test
    fun `Caltha palustris est présent et hygrophyte strict`() {
        val sp = FloristDatabase.findById("CALTHA_PALUSTRIS")
        assertNotNull("CALTHA_PALUSTRIS manquant", sp)
        assertEquals(IndicHumidite.HYGROPHYTE_STRICT, sp!!.valeurIndicatrice.ellenbergH)
    }

    @Test
    fun `Luzula luzuloides est acidophile`() {
        val sp = FloristDatabase.findById("LUZULA_LUZULOIDES")
        assertNotNull("LUZULA_LUZULOIDES manquant", sp)
        assertEquals(IndicAcidite.ACIDOPHILE, sp!!.valeurIndicatrice.ellenbergR)
    }

    @Test
    fun `Sambucus nigra est nitrophile et indicateur perturbation`() {
        val sp = FloristDatabase.findById("SAMBUCUS_NIGRA")
        assertNotNull("SAMBUCUS_NIGRA manquant", sp)
        assertEquals(IndicFertilite.NITROPHILE, sp!!.valeurIndicatrice.ellenbergN)
        assertTrue(sp.valeurIndicatrice.indicateurPerturbation)
    }

    @Test
    fun `Epilobium angustifolium est indicateur perturbation`() {
        val sp = FloristDatabase.findById("EPILOBIUM_ANGUSTIFOLIUM")
        assertNotNull("EPILOBIUM_ANGUSTIFOLIUM manquant", sp)
        assertTrue(sp!!.valeurIndicatrice.indicateurPerturbation)
    }

    // ── Recherche par nom français ────────────────────────────────────────────

    @Test
    fun `findByNomFrancais Douglas trouve PSEUDOTSUGA_MENZIESII`() {
        val sp = FloristDatabase.findByNomFrancais("Douglas")
        assertNotNull("Recherche 'Douglas' doit trouver une espèce", sp)
    }

    @Test
    fun `findByNomFrancais Aspérule trouve GALIUM_ODORATUM`() {
        val sp = FloristDatabase.findByNomFrancais("Aspérule")
        assertNotNull("Recherche 'Aspérule' doit trouver GALIUM_ODORATUM", sp)
        assertEquals("GALIUM_ODORATUM", sp!!.id)
    }

    // ── findIndicatrices par milieu ───────────────────────────────────────────

    @Test
    fun `findIndicatrices RIPISYLVE retourne au moins 3 especes`() {
        val results = FloristDatabase.findIndicatrices(TypeMilieu.RIPISYLVE)
        assertTrue(
            "Doit trouver >= 3 espèces pour RIPISYLVE, obtenu ${results.size}",
            results.size >= 3
        )
    }

    @Test
    fun `findIndicatrices ZONE_HUMIDE retourne des hygrophytes`() {
        val results = FloristDatabase.findIndicatrices(TypeMilieu.ZONE_HUMIDE)
        assertTrue("Doit retourner au moins une espèce ZONE_HUMIDE", results.isNotEmpty())
        val hygrophytes = results.count { it.valeurIndicatrice.ellenbergH.codeEllenberg >= 4 }
        assertTrue(
            "La majorité des espèces ZONE_HUMIDE doivent être hygrophytes ($hygrophytes/${results.size})",
            hygrophytes >= results.size / 2
        )
    }

    // ── diagnostiquerFlore ────────────────────────────────────────────────────

    @Test
    fun `diagnostiquerFlore avec hygrophytes calcule forte probabilite hydromorphie`() {
        val ids = listOf("JUNCUS_EFFUSUS", "MOLINIA_CAERULEA", "FILIPENDULA_ULMARIA", "RANUNCULUS_REPENS")
        val diag = FloristDatabase.diagnostiquerFlore(ids)
        assertTrue(
            "Probabilité hydromorphie doit être > 0.5 avec 4 hygrophytes, obtenu ${diag.probabiliteHydromorphie}",
            diag.probabiliteHydromorphie > 0.5
        )
    }

    @Test
    fun `diagnostiquerFlore liste vide retourne diagnostic neutre`() {
        val diag = FloristDatabase.diagnostiquerFlore(emptyList())
        assertEquals(3.0, diag.gradientHydriqueDeduît, 0.1)
        assertEquals(3.0, diag.gradientFertiliteDeduît, 0.1)
    }

    @Test
    fun `diagnostiquerFlore especes IDs inconnus retourne valeurs defaut`() {
        val diag = FloristDatabase.diagnostiquerFlore(listOf("INCONNU_A", "INCONNU_B"))
        assertEquals("Aucune espèce reconnue.", diag.interpretationTextuelle)
    }
}
