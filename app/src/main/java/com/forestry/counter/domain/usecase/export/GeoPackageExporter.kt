package com.forestry.counter.domain.usecase.export

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.presentation.screens.forestry.MartelageStats
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI

/**
 * Exporteur GeoPackage (.gpkg) pour les données de martelage.
 *
 * Format GeoPackage 1.2 : SQLite + tables normatives + géométrie WKB.
 * Deux couches : `martelage_tiges` (points GPS par tige) + `martelage_stats` (table attributaire).
 * Aucune dépendance externe — repose sur android.database.sqlite.SQLiteDatabase.
 */
object GeoPackageExporter {

    fun export(
        context: Context,
        uri: Uri,
        tiges: List<Tige>,
        stats: MartelageStats,
        scopeLabel: String
    ): Result<Unit> = runCatching {
        val tmpFile = File(context.cacheDir, "martelage_${System.currentTimeMillis()}.gpkg")
        try {
            buildGeoPackage(tmpFile, tiges, stats, scopeLabel)
            context.contentResolver.openOutputStream(uri)?.use { os ->
                FileInputStream(tmpFile).use { fis -> fis.copyTo(os) }
            } ?: error("Impossible d'ouvrir le flux de sortie")
        } finally {
            tmpFile.delete()
        }
    }

    // ─── Construction du fichier GeoPackage ───────────────────────────────

    private fun buildGeoPackage(
        file: File,
        tiges: List<Tige>,
        stats: MartelageStats,
        scopeLabel: String
    ) {
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            // ── GeoPackage headers SQLite ──
            db.execSQL("PRAGMA application_id = 0x47504B47")  // "GPKG"
            db.execSQL("PRAGMA user_version = 10200")           // GeoPackage 1.2.0

            createNormativeTables(db)
            insertSrsSystems(db)

            val gpsTiges = tiges.filter { QgisExportHelper.parseWktPointZ(it.gpsWkt) != null }

            val bbox = computeBbox(gpsTiges)
            val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())

            createTigesLayer(db, tiges, gpsTiges, bbox, now, scopeLabel)
            createStatsLayer(db, stats, now, scopeLabel)

        } finally {
            db.close()
        }
    }

    // ─── Tables normatives GeoPackage ─────────────────────────────────────

    private fun createNormativeTables(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS gpkg_spatial_ref_sys (
                srs_name TEXT NOT NULL,
                srs_id INTEGER NOT NULL PRIMARY KEY,
                organization TEXT NOT NULL,
                organization_coordsys_id INTEGER NOT NULL,
                definition TEXT NOT NULL,
                description TEXT
            )""")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS gpkg_contents (
                table_name TEXT NOT NULL PRIMARY KEY,
                data_type TEXT NOT NULL,
                identifier TEXT,
                description TEXT,
                last_change DATETIME NOT NULL,
                min_x REAL, min_y REAL, max_x REAL, max_y REAL,
                srs_id INTEGER,
                CONSTRAINT fk_gc_r_srs_id FOREIGN KEY (srs_id)
                    REFERENCES gpkg_spatial_ref_sys(srs_id)
            )""")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS gpkg_geometry_columns (
                table_name TEXT NOT NULL,
                column_name TEXT NOT NULL,
                geometry_type_name TEXT NOT NULL,
                srs_id INTEGER NOT NULL,
                z TINYINT NOT NULL,
                m TINYINT NOT NULL,
                CONSTRAINT pk_geom_cols PRIMARY KEY (table_name, column_name),
                CONSTRAINT fk_gc_tn FOREIGN KEY (table_name)
                    REFERENCES gpkg_contents(table_name),
                CONSTRAINT fk_gc_srs FOREIGN KEY (srs_id)
                    REFERENCES gpkg_spatial_ref_sys(srs_id)
            )""")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS gpkg_tile_matrix_set (
                table_name TEXT NOT NULL PRIMARY KEY,
                srs_id INTEGER NOT NULL,
                min_x REAL NOT NULL, min_y REAL NOT NULL,
                max_x REAL NOT NULL, max_y REAL NOT NULL,
                CONSTRAINT fk_gtms_table_name FOREIGN KEY (table_name)
                    REFERENCES gpkg_contents(table_name),
                CONSTRAINT fk_gtms_srs FOREIGN KEY (srs_id)
                    REFERENCES gpkg_spatial_ref_sys (srs_id)
            )""")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS gpkg_tile_matrix (
                table_name TEXT NOT NULL,
                zoom_level INTEGER NOT NULL,
                matrix_width INTEGER NOT NULL,
                matrix_height INTEGER NOT NULL,
                tile_width INTEGER NOT NULL,
                tile_height INTEGER NOT NULL,
                pixel_x_size REAL NOT NULL,
                pixel_y_size REAL NOT NULL,
                CONSTRAINT pk_ttm PRIMARY KEY (table_name, zoom_level),
                CONSTRAINT fk_tmm_table_name FOREIGN KEY (table_name)
                    REFERENCES gpkg_contents(table_name)
            )""")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS gpkg_extensions (
                table_name TEXT,
                column_name TEXT,
                extension_name TEXT NOT NULL,
                definition TEXT NOT NULL,
                scope TEXT NOT NULL,
                CONSTRAINT ge_tce UNIQUE (table_name, column_name, extension_name)
            )""")
    }

    private fun insertSrsSystems(db: SQLiteDatabase) {
        db.execSQL("""INSERT OR IGNORE INTO gpkg_spatial_ref_sys
            (srs_name, srs_id, organization, organization_coordsys_id, definition, description)
            VALUES ('Undefined Cartesian SRS', -1, 'NONE', -1, 'undefined', 'Undefined Cartesian')""")

        db.execSQL("""INSERT OR IGNORE INTO gpkg_spatial_ref_sys
            (srs_name, srs_id, organization, organization_coordsys_id, definition, description)
            VALUES ('Undefined Geographic SRS', 0, 'NONE', 0, 'undefined', 'Undefined Geographic')""")

        db.execSQL("""INSERT OR IGNORE INTO gpkg_spatial_ref_sys
            (srs_name, srs_id, organization, organization_coordsys_id, definition, description) VALUES (
            'WGS 84 Geographic 2D', 4326, 'EPSG', 4326,
            'GEOGCS["WGS 84",DATUM["World Geodetic System 1984",SPHEROID["WGS 84",6378137,298.257223563]],PRIMEM["Greenwich",0],UNIT["degree",0.0174532925199433,AUTHORITY["EPSG","9122"]],AUTHORITY["EPSG","4326"]]',
            'longitude/latitude coordinates in decimal degrees on WGS 84 spheroid')""")
    }

    // ─── Couche tiges ─────────────────────────────────────────────────────

    private fun createTigesLayer(
        db: SQLiteDatabase,
        tiges: List<Tige>,
        gpsTiges: List<Tige>,
        bbox: DoubleArray,
        now: String,
        scopeLabel: String
    ) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS martelage_tiges (
                fid INTEGER PRIMARY KEY AUTOINCREMENT,
                geom BLOB,
                tige_id TEXT,
                essence_code TEXT,
                diam_cm REAL,
                hauteur_m REAL,
                g_m2 REAL,
                valeur_eur REAL,
                qualite INTEGER,
                destination TEXT,
                categorie TEXT,
                placette_id TEXT,
                defauts TEXT,
                note TEXT,
                precision_gps_m REAL,
                altitude_m REAL
            )""")

        db.execSQL("""INSERT INTO gpkg_contents
            (table_name, data_type, identifier, description, last_change,
             min_x, min_y, max_x, max_y, srs_id)
            VALUES ('martelage_tiges', 'features', 'Tiges martelage',
            '$scopeLabel', '$now',
            ${bbox[0]}, ${bbox[1]}, ${bbox[2]}, ${bbox[3]}, 4326)""")

        db.execSQL("""INSERT INTO gpkg_geometry_columns
            (table_name, column_name, geometry_type_name, srs_id, z, m)
            VALUES ('martelage_tiges', 'geom', 'POINTZ', 4326, 2, 0)""")

        val stmt = db.compileStatement("""
            INSERT INTO martelage_tiges
            (geom, tige_id, essence_code, diam_cm, hauteur_m, g_m2,
             valeur_eur, qualite, destination, categorie,
             placette_id, defauts, note, precision_gps_m, altitude_m)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""")

        db.beginTransaction()
        try {
            tiges.forEach { t ->
                val pt = QgisExportHelper.parseWktPointZ(t.gpsWkt)
                val g = PI / 4.0 * (t.diamCm / 100.0) * (t.diamCm / 100.0)

                if (pt != null) {
                    stmt.bindBlob(1, encodeGpkgPointZ(pt.lon, pt.lat, pt.alt ?: t.altitudeM ?: 0.0))
                } else {
                    stmt.bindNull(1)
                }
                stmt.bindString(2, t.id)
                stmt.bindString(3, t.essenceCode)
                stmt.bindDouble(4, t.diamCm)
                t.hauteurM?.let { stmt.bindDouble(5, it) } ?: stmt.bindNull(5)
                stmt.bindDouble(6, g)
                t.valueEur?.let { stmt.bindDouble(7, it) } ?: stmt.bindNull(7)
                t.qualite?.let { stmt.bindLong(8, it.toLong()) } ?: stmt.bindNull(8)
                stmt.bindString(9, t.destination ?: "COUPER")
                t.categorie?.let { stmt.bindString(10, it) } ?: stmt.bindNull(10)
                t.placetteId?.let { stmt.bindString(11, it) } ?: stmt.bindNull(11)
                t.defauts?.let { stmt.bindString(12, it.joinToString(",")) } ?: stmt.bindNull(12)
                t.note?.let { stmt.bindString(13, it) } ?: stmt.bindNull(13)
                t.precisionM?.let { stmt.bindDouble(14, it) } ?: stmt.bindNull(14)
                t.altitudeM?.let { stmt.bindDouble(15, it) } ?: stmt.bindNull(15)
                stmt.execute()
                stmt.clearBindings()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        stmt.close()
    }

    // ─── Couche statistiques (table attributaire) ──────────────────────────

    private fun createStatsLayer(
        db: SQLiteDatabase,
        stats: MartelageStats,
        now: String,
        scopeLabel: String
    ) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS martelage_stats (
                fid INTEGER PRIMARY KEY AUTOINCREMENT,
                scope TEXT,
                n_total INTEGER,
                n_per_ha REAL,
                g_total_m2 REAL,
                g_per_ha_m2 REAL,
                v_total_m3 REAL,
                v_per_ha_m3 REAL,
                dm_cm REAL,
                dg_cm REAL,
                h_moy_m REAL,
                h_lorey_m REAL,
                dmin_cm REAL,
                dmax_cm REAL,
                revenue_total_eur REAL,
                n_essences INTEGER,
                shannon_h REAL,
                ibp_score INTEGER
            )""")

        db.execSQL("""INSERT INTO gpkg_contents
            (table_name, data_type, identifier, description, last_change, srs_id)
            VALUES ('martelage_stats', 'attributes', 'Statistiques martelage', '$scopeLabel', '$now', 0)""")

        val stmt = db.compileStatement("""
            INSERT INTO martelage_stats
            (scope, n_total, n_per_ha, g_total_m2, g_per_ha_m2, v_total_m3, v_per_ha_m3,
             dm_cm, dg_cm, h_moy_m, h_lorey_m, dmin_cm, dmax_cm,
             revenue_total_eur, n_essences, shannon_h, ibp_score)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""")

        stmt.bindString(1, scopeLabel)
        stmt.bindLong(2, stats.nTotal.toLong())
        stmt.bindDouble(3, stats.nPerHa)
        stmt.bindDouble(4, stats.gTotal)
        stmt.bindDouble(5, stats.gPerHa)
        stmt.bindDouble(6, stats.vTotal)
        stmt.bindDouble(7, stats.vPerHa)
        stats.dm?.let { stmt.bindDouble(8, it) } ?: stmt.bindNull(8)
        stats.dg?.let { stmt.bindDouble(9, it) } ?: stmt.bindNull(9)
        stats.meanH?.let { stmt.bindDouble(10, it) } ?: stmt.bindNull(10)
        stats.hLorey?.let { stmt.bindDouble(11, it) } ?: stmt.bindNull(11)
        stats.dMin?.let { stmt.bindDouble(12, it) } ?: stmt.bindNull(12)
        stats.dMax?.let { stmt.bindDouble(13, it) } ?: stmt.bindNull(13)
        stats.revenueTotal?.let { stmt.bindDouble(14, it) } ?: stmt.bindNull(14)
        stmt.bindLong(15, stats.perEssence.size.toLong())
        stats.biodiversity?.let { stmt.bindDouble(16, it.shannonH) } ?: stmt.bindNull(16)
        stats.biodiversity?.let { stmt.bindLong(17, it.ibpScore.toLong()) } ?: stmt.bindNull(17)
        stmt.execute()
        stmt.close()
    }

    // ─── Encodage géométrie GeoPackage WKB ────────────────────────────────

    /**
     * Encode un POINT Z au format GeoPackage Geometry Binary (ISO WKB).
     * Spec GeoPackage 1.2 §2.1.3 : magic(2) + version(1) + flags(1) + srs_id(4) + WKB
     */
    private fun encodeGpkgPointZ(lon: Double, lat: Double, z: Double): ByteArray {
        val gpkgHeaderSize = 2 + 1 + 1 + 4   // magic + version + flags + srs_id
        val wkbSize = 1 + 4 + 8 + 8 + 8       // byteOrder + type + x + y + z
        val buf = ByteBuffer.allocate(gpkgHeaderSize + wkbSize).order(ByteOrder.LITTLE_ENDIAN)

        // GeoPackage standard geometry header
        buf.put('G'.code.toByte())   // magic[0]
        buf.put('P'.code.toByte())   // magic[1]
        buf.put(0x00.toByte())       // version 1
        // flags: bit0=1 (little-endian), bits 1-3=0 (no envelope), bit4=0 (not empty)
        buf.put(0x01.toByte())
        buf.putInt(4326)             // srs_id

        // WKB PointZ (ISO WKB type 1001)
        buf.put(0x01.toByte())       // little-endian
        buf.putInt(1001)             // ISO WKB type PointZ
        buf.putDouble(lon)
        buf.putDouble(lat)
        buf.putDouble(z)

        return buf.array()
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private fun computeBbox(gpsTiges: List<Tige>): DoubleArray {
        if (gpsTiges.isEmpty()) return doubleArrayOf(-180.0, -90.0, 180.0, 90.0)
        var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE
        var minY = Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        gpsTiges.forEach { t ->
            val pt = QgisExportHelper.parseWktPointZ(t.gpsWkt) ?: return@forEach
            if (pt.lon < minX) minX = pt.lon; if (pt.lon > maxX) maxX = pt.lon
            if (pt.lat < minY) minY = pt.lat; if (pt.lat > maxY) maxY = pt.lat
        }
        // Ajout d'un léger buffer (0.001°≈111m)
        return doubleArrayOf(minX - 0.001, minY - 0.001, maxX + 0.001, maxY + 0.001)
    }
}
