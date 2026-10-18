package com.forestry.counter.domain.location

/** A confirmed public candidate; geometry and ownership must never be inferred from its name. */
data class ForestSearchResult(
    val id: String,
    val name: String,
    val department: String?,
    val geometry: ParcelGeometry?,
    val center: ParcelCoordinate?,
    val sourceLabel: String,
    val sourceDataset: String? = null,
)

enum class ParcelMapSource { CADASTRE, ONF }

data class SourceParcel(
    val id: String,
    val label: String,
    val geometry: ParcelGeometry,
    val source: ParcelMapSource,
    val attribution: String,
    val fetchedAt: Long,
    val geometrySha256: String,
    val communeCode: String? = null,
    val communeName: String? = null,
    val cadastralSection: String? = null,
    val cadastralNumber: String? = null,
    val cadastralAreaSquareMetres: Double? = null,
    val sourceAreaSquareMetres: Double? = null,
    val sourceDataset: String = "ign-parcellaire-express-wfs-2026",
    val packReference: String? = null,
) {
    val provenance: String get() = "$sourceDataset:$id; retrieved=$fetchedAt; geometry-sha256=$geometrySha256; ${packReference?.let { "pack=$it; " } ?: ""}$attribution"
}

data class ParcelMapResult(val parcels: List<SourceParcel>, val truncated: Boolean, val rejected: Int, val unavailableSourceIds: Set<String> = emptySet())

interface ParcelMapRepository {
    suspend fun searchForests(query: String): List<ForestSearchResult>
    suspend fun loadForests(bounds: ParcelBounds): List<ForestSearchResult>
    suspend fun loadParcels(bounds: ParcelBounds, source: ParcelMapSource): ParcelMapResult
    fun featureCollection(parcels: List<SourceParcel>): String
    fun isAvailable(source: ParcelMapSource): Boolean
}
