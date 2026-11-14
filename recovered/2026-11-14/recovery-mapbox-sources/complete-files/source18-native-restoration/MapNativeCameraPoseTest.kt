package com.quintessences.geosylva.cartography.mapbox

import org.junit.Assert.*
import org.junit.Test

class MapNativeCameraPoseTest {
    private val camera=MapCameraPosition(5.548623099999986,45.07304521627991,14.88532104820775,
        -0.0,48.00000000000001,MapCameraPadding(189.0,112.0,707.0,196.0))
    private val context=MapNativeCameraContext(nativePoseStyleKey(MapboxStyles.Satellite),"TEST-map",MapRelief(true),
        720,1560,1.75,36.86989764584402,"TERRAIN",MapCameraPadding(168.0,21.0,560.0,31.5),camera.padding)
    private val pose=MapNativeCameraPose(camera,context,0.5154128419444445,0.3595020030374106,9.265569310487967E-5,
        -0.4067366430758002,0.0,0.0,0.9135454576426009)
    private val saved=camera.copy(nativePose=pose)

    @Test fun old_nine_doubles_restore_the_same_logical_camera() {
        val values=encodeMapCameraPosition(camera)
        assertEquals(9,values.size);assertEquals(camera,decodeMapCameraPosition(values))
        assertFalse(MapNativeCameraRestoration(camera).pending)
    }
    @Test fun full_native_witness_round_trips_without_decimal_conversion() {
        val values=encodeMapCameraPosition(saved)
        assertEquals(30,values.size);assertEquals(saved,decodeMapCameraPosition(values))
        assertEquals(encodeMapCameraPosition(camera),values.take(9))
    }
    @Test fun unknown_or_invalid_native_extension_preserves_the_legacy_camera() {
        val values=encodeMapCameraPosition(saved)
        listOf(values.toMutableList().apply {this[9]="future-version"},
            values.toMutableList().apply {this[25]=Double.NaN},
            values.toMutableList().apply {this[17]=0.0},values.take(20)).forEach {
            assertEquals(camera,decodeMapCameraPosition(it))
        }
        assertNull(decodeMapCameraPosition(listOf(1.0)))
    }
    @Test fun copied_command_coordinates_cannot_inherit_a_physical_witness() {
        val command=saved.copy(latitude=saved.latitude+0.01)
        assertEquals(9,encodeMapCameraPosition(command).size)
        assertFalse(MapNativeCameraRestoration(command).pending)
    }
    @Test fun every_render_context_dimension_is_part_of_compatibility() {
        val restoration=MapNativeCameraRestoration(saved)
        assertEquals(pose,restoration.proposal(context))
        listOf(context.copy(styleKey=nativePoseStyleKey(MapboxStyles.Plan)),context.copy(scopeId="OTHER"),
            context.copy(relief=MapRelief(false)),context.copy(relief=MapRelief(true,2.0)),
            context.copy(widthPx=1440),context.copy(heightPx=3120),context.copy(pixelRatio=3.5),
            context.copy(verticalFov=40.0),context.copy(centerAltitudeMode="SEA"),
            context.copy(visibleInsets=context.visibleInsets.copy(bottom=561.0)),
            context.copy(cameraPadding=context.cameraPadding.copy(top=190.0))).forEach {
            assertFalse(restoration.compatible(it));assertNull(restoration.proposal(it))
        }
    }
    @Test fun loaded_evidence_must_follow_terrain_and_latest_camera_movement() {
        val events=NativeRestoreEvents().apply {resourcesAfterMs=100;idleAtMs=110;fullAtMs=120;cameraAtMs=90}
        assertTrue(events.readyForPose())
        events.cameraAtMs=115;assertFalse(events.readyForPose())
        events.idleAtMs=121;assertTrue(events.readyForPose())
        events.resourcesAfterMs=121;assertFalse(events.readyForPose())
    }
    @Test fun restore_uses_one_application_then_new_idle_and_full_not_older_callbacks() {
        val restoration=MapNativeCameraRestoration(saved)
        val events=NativeRestoreEvents().apply {idleSequence=3;fullSequence=4;idleAtMs=100;fullAtMs=100}
        val boundary=events.fence(110)
        restoration.submitted(boundary);assertNull(restoration.proposal(context))
        events.idleSequence=4;events.fullSequence=5;events.idleAtMs=109;events.fullAtMs=120
        assertFalse(restoration.observe(events))
        events.idleAtMs=120;events.cameraAtMs=121
        assertFalse(restoration.observe(events))
        events.idleAtMs=125;events.fullAtMs=126
        assertTrue(restoration.observe(events));assertFalse(restoration.pending)
        assertFalse(restoration.isAwaiting(boundary));assertNull(restoration.proposal(context))
    }
    @Test fun canceled_intention_cannot_be_revived_by_a_late_callback_or_deadline() {
        val restoration=MapNativeCameraRestoration(saved);val boundary=NativeRestoreFence(3,4,100)
        restoration.submitted(boundary);restoration.cancel()
        val events=NativeRestoreEvents().apply {idleSequence=9;fullSequence=9;idleAtMs=200;fullAtMs=200}
        assertFalse(restoration.observe(events));assertFalse(restoration.isAwaiting(boundary))
        assertNull(restoration.proposal(context))
    }
    @Test fun a_failed_restore_stays_failed_until_a_new_intention_cancels_it() {
        val restoration=MapNativeCameraRestoration(saved)
        restoration.fail();assertTrue(restoration.failed);assertFalse(restoration.pending)
        assertFalse(restoration.observe(NativeRestoreEvents()))
        restoration.cancel();assertFalse(restoration.failed);assertNull(restoration.proposal(context))
    }
    @Test fun exact_native_no_op_completes_only_an_already_submitted_application() {
        val restoration=MapNativeCameraRestoration(saved)
        assertThrows(IllegalStateException::class.java) {restoration.exactNoOp()}
        restoration.submitted(NativeRestoreFence(3,4,100));restoration.exactNoOp()
        assertFalse(restoration.pending);assertFalse(restoration.failed)
    }
    @Test fun invalid_native_pose_is_rejected_before_it_can_be_saved() {
        assertThrows(IllegalArgumentException::class.java) {pose.copy(z=Double.NaN)}
        assertThrows(IllegalArgumentException::class.java) {pose.copy(z=0.0)}
        assertThrows(IllegalArgumentException::class.java) {pose.copy(qx=0.0,qy=0.0,qz=0.0,qw=0.0)}
    }
}
