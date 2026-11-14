package com.quintessences.geosylva.cartography.mapbox

import com.mapbox.geojson.Geometry
import com.mapbox.geojson.GeometryCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import org.junit.Assert.*
import org.junit.Test

class PointLabelPlacementTest {
    @Test fun `point pills sit above the real circle stroke and selected radius without moving coordinates`() {
        val style=MapOverlayStyle(pointRadius=6.0)
        assertEquals(PointLabelPlacement("bottom",-11.0),pointLabelPlacement(style,false,false))
        assertEquals(PointLabelPlacement("bottom",-13.0),pointLabelPlacement(style,true,false))
        val point=Point.fromLngLat(2.0,45.0)
        val feature=MapOverlayFeature(MapFeatureKey("TEST","point"),point,style,label="P-01")
        val payload=requireNotNull(overlayFeatureCollection(MapOverlayScene("TEST",1,listOf(feature))).features()).single()
        assertEquals(point,payload.geometry());assertEquals("bottom",payload.getStringProperty("gs_label_point_anchor"))
        assertEquals(-11.0,payload.getProperty("gs_label_point_offset").asJsonArray[1].asDouble,0.0)
    }
    @Test fun `pin labels clear admitted sprite height and selection scale while fallback circles use their own height`() {
        for (symbol in listOf(MapOverlayPointSymbol.PIN, MapOverlayPointSymbol.PIN_CHECKED)) {
            val style=MapOverlayStyle(pointRadius=6.0,pointSymbol=symbol)
            assertEquals(-25.0,pointLabelPlacement(style,false,true,2f).offsetY,0.0)
            assertEquals(-29.4,pointLabelPlacement(style,true,true,2f).offsetY,0.0001)
            assertEquals(-11.0,pointLabelPlacement(style,false,false,2f).offsetY,0.0)
            assertEquals(-13.0,pointLabelPlacement(style,true,false,2f).offsetY,0.0)
        }
    }
    @Test fun `a lower point pill preserves position identity and label while clearing the circle stroke`() {
        val style=MapOverlayStyle(pointRadius=9.0,strokeWidth=3.0,
            pointLabelSide=MapOverlayPointLabelSide.BELOW)
        assertEquals(PointLabelPlacement("top",15.0),pointLabelPlacement(style,false,false,2f))
        assertEquals(PointLabelPlacement("top",17.0),pointLabelPlacement(style,true,false,2f))
        val coordinate=Point.fromLngLat(5.551654,45.071787)
        val key=MapFeatureKey("maps-location","position")
        val feature=MapOverlayFeature(key,coordinate,style,label="Dernière position TEST",selectable=false)
        val payload=requireNotNull(overlayFeatureCollection(MapOverlayScene("TEST-arrived",4,listOf(feature)),
            dataId="TEST-current-generation",renderDensity=2f).features()).single()
        assertEquals(coordinate,payload.geometry())
        assertEquals(key.layerId,payload.getStringProperty(OVERLAY_LAYER_PROPERTY))
        assertEquals(key.featureId,payload.getStringProperty(OVERLAY_FEATURE_PROPERTY))
        assertEquals("Dernière position TEST",payload.getStringProperty(MAP_OVERLAY_LABEL_PROPERTY))
        assertEquals("TEST-current-generation",payload.getStringProperty(MAP_OVERLAY_DATA_ID_PROPERTY))
        assertFalse(payload.getBooleanProperty("gs_selectable"))
        assertEquals("top",payload.getStringProperty("gs_label_point_anchor"))
        assertEquals(15.0,payload.getProperty("gs_label_point_offset").asJsonArray[1].asDouble,0.0)
        assertEquals(PointLabelPlacement("bottom",-15.0),pointLabelPlacement(
            style.copy(pointLabelSide=MapOverlayPointLabelSide.ABOVE),false,false,2f))
    }
    @Test fun `lower pin pills clear its anchored tip while centred badges and invisible anchors do not move`() {
        val pin=MapOverlayStyle(pointSymbol=MapOverlayPointSymbol.PIN_CHECKED,pointRadius=10.0,
            pointLabelSide=MapOverlayPointLabelSide.BELOW)
        assertEquals(PointLabelPlacement("top",3.0),pointLabelPlacement(pin,true,true,2f))
        assertEquals(PointLabelPlacement("top",17.0),pointLabelPlacement(pin,true,false,2f))
        assertEquals(PointLabelPlacement("center",0.0),pointLabelPlacement(pin.copy(pointRadius=0.0),false,false))
        assertEquals(PointLabelPlacement("center",0.0),pointLabelPlacement(pin.copy(labelBadge=MapOverlayLabelBadge.CIRCLE),false,false))
    }

    @Test fun `measurement circles and invisible point label anchors remain centred`() {
        assertEquals(PointLabelPlacement("center",0.0),pointLabelPlacement(MapOverlayStyle(labelBadge=MapOverlayLabelBadge.CIRCLE),true,false))
        assertEquals(PointLabelPlacement("center",0.0),pointLabelPlacement(MapOverlayStyle(pointRadius=0.0),true,false))
        val a=Point.fromLngLat(2.0,45.0);val b=Point.fromLngLat(2.1,45.1)
        val collection=GeometryCollection.fromGeometries(listOf<Geometry>(a,LineString.fromLngLats(listOf(a,b))))
        val feature=MapOverlayFeature(MapFeatureKey("TEST","mixed"),collection,label="TEST")
        val payload=requireNotNull(overlayFeatureCollection(MapOverlayScene("TEST",1,listOf(feature))).features()).single()
        assertEquals(collection,payload.geometry()) // Native geometry-type expression, rather than a business ID, picks each child's anchor.
    }
}
