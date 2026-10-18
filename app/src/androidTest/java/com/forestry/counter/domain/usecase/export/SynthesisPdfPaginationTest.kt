package com.forestry.counter.domain.usecase.export

import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.domain.calculation.SynthesisParameters
import com.forestry.counter.domain.calculation.SynthesisReportCalculator
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.model.Tige
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic test observations stay in cache and never enter the pilot database or pack registry. */
@RunWith(AndroidJUnit4::class)
class SynthesisPdfPaginationTest {
    @Test fun many_real_report_rows_produce_readable_native_pages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val parcel = Parcelle("PDF_TEST", null, name = "Pagination test", surfaceHa = 1.0, shape = null,
            slopePct = null, aspect = null, access = null, altitudeM = null, objectifType = null,
            objectifVal = null, tolerancePct = null, samplingMode = null, sampleAreaM2 = null,
            targetSpeciesCsv = null, srid = null, remarks = null)
        val plot = Placette("PDF_PLOT", parcel.id, "Test", "ENTIRE_PARCEL", null, 10_000.0, null)
        val trees = (1..150).map { index -> Tige("test-$index", parcel.id, plot.id, "TEST_SPECIES_$index", 40.0,
            20.0, null, null, null, timestamp = index.toLong(), note = null, produit = null, fCoef = null,
            valueEur = null, sessionId = "PDF_SESSION") }
        val parameters = SynthesisParameters()
        val report = SynthesisReportCalculator.calculate(parcel, listOf(plot), trees,
            trees.associate { it.essenceCode to "Species ${it.id} with a long descriptive name to wrap across the available width" },
            parameters, "PDF_SESSION", "PDF_CAMPAIGN", 1, 1000L)
        val document = SynthesisDocument(report, false, null, null, Json.encodeToJsonElement(parameters).jsonObject, trees)
        val output = File.createTempFile("synthesis-pagination-test-", ".pdf", context.cacheDir)
        try {
            PdfSynthesisExporter.export(context, Uri.fromFile(output), document)
            assertTrue(output.length() > 1000L)
            assertEquals("%PDF", output.inputStream().use { stream -> String(ByteArray(4).also { assertEquals(4, stream.read(it)) }) })
            ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    assertTrue("Long reports must paginate", renderer.pageCount > 5)
                    renderer.openPage(renderer.pageCount - 1).use { page ->
                        assertEquals(595, page.width)
                        assertEquals(842, page.height)
                    }
                }
            }
        } finally { output.delete() }
    }
}
