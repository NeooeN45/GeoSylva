package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Control metadata only; actual map resources belong to the shared native TileStore. */
@Entity(tableName = "offline_map_packs", indices = [Index(value = ["authorityKey", "createdAt"]), Index(value = ["nativeRegionId"], unique = true)])
data class OfflineMapPackEntity(
    @PrimaryKey val id: String,
    val authorityKey: String,
    val ownerKey: String,
    val scopeKind: String,
    val scopeId: String,
    val name: String,
    val geometryJson: String,
    val minZoom: Int,
    val maxZoom: Int,
    val pixelRatio: Float,
    val styleUri: String,
    val extraTilesetsJson: String,
    val datasetsJson: String,
    val territoriesJson: String,
    val definitionHash: String,
    val requestSha256: String,
    val nativeRegionId: String,
    val localVersion: String,
    val createdAt: Long,
    val status: String,
    val stage: String? = null,
    val completedResources: Long = 0,
    val requiredResources: Long = 0,
    val resourceBytes: Long = 0,
    val validatedAt: Long? = null,
    val expiresAt: Long? = null,
    val issue: String? = null,
)
