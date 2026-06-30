package com.forestry.counter.domain.geo

import com.forestry.counter.domain.calculation.pricing.GrecoRegion

/**
 * Détection automatique de la GRECO à partir de la position GPS ou du code commune.
 *
 * Flux :
 * 1. GPS → geo.api.gouv.fr → code commune INSEE (5 chiffres)
 * 2. Code INSEE → 2 premiers chiffres = département
 * 3. Département → GrecoRegion.fromDepartment()
 *
 * Si la position n'est pas disponible, on retourne null (l'utilisateur
 * peut saisir manuellement ou on utilise la moyenne nationale).
 *
 * Source : IGN Inventaire Forestier — 12 GRECO (A-L)
 */
object GrecoDetector {

    /**
     * Déduit la GRECO à partir d'un code commune INSEE (5 chiffres).
     * Les 2 premiers chiffres correspondent au département.
     *
     * @param codeCommune code INSEE à 5 chiffres (ex: "25056" → département 25 → Jura → GRECO E)
     * @return la GRECO correspondante, ou null si non trouvée
     */
    fun fromCodeCommune(codeCommune: String): GrecoRegion? {
        val normalized = codeCommune.trim().padStart(5, '0')
        if (normalized.length < 2) return null

        // Cas particuliers : Corse (2A, 2B)
        val dept = if (normalized.startsWith("2A") || normalized.startsWith("2B")) {
            normalized.substring(0, 2)
        } else {
            normalized.substring(0, 2)
        }

        return GrecoRegion.fromDepartment(dept)
    }

    /**
     * Déduit la GRECO à partir d'un code département (2-3 chiffres).
     *
     * @param deptCode code département (ex: "25", "2A", "974")
     * @return la GRECO correspondante, ou null si non trouvée
     */
    fun fromDepartment(deptCode: String): GrecoRegion? {
        return GrecoRegion.fromDepartment(deptCode)
    }

    /**
     * Déduit la GRECO à partir de coordonnées GPS (latitude, longitude).
     * Utilise l'API geo.api.gouv.fr pour récupérer le code commune, puis déduit la GRECO.
     *
     * @param latitude latitude en degrés décimaux
     * @param longitude longitude en degrés décimaux
     * @return la GRECO correspondante, ou null si non trouvée / erreur réseau
     */
    suspend fun fromGpsCoordinates(latitude: Double, longitude: Double): GrecoRegion? {
        return try {
            val url = "https://geo.api.gouv.fr/communes?lat=$latitude&lon=$longitude&fields=code&format=json&geometry=centre"
            val text = java.net.URL(url).readText()
            val arr = org.json.JSONArray(text)
            if (arr.length() > 0) {
                val code = arr.getJSONObject(0).optString("code", null)
                if (!code.isNullOrEmpty()) fromCodeCommune(code) else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Déduit la GRECO à partir d'un résultat ParcelleAutoFill existant.
     * Évite un double appel API si le code commune a déjà été récupéré.
     */
    fun fromAutoFill(autoFill: ParcelleAutoFill): GrecoRegion? {
        val code = autoFill.codeCommune ?: return null
        return fromCodeCommune(code)
    }
}
