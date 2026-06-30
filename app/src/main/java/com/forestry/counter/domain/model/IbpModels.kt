package com.forestry.counter.domain.model

import kotlinx.serialization.Serializable

enum class IbpGrowthConditions {
    LOWLAND,        // Plaine/Colline < ~900 m
    HIGHLAND,       // Montagne > ~900 m
    SUBALPINE,      // Sub-alpin
    MEDITERRANEAN   // Méditerranéen
}

enum class IbpCriterionId(val code: String, val displayCode: String, val group: IbpGroup) {
    E1("E1",  "A", IbpGroup.A),  // A – Essences autochtones
    E2("E2",  "B", IbpGroup.A),  // B – Structure verticale
    BMS("BMS","C", IbpGroup.A),  // C – Bois morts sur pied
    BMC("BMC","D", IbpGroup.A),  // D – Bois morts au sol
    GB("GB",  "E", IbpGroup.A),  // E – Très gros bois vivants
    DMH("DMH","F", IbpGroup.A),  // F – Dendromicrohabitats
    VS("VS",  "G", IbpGroup.A),  // G – Milieux ouverts florifers
    CF("CF",  "H", IbpGroup.B),  // H – Continuité temporelle
    CO("CO",  "I", IbpGroup.B),  // I – Milieux aquatiques
    HC("HC",  "J", IbpGroup.B);  // J – Milieux rocheux

    companion object {
        val ALL    = listOf(E1, E2, BMS, BMC, GB, DMH, VS, CF, CO, HC) // A→J order
        val GROUP_A = listOf(E1, E2, BMS, BMC, GB, DMH, VS)
        val GROUP_B = listOf(CF, CO, HC)
    }
}

enum class IbpGroup { A, B }

/**
 * IBP evaluation mode — determines which criteria are shown in the UI.
 * Scores are always stored for all 10 criteria; mode only affects display.
 */
enum class IbpMode {
    COMPLET,     // All 10 criteria A→J (default)
    RAPIDE,      // Quick field: A, C, E, F, H (5 essential criteria)
    BOIS_MORT,   // Dead-wood specialist: C, D, E
    CONTEXTE,    // Habitat context: H, I, J
    PEUPLEMENT;  // Stand only: A, B, C, D, E, F, G

    fun criteria(): List<IbpCriterionId> = when (this) {
        COMPLET    -> IbpCriterionId.ALL
        RAPIDE     -> listOf(IbpCriterionId.E1, IbpCriterionId.BMS, IbpCriterionId.GB, IbpCriterionId.DMH, IbpCriterionId.CF)
        BOIS_MORT  -> listOf(IbpCriterionId.BMS, IbpCriterionId.BMC, IbpCriterionId.GB)
        CONTEXTE   -> listOf(IbpCriterionId.CF, IbpCriterionId.CO, IbpCriterionId.HC)
        PEUPLEMENT -> IbpCriterionId.GROUP_A
    }

    fun maxScore(): Int = criteria().size * 5
}

/** Valid answer values: -1 (unanswered), 0, 2, 5 (IBP v3 scoring).
 *  schemaVersion=1 = legacy 0/1/2 system; schemaVersion=2 = current 0/2/5 system.
 *  counts: raw field measurements (BMg/ha, TGB/ha, dmh trees/ha, open %, …)
 *  tremTypesPresents: liste des codes TreM détectés (ex: "TF1", "TF2", "TE1"…)
 *  tremNbArbresHabitat: nb de tiges marquées isTigeHabitat dans la placette
 *  continuiteForestiereAns: ancienneté estimée de la forêt (années, 0=inconnue)
 *  continuiteSpatialePct: % de connectivité spatiale estimée (0–100)
 *  isForetAncienneConfirmee: forêt ancienne (>200 ans) confirmée (isForetAncienne DB)
 */
@Serializable
data class IbpAnswers(
    val e1: Int = -1,
    val e2: Int = -1,
    val gb: Int = -1,
    val bms: Int = -1,
    val bmc: Int = -1,
    val dmh: Int = -1,
    val vs: Int = -1,
    val cf: Int = -1,
    val co: Int = -1,
    val hc: Int = -1,
    val schemaVersion: Int = 2,
    val details: Map<String, List<String>> = emptyMap(),
    val counts: Map<String, Float> = emptyMap(),

    // Enrichissement TreM (v3 — DB v27)
    val tremTypesPresents: List<String> = emptyList(),
    val tremNbArbresHabitat: Int = 0,
    val continuiteForestiereAns: Int = 0,
    val continuiteSpatialePct: Int = 0,
    val isForetAncienneConfirmee: Boolean = false
) {
    fun getDetails(id: IbpCriterionId): List<String> = details[id.code] ?: emptyList()
    fun setDetails(id: IbpCriterionId, items: List<String>): IbpAnswers =
        copy(details = details + (id.code to items))
    fun getCount(key: String): Float = counts[key] ?: 0f
    fun withCount(key: String, value: Float): IbpAnswers = copy(counts = counts + (key to value))
    fun get(id: IbpCriterionId): Int = when (id) {
        IbpCriterionId.E1  -> e1
        IbpCriterionId.E2  -> e2
        IbpCriterionId.GB  -> gb
        IbpCriterionId.BMS -> bms
        IbpCriterionId.BMC -> bmc
        IbpCriterionId.DMH -> dmh
        IbpCriterionId.VS  -> vs
        IbpCriterionId.CF  -> cf
        IbpCriterionId.CO  -> co
        IbpCriterionId.HC  -> hc
    }

    fun set(id: IbpCriterionId, value: Int): IbpAnswers = when (id) {
        IbpCriterionId.E1  -> copy(e1 = value)
        IbpCriterionId.E2  -> copy(e2 = value)
        IbpCriterionId.GB  -> copy(gb = value)
        IbpCriterionId.BMS -> copy(bms = value)
        IbpCriterionId.BMC -> copy(bmc = value)
        IbpCriterionId.DMH -> copy(dmh = value)
        IbpCriterionId.VS  -> copy(vs = value)
        IbpCriterionId.CF  -> copy(cf = value)
        IbpCriterionId.CO  -> copy(co = value)
        IbpCriterionId.HC  -> copy(hc = value)
    }

    /** Migrate legacy 0/1/2 scores to 0/2/5 system. */
    fun migrateToV2(): IbpAnswers = copy(
        e1 = migrateLegacyScore(e1), e2 = migrateLegacyScore(e2),
        gb = migrateLegacyScore(gb), bms = migrateLegacyScore(bms),
        bmc = migrateLegacyScore(bmc), dmh = migrateLegacyScore(dmh),
        vs = migrateLegacyScore(vs), cf = migrateLegacyScore(cf),
        co = migrateLegacyScore(co), hc = migrateLegacyScore(hc),
        schemaVersion = 2
    )

    private fun migrateLegacyScore(v: Int): Int = when (v) {
        -1 -> -1; 0 -> 0; 1 -> 2; 2 -> 5; else -> -1
    }

    val scoreA: Int get() {
        val vals = listOf(e1, e2, gb, bms, bmc, dmh, vs)
        if (vals.any { it < 0 }) return -1
        return vals.sum()
    }

    val scoreB: Int get() {
        val vals = listOf(cf, co, hc)
        if (vals.any { it < 0 }) return -1
        return vals.sum()
    }

    val scoreTotal: Int get() {
        val a = scoreA
        val b = scoreB
        if (a < 0 || b < 0) return -1
        return a + b
    }

    val answeredCount: Int get() = listOf(e1, e2, gb, bms, bmc, dmh, vs, cf, co, hc).count { it >= 0 }
    val isComplete: Boolean get() = answeredCount == 10

    companion object {
        /** Create a new IbpAnswers with current schema (v2 = 0/2/5 system, Larrieu & Gonin 2008). */
        fun new() = IbpAnswers(schemaVersion = 2)
        val VALID_SCORES = setOf(0, 2, 5)
    }
}

data class IbpEvaluation(
    val id: String,
    val placetteId: String,
    val parcelleId: String,
    val observationDate: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val evaluatorName: String = "",
    val answers: IbpAnswers = IbpAnswers.new(),
    val globalNote: String = "",
    val growthConditions: IbpGrowthConditions = IbpGrowthConditions.LOWLAND,
    val ibpMode: IbpMode = IbpMode.COMPLET,
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    val scoreA: Int get() = answers.scoreA
    val scoreB: Int get() = answers.scoreB
    val scoreTotal: Int get() = answers.scoreTotal
    val isComplete: Boolean get() = answers.isComplete

    /** Score A (critères A-G, gestion) en pourcentage (0-100). Max = 35 points. */
    val scoreAPct: Int get() = if (scoreA > 0) (scoreA * 100 / 35) else 0

    /** Score B (critères H-J, contexte) en pourcentage (0-100). Max = 15 points. */
    val scoreBPct: Int get() = if (scoreB > 0) (scoreB * 100 / 15) else 0

    fun levelColor(): IbpLevel = IbpLevel.fromScore(scoreTotal)
}

enum class IbpLevel(val minScore: Int, val maxScore: Int) {
    VERY_LOW(0, 9),
    LOW(10, 19),
    MEDIUM(20, 29),
    GOOD(30, 39),
    VERY_GOOD(40, 50);

    companion object {
        fun fromScore(score: Int): IbpLevel {
            if (score < 0) return VERY_LOW
            return values().lastOrNull { score >= it.minScore } ?: VERY_LOW
        }
    }
}
