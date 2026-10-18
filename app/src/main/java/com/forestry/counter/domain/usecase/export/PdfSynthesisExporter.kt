package com.forestry.counter.domain.usecase.export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.forestry.counter.R
import com.forestry.counter.domain.calculation.SynthesisReport
import com.forestry.counter.domain.model.Essence
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.model.MartelageGpsTrace
import com.forestry.counter.domain.model.MartelageGpsTraceCodec
import java.io.File
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray

/** A captured permission: it never adopts a later account or ownership generation. */
class SynthesisReadGuard(
    private val current: () -> Boolean,
    private val validated: suspend () -> Boolean,
) {
    fun isCurrent(): Boolean = current()
    fun requireCurrent() { check(current()) { "SYNTHESIS_READ_ACCESS_CHANGED" } }
    suspend fun requireRevalidated() {
        requireCurrent()
        check(validated()) { "SYNTHESIS_READ_ACCESS_CHANGED" }
        requireCurrent()
    }
}

/** Rendering may finish late; no captured payload reaches an output after revocation. */
internal suspend fun guardedSynthesisOutput(
    guard: SynthesisReadGuard,
    produce: suspend () -> ByteArray,
    output: suspend (ByteArray) -> Unit,
) {
    guard.requireRevalidated()
    val bytes = produce()
    guard.requireRevalidated()
    output(bytes)
    guard.requireCurrent()
}

/** Complete export input captured before opening Android's document picker. */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class SynthesisDocument(
    val report: SynthesisReport,
    val closed: Boolean,
    val closedAt: Long?,
    val sourceFingerprint: String?,
    val parameters: JsonObject,
    val observations: List<Tige>,
    /** Omit null to preserve the exact serialized inputs of historical V1 archives. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val traceJson: String? = null,
) {
    val essences: List<Essence> get() = report.perEssence.map { Essence(it.code, it.name, null, null) }
    init {
        require(!closed || (report.sessionId != null && closedAt != null && !sourceFingerprint.isNullOrBlank()))
        require(observations.all { it.parcelleId == report.parcelId &&
            (report.sessionId == null || it.sessionId == report.sessionId) })
    }
}

fun decodedSynthesisTrace(value: String?): MartelageGpsTrace? = value?.let {
    runCatching { MartelageGpsTraceCodec.decode(it) }.getOrNull()
}

/** CRS84 coordinates and separate trace segments; never connect a pause or a GPS gap. */
fun buildSynthesisGeoJson(document: SynthesisDocument): Pair<String, Int> {
    val (treesJson, treeCount) = QgisExportHelper.buildGeoJson(document.observations, document.essences, document.report.parcelName)
    val original = Json.parseToJsonElement(treesJson).jsonObject
    val trace = decodedSynthesisTrace(document.traceJson)
    val points = trace?.points.orEmpty()
    val features = original.getValue("features").jsonArray.toMutableList()
    points.forEach { point ->
        features += buildJsonObject {
            put("type", "Feature")
            put("geometry", buildJsonObject {
                put("type", "Point")
                put("coordinates", JsonArray(listOf(JsonPrimitive(point.longitude), JsonPrimitive(point.latitude))))
            })
            put("properties", buildJsonObject {
                put("kind", "martelage_gps_position"); put("segment_id", point.segmentId)
                put("accuracy_m", point.accuracyM); put("captured_at_ms", point.capturedAtMs)
                put("session_id", document.report.sessionId?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull)
                point.altitudeM?.let { put("altitude_m", it) }
            })
        }
    }
    val segments = points.groupBy { it.segmentId }.values.filter { it.size >= 2 }
    if (segments.isNotEmpty()) features += buildJsonObject {
        put("type", "Feature")
        put("geometry", buildJsonObject {
            put("type", "MultiLineString")
            put("coordinates", JsonArray(segments.map { segment -> JsonArray(segment.map {
                JsonArray(listOf(JsonPrimitive(it.longitude), JsonPrimitive(it.latitude)))
            }) }))
        })
        put("properties", buildJsonObject {
            put("kind", "martelage_gps_trace"); put("segment_count", segments.size)
            put("session_id", document.report.sessionId?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull)
        })
    }
    val metadata = original.getValue("metadata").jsonObject.toMutableMap().apply {
        put("gps_position_count", JsonPrimitive(points.size))
        put("gps_trace_status", JsonPrimitive(when { document.traceJson == null -> "ABSENT"; trace == null -> "INVALID"; else -> "RECORDED" }))
    }
    return JsonObject(original.toMutableMap().apply {
        put("features", JsonArray(features)); put("metadata", JsonObject(metadata))
    }).toString() to (treeCount + points.size)
}

/** Native A4 PDF, with wrapped text and page breaks; no truncated one-page table. */
object PdfSynthesisExporter {
    private const val WIDTH = 595
    private const val HEIGHT = 842
    private const val MARGIN = 40f
    private const val BOTTOM = 785f
    private val json = Json { encodeDefaults = true }

    suspend fun exportGuarded(context: Context, uri: Uri, document: SynthesisDocument, guard: SynthesisReadGuard) {
        guardedSynthesisOutput(guard, produce = {
            if (document.closed) archiveClosedGuarded(context, document, guard).readBytes()
            else ByteArrayOutputStream().use { bytes -> write(context, document) { it.writeTo(bytes) }; bytes.toByteArray() }
        }, output = { bytes ->
            guard.requireRevalidated()
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                var offset = 0
                while (offset < bytes.size) {
                    guard.requireCurrent()
                    val length = minOf(8_192, bytes.size - offset)
                    stream.write(bytes, offset, length)
                    offset += length
                }
                guard.requireCurrent()
            } ?: error("PDF_OUTPUT_UNAVAILABLE")
        })
    }

    suspend fun archiveClosedGuarded(context: Context, document: SynthesisDocument, guard: SynthesisReadGuard): File {
        guard.requireRevalidated()
        val result = archiveClosed(context, document, guard::requireCurrent)
        // A background rendering completion never grants the next identity access.
        guard.requireRevalidated()
        return result
    }

    fun export(context: Context, uri: Uri, document: SynthesisDocument) {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            if (document.closed) archiveClosed(context, document).inputStream().use { it.copyTo(stream) }
            else write(context, document) { it.writeTo(stream) }
        } ?: error("PDF_OUTPUT_UNAVAILABLE")
    }

    /** A closed revision is saved under its input/result digest, never overwritten. */
    @Synchronized
    fun archiveClosed(context: Context, document: SynthesisDocument, requireAccess: () -> Unit = {}): File {
        requireAccess()
        require(document.closed)
        val locale = context.resources.configuration.locales[0].toLanguageTag()
        val digest = sha256((locale + "\n" + json.encodeToString(document)).toByteArray(Charsets.UTF_8))
        val directory = File(context.filesDir, "martelage_reports")
        check(directory.isDirectory || directory.mkdirs())
        val revisionDirectory = File(directory, digest)
        val target = File(revisionDirectory, "report.pdf")
        val signature = File(revisionDirectory, "report.sha256")
        if (target.isFile && signature.isFile) {
            check(signature.readText().trim() == sha256(target.readBytes())) { "PDF_ARCHIVE_CORRUPT" }
            requireAccess()
            return target
        }
        // Never overwrite an incomplete existing artifact silently.
        check(!revisionDirectory.exists()) { "PDF_ARCHIVE_INCOMPLETE" }
        val temporaryDirectory = File(directory, ".pending-${java.util.UUID.randomUUID()}")
        check(temporaryDirectory.mkdir())
        val temporary = File(temporaryDirectory, "report.pdf")
        try {
            temporary.outputStream().use { output -> write(context, document) { it.writeTo(output) } }
            requireAccess()
            File(temporaryDirectory, "report.sha256").writeText(sha256(temporary.readBytes()) + "\n")
            File(temporaryDirectory, "inputs.json").writeText(json.encodeToString(document))
            requireAccess()
            check(temporaryDirectory.renameTo(revisionDirectory)) { "PDF_ARCHIVE_WRITE_FAILED" }
        } finally { temporaryDirectory.deleteRecursively() }
        return target
    }

    private fun write(context: Context, document: SynthesisDocument, output: (PdfDocument) -> Unit) {
        val pdf = PdfDocument()
        try {
            val writer = Writer(pdf, context)
            val report = document.report
            writer.title(context.getString(R.string.martelage_synthesis_title))
            writer.paragraph(report.parcelName, bold = true)
            report.selectedPlotName?.takeIf(String::isNotBlank)?.let { plotName ->
                writer.paragraph(context.getString(R.string.synthesis_plot_scope, plotName), bold = true)
            }
            writer.kv(context.getString(R.string.synthesis_pdf_status), context.getString(if (document.closed) R.string.synthesis_closed else R.string.synthesis_provisional))
            report.campaignId?.let { writer.kv(context.getString(R.string.synthesis_campaign), it) }
            report.revisionNumber?.let { writer.paragraph(context.getString(R.string.synthesis_revision, it)) }
            writer.paragraph(context.getString(R.string.synthesis_generated, date(report.generatedAt)))
            document.closedAt?.let { writer.paragraph(context.getString(R.string.synthesis_closed_at, date(it))) }
            writer.section(context.getString(R.string.synthesis_observations))
            writer.kv(context.getString(R.string.synthesis_stems), report.observationCount.toString())
            writer.kv(context.getString(synthesisRecordedHeightResource(report, R.string.synthesis_heights)), "${report.measuredHeightCount}/${report.observationCount}")
            writer.kv(context.getString(R.string.synthesis_locations), "${report.locatedCount}/${report.observationCount}")
            // Historical V1 PDFs keep their original content. Only recorded V2 traces add this block.
            document.traceJson?.let { raw ->
                val trace = decodedSynthesisTrace(raw)
                if (trace == null) writer.paragraph(context.getString(R.string.synthesis_trace_invalid))
                else {
                    writer.kv(context.getString(R.string.synthesis_trace_positions), trace.points.size.toString())
                    writer.paragraph(context.getString(R.string.synthesis_trace_precision,
                        number(trace.points.minOf { it.accuracyM }, 1), number(trace.points.maxOf { it.accuracyM }, 1)))
                    writer.kv(context.getString(R.string.synthesis_trace_segments), trace.points.map { it.segmentId }.distinct().size.toString())
                }
            }
            writer.kv(context.getString(R.string.synthesis_qualities), "${report.qualityCount}/${report.observationCount}")
            writer.kv(context.getString(R.string.synthesis_surface), report.surfaceM2?.let { "${number(it, 2)} m² (${number(it / 10_000.0, 4)} ha)" } ?: context.getString(R.string.synthesis_unknown))
            writer.paragraph(context.getString(if (report.surfaceM2 == null) R.string.synthesis_surface_unresolved else R.string.synthesis_surface_observations))
            if (report.surfaceReason in setOf("SAMPLING_BOUNDARY_UNKNOWN", "INVALID_SAMPLING_PLAN"))
                writer.paragraph(context.getString(R.string.synthesis_surface_boundary_warning))
            if (report.sessionId == null) writer.paragraph(context.getString(R.string.synthesis_legacy_hint))
            writer.section(context.getString(R.string.synthesis_calculations))
            writer.kv(context.getString(R.string.synthesis_basal), "${number(report.basalAreaM2, 3)} m²")
            writer.kv(context.getString(R.string.synthesis_volume), report.volumeM3?.let { "${number(it, 3)} m³" } ?: context.getString(R.string.synthesis_unknown))
            writer.paragraph(context.getString(if (report.isVolumeComplete) R.string.synthesis_volume_complete else R.string.synthesis_volume_partial, report.volumeComputedCount, report.observationCount))
            writer.kv(context.getString(R.string.synthesis_diameter), report.meanDiameterCm?.let { "${number(it, 1)} cm" } ?: context.getString(R.string.synthesis_unknown))
            writer.kv(context.getString(R.string.synthesis_quadratic), report.quadraticDiameterCm?.let { "${number(it, 1)} cm" } ?: context.getString(R.string.synthesis_unknown))
            if (report.surfaceM2 != null) {
                writer.kv(context.getString(R.string.synthesis_g_per_ha), "${number(report.basalAreaPerHa, 3)} m²/ha")
                writer.kv(context.getString(R.string.synthesis_v_per_ha), report.volumePerHa?.let { "${number(it, 3)} m³/ha" } ?: context.getString(R.string.synthesis_unknown))
            }
            writer.kv(context.getString(R.string.synthesis_method), report.methods.takeIf { it.isNotEmpty() }?.joinToString("; ") ?: context.getString(synthesisEmptyAppliedMethodResource(report)))
            report.uncomputedReasons.forEach { (reason, count) -> writer.kv(context.getString(synthesisVolumeReasonResource(reason)), count.toString()) }
            if (needsSynthesisVolumeQualification(report)) {
                writer.paragraph(context.getString(R.string.synthesis_volume_conditions_body))
                writer.paragraph(context.getString(R.string.synthesis_volume_conditions_commercial))
            }
            writer.section(context.getString(R.string.synthesis_species))
            report.perEssence.forEach { row ->
                writer.paragraph(row.name, bold = true)
                writer.paragraph("${context.getString(R.string.synthesis_stems)}: ${row.count} · G: ${number(row.basalAreaM2, 3)} m² · V: ${row.volumeM3?.let { number(it, 3) } ?: "—"} m³ · ${row.volumeComputedCount}/${row.count}")
            }
            if (report.categories.isNotEmpty()) {
                writer.section(context.getString(R.string.synthesis_categories))
                report.categories.forEach { (category, count) -> writer.kv(category, count.toString()) }
            }
            writer.section(context.getString(R.string.synthesis_economy))
            writer.kv(context.getString(R.string.synthesis_values), report.enteredValueEur?.let { "${number(it, 2)} €" } ?: context.getString(R.string.synthesis_unknown))
            writer.paragraph(context.getString(if (report.enteredValueEur == null) R.string.synthesis_no_values else R.string.synthesis_value_coverage, report.enteredValueCount, report.observationCount))
            writer.kv(context.getString(R.string.synthesis_costs), context.getString(R.string.synthesis_unknown))
            writer.kv(context.getString(R.string.synthesis_net), context.getString(R.string.synthesis_unknown))
            writer.paragraph(context.getString(R.string.synthesis_cost_hint))
            writer.section(context.getString(R.string.synthesis_sources))
            report.sourceReferences.ifEmpty { listOf(context.getString(R.string.synthesis_source_unknown)) }.forEach { writer.paragraph(it) }
            writer.paragraph(context.getString(synthesisEngineDescriptionResource(report), report.engineVersion))
            document.sourceFingerprint?.let { writer.kv(context.getString(R.string.synthesis_snapshot), it) }
            writer.section(context.getString(R.string.synthesis_limits))
            writer.paragraph(context.getString(R.string.synthesis_limit_stand))
            writer.paragraph(context.getString(R.string.synthesis_limit_territory))
            writer.paragraph(context.getString(R.string.synthesis_export_scope))
            writer.finish()
            output(pdf)
        } finally { pdf.close() }
    }

    private class Writer(private val pdf: PdfDocument, private val context: Context) {
        private var page: PdfDocument.Page? = null
        private var y = MARGIN + 12f
        private var pageNumber = 0
        private val body = paint(10f, Color.DKGRAY)
        private val bold = paint(10f, Color.BLACK, true)
        private fun begin() {
            pageNumber++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, pageNumber).create())
            y = MARGIN + 12f
        }
        private fun space(lines: Float) { if (page == null) begin(); if (y + lines > BOTTOM) { finishPage(); begin() } }
        fun title(text: String) { paragraph(text, size = 20f, bold = true); y += 8f }
        fun section(text: String) {
            space(45f)
            y += 9f
            page!!.canvas.drawLine(MARGIN, y, WIDTH - MARGIN, y, paint(1f, Color.LTGRAY))
            y += 18f
            page!!.canvas.drawText(text, MARGIN, y, paint(12f, Color.parseColor("#2E683E"), true))
            y += 18f
        }
        fun kv(label: String, value: String) { paragraph("$label : $value") }
        fun paragraph(text: String, bold: Boolean = false, size: Float = 10f) {
            val pen = if (size != 10f) paint(size, Color.BLACK, bold) else if (bold) this.bold else body
            val lineHeight = size * 1.45f
            wrapPdfText(text, WIDTH - 2 * MARGIN) { part, width -> pen.breakText(part, true, width, null) }.forEach { line ->
                space(lineHeight)
                page!!.canvas.drawText(line, MARGIN, y, pen)
                y += lineHeight
            }
            y += 4f
        }
        private fun finishPage() {
            val current = page ?: return
            val footer = paint(8f, Color.GRAY)
            current.canvas.drawText(context.getString(R.string.synthesis_pdf_notice), MARGIN, HEIGHT - 25f, footer)
            footer.textAlign = Paint.Align.RIGHT
            current.canvas.drawText(context.getString(R.string.synthesis_pdf_page, pageNumber), WIDTH - MARGIN, HEIGHT - 25f, footer)
            pdf.finishPage(current)
            page = null
        }
        fun finish() { finishPage() }
    }
    private fun paint(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color; typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    }
    private fun number(value: Double?, decimals: Int) = value?.let { String.format(Locale.getDefault(), "%.${decimals}f", it) } ?: "—"
    private fun date(value: Long) = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(value))
    private fun sha256(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

/** Pure wrapping helper. The renderer supplies actual font measurement. */
internal fun wrapPdfText(text: String, width: Float, fittingCharacters: (String, Float) -> Int): List<String> = buildList {
    text.split('\n').forEach { paragraph ->
        var rest = paragraph.trim()
        if (rest.isEmpty()) add("")
        while (rest.isNotEmpty()) {
            var end = fittingCharacters(rest, width).coerceIn(1, rest.length)
            if (end < rest.length && end > 1 && Character.isHighSurrogate(rest[end - 1])) end--
            if (end == 1 && rest.length > 1 && Character.isHighSurrogate(rest[0]) && Character.isLowSurrogate(rest[1])) end = 2
            if (end < rest.length) {
                val wordBoundary = rest.lastIndexOf(' ', end - 1)
                if (wordBoundary > 0) end = wordBoundary
            }
            add(rest.substring(0, end).trimEnd())
            rest = rest.substring(end).trimStart()
        }
    }
}
