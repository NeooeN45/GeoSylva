package com.forestry.counter.domain.export

import com.forestry.counter.domain.field.*
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream

/**
 * No filesystem, networking or account lookup: capture and sharing authorization belong to
 * the repository/UI. Call on an IO dispatcher. Streams remain owned by the caller.
 * Hashes detect alteration; they do not establish contractual authenticity or server acceptance.
 */
object GeoSylvaFieldExporter {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false; isLenient = false; coerceInputValues = false }
    private val names = setOf("snapshot.json", "ledger.csv", "balances.csv", "receipts.csv", "observations.csv", "parcels.csv", "README.txt")
    private const val manifestName = "manifest.json"
    private const val MAX_SNAPSHOT_BUFFER_BYTES = 8 * 1024 * 1024

    fun write(snapshot: WorksiteExportSnapshot, output: OutputStream, limits: FieldExportLimits = FieldExportLimits()): ExportManifest = guarded {
        preflight(snapshot, limits)
        val ordered = ordered(snapshot)
        FieldExportValidation.validate(ordered)
        val files = files(ordered)
        val manifest = manifest(ordered, files)
        val manifestBytes = json.encodeToString(manifest).toByteArray(Charsets.UTF_8)
        checkLimits(files.values + manifestBytes, limits)
        ZipOutputStream(object : FilterOutputStream(output) { override fun close() { flush() } }).use { zip ->
            (mapOf(manifestName to manifestBytes) + files).forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name).apply { time = 0L })
                zip.write(bytes)
                zip.closeEntry()
            }
            zip.finish()
        }
        manifest
    }

    /** Reads independently from the produced files. Invalid input never becomes imported data. */
    fun verify(input: InputStream, limits: FieldExportLimits = FieldExportLimits()): ExportVerificationResult = guarded {
        val files = readArchive(input, limits)
        val manifestText = utf8(files.getValue(manifestName))
        val manifest = json.decodeFromString<ExportManifest>(manifestText)
        if (manifest.format != "geosylva-worksite-export" || manifest.formatVersion != 1) fail(FieldExportError.UNSUPPORTED_FORMAT)
        if (manifestText != json.encodeToString(manifest)) fail(FieldExportError.INTEGRITY)
        if (manifest.files.map { it.name }.toSet() != names || manifest.files.size != names.size) fail(FieldExportError.INTEGRITY)
        manifest.files.forEach { file ->
            val bytes = files.getValue(file.name)
            if (file.bytes != bytes.size || file.sha256 != sha256(bytes)) fail(FieldExportError.INTEGRITY)
        }
        val snapshotText = utf8(files.getValue("snapshot.json"))
        val snapshot = json.decodeFromString<WorksiteExportSnapshot>(snapshotText)
        if (snapshot.formatVersion != 1) fail(FieldExportError.UNSUPPORTED_FORMAT)
        if (snapshotText != json.encodeToString(ordered(snapshot))) fail(FieldExportError.INTEGRITY)
        FieldExportValidation.validate(snapshot)
        // Derive CSV again from the decoded JSON, so changing CSV plus its digest is detected too.
        val rebuilt = files(snapshot)
        if (manifest != manifest(snapshot, rebuilt)) fail(FieldExportError.INTEGRITY)
        rebuilt.forEach { (name, bytes) -> if (!bytes.contentEquals(files.getValue(name))) fail(FieldExportError.INTEGRITY) }
        ExportVerificationResult(manifest, snapshot)
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    /**
     * Reject oversized histories before sorting or materializing JSON/CSV strings. Streaming to a
     * counter keeps no serialized copy. Reserve headroom for CSV projections, UTF-16 intermediates
     * and the archive on a phone; actual per-file and total limits are still enforced afterwards.
     * This caps export construction, not the repository's preceding database snapshot capture.
     */
    @OptIn(ExperimentalSerializationApi::class)
    private fun preflight(snapshot: WorksiteExportSnapshot, limits: FieldExportLimits) {
        val budget = minOf(MAX_SNAPSHOT_BUFFER_BYTES, limits.maxFileBytes, (limits.maxArchiveBytes / 4).coerceAtLeast(1))
        val counter = object : OutputStream() {
            var count = 0L
            fun consume(bytes: Int) {
                count += bytes
                if (count > budget) fail(FieldExportError.LIMIT_EXCEEDED)
            }
            override fun write(value: Int) = consume(1)
            override fun write(bytes: ByteArray, offset: Int, length: Int) = consume(length)
        }
        json.encodeToStream(snapshot, counter)
    }

    private fun ordered(snapshot: WorksiteExportSnapshot) = snapshot.copy(
        links = snapshot.links.sortedWith(compareBy({ it.target.kind.name }, { it.target.id })),
        depots = snapshot.depots.sortedBy { it.id },
        objects = snapshot.objects.sortedBy { it.id }.map { item -> item.copy(
            observations = item.observations.sortedBy { it.id }, history = item.history.sortedBy { it.id }) },
        receipts = snapshot.receipts.sortedWith(compareBy({ it.id }, { it.revision })),
        entries = snapshot.entries.sortedBy { it.id },
        photos = snapshot.photos.sortedBy { it.photo.id },
        balances = snapshot.balances.sortedWith(compareBy({ it.objectId }, { it.unit.name }, { it.convention }, { it.at })),
    )

    private fun manifest(snapshot: WorksiteExportSnapshot, files: Map<String, ByteArray>) = ExportManifest(
        exportId = snapshot.exportId, capturedAt = snapshot.capturedAt, capturedAtUtc = Instant.ofEpochMilli(snapshot.capturedAt).toString(),
        scope = snapshot.scope, worksiteId = snapshot.site.id, worksiteRevision = snapshot.site.revision,
        revisions = buildList {
            add(ExportRevision("WORKSITE", snapshot.site.id, snapshot.site.revision))
            snapshot.depots.forEach { add(ExportRevision("WOOD_DEPOT", it.id, it.revision)) }
            snapshot.objects.forEach { item ->
                add(ExportRevision("WOOD_OBJECT", item.id, item.revision))
                item.observations.forEach { add(ExportRevision("WOOD_OBSERVATION", it.id, it.revision)) }
            }
            snapshot.receipts.forEach { add(ExportRevision("RECEIPT", it.id, it.revision)) }
        }.sortedWith(compareBy({ it.type }, { it.id }, { it.revision })),
        files = files.map { (name, bytes) -> ExportFileDigest(name, bytes.size, sha256(bytes)) }.sortedBy { it.name },
    )

    private fun files(snapshot: WorksiteExportSnapshot): Map<String, ByteArray> {
        val correctedIds = snapshot.entries.mapNotNull { it.form.correctionOf }.toSet()
        return linkedMapOf(
        "snapshot.json" to json.encodeToString(snapshot).toByteArray(Charsets.UTF_8),
        "ledger.csv" to csv(
            listOf("entry_id", "target_kind", "target_id", "kind", "active", "title", "observed_local", "zone", "observed_utc", "recorded_utc", "author", "quantity_known", "quantity", "unit", "convention", "remainder", "correction_of", "correction_reason", "document_reference", "passage_reference", "counterparty", "destination", "notes", "photo_ids"),
            snapshot.entries.map { entry ->
                val form = entry.form
                listOf(entry.id, entry.target.kind.name, entry.target.id, form.kind.name,
                    (entry.id !in correctedIds).toString(), form.title,
                    form.observed, form.zone, form.observedInstant()?.let { Instant.ofEpochMilli(it).toString() },
                    Instant.ofEpochMilli(entry.recordedAt).toString(), entry.author,
                    form.quantity.isNotBlank().toString(), form.quantity, form.unit?.name, form.convention,
                    form.remainder?.name, form.correctionOf, form.correctionReason, form.documentReference,
                    form.passageReference, form.counterparty, form.destination, form.notes, form.photoIds.joinToString("|"))
            }),
        "balances.csv" to csv(
            listOf("object_id", "at_utc", "unit", "convention", "expected_known", "expected", "observed_known", "observed", "difference_known", "difference", "reason"),
            snapshot.balances.map { listOf(it.objectId, Instant.ofEpochMilli(it.at).toString(), it.unit.name, it.convention,
                (it.expected != null).toString(), it.expected, (it.observed != null).toString(), it.observed,
                (it.difference != null).toString(), it.difference, it.reason) }),
        "receipts.csv" to csv(
            listOf("receipt_id", "revision", "status", "site_id", "title", "line_id", "object_id", "depot_id", "observation_id", "observation_revision", "physical_quantity", "unit", "convention", "convention_version", "difference_reason", "observed_local", "zone", "recorded_utc", "author", "stock_entry_id", "cancellation_entry_id", "cancellation_reason", "photo_ids"),
            snapshot.receipts.flatMap { receipt -> receipt.form.lines.map { line ->
                val credit = receipt.credits.singleOrNull { it.lineId == line.id }
                listOf(receipt.id, receipt.revision.toString(), receipt.status.name, receipt.siteId, receipt.form.title,
                    line.id, line.objectId, line.depotId, line.observationId, line.observationRevision.toString(), line.quantity,
                    line.unit.name, line.convention, line.conventionVersion, line.differenceReason, receipt.form.observed,
                    receipt.form.zone, Instant.ofEpochMilli(receipt.recordedAt).toString(), receipt.recordedBy,
                    credit?.entryId, credit?.cancellationEntryId, receipt.cancellationReason, line.photoIds.joinToString("|"))
            } }),
        "observations.csv" to csv(
            listOf("object_id", "depot_id", "object_kind", "object_revision", "observation_id", "root_id", "revision", "effective", "correction_of", "correction_reason", "recorded_utc", "author", "recorded_zone", "recorded_offset_seconds", "payload_sha256", "payload_json"),
            snapshot.objects.flatMap { item -> item.observations.map { observation ->
                listOf(item.id, item.depotId, item.kind, item.revision.toString(), observation.id, observation.rootId,
                    observation.revision.toString(), (item.effectiveObservationId == observation.id).toString(),
                    observation.correctionOf, observation.correctionReason, Instant.ofEpochMilli(observation.recordedAt).toString(),
                    observation.recordedBy, observation.recordedZoneId, observation.recordedOffsetSeconds.toString(), observation.sha256, observation.payloadJson)
            } }),
        "parcels.csv" to csv(listOf("site_id", "source", "reference", "label", "local_parcel_id", "source_version"),
            snapshot.site.form.parcels.map { listOf(snapshot.site.id, it.source, it.reference, it.label, it.localParcelId, it.sourceVersion) }),
        "README.txt" to readme.toByteArray(Charsets.UTF_8),
        )
    }

    private fun csv(headers: List<String>, rows: List<List<String?>>): ByteArray = buildString {
        append('\uFEFF')
        (listOf(headers) + rows).forEach { row ->
            append(row.joinToString(";") { cell -> "\"${spreadsheetSafe(cell.orEmpty()).replace("\"", "\"\"")}\"" })
            append("\r\n")
        }
    }.toByteArray(Charsets.UTF_8)

    /** Quoting alone does not prevent formulas. JSON keeps every original character. */
    private fun spreadsheetSafe(value: String): String {
        val leading = value.dropWhile { it.isWhitespace() || it in "\uFEFF\u200B\u200C\u200D" }
        val dangerous = leading.firstOrNull() in listOf('=', '+', '-', '@', '＝', '＋', '－', '＠') || value.firstOrNull()?.isISOControl() == true
        return if (dangerous) "'$value" else value
    }

    private fun readArchive(input: InputStream, limits: FieldExportLimits): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        var total = 0L
        // Also bound compressed bytes: an adversarial DEFLATE stream can consume input without
        // producing many expanded bytes. Extra allowance covers ordinary ZIP headers.
        val boundedInput = object : FilterInputStream(input) {
            var consumed = 0L
            fun count(size: Int) { if (size > 0) { consumed += size; if (consumed > limits.maxArchiveBytes.toLong() + 1024 * 1024) fail(FieldExportError.LIMIT_EXCEEDED) } }
            override fun read(): Int = `in`.read().also { if (it >= 0) count(1) }
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = `in`.read(bytes, offset, length).also(::count)
            override fun close() = Unit
        }
        ZipInputStream(boundedInput).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || entry.name !in names + manifestName || result.containsKey(entry.name)) fail(FieldExportError.INVALID_ARCHIVE)
                val bytes = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = zip.read(buffer)
                    if (count == -1) break
                    total += count
                    if (bytes.size().toLong() + count > limits.maxFileBytes || total > limits.maxArchiveBytes) fail(FieldExportError.LIMIT_EXCEEDED)
                    bytes.write(buffer, 0, count)
                }
                result[entry.name] = bytes.toByteArray()
                zip.closeEntry()
            }
        }
        if (result.keys != names + manifestName) fail(FieldExportError.INVALID_ARCHIVE)
        return result
    }

    private fun checkLimits(files: Collection<ByteArray>, limits: FieldExportLimits) {
        if (files.any { it.size > limits.maxFileBytes } || files.sumOf { it.size.toLong() } > limits.maxArchiveBytes) fail(FieldExportError.LIMIT_EXCEEDED)
    }
    private fun utf8(bytes: ByteArray) = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()

    private inline fun <T> guarded(block: () -> T): T = try { block() }
    catch (e: FieldExportException) { throw e }
    catch (_: IOException) { fail(FieldExportError.IO) }
    catch (_: IllegalArgumentException) { fail(FieldExportError.INVALID_ARCHIVE) }

    private fun fail(code: FieldExportError): Nothing = throw FieldExportException(code)

    private val readme = """
        EXPORT LOCAL GEOSYLVA — FORMAT 1

        Ce dossier est un instantané du chantier autorisé, pas une synchronisation GSIE/FORLOG,
        une quantité payable, une signature ni une réception contractuelle d'entreprise.
        manifest.json décrit les fichiers, leurs empreintes SHA-256 et les versions incluses.
        Une empreinte vérifie l'intégrité ; elle ne prouve pas l'identité d'un émetteur.
        snapshot.json conserve les données et textes d'origine, les mesures brutes, corrections,
        états de réception et références photographiques. Les images ne sont pas jointes.
        La lecture des mesures ne crée pas de stock. Seuls les événements physiques explicites
        participent au solde local. Les constats REMAINING sont des observations, pas des sorties.
        Une ancienne déclaration INITIAL_STOCK ne devient pas une réception certifiée.

        CSV : UTF-8 avec BOM, séparateur point-virgule, guillemets doublés, fin de ligne CRLF.
        Les chaînes pouvant être interprétées comme formules commencent par une apostrophe
        protectrice dans les CSV ; le JSON reste la source textuelle exacte.
        Les quantités sont des textes décimaux exacts. Importer les colonnes comme TEXTE pour
        éviter les arrondis automatiques du tableur. Les colonnes *_known distinguent inconnu de 0.
        Ne pas additionner des objets de conventions ou d'unités différentes, ni des révisions
        de réception, ni un constat avec les mouvements. Aucune conversion n'est implicite.
        Les dates de fait gardent leur fuseau ; les dates UTC sont au format ISO-8601.
        Les soldes sont calculés à l'instant indiqué et dans cet espace local uniquement.
        Le contrôleur de l'application relit les fichiers et reconstruit les soldes avant partage.
        Il ne corrige pas silencieusement les divergences et n'importe pas l'archive.
    """.trimIndent() + "\n"
}
