package com.forestry.counter.presentation.screens.forestry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forestry.counter.domain.model.Essence
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.repository.EssenceRepository
import com.forestry.counter.domain.repository.PlacetteRepository
import com.forestry.counter.domain.repository.TigeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * Données agrégées pour une année donnée d'une placette.
 */
data class YearEvolutionStats(
    val year: Int,
    val tiges: List<Tige>,
    val stemCount: Int,
    val essenceCount: Int,
    val meanDiameterCm: Double,        // Dm
    val quadraticMeanDiameterCm: Double, // Dg
    val basalAreaM2: Double,           // G total
    val basalAreaPerHaM2: Double?,     // G/ha
    val stemsPerHa: Double?,           // N/ha
    val meanHeightM: Double?,          // Hm
    val loreyHeightM: Double?,         // Hg
    val volumeM3: Double?,             // V total (si hauteurs)
    val volumePerHaM3: Double?,        // V/ha
    val biomassTonnes: Double?,        // biomasse tiges
    val carbonTonnes: Double?,         // carbone tiges
    val habitatTreesCount: Int,        // tiges habitat
    val byEssence: List<EssenceYearStats>,
    val byCategory: List<CategoryYearStats>,
    val diameterDistribution: List<DiameterClassEntry>
)

data class EssenceYearStats(
    val essenceCode: String,
    val essenceName: String,
    val colorHex: String?,
    val stemCount: Int,
    val meanDiameterCm: Double,
    val basalAreaM2: Double,
    val volumeM3: Double?,
    val percentOfStems: Double,        // % du nombre total
    val percentOfBasalArea: Double     // % de G
)

data class CategoryYearStats(
    val category: String,
    val label: String,
    val color: Long,
    val stemCount: Int,
    val basalAreaM2: Double,
    val volumeM3: Double?
)

data class DiameterClassEntry(
    val diamClass: Int,   // borne inférieure (ex: 15 pour classe 15-20)
    val stemCount: Int,
    val basalAreaM2: Double
)

data class YearSummary(
    val year: Int,
    val stemCount: Int,
    val essenceCount: Int,
    val meanDiameterCm: Double,
    val basalAreaM2: Double,
    val basalAreaPerHaM2: Double?
)

/**
 * ViewModel pour la page Évolution détaillée d'une placette pour une année.
 */
class PlacetteEvolutionViewModel(
    val placetteId: String,
    val year: Int,
    private val tigeRepository: TigeRepository,
    private val essenceRepository: EssenceRepository,
    private val placetteRepository: PlacetteRepository
) : ViewModel() {

    val tiges: StateFlow<List<Tige>> =
        tigeRepository.getTigesByPlacette(placetteId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val essences: StateFlow<List<Essence>> =
        essenceRepository.getAllEssences()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val placette: StateFlow<Placette?> =
        placetteRepository.getPlacetteById(placetteId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        // Classes de diamètre par 5 cm (10-15, 15-20, 20-25, ...)
        private val DIAMETER_CLASSES = listOf(10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80)

        // Catégories de martelage avec couleur ARGB et libellé
        private val MARTELAGE_CATEGORIES = linkedMapOf(
            "AVENIR" to (0xFF4CAF50 to "Avenir"),
            "RESERVE" to (0xFF2196F3 to "Réserve"),
            "ENLEVER" to (0xFFF44336 to "Enlever"),
            "DEPERIR" to (0xFFFF9800 to "Dépérir"),
            "BIODIV" to (0xFF26A69A to "Biodiversité")
        )
        private const val OTHER_CATEGORY = "AUTRE"
        private val OTHER_COLOR = 0xFF607D8B
        private const val OTHER_LABEL = "Non catégorisé"

        /**
         * Calcule les statistiques complètes pour une année à partir des tiges.
         */
        fun computeYearStats(
            year: Int,
            allTiges: List<Tige>,
            essences: List<Essence>,
            surfaceM2: Double?
        ): YearEvolutionStats {
            val yearTiges = allTiges.filter {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = it.timestamp }
                cal.get(java.util.Calendar.YEAR) == year
            }

            val stemCount = yearTiges.size
            val essenceCount = yearTiges.map { it.essenceCode }.distinct().size
            val diameters = yearTiges.map { it.diamCm }

            val meanDiameterCm = if (diameters.isNotEmpty()) diameters.average() else 0.0
            val basalAreaM2 = yearTiges.sumOf { computeG(it.diamCm) }
            val quadraticMeanDiameterCm = if (stemCount > 0 && basalAreaM2 > 0.0) {
                sqrt((4.0 * basalAreaM2) / (PI * stemCount.toDouble())) * 100.0
            } else 0.0

            val surfaceHa = surfaceM2?.let { it / 10_000.0 }
            val basalAreaPerHaM2 = surfaceHa?.let { if (it > 0) basalAreaM2 / it else null }
            val stemsPerHa = surfaceHa?.let { if (it > 0) stemCount / it else null }

            // Hauteurs
            val tigesWithHeight = yearTiges.mapNotNull { t -> t.hauteurM?.let { t to it } }
            val meanHeightM = if (tigesWithHeight.isNotEmpty()) {
                tigesWithHeight.map { it.second }.average()
            } else null

            // Hauteur de Lorey = somme(G * H) / somme(G)
            val loreyHeightM = if (tigesWithHeight.isNotEmpty()) {
                val gHSum = tigesWithHeight.sumOf { (it.first to it.second).let { (t, h) -> computeG(t.diamCm) * h } }
                val gSum = tigesWithHeight.sumOf { computeG(it.first.diamCm) }
                if (gSum > 0.0) gHSum / gSum else null
            } else null

            // Volume simplifié : V = G * Hm * 0.5 (coefficient de forme moyen) si hauteurs dispo
            val volumeM3 = if (meanHeightM != null && meanHeightM > 0 && basalAreaM2 > 0) {
                basalAreaM2 * meanHeightM * 0.5
            } else null
            val volumePerHaM3 = volumeM3?.let { v -> surfaceHa?.let { if (it > 0) v / it else null } }

            // Biomasse / carbone
            val biomassTonnes = yearTiges.sumOf { it.biomasseFusTonnes ?: 0.0 }.takeIf { it > 0 }
            val carbonTonnes = yearTiges.sumOf { it.carboneFusTonnes ?: 0.0 }.takeIf { it > 0 }

            val habitatTreesCount = yearTiges.count { it.isTigeHabitat }

            // Par essence
            val byEssence = yearTiges.groupBy { it.essenceCode }
                .map { (code, tiges) ->
                    val essence = essences.firstOrNull { it.code == code }
                    val essDiameters = tiges.map { it.diamCm }
                    val essG = tiges.sumOf { computeG(it.diamCm) }
                    val essV = if (meanHeightM != null && essG > 0) essG * meanHeightM * 0.5 else null
                    EssenceYearStats(
                        essenceCode = code,
                        essenceName = essence?.name ?: code,
                        colorHex = essence?.colorHex,
                        stemCount = tiges.size,
                        meanDiameterCm = if (essDiameters.isNotEmpty()) essDiameters.average() else 0.0,
                        basalAreaM2 = essG,
                        volumeM3 = essV,
                        percentOfStems = if (stemCount > 0) tiges.size.toDouble() / stemCount * 100.0 else 0.0,
                        percentOfBasalArea = if (basalAreaM2 > 0) essG / basalAreaM2 * 100.0 else 0.0
                    )
                }
                .sortedByDescending { it.percentOfBasalArea }

            // Par catégorie de martelage
            val byCategory = MARTELAGE_CATEGORIES.map { (cat, pair) ->
                val (color, label) = pair
                val catTiges = yearTiges.filter { it.categorie?.uppercase()?.trim() == cat }
                if (catTiges.isEmpty()) null else {
                    val catG = catTiges.sumOf { computeG(it.diamCm) }
                    val catV = if (meanHeightM != null && catG > 0) catG * meanHeightM * 0.5 else null
                    CategoryYearStats(cat, label, color, catTiges.size, catG, catV)
                }
            }.filterNotNull() + run {
                val categorized = yearTiges.filter { t ->
                    t.categorie?.uppercase()?.trim() in MARTELAGE_CATEGORIES.keys
                }
                val otherTiges = yearTiges - categorized
                if (otherTiges.isEmpty()) emptyList() else {
                    val otherG = otherTiges.sumOf { computeG(it.diamCm) }
                    val otherV = if (meanHeightM != null && otherG > 0) otherG * meanHeightM * 0.5 else null
                    listOf(CategoryYearStats(OTHER_CATEGORY, OTHER_LABEL, OTHER_COLOR, otherTiges.size, otherG, otherV))
                }
            }

            // Distribution par classes de diamètre
            val diameterDistribution = DIAMETER_CLASSES.map { cls ->
                val classTiges = yearTiges.filter { it.diamCm >= cls && it.diamCm < cls + 5 }
                val classG = classTiges.sumOf { computeG(it.diamCm) }
                DiameterClassEntry(cls, classTiges.size, classG)
            }.filter { it.stemCount > 0 }

            return YearEvolutionStats(
                year = year,
                tiges = yearTiges,
                stemCount = stemCount,
                essenceCount = essenceCount,
                meanDiameterCm = meanDiameterCm,
                quadraticMeanDiameterCm = quadraticMeanDiameterCm,
                basalAreaM2 = basalAreaM2,
                basalAreaPerHaM2 = basalAreaPerHaM2,
                stemsPerHa = stemsPerHa,
                meanHeightM = meanHeightM,
                loreyHeightM = loreyHeightM,
                volumeM3 = volumeM3,
                volumePerHaM3 = volumePerHaM3,
                biomassTonnes = biomassTonnes,
                carbonTonnes = carbonTonnes,
                habitatTreesCount = habitatTreesCount,
                byEssence = byEssence,
                byCategory = byCategory,
                diameterDistribution = diameterDistribution
            )
        }

        /**
         * Calcule un résumé léger pour chaque année disponible (pour la liste et le line chart).
         */
        fun computeYearSummaries(allTiges: List<Tige>, surfaceM2: Double?): List<YearSummary> {
            val surfaceHa = surfaceM2?.let { it / 10_000.0 }
            return allTiges.groupBy { t ->
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = t.timestamp }
                cal.get(java.util.Calendar.YEAR)
            }.map { (year, tiges) ->
                val diameters = tiges.map { it.diamCm }
                val g = tiges.sumOf { computeG(it.diamCm) }
                YearSummary(
                    year = year,
                    stemCount = tiges.size,
                    essenceCount = tiges.map { it.essenceCode }.distinct().size,
                    meanDiameterCm = if (diameters.isNotEmpty()) diameters.average() else 0.0,
                    basalAreaM2 = g,
                    basalAreaPerHaM2 = surfaceHa?.let { if (it > 0) g / it else null }
                )
            }.sortedBy { it.year }
        }

        /** Surface terrière d'une tige : G = π × (d/200)² en m² */
        private fun computeG(diamCm: Double): Double {
            val radiusM = diamCm / 200.0
            return PI * radiusM * radiusM
        }
    }
}
