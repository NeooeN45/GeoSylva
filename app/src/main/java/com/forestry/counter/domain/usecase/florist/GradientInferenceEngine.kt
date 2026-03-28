package com.forestry.counter.domain.usecase.florist

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Moteur d'inférence automatique des gradients écologiques à partir d'un cortège floristique.
 *
 * À partir d'une liste d'espèces observées (IDs FloristDatabase), calcule automatiquement :
 *   - gradient hydrique     (Ellenberg H, échelle 1–7)
 *   - gradient trophique    (Ellenberg N, échelle 1–6)
 *   - gradient acidité      (Ellenberg R, échelle 1–5)
 *   - gradient lumière      (Ellenberg L, approximé)
 *   - probabilité hydromorphie
 *   - probabilité perturbation
 *   - cohérence interne du cortège
 *   - conflits détectés
 *   - taxons qui tirent vers le sec / le frais
 *   - suggestions d'observations complémentaires
 *
 * Pondération :
 *   - espèces de la strate herbacée ont le poids le plus fort (indicatrices directes)
 *   - arbustes poids intermédiaire
 *   - arbres poids faible (moins spécifiques à la station)
 *   - espèces indicatrices de perturbation réduisent la confiance
 */
object GradientInferenceEngine {

    // ─── Sortie principale ────────────────────────────────────────────────────

    data class GradientResult(
        // Gradients calculés (moyennes pondérées Ellenberg)
        val hydrique: Double,           // 1–7  (1=xérique, 7=hydrophyte)
        val trophique: Double,          // 1–6  (1=oligotrophe, 6=nitrophile)
        val acidite: IndicAcidite,      // catégorie dominante
        val aciditeScore: Double,       // 1–5  (1=très acide, 5=calcicole)
        val lumiere: Double,            // 1–5  (1=ombre, 5=plein soleil)
        val probabiliteHydromorphie: Double,   // 0–1
        val probabilitePerturbation: Double,   // 0–1
        val probabiliteCompaction: Double,     // 0–1

        // Interprétation
        val hydriqueLabelFr: String,
        val trophiqueLabelFr: String,
        val cohérenceInterne: Coherence,
        val confidenceLevel: ConfidenceGradient,
        val nbTaxonsAnalysables: Int,
        val nbTaxonsTotaux: Int,

        // Détails explicatifs
        val especesTirantVersSec: List<String>,
        val especesTirantVersFrais: List<String>,
        val especesContradictoires: List<String>,
        val conflits: List<ConflitGradient>,
        val observationsComplementaires: List<String>,

        // Texte de synthèse
        val syntheseTextuelle: String
    )

    data class ConflitGradient(
        val gradient: String,
        val description: String,
        val espece1: String,
        val espece2: String,
        val severite: Severite
    )

    enum class Coherence(val labelFr: String, val icon: String) {
        FORTE("Cortège cohérent", "✅"),
        MOYENNE("Cohérence acceptable", "⚠️"),
        FAIBLE("Cortège hétérogène", "⚠️"),
        CONTRADICTOIRE("Contradictions fortes", "❌"),
        INSUFFISANT("Trop peu d'espèces", "ℹ️")
    }

    enum class ConfidenceGradient(val labelFr: String) {
        HAUTE("Haute — cortège riche et cohérent"),
        MOYENNE("Moyenne — quelques lacunes"),
        FAIBLE("Faible — cortège pauvre ou contradictoire"),
        INSUFFISANTE("Insuffisante — moins de 3 taxons analysables")
    }

    enum class Severite { FORTE, MODERE, FAIBLE }

    // Poids selon strate (herbacée = meilleur indicateur)
    private fun poidsStrate(sp: EspeceVegetale): Double = when (sp.classification.strateVegetale) {
        StrateVegetale.HERBACEE    -> 1.0
        StrateVegetale.SOUS_ARBUSTE -> 0.9
        StrateVegetale.MOUSSE      -> 0.85
        StrateVegetale.ARBUSTE     -> 0.6
        StrateVegetale.LIANE       -> 0.5
        StrateVegetale.ARBRE       -> 0.3
    }

    // ─── Calcul principal ────────────────────────────────────────────────────

    /**
     * Calcule les gradients à partir d'une liste d'IDs d'espèces.
     *
     * @param speciesIds IDs des espèces dans FloristDatabase
     * @param contextHydrique  Gradient hydrique observé terrain (override partiel)
     * @param contextTrophique Gradient trophique observé terrain (override partiel)
     */
    fun computeGradients(
        speciesIds: List<String>,
        contextHydrique: Int? = null,
        contextTrophique: Int? = null
    ): GradientResult {
        val allSpecies = speciesIds.mapNotNull { FloristDatabase.findById(it) }
        val analyzable = allSpecies.filter { it.valeurIndicatrice.ellenbergH != IndicHumidite.XEROPHYTE ||
                it.valeurIndicatrice.ellenbergN != IndicFertilite.OLIGOTROPHE }

        if (analyzable.size < 2) {
            return insufficientResult(allSpecies.size, speciesIds.size)
        }

        // ── Moyennes pondérées Ellenberg ──────────────────────────────────────
        var sumH = 0.0; var sumN = 0.0; var sumR = 0.0; var sumL = 0.0
        var weightTotal = 0.0
        val rCounts = mutableMapOf<IndicAcidite, Double>()

        for (sp in analyzable) {
            val w = poidsStrate(sp)
            // Réduire le poids des espèces perturbantes (moins fiables)
            val wAdj = if (sp.valeurIndicatrice.indicateurPerturbation) w * 0.5 else w

            sumH += sp.valeurIndicatrice.ellenbergH.codeEllenberg * wAdj
            sumN += sp.valeurIndicatrice.ellenbergN.codeEllenberg * wAdj
            rCounts[sp.valeurIndicatrice.ellenbergR] = (rCounts[sp.valeurIndicatrice.ellenbergR] ?: 0.0) + wAdj
            // Lumière : approximer depuis besoin lumière de l'écologie station
            val lApprox = sp.ecologie.besoinLumiere.valeur.toDouble()
            sumL += lApprox * wAdj
            weightTotal += wAdj
        }

        val meanH = if (weightTotal > 0) sumH / weightTotal else 3.0
        val meanN = if (weightTotal > 0) sumN / weightTotal else 3.0
        val meanL = if (weightTotal > 0) sumL / weightTotal else 3.0
        val domR  = rCounts.maxByOrNull { it.value }?.key ?: IndicAcidite.NEUTROPHILE
        val meanRScore = rCounts.entries.sumOf { (k, v) -> k.codeEllenberg * v } /
                rCounts.values.sum().coerceAtLeast(0.001)

        // ── Intégration override terrain ──────────────────────────────────────
        // Si gradient terrain fourni, pondérer 60% flore / 40% terrain
        val hydriqueFinal = if (contextHydrique != null)
            meanH * 0.6 + contextHydrique.toDouble() * 0.4
        else meanH

        val trophiqueFinal = if (contextTrophique != null)
            meanN * 0.6 + contextTrophique.toDouble() * 0.4
        else meanN

        // ── Probabilités ──────────────────────────────────────────────────────
        val probHydro   = analyzable.count { it.valeurIndicatrice.indicateurHydromorphie }.toDouble() / analyzable.size
        val probPerturb = analyzable.count { it.valeurIndicatrice.indicateurPerturbation }.toDouble() / analyzable.size
        val probCompact = analyzable.count { it.valeurIndicatrice.indicateurCompaction  }.toDouble() / analyzable.size

        // ── Conflits ──────────────────────────────────────────────────────────
        val conflits = detectConflits(analyzable, meanH, meanN)

        // ── Cohérence interne ─────────────────────────────────────────────────
        val coherence = computeCoherence(analyzable, meanH, meanN, conflits)

        // ── Confiance ─────────────────────────────────────────────────────────
        val confidence = computeConfidence(analyzable.size, coherence, probPerturb)

        // ── Espèces directionnelles ───────────────────────────────────────────
        val especesSec   = analyzable.filter { it.valeurIndicatrice.ellenbergH.codeEllenberg <= 2 }
            .map { it.taxonomie.nomFrancais }
        val especesFrais = analyzable.filter { it.valeurIndicatrice.ellenbergH.codeEllenberg >= 5 }
            .map { it.taxonomie.nomFrancais }
        val especesCont  = analyzable.filter {
            val h = it.valeurIndicatrice.ellenbergH.codeEllenberg
            abs(h - meanH) > 1.5
        }.map { it.taxonomie.nomFrancais }

        // ── Observations complémentaires ──────────────────────────────────────
        val obs = mutableListOf<String>()
        if (analyzable.size < 5)
            obs += "Enrichir le cortège : observer d'autres herbacées du sous-bois"
        if (probHydro > 0.3)
            obs += "Vérifier la profondeur d'apparition de l'hydromorphie (taches rouille)"
        if (hydriqueFinal in 3.5..4.5)
            obs += "Test ressuyage : observer si le sol est humide en profondeur après pluie"
        if (conflits.any { it.gradient == "Hydrique" && it.severite == Severite.FORTE })
            obs += "Cortège hydrique incohérent — vérifier la micro-topographie"
        if (probPerturb > 0.4)
            obs += "Flore perturbée : estimer l'ancienneté et la nature des perturbations"
        if (meanN > 4.5)
            obs += "Forte trophie probable : chercher indices de fertilisation ou d'apports alluviaux"
        if (domR == IndicAcidite.ACIDOPHILE_STRICT)
            obs += "pH très acide probable : vérifier test HCl négatif et humus de type mor"
        if (domR == IndicAcidite.BASOPHILE)
            obs += "Calcicole : vérifier test HCl positif ou présence de carbonates"

        return GradientResult(
            hydrique = hydriqueFinal,
            trophique = trophiqueFinal,
            acidite = domR,
            aciditeScore = meanRScore,
            lumiere = meanL,
            probabiliteHydromorphie = probHydro,
            probabilitePerturbation = probPerturb,
            probabiliteCompaction = probCompact,
            hydriqueLabelFr = labelHydrique(hydriqueFinal),
            trophiqueLabelFr = labelTrophique(trophiqueFinal),
            cohérenceInterne = coherence,
            confidenceLevel = confidence,
            nbTaxonsAnalysables = analyzable.size,
            nbTaxonsTotaux = allSpecies.size,
            especesTirantVersSec = especesSec,
            especesTirantVersFrais = especesFrais,
            especesContradictoires = especesCont,
            conflits = conflits,
            observationsComplementaires = obs,
            syntheseTextuelle = buildSynthese(
                hydriqueFinal, trophiqueFinal, domR, probHydro, probPerturb,
                coherence, analyzable.size, conflits
            )
        )
    }

    // ─── Détection de conflits ────────────────────────────────────────────────

    private fun detectConflits(
        species: List<EspeceVegetale>,
        meanH: Double,
        meanN: Double
    ): List<ConflitGradient> {
        val conflits = mutableListOf<ConflitGradient>()
        val pairs = species.take(20) // limiter les comparaisons

        for (i in pairs.indices) {
            for (j in i + 1 until pairs.size) {
                val a = pairs[i]; val b = pairs[j]

                // Conflit hydrique fort (xérophyte strict + hygrophyte strict)
                val diffH = abs(
                    a.valeurIndicatrice.ellenbergH.codeEllenberg -
                    b.valeurIndicatrice.ellenbergH.codeEllenberg
                )
                if (diffH >= 4) {
                    conflits += ConflitGradient(
                        "Hydrique",
                        "Espèces aux exigences hydriques opposées",
                        a.taxonomie.nomFrancais, b.taxonomie.nomFrancais,
                        if (diffH >= 5) Severite.FORTE else Severite.MODERE
                    )
                }

                // Conflit trophique fort
                val diffN = abs(
                    a.valeurIndicatrice.ellenbergN.codeEllenberg -
                    b.valeurIndicatrice.ellenbergN.codeEllenberg
                )
                if (diffN >= 4) {
                    conflits += ConflitGradient(
                        "Trophique",
                        "Espèces aux exigences trophiques très opposées",
                        a.taxonomie.nomFrancais, b.taxonomie.nomFrancais,
                        if (diffN >= 5) Severite.FORTE else Severite.MODERE
                    )
                }

                // Conflit acidité : acidophile strict + basophile
                if (a.valeurIndicatrice.ellenbergR == IndicAcidite.ACIDOPHILE_STRICT &&
                    b.valeurIndicatrice.ellenbergR == IndicAcidite.BASOPHILE) {
                    conflits += ConflitGradient(
                        "Acidité",
                        "Acidophile strict et calcicole dans le même cortège",
                        a.taxonomie.nomFrancais, b.taxonomie.nomFrancais,
                        Severite.FORTE
                    )
                }
            }
        }

        return conflits.distinctBy { "${it.espece1}-${it.espece2}-${it.gradient}" }.take(5)
    }

    // ─── Cohérence interne ────────────────────────────────────────────────────

    private fun computeCoherence(
        species: List<EspeceVegetale>,
        meanH: Double,
        meanN: Double,
        conflits: List<ConflitGradient>
    ): Coherence {
        if (species.size < 3) return Coherence.INSUFFISANT

        // Écart-type des valeurs H et N
        val stdH = standardDeviation(species.map { it.valeurIndicatrice.ellenbergH.codeEllenberg.toDouble() })
        val stdN = standardDeviation(species.map { it.valeurIndicatrice.ellenbergN.codeEllenberg.toDouble() })

        val conflitsForts = conflits.count { it.severite == Severite.FORTE }

        return when {
            conflitsForts >= 2 || (stdH > 2.0 && stdN > 1.8) -> Coherence.CONTRADICTOIRE
            conflitsForts >= 1 || stdH > 1.5 || stdN > 1.5   -> Coherence.FAIBLE
            stdH > 1.0 || stdN > 1.0                          -> Coherence.MOYENNE
            else                                               -> Coherence.FORTE
        }
    }

    private fun standardDeviation(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }

    // ─── Confiance ───────────────────────────────────────────────────────────

    private fun computeConfidence(
        n: Int,
        coherence: Coherence,
        probPerturb: Double
    ): ConfidenceGradient = when {
        n < 3                                      -> ConfidenceGradient.INSUFFISANTE
        coherence == Coherence.CONTRADICTOIRE      -> ConfidenceGradient.FAIBLE
        n >= 8 && coherence == Coherence.FORTE && probPerturb < 0.2 -> ConfidenceGradient.HAUTE
        n >= 5 && coherence != Coherence.FAIBLE    -> ConfidenceGradient.MOYENNE
        else                                       -> ConfidenceGradient.FAIBLE
    }

    // ─── Labels de gradients ─────────────────────────────────────────────────

    fun labelHydrique(h: Double): String = when {
        h < 1.5 -> "Très sec (xérique)"
        h < 2.5 -> "Sec"
        h < 3.5 -> "Frais / mésophile"
        h < 4.5 -> "Humide"
        h < 5.5 -> "Très humide"
        h < 6.5 -> "Engorgé / marécageux"
        else    -> "Aquatique / semi-aquatique"
    }

    fun labelTrophique(n: Double): String = when {
        n < 1.5 -> "Oligotrophe — sol très pauvre"
        n < 2.5 -> "Méso-oligotrophe — sol pauvre"
        n < 3.5 -> "Mésotrophe — sol intermédiaire"
        n < 4.5 -> "Méso-eutrophe — sol assez fertile"
        n < 5.5 -> "Eutrophe — sol fertile"
        else    -> "Nitrophile — sol très riche"
    }

    // ─── Synthèse textuelle ───────────────────────────────────────────────────

    private fun buildSynthese(
        h: Double, n: Double,
        acidite: IndicAcidite,
        probHydro: Double, probPerturb: Double,
        coherence: Coherence, nbSp: Int,
        conflits: List<ConflitGradient>
    ): String = buildString {
        append("Cortège de $nbSp espèces analysables. ")
        append("Humidité : ${labelHydrique(h)} (H=${String.format("%.1f", h)}/7). ")
        append("Fertilité : ${labelTrophique(n)} (N=${String.format("%.1f", n)}/6). ")
        append("Réaction du sol : ${acidite.labelFr}. ")
        if (probHydro > 0.35) append("⚠ Hydromorphie probable (${(probHydro*100).toInt()}% hygrophytes). ")
        if (probPerturb > 0.35) append("⚠ Flore perturbée ou nitrophile (${(probPerturb*100).toInt()}% indicateurs). ")
        if (conflits.isNotEmpty()) append("⚠ ${conflits.size} conflit(s) détecté(s) dans le cortège. ")
        append("Cohérence : ${coherence.labelFr}.")
    }

    // ─── Résultat insuffisant ─────────────────────────────────────────────────

    private fun insufficientResult(nbSpecies: Int, nbIds: Int): GradientResult =
        GradientResult(
            hydrique = 3.0, trophique = 3.0,
            acidite = IndicAcidite.NEUTROPHILE, aciditeScore = 3.0,
            lumiere = 3.0,
            probabiliteHydromorphie = 0.0,
            probabilitePerturbation = 0.0,
            probabiliteCompaction = 0.0,
            hydriqueLabelFr = "Indéterminé",
            trophiqueLabelFr = "Indéterminé",
            cohérenceInterne = Coherence.INSUFFISANT,
            confidenceLevel = ConfidenceGradient.INSUFFISANTE,
            nbTaxonsAnalysables = nbSpecies,
            nbTaxonsTotaux = nbIds,
            especesTirantVersSec = emptyList(),
            especesTirantVersFrais = emptyList(),
            especesContradictoires = emptyList(),
            conflits = emptyList(),
            observationsComplementaires = listOf(
                "Saisir au moins 3–5 espèces herbacées du sous-bois pour un diagnostic fiable"
            ),
            syntheseTextuelle = "Cortège insuffisant ($nbSpecies esp.) — diagnostic non fiable."
        )

    // ─── Conversion vers gradients StationObservation (échelle 1–5) ──────────

    /**
     * Convertit le gradient hydrique Ellenberg (1–7) vers l'échelle Station (1–5).
     */
    fun ellenbergHToStationGradient(h: Double): Int = when {
        h < 1.5 -> 1
        h < 2.5 -> 1
        h < 3.5 -> 2
        h < 4.5 -> 3
        h < 5.5 -> 4
        else    -> 5
    }

    /**
     * Convertit le gradient trophique Ellenberg (1–6) vers l'échelle Station (1–5).
     */
    fun ellenbergNToStationGradient(n: Double): Int = when {
        n < 1.5 -> 1
        n < 2.5 -> 2
        n < 3.5 -> 3
        n < 4.5 -> 4
        else    -> 5
    }
}
