package com.forestry.counter.domain.calculation.cubage

import kotlin.math.PI
import kotlin.math.sqrt

/**
 * Méthodes géométriques exactes de cubage des grumes/billons (docs/CUBAGE_OFFLINE_FIRST.md,
 * "noyau de calcul local", Phase 1). Vérifiées à 1e-7 contre les vecteurs de référence
 * indépendants de docs/recherche/02_marche_prix/vecteurs_tests_cubage.json — voir
 * GeoSylva/app/src/test/.../domain/calculation/cubage/.
 *
 * Toutes calculent une section circulaire S = π × (D/2)² à partir d'un diamètre en mètres.
 */

private const val SOURCE_CITATION = "Formule géométrique documentée dans GEOSYLVA-RECH-CUBAGE-006"

private fun sectionArea(diameterM: Double): Double = PI * (diameterM / 2.0) * (diameterM / 2.0)

/** Volume cylindrique = L × S(diamètre milieu). Exact pour un cylindre, approximation pour une grume réelle. */
object HuberMethod : CubageMethod<LogSegmentMeasurement> {
    override val id: String = "HUBER"
    override val version: String = "1.0.0"
    override val qualification: MethodQualification = MethodQualification.QUALIFIED
    override val sourceCitation: String = SOURCE_CITATION

    override fun compute(input: LogSegmentMeasurement): CubageResult {
        if (input.lengthM <= 0.0) return CubageResult.Blocked("Longueur manquante ou ≤ 0")
        val dMid = input.diameterMidM
            ?: return CubageResult.Blocked("Diamètre milieu manquant (requis pour Huber)")
        if (dMid <= 0.0) return CubageResult.Blocked("Diamètre milieu ≤ 0")
        val volume = input.lengthM * sectionArea(dMid)
        return CubageResult.Valid(volumeM3 = volume)
    }
}

/** Volume = L × moyenne(S(base), S(sommet)). Approximation classique pour une grume tronconique. */
object SmalianMethod : CubageMethod<LogSegmentMeasurement> {
    override val id: String = "SMALIAN"
    override val version: String = "1.0.0"
    override val qualification: MethodQualification = MethodQualification.QUALIFIED
    override val sourceCitation: String = SOURCE_CITATION

    override fun compute(input: LogSegmentMeasurement): CubageResult {
        if (input.lengthM <= 0.0) return CubageResult.Blocked("Longueur manquante ou ≤ 0")
        val dBase = input.diameterBaseM
            ?: return CubageResult.Blocked("Diamètre base manquant (requis pour Smalian)")
        val dTop = input.diameterTopM
            ?: return CubageResult.Blocked("Diamètre sommet manquant (requis pour Smalian)")
        if (dBase <= 0.0 || dTop <= 0.0) return CubageResult.Blocked("Diamètre base/sommet ≤ 0")
        val volume = input.lengthM * (sectionArea(dBase) + sectionArea(dTop)) / 2.0
        return CubageResult.Valid(volumeM3 = volume)
    }
}

/**
 * Volume = (L/6) × (S(base) + 4×S(milieu) + S(sommet)) — règle de Simpson à 3 sections.
 * Exacte pour un profil dont l'aire de section varie de façon quadratique le long de l'axe
 * (donc exacte pour un tronc de cône réel, pas seulement une approximation).
 */
object NewtonMethod : CubageMethod<LogSegmentMeasurement> {
    override val id: String = "NEWTON"
    override val version: String = "1.0.0"
    override val qualification: MethodQualification = MethodQualification.QUALIFIED
    override val sourceCitation: String = SOURCE_CITATION

    override fun compute(input: LogSegmentMeasurement): CubageResult {
        if (input.lengthM <= 0.0) return CubageResult.Blocked("Longueur manquante ou ≤ 0")
        val dBase = input.diameterBaseM
            ?: return CubageResult.Blocked("Diamètre base manquant (requis pour Newton)")
        val dMid = input.diameterMidM
            ?: return CubageResult.Blocked("Diamètre milieu manquant (requis pour Newton)")
        val dTop = input.diameterTopM
            ?: return CubageResult.Blocked("Diamètre sommet manquant (requis pour Newton)")
        if (dBase <= 0.0 || dMid <= 0.0 || dTop <= 0.0) {
            return CubageResult.Blocked("Diamètre base/milieu/sommet ≤ 0")
        }
        val volume = (input.lengthM / 6.0) * (sectionArea(dBase) + 4.0 * sectionArea(dMid) + sectionArea(dTop))
        return CubageResult.Valid(volumeM3 = volume)
    }
}

/**
 * Volume exact d'un tronc de cône = (L/3) × (S(base) + S(sommet) + √(S(base)×S(sommet))).
 * N'utilise que base + sommet (pas de section milieu) — formule géométrique fermée,
 * pas une règle d'intégration approchée.
 */
object ConeFrustumMethod : CubageMethod<LogSegmentMeasurement> {
    override val id: String = "CONE_FRUSTUM"
    override val version: String = "1.0.0"
    override val qualification: MethodQualification = MethodQualification.QUALIFIED
    override val sourceCitation: String = SOURCE_CITATION

    override fun compute(input: LogSegmentMeasurement): CubageResult {
        if (input.lengthM <= 0.0) return CubageResult.Blocked("Longueur manquante ou ≤ 0")
        val dBase = input.diameterBaseM
            ?: return CubageResult.Blocked("Diamètre base manquant (requis pour tronc de cône)")
        val dTop = input.diameterTopM
            ?: return CubageResult.Blocked("Diamètre sommet manquant (requis pour tronc de cône)")
        if (dBase <= 0.0 || dTop <= 0.0) return CubageResult.Blocked("Diamètre base/sommet ≤ 0")
        val sBase = sectionArea(dBase)
        val sTop = sectionArea(dTop)
        val volume = (input.lengthM / 3.0) * (sBase + sTop + sqrt(sBase * sTop))
        return CubageResult.Valid(volumeM3 = volume)
    }
}
