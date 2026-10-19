package com.forestry.counter.data.local.dao

import androidx.room.*
import com.forestry.counter.data.local.entity.*

@Dao interface FieldDao {
    @Query("SELECT * FROM field_worksites WHERE ownerKey = :owner ORDER BY updatedAt DESC")
    suspend fun sites(owner: String): List<FieldWorksiteEntity>
    @Query("SELECT * FROM field_worksites WHERE id = :id") suspend fun site(id: String): FieldWorksiteEntity?
    @Insert suspend fun insertSite(row: FieldWorksiteEntity)
    @Query("UPDATE field_worksites SET payload=:payload, sha256=:hash, revision=revision+1, archived=:archived, updatedAt=:time WHERE id=:id AND ownerKey=:owner AND revision=:expected")
    suspend fun updateSite(id: String, owner: String, expected: Int, payload: String, hash: String, archived: Boolean, time: Long): Int
    @Query("SELECT * FROM field_site_links WHERE siteId=:site") suspend fun links(site: String): List<FieldSiteLinkEntity>
    @Query("SELECT * FROM field_site_links WHERE targetKind=:kind AND targetId=:id") suspend fun sitesFor(kind: String, id: String): List<FieldSiteLinkEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addLink(row: FieldSiteLinkEntity)
    @Query("SELECT * FROM field_entries WHERE ownerKey=:owner AND targetKind=:kind AND targetId=:id ORDER BY recordedAt DESC, id")
    suspend fun entries(owner: String, kind: String, id: String): List<FieldEntryEntity>
    @Query("SELECT * FROM field_entries WHERE id=:id") suspend fun entry(id: String): FieldEntryEntity?
    @Insert suspend fun insertEntry(row: FieldEntryEntity)
    @Query("SELECT * FROM field_drafts WHERE ownerKey=:owner AND targetKind=:kind AND targetId=:id AND slot=:slot")
    suspend fun draft(owner: String, kind: String, id: String, slot: String): FieldDraftEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDraft(row: FieldDraftEntity)
    @Query("DELETE FROM field_drafts WHERE ownerKey=:owner AND targetKind=:kind AND targetId=:id AND slot=:slot AND revision=:revision")
    suspend fun deleteDraft(owner: String, kind: String, id: String, slot: String, revision: Long): Int
    @Insert suspend fun insertPhoto(row: FieldPhotoEntity)
    @Insert suspend fun insertChunks(rows: List<FieldPhotoChunkEntity>)
    @Query("SELECT * FROM field_photos WHERE id=:id") suspend fun photo(id: String): FieldPhotoEntity?
    @Query("SELECT * FROM field_photo_chunks WHERE photoId=:id ORDER BY part") suspend fun chunks(id: String): List<FieldPhotoChunkEntity>
    @Query("DELETE FROM field_photo_chunks") suspend fun deleteChunks()
    @Query("DELETE FROM field_photos") suspend fun deletePhotos()
    @Query("DELETE FROM field_drafts") suspend fun deleteDrafts()
    @Query("DELETE FROM field_receipt_commands") suspend fun deleteReceiptCommands()
    @Query("DELETE FROM field_receipt_credits") suspend fun deleteReceiptCredits()
    @Query("DELETE FROM field_receipt_versions") suspend fun deleteReceiptVersions()
    @Query("DELETE FROM field_receipts") suspend fun deleteReceipts()
    @Query("SELECT * FROM field_photos WHERE ownerKey=:owner AND targetKind=:kind AND targetId=:id ORDER BY id")
    suspend fun photos(owner: String, kind: String, id: String): List<FieldPhotoEntity>
    @Query("DELETE FROM field_entries") suspend fun deleteEntries()
    @Query("DELETE FROM field_site_links") suspend fun deleteLinks()
    @Query("DELETE FROM field_worksites") suspend fun deleteSites()
}
