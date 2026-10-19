package com.forestry.counter.domain.field

import java.math.BigDecimal
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable enum class ReceiptStatus { DRAFT, READY, VALIDATED, CANCELLED }

/** A physical declaration, never a payable quantity or a contract signature. */
@Serializable data class ReceiptLine(
    val id: String = UUID.randomUUID().toString(),
    val objectId: String, val depotId: String,
    val observationId: String, val observationRevision: Int,
    val quantity: String = "", val unit: FieldQuantityUnit = FieldQuantityUnit.SOLID_M3,
    val convention: String = "", val conventionVersion: String = "",
    val differenceReason: String = "", val photoIds: List<String> = emptyList(),
) {
    /** Version is part of the ledger identity: different conventions never share a balance. */
    fun ledgerConvention(): String = if (conventionVersion.isBlank()) convention else "$convention [v:$conventionVersion]"
}
@Serializable data class ReceiptForm(
    val title: String = "", val observed: String = "", val zone: String = "Europe/Paris",
    val notes: String = "", val documentReference: String = "", val lines: List<ReceiptLine> = emptyList(),
) {
    fun validate(complete: Boolean): ReceiptForm {
        require(title.length <= 200 && notes.length <= 5000 && documentReference.length <= 500)
        require(observed.length <= 32 && zone.length <= 100 && lines.size <= 100)
        require(lines.map { it.id }.distinct().size == lines.size && lines.map { it.objectId }.distinct().size == lines.size)
        lines.forEach { line ->
            listOf(line.id,line.objectId,line.depotId,line.observationId).forEach { require(UUID.fromString(it).toString() == it) }
            require(line.observationRevision > 0 && line.quantity.length <= 64)
            require(line.convention.length <= 350 && line.conventionVersion.length <= 100 && line.differenceReason.length <= 2000)
            // Reserve the suffix syntax to make convention/version encoding unambiguous.
            require(!line.convention.contains(" [v:") && !line.conventionVersion.contains(']'))
            require(line.photoIds.size <= 6 && line.photoIds.distinct().size == line.photoIds.size)
            line.photoIds.forEach { require(UUID.fromString(it).toString() == it) }
            if (complete) {
                require(line.convention.isNotBlank() && FieldEntryForm.decimal(line.quantity) > BigDecimal.ZERO)
                FieldEntryForm(kind=FieldEntryKind.INITIAL_STOCK,title=title,observed=observed,zone=zone,
                    quantity=line.quantity,unit=line.unit,convention=line.ledgerConvention()).validate(FieldTarget(FieldTargetKind.WOOD_OBJECT,line.objectId))
            }
        }
        if (complete) require(title.isNotBlank() && observed.isNotBlank() && lines.isNotEmpty())
        return this
    }
}
@Serializable data class FrozenReceiptSource(
    val lineId: String, val objectRevision: Int, val objectName: String,
    val observationPayloadJson: String, val observationSha256: String,
    val observationRecordedAt: Long, val observationRecordedBy: String,
)
@Serializable data class ReceiptCredit(val lineId: String, val objectId: String, val entryId: String, val cancellationEntryId: String? = null)
@Serializable data class SavedReceipt(
    val id: String, val siteId: String, val revision: Int, val status: ReceiptStatus,
    val form: ReceiptForm, val recordedAt: Long, val recordedBy: String,
    val sources: List<FrozenReceiptSource> = emptyList(), val credits: List<ReceiptCredit> = emptyList(),
    val cancellationReason: String = "",
)
/** Candidate observation is explicit; a later remesure never rewrites a validated source. */
@Serializable data class ReceiptWoodChoice(
    val objectId: String, val depotId: String, val name: String,
    val observationId: String, val observationRevision: Int,
    val observationPayloadJson: String, val observationSha256: String,
    val alreadyHasStock: Boolean,
)
@Serializable data class ReceiptOperation(
    val commandId: String, val receiptId: String, val expectedRevision: Int,
    val action: String, val reason: String = "",
)

@Serializable data class WorksiteExportSnapshot(
    val formatVersion: Int = 1, val exportId: String, val capturedAt: Long,
    val scope: String, val site: SavedWorksite, val links: List<FieldTargetChoice>,
    val depots: List<ExportWoodDepot>, val objects: List<ExportWoodObject>,
    val receipts: List<SavedReceipt>, val entries: List<FieldEntry>,
    val photos: List<ExportPhotoReference>, val balances: List<ExportFieldBalance>,
)
@Serializable data class ExportWoodDepot(
    val id: String, val name: String, val kind: String, val revision: Int,
    val notes: String?, val latitude: Double?, val longitude: Double?, val accuracyM: Double?,
    val locationSource: String?, val deletedAt: Long?,
    val createdAt: Long? = null, val createdBy: String? = null, val updatedAt: Long? = null, val updatedBy: String? = null,
)
@Serializable data class ExportWoodObject(
    val id: String, val depotId: String, val kind: String, val revision: Int,
    val headerJson: String, val headerSha256: String, val archivedAt: Long?,
    val effectiveObservationId: String?, val observations: List<ExportWoodObservation>,
    val createdAt: Long? = null, val createdBy: String? = null, val updatedAt: Long? = null, val updatedBy: String? = null,
    val history: List<ExportWoodCommand> = emptyList(),
)
@Serializable data class ExportWoodObservation(
    val id: String, val rootId: String, val revision: Int, val correctionOf: String?, val correctionReason: String?,
    val payloadJson: String, val sha256: String, val recordedAt: Long, val recordedBy: String,
    val recordedZoneId: String, val recordedOffsetSeconds: Int,
)
@Serializable data class ExportPhotoReference(val target: FieldTarget, val photo: FieldPhoto)
@Serializable data class ExportFieldBalance(
    val objectId: String, val at: Long, val unit: FieldQuantityUnit, val convention: String,
    val expected: String?, val observed: String?, val difference: String?, val reason: String?,
)

@Serializable data class ExportWoodCommand(
    val id: String, val payloadJson: String, val sha256: String, val appliedAt: Long,
    val appliedObjectRevision: Int, val createdObservationId: String?, val effectiveObservationId: String?,
)
