package com.forestry.counter.data.maps

import com.forestry.counter.domain.maps.MapsFailure
import com.forestry.counter.domain.maps.MapsProfile
import com.forestry.counter.domain.maps.MapsRouteSource
import com.forestry.counter.domain.maps.MapsServiceException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Current contract tests for the restored parsers; no HTTP request or account token. */
class PublicMapsRepositoryContractTest {
    @Test fun `search retains qualified destinations when another result is malformed`() {
        val results = PublicMapsRepository.parseSearch("""
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"label":"Entrée nord"},"geometry":{"type":"Point","coordinates":[2.0,46.0]}},
              {"type":"Feature","properties":{"label":"Hors domaine"},"geometry":{"type":"Point","coordinates":[700000,6600000]}},
              {"type":"Feature","properties":{"label":"Entrée nord"},"geometry":{"type":"Point","coordinates":[2.0,46.0]}}
            ]}
        """.trimIndent())
        assertEquals(1, results.size)
        assertEquals("Entrée nord", results.single().label)
        assertEquals(2.0, results.single().point.longitude, 0.0)
        assertEquals(46.0, results.single().point.latitude, 0.0)
    }

    @Test fun `IGN route preserves provider quantities and declared source`() {
        val route = PublicMapsRepository.parseRoute(ignRoute, MapsProfile.WALK)
        assertEquals(140.0, route.distanceMetres, 0.0)
        assertEquals(115.0, route.durationSeconds, 0.0)
        assertEquals(MapsProfile.WALK, route.profile)
        assertEquals(MapsRouteSource.IGN, route.source)
        assertEquals("2026-11", route.sourceVersion)
    }

    @Test fun `IGN response with incompatible units profile or CRS is unavailable`() {
        val incompatible = listOf(
            ignRoute.replace("\"meter\"", "\"kilometer\""),
            ignRoute.replace("\"pedestrian\"", "\"car\""),
            ignRoute.replace("EPSG:4326", "EPSG:2154"),
        )
        incompatible.forEach { response ->
            val failure = assertThrows(MapsServiceException::class.java) {
                PublicMapsRepository.parseRoute(response, MapsProfile.WALK)
            }
            assertEquals(MapsFailure.INVALID_RESPONSE, failure.failure)
        }
    }

    @Test fun `Mapbox route retains actual maneuver instructions and geometry`() {
        val route = PublicMapsRepository.parseMapboxRoute("""
            {"code":"Ok","routes":[{"distance":144,"duration":99,
              "geometry":{"type":"LineString","coordinates":[[2.0,46.0],[2.001,46.001]]},
              "legs":[{"steps":[{
                "maneuver":{"instruction":"Tournez à droite","location":[2.0,46.0]},
                "distance":144,"duration":99,
                "geometry":{"type":"LineString","coordinates":[[2.0,46.0],[2.001,46.001]]}
              }]}]
            }]}
        """.trimIndent(), MapsProfile.CAR)
        assertEquals(MapsRouteSource.MAPBOX, route.source)
        assertEquals("Tournez à droite", route.steps.single().instruction)
        assertEquals(route.points, route.steps.single().geometry)
        assertEquals(144.0, route.distanceMetres, 0.0)
        assertEquals(99.0, route.durationSeconds, 0.0)
    }

    @Test fun `Mapbox no route result remains an explicit failure`() {
        val failure = assertThrows(MapsServiceException::class.java) {
            PublicMapsRepository.parseMapboxRoute("""{"code":"NoRoute"}""", MapsProfile.CAR)
        }
        assertEquals(MapsFailure.NO_ROUTE, failure.failure)
    }

    private val ignRoute = """
        {"crs":"EPSG:4326","resource":"bdtopo-valhalla","profile":"pedestrian",
         "distanceUnit":"meter","timeUnit":"second","distance":140,"duration":115,
         "resourceVersion":"2026-11",
         "geometry":{"type":"LineString","coordinates":[[2.0,46.0],[2.001,46.001]]}}
    """.trimIndent()
}
