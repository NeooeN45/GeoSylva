package com.forestry.counter.domain.usecase.export

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Independent format/version namespace: neither the original PDF nor its serialized inputs change. */
object IllustratedSynthesisExporter {
    private val json = Json { encodeDefaults = true }

    suspend fun exportGuarded(context: Context, uri: Uri, input: IllustratedSynthesisDocument, guard: SynthesisReadGuard) {
        guardedSynthesisOutput(guard, produce = {
            if (input.document.closed) archiveClosed(context, input, guard::requireCurrent).readBytes()
            else ByteArrayOutputStream().use { bytes ->
                writeIllustratedSynthesisPdf(context, input) { it.writeTo(bytes) }
                bytes.toByteArray()
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
            } ?: error("ILLUSTRATED_PDF_OUTPUT_UNAVAILABLE")
        })
    }

    @Synchronized
    fun archiveClosed(context: Context, input: IllustratedSynthesisDocument, requireAccess: () -> Unit = {}): File {
        requireAccess()
        require(input.document.closed)
        val locale = context.resources.configuration.locales[0].toLanguageTag()
        val inputs = json.encodeToString(input)
        val digest = sha256(("illustrated-v1\n$locale\n$inputs").toByteArray(Charsets.UTF_8))
        val directory = File(context.filesDir, "martelage_illustrated_reports/v1")
        check(directory.isDirectory || directory.mkdirs())
        val revision = File(directory, digest)
        val target = File(revision, "report.pdf")
        val signature = File(revision, "report.sha256")
        if (target.isFile && signature.isFile) {
            check(signature.readText().trim() == sha256(target.readBytes())) { "ILLUSTRATED_PDF_ARCHIVE_CORRUPT" }
            requireAccess()
            return target
        }
        check(!revision.exists()) { "ILLUSTRATED_PDF_ARCHIVE_INCOMPLETE" }
        val pending = File(directory, ".pending-${java.util.UUID.randomUUID()}")
        check(pending.mkdir())
        try {
            File(pending, "report.pdf").outputStream().use { output ->
                writeIllustratedSynthesisPdf(context, input) { it.writeTo(output) }
            }
            requireAccess()
            File(pending, "report.sha256").writeText(sha256(File(pending, "report.pdf").readBytes()) + "\n")
            File(pending, "inputs.json").writeText(inputs)
            requireAccess()
            check(pending.renameTo(revision)) { "ILLUSTRATED_PDF_ARCHIVE_WRITE_FAILED" }
        } finally { pending.deleteRecursively() }
        return target
    }

    private fun sha256(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
