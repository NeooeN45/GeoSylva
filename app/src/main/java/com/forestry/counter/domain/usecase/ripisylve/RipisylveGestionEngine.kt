package com.forestry.counter.domain.usecase.ripisylve

import com.forestry.counter.domain.model.ripisylve.RipisylveObservation
import com.forestry.counter.domain.model.ripisylve.RipisylveScore

/**
 * Moteur de gestion des ripisylves — diagnostic fonctionnel et plan d'action.
 * Source : Guide ONF Ripisylves 2019 + INRAE Gestion des boisements riverains.
 */
object RipisylveGestionEngine {

    data class ActionGestion(
        val titre: String,
        val description: String,
        val urgence: Urgence,
        val type: TypeAction
    )

    data class InvasiveInfo(
        val nomFr: String,
        val nomLatin: String,
        val risque: NiveauRisque,
        val actionPrioritaire: String
    )

    data class DiagnosticFonctionnel(
        val score: RipisylveScore,
        val niveauFonctionnalite: NiveauFonctionnalite,
        val actions: List<ActionGestion>,
        val invasivesDetectees: List<InvasiveInfo>,
        val syntheseFr: String,
        val pointsForts: List<String>,
        val pointsFaibles: List<String>
    )

    enum class Urgence { IMMEDIATE, COURT_TERME, MOYEN_TERME, LONG_TERME }
    enum class TypeAction { INVASIVES, RESTAURATION, DIVERSIFICATION, SURVEILLANCE, GESTION_EAU, BOISEMENT }
    enum class NiveauRisque { CRITIQUE, ELEVE, MODERE, FAIBLE }
    enum class NiveauFonctionnalite(val label: String, val color: Long) {
        DEGRADEE("Dégradée", 0xFFD32F2F),
        ALTEREE("Altérée", 0xFFF57C00),
        FONCTIONNELLE("Fonctionnelle", 0xFF388E3C),
        EXCELLENTE("Excellente", 0xFF1B5E20)
    }

    private val INVASIVES_CATALOGUE = listOf(
        InvasiveInfo("Renouée du Japon", "Reynoutria japonica", NiveauRisque.CRITIQUE,
            "Arrachage mécanique répété en été (3×/an min.) + bâchage hivernal. Ne jamais couper seul — aggrave la repousse."),
        InvasiveInfo("Renouée de Sakhaline", "Reynoutria sachalinensis", NiveauRisque.CRITIQUE,
            "Même protocole que Reynoutria japonica. Surveiller les zones inondables."),
        InvasiveInfo("Renouée de Bohème", "Reynoutria ×bohemica", NiveauRisque.CRITIQUE,
            "Hybride particulièrement invasif. Signaler au gestionnaire de cours d'eau."),
        InvasiveInfo("Balsamine de l'Himalaya", "Impatiens glandulifera", NiveauRisque.ELEVE,
            "Arrachage manuel avant floraison (juin) — une seule intervention suffit si précoce."),
        InvasiveInfo("Balsamine géante", "Impatiens noli-tangere", NiveauRisque.MODERE,
            "Arrachage avant fructification. Surveiller les berges perturbées."),
        InvasiveInfo("Buddléia de David", "Buddleja davidii", NiveauRisque.ELEVE,
            "Coupe avant fructification + traitement souche. Éviter en berge — remplacer par saule ou aulne."),
        InvasiveInfo("Arbre aux papillons", "Buddleja davidii", NiveauRisque.ELEVE,
            "Élimination mécanique — couper et dessoucher. Replanter espèces indigènes."),
        InvasiveInfo("Érable negundo", "Acer negundo", NiveauRisque.ELEVE,
            "Annélation ou arrachage des rejets. Favoriser l'aulne glutineux et le frêne en remplacement."),
        InvasiveInfo("Robinier faux-acacia", "Robinia pseudoacacia", NiveauRisque.MODERE,
            "Ne pas couper seul — rejette vigoureusement. Annélation ou herbicide sur souche fraîche."),
        InvasiveInfo("Jussie", "Ludwigia grandiflora", NiveauRisque.CRITIQUE,
            "Signalement obligatoire. Arrachage manuel en eaux calmes. Ne pas fragmenter."),
        InvasiveInfo("Myriophylle du Brésil", "Myriophyllum aquaticum", NiveauRisque.CRITIQUE,
            "Signalement obligatoire. Gestion mécanique en eaux courantes — jamais de traitement chimique."),
        InvasiveInfo("Ailante glanduleux", "Ailanthus altissima", NiveauRisque.ELEVE,
            "Annélation printanière ou injection herbicide ciblée. Éviter la coupe seule.")
    )

    fun buildDiagnostic(obs: RipisylveObservation, score: RipisylveScore): DiagnosticFonctionnel {
        val niveau = classifyFonctionnalite(score.scoreTotal)
        val actions = buildActions(obs, score, niveau)
        val invasives = detectInvasives(obs)
        val (forts, faibles) = buildPointsFortsFaibles(obs, score)
        val synthese = buildSynthese(niveau, score, actions, invasives)

        return DiagnosticFonctionnel(
            score = score,
            niveauFonctionnalite = niveau,
            actions = actions,
            invasivesDetectees = invasives,
            syntheseFr = synthese,
            pointsForts = forts,
            pointsFaibles = faibles
        )
    }

    private fun classifyFonctionnalite(total: Int): NiveauFonctionnalite = when {
        total >= 70 -> NiveauFonctionnalite.EXCELLENTE
        total >= 50 -> NiveauFonctionnalite.FONCTIONNELLE
        total >= 30 -> NiveauFonctionnalite.ALTEREE
        else -> NiveauFonctionnalite.DEGRADEE
    }

    private fun detectInvasives(obs: RipisylveObservation): List<InvasiveInfo> {
        if (obs.invasivesPct == 0.0) return emptyList()
        val critiques = INVASIVES_CATALOGUE.filter { it.risque == NiveauRisque.CRITIQUE }
        val eleves = INVASIVES_CATALOGUE.filter { it.risque == NiveauRisque.ELEVE }
        return when {
            obs.invasivesPct >= 50 -> critiques + eleves.take(2)
            obs.invasivesPct >= 25 -> critiques.take(2)
            else -> critiques.take(1)
        }
    }

    private fun buildActions(
        obs: RipisylveObservation,
        score: RipisylveScore,
        niveau: NiveauFonctionnalite
    ): List<ActionGestion> {
        val actions = mutableListOf<ActionGestion>()

        if (obs.invasivesPct > 0) {
            actions.add(ActionGestion(
                titre = "Lutte contre les plantes invasives",
                description = "Invasives présentes sur ${obs.invasivesPct}% du linéaire. Établir un plan de gestion annuel avec protocole espèce par espèce.",
                urgence = if (obs.invasivesPct >= 25) Urgence.IMMEDIATE else Urgence.COURT_TERME,
                type = TypeAction.INVASIVES
            ))
        }

        if (obs.continuitePct < 50) {
            actions.add(ActionGestion(
                titre = "Restauration de la continuité boisée",
                description = "Continuité à ${obs.continuitePct}% — des gaps importants fragmentent la ripisylve. Replanter aulne, saule, frêne sur les zones ouvertes.",
                urgence = if (obs.continuitePct < 25) Urgence.IMMEDIATE else Urgence.COURT_TERME,
                type = TypeAction.RESTAURATION
            ))
        }

        val nbStrates = listOf(obs.strateHerbacee, obs.strateArbustive, obs.strateArborescente).count { it }
        if (nbStrates < 2) {
            actions.add(ActionGestion(
                titre = "Diversification structurelle (strates)",
                description = "Seulement $nbStrates strate(s) présente(s). Introduire arbustes indigènes (cornouiller, viorne, sureau) pour créer une structure multi-strates.",
                urgence = Urgence.MOYEN_TERME,
                type = TypeAction.DIVERSIFICATION
            ))
        }

        if (obs.stabilitePct >= 25) {
            actions.add(ActionGestion(
                titre = "Consolidation des berges instables",
                description = "${obs.stabilitePct}% du linéaire avec berges instables. Techniques végétales : boutures de saule, fascines, génie végétal.",
                urgence = if (obs.stabilitePct >= 50) Urgence.IMMEDIATE else Urgence.COURT_TERME,
                type = TypeAction.GESTION_EAU
            ))
        }

        if (obs.sanitairePct >= 25) {
            actions.add(ActionGestion(
                titre = "Traitement sanitaire",
                description = "${obs.sanitairePct}% des individus présentent des problèmes sanitaires. Abattre les arbres dangereux, traiter les foyers fongiques.",
                urgence = if (obs.sanitairePct >= 50) Urgence.IMMEDIATE else Urgence.COURT_TERME,
                type = TypeAction.SURVEILLANCE
            ))
        }

        if (obs.nbEspecesObservees < 3) {
            actions.add(ActionGestion(
                titre = "Enrichissement floristique",
                description = "Seulement ${obs.nbEspecesObservees} espèce(s) ligneuse(s) notée(s). Objectif ≥ 5 espèces indigènes : aulne, saule, frêne, cornouiller sanguin, viorne lantane.",
                urgence = Urgence.MOYEN_TERME,
                type = TypeAction.DIVERSIFICATION
            ))
        }

        if (score.scoreMicrohabitats == 0) {
            actions.add(ActionGestion(
                titre = "Préservation des microhabitats",
                description = "Aucun microhabitat détecté. Conserver les vieux arbres creux, les souches et le bois mort au sol — essentiels pour la faune riparienne.",
                urgence = Urgence.LONG_TERME,
                type = TypeAction.SURVEILLANCE
            ))
        }

        return actions.sortedBy { it.urgence.ordinal }
    }

    private fun buildPointsFortsFaibles(
        obs: RipisylveObservation,
        score: RipisylveScore
    ): Pair<List<String>, List<String>> {
        val forts = mutableListOf<String>()
        val faibles = mutableListOf<String>()

        if (obs.continuitePct >= 75) forts.add("Continuité boisée excellente (${obs.continuitePct}%)")
        else if (obs.continuitePct < 50) faibles.add("Continuité insuffisante (${obs.continuitePct}%)")

        if (score.nbStrates == 3) forts.add("Structure multi-strates complète")
        else faibles.add("Structure incomplète (${score.nbStrates} strate(s))")

        if (obs.nbEspecesObservees >= 5) forts.add("Bonne diversité spécifique (${obs.nbEspecesObservees} esp.)")
        else if (obs.nbEspecesObservees < 3) faibles.add("Diversité spécifique faible (${obs.nbEspecesObservees} esp.)")

        if (obs.invasivesPct == 0.0) forts.add("Absence d'espèces invasives")
        else faibles.add("Invasives présentes (${obs.invasivesPct.toInt()}%)")

        if (score.nbMicrohabitats >= 3) forts.add("Microhabitats diversifiés (${score.nbMicrohabitats})")
        else if (score.nbMicrohabitats == 0) faibles.add("Aucun microhabitat observé")

        if (obs.stabilitePct == 0.0) forts.add("Berges stables")
        else if (obs.stabilitePct >= 50) faibles.add("Berges fortement instables (${obs.stabilitePct.toInt()}%)")


        return forts to faibles
    }

    private fun buildSynthese(
        niveau: NiveauFonctionnalite,
        score: RipisylveScore,
        actions: List<ActionGestion>,
        invasives: List<InvasiveInfo>
    ): String {
        val sb = StringBuilder()
        sb.append("Ripisylve ${niveau.label.lowercase()} — score ${score.scoreTotal}/100. ")
        if (invasives.isNotEmpty()) {
            sb.append("${invasives.size} espèce(s) invasive(s) détectée(s). ")
        }
        val urgentes = actions.filter { it.urgence == Urgence.IMMEDIATE }
        if (urgentes.isNotEmpty()) {
            sb.append("Actions immédiates requises : ${urgentes.joinToString(", ") { it.titre }}.")
        } else {
            sb.append("Aucune action urgente. Maintenir la gestion conservatoire.")
        }
        return sb.toString()
    }
}
