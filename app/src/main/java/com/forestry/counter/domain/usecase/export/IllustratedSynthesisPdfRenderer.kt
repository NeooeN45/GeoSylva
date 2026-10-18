package com.forestry.counter.domain.usecase.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.forestry.counter.R
import com.forestry.counter.domain.calculation.SynthesisReport
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlinx.serialization.json.Json

/** Native, entirely offline rendering of one captured revision. No database or network reads. */
internal fun writeIllustratedSynthesisPdf(
    context: Context,
    input: IllustratedSynthesisDocument,
    output: (PdfDocument) -> Unit,
) {
    val data = buildIllustratedSynthesisData(input)
    val pdf = PdfDocument()
    try {
        IllustratedReportWriter(pdf, context, input.timeZoneId).render(input, data)
        output(pdf)
    } finally {
        pdf.close()
    }
}

private object ReportPalette {
    val forest = Color.rgb(35, 88, 61)
    val ink = Color.rgb(31, 43, 40)
    val muted = Color.rgb(88, 102, 96)
    val line = Color.rgb(218, 226, 219)
    val light = Color.rgb(245, 248, 244)
    val sand = Color.rgb(244, 237, 218)
    val amber = Color.rgb(131, 87, 20)
    val parcel = Color.rgb(221, 235, 215)
    val blue = Color.rgb(38, 90, 133)
    val trace = Color.rgb(155, 89, 24)
}

private class IllustratedReportWriter(
    private val pdf: PdfDocument,
    private val context: Context,
    private val timeZoneId: String,
) {
    private val width = 595
    private val height = 842
    private val margin = 36f
    private val contentWidth = width - 2 * margin
    private val bottom = 786f
    private var page: PdfDocument.Page? = null
    private var pageNumber = 0
    private var y = 92f
    private val locale = context.resources.configuration.locales[0]
    private val body = pen(10f, ReportPalette.ink)
    private val small = pen(8.5f, ReportPalette.muted)
    private val tablePen = pen(8.5f, ReportPalette.ink)
    private val tableHeaderPen = pen(8.5f, Color.WHITE, true)
    private val lineHeight = 12.5f

    fun render(input: IllustratedSynthesisDocument, data: IllustratedSynthesisData) {
        val document = input.document
        val report = document.report
        paragraph(text(R.string.synthesis_illustrated_subtitle), size = 10f, color = ReportPalette.forest)
        paragraph(report.parcelName, bold = true, size = 25f)
        report.selectedPlotName?.takeIf(String::isNotBlank)?.let {
            paragraph(text(R.string.synthesis_plot_scope, it), bold = true)
        }
        paragraph(text(if (document.closed) R.string.synthesis_closed else R.string.synthesis_provisional),
            bold = true, color = if (document.closed) ReportPalette.forest else ReportPalette.amber)
        report.revisionNumber?.let { paragraph(text(R.string.synthesis_revision, it)) }
        paragraph(text(R.string.synthesis_generated, date(report.generatedAt)))
        paragraph(text(R.string.synthesis_illustrated_timezone, timeZoneId), size = 8.5f)
        document.closedAt?.let { paragraph(text(R.string.synthesis_closed_at, date(it))) }
        report.campaignId?.let { keyValue(text(R.string.synthesis_campaign), it) }
        if (report.firstObservationAt != null && report.lastObservationAt != null) {
            keyValue(text(R.string.synthesis_period), "${date(report.firstObservationAt)} — ${date(report.lastObservationAt)}")
        }
        section(text(R.string.synthesis_illustrated_overview))
        metricRow(listOf(
            Metric(text(R.string.synthesis_stems), report.observationCount.toString()),
            Metric(text(R.string.synthesis_illustrated_species_count), report.perEssence.size.toString()),
            Metric(text(R.string.synthesis_basal), "${number(report.basalAreaM2, 3)} m²"),
        ))
        metricRow(listOf(
            Metric(text(R.string.synthesis_volume), report.volumeM3?.let { "${number(it, 3)} m³" } ?: unknown()),
            Metric(text(R.string.synthesis_surface), report.surfaceM2?.let { "${number(it, 2)} m²" } ?: unknown()),
            Metric(text(R.string.synthesis_illustrated_computed_short), "${report.volumeComputedCount} / ${report.observationCount}"),
        ))
        paragraph(text(if (report.isVolumeComplete) R.string.synthesis_volume_complete else R.string.synthesis_volume_partial,
            report.volumeComputedCount, report.observationCount), color = ReportPalette.amber)
        paragraph(text(R.string.synthesis_campaign_hint))
        if (report.sessionId == null) warning(text(R.string.synthesis_legacy_hint))
        section(text(R.string.synthesis_illustrated_observation_scheme))
        scheme()
        paragraph(text(R.string.synthesis_illustrated_scheme_hint))

        newPage()
        section(text(R.string.synthesis_illustrated_completeness))
        completeness(text(synthesisRecordedHeightResource(report, R.string.synthesis_heights)), report.measuredHeightCount, report.observationCount)
        completeness(text(R.string.synthesis_locations), report.locatedCount, report.observationCount)
        completeness(text(R.string.synthesis_qualities), report.qualityCount, report.observationCount)
        completeness(text(R.string.synthesis_illustrated_computed_short), report.volumeComputedCount, report.observationCount)
        completeness(text(R.string.synthesis_illustrated_value_count_short), report.enteredValueCount, report.observationCount)
        paragraph(text(R.string.synthesis_illustrated_completeness_hint))
        val trace = decodedSynthesisTrace(document.traceJson)
        when {
            document.traceJson == null -> paragraph(text(R.string.synthesis_illustrated_trace_none))
            trace == null -> warning(text(R.string.synthesis_trace_invalid))
            else -> {
                paragraph(text(R.string.synthesis_illustrated_trace_summary,
                    trace.points.size, trace.points.map { it.segmentId }.distinct().size))
                if (trace.points.isNotEmpty()) paragraph(text(R.string.synthesis_trace_precision,
                    number(trace.points.minOf { it.accuracyM }, 1), number(trace.points.maxOf { it.accuracyM }, 1)))
            }
        }

        newPage()
        section(text(R.string.synthesis_illustrated_location))
        if (data.map == null) {
            warning(text(R.string.synthesis_illustrated_map_absent))
        } else {
            geographicMap(data.map)
            paragraph(text(R.string.synthesis_illustrated_map_bounds,
                number(data.map.bounds.west, 6), number(data.map.bounds.east, 6),
                number(data.map.bounds.south, 6), number(data.map.bounds.north, 6)), size = 8.5f)
            paragraph(text(R.string.synthesis_illustrated_map_counts,
                data.map.parcel.size, data.map.footprints.size, data.map.plotCenters.size,
                data.map.trees.size, data.map.traceSegments.size), size = 8.5f)
            paragraph(text(R.string.synthesis_illustrated_map_hint))
        }

        section(text(R.string.synthesis_illustrated_species_table))
        if (report.perEssence.isEmpty()) paragraph(text(R.string.synthesis_illustrated_empty_species))
        else table(
            headers = listOf(text(R.string.synthesis_illustrated_species_name), text(R.string.synthesis_illustrated_count_short),
                text(R.string.synthesis_illustrated_basal_short), text(R.string.synthesis_illustrated_volume_short),
                text(R.string.synthesis_illustrated_computed_short), text(R.string.synthesis_illustrated_height_short)),
            widths = listOf(178f, 42f, 67f, 67f, 79f, 90f),
            rows = report.perEssence.map { row -> listOf("${row.name}\n${row.code}", row.count.toString(),
                number(row.basalAreaM2, 3), row.volumeM3?.let { number(it, 3) } ?: unknown(),
                "${row.volumeComputedCount}/${row.count}", "${row.measuredHeightCount}/${row.count}") },
        )

        section(text(R.string.synthesis_illustrated_diameters))
        paragraph(text(R.string.synthesis_illustrated_diameter_hint))
        if (data.invalidDiameters > 0) warning(text(R.string.synthesis_illustrated_invalid_diameters, data.invalidDiameters))
        if (data.diameters.isEmpty()) paragraph(text(R.string.synthesis_illustrated_no_diameters))
        else diameterHistogram(data.diameters)

        section(text(R.string.synthesis_illustrated_details))
        keyValue(text(R.string.synthesis_diameter), report.meanDiameterCm?.let { "${number(it, 1)} cm" } ?: unknown())
        keyValue(text(R.string.synthesis_quadratic), report.quadraticDiameterCm?.let { "${number(it, 1)} cm" } ?: unknown())
        keyValue(text(R.string.synthesis_surface), report.surfaceM2?.let {
            "${number(it, 2)} m² (${number(it / 10_000.0, 4)} ha)"
        } ?: unknown())
        keyValue(text(R.string.synthesis_illustrated_surface_reason), text(if (report.surfaceM2 != null)
            R.string.synthesis_illustrated_surface_recorded else R.string.synthesis_illustrated_surface_missing))
        paragraph(text(if (report.surfaceM2 == null) R.string.synthesis_surface_unresolved else R.string.synthesis_surface_observations))
        if (report.surfaceReason in setOf("SAMPLING_BOUNDARY_UNKNOWN", "INVALID_SAMPLING_PLAN"))
            warning(text(R.string.synthesis_surface_boundary_warning))
        if (report.surfaceReason == "CHANGED_PARCEL_AREA") warning(text(R.string.synthesis_surface_changed))
        if (report.surfaceM2 != null) {
            keyValue(text(R.string.synthesis_n_per_ha), number(report.observationsPerHa, 2))
            keyValue(text(R.string.synthesis_g_per_ha), "${number(report.basalAreaPerHa, 3)} m²/ha")
            keyValue(text(R.string.synthesis_v_per_ha), report.volumePerHa?.let { "${number(it, 3)} m³/ha" } ?: unknown())
        }
        keyValue(text(R.string.synthesis_method), report.methods.takeIf { it.isNotEmpty() }?.joinToString("; ")
            ?: text(synthesisEmptyAppliedMethodResource(report)))
        paragraph(text(R.string.synthesis_method_hint))
        if (report.uncomputedReasons.isNotEmpty()) {
            section(text(R.string.synthesis_uncomputed))
            report.uncomputedReasons.forEach { (reason, count) -> keyValue(text(synthesisVolumeReasonResource(reason)), count.toString()) }
            if (needsSynthesisVolumeQualification(report)) {
                paragraph(text(R.string.synthesis_volume_conditions_body))
                warning(text(R.string.synthesis_volume_conditions_commercial))
            }
        }
        if (report.categories.isNotEmpty()) {
            section(text(R.string.synthesis_categories))
            table(listOf(text(R.string.synthesis_categories), text(R.string.synthesis_illustrated_count_short)),
                listOf(428f, 95f), report.categories.map { (category, count) -> listOf(category, count.toString()) })
        }

        section(text(R.string.synthesis_economy))
        metricRow(listOf(
            Metric(text(R.string.synthesis_values), report.enteredValueEur?.let { "${number(it, 2)} €" } ?: unknown()),
            Metric(text(R.string.synthesis_costs), unknown()),
            Metric(text(R.string.synthesis_net), unknown()),
        ))
        paragraph(text(if (report.enteredValueEur == null) R.string.synthesis_no_values else R.string.synthesis_value_coverage,
            report.enteredValueCount, report.observationCount))
        warning(text(R.string.synthesis_cost_hint))
        if (report.perEssence.isNotEmpty()) table(
            listOf(text(R.string.synthesis_illustrated_species_name), text(R.string.synthesis_illustrated_count_short),
                text(R.string.synthesis_illustrated_value_short), text(R.string.synthesis_illustrated_value_count_short)),
            listOf(263f, 65f, 95f, 100f),
            report.perEssence.map { listOf("${it.name}\n${it.code}", it.count.toString(),
                it.enteredValueEur?.let { amount -> number(amount, 2) } ?: unknown(), "${it.enteredValueCount}/${it.count}") },
        )

        section(text(R.string.synthesis_sources))
        report.sourceReferences.ifEmpty { listOf(text(R.string.synthesis_source_unknown)) }.forEach { paragraph(it) }
        paragraph(text(synthesisEngineDescriptionResource(report), report.engineVersion))
        document.sourceFingerprint?.let { keyValue(text(R.string.synthesis_snapshot), it) }
        section(text(R.string.synthesis_limits))
        paragraph(text(R.string.synthesis_limit_stand))
        paragraph(text(R.string.synthesis_limit_territory))
        paragraph(text(R.string.synthesis_export_scope))
        section(text(R.string.synthesis_illustrated_parameters))
        // Retain every captured field while avoiding a final page of split JSON for new reports.
        // Historical editions keep their original whitespace and pagination.
        val parameters = Json { prettyPrint = report.engineVersion != SynthesisReport.CURRENT_ENGINE_VERSION }.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(), document.parameters)
        paragraph(parameters, size = 8.5f)
        finishPage()
    }

    private data class Metric(val label: String, val value: String)

    private fun metricRow(metrics: List<Metric>) {
        val gap = 10f
        val cardWidth = (contentWidth - gap * (metrics.size - 1)) / metrics.size
        val labelPen = pen(8.5f, ReportPalette.muted)
        val valuePen = pen(16f, ReportPalette.forest, true)
        val labels = metrics.map { lines(it.label, cardWidth - 20f, labelPen) }
        val values = metrics.map { lines(it.value, cardWidth - 20f, valuePen) }
        val cardHeight = max(74f, 24f + labels.maxOf { it.size } * 12f + values.maxOf { it.size } * 21f)
        // Extreme but finite numeric inputs can be hundreds of characters long. Preserve them as
        // paginated text instead of overflowing a fixed card or dropping their significant digits.
        if (cardHeight > bottom - 92f) {
            metrics.forEach { keyValue(it.label, it.value) }
            return
        }
        ensure(cardHeight + 12f)
        metrics.indices.forEach { index ->
            val left = margin + index * (cardWidth + gap)
            canvas().drawRoundRect(RectF(left, y, left + cardWidth, y + cardHeight), 9f, 9f, pen(1f, ReportPalette.light))
            drawLines(labels[index], left + 10f, y + 18f, 12f, labelPen)
            drawLines(values[index], left + 10f, y + 27f + labels.maxOf { it.size } * 12f, 21f, valuePen)
        }
        y += cardHeight + 12f
    }

    private fun scheme() {
        val labels = listOf(text(R.string.synthesis_illustrated_scheme_observations),
            text(R.string.synthesis_illustrated_scheme_calculation), text(R.string.synthesis_illustrated_scheme_limits))
        val boxWidth = (contentWidth - 44f) / 3f
        val p = pen(10f, ReportPalette.forest, true)
        val wrapped = labels.map { lines(it, boxWidth - 24f, p) }
        val boxHeight = wrapped.maxOf { it.size } * 15f + 25f
        ensure(boxHeight + 14f)
        labels.indices.forEach { index ->
            val left = margin + index * (boxWidth + 22f)
            canvas().drawRoundRect(RectF(left, y, left + boxWidth, y + boxHeight), 8f, 8f, pen(1f, ReportPalette.sand))
            drawLines(wrapped[index], left + 12f, y + 21f, 15f, p)
            if (index < labels.lastIndex) {
                val x = left + boxWidth + 6f
                val center = y + boxHeight / 2f
                canvas().drawLine(x, center, x + 11f, center, stroke(ReportPalette.forest, 1f))
                canvas().drawLine(x + 7f, center - 3f, x + 11f, center, stroke(ReportPalette.forest, 1f))
                canvas().drawLine(x + 7f, center + 3f, x + 11f, center, stroke(ReportPalette.forest, 1f))
            }
        }
        y += boxHeight + 14f
    }

    private fun completeness(label: String, count: Int, total: Int) {
        val labels = lines(label, 218f, body)
        val rowHeight = max(28f, labels.size * 14f + 8f)
        ensure(rowHeight)
        drawLines(labels, margin, y + 13f, 14f, body)
        val left = margin + 231f
        val track = RectF(left, y + 5f, left + 193f, y + 15f)
        canvas().drawRoundRect(track, 4f, 4f, pen(1f, ReportPalette.line))
        val fraction = if (total > 0) (count.toDouble() / total).coerceIn(0.0, 1.0).toFloat() else 0f
        if (fraction > 0f) canvas().drawRoundRect(RectF(track.left, track.top, track.left + track.width() * fraction, track.bottom),
            4f, 4f, pen(1f, ReportPalette.forest))
        val value = pen(9f, ReportPalette.ink, true).apply { textAlign = Paint.Align.RIGHT }
        canvas().drawText("$count / $total", width - margin, y + 14f, value)
        y += rowHeight
    }

    /** Every cell is wrapped; exceptionally tall rows are continued under repeated headers. */
    private fun table(headers: List<String>, widths: List<Float>, rows: List<List<String>>) {
        require(headers.size == widths.size && rows.all { it.size == widths.size })
        require(kotlin.math.abs(widths.sum() - contentWidth) < 0.1f)
        val headerLines = headers.mapIndexed { index, label -> lines(label, widths[index] - 14f, tableHeaderPen) }
        val headerHeight = headerLines.maxOf { it.size } * lineHeight + 14f
        fun header() {
            ensure(headerHeight + lineHeight + 14f)
            canvas().drawRect(margin, y, width - margin, y + headerHeight, pen(1f, ReportPalette.forest))
            var left = margin
            headerLines.forEachIndexed { index, value ->
                drawLines(value, left + 7f, y + 14f, lineHeight, tableHeaderPen)
                left += widths[index]
            }
            y += headerHeight
        }
        header()
        rows.forEachIndexed { rowIndex, row ->
            val cellLines = row.mapIndexed { index, value -> lines(value, widths[index] - 14f, tablePen) }
            val lineCount = cellLines.maxOf { it.size }
            var offset = 0
            val needed = lineCount * lineHeight + 14f
            if (needed <= bottom - 92f - headerHeight && y + needed > bottom) { newPage(); header() }
            while (offset < lineCount) {
                var available = ((bottom - y - 14f) / lineHeight).toInt()
                if (available < 1) { newPage(); header(); available = ((bottom - y - 14f) / lineHeight).toInt() }
                val take = min(lineCount - offset, available)
                val rowHeight = take * lineHeight + 14f
                canvas().drawRect(margin, y, width - margin, y + rowHeight,
                    pen(1f, if (rowIndex % 2 == 0) ReportPalette.light else Color.WHITE))
                var left = margin
                cellLines.forEachIndexed { index, value ->
                    drawLines(value.drop(offset).take(take), left + 7f, y + 14f, lineHeight, tablePen)
                    left += widths[index]
                }
                y += rowHeight
                canvas().drawLine(margin, y, width - margin, y, stroke(ReportPalette.line, 0.6f))
                offset += take
                if (offset < lineCount) { newPage(); header() }
            }
        }
        y += 10f
    }

    /** Discrete horizontal bars retain every class and exact count, including across page breaks. */
    private fun diameterHistogram(rows: List<IllustratedDiameterRow>) {
        val maximum = rows.maxOf { it.count }.coerceAtLeast(1)
        fun header() {
            ensure(48f)
            canvas().drawText(text(R.string.synthesis_illustrated_diameter_header), margin + 7f, y + 12f, small)
            val right = pen(8.5f, ReportPalette.muted).apply { textAlign = Paint.Align.RIGHT }
            canvas().drawText(text(R.string.synthesis_illustrated_count_short), width - margin - 7f, y + 12f, right)
            y += 24f
        }
        header()
        rows.forEach { row ->
            if (y + 25f > bottom) { newPage(); header() }
            val left = margin + 98f
            val barWidth = contentWidth - 176f
            canvas().drawText(row.diameterCm.toString(), margin + 7f, y + 16f, body)
            canvas().drawRect(left, y + 5f, left + barWidth, y + 18f, pen(1f, ReportPalette.light))
            canvas().drawRect(left, y + 5f, left + barWidth * (row.count.toDouble() / maximum).toFloat(), y + 18f,
                pen(1f, ReportPalette.forest))
            val right = pen(10f, ReportPalette.ink, true).apply { textAlign = Paint.Align.RIGHT }
            canvas().drawText(row.count.toString(), width - margin - 7f, y + 16f, right)
            y += 25f
        }
        y += 10f
    }

    private fun geographicMap(map: IllustratedMapData) {
        ensure(390f)
        val frame = RectF(margin, y, width - margin, y + 330f)
        val viewport = RectF(frame.left + 19f, frame.top + 19f, frame.right - 19f, frame.bottom - 19f)
        canvas().drawRoundRect(frame, 9f, 9f, pen(1f, Color.WHITE))
        canvas().drawRoundRect(frame, 9f, 9f, stroke(ReportPalette.line, 1f))
        // Local equirectangular display, north up; bounds are not reinterpreted as metres or access data.
        val cosine = cos(Math.toRadians((map.bounds.north + map.bounds.south) / 2.0)).coerceAtLeast(0.01)
        val spanX = (map.bounds.east - map.bounds.west).coerceAtLeast(1e-8) * cosine
        val spanY = (map.bounds.north - map.bounds.south).coerceAtLeast(1e-8)
        val scale = min(viewport.width() / spanX, viewport.height() / spanY)
        val drawnWidth = spanX * scale
        val drawnHeight = spanY * scale
        val left = viewport.left + (viewport.width() - drawnWidth) / 2.0
        val top = viewport.top + (viewport.height() - drawnHeight) / 2.0
        fun project(point: IllustratedMapPoint): Pair<Float, Float> =
            (left + (point.longitude - map.bounds.west) * cosine * scale).toFloat() to
                (top + (map.bounds.north - point.latitude) * scale).toFloat()
        fun polygon(value: IllustratedMapPolygon): Path = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            (listOf(value.exterior) + value.holes).forEach { ring ->
                ring.forEachIndexed { index, point ->
                    val (x, py) = project(point)
                    if (index == 0) moveTo(x, py) else lineTo(x, py)
                }
                close()
            }
        }
        val saved = canvas().save()
        canvas().clipRect(viewport)
        map.parcel.forEach { value ->
            val path = polygon(value)
            canvas().drawPath(path, pen(1f, ReportPalette.parcel))
            canvas().drawPath(path, stroke(ReportPalette.forest, 1.5f))
        }
        map.footprints.forEach { value ->
            val path = polygon(value)
            canvas().drawPath(path, pen(1f, ReportPalette.sand))
            canvas().drawPath(path, stroke(ReportPalette.blue, 1f))
        }
        map.traceSegments.forEach { segment ->
            if (segment.size >= 2) {
                val path = Path()
                segment.forEachIndexed { index, point ->
                    val (x, py) = project(point)
                    if (index == 0) path.moveTo(x, py) else path.lineTo(x, py)
                }
                canvas().drawPath(path, stroke(ReportPalette.trace, 1.3f).apply { pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f) })
            }
        }
        map.trees.forEach { point -> val (x, py) = project(point); canvas().drawCircle(x, py, 2.7f, pen(1f, ReportPalette.forest)) }
        map.plotCenters.forEach { point ->
            val (x, py) = project(point)
            val triangle = Path().apply { moveTo(x, py - 4f); lineTo(x - 4f, py + 3.5f); lineTo(x + 4f, py + 3.5f); close() }
            canvas().drawPath(triangle, pen(1f, ReportPalette.blue))
        }
        canvas().restoreToCount(saved)
        val northX = frame.right - 24f
        val northY = frame.top + 29f
        canvas().drawCircle(northX, northY, 17f, pen(1f, Color.WHITE))
        canvas().drawLine(northX, northY + 8f, northX, northY - 3f, stroke(ReportPalette.ink, 1.2f))
        val arrow = Path().apply { moveTo(northX, northY - 8f); lineTo(northX - 3f, northY - 2f); lineTo(northX + 3f, northY - 2f); close() }
        canvas().drawPath(arrow, pen(1f, ReportPalette.ink))
        canvas().drawText(text(R.string.synthesis_illustrated_map_north), northX - 3f, northY - 11f, pen(8f, ReportPalette.ink, true))
        y = frame.bottom + 14f
        legend(text(R.string.synthesis_illustrated_map_parcel), ReportPalette.forest, 0)
        legend(text(R.string.synthesis_illustrated_map_footprint), ReportPalette.blue, 1)
        legend(text(R.string.synthesis_illustrated_map_center), ReportPalette.blue, 2)
        legend(text(R.string.synthesis_illustrated_map_tree), ReportPalette.forest, 3)
        legend(text(R.string.synthesis_illustrated_map_trace), ReportPalette.trace, 4)
        y += 7f
    }

    private fun legend(label: String, color: Int, shape: Int) {
        val wrapped = lines(label, contentWidth - 26f, small)
        ensure(wrapped.size * 12f + 5f)
        val x = margin + 7f
        val py = y + 6f
        when (shape) {
            0 -> canvas().drawRect(x - 4f, py - 4f, x + 5f, py + 4f, stroke(color, 1.2f))
            1 -> { canvas().drawRect(x - 4f, py - 4f, x + 5f, py + 4f, pen(1f, ReportPalette.sand)); canvas().drawRect(x - 4f, py - 4f, x + 5f, py + 4f, stroke(color, 1f)) }
            2 -> canvas().drawPath(Path().apply { moveTo(x, py - 4f); lineTo(x - 4f, py + 4f); lineTo(x + 4f, py + 4f); close() }, pen(1f, color))
            3 -> canvas().drawCircle(x, py, 2.7f, pen(1f, color))
            else -> canvas().drawLine(x - 6f, py, x + 7f, py, stroke(color, 1.3f).apply { pathEffect = DashPathEffect(floatArrayOf(3f, 2f), 0f) })
        }
        drawLines(wrapped, margin + 25f, y + 9f, 12f, small)
        y += wrapped.size * 12f + 5f
    }

    private fun section(value: String) {
        val p = pen(13f, ReportPalette.forest, true)
        val wrapped = lines(value, contentWidth - 14f, p)
        ensure(wrapped.size * 18f + 42f)
        y += 10f
        canvas().drawRect(margin, y - 1f, margin + 3f, y + wrapped.size * 18f + 2f, pen(1f, ReportPalette.forest))
        drawLines(wrapped, margin + 12f, y + 13f, 18f, p)
        y += wrapped.size * 18f + 14f
    }

    private fun warning(value: String) { paragraph(value, color = ReportPalette.amber) }

    private fun keyValue(label: String, value: String) { paragraph("$label : $value") }

    private fun paragraph(value: String, bold: Boolean = false, size: Float = 10f, color: Int = ReportPalette.ink) {
        val p = pen(size, color, bold)
        val step = size * 1.45f
        lines(value, contentWidth, p).forEach { line ->
            ensure(step + 3f)
            canvas().drawText(line, margin, y + size, p)
            y += step
        }
        y += 7f
    }

    private fun lines(value: String, available: Float, p: Paint): List<String> =
        wrapPdfText(value, available) { part, size -> p.breakText(part, true, size, null) }

    private fun drawLines(values: List<String>, left: Float, baseline: Float, step: Float, p: Paint) {
        values.forEachIndexed { index, value -> canvas().drawText(value, left, baseline + index * step, p) }
    }

    private fun canvas(): Canvas { if (page == null) beginPage(); return page!!.canvas }

    private fun ensure(space: Float) {
        if (page == null) beginPage()
        if (y + space > bottom) newPage()
    }

    private fun newPage() { finishPage(); beginPage() }

    private fun beginPage() {
        pageNumber++
        page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, pageNumber).create())
        y = 92f
        val current = page!!.canvas
        current.drawColor(Color.WHITE)
        current.drawRect(0f, 0f, width.toFloat(), 5f, pen(1f, ReportPalette.forest))
        current.drawText("GeoSylva", margin, 41f, pen(18f, ReportPalette.forest, true))
        val heading = pen(9f, ReportPalette.muted).apply { textAlign = Paint.Align.RIGHT }
        current.drawText(text(R.string.synthesis_illustrated_title), width - margin, 39f, heading)
        current.drawLine(margin, 63f, width - margin, 63f, stroke(ReportPalette.line, 0.7f))
    }

    private fun finishPage() {
        val current = page ?: return
        val footerY = height - 25f
        current.canvas.drawLine(margin, footerY - 15f, width - margin, footerY - 15f, stroke(ReportPalette.line, 0.7f))
        current.canvas.drawText(text(R.string.synthesis_illustrated_footer), margin, footerY, pen(8f, ReportPalette.muted))
        val pagePen = pen(8f, ReportPalette.muted).apply { textAlign = Paint.Align.RIGHT }
        current.canvas.drawText(text(R.string.synthesis_pdf_page, pageNumber), width - margin, footerY, pagePen)
        pdf.finishPage(current)
        page = null
    }

    private fun text(id: Int, vararg arguments: Any): String = context.getString(id, *arguments)
    private fun unknown(): String = text(R.string.synthesis_unknown)
    private fun number(value: Double?, decimals: Int): String = value?.takeIf(Double::isFinite)?.let {
        String.format(locale, "%.${decimals}f", it)
    } ?: unknown()
    private fun date(value: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", locale).apply {
        timeZone = TimeZone.getTimeZone(ZoneId.of(timeZoneId))
    }.format(Date(value))
    private fun pen(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
    private fun stroke(color: Int, width: Float) = pen(1f, color).apply { style = Paint.Style.STROKE; strokeWidth = width }
}
