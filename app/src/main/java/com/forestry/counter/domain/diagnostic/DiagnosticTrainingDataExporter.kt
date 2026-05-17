package com.forestry.counter.domain.diagnostic

import android.content.Context
import com.forestry.counter.data.local.entity.StationEnvironnementaleEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Exporte chaque diagnostic généré comme un sample d'entraînement TFLite.
 *
 * Chaque sample = vecteur de features (station + peuplement) + labels (scores essences).
 * Stocké dans : /data/data/<package>/files/training_samples/samples.jsonl
 *
 * Format JSONL — une ligne JSON par sample :
 * {"features": {...}, "labels": {...}, "timestamp": 123456789}
 *
 * Utilisation future (étape 7c) :
 *   1. Extraire samples.jsonl via ADB ou export UI
 *   2. Entraîner Random Forest / GBT avec sklearn / TensorFlow Decision Forests
 *   3. Exporter en TFLite
 *   4. Intégrer dans assets/ pour MLEnhancedScorer (étape 7d)
 */
object DiagnosticTrainingDataExporter {

    private const val SAMPLES_DIR  = "training_samples"
    private const val SAMPLES_FILE = "samples.jsonl"
    private const val MAX_SAMPLES  = 10_000

    private val json = Json { encodeDefaults = true }

    @Serializable
    data class TrainingSample(
        val timestamp: Long,
        val features: StationFeatures,
        val peuplementFeatures: PeuplementFeatures,
        val labels: Map<String, Int>
    )

    @Serializable
    data class StationFeatures(
        val altitudeM: Double?,
        val slopePct: Double?,
        val aspectDeg: Double?,
        val soilPh: Double?,
        val soilRumMm: Double?,
        val soilTexture: String?,
        val tempMoyC: Double?,
        val precipMmAn: Double?,
        val codeSer: String?,
        val isForetAncienne: Boolean
    )

    @Serializable
    data class PeuplementFeatures(
        val nTiges: Int,
        val gM2Ha: Double?,
        val dgCm: Double?,
        val hauteurMoyM: Double?,
        val nbArbresHabitat: Int,
        val essenceDominante: String?
    )

    /**
     * Persiste un sample d'entraînement issu d'un diagnostic.
     * Appelé automatiquement depuis SylviculturalDiagnosticEngine.
     */
    fun export(
        context: Context,
        station: StationEnvironnementaleEntity?,
        peuplement: SylviculturalDiagnosticEngine.PeuplementIndicateurs,
        scores: List<EssenceSuitabilityScorer.SuitabilityScore>
    ) {
        if (scores.isEmpty()) return
        val sample = TrainingSample(
            timestamp = System.currentTimeMillis(),
            features = StationFeatures(
                altitudeM    = station?.altitudeM,
                slopePct     = station?.slopePct,
                aspectDeg    = station?.aspectDeg,
                soilPh       = station?.soilPh,
                soilRumMm    = station?.soilRumMm,
                soilTexture  = station?.soilTexture,
                tempMoyC     = station?.tempMoyC,
                precipMmAn   = station?.precipMmAn,
                codeSer      = station?.codeSer,
                isForetAncienne = station?.isForetAncienne ?: false
            ),
            peuplementFeatures = PeuplementFeatures(
                nTiges           = peuplement.nTiges,
                gM2Ha            = peuplement.gM2Ha,
                dgCm             = peuplement.dgCm,
                hauteurMoyM      = peuplement.hauteurMoyM,
                nbArbresHabitat  = peuplement.nbArbresHabitat,
                essenceDominante = peuplement.essencesDominantes.firstOrNull()
            ),
            labels = scores.associate { it.essenceCode to it.scoreTotal }
        )
        appendSample(context, sample)
    }

    /**
     * Retourne le nombre de samples enregistrés.
     */
    fun sampleCount(context: Context): Int {
        val file = samplesFile(context)
        if (!file.exists()) return 0
        return file.bufferedReader().lines().filter { it.isNotBlank() }.count().toInt()
    }

    /**
     * Retourne le fichier de samples pour export ADB ou partage.
     */
    fun getSamplesFile(context: Context): File = samplesFile(context)

    private fun appendSample(context: Context, sample: TrainingSample) {
        runCatching {
            val file = samplesFile(context)
            if (sampleCount(context) >= MAX_SAMPLES) rotateSamples(file)
            file.appendText(json.encodeToString(sample) + "\n")
        }
    }

    private fun rotateSamples(file: File) {
        val lines = file.readLines().filter { it.isNotBlank() }
        val keep  = lines.takeLast(MAX_SAMPLES / 2)
        file.writeText(keep.joinToString("\n") + "\n")
    }

    private fun samplesFile(context: Context): File {
        val dir = File(context.filesDir, SAMPLES_DIR)
        dir.mkdirs()
        return File(dir, SAMPLES_FILE)
    }
}
