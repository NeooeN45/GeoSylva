package com.forestry.counter.domain.usecase.brain

import android.content.Context
import com.forestry.counter.data.local.ForestryDatabase
import com.forestry.counter.data.local.entity.FloraFtsEntity
import com.forestry.counter.data.local.entity.GpsContextCacheEntity
import com.forestry.counter.domain.usecase.florist.FloristDatabase
import com.forestry.counter.domain.usecase.florist.FloraNormalizer
import com.forestry.counter.domain.usecase.florist.TypeMilieu
import com.forestry.counter.domain.usecase.pack.PackManager
import com.forestry.counter.domain.usecase.pack.PackResolver
import com.forestry.counter.domain.usecase.pack.RegionalPackContent
import com.forestry.counter.domain.geo.ClimateContextService
import com.forestry.counter.domain.geo.ClimateType
import com.forestry.counter.domain.geo.EmbeddedDemService
import com.forestry.counter.domain.geo.EmbeddedSoilService
import com.forestry.counter.domain.geo.GeologyEmbeddedService
import com.forestry.counter.domain.geo.NormalesClimatiques
import com.forestry.counter.domain.geo.SrtmElevationService
import com.forestry.counter.domain.model.station.Drainage
import com.forestry.counter.domain.model.station.TextureSol
import com.forestry.counter.domain.usecase.territory.TerritorialResolver
import kotlinx.coroutines.*
import kotlin.math.roundToInt

/**
 * Cerveau local de GeoSylva.
 *
 * Orchestre tous les sous-systèmes offline-first :
 * - Index FTS flore (Room/SQLite)
 * - Cache contextes GPS calculés
 * - Profils régionaux via PackManager
 * - Mini-moteurs de scoring embarqués
 *
 * Point d'entrée unique pour tous les besoins d'intelligence locale.
 * Fonctionne intégralement sans réseau une fois initialisé.
 */
class LocalBrainCore(private val db: ForestryDatabase, private val context: Context) {

    companion object {
        @Volatile private var instance: LocalBrainCore? = null
        fun getInstance(ctx: Context): LocalBrainCore {
            val app = ctx.applicationContext as? com.forestry.counter.ForestryCounterApplication
                ?: error("LocalBrainCore requires ForestryCounterApplication")
            return instance ?: synchronized(this) {
                instance ?: LocalBrainCore(app.database, ctx.applicationContext).also { instance = it }
            }
        }
        // Durée de validité du cache GPS : 30 jours
        private const val CACHE_GPS_TTL_MS = 30L * 24 * 3600 * 1000
        // Précision de la grille GPS cache (0.01° ≈ 1 km)
        private const val GPS_GRID_PRECISION = 100.0
    }

    private val packManager by lazy { PackManager.getInstance(context) }

    // ─── Initialisation de l'index FTS ────────────────────────────────────────

    /**
     * Seed l'index FTS avec toutes les espèces de FloristDatabase.
     * À appeler au premier démarrage ou après installation d'un pack.
     * Idempotent — vérifie le count avant de seeder.
     */
    suspend fun ensureFtsIndexReady(): Boolean = withContext(Dispatchers.IO) {
        val dao = db.floraFtsDao()
        val existingCount = dao.count()
        if (existingCount >= FloristDatabase.species.size) return@withContext true

        val entities = FloristDatabase.species.map { sp ->
            FloraFtsEntity(
                speciesId       = sp.id,
                nomFrancais     = sp.taxonomie.nomFrancais,
                nomScientifique = sp.taxonomie.nomScientifique,
                vernaculaires   = sp.taxonomie.nomsVernaculaires.joinToString("|"),
                synonymes       = sp.taxonomie.synonymes.joinToString("|"),
                typeMilieu      = sp.habitat.milieuxPrincipaux.firstOrNull()?.name ?: "",
                strate          = sp.classification.strateVegetale.name
            )
        }
        dao.clearAll()
        dao.insertAll(entities)
        true
    }

    // ─── Recherche FTS ultra-rapide ────────────────────────────────────────────

    /**
     * Recherche full-text dans l'index local — retourne des IDs d'espèces.
     * Fallback sur FloraNormalizer si FTS vide ou requête trop courte.
     *
     * @param query  Saisie utilisateur (brute, tolérante aux fautes)
     * @param milieu Contexte milieu pour priorisation
     * @param limit  Nombre max de résultats
     */
    suspend fun searchFlora(
        query: String,
        milieu: TypeMilieu? = null,
        limit: Int = 10
    ): List<FtsSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank() || query.length < 2) return@withContext emptyList()

        val dao = db.floraFtsDao()
        val ftsCount = dao.count()

        // Si FTS non peuplé → fallback sur FloraNormalizer en mémoire
        if (ftsCount < 10) {
            return@withContext FloraNormalizer.searchWithCorrection(query, limit, milieu)
                .map { FtsSearchResult(it.espece.id, it.score, FtsSource.NORMALIZER) }
        }

        val ftsQuery = sanitizeFtsQuery(query)
        val raw = if (milieu != null) {
            dao.searchInMilieu(ftsQuery, milieu.name, limit)
        } else {
            dao.search(ftsQuery, limit)
        }

        if (raw.isEmpty()) {
            // Fallback phonétique via FloraNormalizer
            return@withContext FloraNormalizer.searchWithCorrection(query, limit, milieu)
                .map { FtsSearchResult(it.espece.id, it.score, FtsSource.NORMALIZER_FALLBACK) }
        }

        raw.mapNotNull { entity ->
            FloristDatabase.findById(entity.speciesId)?.let {
                FtsSearchResult(entity.speciesId, 0.9f, FtsSource.FTS_INDEX)
            }
        }
    }

    /** Nettoie la requête pour FTS4 (évite les caractères spéciaux crashants). */
    private fun sanitizeFtsQuery(raw: String): String {
        val cleaned = raw.replace(Regex("[^a-zA-ZàâäéèêëîïôùûüçÀÂÄÉÈÊËÎÏÔÙÛÜÇ\\s-]"), "")
            .trim()
        return if (cleaned.isBlank()) raw.take(20) else "$cleaned*"
    }

    // ─── Cache GPS contextes ──────────────────────────────────────────────────

    /**
     * Retourne le contexte GPS depuis le cache ou le calcule si absent.
     * Granularité ~1 km² (arrondi à 0.01°).
     */
    suspend fun getOrComputeGpsContext(
        lat: Double,
        lon: Double
    ): GpsContext = withContext(Dispatchers.IO) {
        val latKey = (lat * GPS_GRID_PRECISION).roundToInt() / GPS_GRID_PRECISION
        val lonKey = (lon * GPS_GRID_PRECISION).roundToInt() / GPS_GRID_PRECISION

        val dao = db.floraFtsDao()
        val cached = dao.getGpsContext(latKey, lonKey)

        // Cache valide ?
        if (cached != null && (System.currentTimeMillis() - cached.computedAt) < CACHE_GPS_TTL_MS) {
            return@withContext cached.toGpsContext()
        }

        // Recalcul
        val ctx = computeGpsContext(lat, lon)

        // Sauvegarder
        dao.insertGpsContext(
            GpsContextCacheEntity(
                latKey          = latKey,
                lonKey          = lonKey,
                regionCode      = ctx.regionCode ?: "",
                deptCode        = ctx.deptCode ?: "",
                altitudeApproxM = ctx.altitudeApproxM,
                topoHint        = ctx.topoHint,
                zoneHumideProb  = ctx.zoneHumideProb,
                packIdActive    = packManager.getContextFor(lat, lon).activePacks
                    .maxByOrNull { it.level.priority }?.id ?: PackResolver.EMBEDDED_NATIONAL_PACK.id,
                computedAt      = System.currentTimeMillis(),
                slopePct        = ctx.slopePct,
                aspectDeg       = ctx.aspectDeg,
                aspectLabel     = ctx.aspectLabel,
                precipMmAn      = ctx.precipMmAn,
                tempMoyC        = ctx.tempMoyC,
                climateType     = ctx.climateType.name,
                soilPh          = ctx.soilPh,
                soilRumMm       = ctx.soilRumMm,
                soilTexture     = ctx.soilTexture.name,
                soilDrainage    = ctx.soilDrainage.name
            )
        )

        // Éviction cache > 6 mois
        dao.evictOldContexts(System.currentTimeMillis() - 180L * 24 * 3600 * 1000)
        ctx
    }

    private suspend fun computeGpsContext(lat: Double, lon: Double): GpsContext {
        val territorialCtx = PackResolver.inferTerritorialContext(lat, lon, emptyList())

        // Sol + Géologie + Normales climatiques (100 % offline — synchrone, instantané)
        val soil    = EmbeddedSoilService.getSoilData(lat, lon)
        val geology = GeologyEmbeddedService.getGeologyContext(lat, lon)
        val normales = NormalesClimatiques.getByLocation(lat, lon)

        // DEM local (tuiles HGT) + climat en parallèle
        val (srtm, climate) = coroutineScope {
            val s = async(Dispatchers.IO) {
                // Priorité : tuile HGT locale > API SRTM + cache
                EmbeddedDemService.getTerrainData(context, lat, lon)
                    ?: SrtmElevationService.getTerrainData(context, lat, lon)
            }
            val c = async(Dispatchers.IO) { ClimateContextService.getClimateData(context, lat, lon) }
            Pair(s.await(), c.await())
        }

        // Altitude : SRTM si disponible, sinon IDW (fallback offline)
        val altM = srtm?.altitudeM?.toDouble() ?: TerritorialResolver.interpolateAltitude(lat, lon)
        val slopePct  = srtm?.slopePct  ?: -1
        val aspectDeg = srtm?.aspectDeg ?: -1
        val aspectLabel = srtm?.aspectLabel ?: ""

        val climateType = try {
            climate?.climateType ?: ClimateType.INCONNU
        } catch (_: Exception) { ClimateType.INCONNU }
        val precipMmAn = climate?.precipMmAn ?: -1
        val tempMoyC   = climate?.tempMoyC   ?: -99.0

        val topoHint = buildTopoHint(altM, slopePct, aspectDeg)
        val zoneHumideProb = estimateWetlandProbability(lat, lon, altM, slopePct, precipMmAn, climateType)

        return GpsContext(
            regionCode      = territorialCtx.regionCode,
            deptCode        = territorialCtx.deptCode,
            altitudeApproxM = altM,
            topoHint        = topoHint,
            zoneHumideProb  = zoneHumideProb,
            suggestedMilieu = suggestMilieu(zoneHumideProb, altM, climateType),
            slopePct        = slopePct,
            aspectDeg       = aspectDeg,
            aspectLabel     = aspectLabel,
            precipMmAn      = precipMmAn,
            tempMoyC        = tempMoyC,
            climateType     = climateType,
            soilPh          = soil.phSurface,
            soilRumMm       = soil.rumMm,
            soilTexture     = soil.texture,
            soilDrainage    = soil.drainage,
            geology         = geology,
            normalesDept    = normales
        )
    }

    private fun buildTopoHint(alt: Double, slopePct: Int, aspectDeg: Int): String {
        val slopeClass = when {
            slopePct < 0  -> null
            slopePct < 5  -> "plat"
            slopePct < 15 -> "doux"
            slopePct < 30 -> "moyen"
            slopePct < 60 -> "raide"
            else          -> "tres_raide"
        }
        val aspectClass = if (aspectDeg >= 0) when (((aspectDeg + 22) % 360) / 45) {
            0 -> "N" ; 1 -> "NE" ; 2 -> "E" ; 3 -> "SE"
            4 -> "S" ; 5 -> "SO" ; 6 -> "O" ; else -> "NO"
        } else null

        val altClass = when {
            alt > 1800 -> "subalpin"
            alt > 1200 -> "montagnard_haut"
            alt > 800  -> "montagnard"
            alt > 400  -> "colline"
            alt > 100  -> "plaine"
            else       -> "vallee_basse"
        }
        return listOfNotNull(slopeClass?.let { if (it == "plat") null else "versant" }, aspectClass, slopeClass, altClass)
            .joinToString("_").ifBlank { altClass }
    }

    private fun estimateWetlandProbability(
        lat: Double, lon: Double, alt: Double,
        slopePct: Int, precipMmAn: Int, climateType: ClimateType
    ): Double {
        var p = 0.10
        if (slopePct in 0..3)               p += 0.18  // terrain plat = accumulation
        if (alt < 80)                        p += 0.18  // fond de vallée alluviale
        if (alt < 150 && slopePct in 0..5)  p += 0.10  // plaine basse et plate
        if (precipMmAn > 800)               p += 0.12  // climat humide
        if (precipMmAn > 1200)              p += 0.08  // très humide
        if (climateType == ClimateType.OCEANIQ) p += 0.08
        if (climateType == ClimateType.MONTAGNARD) p += 0.05
        // Géographie France
        if (lon < 0.0 && lat in 43.0..49.0) p += 0.08  // façade atlantique
        if (lat in 46.0..48.5 && lon in 0.0..3.0 && alt < 150) p += 0.12  // plaines alluviales Loire/Seine
        if (lat in 46.5..48.0 && lon in (-3.0)..(-0.5)) p += 0.12  // Bretagne/marais
        return p.coerceIn(0.0, 1.0)
    }

    private fun suggestMilieu(zoneHumideProb: Double, alt: Double, climateType: ClimateType): TypeMilieu = when {
        climateType == ClimateType.MEDITERRANEEN          -> TypeMilieu.FORET_FEUILLUE
        climateType == ClimateType.SUBALPIN || alt > 1800 -> TypeMilieu.MONTAGNARD
        climateType == ClimateType.MONTAGNARD || alt > 1000 -> TypeMilieu.FORET_RESINEUSE
        zoneHumideProb > 0.55                             -> TypeMilieu.ZONE_HUMIDE
        alt > 600                                         -> TypeMilieu.FORET_MIXTE
        else                                              -> TypeMilieu.FORET_FEUILLUE
    }

    // ─── Profils régionaux ────────────────────────────────────────────────────

    /**
     * Espèces indicatrices prioritaires pour une région donnée.
     *
     * Algorithme de priorisation locale :
     *  1. Espèces régionales spécifiques (RegionalPackContent) en tête
     *  2. Espèces du milieu demandé (si fourni)
     *  3. Filtre par contexte altitude si fourni
     *  4. Toutes les autres espèces indicatrices en complément
     */
    fun prioritySpeciesForRegion(
        regionCode: String?,
        deptCode: String? = null,
        milieu: TypeMilieu? = null,
        altitudeM: Double? = null,
        limit: Int = 50
    ): List<String> {
        // 1. Espèces indicatrices régionales connues (noms français → lookup ID)
        val regionalNoms = RegionalPackContent.resolveSpeciesIndicatrices(regionCode)
        val regionalIds = regionalNoms.mapNotNull { nom ->
            FloristDatabase.findByNomFrancais(nom)?.id
        }

        // 2. Espèces du milieu demandé
        val milieuIds = if (milieu != null)
            FloristDatabase.findIndicatrices(milieu).map { it.id }
        else emptyList()

        // 3. Filtre altitude : exclure espèces strictement alpines si altitude < 600 m
        val altFilter: (String) -> Boolean = { id ->
            if (altitudeM == null) true
            else {
                val sp = FloristDatabase.findById(id)
                if (sp == null) true
                else {
                    val altMax = sp.ecologie.altitudeMaxM
                    val altMin = when {
                        sp.taxonomie.nomFrancais.contains("alpin", ignoreCase = true) -> 1200
                        sp.taxonomie.nomFrancais.contains("montagnard", ignoreCase = true) -> 600
                        else -> 0
                    }
                    altitudeM >= altMin && (altMax == 0 || altitudeM <= altMax)
                }
            }
        }

        // 4. Toutes les autres espèces en fallback
        val allOther = FloristDatabase.species.map { it.id }

        return buildList {
            addAll(regionalIds)
            addAll(milieuIds)
            addAll(allOther)
        }.distinct().filter(altFilter).take(limit)
    }

    /**
     * Taille cache GPS en nombre d'entrées.
     */
    suspend fun gpsCacheSize(): Int = withContext(Dispatchers.IO) {
        db.floraFtsDao().gpsContextCount()
    }

    // ─── Modèles de sortie ────────────────────────────────────────────────────

    data class GpsContext(
        val regionCode: String?,
        val deptCode: String?,
        val altitudeApproxM: Double,
        val topoHint: String,
        val zoneHumideProb: Double,
        val suggestedMilieu: TypeMilieu,
        val slopePct: Int = -1,
        val aspectDeg: Int = -1,
        val aspectLabel: String = "",
        val precipMmAn: Int = -1,
        val tempMoyC: Double = -99.0,
        val climateType: ClimateType = ClimateType.INCONNU,
        val soilPh: Double = -1.0,
        val soilRumMm: Int = -1,
        val soilTexture: TextureSol = TextureSol.INCONNUE,
        val soilDrainage: Drainage = Drainage.NORMAL,
        // Géologie + normales — toujours dispo offline, non cachées en DB
        val geology: GeologyEmbeddedService.GeologyContext? = null,
        val normalesDept: NormalesClimatiques.NormalesDept? = null
    )

    data class FtsSearchResult(
        val speciesId: String,
        val score: Float,
        val source: FtsSource
    )

    enum class FtsSource { FTS_INDEX, NORMALIZER, NORMALIZER_FALLBACK }

    private fun GpsContextCacheEntity.toGpsContext(): GpsContext {
        val ct  = try { ClimateType.valueOf(climateType) } catch (_: Exception) { ClimateType.INCONNU }
        val tex = try { TextureSol.valueOf(soilTexture) }  catch (_: Exception) { TextureSol.INCONNUE }
        val dr  = try { Drainage.valueOf(soilDrainage) }   catch (_: Exception) { Drainage.NORMAL }
        return GpsContext(
            regionCode      = regionCode.ifBlank { null },
            deptCode        = deptCode.ifBlank { null },
            altitudeApproxM = altitudeApproxM,
            topoHint        = topoHint,
            zoneHumideProb  = zoneHumideProb,
            suggestedMilieu = suggestMilieu(zoneHumideProb, altitudeApproxM, ct),
            slopePct        = slopePct,
            aspectDeg       = aspectDeg,
            aspectLabel     = aspectLabel,
            precipMmAn      = precipMmAn,
            tempMoyC        = tempMoyC,
            climateType     = ct,
            soilPh          = soilPh,
            soilRumMm       = soilRumMm,
            soilTexture     = tex,
            soilDrainage    = dr,
            geology         = GeologyEmbeddedService.getGeologyContext(latKey, lonKey),
            normalesDept    = NormalesClimatiques.getByLocation(latKey, lonKey)
        )
    }
}

