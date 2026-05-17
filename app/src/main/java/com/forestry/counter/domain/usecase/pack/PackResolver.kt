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
                it.status in listOf(PackStatus.INSTALLED, PackStatus.EMBEDDED)
            }?.let { result += it }
        }

        // 3. Pack départemental (si disponible)
        if (context.deptCode != null) {
            allInstalled.firstOrNull {
                it.level == PackLevel.DEPARTEMENTAL && it.codeINSEE == context.deptCode &&
                it.status in listOf(PackStatus.INSTALLED, PackStatus.EMBEDDED)
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
        for (pack in activePacks.sortedByDescending { it.level.priority }) {
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

    // ─── Socle national embarqué (toujours disponible sans téléchargement) ───

    val EMBEDDED_NATIONAL_PACK = GeoPackDescriptor(
        id           = "fr.national.v1",
        level        = PackLevel.SOCLE_NATIONAL,
        name         = "France — Socle national",
        codeINSEE    = "FR",
        parentId     = null,
        version      = "1.0.0",
        buildDate    = "2025-01",
        sizeKb       = 0L,
        status       = PackStatus.EMBEDDED,
        features     = PackFeatures(
            hasFloraDatabase       = true,
            hasStationRules        = true,
            hasRipisylveRules      = true,
            hasSylviculturalRules  = true,
            hasDriasProjets        = true,
            hasFtsIndex            = true,
            hasGpsContextCache     = false,
            hasRegionalSRGS        = false,
            floraSpeciesCount      = 350,
            stationTypesCount      = 45,
            essencesCount          = 120
        )
    )

    /**
     * Catalogue des packs régionaux disponibles (non installés par défaut).
     * TODO_METIER : charger depuis un manifest remote + cache local signé.
     */
    val REGIONAL_CATALOG: List<GeoPackDescriptor> = RegionFrance.values().map { region ->
        GeoPackDescriptor(
            id        = "fr.region.${region.codeINSEE}",
            level     = PackLevel.REGIONAL,
            name      = region.labelFr,
            codeINSEE = region.codeINSEE,
            parentId  = "fr.national.v1",
            version   = "1.0.0",
            buildDate = "2025-01",
            sizeKb    = estimatedRegionalSizeKb(region),
            status    = PackStatus.AVAILABLE,
            features  = PackFeatures(
                hasFloraDatabase      = true,
                hasStationRules       = true,
                hasRipisylveRules     = true,
                hasSylviculturalRules = true,
                hasDriasProjets       = true,
                hasFtsIndex           = true,
                hasGpsContextCache    = true,
                hasRegionalSRGS       = true,
                floraSpeciesCount     = regionalFloraCount(region),
                stationTypesCount     = 25,
                essencesCount         = regionalEssencesCount(region)
            )
        )
    }

    private fun estimatedRegionalSizeKb(region: RegionFrance): Long = when (region) {
        RegionFrance.NOUVELLE_AQUITAINE    -> 4200L
        RegionFrance.AUVERGNE_RHONE_ALPES  -> 4800L
        RegionFrance.OCCITANIE             -> 3900L
        RegionFrance.GRAND_EST             -> 3600L
        RegionFrance.BOURGOGNE_FRANCHE_COMTE -> 3200L
        else                               -> 2800L
    }

    private fun regionalFloraCount(region: RegionFrance): Int = when (region) {
        RegionFrance.NOUVELLE_AQUITAINE    -> 580
        RegionFrance.AUVERGNE_RHONE_ALPES  -> 640
        RegionFrance.PROVENCE_ALPES_COTE_AZUR -> 720
        RegionFrance.OCCITANIE             -> 700
        else                               -> 450
    }

    private fun regionalEssencesCount(region: RegionFrance): Int = when (region) {
        RegionFrance.AUVERGNE_RHONE_ALPES  -> 85
        RegionFrance.NOUVELLE_AQUITAINE    -> 75
        else                               -> 60
    }
}
