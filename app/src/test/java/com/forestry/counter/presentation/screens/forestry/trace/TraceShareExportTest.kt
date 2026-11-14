package com.forestry.counter.presentation.screens.forestry.trace

import com.forestry.counter.domain.repository.CartographicStoreError
import com.forestry.counter.domain.repository.CartographicStoreResult
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class TraceShareExportTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `disk full removes a partial file and never opens the chooser`() = runTest {
        val directory = temporary.newFolder()
        var opened = false
        try {
            shareTraceExport(directory, { CartographicStoreResult.Success("complete GeoJSON") }, { opened = true },
                StandardTestDispatcher(testScheduler), writePayload = { file, _ ->
                    file.writeText("partial")
                    throw IOException("disk full")
                })
            fail("An IO failure must reach the host error handler")
        } catch (_: IOException) { /* The host displays its localized export error. */ }
        assertFalse(opened)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun `cancellation propagates and removes a partial export`() = runTest {
        val directory = temporary.newFolder()
        val cancellation = CancellationException("screen closed")
        try {
            shareTraceExport(directory, { CartographicStoreResult.Success("GeoJSON") }, { fail("No chooser after cancellation") },
                StandardTestDispatcher(testScheduler), writePayload = { file, _ ->
                    file.writeText("partial")
                    throw cancellation
                })
            fail("Cancellation must propagate")
        } catch (caught: CancellationException) { assertEquals(cancellation.message, caught.message) }
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun `authority revoked during IO removes the file before any chooser`() = runTest {
        val directory = temporary.newFolder()
        var reads = 0
        var opened = false
        val shared = shareTraceExport(directory, exportPreview = {
            if (++reads == 1) CartographicStoreResult.Success("GeoJSON")
            else CartographicStoreResult.Failure(CartographicStoreError.ACCESS_CHANGED)
        }, openChooser = { opened = true }, ioDispatcher = StandardTestDispatcher(testScheduler))
        assertEquals(2, reads)
        assertFalse(shared)
        assertFalse(opened)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun `chooser failure removes the completed file while success retains the shared artifact`() = runTest {
        val directory = temporary.newFolder()
        try {
            shareTraceExport(directory, { CartographicStoreResult.Success("GeoJSON") }, { throw IllegalStateException("no chooser") },
                StandardTestDispatcher(testScheduler))
            fail("The chooser failure must reach the host error handler")
        } catch (_: IllegalStateException) { }
        assertTrue(directory.listFiles().orEmpty().isEmpty())
        var opened: File? = null
        assertTrue(shareTraceExport(directory, { CartographicStoreResult.Success("GeoJSON") }, { opened = it },
            StandardTestDispatcher(testScheduler)))
        assertEquals("GeoJSON", requireNotNull(opened).readText())
        assertEquals(listOf(opened), directory.listFiles().orEmpty().toList())
    }
}
