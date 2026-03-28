package com.forestry.counter.domain.geo

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Service MNT embarqué — lecture de tuiles HGT (SRTM 90 m) locales.
 *
 * Priorité de recherche des tuiles (première trouvée) :
 *  1. [context.filesDir]/dem/          ← téléchargement in-app / Play Asset Delivery
 *  2. [context.getExternalFilesDir(null)]/dem/  ← copie manuelle SD/USB
 *
 * Format HGT standard NASA/CGIAR :
 *  - Nommage   : N{lat:02d}E{lon:03d}.hgt  ou  N{lat:02d}W{lon:03d}.hgt, etc.
 *  - Grille    : 1201 × 1201 points (SRTM 3-arcsec = ~90 m)
 *  - Encodage  : Int16 big-endian, -32768 = nodata, valeur en mètres
 *  - Couverture: 1° × 1° par tuile (coin SW = (lat, lon))
 *
 * Pente/exposition calculées par l'algorithme de Horn (identique à SrtmElevationService).
 *
 * Source recommandée pour les tuiles France :
 *   https://srtm.csi.cgiar.org/srtmdata/  (CGIAR-CSI SRTM v4.1, licence CC-BY)
 *   ~160 tuiles × ~600 KB compressées = ~96 MB pour la France entière
 */
object EmbeddedDemService {

    private const val SRTM3_SIZE  = 1201          // points par côté (3 arcsec)
    private const val CELL_DEG    = 1.0 / 1200.0  // pas angulaire = 3 arcsec

    // ─── API publique ──────────────────────────────────────────────────────────

    /**
     * Retourne les données terrain depuis les tuiles locales.
     * @return [SrtmTerrainData] ou null si tuile absente ou inutilisable.
     */
    fun getTerrainData(context: Context, lat: Double, lon: Double): SrtmTerrainData? {
        val tileLat = Math.floor(lat).toInt()
        val tileLon = Math.floor(lon).toInt()
        val file    = findTile(context, tileLat, tileLon) ?: return null

        return try {
            readTerrainFromHgt(file, lat, lon, tileLat, tileLon)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Vérifie si une tuile est disponible localement pour la position donnée.
     */
    fun hasTile(context: Context, lat: Double, lon: Double): Boolean {
        val tileLat = Math.floor(lat).toInt()
        val tileLon = Math.floor(lon).toInt()
        return findTile(context, tileLat, tileLon) != null
    }

    /**
     * Liste toutes les tuiles disponibles dans les répertoires locaux.
     */
    fun availableTiles(context: Context): List<String> {
        return demDirs(context).flatMap { dir ->
            dir.listFiles { f -> f.extension.lowercase() == "hgt" }?.map { it.name } ?: emptyList()
        }.distinct().sorted()
    }

    // ─── Recherche de tuile ────────────────────────────────────────────────────

    private fun demDirs(context: Context): List<File> = listOfNotNull(
        File(context.filesDir, "dem"),
        context.getExternalFilesDir(null)?.let { File(it, "dem") }
    )

    private fun findTile(context: Context, tileLat: Int, tileLon: Int): File? {
        val name = tileName(tileLat, tileLon)
        return demDirs(context)
            .flatMap { dir -> listOf(File(dir, name), File(dir, name.lowercase())) }
            .firstOrNull { it.exists() && it.length() >= SRTM3_SIZE.toLong() * SRTM3_SIZE * 2 }
    }

    /** Construit le nom de fichier HGT standard : N45E002.hgt */
    private fun tileName(lat: Int, lon: Int): String {
        val ns = if (lat >= 0) "N" else "S"
        val ew = if (lon >= 0) "E" else "W"
        return "${ns}${"%02d".format(Math.abs(lat))}${ew}${"%03d".format(Math.abs(lon))}.hgt"
    }

    // ─── Lecture HGT + calcul pente/exposition ─────────────────────────────────

    private fun readTerrainFromHgt(
        file: File, lat: Double, lon: Double,
        tileLat: Int, tileLon: Int
    ): SrtmTerrainData? {

        // Position relative dans la tuile (0.0–1.0)
        val rowFrac = (lat  - tileLat) / 1.0  // 0 = coin SW, 1 = coin NW
        val colFrac = (lon  - tileLon) / 1.0  // 0 = coin W,  1 = coin E

        // Index de la cellule centrale (row 0 = coin NW dans HGT)
        val col = (colFrac * (SRTM3_SIZE - 1)).toInt().coerceIn(1, SRTM3_SIZE - 2)
        val row = ((1.0 - rowFrac) * (SRTM3_SIZE - 1)).toInt().coerceIn(1, SRTM3_SIZE - 2)

        // Lire la grille 3×3 autour de la cellule cible
        val grid = Array(3) { IntArray(3) }
        RandomAccessFile(file, "r").use { raf ->
            for (dr in -1..1) for (dc in -1..1) {
                val r = row + dr; val c = col + dc
                val offset = ((r * SRTM3_SIZE) + c) * 2L
                raf.seek(offset)
                val hi = raf.read(); val lo = raf.read()
                if (hi < 0 || lo < 0) return null
                val raw = (hi shl 8) or lo
                grid[dr + 1][dc + 1] = if (raw == 0x8000) 0 else
                    if (raw >= 0x8000) raw - 0x10000 else raw
            }
        }

        val alt = grid[1][1]
        if (alt <= -32000) return null

        // Cellule physique en mètres (90 m ≈ 3 arcsec à lat ~45°)
        val cellM = CELL_DEG * 111_320.0 * Math.cos(Math.toRadians(lat))

        // Algorithme de Horn
        val dzdx = ((grid[0][2] + 2 * grid[1][2] + grid[2][2]) -
                    (grid[0][0] + 2 * grid[1][0] + grid[2][0])) / (8.0 * cellM)
        val dzdy = ((grid[2][0] + 2 * grid[2][1] + grid[2][2]) -
                    (grid[0][0] + 2 * grid[0][1] + grid[0][2])) / (8.0 * cellM)

        val slopePct  = (sqrt(dzdx * dzdx + dzdy * dzdy) * 100).toInt().coerceIn(0, 200)
        val aspectDeg = ((Math.toDegrees(atan2(dzdx, dzdy)) + 360) % 360).toInt()
        val aspectLabel = aspectLabel(aspectDeg)

        return SrtmTerrainData(alt, slopePct, aspectDeg, aspectLabel)
    }

    private fun aspectLabel(deg: Int): String = when (((deg + 22) % 360) / 45) {
        0 -> "N"; 1 -> "NE"; 2 -> "E"; 3 -> "SE"
        4 -> "S"; 5 -> "SW"; 6 -> "W"; else -> "NW"
    }
}
