package com.forestry.counter.domain.usecase.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.forestry.counter.R
import com.forestry.counter.domain.calculation.ProductBreakdownRow
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.calculation.BiodiversityIndex
import com.forestry.counter.domain.calculation.MartelageStats
import com.forestry.counter.domain.calculation.SpecialTreeEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Génère un PDF professionnel de synthèse de martelage à partir de [MartelageStats].
 * Utilise l'API native [android.graphics.pdf.PdfDocument] — aucune dépendance tierce.
 */
object PdfSynthesisExporter {

    private const val PAGE_W = 595  // A4 portrait (pt)
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val CONTENT_W = PAGE_W - 2 * MARGIN.toInt()

    fun export(
        context: Context,
        uri: Uri,
        stats: MartelageStats,
        scopeLabel: String,
        surfaceM2: Double?,
        parcelle: Parcelle? = null,
        productBreakdown: Map<String, List<ProductBreakdownRow>> = emptyMap()
    ) {
        val doc = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            val y = drawContent(canvas, context, stats, scopeLabel, surfaceM2, parcelle, productBreakdown)

            // Footer
            val footerPaint = paint(9f, Color.GRAY)
            val footer = context.getString(R.string.pdf_footer)
            canvas.drawText(footer, PAGE_W / 2f, PAGE_H - 24f, footerPaint.apply { textAlign = Paint.Align.CENTER })

            doc.finishPage(page)

            // Écriture via ContentResolver
            context.contentResolver.openOutputStream(uri)?.use { os ->
                doc.writeTo(os)
            }
        } finally {
            doc.close()
        }
    }

    // ── Dessin du contenu ──────────────────────────────────

    private fun drawContent(
        canvas: Canvas,
        ctx: Context,
        s: MartelageStats,
        scopeLabel: String,
        surfaceM2: Double?,
        parcelle: Parcelle? = null,
        productBreakdown: Map<String, List<ProductBreakdownRow>> = emptyMap()
    ): Float {
        var y = MARGIN + 10f

        // Titre
        val titlePaint = paint(18f, Color.parseColor("#2E7D32"), bold = true)
        titlePaint.textAlign = Paint.Align.CENTER
        canvas.drawText(ctx.getString(R.string.pdf_title), PAGE_W / 2f, y, titlePaint)
        y += 8f

        // Ligne décorative
        val linePaint = Paint().apply { color = Color.parseColor("#4CAF50"); strokeWidth = 2f }
        canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, linePaint)
        y += 20f

        // Date
        val now = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val datePaint = paint(9f, Color.GRAY)
        datePaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(ctx.getString(R.string.pdf_generated_at, now), PAGE_W - MARGIN, y, datePaint)
        y += 22f

        // Section : informations générales
        val sectionPaint = paint(13f, Color.parseColor("#2E7D32"), bold = true)
        val labelPaint = paint(10f, Color.DKGRAY)
        val valuePaint = paint(10f, Color.BLACK, bold = true)

        y = drawKvRow(canvas, y, ctx.getString(R.string.pdf_scope), scopeLabel, labelPaint, valuePaint)
        if (surfaceM2 != null && surfaceM2 > 0.0) {
            val surfaceHa = surfaceM2 / 10_000.0
            val surfaceStr = if (surfaceHa >= 1.0) fmt1(surfaceHa) + " ha" else fmt0(surfaceM2) + " m²"
            y = drawKvRow(canvas, y, ctx.getString(R.string.pdf_surface), surfaceStr, labelPaint, valuePaint)
        }

        // Info parcelle
        if (parcelle != null) {
            y += 6f
            canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, thinLine())
            y += 14f
            canvas.drawText("Parcelle", MARGIN, y, sectionPaint)
            y += 18f
            y = drawKvRow(canvas, y, "Nom", parcelle.name, labelPaint, valuePaint)
            parcelle.surfaceHa?.let { y = drawKvRow(canvas, y, "Surface", "${fmt2(it)} ha", labelPaint, valuePaint) }
            parcelle.slopePct?.let { y = drawKvRow(canvas, y, "Pente", "${fmt1(it)} %", labelPaint, valuePaint) }
            parcelle.aspect?.let { y = drawKvRow(canvas, y, "Exposition", it, labelPaint, valuePaint) }
            parcelle.altitudeM?.let { y = drawKvRow(canvas, y, "Altitude", "${fmt0(it)} m", labelPaint, valuePaint) }
            parcelle.access?.let { y = drawKvRow(canvas, y, "Accès", it, labelPaint, valuePaint) }
            parcelle.remarks?.let { y = drawKvRow(canvas, y, "Remarques", it, labelPaint, valuePaint) }
        }

        y += 6f
        canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, thinLine())
        y += 14f

        // Section : résultats dendrométriques
        canvas.drawText("Dendrométrie", MARGIN, y, sectionPaint)
        y += 18f

        y = drawKvRow(canvas, y, ctx.getString(R.string.pdf_stems), "${s.nTotal}  (${fmt1(s.nPerHa)} /ha)", labelPaint, valuePaint)
        y = drawKvRow(canvas, y, ctx.getString(R.string.pdf_volume), "${fmt3(s.vTotal)} m³", labelPaint, valuePaint)
        y = drawKvRow(canvas, y, ctx.getString(R.string.pdf_volume_ha), "${fmt3(s.vPerHa)} m³/ha", labelPaint, valuePaint)
        y = drawKvRow(canvas, y, ctx.getString(R.string.pdf_basal_area), "${fmt3(s.gTotal)} m²", labelPaint, valuePaint)
        y = drawKvRow(canvas, y, ctx.getString(R.string.pdf_basal_area_ha), "${fmt3(s.gPerHa)} m²/ha", labelPaint, valuePaint)

        s.dm?.let { y = drawKvRow(canvas, y, ctx.getString(R.string.pdf_dm), "${fmt1(it)} cm", labelPaint, valuePaint) }
        s.dg?.let { y = drawKvRow(canvas, y, ctx.getString(R.string.pdf_dg), "${fmt1(it)} cm", labelPaint, valuePaint) }
        s.meanH?.let { y = drawKvRow(canvas, y, ctx.getString(R.string.pdf_h_mean), "${fmt1(it)} m", labelPaint, valuePaint) }
        s.hLorey?.let { y = drawKvRow(canvas, y, ctx.getString(R.string.pdf_h_lorey), "${fmt1(it)} m", labelPaint, valuePaint) }
        if (s.dMin != null && s.dMax != null) {
            y = drawKvRow(canvas, y, "Dmin – Dmax", "${fmt1(s.dMin)} – ${fmt1(s.dMax)} cm", labelPaint, valuePaint)
        }
        s.cvDiam?.let { y = drawKvRow(canvas, y, "CV diamètres", "${fmt0(it)} %", labelPaint, valuePaint) }
        s.ratioVG?.let { y = drawKvRow(canvas, y, "V/G", fmt1(it), labelPaint, valuePaint) }

        if (s.revenueTotal != null && s.revenueTotal > 0.0) {
            y += 6f
            canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, thinLine())
            y += 14f
            canvas.drawText("Valorisation", MARGIN, y, sectionPaint)
            y += 18f
            y = drawKvRow(canvas, y, ctx.getString(R.string.pdf_revenue), CurrencyFormatter.format(s.revenueTotal), labelPaint, valuePaint)
            s.revenuePerHa?.let { y = drawKvRow(canvas, y, ctx.getString(R.string.pdf_revenue_ha), "${CurrencyFormatter.format(it)}/ha", labelPaint, valuePaint) }
        }

        // ── Tableau par essence ──
        if (s.perEssence.isNotEmpty()) {
            y += 10f
            canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, thinLine())
            y += 16f
            canvas.drawText(ctx.getString(R.string.pdf_per_essence_title), MARGIN, y, sectionPaint)
            y += 18f
            y = drawEssenceTable(canvas, ctx, s, y)
        }

        // ── Ventilation par produit ──
        val allBreakdownRows = productBreakdown.values.flatten()
        if (allBreakdownRows.isNotEmpty()) {
            y += 10f
            canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, thinLine())
            y += 16f
            canvas.drawText(ctx.getString(R.string.product_breakdown_title), MARGIN, y, sectionPaint)
            y += 18f
            y = drawProductTable(canvas, ctx, productBreakdown, s, y)
        }

        // ── Arbres spéciaux (avec détails) ──
        if (s.specialTrees.isNotEmpty()) {
            y += 10f
            canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, thinLine())
            y += 16f
            canvas.drawText(ctx.getString(R.string.martelage_special_trees_title), MARGIN, y, sectionPaint)
            y += 18f

            val detailPaint = paint(8f, Color.DKGRAY)
            val catPaint = paint(10f, Color.BLACK, bold = true)

            s.specialTrees.forEach { entry ->
                val catLabel = when (entry.categorie) {
                    "DEPERISSANT" -> ctx.getString(R.string.special_tree_dying)
                    "ARBRE_BIO" -> ctx.getString(R.string.special_tree_bio)
                    "MORT" -> ctx.getString(R.string.special_tree_dead)
                    "PARASITE" -> ctx.getString(R.string.special_tree_parasite)
                    else -> entry.categorie
                }
                // Category header
                canvas.drawText("$catLabel (${entry.count})", MARGIN, y, catPaint)
                y += 14f

                // Per-tree details
                entry.trees.forEach { tree ->
                    val line = buildString {
                        append("  \u2022 ")
                        append(tree.essenceName)
                        append(" \u2014 \u2300 ")
                        append(fmt0(tree.diamCm))
                        append(" cm")
                        tree.hauteurM?.let {
                            append(" \u2014 H ")
                            append(fmt1(it))
                            append(" m")
                        }
                        if (!tree.defauts.isNullOrEmpty()) {
                            append(" \u2014 ")
                            append(tree.defauts.joinToString(", "))
                        }
                        if (!tree.note.isNullOrBlank()) {
                            append(" \u2014 ")
                            append(tree.note)
                        }
                        if (tree.hasGps) append(" \uD83D\uDCCD")
                    }
                    canvas.drawText(line, MARGIN + 8f, y, detailPaint)
                    y += 12f
                }
                y += 4f
            }
        }

        // ── Biodiversité ──
        s.biodiversity?.let { bio ->
            y += 10f
            canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, thinLine())
            y += 16f
            canvas.drawText(ctx.getString(R.string.biodiversity_title), MARGIN, y, sectionPaint)
            y += 18f
            y = drawKvRow(canvas, y, "Shannon H'", fmt2(bio.shannonH), labelPaint, valuePaint)
            bio.pielou?.let { y = drawKvRow(canvas, y, "Piélou J", fmt2(it), labelPaint, valuePaint) }
            y = drawKvRow(canvas, y, ctx.getString(R.string.biodiversity_species), bio.speciesCount.toString(), labelPaint, valuePaint)
            y = drawKvRow(canvas, y, "IBP", "${bio.ibpScore}/${bio.ibpMax}", labelPaint, valuePaint)
            if (bio.tgbCount > 0) y = drawKvRow(canvas, y, "TGB \u226570cm", bio.tgbCount.toString(), labelPaint, valuePaint)
            if (bio.bioTreeCount > 0) y = drawKvRow(canvas, y, ctx.getString(R.string.special_tree_bio), bio.bioTreeCount.toString(), labelPaint, valuePaint)
            if (bio.deadTreeCount > 0) y = drawKvRow(canvas, y, ctx.getString(R.string.special_tree_dead), bio.deadTreeCount.toString(), labelPaint, valuePaint)
            if (bio.dyingTreeCount > 0) y = drawKvRow(canvas, y, ctx.getString(R.string.special_tree_dying), bio.dyingTreeCount.toString(), labelPaint, valuePaint)
        }

        return y
    }

    // ── Tableau par essence ───────────────────────────────

    private fun drawEssenceTable(canvas: Canvas, ctx: Context, s: MartelageStats, startY: Float): Float {
        val headers = listOf(
            ctx.getString(R.string.pdf_col_essence),
            ctx.getString(R.string.pdf_col_n),
            ctx.getString(R.string.pdf_col_v),
            ctx.getString(R.string.pdf_col_v_ha),
            ctx.getString(R.string.pdf_col_g),
            ctx.getString(R.string.pdf_col_g_ha),
            ctx.getString(R.string.pdf_col_price),
            ctx.getString(R.string.pdf_col_revenue)
        )

        // Largeurs colonnes (proportionnelles sur CONTENT_W)
        val colW = floatArrayOf(
            CONTENT_W * 0.22f,  // Essence
            CONTENT_W * 0.07f,  // N
            CONTENT_W * 0.11f,  // V
            CONTENT_W * 0.10f,  // V/ha
            CONTENT_W * 0.10f,  // G
            CONTENT_W * 0.10f,  // G/ha
            CONTENT_W * 0.12f,  // €/m³
            CONTENT_W * 0.18f   // Recettes
        )

        val rowH = 16f
        val headerPaint = paint(8f, Color.WHITE, bold = true)
        val cellPaint = paint(8f, Color.DKGRAY)
        val cellBoldPaint = paint(8f, Color.BLACK, bold = true)
        val headerBg = Paint().apply { color = Color.parseColor("#388E3C"); style = Paint.Style.FILL }
        val stripeBg = Paint().apply { color = Color.parseColor("#F1F8E9"); style = Paint.Style.FILL }

        var y = startY

        // Header background
        canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + rowH, headerBg)
        var x = MARGIN + 4f
        for (i in headers.indices) {
            val align = if (i == 0) Paint.Align.LEFT else Paint.Align.RIGHT
            val xText = if (i == 0) x else x + colW[i] - 4f
            headerPaint.textAlign = align
            canvas.drawText(headers[i], xText, y + 12f, headerPaint)
            x += colW[i]
        }
        y += rowH

        // Rows
        s.perEssence.forEachIndexed { idx, row ->
            if (idx % 2 == 0) {
                canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + rowH, stripeBg)
            }
            x = MARGIN + 4f
            val cells = listOf(
                row.essenceName,
                row.n.toString(),
                fmt2(row.vTotal),
                fmt2(row.vPerHa),
                fmt3(row.gTotal),
                fmt3(row.gPerHa),
                row.meanPricePerM3?.let { fmt0(it) } ?: "–",
                row.revenueTotal?.let { fmt0(it) } ?: "–"
            )
            for (i in cells.indices) {
                val p = if (i == 0) cellBoldPaint else cellPaint
                val align = if (i == 0) Paint.Align.LEFT else Paint.Align.RIGHT
                val xText = if (i == 0) x else x + colW[i] - 4f
                p.textAlign = align
                canvas.drawText(cells[i], xText, y + 12f, p)
                x += colW[i]
            }
            y += rowH
        }

        // Totals row
        val totalBg = Paint().apply { color = Color.parseColor("#E8F5E9"); style = Paint.Style.FILL }
        canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + rowH, totalBg)
        x = MARGIN + 4f
        val totals = listOf(
            "TOTAL",
            s.nTotal.toString(),
            fmt2(s.vTotal),
            fmt2(s.vPerHa),
            fmt3(s.gTotal),
            fmt3(s.gPerHa),
            "",
            s.revenueTotal?.let { fmt0(it) } ?: "–"
        )
        for (i in totals.indices) {
            val xText = if (i == 0) x else x + colW[i] - 4f
            cellBoldPaint.textAlign = if (i == 0) Paint.Align.LEFT else Paint.Align.RIGHT
            canvas.drawText(totals[i], xText, y + 12f, cellBoldPaint)
            x += colW[i]
        }
        y += rowH + 2f

        // Bordure extérieure
        val border = Paint().apply { color = Color.parseColor("#388E3C"); style = Paint.Style.STROKE; strokeWidth = 1f }
        canvas.drawRect(MARGIN, startY, PAGE_W - MARGIN, y, border)

        return y
    }

    // ── Tableau ventilation produits ─────────────────────

    private fun drawProductTable(
        canvas: Canvas,
        ctx: Context,
        breakdown: Map<String, List<ProductBreakdownRow>>,
        s: MartelageStats,
        startY: Float
    ): Float {
        val headers = listOf(
            ctx.getString(R.string.pdf_col_essence),
            ctx.getString(R.string.product_col_product),
            ctx.getString(R.string.product_col_vol),
            "${CurrencyFormatter.symbol}/m³",
            ctx.getString(R.string.product_col_total)
        )
        val colW = floatArrayOf(
            CONTENT_W * 0.25f,
            CONTENT_W * 0.25f,
            CONTENT_W * 0.16f,
            CONTENT_W * 0.16f,
            CONTENT_W * 0.18f
        )
        val rowH = 15f
        val headerPaint = paint(8f, Color.WHITE, bold = true)
        val cellPaint = paint(8f, Color.DKGRAY)
        val cellBoldPaint = paint(8f, Color.BLACK, bold = true)
        val headerBg = Paint().apply { color = Color.parseColor("#1565C0"); style = Paint.Style.FILL }
        val stripeBg = Paint().apply { color = Color.parseColor("#E3F2FD"); style = Paint.Style.FILL }

        var y = startY

        // Header
        canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + rowH, headerBg)
        var x = MARGIN + 4f
        for (i in headers.indices) {
            val align = if (i == 0) Paint.Align.LEFT else Paint.Align.RIGHT
            val xText = if (i == 0) x else x + colW[i] - 4f
            headerPaint.textAlign = align
            canvas.drawText(headers[i], xText, y + 11f, headerPaint)
            x += colW[i]
        }
        y += rowH

        // Rows grouped by essence
        var rowIdx = 0
        val essenceNameMap = s.perEssence.associate { it.essenceCode to it.essenceName }
        breakdown.entries.sortedBy { essenceNameMap[it.key] ?: it.key }.forEach { (essCode, rows) ->
            val essName = essenceNameMap[essCode] ?: essCode
            rows.filter { it.volumeM3 > 0.0 }.forEach { row ->
                if (rowIdx % 2 == 0) {
                    canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + rowH, stripeBg)
                }
                x = MARGIN + 4f
                val cells = listOf(
                    essName,
                    row.product,
                    fmt2(row.volumeM3),
                    fmt0(row.pricePerM3),
                    fmt0(row.totalEur) + " " + CurrencyFormatter.symbol
                )
                for (i in cells.indices) {
                    val p = if (i == 0) cellBoldPaint else cellPaint
                    val align = if (i == 0) Paint.Align.LEFT else Paint.Align.RIGHT
                    val xText = if (i == 0) x else x + colW[i] - 4f
                    p.textAlign = align
                    canvas.drawText(cells[i], xText, y + 11f, p)
                    x += colW[i]
                }
                y += rowH
                rowIdx++
            }
        }

        // Total row
        val totalEur = breakdown.values.flatten().sumOf { it.totalEur }
        val totalVol = breakdown.values.flatten().sumOf { it.volumeM3 }
        val totalBg = Paint().apply { color = Color.parseColor("#BBDEFB"); style = Paint.Style.FILL }
        canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + rowH, totalBg)
        x = MARGIN + 4f
        val totals = listOf("TOTAL", "", fmt2(totalVol), "", fmt0(totalEur) + " " + CurrencyFormatter.symbol)
        for (i in totals.indices) {
            val xText = if (i == 0) x else x + colW[i] - 4f
            cellBoldPaint.textAlign = if (i == 0) Paint.Align.LEFT else Paint.Align.RIGHT
            canvas.drawText(totals[i], xText, y + 11f, cellBoldPaint)
            x += colW[i]
        }
        y += rowH + 2f

        // Border
        val border = Paint().apply { color = Color.parseColor("#1565C0"); style = Paint.Style.STROKE; strokeWidth = 1f }
        canvas.drawRect(MARGIN, startY, PAGE_W - MARGIN, y, border)

        return y
    }

    // ── Helpers ────────────────────────────────────────────

    private fun drawKvRow(canvas: Canvas, y: Float, label: String, value: String, lp: Paint, vp: Paint): Float {
        lp.textAlign = Paint.Align.LEFT
        vp.textAlign = Paint.Align.RIGHT
        canvas.drawText(label, MARGIN, y, lp)
        canvas.drawText(value, PAGE_W - MARGIN, y, vp)
        return y + 16f
    }

    private fun paint(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    }

    private fun thinLine() = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f }

    private fun fmt0(v: Double) = String.format(Locale.getDefault(), "%.0f", v)
    private fun fmt1(v: Double) = String.format(Locale.getDefault(), "%.1f", v)
    private fun fmt2(v: Double) = String.format(Locale.getDefault(), "%.2f", v)
    private fun fmt3(v: Double) = String.format(Locale.getDefault(), "%.3f", v)
}
