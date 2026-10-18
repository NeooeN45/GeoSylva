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
    private suspend fun <T> tx(access: FieldAccess, block: suspend (Access) -> T): T {
        val a = current(access)
        return try {
            val value = db.woodDataGate.withAccess(a.epoch) { db.withTransaction {
                if (!reads.revalidate(a.snapshot)) fail(FieldError.ACCESS_CHANGED)
                current(a)
                val result = block(a)
                if (!reads.revalidate(a.snapshot)) fail(FieldError.ACCESS_CHANGED)
                current(a)
                result
            } }
            current(a)
            value
        } catch (e: CancellationException) { throw e }
        catch (e: IllegalStateException) {
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
            form.correctionOf?.let { correction ->
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
            if (row.ownerAccountId != a.snapshot.accountId || write && row.archivedAt != null) fail(FieldError.UNAVAILABLE)
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
