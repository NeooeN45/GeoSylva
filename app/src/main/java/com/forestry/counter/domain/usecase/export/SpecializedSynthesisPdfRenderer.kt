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
import com.forestry.counter.domain.calculation.SynthesisParameters
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlinx.serialization.json.Json

/** Three native A4 publications, exclusively from captured inputs. No database or network reads. */
internal fun writeSpecializedSynthesisPdf(
    context: Context,
    input: SpecializedSynthesisDocument,
    output: (PdfDocument) -> Unit,
) {
    val pdf = PdfDocument()
    try {
        SpecializedReportWriter(pdf, context, input).render()
        output(pdf)
    } finally {
        pdf.close()
    }
}

private object SpecializedPalette {
    val ink = Color.rgb(29, 43, 41)
    val muted = Color.rgb(93, 107, 103)
    val line = Color.rgb(218, 226, 221)
    val light = Color.rgb(245, 248, 246)
    val sand = Color.rgb(246, 238, 218)
    val amber = Color.rgb(125, 84, 21)
    val parcel = Color.rgb(221, 235, 215)
    val forest = Color.rgb(35, 88, 61)
    val blue = Color.rgb(38, 90, 133)
    val trace = Color.rgb(155, 89, 24)
}

private class SpecializedReportWriter(
    private val pdf: PdfDocument,
    private val context: Context,
    private val input: SpecializedSynthesisDocument,
) {
    private val width = 595
    private val height = 842
    private val margin = 36f
    private val contentWidth = width - 2 * margin
    private val bottom = 786f
    private var page: PdfDocument.Page? = null
    private var pageNumber = 0
    private var sectionNumber = 0
    private var y = 88f
    private val locale = context.resources.configuration.locales[0]
    private val document = input.illustrated.document
    private val report = document.report
    private val data = buildIllustratedSynthesisData(input.illustrated)
    private val accent = when (input.type) {
        SpecializedReportType.REVENUE -> Color.rgb(27, 81, 85)
        SpecializedReportType.COSTS -> Color.rgb(42, 66, 101)
        SpecializedReportType.ENVIRONMENTAL -> SpecializedPalette.forest
    }
    private val titleId = when (input.type) {
        SpecializedReportType.REVENUE -> R.string.synthesis_specialized_revenue_title
        SpecializedReportType.COSTS -> R.string.synthesis_specialized_costs_title
        SpecializedReportType.ENVIRONMENTAL -> R.string.synthesis_specialized_environmental_title
    }
    private val body = pen(10f, SpecializedPalette.ink)
    private val small = pen(8.5f, SpecializedPalette.muted)
    private val tablePen = pen(8.5f, SpecializedPalette.ink)
    private val tableHeaderPen = pen(8.5f, Color.WHITE, true)
    private val lineHeight = 12.5f

    fun render() {
        cover()
        newPage()
        when (input.type) {
            SpecializedReportType.REVENUE -> revenue()
            SpecializedReportType.COSTS -> costs()
            SpecializedReportType.ENVIRONMENTAL -> environment()
        }
        newPage()
        location()
        newPage()
        annex()
        finishPage()
    }

    private fun cover() {
        beginPage(cover = true)
        paragraph(report.parcelName, bold = true, size = 25f)
        report.selectedPlotName?.takeIf(String::isNotBlank)?.let {
            paragraph(text(R.string.synthesis_plot_scope, it), size = 12f, color = accent)
        }
        paragraph(text(if (document.closed) R.string.synthesis_specialized_reference
            else R.string.synthesis_specialized_provisional), bold = true, color = accent)
        report.revisionNumber?.let { paragraph(text(R.string.synthesis_revision, it), size = 9f) }
        paragraph(text(R.string.synthesis_generated, date(report.generatedAt)), size = 9f)
        section(text(R.string.synthesis_specialized_overview))
        when (input.type) {
            SpecializedReportType.REVENUE -> {
                val values = buildSpecializedRevenueData(input)
                metrics(listOf(
                    Metric(text(R.string.synthesis_specialized_total_revenue), money(values.totalCents)),
                    Metric(text(R.string.synthesis_specialized_priced), fraction(values.pricedCount, values.observationCount)),
                    Metric(text(R.string.synthesis_specialized_species), report.perEssence.size.toString()),
                ))
                note(text(R.string.synthesis_specialized_revenue_hint))
            }
            SpecializedReportType.COSTS -> {
                val values = buildSpecializedCostData(input)
                metrics(listOf(
                    Metric(text(R.string.synthesis_specialized_total_costs), money(values.totalCents)),
                    Metric(text(R.string.synthesis_specialized_cost_lines), fraction(values.computedCount, values.lineCount)),
                    Metric(text(R.string.synthesis_specialized_observations), report.observationCount.toString()),
                ))
                note(text(R.string.synthesis_specialized_costs_hint))
            }
            SpecializedReportType.ENVIRONMENTAL -> {
                val values = buildSpecializedEnvironmentalData(input)
                metrics(listOf(
                    Metric(text(R.string.synthesis_specialized_observations), values.observationCount.toString()),
                    Metric(text(R.string.synthesis_specialized_species), report.perEssence.size.toString()),
                    Metric(text(R.string.synthesis_specialized_habitat), values.habitatCount.toString()),
                ))
                note(text(R.string.synthesis_specialized_environmental_hint))
            }
        }
        paragraph(text(R.string.synthesis_specialized_reading), bold = true, size = 10f, color = accent)
        paragraph(text(when (input.type) {
            SpecializedReportType.REVENUE -> R.string.synthesis_specialized_revenue_navigation
            SpecializedReportType.COSTS -> R.string.synthesis_specialized_costs_navigation
            SpecializedReportType.ENVIRONMENTAL -> R.string.synthesis_specialized_environmental_navigation
        }), size = 9f)
    }

    private fun revenue() {
        val values = buildSpecializedRevenueData(input)
        section(text(R.string.synthesis_specialized_revenue_section))
        coverage(text(R.string.synthesis_specialized_priced), values.pricedCount, values.observationCount)
        if (values.isComplete) paragraph(text(R.string.synthesis_specialized_revenue_complete, values.observationCount))
        else note(text(R.string.synthesis_specialized_revenue_missing, values.missingCount, values.invalidCount))
        if (values.rows.isEmpty()) paragraph(text(R.string.synthesis_specialized_no_observations))
        else {
            table(
                listOf(text(R.string.synthesis_specialized_essence), text(R.string.synthesis_specialized_count),
                    text(R.string.synthesis_specialized_priced), text(R.string.synthesis_specialized_value)),
                listOf(241f, 70f, 102f, 110f),
                values.rows.groupBy { it.essenceCode }.values.map { rows ->
                    val amounts = rows.mapNotNull { it.amountCents }
                    listOf("${rows.first().essenceName}\n${rows.first().essenceCode}", rows.size.toString(),
                        fraction(amounts.size, rows.size), amount(amounts.takeIf { it.isNotEmpty() }
                            ?.fold(0L) { total, amount -> Math.addExact(total, amount) }))
                },
            )
            section(text(R.string.synthesis_specialized_revenue_detail))
            table(
                listOf(text(R.string.synthesis_specialized_id), text(R.string.synthesis_specialized_essence),
                    text(R.string.synthesis_specialized_quality) + " / " + text(R.string.synthesis_specialized_product),
                    text(R.string.synthesis_specialized_value), text(R.string.synthesis_specialized_observation_source),
                    text(R.string.synthesis_specialized_observed_at)),
                listOf(90f, 125f, 77f, 72f, 90f, 69f),
                values.rows.map { row -> listOf(row.observationId, "${row.essenceName}\n${row.essenceCode}",
                    "${row.quality?.toString() ?: unknown()}\n${row.product ?: unknown()}", amount(row.amountCents),
                    row.source ?: unknown(), date(row.observedAt)) },
            )
        }
        note(text(R.string.synthesis_specialized_net_hint))
    }

    private fun costs() {
        val values = buildSpecializedCostData(input)
        section(text(R.string.synthesis_specialized_costs_section))
        if (values.rows.isEmpty()) note(text(R.string.synthesis_specialized_costs_empty))
        else {
            coverage(text(R.string.synthesis_specialized_cost_lines), values.computedCount, values.lineCount)
            paragraph(text(if (values.isComplete) R.string.synthesis_specialized_costs_complete
                else R.string.synthesis_specialized_costs_missing, values.missingCount))
            table(
                listOf(text(R.string.synthesis_specialized_operation), text(R.string.synthesis_specialized_quantity),
                    text(R.string.synthesis_specialized_unit), text(R.string.synthesis_specialized_unit_price),
                    text(R.string.synthesis_specialized_amount)),
                listOf(183f, 65f, 64f, 100f, 111f),
                values.rows.map { row -> listOf("${row.line.label}\n${row.line.id}", row.line.quantity ?: unknown(),
                    unit(row.line.unit), row.line.unitPriceEur ?: unknown(), amount(row.amountCents)) },
            )
            keyValue(text(R.string.synthesis_specialized_total_costs), money(values.totalCents))
            note(text(R.string.synthesis_specialized_fixed_hint))
            section(text(R.string.synthesis_specialized_costs_sources))
            table(
                listOf(text(R.string.synthesis_specialized_operation), text(R.string.synthesis_specialized_basis),
                    text(R.string.synthesis_specialized_price_date), text(R.string.synthesis_specialized_source)),
                listOf(145f, 100f, 70f, 208f),
                values.rows.map { row -> listOf("${row.line.label}\n${row.line.id}",
                    text(if (row.line.basis == SpecializedCostBasis.USER_QUOTATION)
                        R.string.synthesis_specialized_user_quotation else R.string.synthesis_specialized_user_estimate),
                    row.line.priceDate ?: unknown(), row.line.provenance) },
            )
        }
        note(text(R.string.synthesis_specialized_costs_hint))
        paragraph(text(R.string.synthesis_specialized_costs_capture_hint), size = 9f)
        note(text(R.string.synthesis_specialized_net_hint))
    }

    private fun environment() {
        val values = buildSpecializedEnvironmentalData(input)
        section(text(R.string.synthesis_specialized_environmental_section))
        coverage(text(synthesisRecordedHeightResource(report, R.string.synthesis_specialized_heights)), values.measuredHeightCount, values.observationCount)
        coverage(text(R.string.synthesis_specialized_gps), values.locatedCount, values.observationCount)
        coverage(text(R.string.synthesis_specialized_wood_quality), values.qualityCount, values.observationCount)
        paragraph(text(R.string.synthesis_specialized_environmental_hint), size = 9f)
        keyValue(text(R.string.synthesis_specialized_surface), values.referenceAreaM2?.let {
            "${number(it, 2)} m² (${number(it / 10_000.0, 4)} ha)"
        } ?: unknown())
        keyValue(text(R.string.synthesis_specialized_surface_reason), surfaceReason(values.surfaceReason))
        note(text(R.string.synthesis_specialized_surface_hint))
        section(text(R.string.synthesis_specialized_species_distribution))
        bars(report.perEssence.map { "${it.name} (${it.code})" to it.count },
            text(R.string.synthesis_specialized_no_observations))
        section(text(R.string.synthesis_specialized_categories))
        bars(values.categories.entries.map { it.key to it.value }, text(R.string.synthesis_specialized_no_categories))
        section(text(R.string.synthesis_specialized_health))
        bars(values.healthStates.entries.map { it.key to it.value }, text(R.string.synthesis_specialized_no_health))
        section(text(R.string.synthesis_specialized_qualities))
        val qualities = document.observations.mapNotNull { it.qualite }.groupingBy { it }.eachCount().toSortedMap()
        bars(qualities.map { text(R.string.synthesis_specialized_quality_code, it.key) to it.value },
            text(R.string.synthesis_specialized_no_quality))
        paragraph(text(R.string.synthesis_specialized_category_hint), size = 9f)
        section(text(R.string.synthesis_specialized_diameters))
        paragraph(text(R.string.synthesis_specialized_diameter_hint), size = 9f)
        if (data.invalidDiameters > 0) note(text(R.string.synthesis_specialized_invalid_diameter, data.invalidDiameters))
        bars(data.diameters.map { "${it.diameterCm} cm" to it.count }, text(R.string.synthesis_specialized_no_diameter))
    }

    private fun location() {
        section(text(R.string.synthesis_specialized_location))
        val map = data.map
        if (map == null) note(text(R.string.synthesis_specialized_map_absent))
        else {
            geographicMap(map)
            paragraph(text(R.string.synthesis_specialized_bounds,
                number(map.bounds.west, 6), number(map.bounds.east, 6),
                number(map.bounds.south, 6), number(map.bounds.north, 6)), size = 8.5f)
            paragraph(text(R.string.synthesis_specialized_map_counts,
                map.parcel.size, map.footprints.size, map.plotCenters.size, map.trees.size,
                map.traceSegments.size), size = 8.5f)
            paragraph(text(R.string.synthesis_specialized_map_hint), size = 9f)
        }
        section(text(R.string.synthesis_specialized_scope))
        keyValue(text(R.string.synthesis_specialized_record), report.selectedPlotName ?: unknown())
        keyValue(text(R.string.synthesis_specialized_observations), report.observationCount.toString())
        keyValue(text(R.string.synthesis_specialized_volume), report.volumeM3?.let { "${number(it, 3)} m³" } ?: unknown())
        paragraph(text(if (report.isVolumeComplete) R.string.synthesis_volume_complete
            else R.string.synthesis_volume_partial, report.volumeComputedCount, report.observationCount), size = 9f)
        val volumeReasons = currentSynthesisVolumeReasons(report)
        if (volumeReasons.isNotEmpty()) {
            section(text(R.string.synthesis_uncomputed))
            volumeReasons.forEach { (reason, count) ->
                keyValue(text(synthesisVolumeReasonResource(reason)), count.toString())
            }
            if (needsSynthesisVolumeQualification(report)) {
                paragraph(text(R.string.synthesis_volume_conditions_body), size = 9f)
                note(text(R.string.synthesis_volume_conditions_commercial))
            }
        }
    }

    private fun annex() {
        section(text(R.string.synthesis_specialized_annex))
        keyValue(text(R.string.synthesis_specialized_engine), report.engineVersion)
        keyValue(text(R.string.synthesis_specialized_format), input.formatVersion.toString())
        keyValue(text(R.string.synthesis_specialized_timezone), input.illustrated.timeZoneId)
        document.closedAt?.let { paragraph(text(R.string.synthesis_closed_at, date(it))) }
        report.campaignId?.let { keyValue(text(R.string.synthesis_campaign), it) }
        document.sourceFingerprint?.let { keyValue(text(R.string.synthesis_specialized_fingerprint), it) }
        section(text(R.string.synthesis_specialized_parameters))
        val parameters = runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromJsonElement(SynthesisParameters.serializer(), document.parameters)
        }.getOrNull()
        if (parameters == null) note(text(R.string.synthesis_specialized_parameters_unknown))
        else {
            keyValue(text(R.string.synthesis_specialized_param_tarif), parameters.tarifSelection?.method ?: unknown())
            keyValue(text(R.string.synthesis_specialized_param_number),
                listOfNotNull(parameters.tarifSelection?.schaefferNumero, parameters.tarifSelection?.ifnNumero)
                    .takeIf { it.isNotEmpty() }?.joinToString(" / ") ?: unknown())
            keyValue(text(R.string.synthesis_specialized_param_classes), parameters.diameterClasses
                .takeIf { it.isNotEmpty() }?.joinToString(", ") ?: unknown())
            keyValue(text(R.string.synthesis_specialized_param_policy), parameters.sourcePolicy)
            parameters.tarifSelection?.essenceOverrides?.takeIf { it.isNotEmpty() }?.let { methods ->
                paragraph(text(R.string.synthesis_specialized_param_overrides), bold = true)
                methods.forEach { (essence, method) -> keyValue(essence, method) }
            }
            if (parameters.heightModes.isNotEmpty()) {
                paragraph(text(R.string.synthesis_specialized_param_height), bold = true)
                parameters.heightModes.forEach { entry ->
                    val mode = when (entry.mode.uppercase(java.util.Locale.ROOT)) {
                        "FIXED" -> text(R.string.synthesis_specialized_height_fixed, number(entry.fixed, 2))
                        "SAMPLES" -> text(if (report.engineVersion == SynthesisReport.CURRENT_ENGINE_VERSION)
                            R.string.synthesis_recorded_class_heights else R.string.synthesis_specialized_height_samples)
                        "DEFAULT" -> text(if (report.engineVersion == SynthesisReport.CURRENT_ENGINE_VERSION)
                            R.string.synthesis_recorded_tree_height else R.string.synthesis_specialized_height_default)
                        else -> entry.mode
                    }
                    paragraph(text(R.string.synthesis_specialized_param_height_mode, entry.essence, entry.diamClass, mode), size = 9f)
                }
            }
        }
        paragraph(text(R.string.synthesis_specialized_parameter_rule), size = 9f)
        section(text(R.string.synthesis_specialized_sources))
        if (report.sourceReferences.isEmpty()) paragraph(text(R.string.synthesis_specialized_no_sources))
        else report.sourceReferences.forEachIndexed { index, source ->
            paragraph(text(R.string.synthesis_specialized_source_number, index + 1), bold = true, size = 9f, color = accent)
            paragraph(source, size = 9f)
        }
        val limits = listOf(text(R.string.synthesis_limit_stand), text(R.string.synthesis_limit_territory),
            text(R.string.synthesis_export_scope))
        keepParagraphsTogether(limits, size = 9f)
        limits.forEach { paragraph(it, size = 9f) }
    }

    private data class Metric(val label: String, val value: String)

    private fun metrics(values: List<Metric>) {
        val gap = 10f
        val cardWidth = (contentWidth - gap * (values.size - 1)) / values.size
        val labelPen = pen(8.5f, SpecializedPalette.muted)
        val valuePen = pen(18f, accent, true)
        val labels = values.map { lines(it.label, cardWidth - 24f, labelPen) }
        val amounts = values.map { lines(it.value, cardWidth - 24f, valuePen) }
        val cardHeight = max(78f, labels.maxOf { it.size } * 12f + amounts.maxOf { it.size } * 23f + 27f)
        if (cardHeight > bottom - 88f) { values.forEach { keyValue(it.label, it.value) }; return }
        ensure(cardHeight + 12f)
        values.indices.forEach { index ->
            val left = margin + index * (cardWidth + gap)
            canvas().drawRoundRect(RectF(left, y, left + cardWidth, y + cardHeight), 10f, 10f,
                pen(1f, SpecializedPalette.light))
            canvas().drawRoundRect(RectF(left, y, left + 3f, y + cardHeight), 2f, 2f, pen(1f, accent))
            drawLines(labels[index], left + 12f, y + 19f, 12f, labelPen)
            drawLines(amounts[index], left + 12f, y + 29f + labels.maxOf { it.size } * 12f, 23f, valuePen)
        }
        y += cardHeight + 14f
    }

    private fun coverage(label: String, count: Int, total: Int) {
        val labels = lines(label, 225f, body)
        val rowHeight = max(30f, labels.size * 14f + 8f)
        ensure(rowHeight)
        drawLines(labels, margin, y + 14f, 14f, body)
        val track = RectF(margin + 239f, y + 5f, margin + 423f, y + 15f)
        canvas().drawRoundRect(track, 4f, 4f, pen(1f, SpecializedPalette.line))
        val part = if (total > 0) (count.toDouble() / total).coerceIn(0.0, 1.0).toFloat() else 0f
        if (part > 0f) canvas().drawRoundRect(RectF(track.left, track.top, track.left + track.width() * part, track.bottom),
            4f, 4f, pen(1f, accent))
        canvas().drawText(fraction(count, total), width - margin, y + 14f,
            pen(9f, SpecializedPalette.ink, true).apply { textAlign = Paint.Align.RIGHT })
        y += rowHeight
    }

    /** Every recorded label/count is retained; long labels and every continuation repeat headers. */
    private fun bars(rows: List<Pair<String, Int>>, emptyLabel: String) {
        if (rows.isEmpty()) { paragraph(emptyLabel); return }
        val maximum = rows.maxOf { it.second }.coerceAtLeast(1)
        table(listOf(text(R.string.synthesis_specialized_observed_label), text(R.string.synthesis_specialized_count)),
            listOf(423f, 100f), rows.map { listOf(it.first, it.second.toString()) }, barValues = rows.map { it.second }, barMaximum = maximum)
    }

    /** Wrapped cells, repeated headers and continued oversized rows without silently dropping data. */
    private fun table(
        headers: List<String>,
        widths: List<Float>,
        rows: List<List<String>>,
        barValues: List<Int>? = null,
        barMaximum: Int = 1,
    ) {
        require(headers.size == widths.size && rows.all { it.size == widths.size })
        require(kotlin.math.abs(widths.sum() - contentWidth) < 0.1f)
        require(barValues == null || barValues.size == rows.size)
        val headerLines = headers.mapIndexed { index, label -> lines(label, widths[index] - 14f, tableHeaderPen) }
        val headerHeight = headerLines.maxOf { it.size } * lineHeight + 14f
        fun header() {
            ensure(headerHeight + lineHeight + 14f)
            canvas().drawRoundRect(RectF(margin, y, width - margin, y + headerHeight), 3f, 3f, pen(1f, accent))
            var left = margin
            headerLines.forEachIndexed { index, labels ->
                drawLines(labels, left + 7f, y + 14f, lineHeight, tableHeaderPen)
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
            if (needed <= bottom - 88f - headerHeight && y + needed > bottom) { newPage(); header() }
            while (offset < lineCount) {
                var available = ((bottom - y - 14f) / lineHeight).toInt()
                if (available < 1) { newPage(); header(); available = ((bottom - y - 14f) / lineHeight).toInt() }
                val take = min(lineCount - offset, available)
                val rowHeight = take * lineHeight + 14f
                canvas().drawRect(margin, y, width - margin, y + rowHeight,
                    pen(1f, if (rowIndex % 2 == 0) SpecializedPalette.light else Color.WHITE))
                var left = margin
                cellLines.forEachIndexed { index, value ->
                    drawLines(value.drop(offset).take(take), left + 7f, y + 14f, lineHeight, tablePen)
                    left += widths[index]
                }
                barValues?.let { values ->
                    if (offset + take == lineCount) {
                        val leftBar = width - margin - widths.last() + 7f
                        val availableWidth = widths.last() - 14f
                        canvas().drawRect(leftBar, y + rowHeight - 7f,
                            leftBar + availableWidth * (values[rowIndex].toFloat() / barMaximum).coerceIn(0f, 1f),
                            y + rowHeight - 4f, pen(1f, accent))
                    }
                }
                y += rowHeight
                canvas().drawLine(margin, y, width - margin, y, stroke(SpecializedPalette.line, 0.6f))
                offset += take
                if (offset < lineCount) { newPage(); header() }
            }
        }
        y += 12f
    }

    private fun geographicMap(map: IllustratedMapData) {
        // Leave room for the captured scope and its volume coverage on the same A4 page.
        ensure(360f)
        val frame = RectF(margin, y, width - margin, y + 260f)
        val viewport = RectF(frame.left + 19f, frame.top + 19f, frame.right - 19f, frame.bottom - 19f)
        canvas().drawRoundRect(frame, 10f, 10f, pen(1f, Color.WHITE))
        canvas().drawRoundRect(frame, 10f, 10f, stroke(SpecializedPalette.line, 1f))
        val cosine = cos(Math.toRadians((map.bounds.north + map.bounds.south) / 2.0)).coerceAtLeast(0.01)
        val spanX = (map.bounds.east - map.bounds.west).coerceAtLeast(1e-8) * cosine
        val spanY = (map.bounds.north - map.bounds.south).coerceAtLeast(1e-8)
        val scale = min(viewport.width() / spanX, viewport.height() / spanY)
        val left = viewport.left + (viewport.width() - spanX * scale) / 2.0
        val top = viewport.top + (viewport.height() - spanY * scale) / 2.0
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
            canvas().drawPath(path, pen(1f, SpecializedPalette.parcel))
            canvas().drawPath(path, stroke(SpecializedPalette.forest, 1.5f))
        }
        map.footprints.forEach { value ->
            val path = polygon(value)
            canvas().drawPath(path, pen(1f, SpecializedPalette.sand))
            canvas().drawPath(path, stroke(SpecializedPalette.blue, 1f))
        }
        map.traceSegments.forEach { segment ->
            if (segment.size >= 2) {
                val path = Path()
                segment.forEachIndexed { index, point ->
                    val (x, py) = project(point)
                    if (index == 0) path.moveTo(x, py) else path.lineTo(x, py)
                }
                canvas().drawPath(path, stroke(SpecializedPalette.trace, 1.3f)
                    .apply { pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f) })
            }
        }
        map.trees.forEach { point ->
            val (x, py) = project(point)
            canvas().drawCircle(x, py, 2.7f, pen(1f, SpecializedPalette.forest))
        }
        map.plotCenters.forEach { point ->
            val (x, py) = project(point)
            canvas().drawPath(Path().apply { moveTo(x, py - 4f); lineTo(x - 4f, py + 3.5f)
                lineTo(x + 4f, py + 3.5f); close() }, pen(1f, SpecializedPalette.blue))
        }
        canvas().restoreToCount(saved)
        val northX = frame.right - 24f
        val northY = frame.top + 29f
        canvas().drawCircle(northX, northY, 17f, pen(1f, Color.WHITE))
        canvas().drawLine(northX, northY + 8f, northX, northY - 3f, stroke(SpecializedPalette.ink, 1.2f))
        canvas().drawPath(Path().apply { moveTo(northX, northY - 8f); lineTo(northX - 3f, northY - 2f)
            lineTo(northX + 3f, northY - 2f); close() }, pen(1f, SpecializedPalette.ink))
        canvas().drawText(text(R.string.synthesis_illustrated_map_north), northX - 3f, northY - 11f,
            pen(8f, SpecializedPalette.ink, true))
        y = frame.bottom + 14f
        legend(text(R.string.synthesis_illustrated_map_parcel), SpecializedPalette.forest, 0)
        legend(text(R.string.synthesis_illustrated_map_footprint), SpecializedPalette.blue, 1)
        legend(text(R.string.synthesis_illustrated_map_center), SpecializedPalette.blue, 2)
        legend(text(R.string.synthesis_illustrated_map_tree), SpecializedPalette.forest, 3)
        legend(text(R.string.synthesis_illustrated_map_trace), SpecializedPalette.trace, 4)
        y += 7f
    }

    private fun legend(label: String, color: Int, shape: Int) {
        val wrapped = lines(label, contentWidth - 26f, small)
        ensure(wrapped.size * 12f + 5f)
        val x = margin + 7f
        val py = y + 6f
        when (shape) {
            0 -> canvas().drawRect(x - 4f, py - 4f, x + 5f, py + 4f, stroke(color, 1.2f))
            1 -> { canvas().drawRect(x - 4f, py - 4f, x + 5f, py + 4f, pen(1f, SpecializedPalette.sand))
                canvas().drawRect(x - 4f, py - 4f, x + 5f, py + 4f, stroke(color, 1f)) }
            2 -> canvas().drawPath(Path().apply { moveTo(x, py - 4f); lineTo(x - 4f, py + 4f)
                lineTo(x + 4f, py + 4f); close() }, pen(1f, color))
            3 -> canvas().drawCircle(x, py, 2.7f, pen(1f, color))
            else -> canvas().drawLine(x - 6f, py, x + 7f, py, stroke(color, 1.3f)
                .apply { pathEffect = DashPathEffect(floatArrayOf(3f, 2f), 0f) })
        }
        drawLines(wrapped, margin + 25f, y + 9f, 12f, small)
        y += wrapped.size * 12f + 5f
    }

    private fun section(value: String) {
        sectionNumber++
        val p = pen(14f, accent, true)
        val wrapped = lines(value, contentWidth - 39f, p)
        ensure(wrapped.size * 19f + 40f)
        y += 8f
        canvas().drawRoundRect(RectF(margin, y - 1f, margin + 25f, y + 23f), 5f, 5f, pen(1f, accent))
        canvas().drawText(text(R.string.synthesis_specialized_section_index, sectionNumber), margin + 5f, y + 14f,
            pen(9f, Color.WHITE, true))
        drawLines(wrapped, margin + 36f, y + 15f, 19f, p)
        y += wrapped.size * 19f + 8f
    }

    private fun note(value: String) {
        val p = pen(9f, SpecializedPalette.amber)
        val wrapped = lines(value, contentWidth - 24f, p)
        val height = wrapped.size * 13f + 20f
        if (height > bottom - 88f) { paragraph(value, size = 9f, color = SpecializedPalette.amber); return }
        ensure(height + 12f)
        canvas().drawRoundRect(RectF(margin, y, width - margin, y + height), 8f, 8f, pen(1f, SpecializedPalette.sand))
        drawLines(wrapped, margin + 12f, y + 17f, 13f, p)
        y += height + 12f
    }

    private fun keyValue(label: String, value: String) {
        val labelLines = lines(label, contentWidth, pen(9f, SpecializedPalette.muted, true)).size
        val firstValueLines = min(2, lines(value, contentWidth, body).size)
        ensure(labelLines * 12.6f + firstValueLines * 14f + 5f)
        paragraph(label, bold = true, size = 9f, color = SpecializedPalette.muted, after = 1f)
        paragraph(value, size = 10f, after = 4f)
    }

    /** A short closing block must not strand its last sentence on a new, otherwise empty page. */
    private fun keepParagraphsTogether(values: List<String>, size: Float) {
        val p = pen(size, SpecializedPalette.ink)
        val needed = values.sumOf { lines(it, contentWidth, p).size.toDouble() * size * 1.4f + 7f }.toFloat()
        if (needed <= (bottom - 88f) / 3f) ensure(needed + 3f)
    }

    private fun paragraph(
        value: String,
        bold: Boolean = false,
        size: Float = 10f,
        color: Int = SpecializedPalette.ink,
        after: Float = 7f,
    ) {
        val p = pen(size, color, bold)
        val step = size * 1.4f
        val wrapped = lines(value, contentWidth, p)
        val needed = wrapped.size * step + after
        // Ordinary notes, short references and scope statements stay whole; genuinely long
        // source identifiers still paginate without truncation or shrinking their text.
        if (needed <= (bottom - 88f) / 4f) ensure(needed + 3f)
        wrapped.forEachIndexed { index, line ->
            val remaining = wrapped.size - index
            ensure(if (remaining == 2) 2f * step + after else step + 3f)
            canvas().drawText(line, margin, y + size, p)
            y += step
        }
        y += after
    }

    private fun lines(value: String, available: Float, p: Paint): List<String> =
        wrapPdfText(value, available) { part, size -> p.breakText(part, true, size, null) }

    private fun drawLines(values: List<String>, left: Float, baseline: Float, step: Float, p: Paint) {
        values.forEachIndexed { index, value -> canvas().drawText(value, left, baseline + index * step, p) }
    }

    private fun canvas(): Canvas { if (page == null) beginPage(); return page!!.canvas }
    private fun ensure(space: Float) { if (page == null) beginPage(); if (y + space > bottom) newPage() }
    private fun newPage() { finishPage(); beginPage() }

    private fun beginPage(cover: Boolean = false) {
        pageNumber++
        page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, pageNumber).create())
        val current = page!!.canvas
        current.drawColor(Color.WHITE)
        if (cover) {
            current.drawRect(0f, 0f, width.toFloat(), 205f, pen(1f, accent))
            current.drawText("GeoSylva", margin, 46f, pen(22f, Color.WHITE, true))
            current.drawText(text(R.string.synthesis_specialized_report), margin, 80f, pen(8.5f, Color.WHITE))
            val titlePen = pen(29f, Color.WHITE, true)
            drawLines(lines(text(titleId), contentWidth, titlePen), margin, 124f, 34f, titlePen)
            val subtitle = when (input.type) {
                SpecializedReportType.REVENUE -> R.string.synthesis_specialized_revenue_subtitle
                SpecializedReportType.COSTS -> R.string.synthesis_specialized_costs_subtitle
                SpecializedReportType.ENVIRONMENTAL -> R.string.synthesis_specialized_environmental_subtitle
            }
            current.drawText(text(subtitle), margin, 186f, pen(10f, Color.WHITE))
            y = 232f
        } else {
            current.drawRect(0f, 0f, width.toFloat(), 5f, pen(1f, accent))
            current.drawText("GeoSylva", margin, 40f, pen(17f, accent, true))
            current.drawText(text(titleId), width - margin, 39f,
                pen(9f, SpecializedPalette.muted).apply { textAlign = Paint.Align.RIGHT })
            current.drawLine(margin, 62f, width - margin, 62f, stroke(SpecializedPalette.line, 0.7f))
            y = 88f
        }
    }

    private fun finishPage() {
        val current = page ?: return
        val footerY = height - 25f
        current.canvas.drawLine(margin, footerY - 15f, width - margin, footerY - 15f, stroke(SpecializedPalette.line, 0.7f))
        current.canvas.drawText(text(R.string.synthesis_specialized_footer), margin, footerY,
            pen(7.5f, SpecializedPalette.muted))
        current.canvas.drawText(text(R.string.synthesis_pdf_page, pageNumber), width - margin, footerY,
            pen(8f, SpecializedPalette.muted).apply { textAlign = Paint.Align.RIGHT })
        pdf.finishPage(current)
        page = null
    }

    private fun text(id: Int, vararg arguments: Any): String = context.getString(id, *arguments)
    private fun unknown(): String = text(R.string.synthesis_specialized_unknown)
    private fun fraction(count: Int, total: Int) = text(R.string.synthesis_specialized_count_fraction, count, total)
    private fun amount(cents: Long?): String = cents?.let {
        java.text.NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(BigDecimal.valueOf(it, 2))
    } ?: unknown()
    private fun money(cents: Long?): String = cents?.let { "${amount(it)} €" } ?: unknown()
    private fun number(value: Double?, decimals: Int): String = value?.takeIf(Double::isFinite)?.let {
        String.format(locale, "%.${decimals}f", it)
    } ?: unknown()
    private fun date(value: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", locale).apply {
        timeZone = TimeZone.getTimeZone(ZoneId.of(input.illustrated.timeZoneId))
    }.format(Date(value))
    private fun unit(value: SpecializedCostUnit): String = text(when (value) {
        SpecializedCostUnit.HOUR -> R.string.synthesis_specialized_unit_hour
        SpecializedCostUnit.DAY -> R.string.synthesis_specialized_unit_day
        SpecializedCostUnit.CUBIC_METRE -> R.string.synthesis_specialized_unit_m3
        SpecializedCostUnit.HECTARE -> R.string.synthesis_specialized_unit_ha
        SpecializedCostUnit.KILOMETRE -> R.string.synthesis_specialized_unit_km
        SpecializedCostUnit.ITEM -> R.string.synthesis_specialized_unit_item
        SpecializedCostUnit.FIXED_JOB -> R.string.synthesis_specialized_unit_fixed
    })
    private fun surfaceReason(value: String): String = when (value) {
        "ENTIRE_PARCEL" -> text(R.string.synthesis_specialized_surface_entire)
        "CIRCULAR_PLOT" -> text(R.string.synthesis_specialized_surface_circle)
        "RECORDED_PLOT_AREA" -> text(R.string.synthesis_specialized_surface_recorded)
        "SAMPLING_BOUNDARY_UNKNOWN" -> text(R.string.synthesis_specialized_surface_edge)
        "CHANGED_PARCEL_AREA" -> text(R.string.synthesis_specialized_surface_changed)
        "INCONSISTENT_PLOT_AREA" -> text(R.string.synthesis_specialized_surface_inconsistent)
        else -> text(R.string.synthesis_specialized_surface_unresolved) + " ($value)"
    }
    private fun pen(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
    private fun stroke(color: Int, width: Float) = pen(1f, color).apply { style = Paint.Style.STROKE; strokeWidth = width }
}
