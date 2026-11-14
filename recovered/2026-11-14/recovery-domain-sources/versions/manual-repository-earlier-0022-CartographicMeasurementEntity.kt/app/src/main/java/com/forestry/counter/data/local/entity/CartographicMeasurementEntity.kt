package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Personal, immutable map geometry, distinct from tree-observation measurements. */
@Entity(
    tableName = "cartographic_measurements",
    indices = [Index(value = ["ownerKey", "scopeKind", "scopeId", "recordedAt"])],
)
data class CartographicMeasurementEntity(
    @PrimaryKey val id: String,
    val ownerKey: String,
    val scopeKind: String,
    val scopeId: String,
    val name: String?,
    val kind: String,
    val geometryJson: String,
    val valueSi: Double,
    val methodId: String,
    val methodVersion: Int,
    val recordedAt: Long,
    val payloadSha256: String,
)
