package com.forestry.counter.domain.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.*

enum class SensorCapability {
    NONE,    // Pas d'accéléromètre → saisie manuelle uniquement
    BASIC,   // Accéléromètre seul (précision ±2°)
    MEDIUM,  // Accéléromètre + gyroscope (±1°)
    HIGH     // Vecteur de rotation (fusion capteurs : ±0.5°)
}

data class AngleMeasurement(
    val pitchDeg: Float,
    val avgPitchDeg: Float,      // Moyenne lissée des dernières lectures stables
    val stabilityScore: Float   // 0.0 (instable) → 1.0 (stable)
)

data class TreeHeightResult(
    val heightM: Double,
    val precisionM: Double,
    val capability: SensorCapability
)

/**
 * Résultat de la méthode 2-stations.
 * @param combinedHeightM   Moyenne pondérée par inverse-variance des deux mesures
 * @param precisionM        Précision combinée (propagation d'erreur)
 * @param height1M          Hauteur issue de la station 1
 * @param height2M          Hauteur issue de la station 2
 * @param agreementPct      Taux de concordance 0–100 (100 = identiques)
 * @param confidenceLowM    Borne basse de l'intervalle de confiance à 95 %
 * @param confidenceHighM   Borne haute
 */
data class TwoStationResult(
    val combinedHeightM: Double,
    val precisionM: Double,
    val height1M: Double,
    val height2M: Double,
    val agreementPct: Int,
    val confidenceLowM: Double,
    val confidenceHighM: Double
)

object TreeHeightMeasureTool {

    /** Détecte le meilleur capteur disponible sur l'appareil. */
    fun detectCapability(context: Context): SensorCapability {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        return when {
            sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null -> SensorCapability.HIGH
            sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null &&
                sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null -> SensorCapability.MEDIUM
            sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null -> SensorCapability.BASIC
            else -> SensorCapability.NONE
        }
    }

    /**
     * Flow de mesures d'angle en temps réel.
     * pitchDeg > 0 : téléphone incliné vers le haut (cime)
     * pitchDeg < 0 : téléphone incliné vers le bas (base)
     */
    fun pitchFlow(context: Context, capability: SensorCapability): Flow<AngleMeasurement> =
        callbackFlow {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val buffer = ArrayDeque<Float>(10)

            fun computeStability(): Float {
                if (buffer.size < 4) return 0f
                val range = buffer.max() - buffer.min()
                return (1f - (range / 8f).coerceIn(0f, 1f))
            }

            fun computeAvg(): Float {
                if (buffer.isEmpty()) return 0f
                return buffer.average().toFloat()
            }

            when (capability) {
                SensorCapability.HIGH -> {
                    val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                        ?: run { close(); return@callbackFlow }
                    val rotMatrix = FloatArray(9)
                    val listener = object : SensorEventListener {
                        override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
                        override fun onSensorChanged(event: SensorEvent) {
                            SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
                            // Clinometer elevation angle: how much the camera looks
                            // above (+) or below (−) horizontal.
                            // Camera direction in device frame = (0,0,−1); in world frame
                            // its Z-component = −R[8].  World-Z points up (opposite gravity).
                            val elevRad = asin((-rotMatrix[8]).toDouble().coerceIn(-1.0, 1.0))
                            val pitchDeg = Math.toDegrees(elevRad).toFloat()
                            buffer.addLast(pitchDeg)
                            if (buffer.size > 8) buffer.removeFirst()
                            trySend(AngleMeasurement(pitchDeg, computeAvg(), computeStability()))
                        }
                    }
                    sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
                    awaitClose { sm.unregisterListener(listener) }
                }

                SensorCapability.MEDIUM, SensorCapability.BASIC -> {
                    val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                        ?: run { close(); return@callbackFlow }
                    val listener = object : SensorEventListener {
                        override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
                        override fun onSensorChanged(event: SensorEvent) {
                            val ay = event.values[1].toDouble()
                            val az = event.values[2].toDouble()
                            // Clinometer angle in portrait mode:
                            // atan2(-az, ay) = 0° when upright (horizontal aim),
                            // positive when looking up, negative when looking down.
                            val pitchRad = atan2(-az, ay)
                            val pitchDeg = Math.toDegrees(pitchRad).toFloat()
                            buffer.addLast(pitchDeg)
                            if (buffer.size > 8) buffer.removeFirst()
                            trySend(AngleMeasurement(pitchDeg, computeAvg(), computeStability()))
                        }
                    }
                    sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
                    awaitClose { sm.unregisterListener(listener) }
                }

                SensorCapability.NONE -> close()
            }
        }

    /**
     * Flow d'altitude barométrique en temps réel (m).
     * Utilise TYPE_PRESSURE + SensorManager.getAltitude().
     * Retourne null si le capteur n'est pas disponible.
     */
    fun barometerAltitudeFlow(context: Context): Flow<Float>? {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_PRESSURE) ?: return null
        val buffer = ArrayDeque<Float>(5)
        return callbackFlow {
            val listener = object : SensorEventListener {
                override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
                override fun onSensorChanged(event: SensorEvent) {
                    val hPa = event.values[0]
                    val alt = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, hPa)
                    buffer.addLast(alt)
                    if (buffer.size > 5) buffer.removeFirst()
                    trySend(buffer.average().toFloat())
                }
            }
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            awaitClose { sm.unregisterListener(listener) }
        }
    }

    /**
     * Calcule la hauteur d'un arbre par la méthode des tangentes.
     *
     * @param distanceM      Distance horizontale au pied de l'arbre (m)
     * @param angleTopDeg    Angle d'élévation vers la cime (degrés, positif = vers le haut)
     * @param angleBaseDeg   Angle vers la base (degrés, négatif = vers le bas, 0 = terrain plat)
     * @param phoneHeightM   Hauteur du téléphone par rapport au sol (m)
     * @param capability     Qualité du capteur (pour l'estimation de précision)
     *
     * Formule : H = hauteur_téléphone + D×tan(α_cime) − D×tan(α_base)
     * (en terrain plat ou légèrement incliné)
     */
    /**
     * Calcule la hauteur combinée par la méthode des 2 stations.
     * Pondération inverse-variance : la mesure la plus précise contribue davantage.
     */
    fun calculateHeight2Stations(
        distance1M: Double,
        angleTop1Deg: Double,
        angleBase1Deg: Double = 0.0,
        distance2M: Double,
        angleTop2Deg: Double,
        angleBase2Deg: Double = 0.0,
        phoneHeightM: Double = 1.5,
        capability: SensorCapability = SensorCapability.BASIC
    ): TwoStationResult {
        val r1 = calculateHeight(distance1M, angleTop1Deg, angleBase1Deg, phoneHeightM, capability)
        val r2 = calculateHeight(distance2M, angleTop2Deg, angleBase2Deg, phoneHeightM, capability)

        // Pondération inverse-variance (w = 1/σ²)
        val v1 = r1.precisionM * r1.precisionM
        val v2 = r2.precisionM * r2.precisionM
        val w1 = if (v1 > 0) 1.0 / v1 else 1.0
        val w2 = if (v2 > 0) 1.0 / v2 else 1.0
        val combined = (r1.heightM * w1 + r2.heightM * w2) / (w1 + w2)

        // Précision combinée
        val precisionCombined = sqrt(1.0 / (w1 + w2)).coerceAtLeast(0.05)

        // Concordance : 100% si identiques, 0% si > 20% d'écart
        val maxH = maxOf(r1.heightM, r2.heightM)
        val diffRatio = if (maxH > 0) abs(r1.heightM - r2.heightM) / maxH else 0.0
        val agreementPct = ((1.0 - (diffRatio / 0.20).coerceIn(0.0, 1.0)) * 100).toInt()

        // Intervalle de confiance à 95 % (± 1,96σ)
        val margin = 1.96 * precisionCombined
        return TwoStationResult(
            combinedHeightM   = combined,
            precisionM        = precisionCombined,
            height1M          = r1.heightM,
            height2M          = r2.heightM,
            agreementPct      = agreementPct,
            confidenceLowM    = (combined - margin).coerceAtLeast(0.5),
            confidenceHighM   = combined + margin
        )
    }

    fun calculateHeight(
        distanceM: Double,
        angleTopDeg: Double,
        angleBaseDeg: Double = 0.0,
        phoneHeightM: Double = 1.5,
        capability: SensorCapability = SensorCapability.BASIC
    ): TreeHeightResult {
        val topRad  = Math.toRadians(angleTopDeg)
        val baseRad = Math.toRadians(angleBaseDeg)

        val heightAbovePhone = distanceM * tan(topRad)
        val heightBelowPhone = if (angleBaseDeg < -0.5) distanceM * abs(tan(baseRad)) else 0.0
        val total = (phoneHeightM + heightAbovePhone + heightBelowPhone).coerceAtLeast(0.5)

        // Estimation d'erreur propagée depuis l'erreur angulaire typique
        val angleErrorDeg = when (capability) {
            SensorCapability.HIGH   -> 0.5
            SensorCapability.MEDIUM -> 1.0
            SensorCapability.BASIC  -> 2.0
            SensorCapability.NONE   -> 3.0
        }
        val angleErrorRad = Math.toRadians(angleErrorDeg)
        val precisionM = distanceM * abs(tan(topRad + angleErrorRad) - tan(topRad)) + 0.1

        return TreeHeightResult(total, precisionM, capability)
    }
}
