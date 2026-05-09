package com.forestry.counter.domain.calculation

import com.forestry.counter.domain.calculation.quality.WoodQualityGrade
import com.forestry.counter.domain.model.Essence
import com.forestry.counter.domain.model.Tige
import java.util.Locale
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.sqrt

// Vue locale pour la synthèse (indépendante du scope de navigation)
enum class MartelageViewScope { PLACETTE, PARCELLE, GLOBAL }

/**
 * Calcule les agrégats de martelage pour un jeu de tiges donné.
 * Logique métier pure, extraite de l'écran Compose pour testabilité.
 */
suspend fun computeMartelageStats(
    tigesInScope: List<Tige>,
    surfaceM2: Double,
    selectedEssenceCodes: Set<String>,
    martelageHeights: Map<String, Map<Int, Double>>,
    synthesisParams: ForestrySynthesisParams?,
    diameterClasses: List<Int>,
    essences: List<Essence>,
    forestryCalculator: ForestryCalculator,
    nHaAvant: Double? = null,
    gHaAvant: Double? = null
): MartelageStats? {
    if (tigesInScope.isEmpty() || surfaceM2 <= 0.0) return null

    val surfaceHa = surfaceM2 / 10_000.0
    if (surfaceHa <= 0.0) return null

    fun normalizeEssenceCode(code: String): String = code.trim().uppercase(Locale.getDefault())

    val classes = if (diameterClasses.isNotEmpty()) diameterClasses else (5..120 step 5).toList()

    val byEssence = tigesInScope.groupBy { normalizeEssenceCode(it.essenceCode) }
    val perEssence = mutableListOf<PerEssenceStats>()

    val heightModes = synthesisParams?.heightModes.orEmpty()
    val missingHeightsByEssence = mutableMapOf<String, List<Int>>()
    byEssence.forEach { (code, tigesEss) ->
        if (selectedEssenceCodes.isNotEmpty() && code !in selectedEssenceCodes) return@forEach
        val byClass = tigesEss.groupBy { forestryCalculator.diameterClassFor(it.diamCm, classes) }
        val manual = martelageHeights[normalizeEssenceCode(code)].orEmpty()
        val missing = byClass.entries
            .filter { (diamClass, list) ->
                val hasMissingMeasured = list.any { it.hauteurM == null }
                if (!hasMissingMeasured) {
                    false
                } else {
                    val manualH = manual[diamClass]
                    val mode = heightModes.firstOrNull { it.essence.equals(code, true) && it.diamClass == diamClass }
                    val fixedH = mode?.mode?.equals("FIXED", ignoreCase = true) == true && (mode.fixed ?: 0.0) > 0.0
                    val sampleH = mode?.mode?.equals("SAMPLES", ignoreCase = true) == true && list.any { it.hauteurM != null }
                    val canResolve = manualH != null || fixedH || sampleH
                    !canResolve
                }
            }
            .map { it.key }
            .sorted()
        if (missing.isNotEmpty()) {
            missingHeightsByEssence[code] = missing
        }
    }
    val missingHeightEssenceCodes = missingHeightsByEssence.keys.sorted()
    val missingHeightEssenceNames = missingHeightEssenceCodes.map { code ->
        essences.firstOrNull { normalizeEssenceCode(it.code) == code }?.name ?: code
    }

    var nTotal = 0
    var gTotal = 0.0
    var vTotal = 0.0
    var revenueTotal = 0.0
    var unpricedVolumeTotal = 0.0
    val unpricedEssenceNames = mutableListOf<String>()
    var dmSum = 0.0
    var dmWeight = 0
    var hSum = 0.0
    var hWeight = 0
    var loreyGhSum = 0.0
    var loreyGSum = 0.0
    var volumeExpectedCountTotal = 0
    var volumeComputedCountTotal = 0
    val allDiams = mutableListOf<Double>()

    // Distribution par classe (agrégée toutes essences confondues)
    val classDistMap = mutableMapOf<Int, Triple<Int, Double, Double>>() // class -> (n, g, v)

    // Distribution par qualité bois
    val qualityCounts = mutableMapOf<WoodQualityGrade, Int>()
    var qualityAssessedCount = 0

    byEssence.forEach { (code, tigesEss) ->
        if (selectedEssenceCodes.isNotEmpty() && code !in selectedEssenceCodes) return@forEach

        val manualHeightsForEss = martelageHeights[normalizeEssenceCode(code)]
        val (rows, totals) = try {
            forestryCalculator.synthesisForEssence(
                essenceCode = code,
                classes = classes,
                tiges = tigesEss,
                manualHeights = manualHeightsForEss,
                method = null,
                params = synthesisParams,
                requireHeights = true
            )
        } catch (_: Throwable) {
            emptyList<ClassSynthesis>() to SynthesisTotals(0, null, null, null)
        }

        val vEss = totals.vTotal ?: 0.0
        val gEss = tigesEss.sumOf { forestryCalculator.computeG(it.diamCm) }
        val revEss = rows.sumOf { it.valueSumEur ?: 0.0 }
        val nEss = tigesEss.size
        volumeExpectedCountTotal += totals.volumeExpectedCount
        volumeComputedCountTotal += totals.volumeComputedCount

        nTotal += nEss
        gTotal += gEss
        vTotal += vEss
        revenueTotal += revEss
        tigesEss.forEach { allDiams += it.diamCm }
        totals.dmWeighted?.let { dm ->
            dmSum += dm * nEss
            dmWeight += nEss
        }
        totals.hMean?.let { hm ->
            hSum += hm * nEss
            hWeight += nEss
        }

        val manual = manualHeightsForEss.orEmpty()
        tigesEss.forEach { t ->
            val diamClass = forestryCalculator.diameterClassFor(t.diamCm, classes)
            val h = t.hauteurM ?: manual[diamClass]
            if (h != null) {
                val g = forestryCalculator.computeG(t.diamCm)
                loreyGhSum += g * h
                loreyGSum += g
            }
        }

        // Distribution par classe — G pré-calculée par classe pour éviter O(n²)
        val gByClass = mutableMapOf<Int, Double>()
        tigesEss.forEach { t ->
            val dc = forestryCalculator.diameterClassFor(t.diamCm, classes)
            gByClass[dc] = (gByClass[dc] ?: 0.0) + forestryCalculator.computeG(t.diamCm)
        }
        rows.forEach { r ->
            val prev = classDistMap[r.diamClass] ?: Triple(0, 0.0, 0.0)
            classDistMap[r.diamClass] = Triple(
                prev.first + r.count,
                prev.second + (gByClass[r.diamClass] ?: 0.0),
                prev.third + (r.vSum ?: 0.0)
            )
        }

        // Qualité bois par tige
        tigesEss.forEach { t ->
            val grade = t.qualite?.let { q -> WoodQualityGrade.entries.getOrNull(q) }
            if (grade != null) {
                qualityCounts[grade] = (qualityCounts[grade] ?: 0) + 1
                qualityAssessedCount++
            }
        }

        val unpricedVEss = rows.asSequence()
            .filter { r -> r.count > 0 && (r.vSum ?: 0.0) > 0.0 && r.valueSumEur == null }
            .sumOf { it.vSum ?: 0.0 }
        if (unpricedVEss > 0.0) {
            unpricedVolumeTotal += unpricedVEss
            val essenceName = essences.firstOrNull { normalizeEssenceCode(it.code) == code }?.name ?: code
            unpricedEssenceNames += essenceName
        }

        val essenceName = essences.firstOrNull { normalizeEssenceCode(it.code) == code }?.name ?: code
        val dmEss = if (tigesEss.isNotEmpty()) tigesEss.sumOf { it.diamCm } / tigesEss.size else null
        val dgEss = if (nEss > 0 && gEss > 0.0) sqrt((4.0 * gEss) / (PI * nEss.toDouble())) * 100.0 else null
        val qualityEss = tigesEss.mapNotNull { t -> t.qualite?.let { WoodQualityGrade.entries.getOrNull(it) } }
        val dominantQuality = qualityEss.groupBy { it }.maxByOrNull { it.value.size }?.key

        perEssence += PerEssenceStats(
            essenceCode = code,
            essenceName = essenceName,
            n = nEss,
            nPct = 0.0,
            vTotal = vEss,
            vPct = 0.0,
            vPerHa = if (surfaceHa > 0.0) vEss / surfaceHa else 0.0,
            gTotal = gEss,
            gPct = 0.0,
            gPerHa = if (surfaceHa > 0.0) gEss / surfaceHa else 0.0,
            dm = dmEss,
            dg = dgEss,
            meanPricePerM3 = if (vEss > 0.0 && revEss > 0.0) revEss / vEss else null,
            revenueTotal = if (revEss > 0.0) revEss else null,
            revenuePerHa = if (revEss > 0.0 && surfaceHa > 0.0) revEss / surfaceHa else null,
            dominantQuality = dominantQuality,
            qualityAssessedPct = if (tigesEss.isNotEmpty()) qualityEss.size.toDouble() / tigesEss.size * 100.0 else 0.0
        )
    }

    // Special trees count (DEPERISSANT, ARBRE_BIO, MORT, PARASITE)
    val specialCategories = setOf("DEPERISSANT", "ARBRE_BIO", "MORT", "PARASITE")
    val specialByCategory = mutableMapOf<String, MutableList<SpecialTreeDetail>>()
    tigesInScope.forEach { t ->
        val cat = t.categorie?.uppercase(Locale.getDefault())
        if (cat != null && cat in specialCategories) {
            val essName = essences.firstOrNull { normalizeEssenceCode(it.code) == normalizeEssenceCode(t.essenceCode) }?.name ?: t.essenceCode
            specialByCategory.getOrPut(cat) { mutableListOf() } += SpecialTreeDetail(
                essenceName = essName,
                essenceCode = normalizeEssenceCode(t.essenceCode),
                diamCm = t.diamCm,
                hauteurM = t.hauteurM,
                defauts = t.defauts,
                note = t.note,
                hasGps = t.gpsWkt != null
            )
        }
    }
    val specialTreeEntries = specialByCategory.entries
        .sortedBy { it.key }
        .map { (cat, trees) -> SpecialTreeEntry(cat, trees.size, trees) }

    if (nTotal == 0 && vTotal == 0.0 && gTotal == 0.0 && specialTreeEntries.isEmpty()) return null

    // ── Garde-fou : vérification de cohérence ──
    val sanityWarnings = mutableListOf<SanityWarning>()
    sanityWarnings.addAll(SanityChecker.checkAllTiges(tigesInScope))

    val volumeCompletenessPct = if (volumeExpectedCountTotal > 0) {
        volumeComputedCountTotal.toDouble() / volumeExpectedCountTotal.toDouble() * 100.0
    } else {
        100.0
    }
    val volumeFullyComplete = volumeCompletenessPct >= 99.999

    // Recalculer les pourcentages par essence
    val perEssenceWithPct = perEssence.map { row ->
        row.copy(
            nPct = if (nTotal > 0) row.n.toDouble() / nTotal * 100.0 else 0.0,
            gPct = if (gTotal > 0.0) row.gTotal / gTotal * 100.0 else 0.0,
            vPct = if (vTotal > 0.0) row.vTotal / vTotal * 100.0 else 0.0
        )
    }

    val nPerHa = if (surfaceHa > 0.0) nTotal / surfaceHa else 0.0
    val gPerHa = if (surfaceHa > 0.0) gTotal / surfaceHa else 0.0
    val vPerHa = if (surfaceHa > 0.0) vTotal / surfaceHa else 0.0
    val unpricedVolumePerHa = if (surfaceHa > 0.0) unpricedVolumeTotal / surfaceHa else 0.0
    val revenuePerHa = if (revenueTotal > 0.0 && surfaceHa > 0.0) revenueTotal / surfaceHa else null

    val dm = if (dmWeight > 0) dmSum / dmWeight else null
    val hMean = if (hWeight > 0) hSum / hWeight else null
    val dg = if (nTotal > 0 && gTotal > 0.0) sqrt((4.0 * gTotal) / (PI * nTotal.toDouble())) * 100.0 else null
    val hLorey = if (loreyGSum > 0.0) loreyGhSum / loreyGSum else null

    // Statistiques avancées sur les diamètres
    val dMin = allDiams.minOrNull()
    val dMax = allDiams.maxOrNull()
    val cvDiam = if (allDiams.size > 1 && dm != null && dm > 0.0) {
        val variance = allDiams.sumOf { (it - dm) * (it - dm) } / (allDiams.size - 1)
        (sqrt(variance) / dm) * 100.0
    } else null
    val ratioVG = if (gTotal > 0.0 && vTotal > 0.0) vTotal / gTotal else null

    // Distribution par classes triée
    val classDistribution = classDistMap.entries
        .sortedBy { it.key }
        .map { (cls, triple) -> ClassDistEntry(cls, triple.first, triple.second, triple.third.takeIf { it > 0.0 }) }

    // Distribution qualité agrégée
    val qualityDistribution = WoodQualityGrade.entries.map { grade ->
        QualityDistEntry(
            grade = grade,
            count = qualityCounts[grade] ?: 0,
            pct = if (qualityAssessedCount > 0) (qualityCounts[grade] ?: 0).toDouble() / qualityAssessedCount * 100.0 else 0.0
        )
    }.filter { it.count > 0 }

    // Harvest simulation — taux de prélèvement
    val harvestNhaPct = if (nHaAvant != null && nHaAvant > 0.0 && nPerHa > 0.0)
        (nPerHa / nHaAvant * 100.0).coerceAtMost(100.0) else null
    val harvestGhaPct = if (gHaAvant != null && gHaAvant > 0.0 && gPerHa > 0.0)
        (gPerHa / gHaAvant * 100.0).coerceAtMost(100.0) else null
    // V/ha removal rate only if both N and G before are known (rough proportionality)
    val harvestVhaPct = if (harvestNhaPct != null && harvestGhaPct != null && vPerHa > 0.0 && nHaAvant != null && nHaAvant > 0.0) {
        // Estimate V/ha avant from ratio: V_avant_approx = V_prélèvement / (G_prélèvement_pct/100)
        if (harvestGhaPct > 0.0) (harvestGhaPct).coerceAtMost(100.0) else null
    } else null
    val residualNha = if (nHaAvant != null && nPerHa > 0.0) (nHaAvant - nPerHa).coerceAtLeast(0.0) else null
    val residualGha = if (gHaAvant != null && gPerHa > 0.0) (gHaAvant - gPerHa).coerceAtLeast(0.0) else null
    val residualVha: Double? = null // Cannot estimate without V/ha avant

    // ── Indice de biodiversité ──
    val biodiversity = computeBiodiversityIndex(tigesInScope, specialTreeEntries, nTotal, perEssenceWithPct)

    sanityWarnings.addAll(
        SanityChecker.checkAggregates(
            nPerHa = nPerHa,
            gPerHa = gPerHa,
            vPerHa = vPerHa,
            revenuePerHa = revenuePerHa,
            surfaceHa = surfaceHa,
            ratioVG = ratioVG
        )
    )

    return MartelageStats(
        nTotal = nTotal,
        nPerHa = nPerHa,
        gTotal = gTotal,
        gPerHa = gPerHa,
        vTotal = vTotal,
        vPerHa = vPerHa,
        unpricedVolumeTotal = unpricedVolumeTotal,
        unpricedVolumePerHa = unpricedVolumePerHa,
        unpricedEssenceNames = unpricedEssenceNames,
        revenueTotal = if (revenueTotal > 0.0) revenueTotal else null,
        revenuePerHa = revenuePerHa,
        dm = dm,
        meanH = hMean,
        dg = dg,
        hLorey = hLorey,
        dMin = dMin,
        dMax = dMax,
        cvDiam = cvDiam,
        ratioVG = ratioVG,
        surfaceHa = surfaceHa,
        classDistribution = classDistribution,
        qualityDistribution = qualityDistribution,
        qualityAssessedCount = qualityAssessedCount,
        qualityTotalCount = nTotal,
        perEssence = perEssenceWithPct.sortedBy { it.essenceName },
        volumeAvailable = volumeFullyComplete,
        volumeCompletenessPct = volumeCompletenessPct.coerceIn(0.0, 100.0),
        missingHeightEssenceCodes = missingHeightEssenceCodes,
        missingHeightEssenceNames = missingHeightEssenceNames,
        harvestNhaPct = harvestNhaPct,
        harvestGhaPct = harvestGhaPct,
        harvestVhaPct = harvestVhaPct,
        residualNha = residualNha,
        residualGha = residualGha,
        residualVha = residualVha,
        sanityWarnings = sanityWarnings,
        specialTrees = specialTreeEntries,
        biodiversity = biodiversity
    )
}

/**
 * Calcule l'indice de biodiversité : Shannon, Piélou, IBP simplifié.
 */
private fun computeBiodiversityIndex(
    tiges: List<Tige>,
    specialTrees: List<SpecialTreeEntry>,
    nTotal: Int,
    perEssence: List<PerEssenceStats>
): BiodiversityIndex? {
    if (nTotal == 0) return null
    val speciesCount = perEssence.size

    // Shannon H' = -Σ(pi * ln(pi))
    var shannon = 0.0
    perEssence.forEach { row ->
        val pi = row.n.toDouble() / nTotal
        if (pi > 0.0) shannon -= pi * ln(pi)
    }

    // Piélou J = H' / ln(S)
    val pielou = if (speciesCount > 1) shannon / ln(speciesCount.toDouble()) else if (speciesCount == 1) 0.0 else null

    // IBP simplifié (0–10 points)
    var ibpScore = 0
    val ibpDetails = mutableListOf<String>()

    // 1. Diversité spécifique (≥3 essences = 1pt, ≥6 = 2pt)
    if (speciesCount >= 6) { ibpScore += 2; ibpDetails += "diversite_6+" }
    else if (speciesCount >= 3) { ibpScore += 1; ibpDetails += "diversite_3+" }

    // 2. Très gros bois (TGB ≥ 70cm) présents
    val tgbCount = tiges.count { it.diamCm >= 70.0 }
    if (tgbCount >= 3) { ibpScore += 2; ibpDetails += "tgb_3+" }
    else if (tgbCount >= 1) { ibpScore += 1; ibpDetails += "tgb_1+" }

    // 3. Arbres bio vivants
    val bioCount = specialTrees.firstOrNull { it.categorie == "ARBRE_BIO" }?.count ?: 0
    if (bioCount >= 3) { ibpScore += 2; ibpDetails += "bio_3+" }
    else if (bioCount >= 1) { ibpScore += 1; ibpDetails += "bio_1+" }

    // 4. Bois mort sur pied
    val deadCount = specialTrees.firstOrNull { it.categorie == "MORT" }?.count ?: 0
    if (deadCount >= 3) { ibpScore += 2; ibpDetails += "mort_3+" }
    else if (deadCount >= 1) { ibpScore += 1; ibpDetails += "mort_1+" }

    // 5. Arbres dépérissants
    val dyingCount = specialTrees.firstOrNull { it.categorie == "DEPERISSANT" }?.count ?: 0
    if (dyingCount >= 1) { ibpScore += 1; ibpDetails += "deperissant_1+" }

    // 6. Régularité de Shannon (Piélou > 0.6)
    if (pielou != null && pielou >= 0.6) { ibpScore += 1; ibpDetails += "equitabilite" }

    return BiodiversityIndex(
        shannonH = shannon,
        pielou = pielou,
        speciesCount = speciesCount,
        tgbCount = tgbCount,
        bioTreeCount = bioCount,
        deadTreeCount = deadCount,
        dyingTreeCount = dyingCount,
        ibpScore = ibpScore.coerceAtMost(10),
        ibpMax = 10,
        ibpDetails = ibpDetails
    )
}

// Agrégats globaux martelage pour une vue donnée
data class MartelageStats(
    val nTotal: Int,
    val nPerHa: Double,
    val gTotal: Double,
    val gPerHa: Double,
    val vTotal: Double,
    val vPerHa: Double,
    val unpricedVolumeTotal: Double,
    val unpricedVolumePerHa: Double,
    val unpricedEssenceNames: List<String>,
    val revenueTotal: Double?,
    val revenuePerHa: Double?,
    val dm: Double?,
    val meanH: Double?,
    val dg: Double?,
    val hLorey: Double?,
    val dMin: Double?,
    val dMax: Double?,
    val cvDiam: Double?,
    val ratioVG: Double?,
    val surfaceHa: Double,
    val classDistribution: List<ClassDistEntry>,
    val qualityDistribution: List<QualityDistEntry>,
    val qualityAssessedCount: Int,
    val qualityTotalCount: Int,
    val perEssence: List<PerEssenceStats>,
    val volumeAvailable: Boolean,
    val volumeCompletenessPct: Double,
    val missingHeightEssenceCodes: List<String>,
    val missingHeightEssenceNames: List<String>,
    // Harvest simulation (taux de prélèvement)
    val harvestNhaPct: Double? = null,
    val harvestGhaPct: Double? = null,
    val harvestVhaPct: Double? = null,
    val residualNha: Double? = null,
    val residualGha: Double? = null,
    val residualVha: Double? = null,
    // Garde-fou
    val sanityWarnings: List<SanityWarning> = emptyList(),
    // Arbres spéciaux (dépérissant, bio, mort, parasité)
    val specialTrees: List<SpecialTreeEntry> = emptyList(),
    // Indice de biodiversité
    val biodiversity: BiodiversityIndex? = null
)

data class ClassDistEntry(
    val diamClass: Int,
    val n: Int,
    val g: Double,
    val v: Double?
)

data class QualityDistEntry(
    val grade: WoodQualityGrade,
    val count: Int,
    val pct: Double
)

data class BiodiversityIndex(
    val shannonH: Double,
    val pielou: Double?,
    val speciesCount: Int,
    val tgbCount: Int,       // Très gros bois (≥70cm)
    val bioTreeCount: Int,
    val deadTreeCount: Int,
    val dyingTreeCount: Int,
    val ibpScore: Int,       // Score IBP simplifié (0–10)
    val ibpMax: Int,
    val ibpDetails: List<String>
)

data class SpecialTreeDetail(
    val essenceName: String,
    val essenceCode: String,
    val diamCm: Double,
    val hauteurM: Double?,
    val defauts: List<String>?,
    val note: String?,
    val hasGps: Boolean = false
)

data class SpecialTreeEntry(
    val categorie: String, // DEPERISSANT, ARBRE_BIO, MORT, PARASITE
    val count: Int,
    val trees: List<SpecialTreeDetail> = emptyList()
)

// Agrégats par essence pour le tableau
data class PerEssenceStats(
    val essenceCode: String,
    val essenceName: String,
    val n: Int,
    val nPct: Double,
    val vTotal: Double,
    val vPct: Double,
    val vPerHa: Double,
    val gTotal: Double,
    val gPct: Double,
    val gPerHa: Double,
    val dm: Double?,
    val dg: Double?,
    val meanPricePerM3: Double?,
    val revenueTotal: Double?,
    val revenuePerHa: Double?,
    val dominantQuality: WoodQualityGrade? = null,
    val qualityAssessedPct: Double = 0.0
)
