package com.forestry.counter.presentation.screens.forestry

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.forestry.counter.R
import com.forestry.counter.domain.location.MapLibreHttpConfig
import com.forestry.counter.domain.location.MapSourcePolicy
import com.forestry.counter.domain.location.ParcelBounds
import com.forestry.counter.domain.location.ParcelCoordinate
import com.forestry.counter.domain.location.ParcelGeometry
import com.forestry.counter.domain.location.SamplingFootprint
import com.forestry.counter.network.SecureHttpClient
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.MultiPolygon
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.MapboxMapOptions
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.expressions.Expression
import com.mapbox.mapboxsdk.style.layers.CircleLayer
import com.mapbox.mapboxsdk.style.layers.FillLayer
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.layers.RasterLayer
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.mapboxsdk.style.sources.RasterSource
import com.mapbox.mapboxsdk.style.sources.TileSet
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.tan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response

private const val SELECTION_STYLE = """{
  "version": 8,
  "name": "Sélection géographique",
  "sources": {},
  "layers": [{"id":"background","type":"background","paint":{"background-color":"#EAF2EE"}}]
}"""
private const val PARCEL_SOURCE = "selection-parcels"
private const val PARCEL_LABEL_SOURCE = "selection-parcel-labels"
private const val PARENT_SOURCE = "selection-parent"
private const val POINT_SOURCE = "selection-plot-points"
private const val FOOTPRINT_SOURCE = "selection-plot-footprints"
private const val GRID_SOURCE = "selection-grid-cells"
private const val PHOTO_SOURCE = "selection-ign-orthophoto"
private const val PHOTO_LAYER = "selection-ign-orthophoto-layer"
private const val EMPTY_COLLECTION = """{"type":"FeatureCollection","features":[]}"""

private enum class SelectionPhotoState { LOCAL, OFFLINE, LOADING, AVAILABLE, UNAVAILABLE }

/**
 * Reusable renderer for a single source parcel, its boundary and plot centres.
 *
 * The parent supplies qualified vector data and persists the selection. A map
 * click returns a coordinate, never a guessed parcel or automatic GPS fix.
 * Viewport notifications occur only after the camera stops; changing the
 * selected feature leaves the user's camera untouched. Labels use Android
 * bitmaps, so this local style requires neither remote glyphs nor sprites.
 * IGN orthophotography is an optional qualified online background, not an
 * offline parcel pack, terrain measurement or source of access rights.
 */
@Composable
fun GeographicSelectionMap(
    featureCollectionJson: String,
    selectedFeatureId: String?,
    initialBounds: ParcelBounds?,
    onMapClick: (ParcelCoordinate) -> Unit,
    onViewportChanged: (ParcelBounds, Double) -> Unit,
    modifier: Modifier = Modifier,
    plotPoints: List<ParcelCoordinate> = emptyList(),
    parcelGeometry: ParcelGeometry? = null,
    plotFootprints: List<SamplingFootprint> = emptyList(),
    gridCells: List<ParcelGeometry> = emptyList(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestClick by rememberUpdatedState(onMapClick)
    val latestViewport by rememberUpdatedState(onViewportChanged)
    val latestSelection by rememberUpdatedState(selectedFeatureId)
    var readyMap by remember { mutableStateOf<MapboxMap?>(null) }
    var renderFailed by remember { mutableStateOf(false) }
    var savedLatitude by rememberSaveable { mutableStateOf(46.6) }
    var savedLongitude by rememberSaveable { mutableStateOf(2.4) }
    var savedZoom by rememberSaveable { mutableStateOf(4.0) }
    var savedPadding by rememberSaveable { mutableStateOf(CameraPosition.DEFAULT.padding?.copyOf()) }
    var hasSavedCamera by rememberSaveable { mutableStateOf(false) }
    var framedBoundsKey by rememberSaveable { mutableStateOf<String?>(null) }
    var preferPhoto by rememberSaveable { mutableStateOf(true) }
    var photoUsable by remember { mutableStateOf(false) }
    var photoState by remember { mutableStateOf(SelectionPhotoState.LOCAL) }
    var cameraTile by remember { mutableStateOf(selectionPhotoTile(savedLatitude, savedLongitude, savedZoom)) }
    val hasInternet by rememberSelectionInternet(context)
    val parcelImageIds = remember { mutableSetOf<String>() }
    val pointImageIds = remember { mutableSetOf<String>() }

    val renderData by produceState<Result<SelectionRenderData>?>(null, featureCollectionJson) {
        value = null
        value = try {
            Result.success(withContext(Dispatchers.Default) { selectionRenderData(featureCollectionJson) })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.failure(IllegalArgumentException("Invalid parcel display data"))
        }
    }
    val latestRenderData by rememberUpdatedState(renderData?.getOrNull())
    val photoClient = remember(context) {
        SecureHttpClient.createSecureClient(context).newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
    LaunchedEffect(readyMap, hasInternet, preferPhoto, cameraTile) {
        if (readyMap == null || !hasInternet || !preferPhoto) {
            photoUsable = false
            photoState = if (!hasInternet) SelectionPhotoState.OFFLINE else SelectionPhotoState.LOCAL
            return@LaunchedEffect
        }
        if (!photoUsable) photoState = SelectionPhotoState.LOADING
        delay(300)
        try {
            photoClient.newCall(Request.Builder().url(cameraTile.url()).build()).awaitSelectionResponse().use { response ->
                withContext(Dispatchers.IO) {
                    val type = requireNotNull(response.body?.contentType())
                    require(response.isSuccessful && type.type == "image" && type.subtype == "jpeg")
                    val output = ByteArrayOutputStream()
                    requireNotNull(response.body).byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            coroutineContext.ensureActive()
                            val size = input.read(buffer)
                            if (size < 0) break
                            require(output.size() + size <= 2 * 1024 * 1024)
                            output.write(buffer, 0, size)
                        }
                    }
                    val bytes = output.toByteArray()
                    val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, dimensions)
                    require(dimensions.outWidth == 256 && dimensions.outHeight == 256)
                }
            }
            photoUsable = true
            photoState = SelectionPhotoState.AVAILABLE
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            photoUsable = false
            photoState = SelectionPhotoState.UNAVAILABLE
        }
    }

    val mapView = remember(context, lifecycleOwner) {
        try {
            Mapbox.getInstance(context)
            MapLibreHttpConfig.configure(context)
            MapView(context, MapboxMapOptions.createFromAttributes(context).textureMode(true)).apply {
                setBackgroundColor(android.graphics.Color.parseColor("#EAF2EE"))
                onCreate(null)
            }
        } catch (_: Exception) {
            renderFailed = true
            null
        }
    }
    val mapLifecycle = remember(mapView) { mapView?.let(::SelectionMapLifecycle) }
    DisposableEffect(mapView, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> mapLifecycle?.handle(event) }
        val failureListener = MapView.OnDidFailLoadingMapListener {
            if (mapLifecycle?.destroyed == false) {
                if (readyMap != null) {
                    photoUsable = false
                    photoState = SelectionPhotoState.UNAVAILABLE
                } else renderFailed = true
            }
        }
        val memoryCallbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(configuration: Configuration) = Unit
            override fun onLowMemory() {
                if (mapLifecycle?.destroyed == false) mapView?.onLowMemory()
            }
        }
        mapView?.addOnDidFailLoadingMapListener(failureListener)
        context.registerComponentCallbacks(memoryCallbacks)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            context.unregisterComponentCallbacks(memoryCallbacks)
            mapView?.removeOnDidFailLoadingMapListener(failureListener)
            readyMap = null
            mapLifecycle?.destroy()
            parcelImageIds.clear()
            pointImageIds.clear()
        }
    }

    LaunchedEffect(mapView) {
        val view = mapView ?: return@LaunchedEffect
        view.getMapAsync { map ->
            if (mapLifecycle?.destroyed != false) return@getMapAsync
            try {
                map.uiSettings.apply {
                    isRotateGesturesEnabled = false
                    isTiltGesturesEnabled = false
                    isCompassEnabled = false
                    isZoomGesturesEnabled = true
                    isScrollGesturesEnabled = true
                    logoGravity = Gravity.TOP or Gravity.START
                    attributionGravity = Gravity.TOP or Gravity.START
                    val density = context.resources.displayMetrics.density
                    setLogoMargins((8 * density).roundToInt(), (8 * density).roundToInt(), 0, 0)
                    setAttributionMargins((8 * density).roundToInt(), (30 * density).roundToInt(), 0, 0)
                }
                map.setMinZoomPreference(2.0)
                map.setMaxZoomPreference(22.0)
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(savedLatitude, savedLongitude))
                    .zoom(savedZoom).padding(savedPadding?.copyOf()).build()
                map.setStyle(Style.Builder().fromJson(SELECTION_STYLE)) { style ->
                    if (mapLifecycle?.destroyed != false) return@setStyle
                    try {
                        addSelectionLayers(style)
                        latestRenderData?.let { applySelectionData(style, it, parcelImageIds) }
                        applySingleParcelSelection(style, latestSelection)
                        val moveListener = MapboxMap.OnCameraMoveListener {
                            val camera = map.cameraPosition
                            camera.target?.let { target ->
                                savedLatitude = target.latitude
                                savedLongitude = target.longitude
                            }
                            savedZoom = camera.zoom
                            savedPadding = camera.padding?.copyOf()
                            hasSavedCamera = true
                        }
                        val idleListener = MapboxMap.OnCameraIdleListener {
                            if (!mapLifecycle.destroyed) {
                                moveListener.onCameraMove()
                                cameraTile = selectionPhotoTile(savedLatitude, savedLongitude, savedZoom)
                                val bounds = map.projection.visibleRegion.latLngBounds
                                if (bounds.longitudeWest.isFinite() && bounds.longitudeWest in -180.0..180.0 &&
                                    bounds.longitudeEast.isFinite() && bounds.longitudeEast in -180.0..180.0 &&
                                    bounds.latitudeSouth.isFinite() && bounds.latitudeSouth in -90.0..90.0 &&
                                    bounds.latitudeNorth.isFinite() && bounds.latitudeNorth in -90.0..90.0 &&
                                    bounds.longitudeWest < bounds.longitudeEast && bounds.latitudeSouth < bounds.latitudeNorth
                                ) {
                                    latestViewport(ParcelBounds(bounds.longitudeWest, bounds.latitudeSouth, bounds.longitudeEast, bounds.latitudeNorth), savedZoom)
                                }
                            }
                        }
                        val clickListener = MapboxMap.OnMapClickListener { location ->
                            if (!location.longitude.isFinite() || !location.latitude.isFinite() ||
                                location.latitude !in -90.0..90.0
                            ) return@OnMapClickListener false
                            latestClick(ParcelCoordinate(wrappedSelectionLongitude(location.longitude), location.latitude))
                            true
                        }
                        map.addOnCameraMoveListener(moveListener)
                        map.addOnCameraIdleListener(idleListener)
                        map.addOnMapClickListener(clickListener)
                        mapLifecycle.beforeDestroy = {
                            map.removeOnCameraMoveListener(moveListener)
                            map.removeOnCameraIdleListener(idleListener)
                            map.removeOnMapClickListener(clickListener)
                        }
                        readyMap = map
                        view.post { if (!mapLifecycle.destroyed) idleListener.onCameraIdle() }
                    } catch (_: Exception) {
                        renderFailed = true
                    }
                }
            } catch (_: Exception) {
                renderFailed = true
            }
        }
    }

    LaunchedEffect(readyMap, initialBounds) {
        val map = readyMap ?: return@LaunchedEffect
        val bounds = initialBounds ?: return@LaunchedEffect
        val key = "${bounds.west},${bounds.south},${bounds.east},${bounds.north}"
        if (hasSavedCamera && framedBoundsKey == key) return@LaunchedEffect
        mapView?.post {
            if (mapLifecycle?.destroyed == false) {
                try {
                    map.moveCamera(selectionCamera(bounds, context.resources.displayMetrics.density, mapView.width, mapView.height))
                    framedBoundsKey = key
                    hasSavedCamera = true
                } catch (_: Exception) {
                    renderFailed = true
                }
            }
        }
    }
    LaunchedEffect(readyMap, renderData) {
        readyMap?.style?.let { style ->
            try {
                applySelectionData(style, renderData?.getOrNull() ?: emptySelectionData(), parcelImageIds)
            } catch (_: Exception) {
                renderFailed = true
            }
        }
    }
    LaunchedEffect(readyMap, selectedFeatureId, photoUsable) {
        readyMap?.style?.let { style ->
            try {
                setSelectionPhoto(style, photoUsable)
                applySingleParcelSelection(style, selectedFeatureId)
            } catch (_: Exception) {
                photoUsable = false
                photoState = SelectionPhotoState.UNAVAILABLE
            }
        }
    }
    LaunchedEffect(readyMap, parcelGeometry, plotPoints, plotFootprints, gridCells) {
        readyMap?.style?.let { style ->
            try {
                val parent = parcelGeometry?.let {
                    FeatureCollection.fromFeatures(listOf(Feature.fromJson("""{"type":"Feature","properties":{},"geometry":${it.toGeoJson()}}""")))
                } ?: FeatureCollection.fromJson(EMPTY_COLLECTION)
                style.getSourceAs<GeoJsonSource>(PARENT_SOURCE)?.setGeoJson(parent)
                require(plotPoints.size <= 500)
                require(plotFootprints.size <= 500 && gridCells.size <= 500)
                style.getSourceAs<GeoJsonSource>(FOOTPRINT_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(plotFootprints.map {
                    Feature.fromJson("""{"type":"Feature","properties":{"boundary_warning":${it.crossesBoundary}},"geometry":${it.geometry.toGeoJson()}}""")
                }))
                style.getSourceAs<GeoJsonSource>(GRID_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(gridCells.map {
                    Feature.fromJson("""{"type":"Feature","properties":{},"geometry":${it.toGeoJson()}}""")
                }))
                val images = linkedMapOf<String, Bitmap>()
                val points = plotPoints.mapIndexed { index, coordinate ->
                    require(coordinate.longitude.isFinite() && coordinate.longitude in -180.0..180.0 &&
                        coordinate.latitude.isFinite() && coordinate.latitude in -90.0..90.0)
                    val imageId = "plot-label-$index"
                    images[imageId] = selectionLabelBitmap((index + 1).toString())
                    Feature.fromGeometry(Point.fromLngLat(coordinate.longitude, coordinate.latitude)).apply {
                        addStringProperty("label_image", imageId)
                    }
                }
                replaceSelectionImages(style, images, pointImageIds)
                style.getSourceAs<GeoJsonSource>(POINT_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(points))
            } catch (_: Exception) {
                renderFailed = true
            }
        }
    }

    Box(modifier) {
        if (mapView != null) AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        if (renderFailed || renderData?.isFailure == true) {
            Surface(
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(stringResource(R.string.pack_map_render_failed), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        } else if (readyMap == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 2.dp,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = { readyMap?.animateCamera(CameraUpdateFactory.zoomIn(), 200) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Add, stringResource(R.string.pack_map_zoom_in))
                    }
                    IconButton(onClick = { readyMap?.animateCamera(CameraUpdateFactory.zoomOut(), 200) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Remove, stringResource(R.string.pack_map_zoom_out))
                    }
                    val recenterBounds = initialBounds ?: parcelGeometry?.bounds
                    if (recenterBounds != null) {
                        IconButton(
                            onClick = {
                                readyMap?.animateCamera(selectionCamera(recenterBounds, context.resources.displayMetrics.density, mapView?.width ?: 0, mapView?.height ?: 0), 300)
                            },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Default.CenterFocusStrong, stringResource(R.string.geographic_map_recenter))
                        }
                    }
                }
            }
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 52.dp).widthIn(max = 210.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            ) {
                TextButton(onClick = { preferPhoto = !preferPhoto }) {
                    Icon(Icons.Default.Layers, null, Modifier.size(16.dp))
                    val label = when (photoState) {
                        SelectionPhotoState.AVAILABLE -> R.string.pack_map_photo_active
                        SelectionPhotoState.LOADING -> R.string.pack_map_photo_loading
                        SelectionPhotoState.OFFLINE -> R.string.pack_map_local_offline
                        SelectionPhotoState.UNAVAILABLE -> R.string.pack_map_photo_unavailable
                        SelectionPhotoState.LOCAL -> R.string.pack_map_local_selected
                    }
                    Text(stringResource(label), modifier = Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (photoUsable) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).widthIn(max = 310.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                ) {
                    Text(stringResource(R.string.pack_map_photo_attribution), modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private data class SelectionRenderData(
    val parcels: FeatureCollection,
    val labels: FeatureCollection,
    val images: Map<String, Bitmap>,
)

private fun emptySelectionData() = SelectionRenderData(
    FeatureCollection.fromJson(EMPTY_COLLECTION), FeatureCollection.fromJson(EMPTY_COLLECTION), emptyMap(),
)

private fun selectionRenderData(raw: String): SelectionRenderData {
    require(raw.length <= 4 * 1024 * 1024)
    val collection = FeatureCollection.fromJson(raw)
    val features = collection.features().orEmpty()
    require(features.size <= 500)
    val ids = mutableSetOf<String>()
    val labels = mutableListOf<Feature>()
    val images = linkedMapOf<String, Bitmap>()
    features.forEachIndexed { index, feature ->
        require(feature.geometry() is Polygon || feature.geometry() is MultiPolygon)
        val id = selectionStringProperty(feature, "parcel_id")
        require(id != null && id.isNotBlank() && id.length <= 200 && ids.add(id))
        val geometry = ParcelGeometry.fromGeoJson(requireNotNull(feature.geometry()).toJson())
        val name = (selectionStringProperty(feature, "parcel_label") ?: selectionStringProperty(feature, "label") ?: id).take(60)
        val anchor = selectionLabelAnchor(geometry, feature)
        if (anchor != null) {
            val imageId = "parcel-label-$index"
            images[imageId] = selectionLabelBitmap(name)
            labels += Feature.fromGeometry(Point.fromLngLat(anchor.longitude, anchor.latitude)).apply {
                addStringProperty("label_image", imageId)
            }
        }
    }
    return SelectionRenderData(collection, FeatureCollection.fromFeatures(labels), images)
}

private fun selectionStringProperty(feature: Feature, key: String): String? = feature.getProperty(key)?.let { value ->
    if (value.isJsonPrimitive && value.asJsonPrimitive.isString) value.asString else null
}

/** Display anchor only; never persisted as a survey or navigation coordinate. */
private fun selectionLabelAnchor(geometry: ParcelGeometry, feature: Feature): ParcelCoordinate? {
    val bounds = geometry.bounds
    if (geometry.contains(bounds.center)) return bounds.center
    for (y in 1..7) for (x in 1..7) {
        val point = ParcelCoordinate(bounds.west + (bounds.east - bounds.west) * x / 8, bounds.south + (bounds.north - bounds.south) * y / 8)
        if (geometry.contains(point)) return point
    }
    val vertex = when (val polygon = feature.geometry()) {
        is Polygon -> polygon.coordinates().firstOrNull()?.firstOrNull()
        is MultiPolygon -> polygon.coordinates().firstOrNull()?.firstOrNull()?.firstOrNull()
        else -> null
    }
    return vertex?.let { ParcelCoordinate(it.longitude(), it.latitude()) }
}

private fun selectionLabelBitmap(name: String): Bitmap {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 15f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val metrics = paint.fontMetrics
    val width = (ceil(paint.measureText(name)).toInt() + 12).coerceAtLeast(1)
    val height = (ceil(metrics.descent - metrics.ascent).toInt() + 12).coerceAtLeast(1)
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        val baseline = 6f - metrics.ascent
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = android.graphics.Color.parseColor("#103F35")
        canvas.drawText(name, 6f, baseline, paint)
        paint.style = Paint.Style.FILL
        paint.color = android.graphics.Color.WHITE
        canvas.drawText(name, 6f, baseline, paint)
    }
}

private fun addSelectionLayers(style: Style) {
    listOf(PARCEL_SOURCE, PARCEL_LABEL_SOURCE, PARENT_SOURCE, POINT_SOURCE, FOOTPRINT_SOURCE, GRID_SOURCE).forEach {
        style.addSource(GeoJsonSource(it, EMPTY_COLLECTION))
    }
    style.addLayer(FillLayer("$PARCEL_SOURCE-fill", PARCEL_SOURCE).withProperties(PropertyFactory.fillColor("#328CA0"), PropertyFactory.fillOpacity(0.12f)))
    style.addLayer(LineLayer("$PARCEL_SOURCE-halo", PARCEL_SOURCE).withProperties(PropertyFactory.lineColor("#FFFFFF"), PropertyFactory.lineWidth(4f), PropertyFactory.lineOpacity(0.8f)))
    style.addLayer(LineLayer("$PARCEL_SOURCE-outline", PARCEL_SOURCE).withProperties(PropertyFactory.lineColor("#156579"), PropertyFactory.lineWidth(1.8f)))
    style.addLayer(LineLayer("$PARCEL_SOURCE-selected", PARCEL_SOURCE).withFilter(Expression.literal(false)).withProperties(PropertyFactory.lineColor("#117B43"), PropertyFactory.lineWidth(4f)))
    style.addLayer(FillLayer("$PARENT_SOURCE-fill", PARENT_SOURCE).withProperties(PropertyFactory.fillColor("#37A977"), PropertyFactory.fillOpacity(0.18f)))
    style.addLayer(LineLayer("$PARENT_SOURCE-halo", PARENT_SOURCE).withProperties(PropertyFactory.lineColor("#FFFFFF"), PropertyFactory.lineWidth(6f), PropertyFactory.lineOpacity(0.9f)))
    style.addLayer(LineLayer("$PARENT_SOURCE-outline", PARENT_SOURCE).withProperties(PropertyFactory.lineColor("#0D754B"), PropertyFactory.lineWidth(3.5f)))
    style.addLayer(LineLayer("$GRID_SOURCE-outline", GRID_SOURCE).withProperties(
        PropertyFactory.lineColor("#566A61"), PropertyFactory.lineWidth(1f), PropertyFactory.lineOpacity(0.7f),
        PropertyFactory.lineDasharray(arrayOf(3f, 2f)),
    ))
    val footprintColor = Expression.switchCase(Expression.eq(Expression.get("boundary_warning"), Expression.literal(true)),
        Expression.color(android.graphics.Color.parseColor("#C66C12")), Expression.color(android.graphics.Color.parseColor("#176CAA")))
    style.addLayer(FillLayer("$FOOTPRINT_SOURCE-fill", FOOTPRINT_SOURCE).withProperties(
        PropertyFactory.fillColor(footprintColor), PropertyFactory.fillOpacity(0.22f),
    ))
    style.addLayer(LineLayer("$FOOTPRINT_SOURCE-outline", FOOTPRINT_SOURCE).withProperties(
        PropertyFactory.lineColor(footprintColor), PropertyFactory.lineWidth(2f),
    ))
    style.addLayer(SymbolLayer("$PARCEL_SOURCE-labels", PARCEL_LABEL_SOURCE).withProperties(
        PropertyFactory.iconImage(Expression.get("label_image")), PropertyFactory.iconAllowOverlap(false), PropertyFactory.iconIgnorePlacement(false), PropertyFactory.iconPadding(4f),
    ))
    style.addLayer(CircleLayer("$POINT_SOURCE-circles", POINT_SOURCE).withProperties(
        PropertyFactory.circleRadius(7f), PropertyFactory.circleColor("#176CAA"), PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(3f),
    ))
    style.addLayer(SymbolLayer("$POINT_SOURCE-labels", POINT_SOURCE).withProperties(
        PropertyFactory.iconImage(Expression.get("label_image")), PropertyFactory.iconAnchor(com.mapbox.mapboxsdk.style.layers.Property.ICON_ANCHOR_BOTTOM), PropertyFactory.iconOffset(arrayOf(0f, -10f)),
        PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true),
    ))
}

private fun applySelectionData(style: Style, data: SelectionRenderData, previousImageIds: MutableSet<String>) {
    replaceSelectionImages(style, data.images, previousImageIds)
    style.getSourceAs<GeoJsonSource>(PARCEL_SOURCE)?.setGeoJson(data.parcels)
    style.getSourceAs<GeoJsonSource>(PARCEL_LABEL_SOURCE)?.setGeoJson(data.labels)
}

private fun replaceSelectionImages(style: Style, images: Map<String, Bitmap>, previousImageIds: MutableSet<String>) {
    (previousImageIds - images.keys).forEach { style.removeImage(it) }
    if (images.isNotEmpty()) style.addImages(HashMap(images))
    previousImageIds.clear()
    previousImageIds.addAll(images.keys)
}

private fun applySingleParcelSelection(style: Style, id: String?) {
    val selected = if (id == null) Expression.literal(false) else Expression.eq(Expression.get("parcel_id"), Expression.literal(id))
    style.getLayerAs<LineLayer>("$PARCEL_SOURCE-selected")?.setFilter(selected)
    style.getLayer("$PARCEL_SOURCE-fill")?.setProperties(
        PropertyFactory.fillColor(Expression.switchCase(selected, Expression.color(android.graphics.Color.parseColor("#26A76A")), Expression.color(android.graphics.Color.parseColor("#328CA0")))),
        PropertyFactory.fillOpacity(Expression.switchCase(selected, Expression.literal(0.3f), Expression.literal(0.1f))),
    )
}

private fun setSelectionPhoto(style: Style, visible: Boolean) {
    if (!visible) {
        style.removeLayer(PHOTO_LAYER)
        style.removeSource(PHOTO_SOURCE)
    } else if (style.getLayer(PHOTO_LAYER) == null) {
        val template = MapSourcePolicy.ORTHOPHOTO_TEMPLATE
        require(MapSourcePolicy.isOnlineTemplateQualified(template))
        val tiles = TileSet("2.1.0", template).apply {
            minZoom = 0f
            maxZoom = 19f
            scheme = "xyz"
            attribution = "<a href=\"https://cartes.gouv.fr/rechercher-une-donnee/dataset/IGNF_BD-ORTHO\">${MapSourcePolicy.ORTHOPHOTO_ATTRIBUTION}</a>"
        }
        style.addSource(RasterSource(PHOTO_SOURCE, tiles, 256))
        style.addLayerAbove(RasterLayer(PHOTO_LAYER, PHOTO_SOURCE), "background")
    }
}

private fun selectionCamera(bounds: ParcelBounds, density: Float, width: Int = 0, height: Int = 0): com.mapbox.mapboxsdk.camera.CameraUpdate {
    // A compact plot form must still leave a positive map area at large font sizes.
    fun padding(dp: Int, dimension: Int, fraction: Float): Int =
        (dp * density).roundToInt().let { if (dimension > 0) it.coerceAtMost((dimension * fraction).roundToInt()) else it }
    return CameraUpdateFactory.newLatLngBounds(
        LatLngBounds.from(bounds.north, bounds.east, bounds.south, bounds.west),
        padding(28, width, 0.15f), padding(98, height, 0.25f),
        padding(74, width, 0.25f), padding(62, height, 0.2f),
    )
}

private data class SelectionPhotoTile(val zoom: Int, val x: Int, val y: Int) {
    fun url(): String = MapSourcePolicy.ORTHOPHOTO_TEMPLATE.replace("{z}", zoom.toString()).replace("{x}", x.toString()).replace("{y}", y.toString())
}

private fun selectionPhotoTile(latitude: Double, longitude: Double, zoom: Double): SelectionPhotoTile {
    val level = floor(zoom).toInt().coerceIn(0, 19)
    val count = 1 shl level
    val radians = Math.toRadians(latitude.coerceIn(-85.05112878, 85.05112878))
    val x = floor((wrappedSelectionLongitude(longitude) + 180) / 360 * count).toInt().coerceIn(0, count - 1)
    val y = floor((1 - ln(tan(radians) + 1 / cos(radians)) / Math.PI) / 2 * count).toInt().coerceIn(0, count - 1)
    return SelectionPhotoTile(level, x, y)
}

/** MapLibre permits world copies; this preserves the same physical longitude. */
private fun wrappedSelectionLongitude(longitude: Double): Double = ((longitude + 180) % 360 + 360) % 360 - 180

@Composable
private fun rememberSelectionInternet(context: Context): State<Boolean> {
    val manager = remember(context) { context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager }
    fun connected(): Boolean = runCatching {
        manager?.activeNetwork?.let { manager.getNetworkCapabilities(it) }?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(false)
    val connection = remember(manager) { mutableStateOf(connected()) }
    DisposableEffect(manager) {
        val handler = Handler(Looper.getMainLooper())
        var active = true
        val callback = object : ConnectivityManager.NetworkCallback() {
            private fun update() { handler.post { if (active) connection.value = connected() } }
            override fun onAvailable(network: Network) = update()
            override fun onLost(network: Network) = update()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = update()
        }
        val registered = runCatching { manager?.registerDefaultNetworkCallback(callback); manager != null }.getOrDefault(false)
        onDispose {
            active = false
            handler.removeCallbacksAndMessages(null)
            if (registered) runCatching { manager?.unregisterNetworkCallback(callback) }
        }
    }
    return connection
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private suspend fun Call.awaitSelectionResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, error: IOException) {
            if (continuation.isActive) continuation.resumeWithException(error)
        }
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { response.close() }
        }
    })
}

private class SelectionMapLifecycle(private val view: MapView) {
    var destroyed = false
        private set
    var beforeDestroy: (() -> Unit)? = null
    private var started = false
    private var resumed = false

    fun handle(event: Lifecycle.Event) {
        if (destroyed) return
        when (event) {
            Lifecycle.Event.ON_START -> if (!started) { view.onStart(); started = true }
            Lifecycle.Event.ON_RESUME -> if (!resumed) { view.onResume(); resumed = true }
            Lifecycle.Event.ON_PAUSE -> pause()
            Lifecycle.Event.ON_STOP -> stop()
            Lifecycle.Event.ON_DESTROY -> destroy()
            else -> Unit
        }
    }

    private fun pause() {
        if (resumed) { view.onPause(); resumed = false }
    }

    private fun stop() {
        pause()
        if (started) { view.onStop(); started = false }
    }

    fun destroy() {
        if (destroyed) return
        beforeDestroy?.invoke()
        beforeDestroy = null
        stop()
        destroyed = true
        view.onDestroy()
    }
}
