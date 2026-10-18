package com.forestry.counter.presentation.screens.wood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forestry.counter.domain.model.WoodDepotOwnerScope
import com.forestry.counter.domain.repository.*
import com.forestry.counter.domain.wood.*
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import com.forestry.counter.domain.field.*

/** Exact raw values stay in the form, including blanks and decimal commas. */
data class WoodManualForm(
    val kind: WoodObjectKind,
    val name: String = "", val tag: String = "", val species: String = "", val quality: String = "",
    val origin: String = "", val notes: String = "",
    val length: String = "", val depth: String = "", val height: String = "", val pieceLength: String = "",
    val start: String = "", val middle: String = "", val end: String = "",
    val shape: Boolean = false, val protocol: String = "", val method: String? = null, val bark: String? = null,
    val observedDate: String = "", val observedZone: String = ZoneId.systemDefault().id,
    val reason: String = "", val correction: SavedWoodObservation? = null,
    val identityOnly: Boolean = false, val newWithMeasurement: Boolean = false,
) {
    fun header() = WoodObjectHeader(name, tag.optional(), notes.optional(), origin.optional(), species.optional(), quality.optional())
    fun payload(): WoodObservationPayload {
        val originalTime = correction?.payload?.observedTime
        val observed = if (originalTime != null && observedZone == originalTime.zoneId &&
            observedDate == DATE_FORMAT.format(originalTime.instant.atZone(ZoneId.of(originalTime.zoneId)))) originalTime
        else if (observedDate.isBlank()) null else {
            val date = LocalDateTime.parse(observedDate, DATE_FORMAT)
            val zone = ZoneId.of(observedZone)
            val offsets = zone.rules.getValidOffsets(date)
            require(offsets.size == 1) { "INVALID_OBSERVED_DATE" }
            WoodObservedTime(date.toInstant(offsets.single()), zone.id, offsets.single().totalSeconds)
        }
        val measurement = if (kind == WoodObjectKind.PILE) RawWoodMeasurement.Pile(
            length.dimension("m"), depth.dimension("m"), height.dimension("m"),
            RawWoodMeasurement.Pile.RECTANGULAR_ENVELOPE.takeIf { shape }, protocol.optional(), pieceLength.dimension("m"),
        ) else RawWoodMeasurement.Log(length.dimension("m"), start.dimension("cm"), middle.dimension("cm"), end.dimension("cm"), method, bark)
        return WoodObservationPayload(measurement, observed, notes.optional(), origin.optional(), species.optional(), quality.optional(),
            correction?.payload?.location)
    }
    companion object {
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm").withResolverStyle(ResolverStyle.STRICT)
        fun from(item: SavedWoodObject, correction: SavedWoodObservation? = null): WoodManualForm {
            val p = correction?.payload
            val raw = p?.measurement
            val pile = raw as? RawWoodMeasurement.Pile
            val log = raw as? RawWoodMeasurement.Log
            // Editing is offered only for this UI's units; never silently relabel an imported dimension.
            return WoodManualForm(item.kind, name = item.header.name, tag = item.header.tag.orEmpty(),
                species = if (p != null) p.declaredSpecies.orEmpty() else item.header.declaredSpecies.orEmpty(),
                quality = if (p != null) p.declaredQuality.orEmpty() else item.header.declaredQuality.orEmpty(),
                origin = if (p != null) p.declaredOrigin.orEmpty() else item.header.declaredOrigin.orEmpty(),
                notes = p?.description.orEmpty(), length = (pile?.length ?: log?.length)?.rawText.orEmpty(),
                depth = pile?.width?.rawText.orEmpty(), height = pile?.meanHeight?.rawText.orEmpty(), pieceLength = pile?.pieceLength?.rawText.orEmpty(),
                start = log?.startDiameter?.rawText.orEmpty(), middle = log?.middleDiameter?.rawText.orEmpty(), end = log?.endDiameter?.rawText.orEmpty(),
                shape = pile?.shapeCode == RawWoodMeasurement.Pile.RECTANGULAR_ENVELOPE, protocol = pile?.protocolReference.orEmpty(),
                method = log?.methodCode, bark = log?.barkConventionCode,
                observedDate = p?.observedTime?.let { DATE_FORMAT.format(it.instant.atZone(ZoneId.of(it.zoneId))) }.orEmpty(),
                observedZone = p?.observedTime?.zoneId ?: ZoneId.systemDefault().id, correction = correction)
        }
        private fun String.dimension(unit: String): RawWoodDimension? = if (isEmpty()) null else RawWoodDimension(this, unit)
        private fun String.optional(): String? = takeUnless { isEmpty() }
    }
}

data class WoodObjectsState(
    val items: List<SavedWoodObject> = emptyList(), val detail: WoodObjectDetails? = null,
    val form: WoodManualForm? = null, val busy: Boolean = false, val saved: Boolean = false,
    val error: WoodRepositoryError? = null, val validationError: Boolean = false,
    val pendingRetry: Boolean = false, val accessLost: Boolean = false, val draftSaved: Boolean = false, val draftConflict: Boolean = false,
)

class WoodObjectsViewModel(private val repository: WoodObjectRepository, val depotId: UUID, private val fieldRepository: FieldRepository? = null) : ViewModel() {
    private val mutable = MutableStateFlow(WoodObjectsState())
    val state = mutable.asStateFlow()
    private val access = runCatching { repository.captureAccess() }.getOrNull()
    private var operation: Job? = null
    private var pending: WoodObjectCommand? = null
    private val draftMutex=Mutex()
    private var draftJob: Job?=null
    private var fieldAccess: FieldAccess?=null
    private var draftRevision: Long?=null
    private var nextAfterSave=false
    private val draftTarget=FieldTarget(FieldTargetKind.WOOD_DEPOT,depotId.toString())
    init {
        viewModelScope.launch { repository.accessChanges().collect { verifyAccess() } }
        run {
            if (fieldRepository != null) {
                fieldAccess=fieldRepository.captureAccess()
                restoreDraft()
            }
            mutable.value=mutable.value.copy(items=repository.list(depotId,checkNotNull(access)))
        }
    }
    fun verifyAccess(): Boolean {
        if (access != null && repository.isAccessCurrent(access) && (fieldAccess == null || fieldRepository?.isCurrent(checkNotNull(fieldAccess)) == true)) return true
        operation?.cancel()
        pending = null
        draftJob?.cancel()
        mutable.value = WoodObjectsState(accessLost = true, error = WoodRepositoryError.ACCESS_CHANGED)
        return false
    }
    private fun ready() = verifyAccess() && !mutable.value.busy
    fun refresh() { if (ready()) run { mutable.value = mutable.value.copy(items = repository.list(depotId, checkNotNull(access))) } }
    fun open(id: UUID) { if (ready()) run { mutable.value = mutable.value.copy(detail = repository.details(depotId, id, checkNotNull(access)), form = null, saved = false) } }
    fun create(kind: WoodObjectKind) {
        if (!ready()) return
        pending = null
        mutable.value = mutable.value.copy(form = WoodManualForm(kind, identityOnly = true), detail = null, error = null, saved = false, draftSaved = false)
        scheduleDraft()
    }
    fun editIdentity() {
        if (!ready()) return
        val item = mutable.value.detail?.item ?: return
        if (item.archivedAt != null) return
        pending = null
        mutable.value = mutable.value.copy(form = WoodManualForm.from(item).copy(identityOnly = true, notes = item.header.description.orEmpty()), error = null, saved = false, draftSaved = false)
        scheduleDraft()
    }
    fun measure(correction: SavedWoodObservation? = null) {
        if (!ready()) return
        val item = mutable.value.detail?.item ?: return
        if (item.archivedAt != null || correction != null && !canEdit(correction.payload.measurement)) return
        pending = null
        mutable.value = mutable.value.copy(form = WoodManualForm.from(item, correction), error = null, saved = false, draftSaved = false)
        scheduleDraft()
    }
    fun change(update: WoodManualForm.() -> WoodManualForm) {
        if (!ready() || pending != null || mutable.value.draftConflict) return
        val form = mutable.value.form ?: return
        mutable.value = mutable.value.copy(form = form.update(), validationError = false, error = null, draftSaved = false)
        scheduleDraft()
    }
    fun close() {
        if (!ready()) return
        draftJob?.cancel()
        run { draftMutex.withLock {
            clearDraft()
            pending=null
            mutable.value=WoodObjectsState(items=repository.list(depotId,checkNotNull(access)))
        } }
    }
    fun saveAndNext() { if (ready() && mutable.value.form?.kind==WoodObjectKind.LOG && mutable.value.form?.correction==null) saveInternal(true) }
    fun save() = saveInternal(false)
    private fun saveInternal(next: Boolean) {
        if (!ready() || mutable.value.draftConflict || mutable.value.error == WoodRepositoryError.CONFLICT) return
        if (pending == null) nextAfterSave=next
        val form = mutable.value.form ?: return
        val command = pending ?: try {
            if (form.identityOnly || form.newWithMeasurement) {
                val item = mutable.value.detail?.item
                if (item == null) WoodObjectCommand.Create(context(UUID.randomUUID()), form.kind, form.header(), if (form.newWithMeasurement) NewWoodObservation(UUID.randomUUID(),form.payload()) else null, false)
                else WoodObjectCommand.UpdateHeader(context(item.id), item.revision, form.header().copy(location = item.header.location))
            } else {
                val item = mutable.value.detail?.item ?: return
                val observation = NewWoodObservation(UUID.randomUUID(), form.payload())
                form.correction?.let { old -> WoodObjectCommand.CorrectObservation(context(item.id), item.revision,
                    old.record.observationId, old.record.observationRevision, observation, form.reason) }
                    ?: WoodObjectCommand.AddObservation(context(item.id), item.revision, observation, makeEffective = false)
            }
        } catch (_: IllegalArgumentException) {
            mutable.value = mutable.value.copy(validationError = true); return
        } catch (_: java.time.DateTimeException) {
            mutable.value = mutable.value.copy(validationError = true); return
        }
        submit(command)
    }
    fun select(observationId: UUID) {
        if (!ready()) return
        val item = mutable.value.detail?.item ?: return
        submit(pending ?: WoodObjectCommand.SelectEffective(context(item.id), item.revision, observationId))
    }
    fun retry() { if (ready()) pending?.let(::submit) ?: refresh() }
    private fun context(objectId: UUID) = WoodCommandContext(UUID.randomUUID(), depotId, objectId,
        WoodOwner(if (checkNotNull(access).accountId == null) WoodDepotOwnerScope.LOCAL_DEVICE else WoodDepotOwnerScope.PERSONAL, access.accountId), access.accountId ?: "anonymous")
    private fun submit(command: WoodObjectCommand) {
        pending = command
        draftJob?.cancel()
        run { draftMutex.withLock {
            persistDraft()
            repository.execute(command, checkNotNull(access))
            val detail = repository.details(depotId, command.context.objectId, access)
            val items = repository.list(depotId, access)
            if (!verifyAccess()) return@withLock
            val old=mutable.value.form
            clearDraft()
            pending = null
            mutable.value = WoodObjectsState(items, detail, saved = true)
            if(nextAfterSave && old!=null) {
                mutable.value=mutable.value.copy(detail=null,form=WoodManualForm(WoodObjectKind.LOG,
                    species=old.species,method=old.method,bark=old.bark,newWithMeasurement=true))
                nextAfterSave=false
                persistDraft()
            }
        } }
    }
    private fun scheduleDraft() {
        draftJob?.cancel()
        draftJob=viewModelScope.launch {delay(400); viewModelScope.launch {flushDraftNow()}}
    }
    fun flushDraft() {viewModelScope.launch {flushDraftNow()}}
    private suspend fun flushDraftNow() {
        try {draftMutex.withLock {persistDraft()}}
        catch(e:CancellationException) {throw e}
        catch(_:Exception) {if(verifyAccess()) mutable.value=mutable.value.copy(error=WoodRepositoryError.UNKNOWN_RESULT,draftSaved=false)}
    }
    private suspend fun persistDraft() {
        val repo=fieldRepository ?: return
        val form=mutable.value.form
        if(form==null && pending==null) return
        val command=pending?.let(WoodContractCodec::freezeCommand)
        val record=WoodStoredDraft(form?.toDraftFields(),mutable.value.detail?.item?.id?.toString(),mutable.value.detail?.item?.revision,
            form?.correction?.record?.observationId?.toString(),command?.utf8Bytes()?.toString(Charsets.UTF_8),command?.sha256,nextAfterSave)
        val saved=repo.saveDraft(draftTarget,"wood-form-v1",draftRevision,FieldCodec.json.encodeToString(record),checkNotNull(fieldAccess))
        draftRevision=saved.revision
        if(mutable.value.form==form) mutable.value=mutable.value.copy(draftSaved=true)
    }
    private suspend fun restoreDraft() {
        val repo=fieldRepository ?: return
        val saved=repo.loadDraft(draftTarget,"wood-form-v1",checkNotNull(fieldAccess)) ?: return
        val record=FieldCodec.json.decodeFromString<WoodStoredDraft>(saved.content)
        draftRevision=saved.revision
        val detail=record.objectId?.let {repository.details(depotId,UUID.fromString(it),checkNotNull(access))}
        val correction=record.correctionId?.let {id->checkNotNull(detail?.observations?.singleOrNull {it.record.observationId.toString()==id})}
        pending=record.command?.let {WoodContractCodec.readCommand(it.toByteArray(),checkNotNull(record.commandSha256))}
        nextAfterSave=record.next
        mutable.value=mutable.value.copy(detail=detail,form=record.fields?.let {restoreWoodForm(it,correction)},pendingRetry=pending!=null,draftSaved=true,
            draftConflict=pending==null && detail?.item?.revision!=record.revision,
            error=if(pending==null && detail?.item?.revision!=record.revision) WoodRepositoryError.CONFLICT else null)
    }
    private suspend fun clearDraft() {
        val repo=fieldRepository ?: return
        draftRevision?.let {
            try {repo.deleteDraft(draftTarget,"wood-form-v1",it,checkNotNull(fieldAccess))}
            catch(e:FieldException) {if(e.code != FieldError.CONFLICT) throw e}
            // A newer editor owns the slot: retain its draft, discard only this screen's copy.
        }
        draftRevision=null
    }
    private fun run(block: suspend () -> Unit) {
        mutable.value = mutable.value.copy(busy = true, error = null)
        operation = viewModelScope.launch {
            try {
                block()
                if (verifyAccess()) mutable.value = mutable.value.copy(busy = false, pendingRetry = pending != null)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (verifyAccess()) mutable.value = mutable.value.copy(busy = false,
                    error = (e as? WoodRepositoryException)?.code ?: WoodRepositoryError.UNKNOWN_RESULT, pendingRetry = pending != null)
            }
        }
    }
}
