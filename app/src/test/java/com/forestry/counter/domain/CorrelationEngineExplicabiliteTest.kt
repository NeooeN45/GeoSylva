package com.forestry.counter.domain

import com.forestry.counter.domain.model.station.Drainage
import com.forestry.counter.domain.model.station.PositionTopo
import com.forestry.counter.domain.model.station.StationObservation
import com.forestry.counter.domain.model.station.TextureSol
import com.forestry.counter.domain.model.ripisylve.RipisylveObservation
import com.forestry.counter.domain.usecase.correlateur.CorrelationEngine
import com.forestry.counter.domain.usecase.florist.GradientInferenceEngine
import com.forestry.counter.domain.usecase.florist.IndicAcidite
import org.junit.Assert.*
import org.junit.Test

class CorrelationEngineExplicabiliteTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun station(
        gradH: Int = 3, gradT: Int = 3,
        drainage: Drainage = Drainage.BON,
        lat: Double? = 48.0, lon: Double? = 2.0,
        hydroDepth: Int? = null,
        positionTopo: PositionTopo = PositionTopo.MI_VERSANT,
        texture: TextureSol = TextureSol.LIMONEUSE
    ) = StationObservation(
        gradientHydrique = gradH, gradientTrophique = gradT,
        drainage = drainage, latitude = lat, longitude = lon,
        hydromorphieProfondeurCm = hydroDepth, positionTopo = positionTopo,
        texture = texture
    )

    private fun floraGradient(
        hydrique: Double = 3.0, trophique: Double = 3.0,
        probHydro: Double = 0.0, probPerturb: Double = 0.0,
        nbTaxons: Int = 5
    ) = GradientInferenceEngine.GradientResult(
        hydrique = hydrique,
        trophique = trophique,
        acidite = IndicAcidite.NEUTROPHILE,
        aciditeScore = 3.0,
        lumiere = 3.0,
        probabiliteHydromorphie = probHydro,
        probabilitePerturbation = probPerturb,
        probabiliteCompaction = 0.0,
        hydriqueLabelFr = "Mésophyte",
        trophiqueLabelFr = "Mésotrophe",
        cohérenceInterne = GradientInferenceEngine.Coherence.MOYENNE,
        confidenceLevel = GradientInferenceEngine.ConfidenceGradient.MOYENNE,
        nbTaxonsAnalysables = nbTaxons,
        nbTaxonsTotaux = nbTaxons,
        especesTirantVersSec = emptyList(),
        especesTirantVersFrais = emptyList(),
        especesContradictoires = emptyList(),
        conflits = emptyList(),
        observationsComplementaires = emptyList(),
        syntheseTextuelle = "Test"
    )

    private fun ripisylve(
        continuite: Double = 70.0,
        invasives: Double = 10.0,
        stabilitePct: Double = 10.0,
        strateHerbacee: Boolean = true
    ) = RipisylveObservation(
        continuitePct = continuite,
        invasivesPct = invasives,
        stabilitePct = stabilitePct,
        strateHerbacee = strateHerbacee
    )

    // ── Rapport non null ──────────────────────────────────────────────────────

    @Test
    fun `correlate sans donnees retourne rapport coherence INSUFFISANT`() {
        val report = CorrelationEngine.correlate()
        assertEquals(CorrelationEngine.CoherenceGlobale.INSUFFISANT, report.coherenceGlobale)
    }

    @Test
    fun `correlate avec station seule retourne rapport non null`() {
        val report = CorrelationEngine.correlate(station = station())
        assertNotNull(report)
        assertNotNull(report.conclusionPrincipale)
    }

    // ── Confirmations ─────────────────────────────────────────────────────────

    @Test
    fun `gradient hydrique coherent genere une confirmation`() {
        val report = CorrelationEngine.correlate(
            station = station(gradH = 3),
            floraGradients = floraGradient(hydrique = 3.5)
        )
        val hasConfirmation = report.confirmations.any {
            it.description.contains("hydrique", ignoreCase = true) &&
            it.type == CorrelationEngine.FactType.CONFIRMATION
        }
        assertTrue("Doit avoir une confirmation hydrique pour Δ=0.5", hasConfirmation)
    }

    @Test
    fun `confirmation hydrique a un raisonnement non vide`() {
        val report = CorrelationEngine.correlate(
            station = station(gradH = 3),
            floraGradients = floraGradient(hydrique = 3.2)
        )
        val fact = report.confirmations.firstOrNull {
            it.description.contains("hydrique", ignoreCase = true)
        }
        assertNotNull("Devrait avoir une confirmation hydrique", fact)
        assertTrue(
            "Le raisonnement doit être non vide pour une confirmation",
            fact!!.raisonnement.isNotBlank()
        )
    }

    @Test
    fun `hydromorphie confirmee par flore et profil sol`() {
        val report = CorrelationEngine.correlate(
            station = station(gradH = 4, hydroDepth = 30),
            floraGradients = floraGradient(probHydro = 0.5)
        )
        val hasHydroConfirmation = report.confirmations.any {
            it.description.contains("Hydromorphie confirmée", ignoreCase = true)
        }
        assertTrue("Doit confirmer hydromorphie flore+sol", hasHydroConfirmation)
    }

    // ── Contradictions ────────────────────────────────────────────────────────

    @Test
    fun `gradient hydrique tres different genere une contradiction`() {
        val report = CorrelationEngine.correlate(
            station = station(gradH = 1, drainage = Drainage.EXCESSIF),
            floraGradients = floraGradient(hydrique = 5.5)
        )
        assertTrue(
            "Doit avoir au moins une contradiction pour Δ=4.5",
            report.contradictions.isNotEmpty()
        )
    }

    @Test
    fun `contradiction hydrique forte genere une alerte critique`() {
        val report = CorrelationEngine.correlate(
            station = station(gradH = 1),
            floraGradients = floraGradient(hydrique = 6.0) // Δ = 5
        )
        assertTrue(
            "Doit générer une alerte critique pour Δ > 3",
            report.alertesCritiques.any { it.contains("hydrique", ignoreCase = true) }
        )
    }

    @Test
    fun `contradiction possede un raisonnement explicatif`() {
        val report = CorrelationEngine.correlate(
            station = station(gradH = 1),
            floraGradients = floraGradient(hydrique = 5.0)
        )
        val contradiction = report.contradictions.firstOrNull {
            it.description.contains("hydrique", ignoreCase = true)
        }
        assertNotNull("Devrait avoir une contradiction hydrique", contradiction)
        assertTrue(
            "La contradiction doit avoir un raisonnement",
            contradiction!!.raisonnement.isNotBlank()
        )
    }

    @Test
    fun `flore hygrophile avec drainage BON genere contradiction`() {
        val report = CorrelationEngine.correlate(
            station = station(drainage = Drainage.BON),
            floraGradients = floraGradient(probHydro = 0.55)
        )
        assertTrue(
            "Flore hygrophile + drainage bon = contradiction",
            report.contradictions.isNotEmpty()
        )
    }

    // ── Données manquantes ────────────────────────────────────────────────────

    @Test
    fun `sans station les donnees manquantes incluent station`() {
        val report = CorrelationEngine.correlate(floraIds = listOf("JUNCUS_EFFUSUS"))
        assertTrue(
            "Sans station, donneesManquantes doit mentionner station",
            report.donneesManquantes.any { it.source == CorrelationEngine.DataSource.STATION }
        )
    }

    @Test
    fun `sans coordonnees GPS les donnees manquantes incluent GPS`() {
        val report = CorrelationEngine.correlate(
            station = station(lat = null, lon = null)
        )
        assertTrue(
            "Sans GPS, donneesManquantes doit mentionner GPS",
            report.donneesManquantes.any { it.source == CorrelationEngine.DataSource.GPS }
        )
    }

    @Test
    fun `flore insuffisante genere donnee manquante flore`() {
        val report = CorrelationEngine.correlate(
            station = station(),
            floraGradients = null,
            floraIds = emptyList()
        )
        assertTrue(
            "Flore absente doit générer donnée manquante FLORE",
            report.donneesManquantes.any { it.source == CorrelationEngine.DataSource.FLORE }
        )
    }

    // ── Conclusion structurée ─────────────────────────────────────────────────

    @Test
    fun `conclusion est non null meme sans donnees`() {
        val report = CorrelationEngine.correlate()
        assertNotNull(report.conclusionPrincipale)
    }

    @Test
    fun `conclusion avec confirmations a recommandation non vide`() {
        val report = CorrelationEngine.correlate(
            station = station(gradH = 3),
            floraGradients = floraGradient(hydrique = 3.0, trophique = 3.0, nbTaxons = 5)
        )
        assertTrue(
            "La conclusion doit avoir une recommandation terrain non vide",
            report.conclusionPrincipale.recommandationTerrain.isNotBlank()
        )
    }

    @Test
    fun `conclusion coherente a points certains non vides si confirmations presentes`() {
        val report = CorrelationEngine.correlate(
            station = station(gradH = 3),
            floraGradients = floraGradient(hydrique = 3.2)
        )
        if (report.confirmations.isNotEmpty()) {
            assertTrue(
                "pointsCertains doit lister les confirmations",
                report.conclusionPrincipale.pointsCertains.isNotEmpty()
            )
        }
    }

    @Test
    fun `synthese textuelle non vide`() {
        val report = CorrelationEngine.correlate(
            station = station(),
            floraGradients = floraGradient()
        )
        assertTrue(
            "syntheseTextuelle doit être non vide",
            report.syntheseTextuelle.isNotBlank()
        )
    }

    // ── Score cohérence ───────────────────────────────────────────────────────

    @Test
    fun `score coherence entre 0 et 1`() {
        val report = CorrelationEngine.correlate(
            station = station(),
            floraGradients = floraGradient()
        )
        assertTrue(
            "scoreCoherence doit être dans [0, 1], obtenu ${report.scoreCoherence}",
            report.scoreCoherence in 0f..1f
        )
    }

    @Test
    fun `score coherence plus eleve quand pas de contradictions`() {
        val coherent = CorrelationEngine.correlate(
            station = station(gradH = 3),
            floraGradients = floraGradient(hydrique = 3.0, trophique = 3.0)
        )
        val incoherent = CorrelationEngine.correlate(
            station = station(gradH = 1),
            floraGradients = floraGradient(hydrique = 6.0, trophique = 6.0)
        )
        assertTrue(
            "Score cohérent doit être >= score incohérent",
            coherent.scoreCoherence >= incoherent.scoreCoherence
        )
    }
}
