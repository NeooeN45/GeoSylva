package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "cartographic_traces", indices = [Index(value = ["authorityKey", "updatedAt"])])
data class CartographicTraceEntity(
    @PrimaryKey val id: String,
    val authorityKey: String,
    val ownerKey: String,
    val scopeKind: String,
    val scopeId: String,
    val saved: Boolean,
    val payloadJson: String,
    val payloadSha256: String,
    val revision: Long,
    val updatedAt: Long,
    val recordedAt: Long?,
    val sourceTraceId: String?,
)
