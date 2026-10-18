package com.forestry.counter.presentation.screens.field

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forestry.counter.domain.field.*
import com.forestry.counter.domain.repository.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

@Serializable internal data class JournalDraft(val id: String, val form: FieldEntryForm, val submitted: Boolean = false)
data class FieldJournalState(
    val title: String = "", val entries: List<FieldEntry> = emptyList(), val form: FieldEntryForm? = null,
    val busy: Boolean = true, val error: FieldError? = null, val draftSaved: Boolean = false,
    val retry: Boolean = false, val accessLost: Boolean = false, val writable: Boolean = false, val measurements: WoodObjectDetails? = null,
)
class FieldJournalViewModel(private val repository: FieldRepository, val target: FieldTarget) : ViewModel() {
    private val mutable=MutableStateFlow(FieldJournalState())
    val state=mutable.asStateFlow()
    private var access: FieldAccess?=null
    private var draft: JournalDraft?=null
    private var revision: Long?=null
    private val writes=Mutex()
    private var debounce: Job?=null
    init {
        action {
            access=repository.captureAccess()
            mutable.value=mutable.value.copy(title=repository.title(target,permit()),entries=repository.entries(target,permit()),writable=repository.writable(target,permit()),measurements=repository.measurements(target,permit()))
            repository.loadDraft(target,SLOT,permit())?.let {
                revision=it.revision
                draft=FieldCodec.json.decodeFromString<JournalDraft>(it.content)
                mutable.value=mutable.value.copy(form=draft?.form,retry=draft?.submitted == true,draftSaved=true)
            }
        }
        viewModelScope.launch { repository.accessChanges().collect { if (access != null && !repository.isCurrent(permit())) revoke() } }
    }
    private fun permit()=checkNotNull(access)
    private fun revoke() { debounce?.cancel(); draft=null; revision=null; mutable.value=FieldJournalState(busy=false,accessLost=true,error=FieldError.ACCESS_CHANGED) }
    private fun ready()=!mutable.value.busy && !mutable.value.accessLost
    fun start(entry: FieldEntry?=null) {
        if (!ready() || draft != null || !mutable.value.writable) return
        val form=entry?.form?.copy(correctionOf=entry.id,correctionReason="") ?: FieldEntryForm()
        draft=JournalDraft(UUID.randomUUID().toString(),form)
        mutable.value=mutable.value.copy(form=form,error=null,draftSaved=false)
        schedule()
    }
    fun change(update: FieldEntryForm.()->FieldEntryForm) {
        if (!ready() || draft?.submitted == true) return
        draft=draft?.copy(form=checkNotNull(draft).form.update()) ?: return
        mutable.value=mutable.value.copy(form=draft?.form,error=null,draftSaved=false)
        schedule()
    }
    private fun schedule() {
        debounce?.cancel()
        debounce=viewModelScope.launch { delay(400); viewModelScope.launch { persistSafely() } }
    }
    fun flush() { viewModelScope.launch { persistSafely() } }
    private suspend fun persistSafely() {
        try { writes.withLock { persist() } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error(e) }
    }
    private suspend fun persist() {
        val current=draft ?: return
        val saved=repository.saveDraft(target,SLOT,revision,FieldCodec.json.encodeToString(current),permit())
        revision=saved.revision
        if (draft == current) mutable.value=mutable.value.copy(draftSaved=true)
    }
    fun save() {
        if (!ready()) return
        val current=draft ?: return
        try { current.form.validate(target) } catch (_: Exception) { mutable.value=mutable.value.copy(error=FieldError.INVALID); return }
        action {
            writes.withLock {
                draft=current.copy(submitted=true)
                mutable.value=mutable.value.copy(retry=true)
                persist() // Stable entry identity is durable BEFORE the potentially interrupted write.
                repository.append(current.id,target,current.form,permit())
                revision?.let { repository.deleteDraft(target,SLOT,it,permit()) }
                draft=null; revision=null
                mutable.value=mutable.value.copy(form=null,retry=false,draftSaved=false,entries=repository.entries(target,permit()))
            }
        }
    }
    fun leave(onDone: ()->Unit) {
        if (!ready()) return
        action { writes.withLock { persist() }; onDone() }
    }
    fun discard() {
        if (!ready() || draft?.submitted == true) return
        debounce?.cancel()
        action { writes.withLock {
            revision?.let { repository.deleteDraft(target,SLOT,it,permit()) }
            draft=null; revision=null
            mutable.value=mutable.value.copy(form=null,draftSaved=false)
        } }
    }
    fun addPhoto(bytes: ByteArray, mime: String) {
        if (!ready() || draft?.submitted == true || (draft?.form?.photoIds?.size ?: 6) >= 6) return
        action { writes.withLock {
            val photo=repository.addPhoto(target,bytes,mime,"",permit())
            val current=checkNotNull(draft)
            draft=current.copy(form=current.form.copy(photoIds=current.form.photoIds+photo.id))
            mutable.value=mutable.value.copy(form=draft?.form)
            persist()
        } }
    }
    suspend fun photo(id: String): ByteArray=repository.photo(id,target,permit()).second
    fun photoError() { mutable.value=mutable.value.copy(error=FieldError.INVALID) }
    private fun action(block: suspend ()->Unit) {
        mutable.value=mutable.value.copy(busy=true,error=null)
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error(e) }
            finally { if (access != null && !repository.isCurrent(permit())) revoke() else mutable.value=mutable.value.copy(busy=false) }
        }
    }
    private fun error(e: Exception) {
        if (access != null && !repository.isCurrent(permit())) revoke()
        else mutable.value=mutable.value.copy(error=(e as? FieldException)?.code ?: if (e is IllegalArgumentException) FieldError.INVALID else FieldError.IO)
    }
    companion object { private const val SLOT="journal-v1" }
}
