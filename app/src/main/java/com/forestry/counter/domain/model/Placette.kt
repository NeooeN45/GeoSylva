package com.forestry.counter.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Placette(
    val id: String,
    val parcelleId: String,
    val name: String?,
    val type: String?,
    val rayonM: Double?,
    val surfaceM2: Double?,
    val centerWkt: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    // Metadata spec GeoSylva 3.0 §3.1 — provenance et traçabilité
    override val deletedAt: Long? = null,
    override val auteur: String? = null,
    override val source: String? = null,
    override val version: Int = 1,
    val samplingPlanJson: String? = null,
    val footprintGeoJson: String? = null,
) : Metadatable<Placette> {
    override fun withMetadata(auteur: String, source: String, version: Int): Placette =
        copy(auteur = auteur, source = source, version = version)
}
