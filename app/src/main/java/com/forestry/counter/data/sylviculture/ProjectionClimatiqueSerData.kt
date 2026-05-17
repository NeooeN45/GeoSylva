package com.forestry.counter.data.sylviculture

import com.forestry.counter.data.local.entity.ProjectionClimatiqueSerEntity

/**
 * Données embarquées de projections climatiques GIEC CMIP6 par SylvoÉcoRégion.
 * Source : DRIAS-Les Futurs du Climat (Météo-France), GIEC AR6 WG1, Climessences INRAE.
 *
 * Scénarios : SSP1-2.6 (optimiste), SSP2-4.5 (intermédiaire), SSP5-8.5 (pessimiste)
 * Horizons : 2050, 2080
 * deltas = écart par rapport à la période de référence 1981-2010
 */
object ProjectionClimatiqueSerData {

    private const val SOURCE = "GIEC AR6 / DRIAS Météo-France / Climessences INRAE"

    fun buildAll(): List<ProjectionClimatiqueSerEntity> {
        val list = mutableListOf<ProjectionClimatiqueSerEntity>()
        list += buildRegionNord()
        list += buildRegionNordEst()
        list += buildRegionOuest()
        list += buildRegionCentre()
        list += buildRegionMassifCentral()
        list += buildRegionAlpesNord()
        list += buildRegionAlpesSud()
        list += buildRegionPyrenees()
        list += buildRegionMediterranee()
        list += buildRegionAquitaine()
        return list
    }

    // ──────────────────────────────────────────────────────────────────────────
    // NORD (Ardenne, Boulonnais, Thiérache) — B11, B12, D11
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildRegionNord(): List<ProjectionClimatiqueSerEntity> {
        val serList = listOf("B11" to "Ardenne primaire", "B12" to "Ardenne secondaire", "D11" to "Boulonnais-Thiérache")
        return buildForSers(serList, deltas = mapOf(
            "SSP1-2.6" to mapOf(2050 to Delta(1.2, 1.4, -30.0, -20.0, 4, -0.2),
                                 2080 to Delta(1.5, 1.8, -40.0, -25.0, 5, -0.3)),
            "SSP2-4.5" to mapOf(2050 to Delta(1.6, 1.8, -50.0, -35.0, 6, -0.3),
                                 2080 to Delta(2.2, 2.8, -70.0, -45.0, 10, -0.5)),
            "SSP5-8.5" to mapOf(2050 to Delta(2.0, 2.5, -60.0, -40.0, 8, -0.4),
                                 2080 to Delta(3.5, 4.5, -100.0, -65.0, 18, -0.8))
        ))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // NORD-EST (Lorraine, Vosges) — C10, C11
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildRegionNordEst(): List<ProjectionClimatiqueSerEntity> {
        val serList = listOf("C10" to "Lorraine", "C11" to "Vosges")
        return buildForSers(serList, deltas = mapOf(
            "SSP1-2.6" to mapOf(2050 to Delta(1.3, 1.6, -25.0, -15.0, 4, -0.2),
                                 2080 to Delta(1.6, 2.0, -35.0, -20.0, 6, -0.3)),
            "SSP2-4.5" to mapOf(2050 to Delta(1.7, 2.2, -45.0, -30.0, 7, -0.35),
                                 2080 to Delta(2.5, 3.2, -65.0, -42.0, 12, -0.55)),
            "SSP5-8.5" to mapOf(2050 to Delta(2.2, 2.8, -55.0, -38.0, 10, -0.45),
                                 2080 to Delta(4.0, 5.2, -95.0, -62.0, 22, -0.9))
        ))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // OUEST (Normandie, Bretagne, Pays de Loire) — E10, E11, E12
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildRegionOuest(): List<ProjectionClimatiqueSerEntity> {
        val serList = listOf("E10" to "Normandie", "E11" to "Bretagne", "E12" to "Pays de Loire")
        return buildForSers(serList, deltas = mapOf(
            "SSP1-2.6" to mapOf(2050 to Delta(1.1, 1.3, -20.0, -10.0, 3, -0.15),
                                 2080 to Delta(1.4, 1.7, -30.0, -15.0, 4, -0.2)),
            "SSP2-4.5" to mapOf(2050 to Delta(1.5, 1.8, -40.0, -22.0, 5, -0.25),
                                 2080 to Delta(2.1, 2.7, -60.0, -35.0, 9, -0.45)),
            "SSP5-8.5" to mapOf(2050 to Delta(1.9, 2.3, -50.0, -30.0, 7, -0.35),
                                 2080 to Delta(3.3, 4.2, -85.0, -55.0, 16, -0.7))
        ))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // CENTRE (Île-de-France, Sologne, Champagne) — F10, F11, F12
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildRegionCentre(): List<ProjectionClimatiqueSerEntity> {
        val serList = listOf("F10" to "Île-de-France", "F11" to "Sologne", "F12" to "Champagne")
        return buildForSers(serList, deltas = mapOf(
            "SSP1-2.6" to mapOf(2050 to Delta(1.3, 1.7, -30.0, -20.0, 5, -0.25),
                                 2080 to Delta(1.7, 2.2, -40.0, -25.0, 7, -0.35)),
            "SSP2-4.5" to mapOf(2050 to Delta(1.8, 2.3, -50.0, -35.0, 8, -0.4),
                                 2080 to Delta(2.6, 3.5, -75.0, -50.0, 14, -0.6)),
            "SSP5-8.5" to mapOf(2050 to Delta(2.3, 3.0, -65.0, -45.0, 12, -0.55),
                                 2080 to Delta(4.2, 5.8, -110.0, -75.0, 26, -1.0))
        ))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // MASSIF CENTRAL (nord + sud + Cévennes) — G10, H10, I10
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildRegionMassifCentral(): List<ProjectionClimatiqueSerEntity> {
        val serList = listOf("G10" to "Massif central nord", "H10" to "Massif central sud", "I10" to "Cévennes")
        return buildForSers(serList, deltas = mapOf(
            "SSP1-2.6" to mapOf(2050 to Delta(1.4, 1.8, -30.0, -25.0, 6, -0.3),
                                 2080 to Delta(1.8, 2.3, -40.0, -32.0, 8, -0.4)),
            "SSP2-4.5" to mapOf(2050 to Delta(1.9, 2.5, -55.0, -42.0, 10, -0.5),
                                 2080 to Delta(2.8, 3.8, -80.0, -60.0, 17, -0.7)),
            "SSP5-8.5" to mapOf(2050 to Delta(2.5, 3.3, -70.0, -55.0, 14, -0.65),
                                 2080 to Delta(4.5, 6.2, -115.0, -85.0, 28, -1.1))
        ))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ALPES NORD (internes + externes) — J10, K10
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildRegionAlpesNord(): List<ProjectionClimatiqueSerEntity> {
        val serList = listOf("J10" to "Alpes nord internes", "K10" to "Alpes nord externes")
        return buildForSers(serList, deltas = mapOf(
            "SSP1-2.6" to mapOf(2050 to Delta(1.5, 2.0, -20.0, -15.0, 5, -0.25),
                                 2080 to Delta(1.9, 2.5, -28.0, -20.0, 7, -0.35)),
            "SSP2-4.5" to mapOf(2050 to Delta(2.0, 2.7, -38.0, -28.0, 9, -0.45),
                                 2080 to Delta(2.9, 4.0, -55.0, -42.0, 15, -0.65)),
            "SSP5-8.5" to mapOf(2050 to Delta(2.6, 3.5, -48.0, -38.0, 13, -0.6),
                                 2080 to Delta(4.8, 6.5, -90.0, -70.0, 25, -1.05))
        ))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ALPES SUD — L10
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildRegionAlpesSud(): List<ProjectionClimatiqueSerEntity> {
        val serList = listOf("L10" to "Alpes sud")
        return buildForSers(serList, deltas = mapOf(
            "SSP1-2.6" to mapOf(2050 to Delta(1.6, 2.2, -35.0, -30.0, 7, -0.35),
                                 2080 to Delta(2.0, 2.8, -48.0, -40.0, 10, -0.45)),
            "SSP2-4.5" to mapOf(2050 to Delta(2.2, 3.0, -60.0, -50.0, 12, -0.55),
                                 2080 to Delta(3.2, 4.5, -85.0, -70.0, 20, -0.8)),
            "SSP5-8.5" to mapOf(2050 to Delta(2.8, 3.8, -75.0, -62.0, 16, -0.7),
                                 2080 to Delta(5.0, 7.0, -125.0, -100.0, 30, -1.2))
        ))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PYRÉNÉES — M10
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildRegionPyrenees(): List<ProjectionClimatiqueSerEntity> {
        val serList = listOf("M10" to "Pyrénées")
        return buildForSers(serList, deltas = mapOf(
            "SSP1-2.6" to mapOf(2050 to Delta(1.5, 2.0, -30.0, -25.0, 6, -0.3),
                                 2080 to Delta(1.9, 2.5, -42.0, -35.0, 8, -0.4)),
            "SSP2-4.5" to mapOf(2050 to Delta(2.0, 2.7, -52.0, -42.0, 11, -0.5),
                                 2080 to Delta(2.9, 4.0, -75.0, -60.0, 18, -0.7)),
            "SSP5-8.5" to mapOf(2050 to Delta(2.6, 3.5, -65.0, -52.0, 15, -0.65),
                                 2080 to Delta(4.8, 6.5, -115.0, -92.0, 27, -1.1))
        ))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // MÉDITERRANÉE (Provence + Languedoc) — P10, Q10, R10
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildRegionMediterranee(): List<ProjectionClimatiqueSerEntity> {
        val serList = listOf("P10" to "Provence cristalline", "Q10" to "Provence calcaire", "R10" to "Languedoc-Roussillon")
        return buildForSers(serList, deltas = mapOf(
            "SSP1-2.6" to mapOf(2050 to Delta(1.7, 2.3, -45.0, -42.0, 9, -0.45),
                                 2080 to Delta(2.1, 2.9, -60.0, -55.0, 12, -0.55)),
            "SSP2-4.5" to mapOf(2050 to Delta(2.3, 3.2, -75.0, -68.0, 15, -0.7),
                                 2080 to Delta(3.4, 4.8, -105.0, -95.0, 24, -1.0)),
            "SSP5-8.5" to mapOf(2050 to Delta(3.0, 4.2, -95.0, -85.0, 20, -0.9),
                                 2080 to Delta(5.5, 7.8, -150.0, -135.0, 38, -1.5))
        ))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AQUITAINE (Landes, Périgord) — N10, O10
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildRegionAquitaine(): List<ProjectionClimatiqueSerEntity> {
        val serList = listOf("N10" to "Landes de Gascogne", "O10" to "Périgord-Quercy")
        return buildForSers(serList, deltas = mapOf(
            "SSP1-2.6" to mapOf(2050 to Delta(1.4, 1.9, -35.0, -30.0, 7, -0.35),
                                 2080 to Delta(1.8, 2.4, -48.0, -40.0, 9, -0.42)),
            "SSP2-4.5" to mapOf(2050 to Delta(1.9, 2.6, -60.0, -50.0, 11, -0.52),
                                 2080 to Delta(2.8, 3.9, -85.0, -70.0, 19, -0.75)),
            "SSP5-8.5" to mapOf(2050 to Delta(2.5, 3.4, -75.0, -62.0, 15, -0.65),
                                 2080 to Delta(4.6, 6.4, -125.0, -105.0, 31, -1.15))
        ))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Builder générique
    // ──────────────────────────────────────────────────────────────────────────
    private data class Delta(
        val deltaTMoy: Double,
        val deltaTEte: Double,
        val deltaPAn: Double,
        val deltaPEte: Double,
        val joursChauds: Int,
        val speiDelta: Double
    )

    private fun buildForSers(
        serList: List<Pair<String, String>>,
        deltas: Map<String, Map<Int, Delta>>
    ): List<ProjectionClimatiqueSerEntity> {
        val list = mutableListOf<ProjectionClimatiqueSerEntity>()
        for ((codeSer, _) in serList) {
            for ((scenario, horizons) in deltas) {
                for ((horizon, d) in horizons) {
                    list += ProjectionClimatiqueSerEntity(
                        projId = "${codeSer}_${scenario.replace("-","")}_$horizon",
                        codeSer = codeSer,
                        scenario = scenario,
                        horizon = horizon,
                        deltaTMoyC = d.deltaTMoy,
                        deltaTEteC = d.deltaTEte,
                        deltaPMmAn = d.deltaPAn,
                        deltaPEteMm = d.deltaPEte,
                        nbJoursChaudsSup = d.joursChauds,
                        speiDelta = d.speiDelta,
                        sourceGiec = SOURCE
                    )
                }
            }
        }
        return list
    }
}
