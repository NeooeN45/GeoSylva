package com.quintessences.geosylva.cartography.mapbox

import java.security.MessageDigest

/** A physical witness of this same observed camera, never a command or a separate viewport. */
data class MapNativeCameraPose(
    val camera: MapCameraPosition,
    val context: MapNativeCameraContext,
    val x: Double, val y: Double, val z: Double,
    val qx: Double, val qy: Double, val qz: Double, val qw: Double,
) {
    init {
        require(camera.nativePose == null)
        require(context.cameraPadding == camera.padding)
        require(listOf(x,y,z,qx,qy,qz,qw).all(Double::isFinite) && z > 0.0)
        require((qx*qx+qy*qy+qz*qz+qw*qw).let {it.isFinite() && it > 0.0})
        require(camera.padding.left+camera.padding.right < context.widthPx &&
            camera.padding.top+camera.padding.bottom < context.heightPx)
    }
    fun belongsTo(value: MapCameraPosition): Boolean = camera == value.copy(nativePose=null)
}

/** Exact rendering inputs. Changing any one makes a saved physical pose inapplicable. */
data class MapNativeCameraContext(
    val styleKey: String, val scopeId: String, val relief: MapRelief,
    val widthPx: Int, val heightPx: Int, val pixelRatio: Double, val verticalFov: Double,
    val centerAltitudeMode: String, val visibleInsets: MapCameraPadding, val cameraPadding: MapCameraPadding,
) {
    init {
        require(styleKey.length == 64 && scopeId.isNotBlank() && widthPx > 0 && heightPx > 0)
        require(pixelRatio.isFinite() && pixelRatio > 0 && verticalFov.isFinite() && verticalFov > 0 && verticalFov < 180)
        require(centerAltitudeMode in setOf("TERRAIN","SEA"))
    }
}

/** Stable across process recreation; stores no token or full style document in saveable state. */
internal fun nativePoseStyleKey(style: MapboxStyleSpec): String {
    val digest=MessageDigest.getInstance("SHA-256")
    listOf(style.id,style.document,style.demSourceUrl.orEmpty()).forEach { value ->
        val bytes=value.toByteArray(Charsets.UTF_8)
        digest.update(byteArrayOf((bytes.size ushr 24).toByte(),(bytes.size ushr 16).toByte(),
            (bytes.size ushr 8).toByte(),bytes.size.toByte()))
        digest.update(bytes)
    }
    return digest.digest().joinToString("") {"%02x".format(it)}
}

/** First nine entries retain the old representation. Malformed/unknown pose data falls back to it. */
internal fun encodeMapCameraPosition(value: MapCameraPosition): List<Any> {
    val logical=listOf(value.longitude,value.latitude,value.zoom,value.bearing,value.pitch,
        value.padding.top,value.padding.left,value.padding.bottom,value.padding.right)
    val pose=value.nativePose?.takeIf {it.belongsTo(value)} ?: return logical
    val c=pose.context
    return logical+listOf("native-pose-v1",c.styleKey,c.scopeId,c.relief.enabled,c.relief.exaggeration,
        c.widthPx,c.heightPx,c.pixelRatio,c.verticalFov,c.centerAltitudeMode,
        c.visibleInsets.top,c.visibleInsets.left,c.visibleInsets.bottom,c.visibleInsets.right,
        pose.x,pose.y,pose.z,pose.qx,pose.qy,pose.qz,pose.qw)
}

internal fun decodeMapCameraPosition(values: List<Any>): MapCameraPosition? {
    val logical=runCatching {
        if(values.size < 9) return null
        fun d(i: Int)=values[i] as Double
        MapCameraPosition(d(0),d(1),d(2),d(3),d(4),MapCameraPadding(d(5),d(6),d(7),d(8)))
    }.getOrNull() ?: return null
    if(values.size != 30 || values[9] != "native-pose-v1") return logical
    return runCatching {
        fun d(i: Int)=values[i] as Double
        val context=MapNativeCameraContext(values[10] as String,values[11] as String,
            MapRelief(values[12] as Boolean,d(13)),values[14] as Int,values[15] as Int,d(16),d(17),values[18] as String,
            MapCameraPadding(d(19),d(20),d(21),d(22)),logical.padding)
        logical.copy(nativePose=MapNativeCameraPose(logical,context,d(23),d(24),d(25),d(26),d(27),d(28),d(29)))
    }.getOrDefault(logical)
}

/** One bootstrap attempt, synchronous setter, then actual newer native Idle and FULL. */
internal class MapNativeCameraRestoration(initial: MapCameraPosition) {
    private var pose=initial.nativePose?.takeIf {it.belongsTo(initial)}
    private var fence: NativeRestoreFence?=null
    val pending: Boolean get()=pose != null
    var failed=false
        private set
    fun cancel() {pose=null;fence=null;failed=false}
    fun fail() {pose=null;fence=null;failed=true}
    fun compatible(context: MapNativeCameraContext): Boolean = pose?.context == context
    fun proposal(context: MapNativeCameraContext): MapNativeCameraPose? =
        pose?.takeIf {fence == null && it.context == context}
    fun submitted(boundary: NativeRestoreFence) {check(pending && fence == null);fence=boundary}
    fun isAwaiting(boundary: NativeRestoreFence): Boolean = fence === boundary && pending
    fun exactNoOp() {check(fence != null);cancel()}
    fun observe(events: NativeRestoreEvents): Boolean {
        val at=fence ?: return false
        if(events.idleSequence <= at.idleSequence || events.fullSequence <= at.fullSequence ||
            events.idleAtMs < at.submittedAtMs || events.fullAtMs < at.submittedAtMs ||
            events.idleAtMs < events.cameraAtMs || events.fullAtMs < events.cameraAtMs) return false
        cancel();return true
    }
}

internal data class NativeRestoreFence(val idleSequence: Long,val fullSequence: Long,val submittedAtMs: Long)
internal class NativeRestoreEvents {
    var cameraAtMs=0L
    var cameraSequence=0L
    var idleAtMs=0L
    var idleSequence=0L
    var fullAtMs=0L
    var fullSequence=0L
    var resourcesAfterMs=Long.MAX_VALUE
    fun readyForPose(): Boolean = idleAtMs > resourcesAfterMs && fullAtMs > resourcesAfterMs &&
        idleAtMs >= cameraAtMs && fullAtMs >= cameraAtMs
    /** A first image of this setter's view, including a fresh Idle after that FULL began. */
    fun readyAfter(at: NativeRestoreFence): Boolean =
        idleSequence > at.idleSequence && fullSequence > at.fullSequence &&
            fullAtMs >= at.submittedAtMs && idleAtMs >= fullAtMs &&
            fullAtMs >= cameraAtMs && idleAtMs >= cameraAtMs
    fun fence(nowMs: Long)=NativeRestoreFence(idleSequence,fullSequence,nowMs)
}
