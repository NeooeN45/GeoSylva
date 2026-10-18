package com.forestry.counter.domain.usecase.export

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.domain.calculation.SynthesisParameters
import com.forestry.counter.domain.calculation.SynthesisReportCalculator
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.model.Tige
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated cache sandbox only: no product database, account or previous archive is inspected. */
@RunWith(AndroidJUnit4::class)
class SpecializedReportArchiveTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun types_cost_inputs_and_timezone_have_distinct_immutable_archives() = sandbox { isolated ->
        val original = fixture()
        val first = SpecializedSynthesisExporter.archiveClosed(isolated, original)
        val hash = sha(first)
        val revenue = SpecializedSynthesisExporter.archiveClosed(isolated, original.copy(type = SpecializedReportType.REVENUE))
        val costs = SpecializedSynthesisExporter.archiveClosed(isolated, original.copy(type = SpecializedReportType.COSTS,
            costLines = listOf(cost("65"))))
        val revisedCost = SpecializedSynthesisExporter.archiveClosed(isolated, original.copy(type = SpecializedReportType.COSTS,
            costLines = listOf(cost("66"))))
        val otherZone = SpecializedSynthesisExporter.archiveClosed(isolated,
            original.copy(illustrated = original.illustrated.copy(timeZoneId = "UTC")))
        assertEquals(5, setOf(first.parent, revenue.parent, costs.parent, revisedCost.parent, otherZone.parent).size)
        assertEquals(first, SpecializedSynthesisExporter.archiveClosed(isolated, original))
        assertEquals(hash, sha(first))
        assertTrue(File(costs.parentFile, "inputs.json").readText().contains("CACHE_ONLY_TEST_QUOTATION"))
    }

    @Test fun corrupt_pdf_or_input_metadata_is_rejected_without_overwriting_it() = sandbox { isolated ->
        val input = fixture()
        val archived = SpecializedSynthesisExporter.archiveClosed(isolated, input)
        val metadata = File(archived.parentFile, "inputs.json")
        val originalMetadata = metadata.readText()
        metadata.writeText("CORRUPTED_CACHE_ONLY_TEST_INPUT")
        assertFails { SpecializedSynthesisExporter.archiveClosed(isolated, input) }
        assertEquals("CORRUPTED_CACHE_ONLY_TEST_INPUT", metadata.readText())
        metadata.writeText(originalMetadata)
        archived.appendText("CORRUPTED_CACHE_ONLY_TEST_PDF")
        val corruptHash = sha(archived)
        assertFails { SpecializedSynthesisExporter.archiveClosed(isolated, input) }
        assertEquals(corruptHash, sha(archived))
    }

    @Test fun revoked_read_guard_does_not_write_to_the_selected_file() = runBlocking {
        sandbox { isolated ->
            val output = File(isolated.cacheDir, "specialized-revocation-${java.util.UUID.randomUUID()}.pdf")
            try {
                runBlocking {
                    val guard = SynthesisReadGuard(current = { true }, validated = { false })
                    assertFailsSuspend { SpecializedSynthesisExporter.exportGuarded(isolated, Uri.fromFile(output), fixture(), guard) }
                }
                assertFalse(output.exists())
                assertFalse(File(isolated.filesDir, "martelage_specialized_reports").exists())
            } finally { output.delete() }
        }
    }

    @Test fun all_new_types_preserve_original_and_illustrated_archive_bytes() = sandbox { isolated ->
        val input = fixture()
        val legacy = PdfSynthesisExporter.archiveClosed(isolated, input.illustrated.document)
        val illustrated = IllustratedSynthesisExporter.archiveClosed(isolated, input.illustrated)
        val before = mapOf(legacy to sha(legacy), illustrated to sha(illustrated))
        SpecializedReportType.values().forEach { type ->
            SpecializedSynthesisExporter.archiveClosed(isolated, input.copy(type = type,
                costLines = if (type == SpecializedReportType.COSTS) listOf(cost("65")) else emptyList()))
        }
        before.forEach { (file, hash) -> assertEquals(hash, sha(file)) }
    }

    @Test fun revocation_after_rendering_still_prevents_external_output() = sandbox { isolated ->
        val output = File(isolated.cacheDir, "specialized-late-revocation-${java.util.UUID.randomUUID()}.pdf")
        try {
            runBlocking {
                var validations = 0
                val guard = SynthesisReadGuard(current = { true }, validated = { ++validations == 1 })
                assertFailsSuspend {
                    SpecializedSynthesisExporter.exportGuarded(isolated, Uri.fromFile(output), fixture(), guard)
                }
                assertEquals(2, validations)
            }
            assertFalse("A generated archive cannot bypass the revalidation before the selected URI", output.exists())
            assertTrue(File(isolated.filesDir, "martelage_specialized_reports/v2").isDirectory)
        } finally { output.delete() }
    }

    private fun cost(price: String) = SpecializedCostLine("TEST_COST", "TEST operation", "18",
        SpecializedCostUnit.HOUR, price, "CACHE_ONLY_TEST_QUOTATION", "2026-10-07", SpecializedCostBasis.USER_QUOTATION)

    private fun fixture(): SpecializedSynthesisDocument {
        val parcel = Parcelle(id = "SPECIALIZED_ARCHIVE_TEST_PARCEL", forestId = null, name = "TEST parcel",
            surfaceHa = 1.0, shape = null, slopePct = null, aspect = null, access = null, altitudeM = null,
            objectifType = null, objectifVal = null, tolerancePct = null, samplingMode = null,
            sampleAreaM2 = null, targetSpeciesCsv = null, srid = 4326, remarks = null)
        val plot = Placette("SPECIALIZED_ARCHIVE_TEST_PLOT", parcel.id, "TEST plot", "ENTIRE_PARCEL", null, 10_000.0, null)
        val tree = Tige("SPECIALIZED_ARCHIVE_TEST_TREE", parcel.id, plot.id, "TEST_OAK", 40.0, 20.0,
            null, null, null, timestamp = 1_000, note = null, produit = null, fCoef = null, valueEur = null,
            sessionId = "SPECIALIZED_ARCHIVE_TEST_SESSION")
        val params = SynthesisParameters(selectedPlotId = plot.id)
        val report = SynthesisReportCalculator.calculate(parcel, listOf(plot), listOf(tree), mapOf("TEST_OAK" to "TEST oak"),
            params, tree.sessionId, "SPECIALIZED_ARCHIVE_TEST_CAMPAIGN", 1, 2_000)
        val doc = SynthesisDocument(report, true, 3_000, "CACHE_ONLY_ARCHIVE_TEST_FINGERPRINT",
            Json.encodeToJsonElement(params).jsonObject, listOf(tree))
        return SpecializedSynthesisDocument(SpecializedReportType.ENVIRONMENTAL,
            IllustratedSynthesisDocument(doc, parcel, listOf(plot), timeZoneId = "Europe/Paris"))
    }

    private fun sandbox(block: (Context) -> Unit) {
        val dir = File(context.cacheDir, "specialized-archive-test-${java.util.UUID.randomUUID()}")
        assertTrue(dir.mkdir())
        val isolated = object : ContextWrapper(context) { override fun getFilesDir(): File = dir }
        try { block(isolated) } finally { dir.deleteRecursively() }
    }
    private fun sha(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private fun assertFails(block: () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: IllegalStateException) { }
    }
    private suspend fun assertFailsSuspend(block: suspend () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: IllegalStateException) { }
    }
}
