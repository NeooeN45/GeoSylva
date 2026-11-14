package com.quintessences.geosylva.cartography.mapbox

import com.mapbox.geojson.Point
import org.junit.Assert.*
import org.junit.Test

class OverlaySourceAckGateTest {
    private fun scene(scope: String = "TEST", revision: Long = 1) = MapOverlayScene(scope,revision,listOf(
        MapOverlayFeature(MapFeatureKey("TEST-layer","point"),Point.fromLngLat(2.0,45.0))))
    @Test fun `setter submission and unrelated source metadata can never qualify an applied scene`() {
        val gate=OverlaySourceAckGate();val style=Any();val scene=scene()
        gate.submit(style,scene,"TEST-data")
        assertNull(gate.fullFrame(style,scene,200,true))
        assertNull(gate.acknowledge(style,scene,"terrain","TEST-data"))
        assertNull(gate.acknowledge(style,scene,MAP_OVERLAY_SOURCE_ID,null))
        assertNull(gate.acknowledge(style,scene,MAP_OVERLAY_SOURCE_ID,"TEST-old"))
        assertFalse(gate.awaitingFrame(style,scene))
    }
    @Test fun `only a FULL frame started after exact source acknowledgement and reveal qualifies once`() {
        val gate=OverlaySourceAckGate();val style=Any();val scene=scene()
        gate.submit(style,scene,"TEST-data")
        val ticket=requireNotNull(gate.acknowledge(style,scene,MAP_OVERLAY_SOURCE_ID,"TEST-data"))
        assertNull(gate.fullFrame(style,scene,200,true))
        assertTrue(gate.revealed(ticket,100));assertTrue(gate.awaitingFrame(style,scene))
        assertNull(gate.fullFrame(style,scene,99,true));assertNull(gate.fullFrame(style,scene,100,true))
        assertNull(gate.fullFrame(style,scene,101,false))
        assertSame(ticket,gate.fullFrame(style,scene,101,true))
        assertFalse(gate.awaitingFrame(style,scene));assertNull(gate.fullFrame(style,scene,102,true))
    }
    @Test fun `a newer scope or scene identity rejects late ACK and frames even if a revision was reused`() {
        val gate=OverlaySourceAckGate();val style=Any();val old=scene();val next=scene("TEST-other",1)
        val before=gate.submit(style,old,"TEST-old")
        gate.submit(style,next,"TEST-new")
        assertNull(gate.acknowledge(style,old,MAP_OVERLAY_SOURCE_ID,"TEST-old"))
        assertNull(gate.acknowledge(style,next,MAP_OVERLAY_SOURCE_ID,"TEST-old"))
        assertFalse(gate.revealed(before,100));assertNull(gate.fullFrame(style,old,200,true))
        val now=requireNotNull(gate.acknowledge(style,next,MAP_OVERLAY_SOURCE_ID,"TEST-new"))
        assertTrue(gate.revealed(now,100));assertSame(now,gate.fullFrame(style,next,101,true))
    }
    @Test fun `style generation invalidation and duplicate source callbacks cannot reactivate a retired request`() {
        val gate=OverlaySourceAckGate();val style=Any();val scene=scene()
        gate.submit(style,scene,"TEST-data")
        assertNull(gate.acknowledge(Any(),scene,MAP_OVERLAY_SOURCE_ID,"TEST-data"))
        val ticket=requireNotNull(gate.acknowledge(style,scene,MAP_OVERLAY_SOURCE_ID,"TEST-data"))
        assertNull(gate.acknowledge(style,scene,MAP_OVERLAY_SOURCE_ID,"TEST-data"))
        gate.invalidate();assertFalse(gate.revealed(ticket,100));assertNull(gate.fullFrame(style,scene,200,true))
    }
    @Test fun `empty denied scene has its own processing barrier without inventing a sentinel geometry`() {
        val gate=OverlaySourceAckGate();val style=Any();val empty=MapOverlayScene("TEST-denied",2,emptyList())
        gate.submit(style,empty,"TEST-empty")
        val ticket=requireNotNull(gate.acknowledge(style,empty,MAP_OVERLAY_SOURCE_ID,"TEST-empty"))
        assertTrue(gate.revealed(ticket,100));assertSame(ticket,gate.fullFrame(style,empty,101,true))
        assertTrue(empty.features.isEmpty())
    }
    @Test fun `QA payload carries exact revision and opaque submission plus bounded display label`() {
        val feature=scene().features.single().copy(label="Dernière position",style=MapOverlayStyle(colorHex="#7A8190"))
        val scene=MapOverlayScene("TEST-private-scope",Long.MAX_VALUE,listOf(feature))
        val payload=requireNotNull(overlayFeatureCollection(scene,dataId="TEST-opaque-data").features()).single()
        assertEquals(Long.MAX_VALUE.toString(),payload.getStringProperty(MAP_OVERLAY_SCENE_REVISION_PROPERTY))
        assertEquals("TEST-opaque-data",payload.getStringProperty(MAP_OVERLAY_DATA_ID_PROPERTY))
        assertEquals("Dernière position",payload.getStringProperty(MAP_OVERLAY_LABEL_PROPERTY))
        assertEquals("#7A8190",payload.getStringProperty("gs_color"));assertEquals(feature.geometry,payload.geometry())
        assertFalse(payload.toJson().contains("TEST-private-scope"))
        val long=scene.features.single().copy(label="W".repeat(200))
        val bounded=requireNotNull(overlayFeatureCollection(MapOverlayScene("TEST",3,listOf(long)),dataId="TEST-next").features()).single()
        assertEquals(96,bounded.getStringProperty(MAP_OVERLAY_LABEL_PROPERTY).length)
    }
}
