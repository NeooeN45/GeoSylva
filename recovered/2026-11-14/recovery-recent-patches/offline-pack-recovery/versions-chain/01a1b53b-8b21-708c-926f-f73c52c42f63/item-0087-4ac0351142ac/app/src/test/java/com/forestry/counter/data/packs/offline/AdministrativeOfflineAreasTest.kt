package com.forestry.counter.data.packs.offline

import android.content.Context
import com.mapbox.geojson.MultiPolygon
import org.junit.Assert.*
import org.junit.Test
import io.mockk.mockk

class AdministrativeOfflineAreasTest {
    private val geometry = """{"type":"Polygon","coordinates":[[[1,45],[2,45],[2,46],[1,46],[1,45]]]}"""
    private val areas = listOf(
        AdministrativeOfflineAreas.Area("region:75", "Nouvelle-Aquitaine", null, geometry),
        AdministrativeOfflineAreas.Area("department:16", "Charente", "region:75", geometry),
        AdministrativeOfflineAreas.Area("department:17", "Charente-Maritime", "region:75", geometry),
        AdministrativeOfflineAreas.Area("department:33", "Gironde", "region:75", geometry),
        AdministrativeOfflineAreas.Area("department:38", "Isère", "region:84", geometry),
    )
    @Test fun `region selection expands all departments and deduplicates explicit children`() {
        val selected = AdministrativeOfflineAreas(mockk<Context>()).selection(areas, setOf("region:75", "department:16"))
        assertEquals(setOf("department:16", "department:17", "department:33"), selected.departmentIds)
        assertEquals(3, MultiPolygon.fromJson(selected.geometryJson).coordinates().size)
    }
    @Test fun `a missing territory fails instead of silently reducing coverage`() {
        assertThrows(IllegalArgumentException::class.java) {
            AdministrativeOfflineAreas(mockk<Context>()).selection(areas, setOf("region:99"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AdministrativeOfflineAreas(mockk<Context>()).selection(areas, setOf("department:99", "department:16"))
        }
    }
}
