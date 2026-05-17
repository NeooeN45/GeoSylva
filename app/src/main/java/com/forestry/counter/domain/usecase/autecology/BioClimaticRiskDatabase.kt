package com.forestry.counter.domain.usecase.autecology

import com.forestry.counter.domain.model.ClimateZone

object BioClimaticRiskDatabase {

    enum class RiskSeverity { LOW, MEDIUM, HIGH, CRITICAL }
    enum class RiskProbability { UNLIKELY, POSSIBLE, PROBABLE, CERTAIN }

    data class BioclimaticRisk(
        val name: String,
        val description: String,
        val affectedZones: Set<ClimateZone>,
        val severity: RiskSeverity,
        val probability2050: RiskProbability,
        val earlyWarnings: List<String> = emptyList()
    )

    data class EssenceRiskProfile(
        val code: String,
        val nameFr: String,
        val risks: List<BioclimaticRisk>
    )

    private val profiles: List<EssenceRiskProfile> = listOf(
        EssenceRiskProfile("PIAB", "Épicéa commun", listOf(
            BioclimaticRisk("Pullulation de scolytes (Ips typographus)",
                "Risque systémique en forêts résineuses denses après stress hydrique.",
                setOf(ClimateZone.SEMI_OCEANIQUE, ClimateZone.CONTINENTALE, ClimateZone.MONTAGNARDE),
                RiskSeverity.CRITICAL, RiskProbability.CERTAIN,
                listOf("Aiguilles jaunissantes", "Galeries sous l'écorce", "Sciure de bois"))
        )),
        EssenceRiskProfile("FRCO", "Frêne commun", listOf(
            BioclimaticRisk("Chalarose du frêne (Hymenoscyphus fraxineus)",
                "Maladie fongique en expansion rapide, mortalité jusqu'à 90%.",
                setOf(ClimateZone.ATLANTIQUE, ClimateZone.SEMI_OCEANIQUE, ClimateZone.CONTINENTALE),
                RiskSeverity.CRITICAL, RiskProbability.CERTAIN,
                listOf("Nécrose corticale", "Dessèchement des rameaux", "Chancres sur tronc"))
        )),
        EssenceRiskProfile("FASY", "Hêtre commun", listOf(
            BioclimaticRisk("Dépérissement climatique du hêtre",
                "Sensibilité accrue aux sécheresses printanières et estivales prolongées.",
                setOf(ClimateZone.SEMI_OCEANIQUE, ClimateZone.CONTINENTALE),
                RiskSeverity.HIGH, RiskProbability.PROBABLE,
                listOf("Feuillage brûlé en été", "Diminution de l'accroissement", "Mortalité des houppiers"))
        )),
        EssenceRiskProfile("QUPE", "Chêne pédonculé", listOf(
            BioclimaticRisk("Oïdium du chêne (Erysiphe alphitoides)",
                "Champignon favorisé par les printemps humides et étés chauds.",
                setOf(ClimateZone.ATLANTIQUE, ClimateZone.SEMI_OCEANIQUE),
                RiskSeverity.MEDIUM, RiskProbability.PROBABLE,
                listOf("Feuilles blanchâtres poudrées", "Réduction de croissance"))
        ))
    )

    fun getProfileByCode(code: String): EssenceRiskProfile? =
        profiles.find { it.code == code.uppercase() }

    fun getAll(): List<EssenceRiskProfile> = profiles
}
