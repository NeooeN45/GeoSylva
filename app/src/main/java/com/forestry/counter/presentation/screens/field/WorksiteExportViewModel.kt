package com.forestry.counter.presentation.screens.field

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forestry.counter.domain.export.GeoSylvaFieldExporter
import com.forestry.counter.domain.field.FieldTarget
import com.forestry.counter.domain.field.FieldTargetKind
import com.forestry.counter.domain.repository.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class WorksiteExportState(
    val title: String = "", val busy: Boolean = true, val prepared: Boolean = false,
    val fileName: String = "", val byteCount: Int = 0, val objects: Int = 0,
    val receipts: Int = 0, val entries: Int = 0, val saved: Boolean = false,
    val accessLost: Boolean = false, val error: Boolean = false,
    val cleanupFailed: Boolean = false,
)

class WorksiteExportViewModel(private val repository: FieldRepository, private val siteId: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val calculationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val mutable = MutableStateFlow(WorksiteExportState())
    val state = mutable.asStateFlow()
    private var access: FieldAccess? = null
    private var archive: ByteArray? = null

    init {
        viewModelScope.launch {
            try {
                access = repository.captureAccess()
                val title = repository.title(FieldTarget(FieldTargetKind.WORKSITE, siteId), checkNotNull(access))
                if (current()) mutable.value = mutable.value.copy(title = title, busy = false) else revoke()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = mutable.value.copy(busy = false, error = true) }
        }
        viewModelScope.launch { repository.accessChanges().collect { if (access != null && !current()) revoke() } }
    }
    private fun current() = access?.let(repository::isCurrent) == true
    private fun clearBytes() { archive?.fill(0); archive = null }
    private fun revoke() { clearBytes(); mutable.value = WorksiteExportState(busy = false, accessLost = true, cleanupFailed = mutable.value.cleanupFailed) }
    fun canSave(): Boolean {
        if (!current()) { revoke(); return false }
        return !mutable.value.busy && archive != null
    }
    fun prepare() {
        if (mutable.value.busy || mutable.value.accessLost || access == null) return
        clearBytes()
        mutable.value = mutable.value.copy(busy = true, error = false, prepared = false, saved = false)
        viewModelScope.launch {
            var prepared: ByteArray? = null
            try {
                val snapshot = repository.exportSnapshot(siteId, checkNotNull(access))
                prepared = withContext(calculationDispatcher) {
                    val buffer = ByteArrayOutputStream()
                    GeoSylvaFieldExporter.write(snapshot, buffer)
                    val bytes = buffer.toByteArray()
                    try { GeoSylvaFieldExporter.verify(ByteArrayInputStream(bytes)); bytes }
                    catch (e: Exception) { bytes.fill(0); throw e }
                }
                if (!current()) { revoke(); return@launch }
                archive = prepared
                mutable.value = mutable.value.copy(prepared = true, fileName = "geosylva-chantier-${snapshot.exportId}.zip",
                    byteCount = checkNotNull(prepared).size, objects = snapshot.objects.size, receipts = snapshot.receipts.size, entries = snapshot.entries.size)
                prepared = null // Ownership transferred; revoke/onCleared wipes the private in-memory archive.
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = mutable.value.copy(error = true) }
            finally {
                prepared?.fill(0)
                if (!current()) revoke() else mutable.value = mutable.value.copy(busy = false)
            }
        }
    }

    /** The SAF writer receives an epoch predicate, checked during every output chunk. */
    fun save(writer: suspend (ByteArray, () -> Boolean) -> Unit, cleanup: suspend () -> Boolean) {
        if (!canSave()) {
            viewModelScope.launch {
                val cleaned = withContext(ioDispatcher) { runCatching { cleanup() }.getOrDefault(false) }
                if (!cleaned) mutable.value = mutable.value.copy(cleanupFailed = true)
            }
            return
        }
        val bytes = checkNotNull(archive)
        mutable.value = mutable.value.copy(busy = true, saved = false, error = false)
        viewModelScope.launch {
            var completed = false
            try {
                withContext(ioDispatcher) {
                    check(current())
                    writer(bytes, ::current)
                    check(current())
                }
                check(current()) // The identity may have changed while returning from the IO dispatcher.
                completed = true
                mutable.value = mutable.value.copy(saved = true)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = mutable.value.copy(error = true) }
            finally {
                if (!completed || !current()) {
                    val cleaned = withContext(NonCancellable + ioDispatcher) { runCatching { cleanup() }.getOrDefault(false) }
                    if (!cleaned) mutable.value = mutable.value.copy(cleanupFailed = true)
                }
                if (!current()) revoke() else mutable.value = mutable.value.copy(busy = false)
            }
        }
    }
    override fun onCleared() { clearBytes(); super.onCleared() }
}
