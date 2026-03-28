package com.forestry.counter.domain.model

data class Parcelle(
    val id: String,
    val forestId: String?,
    val name: String,
    val surfaceHa: Double?,
    val shape: String?,
    val slopePct: Double?,
    val aspect: String?,
    val access: String?,
    val altitudeM: Double?,
    val commune: String?,
    val objectifType: String?,
    val objectifVal: Double?,
    val tolerancePct: Double?,
    val samplingMode: String?,
    val sampleAreaM2: Double?,
    val targetSpeciesCsv: String?,
    val srid: Int?,
    val remarks: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
