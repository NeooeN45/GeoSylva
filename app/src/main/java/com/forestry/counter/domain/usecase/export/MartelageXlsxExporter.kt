package com.forestry.counter.domain.usecase.export

import android.content.Context
import android.net.Uri
import com.forestry.counter.domain.calculation.ProductBreakdownRow
import com.forestry.counter.domain.calculation.tarifs.TarifMethod
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.presentation.screens.forestry.MartelageStats
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI

/**
 * Exporteur XLSX multi-feuilles pour les données de martelage.
 * Feuilles : Synthèse | Tiges | Par Essence | Découpe Produits
 */
object MartelageXlsxExporter {

    // ── Couleurs hexadécimales ─────────────────────────────────────────────
    private val COLOR_DARK_GREEN  = byteArrayOf(0x1B.toByte(), 0x5E.toByte(), 0x20.toByte())
    private val COLOR_MID_GREEN   = byteArrayOf(0x38.toByte(), 0x8E.toByte(), 0x3C.toByte())
    private val COLOR_LIGHT_GREEN = byteArrayOf(0xF1.toByte(), 0xF8.toByte(), 0xE9.toByte())
    private val COLOR_STRIPE      = byteArrayOf(0xFA.toByte(), 0xFA.toByte(), 0xFA.toByte())
    private val COLOR_DARK_BLUE   = byteArrayOf(0x15.toByte(), 0x65.toByte(), 0xC0.toByte())
    private val COLOR_LIGHT_BLUE  = byteArrayOf(0xE3.toByte(), 0xF2.toByte(), 0xFD.toByte())
    private val COLOR_TOTAL_BG    = byteArrayOf(0xE8.toByte(), 0xF5.toByte(), 0xE9.toByte())
    private val COLOR_TITLE_BG    = byteArrayOf(0x26.toByte(), 0x32.toByte(), 0x38.toByte())
    private val COLOR_SECTION_BG  = byteArrayOf(0xEC.toByte(), 0xEF.toByte(), 0xF1.toByte())
    private val COLOR_HARVEST_BG  = byteArrayOf(0xBB.toByte(), 0xDE.toByte(), 0xFB.toByte())
    private val COLOR_ORANGE_BG   = byteArrayOf(0xFF.toByte(), 0xF3.toByte(), 0xE0.toByte())

    // ─── API publique ──────────────────────────────────────────────────────

    fun export(
        context: Context,
        uri: Uri,
        stats: MartelageStats,
        scopeLabel: String,
        tiges: List<Tige> = emptyList(),
        productBreakdown: Map<String, List<ProductBreakdownRow>> = emptyMap(),
        tarifMethod: TarifMethod? = null,
        tarifNumero: Int? = null
    ): Result<Unit> = runCatching {
        val wb = XSSFWorkbook()
        buildSynthesisSheet(wb, stats, scopeLabel, tarifMethod, tarifNumero)
        if (tiges.isNotEmpty()) buildTigesSheet(wb, tiges, stats)
        if (stats.perEssence.isNotEmpty()) buildEssenceSheet(wb, stats)
        if (productBreakdown.values.flatten().isNotEmpty()) buildProductSheet(wb, productBreakdown, stats)

        context.contentResolver.openOutputStream(uri)?.use { os ->
            wb.write(os)
        } ?: error("Impossible d'ouvrir le flux de sortie")
        wb.close()
    }

    // ─── Feuille 1 : Synthèse ──────────────────────────────────────────────

    private fun buildSynthesisSheet(
        wb: XSSFWorkbook,
        s: MartelageStats,
        scopeLabel: String,
        tarifMethod: TarifMethod?,
        tarifNumero: Int?
    ) {
        val sheet = wb.createSheet("Synthèse")
        val st = StyleKit(wb)
        var r = 0

        // Titre principal
        val titleRow = sheet.createRow(r++)
        titleRow.heightInPoints = 22f
        val titleCell = titleRow.createCell(0)
        titleCell.setCellValue("GeoSylva — Rapport de Martelage")
        titleCell.cellStyle = st.title()
        sheet.addMergedRegion(CellRangeAddress(r - 1, r - 1, 0, 4))

        // Sous-titre
        val subRow = sheet.createRow(r++)
        subRow.heightInPoints = 16f
        val subCell = subRow.createCell(0)
        subCell.setCellValue("$scopeLabel   •   ${nowDate()}")
        subCell.cellStyle = st.subtitle()
        sheet.addMergedRegion(CellRangeAddress(r - 1, r - 1, 0, 4))

        r++ // ligne vide

        fun sectionHeader(title: String) {
            val hRow = sheet.createRow(r++)
            hRow.heightInPoints = 14f
            val hCell = hRow.createCell(0)
            hCell.setCellValue(title)
            hCell.cellStyle = st.sectionHeader()
            sheet.addMergedRegion(CellRangeAddress(r - 1, r - 1, 0, 3))
            r++ // ligne vide après header — non, on garde compact
        }

        fun colHeader(vararg labels: String) {
            val hRow = sheet.createRow(r++)
            labels.forEachIndexed { i, lbl ->
                val c = hRow.createCell(i)
                c.setCellValue(lbl)
                c.cellStyle = st.colHeader(darkGreen = false)
            }
        }

        fun dataRow(label: String, value: String, unit: String = "", note: String = "", stripe: Boolean = false) {
            val dRow = sheet.createRow(r++)
            dRow.createCell(0).apply { setCellValue(label); cellStyle = st.label(stripe) }
            dRow.createCell(1).apply { setCellValue(value); cellStyle = st.valueCell(stripe, bold = true) }
            dRow.createCell(2).apply { setCellValue(unit); cellStyle = st.valueCell(stripe) }
            dRow.createCell(3).apply { setCellValue(note); cellStyle = st.note(stripe) }
        }

        fun dataRowNum(label: String, v: Double?, unit: String = "", note: String = "", stripe: Boolean = false) {
            dataRow(label, v?.let { fmt2(it) } ?: "—", unit, note, stripe)
        }

        // Tarif utilisé
        if (tarifMethod != null) {
            sectionHeader("Tarif de cubage utilisé")
            var st2 = false
            val tarifLabel = buildString {
                append(tarifMethod.label)
                if (tarifNumero != null) append(" n°$tarifNumero")
                tarifMethod.regionLabel?.let { append(" — $it") }
            }
            dataRow("Méthode", tarifLabel, "", "★".repeat(tarifMethod.reliability) + "☆".repeat(5 - tarifMethod.reliability), st2); st2 = !st2
            dataRow("Entrées", if (tarifMethod.entrees == 2) "2 entrées (D + H)" else "1 entrée (D seul)", "", "", st2); st2 = !st2
            dataRow("Catégorie", tarifMethod.category.name, "", tarifMethod.description.take(80), st2)
            r++
        }

        // Peuplement
        sectionHeader("1 — Peuplement : Effectifs et Volumes")
        colHeader("Indicateur", "Valeur", "Unité", "Note / Interprétation")
        var stripe = false
        dataRow("N total (tiges mesurées)", s.nTotal.toString(), "tiges", "", stripe); stripe = !stripe
        dataRow("N/ha", fmt1(s.nPerHa), "t/ha", densiteLabel(s.nPerHa), stripe); stripe = !stripe
        dataRowNum("G total", s.gTotal, "m²", "", stripe); stripe = !stripe
        dataRowNum("G/ha", s.gPerHa, "m²/ha", ghaLabel(s.gPerHa), stripe); stripe = !stripe
        dataRowNum("V total", s.vTotal, "m³", "", stripe); stripe = !stripe
        dataRowNum("V/ha", s.vPerHa, "m³/ha", "", stripe); stripe = !stripe
        r++

        if (s.nPerches > 0 || s.nBoisFort > 0) {
            sectionHeader("1b — Perches / Bois fort")
            colHeader("Catégorie", "N", "N/ha", "V total (m³)")
            stripe = false
            val pRow = sheet.createRow(r++)
            pRow.createCell(0).apply { setCellValue("Perches (< 17,5 cm)"); cellStyle = st.label(stripe) }
            pRow.createCell(1).apply { setCellValue(s.nPerches.toString()); cellStyle = st.valueCell(stripe) }
            pRow.createCell(2).apply { setCellValue(fmt1(s.nPerHaPerches)); cellStyle = st.valueCell(stripe) }
            pRow.createCell(3).apply { setCellValue(fmt3(s.vPerches)); cellStyle = st.valueCell(stripe) }
            stripe = !stripe
            val bfRow = sheet.createRow(r++)
            bfRow.createCell(0).apply { setCellValue("Bois fort (≥ 17,5 cm)"); cellStyle = st.label(stripe) }
            bfRow.createCell(1).apply { setCellValue(s.nBoisFort.toString()); cellStyle = st.valueCell(stripe) }
            bfRow.createCell(2).apply { setCellValue(fmt1(s.nPerHaBoisFort)); cellStyle = st.valueCell(stripe) }
            bfRow.createCell(3).apply { setCellValue(fmt3(s.vBoisFort)); cellStyle = st.valueCell(stripe) }
            r++
        }

        // Dendrométrie
        sectionHeader("2 — Dendrométrie")
        colHeader("Indicateur", "Valeur", "Unité", "Note / Interprétation")
        stripe = false
        s.dm?.let { dataRow("Dm (diamètre moyen)", fmt1(it), "cm", "", stripe); stripe = !stripe }
        s.dg?.let { dataRow("Dg (diam. quadratique)", fmt1(it), "cm",
            s.dm?.let { dm -> if (it > 0) "Dm/Dg=${fmt2(dm / it)}" else "" } ?: "", stripe); stripe = !stripe }
        if (s.dMin != null && s.dMax != null) {
            dataRow("Dmin — Dmax", "${fmt1(s.dMin)} — ${fmt1(s.dMax)}", "cm", "", stripe); stripe = !stripe
        }
        s.cvDiam?.let { dataRow("CV(D) — coeff. variation", fmt0(it), "%", cvLabel(it), stripe); stripe = !stripe }
        s.meanH?.let { dataRow("H moy", fmt1(it), "m", "", stripe); stripe = !stripe }
        s.hLorey?.let { dataRow("H Lorey", fmt1(it), "m",
            s.meanH?.let { mh -> if (mh > 0) "HLorey/Hmoy=${fmt2(it / mh)}" else "" } ?: "", stripe); stripe = !stripe }
        r++

        // Corroboration
        sectionHeader("3 — Corroboration (vérification croisée)")
        colHeader("Indicateur", "Valeur", "Unité", "Interprétation")
        stripe = false
        s.ratioVG?.let { vg ->
            dataRow("Ratio V/G", fmt1(vg), "m³/m²",
                when { vg < 4 -> "⚠ Très bas — tarif inadapté ?"; vg < 6 -> "Bas — peuplement jeune"; vg > 22 -> "⚠ Très élevé"; else -> "✓ Cohérent (8–15)" },
                stripe); stripe = !stripe
        }
        val dm = s.dm; val dg = s.dg
        if (dm != null && dg != null && dg > 0) {
            val rat = dm / dg
            dataRow("Dm/Dg", fmt3(rat), "",
                when { rat < 0.82 -> "Étalée vers grands D"; rat > 1.08 -> "Étalée vers petits D"; else -> "✓ Distribution symétrique" },
                stripe); stripe = !stripe
        }
        r++

        // Simulation de prélèvement
        if (s.harvestNhaPct != null || s.harvestGhaPct != null) {
            sectionHeader("4 — Simulation de prélèvement")
            colHeader("Indicateur", "Prélèvement (%)", "Résiduel", "")
            stripe = false
            s.harvestNhaPct?.let { dataRow("Taux ΔN/ha (tiges)", fmt1(it) + " %",
                s.residualNha?.let { r -> fmt0(r) + " t/ha" } ?: "—", "", stripe); stripe = !stripe }
            s.harvestGhaPct?.let { dataRow("Taux ΔG/ha (surf. terr.)", fmt1(it) + " %",
                s.residualGha?.let { r -> fmt2(r) + " m²/ha" } ?: "—",
                when { it > 40 -> "⚠ Très intense"; it > 30 -> "Fort — surveiller"; else -> "✓ Modéré" },
                stripe); stripe = !stripe }
            s.harvestVhaPct?.let { dataRow("Taux ΔV/ha (volume)", fmt1(it) + " %", "—", "", stripe) }
            r++
        }

        // Valorisation
        if (s.revenueTotal != null && s.revenueTotal > 0) {
            sectionHeader("5 — Valorisation")
            colHeader("Indicateur", "Valeur", "Unité", "")
            stripe = false
            dataRow("Revenu total estimé", fmt0(s.revenueTotal), "€", "", stripe); stripe = !stripe
            s.revenuePerHa?.let { dataRow("Revenu/ha", fmt0(it), "€/ha", "", stripe) }
            r++
        }

        // Biodiversité
        s.biodiversity?.let { bio ->
            sectionHeader("6 — Biodiversité")
            colHeader("Indicateur", "Valeur", "Unité", "Interprétation")
            stripe = false
            dataRow("Nb essences", bio.speciesCount.toString(), "", "", stripe); stripe = !stripe
            dataRow("Shannon H'", fmt3(bio.shannonH), "",
                when { bio.shannonH < 0.5 -> "Monospécifique"; bio.shannonH < 1.5 -> "Diversité faible"; bio.shannonH < 2.0 -> "Modérée"; else -> "Élevée" },
                stripe); stripe = !stripe
            bio.pielou?.let { dataRow("Piélou J", fmt3(it), "", if (it > 0.8) "Distribution équitable" else "", stripe); stripe = !stripe }
            dataRow("TGB (D ≥ 70 cm)", bio.tgbCount.toString(), "tiges", "", stripe); stripe = !stripe
            dataRow("Arbres bio", bio.bioTreeCount.toString(), "tiges", "", stripe); stripe = !stripe
            dataRow("Arbres morts", bio.deadTreeCount.toString(), "tiges", "", stripe); stripe = !stripe
            dataRow("Score IBP simplifié", "${bio.ibpScore}/${bio.ibpMax}", "",
                when { bio.ibpScore >= 7 -> "Haute valeur biologique"; bio.ibpScore >= 4 -> "Valeur modérée"; else -> "Valeur faible" },
                stripe)
            r++
        }

        // Colonnes width
        sheet.setColumnWidth(0, 9000); sheet.setColumnWidth(1, 4000)
        sheet.setColumnWidth(2, 3200); sheet.setColumnWidth(3, 10000)
        sheet.createFreezePane(0, 3)
    }

    // ─── Feuille 2 : Tiges ────────────────────────────────────────────────

    private fun buildTigesSheet(wb: XSSFWorkbook, tiges: List<Tige>, stats: MartelageStats) {
        val sheet = wb.createSheet("Tiges")
        val st = StyleKit(wb)
        var r = 0

        val headers = listOf("ID", "Essence", "Diam. (cm)", "Haut. (m)", "G (m²)",
            "Volume (m³)", "Valeur (€)", "Qualité", "Statut", "Catégorie",
            "Placette", "Défauts", "Note", "GPS WKT", "Précision GPS (m)")
        val hRow = sheet.createRow(r++)
        headers.forEachIndexed { i, h ->
            hRow.createCell(i).apply { setCellValue(h); cellStyle = st.colHeader(darkGreen = true) }
        }
        sheet.createFreezePane(0, 1)

        val colWidths = intArrayOf(3500, 5500, 3000, 2800, 3200, 3500, 3200, 2800, 3500, 3800, 3000, 7000, 7000, 9000, 4000)
        colWidths.forEachIndexed { i, w -> sheet.setColumnWidth(i, w) }

        tiges.forEachIndexed { idx, t ->
            val stripe = idx % 2 == 0
            val dRow = sheet.createRow(r++)
            val g = PI / 4.0 * (t.diamCm / 100.0) * (t.diamCm / 100.0)
            val marquee = when (t.destination) {
                "COUPER" -> "Martelé"; "CONSERVER" -> "Conservé"; null -> "Martelé"; else -> t.destination
            }
            val cells = listOf(
                t.id.toString(), t.essenceCode, fmt1(t.diamCm),
                t.hauteurM?.let { fmt1(it) } ?: "",
                fmt4(g),
                "",
                t.valueEur?.let { fmt2(it) } ?: "",
                t.qualite?.toString() ?: "",
                marquee,
                t.categorie ?: "",
                t.placetteId ?: "",
                t.defauts?.joinToString(" | ") ?: "",
                t.note ?: "",
                t.gpsWkt ?: "",
                t.precisionM?.let { fmt1(it) } ?: ""
            )
            cells.forEachIndexed { i, v ->
                dRow.createCell(i).apply { setCellValue(v); cellStyle = st.valueCell(stripe) }
            }
        }
    }

    // ─── Feuille 3 : Par Essence ──────────────────────────────────────────

    private fun buildEssenceSheet(wb: XSSFWorkbook, s: MartelageStats) {
        val sheet = wb.createSheet("Par Essence")
        val st = StyleKit(wb)
        var r = 0

        val headers = listOf("Essence", "N", "N %", "G total (m²)", "G/ha (m²/ha)",
            "G %", "V total (m³)", "V/ha (m³/ha)", "V %",
            "Dm (cm)", "Dg (cm)", "Prix moy (€/m³)", "Rev. total (€)", "Rev./ha (€/ha)", "Qualité dom.")
        val hRow = sheet.createRow(r++)
        headers.forEachIndexed { i, h ->
            hRow.createCell(i).apply { setCellValue(h); cellStyle = st.colHeader(darkGreen = true) }
        }
        sheet.createFreezePane(0, 1)

        s.perEssence.forEachIndexed { idx, e ->
            val stripe = idx % 2 == 0
            val dRow = sheet.createRow(r++)
            val cells = listOf(
                e.essenceName, e.n.toString(), fmt1(e.nPct),
                fmt3(e.gTotal), fmt3(e.gPerHa), fmt1(e.gPct),
                fmt3(e.vTotal), fmt3(e.vPerHa), fmt1(e.vPct),
                e.dm?.let { fmt1(it) } ?: "—",
                e.dg?.let { fmt1(it) } ?: "—",
                e.meanPricePerM3?.let { fmt0(it) } ?: "—",
                e.revenueTotal?.let { fmt0(it) } ?: "—",
                e.revenuePerHa?.let { fmt0(it) } ?: "—",
                e.dominantQuality?.name ?: "—"
            )
            cells.forEachIndexed { i, v ->
                dRow.createCell(i).apply { setCellValue(v); cellStyle = st.valueCell(stripe, bold = i == 0) }
            }
        }

        // Ligne total
        val tRow = sheet.createRow(r++)
        val totals = listOf(
            "TOTAL", s.nTotal.toString(), "100,0",
            fmt3(s.gTotal), fmt3(s.gPerHa), "100,0",
            fmt3(s.vTotal), fmt3(s.vPerHa), "100,0",
            "", "", "",
            s.revenueTotal?.let { fmt0(it) } ?: "—",
            s.revenuePerHa?.let { fmt0(it) } ?: "—", ""
        )
        totals.forEachIndexed { i, v ->
            tRow.createCell(i).apply { setCellValue(v); cellStyle = st.total() }
        }

        val widths = intArrayOf(6000, 2500, 2500, 3500, 3500, 2500, 3500, 3500, 2500, 2800, 2800, 3200, 3500, 3500, 3200)
        widths.forEachIndexed { i, w -> sheet.setColumnWidth(i, w) }
    }

    // ─── Feuille 4 : Découpe produits ─────────────────────────────────────

    private fun buildProductSheet(
        wb: XSSFWorkbook,
        breakdown: Map<String, List<ProductBreakdownRow>>,
        s: MartelageStats
    ) {
        val sheet = wb.createSheet("Découpe Produits")
        val st = StyleKit(wb)
        var r = 0

        val headers = listOf("Essence", "Produit", "Volume (m³)", "Prix (€/m³)", "Total (€)")
        val hRow = sheet.createRow(r++)
        headers.forEachIndexed { i, h ->
            hRow.createCell(i).apply { setCellValue(h); cellStyle = st.colHeader(darkGreen = false) }
        }
        sheet.createFreezePane(0, 1)

        val essenceNameMap = s.perEssence.associate { it.essenceCode to it.essenceName }
        var rowIdx = 0
        breakdown.entries.sortedBy { essenceNameMap[it.key] ?: it.key }.forEach { (code, rows) ->
            val essName = essenceNameMap[code] ?: code
            rows.filter { it.volumeM3 > 0.0 }.forEach { row ->
                val stripe = rowIdx++ % 2 == 0
                val dRow = sheet.createRow(r++)
                listOf(essName, row.product, fmt3(row.volumeM3), fmt0(row.pricePerM3), fmt0(row.totalEur) + " €")
                    .forEachIndexed { i, v ->
                        dRow.createCell(i).apply { setCellValue(v); cellStyle = st.valueCell(stripe, bold = i == 0) }
                    }
            }
        }

        // Total
        val totalVol = breakdown.values.flatten().sumOf { it.volumeM3 }
        val totalEur = breakdown.values.flatten().sumOf { it.totalEur }
        val tRow = sheet.createRow(r++)
        listOf("TOTAL", "", fmt3(totalVol), "", fmt0(totalEur) + " €").forEachIndexed { i, v ->
            tRow.createCell(i).apply { setCellValue(v); cellStyle = st.total() }
        }

        sheet.setColumnWidth(0, 6000); sheet.setColumnWidth(1, 6000)
        sheet.setColumnWidth(2, 3500); sheet.setColumnWidth(3, 3500); sheet.setColumnWidth(4, 4000)
    }

    // ─── Kit de styles ─────────────────────────────────────────────────────

    private class StyleKit(private val wb: XSSFWorkbook) {

        private fun baseFont(bold: Boolean = false, sizePoints: Short = 10, colorBytes: ByteArray? = null) =
            wb.createFont().apply {
                this.bold = bold
                fontHeightInPoints = sizePoints
                if (colorBytes != null) setColor(XSSFColor(colorBytes, null))
            }

        private fun newStyle(
            bgBytes: ByteArray? = null,
            bold: Boolean = false,
            sizePoints: Short = 10,
            align: HorizontalAlignment = HorizontalAlignment.LEFT,
            fontColor: ByteArray? = null,
            wrapText: Boolean = false
        ): XSSFCellStyle = (wb.createCellStyle() as XSSFCellStyle).apply {
            if (bgBytes != null) {
                fillPattern = FillPatternType.SOLID_FOREGROUND
                setFillForegroundColor(XSSFColor(bgBytes, null))
            }
            setFont(baseFont(bold, sizePoints, fontColor))
            alignment = align
            this.wrapText = wrapText
            setBorderBottom(BorderStyle.THIN)
            setBorderTop(BorderStyle.THIN)
            setBorderLeft(BorderStyle.THIN)
            setBorderRight(BorderStyle.THIN)
            val borderColor = XSSFColor(byteArrayOf(0xE0.toByte(), 0xE0.toByte(), 0xE0.toByte()), null)
            setBottomBorderColor(borderColor)
            setTopBorderColor(borderColor)
            setLeftBorderColor(borderColor)
            setRightBorderColor(borderColor)
        }

        fun title(): XSSFCellStyle = newStyle(
            bgBytes = COLOR_TITLE_BG, bold = true, sizePoints = 14,
            align = HorizontalAlignment.LEFT, fontColor = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        )

        fun subtitle(): XSSFCellStyle = newStyle(
            bgBytes = COLOR_SECTION_BG, bold = false, sizePoints = 10,
            fontColor = byteArrayOf(0x42.toByte(), 0x42.toByte(), 0x42.toByte())
        )

        fun sectionHeader(): XSSFCellStyle = newStyle(
            bgBytes = COLOR_LIGHT_GREEN, bold = true, sizePoints = 10,
            fontColor = COLOR_DARK_GREEN
        )

        fun colHeader(darkGreen: Boolean): XSSFCellStyle = newStyle(
            bgBytes = if (darkGreen) COLOR_DARK_GREEN else COLOR_DARK_BLUE,
            bold = true, sizePoints = 9, align = HorizontalAlignment.CENTER,
            fontColor = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        )

        fun label(stripe: Boolean): XSSFCellStyle = newStyle(
            bgBytes = if (stripe) COLOR_STRIPE else null,
            bold = false, sizePoints = 9,
            fontColor = byteArrayOf(0x42.toByte(), 0x42.toByte(), 0x42.toByte())
        )

        fun valueCell(stripe: Boolean, bold: Boolean = false): XSSFCellStyle = newStyle(
            bgBytes = if (stripe) COLOR_STRIPE else null,
            bold = bold, sizePoints = 9, align = HorizontalAlignment.RIGHT
        )

        fun note(stripe: Boolean): XSSFCellStyle = newStyle(
            bgBytes = if (stripe) COLOR_STRIPE else null,
            bold = false, sizePoints = 8,
            fontColor = byteArrayOf(0x78.toByte(), 0x90.toByte(), 0x9C.toByte()),
            wrapText = true
        )

        fun total(): XSSFCellStyle = newStyle(
            bgBytes = COLOR_TOTAL_BG, bold = true, sizePoints = 9,
            fontColor = COLOR_DARK_GREEN
        )
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private fun densiteLabel(n: Double) = when {
        n < 100 -> "Clair"; n < 300 -> "Normal"; n < 700 -> "Fourni"; n < 1500 -> "Dense"; else -> "Très dense"
    }
    private fun ghaLabel(g: Double) = when {
        g < 10 -> "Faible"; g < 20 -> "Modéré"; g < 35 -> "Normal"; g < 50 -> "Dense"; else -> "Surpeuplement"
    }
    private fun cvLabel(cv: Double) = when {
        cv < 15 -> "Futaie équienne"; cv < 30 -> "Futaie régulière"; cv < 50 -> "Bi-étagée"; else -> "Jardinée"
    }

    private fun fmt0(v: Double) = String.format(Locale.FRANCE, "%.0f", v)
    private fun fmt1(v: Double) = String.format(Locale.FRANCE, "%.1f", v)
    private fun fmt2(v: Double) = String.format(Locale.FRANCE, "%.2f", v)
    private fun fmt3(v: Double) = String.format(Locale.FRANCE, "%.3f", v)
    private fun fmt4(v: Double) = String.format(Locale.FRANCE, "%.4f", v)

    private fun nowDate() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date())
}
