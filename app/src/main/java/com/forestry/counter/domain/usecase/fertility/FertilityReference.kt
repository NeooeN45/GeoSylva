package com.forestry.counter.domain.usecase.fertility

import com.forestry.counter.domain.model.ClimateZone

/**
 * Classe de fertilité forestière I (très bon) → IV (faible).
 * Basé sur les guides de sylviculture ONF/CNPF français.
 */
enum class FertilityClass(val roman: String, val label: String, val color: Long) {
    I("I", "Très bon", 0xFF2E7D32L),
    II("II", "Bon", 0xFF558B2FL),
    III("III", "Moyen", 0xFFEF6C00L),
    IV("IV", "Faible", 0xFFC62828L),
    UNKNOWN("?", "Indéterminé", 0xFF757575L)
}

/**
 * Âge de référence utilisé pour le site index (H50 ou H100 selon l'essence).
 */
enum class ReferenceAge(val years: Int) { AGE_50(50), AGE_100(100) }

/**
 * Référentiel de fertilité pour une essence :
 * - thresholds : liste de (minHeight, FertilityClass) triée du meilleur au moins bon
 * - referenceAge : H50 ou H100
 * - preferredZones : zones climatiques de prédilection
 * - typicalHarvestDiam : diamètre d'exploitation typique (cm) — sert à l'estimation sans âge
 * - expectedHAtHarvestByCls : hauteur attendue au diamètre d'exploitation par classe I..IV
 *
 * Sources : ONF guides de sylviculture, CNPF, IFN France, CRPF
 */
data class SpeciesFertilityRef(
    val essenceCodes: List<String>,   // codes correspondants dans l'app
    val commonName: String,
    val refAge: ReferenceAge,
    val thresholds: List<Pair<Double, FertilityClass>>, // (minH, class) desc order
    val preferredZones: List<ClimateZone>,
    val typicalHarvestDiamCm: Double,
    val expectedHAtHarvestByCls: Map<FertilityClass, Double> // dominant H at harvest diam
)

/**
 * Référentiels pour les principales essences productives françaises.
 * Données issues de :
 * - Guides de sylviculture ONF (hêtre, chêne, douglas, sapin, épicéa)
 * - Guides CNPF/CRPF (pin maritime, pin sylvestre, châtaignier)
 * - Tables IFN (tarifs par essence)
 * - Plaquettes FCBA (mélèze, pin laricio)
 */
object FertilityReference {

    val ALL: List<SpeciesFertilityRef> = listOf(

        // ── HÊTRE (Fagus sylvatica) ──
        // Ref: Guide sylviculture hêtre, ONF 2012 — H100
        // C-DOM-2: seuils corrigés selon ONF 2012 (classe I = 32-35m, pas 28m)
        SpeciesFertilityRef(
            essenceCodes = listOf("HETRE", "FAGUS", "HET", "FB"),
            commonName = "Hêtre",
            refAge = ReferenceAge.AGE_100,
            thresholds = listOf(
                32.0 to FertilityClass.I,
                26.0 to FertilityClass.II,
                20.0 to FertilityClass.III,
                0.0  to FertilityClass.IV
            ),
            preferredZones = listOf(ClimateZone.ATLANTIQUE, ClimateZone.SEMI_OCEANIQUE, ClimateZone.MONTAGNARDE),
            typicalHarvestDiamCm = 55.0,
            expectedHAtHarvestByCls = mapOf(
                FertilityClass.I   to 36.0,
                FertilityClass.II  to 30.0,
                FertilityClass.III to 24.0,
                FertilityClass.IV  to 18.0
            )
        ),

        // ── CHÊNE SESSILE / PÉDONCULÉ (Quercus petraea / robur) ──
        // Ref: Guide sylviculture chêne, ONF 2006 — H100
        // C-DOM-2: seuils corrigés selon ONF 2006 (classe I = 28-30m, pas 25m)
        SpeciesFertilityRef(
            essenceCodes = listOf("CHENE_SESSILE", "CHENE_PEDONCULE", "QUERCUS", "CHE", "CHS", "CHP", "QP", "QR"),
            commonName = "Chêne",
            refAge = ReferenceAge.AGE_100,
            thresholds = listOf(
                28.0 to FertilityClass.I,
                22.0 to FertilityClass.II,
                16.0 to FertilityClass.III,
                0.0  to FertilityClass.IV
            ),
            preferredZones = listOf(ClimateZone.ATLANTIQUE, ClimateZone.SEMI_OCEANIQUE, ClimateZone.CONTINENTALE),
            typicalHarvestDiamCm = 60.0,
            expectedHAtHarvestByCls = mapOf(
                FertilityClass.I   to 32.0,
                FertilityClass.II  to 26.0,
                FertilityClass.III to 20.0,
                FertilityClass.IV  to 15.0
            )
        ),

        // ── DOUGLAS VERT (Pseudotsuga menziesii) ──
        // Ref: Guide sylviculture douglas, CRPF 2015 — H50
        SpeciesFertilityRef(
            essenceCodes = listOf("DOUGLAS_VERT", "DOUGLAS", "PSEUDOTSUGA", "DGL", "PS"),
            commonName = "Douglas",
            refAge = ReferenceAge.AGE_50,
            thresholds = listOf(
                33.0 to FertilityClass.I,
                26.0 to FertilityClass.II,
                20.0 to FertilityClass.III,
                0.0  to FertilityClass.IV
            ),
            preferredZones = listOf(ClimateZone.ATLANTIQUE, ClimateZone.SEMI_OCEANIQUE, ClimateZone.MONTAGNARDE),
            typicalHarvestDiamCm = 50.0,
            expectedHAtHarvestByCls = mapOf(
                FertilityClass.I   to 42.0,
                FertilityClass.II  to 35.0,
                FertilityClass.III to 28.0,
                FertilityClass.IV  to 22.0
            )
        ),

        // ── SAPIN PECTINÉ (Abies alba) ──
        // Ref: ONF — tarifs Massif Central/Vosges — H50
        SpeciesFertilityRef(
            essenceCodes = listOf("SAPIN_PECTINE", "SAPIN", "ABIES", "SAP", "AB"),
            commonName = "Sapin pectiné",
            refAge = ReferenceAge.AGE_50,
            thresholds = listOf(
                24.0 to FertilityClass.I,
                18.0 to FertilityClass.II,
                12.0 to FertilityClass.III,
                0.0  to FertilityClass.IV
            ),
            preferredZones = listOf(ClimateZone.MONTAGNARDE, ClimateZone.SEMI_OCEANIQUE),
            typicalHarvestDiamCm = 45.0,
            expectedHAtHarvestByCls = mapOf(
                FertilityClass.I   to 32.0,
                FertilityClass.II  to 26.0,
                FertilityClass.III to 20.0,
                FertilityClass.IV  to 14.0
            )
        ),

        // ── ÉPICÉA COMMUN (Picea abies) ──
        // Ref: ONF guide épicéa — H50
        SpeciesFertilityRef(
            essenceCodes = listOf("EPICEA_COMMUN", "EPICEA", "PICEA", "EPC", "PA"),
            commonName = "Épicéa commun",
            refAge = ReferenceAge.AGE_50,
            thresholds = listOf(
                28.0 to FertilityClass.I,
                22.0 to FertilityClass.II,
                16.0 to FertilityClass.III,
                0.0  to FertilityClass.IV
            ),
            preferredZones = listOf(ClimateZone.MONTAGNARDE, ClimateZone.CONTINENTALE),
            typicalHarvestDiamCm = 40.0,
            expectedHAtHarvestByCls = mapOf(
                FertilityClass.I   to 35.0,
                FertilityClass.II  to 28.0,
                FertilityClass.III to 22.0,
                FertilityClass.IV  to 16.0
            )
        ),

        // ── PIN SYLVESTRE (Pinus sylvestris) ──
        // Ref: IFN tables pin sylvestre — H100
        SpeciesFertilityRef(
            essenceCodes = listOf("PIN_SYLVESTRE", "PINUS_SYLVESTRIS", "PSY", "PS"),
            commonName = "Pin sylvestre",
            refAge = ReferenceAge.AGE_100,
            thresholds = listOf(
                22.0 to FertilityClass.I,
                16.0 to FertilityClass.II,
                11.0 to FertilityClass.III,
                0.0  to FertilityClass.IV
            ),
            preferredZones = listOf(ClimateZone.CONTINENTALE, ClimateZone.SEMI_OCEANIQUE, ClimateZone.MONTAGNARDE),
            typicalHarvestDiamCm = 35.0,
            expectedHAtHarvestByCls = mapOf(
                FertilityClass.I   to 25.0,
                FertilityClass.II  to 20.0,
                FertilityClass.III to 15.0,
                FertilityClass.IV  to 10.0
            )
        ),

        // ── PIN MARITIME (Pinus pinaster) ──
        // Ref: CRPF Aquitaine, tables Landes de Gascogne — H50
        SpeciesFertilityRef(
            essenceCodes = listOf("PIN_MARITIME", "PINUS_PINASTER", "PM", "PIM"),
            commonName = "Pin maritime",
            refAge = ReferenceAge.AGE_50,
            thresholds = listOf(
                24.0 to FertilityClass.I,
                18.0 to FertilityClass.II,
                12.0 to FertilityClass.III,
                0.0  to FertilityClass.IV
            ),
            preferredZones = listOf(ClimateZone.ATLANTIQUE, ClimateZone.MEDITERRANEENNE),
            typicalHarvestDiamCm = 30.0,
            expectedHAtHarvestByCls = mapOf(
                FertilityClass.I   to 28.0,
                FertilityClass.II  to 22.0,
                FertilityClass.III to 16.0,
                FertilityClass.IV  to 11.0
            )
        ),

        // ── PIN LARICIO (Pinus nigra laricio) ──
        // Ref: FCBA fiches techniques — H50
        SpeciesFertilityRef(
            essenceCodes = listOf("PIN_LARICIO", "PINUS_NIGRA", "PL", "PLR"),
            commonName = "Pin laricio",
            refAge = ReferenceAge.AGE_50,
            thresholds = listOf(
                26.0 to FertilityClass.I,
                20.0 to FertilityClass.II,
                14.0 to FertilityClass.III,
                0.0  to FertilityClass.IV
            ),
            preferredZones = listOf(ClimateZone.MEDITERRANEENNE, ClimateZone.SEMI_OCEANIQUE),
            typicalHarvestDiamCm = 40.0,
            expectedHAtHarvestByCls = mapOf(
                FertilityClass.I   to 32.0,
                FertilityClass.II  to 26.0,
                FertilityClass.III to 20.0,
                FertilityClass.IV  to 14.0
            )
        ),

        // ── MÉLÈZE D'EUROPE (Larix decidua) ──
        // Ref: ONF guide mélèze, Alpes — H100
        SpeciesFertilityRef(
            essenceCodes = listOf("MELEZE", "LARIX_DECIDUA", "MEL", "LD"),
            commonName = "Mélèze d'Europe",
            refAge = ReferenceAge.AGE_100,
            thresholds = listOf(
                28.0 to FertilityClass.I,
                22.0 to FertilityClass.II,
                16.0 to FertilityClass.III,
                0.0  to FertilityClass.IV
            ),
            preferredZones = listOf(ClimateZone.MONTAGNARDE),
            typicalHarvestDiamCm = 45.0,
            expectedHAtHarvestByCls = mapOf(
                FertilityClass.I   to 34.0,
                FertilityClass.II  to 27.0,
                FertilityClass.III to 21.0,
                FertilityClass.IV  to 16.0
            )
        ),

        // ── CHÂTAIGNIER (Castanea sativa) ──
        // Ref: CRPF Centre/Bretagne, fiches châtaignier — H50
        SpeciesFertilityRef(
            essenceCodes = listOf("CHATAIGNIER", "CASTANEA", "CHA", "CS"),
            commonName = "Châtaignier",
            refAge = ReferenceAge.AGE_50,
            thresholds = listOf(
                18.0 to FertilityClass.I,
                14.0 to FertilityClass.II,
                10.0 to FertilityClass.III,
                0.0  to FertilityClass.IV
            ),
            preferredZones = listOf(ClimateZone.ATLANTIQUE, ClimateZone.SEMI_OCEANIQUE),
            typicalHarvestDiamCm = 35.0,
            expectedHAtHarvestByCls = mapOf(
                FertilityClass.I   to 22.0,
                FertilityClass.II  to 17.0,
                FertilityClass.III to 13.0,
                FertilityClass.IV  to 9.0
            )
        ),

        // ── FRÊNE COMMUN (Fraxinus excelsior) ──
        // Ref: ONF fiche frêne — H50
        SpeciesFertilityRef(
            essenceCodes = listOf("FRENE_COMMUN", "FRAXINUS", "FRE", "FX"),
            commonName = "Frêne commun",
            refAge = ReferenceAge.AGE_50,
            thresholds = listOf(
                22.0 to FertilityClass.I,
                17.0 to FertilityClass.II,
                12.0 to FertilityClass.III,
                0.0  to FertilityClass.IV
            ),
            preferredZones = listOf(ClimateZone.ATLANTIQUE, ClimateZone.SEMI_OCEANIQUE, ClimateZone.CONTINENTALE),
            typicalHarvestDiamCm = 45.0,
            expectedHAtHarvestByCls = mapOf(
                FertilityClass.I   to 28.0,
                FertilityClass.II  to 23.0,
                FertilityClass.III to 18.0,
                FertilityClass.IV  to 13.0
            )
        ),

        // ── SAPIN DE GRANDIS (Abies grandis) ──
        // Ref: FCBA fiche grandis — H50
        SpeciesFertilityRef(
            essenceCodes = listOf("SAPIN_GRANDIS", "ABIES_GRANDIS", "AG", "SGR"),
            commonName = "Sapin de Grandis",
            refAge = ReferenceAge.AGE_50,
            thresholds = listOf(
                30.0 to FertilityClass.I,
                24.0 to FertilityClass.II,
                18.0 to FertilityClass.III,
                0.0  to FertilityClass.IV
            ),
            preferredZones = listOf(ClimateZone.ATLANTIQUE, ClimateZone.SEMI_OCEANIQUE, ClimateZone.MONTAGNARDE),
            typicalHarvestDiamCm = 50.0,
            expectedHAtHarvestByCls = mapOf(
                FertilityClass.I   to 40.0,
                FertilityClass.II  to 32.0,
                FertilityClass.III to 25.0,
                FertilityClass.IV  to 18.0
            )
        )
    )

    /** Trouve le référentiel correspondant à un code essence (insensible à la casse). */
    fun forCode(code: String): SpeciesFertilityRef? {
        val upper = code.uppercase()
        return ALL.firstOrNull { ref -> ref.essenceCodes.any { it.uppercase() == upper } }
    }
}
