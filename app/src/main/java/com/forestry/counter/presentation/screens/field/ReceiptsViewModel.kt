package com.forestry.counter.presentation.screens.field

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forestry.counter.domain.field.*
import com.forestry.counter.domain.repository.*
import com.forestry.counter.domain.wood.RawWoodMeasurement
import com.forestry.counter.domain.wood.WoodContractCodec
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

data class ReceiptsState(
    val siteTitle: String = "",
    val receipts: List<SavedReceipt> = emptyList(),
    val choices: List<ReceiptWoodChoice> = emptyList(),
    val detail: SavedReceipt? = null,
    val history: List<SavedReceipt> = emptyList(),
    val form: ReceiptForm? = null,
    val photos: Map<String, List<String>> = emptyMap(),
    val busy: Boolean = true,
    val draftSaved: Boolean = false,
    val writable: Boolean = false,
    val accessLost: Boolean = false,
    val retry: Boolean = false,
    val error: FieldError? = null,
)

/** Durable business draft and durable operation ID are separate from the ledger. */
class ReceiptsViewModel(private val repository: FieldRepository, val siteId: String) : ViewModel() {
    private val mutable = MutableStateFlow(ReceiptsState())
    val state = mutable.asStateFlow()
    private val target = FieldTarget(FieldTargetKind.WORKSITE, siteId)
    private var access: FieldAccess? = null
    private var editorId: String? = null
    private var editorRevision = 0
    private var pending: ReceiptOperation? = null
    private var pendingRevision: Long? = null
    private val writes = Mutex()
    private var debounce: Job? = null

    init {
        action {
            access = repository.captureAccess()
            mutable.value = mutable.value.copy(siteTitle = repository.title(target, permit()), writable = repository.writable(target, permit()))
            refresh()
            repository.loadDraft(target, OPERATION_SLOT, permit())?.let { saved ->
                pendingRevision = saved.revision
                pending = FieldCodec.json.decodeFromString<ReceiptOperation>(saved.content)
                val operation = checkNotNull(pending)
                require(operation.action in setOf("VALIDATE", "CANCEL"))
                require(repository.receipt(operation.receiptId, permit()).siteId == siteId)
                show(operation.receiptId)
                mutable.value = mutable.value.copy(retry = true, error = FieldError.UNKNOWN_RESULT)
            }
        }
        viewModelScope.launch {
            repository.accessChanges().collect { if (access != null && !repository.isCurrent(permit())) revoke() }
        }
    }

    private fun permit() = checkNotNull(access)
    private fun ready() = !mutable.value.busy && !mutable.value.accessLost
    private fun revoke() {
        debounce?.cancel(); pending = null; pendingRevision = null; editorId = null
        mutable.value = ReceiptsState(busy = false, accessLost = true, error = FieldError.ACCESS_CHANGED)
    }
    private suspend fun refresh() {
        val receipts = repository.receipts(siteId, permit())
        val choices = repository.receiptWoodChoices(siteId, permit())
        val photos = choices.map { it.objectId }.distinct().associateWith { id ->
            repository.entries(FieldTarget(FieldTargetKind.WOOD_OBJECT, id), permit()).flatMap { it.form.photoIds }.distinct()
        }
        mutable.value = mutable.value.copy(receipts = receipts, choices = choices, photos = photos)
    }
    private suspend fun show(id: String) {
        val receipt = repository.receipt(id, permit())
        require(receipt.siteId == siteId)
        editorId = id; editorRevision = receipt.revision
        mutable.value = mutable.value.copy(detail = receipt, history = repository.receiptHistory(id, permit()), form = null, draftSaved = true)
    }
    fun open(id: String) {
        if (!ready() || pending != null) return
        action { show(id) }
    }
    fun reload() {
        if (!ready() || mutable.value.form != null || pending != null) return
        action {
            mutable.value = mutable.value.copy(writable = repository.writable(target, permit()))
            refresh()
            mutable.value.detail?.id?.let { show(it) }
        }
    }
    fun create() {
        if (!ready() || !mutable.value.writable || pending != null) return
        editorId = UUID.randomUUID().toString(); editorRevision = 0
        mutable.value = mutable.value.copy(detail = null, history = emptyList(), form = ReceiptForm(
            observed = LocalDateTime.now().format(FieldEntryForm.dateFormat), zone = ZoneId.systemDefault().id,
        ), draftSaved = false)
        action { writes.withLock { persist(false) } }
    }
    fun edit() {
        if (!ready() || !mutable.value.writable || pending != null) return
        val detail = mutable.value.detail ?: return
        if (detail.status !in setOf(ReceiptStatus.DRAFT, ReceiptStatus.READY)) return
        editorId = detail.id; editorRevision = detail.revision
        mutable.value = mutable.value.copy(form = detail.form, draftSaved = true, error = null)
    }
    fun change(update: ReceiptForm.() -> ReceiptForm) {
        if (!ready() || pending != null || !mutable.value.writable) return
        val form = mutable.value.form ?: return
        mutable.value = mutable.value.copy(form = form.update(), draftSaved = false, error = null)
        debounce?.cancel()
        debounce = viewModelScope.launch { delay(450); viewModelScope.launch { persistSafely() } }
    }
    fun setNow() {
        if (!ready()) return
        val form = mutable.value.form ?: return
        val zone = runCatching { ZoneId.of(form.zone) }.getOrNull()
        if (zone == null) mutable.value = mutable.value.copy(error = FieldError.INVALID)
        else change { copy(observed = LocalDateTime.now(zone).format(FieldEntryForm.dateFormat)) }
    }
    fun addWood(choice: ReceiptWoodChoice) {
        if (choice.alreadyHasStock || mutable.value.form?.lines?.any { it.objectId == choice.objectId } != false) return
        val payload = runCatching { WoodContractCodec.readPayload(choice.observationPayloadJson.toByteArray(), choice.observationSha256) }.getOrNull() ?: return
        change { copy(lines = lines + ReceiptLine(objectId = choice.objectId, depotId = choice.depotId,
            observationId = choice.observationId, observationRevision = choice.observationRevision,
            unit = if (payload.measurement is RawWoodMeasurement.Pile) FieldQuantityUnit.APPARENT_M3 else FieldQuantityUnit.SOLID_M3)) }
    }
    fun line(id: String, update: ReceiptLine.() -> ReceiptLine) = change { copy(lines = lines.map { if (it.id == id) it.update() else it }) }
    fun removeLine(id: String) = change { copy(lines = lines.filterNot { it.id == id }) }
    fun flush() {
        debounce?.cancel()
        viewModelScope.launch { persistSafely() }
    }
    private suspend fun persistSafely() {
        try { writes.withLock { if (mutable.value.form != null && pending == null) persist(false) } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error(e) }
    }
    private suspend fun persist(ready: Boolean): SavedReceipt? {
        val form = mutable.value.form ?: return null
        val id = checkNotNull(editorId)
        val saved = try { repository.saveReceipt(id, siteId, form, editorRevision, ready, permit()) }
        catch (e: Exception) {
            if (e is CancellationException) throw e
            // A response can be lost after storage. Only the exact persisted draft is recovered.
            if ((e as? FieldException)?.code !in setOf(FieldError.IO, FieldError.UNKNOWN_RESULT)) throw e
            val found = runCatching { repository.receipt(id, permit()) }.getOrNull()
            if (found == null || found.siteId != siteId || found.form != form || found.revision != editorRevision + 1 ||
                found.status != if (ready) ReceiptStatus.READY else ReceiptStatus.DRAFT) throw e
            found
        }
        editorRevision = saved.revision
        if (mutable.value.form == form) mutable.value = mutable.value.copy(detail = saved, draftSaved = true)
        return saved
    }
    fun saveDraft() {
        if (!ready() || pending != null) return
        debounce?.cancel()
        action { writes.withLock { persist(false) }; refresh() }
    }
    fun review() {
        if (!ready() || pending != null) return
        try { mutable.value.form?.validate(true) ?: return }
        catch (_: IllegalArgumentException) { mutable.value = mutable.value.copy(error = FieldError.INVALID); return }
        debounce?.cancel()
        action {
            writes.withLock { persist(true)?.let { show(it.id) } }
            refresh()
        }
    }
    fun validate() {
        if (!ready()) return
        val detail = mutable.value.detail ?: return
        if (pending == null && detail.status != ReceiptStatus.READY) return
        operate(pending ?: ReceiptOperation(UUID.randomUUID().toString(), detail.id, detail.revision, "VALIDATE"))
    }
    fun cancel(reason: String) {
        if (!ready() || reason.isBlank()) return
        val detail = mutable.value.detail ?: return
        if (pending == null && detail.status != ReceiptStatus.VALIDATED) return
        operate(pending ?: ReceiptOperation(UUID.randomUUID().toString(), detail.id, detail.revision, "CANCEL", reason.trim()))
    }
    fun retry() { if (ready()) pending?.let(::operate) }
    private fun operate(operation: ReceiptOperation) {
        action {
            writes.withLock {
                pending = operation
                mutable.value = mutable.value.copy(retry = true)
                if (pendingRevision == null) {
                    val saved = repository.saveDraft(target, OPERATION_SLOT, pendingRevision, FieldCodec.json.encodeToString(operation), permit())
                    pendingRevision = saved.revision
                }
                val result = when (operation.action) {
                    "VALIDATE" -> repository.validateReceipt(operation.commandId, operation.receiptId, operation.expectedRevision, permit())
                    "CANCEL" -> repository.cancelReceipt(operation.commandId, operation.receiptId, operation.expectedRevision, operation.reason, permit())
                    else -> throw FieldException(FieldError.INVALID)
                }
                pendingRevision?.let { repository.deleteDraft(target, OPERATION_SLOT, it, permit()) }
                pending = null; pendingRevision = null
                show(result.id); refresh()
                mutable.value = mutable.value.copy(retry = false)
            }
        }
    }
    /** A refused command has no effect; clear its pending retry only after reading it again. */
    fun dismissRefusedOperation() {
        if (!ready() || mutable.value.error !in setOf(FieldError.DEPENDENCIES, FieldError.INVALID, FieldError.CONFLICT)) return
        val operation = pending ?: return
        action { writes.withLock {
            show(operation.receiptId)
            pendingRevision?.let { repository.deleteDraft(target, OPERATION_SLOT, it, permit()) }
            pending = null; pendingRevision = null
            mutable.value = mutable.value.copy(retry = false)
        } }
    }
    fun leave(onDone: () -> Unit) {
        if (mutable.value.accessLost) { onDone(); return }
        if (!ready()) return
        debounce?.cancel()
        action {
            writes.withLock { if (mutable.value.form != null) persist(false) }
            if (mutable.value.detail != null || mutable.value.form != null) {
                mutable.value = mutable.value.copy(detail = null, form = null, history = emptyList())
                refresh()
            } else onDone()
        }
    }
    private fun action(block: suspend () -> Unit) {
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error(e) }
            finally { if (access != null && !repository.isCurrent(permit())) revoke() else mutable.value = mutable.value.copy(busy = false) }
        }
    }
    private fun error(e: Exception) {
        if (access != null && !repository.isCurrent(permit())) revoke()
        else mutable.value = mutable.value.copy(error = (e as? FieldException)?.code ?: if (e is IllegalArgumentException) FieldError.INVALID else FieldError.IO)
    }
    companion object { private const val OPERATION_SLOT = "receipt-operation-v1" }
}
