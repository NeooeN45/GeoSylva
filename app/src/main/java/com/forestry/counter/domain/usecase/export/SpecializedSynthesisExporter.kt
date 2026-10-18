package com.forestry.counter.domain.usecase.export

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Separate immutable edition; neither observations nor historical PDF archives are rewritten. */
object SpecializedSynthesisExporter {
    const val TEMPLATE_VERSION = 2
    private val json = Json { encodeDefaults = true }

    suspend fun exportGuarded(
        context: Context,
        uri: Uri,
        input: SpecializedSynthesisDocument,
        guard: SynthesisReadGuard,
    ) {
        guardedSynthesisOutput(guard, produce = {
            if (input.illustrated.document.closed) {
                archiveClosed(context, input, guard::requireCurrent).readBytes()
            } else {
                ByteArrayOutputStream().use { bytes ->
                    writeSpecializedSynthesisPdf(context, input) { it.writeTo(bytes) }
                    bytes.toByteArray()
                }
            }
        }, output = { bytes ->
            guard.requireRevalidated()
            context.contentResolver.openOutputStream(uri)?.use { output ->
                var offset = 0
                while (offset < bytes.size) {
                    guard.requireCurrent()
                    val size = minOf(8_192, bytes.size - offset)
                    output.write(bytes, offset, size)
                    offset += size
                }
                guard.requireCurrent()
            } ?: error("SPECIALIZED_PDF_OUTPUT_UNAVAILABLE")
        })
    }

    @Synchronized
    fun archiveClosed(
        context: Context,
        input: SpecializedSynthesisDocument,
        requireAccess: () -> Unit = {},
    ): File {
        requireAccess()
        require(input.illustrated.document.closed)
        val locale = context.resources.configuration.locales[0].toLanguageTag()
        val inputs = json.encodeToString(input)
        val digest = sha256(("specialized-v$TEMPLATE_VERSION\n$locale\n$inputs").toByteArray(Charsets.UTF_8))
        val directory = File(context.filesDir, "martelage_specialized_reports/v$TEMPLATE_VERSION")
        check(directory.isDirectory || directory.mkdirs())
        val revision = File(directory, digest)
        val target = File(revision, "report.pdf")
        val signature = File(revision, "report.sha256")
        val recordedInputs = File(revision, "inputs.json")
        if (revision.exists()) {
            check(target.isFile && signature.isFile && recordedInputs.isFile) {
                "SPECIALIZED_PDF_ARCHIVE_INCOMPLETE"
            }
            check(recordedInputs.readText() == inputs &&
                signature.readText().trim() == sha256(target.readBytes())) {
                "SPECIALIZED_PDF_ARCHIVE_CORRUPT"
            }
            requireAccess()
            return target
        }
        val pending = File(directory, ".pending-${java.util.UUID.randomUUID()}")
        check(pending.mkdir())
        try {
            File(pending, "report.pdf").outputStream().use { output ->
                writeSpecializedSynthesisPdf(context, input) { it.writeTo(output) }
            }
            requireAccess()
            File(pending, "report.sha256").writeText(sha256(File(pending, "report.pdf").readBytes()) + "\n")
            File(pending, "inputs.json").writeText(inputs)
            requireAccess()
            check(pending.renameTo(revision)) { "SPECIALIZED_PDF_ARCHIVE_WRITE_FAILED" }
        } finally {
            pending.deleteRecursively()
        }
        requireAccess()
        return target
    }

    private fun sha256(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
