package com.forestry.counter.domain.usecase.export

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.domain.calculation.SynthesisParameters
import com.forestry.counter.domain.calculation.SynthesisReportCalculator
import com.forestry.counter.domain.calculation.SynthesisReport
import com.forestry.counter.domain.calculation.tarifs.TarifSelection
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.model.Tige
import java.io.File
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Cache rendering plus explicit TEST v3 PDFs for review; no observation reaches the pilot database. */
@RunWith(AndroidJUnit4::class)
class SpecializedSynthesisPdfTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun three_distinct_reports_render_as_a4_without_mutating_the_captured_inputs() {
        val covers = mutableListOf<IntArray>()
        for (type in SpecializedReportType.values()) {
            val input = fixture(type, count = 4, representativeClosed = true,
                shape = "POLYGON((2 45,2.02 45,2.02 45.02,2 45.02,2 45))", historicalV2 = true)
            val before = Json.encodeToString(SpecializedSynthesisDocument.serializer(), input)
            assertEquals(SynthesisReport.ENGINE_VERSION, input.illustrated.document.report.engineVersion)
            assertTrue(currentSynthesisVolumeReasons(input.illustrated.document.report).isEmpty())
            withPdf(input) { renderer ->
                assertEquals("The historical v2 TEST survey must retain four useful pages, without a new v3 notice or an orphan line",
                    4, renderer.pageCount)
                assertReadablePages(renderer)
                val cover = renderPage(renderer, 0)
                try { covers += pixels(cover) } finally { cover.recycle() }
            }
            assertEquals("Rendering must leave the captured observations and cost annex unchanged",
                before, Json.encodeToString(SpecializedSynthesisDocument.serializer(), input))
        }
        assertEquals(3, covers.size)
        assertFalse("Revenue and costs need distinct content and covers", covers[0].contentEquals(covers[1]))
        assertFalse("Environmental observations need their own publication", covers[1].contentEquals(covers[2]))
        assertFalse(covers[0].contentEquals(covers[2]))
    }

    @Test fun current_v3_reports_keep_unavailable_volume_and_render_readable_test_pdfs_without_mutation() {
        val directory = File(requireNotNull(context.getExternalFilesDir(null)),
            "GeoSylva-TEST-synthesis-cubage-2026-10-12/${UUID.randomUUID()}")
        assertTrue("The isolated TEST output directory must be created", directory.mkdirs())
        for (type in SpecializedReportType.values()) {
            val input = fixture(type, count = 4, representativeClosed = true,
                shape = "POLYGON((2 45,2.02 45,2.02 45.02,2 45.02,2 45))")
            val report = input.illustrated.document.report
            val before = Json.encodeToString(SpecializedSynthesisDocument.serializer(), input)
            assertEquals(SynthesisReport.CURRENT_ENGINE_VERSION, report.engineVersion)
            assertEquals(mapOf("UNQUALIFIED_TARIF" to 4), currentSynthesisVolumeReasons(report))
            assertTrue(needsSynthesisVolumeQualification(report))
            assertNull(report.volumeM3)
            assertNull(report.volumePerHa)
            assertEquals(0, report.volumeComputedCount)
            assertTrue(report.perEssence.all { it.volumeM3 == null && it.volumeComputedCount == 0 })
            val file = File(directory, "TEST-v3-${type.name.lowercase(Locale.ROOT)}.pdf")
            assertTrue("A TEST PDF must not overwrite an existing file", file.createNewFile())
            file.outputStream().use { stream -> writeSpecializedSynthesisPdf(context, input) { it.writeTo(stream) } }
            assertTrue("A TEST PDF must contain rendered content", file.length() > 1_000L)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer -> assertReadablePages(renderer) }
            }
            assertEquals("Rendering must leave every v3 input, reason and declared amount unchanged",
                before, Json.encodeToString(SpecializedSynthesisDocument.serializer(), input))
            println("GEOSYLVA_TEST_SYNTHESIS_PDF=${file.absolutePath}")
            // Keep only these explicitly identified TEST PDFs for pdftotext and visual inspection.
        }
    }

    @Test fun long_revenue_and_cost_tables_continue_as_readable_pages_without_a_row_limit() {
        val revenue = fixture(SpecializedReportType.REVENUE, count = 95, longLabels = true)
        withPdf(revenue) { renderer ->
            assertTrue("All species and detailed observation rows must paginate", renderer.pageCount > 10)
            assertReadablePages(renderer)
        }
        val costs = fixture(SpecializedReportType.COSTS, count = 1).copy(costLines = (1..65).map { index ->
            SpecializedCostLine("TEST-cost-$index",
                "TEST operation $index with a detailed forestry task label retained completely in the table",
                "1.25", SpecializedCostUnit.HOUR, "12.34",
                "TEST entered quotation $index: " + "a provenance detail retained completely. ".repeat(7),
                "2026-10-07", SpecializedCostBasis.USER_QUOTATION)
        })
        withPdf(costs) { renderer ->
            assertTrue("Operation and provenance tables must both retain all declared rows", renderer.pageCount > 10)
            assertReadablePages(renderer)
        }
    }

    @Test fun polygon_holes_stay_unfilled_in_the_specialized_location_map() {
        val exterior = "2 45,2.02 45,2.02 45.02,2 45.02,2 45"
        val hole = "2.006 45.006,2.014 45.006,2.014 45.014,2.006 45.014,2.006 45.006"
        val solidInput = fixture(SpecializedReportType.ENVIRONMENTAL, shape = "POLYGON(($exterior))")
        val holedInput = fixture(SpecializedReportType.ENVIRONMENTAL, shape = "POLYGON(($exterior),($hole))")
        val parcelFill = Color.rgb(221, 235, 215)
        withPdf(solidInput) { solid ->
            withPdf(holedInput) { holed ->
                val (index, solidMap) = findMapPage(solid, parcelFill)
                val holedMap = renderPage(holed, index)
                try {
                    var minX = solidMap.width
                    var minY = solidMap.height
                    var maxX = 0
                    var maxY = 0
                    for (y in 88 until 720) for (x in 36 until 559) {
                        if (solidMap.getPixel(x, y) == parcelFill) {
                            minX = minOf(minX, x); maxX = maxOf(maxX, x)
                            minY = minOf(minY, y); maxY = maxOf(maxY, y)
                        }
                    }
                    val centerX = (minX + maxX) / 2
                    val centerY = (minY + maxY) / 2
                    assertEquals(parcelFill, solidMap.getPixel(centerX, centerY))
                    assertEquals("A geographic hole is not a second filled parcel", Color.WHITE,
                        holedMap.getPixel(centerX, centerY))
                    assertEquals(solid.pageCount, holed.pageCount)
                } finally { solidMap.recycle(); holedMap.recycle() }
            }
        }
    }

    @Test fun absent_geometry_never_produces_a_substitute_map_in_any_report_type() {
        val parcelFill = Color.rgb(221, 235, 215)
        for (type in SpecializedReportType.values()) {
            val input = fixture(type, shape = null)
            assertEquals(null, buildIllustratedSynthesisData(input.illustrated).map)
            withPdf(input) { renderer ->
                for (index in 0 until renderer.pageCount) {
                    val bitmap = renderPage(renderer, index)
                    try {
                        assertFalse("A report with no geometry must not contain an invented parcel fill",
                            pixels(bitmap).any { it == parcelFill })
                    } finally { bitmap.recycle() }
                }
            }
        }
    }

    @Test fun captured_dates_survive_device_timezone_changes_and_the_english_publication_is_distinct() {
        val input = fixture(SpecializedReportType.REVENUE).let {
            it.copy(illustrated = it.illustrated.copy(timeZoneId = "Europe/Paris"))
        }
        val originalZone = TimeZone.getDefault()
        val french = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.FRENCH) })
        val english = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) })
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            withPdf(input, french) { first ->
                val firstPage = renderPage(first, 0)
                val firstPixels = try { pixels(firstPage) } finally { firstPage.recycle() }
                TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))
                withPdf(input, french) { second ->
                    assertEquals(first.pageCount, second.pageCount)
                    val secondPage = renderPage(second, 0)
                    try {
                        assertTrue("Dates must use the captured zone rather than the new device zone",
                            firstPixels.contentEquals(pixels(secondPage)))
                    } finally { secondPage.recycle() }
                }
                withPdf(input, english) { englishPdf ->
                    val englishPage = renderPage(englishPdf, 0)
                    try { assertFalse(firstPixels.contentEquals(pixels(englishPage))) }
                    finally { englishPage.recycle() }
                }
            }
        } finally { TimeZone.setDefault(originalZone) }
    }

    private fun fixture(
        type: SpecializedReportType,
        count: Int = 1,
        longLabels: Boolean = false,
        shape: String? = null,
        representativeClosed: Boolean = false,
        historicalV2: Boolean = false,
    ): SpecializedSynthesisDocument {
        val parcel = Parcelle("SPECIALIZED_TEST_PARCEL", null, name = "TEST — Specialized reports",
            surfaceHa = 1.0, shape = shape, slopePct = null, aspect = null, access = null, altitudeM = null,
            objectifType = null, objectifVal = null, tolerancePct = null, samplingMode = null, sampleAreaM2 = null,
            targetSpeciesCsv = null, srid = 4326, remarks = null,
            source = if (representativeClosed) "TEST recorded parcel source; retrieved=1760001234000; " +
                "geometry-sha256=" + "a".repeat(64) + "; TEST attributed data source, release date and licence retained verbatim"
            else null)
        val plot = Placette("SPECIALIZED_TEST_PLOT", parcel.id, "TEST plot", "ENTIRE_PARCEL", null, 10_000.0, null)
        val trees = (1..count).map { index -> Tige(if (representativeClosed)
            "00000000-0000-0000-0000-${index.toString().padStart(12, '0')}" else "TEST-tree-$index",
            parcel.id, plot.id, if (representativeClosed) "TEST_OAK" else "TEST_$index",
            if (representativeClosed) 40.0 else index.toDouble() + 10.0,
            20.0, null, null, null, timestamp = 1_760_001_234_000L + index,
            note = null, produit = if (representativeClosed) null else "TEST product", fCoef = null,
            valueEur = if (representativeClosed || index % 3 == 0) null else index * 1.005,
            categorie = if (!representativeClosed && index % 2 == 0) "TEST biological tree" else null,
            qualite = if (!representativeClosed && index % 2 == 0) 2 else null,
            etatSanitaire = if (!representativeClosed && index % 2 == 0) "TEST recorded health" else null,
            isTigeHabitat = !representativeClosed && index % 2 == 0, sessionId = "SPECIALIZED_TEST_SESSION",
            source = "TEST entered field observation") }
        val parameters = SynthesisParameters(selectedPlotId = plot.id,
            tarifSelection = if (representativeClosed) TarifSelection("SCHAEFFER_1E", schaefferNumero = 8) else null,
            diameterClasses = if (representativeClosed) (5..100 step 5).toList() +
                listOf(110, 120, 130, 140, 150, 160, 170, 180, 190, 200, 225, 250, 275, 300, 350, 400, 450, 500)
            else emptyList())
        val names = trees.associate { tree -> tree.essenceCode to if (longLabels)
            "TEST species ${tree.id}: a deliberately long scientific label kept completely in both the species table and the detailed observation rows of this native PDF publication"
        else "TEST species" }
        val report = SynthesisReportCalculator.calculate(parcel, listOf(plot), trees, names, parameters,
            "SPECIALIZED_TEST_SESSION", "SPECIALIZED_TEST_CAMPAIGN", 1, 1_760_001_234_000L)
        val fixtureReport = if (historicalV2) {
            require(representativeClosed && count == 4)
            // Representative frozen TEST v2 content matching the former fixture's four 40-cm stems.
            // This is not a field archive or a scientific qualification; no legacy engine runs here.
            val recordedVolume = 9.394680658761946
            report.copy(engineVersion = SynthesisReport.ENGINE_VERSION, volumeM3 = recordedVolume,
                volumeComputedCount = count, methods = listOf("SCHAEFFER_1E n°8"), uncomputedReasons = emptyMap(),
                perEssence = report.perEssence.map { it.copy(volumeM3 = recordedVolume, volumeComputedCount = count) })
        } else report
        val captured = fixtureReport.copy(campaignId = if (representativeClosed) "00000000-0000-0000-0000-000000000001"
            else report.campaignId, selectedPlotName = if (representativeClosed) "TEST 1" else report.selectedPlotName)
        val illustrated = IllustratedSynthesisDocument(SynthesisDocument(captured, representativeClosed,
            if (representativeClosed) 1_760_001_234_000L else null, if (representativeClosed) "b".repeat(64) else null,
            Json.encodeToJsonElement(parameters).jsonObject, trees), parcel, listOf(plot), timeZoneId = "Europe/Paris")
        val costs = if (type == SpecializedReportType.COSTS) listOf(
            SpecializedCostLine("TEST-operation", "TEST entered forestry operation", "2.5", SpecializedCostUnit.HOUR,
                "10.125", "TEST entered quotation", "2026-10-07", SpecializedCostBasis.USER_QUOTATION),
            SpecializedCostLine("TEST-incomplete", "TEST incomplete operation", null, SpecializedCostUnit.HECTARE,
                "15.00", "TEST entered estimate"),
        ) else emptyList()
        return SpecializedSynthesisDocument(type, illustrated, costs)
    }

    private fun withPdf(
        input: SpecializedSynthesisDocument,
        renderingContext: Context = context,
        block: (PdfRenderer) -> Unit,
    ) {
        val file = File.createTempFile("specialized-synthesis-test-", ".pdf", context.cacheDir)
        try {
            file.outputStream().use { stream -> writeSpecializedSynthesisPdf(renderingContext, input) { it.writeTo(stream) } }
            assertTrue(file.length() > 1_000L)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use(block)
            }
        } finally { file.delete() }
    }

    private fun assertReadablePages(renderer: PdfRenderer) {
        for (index in 0 until renderer.pageCount) {
            renderer.openPage(index).use { page -> assertEquals(595, page.width); assertEquals(842, page.height) }
            val bitmap = renderPage(renderer, index)
            try {
                var bodyInk = 0
                for (y in 100 until 786 step 3) for (x in 36 until 559 step 3) {
                    val pixel = bitmap.getPixel(x, y)
                    if (Color.red(pixel) < 190 && Color.green(pixel) < 190 && Color.blue(pixel) < 190) bodyInk++
                }
                assertTrue("Page ${index + 1} must contain content beyond its header", bodyInk > 8)
            } finally { bitmap.recycle() }
        }
    }

    private fun renderPage(renderer: PdfRenderer, index: Int): Bitmap = renderer.openPage(index).use { page ->
        Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
        }
    }

    private fun pixels(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private fun findMapPage(renderer: PdfRenderer, fill: Int): Pair<Int, Bitmap> {
        for (index in 0 until renderer.pageCount) {
            val bitmap = renderPage(renderer, index)
            val fillCount = pixels(bitmap).count { it == fill }
            if (fillCount > 20_000) return index to bitmap
            bitmap.recycle()
        }
        error("A genuine geographic parcel polygon must be rendered")
    }
}
