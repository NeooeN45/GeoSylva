package com.forestry.counter.domain.usecase.station

import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.model.station.Drainage
import com.forestry.counter.domain.model.station.PositionTopo
import com.forestry.counter.domain.model.station.StationObservation
import com.forestry.counter.domain.model.station.TestHCl
import com.forestry.counter.domain.model.station.TextureSol
import com.forestry.counter.domain.model.station.TypeHumus
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Moteur de diagnostic stationnel expert.
 * Combine données GPS, dendro, pédologie et botanique pour produire
 * un diagnostic station + recommandations.
 */
object StationDiagnosticEngine {

    // ─────────────────────────────────────────────────────────────────
    //  Résultat de diagnostic
    // ─────────────────────────────────────────────────────────────────

    data class StationResult(
        val typeStation: String,
        val gradientHydriqueFinal: Int,
        val gradientTrophiqueFinal: Int,
        val contrainteHydrique: Contrainte,
        val contrainteTrophique: Contrainte,
        val contrainteProfondeur: Contrainte,
        val risqueDepiecement: Boolean,
        val risqueEngorgement: Boolean,
        val atouts: List<String>,
        val contraintes: List<String>,
        val alertes: List<String>,
        val recommendedEssences: List<String>,
        val discouragedEssences: List<String>,
        val syntheseTextuelle: String,
        val confidence: DiagConfidenceStation
    )

    data class DendroContext(
        val nbTiges: Int,
        val dg: Double?,        // diamètre de tige de surface terrière
        val gHa: Double?,       // surface terrière (m²/ha)
        val hmMoyen: Double?,   // hauteur moyenne
        val hdDominante: Double?,
        val hd: Double?,
        val slenderness: Double?,   // H/D
        val sPct: Double?,          // coefficient d'espacement %
        val classesDiam: Set<String>,
        val essencesPresentes: List<String>
    )

    enum class Contrainte { NULLE, FAIBLE, MODEREE, FORTE, TRES_FORTE }
    enum class DiagConfidenceStation(val labelFr: String) {
        FORTE("Forte"), MOYENNE("Moyenne"), FAIBLE("Faible – données incomplètes")
    }

    // ─────────────────────────────────────────────────────────────────
    //  Calcul dendro automatique depuis les tiges
    // ─────────────────────────────────────────────────────────────────

    fun computeDendroContext(tiges: List<Tige>, surfaceHa: Double = 1.0): DendroContext {
        if (tiges.isEmpty()) return DendroContext(0, null, null, null, null, null, null, null, emptySet(), emptyList())

        val n = tiges.size
        val g = tiges.sumOf { (it.diamCm / 200.0).pow(2) * Math.PI }
        val gHa = g / surfaceHa
        val dg = if (n > 0) sqrt(g / n / Math.PI) * 200.0 else null
        val heights = tiges.mapNotNull { it.hauteurM }
        val hmMoyen = if (heights.isNotEmpty()) heights.average() else null
        val topTiges = tiges.sortedByDescending { it.diamCm }.take(maxOf(1, n / 5))
        val hd = topTiges.mapNotNull { it.hauteurM }.average().takeIf { topTiges.any { t -> t.hauteurM != null } }
        val slenderness = if (hd != null && dg != null && dg > 0) (hd * 100) / dg else null
        val sPct = if (hd != null && surfaceHa > 0) {
            val spacing = sqrt(10000.0 / n)
            (spacing / hd) * 100
        } else null

        val classesDiam = mutableSetOf<String>()
        tiges.forEach { t ->
            when {
                t.diamCm <= 7  -> classesDiam.add("Très petit bois (≤7cm)")
                t.diamCm < 20  -> classesDiam.add("Petit bois (7–20cm)")
                t.diamCm < 40  -> classesDiam.add("Moyen bois (20–40cm)")
                else           -> classesDiam.add("Gros bois (≥40cm)")
            }
        }
        val essences = tiges.map { it.essenceCode.uppercase() }.distinct()

        return DendroContext(n, dg, gHa, hmMoyen, hd, hd, slenderness, sPct, classesDiam, essences)
    }

    // ─────────────────────────────────────────────────────────────────
    //  Diagnostic principal
    // ─────────────────────────────────────────────────────────────────

    fun diagnose(obs: StationObservation, dendro: DendroContext? = null): StationResult {
        val contraH  = contrainteHydrique(obs)
        val contraT  = contrainteTrophique(obs)
        val contraP  = contrainteProfondeur(obs)
        val atouts   = mutableListOf<String>()
        val contr    = mutableListOf<String>()
        val alertes  = mutableListOf<String>()

        // Atouts
        if (obs.profondeurSolCm != null && obs.profondeurSolCm >= 60)
            atouts.add("Sol profond (${obs.profondeurSolCm} cm) favorable à un bon enracinement")
        if (obs.drainage == Drainage.NORMAL || obs.drainage == Drainage.BON)
            atouts.add("Drainage favorable")
        if (obs.gradientTrophique >= 4)
            atouts.add("Station eutrophe à mésotrophe — richesse trophique satisfaisante")
        if (obs.altitudeM != null && obs.altitudeM < 600)
            atouts.add("Altitude favorable (${obs.altitudeM.toInt()} m)")
        if (obs.positionTopo == PositionTopo.MI_VERSANT || obs.positionTopo == PositionTopo.BAS_VERSANT)
            atouts.add("Position topographique de mi ou bas versant — apports hydriques réguliers")

        // Contraintes
        if (contraP == Contrainte.FORTE || contraP == Contrainte.TRES_FORTE)
            contr.add("Sol peu profond — contrainte racinaire importante")
        if (contraH == Contrainte.FORTE || contraH == Contrainte.TRES_FORTE)
            contr.add("Contrainte hydrique forte — stress estival probable")
        if (obs.drainage == Drainage.MAUVAIS || obs.drainage == Drainage.TRES_MAUVAIS)
            contr.add("Drainage insuffisant — risque d'asphyxie racinaire")
        if (obs.pierrosite.name.startsWith("FORT") || obs.pierrosite.name.startsWith("TRES"))
            contr.add("Pierrosité élevée — RU limitée")

        // Alertes (incohérences)
        if (obs.testHcl == TestHCl.TRES_FORT && obs.humus == TypeHumus.MOR)
            alertes.add("Incohérence détectée : effervescence HCl forte (sol calcaire) + humus de type mor")
        if (obs.drainage == Drainage.TRES_MAUVAIS && obs.expositionIsSouth())
            alertes.add("Combinaison drainage très mauvais + exposition sud inhabituelle — vérifier les données")
        if (obs.gradientHydrique <= 2 && (obs.drainage == Drainage.MAUVAIS || obs.drainage == Drainage.TRES_MAUVAIS))
            alertes.add("Incohérence : gradient hydrique sec + drainage mauvais")
        if (obs.profondeurSolCm != null && obs.profondeurSolCm < 20 && obs.gradientTrophique >= 4)
            alertes.add("Sol très superficiel (<20 cm) malgré une richesse trophique élevée — vérifier")

        // Type de station
        val typeStation = computeTypeStation(obs)
        val risqueEngor = obs.hydromorphieProfondeurCm != null && obs.hydromorphieProfondeurCm < 40
        val risqueDep   = obs.gradientHydrique <= 2 && obs.altitudeM != null && obs.altitudeM < 300

        // Essences recommandées / déconseillées (règles simplifiées)
        val recommended = mutableListOf<String>()
        val discouraged = mutableListOf<String>()
        computeEssenceRecommendations(obs, recommended, discouraged)

        // Confiance
        val confidence = computeConfidence(obs)

        return StationResult(
            typeStation          = typeStation,
            gradientHydriqueFinal  = obs.gradientHydrique,
            gradientTrophiqueFinal = obs.gradientTrophique,
            contrainteHydrique   = contraH,
            contrainteTrophique  = contraT,
            contrainteProfondeur = contraP,
            risqueDepiecement    = risqueDep,
            risqueEngorgement    = risqueEngor,
            atouts               = atouts,
            contraintes          = contr,
            alertes              = alertes,
            recommendedEssences  = recommended,
            discouragedEssences  = discouraged,
            syntheseTextuelle    = generateSynthese(typeStation, atouts, contr, alertes, confidence),
            confidence           = confidence
        )
    }

    // ─────────────────────────────────────────────────────────────────
    //  Helpers internes
    // ─────────────────────────────────────────────────────────────────

    private fun StationObservation.expositionIsSouth(): Boolean =
        exposition.name in listOf("S", "SE", "SO")

    private fun contrainteHydrique(obs: StationObservation): Contrainte = when {
        obs.gradientHydrique <= 1 -> Contrainte.TRES_FORTE
        obs.gradientHydrique == 2 -> Contrainte.FORTE
        obs.gradientHydrique == 3 -> Contrainte.MODEREE
        obs.gradientHydrique == 4 -> Contrainte.FAIBLE
        else -> Contrainte.NULLE
    }

    private fun contrainteTrophique(obs: StationObservation): Contrainte = when {
        obs.gradientTrophique <= 1 -> Contrainte.TRES_FORTE
        obs.gradientTrophique == 2 -> Contrainte.FORTE
        else -> Contrainte.FAIBLE
    }

    private fun contrainteProfondeur(obs: StationObservation): Contrainte {
        val p = obs.profondeurSolCm ?: return Contrainte.MODEREE
        return when {
            p < 20  -> Contrainte.TRES_FORTE
            p < 40  -> Contrainte.FORTE
            p < 60  -> Contrainte.MODEREE
            p < 100 -> Contrainte.FAIBLE
            else    -> Contrainte.NULLE
        }
    }

    private fun computeTypeStation(obs: StationObservation): String {
        val hydro = obs.gradientHydrique
        val tro   = obs.gradientTrophique
        val prof  = obs.profondeurSolCm ?: 60
        val pos   = obs.positionTopo

        return when {
            hydro >= 5 && pos == PositionTopo.VALLON -> "Station humide de fond de vallon — drainage imparfait à mauvais"
            hydro >= 4 && tro >= 4                  -> "Station fraîche eutrophe — favorable aux essences mésophiles exigeantes"
            hydro <= 2 && obs.expositionIsSouth() && prof < 40 -> "Station sèche à très sèche sur sol superficiel — contrainte hydrique forte"
            hydro <= 2 && tro >= 3                  -> "Station sèche mésotrophe — attention aux essences mésophiles"
            tro <= 2                                -> "Station oligotrophe — sol pauvre, production limitée"
            pos == PositionTopo.CRETE               -> "Station de crête — sol souvent superficiel et exposé"
            hydro == 3 && tro in 3..4 && prof >= 60 -> "Station mésophile équilibrée — conditions favorables"
            else -> "Station mésophile — conditions intermédiaires"
        }
    }

    private fun computeEssenceRecommendations(
        obs: StationObservation,
        recommended: MutableList<String>,
        discouraged: MutableList<String>
    ) {
        val h = obs.gradientHydrique
        val t = obs.gradientTrophique
        val p = obs.profondeurSolCm ?: 60
        val isCalcaire = obs.testHcl == TestHCl.TRES_FORT || obs.testHcl == TestHCl.FORT

        when {
            h >= 4 && p >= 60 && t >= 3 -> {
                recommended.addAll(listOf("Aulne glutineux", "Frêne commun", "Peuplier"))
                discouraged.addAll(listOf("Pin sylvestre", "Mélèze", "Sapin pectiné"))
            }
            h <= 2 && t >= 3 -> {
                recommended.addAll(listOf("Chêne pubescent", "Pin sylvestre", "Pin d'Alep"))
                discouraged.addAll(listOf("Épicéa", "Frêne", "Aulne"))
            }
            isCalcaire && t >= 3 -> {
                recommended.addAll(listOf("Chêne sessile", "Hêtre", "Érable sycomore", "Orme"))
                discouraged.addAll(listOf("Douglas", "Châtaignier", "Pin maritime"))
            }
            t >= 4 && h in 2..4 && p >= 60 -> {
                recommended.addAll(listOf("Chêne pédonculé", "Hêtre", "Érable sycomore", "Douglas"))
                discouraged.addAll(listOf("Pin maritime", "Pin sylvestre"))
            }
            t <= 2 -> {
                recommended.addAll(listOf("Pin sylvestre", "Bouleau", "Pin maritime"))
                discouraged.addAll(listOf("Hêtre", "Épicéa de Sitka", "Peuplier"))
            }
            else -> {
                recommended.addAll(listOf("Chêne sessile", "Douglas", "Mélèze d'Europe", "Épicéa commun"))
            }
        }
    }

    private fun generateSynthese(
        typeStation: String,
        atouts: List<String>,
        contraintes: List<String>,
        alertes: List<String>,
        confidence: DiagConfidenceStation
    ): String {
        val sb = StringBuilder()
        sb.append("Station diagnostiquée : $typeStation. ")
        if (atouts.isNotEmpty())
            sb.append("Atouts principaux : ${atouts.take(2).joinToString("; ")}. ")
        if (contraintes.isNotEmpty())
            sb.append("Contraintes : ${contraintes.take(2).joinToString("; ")}. ")
        if (alertes.isNotEmpty())
            sb.append("⚠ Alertes : ${alertes.first()}. ")
        sb.append("Niveau de confiance du diagnostic : ${confidence.labelFr}.")
        return sb.toString()
    }

    private fun computeConfidence(obs: StationObservation): DiagConfidenceStation {
        var score = 0
        if (obs.profondeurSolCm != null) score++
        if (obs.texture != TextureSol.INCONNUE) score++
        if (obs.altitudeM != null) score++
        if (obs.pentePct != null) score++
        if (obs.phEstime != null) score++
        if (obs.especesIndicatrices.isNotEmpty()) score++
        return when {
            score >= 5 -> DiagConfidenceStation.FORTE
            score >= 3 -> DiagConfidenceStation.MOYENNE
            else -> DiagConfidenceStation.FAIBLE
        }
    }
}
