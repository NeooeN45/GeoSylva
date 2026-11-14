package com.quintessences.geosylva.cartography.mapbox

import android.animation.ValueAnimator
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.android.gestures.RotateGestureDetector
import com.mapbox.android.gestures.ShoveGestureDetector
import com.mapbox.android.gestures.StandardScaleGestureDetector
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.animation.CameraAnimationsLifecycleListener
import com.mapbox.maps.plugin.animation.CameraAnimatorType
import com.mapbox.maps.plugin.animation.MapAnimationOwnerRegistry
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.OnRotateListener
import com.mapbox.maps.plugin.gestures.OnScaleListener
import com.mapbox.maps.plugin.gestures.OnShoveListener
import com.mapbox.maps.plugin.gestures.gestures

/** Observe real SDK input ownership. CameraChanged also contains our own commands and is unsuitable. */
internal fun observeMapCameraGestures(view: MapView, onGesture: () -> Unit): () -> Unit {
    val gestures = view.gestures
    val camera = view.camera
    val move = object : OnMoveListener {
        override fun onMoveBegin(detector: MoveGestureDetector) = onGesture()
        override fun onMove(detector: MoveGestureDetector) = false
        override fun onMoveEnd(detector: MoveGestureDetector) = Unit
    }
    val scale = object : OnScaleListener {
        override fun onScaleBegin(detector: StandardScaleGestureDetector) = onGesture()
        override fun onScale(detector: StandardScaleGestureDetector) = Unit
        override fun onScaleEnd(detector: StandardScaleGestureDetector) = Unit
    }
    val rotate = object : OnRotateListener {
        override fun onRotateBegin(detector: RotateGestureDetector) = onGesture()
        override fun onRotate(detector: RotateGestureDetector) = Unit
        override fun onRotateEnd(detector: RotateGestureDetector) = Unit
    }
    val shove = object : OnShoveListener {
        override fun onShoveBegin(detector: ShoveGestureDetector) = onGesture()
        override fun onShove(detector: ShoveGestureDetector) = Unit
        override fun onShoveEnd(detector: ShoveGestureDetector) = Unit
    }
    // Includes double-tap/quick-zoom animations, which need not begin a Move gesture.
    val animations = object : CameraAnimationsLifecycleListener {
        override fun onAnimatorStarting(type: CameraAnimatorType, animator: ValueAnimator, owner: String?) {
            if (owner == MapAnimationOwnerRegistry.GESTURES || owner == MapAnimationOwnerRegistry.COMPASS) onGesture()
        }
        override fun onAnimatorInterrupting(type: CameraAnimatorType, runningAnimator: ValueAnimator,
            runningAnimatorOwner: String?, newAnimator: ValueAnimator, newAnimatorOwner: String?) = Unit
        override fun onAnimatorEnding(type: CameraAnimatorType, animator: ValueAnimator, owner: String?) = Unit
        override fun onAnimatorCancelling(type: CameraAnimatorType, animator: ValueAnimator, owner: String?) = Unit
    }
    gestures.addOnMoveListener(move)
    gestures.addOnScaleListener(scale)
    gestures.addOnRotateListener(rotate)
    gestures.addOnShoveListener(shove)
    camera.addCameraAnimationsLifecycleListener(animations)
    return {
        gestures.removeOnMoveListener(move)
        gestures.removeOnScaleListener(scale)
        gestures.removeOnRotateListener(rotate)
        gestures.removeOnShoveListener(shove)
        camera.removeCameraAnimationsLifecycleListener(animations)
    }
}
