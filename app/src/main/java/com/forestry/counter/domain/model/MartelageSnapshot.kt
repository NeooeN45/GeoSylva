package com.forestry.counter.domain.model

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.encodeToJsonElement

/** Local lifecycle only. An unknown legacy type is never treated as a managed campaign. */
object MartelageStates {
    const val RUNNING = "RUNNING"
    const val PAUSED = "PAUSED"
    const val CLOSED = "CLOSED"
    const val TYPE = "MARTELAGE"
    val ACTIVE = setOf(RUNNING, PAUSED)
}

data class MartelageClockReading(val wallTimeMs: Long, val elapsedRealtimeMs: Long, val bootCount: Int?)

fun interface MartelageClock {
    fun read(): MartelageClockReading
}

/** Never extrapolate across a reboot or a missing monotonic clock identity. */
fun InventaireSession.durationAt(reading: MartelageClockReading): Long {
    if (martelageState != MartelageStates.RUNNING) return activeDurationMs.coerceAtLeast(0L)
    val start = segmentStartedElapsedMs ?: return activeDurationMs.coerceAtLeast(0L)
    if (segmentBootCount == null || segmentBootCount != reading.bootCount || reading.elapsedRealtimeMs < start) {
        return activeDurationMs.coerceAtLeast(0L)
    }
    val delta = reading.elapsedRealtimeMs - start
    return activeDurationMs.coerceAtLeast(0L).let { base ->
        if (delta > Long.MAX_VALUE - base) Long.MAX_VALUE else base + delta
    }
}

fun InventaireSession.needsClockRecovery(reading: MartelageClockReading): Boolean =
    martelageState == MartelageStates.RUNNING &&
        (segmentBootCount == null || segmentBootCount != reading.bootCount ||
            segmentStartedElapsedMs == null || reading.elapsedRealtimeMs < segmentStartedElapsedMs)

/** Calculations are supplied by the actual synthesis engine; null means no result was sealed. */
@Serializable
data class MartelageCalculationSnapshotV1(
    val generatedAt: Long,
    val engineVersion: String,
    val parameters: JsonObject,
    val results: JsonObject,
    val sourceFingerprint: String,
)

/** Complete, immutable-at-rest observation snapshot. It is not a GSIE inference or a PDF. */
@Serializable
data class MartelageSnapshotV1(
    val schemaVersion: Int = 1,
    val sessionId: String,
    val parcelleId: String,
    val campaignId: String,
    val revisionNumber: Int,
    val previousSessionId: String?,
    val startedAt: Long,
    val closedAt: Long,
    val activeDurationMs: Long,
    val auteur: String?,
    val parcelle: Parcelle,
    val placettes: List<Placette>,
    val tiges: List<Tige>,
    val calculation: MartelageCalculationSnapshotV1? = null,
    /** V1 historical parcel scope stays null; V2 requires one explicit plot. */
    val placetteId: String? = null,
    val traceJson: String? = null,
)

@Serializable
private data class MartelageInputs(val parcelle: Parcelle, val placettes: List<Placette>, val tiges: List<Tige>)

object MartelageSnapshotCodec {
    private val json = Json { encodeDefaults = true; explicitNulls = true }
    fun encode(snapshot: MartelageSnapshotV1): String = json.encodeToString(snapshot)
    fun decode(value: String): MartelageSnapshotV1 = json.decodeFromString<MartelageSnapshotV1>(value).also {
        require(it.schemaVersion in 1..2 && it.revisionNumber >= 1 && it.activeDurationMs >= 0L) {
            "MARTELAGE_SNAPSHOT_INVALID"
        }
        require(it.parcelle.id == it.parcelleId && it.tiges.all { tree ->
            tree.parcelleId == it.parcelleId && tree.sessionId == it.sessionId
        }) { "MARTELAGE_SNAPSHOT_PARENT_MISMATCH" }
        require(it.placettes.all { plot -> plot.parcelleId == it.parcelleId }) {
            "MARTELAGE_SNAPSHOT_PARENT_MISMATCH"
        }
        if (it.schemaVersion == 1) {
            require(it.placetteId == null && it.traceJson == null) { "MARTELAGE_SNAPSHOT_LEGACY_SCOPE_MISMATCH" }
        } else {
            require(!it.placetteId.isNullOrBlank() && it.placettes.size == 1 &&
                it.placettes.single().id == it.placetteId && it.tiges.all { tree -> tree.placetteId == it.placetteId }) {
                "MARTELAGE_SNAPSHOT_PLOT_MISMATCH"
            }
        }
    }
}

object MartelageSnapshotFingerprint {
    private val json = Json { encodeDefaults = true; explicitNulls = true }
    /** Projection du schéma 2 : une extension du modèle ne réinterprète pas les résultats clôturés. */
    fun compute(parcelle: Parcelle, placettes: List<Placette>, tiges: List<Tige>): String =
        sha256(frozenInputs(parcelle, placettes, tiges, plotV2Fields).toString())

    /** Le schéma 1 précède la géométrie d'échantillonnage ; ses octets historiques restent la référence. */
    fun computeLegacyV1(parcelle: Parcelle, placettes: List<Placette>, tiges: List<Tige>): String =
        sha256(frozenInputs(parcelle, placettes, tiges, plotV1Fields).toString())

    // Ces champs ordonnés figent le contrat V1/V2, indépendamment des futurs modèles du domaine.
    // Une métadonnée scientifique nécessite un nouveau snapshot versionné, sans défaut ajouté au legacy.
    private val parcelFields = listOf(
        "id", "forestId", "foretId", "name", "surfaceHa", "shape",
        "slopePct", "aspect", "access", "altitudeM", "objectifType", "objectifVal",
        "tolerancePct", "samplingMode", "sampleAreaM2", "targetSpeciesCsv", "srid", "remarks",
        "codeInseeCommune", "nomCommune", "sectionCadastrale", "numeroCadastral", "contenanceCadastraleHa", "geometrieIgnWkt",
        "natureCadastraleCode", "localisationMode", "codeSer", "nomSer", "createdAt", "updatedAt",
        "deletedAt", "auteur", "source", "version",
    )

    private val plotV1Fields = listOf(
        "id", "parcelleId", "name", "type", "rayonM", "surfaceM2",
        "centerWkt", "createdAt", "updatedAt", "deletedAt", "auteur", "source",
        "version",
    )
    private val plotV2Fields = plotV1Fields + listOf("samplingPlanJson", "footprintGeoJson")

    private val treeFields = listOf(
        "id", "parcelleId", "placetteId", "essenceCode", "diamCm", "hauteurM",
        "gpsWkt", "precisionM", "altitudeM", "timestamp", "note", "produit",
        "fCoef", "valueEur", "numero", "categorie", "qualite", "defauts",
        "photoUri", "qualiteDetail", "classeKraft", "etatSanitaire", "vigueur", "origine",
        "typeCoupe", "biomasseFusTonnes", "carboneFusTonnes", "coefficientElancement", "houppierM", "houppierPct",
        "isTigeHabitat", "sessionId", "originTigeId", "deletedAt", "auteur", "source",
        "version",
    )

    private fun frozenInputs(
        parcelle: Parcelle,
        placettes: List<Placette>,
        tiges: List<Tige>,
        plotFields: List<String>,
    ): JsonObject {
        val inputs = json.encodeToJsonElement(MartelageInputs(
            parcelle, placettes.sortedBy { it.id }, tiges.sortedBy { it.id },
        )).jsonObject
        return JsonObject(linkedMapOf(
            "parcelle" to frozenFields(inputs.getValue("parcelle").jsonObject, parcelFields),
            "placettes" to JsonArray(inputs.getValue("placettes").jsonArray.map {
                frozenFields(it.jsonObject, plotFields)
            }),
            "tiges" to JsonArray(inputs.getValue("tiges").jsonArray.map {
                frozenFields(it.jsonObject, treeFields)
            }),
        ))
    }

    private fun frozenFields(value: JsonObject, fields: List<String>): JsonObject =
        JsonObject(fields.associateWith { value.getValue(it) })

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
