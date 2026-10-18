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
import com.forestry.counter.domain.calculation.SynthesisReportCalculator
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.model.Tige
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Cache-only test inputs; no observations or archives are written into the pilot database. */
@RunWith(AndroidJUnit4::class)
class IllustratedSynthesisPdfTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun long_species_tables_and_diameter_counts_paginate_to_readable_native_pages() {
        val input = fixture(count = 161, longNames = true)
        withPdf(input) { renderer ->
            assertTrue("Both species tables must paginate, without a row limit", renderer.pageCount > 12)
            for (index in 0 until renderer.pageCount) {
                renderer.openPage(index).use { page ->
                    assertEquals(595, page.width)
                    assertEquals(842, page.height)
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        var bodyInk = 0
                        for (y in 100 until 786 step 3) for (x in 36 until 559 step 3) {
                            val pixel = bitmap.getPixel(x, y)
                            if (Color.red(pixel) < 190 && Color.green(pixel) < 190 && Color.blue(pixel) < 190) bodyInk++
                        }
                        assertTrue("Page ${index + 1} must contain report content beyond its header", bodyInk > 8)
                    } finally { bitmap.recycle() }
                }
            }
        }
    }

    @Test fun polygon_hole_is_rendered_as_an_actual_unfilled_area() {
        val exterior = "2 45,2.02 45,2.02 45.02,2 45.02,2 45"
        val hole = "2.006 45.006,2.014 45.006,2.014 45.014,2.006 45.014,2.006 45.006"
        val withoutHole = fixture(shape = "POLYGON(($exterior))")
        val withHole = fixture(shape = "POLYGON(($exterior),($hole))")
        val parcelFill = Color.rgb(221, 235, 215)
        withPdf(withoutHole) { solid ->
            withPdf(withHole) { holed ->
                val (mapPageIndex, solidPage) = findMapPage(solid, parcelFill)
                val holedPage = renderPage(holed, mapPageIndex)
                try {
                    var minimumX = solidPage.width
                    var minimumY = solidPage.height
                    var maximumX = 0
                    var maximumY = 0
                    var fillCount = 0
                    for (y in 100 until 650) for (x in 36 until 559) {
                        if (solidPage.getPixel(x, y) == parcelFill) {
                            minimumX = minOf(minimumX, x); maximumX = maxOf(maximumX, x)
                            minimumY = minOf(minimumY, y); maximumY = maxOf(maximumY, y)
                            fillCount++
                        }
                    }
                    assertTrue("The geographic polygon must actually be drawn", fillCount > 20_000)
                    val centerX = (minimumX + maximumX) / 2
                    val centerY = (minimumY + maximumY) / 2
                    assertEquals(parcelFill, solidPage.getPixel(centerX, centerY))
                    assertEquals("A hole must remain white, not be filled as a second polygon",
                        Color.WHITE, holedPage.getPixel(centerX, centerY))
                    assertEquals(solid.pageCount, holed.pageCount)
                } finally { solidPage.recycle(); holedPage.recycle() }
            }
        }
    }

    @Test fun absent_geography_does_not_create_a_substitute_parcel_map() {
        val input = fixture(shape = null)
        assertEquals(null, buildIllustratedSynthesisData(input).map)
        val parcelFill = Color.rgb(221, 235, 215)
        withPdf(input) { renderer ->
            for (index in 0 until renderer.pageCount) {
                val bitmap = renderPage(renderer, index)
                try {
                    var mapPixels = 0
                    for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                        if (bitmap.getPixel(x, y) == parcelFill) mapPixels++
                    }
                    assertEquals("No invented map fill may appear without geometry", 0, mapPixels)
                } finally { bitmap.recycle() }
            }
        }
    }

    @Test fun captured_time_zone_survives_a_device_time_zone_change() {
        val input = fixture().copy(timeZoneId = "Europe/Paris")
        val originalZone = TimeZone.getDefault()
        val sandbox = File(context.cacheDir, "illustrated-timezone-test-${java.util.UUID.randomUUID()}")
        assertTrue(sandbox.mkdir())
        val isolated = object : ContextWrapper(context) { override fun getFilesDir(): File = sandbox }
        val closed = input.copy(document = input.document.copy(closed = true, closedAt = 2_000L,
            sourceFingerprint = "CACHE_ONLY_TIMEZONE_FINGERPRINT"))
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val firstArchive = IllustratedSynthesisExporter.archiveClosed(isolated, closed)
            val firstArchiveHash = sha256(firstArchive.readBytes())
            withPdf(input) { first ->
                val firstPage = renderPage(first, 0)
                try {
                    val firstPixels = IntArray(firstPage.width * firstPage.height)
                    firstPage.getPixels(firstPixels, 0, firstPage.width, 0, 0, firstPage.width, firstPage.height)
                    TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))
                    val secondArchive = IllustratedSynthesisExporter.archiveClosed(isolated, closed)
                    assertEquals("The captured time zone is part of stable archive inputs", firstArchive.absolutePath, secondArchive.absolutePath)
                    assertEquals(firstArchiveHash, sha256(secondArchive.readBytes()))
                    withPdf(input) { second ->
                        assertEquals(first.pageCount, second.pageCount)
                        val secondPage = renderPage(second, 0)
                        try {
                            val secondPixels = IntArray(secondPage.width * secondPage.height)
                            secondPage.getPixels(secondPixels, 0, secondPage.width, 0, 0, secondPage.width, secondPage.height)
                            assertTrue("Captured dates and their displayed time zone must survive device changes",
                                firstPixels.contentEquals(secondPixels))
                        } finally { secondPage.recycle() }
                    }
                } finally { firstPage.recycle() }
            }
        } finally { TimeZone.setDefault(originalZone); sandbox.deleteRecursively() }
    }

    @Test fun closed_illustrated_archive_coexists_with_the_original_and_reuses_its_own_bytes() {
        val sandbox = File(context.cacheDir, "illustrated-archives-test-${java.util.UUID.randomUUID()}")
        assertTrue(sandbox.mkdir())
        val isolated = object : ContextWrapper(context) { override fun getFilesDir(): File = sandbox }
        val provisional = fixture()
        val closed = provisional.copy(document = provisional.document.copy(closed = true,
            closedAt = 2_000L, sourceFingerprint = "CACHE_ONLY_TEST_FINGERPRINT"))
        try {
            val original = PdfSynthesisExporter.archiveClosed(isolated, closed.document)
            val originalBytes = original.readBytes()
            val originalHash = sha256(originalBytes)
            val illustrated = IllustratedSynthesisExporter.archiveClosed(isolated, closed)
            val illustratedHash = sha256(illustrated.readBytes())
            assertTrue(original.isFile && illustrated.isFile)
            assertNotEquals(original.absolutePath, illustrated.absolutePath)
            assertNotEquals(originalHash, illustratedHash)
            assertEquals(illustrated.absolutePath, IllustratedSynthesisExporter.archiveClosed(isolated, closed).absolutePath)
            assertEquals(illustratedHash, sha256(illustrated.readBytes()))
            assertEquals("The existing closed PDF must never be rewritten", originalHash, sha256(original.readBytes()))
            assertTrue(originalBytes.contentEquals(original.readBytes()))
            val englishConfiguration = Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) }
            val englishContext = object : ContextWrapper(context.createConfigurationContext(englishConfiguration)) {
                override fun getFilesDir(): File = sandbox
            }
            val english = IllustratedSynthesisExporter.archiveClosed(englishContext, closed)
            assertNotEquals("Locale-specific illustrated archives must remain distinct", illustrated.absolutePath, english.absolutePath)
            assertEquals(illustratedHash, sha256(illustrated.readBytes()))
            assertEquals(originalHash, sha256(original.readBytes()))
        } finally { sandbox.deleteRecursively() }
    }

    @Test fun revoked_identity_never_opens_the_illustrated_export_output() = runBlocking {
        val output = File(context.cacheDir, "illustrated-revoked-${java.util.UUID.randomUUID()}.pdf")
        assertFalse(output.exists())
        val guard = SynthesisReadGuard(current = { false }, validated = { false })
        var rejected = false
        try {
            IllustratedSynthesisExporter.exportGuarded(context, Uri.fromFile(output), fixture(), guard)
        } catch (error: IllegalStateException) {
            rejected = error.message == "SYNTHESIS_READ_ACCESS_CHANGED"
        } finally {
            assertFalse("Revocation must be checked before opening the destination", output.exists())
            output.delete()
        }
        assertTrue(rejected)
    }

    private fun fixture(count: Int = 1, longNames: Boolean = false, shape: String? = null): IllustratedSynthesisDocument {
        val parcel = Parcelle("ILLUSTRATED_TEST_PARCEL", null, name = "TEST — Illustrated report",
            surfaceHa = 1.0, shape = shape, slopePct = null, aspect = null, access = null, altitudeM = null,
            objectifType = null, objectifVal = null, tolerancePct = null, samplingMode = null, sampleAreaM2 = null,
            targetSpeciesCsv = null, srid = 4326, remarks = null)
        val plot = Placette("ILLUSTRATED_TEST_PLOT", parcel.id, "TEST plot", "ENTIRE_PARCEL", null, 10_000.0, null)
        val trees = (1..count).map { index -> Tige("test-$index", parcel.id, plot.id, "TEST_$index",
            index.toDouble() + 10.0, 20.0, null, null, null, timestamp = index.toLong(), note = null,
            produit = null, fCoef = null, valueEur = null, sessionId = "ILLUSTRATED_TEST_SESSION") }
        val parameters = SynthesisParameters(selectedPlotId = plot.id)
        val names = trees.associate { tree -> tree.essenceCode to if (longNames)
            "TEST species ${tree.id}: a deliberately long descriptive scientific species label retained fully in the paginated native table, with an additional identification note"
        else "TEST species" }
        val report = SynthesisReportCalculator.calculate(parcel, listOf(plot), trees, names, parameters,
            "ILLUSTRATED_TEST_SESSION", "ILLUSTRATED_TEST_CAMPAIGN", 1, 1_000L)
        return IllustratedSynthesisDocument(SynthesisDocument(report, false, null, null,
            Json.encodeToJsonElement(parameters).jsonObject, trees), parcel, listOf(plot))
    }

    private fun withPdf(input: IllustratedSynthesisDocument, block: (PdfRenderer) -> Unit) {
        val file = File.createTempFile("illustrated-synthesis-test-", ".pdf", context.cacheDir)
        try {
            file.outputStream().use { stream -> writeIllustratedSynthesisPdf(context, input) { it.writeTo(stream) } }
            assertTrue(file.length() > 1_000L)
            assertEquals("%PDF", file.inputStream().use { stream -> String(ByteArray(4).also { assertEquals(4, stream.read(it)) }) })
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use(block)
            }
        } finally { file.delete() }
    }

    private fun renderPage(renderer: PdfRenderer, index: Int): Bitmap = renderer.openPage(index).use { page ->
        Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
        }
    }

    private fun findMapPage(renderer: PdfRenderer, parcelFill: Int): Pair<Int, Bitmap> {
        for (index in 0 until renderer.pageCount) {
            val bitmap = renderPage(renderer, index)
            var fillCount = 0
            for (y in 100 until 650) for (x in 36 until 559) {
                if (bitmap.getPixel(x, y) == parcelFill) fillCount++
            }
            if (fillCount > 20_000) return index to bitmap
            bitmap.recycle()
        }
        error("The PDF must contain a rendered parcel polygon")
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
