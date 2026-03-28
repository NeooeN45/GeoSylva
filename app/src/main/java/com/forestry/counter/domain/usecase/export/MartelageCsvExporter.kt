package com.forestry.counter.domain.usecase.export

import android.content.Context
import android.net.Uri
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.presentation.screens.forestry.MartelageStats
import java.io.OutputStreamWriter
import kotlin.math.PI
import kotlin.math.abs

/**
 * Exporteur CSV enrichi pour les données de martelage.
 *
 * Génère deux feuilles (onglets CSV séparés par un séparateur de section) :
 *  1. Synthèse — indicateurs agrégés calculés automatiquement par l'application
 *  2. Détail tiges — une ligne par tige avec volume et surface terrière calculés
 *
 * Source Pierre Chaudé (1991) : les volumes individuels sont inclus si disponibles.
 */
object MartelageCsvExporter {

    private const val SEP = ";"
    private const val NL  = "\r\n"

    // ─── Export principal ─────────────────────────────────────────────────────

    fun exportSynthesis(
        context: Context,
        uri: Uri,
        stats: MartelageStats,
        scopeLabel: String,
        tiges: List<Tige> = emptyList()
    ): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { os ->
            OutputStreamWriter(os, Charsets.UTF_8).use { w ->
                w.write("\uFEFF")  // BOM UTF-8 pour Excel
                w.write(buildSynthesisSheet(stats, scopeLabel))
                w.write("$NL$NL")
                if (tiges.isNotEmpty()) {
                    w.write(buildTigesSheet(tiges, stats))
                }
            }
        } ?: error("Impossible d'ouvrir le flux de sortie")
    }

    // ─── Feuille synthèse ─────────────────────────────────────────────────────

    private fun buildSynthesisSheet(s: MartelageStats, scopeLabel: String): String {
        val sb = StringBuilder()

        fun h(title: String) { sb.append("${title.uppercase()}$NL") }
        fun row(label: String, value: String, unit: String = "", note: String = "") {
            sb.append("$label${SEP}${value}${SEP}${unit}${SEP}${note}$NL")
        }
        fun rowD(label: String, v: Double?, unit: String = "", note: String = "") {
            row(label, v?.let { fmt2(it) } ?: "", unit, note)
        }
        fun blank() { sb.append(NL) }

        // ── En-tête ──
        sb.append("GeoSylva — Export Martelage${SEP}${scopeLabel}${SEP}${nowDate()}$NL")
        sb.append("Champ${SEP}Valeur${SEP}Unité${SEP}Note$NL")
        blank()

        // ── Peuplement résiduel (tout) ──
        h("1. PEUPLEMENT — EFFECTIFS ET VOLUMES")
        row("N total (tiges mesurées)", s.nTotal.toString(), "tiges")
        row("N/ha", fmt1(s.nPerHa), "t/ha")
        rowD("G total", s.gTotal, "m²")
        rowD("G/ha", s.gPerHa, "m²/ha",
            when { s.gPerHa < 10 -> "Faible"; s.gPerHa < 20 -> "Modéré"; s.gPerHa < 35 -> "Normal"; s.gPerHa < 50 -> "Dense"; else -> "Surpeuplement" })
        rowD("V total", s.vTotal, "m³")
        rowD("V/ha", s.vPerHa, "m³/ha")
        blank()

        // ── Dendrométrie ──
        h("2. DENDROMÉTRIE")
        rowD("Dm (diamètre moyen)", s.dm, "cm")
        rowD("Dg (diam. quadratique)", s.dg, "cm",
            if (s.dm != null && s.dg != null && s.dg > 0) "Dm/Dg=${fmt2(s.dm / s.dg)}" else "")
        rowD("Dmin", s.dMin, "cm")
        rowD("Dmax", s.dMax, "cm")
        rowD("CV(D) — coeff. variation", s.cvDiam, "%",
            s.cvDiam?.let {
                when { it < 15 -> "Futaie régulière équienne"; it < 30 -> "Futaie régulière normale"; it < 50 -> "Structure bi-étagée"; else -> "Structure complexe/jardinée" }
            } ?: "")
        rowD("H moy", s.meanH, "m")
        rowD("H Lorey", s.hLorey, "m",
            if (s.hLorey != null && s.meanH != null && s.meanH > 0)
                "H Lorey/H moy=${fmt2(s.hLorey / s.meanH)}" else "")
        blank()

        // ── Élancement H/D — résineux uniquement ──
        val dominantCode = s.perEssence.maxByOrNull { it.gPct }?.essenceCode ?: ""
        val isResineuxDom = dominantCode.uppercase().let { up ->
            up.contains("PIN") || up.contains("SAPIN") || up.contains("EPICEA") ||
            up.contains("DOUGLAS") || up.contains("MELEZE") || up.contains("CEDRE") ||
            up.contains("SEQUOIA") || up.contains("THUYA")
        }
        if (s.dg != null && s.meanH != null && s.dg > 0.0 && isResineuxDom) {
            val elancement = s.meanH / (s.dg / 100.0)
            row("Élancement H/D (résineux)", fmt0(elancement), "",
                when { elancement < 70 -> "Très stable"; elancement < 85 -> "Stable"; elancement < 100 -> "Normal"; else -> "Élancé ⚠ — risque venteux" })
        }
        blank()

        // ── Ratio V/G ──
        h("3. CORROBORATION (VÉRIFICATION CROISÉE)")
        s.ratioVG?.let { vg ->
            row("Ratio V/G", fmt1(vg), "m³/m²",
                when { vg < 4 -> "Très bas — tarif inadapté ?"; vg < 6 -> "Bas — peuplement jeune/dense"; vg > 22 -> "Très élevé — vérifier tarif"; vg > 16 -> "Élevé — grands arbres"; else -> "Cohérent (8–15)" })
        }
        val dm = s.dm; val dg = s.dg
        if (dm != null && dg != null && dg > 0) {
            val r = dm / dg
            row("Dm/Dg", fmt3(r), "",
                when { r < 0.82 -> "Distribution étalée vers grands D"; r > 1.08 -> "Distribution étalée vers petits D"; else -> "Distribution symétrique (normal)" })
        }
        if (dg != null && s.nPerHa > 0 && s.gPerHa > 0) {
            val gCalc = s.nPerHa * PI / 4.0 * (dg / 100.0) * (dg / 100.0)
            val errPct = abs(gCalc - s.gPerHa) / s.gPerHa * 100
            row("G/ha recalculé (N×π/4×Dg²)", fmt2(gCalc), "m²/ha",
                "Écart vs mesuré=${fmt0(errPct)}%${if (errPct > 20) " — diamètres manquants ?" else ""}")
        }
        s.cvDiam?.let { cv ->
            row("Structure CV(D)", fmt0(cv), "%",
                when { cv < 15 -> "Équienne"; cv < 30 -> "Régulière normale"; cv < 50 -> "Bi-étagée"; else -> "Structure complexe" })
        }
        blank()

        // ── Simulation de prélèvement ──
        if (s.harvestNhaPct != null || s.harvestGhaPct != null) {
            h("4. SIMULATION DE PRÉLÈVEMENT (MARTELAGE)")
            s.harvestNhaPct?.let { row("Taux ΔN/ha", fmt1(it), "%") }
            s.residualNha?.let   { row("Résiduel N/ha", fmt0(it), "t/ha") }
            s.harvestGhaPct?.let { row("Taux ΔG/ha", fmt1(it), "%",
                when { it > 40 -> "Très intense — risque déstabilisation"; it > 30 -> "Fort — surveiller le résiduel"; else -> "Modéré — conforme ONF/CNPF" }) }
            s.residualGha?.let   { row("Résiduel G/ha", fmt2(it), "m²/ha") }
            s.harvestVhaPct?.let { row("Taux ΔV/ha", fmt1(it), "%") }
            s.residualVha?.let   { row("Résiduel V/ha", fmt2(it), "m³/ha") }
            blank()
        }

        // ── Valorisation ──
        if (s.revenueTotal != null && s.revenueTotal > 0) {
            h("5. VALORISATION")
            row("Revenu total estimé", fmt0(s.revenueTotal), "€")
            s.revenuePerHa?.let { row("Revenu/ha", fmt0(it), "€/ha") }
            blank()
        }

        // ── Biodiversité ──
        s.biodiversity?.let { bio ->
            h("6. BIODIVERSITÉ")
            row("Nb essences", bio.speciesCount.toString(), "")
            row("Shannon H'", fmt3(bio.shannonH), "",
                when { bio.shannonH < 0.5 -> "Peuplement monospécifique"; bio.shannonH < 1.5 -> "Diversité faible"; bio.shannonH < 2.0 -> "Diversité modérée"; else -> "Diversité élevée" })
            bio.pielou?.let { row("Piélou J", fmt3(it), "", if (it > 0.8) "Distribution équitable" else "") }
            row("TGB (D≥70cm)", bio.tgbCount.toString(), "tiges")
            row("Arbres bio", bio.bioTreeCount.toString(), "tiges")
            row("Arbres morts", bio.deadTreeCount.toString(), "tiges")
            row("Score IBP simplifié", "${bio.ibpScore}/${bio.ibpMax}", "",
                when { bio.ibpScore >= 7 -> "Haute valeur biologique"; bio.ibpScore >= 4 -> "Valeur modérée"; else -> "Valeur faible" })
            blank()
        }

        // ── Distribution qualité ──
        if (s.qualityDistribution.isNotEmpty()) {
            h("7. DISTRIBUTION PAR QUALITÉ")
            row("Arbres évalués", "${s.qualityAssessedCount}/${s.qualityTotalCount}", "tiges")
            s.qualityDistribution.filter { it.count > 0 }.forEach { q ->
                row("Qualité ${q.grade}", q.count.toString(), "tiges (${fmt1(q.pct)}%)")
            }
            blank()
        }

        // ── Alertes sanitaires ──
        if (s.sanityWarnings.isNotEmpty()) {
            h("8. ALERTES SANITAIRES ET COHÉRENCE")
            s.sanityWarnings.forEach { w ->
                val sev = w.severity.name
                val valStr = w.value?.let { "valeur=${fmt2(it)}" } ?: ""
                row("[$sev] ${w.code}", valStr, "", w.tigeId ?: "")
            }
            blank()
        }

        // ── Par essence ──
        if (s.perEssence.isNotEmpty()) {
            h("9. TABLEAU PAR ESSENCE")
            sb.append("Essence${SEP}N${SEP}N%${SEP}G total (m²)${SEP}G/ha (m²/ha)${SEP}V total (m³)${SEP}V/ha (m³/ha)${SEP}Dm (cm)${SEP}Dg (cm)${SEP}Rev. total (€)${SEP}Rev./ha (€/ha)${SEP}Qualité dominante$NL")
            s.perEssence.forEach { e ->
                sb.append("${e.essenceName}${SEP}${e.n}${SEP}${fmt1(e.nPct)}${SEP}" +
                    "${fmt2(e.gTotal)}${SEP}${fmt2(e.gPerHa)}${SEP}" +
                    "${fmt2(e.vTotal)}${SEP}${fmt2(e.vPerHa)}${SEP}" +
                    "${e.dm?.let { fmt1(it) } ?: ""}${SEP}${e.dg?.let { fmt1(it) } ?: ""}${SEP}" +
                    "${e.revenueTotal?.let { fmt0(it) } ?: ""}${SEP}" +
                    "${e.revenuePerHa?.let { fmt0(it) } ?: ""}${SEP}" +
                    "${e.dominantQuality ?: ""}$NL")
            }
        }

        return sb.toString()
    }

    // ─── Feuille détail tiges ─────────────────────────────────────────────────

    private fun buildTigesSheet(tiges: List<Tige>, stats: MartelageStats): String {
        val sb = StringBuilder()
        sb.append("DÉTAIL TIGES$NL")
        sb.append("ID${SEP}Essence${SEP}Diamètre (cm)${SEP}Hauteur (m)${SEP}" +
            "Surface terrière (m²)${SEP}Valeur (€)${SEP}" +
            "Qualité (1-5)${SEP}Statut${SEP}GPS WKT${SEP}Défauts${SEP}Note$NL")

        tiges.forEach { t ->
            val g = PI / 4.0 * (t.diamCm / 100.0) * (t.diamCm / 100.0)
            val marquee = when (t.destination) {
                "COUPER"    -> "Martelé"
                "CONSERVER" -> "Conservé"
                null        -> "Martelé"  // comportement par défaut martelage
                else        -> t.destination
            }
            sb.append("${t.id}${SEP}" +
                "${t.essenceCode}${SEP}" +
                "${fmt1(t.diamCm)}${SEP}" +
                "${t.hauteurM?.let { fmt1(it) } ?: ""}${SEP}" +
                "${fmt4(g)}${SEP}" +
                "${t.valueEur?.let { fmt2(it) } ?: ""}${SEP}" +
                "${t.qualite ?: ""}${SEP}" +
                "${marquee}${SEP}" +
                "${t.gpsWkt ?: ""}${SEP}" +
                "${t.defauts?.joinToString("|") ?: ""}${SEP}" +
                "${t.note ?: ""}$NL")
        }
        return sb.toString()
    }

    // ─── Helpers de formatage ─────────────────────────────────────────────────

    private fun fmt0(v: Double) = "%.0f".format(v)
    private fun fmt1(v: Double) = "%.1f".format(v)
    private fun fmt2(v: Double) = "%.2f".format(v)
    private fun fmt3(v: Double) = "%.3f".format(v)
    private fun fmt4(v: Double) = "%.4f".format(v)
    private fun fmt5(v: Double) = "%.5f".format(v)

    private fun nowDate(): String {
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.FRANCE)
        return sdf.format(java.util.Date())
    }
}
