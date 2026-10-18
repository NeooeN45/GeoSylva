package com.forestry.counter.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Parcelle(
    val id: String,
    val forestId: String?,
    val foretId: String? = null,
    val name: String,
    val surfaceHa: Double?,
    val shape: String?,
    val slopePct: Double?,
    val aspect: String?,
    val access: String?,
    val altitudeM: Double?,
    val objectifType: String?,
    val objectifVal: Double?,
    val tolerancePct: Double?,
    val samplingMode: String?,
    val sampleAreaM2: Double?,
    val targetSpeciesCsv: String?,
    val srid: Int?,
    val remarks: String?,
    val codeInseeCommune: String? = null,
    val nomCommune: String? = null,
    val sectionCadastrale: String? = null,
    val numeroCadastral: String? = null,
    val contenanceCadastraleHa: Double? = null,
    val geometrieIgnWkt: String? = null,
    val natureCadastraleCode: String? = null,
    val localisationMode: String? = null,
    val codeSer: String? = null,
    val nomSer: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    // Metadata spec GeoSylva 3.0 §3.1 — provenance et traçabilité
    override val deletedAt: Long? = null,
    override val auteur: String? = null,
    override val source: String? = null,
    override val version: Int = 1
) : Metadatable<Parcelle> {
    override fun withMetadata(auteur: String, source: String, version: Int): Parcelle =
        copy(auteur = auteur, source = source, version = version)
}
