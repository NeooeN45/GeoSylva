package com.forestry.counter.domain.usecase.pack

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Properties

/**
 * Télécharge un fichier en vérifiant son SHA-256 et reprend uniquement un
 * fragment lié à cette URL, ce checksum et un ETag fort. La metadata est
 * enregistrée avant la lecture du corps, y compris à la première tentative.
 *
 * Aucune dépendance Android : [PackManager] impose HTTPS avant cet appel ; les
 * tests JVM utilisent le serveur HTTP local MockWebServer. [checkCancellation]
 * permet au propriétaire de la coroutine de propager son annulation. Les
 * lectures réseau bloquantes restent bornées par [READ_TIMEOUT_MS].
 */
internal fun downloadWithChecksum(
    url: URL,
    destination: File,
    expectedSha256: String,
    checkCancellation: () -> Unit = {},
    expectedBytes: Long? = null,
    connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    onProgress: (Float) -> Unit = {}
) {
    expectedBytes?.let { require(it > 0); checkPackSize(it, url.path) }
    val expectedSha = expectedSha256.trim().lowercase()
    require(expectedSha.matches(Regex("[0-9a-f]{64}"))) { "Checksum SHA-256 invalide pour ${url.path}" }
    val metadataFile = File(destination.parentFile, "${destination.name}.etag")
    var metadata = readResumeMetadata(metadataFile)?.takeIf {
        it.url == url.toExternalForm() && it.sha256 == expectedSha
    }
    var resumeFrom = if (destination.isFile) destination.length() else 0L
    if (resumeFrom == 0L || metadata == null || resumeFrom > MAX_PACK_BYTES ||
        metadata.totalBytes?.let { resumeFrom > it } == true
    ) {
        clearPartial(destination, metadataFile)
        metadata = null
        resumeFrom = 0L
    }

    // Une incohérence de reprise autorise une seule tentative complète. Un
    // serveur invalide ne peut pas déclencher une boucle de téléchargements.
    var restarted = false
    while (true) {
        checkCancellation()
        val previousMetadata = metadata
        val connection = connectionFactory(url).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = false
            setRequestProperty("Accept-Encoding", "identity")
            if (resumeFrom > 0L) {
                setRequestProperty("Range", "bytes=$resumeFrom-")
                previousMetadata?.let { setRequestProperty("If-Range", it.etag) }
            }
        }
        try {
            checkCancellation()
            val code = connection.responseCode
            checkCancellation()
            val responseEtag = strongEtag(connection.getHeaderField("ETag"))
            val range = parseContentRange(connection.getHeaderField("Content-Range"))
            val resuming = code == HttpURLConnection.HTTP_PARTIAL && resumeFrom > 0L

            if (code == HTTP_RANGE_NOT_SATISFIABLE && resumeFrom > 0L) {
                val serverSize = parseUnsatisfiedRange(connection.getHeaderField("Content-Range"))
                if (serverSize == resumeFrom && (expectedBytes == null || serverSize == expectedBytes) && previousMetadata?.totalBytes?.let { it != serverSize } != true &&
                    fileSha256(destination, checkCancellation) == expectedSha
                ) {
                    onProgress(1f)
                    return
                }
            }

            val invalidResume = resumeFrom > 0L && (
                code == HTTP_RANGE_NOT_SATISFIABLE ||
                    (code == HttpURLConnection.HTTP_PARTIAL && (
                        range == null || range.start != resumeFrom || responseEtag != previousMetadata?.etag ||
                            previousMetadata?.totalBytes?.let { it != range.total } == true
                        ))
                )
            if (invalidResume) {
                clearPartial(destination, metadataFile)
                metadata = null
                resumeFrom = 0L
                check(!restarted) { "Réponse de reprise incohérente pour ${url.path}" }
                restarted = true
                continue
            }
            check(code == HttpURLConnection.HTTP_OK || resuming) { "Serveur packs HTTP $code" }
            check(connection.getHeaderField("Content-Encoding").let { it == null || it.equals("identity", true) }) {
                "Encodage HTTP incompatible avec la reprise pour ${url.path}"
            }

            if (!resuming) {
                clearPartial(destination, metadataFile)
                metadata = null
                resumeFrom = 0L
            }
            val declaredLength = declaredContentLength(connection)
            val total = if (resuming) range?.total else declaredLength
            check(expectedBytes == null || total == null || total == expectedBytes) { "Taille HTTP différente du manifeste signé" }
            val responseBytes = if (resuming) range?.let { it.end - it.start + 1L } else declaredLength
            if (resuming && declaredLength != null && declaredLength != responseBytes) {
                clearPartial(destination, metadataFile)
                error("Content-Length incompatible avec Content-Range pour ${url.path}")
            }
            if (total != null) {
                checkPackSize(total, url.path)
                checkDiskSpace(destination.parentFile?.usableSpace ?: Long.MAX_VALUE, total - resumeFrom, url.path)
            }

            metadata = responseEtag?.let { ResumeMetadata(url.toExternalForm(), expectedSha, it, total) }
            metadata?.let { writeResumeMetadata(metadataFile, it) } ?: metadataFile.delete()
            val digest = MessageDigest.getInstance("SHA-256")
            if (resuming) updateDigest(destination, digest, checkCancellation)

            var readTotal = resumeFrom
            connection.inputStream.use { input ->
                FileOutputStream(destination, resuming).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        checkCancellation()
                        val read = input.read(buffer)
                        checkCancellation()
                        if (read < 0) break
                        if (read == 0) continue
                        val nextTotal = readTotal + read
                        checkPackSize(nextTotal, url.path)
                        check(total == null || nextTotal <= total) { "Corps HTTP trop long pour ${url.path}" }
                        check(expectedBytes == null || nextTotal <= expectedBytes) { "Artefact supérieur à la taille signée" }
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        readTotal = nextTotal
                        if (total != null && total > 0L) {
                            onProgress((readTotal.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                    output.fd.sync()
                }
            }
            if ((responseBytes != null && readTotal - resumeFrom != responseBytes) ||
                (total != null && readTotal < total)
            ) {
                throw IOException("Téléchargement interrompu pour ${url.path} ($readTotal/$total octets)")
            }
            checkCancellation()
            check(expectedBytes == null || readTotal == expectedBytes) { "Taille du fichier différente du manifeste signé" }
            val actualSha256 = digest.digest().toHex()
            if (actualSha256 != expectedSha) {
                clearPartial(destination, metadataFile)
                metadata = null
                if (resuming && !restarted) {
                    resumeFrom = 0L
                    restarted = true
                    continue
                }
                error("Checksum SHA-256 invalide pour ${url.path}")
            }
            onProgress(1f)
            return
        } catch (error: IOException) {
            // Si une annulation a provoqué/suivi l'erreur réseau, propager
            // l'annulation plutôt que l'afficher comme une panne de serveur.
            if (metadata == null) clearPartial(destination, metadataFile)
            checkCancellation()
            throw error
        } catch (error: Throwable) {
            // Sans ETag fort/identité enregistrée, ce fragment ne pourra pas
            // être repris de façon sûre, même après une annulation.
            if (metadata == null) clearPartial(destination, metadataFile)
            throw error
        } finally {
            connection.disconnect()
        }
    }
}

private data class ResumeMetadata(val url: String, val sha256: String, val etag: String, val totalBytes: Long?)
private data class ContentRange(val start: Long, val end: Long, val total: Long)

private fun strongEtag(value: String?): String? = value?.trim()?.takeIf { etag ->
    etag.length >= 2 && etag.startsWith('"') && etag.endsWith('"') &&
        etag.substring(1, etag.length - 1).all { it.code == 33 || it.code in 35..126 || it.code in 128..255 }
}

private fun parseContentRange(value: String?): ContentRange? {
    val match = value?.trim()?.let { Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matchEntire(it) } ?: return null
    val start = match.groupValues[1].toLongOrNull() ?: return null
    val end = match.groupValues[2].toLongOrNull() ?: return null
    val total = match.groupValues[3].toLongOrNull() ?: return null
    return ContentRange(start, end, total).takeIf { start <= end && end < total && total <= MAX_PACK_BYTES }
}

private fun parseUnsatisfiedRange(value: String?): Long? =
    value?.trim()?.let { Regex("bytes \\*/([0-9]+)").matchEntire(it) }
        ?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it <= MAX_PACK_BYTES }

private fun declaredContentLength(connection: HttpURLConnection): Long? {
    val raw = connection.getHeaderField("Content-Length") ?: return null
    return raw.trim().toLongOrNull()?.takeIf { it >= 0L }
        ?: error("Content-Length invalide pour ${connection.url.path}")
}

private fun readResumeMetadata(file: File): ResumeMetadata? {
    if (!file.isFile) return null
    val properties = Properties()
    try {
        file.inputStream().use { properties.load(it) }
    } catch (_: IOException) {
        return null
    } catch (_: IllegalArgumentException) {
        return null
    }
    // L'ancien fichier .etag ne portait ni URL ni SHA : ne pas lui attribuer
    // une identité qu'il n'a jamais enregistrée.
    if (properties.getProperty("version") != "1") return null
    val url = properties.getProperty("url") ?: return null
    val sha = properties.getProperty("sha256") ?: return null
    val etag = strongEtag(properties.getProperty("etag")) ?: return null
    val size = properties.getProperty("total") ?: return null
    val total = if (size == "unknown") {
        null
    } else {
        size.toLongOrNull()?.takeIf { it in 0..MAX_PACK_BYTES } ?: return null
    }
    return ResumeMetadata(url, sha, etag, total)
}

private fun writeResumeMetadata(file: File, metadata: ResumeMetadata) {
    val properties = Properties().apply {
        setProperty("version", "1")
        setProperty("url", metadata.url)
        setProperty("sha256", metadata.sha256)
        setProperty("etag", metadata.etag)
        setProperty("total", metadata.totalBytes?.toString() ?: "unknown")
    }
    val temporary = File(file.parentFile, "${file.name}.tmp")
    try {
        FileOutputStream(temporary).use { output ->
            properties.store(output, null)
            output.fd.sync()
        }
        check(temporary.renameTo(file)) { "Impossible d'enregistrer la reprise pour ${file.name}" }
    } finally {
        temporary.delete()
    }
}

private fun clearPartial(destination: File, metadataFile: File) {
    if (destination.exists()) check(destination.delete()) { "Impossible d'effacer le fragment ${destination.name}" }
    if (metadataFile.exists()) check(metadataFile.delete()) { "Impossible d'effacer la reprise ${metadataFile.name}" }
}

private fun updateDigest(file: File, digest: MessageDigest, checkCancellation: () -> Unit) {
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            checkCancellation()
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
    }
}

private fun fileSha256(file: File, checkCancellation: () -> Unit): String =
    MessageDigest.getInstance("SHA-256").also { updateDigest(file, it, checkCancellation) }.digest().toHex()

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private const val DEFAULT_BUFFER_SIZE = 8 * 1024
private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 15_000
private const val HTTP_RANGE_NOT_SATISFIABLE = 416

/** Plafond technique conservé ; ce n'est pas une taille régionale mesurée. */
private const val MAX_PACK_BYTES = 512L * 1024 * 1024

internal fun checkPackSize(bytes: Long, path: String) {
    check(bytes in 0..MAX_PACK_BYTES) {
        "Pack $path trop volumineux : $bytes octets (max $MAX_PACK_BYTES)"
    }
}

internal fun checkDiskSpace(available: Long, required: Long, path: String) {
    check(required >= 0L && available >= required) {
        "Espace disque insuffisant pour $path ($required octets requis)"
    }
}

/** HTTPS reste imposé par [PackManager], hors serveur HTTP local des tests. */
internal fun checkHttpsScheme(url: URL) {
    require(url.protocol.equals("https", ignoreCase = true)) {
        "Schéma ${url.protocol} interdit — HTTPS obligatoire pour ${url.path}"
    }
}
