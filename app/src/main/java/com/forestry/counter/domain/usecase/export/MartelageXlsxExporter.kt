package com.forestry.counter.domain.usecase.export

import android.content.Context
import android.net.Uri
import com.forestry.counter.domain.calculation.MartelageStats
import com.forestry.counter.domain.model.Tige
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI

/**
 * Exporteur XLSX simplifié pour les données de martelage.
 *
 * Génère un vrai fichier .xlsx (format Office Open XML) avec 3 feuilles :
 *  1. Synthèse — indicateurs agrégés
 *  2. Par essence — tableau par essence
 *  3. Détail tiges — une ligne par tige
 *
 * Utilise une implémentation SpreadsheetML minimaliste sans dépendance Apache POI
 * pour rester compatible avec l'APK offline-first sans surcharge de taille.
 */
object MartelageXlsxExporter {

    private const val NL = "\n"

    fun exportXlsx(
        context: Context,
        uri: Uri,
        stats: MartelageStats,
        scopeLabel: String,
        tiges: List<Tige> = emptyList()
    ): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { os ->
            writeXlsx(os, stats, scopeLabel, tiges)
        } ?: error("Impossible d'ouvrir le flux de sortie")
    }

    private fun writeXlsx(
        os: OutputStream,
        stats: MartelageStats,
        scopeLabel: String,
        tiges: List<Tige>
    ) {
        val sheets = buildSheets(stats, scopeLabel, tiges)
        val zip = buildZip(sheets)
        os.write(zip)
    }

    private fun buildSheets(
        stats: MartelageStats,
        scopeLabel: String,
        tiges: List<Tige>
    ): Map<String, String> {
        return mapOf(
            "xl/worksheets/sheet1.xml" to buildSyntheseSheet(stats, scopeLabel),
            "xl/worksheets/sheet2.xml" to buildParEssenceSheet(stats),
            "xl/worksheets/sheet3.xml" to buildTigesSheet(tiges, stats)
        )
    }

    private fun buildSyntheseSheet(s: MartelageStats, scopeLabel: String): String {
        val rows = mutableListOf<Pair<String, String>>()
        rows.add("Périmètre" to scopeLabel)
        rows.add("N total" to s.nTotal.toString())
        rows.add("N/ha" to fmt1(s.nPerHa))
        rows.add("G total (m²)" to fmt2(s.gTotal))
        rows.add("G/ha (m²/ha)" to fmt2(s.gPerHa))
        rows.add("V total (m³)" to fmt2(s.vTotal))
        rows.add("V/ha (m³/ha)" to fmt2(s.vPerHa))
        s.dm?.let { rows.add("Dm (cm)" to fmt1(it)) }
        s.dg?.let { rows.add("Dg (cm)" to fmt1(it)) }
        s.meanH?.let { rows.add("H moy (m)" to fmt1(it)) }
        s.hLorey?.let { rows.add("H Lorey (m)" to fmt1(it)) }
        s.cvDiam?.let { rows.add("CV(D) (%)" to fmt1(it)) }
        s.revenueTotal?.let { rows.add("Revenu total (€)" to fmt0(it)) }
        return buildSimpleKeyValueSheet("Synthèse", rows)
    }

    private fun buildParEssenceSheet(s: MartelageStats): String {
        val header = listOf("Essence", "N", "N %", "G total (m²)", "G/ha (m²/ha)",
            "G %", "V total (m³)", "V/ha (m³/ha)", "Dm (cm)", "Dg (cm)")
        val dataRows = s.perEssence.map { e ->
            listOf(e.essenceName, e.n.toString(), fmt1(e.nPct),
                fmt2(e.gTotal), fmt2(e.gPerHa), fmt1(e.gPct),
                fmt2(e.vTotal), fmt2(e.vPerHa),
                e.dm?.let { fmt1(it) } ?: "",
                e.dg?.let { fmt1(it) } ?: "")
        }
        return buildTableSheet("Par Essence", header, dataRows)
    }

    private fun buildTigesSheet(tiges: List<Tige>, stats: MartelageStats): String {
        val header = listOf("ID", "Essence", "Diam (cm)", "H (m)",
            "G (m²)", "V (m³)", "Qualité", "Statut", "GPS WKT")
        val dataRows: List<List<String>> = tiges.map { t ->
            val g = PI / 4.0 * (t.diamCm / 100.0) * (t.diamCm / 100.0)
            val statut = when (t.typeCoupe) { "COUPER" -> "Martelé"; "CONSERVER" -> "Conservé"; else -> t.typeCoupe.orEmpty() }
            listOf(
                t.id, t.essenceCode, fmt1(t.diamCm),
                t.hauteurM?.let { fmt1(it) }.orEmpty(),
                fmt4(g), t.valueEur?.let { fmt2(it) } ?: "",
                t.qualite?.toString() ?: "", statut, t.gpsWkt ?: ""
            )
        }
        return buildTableSheet("Tiges", header, dataRows)
    }

    private fun buildSimpleKeyValueSheet(name: String, rows: List<Pair<String, String>>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        sb.append("""<row r="1"><c r="A1" t="inlineStr"><is><t>Champ</t></is></c><c r="B1" t="inlineStr"><is><t>Valeur</t></is></c></row>""")
        rows.forEachIndexed { i, (k, v) ->
            val r = i + 2
            sb.append("""<row r="$r"><c r="A$r" t="inlineStr"><is><t>${k.xmlEscape()}</t></is></c><c r="B$r" t="inlineStr"><is><t>${v.xmlEscape()}</t></is></c></row>""")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun buildTableSheet(name: String, header: List<String>, rows: List<List<String>>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        sb.append("<row r=\"1\">")
        header.forEachIndexed { i, h ->
            val col = ('A' + i).toString()
            sb.append("""<c r="${col}1" t="inlineStr"><is><t>${h.xmlEscape()}</t></is></c>""")
        }
        sb.append("</row>")
        rows.forEachIndexed { ri, row ->
            val r = ri + 2
            sb.append("<row r=\"$r\">")
            row.forEachIndexed { ci, cell ->
                val col = ('A' + ci).toString()
                sb.append("""<c r="$col$r" t="inlineStr"><is><t>${cell.xmlEscape()}</t></is></c>""")
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun buildZip(sheets: Map<String, String>): ByteArray {
        val entries = mutableMapOf<String, ByteArray>()

        entries["[Content_Types].xml"] = """<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>""".toByteArray(Charsets.UTF_8)

        entries["_rels/.rels"] = """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8)

        entries["xl/workbook.xml"] = """<?xml version="1.0" encoding="UTF-8"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets>
<sheet name="Synthèse" sheetId="1" r:id="rId1"/>
<sheet name="Par Essence" sheetId="2" r:id="rId2"/>
<sheet name="Tiges" sheetId="3" r:id="rId3"/>
</sheets></workbook>""".toByteArray(Charsets.UTF_8)

        entries["xl/_rels/workbook.xml.rels"] = """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8)

        sheets.forEach { (name, content) ->
            entries[name] = content.toByteArray(Charsets.UTF_8)
        }

        return writeZip(entries)
    }

    private fun writeZip(entries: Map<String, ByteArray>): ByteArray {
        val buf = mutableListOf<Byte>()
        val centralDir = mutableListOf<Byte>()
        var offset = 0

        entries.forEach { (name, data) ->
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            val crc = crc32(data)
            val localHeader = ByteBuffer.allocate(30 + nameBytes.size).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(0x04034b50); putShort(20); putShort(0); putShort(0)
                putShort(0); putShort(0); putInt(crc.toInt())
                putInt(data.size); putInt(data.size)
                putShort(nameBytes.size.toShort()); putShort(0)
                put(nameBytes)
            }.array()
            val centralEntry = ByteBuffer.allocate(46 + nameBytes.size).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(0x02014b50); putShort(20); putShort(20); putShort(0); putShort(0)
                putShort(0); putShort(0); putInt(crc.toInt())
                putInt(data.size); putInt(data.size)
                putShort(nameBytes.size.toShort()); putShort(0); putShort(0)
                putShort(0); putShort(0); putInt(0); putInt(offset)
                put(nameBytes)
            }.array()
            buf.addAll(localHeader.toList())
            buf.addAll(data.toList())
            centralDir.addAll(centralEntry.toList())
            offset += localHeader.size + data.size
        }

        val eocd = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0x06054b50); putShort(0); putShort(0)
            putShort(entries.size.toShort()); putShort(entries.size.toShort())
            putInt(centralDir.size); putInt(offset); putShort(0)
        }.array()

        return (buf + centralDir + eocd.toList()).toByteArray()
    }

    private fun crc32(data: ByteArray): Long {
        var crc = 0xFFFFFFFFL
        val table = LongArray(256) { i ->
            var c = i.toLong()
            repeat(8) { c = if (c and 1L != 0L) 0xEDB88320L xor (c ushr 1) else c ushr 1 }
            c
        }
        data.forEach { b -> crc = table[((crc xor b.toLong()) and 0xFF).toInt()] xor (crc ushr 8) }
        return crc xor 0xFFFFFFFFL
    }

    private fun String.xmlEscape() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    private fun fmt0(v: Double) = "%.0f".format(v)
    private fun fmt1(v: Double) = "%.1f".format(v)
    private fun fmt2(v: Double) = "%.2f".format(v)
    private fun fmt4(v: Double) = "%.4f".format(v)
}
