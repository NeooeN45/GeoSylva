package com.forestry.counter.domain.diagnostic

import android.content.Context
import com.forestry.counter.data.local.entity.DiagnosticSylvicoleEntity
import com.forestry.counter.data.local.entity.StationEnvironnementaleEntity
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.repository.DiagnosticSylvicoleRepository
import com.forestry.counter.domain.repository.StationEnvironnementaleRepository
import com.forestry.counter.domain.repository.TigeRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Moteur de diagnostic sylvicole — pièce maîtresse du système.
 *
 * Agrège :
 *  1. Station environnementale (sol, climat, SER, topo)
 *  2. Peuplement (tiges des placettes associées)
 *  3. EssenceSuitabilityScorer (adéquation essences × station)
 *  4. Indicateurs de peuplement (G, N, DG, H100, IBP si dispo)
 *
 * Produit :
 *  - DiagnosticSylvicoleEntity persisté en DB
 *  - DiagnosticResult retourné immédiatement
 *
 * Architecture :
 *  Règles CNPF déterministes (toujours actif)
 *  └── futur : MLEnhancedScorer (TFLite, optionnel, étape 7d)
 */
class SylviculturalDiagnosticEngine(
    private val stationRepository: StationEnvironnementaleRepository,
    private val tigeRepository: TigeRepository,
    private val diagnosticRepository: DiagnosticSylvicoleRepository,
    private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ──────────────────────────────────────────────────────────────────────────
    // Résultat principal retourné à l'UI
    // ──────────────────────────────────────────────────────────────────────────
    data class DiagnosticResult(
        val diagnosticId: String,
        val parcelleId: String,
        val stationQualite: StationQualite,
        val essencesScorees: List<EssenceSuitabilityScorer.SuitabilityScore>,
        val indicateurspeuplement: PeuplementIndicateurs,
        val recommandationsPrincipales: List<String>,
        val alertesSanitaires: List<String>,
        val scoreGlobal: Int,
        val classeGlobale: EssenceSuitabilityScorer.ClasseAdequation
    )

    data class StationQualite(
        val classeIV: String,
        val altitudeM: Double?,
        val slopePct: Double?,
        val soilPh: Double?,
        val soilRumMm: Double?,
        val codeSer: String?,
        val nomSer: String?,
        val tempMoyC: Double?,
        val precipMmAn: Double?,
        val scoreStation: Int
    )

    data class PeuplementIndicateurs(
        val nTiges: Int,
        val gM2Ha: Double?,
        val dgCm: Double?,
        val diamMaxCm: Double?,
        val hauteurMoyM: Double?,
        val classeKraftMoyenne: Double?,
        val nbArbresHabitat: Int,
        val essencesDominantes: List<String>
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Point d'entrée principal
    // ──────────────────────────────────────────────────────────────────────────
    suspend fun run(
        parcelleId: String,
        essencesRetenues: List<String>? = null
    ): DiagnosticResult {
        val station = stationRepository.getByParcelleOnce(parcelleId)
        val tiges   = tigeRepository.getTigesByParcelle(parcelleId).first()

        val stationQualite = buildStationQualite(station)
        val peuplement     = buildPeuplementIndicateurs(tiges)
        val essences       = essencesRetenues ?: detectEssencesToScore(tiges, station)
        val scores         = if (station != null) EssenceSuitabilityScorer.scoreAll(essences, station)
                             else emptyList()

        val recommandations = buildRecommandations(stationQualite, peuplement, scores)
        val alertes         = buildAlerteSanitaires(station, tiges)
        val scoreGlobal     = computeScoreGlobal(stationQualite, peuplement, scores)
        val classeGlobale   = when {
            scoreGlobal >= 80 -> EssenceSuitabilityScorer.ClasseAdequation.TRES_FAVORABLE
            scoreGlobal >= 65 -> EssenceSuitabilityScorer.ClasseAdequation.FAVORABLE
            scoreGlobal >= 45 -> EssenceSuitabilityScorer.ClasseAdequation.MOYEN
            scoreGlobal >= 25 -> EssenceSuitabilityScorer.ClasseAdequation.DEFAVORABLE
            else              -> EssenceSuitabilityScorer.ClasseAdequation.INADAPTE
        }

        val diagnosticId = UUID.randomUUID().toString()
        persistDiagnostic(
            diagnosticId   = diagnosticId,
            parcelleId     = parcelleId,
            station        = station,
            scores         = scores,
            peuplement     = peuplement,
            recommandations = recommandations,
            scoreGlobal    = scoreGlobal
        )

        return DiagnosticResult(
            diagnosticId              = diagnosticId,
            parcelleId                = parcelleId,
            stationQualite            = stationQualite,
            essencesScorees           = scores,
            indicateurspeuplement     = peuplement,
            recommandationsPrincipales = recommandations,
            alertesSanitaires         = alertes,
            scoreGlobal               = scoreGlobal,
            classeGlobale             = classeGlobale
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Construction des indicateurs station
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildStationQualite(station: StationEnvironnementaleEntity?): StationQualite {
        val scoreStation = computeScoreStation(station)
        val classeIV = when {
            scoreStation >= 75 -> "I — Très favorable"
            scoreStation >= 55 -> "II — Favorable"
            scoreStation >= 35 -> "III — Moyen"
            else               -> "IV — Défavorable"
        }
        return StationQualite(
            classeIV    = classeIV,
            altitudeM   = station?.altitudeM,
            slopePct    = station?.slopePct,
            soilPh      = station?.soilPh,
            soilRumMm   = station?.soilRumMm,
            codeSer     = station?.codeSer,
            nomSer      = station?.nomSer,
            tempMoyC    = station?.tempMoyC,
            precipMmAn  = station?.precipMmAn,
            scoreStation = scoreStation
        )
    }

    private fun computeScoreStation(station: StationEnvironnementaleEntity?): Int {
        if (station == null) return 50
        var score = 50
        station.soilPh?.let { if (it in 4.5..7.5) score += 15 else score -= 10 }
        station.soilRumMm?.let { score += (it / 15.0).toInt().coerceIn(0, 20) }
        station.tempMoyC?.let { if (it in 7.0..14.0) score += 10 else score -= 5 }
        station.precipMmAn?.let { if (it >= 600) score += 10 else score -= 10 }
        station.slopePct?.let { if (it > 60) score -= 10 }
        return score.coerceIn(0, 100)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Indicateurs peuplement depuis tiges
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildPeuplementIndicateurs(tiges: List<Tige>): PeuplementIndicateurs {
        if (tiges.isEmpty()) return PeuplementIndicateurs(0, null, null, null, null, null, 0, emptyList())

        val nTiges = tiges.size
        val gSomme = tiges.sumOf { Math.PI * (it.diamCm / 200.0).pow(2) }
        val gHa = gSomme / 0.05 // normalisé par placette 500m² = 0.05ha

        val dgCm = if (gSomme > 0) {
            2 * 100 * Math.sqrt(gSomme / (Math.PI * nTiges))
        } else null

        val diamMax = tiges.maxOfOrNull { it.diamCm }
        val hautMoy = tiges.mapNotNull { it.hauteurM }.average().takeIf { !it.isNaN() }
        val kraftMoy = tiges.mapNotNull { it.classeKraft?.toDouble() }.average().takeIf { !it.isNaN() }
        val nbHabitat = tiges.count { it.isTigeHabitat }
        val essencesDom = tiges.groupBy { it.essenceCode }
            .entries.sortedByDescending { it.value.size }
            .take(3).map { it.key }

        return PeuplementIndicateurs(
            nTiges             = nTiges,
            gM2Ha              = gHa,
            dgCm               = dgCm,
            diamMaxCm          = diamMax,
            hauteurMoyM        = hautMoy,
            classeKraftMoyenne = kraftMoy,
            nbArbresHabitat    = nbHabitat,
            essencesDominantes = essencesDom
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Détection automatique des essences à scorer
    // ──────────────────────────────────────────────────────────────────────────
    private fun detectEssencesToScore(
        tiges: List<Tige>,
        station: StationEnvironnementaleEntity?
    ): List<String> {
        val fromTiges = tiges.map { it.essenceCode }.distinct()
        val serCode = station?.codeSer
        return if (fromTiges.isNotEmpty()) fromTiges
               else EssenceSuitabilityScorer.DEFAULT_ESSENCES
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Recommandations textuelles
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildRecommandations(
        station: StationQualite,
        peuplement: PeuplementIndicateurs,
        scores: List<EssenceSuitabilityScorer.SuitabilityScore>
    ): List<String> {
        val recs = mutableListOf<String>()

        val top3 = scores.take(3)
        if (top3.isNotEmpty()) {
            recs += "Essences recommandées : " + top3.joinToString(", ") {
                "${it.essenceCode} (${it.classeAdequation.emoji}${it.scoreTotal})"
            }
        }

        station.soilRumMm?.let {
            if (it < 60) recs += "Réserve utile faible (${it.toInt()} mm) — privilégier les essences xérophytes"
        }
        station.soilPh?.let {
            when {
                it < 4.5 -> recs += "Sol très acide (pH ${it}) — risque d'Al toxique, chaulage à envisager"
                it > 7.5 -> recs += "Sol calcaire (pH ${it}) — éviter les essences acidophiles"
            }
        }
        peuplement.gM2Ha?.let {
            when {
                it > 35 -> recs += "Peuplement dense (G=${it.toInt()} m²/ha) — éclaircie recommandée"
                it < 10 -> recs += "Peuplement clair (G=${it.toInt()} m²/ha) — potentiel de régénération"
            }
        }
        if (peuplement.nbArbresHabitat > 0) {
            recs += "${peuplement.nbArbresHabitat} arbre(s) habitat TreM — à préserver en ilots de vieillissement"
        }

        return recs
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Alertes sanitaires (depuis PathoEntomoDB × espèces × station)
    // ──────────────────────────────────────────────────────────────────────────
    private fun buildAlerteSanitaires(
        station: StationEnvironnementaleEntity?,
        tiges: List<Tige>
    ): List<String> {
        val alertes = mutableListOf<String>()
        val t = station?.tempMoyC ?: return alertes
        val p = station.precipMmAn ?: return alertes
        val essences = tiges.map { it.essenceCode }.distinct()

        if ("PIAB" in essences && t > 12 && p < 700) alertes += "PIAB — Risque scolytes Ips typographus (T>12°C, P<700mm)"
        if ("FREX" in essences && t > 10)             alertes += "FREX — Chalarose (Hymenoscyphus fraxineus) active"
        if ("ABBA" in essences && p < 800)             alertes += "ABBA — Stress hydrique sapin (P<800mm)"
        if ("FASY" in essences && t > 13)             alertes += "FASY — Hêtre vulnérable aux sécheresses estivales"
        val nbDeper = tiges.count { it.etatSanitaire == "MAUVAIS" || it.categorie == "DEPERISSANT" }
        if (nbDeper > 0) alertes += "$nbDeper tige(s) dépérissante(s) détectée(s) dans le peuplement"

        return alertes
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Score global synthétique 0–100
    // ──────────────────────────────────────────────────────────────────────────
    private fun computeScoreGlobal(
        station: StationQualite,
        peuplement: PeuplementIndicateurs,
        scores: List<EssenceSuitabilityScorer.SuitabilityScore>
    ): Int {
        val scoreStation  = station.scoreStation
        val scoreMeilleureEssence = scores.firstOrNull()?.scoreTotal ?: 50
        val scoreGestion  = computeScoreGestion(peuplement)
        return ((scoreStation * 0.4 + scoreMeilleureEssence * 0.4 + scoreGestion * 0.2)).toInt().coerceIn(0, 100)
    }

    private fun computeScoreGestion(peuplement: PeuplementIndicateurs): Int {
        var score = 70
        peuplement.gM2Ha?.let {
            if (it in 15.0..30.0) score += 20
            else if (it > 40 || it < 5) score -= 20
        }
        if (peuplement.nbArbresHabitat > 0) score += 10
        return score.coerceIn(0, 100)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Persistance DB
    // ──────────────────────────────────────────────────────────────────────────
    private suspend fun persistDiagnostic(
        diagnosticId: String,
        parcelleId: String,
        station: StationEnvironnementaleEntity?,
        scores: List<EssenceSuitabilityScorer.SuitabilityScore>,
        peuplement: PeuplementIndicateurs,
        recommandations: List<String>,
        scoreGlobal: Int
    ) {
        val recommJson = json.encodeToString(recommandations)
        val scoresJson = json.encodeToString(scores.map {
            mapOf(
                "code" to it.essenceCode,
                "score" to it.scoreTotal.toString(),
                "classe" to it.classeAdequation.name
            )
        })
        val entity = DiagnosticSylvicoleEntity(
            diagnosticId                  = diagnosticId,
            parcelleId                    = parcelleId,
            sessionId                     = null,
            dateCreation                  = System.currentTimeMillis(),
            operateurNom                  = null,
            scoreStation                  = peuplement.gM2Ha?.let { (it / 0.4).toInt().coerceIn(0, 100) },
            scorePeuplement               = scoreGlobal,
            scoreBiodiversite             = peuplement.nbArbresHabitat.coerceAtMost(100),
            scoreRisque                   = null,
            scoreGlobal                   = scoreGlobal,
            gHa                           = peuplement.gM2Ha,
            nHa                           = peuplement.nTiges * 20,
            vHa                           = null,
            hoM                           = peuplement.hauteurMoyM,
            hgM                           = null,
            dgCm                          = peuplement.dgCm,
            siteIndex                     = null,
            accroissementIg               = null,
            accroissementIv               = null,
            biomasseTotalTonnes           = null,
            carboneTotalTonnes            = null,
            essencesRecommandeesJson      = scoresJson,
            essencesDeconseillees         = null,
            essencesVigilanceJson         = null,
            risquesDetectesJson           = null,
            recommandationsSylvicolesJson = recommJson,
            typeSylviculturePreco         = null,
            volumeEclairciePreco          = null,
            delaiInterventionAns          = null,
            syntheseTextuelle             = null,
            algoVersion                   = "CNPF_RULES_V1",
            dataSourcesJson               = null,
            remarques                     = null,
            updatedAt                     = System.currentTimeMillis()
        )
        runCatching { diagnosticRepository.insert(entity) }
        DiagnosticTrainingDataExporter.export(context, station, peuplement, scores)
    }
}

// Extension utilitaire
private fun Double.pow(exp: Int): Double = Math.pow(this, exp.toDouble())
