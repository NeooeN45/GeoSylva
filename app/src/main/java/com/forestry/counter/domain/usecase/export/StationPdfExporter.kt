package com.forestry.counter.domain.usecase.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.forestry.counter.domain.model.station.Drainage
import com.forestry.counter.domain.model.station.StationObservation
import com.forestry.counter.domain.usecase.autecology.CompatibilityLevel
import com.forestry.counter.domain.usecase.station.StationDiagnosticEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Génère un PDF professionnel du diagnostic stationnel.
 * Style cohérent avec PdfSynthesisExporter / RipisylvePdfExporter.
 */
object StationPdfExporter {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val CONTENT_W = (PAGE_W - 2 * MARGIN).toInt()
    private const val BOTTOM_MARGIN = 55f
    private val DATE_FMT = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    // ── Palette station (terracotta / sol) ────────────────────────────────────
    private val COLOR_HEADER    = Color.parseColor("#4E342E")
    private val COLOR_SECTION   = Color.parseColor("#5D4037")
    private val COLOR_POSITIVE  = Color.parseColor("#2E7D32")
    private val COLOR_WARNING   = Color.parseColor("#E65100")
    private val COLOR_ALERT     = Color.parseColor("#C62828")
    private val COLOR_NEUTRAL   = Color.parseColor("#37474F")

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
                val fp = mkP(8f, Color.GRAY).apply { textAlign = Paint.Align.CENTER }
                c.drawText("Diagnostic Stationnel — GeoSylva", PAGE_W / 2f, PAGE_H - 22f, fp)
                val pp = mkP(8f, Color.GRAY).apply { textAlign = Paint.Align.RIGHT }
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
            canvas?.drawText(title, MARGIN, y, mkP(12f, COLOR_SECTION, bold = true))
            y += 16f
        }

        fun kv(label: String, value: String, valueColor: Int = Color.BLACK) {
            checkBreak(16f)
            canvas?.drawText(label, MARGIN, y, mkP(9f, Color.DKGRAY))
            canvas?.drawText(value, PAGE_W - MARGIN, y,
                mkP(9f, valueColor, bold = true).apply { textAlign = Paint.Align.RIGHT })
            y += 14f
        }

        fun bullet(icon: String, text: String, color: Int, indent: Float = 0f) {
            checkBreak(14f)
            canvas?.drawText(icon, MARGIN + indent, y, mkP(9f, color, bold = true))
            canvas?.drawText(text, MARGIN + indent + 14f, y, mkP(8.5f, Color.DKGRAY))
            y += 13f
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    fun export(
        context: Context,
        uri: Uri,
        obs: StationObservation,
        parcelleNom: String = ""
    ) {
        val result = StationDiagnosticEngine.diagnose(obs)
        val doc = PdfDocument()
        try {
            val st = St(doc)
            st.startPage()
            drawContent(st, obs, result, parcelleNom)
            st.finishPage()
            context.contentResolver.openOutputStream(uri)?.use { os -> doc.writeTo(os) }
        } finally {
            doc.close()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    private fun drawContent(
        st: St, obs: StationObservation,
        result: StationDiagnosticEngine.StationResult,
        parcelleNom: String
    ) {
        val c = st.canvas ?: return

        // ── Bandeau en-tête ──────────────────────────────────────────────────
        val hBg = Paint().apply { color = COLOR_HEADER; style = Paint.Style.FILL }
        c.drawRect(0f, 0f, PAGE_W.toFloat(), 80f, hBg)
        c.drawText("Diagnostic Stationnel", MARGIN, 34f,
            mkP(20f, Color.WHITE, bold = true))
        c.drawText("Analyse pédologique, gradients écologiques & compatibilité essences", MARGIN, 54f,
            mkP(10f, Color.WHITE).apply { alpha = 210 })
        val brandP = mkP(9f, Color.WHITE).apply { alpha = 180; textAlign = Paint.Align.RIGHT }
        c.drawText("GeoSylva", PAGE_W - MARGIN, 34f, brandP)
        c.drawText(DATE_FMT.format(Date()), PAGE_W - MARGIN, 52f, brandP)
        st.y = 95f

        // ── Bandeau type de station ───────────────────────────────────────────
        val confColor = when (result.confidence) {
            StationDiagnosticEngine.DiagConfidenceStation.FORTE   -> COLOR_POSITIVE
            StationDiagnosticEngine.DiagConfidenceStation.MOYENNE -> COLOR_WARNING
            StationDiagnosticEngine.DiagConfidenceStation.FAIBLE  -> COLOR_ALERT
        }
        val stBg = Paint().apply { color = confColor; alpha = 20; style = Paint.Style.FILL }
        val stBorder = Paint().apply { color = confColor; style = Paint.Style.STROKE; strokeWidth = 2f }
        c.drawRoundRect(MARGIN, st.y, PAGE_W - MARGIN, st.y + 60f, 10f, 10f, stBg)
        c.drawRoundRect(MARGIN, st.y, PAGE_W - MARGIN, st.y + 60f, 10f, 10f, stBorder)
        c.drawText("Type de station", MARGIN + 14f, st.y + 18f,
            mkP(8.5f, Color.DKGRAY))
        c.drawText(result.typeStation, MARGIN + 14f, st.y + 38f,
            mkP(13f, confColor, bold = true))
        c.drawText("Confiance : ${result.confidence.labelFr}", PAGE_W - MARGIN - 4f, st.y + 28f,
            mkP(9f, confColor).apply { textAlign = Paint.Align.RIGHT })
        st.y += 74f

        // ── Informations terrain ─────────────────────────────────────────────
        st.sectionTitle("Informations terrain")
        if (parcelleNom.isNotBlank()) st.kv("Parcelle", parcelleNom)
        if (obs.observerName.isNotBlank()) st.kv("Observateur", obs.observerName)
        st.kv("Date", DATE_FMT.format(Date(obs.observationDate)))
        if (obs.commune.isNotBlank()) st.kv("Commune", obs.commune)
        if (obs.latitude != null && obs.longitude != null)
            st.kv("GPS", "%.5f N, %.5f E".format(obs.latitude, obs.longitude))
        obs.altitudeM?.let { st.kv("Altitude", "${it.toInt()} m") }

        // ── Topographie ──────────────────────────────────────────────────────
        st.sectionTitle("Topographie")
        st.kv("Position topographique", obs.positionTopo.labelFr)
        st.kv("Exposition", obs.exposition.labelFr)
        obs.pentePct?.let { st.kv("Pente", "${it.toInt()} %") }
        obs.distanceCourseauM?.let { st.kv("Distance cours d'eau", "${it.toInt()} m") }

        // ── Pédologie ────────────────────────────────────────────────────────
        st.sectionTitle("Pédologie")
        obs.profondeurSolCm?.let { st.kv("Profondeur du sol", "$it cm") }
        st.kv("Texture", obs.texture.labelFr)
        st.kv("Pierrosité", obs.pierrosite.labelFr)
        st.kv("Humus", obs.humus.labelFr)
        st.kv("Drainage", obs.drainage.labelFr,
            if (obs.drainage == Drainage.NORMAL || obs.drainage == Drainage.BON) COLOR_POSITIVE else COLOR_WARNING)
        st.kv("Test HCl", obs.testHcl.labelFr)
        obs.phEstime?.let { st.kv("pH estimé", "%.1f".format(it)) }
        if (obs.rocheMere.isNotBlank()) st.kv("Roche mère", obs.rocheMere)
        obs.hydromorphieProfondeurCm?.let { st.kv("Hydromorphie à", "$it cm") }

        // ── Gradients écologiques ─────────────────────────────────────────────
        st.sectionTitle("Gradients écologiques (1 = extrême sec/pauvre | 5 = humide/riche)")
        drawGradient(st, "H — Hydrique", obs.gradientHydrique,
            arrayOf("Très sec", "Sec", "Mésophile", "Frais", "Humide"))
        drawGradient(st, "N — Trophique", obs.gradientTrophique,
            arrayOf("Oligotrophe", "Oligo-méso", "Mésotrophe", "Méso-eu", "Eutrophe"))
        drawGradient(st, "L — Lumineux", obs.gradientLumineux,
            arrayOf("Très ombragé", "Ombragé", "Mi-ombre", "Éclairé", "Plein soleil"))
        drawGradient(st, "M — Humique", obs.gradientHumique,
            arrayOf("Mor", "Moder acide", "Moder", "Mull-Moder", "Mull calc."))

        // ── Végétation indicatrice ────────────────────────────────────────────
        if (obs.especesIndicatrices.isNotEmpty()) {
            st.sectionTitle("Espèces indicatrices (${obs.especesIndicatrices.size})")
            obs.especesIndicatrices.take(12).forEach { sp ->
                st.bullet("·", sp, COLOR_NEUTRAL)
            }
            if (obs.especesIndicatrices.size > 12)
                st.bullet("…", "${obs.especesIndicatrices.size - 12} autre(s)", Color.GRAY)
        }

        // ── Contraintes ──────────────────────────────────────────────────────
        val allContraintes = buildList {
            if (result.contrainteHydrique != StationDiagnosticEngine.Contrainte.NULLE &&
                result.contrainteHydrique != StationDiagnosticEngine.Contrainte.FAIBLE)
                add("Contrainte hydrique : ${result.contrainteHydrique.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }}")
            if (result.contrainteTrophique != StationDiagnosticEngine.Contrainte.NULLE &&
                result.contrainteTrophique != StationDiagnosticEngine.Contrainte.FAIBLE)
                add("Contrainte trophique : ${result.contrainteTrophique.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }}")
            if (result.contrainteProfondeur != StationDiagnosticEngine.Contrainte.NULLE &&
                result.contrainteProfondeur != StationDiagnosticEngine.Contrainte.FAIBLE)
                add("Contrainte profondeur : ${result.contrainteProfondeur.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }}")
            addAll(result.contraintes)
        }

        if (result.atouts.isNotEmpty() || allContraintes.isNotEmpty() || result.alertes.isNotEmpty()) {
            st.sectionTitle("Synthèse pédologique")
            result.atouts.forEach { st.bullet("✔", it, COLOR_POSITIVE) }
            allContraintes.forEach { st.bullet("⚠", it, COLOR_WARNING) }
            result.alertes.forEach { st.bullet("✘", it, COLOR_ALERT) }
            if (result.risqueEngorgement) st.bullet("⚠", "Risque d'engorgement du sol (hydromorphie <40 cm)", COLOR_WARNING)
            if (result.risqueDepiecement) st.bullet("⚠", "Risque de dépiècement en période sèche", COLOR_WARNING)
        }

        // ── Essences recommandées ─────────────────────────────────────────────
        if (result.recommendedEssences.isNotEmpty() || result.discouragedEssences.isNotEmpty()) {
            st.sectionTitle("Compatibilité essences")
            if (result.recommendedEssences.isNotEmpty()) {
                st.checkBreak(14f)
                st.canvas?.drawText("Essences favorables :", MARGIN, st.y, mkP(9f, COLOR_POSITIVE, bold = true))
                st.y += 13f
                result.recommendedEssences.chunked(3).forEach { row ->
                    st.bullet("✔", row.joinToString("  |  "), COLOR_POSITIVE)
                }
            }
            if (result.discouragedEssences.isNotEmpty()) {
                st.checkBreak(14f)
                st.canvas?.drawText("Essences déconseillées :", MARGIN, st.y, mkP(9f, COLOR_ALERT, bold = true))
                st.y += 13f
                result.discouragedEssences.chunked(3).forEach { row ->
                    st.bullet("✘", row.joinToString("  |  "), COLOR_ALERT)
                }
            }
        }

        // ── Tableau compatibilité courant ─────────────────────────────────────
        if (result.currentEssencesCompatibility.isNotEmpty()) {
            st.sectionTitle("Compatibilité des essences en place")
            drawCompatibilityTable(st, result.currentEssencesCompatibility)
        }

        // ── Synthèse textuelle ────────────────────────────────────────────────
        st.sectionTitle("Synthèse diagnostique")
        wrapText(st, result.syntheseTextuelle, mkP(8.5f, Color.parseColor("#424242")), MARGIN, CONTENT_W.toFloat())

        // ── Notes libres ──────────────────────────────────────────────────────
        if (obs.notes.isNotBlank()) {
            st.sectionTitle("Notes terrain")
            wrapText(st, obs.notes, mkP(8f, Color.DKGRAY), MARGIN, CONTENT_W.toFloat())
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    private fun drawGradient(st: St, label: String, value: Int, levels: Array<String>) {
        st.checkBreak(22f)
        val c = st.canvas ?: return
        val barW = CONTENT_W * 0.45f
        val barX = PAGE_W - MARGIN - barW
        val stepW = barW / 5f

        c.drawText(label, MARGIN, st.y + 5f, mkP(9f, Color.parseColor("#212121"), bold = true))

        // 5 segments colorés
        val segColors = intArrayOf(
            Color.parseColor("#FF8A65"), Color.parseColor("#FFB74D"),
            Color.parseColor("#A5D6A7"), Color.parseColor("#4CAF50"),
            Color.parseColor("#1B5E20")
        )
        for (i in 0..4) {
            val segP = Paint().apply {
                color = segColors[i]
                alpha = if (i + 1 == value) 255 else 60
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            c.drawRect(barX + i * stepW, st.y - 6f, barX + (i + 1) * stepW - 1f, st.y + 8f, segP)
        }
        // Valeur numérique
        c.drawText(value.toString(), barX + (value - 1) * stepW + stepW / 2f, st.y + 6f,
            mkP(8f, Color.WHITE, bold = true).apply { textAlign = Paint.Align.CENTER })
        // Label du niveau sélectionné
        if (value in 1..5)
            c.drawText(levels[value - 1], MARGIN + 130f, st.y + 5f, mkP(8f, Color.DKGRAY))

        st.y += 18f
    }

    private fun drawCompatibilityTable(
        st: St,
        compatibilities: List<StationDiagnosticEngine.EssenceCompatibilityResult>
    ) {
        val rowH = 15f
        val colEss = CONTENT_W * 0.40f
        val colComp = CONTENT_W * 0.30f
        val colRaison = CONTENT_W * 0.30f
        val hBg = Paint().apply { color = Color.parseColor("#4E342E"); style = Paint.Style.FILL }

        st.checkBreak(rowH + 4f)
        val c = st.canvas ?: return
        c.drawRect(MARGIN, st.y, PAGE_W - MARGIN, st.y + rowH, hBg)
        c.drawText("Essence", MARGIN + 4f, st.y + 11f, mkP(8f, Color.WHITE, bold = true))
        c.drawText("Compatibilité", MARGIN + colEss + 4f, st.y + 11f, mkP(8f, Color.WHITE, bold = true))
        c.drawText("Motif principal", MARGIN + colEss + colComp + 4f, st.y + 11f, mkP(8f, Color.WHITE, bold = true))
        st.y += rowH

        val stripe = Paint().apply { color = Color.parseColor("#EFEBE9"); style = Paint.Style.FILL }
        compatibilities.forEachIndexed { idx, compat ->
            st.checkBreak(rowH + 2f)
            if (idx % 2 == 0) st.canvas?.drawRect(MARGIN, st.y, PAGE_W - MARGIN, st.y + rowH, stripe)
            val compColor = when (compat.compatibility) {
                CompatibilityLevel.OPTIMUM      -> COLOR_POSITIVE
                CompatibilityLevel.TOLERATED    -> COLOR_WARNING
                CompatibilityLevel.INCOMPATIBLE -> COLOR_ALERT
                else                            -> COLOR_WARNING
            }
            st.canvas?.drawText(compat.essenceName, MARGIN + 4f, st.y + 11f,
                mkP(8f, Color.parseColor("#212121"), bold = true))
            st.canvas?.drawText(compat.compatibility.name.lowercase().replaceFirstChar { it.uppercase() },
                MARGIN + colEss + 4f, st.y + 11f, mkP(8f, compColor))
            val raison = compat.reasons.firstOrNull()?.take(32) ?: "—"
            st.canvas?.drawText(raison, MARGIN + colEss + colComp + 4f, st.y + 11f,
                mkP(7.5f, Color.DKGRAY))
            st.y += rowH
        }
        st.y += 4f
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

    private fun mkP(size: Float, color: Int, bold: Boolean = false) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
}
