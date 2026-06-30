package com.forestry.counter.domain.calculation.tarifs

/**
 * Conversion de volumes entre stères et mètres cubes (m³).
 *
 * Coefficients standards ONF :
 * - Feuillus : 1 stère = 0.7 m³ (bois dur, empilage plus dense)
 * - Résineux : 1 stère = 0.65 m³ (bois plus léger, empilage moins dense)
 *
 * Source : ONF, Guide de cubage et d'estimation des volumes bois
 */
object VolumeConversion {

    /** Coefficient de conversion stère → m³ pour les feuillus. */
    const val STERE_TO_M3_HARDWOOD: Double = 0.7

    /** Coefficient de conversion stère → m³ pour les résineux. */
    const val STERE_TO_M3_SOFTWOOD: Double = 0.65

    /** Codes d'essences résineuses pour la détermination du coefficient. */
    private val CONIFER_KEYWORDS = listOf(
        "PIN", "SAPIN", "EPICEA", "DOUGLAS", "MELEZE", "CEDRE", "IF", "GENEV",
        "ABIES", "PICEA", "PSEUDOTSUGA", "LARIX", "CEDRUS", "TAXUS", "JUNIPER"
    )

    /**
     * Détermine si une essence est résineuse à partir de son code.
     */
    fun isConifer(essenceCode: String): Boolean {
        val code = essenceCode.trim().uppercase()
        return CONIFER_KEYWORDS.any { code.contains(it) }
    }

    /**
     * Convertit un volume en stères en mètres cubes (m³).
     *
     * @param volumeSteres Volume en stères
     * @param essenceCode Code essence pour déterminer le coefficient
     * @return Volume en m³
     */
    fun stereToM3(volumeSteres: Double, essenceCode: String): Double {
        if (volumeSteres <= 0.0) return 0.0
        val coef = if (isConifer(essenceCode)) STERE_TO_M3_SOFTWOOD else STERE_TO_M3_HARDWOOD
        return volumeSteres * coef
    }

    /**
     * Convertit un volume en m³ en stères.
     *
     * @param volumeM3 Volume en m³
     * @param essenceCode Code essence pour déterminer le coefficient
     * @return Volume en stères
     */
    fun m3ToStere(volumeM3: Double, essenceCode: String): Double {
        if (volumeM3 <= 0.0) return 0.0
        val coef = if (isConifer(essenceCode)) STERE_TO_M3_SOFTWOOD else STERE_TO_M3_HARDWOOD
        return volumeM3 / coef
    }

    /**
     * Retourne le coefficient de conversion stère → m³ pour une essence donnée.
     */
    fun conversionFactor(essenceCode: String): Double {
        return if (isConifer(essenceCode)) STERE_TO_M3_SOFTWOOD else STERE_TO_M3_HARDWOOD
    }
}
