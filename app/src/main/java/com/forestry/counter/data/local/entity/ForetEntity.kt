package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "forets",
    indices = [
        Index(name = "index_forets_proprietaireNom", value = ["proprietaireNom"])
    ]
)
data class ForetEntity(
    @PrimaryKey
    val foretId: String,
    val nom: String,
    val proprietaireNom: String,
    val proprietaireEmail: String?,
    val gestionnaireNom: String?,
    val typeForet: String?,
    val objectifGestion: String?,
    val psgNumero: String?,
    val psgDateExpiration: Long?,
    val departement: String?,
    val remarques: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
