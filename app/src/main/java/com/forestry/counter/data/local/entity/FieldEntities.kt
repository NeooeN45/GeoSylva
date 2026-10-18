package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(tableName = "field_worksites", indices = [Index(value = ["ownerKey"])])
data class FieldWorksiteEntity(@PrimaryKey val id: String, val ownerKey: String, val payload: String,
    val sha256: String, val revision: Int, val archived: Boolean, val updatedAt: Long)

@Entity(tableName = "field_site_links", primaryKeys = ["siteId", "targetKind", "targetId"],
    foreignKeys = [ForeignKey(entity = FieldWorksiteEntity::class, parentColumns = ["id"], childColumns = ["siteId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["targetKind", "targetId"])])
data class FieldSiteLinkEntity(val siteId: String, val targetKind: String, val targetId: String)

@Entity(tableName = "field_entries", indices = [Index(value = ["ownerKey", "targetKind", "targetId"])])
data class FieldEntryEntity(@PrimaryKey val id: String, val ownerKey: String, val targetKind: String,
    val targetId: String, val payload: String, val sha256: String, val recordedAt: Long, val author: String)

@Entity(tableName = "field_drafts", primaryKeys = ["ownerKey", "targetKind", "targetId", "slot"])
data class FieldDraftEntity(val ownerKey: String, val targetKind: String, val targetId: String,
    val slot: String, val revision: Long, val content: String, val updatedAt: Long)

@Entity(tableName = "field_photos", indices = [Index(value = ["ownerKey", "targetKind", "targetId"])])
data class FieldPhotoEntity(@PrimaryKey val id: String, val ownerKey: String, val targetKind: String,
    val targetId: String, val caption: String, val mime: String, val bytes: Int, val sha256: String)

@Entity(tableName = "field_photo_chunks", primaryKeys = ["photoId", "part"],
    foreignKeys = [ForeignKey(entity = FieldPhotoEntity::class, parentColumns = ["id"], childColumns = ["photoId"], onDelete = ForeignKey.CASCADE)])
data class FieldPhotoChunkEntity(val photoId: String, val part: Int, val bytes: ByteArray)
