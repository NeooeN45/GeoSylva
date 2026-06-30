package com.forestry.counter.domain.calculation

import com.forestry.counter.domain.calculation.tarifs.TarifCalculator
import com.forestry.counter.domain.calculation.tarifs.TarifMethod
import com.forestry.counter.domain.calculation.tarifs.TarifSelection
import com.forestry.counter.domain.calculation.tarifs.DecoupeCalculator
import com.forestry.counter.domain.calculation.quality.DefaultProductPrices
import com.forestry.counter.domain.calculation.quality.ProductClassifier
import com.forestry.counter.domain.calculation.quality.WoodQualityGrade
import com.forestry.counter.domain.calculation.pricing.ProPricingEngine
import com.forestry.counter.domain.calculation.pricing.SalePosition
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.parameters.ParameterKeys
import com.forestry.counter.domain.repository.ParameterRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt

data class ForestrySynthesisParams(
    val coefVolumes: List<CoefVolumeRange>,
    val heightDefaults: List<HeightDefaultRange>,
    val heightModes: List<HeightModeEntry>,
    val productRules: List<ProductRule>,
    val prices: List<PriceEntry>,
    val tarifSelection: TarifSelection? = null
)

class ForestryCalculator(
    private val parameterRepository: ParameterRepository
) {
    private val json = Json { ignoreUnknownKeys = true }

    // ─────────────────────────────────────────────────────
    // Calcul de volume unifié via le système de tarifs
    // ─────────────────────────────────────────────────────

    /**
     * Calcule le volume bois fort tige sur pied (m³) en utilisant le tarif
     * sélectionné. Si aucun tarif n'est configuré, utilise Algan par défaut
     * (le plus polyvalent pour les essences françaises).
     */
    private fun computeVolumeWithTarif(
        essenceCode: String,
        diamCm: Double,
        heightM: Double?,
        tarifSelection: TarifSelection?
    ): Double? {
        if (diamCm <= 0.0) return null

        val method = resolveTarifMethod(essenceCode, tarifSelection)
        val numero = resolveTarifNumero(method, essenceCode, tarifSelection)

        return TarifCalculator.computeVolume(
            method = method,
            essenceCode = essenceCode,
            diamCm = diamCm,
            hauteurM = heightM,
            tarifNumero = numero
        )
    }

    /**
     * Résout la méthode de tarif à utiliser pour une essence donnée,
     * en tenant compte des overrides par essence.
     */
    private fun resolveTarifMethod(essenceCode: String, tarifSelection: TarifSelection?): TarifMethod {
        if (tarifSelection == null) return TarifMethod.ALGAN

        // Vérifier override par essence
        val overrideCode = tarifSelection.essenceOverrides?.get(essenceCode.trim().uppercase())
        if (overrideCode != null) {
            TarifMethod.fromCode(overrideCode)?.let { return it }
        }

        return TarifMethod.fromCode(tarifSelection.method) ?: TarifMethod.ALGAN
    }

    /**
     * Résout le numéro de tarif (pour Schaeffer/IFN) pour une essence donnée.
     */
    private fun resolveTarifNumero(method: TarifMethod, essenceCode: String, tarifSelection: TarifSelection?): Int? {
        return when (method) {
            TarifMethod.SCHAEFFER_1E -> tarifSelection?.schaefferNumero
                ?: TarifCalculator.recommendedTarifNumero(method, essenceCode)
            TarifMethod.SCHAEFFER_2E -> tarifSelection?.schaefferNumero
                ?: TarifCalculator.recommendedTarifNumero(method, essenceCode)
            TarifMethod.IFN_RAPIDE -> tarifSelection?.ifnNumero
                ?: TarifCalculator.recommendedTarifNumero(method, essenceCode)
            TarifMethod.IFN_LENT -> tarifSelection?.ifnNumero
                ?: TarifCalculator.recommendedTarifNumero(method, essenceCode)
            else -> null
        }
    }

    private fun essenceCodeCandidates(code: String): List<String> {
        return EssenceAliases.candidates(code)
    }

    suspend fun loadSynthesisParams(): ForestrySynthesisParams {
        val all = parameterRepository.getAllParameters().first()
        val byKey = all.associateBy { it.key }

        fun decodeOrEmpty(key: String): String? {
            val str = byKey[key]?.valueJson
            return if (str.isNullOrBlank()) null else str
        }

        val coefVolumes = runCatching {
            decodeOrEmpty(ParameterKeys.COEFS_VOLUME)?.let { json.decodeFromString<List<CoefVolumeRange>>(it) } ?: emptyList()
        }.getOrElse { emptyList() }

        val heightDefaults = runCatching {
            decodeOrEmpty(ParameterKeys.HAUTEURS_DEFAUT)?.let { json.decodeFromString<List<HeightDefaultRange>>(it) } ?: emptyList()
        }.getOrElse { emptyList() }

        val heightModes = runCatching {
            decodeOrEmpty(ParameterKeys.HEIGHT_MODES)?.let { json.decodeFromString<List<HeightModeEntry>>(it) } ?: emptyList()
        }.getOrElse { emptyList() }

        val productRules = runCatching {
            decodeOrEmpty(ParameterKeys.RULES_PRODUITS)?.let { json.decodeFromString<List<ProductRule>>(it) } ?: emptyList()
        }.getOrElse { emptyList() }

        val prices = runCatching {
            decodeOrEmpty(ParameterKeys.PRIX_MARCHE)?.let { json.decodeFromString<List<PriceEntry>>(it) } ?: emptyList()
        }.getOrElse { emptyList() }

        val tarifSelection = runCatching {
            decodeOrEmpty(ParameterKeys.TARIF_SELECTION)?.let { json.decodeFromString<TarifSelection>(it) }
        }.getOrNull()

        return ForestrySynthesisParams(
            coefVolumes = coefVolumes,
            heightDefaults = heightDefaults,
            heightModes = heightModes,
            productRules = productRules,
            prices = prices,
            tarifSelection = tarifSelection
        )
    }

    /**
     * Charge la sélection de tarif actuelle depuis les paramètres.
     */
    suspend fun loadTarifSelection(): TarifSelection? {
        val item = parameterRepository.getParameter(ParameterKeys.TARIF_SELECTION).first() ?: return null
        return runCatching { json.decodeFromString<TarifSelection>(item.valueJson) }.getOrNull()
    }

    /**
     * Sauvegarde la sélection de tarif dans les paramètres.
     */
    suspend fun saveTarifSelection(selection: TarifSelection) {
        parameterRepository.setParameter(
            com.forestry.counter.domain.model.ParameterItem(
                key = ParameterKeys.TARIF_SELECTION,
                valueJson = json.encodeToString(selection)
            )
        )
    }

    /**
     * Retourne la méthode de tarif actuellement sélectionnée (ou ALGAN par défaut).
     */
    suspend fun currentTarifMethod(): TarifMethod {
        val sel = loadTarifSelection()
        return TarifMethod.fromCode(sel?.method ?: "") ?: TarifMethod.ALGAN
    }

    suspend fun diameterClasses(): List<Int> {
        val item = parameterRepository.getParameter(ParameterKeys.CLASSES_DIAM).first()
        return runCatching {
            item?.valueJson?.takeIf { it.isNotBlank() }?.let { json.decodeFromString<List<Int>>(it) } ?: emptyList()
        }.getOrElse { emptyList() }
    }

    suspend fun lookupF(essenceCode: String, diamCm: Double, method: String? = null): Double? {
        val d = diamCm.toInt()

        val item = parameterRepository.getParameter(ParameterKeys.COEFS_VOLUME).first()
        val list = runCatching { item?.valueJson?.let { json.decodeFromString<List<CoefVolumeRange>>(it) } }
            .getOrNull()

        if (list == null) {
            return fallbackF(diamCm, method)
        }

        val codes = essenceCodeCandidates(essenceCode)
        for (c in codes) {
            val candidates = list.filter { it.essence.trim().equals(c, true) && d >= it.min && d <= it.max }
            if (candidates.isNotEmpty()) {
                return if (method != null) {
                    val byMethod = candidates.firstOrNull { it.method?.equals(method, ignoreCase = true) == true }
                    val fallback = candidates.firstOrNull { it.method.isNullOrBlank() }
                    (byMethod ?: fallback)?.f ?: fallbackF(diamCm, method)
                } else {
                    val defaultRange = candidates.firstOrNull { it.method.isNullOrBlank() } ?: candidates.first()
                    defaultRange.f
                }
            }
        }

        val wildcardCandidates = list.filter { it.essence == "*" && d >= it.min && d <= it.max }
        if (wildcardCandidates.isNotEmpty()) {
            return if (method != null) {
                val byMethod = wildcardCandidates.firstOrNull { it.method?.equals(method, ignoreCase = true) == true }
                val fallback = wildcardCandidates.firstOrNull { it.method.isNullOrBlank() }
                (byMethod ?: fallback)?.f ?: fallbackF(diamCm, method)
            } else {
                val defaultRange = wildcardCandidates.firstOrNull { it.method.isNullOrBlank() } ?: wildcardCandidates.first()
                defaultRange.f
            }
        }

        return fallbackF(diamCm, method)
    }

    suspend fun lookupH(essenceCode: String, diamCm: Double): Double? {
        val item = parameterRepository.getParameter(ParameterKeys.HAUTEURS_DEFAUT).first() ?: return null
        val list = runCatching { json.decodeFromString<List<HeightDefaultRange>>(item.valueJson) }.getOrNull() ?: return null
        return lookupHFromRanges(list, essenceCode, diamCm)
    }

    private suspend fun heightModes(): List<HeightModeEntry> {
        val item = parameterRepository.getParameter(ParameterKeys.HEIGHT_MODES).first()
        val str = item?.valueJson ?: return emptyList()
        if (str.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<HeightModeEntry>>(str) }.getOrElse { emptyList() }
    }

    private suspend fun heightModeFor(essenceCode: String, diamClass: Int): HeightModeEntry? {
        val modes = heightModes()
        return modes.firstOrNull { it.essence.equals(essenceCode, true) && it.diamClass == diamClass }
    }

    suspend fun getHeightMode(essenceCode: String, diamClass: Int): HeightModeEntry? = heightModeFor(essenceCode, diamClass)

    suspend fun setHeightMode(entry: HeightModeEntry?) {
        val current = heightModes().toMutableList()
        // remove existing
        if (entry != null) {
            current.removeAll { it.essence.equals(entry.essence, true) && it.diamClass == entry.diamClass }
            // if DEFAULT with no fixed, we just remove any override
            if (entry.mode.equals("DEFAULT", true)) {
                // no-op after remove
            } else {
                current.add(entry)
            }
        }
        val jsonStr = json.encodeToString(current)
        // store back
        parameterRepository.setParameter(
            com.forestry.counter.domain.model.ParameterItem(
                key = ParameterKeys.HEIGHT_MODES,
                valueJson = jsonStr
            )
        )
    }

    fun computeG(diamCm: Double): Double {
        // G = pi * (DBH/200)^2 (m^2), DBH in cm
        val radiusM = diamCm / 200.0
        return PI * radiusM.pow(2)
    }

    /**
     * Coefficient de forme par défaut lorsque l’essence n’est pas dans TarifData.
     *
     * Valeurs issues de Pardé & Bouchon (1988) « Dendrométrie », ENGREF Nancy,
     * tableau 3.2 (coefficients de forme moyens par type de peuplement) :
     *  - "RAPIDE" (tarif 1 entrée rapide) → f ≈ 0.53 (peuplements réguliers denses)
     *  - "LENT"   (tarif 1 entrée lent)   → f ≈ 0.48 (peuplements irréguliers/clairs)
     *  - défaut                            → f = 0.50 (valeur centrale, médiane tous peuplements)
     *
     * ⚠ Erreur potentielle sur volume individuel : ±10–15 % selon l’essence réelle.
     * À n’utiliser que faute de données spécifiques à l’essence dans TarifData.
     */
    private fun fallbackF(diamCm: Double, method: String?): Double {
        return when (method?.uppercase()) {
            "RAPIDE" -> 0.53  // Pardé & Bouchon (1988), peuplements réguliers
            "LENT"   -> 0.48  // Pardé & Bouchon (1988), peuplements irréguliers
            else     -> 0.50  // Valeur centrale — incertitude ±10-15%
        }
    }

    suspend fun computeV(
        essenceCode: String,
        diamCm: Double,
        heightM: Double?,
        method: String? = null
    ): Double? {
        val tarifSel = loadTarifSelection()
        val resolvedMethod = resolveTarifMethod(essenceCode, tarifSel)
        val h = if (TarifCalculator.requiresHeight(resolvedMethod)) {
            heightM ?: lookupH(essenceCode, diamCm) ?: return null
        } else {
            heightM // may be null for 1-entry tariffs
        }
        return computeVolumeWithTarif(essenceCode, diamCm, h, tarifSel)
    }

    suspend fun volumeForTige(tige: Tige): Double? {
        return computeV(
            essenceCode = tige.essenceCode,
            diamCm = tige.diamCm,
            heightM = tige.hauteurM,
            method = null
        )
    }

    private fun diamToClass(diamCm: Double): Int = diamCm.roundToInt()

    private fun diamToClass(diamCm: Double, classes: List<Int>): Int {
        if (classes.isEmpty()) return diamToClass(diamCm)
        val sorted = classes.distinct().sorted()
        if (sorted.size == 1) return sorted.first()

        val d = diamCm
        for (i in 0 until sorted.size - 1) {
            val a = sorted[i].toDouble()
            val b = sorted[i + 1].toDouble()
            val boundary = (a + b) / 2.0
            if (d < boundary) return sorted[i]
        }
        return sorted.last()
    }

    fun diameterClassFor(diamCm: Double, classes: List<Int>): Int = diamToClass(diamCm, classes)

    private fun mean(values: List<Double>): Double? = values.takeIf { it.isNotEmpty() }?.average()

    private fun weightedMean(values: List<Double>, weights: List<Double>): Double? {
        if (values.isEmpty() || values.size != weights.size) return null
        val sumW = weights.sum()
        if (sumW == 0.0) return null
        return values.zip(weights).sumOf { it.first * it.second } / sumW
    }

    private fun sampleHeightMeanInClass(tiges: List<Tige>, essenceCode: String, diamClass: Int): Double? {
        val hs = tiges.asSequence()
            .filter { it.essenceCode.equals(essenceCode, true) && diamToClass(it.diamCm) == diamClass }
            .mapNotNull { it.hauteurM }
            .toList()
        return mean(hs)
    }

    suspend fun resolveHeightForClass(essenceCode: String, diamClass: Int, tiges: List<Tige>): Double? {
        val mode = heightModeFor(essenceCode, diamClass)
        return when (mode?.mode?.uppercase()) {
            "FIXED" -> mode.fixed
            "SAMPLES" -> sampleHeightMeanInClass(tiges, essenceCode, diamClass) ?: lookupH(essenceCode, diamClass.toDouble())
            else -> lookupH(essenceCode, diamClass.toDouble())
        }
    }

    private fun lookupHIn(heightDefaults: List<HeightDefaultRange>, essenceCode: String, diamCm: Double): Double? {
        return lookupHFromRanges(heightDefaults, essenceCode, diamCm)
    }

    private fun lookupHFromRanges(heightDefaults: List<HeightDefaultRange>, essenceCode: String, diamCm: Double): Double? {
        val d = diamCm.toInt()

        val specific = matchHeightRangesForEssence(heightDefaults, essenceCode)
        val exactSpecific = specific.firstOrNull { d >= it.min && d <= it.max }?.h
        if (exactSpecific != null) return exactSpecific

        val interpolatedSpecific = interpolateHeight(specific, diamCm)
        if (interpolatedSpecific != null) return interpolatedSpecific

        val wildcard = heightDefaults.filter { it.essence.trim() == "*" }
        val exactWildcard = wildcard.firstOrNull { d >= it.min && d <= it.max }?.h
        if (exactWildcard != null) return exactWildcard

        return interpolateHeight(wildcard, diamCm)
    }

    private fun matchHeightRangesForEssence(
        heightDefaults: List<HeightDefaultRange>,
        essenceCode: String
    ): List<HeightDefaultRange> {
        val codes = essenceCodeCandidates(essenceCode)
        for (c in codes) {
            val found = heightDefaults.filter { it.essence.trim().equals(c, true) }
            if (found.isNotEmpty()) return found
        }
        return emptyList()
    }

    private fun interpolateHeight(ranges: List<HeightDefaultRange>, diamCm: Double): Double? {
        if (ranges.isEmpty()) return null
        if (ranges.size == 1) return ranges.first().h

        val points = ranges
            .map { ((it.min + it.max) / 2.0) to it.h }
            .distinctBy { it.first }
            .sortedBy { it.first }

        if (points.isEmpty()) return null
        if (points.size == 1) return points.first().second

        val x = diamCm
        if (x <= points.first().first) return points.first().second
        if (x >= points.last().first) return points.last().second

        for (i in 0 until points.size - 1) {
            val (x1, y1) = points[i]
            val (x2, y2) = points[i + 1]
            if (x in x1..x2) {
                val span = x2 - x1
                if (span == 0.0) return y1
                val ratio = (x - x1) / span
                return y1 + (y2 - y1) * ratio
            }
        }
        return null
    }

    private fun lookupFIn(coefs: List<CoefVolumeRange>, essenceCode: String, diamCm: Double, method: String? = null): Double {
        if (coefs.isEmpty()) return fallbackF(diamCm, method)
        val d = diamCm.toInt()

        val codes = essenceCodeCandidates(essenceCode)
        for (c in codes) {
            val candidates = coefs.filter { it.essence.trim().equals(c, true) && d >= it.min && d <= it.max }
            if (candidates.isNotEmpty()) {
                return if (method != null) {
                    val byMethod = candidates.firstOrNull { it.method?.equals(method, ignoreCase = true) == true }
                    val fallback = candidates.firstOrNull { it.method.isNullOrBlank() }
                    (byMethod ?: fallback)?.f ?: fallbackF(diamCm, method)
                } else {
                    val defaultRange = candidates.firstOrNull { it.method.isNullOrBlank() } ?: candidates.first()
                    defaultRange.f
                }
            }
        }

        val wildcardCandidates = coefs.filter { it.essence == "*" && d >= it.min && d <= it.max }
        if (wildcardCandidates.isNotEmpty()) {
            return if (method != null) {
                val byMethod = wildcardCandidates.firstOrNull { it.method?.equals(method, ignoreCase = true) == true }
                val fallback = wildcardCandidates.firstOrNull { it.method.isNullOrBlank() }
                (byMethod ?: fallback)?.f ?: fallbackF(diamCm, method)
            } else {
                val defaultRange = wildcardCandidates.firstOrNull { it.method.isNullOrBlank() } ?: wildcardCandidates.first()
                defaultRange.f
            }
        }

        return fallbackF(diamCm, method)
    }

    private fun resolveHeightForClassIn(
        essenceCode: String,
        diamClass: Int,
        tigesByClass: Map<Int, List<Tige>>,
        heightModes: List<HeightModeEntry>,
        heightDefaults: List<HeightDefaultRange>
    ): Double? {
        val mode = heightModes.firstOrNull { it.essence.equals(essenceCode, true) && it.diamClass == diamClass }
        return when (mode?.mode?.uppercase()) {
            "FIXED" -> mode.fixed
            "SAMPLES" -> {
                val sampleMean = tigesByClass[diamClass].orEmpty().asSequence().mapNotNull { it.hauteurM }.toList().average().takeIf { !it.isNaN() }
                sampleMean ?: lookupHIn(heightDefaults, essenceCode, diamClass.toDouble())
            }
            else -> lookupHIn(heightDefaults, essenceCode, diamClass.toDouble())
        }
    }

    private fun computeVIn(
        essenceCode: String,
        diamCm: Double,
        heightM: Double?,
        method: String?,
        coefs: List<CoefVolumeRange>,
        heightDefaults: List<HeightDefaultRange>,
        tarifSelection: TarifSelection? = null
    ): Double? {
        val resolvedMethod = resolveTarifMethod(essenceCode, tarifSelection)
        val h = if (TarifCalculator.requiresHeight(resolvedMethod)) {
            heightM ?: lookupHIn(heightDefaults, essenceCode, diamCm) ?: return null
        } else {
            heightM
        }
        return computeVolumeWithTarif(essenceCode, diamCm, h, tarifSelection)
    }

    suspend fun synthesisForEssence(
        essenceCode: String,
        classes: List<Int>,
        tiges: List<Tige>,
        manualHeights: Map<Int, Double>? = null,
        method: String? = null,
        params: ForestrySynthesisParams? = null,
        requireHeights: Boolean = false
    ): Pair<List<ClassSynthesis>, SynthesisTotals> {
        val tarifSel = params?.tarifSelection ?: loadTarifSelection()
        val perClass = mutableListOf<ClassSynthesis>()
        val tigesEss = tiges.filter { it.essenceCode.equals(essenceCode, true) }
        val sortedClasses = classes.distinct().sorted()
        val tigesByClass = if (sortedClasses.isNotEmpty()) {
            tigesEss.groupBy { diamToClass(it.diamCm, sortedClasses) }
        } else {
            tigesEss.groupBy { diamToClass(it.diamCm) }
        }

        var vTotal = 0.0
        var nTotal = 0
        var diamSum = 0.0
        var diamCount = 0
        var hSum = 0.0
        var hCount = 0
        val rules = params?.productRules ?: productRules()
        val prices = params?.prices ?: priceEntries()
        val coefs = params?.coefVolumes
        val heightDefaults = params?.heightDefaults
        val heightModes = params?.heightModes
        val resolvedMethod = resolveTarifMethod(essenceCode, tarifSel)
        val needsHeight = TarifCalculator.requiresHeight(resolvedMethod)

        val allClasses = (sortedClasses + tigesByClass.keys).distinct().sorted()

        var volumeExpectedCount = 0
        var volumeComputedCount = 0

        for (d in allClasses) {
            val list = tigesByClass[d].orEmpty()
            val count = list.size
            nTotal += count

            val resolvedH = manualHeights?.get(d) ?: if (params != null && heightModes != null && heightDefaults != null) {
                if (requireHeights) {
                    val mode = heightModes.firstOrNull { it.essence.equals(essenceCode, true) && it.diamClass == d }
                    when (mode?.mode?.uppercase()) {
                        "FIXED" -> mode.fixed
                        "SAMPLES" -> list.asSequence().mapNotNull { it.hauteurM }.toList().average().takeIf { !it.isNaN() }
                        else -> lookupHIn(heightDefaults, essenceCode, d.toDouble())
                    }
                } else {
                    resolveHeightForClassIn(essenceCode, d, tigesByClass, heightModes, heightDefaults)
                }
            } else {
                val mode = heightModeFor(essenceCode, d)
                when (mode?.mode?.uppercase()) {
                    "FIXED" -> mode.fixed
                    "SAMPLES" -> {
                        val sampleMean = list.asSequence().mapNotNull { it.hauteurM }.toList().average().takeIf { !it.isNaN() }
                        if (sampleMean != null) sampleMean else if (requireHeights) null else lookupH(essenceCode, d.toDouble())
                    }
                    else -> if (requireHeights) null else lookupH(essenceCode, d.toDouble())
                }
            }

            val heights = list.mapNotNull { it.hauteurM } +
                if (resolvedH != null && list.any { it.hauteurM == null }) {
                    List(list.count { it.hauteurM == null }) { resolvedH }
                } else {
                    emptyList()
                }
            val hMean = mean(heights)

            val defaultProd = classifyProduct(essenceCode, d, rules)

            var vSum: Double? = null
            var valueSum: Double? = null
            if (count > 0) {
                var vs = 0.0
                var valSum = 0.0
                volumeExpectedCount += count

                list.forEach { t ->
                    val h = t.hauteurM ?: resolvedH
                    diamSum += t.diamCm
                    diamCount += 1
                    if (h != null) {
                        hSum += h
                        hCount += 1
                    }

                    val v = when {
                        requireHeights && needsHeight && h == null -> null
                        else -> {
                            val effectiveH = if (needsHeight) h else null
                            if (params != null && heightDefaults != null) {
                                computeVIn(essenceCode, t.diamCm, effectiveH ?: h, method, coefs ?: emptyList(), heightDefaults, tarifSel)
                            } else {
                                computeVolumeWithTarif(essenceCode, t.diamCm, effectiveH ?: h, tarifSel)
                            }
                        }
                    }

                    if (v != null) {
                        vs += v
                        volumeComputedCount += 1

                        val quality = t.qualite
                        val ruleProduct = classifyProduct(
                            essence = essenceCode,
                            diamClass = d,
                            rules = rules,
                            quality = quality,
                            defects = t.defauts
                        )
                        val treeProduct = t.produit?.trim()?.takeIf { it.isNotEmpty() } ?: ruleProduct

                        val qualityCode = quality?.let { q -> WoodQualityGrade.entries.getOrNull(q)?.code }

                        // C-PRIX-2/3: Ventiler le volume par produit avant application des prix.
                        // Au lieu d'appliquer un seul prix au volume total, on ventile en BO/BI/BCh/PATE
                        // et on applique le prix spécifique à chaque fraction.
                        val volumeParProduit = DecoupeCalculator.ventilerParProduit(
                            volumeTotal = v,
                            essenceCode = essenceCode,
                            categorie = null,
                            diamCm = t.diamCm
                        )

                        for ((produit, volumeFraction) in volumeParProduit) {
                            if (volumeFraction <= 0.0) continue
                            val prixProduit = priceFor(essenceCode, produit, d, prices, qualityCode)
                                ?: if (!produit.equals(defaultProd, ignoreCase = true)) {
                                    priceFor(essenceCode, defaultProd, d, prices, qualityCode)
                                } else {
                                    null
                                }
                            val prixFinal = prixProduit ?: run {
                                val grade = quality?.let { q ->
                                    WoodQualityGrade.entries.getOrNull(q)
                                } ?: WoodQualityGrade.C
                                DefaultProductPrices.priceFor(produit, essenceCode, grade)
                            }
                            valSum += volumeFraction * prixFinal
                        }
                    }
                }

                if (vs > 0.0) {
                    vSum = vs
                    vTotal += vs
                    valueSum = valSum
                }
            }
            perClass += ClassSynthesis(diamClass = d, count = count, hMean = hMean, vSum = vSum, valueSumEur = valueSum)
        }

        val dm = if (diamCount > 0) diamSum / diamCount else null
        val hm = if (hCount > 0) hSum / hCount else null
        val completenessPct = if (volumeExpectedCount > 0) {
            (volumeComputedCount.toDouble() / volumeExpectedCount.toDouble()) * 100.0
        } else {
            100.0
        }
        val totals = SynthesisTotals(
            nTotal = nTotal,
            dmWeighted = dm,
            hMean = hm,
            vTotal = if (vTotal > 0.0) vTotal else null,
            volumeCompletenessPct = completenessPct.coerceIn(0.0, 100.0),
            volumeComputedCount = volumeComputedCount,
            volumeExpectedCount = volumeExpectedCount
        )
        return perClass to totals
    }

    private suspend fun productRules(): List<ProductRule> {
        val item = parameterRepository.getParameter(ParameterKeys.RULES_PRODUITS).first()
        val str = item?.valueJson ?: return emptyList()
        if (str.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<ProductRule>>(str) }.getOrElse { emptyList() }
    }

    suspend fun loadPriceEntries(): List<PriceEntry> = priceEntries()

    private suspend fun priceEntries(): List<PriceEntry> {
        val item = parameterRepository.getParameter(ParameterKeys.PRIX_MARCHE).first()
        val str = item?.valueJson ?: return emptyList()
        if (str.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<PriceEntry>>(str) }.getOrElse { emptyList() }
    }

    private fun classifyProduct(
        essence: String,
        diamClass: Int,
        rules: List<ProductRule>,
        quality: Int? = null,
        defects: List<String>? = null
    ): String {
        // First matching rule wins; fallback basic thresholds if none
        val codes = essenceCodeCandidates(essence)
        val defectsNorm = defects.orEmpty().map { it.trim().uppercase() }
        rules.forEach { r ->
            val rEss = r.essence?.trim()
            val essMatch = rEss == null || rEss == "*" || codes.any { c -> rEss.equals(c, true) }
            val minOk = r.min?.let { diamClass >= it } ?: true
            val maxOk = r.max?.let { diamClass <= it } ?: true
            val minQualityOk = r.minQuality?.let { qMin -> quality != null && quality >= qMin } ?: true
            val maxQualityOk = r.maxQuality?.let { qMax -> quality != null && quality <= qMax } ?: true
            val requiresDefectOk = r.requiresDefect?.trim()?.takeIf { it.isNotEmpty() }?.let { req ->
                defectsNorm.contains(req.uppercase())
            } ?: true
            val excludesDefectOk = r.excludesDefect?.trim()?.takeIf { it.isNotEmpty() }?.let { forbidden ->
                !defectsNorm.contains(forbidden.uppercase())
            } ?: true

            if (essMatch && minOk && maxOk && minQualityOk && maxQualityOk && requiresDefectOk && excludesDefectOk) {
                return r.product.trim()
            }
        }

        // fallback heuristic with practical quality/defect downgrades
        if (quality != null) {
            when {
                quality >= 3 -> return "PATE"
                quality >= 2 && diamClass >= 20 -> return "BCh"
            }
        }
        if (defectsNorm.isNotEmpty() && diamClass >= 20) {
            return "BCh"
        }

        return when {
            diamClass >= 35 -> "BO"
            diamClass >= 20 -> "BI"
            diamClass >= 7 -> "BCh"
            else -> "PATE"
        }
    }

    private fun priceFor(
        essence: String,
        product: String,
        diamClass: Int,
        prices: List<PriceEntry>,
        qualityCode: String? = null,
        region: com.forestry.counter.domain.calculation.pricing.GrecoRegion? = null
    ): Double? {
        // C-PRIX-PRO : Utilisation du moteur de calcul professionnel (8 coefficients).
        // Le moteur cherche le prix de référence dans PriceEntry (en résolvant les alias d'essence),
        // applique le coefficient qualité (NF EN 1316/1927), puis les autres coefficients.
        // Retourne null si aucun prix PriceEntry trouvé (pour préserver le fallback ForestryCalculator).
        // `region` (GRECO) est transmis au moteur pour appliquer le coefficient régional ;
        // tant que l'UI ne fournit pas la région détectée, il vaut null (×1.0).
        val candidates = essenceCodeCandidates(essence)
        return ProPricingEngine.calculateFromEntryOnly(
            essenceCode = essence,
            product = product,
            diamCm = diamClass,
            qualityGrade = qualityCode,
            prices = prices,
            position = SalePosition.SUR_PIED,
            essenceCandidates = candidates,
            region = region
        )
    }
}
