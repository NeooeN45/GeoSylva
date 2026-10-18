package com.forestry.counter.domain.repository

import com.forestry.counter.domain.model.WoodDepotDraft
import com.forestry.counter.domain.model.WoodDepotRecord
import kotlinx.coroutines.flow.Flow

/** Opaque initiating identity generation, without tokens or organisation membership. */
interface WoodDepotAccess {
    val accountId: String?
}

/** Local-only deposits. No implicit guest adoption, server transport, stock or payment. */
interface WoodDepotRepository {
    fun captureAccess(): WoodDepotAccess
    fun isAccessCurrent(access: WoodDepotAccess): Boolean
    fun observeDepots(): Flow<List<WoodDepotRecord>>
    suspend fun getDepot(id: String, access: WoodDepotAccess): WoodDepotRecord?
    /** Same UUID and original creation payload is a no-op; a conflicting payload is refused. */
    suspend fun createDepot(id: String, draft: WoodDepotDraft, access: WoodDepotAccess): WoodDepotRecord
    suspend fun updateDepot(id: String, expectedRevision: Int, draft: WoodDepotDraft, access: WoodDepotAccess): WoodDepotRecord
    /** Soft deletion retains the dossier and prevents reuse of its UUID. */
    suspend fun deleteDepot(id: String, expectedRevision: Int, access: WoodDepotAccess): WoodDepotRecord
}
