package com.forestry.counter.domain.classification.stand

import com.forestry.counter.presentation.screens.forestry.*
import com.forestry.counter.domain.calculation.quality.WoodQualityGrade
import com.forestry.counter.domain.calculation.SanityWarning
import com.forestry.counter.domain.calculation.SanitySeverity
import com.forestry.counter.domain.calculation.SanityDomain
import org.junit.Assert.*
import org.junit.Test

class StandClassificationEngineTest {

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun essenceStats(code: String, name: String, gPct: Double) = PerEssenceStats(
        essenceCode = code, essenceName = name,
        n = 100, nPct = gPct,
        vTotal = 0.0, vPct = 0.0, vPerHa = 0.0,
        gTotal = 0.0, gPct = gPct, gPerHa = 0.0,
        dm = null, dg = null,
        meanPricePerM3 = null, revenueTotal = null, revenuePerHa = null
    )

    private fun baseStats(
        nTotal: Int = 200,
        nPerHa: Double = 400.0,
        gPerHa: Double = 22.0,
        dg: Double = 30.0,
        cvDiam: Double = 20.0,
        dm: Double? = 28.0,
        meanH: Double? = 20.0,
        hLorey: Double? = 22.0,
        perEssence: List<PerEssenceStats> = listOf(essenceStats("CH_SESSILE", "Chêne sessile", 85.0)),
        classDistribution: List<ClassDistEntry> = listOf(
            ClassDistEntry(20, 80, 0.5, null),
            ClassDistEntry(30, 80, 0.8, null),
            ClassDistEntry(40, 40, 0.6, null)
        ),
        sanityWarnings: List<SanityWarning> = emptyList(),
        biodiversity: BiodiversityIndex? = null
    ) = MartelageStats(
        nTotal = nTotal, nPerHa = nPerHa, gTotal = 11.0, gPerHa = gPerHa,
        vTotal = 0.0, vPerHa = 0.0, unpricedVolumeTotal = 0.0, unpricedVolumePerHa = 0.0,
        unpricedEssenceNames = emptyList(), revenueTotal = null, revenuePerHa = null,
        dm = dm, meanH = meanH, dg = dg, hLorey = hLorey,
        dMin = 10.0, dMax = 60.0, cvDiam = cvDiam, ratioVG = null,
        surfaceHa = 0.5, classDistribution = classDistribution,
        qualityDistribution = emptyList(), qualityAssessedCount = 0, qualityTotalCount = 0,
        perEssence = perEssence, volumeAvailable = false, volumeCompletenessPct = 0.0,
        missingHeightEssenceCodes = emptyList(), missingHeightEssenceNames = emptyList(),
        sanityWarnings = sanityWarnings, biodiversity = biodiversity
    )

    // ─── Tests classify() ─────────────────────────────────────────────────────

    @Test
    fun `classify — returns non-null result for minimal stats`() {
        val result = StandClassificationEngine.classify(baseStats())
        assertNotNull(result)
    }

    @Test
    fun `classify — confidence between 0 and 1`() {
        val result = StandClassificationEngine.classify(baseStats())
        assertTrue("confidence should be >= 0.20", result.confidence >= 0.20f)
        assertTrue("confidence should be <= 1.00", result.confidence <= 1.00f)
    }

    @Test
    fun `classify — pure feuillu when dominant essence at 85 pct`() {
        val result = StandClassificationEngine.classify(baseStats())
        assertEquals(StandComposition.PUR_FEUILLU, result.composition)
    }

    @Test
    fun `classify — pure resineux when Douglas at 90 pct`() {
        val stats = baseStats(
            perEssence = listOf(essenceStats("DOUGLAS_VERT", "Douglas vert", 90.0))
        )
        val result = StandClassificationEngine.classify(stats)
        assertEquals(StandComposition.PUR_RESINEUX, result.composition)
    }

    @Test
    fun `classify — mixed composition when 3 essences with no dominant`() {
        val stats = baseStats(
            perEssence = listOf(
                essenceStats("CH_SESSILE", "Chêne sessile", 40.0),
                essenceStats("HETRE_COMMUN", "Hêtre commun", 35.0),
                essenceStats("CHARME", "Charme", 25.0)
            )
        )
        val result = StandClassificationEngine.classify(stats)
        assertNotEquals(StandComposition.PUR_FEUILLU, result.composition)
        assertNotEquals(StandComposition.PUR_RESINEUX, result.composition)
    }

    @Test
    fun `classify — equienne structure for low CV`() {
        val result = StandClassificationEngine.classify(baseStats(cvDiam = 10.0))
        assertEquals(AgeStructure.EQUIENNE, result.ageStructure)
    }

    @Test
    fun `classify — irreguliere for high CV`() {
        val result = StandClassificationEngine.classify(baseStats(cvDiam = 60.0))
        assertTrue(
            "Expected irregular structure, got ${result.ageStructure}",
            result.ageStructure in listOf(
                AgeStructure.IRREGULIERE_SIMPLE,
                AgeStructure.IRREGULIERE_COMPLEXE,
                AgeStructure.IRREGULIERE_EQUILIBREE,
                AgeStructure.IRREGULIERE_DESEQUILIBREE
            )
        )
    }

    @Test
    fun `classify — plantation origin when user confirms plantation`() {
        val result = StandClassificationEngine.classify(
            baseStats(cvDiam = 8.0, perEssence = listOf(essenceStats("DOUGLAS_VERT", "Douglas", 100.0))),
            userAnswers = mapOf("is_plantation" to 1)
        )
        assertEquals(StandOrigin.PLANTATION_MONO, result.origin)
    }

    @Test
    fun `classify — taillis origin when user confirms cepees`() {
        val result = StandClassificationEngine.classify(
            baseStats(nPerHa = 2000.0, dg = 15.0, cvDiam = 30.0),
            userAnswers = mapOf("has_cepees" to 2)
        )
        assertEquals(StandOrigin.REJETS_SOUCHE, result.origin)
    }

    @Test
    fun `classify — ripisylve ecological type when aulne present`() {
        val result = StandClassificationEngine.classify(
            baseStats(perEssence = listOf(essenceStats("AULNE_GLUT", "Aulne glutineux", 80.0)))
        )
        assertEquals(EcologicalType.RIPISYLVE, result.ecologicalType)
    }

    @Test
    fun `classify — montagnarde type when sapin present`() {
        val result = StandClassificationEngine.classify(
            baseStats(perEssence = listOf(essenceStats("SAPIN_PECTINE", "Sapin pectiné", 80.0)))
        )
        assertEquals(EcologicalType.MONTAGNARDE, result.ecologicalType)
    }

    @Test
    fun `classify — ecological type from user answer overrides heuristic`() {
        val stats = baseStats(perEssence = listOf(essenceStats("AULNE_GLUT", "Aulne", 80.0)))
        val result = StandClassificationEngine.classify(stats, userAnswers = mapOf("eco_zone" to 2))
        assertEquals(EcologicalType.MEDITERRANEENNE, result.ecologicalType)
    }

    @Test
    fun `classify — disturbance DEPERISSANT when many dying trees`() {
        val bio = BiodiversityIndex(
            shannonH = 1.5, pielou = 0.8, speciesCount = 3, tgbCount = 2,
            bioTreeCount = 1, deadTreeCount = 10, dyingTreeCount = 50,
            ibpScore = 5, ibpMax = 10, ibpDetails = emptyList()
        )
        val result = StandClassificationEngine.classify(baseStats(nTotal = 200, biodiversity = bio))
        assertEquals(DisturbanceState.DEPERISSANT, result.disturbanceState)
    }

    @Test
    fun `classify — disturbance SAIN when no issues`() {
        val result = StandClassificationEngine.classify(baseStats())
        assertEquals(DisturbanceState.SAIN, result.disturbanceState)
    }

    @Test
    fun `classify — management program is not null`() {
        val result = StandClassificationEngine.classify(baseStats())
        assertNotNull(result.managementProgram)
        assertTrue("Program objective should not be blank",
            result.managementProgram.objectiveLabel.isNotBlank())
    }

    @Test
    fun `classify — diagnosis is not null with non-blank label`() {
        val result = StandClassificationEngine.classify(baseStats())
        assertNotNull(result.diagnosis)
        assertTrue(result.diagnosis.standTypeLabel.isNotBlank())
        assertTrue(result.diagnosis.standTypeCode.isNotBlank())
    }

    @Test
    fun `classify — unanswered questions reduce confidence`() {
        val confWithAnswers = StandClassificationEngine.classify(
            baseStats(nPerHa = 2000.0, dg = 15.0, cvDiam = 30.0),
            userAnswers = mapOf("has_cepees" to 1, "has_reserves" to 0,
                "has_regeneration" to 0, "is_plantation" to 0, "eco_zone" to 0)
        ).confidence
        val confWithout = StandClassificationEngine.classify(
            baseStats(nPerHa = 2000.0, dg = 15.0, cvDiam = 30.0),
            userAnswers = emptyMap()
        ).confidence
        assertTrue("Answered questions should increase confidence",
            confWithAnswers >= confWithout)
    }

    // ─── Tests DevelopmentStage.fromDg ────────────────────────────────────────

    @Test
    fun `fromDg — SEMIS for dg below 2_5`() {
        assertEquals(DevelopmentStage.SEMIS, DevelopmentStage.fromDg(1.0))
    }

    @Test
    fun `fromDg — FOURRE for dg 5`() {
        assertEquals(DevelopmentStage.FOURRE, DevelopmentStage.fromDg(5.0))
    }

    @Test
    fun `fromDg — GAULIS for dg 12`() {
        assertEquals(DevelopmentStage.GAULIS, DevelopmentStage.fromDg(12.0))
    }

    @Test
    fun `fromDg — PERCHIS for dg 20`() {
        assertEquals(DevelopmentStage.PERCHIS, DevelopmentStage.fromDg(20.0))
    }

    @Test
    fun `fromDg — JEUNE_FUTAIE for dg 25`() {
        assertEquals(DevelopmentStage.JEUNE_FUTAIE, DevelopmentStage.fromDg(25.0))
    }

    @Test
    fun `fromDg — FUTAIE_ADULTE for dg 40`() {
        assertEquals(DevelopmentStage.FUTAIE_ADULTE, DevelopmentStage.fromDg(40.0))
    }

    @Test
    fun `fromDg — FUTAIE_MURE for dg 60`() {
        assertEquals(DevelopmentStage.FUTAIE_MURE, DevelopmentStage.fromDg(60.0))
    }

    @Test
    fun `fromDg — FUTAIE_SURANNEE for dg 80`() {
        assertEquals(DevelopmentStage.FUTAIE_SURANNEE, DevelopmentStage.fromDg(80.0))
    }

    @Test
    fun `fromDg — boundary dg 22_5 is JEUNE_FUTAIE`() {
        assertEquals(DevelopmentStage.JEUNE_FUTAIE, DevelopmentStage.fromDg(22.5))
    }

    // ─── Tests DiameterCategoryRatio ──────────────────────────────────────────

    @Test
    fun `trianglePosition — PB dominant`() {
        val ratio = DiameterCategoryRatio(pbPct = 75.0, bmPct = 15.0, gbPct = 10.0)
        assertEquals(StructureTrianglePosition.PB, ratio.trianglePosition())
    }

    @Test
    fun `trianglePosition — BM dominant`() {
        val ratio = DiameterCategoryRatio(pbPct = 15.0, bmPct = 75.0, gbPct = 10.0)
        assertEquals(StructureTrianglePosition.BM, ratio.trianglePosition())
    }

    @Test
    fun `trianglePosition — GB dominant`() {
        val ratio = DiameterCategoryRatio(pbPct = 5.0, bmPct = 10.0, gbPct = 85.0)
        assertEquals(StructureTrianglePosition.GB, ratio.trianglePosition())
    }

    @Test
    fun `trianglePosition — PB_BM when both elevated`() {
        val ratio = DiameterCategoryRatio(pbPct = 45.0, bmPct = 45.0, gbPct = 10.0)
        assertEquals(StructureTrianglePosition.PB_BM, ratio.trianglePosition())
    }

    @Test
    fun `trianglePosition — BM_GB when both elevated`() {
        val ratio = DiameterCategoryRatio(pbPct = 10.0, bmPct = 45.0, gbPct = 45.0)
        assertEquals(StructureTrianglePosition.BM_GB, ratio.trianglePosition())
    }

    @Test
    fun `trianglePosition — EQUILIBRE for even distribution`() {
        val ratio = DiameterCategoryRatio(pbPct = 33.0, bmPct = 34.0, gbPct = 33.0)
        assertEquals(StructureTrianglePosition.EQUILIBRE, ratio.trianglePosition())
    }

    // ─── Tests hasEnoughForPartialResult ─────────────────────────────────────

    @Test
    fun `hasEnoughForPartialResult — true when nTotal large and gPerHa positive`() {
        assertTrue(StandClassificationEngine.hasEnoughForPartialResult(baseStats(nTotal = 10, gPerHa = 5.0)))
    }

    @Test
    fun `hasEnoughForPartialResult — false when too few trees`() {
        assertFalse(StandClassificationEngine.hasEnoughForPartialResult(baseStats(nTotal = 3, gPerHa = 1.0)))
    }

    @Test
    fun `hasEnoughForPartialResult — false when gPerHa is zero`() {
        assertFalse(StandClassificationEngine.hasEnoughForPartialResult(baseStats(nTotal = 10, gPerHa = 0.0)))
    }

    // ─── Tests shortLabel ─────────────────────────────────────────────────────

    @Test
    fun `shortLabel — returns non-blank string`() {
        val result = StandClassificationEngine.classify(baseStats())
        val label = StandClassificationEngine.shortLabel(result)
        assertTrue("shortLabel should not be blank", label.isNotBlank())
        assertTrue("shortLabel should contain treatment mode", label.isNotEmpty())
    }

    // ─── Tests requiredQuestions ──────────────────────────────────────────────

    @Test
    fun `requiredQuestions — always includes eco_zone`() {
        val questions = StandClassificationEngine.requiredQuestions(baseStats())
        assertTrue("eco_zone question should always be present",
            questions.any { it.id == "eco_zone" })
    }

    @Test
    fun `requiredQuestions — includes cepees for taillis-looking data`() {
        val stats = baseStats(nPerHa = 2500.0, dg = 12.0, cvDiam = 35.0)
        val questions = StandClassificationEngine.requiredQuestions(stats)
        assertTrue("has_cepees question expected for taillis profile",
            questions.any { it.id == "has_cepees" })
    }

    @Test
    fun `requiredQuestions — includes plantation for very low CV and 1 essence`() {
        val stats = baseStats(
            cvDiam = 8.0,
            dm = 20.0,
            perEssence = listOf(essenceStats("DOUGLAS_VERT", "Douglas", 100.0))
        )
        val questions = StandClassificationEngine.requiredQuestions(stats)
        assertTrue("is_plantation question expected for mono-essence low CV",
            questions.any { it.id == "is_plantation" })
    }

    // ─── Tests cohérence classify pour scénarios réels ───────────────────────

    @Test
    fun `scenario — chenaie adulte sessile`() {
        val stats = baseStats(
            nTotal = 150, nPerHa = 300.0, gPerHa = 25.0,
            dg = 40.0, cvDiam = 12.0, dm = 38.0, meanH = 22.0,
            perEssence = listOf(essenceStats("CH_SESSILE", "Chêne sessile", 90.0))
        )
        val result = StandClassificationEngine.classify(stats,
            userAnswers = mapOf("eco_zone" to 0, "is_plantation" to 0, "has_regeneration" to 0))
        assertEquals(DevelopmentStage.FUTAIE_ADULTE, result.developmentStage)
        assertEquals(StandComposition.PUR_FEUILLU, result.composition)
        assertEquals(AgeStructure.EQUIENNE, result.ageStructure)
    }

    @Test
    fun `scenario — plantation douglas jeune`() {
        val stats = baseStats(
            nTotal = 600, nPerHa = 1200.0, gPerHa = 15.0,
            dg = 14.0, cvDiam = 7.0, dm = 13.0, meanH = 10.0,
            perEssence = listOf(essenceStats("DOUGLAS_VERT", "Douglas", 100.0))
        )
        val result = StandClassificationEngine.classify(stats,
            userAnswers = mapOf("is_plantation" to 1, "eco_zone" to 0))
        assertEquals(StandComposition.PUR_RESINEUX, result.composition)
        assertEquals(StandOrigin.PLANTATION_MONO, result.origin)
    }

    @Test
    fun `scenario — taillis sous futaie chene`() {
        val stats = baseStats(
            nTotal = 800, nPerHa = 1600.0, gPerHa = 18.0,
            dg = 16.0, cvDiam = 42.0, dm = 14.0,
            perEssence = listOf(
                essenceStats("CH_SESSILE", "Chêne sessile", 55.0),
                essenceStats("CHARME", "Charme", 45.0)
            )
        )
        val result = StandClassificationEngine.classify(stats,
            userAnswers = mapOf("has_cepees" to 2, "has_reserves" to 1, "eco_zone" to 0))
        assertEquals(StandOrigin.REJETS_SOUCHE, result.origin)
    }

    @Test
    fun `scenario — futaie jardinee sapiniere irreguliere`() {
        val stats = baseStats(
            nTotal = 300, nPerHa = 300.0, gPerHa = 30.0,
            dg = 35.0, cvDiam = 60.0, dm = 30.0,
            classDistribution = listOf(
                ClassDistEntry(15, 100, 0.3, null),
                ClassDistEntry(30, 100, 1.5, null),
                ClassDistEntry(50, 100, 3.0, null)
            ),
            perEssence = listOf(essenceStats("SAPIN_PECTINE", "Sapin pectiné", 85.0))
        )
        val result = StandClassificationEngine.classify(stats,
            userAnswers = mapOf("eco_zone" to 1))
        assertEquals(EcologicalType.MONTAGNARDE, result.ecologicalType)
        assertTrue("High CV should give irregular structure",
            result.ageStructure in listOf(
                AgeStructure.IRREGULIERE_SIMPLE,
                AgeStructure.IRREGULIERE_COMPLEXE,
                AgeStructure.IRREGULIERE_EQUILIBREE,
                AgeStructure.IRREGULIERE_DESEQUILIBREE
            ))
    }
}
