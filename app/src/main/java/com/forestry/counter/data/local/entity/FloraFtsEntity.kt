package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4

@Fts4
@Entity(tableName = "flora_fts")
data class FloraFtsEntity(
    val speciesId: String = "",
    val nomFrancais: String = "",
    val nomScientifique: String = "",
    val vernaculaires: String = "",
    val synonymes: String = "",
    val typeMilieu: String = "",
    val strate: String = ""
)
