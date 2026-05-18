package com.forestry.counter.domain.usecase.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.forestry.counter.domain.model.ripisylve.RipisylveObservation
import com.forestry.counter.domain.model.ripisylve.RipisylveScore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Génère un PDF professionnel du diagnostic de ripisylve.
 * Style cohérent avec PdfSynthesisExporter (A4, même PdfState).
 */
object RipisylvePdfExporter {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val CONTENT_W = (PAGE_W - 2 * MARGIN).toInt()
    private const val BOTTOM_MARGIN = 55f
    private val DATE_FMT = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    // ── Palette ripisylve (bleu-vert eau) ────────────────────────────────────
    private val COLOR_PRIMARY   = Color.parseColor("#1565C0")
    private val COLOR_HEADER_BG = Color.parseColor("#0D47A1")
    private val COLOR_SECTION   = Color.parseColor("#1565C0")
    private val COLOR_POSITIVE  = Color.parseColor("#2E7D32")
    private val COLOR_PENALTY   = Color.parseColor("#C62828")
    private val COLOR_NEUTRAL   = Color.parseColor("#37474F")

    // ─────────────────────────────────────────────────────────────────────────
    // État partagé de rendu (même pattern que PdfSynthesisExporter)
    // ─────────────────────────────────────────────────────────────────────────
    private class St(val doc: PdfDocument) {
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = MARGIN + 10f
        var pageNum = 1

        fun startPage() {
            val info = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create()
            page = doc.startPage(info)
            canvas = requireNotNull(page) { "PdfDocument.startPage returned null" }.canvas
            y = MARGIN + 10f
        }

        fun finishPage() {
            canvas?.let { c ->
                val fp = mkPaint(8f, Color.GRAY).apply { textAlign = Paint.Align.CENTER }
                c.drawText("Diagnostic Ripisylve — GeoSylva", PAGE_W / 2f, PAGE_H - 22f, fp)
                val pp = mkPaint(8f, Color.GRAY).apply { textAlign = Paint.Align.RIGHT }
                c.drawText("Page $pageNum", PAGE_W - MARGIN, PAGE_H - 22f, pp)
            }
            page?.let { doc.finishPage(it) }
            page = null; canvas = null; pageNum++
        }

        fun checkBreak(needed: Float = 30f) {
            if (y + needed > PAGE_H - BOTTOM_MARGIN) { finishPage(); startPage() }
        }

        fun sectionTitle(title: String) {
            checkBreak(48f)
            y += 6f
            canvas?.drawLine(MARGIN, y, PAGE_W - MARGIN, y,
                Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f })
            y += 14f
            canvas?.drawText(title, MARGIN, y,
                mkPaint(12f, COLOR_SECTION, bold = true))
            y += 16f
        }

        fun kv(label: String, value: String, valueColor: Int = Color.BLACK) {
            checkBreak(16f)
            canvas?.drawText(label, MARGIN, y, mkPaint(9f, Color.DKGRAY))
            canvas?.drawText(value, PAGE_W - MARGIN, y,
                mkPaint(9f, valueColor, bold = true).apply { textAlign = Paint.Align.RIGHT })
            y += 14f
        }

        fun bullet(icon: String, text: String, color: Int, indent: Float = 0f) {
            checkBreak(14f)
            canvas?.drawText(icon, MARGIN + indent, y, mkPaint(9f, color, bold = true))
            canvas?.drawText(text, MARGIN + indent + 14f, y, mkPaint(8.5f, Color.DKGRAY))
            y += 13f
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Point d'entrée public
    // ─────────────────────────────────────────────────────────────────────────
    fun export(
        context: Context,
        uri: Uri,
        obs: RipisylveObservation,
        score: RipisylveScore,
        parcelleNom: String = ""
    ) {
        val doc = PdfDocument()
        try {
            val st = St(doc)
            st.startPage()
            drawContent(st, obs, score, parcelleNom)
            st.finishPage()
            context.contentResolver.openOutputStream(uri)?.use { os -> doc.writeTo(os) }
        } finally {
            doc.close()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    private fun drawContent(st: St, obs: RipisylveObservation, score: RipisylveScore, parcelleNom: String) {
        val c = st.canvas ?: return

        // ── Bandeau en-tête ──────────────────────────────────────────────────
        val headerBg = Paint().apply { color = COLOR_HEADER_BG; style = Paint.Style.FILL }
        c.drawRect(0f, 0f, PAGE_W.toFloat(), 80f, headerBg)
        c.drawText("Diagnostic Ripisylve", MARGIN, 34f,
            mkPaint(20f, Color.WHITE, bold = true))
        c.drawText("Indice fonctionnel CRPF — Section de cours d'eau", MARGIN, 54f,
            mkPaint(11f, Color.WHITE).apply { alpha = 210 })
        val brandP = mkPaint(9f, Color.WHITE).apply { alpha = 180; textAlign = Paint.Align.RIGHT }
        c.drawText("GeoSylva", PAGE_W - MARGIN, 34f, brandP)
        c.drawText(DATE_FMT.format(Date()), PAGE_W - MARGIN, 52f, brandP)
        st.y = 95f

        // ── Score principal ──────────────────────────────────────────────────
        val fonc = score.fonctionnalite
        val foncColor = Color.parseColor("#" + fonc.colorHex.toString(16).takeLast(6).padStart(6, '0'))
        val scoreBg = Paint().apply { color = foncColor; alpha = 25; style = Paint.Style.FILL }
        val scoreBorder = Paint().apply { color = foncColor; style = Paint.Style.STROKE; strokeWidth = 2f }
        c.drawRoundRect(MARGIN, st.y, PAGE_W - MARGIN, st.y + 75f, 10f, 10f, scoreBg)
        c.drawRoundRect(MARGIN, st.y, PAGE_W - MARGIN, st.y + 75f, 10f, 10f, scoreBorder)

        val bigScore = mkPaint(46f, foncColor, bold = true)
        c.drawText("${score.scoreTotal}", MARGIN + 18f, st.y + 52f, bigScore)
        val outOf = mkPaint(18f, Color.parseColor("#616161"))
        c.drawText("/ 100", MARGIN + 75f, st.y + 52f, outOf)

        // Badge fonctionnalité
        val badgeBg = Paint().apply { color = foncColor; style = Paint.Style.FILL }
        c.drawRoundRect(MARGIN + 140f, st.y + 16f, MARGIN + 320f, st.y + 44f, 5f, 5f, badgeBg)
        c.drawText(fonc.labelFr, MARGIN + 150f, st.y + 35f, mkPaint(12f, Color.WHITE, bold = true))

        // Consigne gestion
        val consigneText = "Consigne : ${score.consigneGestion.name.replace("_", " ")}"
        c.drawText(consigneText, MARGIN + 140f, st.y + 60f,
            mkPaint(9f, Color.parseColor("#424242")))

        // Scores positifs / pénalités
        val posP = mkPaint(9f, COLOR_POSITIVE)
        val penP = mkPaint(9f, COLOR_PENALTY)
        c.drawText("✔ Score positif : ${score.scorePositif}", PAGE_W - MARGIN - 120f, st.y + 30f, posP)
        c.drawText("✘ Pénalités : ${score.scorePenalite}", PAGE_W - MARGIN - 120f, st.y + 48f, penP)
        st.y += 88f

        // ── Barre de progression ─────────────────────────────────────────────
        val pct = (score.scoreTotal.coerceAtLeast(0) / 100f).coerceAtMost(1f)
        c.drawRoundRect(MARGIN, st.y, PAGE_W - MARGIN, st.y + 8f, 4f, 4f,
            Paint().apply { color = Color.parseColor("#E0E0E0") })
        if (pct > 0f)
            c.drawRoundRect(MARGIN, st.y, MARGIN + (PAGE_W - 2 * MARGIN) * pct, st.y + 8f, 4f, 4f,
                Paint().apply { color = foncColor; isAntiAlias = true })
        st.y += 20f

        // ── Informations terrain ─────────────────────────────────────────────
        st.sectionTitle("Informations terrain")
        if (parcelleNom.isNotBlank()) st.kv("Parcelle", parcelleNom)
        if (obs.observerName.isNotBlank()) st.kv("Observateur", obs.observerName)
        st.kv("Date", DATE_FMT.format(Date(obs.observationDate)))
        st.kv("Longueur section", "${obs.sectionLengthM.toInt()} m")
        if (obs.latitude != null && obs.longitude != null)
            st.kv("GPS", "%.5f N, %.5f E".format(obs.latitude, obs.longitude))
        if (obs.altitudeM != null) st.kv("Altitude", "${obs.altitudeM.toInt()} m")

        // ── Critères positifs ────────────────────────────────────────────────
        st.sectionTitle("Critères positifs — Score ${score.scorePositif} / 70 pts")
        drawCritere(st, "1 — Continuité boisée", score.scoreContinuite, 30, obs.continuitePct.toInt().toString() + "% houppiers")
        drawCritere(st, "2 — Largeur de la bande", score.scoreLargeur, 20, obs.largeurMode.label)
        val nbStrates = listOf(obs.strateHerbacee, obs.strateArbustive, obs.strateArborescente).count { it }
        drawCritere(st, "3 — Nombre de strates", score.scoreStrates, 20, "$nbStrates strate(s) présente(s)")
        drawCritere(st, "4 — Diversité spécifique", score.scoreDiversite, 10, "${obs.nbEspecesObservees} espèce(s) observée(s)")
        val nbClasses = listOf(obs.hasTresPetitBois, obs.hasPetitBois, obs.hasMoyenBois, obs.hasGrosBois).count { it }
        drawCritere(st, "5 — Classes de diamètre", score.scoreDiametres, 10, "$nbClasses classe(s) présente(s)")
        val nbMicro = listOf(obs.microhabitatCavites, obs.microhabitatFissures, obs.microhabitatDecollementEcorce,
            obs.microhabitatChampignons, obs.microhabitatBoisMort, obs.microhabitatTresGrosBois).count { it }
        drawCritere(st, "6 — Microhabitats", score.scoreMicrohabitats, 10, "$nbMicro type(s)")

        // ── Pénalités ────────────────────────────────────────────────────────
        st.sectionTitle("Facteurs de dégradation — Pénalité ${score.scorePenalite} pts")
        drawCritere(st, "7 — État sanitaire", score.scoreSanitaire, 0, "${obs.sanitairePct.toInt()}% couvert atteint", penalty = true)
        drawCritere(st, "8 — Espèces invasives", score.scoreInvasives, 0, "${obs.invasivesPct.toInt()}% recouvrement", penalty = true)
        if (obs.invasivesIdentifiees.isNotEmpty()) {
            obs.invasivesIdentifiees.take(4).forEach { sp ->
                st.bullet("·", sp, Color.DKGRAY, indent = 20f)
            }
        }
        drawCritere(st, "9 — Espèces inadaptées", score.scoreInadaptees, 0, obs.inadapteesMode.label, penalty = true)
        drawCritere(st, "10 — Stabilité des berges", score.scoreStabilite, 0, "${obs.stabilitePct.toInt()}% arbres instables", penalty = true)

        // ── Actions prioritaires ─────────────────────────────────────────────
        st.sectionTitle("Actions prioritaires recommandées")
        val actions = buildPriorityActions(score)
        if (actions.isEmpty()) {
            st.bullet("✔", "Aucune action urgente — maintenir la gestion actuelle.", COLOR_POSITIVE)
        } else {
            actions.forEach { (label, gain) ->
                st.bullet("→", "$label  (+$gain pts potentiels)", COLOR_PRIMARY)
            }
        }

        // ── Espèces invasives ────────────────────────────────────────────────
        if (obs.especesObservees.isNotEmpty()) {
            st.sectionTitle("Espèces observées (${obs.especesObservees.size})")
            obs.especesObservees.take(12).forEach { sp ->
                st.bullet("·", sp, COLOR_NEUTRAL)
            }
            if (obs.especesObservees.size > 12)
                st.bullet("…", "${obs.especesObservees.size - 12} autre(s) non affichée(s)", Color.GRAY)
        }

        // ── Synthèse textuelle ───────────────────────────────────────────────
        st.sectionTitle("Synthèse automatique")
        val synth = score.generateSummary()
        wrapText(st, synth, mkPaint(8.5f, Color.parseColor("#424242")), MARGIN, CONTENT_W.toFloat())

        // ── Notes de terrain ─────────────────────────────────────────────────
        if (obs.sectionNotes.isNotBlank() || obs.globalNotes.isNotBlank()) {
            st.sectionTitle("Notes terrain")
            val notes = listOf(obs.sectionNotes, obs.globalNotes).filter { it.isNotBlank() }.joinToString(" | ")
            wrapText(st, notes, mkPaint(8f, Color.DKGRAY), MARGIN, CONTENT_W.toFloat())
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    private fun drawCritere(
        st: St, label: String, score: Int, maxPositif: Int, detail: String,
        penalty: Boolean = false
    ) {
        st.checkBreak(18f)
        val c = st.canvas ?: return
        val barW = CONTENT_W * 0.35f
        val barX = PAGE_W - MARGIN - barW

        // Label
        c.drawText(label, MARGIN, st.y + 1f, mkPaint(8.5f, Color.parseColor("#212121"), bold = true))
        c.drawText(detail, MARGIN, st.y + 11f, mkPaint(7.5f, Color.DKGRAY))

        // Barre score
        if (!penalty && maxPositif > 0) {
            val pct = (score.toFloat() / maxPositif).coerceIn(0f, 1f)
            val barColor = when {
                pct >= 0.8f -> COLOR_POSITIVE
                pct >= 0.5f -> Color.parseColor("#F9A825")
                else        -> Color.parseColor("#E65100")
            }
            c.drawRoundRect(barX, st.y - 4f, barX + barW, st.y + 6f, 3f, 3f,
                Paint().apply { color = Color.parseColor("#E0E0E0") })
            if (pct > 0f)
                c.drawRoundRect(barX, st.y - 4f, barX + barW * pct, st.y + 6f, 3f, 3f,
                    Paint().apply { color = barColor; isAntiAlias = true })
            c.drawText("$score / $maxPositif", PAGE_W - MARGIN, st.y + 5f,
                mkPaint(8f, barColor, bold = true).apply { textAlign = Paint.Align.RIGHT })
        } else {
            val penColor = if (score < 0) COLOR_PENALTY else COLOR_POSITIVE
            c.drawText("$score pts", PAGE_W - MARGIN, st.y + 5f,
                mkPaint(8f, penColor, bold = true).apply { textAlign = Paint.Align.RIGHT })
        }
        st.y += 18f
    }

    private fun buildPriorityActions(score: RipisylveScore): List<Pair<String, Int>> {
        val actions = mutableListOf<Pair<String, Int>>()
        if (score.scoreContinuite < 20)
            actions += "Améliorer la continuité boisée (plantation, régénération)" to (20 - score.scoreContinuite)
        if (score.scoreLargeur < 20)
            actions += "Élargir la bande ripisylve (objectif ≥ 5 m)" to (20 - score.scoreLargeur)
        if (score.scoreStrates < 20)
            actions += "Diversifier les strates (herbacée, arbustive, arborescente)" to (20 - score.scoreStrates)
        if (score.scoreInvasives < 0)
            actions += "Lutter contre les espèces invasives (coupe, arrachage)" to (-score.scoreInvasives)
        if (score.scoreSanitaire < 0)
            actions += "Traiter les arbres dépérissants, réduire le stress sanitaire" to (-score.scoreSanitaire)
        if (score.scoreStabilite < 0)
            actions += "Renforcer les berges, éliminer les arbres instables" to (-score.scoreStabilite)
        if (score.scoreMicrohabitats < 10)
            actions += "Conserver et favoriser les dendromicrohabitats" to (10 - score.scoreMicrohabitats)
        return actions.sortedByDescending { it.second }.take(5)
    }

    private fun wrapText(st: St, text: String, p: Paint, x: Float, maxW: Float) {
        val words = text.split(" ")
        var line = ""
        for (word in words) {
            val test = if (line.isEmpty()) word else "$line $word"
            if (p.measureText(test) > maxW) {
                if (line.isNotEmpty()) {
                    st.checkBreak(14f)
                    st.canvas?.drawText(line, x, st.y, p)
                    st.y += 13f
                }
                line = word
            } else { line = test }
        }
        if (line.isNotEmpty()) {
            st.checkBreak(14f)
            st.canvas?.drawText(line, x, st.y, p)
            st.y += 13f
        }
    }

    private fun mkPaint(size: Float, color: Int, bold: Boolean = false) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
}
