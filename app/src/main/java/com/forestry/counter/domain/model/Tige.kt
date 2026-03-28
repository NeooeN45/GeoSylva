package com.forestry.counter.domain.model

data class Tige(
    val id: String,
    val parcelleId: String,
    val placetteId: String?,
    val essenceCode: String,
    val diamCm: Double,
    val hauteurM: Double?,
    val gpsWkt: String?,
    val precisionM: Double?,
    val altitudeM: Double?,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String?,
    val produit: String?,
    val fCoef: Double?,
    val valueEur: Double?,
    val numero: Int? = null,
    val categorie: String? = null,
    val qualite: Int? = null,
    val defauts: List<String>? = null,
    val photoUri: String? = null,
    val qualiteDetail: String? = null,
    val destination: String? = null // "COUPER", "CONSERVER", null (par défaut: COUPER pour le martelage)
)
