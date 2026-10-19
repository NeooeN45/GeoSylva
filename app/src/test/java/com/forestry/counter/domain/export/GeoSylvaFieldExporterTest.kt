package com.forestry.counter.domain.export

import com.forestry.counter.domain.field.*
import com.forestry.counter.domain.wood.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class GeoSylvaFieldExporterTest {
    private val siteId = uuid(1)
    private val depotId = uuid(2)
    private val objectId = uuid(3)
    private val observationId = uuid(4)
    private val target = FieldTarget(FieldTargetKind.WOOD_OBJECT, objectId)
    private val capturedAt = Instant.parse("2026-10-19T12:00:00Z").toEpochMilli()

    @Test fun `archive replays exact physical stock despite another measurement and two removals`() {
        val base = fixture()
        val first = base.objects.single().observations.single()
        val nextPayload = WoodContractCodec.freezePayload(WoodObservationPayload(RawWoodMeasurement.Pile(
            length = RawWoodDimension("98", "m"), width = RawWoodDimension("1", "m"), meanHeight = RawWoodDimension("1", "m"))))
        val updated = base.copy(objects = listOf(base.objects.single().copy(revision = 2, observations = listOf(first,
            first.copy(id = uuid(6), rootId = uuid(6), payloadJson = nextPayload.text, sha256 = nextPayload.sha256)))))
        val entries = updated.entries + listOf(entry(11, FieldEntryKind.REMOVAL, "25", "19/10/2026 10:00"),
            entry(12, FieldEntryKind.REMOVAL, "75", "19/10/2026 11:00"))
        val verified = roundtrip(withBalances(updated.copy(entries = entries)))
        assertEquals("0", verified.snapshot.balances.single().expected)
        assertEquals(2, verified.snapshot.objects.single().observations.size)
        assertEquals(3, verified.snapshot.entries.size)
        assertFalse(verified.manifest.photosIncluded)
        assertEquals("2026-10-19T12:00:00Z", verified.manifest.capturedAtUtc)
    }

    @Test fun `correction replaces a movement without destroying its history`() {
        val base = fixture()
        val removal = entry(11, FieldEntryKind.REMOVAL, "25", "19/10/2026 10:00")
        val correction = removal.copy(id = uuid(12), form = removal.form.copy(quantity = "20", correctionOf = removal.id, correctionReason = "Erreur de transcription"))
        val verified = roundtrip(withBalances(base.copy(entries = base.entries + removal + correction)))
        assertEquals("80", verified.snapshot.balances.single().expected)
        assertEquals(3, verified.snapshot.entries.size)
    }

    @Test fun `remaining observations and mixed units never create physical credits`() {
        val base = fixture()
        val remaining = entry(11, FieldEntryKind.REMAINING, "98", "19/10/2026 12:00").let {
            it.copy(form = it.form.copy(remainder = FieldRemainder.PARTIAL))
        }
        val weight = entry(12, FieldEntryKind.REMAINING, "80", "19/10/2026 12:00").let {
            it.copy(form = it.form.copy(unit = FieldQuantityUnit.TONNE, convention = "Pesée déclarée", remainder = FieldRemainder.PARTIAL))
        }
        val result = roundtrip(withBalances(base.copy(entries = base.entries + remaining + weight)))
        val volume = result.snapshot.balances.single { it.unit == FieldQuantityUnit.SOLID_M3 }
        assertEquals("100", volume.expected)
        assertEquals("98", volume.observed)
        assertEquals("-2", volume.difference)
        val unknown = result.snapshot.balances.single { it.unit == FieldQuantityUnit.TONNE }
        assertNull(unknown.expected)
        assertEquals("BASE_REQUIRED", unknown.reason)
        val csv = unzip(archive(withBalances(base.copy(entries = base.entries + weight)))).getValue("balances.csv").toString(Charsets.UTF_8)
        assertTrue(csv.contains("\"false\";\"\""))
    }

    @Test fun `CSV neutralizes formulas while JSON preserves original accents quotes and newlines`() {
        val text = " \t=HYPERLINK(\"https://invalid.example\";\"Écorce\")\nDeuxième ligne"
        val base = fixture()
        val snapshot = base.copy(entries = listOf(base.entries.single().copy(form = base.entries.single().form.copy(notes = text))))
        val bytes = archive(snapshot)
        val verified = GeoSylvaFieldExporter.verify(ByteArrayInputStream(bytes))
        assertEquals(text, verified.snapshot.entries.single().form.notes)
        val csv = unzip(bytes).getValue("ledger.csv").toString(Charsets.UTF_8)
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("\"' \t=HYPERLINK(\"\"https://invalid.example\"\""))
        assertTrue(csv.contains("Écorce"))
    }

    @Test fun `receipt source and explicit entry survive validation and cancellation versions`() {
        val base = fixture()
        val original = base.entries.single()
        val line = ReceiptLine(uuid(30), objectId, depotId, observationId, 1, "100", FieldQuantityUnit.SOLID_M3, "Mesure déclarée")
        val form = ReceiptForm(title = "Réception TEST", observed = original.form.observed, zone = "UTC", lines = listOf(line))
        val obs = base.objects.single().observations.single()
        val source = FrozenReceiptSource(line.id, 1, "Pile TEST", obs.payloadJson, obs.sha256, obs.recordedAt, obs.recordedBy)
        val ready = SavedReceipt(uuid(31), siteId, 1, ReceiptStatus.READY, form, capturedAt, "anonymous", listOf(source))
        val validated = ready.copy(revision = 2, status = ReceiptStatus.VALIDATED, credits = listOf(ReceiptCredit(line.id, objectId, original.id)))
        val cancellation = original.copy(id = uuid(32), form = original.form.copy(quantity = "0", correctionOf = original.id, correctionReason = "Doublon constaté avant enlèvement"))
        val cancelled = validated.copy(revision = 3, status = ReceiptStatus.CANCELLED, cancellationReason = cancellation.form.correctionReason,
            credits = listOf(validated.credits.single().copy(cancellationEntryId = cancellation.id)))
        val result = roundtrip(withBalances(base.copy(entries = base.entries + cancellation, receipts = listOf(ready, validated, cancelled))))
        assertEquals("0", result.snapshot.balances.single().expected)
        assertEquals(3, result.snapshot.receipts.size)
        assertEquals(3, result.manifest.revisions.count { it.type == "RECEIPT" })
    }

    @Test fun `snapshot balance inconsistency is rejected rather than silently fixed`() {
        val base = fixture()
        expect(FieldExportError.BALANCE_MISMATCH) { archive(base.copy(balances = listOf(base.balances.single().copy(expected = "999")))) }
        expect(FieldExportError.BALANCE_MISMATCH) { archive(base.copy(balances = emptyList())) }
    }

    @Test fun `large precise decimals survive export without floating point rounding`() {
        val base = fixture()
        val exact = "12345678901234567890.000000000000000001"
        val updated = base.entries.single().copy(form = base.entries.single().form.copy(quantity = exact))
        val result = roundtrip(withBalances(base.copy(entries = listOf(updated))))
        assertEquals(exact, result.snapshot.entries.single().form.quantity)
        assertEquals(exact, result.snapshot.balances.single().expected)
    }

    @Test fun `shared depot journal cannot be smuggled into a worksite export`() {
        val base = fixture()
        val depotNote = FieldEntry(uuid(33), FieldTarget(FieldTargetKind.WOOD_DEPOT, depotId),
            FieldEntryForm(title = "Note d'un autre chantier"), capturedAt, "anonymous")
        expect(FieldExportError.INVALID_SNAPSHOT) { archive(base.copy(entries = base.entries + depotNote)) }
    }

    @Test fun `missing correction ancestor is rejected`() {
        val base = fixture()
        val invalid = base.copy(entries = listOf(base.entries.single().copy(form = base.entries.single().form.copy(correctionOf = uuid(99), correctionReason = "Motif"))))
        expect(FieldExportError.INVALID_SNAPSHOT) { archive(invalid) }
    }

    @Test fun `altered file fails integrity verification`() {
        val files = unzip(archive(fixture())).toMutableMap()
        files["ledger.csv"] = files.getValue("ledger.csv") + "changed".toByteArray()
        expect(FieldExportError.INTEGRITY) { GeoSylvaFieldExporter.verify(ByteArrayInputStream(zip(files))) }
    }

    @Test fun `CSV altered together with manifest digest still fails against JSON projection`() {
        val files = unzip(archive(fixture())).toMutableMap()
        files["balances.csv"] = files.getValue("balances.csv").toString(Charsets.UTF_8).replace("100", "999").toByteArray()
        val manifest = FieldCodec.json.decodeFromString<ExportManifest>(files.getValue("manifest.json").toString(Charsets.UTF_8))
        files["manifest.json"] = FieldCodec.json.encodeToString(manifest.copy(files = manifest.files.map {
            if (it.name == "balances.csv") it.copy(bytes = files.getValue(it.name).size, sha256 = GeoSylvaFieldExporter.sha256(files.getValue(it.name))) else it
        })).toByteArray()
        expect(FieldExportError.INTEGRITY) { GeoSylvaFieldExporter.verify(ByteArrayInputStream(zip(files))) }
    }

    @Test fun `unsafe path and duplicate ZIP entry are rejected without extracting files`() {
        expect(FieldExportError.INVALID_ARCHIVE) { GeoSylvaFieldExporter.verify(ByteArrayInputStream(zip(mapOf("../snapshot.json" to byteArrayOf(1))))) }
        val bytes = archive(fixture())
        val from = "snapshot.json".toByteArray()
        val to = "manifest.json".toByteArray()
        for (i in 0..bytes.size - from.size) if (from.indices.all { bytes[i + it] == from[it] }) to.copyInto(bytes, i)
        expect(FieldExportError.INVALID_ARCHIVE) { GeoSylvaFieldExporter.verify(ByteArrayInputStream(bytes)) }
    }

    @Test fun `actual expanded byte limit stops highly compressed input`() {
        val bytes = zip(mapOf("snapshot.json" to ByteArray(4096) { 32 }))
        expect(FieldExportError.LIMIT_EXCEEDED) {
            GeoSylvaFieldExporter.verify(ByteArrayInputStream(bytes), FieldExportLimits(1024, 2048))
        }
    }

    @Test fun `long valid history is rejected before constructing files or touching destination`() {
        val base = fixture()
        val notes = (0 until 40).map { index -> FieldEntry(uuid(200 + index), target,
            FieldEntryForm(title = "Observation $index", notes = "É".repeat(4000)), capturedAt, "anonymous") }
        val snapshot = base.copy(entries = base.entries + notes)
        val output = ByteArrayOutputStream()
        expect(FieldExportError.LIMIT_EXCEEDED) {
            GeoSylvaFieldExporter.write(snapshot, output, FieldExportLimits(64 * 1024, 256 * 1024))
        }
        assertEquals(0, output.size())
    }

    @Test fun `archive is deterministic and streams remain caller owned`() {
        val snapshot = fixture()
        var closed = false
        val output = object : ByteArrayOutputStream() { override fun close() { closed = true; super.close() } }
        GeoSylvaFieldExporter.write(snapshot, output)
        assertFalse(closed)
        assertArrayEquals(output.toByteArray(), archive(snapshot))
        val input = object : ByteArrayInputStream(output.toByteArray()) { override fun close() { closed = true; super.close() } }
        GeoSylvaFieldExporter.verify(input)
        assertFalse(closed)
    }

    private fun fixture(): WorksiteExportSnapshot {
        val header = WoodContractCodec.freezeHeader(WoodObjectHeader("Pile TEST"))
        val payload = WoodContractCodec.freezePayload(WoodObservationPayload(RawWoodMeasurement.Pile(
            length = RawWoodDimension("100", "m"), width = RawWoodDimension("1", "m"), meanHeight = RawWoodDimension("1", "m"))))
        return withBalances(WorksiteExportSnapshot(exportId = uuid(100), capturedAt = capturedAt, scope = "LOCAL_DEVICE",
            site = SavedWorksite(siteId, WorksiteForm("Chantier TEST", parcels = listOf(WorksiteParcel("CADASTRE", "79-000-AB-1", "Parcelle AB 1"))), 1, false),
            links = listOf(FieldTargetChoice(target, "Pile TEST")),
            depots = listOf(ExportWoodDepot(depotId, "Dépôt TEST", "ROADSIDE", 1, null, null, null, null, null, null)),
            objects = listOf(ExportWoodObject(objectId, depotId, "PILE", 1, header.text, header.sha256, null, observationId,
                listOf(ExportWoodObservation(observationId, observationId, 1, null, null, payload.text, payload.sha256, capturedAt, "anonymous", "UTC", 0)))),
            receipts = emptyList(), entries = listOf(entry(10, FieldEntryKind.INITIAL_STOCK, "100", "19/10/2026 09:00")),
            photos = emptyList(), balances = emptyList()))
    }

    private fun entry(n: Int, kind: FieldEntryKind, quantity: String, observed: String) = FieldEntry(uuid(n), target,
        FieldEntryForm(kind = kind, title = "Déclaration TEST", observed = observed, zone = "UTC", quantity = quantity,
            unit = FieldQuantityUnit.SOLID_M3, convention = "Mesure déclarée"), capturedAt, "anonymous")

    private fun withBalances(snapshot: WorksiteExportSnapshot): WorksiteExportSnapshot = snapshot.copy(balances = snapshot.objects.flatMap { item ->
        val history = snapshot.entries.filter { it.target.id == item.id }
        FieldBalance.active(history).filter { it.form.kind in FieldEntryForm.quantityKinds && it.form.unit != null && it.form.convention.isNotBlank() }
            .map { checkNotNull(it.form.unit) to it.form.convention }.distinct().map { (unit, convention) ->
                val b = FieldBalance.at(history, capturedAt, unit, convention)
                ExportFieldBalance(item.id, capturedAt, unit, convention, b.expected?.toPlainString(), b.observed?.toPlainString(), b.difference?.toPlainString(), b.reason)
            }
    })

    private fun roundtrip(snapshot: WorksiteExportSnapshot) = GeoSylvaFieldExporter.verify(ByteArrayInputStream(archive(snapshot)))
    private fun archive(snapshot: WorksiteExportSnapshot) = ByteArrayOutputStream().also { GeoSylvaFieldExporter.write(snapshot, it) }.toByteArray()
    private fun zip(files: Map<String, ByteArray>) = ByteArrayOutputStream().also { bytes -> ZipOutputStream(bytes).use { zip ->
        files.forEach { (name, content) -> zip.putNextEntry(ZipEntry(name)); zip.write(content); zip.closeEntry() }
    } }.toByteArray()
    private fun unzip(bytes: ByteArray): Map<String, ByteArray> = linkedMapOf<String, ByteArray>().also { files ->
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> while (true) { val entry = zip.nextEntry ?: break; files[entry.name] = zip.readBytes(); zip.closeEntry() } }
    }
    private fun expect(code: FieldExportError, block: () -> Unit) {
        try { block(); fail("Expected $code") } catch (error: FieldExportException) { assertEquals(code, error.code) }
    }
    private fun uuid(n: Int): String = UUID(0L, n.toLong()).toString()
}
