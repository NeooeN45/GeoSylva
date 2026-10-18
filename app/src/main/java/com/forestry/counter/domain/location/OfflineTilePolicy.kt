package com.forestry.counter.domain.location

import java.net.URL

/**
 * Politique technique et contractuelle des packs cartographiques hors ligne.
 *
 * Un fond disponible en consultation en ligne n'est pas automatiquement
 * téléchargeable en masse. La liste reste donc volontairement restrictive :
 * tout nouveau fournisseur doit être qualifié avant d'être ajouté.
 */
internal object OfflineTilePolicy {
    private const val WEB_MERCATOR_MAX_LATITUDE = 85.05112878
    private const val MIN_ZOOM = 0
    private const val MAX_ZOOM = 22

    fun validateTemplate(template: String): String? {
        val url = runCatching { URL(template) }.getOrNull()
            ?: return "Source cartographique invalide"
        if (url.protocol.lowercase() != "https") {
            return "Le téléchargement hors ligne exige une source HTTPS"
        }
        if (listOf("{z}", "{x}", "{y}").any { it !in template }) {
            return "Le modèle de tuile doit contenir les coordonnées {z}, {x} et {y}"
        }
        if (url.path.lowercase().endsWith(".pbf")) {
            return "Ce flux vectoriel n'est pas compatible avec le cache raster hors ligne"
        }
        if (!MapSourcePolicy.isOfflineRasterTemplateQualified(template)) {
            return "Le téléchargement raster hors ligne de cette source n'est pas encore qualifié"
        }
        return null
    }

    fun validateRegion(
        latSouth: Double,
        latNorth: Double,
        lonWest: Double,
        lonEast: Double,
        minZoom: Int,
        maxZoom: Int,
    ): String? {
        if (listOf(latSouth, latNorth, lonWest, lonEast).any { !it.isFinite() }) {
            return "Les limites géographiques doivent être des nombres finis"
        }
        if (latSouth !in -WEB_MERCATOR_MAX_LATITUDE..WEB_MERCATOR_MAX_LATITUDE ||
            latNorth !in -WEB_MERCATOR_MAX_LATITUDE..WEB_MERCATOR_MAX_LATITUDE
        ) {
            return "La latitude dépasse les limites de la projection cartographique"
        }
        if (lonWest !in -180.0..180.0 || lonEast !in -180.0..180.0) {
            return "La longitude doit être comprise entre -180° et 180°"
        }
        if (latSouth >= latNorth) return "La limite sud doit être inférieure à la limite nord"
        if (lonWest >= lonEast) return "La limite ouest doit être inférieure à la limite est"
        if (minZoom !in MIN_ZOOM..MAX_ZOOM || maxZoom !in MIN_ZOOM..MAX_ZOOM || minZoom > maxZoom) {
            return "La plage de zoom doit être croissante et comprise entre $MIN_ZOOM et $MAX_ZOOM"
        }
        return null
    }

    /** Identifiant sûr pour les journaux : aucun chemin, paramètre ni jeton. */
    fun providerIdentifier(template: String): String =
        runCatching { URL(template).host.lowercase() }.getOrDefault("source-inconnue")
}
