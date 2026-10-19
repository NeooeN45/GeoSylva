package com.forestry.counter.domain.repository

import com.forestry.counter.domain.field.*
import kotlinx.coroutines.flow.Flow

interface FieldAccess
interface FieldRepository {
    suspend fun receipts(site: String, access: FieldAccess): List<SavedReceipt> = throw FieldException(FieldError.UNAVAILABLE)
    suspend fun receipt(id: String, access: FieldAccess): SavedReceipt = throw FieldException(FieldError.UNAVAILABLE)
    suspend fun receiptHistory(id: String, access: FieldAccess): List<SavedReceipt> = throw FieldException(FieldError.UNAVAILABLE)
    suspend fun receiptWoodChoices(site: String, access: FieldAccess): List<ReceiptWoodChoice> = throw FieldException(FieldError.UNAVAILABLE)
    suspend fun saveReceipt(id: String, site: String, form: ReceiptForm, expectedRevision: Int, ready: Boolean, access: FieldAccess): SavedReceipt = throw FieldException(FieldError.UNAVAILABLE)
    suspend fun validateReceipt(commandId: String, id: String, expectedRevision: Int, access: FieldAccess): SavedReceipt = throw FieldException(FieldError.UNAVAILABLE)
    suspend fun cancelReceipt(commandId: String, id: String, expectedRevision: Int, reason: String, access: FieldAccess): SavedReceipt = throw FieldException(FieldError.UNAVAILABLE)
    suspend fun exportSnapshot(site: String, access: FieldAccess): WorksiteExportSnapshot = throw FieldException(FieldError.UNAVAILABLE)
    suspend fun captureAccess(): FieldAccess
    fun isCurrent(access: FieldAccess): Boolean
    fun accessChanges(): Flow<Unit>
    suspend fun sites(access: FieldAccess): List<SavedWorksite>
    suspend fun saveSite(id: String, form: WorksiteForm, revision: Int, archived: Boolean, access: FieldAccess): SavedWorksite
    suspend fun parcelChoices(access: FieldAccess): List<WorksiteParcel>
    suspend fun woodChoices(access: FieldAccess): List<FieldTargetChoice>
    suspend fun link(site: String, target: FieldTarget, access: FieldAccess)
    suspend fun linked(site: String, access: FieldAccess): List<FieldTargetChoice>
    suspend fun title(target: FieldTarget, access: FieldAccess): String
    suspend fun writable(target: FieldTarget, access: FieldAccess): Boolean
    suspend fun measurements(target: FieldTarget, access: FieldAccess): WoodObjectDetails?
    suspend fun protectedEntries(target: FieldTarget, access: FieldAccess): Set<String> = emptySet()
    suspend fun entries(target: FieldTarget, access: FieldAccess): List<FieldEntry>
    suspend fun append(id: String, target: FieldTarget, form: FieldEntryForm, access: FieldAccess): FieldEntry
    suspend fun loadDraft(target: FieldTarget, slot: String, access: FieldAccess): FieldDraft?
    suspend fun saveDraft(target: FieldTarget, slot: String, expected: Long?, content: String, access: FieldAccess): FieldDraft
    suspend fun deleteDraft(target: FieldTarget, slot: String, expected: Long, access: FieldAccess)
    suspend fun addPhoto(target: FieldTarget, bytes: ByteArray, mime: String, caption: String, access: FieldAccess): FieldPhoto
    suspend fun photo(id: String, target: FieldTarget, access: FieldAccess): Pair<FieldPhoto, ByteArray>
}
enum class FieldError { ACCESS_CHANGED, UNAVAILABLE, CONFLICT, INVALID, CORRUPT, IO, UNKNOWN_RESULT, DEPENDENCIES }
class FieldException(val code: FieldError) : IllegalStateException(code.name)
