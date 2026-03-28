package com.forestry.counter.domain.model.ripisylve

/**
 * Résultat du calcul de l'indice ripisylve.
 * Score total : -20 à 100.
 */
data class RipisylveScore(
    val scoreContinuite: Int,       // 0, 5, 10, 20, 30
    val scoreLargeur: Int,          // 0, 10, 20
    val scoreStrates: Int,          // 0, 10, 20
    val scoreDiversite: Int,        // 0, 5, 10
    val scoreDiametres: Int,        // 0, 5, 10
    val scoreMicrohabitats: Int,    // 0, 5, 10
    val scoreSanitaire: Int,        // 0, -10, -15, -20
    val scoreInvasives: Int,        // 0, -5, -10, -15, -20
    val scoreInadaptees: Int,       // 0, -5, -10
    val scoreStabilite: Int,        // 0, -10, -20
    val nbMicrohabitats: Int,
    val nbStrates: Int,
    val nbClassesDiam: Int,
    /** Score de confiance 0–100, calculé par RipisylveScorer selon les données observées */
    val confidenceScore: Int = 0
) {
    val scoreTotal: Int get() = scoreContinuite + scoreLargeur + scoreStrates +
            scoreDiversite + scoreDiametres + scoreMicrohabitats +
            scoreSanitaire + scoreInvasives + scoreInadaptees + scoreStabilite

    val scorePositif: Int get() = scoreContinuite + scoreLargeur + scoreStrates +
            scoreDiversite + scoreDiametres + scoreMicrohabitats

    val scorePenalite: Int get() = scoreSanitaire + scoreInvasives + scoreInadaptees + scoreStabilite

    val fonctionnalite: RipisylveFonctionnalite get() = when {
        scoreTotal <= 0  -> RipisylveFonctionnalite.TRES_MAUVAISE
        scoreTotal <= 20 -> RipisylveFonctionnalite.MAUVAISE
        scoreTotal <= 40 -> RipisylveFonctionnalite.MEDIOCRE
        scoreTotal <= 60 -> RipisylveFonctionnalite.MOYENNE
        scoreTotal <= 80 -> RipisylveFonctionnalite.BONNE
        else             -> RipisylveFonctionnalite.TRES_BONNE
    }

    val consigneGestion: ConsigneGestion get() = when (fonctionnalite) {
        RipisylveFonctionnalite.TRES_MAUVAISE,
        RipisylveFonctionnalite.MAUVAISE -> ConsigneGestion.RESTAURATION
        RipisylveFonctionnalite.MEDIOCRE,
        RipisylveFonctionnalite.MOYENNE  -> ConsigneGestion.ENTRETIEN
        RipisylveFonctionnalite.BONNE,
        RipisylveFonctionnalite.TRES_BONNE -> ConsigneGestion.MAINTIEN
    }

    /**
     * Niveau de confiance calculé depuis [confidenceScore] (0–100).
     * Jamais FORTE par défaut : doit être gagné par les données observées.
     */
    val confidenceLevel: DiagConfidence get() = when {
        confidenceScore >= 70 -> DiagConfidence.FORTE
        confidenceScore >= 40 -> DiagConfidence.MOYENNE
        else                  -> DiagConfidence.FAIBLE
    }

    /** Résumé textuel automatique */
    fun generateSummary(): String {
        val sb = StringBuilder()
        sb.append("La ripisylve présente ")
        sb.append(when {
            scoreContinuite >= 20 -> "une continuité semi-continue à continue"
            scoreContinuite >= 10 -> "une continuité discontinue"
            scoreContinuite >= 5  -> "une continuité clairsemée"
            else -> "une continuité absente ou très fragmentée"
        })
        sb.append(", ")
        sb.append(when {
            scoreLargeur >= 20 -> "une largeur suffisante (≥ 5 m)"
            scoreLargeur >= 10 -> "une largeur moyenne (1.5–5 m)"
            else -> "une largeur insuffisante (< 1.5 m)"
        })
        sb.append(", une structure ")
        sb.append(when (nbStrates) {
            3 -> "à trois strates"
            2 -> "à deux strates"
            else -> "monostratifiée"
        })
        sb.append(" et une diversité spécifique ")
        sb.append(when {
            scoreDiversite >= 10 -> "élevée"
            scoreDiversite >= 5  -> "correcte"
            else -> "faible"
        })
        sb.append(". La note finale")
        val penalites = mutableListOf<String>()
        if (scoreSanitaire < 0) penalites.add("l'état sanitaire dégradé")
        if (scoreInvasives < 0) penalites.add("la présence d'espèces invasives")
        if (scoreInadaptees < 0) penalites.add("la présence d'espèces inadaptées")
        if (scoreStabilite < 0) penalites.add("l'instabilité des arbres ou des berges")
        if (penalites.isEmpty()) {
            sb.append(" n'est pénalisée par aucun indicateur négatif.")
        } else {
            sb.append(" est pénalisée par ${penalites.joinToString(" et ")}.")
        }
        return sb.toString()
    }
}

enum class RipisylveFonctionnalite(
    val labelFr: String,
    val colorHex: Long
) {
    TRES_MAUVAISE("Très mauvaise", 0xFFB71C1C),
    MAUVAISE("Mauvaise", 0xFFE53935),
    MEDIOCRE("Médiocre", 0xFFFF8F00),
    MOYENNE("Moyenne", 0xFFFFD600),
    BONNE("Bonne", 0xFF43A047),
    TRES_BONNE("Très bonne", 0xFF1B5E20)
}

enum class ConsigneGestion(val labelFr: String) {
    RESTAURATION("Restauration"),
    ENTRETIEN("Entretien"),
    MAINTIEN("Maintien en état, pas d'intervention immédiate")
}

enum class DiagConfidence(val labelFr: String) {
    FORTE("Forte"),
    MOYENNE("Moyenne"),
    FAIBLE("Faible")
}
