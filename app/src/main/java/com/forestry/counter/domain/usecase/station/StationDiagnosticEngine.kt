package com.forestry.counter.domain.usecase.station

import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.model.station.Drainage
import com.forestry.counter.domain.model.station.PositionTopo
import com.forestry.counter.domain.model.station.StationObservation
import com.forestry.counter.domain.model.station.TestHCl
import com.forestry.counter.domain.model.station.TextureSol
import com.forestry.counter.domain.model.station.TypeHumus
import com.forestry.counter.domain.usecase.autecology.AutecologyDatabase
import com.forestry.counter.domain.usecase.autecology.CompatibilityLevel
import com.forestry.counter.domain.usecase.autecology.EssenceAutecology
import com.forestry.counter.domain.usecase.florist.DiagnosticFloristique
import com.forestry.counter.domain.usecase.florist.FloristDatabase
import kotlin.math.abs
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
        val confidence: DiagConfidenceStation,
        val currentEssencesCompatibility: List<EssenceCompatibilityResult> = emptyList()
    )

    data class EssenceCompatibilityResult(
        val essenceName: String,
        val compatibility: CompatibilityLevel,
        val reasons: List<String>
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
        val floristDiag = analyseFloristique(obs)
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
            alertes.add("Incohérence : effervescence HCl forte (sol calcaire) + humus de type mor")
        if (obs.drainage == Drainage.TRES_MAUVAIS && obs.expositionIsSouth())
            alertes.add("Combinaison drainage très mauvais + exposition sud inhabituelle — vérifier les données")
        if (obs.gradientHydrique <= 2 && (obs.drainage == Drainage.MAUVAIS || obs.drainage == Drainage.TRES_MAUVAIS))
            alertes.add("Incohérence : gradient hydrique sec + drainage mauvais")
        if (obs.profondeurSolCm != null && obs.profondeurSolCm < 20 && obs.gradientTrophique >= 4)
            alertes.add("Sol très superficiel (<20 cm) malgré une richesse trophique élevée — vérifier")
        if (obs.altitudeM != null && obs.altitudeM > 1200)
            alertes.add("Altitude > 1 200 m : zone subalpine — les référentiels stationnel et sylvicole standards sont peu adaptés à cette tranche altitudinale. Diagnostic indicatif uniquement.")

        // ── Validation croisée floristique (B3) ──
        floristDiag?.let { fd ->
            val floristH = (fd.gradientHydriqueDeduît * 5.0 / 7.0).toInt().coerceIn(1, 5)
            val floristN = (fd.gradientFertiliteDeduît * 5.0 / 6.0).toInt().coerceIn(1, 5)
            if (abs(floristH - obs.gradientHydrique) >= 2)
                alertes.add("Divergence flore↔gradient hydrique : espèces indicatrices suggèrent H≈$floristH vs saisie ${obs.gradientHydrique}")
            if (abs(floristN - obs.gradientTrophique) >= 2)
                alertes.add("Divergence flore↔gradient trophique : espèces indicatrices suggèrent N≈$floristN vs saisie ${obs.gradientTrophique}")
            if (fd.probabiliteHydromorphie > 0.5)
                alertes.add("Flore indicatrice : hydromorphie probable (${(fd.probabiliteHydromorphie * 100).toInt()}% d'espèces hygrophiles)")
            if (fd.probabilitePerturbation > 0.4)
                alertes.add("Flore perturbée ou nitrophile détectée par les espèces indicatrices")
            if (fd.probabiliteCompaction > 0.4)
                alertes.add("Indicateurs floristiques de compaction du sol détectés")
            if (floristH >= 4 && obs.drainage == Drainage.BON)
                alertes.add("Flore hygrophile présente malgré un drainage déclaré bon — vérifier la nappe")
        }

        // Type de station
        val typeStation = computeTypeStation(obs)
        val risqueEngor = obs.hydromorphieProfondeurCm != null && obs.hydromorphieProfondeurCm < 40
        val risqueDep   = obs.gradientHydrique <= 2 && obs.altitudeM != null && obs.altitudeM < 300

        // Système Expert Autécologique pour la compatibilité des essences présentes
        val compatibilities = mutableListOf<EssenceCompatibilityResult>()
        dendro?.essencesPresentes?.forEach { essCode ->
            val autecology = AutecologyDatabase.getByCodeOrName(essCode)
            if (autecology != null) {
                val res = evaluateCompatibility(autecology, obs)
                compatibilities.add(res)
                if (res.compatibility == CompatibilityLevel.INCOMPATIBLE) {
                    alertes.add("Essence inadaptée à la station : ${autecology.nameFr} (${res.reasons.firstOrNull() ?: "conditions extrêmes"})")
                } else if (res.compatibility == CompatibilityLevel.TOLERATED) {
                    alertes.add("Essence tolérée mais non optimale : ${autecology.nameFr}")
                }
            }
        }

        // Essences recommandées / déconseillées du moteur
        val recommended = mutableListOf<String>()
        val discouraged = mutableListOf<String>()
        computeExpertRecommendations(obs, recommended, discouraged)

        // Confiance
        val confidence = computeConfidence(obs, floristDiag)

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
            confidence           = confidence,
            currentEssencesCompatibility = compatibilities
        )
    }

    // ─────────────────────────────────────────────────────────────────
    //  Système Expert d'Autécologie
    // ─────────────────────────────────────────────────────────────────

    fun evaluateCompatibility(essence: EssenceAutecology, obs: StationObservation): EssenceCompatibilityResult {
        val reasons = mutableListOf<String>()
        var level = CompatibilityLevel.OPTIMUM

        // Hydrique
        if (obs.gradientHydrique < essence.minHydric) {
            level = CompatibilityLevel.INCOMPATIBLE
            reasons.add("Station trop sèche (hydrique ${obs.gradientHydrique} < min ${essence.minHydric})")
        } else if (obs.gradientHydrique > essence.maxHydric) {
            if (!essence.toleratesHydromorphy) {
                level = CompatibilityLevel.INCOMPATIBLE
                reasons.add("Station trop humide/engorgée (hydrique ${obs.gradientHydrique} > max ${essence.maxHydric})")
            } else {
                if (level == CompatibilityLevel.OPTIMUM) level = CompatibilityLevel.TOLERATED
                reasons.add("Station humide (tolérée)")
            }
        } else if (obs.gradientHydrique == essence.minHydric || obs.gradientHydrique == essence.maxHydric) {
            if (level == CompatibilityLevel.OPTIMUM) level = CompatibilityLevel.TOLERATED
            reasons.add("En limite de tolérance hydrique")
        }

        // Trophique
        if (obs.gradientTrophique < essence.minTrophic) {
            level = CompatibilityLevel.INCOMPATIBLE
            reasons.add("Station trop pauvre/acide (trophique ${obs.gradientTrophique} < min ${essence.minTrophic})")
        } else if (obs.gradientTrophique > essence.maxTrophic) {
            level = CompatibilityLevel.INCOMPATIBLE
            reasons.add("Station trop riche/basique (trophique ${obs.gradientTrophique} > max ${essence.maxTrophic})")
        } else if (obs.gradientTrophique == essence.minTrophic || obs.gradientTrophique == essence.maxTrophic) {
            if (level == CompatibilityLevel.OPTIMUM) level = CompatibilityLevel.TOLERATED
            reasons.add("En limite de tolérance trophique")
        }

        // Altitude
        if (essence.maxAltitude != null && obs.altitudeM != null && obs.altitudeM > essence.maxAltitude) {
            level = CompatibilityLevel.INCOMPATIBLE
            reasons.add("Altitude trop élevée (${obs.altitudeM}m > max ${essence.maxAltitude}m)")
        }

        // Hydromorphie et drainage
        if ((obs.drainage == Drainage.MAUVAIS || obs.drainage == Drainage.TRES_MAUVAIS) && !essence.toleratesHydromorphy) {
            level = CompatibilityLevel.INCOMPATIBLE
            reasons.add("Ne tolère pas le mauvais drainage / l'asphyxie")
        }

        return EssenceCompatibilityResult(essence.nameFr, level, reasons)
    }

    private fun computeExpertRecommendations(obs: StationObservation, recommended: MutableList<String>, discouraged: MutableList<String>) {
        AutecologyDatabase.species.forEach { ess ->
            val res = evaluateCompatibility(ess, obs)
            if (res.compatibility == CompatibilityLevel.OPTIMUM) {
                recommended.add(ess.nameFr)
            } else if (res.compatibility == CompatibilityLevel.INCOMPATIBLE) {
                discouraged.add(ess.nameFr)
            }
        }
        
        // Trier et limiter pour la lisibilité
        val sortedRecommended = recommended.sorted().take(5)
        recommended.clear()
        recommended.addAll(sortedRecommended)
        
        val sortedDiscouraged = discouraged.sorted().take(5)
        discouraged.clear()
        discouraged.addAll(sortedDiscouraged)
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
        obs.gradientTrophique == 3 -> Contrainte.MODEREE
        obs.gradientTrophique == 4 -> Contrainte.FAIBLE
        else                       -> Contrainte.NULLE
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
        val isCalcaire = obs.testHcl == TestHCl.TRES_FORT || obs.testHcl == TestHCl.FORT

        return when {
            hydro >= 5 && pos == PositionTopo.VALLON  -> "Station hygrophile de fond de vallon — engorgement fréquent"
            hydro >= 5                                -> "Station très humide — engorgement probable"
            hydro >= 4 && tro >= 4                   -> "Station fraîche eutrophe — favorable aux essences mésophiles exigeantes"
            hydro >= 4 && tro <= 2                   -> "Station hygro-oligotrophe — tourbière ou lande humide"
            hydro <= 1                               -> "Station xérique extrême — contrainte hydrique majeure"
            hydro <= 2 && obs.expositionIsSouth() && prof < 40 -> "Station sèche sur sol superficiel exposé Sud — contrainte forte"
            hydro <= 2 && tro >= 3                   -> "Station sèche mésotrophe — stress estival, essences xérophiles conseillées"
            hydro <= 2 && tro <= 2                   -> "Station xéro-oligotrophe — lande sèche, production très limitée"
            tro <= 1                                 -> "Station oligotrophe acide — milieu très pauvre"
            tro <= 2                                 -> "Station oligotrophe — sol pauvre, production limitée"
            isCalcaire && tro >= 3                   -> "Station calcaire mésotrophe à eutrophe — calcicole typique"
            pos == PositionTopo.CRETE                -> "Station de crête — sol souvent superficiel et exposé au vent"
            pos == PositionTopo.HAUT_VERSANT && hydro <= 2 -> "Station de haut versant sec — drainage excessif"
            hydro == 3 && tro in 3..4 && prof >= 60  -> "Station mésophile équilibrée — conditions favorables à la production"
            hydro == 3 && tro == 5                   -> "Station mésophile eutrophe — très fertile, potentiel élevé"
            else -> "Station mésophile — conditions intermédiaires"
        }
    }

    private fun analyseFloristique(obs: StationObservation): DiagnosticFloristique? {
        if (obs.especesIndicatrices.isEmpty()) return null
        val ids = obs.especesIndicatrices
            .filter { it.length >= 3 }
            .mapNotNull { nom -> FloristDatabase.findByNomFrancais(nom)?.id }
        if (ids.isEmpty()) return null
        return FloristDatabase.diagnostiquerFlore(ids)
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

    private fun computeConfidence(obs: StationObservation, floristDiag: DiagnosticFloristique?): DiagConfidenceStation {
        var score = 0
        if (obs.profondeurSolCm != null) score++
        if (obs.texture != TextureSol.INCONNUE) score++
        if (obs.altitudeM != null) score++
        if (obs.pentePct != null) score++
        if (obs.phEstime != null) score++
        if (obs.especesIndicatrices.isNotEmpty()) score++
        if (obs.rocheMere.isNotBlank()) score++
        if (floristDiag != null) score += 2  // espèces identifiées dans la DB floristique
        return when {
            score >= 6 -> DiagConfidenceStation.FORTE
            score >= 3 -> DiagConfidenceStation.MOYENNE
            else -> DiagConfidenceStation.FAIBLE
        }
    }
}
