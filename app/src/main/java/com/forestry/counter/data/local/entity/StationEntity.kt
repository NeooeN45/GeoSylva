package com.forestry.counter.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.forestry.counter.domain.model.station.Drainage
import com.forestry.counter.domain.model.station.Exposition
import com.forestry.counter.domain.model.station.Pierrosite
import com.forestry.counter.domain.model.station.PositionTopo
import com.forestry.counter.domain.model.station.StationObservation
import com.forestry.counter.domain.model.station.TestHCl
import com.forestry.counter.domain.model.station.TextureSol
import com.forestry.counter.domain.model.station.TypeHumus

@Entity(tableName = "station_diagnostics")
data class StationEntity(
    @PrimaryKey val id: String,
    val parcelleId: String,
    val observerName: String,
    val observationDate: Long,
    val latitude: Double?,
    val longitude: Double?,
    val altitudeM: Double?,
    val commune: String,
    
    val pentePct: Double?,
    val exposition: String,
    val positionTopo: String,
    val distanceCourseauM: Double?,
    
    val profondeurSolCm: Int?,
    val texture: String,
    val pierrosite: String,
    val hydromorphieProfondeurCm: Int?,
    val humus: String,
    val phEstime: Double?,
    val testHcl: String,
    val drainage: String,
    val rocheMere: String,
    
    val gradientHydrique: Int,
    val gradientTrophique: Int,
    val gradientLumineux: Int,
    val gradientHumique: Int,
    
    val especesIndicatricesJson: String,
    val especesXerophiles: Boolean,
    val especesMesophiles: Boolean,
    val especesHygrophiles: Boolean,
    
    val notes: String
) {
    fun toDomain(): StationObservation {
        return StationObservation(
            id = id,
            parcelleId = parcelleId,
            observerName = observerName,
            observationDate = observationDate,
            latitude = latitude,
            longitude = longitude,
            altitudeM = altitudeM,
            commune = commune,
            pentePct = pentePct,
            exposition = Exposition.entries.find { it.name == exposition } ?: Exposition.INCONNUE,
            positionTopo = PositionTopo.entries.find { it.name == positionTopo } ?: PositionTopo.INCONNUE,
            distanceCourseauM = distanceCourseauM,
            profondeurSolCm = profondeurSolCm,
            texture = TextureSol.entries.find { it.name == texture } ?: TextureSol.INCONNUE,
            pierrosite = Pierrosite.entries.find { it.name == pierrosite } ?: Pierrosite.FAIBLE,
            hydromorphieProfondeurCm = hydromorphieProfondeurCm,
            humus = TypeHumus.entries.find { it.name == humus } ?: TypeHumus.INCONNU,
            phEstime = phEstime,
            testHcl = TestHCl.entries.find { it.name == testHcl } ?: TestHCl.NEGATIF,
            drainage = Drainage.entries.find { it.name == drainage } ?: Drainage.NORMAL,
            rocheMere = rocheMere,
            gradientHydrique = gradientHydrique,
            gradientTrophique = gradientTrophique,
            gradientLumineux = gradientLumineux,
            gradientHumique = gradientHumique,
            especesIndicatrices = parseEspeces(especesIndicatricesJson),
            especesXerophiles = especesXerophiles,
            especesMesophiles = especesMesophiles,
            especesHygrophiles = especesHygrophiles,
            notes = notes
        )
    }

    companion object {
        fun fromDomain(obs: StationObservation): StationEntity {
            return StationEntity(
                id = obs.id,
                parcelleId = obs.parcelleId,
                observerName = obs.observerName,
                observationDate = obs.observationDate,
                latitude = obs.latitude,
                longitude = obs.longitude,
                altitudeM = obs.altitudeM,
                commune = obs.commune,
                pentePct = obs.pentePct,
                exposition = obs.exposition.name,
                positionTopo = obs.positionTopo.name,
                distanceCourseauM = obs.distanceCourseauM,
                profondeurSolCm = obs.profondeurSolCm,
                texture = obs.texture.name,
                pierrosite = obs.pierrosite.name,
                hydromorphieProfondeurCm = obs.hydromorphieProfondeurCm,
                humus = obs.humus.name,
                phEstime = obs.phEstime,
                testHcl = obs.testHcl.name,
                drainage = obs.drainage.name,
                rocheMere = obs.rocheMere,
                gradientHydrique = obs.gradientHydrique,
                gradientTrophique = obs.gradientTrophique,
                gradientLumineux = obs.gradientLumineux,
                gradientHumique = obs.gradientHumique,
                especesIndicatricesJson = serializeEspeces(obs.especesIndicatrices),
                especesXerophiles = obs.especesXerophiles,
                especesMesophiles = obs.especesMesophiles,
                especesHygrophiles = obs.especesHygrophiles,
                notes = obs.notes
            )
        }

        private fun parseEspeces(json: String): List<String> {
            if (json.isBlank()) return emptyList()
            return try {
                val list = mutableListOf<String>()
                val cleaned = json.removeSurrounding("[", "]")
                if (cleaned.isNotBlank()) {
                    cleaned.split(",").forEach {
                        list.add(it.trim().removeSurrounding("\""))
                    }
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        }

        private fun serializeEspeces(list: List<String>): String {
            if (list.isEmpty()) return "[]"
            return list.joinToString(",", prefix = "[", postfix = "]") { "\"$it\"" }
        }
    }
}
