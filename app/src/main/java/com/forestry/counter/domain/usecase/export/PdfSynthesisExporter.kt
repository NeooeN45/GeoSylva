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
import com.forestry.counter.domain.calculation.SanitySeverity
import com.forestry.counter.domain.calculation.SanityWarning
import com.forestry.counter.domain.calculation.tarifs.TarifMethod
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.usecase.fertility.ConfidenceLevel
import com.forestry.counter.domain.usecase.fertility.FertilityClass
import com.forestry.counter.domain.usecase.fertility.FertilityResult
import com.forestry.counter.presentation.screens.forestry.MartelageStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs

/**
 * Génère un PDF professionnel de synthèse de martelage à partir de [MartelageStats].
 * Supporte le multi-page, la distribution par classe, la fertilité, etc.
 */
object PdfSynthesisExporter {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val CONTENT_W = (PAGE_W - 2 * MARGIN).toInt()
    private const val BOTTOM_MARGIN = 55f

    private class PdfState(val doc: PdfDocument, val ctx: Context) {
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = MARGIN + 10f
        var pageNum = 1

        fun startPage() {
            val info = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create()
            page = doc.startPage(info)
            canvas = page!!.canvas
            y = MARGIN + 10f
        }

        fun finishPage() {
            canvas?.let { c ->
                val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = Color.GRAY; textAlign = Paint.Align.CENTER }
                c.drawText(ctx.getString(R.string.pdf_footer), PAGE_W / 2f, PAGE_H - 22f, fp)
                val pp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = Color.GRAY; textAlign = Paint.Align.RIGHT }
                c.drawText("Page $pageNum", PAGE_W - MARGIN, PAGE_H - 22f, pp)
            }
            page?.let { doc.finishPage(it) }
            page = null; canvas = null; pageNum++
        }

        fun checkBreak(needed: Float = 35f) {
            if (y + needed > PAGE_H - BOTTOM_MARGIN) { finishPage(); startPage() }
        }

        fun sectionDivider(title: String) {
            checkBreak(55f)
            y += 8f
            // Ligne supérieure fine
            canvas?.drawLine(MARGIN, y, PAGE_W - MARGIN, y, Paint().apply { color = Color.parseColor("#E0E0E0"); strokeWidth = 0.8f })
            y += 12f
            // Titre avec fond subtil
            val bgPaint = Paint().apply { color = Color.parseColor("#F1F8E9"); style = Paint.Style.FILL }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
                textSize = 14f; 
                color = Color.parseColor("#2E7D32"); 
                typeface = Typeface.DEFAULT_BOLD 
            }
            canvas?.drawRect(MARGIN, y - 8f, PAGE_W - MARGIN, y + 8f, bgPaint)
            canvas?.drawText(title, MARGIN + 8f, y + 4f, textPaint)
            y += 20f
            // Ligne inférieure
            canvas?.drawLine(MARGIN, y, PAGE_W - MARGIN, y, Paint().apply { color = Color.parseColor("#E0E0E0"); strokeWidth = 0.8f })
            y += 16f
        }

        private var kvRowEven = true
        fun kvRow(label: String, value: String) {
            checkBreak(20f)
            canvas?.let { c ->
                // Fond alterné pour meilleure lisibilité
                if (kvRowEven) {
                    val bgPaint = Paint().apply { color = Color.parseColor("#FAFAFA"); style = Paint.Style.FILL }
                    c.drawRect(MARGIN - 4f, y - 12f, PAGE_W - MARGIN + 4f, y + 4f, bgPaint)
                }
                
                c.drawText(label, MARGIN, y, Paint(Paint.ANTI_ALIAS_FLAG).apply { 
                    textSize = 10f; 
                    color = Color.parseColor("#424242")
                })
                c.drawText(value, PAGE_W - MARGIN, y, Paint(Paint.ANTI_ALIAS_FLAG).apply { 
                    textSize = 10f; 
                    color = Color.BLACK; 
                    typeface = Typeface.DEFAULT_BOLD; 
                    textAlign = Paint.Align.RIGHT 
                })
            }
            y += 18f
            kvRowEven = !kvRowEven
        }
    }

    fun export(
        context: Context,
        uri: Uri,
        stats: MartelageStats,
        scopeLabel: String,
        surfaceM2: Double?,
        parcelle: Parcelle? = null,
        productBreakdown: Map<String, List<ProductBreakdownRow>> = emptyMap(),
        tiges: List<Tige> = emptyList(),
        fertilityResults: List<FertilityResult> = emptyList(),
        tarifMethod: TarifMethod? = null,
        tarifNumero: Int? = null
    ) {
        val doc = PdfDocument()
        try {
            val st = PdfState(doc, context)
            st.startPage()
            drawContent(st, stats, scopeLabel, surfaceM2, parcelle, productBreakdown, tiges, fertilityResults, tarifMethod, tarifNumero)
            st.finishPage()
            context.contentResolver.openOutputStream(uri)?.use { os -> doc.writeTo(os) }
        } finally {
            doc.close()
        }
    }

    private fun drawContent(
        st: PdfState,
        s: MartelageStats,
        scopeLabel: String,
        surfaceM2: Double?,
        parcelle: Parcelle?,
        productBreakdown: Map<String, List<ProductBreakdownRow>>,
        tiges: List<Tige>,
        fertilityResults: List<FertilityResult>,
        tarifMethod: TarifMethod? = null,
        tarifNumero: Int? = null
    ) {
        val c = st.canvas ?: return

        // Titre amélioré avec meilleur visuel
        val tp = paint(20f, Color.parseColor("#1B5E20"), bold = true).apply { textAlign = Paint.Align.CENTER }
        c.drawText(st.ctx.getString(R.string.pdf_title), PAGE_W / 2f, st.y, tp)
        st.y += 10f
        
        // Ligne de séparation plus professionnelle
        val titleLinePaint = Paint().apply { 
            color = Color.parseColor("#4CAF50") 
            strokeWidth = 3f 
        }
        c.drawLine(MARGIN + 50f, st.y, PAGE_W - MARGIN - 50f, st.y, titleLinePaint)
        st.y += 25f

        // Date
        val now = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val dp = paint(9f, Color.GRAY).apply { textAlign = Paint.Align.RIGHT }
        c.drawText(st.ctx.getString(R.string.pdf_generated_at, now), PAGE_W - MARGIN, st.y, dp)
        st.y += 22f

        // ── Informations générales améliorées ──
        st.sectionDivider("Identification du Martelage")
        st.kvRow("Périmètre", scopeLabel)
        if (surfaceM2 != null && surfaceM2 > 0.0) {
            val ha = surfaceM2 / 10_000.0
            st.kvRow("Surface inventoriée", if (ha >= 1.0) "${fmt1(ha)} ha" else "${fmt0(surfaceM2)} m²")
        }

        // Informations GPS (centrales)
        val gpsTiges = tiges.filter { it.gpsWkt != null }
        if (gpsTiges.isNotEmpty()) {
            val avgLat = gpsTiges.mapNotNull { QgisExportHelper.parseWktPointZ(it.gpsWkt)?.lat }.average()
            val avgLon = gpsTiges.mapNotNull { QgisExportHelper.parseWktPointZ(it.gpsWkt)?.lon }.average()
            val avgPrecision = gpsTiges.mapNotNull { it.precisionM }.average()
            st.kvRow("Coordonnées GPS moyennes", "${String.format("%.5f", avgLat)}°N, ${String.format("%.5f", avgLon)}°E")
            st.kvRow("Précision GPS moyenne", "${fmt1(avgPrecision)} m")
            
            // Tenter de récupérer la commune via géocodage inverse (si disponible)
            // Note: Pour l'instant affiché comme info brute, pourra être amélioré avec un service de géocodage
            st.kvRow("Localisation", "GPS - $scopeLabel")
        }

        // Informations parcelle
        if (parcelle != null) {
            st.kvRow("Parcelle", parcelle.name)
            parcelle.surfaceHa?.let { st.kvRow("Surface parcelle", "${fmt2(it)} ha") }
            parcelle.slopePct?.let { st.kvRow("Pente moyenne", "${fmt1(it)} %") }
            parcelle.aspect?.let { st.kvRow("Exposition dominante", it) }
            parcelle.altitudeM?.let { st.kvRow("Altitude moyenne", "${fmt0(it)} m") }
            parcelle.access?.let { st.kvRow("Accès", it) }
        }

        // Informations placette (si disponible)
        val placetteIds = tiges.mapNotNull { it.placetteId }.distinct()
        if (placetteIds.size == 1) {
            st.kvRow("Placette", placetteIds.first())
        } else if (placetteIds.size > 1) {
            st.kvRow("Placettes", "${placetteIds.size} placettes")
        }

        // Tarif de cubage utilisé
        if (tarifMethod != null) {
            val tarifLabel = buildString {
                append(tarifMethod.label)
                if (tarifNumero != null) append(" n°$tarifNumero")
                append(" (") ; append("★".repeat(tarifMethod.reliability)) ; append("☆".repeat(5 - tarifMethod.reliability)) ; append(")")
                tarifMethod.regionLabel?.let { append(" — $it") }
            }
            st.kvRow("Tarif de cubage", tarifLabel)
            st.kvRow("Entrées tarif", if (tarifMethod.entrees == 2) "2 entrées (D + H)" else "1 entrée (D seul)")
        }

        // Informations sur l'échantillonnage
        st.kvRow("Tiges mesurées", "${tiges.size} arbres")
        val gpsTigesCount = gpsTiges.size
        if (gpsTigesCount > 0) {
            st.kvRow("Tiges géolocalisées", "$gpsTigesCount / ${tiges.size} (${fmt0(gpsTigesCount.toDouble() / tiges.size * 100.0)}%)")
        }

        st.sectionDivider("Dendrométrie")
        st.kvRow(st.ctx.getString(R.string.pdf_stems), "${s.nTotal}  (${fmt1(s.nPerHa)} /ha)")
        st.kvRow(st.ctx.getString(R.string.pdf_volume), "${fmt3(s.vTotal)} m³")
        st.kvRow(st.ctx.getString(R.string.pdf_volume_ha), "${fmt3(s.vPerHa)} m³/ha")
        st.kvRow(st.ctx.getString(R.string.pdf_basal_area), "${fmt3(s.gTotal)} m²")
        st.kvRow(st.ctx.getString(R.string.pdf_basal_area_ha), "${fmt3(s.gPerHa)} m²/ha")
        
        // Distinction perches vs bois fort
        if (s.nPerches > 0 || s.nBoisFort > 0) {
            st.kvRow("Dont Perches (<17,5 cm)", "${s.nPerches} (${fmt1(s.nPerHaPerches)} /ha)")
            st.kvRow("Dont Bois fort (≥17,5 cm)", "${s.nBoisFort} (${fmt1(s.nPerHaBoisFort)} /ha)")
            st.kvRow("Volume perches", "${fmt3(s.vPerches)} m³ (${fmt3(s.vPerHaPerches)} m³/ha)")
            st.kvRow("Volume bois fort", "${fmt3(s.vBoisFort)} m³ (${fmt3(s.vPerHaBoisFort)} m³/ha)")
            st.kvRow("Surface terrière perches", "${fmt3(s.gPerches)} m² (${fmt3(s.gPerHaPerches)} m²/ha)")
            st.kvRow("Surface terrière bois fort", "${fmt3(s.gBoisFort)} m² (${fmt3(s.gPerHaBoisFort)} m²/ha)")
        }
        s.dm?.let { st.kvRow(st.ctx.getString(R.string.pdf_dm), "${fmt1(it)} cm") }
        s.dg?.let { st.kvRow(st.ctx.getString(R.string.pdf_dg), "${fmt1(it)} cm") }
        s.meanH?.let { st.kvRow(st.ctx.getString(R.string.pdf_h_mean), "${fmt1(it)} m") }
        s.hLorey?.let { st.kvRow(st.ctx.getString(R.string.pdf_h_lorey), "${fmt1(it)} m") }
        if (s.dMin != null && s.dMax != null) { st.kvRow("Dmin – Dmax", "${fmt1(s.dMin)} – ${fmt1(s.dMax)} cm") }
        s.cvDiam?.let { st.kvRow("CV diamètres", "${fmt0(it)} %") }
        s.ratioVG?.let { st.kvRow("V/G", fmt1(it)) }

        if (s.revenueTotal != null && s.revenueTotal > 0.0) {
            st.sectionDivider("Valorisation")
            st.kvRow(st.ctx.getString(R.string.pdf_revenue), "${fmt0(s.revenueTotal)} €")
            s.revenuePerHa?.let { st.kvRow(st.ctx.getString(R.string.pdf_revenue_ha), "${fmt0(it)} €/ha") }
        }

        if (s.harvestGhaPct != null || s.harvestNhaPct != null) drawHarvestSimSection(st, s)
        drawCorroborationSection(st, s)
        drawSylviculturalSection(st, s)
        if (s.qualityDistribution.isNotEmpty()) drawQualityDistSection(st, s)
        if (s.sanityWarnings.isNotEmpty()) drawSanityWarningsSection(st, s)
        if (s.classDistribution.isNotEmpty()) drawClassDistribution(st, s)
        if (s.perEssenceClassDist.isNotEmpty()) drawEssenceClassDistribution(st, s)
        if (fertilityResults.isNotEmpty()) drawFertilitySection(st, fertilityResults)

        drawMap(st, tiges)
        
        // Section traçabilité des sources et méthodes
        drawTraceabilitySection(st, s, tiges, tarifMethod, tarifNumero)

        if (s.perEssence.size >= 2) drawVolumeByEssenceChart(st, s)

        if (s.perEssence.isNotEmpty()) {
            st.sectionDivider(st.ctx.getString(R.string.pdf_per_essence_title))
            drawEssenceTable(st, s)
        }

        if (productBreakdown.values.flatten().isNotEmpty()) {
            st.sectionDivider(st.ctx.getString(R.string.product_breakdown_title))
            drawProductTable(st, productBreakdown, s)
        }

        if (s.specialTrees.isNotEmpty()) {
            st.sectionDivider(st.ctx.getString(R.string.martelage_special_trees_title))
            val detailPaint = paint(8f, Color.DKGRAY)
            val catPaint = paint(10f, Color.BLACK, bold = true)

            s.specialTrees.forEach { entry ->
                st.checkBreak(22f)
                val catLabel = when (entry.categorie) {
                    "DEPERISSANT" -> st.ctx.getString(R.string.special_tree_dying)
                    "ARBRE_BIO" -> st.ctx.getString(R.string.special_tree_bio)
                    "MORT" -> st.ctx.getString(R.string.special_tree_dead)
                    "PARASITE" -> st.ctx.getString(R.string.special_tree_parasite)
                    else -> entry.categorie
                }
                st.canvas?.drawText("$catLabel (${entry.count})", MARGIN, st.y, catPaint)
                st.y += 14f

                entry.trees.forEach { tree ->
                    st.checkBreak(14f)
                    val line = buildString {
                        append("  \u2022 "); append(tree.essenceName)
                        append(" \u2014 \u2300 "); append(fmt0(tree.diamCm)); append(" cm")
                        tree.hauteurM?.let { append(" \u2014 H "); append(fmt1(it)); append(" m") }
                        if (!tree.defauts.isNullOrEmpty()) { append(" \u2014 "); append(tree.defauts.joinToString(", ")) }
                        if (!tree.note.isNullOrBlank()) { append(" \u2014 "); append(tree.note) }
                        if (tree.hasGps) append(" \uD83D\uDCCD")
                    }
                    st.canvas?.drawText(line, MARGIN + 8f, st.y, detailPaint)
                    st.y += 12f
                }
                st.y += 4f
            }
        }

        s.biodiversity?.let { bio ->
            st.sectionDivider(st.ctx.getString(R.string.biodiversity_title))
            st.kvRow("Shannon H'", fmt2(bio.shannonH))
            bio.pielou?.let { st.kvRow("Piélou J", fmt2(it)) }
            st.kvRow(st.ctx.getString(R.string.biodiversity_species), bio.speciesCount.toString())
            st.kvRow("IBP", "${bio.ibpScore}/${bio.ibpMax}")
            if (bio.tgbCount > 0) st.kvRow("TGB \u226570cm", bio.tgbCount.toString())
            if (bio.bioTreeCount > 0) st.kvRow(st.ctx.getString(R.string.special_tree_bio), bio.bioTreeCount.toString())
            if (bio.deadTreeCount > 0) st.kvRow(st.ctx.getString(R.string.special_tree_dead), bio.deadTreeCount.toString())
            if (bio.dyingTreeCount > 0) st.kvRow(st.ctx.getString(R.string.special_tree_dying), bio.dyingTreeCount.toString())
        }
    }

    // ─── Simulation de prélèvement ───────────────────────────────────────────
    private fun drawHarvestSimSection(st: PdfState, s: MartelageStats) {
        st.sectionDivider("Simulation de prélèvement")
        st.checkBreak(60f)
        val c = st.canvas ?: return

        val cols = listOf(
            "Indicateur" to (CONTENT_W * 0.40f),
            "Prélèvement" to (CONTENT_W * 0.30f),
            "Résiduel" to (CONTENT_W * 0.30f)
        )
        val rowH = 15f
        val hBg = Paint().apply { color = Color.parseColor("#1565C0"); style = Paint.Style.FILL }
        val hP  = paint(8f, Color.WHITE, bold = true)
        val cP  = paint(8f, Color.DKGRAY)
        val bP  = paint(8f, Color.BLACK, bold = true)
        val sP  = Paint().apply { color = Color.parseColor("#E3F2FD"); style = Paint.Style.FILL }

        // header
        c.drawRect(MARGIN, st.y, PAGE_W - MARGIN, st.y + rowH, hBg)
        var x = MARGIN + 4f
        cols.forEachIndexed { i, (lbl, w) ->
            hP.textAlign = if (i == 0) Paint.Align.LEFT else Paint.Align.RIGHT
            c.drawText(lbl, if (i == 0) x else x + w - 4f, st.y + 11f, hP)
            x += w
        }
        st.y += rowH

        fun row(idx: Int, label: String, harvest: String, resid: String) {
            st.checkBreak(rowH + 2f)
            if (idx % 2 == 0) st.canvas?.drawRect(MARGIN, st.y, PAGE_W - MARGIN, st.y + rowH, sP)
            var rx = MARGIN + 4f
            listOf(label, harvest, resid).forEachIndexed { i, v ->
                val w = cols[i].second
                val p = if (i == 0) bP else cP
                p.textAlign = if (i == 0) Paint.Align.LEFT else Paint.Align.RIGHT
                st.canvas?.drawText(v, if (i == 0) rx else rx + w - 4f, st.y + 11f, p)
                rx += w
            }
            st.y += rowH
        }

        var idx = 0
        s.harvestNhaPct?.let { row(idx++, "Taux N/ha (tiges)", fmt1(it) + " %", s.residualNha?.let { r -> fmt0(r) + " t/ha" } ?: "–") }
        s.harvestGhaPct?.let { row(idx++, "Taux G/ha (surface terri\u00e8re)", fmt1(it) + " %", s.residualGha?.let { r -> fmt2(r) + " m\u00b2/ha" } ?: "–") }
        s.harvestVhaPct?.let { row(idx,  "Taux V/ha (volume estim\u00e9)", fmt1(it) + " %", "–") }

        st.canvas?.drawRect(MARGIN, st.y, PAGE_W - MARGIN, st.y,
            Paint().apply { color = Color.parseColor("#1565C0"); style = Paint.Style.STROKE; strokeWidth = 1f })
        st.y += 6f
    }

    // ─── Rapport de corroboration ────────────────────────────────────────────
    private fun drawCorroborationSection(st: PdfState, s: MartelageStats) {
        data class Check(val label: String, val value: String, val ok: Boolean?, val note: String)
        val checks = mutableListOf<Check>()

        s.ratioVG?.let { vg ->
            val (ok, note) = when {
                vg < 4.0  -> false to "Tr\u00e8s bas \u2014 hauteurs manquantes ou tarif inadapt\u00e9"
                vg < 6.0  -> null  to "Bas \u2014 peuplement jeune/dense"
                vg > 22.0 -> false to "Tr\u00e8s \u00e9lev\u00e9 \u2014 v\u00e9rifier le tarif"
                vg > 16.0 -> null  to "\u00c9lev\u00e9 \u2014 arbres de grande hauteur"
                else      -> true  to "Coh\u00e9rent (8\u201315 m\u00b3/m\u00b2)"
            }
            checks += Check("Ratio V/G", fmt1(vg) + " m\u00b3/m\u00b2", ok, note)
        }
        val dm = s.dm; val dg = s.dg
        if (dm != null && dg != null && dg > 0.0) {
            val r = dm / dg
            val (ok, note) = when {
                r < 0.82 -> null to "Etal\u00e9e vers les gros diam\u00e8tres"
                r > 1.08 -> null to "Etal\u00e9e vers les petits diam\u00e8tres"
                else     -> true to "Distribution sym\u00e9trique"
            }
            checks += Check("Dm / Dg", fmt2(r), ok, note)
        }
        if (dg != null && s.nPerHa > 0.0 && s.gPerHa > 0.0) {
            val gCalc = s.nPerHa * PI / 4.0 * (dg / 100.0) * (dg / 100.0)
            val err = abs(gCalc - s.gPerHa) / s.gPerHa
            val (ok, note) = when {
                err > 0.20 -> false to "Ecart " + fmt0(err * 100) + "% \u2014 diam\u00e8tres manquants ?"
                err > 0.10 -> null  to "Ecart " + fmt0(err * 100) + "% \u2014 v\u00e9rifier les tiges"
                else       -> true  to "N/ha \u00d7 Dg\u00b2 \u00d7 \u03c0/4 \u2248 G/ha mesur\u00e9"
            }
            checks += Check("G/ha recalcul\u00e9", fmt2(gCalc) + " m\u00b2/ha", ok, note)
        }
        s.cvDiam?.let { cv ->
            val note = when {
                cv < 15.0 -> "Futaie r\u00e9guli\u00e8re / \u00e9qui\u00e8nne"
                cv < 30.0 -> "Futaie r\u00e9guli\u00e8re normale"
                cv < 50.0 -> "M\u00e9lange de classes \u2014 bi-\u00e9tag\u00e9e"
                else      -> "Structure complexe \u2014 jardin\u00e9e"
            }
            checks += Check("Structure CV(D)", fmt0(cv) + " %", true, note)
        }
        s.harvestGhaPct?.let { hg ->
            val (ok, note) = when {
                hg > 40.0 -> false to "Tr\u00e8s intense \u2014 risque d\u00e9stabilisation"
                hg > 30.0 -> null  to "Fort \u2014 surveiller le r\u00e9siduel"
                else      -> true  to "Mod\u00e9r\u00e9 \u2014 conforme ONF/CNPF"
            }
            checks += Check("Intensit\u00e9 \u0394G", fmt0(hg) + " %", ok, note)
        }

        if (checks.isEmpty()) return
        st.sectionDivider("Rapport de corroboration")
        st.checkBreak((checks.size + 2) * 14f + 20f)
        val c = st.canvas ?: return

        val rowH = 14f
        val colLabel = CONTENT_W * 0.22f
        val colVal   = CONTENT_W * 0.18f
        val colNote  = CONTENT_W * 0.60f
        val hBg = Paint().apply { color = Color.parseColor("#263238"); style = Paint.Style.FILL }
        val hP  = paint(8f, Color.WHITE, bold = true)
        val lP  = paint(8f, Color.BLACK, bold = true)
        val vP  = paint(8f, Color.DKGRAY)
        val nP  = paint(7.5f, Color.DKGRAY)
        val sP  = Paint().apply { color = Color.parseColor("#ECEFF1"); style = Paint.Style.FILL }

        // header
        c.drawRect(MARGIN, st.y, PAGE_W - MARGIN, st.y + rowH, hBg)
        c.drawText("V\u00e9rification", MARGIN + 4f, st.y + 10f, hP)
        hP.textAlign = Paint.Align.RIGHT
        c.drawText("Valeur", MARGIN + colLabel + colVal - 4f, st.y + 10f, hP)
        hP.textAlign = Paint.Align.LEFT
        c.drawText("Interpr\u00e9tation", MARGIN + colLabel + colVal + 4f, st.y + 10f, hP)
        st.y += rowH

        checks.forEachIndexed { i, check ->
            st.checkBreak(rowH + 2f)
            if (i % 2 == 0) st.canvas?.drawRect(MARGIN, st.y, PAGE_W - MARGIN, st.y + rowH, sP)
            val statusColor = when (check.ok) {
                true  -> Color.parseColor("#2E7D32")
                false -> Color.parseColor("#C62828")
                null  -> Color.parseColor("#E65100")
            }
            val statusChar = when (check.ok) { true -> "\u2714"; false -> "\u2718"; null -> "\u26a0" }
            val slP = paint(8.5f, statusColor, bold = true)
            st.canvas?.drawText(statusChar, MARGIN + 4f, st.y + 10f, slP)
            lP.textAlign = Paint.Align.LEFT
            st.canvas?.drawText(check.label, MARGIN + 14f, st.y + 10f, lP)
            vP.textAlign = Paint.Align.RIGHT
            st.canvas?.drawText(check.value, MARGIN + colLabel + colVal - 4f, st.y + 10f, vP)
            nP.textAlign = Paint.Align.LEFT
            st.canvas?.drawText(check.note, MARGIN + colLabel + colVal + 4f, st.y + 10f, nP)
            st.y += rowH
        }
        st.y += 4f
    }

    // ─── Indicateurs sylvicoles ──────────────────────────────────────────────
    private fun drawSylviculturalSection(st: PdfState, s: MartelageStats) {
        if (s.dg == null && s.meanH == null) return
        st.sectionDivider("Indicateurs sylvicoles")
        st.checkBreak(80f)

        fun kv2(label1: String, val1: String, status1: String,
                 label2: String, val2: String, status2: String) {
            st.checkBreak(18f)
            val c = st.canvas ?: return
            val half = CONTENT_W / 2f
            val lP = paint(8f, Color.DKGRAY)
            val vP = paint(9f, Color.BLACK, bold = true)
            val sP = paint(7.5f, Color.parseColor("#1565C0"))
            c.drawText(label1, MARGIN, st.y, lP)
            c.drawText(val1, MARGIN, st.y + 11f, vP)
            c.drawText(status1, MARGIN + 60f, st.y + 11f, sP)
            c.drawText(label2, MARGIN + half, st.y, lP)
            c.drawText(val2, MARGIN + half, st.y + 11f, vP)
            c.drawText(status2, MARGIN + half + 60f, st.y + 11f, sP)
            st.y += 20f
        }

        val dg = s.dg
        val meanH = s.meanH

        val dominantCode = s.perEssence.maxByOrNull { it.gPct }?.essenceCode ?: ""
        val isResineuxDominant = dominantCode.uppercase().let { up ->
            up.contains("PIN") || up.contains("SAPIN") || up.contains("EPICEA") ||
            up.contains("DOUGLAS") || up.contains("MELEZE") || up.contains("CEDRE") ||
            up.contains("SEQUOIA") || up.contains("THUYA")
        }
        if (dg != null && meanH != null && dg > 0.0 && isResineuxDominant) {
            val sl = meanH / (dg / 100.0)
            val stLabel = when { sl < 70 -> "Tr\u00e8s stable"; sl < 85 -> "Stable"; sl < 100 -> "Normal"; else -> "Elanc\u00e9 \u26a0" }
            val gLabel  = when { s.gPerHa < 10 -> "Faible"; s.gPerHa < 20 -> "Mod\u00e9r\u00e9e"; s.gPerHa < 35 -> "Normale"; s.gPerHa < 50 -> "Dense"; else -> "Surpeuplement" }
            kv2("\u00c9lancement H/D (r\u00e9sineux)", fmt0(sl), stLabel, "Densit\u00e9 G/ha", fmt1(s.gPerHa) + " m\u00b2/ha", gLabel)
        }

        val hLorey = s.hLorey
        if (hLorey != null && meanH != null && meanH > 0.0) {
            val hr = hLorey / meanH
            val hrLabel = when { hr < 1.05 -> "Homog\u00e8ne"; hr < 1.15 -> "L\u00e9g\u00e8re h\u00e9t\u00e9rog."; else -> "H\u00e9t\u00e9rog\u00e8ne" }
            val nLabel = when { s.nPerHa < 100 -> "Clair"; s.nPerHa < 300 -> "Normal"; s.nPerHa < 700 -> "Fourni"; s.nPerHa < 1500 -> "Dense"; else -> "Tr\u00e8s dense" }
            kv2("H Lorey / H moy", fmt2(hr), hrLabel, "Densit\u00e9 N/ha", fmt0(s.nPerHa) + " t/ha", nLabel)
        }
    }

    // ─── Distribution par qualit\u00e9 ────────────────────────────────────────────
    private fun drawQualityDistSection(st: PdfState, s: MartelageStats) {
        if (s.qualityDistribution.isEmpty()) return
        st.sectionDivider("Distribution par Qualit\u00e9")
        st.checkBreak(90f)
        val c = st.canvas ?: return

        val active = s.qualityDistribution.filter { it.count > 0 }
        val maxN = active.maxOf { it.count }.toFloat()
        val chartH = 60f
        val chartW = (PAGE_W - 2 * MARGIN).toFloat()
        val barW = (chartW / active.size.coerceAtLeast(1)).coerceAtMost(40f)
        val totalBarW = barW * active.size
        val gap = (chartW - totalBarW) / (active.size + 1).coerceAtLeast(1)

        val qualColors = mapOf(
            "A" to Color.parseColor("#1B5E20"),
            "B" to Color.parseColor("#388E3C"),
            "C" to Color.parseColor("#F9A825"),
            "D" to Color.parseColor("#C62828")
        )

        val chartTop = st.y
        active.forEachIndexed { i, entry ->
            val barH = (entry.count.toFloat() / maxN * chartH).coerceAtLeast(2f)
            val x = MARGIN + gap + i * (barW + gap)
            val barTop = chartTop + chartH - barH
            val col = qualColors[entry.grade.toString()] ?: Color.parseColor("#78909C")
            val barP = Paint().apply { color = col; style = Paint.Style.FILL }
            c.drawRect(x, barTop, x + barW, chartTop + chartH, barP)
            val countP = paint(7f, col, bold = true).apply { textAlign = Paint.Align.CENTER }
            c.drawText(entry.count.toString(), x + barW / 2, barTop - 3f, countP)
            val lblP = paint(7f, Color.DKGRAY).apply { textAlign = Paint.Align.CENTER }
            c.drawText(entry.grade.toString(), x + barW / 2, chartTop + chartH + 12f, lblP)
        }
        val axisP = paint(8f, Color.DKGRAY).apply { textAlign = Paint.Align.CENTER }
        c.drawText("Classes de qualit\u00e9 (${s.qualityAssessedCount}/${s.qualityTotalCount} arbres \u00e9valu\u00e9s)",
            PAGE_W / 2f, chartTop + chartH + 26f, axisP)
        st.y += chartH + 36f
    }

    // ─── Alertes sanitaires ──────────────────────────────────────────────────
    private fun drawSanityWarningsSection(st: PdfState, s: MartelageStats) {
        if (s.sanityWarnings.isEmpty()) return
        val errors = s.sanityWarnings.filter { it.severity == SanitySeverity.ERROR }
        val warnings = s.sanityWarnings.filter { it.severity == SanitySeverity.WARNING }
        val infos   = s.sanityWarnings.filter { it.severity == SanitySeverity.INFO }
        if (errors.isEmpty() && warnings.isEmpty()) return

        st.sectionDivider("Alertes sanitaires & coh\u00e9rence")
        st.checkBreak((errors.size + warnings.size + 2) * 13f + 20f)

        fun group(items: List<SanityWarning>, labelColor: Int, icon: String) {
            items.forEach { w ->
                st.checkBreak(13f)
                val p = paint(8.5f, labelColor, bold = true)
                st.canvas?.drawText(icon, MARGIN + 4f, st.y + 10f, p)
                val tp = paint(8f, Color.DKGRAY)
                st.canvas?.drawText(w.code, MARGIN + 16f, st.y + 10f, tp)
                w.value?.let {
                    val vp = paint(8f, Color.DKGRAY).apply { textAlign = Paint.Align.RIGHT }
                    st.canvas?.drawText(fmt2(it), PAGE_W - MARGIN, st.y + 10f, vp)
                }
                st.y += 13f
            }
        }

        if (errors.isNotEmpty())   group(errors,   Color.parseColor("#C62828"), "\u2718")
        if (warnings.isNotEmpty()) group(warnings, Color.parseColor("#E65100"), "\u26a0")
        if (infos.isNotEmpty())    group(infos,    Color.parseColor("#1565C0"), "\u2139")
        st.y += 4f
    }

    // Couleur et libellé forestier par classe de diamètre (ONF/CNPF)
    private fun forestCategoryColor(diamClass: Int): Int = when {
        diamClass <= 15 -> Color.parseColor("#BDBDBD") // Perches — gris
        diamClass <= 25 -> Color.parseColor("#81C784") // Petit bois — vert clair
        diamClass <= 45 -> Color.parseColor("#388E3C") // Bois moyen — vert
        diamClass <= 65 -> Color.parseColor("#1B5E20") // Gros bois — vert foncé
        else            -> Color.parseColor("#795548") // Très gros bois — marron
    }
    private fun forestCategoryLabel(diamClass: Int): String = when {
        diamClass <= 15 -> "P"
        diamClass <= 25 -> "PB"
        diamClass <= 45 -> "BM"
        diamClass <= 65 -> "GB"
        else            -> "TGB"
    }
    private fun forestCategoryFull(diamClass: Int): String = when {
        diamClass <= 15 -> "Perches"
        diamClass <= 25 -> "Petit bois"
        diamClass <= 45 -> "Bois moyen"
        diamClass <= 65 -> "Gros bois"
        else            -> "Très gros bois"
    }

    private fun drawClassDistribution(st: PdfState, s: MartelageStats) {
        val active = s.classDistribution.filter { it.n > 0 }
        if (active.isEmpty()) return
        st.sectionDivider("Distribution par Classes de Diamètre")
        st.checkBreak(130f)
        val c = st.canvas ?: return

        val maxN = active.maxOf { it.n }
        val chartH = 80f
        val chartW = (PAGE_W - 2 * MARGIN).toFloat()
        val barW = (chartW / active.size.coerceAtLeast(1)).coerceAtMost(30f)
        val totalBarW = barW * active.size
        val gap = (chartW - totalBarW) / (active.size + 1).coerceAtLeast(1)

        val labelP = paint(6.5f, Color.DKGRAY).apply { textAlign = Paint.Align.CENTER }
        val catP   = paint(6f, Color.WHITE, bold = true).apply { textAlign = Paint.Align.CENTER }
        val countP = paint(6.5f, Color.BLACK, bold = true).apply { textAlign = Paint.Align.CENTER }

        val chartTop = st.y
        active.forEachIndexed { i, entry ->
            val barH = (entry.n.toFloat() / maxN * chartH).coerceAtLeast(4f)
            val x = MARGIN + gap + i * (barW + gap)
            val barTop = chartTop + chartH - barH
            val barColor = forestCategoryColor(entry.diamClass)
            c.drawRect(x, barTop, x + barW, chartTop + chartH,
                Paint().apply { color = barColor; style = Paint.Style.FILL })
            // Catégorie à l'intérieur de la barre (si assez haute)
            if (barH > 14f) {
                c.drawText(forestCategoryLabel(entry.diamClass), x + barW / 2, barTop + 11f, catP)
            }
            c.drawText(entry.n.toString(), x + barW / 2, barTop - 3f, countP)
            c.drawText("${entry.diamClass}", x + barW / 2, chartTop + chartH + 10f, labelP)
        }

        // Axe et légende catégories
        val axisP = paint(7.5f, Color.DKGRAY).apply { textAlign = Paint.Align.CENTER }
        c.drawText("Classes de diamètre (cm) — effectifs par tige", PAGE_W / 2f, chartTop + chartH + 22f, axisP)
        st.y += chartH + 30f

        // Légende des catégories forestières
        st.checkBreak(20f)
        val cats = listOf(
            "Perches (≤15)"   to Color.parseColor("#BDBDBD"),
            "Petit bois (20-25)"  to Color.parseColor("#81C784"),
            "Bois moyen (30-45)"  to Color.parseColor("#388E3C"),
            "Gros bois (50-65)"   to Color.parseColor("#1B5E20"),
            "TGB (≥70)"           to Color.parseColor("#795548")
        )
        val catLegW = chartW / cats.size
        val cv2 = st.canvas ?: return
        cats.forEachIndexed { i, (lbl, col) ->
            val cx = MARGIN + catLegW * i + catLegW / 2f
            cv2.drawRect(cx - catLegW/2f + 4f, st.y, cx + catLegW/2f - 4f, st.y + 8f,
                Paint().apply { color = col; style = Paint.Style.FILL })
            val legP = paint(6f, Color.DKGRAY).apply { textAlign = Paint.Align.CENTER }
            cv2.drawText(lbl, cx, st.y + 18f, legP)
        }
        st.y += 26f
    }

    private fun drawEssenceClassDistribution(st: PdfState, s: MartelageStats) {
        val data = s.perEssenceClassDist.filter { it.value.any { e -> e.n > 0 } }
        if (data.isEmpty() || s.perEssence.isEmpty()) return
        st.sectionDivider("Répartition par Essence et Classe de Diamètre")

        val essenceNameMap = s.perEssence.associate { it.essenceCode to it.essenceName }
        val allClasses = data.values.flatMap { it.map { e -> e.diamClass } }.distinct().sorted()
        if (allClasses.isEmpty()) return

        // Largeurs colonnes : col0 = essence (30%), reste partagé entre classes
        val colEss  = CONTENT_W * 0.28f
        val colRest = CONTENT_W * 0.72f
        val colCls  = (colRest / allClasses.size.coerceAtLeast(1)).coerceAtMost(28f)
        val rowH = 13f
        val hBg  = Paint().apply { color = Color.parseColor("#2E7D32"); style = Paint.Style.FILL }
        val sBg  = Paint().apply { color = Color.parseColor("#F1F8E9"); style = Paint.Style.FILL }
        val hP   = paint(7f, Color.WHITE, bold = true).apply { textAlign = Paint.Align.CENTER }
        val essP = paint(7.5f, Color.BLACK, bold = true)
        val numP = paint(7f, Color.DKGRAY).apply { textAlign = Paint.Align.CENTER }

        // Limite à 20 classes pour ne pas déborder
        val displayClasses = if (allClasses.size > 20) allClasses.take(20) else allClasses

        // En-tête
        st.checkBreak(rowH * 3 + 10f)
        val cv0 = st.canvas ?: return
        cv0.drawRect(MARGIN, st.y, PAGE_W - MARGIN, st.y + rowH, hBg)
        cv0.drawText("Essence", MARGIN + 4f, st.y + 9f, hP.apply { textAlign = Paint.Align.LEFT })
        var xCls = MARGIN + colEss
        displayClasses.forEach { cls ->
            val colColor = forestCategoryColor(cls)
            val cBg = Paint().apply { color = colColor; style = Paint.Style.FILL }
            cv0.drawRect(xCls, st.y, xCls + colCls - 1f, st.y + rowH, cBg)
            cv0.drawText("$cls", xCls + colCls / 2f, st.y + 9f,
                paint(6f, Color.WHITE, bold = true).apply { textAlign = Paint.Align.CENTER })
            xCls += colCls
        }
        st.y += rowH

        // Ligne catégorie (Perches/PB/BM/GB/TGB) sous les classes
        st.checkBreak(rowH + 2f)
        val cv1 = st.canvas ?: return
        xCls = MARGIN + colEss
        var prevCat = ""
        displayClasses.forEach { cls ->
            val cat = forestCategoryLabel(cls)
            if (cat != prevCat) {
                cv1.drawText(cat, xCls + colCls / 2f, st.y + 9f,
                    paint(5.5f, Color.parseColor("#424242")).apply { textAlign = Paint.Align.CENTER })
                prevCat = cat
            }
            xCls += colCls
        }
        st.y += rowH

        // Lignes par essence
        data.entries.sortedBy { essenceNameMap[it.key] ?: it.key }.forEachIndexed { idx, (code, entries) ->
            st.checkBreak(rowH + 2f)
            val cv = st.canvas ?: return
            if (idx % 2 == 0) cv.drawRect(MARGIN, st.y, PAGE_W - MARGIN, st.y + rowH, sBg)
            cv.drawText(essenceNameMap[code] ?: code, MARGIN + 4f, st.y + 9f, essP)
            val entryByClass = entries.associateBy { it.diamClass }
            xCls = MARGIN + colEss
            displayClasses.forEach { cls ->
                val n = entryByClass[cls]?.n ?: 0
                if (n > 0) {
                    cv.drawText(n.toString(), xCls + colCls / 2f, st.y + 9f, numP)
                }
                xCls += colCls
            }
            st.y += rowH
        }
        st.y += 6f
    }

    private fun drawFertilitySection(st: PdfState, results: List<FertilityResult>) {
        st.sectionDivider("Classes de Fertilité du Peuplement — Guides ONF/CNPF")
        st.checkBreak((results.size + 2) * 15f + 30f)
        val c = st.canvas ?: return

        val headers = listOf("Essence", "Classe", "H\u2080 (m)", "Confiance", "Zone")
        val colW = floatArrayOf(
            CONTENT_W * 0.32f, CONTENT_W * 0.14f, CONTENT_W * 0.14f, CONTENT_W * 0.24f, CONTENT_W * 0.16f
        )
        val rowH = 15f
        val headerP = paint(8f, Color.WHITE, bold = true)
        val cellP = paint(8f, Color.DKGRAY)
        val cellBoldP = paint(8f, Color.BLACK, bold = true)
        val headerBg = Paint().apply { color = Color.parseColor("#1B5E20"); style = Paint.Style.FILL }
        val stripeBg = Paint().apply { color = Color.parseColor("#F1F8E9"); style = Paint.Style.FILL }

        c.drawRect(MARGIN, st.y, PAGE_W - MARGIN, st.y + rowH, headerBg)
        var x = MARGIN + 4f
        for (i in headers.indices) {
            headerP.textAlign = if (i == 0) Paint.Align.LEFT else Paint.Align.RIGHT
            c.drawText(headers[i], if (i == 0) x else x + colW[i] - 4f, st.y + 11f, headerP)
            x += colW[i]
        }
        st.y += rowH

        results.forEachIndexed { idx, res ->
            st.checkBreak(rowH + 2f)
            if (idx % 2 == 0) st.canvas?.drawRect(MARGIN, st.y, PAGE_W - MARGIN, st.y + rowH, stripeBg)
            x = MARGIN + 4f
            val classLabel = when (res.fertilityClass) {
                FertilityClass.I -> "I — Excellent"
                FertilityClass.II -> "II — Bon"
                FertilityClass.III -> "III — Moyen"
                FertilityClass.IV -> "IV — Faible"
                FertilityClass.UNKNOWN -> "? — Indéterminé"
            }
            val confLabel = when (res.confidence) {
                ConfidenceLevel.HIGH -> "Élevée"
                ConfidenceLevel.MEDIUM -> "Moyenne"
                ConfidenceLevel.LOW -> "Faible"
                ConfidenceLevel.INSUFFICIENT -> "Insuff."
            }
            val cells = listOf(res.essenceName, classLabel, res.dominantHeightM?.let { fmt1(it) } ?: "–", confLabel, res.zoneCompatibility.icon)
            for (i in cells.indices) {
                val p = if (i == 0) cellBoldP else cellP
                p.textAlign = if (i == 0) Paint.Align.LEFT else Paint.Align.RIGHT
                st.canvas?.drawText(cells[i], if (i == 0) x else x + colW[i] - 4f, st.y + 11f, p)
                x += colW[i]
            }
            st.y += rowH
        }
        st.checkBreak(14f)
        val np = paint(7f, Color.GRAY)
        st.canvas?.drawText("Estimation automatique — données dendrométriques — hauteur dominante H\u2080", MARGIN, st.y + 10f, np)
        st.y += 18f
    }

    private fun drawMap(st: PdfState, tiges: List<Tige>) {
        val gpsTiges = tiges.mapNotNull { t ->
            val pt = QgisExportHelper.parseWktPointZ(t.gpsWkt)
            if (pt != null) Pair(t, pt) else null
        }
        if (gpsTiges.size < 2) return
        st.sectionDivider("Cartographie du Martelage - Fond IGN")
        st.checkBreak(220f)
        val c = st.canvas ?: return

        val mapHeight = 180f
        val mapWidth = (PAGE_W - 2 * MARGIN).toFloat()
        val minLat = gpsTiges.minOf { it.second.lat }
        val maxLat = gpsTiges.maxOf { it.second.lat }
        val minLon = gpsTiges.minOf { it.second.lon }
        val maxLon = gpsTiges.maxOf { it.second.lon }
        
        val latRange = (maxLat - minLat).coerceAtLeast(0.0001)
        val lonRange = (maxLon - minLon).coerceAtLeast(0.0001)
        
        // Fond de carte simulant IGN (plus professionnel)
        val mapBg = Paint().apply { color = Color.parseColor("#F8F6F0"); style = Paint.Style.FILL }
        val mapBorder = Paint().apply { color = Color.parseColor("#8D6E63"); style = Paint.Style.STROKE; strokeWidth = 2f }
        val gridPaint = Paint().apply { color = Color.parseColor("#E0E0E0"); style = Paint.Style.STROKE; strokeWidth = 0.5f }
        
        c.drawRect(MARGIN, st.y, MARGIN + mapWidth, st.y + mapHeight, mapBg)
        c.drawRect(MARGIN, st.y, MARGIN + mapWidth, st.y + mapHeight, mapBorder)
        
        // Grille de référence (simulée)
        for (i in 1..4) {
            val x = MARGIN + i * mapWidth / 5f
            c.drawLine(x, st.y, x, st.y + mapHeight, gridPaint)
        }
        for (i in 1..3) {
            val y = st.y + i * mapHeight / 4f
            c.drawLine(MARGIN, y, MARGIN + mapWidth, y, gridPaint)
        }
        
        // Palette de couleurs par essence (simulée)
        val essenceColors = mapOf(
            "CHENE" to Color.parseColor("#8D6E63"),
            "SENE" to Color.parseColor("#4CAF50"),
            "HETRE" to Color.parseColor("#795548"),
            "CHATAIGNER" to Color.parseColor("#FF9800"),
            "PIN" to Color.parseColor("#2E7D32"),
            "SAPIN" to Color.parseColor("#607D8B"),
            "EPICEA" to Color.parseColor("#37474F"),
            "DOUGLAS" to Color.parseColor("#004D40"),
            "MELEZE" to Color.parseColor("#FF5722"),
            "BOULEAU" to Color.parseColor("#FFC107"),
            "ERABLE" to Color.parseColor("#F44336"),
            "FRENE" to Color.parseColor("#9E9E9E")
        )
        
        // Couleurs pour états spéciaux
        val deadColor = Color.parseColor("#424242")
        val bioColor = Color.parseColor("#7B1FA2")
        val dyingColor = Color.parseColor("#FF6F00")
        val parasiteColor = Color.parseColor("#C62828")
        
        // Dessiner les points avec distinction par essence et état
        gpsTiges.forEach { (t, pt) ->
            val px = MARGIN + ((pt.lon - minLon) / lonRange * (mapWidth - 10f) + 5f).toFloat()
            val py = st.y + mapHeight - ((pt.lat - minLat) / latRange * (mapHeight - 10f) + 5f).toFloat()
            
            val baseColor = essenceColors[t.essenceCode.uppercase()] ?: Color.parseColor("#757575")
            var finalColor = baseColor
            var r = 2.5f
            
            when (t.categorie?.uppercase()) {
                "MORT" -> { finalColor = deadColor; r = 3.5f }
                "ARBRE_BIO" -> { finalColor = bioColor; r = 3.5f }
                "DEPERISSANT" -> { finalColor = dyingColor; r = 3.0f }
                "PARASITE" -> { finalColor = parasiteColor; r = 3.0f }
                else -> { finalColor = baseColor; r = 2.5f }
            }
            
            val pointPaint = Paint().apply { color = finalColor; style = Paint.Style.FILL; isAntiAlias = true }
            c.drawCircle(px, py, r, pointPaint)
            
            // Cercle extérieur pour meilleure visibilité
            val borderPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 0.8f; isAntiAlias = true }
            c.drawCircle(px, py, r + 0.5f, borderPaint)
        }
        
        // Légende améliorée
        st.y += mapHeight + 8f
        val legendTitle = paint(9f, Color.BLACK, bold = true)
        c.drawText("Légende :", MARGIN, st.y + 10f, legendTitle)
        st.y += 15f
        
        val legendPaint = paint(7.5f, Color.DKGRAY)
        var legendX = MARGIN
        var legendY = st.y
        val colWidth = mapWidth / 3f
        
        // Colonne 1 : Essences principales
        val mainEssences = gpsTiges.groupBy { it.first.essenceCode.uppercase() }
            .entries.sortedByDescending { it.value.size }.take(4)
        
        mainEssences.forEach { (code, list) ->
            val essColor = essenceColors[code] ?: Color.parseColor("#757575")
            c.drawCircle(legendX + 5f, legendY + 5f, 2f, Paint().apply { color = essColor; style = Paint.Style.FILL })
            c.drawText("$code (${list.size})", legendX + 12f, legendY + 8f, legendPaint)
            legendY += 12f
        }
        
        // Colonne 2 : États spéciaux
        legendX += colWidth
        legendY = st.y
        data class LegendSpecial(val code: String, val col: Int, val label: String)
        val specialStates = listOf(
            LegendSpecial("MORT",       deadColor,    "Mort sur pied"),
            LegendSpecial("ARBRE_BIO",  bioColor,     "Arbre bio"),
            LegendSpecial("DEPERISSANT",dyingColor,   "Dépérissant"),
            LegendSpecial("PARASITE",   parasiteColor,"Parasité")
        )
        specialStates.forEach { entry ->
            if (gpsTiges.any { it.first.categorie?.uppercase() == entry.code }) {
                c.drawCircle(legendX + 5f, legendY + 5f, 2.5f,
                    Paint().apply { color = entry.col; style = Paint.Style.FILL })
                c.drawText(entry.label, legendX + 12f, legendY + 8f, legendPaint)
                legendY += 12f
            }
        }
        
        // Colonne 3 : Informations GPS
        legendX += colWidth
        legendY = st.y
        val infoPaint = paint(7f, Color.parseColor("#1565C0"))
        c.drawText("GPS : ${gpsTiges.size} points", legendX, legendY + 8f, infoPaint)
        legendY += 12f
        val precVals = gpsTiges.mapNotNull { it.first.precisionM }
        if (precVals.isNotEmpty()) {
            c.drawText("Précision moy : ${String.format("%.1f", precVals.average())} m",
                legendX, legendY + 8f, infoPaint)
        }

        st.y += mapHeight + 70f
    }

    // ─── Graphique volume par essence (barres horizontales) ─────────────────────────
    private fun drawVolumeByEssenceChart(st: PdfState, s: MartelageStats) {
        val sorted = s.perEssence.filter { it.vTotal > 0.0 }.sortedByDescending { it.vTotal }
        if (sorted.isEmpty()) return
        st.sectionDivider("Volume par Essence — répartition")
        val barH = 14f
        val maxV = sorted.maxOf { it.vTotal }
        val maxBarW = (PAGE_W - 2 * MARGIN - 130f)
        val labelW = 110f
        val chartColors = listOf(
            Color.parseColor("#1B5E20"), Color.parseColor("#388E3C"), Color.parseColor("#4CAF50"),
            Color.parseColor("#81C784"), Color.parseColor("#1565C0"), Color.parseColor("#42A5F5"),
            Color.parseColor("#FF8F00"), Color.parseColor("#F57C00"), Color.parseColor("#795548")
        )
        sorted.forEachIndexed { idx, row ->
            st.checkBreak(barH + 4f)
            val c = st.canvas ?: return
            val bW = (row.vTotal / maxV * maxBarW).toFloat().coerceAtLeast(4f)
            val col = chartColors[idx % chartColors.size]
            val bgPaint  = Paint().apply { color = Color.parseColor("#F5F5F5"); style = Paint.Style.FILL }
            val barPaint = Paint().apply { color = col; style = Paint.Style.FILL }
            val lp = paint(8f, Color.BLACK, bold = idx == 0)
            c.drawText(
                if (row.essenceName.length > 16) row.essenceName.take(14) + "…" else row.essenceName,
                MARGIN, st.y + barH - 3f, lp
            )
            c.drawRect(MARGIN + labelW, st.y, MARGIN + labelW + maxBarW, st.y + barH - 2f, bgPaint)
            c.drawRect(MARGIN + labelW, st.y, MARGIN + labelW + bW, st.y + barH - 2f, barPaint)
            val vp = paint(7.5f, Color.DKGRAY)
            c.drawText("${fmt2(row.vTotal)} m³ (${fmt0(row.vPct)}%)",
                MARGIN + labelW + maxBarW + 4f, st.y + barH - 3f, vp)
            st.y += barH + 2f
        }
        st.y += 4f
    }

    // ─── Traçabilité des sources et méthodes ────────────────────────────────────────
    private fun drawTraceabilitySection(st: PdfState, s: MartelageStats, tiges: List<Tige>,
                                        tarifMethod: TarifMethod? = null, tarifNumero: Int? = null) {
        st.sectionDivider("Sources et Méthodes de Calcul")

        // methodRow utilise st.canvas après chaque checkBreak (pas de capture anticipée)
        fun methodRow(label: String, method: String, source: String, notes: String = "") {
            val extraH = if (notes.isNotEmpty()) 52f else 40f
            st.checkBreak(extraH)
            val cv = st.canvas ?: return
            cv.drawText(label,            MARGIN, st.y + 10f, paint(8.5f, Color.parseColor("#1B5E20"), bold = true))
            cv.drawText(method,           MARGIN, st.y + 22f, paint(8f,   Color.DKGRAY))
            cv.drawText("Source : $source", MARGIN, st.y + 34f, paint(7.5f, Color.parseColor("#1565C0")))
            if (notes.isNotEmpty()) {
                cv.drawText("Notes : $notes", MARGIN, st.y + 46f, paint(7f, Color.GRAY))
                st.y += extraH + 4f
            } else {
                st.y += extraH + 2f
            }
        }

        methodRow(
            label  = "Surface terrière (G)",
            method = "G = Σ(π × D²/4) pour chaque tige mesurée au diamètre",
            source = "Formule dendrométrique standard ONF/IFN",
            notes  = "Unité m²/ha. Précision liée au diamètre mesuré au centimètre près."
        )

        val measuredH = tiges.count { it.hauteurM != null }
        val totTiges  = tiges.size.coerceAtLeast(1)
        val volumeSource = if (tarifMethod != null) {
            buildString {
                append(tarifMethod.label)
                if (tarifNumero != null) append(" n°$tarifNumero")
                tarifMethod.regionLabel?.let { append(" — $it") }
                append(" (")
                append("★".repeat(tarifMethod.reliability))
                append("☆".repeat(5 - tarifMethod.reliability))
                append(")")
            }
        } else "Tarifs de cubage par essence – CNPF/ONF (Algan par défaut)"
        methodRow(
            label  = "Volume (V)",
            method = if (measuredH > 0)
                "V = Σ(f(D,H)) avec hauteurs mesurées ($measuredH/$totTiges tiges)"
            else
                "V = Σ(f(D,H)) avec hauteurs estimées par tarif (aucune hauteur mesurée)",
            source = volumeSource,
            notes  = "Complétude volume : ${fmt0(measuredH.toDouble() / totTiges * 100.0)} %"
        )

        methodRow(
            label  = "Densité (N/ha)",
            method = "N = effectif total / surface inventoriée (en ha)",
            source = "Comptage terrain direct + surface mesurée ou saisie",
            notes  = "Surface : ${fmt0(s.surfaceHa * 10_000.0)} m² = ${fmt2(s.surfaceHa)} ha"
        )

        methodRow(
            label  = "Diamètre moyen (Dm) et Dg",
            method = "Dm = Σ(Di)/N  |  Dg = √(4 × G/ha / (π × N/ha)) × 100",
            source = "Statistiques descriptives sur diamètres mesurés (1,30 m)",
            notes  = if (s.cvDiam != null) "CV(D) = ${fmt0(s.cvDiam)} % → structure ${when { s.cvDiam < 20 -> "régulière"; s.cvDiam < 40 -> "bi-étagée"; else -> "jardinée" }}" else ""
        )

        val nHLorey = tiges.count { it.hauteurM != null && it.diamCm > 0 }
        methodRow(
            label  = "Hauteurs (Hmoy, H Lorey)",
            method = "H Lorey = Σ(Gi × Hi) / Σ(Gi)  |  Hmoy = moyenne arithmétique des hauteurs",
            source = "Mesures terrain (dendromètre / laser / perche)",
            notes  = "Hauteurs disponibles : $nHLorey tiges"
        )

        s.biodiversity?.let { bio ->
            methodRow(
                label  = "IBP – Indice de Biodiversité Potentielle",
                method = "IBP = 6 critères notés sur 50 points (diversité 10, TGB 10, bio 10, mort 10, dépér. 5, équitab. 5)",
                source = "Protocole IBP ONF/CNPF 2021",
                notes  = "Score obtenu : ${bio.ibpScore}/${bio.ibpMax} — Shannon H'=${fmt2(bio.shannonH)}${bio.pielou?.let { ", Piélou J=${fmt2(it)}" } ?: ""}"
            )
        }

        methodRow(
            label  = "Classification perches / bois fort",
            method = "Perches : D < 17,5 cm (classes ≤ 15 cm)  |  Bois fort : D ≥ 17,5 cm (classes ≥ 20 cm)",
            source = "Définitions standards ONF/CNPF – grille nationale",
            notes  = "Perches : ${s.nPerches} tiges (${fmt1(s.nPerHaPerches)} /ha)  |  Bois fort : ${s.nBoisFort} tiges (${fmt1(s.nPerHaBoisFort)} /ha)"
        )

        if (s.qualityAssessedCount > 0) {
            methodRow(
                label  = "Qualité bois (grades A–D)",
                method = "Évaluation visuelle in situ par le forestier – 4 grades de qualité",
                source = "Grille qualité ONF adaptée CNPF",
                notes  = "${s.qualityAssessedCount}/${s.qualityTotalCount} arbres évalués (${fmt0(s.qualityAssessedCount.toDouble()/s.qualityTotalCount*100.0)} %)"
            )
        }

        st.checkBreak(15f)
        st.canvas?.drawText(
            "Toutes les formules et seuils suivent les standards dendrométriques français (ONF/CNPF/IFN).",
            MARGIN, st.y + 10f, paint(7f, Color.parseColor("#9E9E9E"))
        )
        st.y += 18f

        // ─ Sections signature acheteur / technicien ──────────────────────────────
        drawSignatureSection(st)
    }

    // ─── Sections signature acheteur / technicien forestier ─────────────────────
    private fun drawSignatureSection(st: PdfState) {
        st.sectionDivider("Validation et Signatures")
        st.checkBreak(200f)
        val c = st.canvas ?: return

        val colW = (CONTENT_W / 2f) - 8f
        val boxH = 160f
        val leftX  = MARGIN
        val rightX = MARGIN + colW + 16f
        val boxBg  = Paint().apply { color = Color.parseColor("#FAFAFA"); style = Paint.Style.FILL }
        val boxBrd = Paint().apply { color = Color.parseColor("#BDBDBD"); style = Paint.Style.STROKE; strokeWidth = 1f }
        val hdrBg  = Paint().apply { color = Color.parseColor("#263238"); style = Paint.Style.FILL }
        val hdrP   = paint(9f, Color.WHITE, bold = true)
        val fieldP = paint(8f, Color.parseColor("#424242"))
        val lineP  = Paint().apply { color = Color.parseColor("#E0E0E0"); style = Paint.Style.STROKE; strokeWidth = 0.8f }
        val noteP  = paint(7f, Color.GRAY)

        fun signatureBox(x: Float, label: String, fields: List<String>) {
            // Box background + border
            c.drawRect(x, st.y, x + colW, st.y + boxH, boxBg)
            c.drawRect(x, st.y, x + colW, st.y + boxH, boxBrd)
            // Header
            c.drawRect(x, st.y, x + colW, st.y + 18f, hdrBg)
            c.drawText(label, x + 6f, st.y + 13f, hdrP)
            var fy = st.y + 26f
            fields.forEach { field ->
                c.drawText(field, x + 6f, fy, fieldP)
                c.drawLine(x + 6f, fy + 4f, x + colW - 6f, fy + 4f, lineP)
                fy += 22f
            }
            // Zone signature
            c.drawText("Signature :", x + 6f, st.y + boxH - 30f, fieldP)
            c.drawRect(x + 60f, st.y + boxH - 38f, x + colW - 6f, st.y + boxH - 10f,
                Paint().apply { color = Color.parseColor("#F5F5F5"); style = Paint.Style.FILL })
            c.drawRect(x + 60f, st.y + boxH - 38f, x + colW - 6f, st.y + boxH - 10f, lineP)
            // Date
            c.drawText("Date : _____ / _____ / __________", x + 6f, st.y + boxH - 6f, noteP)
        }

        signatureBox(
            x = leftX.toFloat(),
            label = "✓ TECHNICIEN FORESTIER",
            fields = listOf("Nom / Prénom :", "Organisme / Cabinet :", "N° expert :", "Tel / Email :")
        )
        signatureBox(
            x = rightX,
            label = "€ ACHETEUR / EXPLOITANT",
            fields = listOf("Nom / Entreprise :", "SIREN / SIRET :", "Adresse :", "Tel / Email :")
        )
        st.y += boxH + 12f

        // Note légale
        st.checkBreak(30f)
        val cv2 = st.canvas ?: return
        val noteLegal = paint(7f, Color.GRAY)
        cv2.drawText("Document à conserver — Valeur juridique liée à l'authenticité des signatures. Toute cession de bois doit être accompagnée d'un contrat de vente.",
            MARGIN, st.y + 10f, noteLegal)
        cv2.drawText("Références : Code forestier L. 124-1 / R. 124-1 — Guide ONF/CNPF des ventes de bois sur pied.",
            MARGIN, st.y + 20f, noteLegal)
        st.y += 28f
    }

    private fun drawEssenceTable(st: PdfState, s: MartelageStats) {
        val headers = listOf(
            st.ctx.getString(R.string.pdf_col_essence),
            st.ctx.getString(R.string.pdf_col_n),
            st.ctx.getString(R.string.pdf_col_v),
            st.ctx.getString(R.string.pdf_col_v_ha),
            st.ctx.getString(R.string.pdf_col_g),
            st.ctx.getString(R.string.pdf_col_g_ha),
            st.ctx.getString(R.string.pdf_col_price),
            st.ctx.getString(R.string.pdf_col_revenue)
        )
        val colW = floatArrayOf(
            CONTENT_W * 0.22f, CONTENT_W * 0.07f, CONTENT_W * 0.11f, CONTENT_W * 0.10f,
            CONTENT_W * 0.10f, CONTENT_W * 0.10f, CONTENT_W * 0.12f, CONTENT_W * 0.18f
        )
        val rowH = 16f
        val headerPaint = paint(8f, Color.WHITE, bold = true)
        val cellPaint = paint(8f, Color.DKGRAY)
        val cellBoldPaint = paint(8f, Color.BLACK, bold = true)
        val headerBg = Paint().apply { color = Color.parseColor("#388E3C"); style = Paint.Style.FILL }
        val stripeBg = Paint().apply { color = Color.parseColor("#F1F8E9"); style = Paint.Style.FILL }

        val c = st.canvas ?: return
        val startY = st.y

        c.drawRect(MARGIN, st.y, PAGE_W - MARGIN, st.y + rowH, headerBg)
        var x = MARGIN + 4f
        for (i in headers.indices) {
            val align = if (i == 0) Paint.Align.LEFT else Paint.Align.RIGHT
            val xText = if (i == 0) x else x + colW[i] - 4f
            headerPaint.textAlign = align
            c.drawText(headers[i], xText, st.y + 12f, headerPaint)
            x += colW[i]
        }
        st.y += rowH

        s.perEssence.forEachIndexed { idx, row ->
            st.checkBreak(rowH + 2f)
            val cv = st.canvas ?: return
            if (idx % 2 == 0) {
                cv.drawRect(MARGIN, st.y, PAGE_W - MARGIN, st.y + rowH, stripeBg)
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
                cv.drawText(cells[i], xText, st.y + 12f, p)
                x += colW[i]
            }
            st.y += rowH
        }

        st.checkBreak(rowH + 4f)
        val totalBg = Paint().apply { color = Color.parseColor("#E8F5E9"); style = Paint.Style.FILL }
        st.canvas?.drawRect(MARGIN, st.y, PAGE_W - MARGIN, st.y + rowH, totalBg)
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
            st.canvas?.drawText(totals[i], xText, st.y + 12f, cellBoldPaint)
            x += colW[i]
        }
        st.y += rowH + 2f

        val border = Paint().apply { color = Color.parseColor("#388E3C"); style = Paint.Style.STROKE; strokeWidth = 1f }
        st.canvas?.drawRect(MARGIN, startY, PAGE_W - MARGIN, st.y, border)
    }

    private fun drawProductTable(st: PdfState, breakdown: Map<String, List<ProductBreakdownRow>>, s: MartelageStats) {
        val headers = listOf(
            st.ctx.getString(R.string.pdf_col_essence),
            st.ctx.getString(R.string.product_col_product),
            st.ctx.getString(R.string.product_col_vol),
            "€/m³",
            st.ctx.getString(R.string.product_col_total)
        )
        val colW = floatArrayOf(CONTENT_W * 0.25f, CONTENT_W * 0.25f, CONTENT_W * 0.16f, CONTENT_W * 0.16f, CONTENT_W * 0.18f)
        val rowH = 15f
        val headerPaint = paint(8f, Color.WHITE, bold = true)
        val cellPaint = paint(8f, Color.DKGRAY)
        val cellBoldPaint = paint(8f, Color.BLACK, bold = true)
        val headerBg = Paint().apply { color = Color.parseColor("#1565C0"); style = Paint.Style.FILL }
        val stripeBg = Paint().apply { color = Color.parseColor("#E3F2FD"); style = Paint.Style.FILL }

        val c = st.canvas ?: return
        val startY = st.y

        c.drawRect(MARGIN, st.y, PAGE_W - MARGIN, st.y + rowH, headerBg)
        var x = MARGIN + 4f
        for (i in headers.indices) {
            val align = if (i == 0) Paint.Align.LEFT else Paint.Align.RIGHT
            val xText = if (i == 0) x else x + colW[i] - 4f
            headerPaint.textAlign = align
            c.drawText(headers[i], xText, st.y + 11f, headerPaint)
            x += colW[i]
        }
        st.y += rowH

        var rowIdx = 0
        val essenceNameMap = s.perEssence.associate { it.essenceCode to it.essenceName }
        breakdown.entries.sortedBy { essenceNameMap[it.key] ?: it.key }.forEach { (essCode, rows) ->
            val essName = essenceNameMap[essCode] ?: essCode
            rows.filter { it.volumeM3 > 0.0 }.forEach { row ->
                st.checkBreak(rowH + 2f)
                val cv = st.canvas ?: return
                if (rowIdx % 2 == 0) {
                    cv.drawRect(MARGIN, st.y, PAGE_W - MARGIN, st.y + rowH, stripeBg)
                }
                x = MARGIN + 4f
                val cells = listOf(essName, row.product, fmt2(row.volumeM3), fmt0(row.pricePerM3), fmt0(row.totalEur) + " €")
                for (i in cells.indices) {
                    val p = if (i == 0) cellBoldPaint else cellPaint
                    val align = if (i == 0) Paint.Align.LEFT else Paint.Align.RIGHT
                    val xText = if (i == 0) x else x + colW[i] - 4f
                    p.textAlign = align
                    cv.drawText(cells[i], xText, st.y + 11f, p)
                    x += colW[i]
                }
                st.y += rowH
                rowIdx++
            }
        }

        st.checkBreak(rowH + 4f)
        val totalEur = breakdown.values.flatten().sumOf { it.totalEur }
        val totalVol = breakdown.values.flatten().sumOf { it.volumeM3 }
        val totalBg = Paint().apply { color = Color.parseColor("#BBDEFB"); style = Paint.Style.FILL }
        st.canvas?.drawRect(MARGIN, st.y, PAGE_W - MARGIN, st.y + rowH, totalBg)
        x = MARGIN + 4f
        val totals = listOf("TOTAL", "", fmt2(totalVol), "", fmt0(totalEur) + " €")
        for (i in totals.indices) {
            val xText = if (i == 0) x else x + colW[i] - 4f
            cellBoldPaint.textAlign = if (i == 0) Paint.Align.LEFT else Paint.Align.RIGHT
            st.canvas?.drawText(totals[i], xText, st.y + 11f, cellBoldPaint)
            x += colW[i]
        }
        st.y += rowH + 2f

        val border = Paint().apply { color = Color.parseColor("#1565C0"); style = Paint.Style.STROKE; strokeWidth = 1f }
        st.canvas?.drawRect(MARGIN, startY, PAGE_W - MARGIN, st.y, border)
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
