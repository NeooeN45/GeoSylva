package com.forestry.counter.domain.export

import com.forestry.counter.domain.field.*
import com.forestry.counter.domain.model.WoodDepotDraft
import com.forestry.counter.domain.model.WoodDepotKind
import com.forestry.counter.domain.model.WoodDepotLocation
import com.forestry.counter.domain.model.WoodDepotLocationSource
import com.forestry.counter.domain.wood.*
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/** Structural validation after decoding; no data repair and no import side effects. */
internal object FieldExportValidation {
    fun validate(snapshot: WorksiteExportSnapshot) {
        try {
            check(snapshot.formatVersion == 1) { "VERSION" }
            id(snapshot.exportId); id(snapshot.site.id)
            check(snapshot.capturedAt >= 0 && snapshot.scope in setOf("LOCAL_DEVICE", "PERSONAL"))
            check(snapshot.site.revision > 0)
            snapshot.site.form.validate()
            unique(snapshot.links.map { it.target })
            unique(snapshot.depots.map { it.id }); unique(snapshot.objects.map { it.id })
            unique(snapshot.entries.map { it.id }); unique(snapshot.photos.map { it.photo.id })
            unique(snapshot.receipts.map { it.id to it.revision })
            unique(snapshot.objects.flatMap { it.observations }.map { it.id })
            val depots = snapshot.depots.associateBy { it.id }
            snapshot.depots.forEach { depot ->
                id(depot.id); check(depot.revision > 0)
                check((depot.latitude == null) == (depot.longitude == null))
                val location = depot.latitude?.let { lat -> WoodDepotLocation(lat, checkNotNull(depot.longitude), depot.accuracyM,
                    depot.locationSource?.let(WoodDepotLocationSource::valueOf) ?: WoodDepotLocationSource.MANUAL) }
                WoodDepotDraft(depot.name, WoodDepotKind.valueOf(depot.kind), depot.notes, location)
            }
            snapshot.objects.forEach { item ->
                id(item.id); check(item.depotId in depots && item.revision > 0)
                WoodContractCodec.readHeader(item.headerJson.toByteArray(Charsets.UTF_8), item.headerSha256)
                val kind = WoodObjectKind.valueOf(item.kind)
                val records = item.observations.map { observation ->
                    id(observation.id); id(observation.rootId)
                    check(observation.recordedAt >= 0 && observation.recordedBy.isNotBlank())
                    val instant = Instant.ofEpochMilli(observation.recordedAt)
                    check(ZoneId.of(observation.recordedZoneId).rules.getOffset(instant) == ZoneOffset.ofTotalSeconds(observation.recordedOffsetSeconds))
                    val payload = WoodContractCodec.readPayload(observation.payloadJson.toByteArray(Charsets.UTF_8), observation.sha256)
                    WoodObservationRecord(RawWoodObservation(UUID.fromString(observation.id), UUID.fromString(item.id), instant,
                        payload.measurement, payload.observedTime), UUID.fromString(observation.rootId), observation.revision,
                        observation.correctionOf?.let(UUID::fromString), observation.correctionReason)
                }
                check(WoodObservationHistory.restore(UUID.fromString(item.id), kind, item.revision, records,
                    item.effectiveObservationId?.let(UUID::fromString)) is WoodHistoryResult.Accepted)
                unique(item.history.map { it.id })
                item.history.forEach { history ->
                    val command = WoodContractCodec.readCommand(history.payloadJson.toByteArray(Charsets.UTF_8), history.sha256)
                    check(command.context.commandId.toString() == history.id && command.context.objectId.toString() == item.id && command.context.depotId.toString() == item.depotId)
                    check(history.appliedAt >= 0 && history.appliedObjectRevision in 1..item.revision)
                    history.createdObservationId?.let { check(it in item.observations.map { obs -> obs.id }) }
                    history.effectiveObservationId?.let { check(it in item.observations.map { obs -> obs.id }) }
                }
            }
            val linkedObjects = snapshot.links.filter { it.target.kind == FieldTargetKind.WOOD_OBJECT }.map { it.target.id }.toSet()
            check(snapshot.objects.map { it.id }.toSet() == linkedObjects)
            // Depot metadata can be shared by several sites; its whole journal is outside this export.
            val allowedTargets = snapshot.objects.map { FieldTarget(FieldTargetKind.WOOD_OBJECT, it.id) }.toSet() +
                FieldTarget(FieldTargetKind.WORKSITE, snapshot.site.id)
            snapshot.photos.forEach { ref ->
                id(ref.photo.id)
                check(ref.target in allowedTargets && ref.photo.bytes in 1..12 * 1024 * 1024)
                check(ref.photo.mime in setOf("image/jpeg", "image/png", "image/webp") && Regex("[a-f0-9]{64}").matches(ref.photo.sha256))
            }
            val photos = snapshot.photos.associateBy { it.photo.id }
            val entries = snapshot.entries.associateBy { it.id }
            snapshot.entries.forEach { entry ->
                id(entry.id)
                check(entry.target in allowedTargets && entry.recordedAt >= 0 && entry.author.isNotBlank())
                entry.form.validate(entry.target)
                entry.form.photoIds.forEach { check(photos[it]?.target == entry.target) }
                entry.form.correctionOf?.let { original ->
                    val old = checkNotNull(entries[original])
                    check(old.id != entry.id && old.target == entry.target && old.form.kind == entry.form.kind)
                }
            }
            // Each immutable event can be corrected at most once, and correction chains cannot cycle.
            unique(snapshot.entries.mapNotNull { it.form.correctionOf })
            val visited = mutableSetOf<String>()
            snapshot.entries.forEach { entry ->
                val path = mutableSetOf<String>()
                var cursor: String? = entry.id
                while (cursor != null && cursor !in visited) {
                    check(path.add(cursor))
                    cursor = entries.getValue(cursor).form.correctionOf
                }
                visited.addAll(path)
            }
            receipts(snapshot, entries, photos)
            balances(snapshot)
        } catch (e: FieldExportException) { throw e }
        catch (_: RuntimeException) { throw FieldExportException(FieldExportError.INVALID_SNAPSHOT) }
    }

    private fun receipts(snapshot: WorksiteExportSnapshot, entries: Map<String, FieldEntry>, photos: Map<String, ExportPhotoReference>) {
        val objects = snapshot.objects.associateBy { it.id }
        snapshot.receipts.forEach { receipt ->
            id(receipt.id)
            check(receipt.siteId == snapshot.site.id && receipt.revision > 0 && receipt.recordedAt >= 0 && receipt.recordedBy.isNotBlank())
            receipt.form.validate(receipt.status != ReceiptStatus.DRAFT)
            unique(receipt.sources.map { it.lineId }); unique(receipt.credits.map { it.lineId }); unique(receipt.credits.map { it.entryId })
            check(receipt.sources.map { it.lineId }.toSet() == receipt.form.lines.map { it.id }.toSet())
            receipt.form.lines.forEach { line ->
                val item = checkNotNull(objects[line.objectId])
                check(item.depotId == line.depotId)
                val observation = item.observations.single { it.id == line.observationId && it.revision == line.observationRevision }
                val source = receipt.sources.single { it.lineId == line.id }
                check(source.objectRevision in 1..item.revision && source.objectName.isNotBlank())
                check(source.observationPayloadJson == observation.payloadJson && source.observationSha256 == observation.sha256 &&
                    source.observationRecordedAt == observation.recordedAt && source.observationRecordedBy == observation.recordedBy)
                line.photoIds.forEach { check(photos[it]?.target == FieldTarget(FieldTargetKind.WOOD_OBJECT, line.objectId)) }
            }
            if (receipt.status in setOf(ReceiptStatus.DRAFT, ReceiptStatus.READY)) check(receipt.credits.isEmpty())
            else {
                check(receipt.credits.map { it.lineId }.toSet() == receipt.form.lines.map { it.id }.toSet())
                receipt.credits.forEach { credit ->
                    val line = receipt.form.lines.single { it.id == credit.lineId }
                    check(credit.objectId == line.objectId)
                    val original = checkNotNull(entries[credit.entryId])
                    check(original.target == FieldTarget(FieldTargetKind.WOOD_OBJECT, line.objectId))
                    check(original.form.kind == FieldEntryKind.INITIAL_STOCK && original.form.correctionOf == null &&
                        original.form.quantity == line.quantity && original.form.unit == line.unit &&
                        original.form.convention == line.ledgerConvention() && original.form.observed == receipt.form.observed && original.form.zone == receipt.form.zone)
                    if (receipt.status == ReceiptStatus.CANCELLED) {
                        check(receipt.cancellationReason.isNotBlank())
                        val cancellation = checkNotNull(entries[checkNotNull(credit.cancellationEntryId)])
                        check(cancellation.target == original.target && cancellation.form.kind == FieldEntryKind.INITIAL_STOCK &&
                            cancellation.form.correctionOf == original.id && cancellation.form.correctionReason == receipt.cancellationReason &&
                            FieldEntryForm.decimal(cancellation.form.quantity).signum() == 0 && cancellation.form.unit == line.unit &&
                            cancellation.form.convention == line.ledgerConvention() && cancellation.form.observed == receipt.form.observed && cancellation.form.zone == receipt.form.zone)
                    } else check(credit.cancellationEntryId == null)
                }
            }
        }
        // The same physical entry cannot be credited by two different receipt documents.
        val creditOwners = mutableMapOf<String, Pair<String, String>>()
        snapshot.receipts.forEach { receipt -> receipt.credits.forEach { credit ->
            val owner = receipt.id to credit.lineId
            check(creditOwners.put(credit.entryId, owner)?.let { it == owner } != false)
        } }
    }

    private fun balances(snapshot: WorksiteExportSnapshot) {
        val expectedKeys = snapshot.objects.flatMap { item ->
            FieldBalance.active(snapshot.entries.filter { it.target == FieldTarget(FieldTargetKind.WOOD_OBJECT, item.id) })
                .filter { it.form.kind in FieldEntryForm.quantityKinds && it.form.unit != null && it.form.convention.isNotBlank() }
                .map { Triple(item.id, checkNotNull(it.form.unit), it.form.convention) }
        }.toSet()
        val actualKeys = snapshot.balances.map { Triple(it.objectId, it.unit, it.convention) }
        if (actualKeys.toSet() != expectedKeys || actualKeys.distinct().size != actualKeys.size) throw FieldExportException(FieldExportError.BALANCE_MISMATCH)
        snapshot.balances.forEach { balance ->
            if (balance.at != snapshot.capturedAt) throw FieldExportException(FieldExportError.BALANCE_MISMATCH)
            val entries = snapshot.entries.filter { it.target == FieldTarget(FieldTargetKind.WOOD_OBJECT, balance.objectId) }
            val computed = FieldBalance.at(entries, balance.at, balance.unit, balance.convention)
            if (balance.expected != computed.expected?.toPlainString() || balance.observed != computed.observed?.toPlainString() ||
                balance.difference != computed.difference?.toPlainString() || balance.reason != computed.reason) throw FieldExportException(FieldExportError.BALANCE_MISMATCH)
        }
    }

    private fun id(value: String) { check(UUID.fromString(value).toString() == value) }
    private fun <T> unique(values: List<T>) { check(values.size == values.toSet().size) }
}
