package com.forestry.counter.domain.usecase.station

import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.model.station.AbondanceDominance
import com.forestry.counter.domain.model.station.BiodiversiteData
import com.forestry.counter.domain.model.station.Drainage
import com.forestry.counter.domain.model.station.FloraEntry
import com.forestry.counter.domain.model.station.Pierrosite
import com.forestry.counter.domain.model.station.PositionTopo
import com.forestry.counter.domain.model.station.SoilHorizon
import com.forestry.counter.domain.model.station.StationObservation
import com.forestry.counter.domain.model.station.StrateVegetale
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

    // ─────────────────────────────────────────────────────────────────
    //  Calcul RUM (Réserve Utile en eau du sol)
    // ─────────────────────────────────────────────────────────────────

    data class RumResult(
        val rumMm: Int,
        val profondeurUtileCm: Int,
        val risqueSecheresse: Boolean,
        val risqueEngorgement: Boolean,
        val facteursFavorables: List<String>,
        val facteursLimitants: List<String>
    )

    fun computeRum(obs: StationObservation): RumResult {
        val horizons = obs.horizons
        val atouts   = mutableListOf<String>()
        val limites  = mutableListOf<String>()

        // RU capacity (mm/cm) per texture
        fun ruCoeff(tex: TextureSol): Double = when (tex) {
            TextureSol.SABLEUSE          -> 0.7
            TextureSol.LIMONO_SABLEUSE   -> 1.0
            TextureSol.GRAVELEUSE        -> 0.6
            TextureSol.LIMONEUSE         -> 1.4
            TextureSol.ARGILO_LIMONEUSE  -> 1.5
            TextureSol.ARGILO_SABLEUSE   -> 1.2
            TextureSol.ARGILEUSE         -> 1.1
            TextureSol.INCONNUE          -> 1.0
        }

        // Pierrosité correction factor
        fun pierroCoeff(pct: Int): Double = 1.0 - (pct.coerceIn(0, 90) / 100.0)

        val rumMm: Int
        val profUtileCm: Int

        if (horizons.isNotEmpty()) {
            var total = 0.0
            var profMax = 0
            horizons.forEach { h ->
                val thickness = (h.depthToCm - h.depthFromCm).coerceAtLeast(0)
                val ru = ruCoeff(h.texture) * thickness * pierroCoeff(h.elemsGrossiersPct)
                total += ru
                profMax = maxOf(profMax, h.depthToCm)
                if (h.hydromorphieSigns) limites.add("Traces d'hydromorphie à ${h.depthFromCm}–${h.depthToCm} cm (horizon ${h.label})")
                if (h.elemsGrossiersPct > 40) limites.add("Charge en éléments grossiers > 40 % à l'horizon ${h.label} — RU réduite")
            }
            rumMm = total.toInt()
            profUtileCm = profMax
        } else {
            // Fallback to simple formula from single-layer fields
            val depth   = (obs.profondeurSolCm ?: 0).coerceAtLeast(0)
            val pierro  = when (obs.pierrosite) {
                Pierrosite.NULLE      -> 0
                Pierrosite.FAIBLE     -> 5
                Pierrosite.MOYENNE    -> 20
                Pierrosite.FORTE      -> 45
                Pierrosite.TRES_FORTE -> 70
            }
            rumMm    = (ruCoeff(obs.texture) * depth * pierroCoeff(pierro)).toInt()
            profUtileCm = depth
        }

        val risqueSec = rumMm < 60
        val risqueEngor = obs.hydromorphieProfondeurCm != null && obs.hydromorphieProfondeurCm < 40 ||
                          horizons.any { it.hydromorphieSigns && it.depthFromCm < 40 }

        if (rumMm >= 150) atouts.add("RU élevée (${rumMm} mm) — bonne réserve hydrique estivale")
        else if (rumMm in 80..149) atouts.add("RU satisfaisante (${rumMm} mm)")
        if (profUtileCm >= 80) atouts.add("Sol profond (${profUtileCm} cm)")
        if (risqueSec) limites.add("RU faible (${rumMm} mm < 60 mm) — risque de sécheresse estivale")
        if (risqueEngor) limites.add("Engorgement temporaire probable (taches < 40 cm)")

        return RumResult(rumMm, profUtileCm, risqueSec, risqueEngor, atouts, limites)
    }

    // ─────────────────────────────────────────────────────────────────
    //  Conclusion floristique intermédiaire (par strates)
    // ─────────────────────────────────────────────────────────────────

    data class ConclusionFlore(
        val nbSpecies: Int,
        val nbPerStrate: Map<StrateVegetale, Int>,
        val gradientHydriqueFlore: Int?,
        val gradientTrophiqueFlore: Int?,
        val habitatPresume: String,
        val richesseLabel: String,
        val coherenceAvecSol: String?
    )

    fun concludeFlore(obs: StationObservation): ConclusionFlore {
        val entries = obs.floraEntries.ifEmpty {
            // backward compat: wrap flat list as HERBACEE UN
            obs.especesIndicatrices.map { FloraEntry(it, it, StrateVegetale.HERBACEE, AbondanceDominance.UN) }
        }

        val nbPerStrate = StrateVegetale.entries.associateWith { s -> entries.count { it.strate == s } }
        val floristDiag = analyseFloristique(obs)

        val gH = floristDiag?.let { (it.gradientHydriqueDeduît * 5.0 / 7.0).toInt().coerceIn(1, 5) }
        val gT = floristDiag?.let { (it.gradientFertiliteDeduît * 5.0 / 6.0).toInt().coerceIn(1, 5) }

        val habitat = when {
            gH != null && gH >= 5 -> "Milieu hygrophile / aulnaie-frênaie"
            gH != null && gH >= 4 && gT != null && gT >= 4 -> "Hêtraie-charmaie fraîche eutrophe"
            gH != null && gH <= 2 -> "Pelouse sèche / chênaie pubescente"
            gT != null && gT <= 2 -> "Milieu oligotrophe acide / boulaie"
            gT != null && gT >= 4 -> "Milieu eutrophe / frênaie-ormaie"
            else -> "Chênaie-hêtraie mésophile"
        }

        val richesse = when {
            entries.size >= 20 -> "Station riche (≥ 20 espèces)"
            entries.size >= 10 -> "Station mésotrophe (10–19 espèces)"
            entries.size >= 5  -> "Station pauvre (5–9 espèces)"
            entries.isNotEmpty() -> "Relevé insuffisant (< 5 espèces)"
            else -> "Aucune espèce renseignée"
        }

        val coherence = when {
            gH != null && obs.gradientHydrique > 0 && abs(gH - obs.gradientHydrique) >= 2 ->
                "⚠ Divergence flore/sol : gradient hydrique flore ≈ $gH vs sol ${obs.gradientHydrique}"
            gT != null && obs.gradientTrophique > 0 && abs(gT - obs.gradientTrophique) >= 2 ->
                "⚠ Divergence flore/sol : gradient trophique flore ≈ $gT vs sol ${obs.gradientTrophique}"
            else -> null
        }

        return ConclusionFlore(entries.size, nbPerStrate, gH, gT, habitat, richesse, coherence)
    }

    // ─────────────────────────────────────────────────────────────────
    //  Conclusion sol intermédiaire
    // ─────────────────────────────────────────────────────────────────

    data class ConclusionSol(
        val rum: RumResult,
        val typeHumusLabel: String,
        val facteursFavorables: List<String>,
        val facteursLimitants: List<String>,
        val pointsAVerifier: List<String>
    )

    fun concludeSol(obs: StationObservation): ConclusionSol {
        val rum = computeRum(obs)
        val atouts  = rum.facteursFavorables.toMutableList()
        val limites = rum.facteursLimitants.toMutableList()
        val verif   = mutableListOf<String>()

        when (obs.humus) {
            TypeHumus.MULL, TypeHumus.MULL_CALCIQUE ->
                atouts.add("Humus actif (${obs.humus.labelFr}) — minéralisation rapide")
            TypeHumus.MOR ->
                limites.add("Humus de type mor — minéralisation très lente, acidification")
            TypeHumus.MODER ->
                limites.add("Humus moder — activité biologique réduite")
            else -> verif.add("Type d'humus à préciser")
        }

        when (obs.drainage) {
            Drainage.BON, Drainage.NORMAL -> atouts.add("Drainage favorable")
            Drainage.MAUVAIS, Drainage.TRES_MAUVAIS -> limites.add("Mauvais drainage — risque d'asphyxie racinaire")
            Drainage.IMPARFAIT -> verif.add("Drainage imparfait à surveiller en période pluvieuse")
            else -> {}
        }

        if (obs.testHcl == TestHCl.TRES_FORT || obs.testHcl == TestHCl.FORT)
            atouts.add("Sol calcaire (HCl ${obs.testHcl.labelFr}) — bonne disponibilité en calcium")

        if (obs.rocheMere.isNotBlank())
            atouts.add("Roche mère identifiée : ${obs.rocheMere}")
        else
            verif.add("Roche mère à caractériser (relation sol/substrat)")

        if (obs.horizons.isEmpty())
            verif.add("Profil pédologique incomplet — saisir les horizons pour affiner la RU")

        return ConclusionSol(rum, obs.humus.labelFr, atouts, limites, verif)
    }

    // ─────────────────────────────────────────────────────────────────
    //  Conclusion biodiversité
    // ─────────────────────────────────────────────────────────────────

    data class ConclusionBiodiversite(
        val niveau: String,
        val points: List<String>
    )

    fun concludeBiodiversite(bio: BiodiversiteData): ConclusionBiodiversite {
        val pts = mutableListOf<String>()
        val vol = bio.boisMortSolVolM3
        val nb  = bio.boisMortDeboutNb

        if (vol != null && vol >= 20) pts.add("Bois mort au sol abondant (≥ 20 m³/ha) — habitat xylophage riche")
        else if (vol != null && vol >= 5) pts.add("Bois mort au sol présent (${vol} m³/ha)")
        else if (vol != null) pts.add("Bois mort au sol faible (${vol} m³/ha) — à augmenter")

        if (nb != null && nb >= 5) pts.add("Bois mort debout abondant (${nb} stipes/ha) — fréquentation des pics probable")
        else if (nb != null && nb > 0) pts.add("Quelques arbres morts debout (${nb}/ha)")

        if (bio.microHabitats.size >= 4) pts.add("Richesse en micro-habitats élevée (${bio.microHabitats.size} types)")
        else if (bio.microHabitats.isNotEmpty()) pts.add("${bio.microHabitats.size} micro-habitat(s) : ${bio.microHabitats.take(3).joinToString { it.labelFr }}")

        if (bio.tracesGibier) pts.add("Traces de gibier observées")

        val niveau = when {
            pts.size >= 4 -> "Biodiversité élevée"
            pts.size >= 2 -> "Biodiversité modérée"
            pts.isNotEmpty() -> "Biodiversité faible"
            else -> "Biodiversité non évaluée"
        }
        return ConclusionBiodiversite(niveau, pts)
    }

    private fun computeConfidence(obs: StationObservation, floristDiag: DiagnosticFloristique?): DiagConfidenceStation {
        var score = 0
        if (obs.profondeurSolCm != null) score++
        if (obs.texture != TextureSol.INCONNUE) score++
        if (obs.altitudeM != null) score++
        if (obs.pentePct != null) score++
        if (obs.phEstime != null) score++
        if (obs.especesIndicatrices.isNotEmpty() || obs.floraEntries.isNotEmpty()) score++
        if (obs.floraEntries.size >= 5) score++   // relevé structuré par strates
        if (obs.horizons.isNotEmpty()) score++     // profil multi-horizons
        if (obs.rocheMere.isNotBlank()) score++
        if (floristDiag != null) score += 2  // espèces identifiées dans la DB floristique
        return when {
            score >= 6 -> DiagConfidenceStation.FORTE
            score >= 3 -> DiagConfidenceStation.MOYENNE
            else -> DiagConfidenceStation.FAIBLE
        }
    }
}
