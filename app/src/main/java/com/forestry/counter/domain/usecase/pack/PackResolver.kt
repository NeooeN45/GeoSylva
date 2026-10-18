package com.forestry.counter.domain.usecase.pack

import com.forestry.counter.domain.model.pack.*
import com.forestry.counter.domain.usecase.territory.TerritorialResolver

/**
 * Résolveur hiérarchique de règles et données.
 *
 * Applique la logique de superposition :
 *   Socle national → Pack régional → Pack départemental
 *
 * Le niveau le plus local surcharge les niveaux supérieurs.
 * Fallback transparent si un pack intermédiaire est absent.
 */
object PackResolver {

    /**
     * Détermine les packs actifs pour un contexte territorial donné.
     * Retourne toujours au moins le socle national.
     */
    fun resolveActivePacks(
        context: TerritorialContext,
        allInstalled: List<GeoPackDescriptor>
    ): List<GeoPackDescriptor> {
        val result = mutableListOf<GeoPackDescriptor>()

        // 1. Socle national — toujours présent
        val national = allInstalled.firstOrNull { it.level == PackLevel.SOCLE_NATIONAL }
            ?: EMBEDDED_NATIONAL_PACK
        result += national

        // 2. Pack régional (si disponible)
        if (context.regionCode != null) {
            allInstalled.firstOrNull {
                it.level == PackLevel.REGIONAL && it.codeINSEE == context.regionCode &&
                it.isUsable && it.contentKind !in setOf("administrative_boundaries", "forestry_layers")
            }?.let { result += it }
        }

        // 3. Pack départemental (si disponible)
        if (context.deptCode != null) {
            allInstalled.firstOrNull {
                it.level == PackLevel.DEPARTEMENTAL && it.codeINSEE == context.deptCode &&
                it.isUsable && it.contentKind !in setOf("administrative_boundaries", "forestry_layers")
            }?.let { result += it }
        }

        return result.sortedBy { it.level.priority }
    }

    /**
     * Résout une valeur en appliquant la surcharge locale.
     * Si le pack département n'a pas la valeur, remonte au régional, puis au national.
     */
    fun <T> resolve(
        activePacks: List<GeoPackDescriptor>,
        getter: (GeoPackDescriptor) -> T?,
        default: T
    ): ResolvedValue<T> {
        // Parcours du plus local au plus global
        for (pack in activePacks.filter { it.contentKind !in setOf("administrative_boundaries", "forestry_layers") }.sortedByDescending { it.level.priority }) {
            val value = getter(pack)
            if (value != null) {
                return ResolvedValue(value, pack.level, pack.id)
            }
        }
        return ResolvedValue(default, PackLevel.SOCLE_NATIONAL, EMBEDDED_NATIONAL_PACK.id)
    }

    /**
     * Déduit le contexte territorial depuis des coordonnées GPS.
     * Délègue à [TerritorialResolver] : 96 départements par centroïdes Haversine.
     * Fallback propre si GPS absent ou hors France.
     */
    fun inferTerritorialContext(
        latitude: Double?,
        longitude: Double?,
        installedPacks: List<GeoPackDescriptor>
    ): TerritorialContext {
        if (latitude == null || longitude == null) {
            return TerritorialContext(
                regionCode = null, deptCode = null,
                activePacks = listOf(EMBEDDED_NATIONAL_PACK),
                appliedLevel = PackLevel.SOCLE_NATIONAL
            )
        }

        val territorial = TerritorialResolver.resolve(latitude, longitude)
        val regionCode  = territorial.regionCode
        val deptCode    = territorial.deptCode

        val activePacks = resolveActivePacks(
            TerritorialContext(regionCode, deptCode, emptyList(), PackLevel.SOCLE_NATIONAL),
            installedPacks
        )
        val highestLevel = activePacks.maxByOrNull { it.level.priority }?.level
            ?: PackLevel.SOCLE_NATIONAL

        return TerritorialContext(
            regionCode   = regionCode,
            deptCode     = deptCode,
            activePacks  = activePacks,
            appliedLevel = highestLevel
        )
    }

    // Référence logique des fonctions embarquées, pas un jeu de données publié.

    val EMBEDDED_NATIONAL_PACK = GeoPackDescriptor(
        id           = "fr.national.v1",
        level        = PackLevel.SOCLE_NATIONAL,
        name         = "Fonctions embarquées de l'application",
        codeINSEE    = "FR",
        parentId     = null,
        version      = "",
        buildDate    = "",
        sizeKb       = 0L,
        status       = PackStatus.EMBEDDED,
        features     = PackFeatures(),
        metaInfo     = PackMetaInfo(
            author = "",
            license = "",
            minAppVersion = "",
            notes = "Référence logique des fonctions embarquées ; aucun manifeste de pack national qualifié"
        )
    )

    /**
     * Aucune publication GSIE de packs régionaux n'est actuellement raccordée.
     * Les territoires de la carte et les fichiers locaux sont indépendants de ce
     * catalogue. Ne pas inventer tailles, versions, contenus ou liens de packs.
     */
    val REGIONAL_CATALOG: List<GeoPackDescriptor> = emptyList()
}
