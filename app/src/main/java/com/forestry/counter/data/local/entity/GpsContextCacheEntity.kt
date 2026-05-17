package com.forestry.counter.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "gps_context_cache",
    primaryKeys = ["latKey", "lonKey"]
)
data class GpsContextCacheEntity(
    val latKey: Double,
    val lonKey: Double,
    val regionCode: String = "",
    val deptCode: String = "",
    val altitudeApproxM: Double = 0.0,
    val topoHint: String = "",
    val zoneHumideProb: Double = 0.0,
    val packIdActive: String = "",
    val computedAt: Long = System.currentTimeMillis()
)
