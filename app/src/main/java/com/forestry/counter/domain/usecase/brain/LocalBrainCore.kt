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
        val context = computeGpsContext(lat, lon)

        // Sauvegarder
        dao.insertGpsContext(
            GpsContextCacheEntity(
                latKey          = latKey,
                lonKey          = lonKey,
                regionCode      = context.regionCode ?: "",
                deptCode        = context.deptCode ?: "",
                altitudeApproxM = context.altitudeApproxM,
                topoHint        = context.topoHint,
                zoneHumideProb  = context.zoneHumideProb,
                packIdActive    = packManager.getContextFor(lat, lon).activePacks
                    .maxByOrNull { it.level.priority }?.id ?: PackResolver.EMBEDDED_NATIONAL_PACK.id,
                computedAt      = System.currentTimeMillis()
            )
        )

        // Éviction cache > 6 mois
        dao.evictOldContexts(System.currentTimeMillis() - 180L * 24 * 3600 * 1000)
        context
    }

    private fun computeGpsContext(lat: Double, lon: Double): GpsContext {
        val territorialCtx = PackResolver.inferTerritorialContext(lat, lon, emptyList())

        // Altitude via IDW 52 points de référence (précision ±50–200 m)
        val altApprox = TerritorialResolver.interpolateAltitude(lat, lon)

        // Heuristique topographique grossière
        val topoHint = when {
            altApprox > 1500 -> "altitude_subalpine"
            altApprox > 800  -> "altitude_montagnarde"
            altApprox > 400  -> "colline_piedmont"
            else             -> "plaine_vallee"
        }

        // Probabilité zone humide basée sur position géographique
        val zoneHumideProb = estimateWetlandProbability(lat, lon, altApprox)

        return GpsContext(
            regionCode      = territorialCtx.regionCode,
            deptCode        = territorialCtx.deptCode,
            altitudeApproxM = altApprox,
            topoHint        = topoHint,
            zoneHumideProb  = zoneHumideProb,
            suggestedMilieu = suggestMilieu(zoneHumideProb, altApprox)
        )
    }

    // approximateAltitude supprimée — remplacée par TerritorialResolver.interpolateAltitude

    private fun estimateWetlandProbability(lat: Double, lon: Double, alt: Double): Double {
        var p = 0.15  // base
        // Zones de fond de vallée (altitude basse)
        if (alt < 100) p += 0.20
        // Zones atlantiques (pluies fréquentes)
        if (lon < 0.0 && lat in 43.0..49.0) p += 0.15
        // Grandes plaines alluviales
        if (lat in 46.0..48.5 && lon in 0.0..3.0 && alt < 150) p += 0.20
        // Marais bretons
        if (lat in 46.5..48.0 && lon in (-3.0)..(-0.5)) p += 0.20
        return p.coerceIn(0.0, 1.0)
    }

    private fun suggestMilieu(zoneHumideProb: Double, alt: Double): TypeMilieu = when {
        zoneHumideProb > 0.50 -> TypeMilieu.ZONE_HUMIDE
        alt > 1200             -> TypeMilieu.FORET_RESINEUSE
        alt > 600              -> TypeMilieu.FORET_MIXTE
        else                   -> TypeMilieu.FORET_FEUILLUE
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
        val suggestedMilieu: TypeMilieu
    )

    data class FtsSearchResult(
        val speciesId: String,
        val score: Float,
        val source: FtsSource
    )

    enum class FtsSource { FTS_INDEX, NORMALIZER, NORMALIZER_FALLBACK }

    private fun GpsContextCacheEntity.toGpsContext() = GpsContext(
        regionCode      = regionCode.ifBlank { null },
        deptCode        = deptCode.ifBlank { null },
        altitudeApproxM = altitudeApproxM,
        topoHint        = topoHint,
        zoneHumideProb  = zoneHumideProb,
        suggestedMilieu = suggestMilieu(zoneHumideProb, altitudeApproxM)
    )
}

