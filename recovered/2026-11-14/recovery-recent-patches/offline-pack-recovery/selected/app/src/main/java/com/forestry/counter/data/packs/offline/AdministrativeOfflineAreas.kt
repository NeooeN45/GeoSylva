package com.forestry.counter.data.packs.offline

import android.content.Context
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.MultiPolygon
import com.mapbox.geojson.Polygon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Official bundled boundaries; regional selection expands to every distinct department. */
class AdministrativeOfflineAreas(private val context: Context) {
    data class Area(val id: String, val name: String, val regionId: String?, val geometryJson: String)
    data class Selection(val departmentIds: Set<String>, val geometryJson: String)
    suspend fun catalog(): List<Area> = withContext(Dispatchers.IO) {
        listOf("regions", "departments").flatMap { file ->
            val collection = context.assets.open("packs/$file.geojson").bufferedReader().use { FeatureCollection.fromJson(it.readText()) }
            collection.features().orEmpty().map { f ->
                val geometry = requireNotNull(f.geometry())
                require(geometry is Polygon || geometry is MultiPolygon)
                Area(f.getStringProperty("territory_id"), f.getStringProperty("name"),
                    if (f.hasProperty("region_code")) "region:${f.getStringProperty("region_code")}" else null, geometry.toJson())
            }
        }.also { require(it.map(Area::id).distinct().size == it.size) }
    }
    fun selection(catalog: List<Area>, ids: Set<String>): Selection {
        require(ids.isNotEmpty())
        val regions = ids.filter { it.startsWith("region:") }.toSet()
        require(regions.all { id -> catalog.any { it.id == id } })
        val selected = (catalog.filter { it.id in ids && it.id.startsWith("department:") } +
            catalog.filter { it.regionId in regions }).distinctBy(Area::id)
        require(selected.isNotEmpty() && ids.filter { it.startsWith("department:") }.all { id -> selected.any { it.id == id } })
        require(regions.all { region -> selected.any { it.regionId == region } })
        val polygons = selected.flatMap {
            if (it.geometryJson.contains("\"MultiPolygon\"")) MultiPolygon.fromJson(it.geometryJson).coordinates()
            else listOf(Polygon.fromJson(it.geometryJson).coordinates())
        }
        return Selection(selected.map(Area::id).toSet(), MultiPolygon.fromLngLats(polygons).toJson())
    }
}
