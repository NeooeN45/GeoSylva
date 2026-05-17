package com.forestry.counter.domain.ibp

import com.forestry.counter.domain.model.IbpAnswers
import com.forestry.counter.domain.model.Tige

/**
 * Calcule le score DMH (F — Dendromicrohabitats) de l'IBP
 * depuis les tiges marquées isTigeHabitat et leurs types TreM observés.
 *
 * Nomenclature TreM CNPF 2023 — 16 types en 4 familles :
 *   TF = Trous & fissures    : TF1 Cavité, TF2 Loge, TF3 Fissure, TF4 Décollement écorce
 *   TE = Témoins d'exposition: TE1 Chancre, TE2 Blessure, TE3 Tige morte, TE4 Crochet
 *   SC = Sève & champignons  : SC1 Exsudat, SC2 Polypore, SC3 Mycètes, SC4 Nécrose
 *   EP = Épiphytes           : EP1 Mousse, EP2 Fougère, EP3 Lierre, EP4 Lichen crustacé
 *
 * Score DMH (IBP v3) :
 *   0  → 0 arbres habitat OU 0 type TreM distinct
 *   2  → 1–2 arbres habitat OU 1–3 types TreM distincts
 *   5  → ≥3 arbres habitat ET ≥4 types TreM distincts
 */
object IbpTremCalculator {

    private val ALL_TREM_CODES = setOf(
        "TF1", "TF2", "TF3", "TF4",
        "TE1", "TE2", "TE3", "TE4",
        "SC1", "SC2", "SC3", "SC4",
        "EP1", "EP2", "EP3", "EP4"
    )

    data class TremResult(
        val scoreDmh: Int,
        val nbArbresHabitat: Int,
        val nbTypesTrem: Int,
        val typesPresents: List<String>,
        val famillesPresentes: Set<String>
    )

    /**
     * Calcule depuis les tiges de la placette.
     * [tigesSurface] en ha pour normaliser si besoin (IBP exprime /ha).
     * [surfacePlacetteHa] = surface de la placette d'inventaire (défaut 0.05 ha = 500 m²).
     */
    fun calculate(
        tiges: List<Tige>,
        surfacePlacetteHa: Double = 0.05
    ): TremResult {
        val arbresHabitat = tiges.filter { it.isTigeHabitat }
        val nbArbresHabitat = arbresHabitat.size
        val nbArbresHabitatParHa = if (surfacePlacetteHa > 0) nbArbresHabitat / surfacePlacetteHa else 0.0

        val allTypes = arbresHabitat
            .flatMap { it.defauts ?: emptyList() }
            .filter { it in ALL_TREM_CODES }
            .distinct()
            .sorted()

        val nbTypesTrem = allTypes.size
        val famillesPresentes = allTypes.map { it.take(2) }.toSet()

        val scoreDmh = scoreDmh(nbArbresHabitatParHa, nbTypesTrem)

        return TremResult(
            scoreDmh = scoreDmh,
            nbArbresHabitat = nbArbresHabitat,
            nbTypesTrem = nbTypesTrem,
            typesPresents = allTypes,
            famillesPresentes = famillesPresentes
        )
    }

    /**
     * Applique le résultat TreM à un IbpAnswers existant.
     * Met à jour dmh, tremTypesPresents, tremNbArbresHabitat.
     */
    fun applyTo(answers: IbpAnswers, result: TremResult): IbpAnswers =
        answers.copy(
            dmh = result.scoreDmh,
            tremTypesPresents = result.typesPresents,
            tremNbArbresHabitat = result.nbArbresHabitat
        )

    /**
     * Retourne la liste des 16 types TreM avec leur label court.
     */
    fun allTypes(): List<Pair<String, String>> = listOf(
        "TF1" to "Cavité",
        "TF2" to "Loge piculiforme",
        "TF3" to "Fissure",
        "TF4" to "Décollement écorce",
        "TE1" to "Chancre / cancer",
        "TE2" to "Blessure ancienne",
        "TE3" to "Tige morte en cime",
        "TE4" to "Crochet / éperon",
        "SC1" to "Exsudat de sève",
        "SC2" to "Polypore lignicole",
        "SC3" to "Mycètes en chapeau",
        "SC4" to "Nécrose corticale",
        "EP1" to "Mousses / hépatiques",
        "EP2" to "Fougères / plantes",
        "EP3" to "Lierre grimpant",
        "EP4" to "Lichens crustacés"
    )

    /**
     * Règle de score IBP v3 pour DMH.
     * Exprimé en arbres habitat/ha.
     */
    private fun scoreDmh(nbHabitatParHa: Double, nbTypes: Int): Int = when {
        nbHabitatParHa == 0.0 || nbTypes == 0 -> 0
        nbHabitatParHa >= 3.0 && nbTypes >= 4  -> 5
        nbHabitatParHa >= 1.0 || nbTypes >= 2  -> 2
        else                                   -> 0
    }
}
