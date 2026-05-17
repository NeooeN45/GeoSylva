package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "projections_climatiques_ser",
    indices = [
        Index(name = "index_proj_ser_scenario", value = ["codeSer", "scenario", "horizon"])
    ]
)
data class ProjectionClimatiqueSerEntity(
    @PrimaryKey
    val projId: String,
    val codeSer: String,
    val scenario: String,
    val horizon: Int,
    val deltaTMoyC: Double,
    val deltaTEteC: Double,
    val deltaPMmAn: Double,
    val deltaPEteMm: Double,
    val nbJoursChaudsSup: Int?,
    val speiDelta: Double?,
    val sourceGiec: String
)
