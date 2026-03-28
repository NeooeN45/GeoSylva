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
import com.forestry.counter.domain.model.station.AbondanceDominance
import com.forestry.counter.domain.model.station.BiodiversiteData
import com.forestry.counter.domain.model.station.DensiteRacines
import com.forestry.counter.domain.model.station.DiagnosticPhoto
import com.forestry.counter.domain.model.station.EtatSanitaire
import com.forestry.counter.domain.model.station.FloraEntry
import com.forestry.counter.domain.model.station.MicroHabitat
import com.forestry.counter.domain.model.station.PeuplementDescription
import com.forestry.counter.domain.model.station.RegenerationNaturelle
import com.forestry.counter.domain.model.station.RegimeSylvicole
import com.forestry.counter.domain.model.station.SoilHorizon
import com.forestry.counter.domain.model.station.StrateVegetale
import com.forestry.counter.domain.model.station.StructureVerticale
import com.forestry.counter.domain.model.station.TypeForet
import androidx.room.ColumnInfo

@Entity(tableName = "station_diagnostics")
data class StationEntity(
    @PrimaryKey val id: String,
    val parcelleId: String,
    val observerName: String,
    val observationDate: Long,
    
    val isDraft: Boolean = true,
    val photosJson: String = "",

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
    
    val notes: String,

    @ColumnInfo(defaultValue = "[]")
    val horizonsJson: String = "[]",

    @ColumnInfo(defaultValue = "[]")
    val floraEntriesJson: String = "[]",

    @ColumnInfo(defaultValue = "")
    val biodiversiteJson: String = "",

    @ColumnInfo(defaultValue = "")
    val peuplementJson: String = ""
) {
    fun toDomain(): StationObservation {
        return StationObservation(
            id = id,
            parcelleId = parcelleId,
            observerName = observerName,
            observationDate = observationDate,
            isDraft = isDraft,
            photos = parsePhotos(photosJson),
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
            notes = notes,
            horizons = parseHorizons(horizonsJson),
            floraEntries = parseFloraEntries(floraEntriesJson),
            biodiversite = parseBiodiversite(biodiversiteJson),
            peuplement = parsePeuplement(peuplementJson)
        )
    }

    companion object {
        fun fromDomain(obs: StationObservation): StationEntity {
            return StationEntity(
                id = obs.id,
                parcelleId = obs.parcelleId,
                observerName = obs.observerName,
                observationDate = obs.observationDate,
                isDraft = obs.isDraft,
                photosJson = serializePhotos(obs.photos),
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
                notes = obs.notes,
                horizonsJson = serializeHorizons(obs.horizons),
                floraEntriesJson = serializeFloraEntries(obs.floraEntries),
                biodiversiteJson = serializeBiodiversite(obs.biodiversite),
                peuplementJson = serializePeuplement(obs.peuplement)
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

        private fun parsePhotos(json: String): List<DiagnosticPhoto> {
            if (json.isBlank() || json == "[]") return emptyList()
            return try {
                val list = mutableListOf<DiagnosticPhoto>()
                val cleaned = json.removeSurrounding("[", "]")
                if (cleaned.isNotBlank()) {
                    // Very basic manual parsing. Assuming no | inside uri/legend/type
                    cleaned.split(";;").forEach { item ->
                        val parts = item.split("|")
                        if (parts.size >= 3) {
                            list.add(DiagnosticPhoto(uri = parts[0], legend = parts[1], type = parts[2]))
                        }
                    }
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        }

        private fun parseHorizons(json: String): List<SoilHorizon> {
            if (json.isBlank() || json == "[]") return emptyList()
            return try {
                val list = mutableListOf<SoilHorizon>()
                val cleaned = json.removeSurrounding("[", "]")
                if (cleaned.isNotBlank()) {
                    cleaned.split(";;H;;").forEach { item ->
                        val parts = item.split("|H|")
                        if (parts.size >= 7) {
                            list.add(SoilHorizon(
                                label              = parts[0],
                                depthFromCm        = parts[1].toIntOrNull() ?: 0,
                                depthToCm          = parts[2].toIntOrNull() ?: 30,
                                texture            = TextureSol.entries.find { it.name == parts[3] } ?: TextureSol.INCONNUE,
                                couleurMunsell     = parts[4],
                                structure          = parts[5],
                                notes              = parts[6],
                                elemsGrossiersPct  = parts.getOrNull(7)?.toIntOrNull() ?: 0,
                                hclTest            = parts.getOrNull(8)?.let { s -> TestHCl.entries.find { it.name == s } } ?: TestHCl.NEGATIF,
                                hydromorphieSigns  = parts.getOrNull(9) == "1",
                                racines            = parts.getOrNull(10)?.let { s -> DensiteRacines.entries.find { it.name == s } } ?: DensiteRacines.MODEREE
                            ))
                        }
                    }
                }
                list
            } catch (_: Exception) { emptyList() }
        }

        private fun serializeHorizons(horizons: List<SoilHorizon>): String {
            if (horizons.isEmpty()) return "[]"
            return horizons.joinToString(";;H;;", prefix = "[", postfix = "]") {
                "${it.label}|H|${it.depthFromCm}|H|${it.depthToCm}|H|${it.texture.name}|H|${it.couleurMunsell}|H|${it.structure}|H|${it.notes}|H|${it.elemsGrossiersPct}|H|${it.hclTest.name}|H|${if (it.hydromorphieSigns) "1" else "0"}|H|${it.racines.name}"
            }
        }

        private fun parseFloraEntries(json: String): List<FloraEntry> {
            if (json.isBlank() || json == "[]") return emptyList()
            return try {
                val list = mutableListOf<FloraEntry>()
                val cleaned = json.removeSurrounding("[", "]")
                if (cleaned.isNotBlank()) {
                    cleaned.split(";;FE;;").forEach { item ->
                        val parts = item.split("|FE|")
                        if (parts.size >= 4) {
                            list.add(FloraEntry(
                                speciesId   = parts[0],
                                displayName = parts[1],
                                strate      = StrateVegetale.entries.find { it.name == parts[2] } ?: StrateVegetale.HERBACEE,
                                abondance   = AbondanceDominance.entries.find { it.name == parts[3] } ?: AbondanceDominance.UN
                            ))
                        }
                    }
                }
                list
            } catch (_: Exception) { emptyList() }
        }

        private fun serializeFloraEntries(entries: List<FloraEntry>): String {
            if (entries.isEmpty()) return "[]"
            return entries.joinToString(";;FE;;", prefix = "[", postfix = "]") {
                "${it.speciesId}|FE|${it.displayName}|FE|${it.strate.name}|FE|${it.abondance.name}"
            }
        }

        private fun parseBiodiversite(raw: String): BiodiversiteData {
            if (raw.isBlank()) return BiodiversiteData()
            return try {
                val parts = raw.split("|BD|")
                BiodiversiteData(
                    boisMortSolVolM3   = parts.getOrNull(0)?.toDoubleOrNull(),
                    boisMortDeboutNb   = parts.getOrNull(1)?.toIntOrNull(),
                    microHabitats      = parts.getOrNull(2)
                        ?.split(",")?.mapNotNull { s -> MicroHabitat.entries.find { it.name == s } }?.toSet() ?: emptySet(),
                    tracesGibier       = parts.getOrNull(3) == "1",
                    notesGibier        = parts.getOrNull(4) ?: "",
                    notesBiodiversite  = parts.getOrNull(5) ?: ""
                )
            } catch (_: Exception) { BiodiversiteData() }
        }

        private fun serializeBiodiversite(b: BiodiversiteData): String {
            val vol = b.boisMortSolVolM3?.toString() ?: ""
            val nb  = b.boisMortDeboutNb?.toString() ?: ""
            val mh  = b.microHabitats.joinToString(",") { it.name }
            val gib = if (b.tracesGibier) "1" else "0"
            return "$vol|BD|$nb|BD|$mh|BD|$gib|BD|${b.notesGibier}|BD|${b.notesBiodiversite}"
        }

        private fun parsePeuplement(raw: String): PeuplementDescription {
            if (raw.isBlank()) return PeuplementDescription()
            return try {
                val parts = raw.split("|PP|")
                PeuplementDescription(
                    typeForet         = parts.getOrNull(0)?.let { s -> TypeForet.entries.find { it.name == s } } ?: TypeForet.INCONNUE,
                    regimeSylvicole   = parts.getOrNull(1)?.let { s -> RegimeSylvicole.entries.find { it.name == s } } ?: RegimeSylvicole.INCONNUE,
                    ageEstimeAns      = parts.getOrNull(2)?.toIntOrNull(),
                    structureVerticale = parts.getOrNull(3)?.let { s -> StructureVerticale.entries.find { it.name == s } } ?: StructureVerticale.INCONNUE,
                    etatSanitaire     = parts.getOrNull(4)?.let { s -> EtatSanitaire.entries.find { it.name == s } } ?: EtatSanitaire.BON,
                    regeneration      = parts.getOrNull(5)?.let { s -> RegenerationNaturelle.entries.find { it.name == s } } ?: RegenerationNaturelle.INCONNUE,
                    notesPeuplement   = parts.getOrNull(6) ?: ""
                )
            } catch (_: Exception) { PeuplementDescription() }
        }

        private fun serializePeuplement(p: PeuplementDescription): String {
            val age = p.ageEstimeAns?.toString() ?: ""
            return "${p.typeForet.name}|PP|${p.regimeSylvicole.name}|PP|$age|PP|${p.structureVerticale.name}|PP|${p.etatSanitaire.name}|PP|${p.regeneration.name}|PP|${p.notesPeuplement}"
        }

        private fun serializePhotos(photos: List<DiagnosticPhoto>): String {
            if (photos.isEmpty()) return "[]"
            return photos.joinToString(";;", prefix = "[", postfix = "]") { 
                "${it.uri}|${it.legend}|${it.type}"
            }
        }
    }
}
