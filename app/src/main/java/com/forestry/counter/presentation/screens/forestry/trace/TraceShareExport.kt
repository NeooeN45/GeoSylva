package com.forestry.counter.presentation.screens.forestry.trace

import com.forestry.counter.domain.repository.CartographicStoreResult
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** The caller catches export/write/chooser failures; every unsuccessful attempt removes its artifact. */
internal suspend fun shareTraceExport(
    directory: File,
    exportPreview: suspend () -> CartographicStoreResult<String>,
    openChooser: (File) -> Unit,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    writePayload: (File, String) -> Unit = { file, payload -> file.writeText(payload, Charsets.UTF_8) },
): Boolean {
    var artifact: File? = null
    var shared = false
    try {
        val payload = exportPreview()
        if (payload !is CartographicStoreResult.Success) return false
        val file = withContext(ioDispatcher) {
            if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) throw IOException("Trace share directory unavailable")
            File(directory, "trace-${UUID.randomUUID()}.geojson").also {
                // Retain the path before writing, including if the write only produces a partial file.
                artifact = it
                writePayload(it, payload.value)
            }
        }
        // The same immutable revision and captured authority must still be readable after disk IO.
        if (exportPreview() !is CartographicStoreResult.Success) return false
        openChooser(file)
        shared = true
        return true
    } finally {
        if (!shared) withContext(NonCancellable + ioDispatcher) { runCatching { artifact?.delete() } }
    }
}
