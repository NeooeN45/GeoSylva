package com.forestry.counter.domain.location

/**
 * Qualification opérationnelle des fonds hérités de Carte/Recherche/Libre.
 *
 * Une clé API, une attribution ou l'accès public ne prouvent pas les droits
 * d'une application commerciale. Les références restent présentes, mais
 * seuls les usages qualifiés sont activables. « Pending » ne signifie pas
 * que la licence interdit l'usage : sa qualification reste à faire.
 */
internal object MapSourcePolicy {
    const val DEFAULT_LAYER_KEY = "ORTHO_IGN"
    const val LOCAL_LAYER_KEY = "OFFLINE_LOCAL"
    const val ORTHOPHOTO_TEMPLATE =
        "https://data.geopf.fr/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0" +
            "&LAYER=ORTHOIMAGERY.ORTHOPHOTOS&STYLE=normal&FORMAT=image/jpeg" +
            "&TILEMATRIXSET=PM_0_19&TILEMATRIX={z}&TILECOL={x}&TILEROW={y}"
    const val ORTHOPHOTO_ATTRIBUTION =
        "© IGN · BD ORTHO® · Licence Ouverte 2.0 · catalogue 11/09/2026 (prises de vue variables)"

    enum class Qualification { QUALIFIED_ONLINE, QUALIFICATION_PENDING, LOCAL_ONLY }

    data class SourceRights(
        val key: String,
        val product: String,
        val online: Qualification,
        /** Preuve embarquée ; le flux ne représente pas une édition photo uniforme. */
        val evidenceAsset: String? = null,
        val offlineRasterDownloadQualified: Boolean = false,
    )

    private fun pending(key: String, product: String) =
        SourceRights(key, product, Qualification.QUALIFICATION_PENDING)

    val sources: List<SourceRights> = listOf(
        SourceRights(
            DEFAULT_LAYER_KEY,
            "IGN BD ORTHO — ORTHOIMAGERY.ORTHOPHOTOS / PM_0_19",
            Qualification.QUALIFIED_ONLINE,
            evidenceAsset = "packs/orthophoto-source.json",
        ),
        pending("PLAN_IGN", "IGN Plan v2"),
        pending("PLAN_IGN_CADASTRE", "IGN Plan v2 + Parcellaire Express"),
        pending("ORTHO_CADASTRE", "IGN BD ORTHO + Parcellaire Express"),
        pending("TOPO", "OpenTopoMap"),
        pending("SATELLITE", "Esri World Imagery"),
        pending("OSM_STANDARD", "OpenStreetMap — service tile.openstreetmap.org"),
        pending("CARTO_LIGHT", "CARTO Positron"),
        pending("CARTO_DARK", "CARTO Dark Matter"),
        pending("MAPTILER_TOPO", "MapTiler Topo 3D"),
        pending("MAPTILER_SATELLITE", "MapTiler Hybrid"),
        pending("MAPTILER_STREETS", "MapTiler Streets"),
        pending("MAPTILER_DARK", "MapTiler Dark Matter"),
        SourceRights(LOCAL_LAYER_KEY, "Données déjà conservées sur cet appareil", Qualification.LOCAL_ONLY),
    )

    fun isLayerActivatable(key: String): Boolean = sources.any {
        it.key == key && it.online != Qualification.QUALIFICATION_PENDING
    }

    /** Résolution conservative d'une ancienne préférence, sans supprimer de données. */
    fun resolveLayerKey(storedKey: String?): String =
        storedKey?.takeIf(::isLayerActivatable) ?: DEFAULT_LAYER_KEY

    /** La qualification porte sur la distribution exacte, pas sur tout le domaine IGN. */
    fun isOnlineTemplateQualified(template: String): Boolean = template == ORTHOPHOTO_TEMPLATE

    /** L'affichage en ligne ne qualifie aucun téléchargement organisé raster. */
    fun isOfflineRasterTemplateQualified(template: String): Boolean = sources.any {
        it.offlineRasterDownloadQualified && it.key == DEFAULT_LAYER_KEY &&
            template == ORTHOPHOTO_TEMPLATE
    }
}
