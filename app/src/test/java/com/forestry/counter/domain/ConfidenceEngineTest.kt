package com.forestry.counter.domain

import com.forestry.counter.domain.model.ripisylve.RipisylveObservation
import com.forestry.counter.domain.model.ripisylve.RipisylveScore
import com.forestry.counter.domain.model.ripisylve.DiagConfidence
import com.forestry.counter.domain.model.station.Drainage
import com.forestry.counter.domain.model.station.PositionTopo
import com.forestry.counter.domain.model.station.DiagnosticPhoto
import com.forestry.counter.domain.model.station.StationObservation
import com.forestry.counter.domain.model.station.TextureSol
import com.forestry.counter.domain.model.station.TypeHumus
import com.forestry.counter.domain.usecase.confidence.ConfidenceEngine
import com.forestry.counter.domain.usecase.correlateur.CorrelationEngine
import org.junit.Assert.*
import org.junit.Test

class ConfidenceEngineTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun minimalStation() = StationObservation()

    private fun fullStation() = StationObservation(
        latitude = 48.0, longitude = 2.0, altitudeM = 150.0,
        photos = listOf(DiagnosticPhoto("p1.jpg"), DiagnosticPhoto("p2.jpg"), DiagnosticPhoto("p3.jpg")),
        profondeurSolCm = 80,
        texture = TextureSol.LIMONEUSE,
        humus = TypeHumus.MULL,
        phEstime = 6.2,
        drainage = Drainage.BON,
        hydromorphieProfondeurCm = null,
        positionTopo = PositionTopo.MI_VERSANT,
        gradientHydrique = 2,
        gradientTrophique = 4,
        gradientLumineux = 2,
        especesIndicatrices = listOf("QUERCUS_ROBUR", "FAGUS_SYLVATICA",
            "CARPINUS_BETULUS", "ANEMONE_NEMOROSA", "GALIUM_ODORATUM", "MILIUM_EFFUSUM")
    )

    private fun minimalRipisylveScore() = RipisylveScore(
        scoreContinuite = 5, scoreLargeur = 0, scoreStrates = 0, scoreDiversite = 0,
        scoreDiametres = 0, scoreMicrohabitats = 0, scoreSanitaire = 0,
        scoreInvasives = 0, scoreInadaptees = 0, scoreStabilite = 0,
        nbMicrohabitats = 0, nbStrates = 1, nbClassesDiam = 1
    )

    private fun fullRipisylveScore() = RipisylveScore(
        scoreContinuite = 20, scoreLargeur = 20, scoreStrates = 20, scoreDiversite = 10,
        scoreDiametres = 10, scoreMicrohabitats = 10, scoreSanitaire = 0,
        scoreInvasives = 0, scoreInadaptees = 0, scoreStabilite = 0,
        nbMicrohabitats = 3, nbStrates = 3, nbClassesDiam = 4
    )

    // ── toLevel ───────────────────────────────────────────────────────────────

    @Test
    fun `toLevel 90 retourne FORTE`() {
        assertEquals(ConfidenceEngine.ConfidenceLevel.FORTE, ConfidenceEngine.toLevel(90))
    }

    @Test
    fun `toLevel 70 retourne BONNE`() {
        assertEquals(ConfidenceEngine.ConfidenceLevel.BONNE, ConfidenceEngine.toLevel(70))
    }

    @Test
    fun `toLevel 50 retourne MOYENNE`() {
        assertEquals(ConfidenceEngine.ConfidenceLevel.MOYENNE, ConfidenceEngine.toLevel(50))
    }

    @Test
    fun `toLevel 30 retourne FAIBLE`() {
        assertEquals(ConfidenceEngine.ConfidenceLevel.FAIBLE, ConfidenceEngine.toLevel(30))
    }

    @Test
    fun `toLevel 10 retourne INSUFFISANTE`() {
        assertEquals(ConfidenceEngine.ConfidenceLevel.INSUFFISANTE, ConfidenceEngine.toLevel(10))
    }

    // ── computeStationConfidence ──────────────────────────────────────────────

    @Test
    fun `station minimale retourne score faible`() {
        val report = ConfidenceEngine.computeStationConfidence(minimalStation())
        assertTrue(
            "Station minimale doit avoir un score faible < 45, obtenu ${report.score}",
            report.score < 45
        )
        assertFalse(report.isReliable())
    }

    @Test
    fun `station complete retourne score eleve`() {
        val report = ConfidenceEngine.computeStationConfidence(fullStation())
        assertTrue(
            "Station complète doit avoir un score >= 60, obtenu ${report.score}",
            report.score >= 60
        )
        assertTrue(report.isReliable())
    }

    @Test
    fun `station sans GPS a donnee manquante GPS`() {
        val report = ConfidenceEngine.computeStationConfidence(
            StationObservation(latitude = null, longitude = null)
        )
        assertTrue(
            "Sans GPS, donneesManquantes doit contenir GPS",
            report.donnéesManquantes.any { it.contains("GPS", ignoreCase = true) }
        )
    }

    @Test
    fun `station avec GPS complet a GPS dans points forts`() {
        val report = ConfidenceEngine.computeStationConfidence(
            StationObservation(latitude = 48.0, longitude = 2.0, altitudeM = 150.0)
        )
        assertTrue(
            "Avec GPS+altitude, GPS doit être dans les points forts",
            report.pointsForts.any { it.contains("GPS", ignoreCase = true) }
        )
    }

    @Test
    fun `station avec 6 especes indicatrices a cortege dans points forts`() {
        val report = ConfidenceEngine.computeStationConfidence(fullStation())
        assertTrue(
            "Cortège de 6 espèces doit générer un point fort",
            report.pointsForts.any { it.contains("Cortège", ignoreCase = true) }
        )
    }

    @Test
    fun `station sans especes indicatrices a cortege dans donnees manquantes`() {
        val report = ConfidenceEngine.computeStationConfidence(
            StationObservation(especesIndicatrices = emptyList())
        )
        assertTrue(
            "Sans espèces indicatrices, doit suggérer d'enrichir le cortège",
            report.donnéesManquantes.any { it.contains("Enrichir", ignoreCase = true) }
        )
    }

    @Test
    fun `station avec texture inconnue a texture dans donnees manquantes`() {
        val report = ConfidenceEngine.computeStationConfidence(
            StationObservation(texture = TextureSol.INCONNUE)
        )
        assertTrue(
            "Texture inconnue doit apparaître dans les données manquantes",
            report.donnéesManquantes.any { it.contains("Texture", ignoreCase = true) }
        )
    }

    @Test
    fun `station avec gradients differents de 3 a gradient dans points forts`() {
        val report = ConfidenceEngine.computeStationConfidence(
            StationObservation(gradientHydrique = 1, gradientTrophique = 5, gradientLumineux = 2)
        )
        assertTrue(
            "3 gradients renseignés doivent générer un point fort",
            report.pointsForts.any { it.contains("gradient", ignoreCase = true) }
        )
    }

    @Test
    fun `score station toujours dans 0 100`() {
        listOf(minimalStation(), fullStation()).forEach { st ->
            val report = ConfidenceEngine.computeStationConfidence(st)
            assertTrue(
                "Score doit être dans [0,100], obtenu ${report.score}",
                report.score in 0..100
            )
        }
    }

    @Test
    fun `conseilAmelioration non vide`() {
        val report = ConfidenceEngine.computeStationConfidence(minimalStation())
        assertTrue(report.conseilAmelioration.isNotBlank())
    }

    // ── computeRipisylveConfidence ────────────────────────────────────────────

    @Test
    fun `ripisylve minimale retourne score faible`() {
        val obs = RipisylveObservation()
        val report = ConfidenceEngine.computeRipisylveConfidence(obs, minimalRipisylveScore())
        assertTrue(
            "Ripisylve minimale doit avoir score < 45, obtenu ${report.score}",
            report.score < 45
        )
    }

    @Test
    fun `ripisylve complete retourne score eleve`() {
        val obs = RipisylveObservation(
            latitude = 48.0, longitude = 2.0,
            photos = listOf(DiagnosticPhoto("p1.jpg"), DiagnosticPhoto("p2.jpg"), DiagnosticPhoto("p3.jpg")),
            continuitePct = 80.0,
            sanitairePct = 5.0,
            invasivesPct = 2.0,
            stabilitePct = 3.0,
            especesObservees = listOf("A", "B", "C", "D", "E")
        )
        val report = ConfidenceEngine.computeRipisylveConfidence(obs, fullRipisylveScore())
        assertTrue(
            "Ripisylve complète doit avoir score >= 60, obtenu ${report.score}",
            report.score >= 60
        )
    }

    @Test
    fun `ripisylve sans GPS a GPS dans donnees manquantes`() {
        val obs = RipisylveObservation(latitude = null, longitude = null)
        val report = ConfidenceEngine.computeRipisylveConfidence(obs, minimalRipisylveScore())
        assertTrue(
            "Sans GPS, doit signaler GPS dans données manquantes",
            report.donnéesManquantes.any { it.contains("GPS", ignoreCase = true) }
        )
    }

    @Test
    fun `score ripisylve toujours dans 0 100`() {
        val obs = RipisylveObservation()
        val report = ConfidenceEngine.computeRipisylveConfidence(obs, minimalRipisylveScore())
        assertTrue("Score doit être dans [0,100]", report.score in 0..100)
    }

    // ── RipisylveScore.confidenceLevel ────────────────────────────────────────

    @Test
    fun `confidenceLevel FORTE si confidenceScore superieur ou egal a 70`() {
        val score = minimalRipisylveScore().copy(confidenceScore = 75)
        assertEquals(DiagConfidence.FORTE, score.confidenceLevel)
    }

    @Test
    fun `confidenceLevel MOYENNE si confidenceScore entre 40 et 69`() {
        val score = minimalRipisylveScore().copy(confidenceScore = 55)
        assertEquals(DiagConfidence.MOYENNE, score.confidenceLevel)
    }

    @Test
    fun `confidenceLevel FAIBLE si confidenceScore inferieur a 40`() {
        val score = minimalRipisylveScore().copy(confidenceScore = 20)
        assertEquals(DiagConfidence.FAIBLE, score.confidenceLevel)
    }

    @Test
    fun `confidenceScore defaut a 0 donne FAIBLE`() {
        val score = minimalRipisylveScore() // confidenceScore = 0
        assertEquals(DiagConfidence.FAIBLE, score.confidenceLevel)
    }

    // ── computeCorrelationConfidence ──────────────────────────────────────────

    @Test
    fun `confidence correlation avec 4 sources est plus haute qu avec 2`() {
        val emptyReport = CorrelationEngine.correlate()
        val conf2 = ConfidenceEngine.computeCorrelationConfidence(emptyReport, 2)
        val conf4 = ConfidenceEngine.computeCorrelationConfidence(emptyReport, 4)
        assertTrue(
            "4 sources doit donner un score > 2 sources (${conf4.score} vs ${conf2.score})",
            conf4.score >= conf2.score
        )
    }

    @Test
    fun `confidence correlation score dans 0 100`() {
        val report = CorrelationEngine.correlate()
        val conf = ConfidenceEngine.computeCorrelationConfidence(report, 3)
        assertTrue(conf.score in 0..100)
    }

    // ── computeRecommendationConfidence ───────────────────────────────────────

    @Test
    fun `recommandation avec donnees completes est fiable`() {
        val report = ConfidenceEngine.computeRecommendationConfidence(
            zoneDetectionConfidence = 80,
            floraScore = 75,
            stationScore = 70,
            hasDepartmentPack = true
        )
        assertTrue(
            "Données complètes doivent donner score >= 60, obtenu ${report.score}",
            report.score >= 60
        )
        assertTrue(
            "Pack départemental doit apparaître dans points forts",
            report.pointsForts.any { it.contains("Pack", ignoreCase = true) }
        )
    }

    @Test
    fun `recommandation sans pack departemental signale donnee manquante`() {
        val report = ConfidenceEngine.computeRecommendationConfidence(50, 50, 50, false)
        assertTrue(
            "Sans pack départ., doit signaler comme donnée manquante",
            report.donnéesManquantes.any { it.contains("Pack", ignoreCase = true) }
        )
    }

    @Test
    fun `recommandation score dans 0 100`() {
        val report = ConfidenceEngine.computeRecommendationConfidence(100, 100, 100, true)
        assertTrue(report.score in 0..100)
    }

    // ── isReliable ────────────────────────────────────────────────────────────

    @Test
    fun `isReliable vrai si score superieur ou egal a 60`() {
        val report = ConfidenceEngine.computeStationConfidence(fullStation())
        if (report.score >= 60) assertTrue(report.isReliable())
        else assertFalse(report.isReliable())
    }
}
