package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cartographic_layers")
data class CartographicLayerEntity(
    @PrimaryKey val id: String,
    val authorityKey: String,
    val payloadJson: String,
    val payloadSha256: String,
    val version: Long,
    val deletedAt: Long? = null,
)

/** Durable receipts make retries unambiguous, including after process recreation. */
@Entity(tableName = "cartographic_layer_operations")
data class CartographicLayerOperationEntity(
    @PrimaryKey val id: String,
    val layerId: String,
    val authorityKey: String,
    val commandSha256: String,
    val resultJson: String,
)
