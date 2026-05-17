package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "fertilite_essence_ser",
    indices = [
        Index(name = "index_fertilite_essence_ser", value = ["essenceCode", "codeSer"]),
        Index(name = "index_fertilite_classeStation", value = ["classeStation"])
    ]
)
data class FertiliteEssenceSerEntity(
    @PrimaryKey
    val fertiliteId: String,
    val essenceCode: String,
    val codeSer: String,
    val nomSer: String?,
    val classeStation: Int,
    val hoRef100Ans: Double?,
    val gMaxRef: Double?,
    val accroissementRefM3HaAn: Double?,
    val conditionsRequisesJson: String?,
    val itineraireSylvicoleJson: String?,
    val sourceGuide: String
)
