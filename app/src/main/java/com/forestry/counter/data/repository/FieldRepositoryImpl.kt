package com.forestry.counter.data.repository

import androidx.room.withTransaction
import android.graphics.BitmapFactory
import com.forestry.counter.data.local.ForestryDatabase
import com.forestry.counter.data.local.entity.*
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.domain.field.*
import com.forestry.counter.domain.repository.*
import com.forestry.counter.domain.wood.WoodContractCodec
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/** Local, personal field records. A profession is never an access grant. */
class FieldRepositoryImpl(
    private val db: ForestryDatabase, private val identity: IdentityRepository,
    private val reads: BusinessReadAccess, private val clock: () -> Long = System::currentTimeMillis,
) : FieldRepository {
    private val dao = db.fieldDao()
    private val owner = Any()
    private class Access(val owner: Any, val snapshot: BusinessReadAccess.AccessSnapshot, val epoch: Long) : FieldAccess {
        val key = snapshot.accountId?.let { "ACCOUNT:$it" } ?: "LOCAL"
        val actor = snapshot.accountId ?: "anonymous"
    }
    override suspend fun captureAccess(): FieldAccess = Access(owner, reads.capture(), db.woodDataGate.capture()).also { current(it) }
    override fun isCurrent(access: FieldAccess) = (access as? Access)?.let {
        it.owner === owner && db.woodDataGate.isCurrent(it.epoch) && reads.isCurrent(it.snapshot)
    } == true
    override fun accessChanges(): Flow<Unit> = combine(identity.session, identity.sessionGeneration, db.woodDataGate.state,
        db.parcelSyncDao().observeBusinessReadOwners(), db.parcelSyncDao().observeBusinessReadParcelParents()) { _,_,_,_,_ -> Unit }
    private fun current(a: FieldAccess): Access {
        if (!isCurrent(a)) fail(FieldError.ACCESS_CHANGED)
        return a as Access
    }
    private suspend fun <T> tx(access: FieldAccess, mutation: Boolean = false, block: suspend (Access) -> T): T {
        val a = current(access)
        var committed = false
        return try {
            val value = db.woodDataGate.withAccess(a.epoch) { db.withTransaction {
                if (!reads.revalidate(a.snapshot)) fail(FieldError.ACCESS_CHANGED)
                current(a)
                val result = block(a)
                if (!reads.revalidate(a.snapshot)) fail(FieldError.ACCESS_CHANGED)
                current(a)
                result
            }.also { committed = mutation } }
            if (committed && !isCurrent(a)) fail(FieldError.UNKNOWN_RESULT)
            current(a)
            value
        } catch (e: CancellationException) { throw e }
        catch (e: IllegalStateException) {
            if (committed) fail(FieldError.UNKNOWN_RESULT)
            if (!isCurrent(a)) fail(FieldError.ACCESS_CHANGED)
            throw e
        }
    }
    override suspend fun sites(access: FieldAccess): List<SavedWorksite> = tx(access) { a -> dao.sites(a.key).map { site ->
        checkParcels(site.saved().form, a); site.saved()
    } }
    override suspend fun saveSite(id: String, form: WorksiteForm, revision: Int, archived: Boolean, access: FieldAccess): SavedWorksite = tx(access) { a ->
        canonical(id); form.validate(); checkParcels(form, a)
        val payload = FieldCodec.json.encodeToString(form)
        val old = dao.site(id)
        if (old == null) {
            if (revision != 0 || archived) fail(FieldError.CONFLICT)
            dao.insertSite(FieldWorksiteEntity(id, a.key, payload, hash(payload), 1, false, clock()))
        } else {
            if (old.ownerKey != a.key) fail(FieldError.UNAVAILABLE)
            if (old.payload != payload || old.archived != archived) {
                if (old.revision != revision || revision == Int.MAX_VALUE) fail(FieldError.CONFLICT)
                if (dao.updateSite(id, a.key, revision, payload, hash(payload), archived, clock()) != 1) fail(FieldError.CONFLICT)
            }
        }
        checkNotNull(dao.site(id)).saved()
    }
    override suspend fun parcelChoices(access: FieldAccess): List<WorksiteParcel> = tx(access) { a ->
        db.parcelleDao().getAllParcellesNow().filter { reads.canReadParcelle(it.parcelleId, a.snapshot) }.map { p ->
            val cadastral = listOfNotNull(p.codeInseeCommune, p.sectionCadastrale, p.numeroCadastral).takeIf { it.size == 3 }?.joinToString(":")
            WorksiteParcel(if (cadastral != null) "CADASTRE" else "GEOSYLVA", cadastral ?: p.parcelleId, p.name,
                p.parcelleId, p.version.toString())
        }
    }
    override suspend fun woodChoices(access: FieldAccess): List<FieldTargetChoice> = tx(access) { a ->
        val depots = db.woodDepotDao().getActive(if (a.snapshot.accountId == null) "LOCAL_DEVICE" else "PERSONAL", a.snapshot.accountId)
        depots.flatMap { d -> listOf(FieldTargetChoice(FieldTarget(FieldTargetKind.WOOD_DEPOT, d.depotId), d.name)) +
            db.woodObjectDao().objectsInDepot(d.depotId).filter { it.archivedAt == null && it.ownerAccountId == a.snapshot.accountId }.map { o ->
                FieldTargetChoice(FieldTarget(FieldTargetKind.WOOD_OBJECT,o.objectId), woodName(o))
            }
        }
    }
    override suspend fun link(site: String, target: FieldTarget, access: FieldAccess) = tx(access) { a ->
        target(FieldTarget(FieldTargetKind.WORKSITE, site), a, true)
        require(target.kind in setOf(FieldTargetKind.WOOD_OBJECT, FieldTargetKind.WOOD_DEPOT))
        target(target,a,true)
        if (target.kind == FieldTargetKind.WOOD_OBJECT && dao.sitesFor(target.kind.name,target.id).any { it.siteId != site }) fail(FieldError.CONFLICT)
        dao.addLink(FieldSiteLinkEntity(site, target.kind.name, target.id)); Unit
    }
    override suspend fun linked(site: String, access: FieldAccess): List<FieldTargetChoice> = tx(access) { a ->
        target(FieldTarget(FieldTargetKind.WORKSITE,site),a,false)
        dao.links(site).map { row -> val ref=FieldTarget(FieldTargetKind.valueOf(row.targetKind),row.targetId)
            FieldTargetChoice(ref, target(ref,a,false)) }
    }
    override suspend fun title(target: FieldTarget, access: FieldAccess): String = tx(access) { a -> target(target,a,false) }
    override suspend fun writable(target: FieldTarget, access: FieldAccess): Boolean = tx(access) { a ->
        try { target(target,a,true); true } catch(e: FieldException) { if(e.code == FieldError.UNAVAILABLE) false else throw e }
    }
    override suspend fun measurements(target: FieldTarget, access: FieldAccess): WoodObjectDetails? {
        if (target.kind != FieldTargetKind.WOOD_OBJECT) return null
        val depot=tx(access) { a -> target(target,a,false); checkNotNull(db.woodObjectDao().getObject(target.id)).depotId }
        val wood=WoodObjectRepositoryImpl(db,identity)
        val result=wood.details(UUID.fromString(depot),UUID.fromString(target.id),wood.captureAccess())
        tx(access) { a -> target(target,a,false) }
        return result
    }
    override suspend fun protectedEntries(target: FieldTarget, access: FieldAccess): Set<String> = tx(access) { a ->
        target(target,a,false)
        if (target.kind != FieldTargetKind.WOOD_OBJECT) emptySet() else {
            db.receiptDao().creditForObject(target.id)?.let { setOfNotNull(it.entryId,it.cancellationEntryId) } ?: emptySet()
        }
    }
    override suspend fun entries(target: FieldTarget, access: FieldAccess): List<FieldEntry> = tx(access) { a ->
        target(target,a,false); dao.entries(a.key,target.kind.name,target.id).map { it.saved() }
    }
    override suspend fun append(id: String, target: FieldTarget, form: FieldEntryForm, access: FieldAccess): FieldEntry = tx(access) { a ->
        target(target,a,false)
        canonical(id); form.validate(target)
        val payload=FieldCodec.json.encodeToString(form)
        val previous=dao.entry(id)
        if (previous != null) {
            if (previous.ownerKey != a.key || previous.targetKind != target.kind.name || previous.targetId != target.id || previous.payload != payload) fail(FieldError.CONFLICT)
            previous.saved()
        } else {
            target(target,a,true)
            val existing=dao.entries(a.key,target.kind.name,target.id).map { it.saved() }
            if (form.kind == FieldEntryKind.INITIAL_STOCK && db.receiptDao().creditForObject(target.id) != null) fail(FieldError.CONFLICT)
            form.correctionOf?.let { correction ->
                if (db.receiptDao().protectedEntry(correction) != 0) fail(FieldError.CONFLICT)
                val old=existing.singleOrNull { it.id == correction } ?: fail(FieldError.CONFLICT)
                if (old.form.kind != form.kind || existing.any { it.form.correctionOf == correction }) fail(FieldError.CONFLICT)
            }
            form.photoIds.forEach { photoId -> photoRow(photoId,target,a) }
            val row=FieldEntryEntity(id,a.key,target.kind.name,target.id,payload,hash(payload),clock(),a.actor)
            val next=FieldBalance.active(existing+row.saved())
            if (form.kind in FieldEntryForm.quantityKinds && form.kind != FieldEntryKind.REMAINING) {
                val ledgers = next.filter { it.form.kind in setOf(FieldEntryKind.INITIAL_STOCK,FieldEntryKind.INCOMING,FieldEntryKind.REMOVAL) }
                    .groupBy { checkNotNull(it.form.unit) to it.form.convention }
                for ((key, group) in ledgers) {
                    val time=group.mapNotNull { it.form.observedInstant() }.maxOrNull() ?: fail(FieldError.INVALID)
                    if (FieldBalance.at(next,time,key.first,key.second).reason in setOf("BASE_REQUIRED","MOVEMENT_BEFORE_BASE","NEGATIVE_BALANCE")) fail(FieldError.INVALID)
                }
            }
            dao.insertEntry(row); row.saved()
        }
    }
    override suspend fun loadDraft(target: FieldTarget, slot: String, access: FieldAccess): FieldDraft? = tx(access) { a ->
        target(target,a,false); draftSlot(slot); dao.draft(a.key,target.kind.name,target.id,slot)?.saved()
    }
    override suspend fun saveDraft(target: FieldTarget, slot: String, expected: Long?, content: String, access: FieldAccess): FieldDraft = tx(access) { a ->
        target(target,a,true); draftSlot(slot); require(content.toByteArray().size <= 100_000)
        val old=dao.draft(a.key,target.kind.name,target.id,slot)
        if (old != null && old.content == content) old.saved() else {
            if (old?.revision != expected || expected == Long.MAX_VALUE) fail(FieldError.CONFLICT)
            val row=FieldDraftEntity(a.key,target.kind.name,target.id,slot,(old?.revision ?: 0)+1,content,clock())
            dao.putDraft(row); row.saved()
        }
    }
    override suspend fun deleteDraft(target: FieldTarget, slot: String, expected: Long, access: FieldAccess) = tx(access) { a ->
        target(target,a,false); draftSlot(slot)
        val old=dao.draft(a.key,target.kind.name,target.id,slot)
        if (old != null && dao.deleteDraft(a.key,target.kind.name,target.id,slot,expected) != 1) fail(FieldError.CONFLICT)
        Unit
    }
    override suspend fun addPhoto(target: FieldTarget, bytes: ByteArray, mime: String, caption: String, access: FieldAccess): FieldPhoto {
        require(bytes.isNotEmpty() && bytes.size <= 12*1024*1024 && caption.length <= 500 && mime in setOf("image/jpeg","image/png","image/webp"))
        val frozen=bytes.copyOf()
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeByteArray(frozen,0,frozen.size,bounds)
        require(bounds.outWidth in 1..20000 && bounds.outHeight in 1..20000 && bounds.outWidth.toLong()*bounds.outHeight <= 100_000_000 && bounds.outMimeType == mime)
        return tx(access) { a ->
            target(target,a,true)
            val id=UUID.randomUUID().toString()
            val row=FieldPhotoEntity(id,a.key,target.kind.name,target.id,caption,mime,frozen.size,hash(frozen))
            dao.insertPhoto(row)
            dao.insertChunks((frozen.indices step 128*1024).mapIndexed { part,start -> FieldPhotoChunkEntity(id,part,frozen.copyOfRange(start,minOf(start+128*1024,frozen.size))) })
            row.saved()
        }
    }
    override suspend fun photo(id: String, target: FieldTarget, access: FieldAccess): Pair<FieldPhoto,ByteArray> = tx(access) { a ->
        target(target,a,false)
        val row=photoRow(id,target,a)
        val parts=dao.chunks(id)
        if (row.bytes !in 1..12*1024*1024 || parts.size > 96 || parts.map { it.part } != parts.indices.toList() || parts.sumOf { it.bytes.size } != row.bytes) fail(FieldError.CORRUPT)
        val bytes=ByteArray(row.bytes); var offset=0
        parts.forEach { it.bytes.copyInto(bytes,offset); offset+=it.bytes.size }
        if (hash(bytes) != row.sha256) fail(FieldError.CORRUPT)
        row.saved() to bytes
    }
    override suspend fun receipts(site: String, access: FieldAccess): List<SavedReceipt> = tx(access) { a ->
        target(FieldTarget(FieldTargetKind.WORKSITE,site),a,false)
        db.receiptDao().receipts(site,a.key).map { receiptVersion(it.id,it.revision,a) }
    }
    override suspend fun receipt(id: String, access: FieldAccess): SavedReceipt = tx(access) { a ->
        val row=receiptRow(id,a,false); receiptVersion(id,row.revision,a)
    }
    override suspend fun receiptHistory(id: String, access: FieldAccess): List<SavedReceipt> = tx(access) { a ->
        receiptRow(id,a,false); db.receiptDao().versions(id).map { it.savedReceipt() }
    }
    override suspend fun receiptWoodChoices(site: String, access: FieldAccess): List<ReceiptWoodChoice> = tx(access) { a ->
        target(FieldTarget(FieldTargetKind.WORKSITE,site),a,false)
        dao.links(site).filter { it.targetKind == FieldTargetKind.WOOD_OBJECT.name }.flatMap { link ->
            val ref=FieldTarget(FieldTargetKind.WOOD_OBJECT,link.targetId)
            target(ref,a,false)
            val item=checkNotNull(db.woodObjectDao().getObject(ref.id))
            if (item.archivedAt != null) emptyList() else {
                val entries=dao.entries(a.key,ref.kind.name,ref.id).map { it.saved() }
                val hasStock=entries.any { it.form.kind == FieldEntryKind.INITIAL_STOCK } || db.receiptDao().creditForObject(ref.id) != null
                val observations=db.woodObjectDao().observations(ref.id)
                val replaced=observations.mapNotNull { it.correctionOfObservationId }.toSet()
                val effective=db.woodObjectDao().effective(ref.id)?.observationId
                observations.filter { it.observationId !in replaced }.sortedWith(
                    compareByDescending<WoodObservationEntity> { it.observationId == effective }.thenByDescending { it.recordedAt }
                ).map { observation ->
                    WoodContractCodec.readPayload(observation.payloadJson.toByteArray(Charsets.UTF_8),observation.payloadSha256)
                    ReceiptWoodChoice(item.objectId,item.depotId,woodName(item),observation.observationId,
                        observation.observationRevision,observation.payloadJson,observation.payloadSha256,hasStock)
                }
            }
        }
    }
    override suspend fun saveReceipt(id: String, site: String, form: ReceiptForm, expectedRevision: Int, ready: Boolean, access: FieldAccess): SavedReceipt {
        // Freeze nested lists before waiting for the access gate; callers may own mutable lists.
        val frozen=FieldCodec.json.decodeFromString<ReceiptForm>(FieldCodec.json.encodeToString(form))
        return tx(access,true) { a ->
        val form=frozen
        canonical(id); form.validate(ready)
        target(FieldTarget(FieldTargetKind.WORKSITE,site),a,true)
        val old=db.receiptDao().receipt(id)
        val status=if(ready) ReceiptStatus.READY else ReceiptStatus.DRAFT
        if(old != null && (old.ownerKey != a.key || old.siteId != site)) fail(FieldError.UNAVAILABLE)
        val previous=old?.let { receiptVersion(id,it.revision,a) }
        if(previous != null && previous.status !in setOf(ReceiptStatus.DRAFT,ReceiptStatus.READY)) fail(FieldError.CONFLICT)
        val sources=receiptSources(site,form,a,ready)
        if(previous != null && previous.form == form && previous.status == status && previous.sources == sources) previous else {
            if((old?.revision ?: 0) != expectedRevision || expectedRevision == Int.MAX_VALUE) fail(FieldError.CONFLICT)
            val next=SavedReceipt(id,site,expectedRevision+1,status,form,clock(),a.actor,sources)
            if(old == null) db.receiptDao().insertReceipt(FieldReceiptEntity(id,a.key,site,next.revision))
            else if(db.receiptDao().advance(id,a.key,expectedRevision,next.revision) != 1) fail(FieldError.CONFLICT)
            storeReceipt(next); next
        }
    }
    }
    override suspend fun validateReceipt(commandId: String, id: String, expectedRevision: Int, access: FieldAccess): SavedReceipt = tx(access,true) { a ->
        val op=ReceiptOperation(commandId,id,expectedRevision,"VALIDATE")
        receiptReplay(op,a) ?: run {
            val row=receiptRow(id,a,true)
            val previous=receiptVersion(id,row.revision,a)
            if(previous.status != ReceiptStatus.READY || row.revision != expectedRevision || expectedRevision == Int.MAX_VALUE) fail(FieldError.CONFLICT)
            previous.form.validate(true)
            val sources=receiptSources(row.siteId,previous.form,a,true)
            if(previous.sources != sources) fail(FieldError.CONFLICT)
            val credits=previous.form.lines.map { line ->
                val ref=FieldTarget(FieldTargetKind.WOOD_OBJECT,line.objectId)
                val entries=dao.entries(a.key,ref.kind.name,ref.id).map { it.saved() }
                // Never infer a second physical receipt for the same pile, even in another unit.
                if(entries.any { it.form.kind == FieldEntryKind.INITIAL_STOCK } || db.receiptDao().creditForObject(line.objectId) != null) fail(FieldError.CONFLICT)
                val entryId=UUID.nameUUIDFromBytes("geosylva-receipt-v1:$id:${expectedRevision+1}:${line.id}".toByteArray(Charsets.UTF_8)).toString()
                val form=FieldEntryForm(kind=FieldEntryKind.INITIAL_STOCK,title=previous.form.title,notes=line.differenceReason,
                    observed=previous.form.observed,zone=previous.form.zone,quantity=line.quantity,unit=line.unit,
                    convention=line.ledgerConvention(),documentReference=previous.form.documentReference,photoIds=line.photoIds)
                val entry=newEntry(entryId,ref,form,a)
                validateLedger(entries+entry.saved())
                dao.insertEntry(entry)
                ReceiptCredit(line.id,line.objectId,entryId)
            }
            val result=previous.copy(revision=expectedRevision+1,status=ReceiptStatus.VALIDATED,recordedAt=clock(),recordedBy=a.actor,credits=credits)
            storeReceipt(result)
            credits.forEach { db.receiptDao().insertCredit(FieldReceiptCreditEntity(id,result.revision,it.lineId,it.objectId,it.entryId,null)) }
            if(db.receiptDao().advance(id,a.key,expectedRevision,result.revision) != 1) fail(FieldError.CONFLICT)
            storeReceiptCommand(op,result,a); result
        }
    }
    override suspend fun cancelReceipt(commandId: String, id: String, expectedRevision: Int, reason: String, access: FieldAccess): SavedReceipt = tx(access,true) { a ->
        require(reason.isNotBlank() && reason.length <= 500)
        val op=ReceiptOperation(commandId,id,expectedRevision,"CANCEL",reason)
        receiptReplay(op,a) ?: run {
            val row=receiptRow(id,a,true)
            val previous=receiptVersion(id,row.revision,a)
            if(previous.status != ReceiptStatus.VALIDATED || row.revision != expectedRevision || expectedRevision == Int.MAX_VALUE) fail(FieldError.CONFLICT)
            val credits=previous.credits.map { credit ->
                val ref=FieldTarget(FieldTargetKind.WOOD_OBJECT,credit.objectId)
                target(ref,a,true)
                val entries=dao.entries(a.key,ref.kind.name,ref.id).map { it.saved() }
                // Even a corrected movement is a dependency requiring a separate reconciliation.
                if(entries.any { it.form.kind in FieldEntryForm.quantityKinds && it.id != credit.entryId }) fail(FieldError.DEPENDENCIES)
                val initial=entries.singleOrNull { it.id == credit.entryId } ?: fail(FieldError.CORRUPT)
                val correctionId=UUID.nameUUIDFromBytes("geosylva-receipt-cancel-v1:$id:${credit.lineId}".toByteArray(Charsets.UTF_8)).toString()
                val form=initial.form.copy(quantity="0",correctionOf=initial.id,correctionReason=reason)
                val correction=newEntry(correctionId,ref,form,a)
                validateLedger(entries+correction.saved()); dao.insertEntry(correction)
                if(db.receiptDao().setCancellation(id,credit.lineId,correctionId) != 1) fail(FieldError.CONFLICT)
                credit.copy(cancellationEntryId=correctionId)
            }
            val result=previous.copy(revision=expectedRevision+1,status=ReceiptStatus.CANCELLED,recordedAt=clock(),recordedBy=a.actor,
                credits=credits,cancellationReason=reason)
            storeReceipt(result)
            if(db.receiptDao().advance(id,a.key,expectedRevision,result.revision) != 1) fail(FieldError.CONFLICT)
            storeReceiptCommand(op,result,a); result
        }
    }
    override suspend fun exportSnapshot(site: String, access: FieldAccess): WorksiteExportSnapshot = tx(access) { a ->
        target(FieldTarget(FieldTargetKind.WORKSITE,site),a,false)
        val worksite=checkNotNull(dao.site(site)).saved()
        val links=dao.links(site).map { FieldTarget(FieldTargetKind.valueOf(it.targetKind),it.targetId) }
        val choices=links.map { FieldTargetChoice(it,target(it,a,false)) }
        val objectIds=links.filter { it.kind == FieldTargetKind.WOOD_OBJECT }.map { it.id }.distinct().sorted()
        val objects=objectIds.map { id ->
            val row=checkNotNull(db.woodObjectDao().getObject(id)); woodName(row)
            val observations=db.woodObjectDao().observations(id).map { obs ->
                WoodContractCodec.readPayload(obs.payloadJson.toByteArray(Charsets.UTF_8),obs.payloadSha256)
                ExportWoodObservation(obs.observationId,obs.rootObservationId,obs.observationRevision,obs.correctionOfObservationId,
                    obs.correctionReason,obs.payloadJson,obs.payloadSha256,obs.recordedAt,obs.recordedBy,obs.recordedZoneId,obs.recordedOffsetSeconds)
            }
            val history=db.woodObjectDao().history(id).map { command ->
                WoodContractCodec.readCommand(command.commandJson.toByteArray(Charsets.UTF_8),command.commandSha256)
                ExportWoodCommand(command.commandId,command.commandJson,command.commandSha256,command.appliedAt,
                    command.appliedObjectRevision,command.createdObservationId,command.effectiveObservationId)
            }
            ExportWoodObject(id,row.depotId,row.kind,row.revision,row.headerJson,row.headerSha256,row.archivedAt,
                db.woodObjectDao().effective(id)?.observationId,observations,row.createdAt,row.createdBy,row.updatedAt,row.updatedBy,history)
        }
        val depotIds=(links.filter { it.kind == FieldTargetKind.WOOD_DEPOT }.map { it.id }+objects.map { it.depotId }).distinct().sorted()
        val depots=depotIds.map { id ->
            target(FieldTarget(FieldTargetKind.WOOD_DEPOT,id),a,false)
            val row=checkNotNull(db.woodDepotDao().getByIdIncludingDeleted(id))
            ExportWoodDepot(id,row.name,row.kind,row.revision,row.notes,row.latitude,row.longitude,row.accuracyM,row.locationSource,row.deletedAt,
                row.createdAt,row.createdBy,row.updatedAt,row.updatedBy)
        }
        // A depot may serve several sites. Only explicitly linked objects enter this snapshot.
        // Depot journals have no site attribution and may describe another worksite.
        // Export their descriptive metadata, but never infer that their journal belongs here.
        val targets=(listOf(FieldTarget(FieldTargetKind.WORKSITE,site))+links.filter { it.kind == FieldTargetKind.WOOD_OBJECT }).distinct()
        val entries=targets.flatMap { t -> dao.entries(a.key,t.kind.name,t.id).map { it.saved() } }
        val photos=targets.flatMap { t -> dao.photos(a.key,t.kind.name,t.id).map { ExportPhotoReference(t,it.saved()) } }
        val receipts=db.receiptDao().receipts(site,a.key).flatMap { db.receiptDao().versions(it.id).map { row -> row.savedReceipt() } }
        val at=clock()
        val balances=objects.flatMap { obj ->
            val history=entries.filter { it.target == FieldTarget(FieldTargetKind.WOOD_OBJECT,obj.id) }
            FieldBalance.active(history).filter { it.form.kind in FieldEntryForm.quantityKinds && it.form.unit != null && it.form.convention.isNotBlank() }
                .map { checkNotNull(it.form.unit) to it.form.convention }.distinct().map { (unit,convention) ->
                    val b=FieldBalance.at(history,at,unit,convention)
                    ExportFieldBalance(obj.id,at,unit,convention,b.expected?.toPlainString(),b.observed?.toPlainString(),b.difference?.toPlainString(),b.reason)
                }
        }
        WorksiteExportSnapshot(exportId=UUID.randomUUID().toString(),capturedAt=at,scope=if(a.snapshot.accountId == null) "LOCAL_DEVICE" else "PERSONAL",
            site=worksite,links=choices,depots=depots,objects=objects,receipts=receipts,entries=entries,photos=photos,balances=balances)
    }
    private suspend fun receiptRow(id: String,a: Access,write: Boolean): FieldReceiptEntity {
        canonical(id)
        val row=db.receiptDao().receipt(id) ?: fail(FieldError.UNAVAILABLE)
        if(row.ownerKey != a.key) fail(FieldError.UNAVAILABLE)
        target(FieldTarget(FieldTargetKind.WORKSITE,row.siteId),a,write)
        return row
    }
    private suspend fun receiptVersion(id: String,revision: Int,a: Access): SavedReceipt {
        val row=receiptRow(id,a,false)
        val result=(db.receiptDao().version(id,revision) ?: fail(FieldError.CORRUPT)).savedReceipt()
        if(result.siteId != row.siteId) fail(FieldError.CORRUPT)
        return result
    }
    private fun FieldReceiptVersionEntity.savedReceipt(): SavedReceipt {
        if(hash(payload) != sha256) fail(FieldError.CORRUPT)
        val result=FieldCodec.json.decodeFromString<SavedReceipt>(payload)
        if(result.id != receiptId || result.revision != revision) fail(FieldError.CORRUPT)
        result.form.validate(result.status != ReceiptStatus.DRAFT)
        return result
    }
    private suspend fun storeReceipt(receipt: SavedReceipt) {
        val payload=FieldCodec.json.encodeToString(receipt)
        require(payload.toByteArray(Charsets.UTF_8).size <= 700_000)
        db.receiptDao().insertVersion(FieldReceiptVersionEntity(receipt.id,receipt.revision,payload,hash(payload)))
    }
    private suspend fun receiptSources(site: String,form: ReceiptForm,a: Access,complete: Boolean): List<FrozenReceiptSource> {
        val links=dao.links(site).filter { it.targetKind == FieldTargetKind.WOOD_OBJECT.name }.map { it.targetId }.toSet()
        return form.lines.map { line ->
            if(line.objectId !in links) fail(FieldError.UNAVAILABLE)
            target(FieldTarget(FieldTargetKind.WOOD_OBJECT,line.objectId),a,true)
            val obj=checkNotNull(db.woodObjectDao().getObject(line.objectId))
            if(obj.depotId != line.depotId) fail(FieldError.CONFLICT)
            val observations=db.woodObjectDao().observations(line.objectId)
            val obs=observations.singleOrNull { it.observationId == line.observationId && it.observationRevision == line.observationRevision } ?: fail(FieldError.CONFLICT)
            if(complete && observations.any { it.correctionOfObservationId == obs.observationId }) fail(FieldError.CONFLICT)
            WoodContractCodec.readPayload(obs.payloadJson.toByteArray(Charsets.UTF_8),obs.payloadSha256)
            line.photoIds.forEach { photoRow(it,FieldTarget(FieldTargetKind.WOOD_OBJECT,line.objectId),a) }
            FrozenReceiptSource(line.id,obj.revision,woodName(obj),obs.payloadJson,obs.payloadSha256,obs.recordedAt,obs.recordedBy)
        }
    }
    private suspend fun receiptReplay(op: ReceiptOperation,a: Access): SavedReceipt? {
        canonical(op.commandId); canonical(op.receiptId)
        val previous=db.receiptDao().command(op.commandId) ?: return null
        val payload=FieldCodec.json.encodeToString(op)
        if(previous.ownerKey != a.key || previous.payload != payload || previous.sha256 != hash(payload)) fail(FieldError.CONFLICT)
        return receiptVersion(op.receiptId,previous.resultRevision,a)
    }
    private suspend fun storeReceiptCommand(op: ReceiptOperation,result: SavedReceipt,a: Access) {
        val payload=FieldCodec.json.encodeToString(op)
        db.receiptDao().insertCommand(FieldReceiptCommandEntity(op.commandId,a.key,result.id,payload,hash(payload),result.revision))
    }
    private fun newEntry(id: String,ref: FieldTarget,form: FieldEntryForm,a: Access): FieldEntryEntity {
        form.validate(ref)
        val payload=FieldCodec.json.encodeToString(form)
        return FieldEntryEntity(id,a.key,ref.kind.name,ref.id,payload,hash(payload),clock(),a.actor)
    }
    private fun validateLedger(entries: List<FieldEntry>) {
        val active=FieldBalance.active(entries)
        val groups=active.filter { it.form.kind in setOf(FieldEntryKind.INITIAL_STOCK,FieldEntryKind.INCOMING,FieldEntryKind.REMOVAL) }
            .groupBy { checkNotNull(it.form.unit) to it.form.convention }
        groups.forEach { (key,group) ->
            val at=group.mapNotNull { it.form.observedInstant() }.maxOrNull() ?: fail(FieldError.INVALID)
            if(FieldBalance.at(active,at,key.first,key.second).reason in setOf("BASE_REQUIRED","MOVEMENT_BEFORE_BASE","NEGATIVE_BALANCE")) fail(FieldError.INVALID)
        }
    }

    private suspend fun photoRow(id: String,t: FieldTarget,a: Access): FieldPhotoEntity {
        val row=dao.photo(id) ?: fail(FieldError.UNAVAILABLE)
        if (row.ownerKey != a.key || row.targetKind != t.kind.name || row.targetId != t.id) fail(FieldError.UNAVAILABLE)
        return row
    }
    private suspend fun checkParcels(form: WorksiteForm,a: Access) {
        form.parcels.mapNotNull { it.localParcelId }.forEach { id ->
            if (db.parcelleDao().getParcelleById(id) == null || !reads.canReadParcelle(id,a.snapshot)) fail(FieldError.UNAVAILABLE)
        }
    }
    private suspend fun target(t: FieldTarget,a: Access,write: Boolean): String = when (t.kind) {
        FieldTargetKind.WORKSITE -> {
            val row=dao.site(t.id) ?: fail(FieldError.UNAVAILABLE)
            if (row.ownerKey != a.key || write && row.archived) fail(FieldError.UNAVAILABLE)
            val form=row.saved().form; checkParcels(form,a); form.name
        }
        FieldTargetKind.WOOD_DEPOT -> {
            val row=db.woodDepotDao().getByIdIncludingDeleted(t.id) ?: fail(FieldError.UNAVAILABLE)
            if (row.ownerAccountId != a.snapshot.accountId || row.ownerScope != if (a.snapshot.accountId == null) "LOCAL_DEVICE" else "PERSONAL") fail(FieldError.UNAVAILABLE)
            if (write && row.deletedAt != null) fail(FieldError.UNAVAILABLE)
            row.name
        }
        FieldTargetKind.WOOD_OBJECT -> {
            val row=db.woodObjectDao().getObject(t.id) ?: fail(FieldError.UNAVAILABLE)
            target(FieldTarget(FieldTargetKind.WOOD_DEPOT,row.depotId),a,write)
            val expectedScope=if (a.snapshot.accountId == null) "LOCAL_DEVICE" else "PERSONAL"
            if (row.ownerAccountId != a.snapshot.accountId || row.ownerScope != expectedScope || write && row.archivedAt != null) fail(FieldError.UNAVAILABLE)
            if (write) dao.sitesFor(t.kind.name,t.id).forEach { target(FieldTarget(FieldTargetKind.WORKSITE,it.siteId),a,true) }
            woodName(row)
        }
        FieldTargetKind.FOREST -> {
            val row=db.foretDao().getById(t.id) ?: fail(FieldError.UNAVAILABLE)
            if (!reads.canReadForest(t.id,a.snapshot)) fail(FieldError.UNAVAILABLE)
            row.nom
        }
        FieldTargetKind.PARCEL -> {
            val row=db.parcelleDao().getParcelleById(t.id) ?: fail(FieldError.UNAVAILABLE)
            if (!reads.canReadParcelle(t.id,a.snapshot)) fail(FieldError.UNAVAILABLE)
            row.name
        }
        FieldTargetKind.PLOT -> {
            val row=db.placetteDao().getPlacetteById(t.id) ?: fail(FieldError.UNAVAILABLE)
            target(FieldTarget(FieldTargetKind.PARCEL,row.parcelleOwnerId),a,write)
            row.name ?: row.placetteId
        }
    }
    private fun woodName(row: WoodObjectEntity)=WoodContractCodec.readHeader(row.headerJson.toByteArray(),row.headerSha256).name
    private fun FieldWorksiteEntity.saved(): SavedWorksite {
        if (hash(payload) != sha256) fail(FieldError.CORRUPT)
        return SavedWorksite(id,FieldCodec.json.decodeFromString<WorksiteForm>(payload).validate(),revision,archived)
    }
    private fun FieldEntryEntity.saved(): FieldEntry {
        if (hash(payload) != sha256) fail(FieldError.CORRUPT)
        val t=FieldTarget(FieldTargetKind.valueOf(targetKind),targetId)
        return FieldEntry(id,t,FieldCodec.json.decodeFromString<FieldEntryForm>(payload).validate(t),recordedAt,author)
    }
    private fun FieldDraftEntity.saved()=FieldDraft(revision,content,updatedAt)
    private fun FieldPhotoEntity.saved()=FieldPhoto(id,caption,mime,bytes,sha256)
    private fun draftSlot(slot: String) { require(slot.matches(Regex("[a-z][a-z0-9_-]{0,40}"))) }
    private fun canonical(id: String) { require(UUID.fromString(id).toString() == id) }
    private fun hash(text: String)=hash(text.toByteArray(Charsets.UTF_8))
    private fun hash(bytes: ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun fail(code: FieldError): Nothing = throw FieldException(code)
}
