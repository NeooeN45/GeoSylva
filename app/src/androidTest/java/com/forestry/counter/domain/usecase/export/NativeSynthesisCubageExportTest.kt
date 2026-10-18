package com.forestry.counter.domain.usecase.export

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.domain.calculation.SynthesisParameters
import com.forestry.counter.domain.calculation.SynthesisReport
import com.forestry.counter.domain.calculation.SynthesisReportCalculator
import com.forestry.counter.domain.calculation.tarifs.TarifSelection
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.model.Tige
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Explicit, synthetic TEST inputs only: no account, database, GPS, network or existing archive
 * is read. Five provisional publications and their TEST JSON inputs are deliberately retained
 * in a fresh external-files UUID directory for the operator's PDF text and visual inspection.
 * No geographic coordinates are supplied, and no private cache/files directory is available.
 */
@RunWith(AndroidJUnit4::class)
class NativeSynthesisCubageExportTest {
    private val json = Json { encodeDefaults = true }

    @Test fun five_publications_preserve_entered_value_and_explain_unavailable_cubage() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val french = target.createConfigurationContext(Configuration(target.resources.configuration).apply {
            setLocale(Locale.FRANCE)
        })
        val external = requireNotNull(target.getExternalFilesDir(null))
        val directory = File(external, "GeoSylva-TEST-synthesis-cubage-2026-10-12/${UUID.randomUUID()}")
        assertFalse("Every qualification creates a fresh TEST directory", directory.exists())
        assertTrue(directory.mkdirs())
        val isolated = object : ContextWrapper(french) {
            override fun getFilesDir(): File = error("TEST_PRIVATE_FILES_ACCESS_FORBIDDEN")
            override fun getCacheDir(): File = error("TEST_PRIVATE_CACHE_ACCESS_FORBIDDEN")
        }
        val document = fixture()
        val illustrated = IllustratedSynthesisDocument(document, testParcel, listOf(testPlot), timeZoneId = "Europe/Paris")
        val specialized = SpecializedReportType.values().associateWith { type ->
            SpecializedSynthesisDocument(type, illustrated, costLines = if (type == SpecializedReportType.COSTS) listOf(
                SpecializedCostLine("TEST-cost-1", "TEST · Poste de coût saisi pour vérifier le document",
                    "1", SpecializedCostUnit.ITEM, "17.05", "TEST · Montant saisi, sans tarif de marché",
                    "2026-10-12", SpecializedCostBasis.USER_ESTIMATE),
            ) else emptyList())
        }
        val capturedDocument = json.encodeToString(document)
        val capturedIllustrated = json.encodeToString(illustrated)
        val capturedSpecialized = specialized.mapValues { json.encodeToString(it.value) }
        assertEquals(SynthesisReport.CURRENT_ENGINE_VERSION, document.report.engineVersion)
        assertEquals(SynthesisParameters.CURRENT_SOURCE_POLICY,
            json.decodeFromJsonElement(SynthesisParameters.serializer(), document.parameters).sourcePolicy)
        assertEquals(mapOf("UNQUALIFIED_TARIF" to 1), document.report.uncomputedReasons)
        assertNull(document.report.volumeM3)
        assertEquals(0, document.report.volumeComputedCount)
        assertTrue(document.report.methods.isEmpty())
        assertEquals(17.05, requireNotNull(document.report.enteredValueEur), 0.0)
        assertEquals(1705L, requireNotNull(buildSpecializedRevenueData(specialized.getValue(SpecializedReportType.REVENUE)).totalCents))
        val guard = SynthesisReadGuard(current = { true }, validated = { true }) // TEST-only authority, no product identity read.
        val oldLocale = Locale.getDefault()
        val oldZone = TimeZone.getDefault()
        val outputs = mutableListOf<File>()
        try {
            Locale.setDefault(Locale.FRANCE)
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"))
            val standard = newOutput(directory, "TEST-standard.pdf", capturedDocument)
            PdfSynthesisExporter.exportGuarded(isolated, Uri.fromFile(standard), document, guard)
            qualify(standard, bodyStartY = 40)
            outputs += standard
            val illustratedFile = newOutput(directory, "TEST-illustrated.pdf", capturedIllustrated)
            IllustratedSynthesisExporter.exportGuarded(isolated, Uri.fromFile(illustratedFile), illustrated, guard)
            qualify(illustratedFile)
            outputs += illustratedFile
            for ((type, input) in specialized) {
                val output = newOutput(directory, "TEST-${type.name.lowercase(Locale.ROOT)}.pdf", capturedSpecialized.getValue(type))
                SpecializedSynthesisExporter.exportGuarded(isolated, Uri.fromFile(output), input, guard)
                qualify(output)
                outputs += output
            }
            assertEquals(5, outputs.size)
            assertEquals(5, outputs.map { it.absolutePath }.distinct().size)
            assertEquals("Standard publication must retain the captured TEST observations",
                capturedDocument, json.encodeToString(document))
            assertEquals("Illustrated publication must retain the captured TEST inputs",
                capturedIllustrated, json.encodeToString(illustrated))
            for ((type, input) in specialized) {
                assertEquals("$type publication must not mutate its TEST inputs",
                    capturedSpecialized.getValue(type), json.encodeToString(input))
            }
            assertEquals(17.05, requireNotNull(document.observations.single().valueEur), 0.0)
            assertNull(document.observations.single().gpsWkt)
        } finally {
            Locale.setDefault(oldLocale)
            TimeZone.setDefault(oldZone)
            // Explicit TEST outputs are retained, including failed render evidence; nothing is overwritten or removed.
        }
    }

    private fun newOutput(directory: File, name: String, capturedInputs: String): File {
        val output = File(directory, name)
        assertTrue("A TEST export must not overwrite a prior file", output.createNewFile())
        val inputs = File(directory, "$name.inputs.json")
        assertTrue("TEST input metadata must also be new", inputs.createNewFile())
        inputs.writeText(capturedInputs, Charsets.UTF_8)
        println("GEOSYLVA_TEST_SYNTHESIS_PDF=${output.absolutePath}")
        println("GEOSYLVA_TEST_SYNTHESIS_INPUTS=${inputs.absolutePath}")
        return output
    }

    private fun qualify(file: File, bodyStartY: Int = 90) {
        assertTrue("${file.name} must contain a PDF publication", file.length() > 1_000L)
        file.inputStream().use { input ->
            val signature = ByteArray(4)
            assertEquals(4, input.read(signature))
            assertEquals("%PDF", String(signature, Charsets.US_ASCII))
        }
        var pages = 0
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                pages = renderer.pageCount
                assertTrue("No fixed page count: the complete content must paginate", pages > 0)
                for (index in 0 until pages) {
                    renderer.openPage(index).use { page ->
                        assertEquals(595, page.width)
                        assertEquals(842, page.height)
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            var bodyInk = 0
                            for (y in bodyStartY until 786 step 3) for (x in 36 until 559 step 3) {
                                val pixel = bitmap.getPixel(x, y)
                                if (Color.red(pixel) < 190 && Color.green(pixel) < 190 && Color.blue(pixel) < 190) bodyInk++
                            }
                            assertTrue("${file.name} page ${index + 1} must contain readable body content", bodyInk > 8)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        println("GEOSYLVA_TEST_SYNTHESIS_PDF_META=${file.name};pages=$pages;bytes=${file.length()};sha256=$digest")
    }

    private val testParcel = Parcelle(
        id = "TEST-PDF-parcel", forestId = null, name = "TEST · Parcelle pédagogique sans géolocalisation",
        surfaceHa = 1.0, shape = null, slopePct = null, aspect = null, access = null, altitudeM = null,
        objectifType = null, objectifVal = null, tolerancePct = null, samplingMode = null, sampleAreaM2 = null,
        targetSpeciesCsv = null, srid = null, remarks = "TEST · Aucun relevé de terrain réel",
        createdAt = 1_791_806_400_000L, updatedAt = 1_791_806_400_000L, auteur = "TEST",
        source = "TEST · Fixture pédagogique isolée, sans base de données",
    )
    private val testPlot = Placette(
        id = "TEST-PDF-plot", parcelleId = testParcel.id, name = "TEST · Placette pédagogique",
        type = "ENTIRE_PARCEL", rayonM = null, surfaceM2 = 10_000.0, centerWkt = null,
        createdAt = 1_791_806_400_000L, updatedAt = 1_791_806_400_000L,
        auteur = "TEST", source = "TEST · Surface de référence déclarée",
    )

    private fun fixture(): SynthesisDocument {
        val observations = listOf(Tige(
            id = "TEST-PDF-tree", parcelleId = testParcel.id, placetteId = testPlot.id,
            essenceCode = "TEST-OAK", diamCm = 40.0, hauteurM = 20.0,
            gpsWkt = null, precisionM = null, altitudeM = null, timestamp = 1_791_806_400_000L,
            note = "TEST · Hauteur brute non qualifiée", produit = null, fCoef = 0.45, valueEur = 17.05,
            sessionId = "TEST-PDF-session", auteur = "TEST", source = "TEST · Valeurs pédagogiques saisies",
        ))
        val parameters = SynthesisParameters(tarifSelection = TarifSelection("FGH"), selectedPlotId = testPlot.id,
            sourcePolicy = SynthesisParameters.CURRENT_SOURCE_POLICY)
        val report = SynthesisReportCalculator.calculate(testParcel, listOf(testPlot), observations,
            mapOf("TEST-OAK" to "TEST · Essence pédagogique"), parameters,
            "TEST-PDF-session", "TEST-PDF-campaign", 1, 1_791_806_400_000L)
        return SynthesisDocument(report, closed = false, closedAt = null, sourceFingerprint = null,
            parameters = json.encodeToJsonElement(parameters).jsonObject, observations = observations)
    }
}
