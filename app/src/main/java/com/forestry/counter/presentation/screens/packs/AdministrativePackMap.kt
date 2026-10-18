package com.forestry.counter.presentation.screens.packs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.forestry.counter.R
import com.forestry.counter.data.packs.AdministrativePackContent
import com.forestry.counter.domain.location.MapLibreHttpConfig
import com.forestry.counter.network.SecureHttpClient
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
import com.mapbox.mapboxsdk.style.layers.FillLayer
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.RasterLayer
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.mapboxsdk.style.sources.RasterSource
import com.mapbox.mapboxsdk.style.sources.TileSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan
import kotlin.math.cos
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response

/** Seuil d'affichage ; les identifiants sélectionnés ne dépendent pas du zoom. */
internal const val ADMINISTRATIVE_DEPARTMENT_ZOOM = 6.5

private const val ADMINISTRATIVE_STYLE = """{
  "version": 8,
  "name": "Territoires hors ligne",
  "sources": {},
  "layers": [{"id": "background", "type": "background", "paint": {"background-color": "#EDF4EF"}}]
}"""

private const val REGION_SOURCE = "pack-admin-regions"
private const val DEPARTMENT_SOURCE = "pack-admin-departments"
private const val ORTHOPHOTO_SOURCE = "pack-ign-orthophoto"
private const val ORTHOPHOTO_LAYER = "pack-ign-orthophoto-layer"
private const val ORTHOPHOTO_ATTRIBUTION = "© IGN · BD ORTHO® · Licence Ouverte 2.0"
private const val ORTHOPHOTO_URL = "https://data.geopf.fr/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=ORTHOIMAGERY.ORTHOPHOTOS&STYLE=normal&FORMAT=image/jpeg&TILEMATRIXSET=PM_0_19&TILEMATRIX={z}&TILECOL={x}&TILEROW={y}"

private enum class PhotoState { OFFLINE, LOCAL, LOADING, AVAILABLE, UNAVAILABLE }
private data class PhotoTile(val zoom: Int, val x: Int, val y: Int) {
    fun url() = ORTHOPHOTO_URL.replace("{z}", zoom.toString()).replace("{x}", x.toString()).replace("{y}", y.toString())
}

/**
 * Sélection cartographique sur les contours administratifs réellement embarqués.
 *
 * Les contours sont locaux. L’orthophotographie IGN commerciale qualifiée est
 * un fond d’affichage en ligne facultatif, jamais une donnée de calcul ou un
 * contenu du pack. La carte n’exige aucune permission de localisation. Ses noms utilisent les polices Android locales :
 * aucun serveur de glyphes n'est requis pour la consultation hors ligne.
 * Le parent conserve la sélection et décide du téléchargement des packs.
 */
@Composable
fun AdministrativePackMap(
    selectedIds: Set<String>,
    onTerritoryClick: (id: String, name: String) -> Unit,
    onVisibleLevelChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    installedContent: AdministrativePackContent? = null,
    overlayTop: Dp = 0.dp,
    overlayBottom: Dp = 0.dp
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOverlayTop by rememberUpdatedState(overlayTop.value)
    val latestOverlayBottom by rememberUpdatedState(overlayBottom.value)
    val latestSelection by rememberUpdatedState(selectedIds)
    val latestClick by rememberUpdatedState(onTerritoryClick)
    val latestLevelChanged by rememberUpdatedState(onVisibleLevelChanged)
    var savedLatitude by rememberSaveable(installedContent?.territoryId, installedContent?.version) { mutableStateOf(46.6) }
    var savedLongitude by rememberSaveable(installedContent?.territoryId, installedContent?.version) { mutableStateOf(2.4) }
    var savedZoom by rememberSaveable(installedContent?.territoryId, installedContent?.version) { mutableStateOf(4.0) }
    var savedBearing by rememberSaveable(installedContent?.territoryId, installedContent?.version) { mutableStateOf(CameraPosition.DEFAULT.bearing) }
    var savedTilt by rememberSaveable(installedContent?.territoryId, installedContent?.version) { mutableStateOf(CameraPosition.DEFAULT.tilt) }
    // Camera bounds carry asymmetric SDK padding. Preserve it with the camera;
    // target and zoom alone shift the projected territories on recreation.
    var savedPadding by rememberSaveable(installedContent?.territoryId, installedContent?.version) {
        mutableStateOf(CameraPosition.DEFAULT.padding?.copyOf())
    }
    var hasSavedCamera by rememberSaveable(installedContent?.territoryId, installedContent?.version) { mutableStateOf(false) }
    var readyMap by remember { mutableStateOf<MapboxMap?>(null) }
    var renderError by remember { mutableStateOf<String?>(null) }
    val hasInternet by rememberPackInternetConnection(context)
    var preferPhoto by rememberSaveable { mutableStateOf(true) }
    var photoUsable by remember { mutableStateOf(false) }
    var photoState by remember { mutableStateOf(PhotoState.OFFLINE) }
    var cameraTile by remember { mutableStateOf(photoTileForCamera(46.6, 2.4, 4.0)) }
    val probeClient = remember(context) {
        SecureHttpClient.createSecureClient(context).newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build()
    }
    LaunchedEffect(hasInternet, preferPhoto, cameraTile) {
        if (!hasInternet || !preferPhoto) {
            photoUsable = false
            photoState = if (!hasInternet) PhotoState.OFFLINE else PhotoState.LOCAL
            return@LaunchedEffect
        }
        if (!photoUsable) photoState = PhotoState.LOADING
        delay(350) // Un contrôle de disponibilité après le geste, pas à chaque image.
        try {
            val response = probeClient.newCall(Request.Builder().url(cameraTile.url()).build()).awaitPackMapResponse()
            response.use { incoming ->
                withContext(Dispatchers.IO) {
                    require(incoming.isSuccessful && incoming.body?.contentType()?.let { type -> type.type == "image" && type.subtype == "jpeg" } == true)
                    val output = ByteArrayOutputStream()
                    val input = requireNotNull(incoming.body).byteStream()
                    input.use { stream ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = stream.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= 2 * 1024 * 1024)
                            output.write(buffer, 0, count)
                        }
                    }
                    val bytes = output.toByteArray()
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    require(options.outWidth == 256 && options.outHeight == 256)
                }
            }
            photoUsable = true
            photoState = PhotoState.AVAILABLE
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            photoUsable = false
            photoState = PhotoState.UNAVAILABLE
        }
    }

    val geometry by produceState<Result<AdministrativeGeometry>?>(null, context, installedContent) {
        value = try {
            Result.success(withContext(Dispatchers.IO) {
                if (installedContent != null) {
                    val prefix = if (installedContent.territoryId.startsWith("region:")) "region:" else "department:"
                    val boundaries = validatedAdministrativeCollection(installedContent.boundariesJson, prefix)
                    val labels = validatedAdministrativeLabels(installedContent.labelsJson, prefix)
                    validateLabelCorrespondence(boundaries, labels)
                    require(boundaries.features().orEmpty().map { it.getStringProperty("territory_id") } == listOf(installedContent.territoryId)) {
                        "Le fichier actif ne correspond pas au territoire annoncé"
                    }
                    val empty = FeatureCollection.fromFeatures(emptyList<com.mapbox.geojson.Feature>())
                    AdministrativeGeometry(
                        regions = if (prefix == "region:") boundaries else empty,
                        departments = if (prefix == "department:") boundaries else empty,
                        regionLabels = if (prefix == "region:") labels else empty,
                        departmentLabels = if (prefix == "department:") labels else empty,
                        installedPreview = true
                    )
                } else AdministrativeGeometry(
                    regions = context.assets.open("packs/regions.geojson").bufferedReader().use {
                        validatedAdministrativeCollection(it.readText(), "region:")
                    },
                    departments = context.assets.open("packs/departments.geojson").bufferedReader().use {
                        validatedAdministrativeCollection(it.readText(), "department:")
                    },
                    regionLabels = context.assets.open("packs/regions-labels.geojson").bufferedReader().use {
                        validatedAdministrativeLabels(it.readText(), "region:")
                    },
                    departmentLabels = context.assets.open("packs/departments-labels.geojson").bufferedReader().use {
                        validatedAdministrativeLabels(it.readText(), "department:")
                    }
                ).also {
                    validateLabelCorrespondence(it.regions, it.regionLabels)
                    validateLabelCorrespondence(it.departments, it.departmentLabels)
                }
            })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    val mapView = remember(context, lifecycleOwner) {
        try {
            Mapbox.getInstance(context)
            MapLibreHttpConfig.configure(context)
            MapView(context, MapboxMapOptions.createFromAttributes(context).textureMode(true)).apply {
                setBackgroundColor(android.graphics.Color.parseColor("#EDF4EF"))
                onCreate(null)
            }
        } catch (error: Throwable) {
            renderError = "La carte n’a pas pu démarrer : ${error.message ?: "rendu indisponible"}"
            null
        }
    }
    val mapLifecycle = remember(mapView) { mapView?.let(::AdministrativeMapLifecycle) }

    DisposableEffect(mapView, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> mapLifecycle?.handle(event) }
        val failureListener = MapView.OnDidFailLoadingMapListener { message ->
            if (mapLifecycle?.destroyed == false) {
                if (readyMap != null && photoUsable) {
                    photoUsable = false
                    photoState = PhotoState.UNAVAILABLE
                } else renderError = message
            }
        }
        mapView?.addOnDidFailLoadingMapListener(failureListener)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.removeOnDidFailLoadingMapListener(failureListener)
            readyMap = null
            mapLifecycle?.destroy()
        }
    }

    val loadedGeometry = geometry?.getOrNull()
    LaunchedEffect(mapView, loadedGeometry) {
        val view = mapView ?: return@LaunchedEffect
        val boundaries = loadedGeometry ?: return@LaunchedEffect
        view.getMapAsync { map ->
            if (mapLifecycle?.destroyed != false) return@getMapAsync
            try {
                map.uiSettings.apply {
                    isRotateGesturesEnabled = false
                    isTiltGesturesEnabled = false
                    isCompassEnabled = false
                    isZoomGesturesEnabled = true
                    isScrollGesturesEnabled = true
                    // La source IGN et l'attribution du moteur restent lisibles
                    // sans se recouvrir dans le cartouche du bas.
                    logoGravity = Gravity.TOP or Gravity.START
                    attributionGravity = Gravity.TOP or Gravity.START
                    val density = context.resources.displayMetrics.density
                    setLogoMargins((8 * density).toInt(), ((8 + latestOverlayTop) * density).toInt(), 0, 0)
                    setAttributionMargins((8 * density).toInt(), ((32 + latestOverlayTop) * density).toInt(), 0, 0)
                }
                map.setMinZoomPreference(2.0)
                map.setMaxZoomPreference(12.0)
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(savedLatitude, savedLongitude))
                    .zoom(savedZoom)
                    .bearing(savedBearing)
                    .tilt(savedTilt)
                    .padding(savedPadding?.copyOf())
                    .build()
                map.setStyle(Style.Builder().fromJson(ADMINISTRATIVE_STYLE)) { style ->
                    if (mapLifecycle?.destroyed != false) return@setStyle
                    try {
                        addAdministrativeLayers(style, REGION_SOURCE, boundaries.regions, boundaries.regionLabels, false, boundaries.installedPreview)
                        addAdministrativeLayers(style, DEPARTMENT_SOURCE, boundaries.departments, boundaries.departmentLabels, true, boundaries.installedPreview)
                        addPersistentSelectionOutline(style, REGION_SOURCE)
                        addPersistentSelectionOutline(style, DEPARTMENT_SOURCE)
                        applyAdministrativeSelection(style, latestSelection)
                        var reportedLevel: String? = null
                        val cameraListener = MapboxMap.OnCameraMoveListener {
                            val position = map.cameraPosition
                            position.target?.let { target ->
                                savedLatitude = target.latitude
                                savedLongitude = target.longitude
                            }
                            savedZoom = position.zoom
                            savedBearing = position.bearing
                            savedTilt = position.tilt
                            savedPadding = position.padding?.copyOf()
                            position.target?.let { target ->
                                cameraTile = photoTileForCamera(target.latitude, target.longitude, position.zoom)
                            }
                            val level = visibleAdministrativeLevel(position.zoom)
                            if (level != reportedLevel) {
                                reportedLevel = level
                                latestLevelChanged(level)
                            }
                        }
                        val clickListener = MapboxMap.OnMapClickListener { location ->
                            if (boundaries.installedPreview) return@OnMapClickListener false
                            val source = if (map.cameraPosition.zoom >= ADMINISTRATIVE_DEPARTMENT_ZOOM) {
                                DEPARTMENT_SOURCE
                            } else REGION_SOURCE
                            val feature = map.queryRenderedFeatures(
                                map.projection.toScreenLocation(location), "$source-fill"
                            ).firstOrNull()
                            if (feature != null && feature.hasProperty("territory_id") && feature.hasProperty("name")) {
                                latestClick(feature.getStringProperty("territory_id"), feature.getStringProperty("name"))
                                true
                            } else false
                        }
                        map.addOnCameraMoveListener(cameraListener)
                        map.addOnMapClickListener(clickListener)
                        mapLifecycle.beforeDestroy = {
                            map.removeOnCameraMoveListener(cameraListener)
                            map.removeOnMapClickListener(clickListener)
                        }
                        cameraListener.onCameraMove()
                        readyMap = map
                        if (!hasSavedCamera) {
                            view.post {
                                if (!mapLifecycle.destroyed) {
                                    // Vue initiale métropolitaine ; ce cadre de caméra
                                    // ne remplace aucune géométrie administrative.
                                    map.moveCamera(if (boundaries.installedPreview) installedCameraUpdate(boundaries, context.resources.displayMetrics.density, latestOverlayTop, latestOverlayBottom) else metropolitanCameraUpdate(context.resources.displayMetrics.density, latestOverlayTop, latestOverlayBottom))
                                    hasSavedCamera = true
                                }
                            }
                        }
                    } catch (error: Exception) {
                        renderError = "Les territoires n’ont pas pu être affichés : ${error.message}"
                    }
                }
            } catch (error: Exception) {
                renderError = "La carte n’a pas pu être préparée : ${error.message}"
            }
        }
    }

    LaunchedEffect(readyMap, overlayTop) {
        val density = context.resources.displayMetrics.density
        readyMap?.uiSettings?.apply {
            setLogoMargins((8 * density).roundToInt(), ((8 + overlayTop.value) * density).roundToInt(), 0, 0)
            setAttributionMargins((8 * density).roundToInt(), ((32 + overlayTop.value) * density).roundToInt(), 0, 0)
        }
    }

    LaunchedEffect(readyMap, selectedIds, photoUsable) {
        readyMap?.style?.let { style ->
            setAdministrativePhoto(style, photoUsable)
            applyAdministrativeSelection(style, selectedIds, photoUsable)
        }
    }

    Box(modifier = modifier) {
        if (mapView != null) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        }
        val errorMessage = geometry?.exceptionOrNull()?.let {
            "Les contours administratifs ne sont pas disponibles : ${it.message}"
        } ?: renderError
        when {
            errorMessage != null -> Surface(
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(stringResource(R.string.pack_map_render_failed), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
            readyMap == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        if (readyMap != null && errorMessage == null) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(start = 12.dp, end = 12.dp, top = 12.dp + overlayTop, bottom = 12.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 2.dp
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = { readyMap?.animateCamera(CameraUpdateFactory.zoomIn(), 200) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.pack_map_zoom_in), modifier = Modifier.size(24.dp))
                    }
                    IconButton(
                        onClick = { readyMap?.animateCamera(CameraUpdateFactory.zoomOut(), 200) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.pack_map_zoom_out), modifier = Modifier.size(24.dp))
                    }
                    IconButton(
                        onClick = {
                            val camera = loadedGeometry?.takeIf { it.installedPreview }?.let {
                                installedCameraUpdate(it, context.resources.displayMetrics.density, latestOverlayTop, latestOverlayBottom)
                            } ?: metropolitanCameraUpdate(context.resources.displayMetrics.density, latestOverlayTop, latestOverlayBottom)
                            readyMap?.animateCamera(camera, 300)
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.CenterFocusStrong, contentDescription = stringResource(if (installedContent == null) R.string.pack_map_recenter else R.string.pack_map_recenter_installed), modifier = Modifier.size(24.dp))
                    }
                }
            }
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 52.dp + overlayTop).widthIn(max = 210.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shape = RoundedCornerShape(50)
            ) {
                TextButton(onClick = { preferPhoto = !preferPhoto }) {
                    Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                    val label = when (photoState) {
                        PhotoState.AVAILABLE -> R.string.pack_map_photo_active
                        PhotoState.LOADING -> R.string.pack_map_photo_loading
                        PhotoState.OFFLINE -> R.string.pack_map_local_offline
                        PhotoState.UNAVAILABLE -> R.string.pack_map_photo_unavailable
                        PhotoState.LOCAL -> R.string.pack_map_local_selected
                    }
                    Text(stringResource(label), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
                }
            }
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp + overlayBottom),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    (installedContent?.attribution ?: stringResource(R.string.pack_map_embedded_attribution)) +
                        if (photoUsable) "\n" + stringResource(R.string.pack_map_photo_attribution) else "",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

private data class AdministrativeGeometry(
    val regions: FeatureCollection,
    val departments: FeatureCollection,
    val regionLabels: FeatureCollection,
    val departmentLabels: FeatureCollection,
    val installedPreview: Boolean = false
)

private fun validatedAdministrativeCollection(raw: String, prefix: String): FeatureCollection {
    val collection = FeatureCollection.fromJson(raw)
    val features = collection.features().orEmpty()
    require(features.isNotEmpty()) { "Couche administrative vide" }
    require(features.all {
        it.hasProperty("territory_id") && it.getStringProperty("territory_id").startsWith(prefix) &&
            it.hasProperty("name") && it.getStringProperty("name").isNotBlank() &&
            (it.geometry() is Polygon || it.geometry() is MultiPolygon)
    }) { "Contours ou identifiants administratifs incompatibles" }
    return collection
}

/** Une seule ancre par territoire, préparée depuis son plus grand morceau. */
private fun validatedAdministrativeLabels(raw: String, prefix: String): FeatureCollection {
    val collection = FeatureCollection.fromJson(raw)
    val features = collection.features().orEmpty()
    require(features.isNotEmpty() && features.all {
        it.hasProperty("territory_id") && it.getStringProperty("territory_id").startsWith(prefix) &&
            it.hasProperty("name") && it.getStringProperty("name").isNotBlank() && it.geometry() is Point
    }) { "Ancres des noms administratifs incompatibles" }
    require(features.map { it.getStringProperty("territory_id") }.distinct().size == features.size) {
        "Un territoire possède plusieurs ancres de nom"
    }
    return collection
}

private fun validateLabelCorrespondence(boundaries: FeatureCollection, labels: FeatureCollection) {
    val namesFromBoundaries = boundaries.features().orEmpty().associate {
        it.getStringProperty("territory_id") to it.getStringProperty("name")
    }
    val namesFromLabels = labels.features().orEmpty().associate {
        it.getStringProperty("territory_id") to it.getStringProperty("name")
    }
    require(namesFromBoundaries == namesFromLabels) { "Les noms ne correspondent pas aux contours administratifs" }
}

private fun visibleAdministrativeLevel(zoom: Double): String =
    if (zoom >= ADMINISTRATIVE_DEPARTMENT_ZOOM) "departments" else "regions"

private fun metropolitanCameraUpdate(density: Float, overlayTop: Float = 0f, overlayBottom: Float = 0f) = framedAdministrativeCamera(
    LatLngBounds.from(51.2, 9.8, 41.3, -5.5), density, overlayTop, overlayBottom
)

private fun installedCameraUpdate(geometry: AdministrativeGeometry, density: Float, overlayTop: Float = 0f, overlayBottom: Float = 0f): com.mapbox.mapboxsdk.camera.CameraUpdate {
    val points = (geometry.regions.features().orEmpty() + geometry.departments.features().orEmpty()).flatMap { feature ->
        when (val boundary = feature.geometry()) {
            is Polygon -> boundary.coordinates().flatten()
            is MultiPolygon -> boundary.coordinates().flatten().flatten()
            else -> emptyList()
        }
    }.map { LatLng(it.latitude(), it.longitude()) }
    require(points.isNotEmpty()) { "Le pack ne contient aucun contour affichable" }
    return framedAdministrativeCamera(LatLngBounds.Builder().includes(points).build(), density, overlayTop, overlayBottom)
}

/** Même cadre initial/recentrage, pour la métropole et les aperçus locaux. */
private fun framedAdministrativeCamera(bounds: LatLngBounds, density: Float, overlayTop: Float = 0f, overlayBottom: Float = 0f): com.mapbox.mapboxsdk.camera.CameraUpdate {
    // Le SDK reçoit des pixels, les contrôles Compose sont en dp. Réserver
    // la colonne droite, le badge/logo en haut et l’attribution multiligne.
    return CameraUpdateFactory.newLatLngBounds(
        bounds,
        (20 * density).roundToInt(),
        ((100 + overlayTop) * density).roundToInt(),
        (84 * density).roundToInt(),
        ((72 + overlayBottom) * density).roundToInt()
    )
}

private fun addAdministrativeLayers(
    style: Style,
    sourceId: String,
    collection: FeatureCollection,
    labels: FeatureCollection,
    departments: Boolean,
    installedPreview: Boolean = false
) {
    style.addSource(GeoJsonSource(sourceId, collection))
    val labelsSourceId = "$sourceId-label-points"
    style.addSource(GeoJsonSource(labelsSourceId, labels))
    val minZoom = if (departments && !installedPreview) ADMINISTRATIVE_DEPARTMENT_ZOOM.toFloat() else 0f
    val maxZoom = if (departments || installedPreview) 13f else ADMINISTRATIVE_DEPARTMENT_ZOOM.toFloat()
    style.addLayer(FillLayer("$sourceId-fill", sourceId).apply {
        setMinZoom(minZoom)
        setMaxZoom(maxZoom)
    }.withProperties(PropertyFactory.fillColor("#C9DED3"), PropertyFactory.fillOpacity(0.85f)))
    style.addLayer(LineLayer("$sourceId-outline-halo", sourceId).apply {
        setMinZoom(minZoom)
        setMaxZoom(maxZoom)
    }.withProperties(PropertyFactory.lineColor("#F8FCF9"), PropertyFactory.lineWidth(3.8f), PropertyFactory.lineOpacity(0.9f)))
    style.addLayer(LineLayer("$sourceId-outline", sourceId).apply {
        setMinZoom(minZoom)
        setMaxZoom(maxZoom)
    }.withProperties(PropertyFactory.lineColor("#708C7D"), PropertyFactory.lineWidth(1.2f)))

    val images = hashMapOf<String, Bitmap>()
    labels.features().orEmpty().forEach { feature ->
        images[feature.getStringProperty("territory_id")] = administrativeNameBitmap(feature.getStringProperty("name"))
    }
    if (images.isNotEmpty()) style.addImages(images)
    style.addLayer(SymbolLayer("$sourceId-labels", labelsSourceId).apply {
        setMinZoom(minZoom)
        setMaxZoom(maxZoom)
    }.withProperties(
        PropertyFactory.iconImage(Expression.get("territory_id")),
        PropertyFactory.iconAllowOverlap(false),
        PropertyFactory.iconIgnorePlacement(false),
        PropertyFactory.iconPadding(3f)
    ))
}

/** Reste visible au changement d'échelle, sans rendre l'autre couche cliquable. */
private fun addPersistentSelectionOutline(style: Style, sourceId: String) {
    style.addLayer(LineLayer("$sourceId-selection", sourceId).withFilter(Expression.literal(false)).withProperties(
        PropertyFactory.lineColor("#C87728"),
        PropertyFactory.lineWidth(3.2f)
    ))
}

private fun applyAdministrativeSelection(style: Style, selectedIds: Set<String>, photo: Boolean = false) {
    val matches = selectedIds.map { Expression.eq(Expression.get("territory_id"), Expression.literal(it)) }
    val isSelected = if (matches.isEmpty()) Expression.literal(false) else Expression.any(*matches.toTypedArray())
    listOf(REGION_SOURCE, DEPARTMENT_SOURCE).forEach { source ->
        style.getLayerAs<LineLayer>("$source-selection")?.setFilter(isSelected)
        style.getLayer("$source-fill")?.setProperties(
            PropertyFactory.fillColor(Expression.switchCase(
                isSelected, Expression.color(android.graphics.Color.parseColor("#F2B66E")),
                Expression.color(android.graphics.Color.parseColor("#C9DED3"))
            )),
            PropertyFactory.fillOpacity(Expression.switchCase(
                isSelected, Expression.literal(if (photo) 0.18f else 0.65f),
                Expression.literal(if (photo) 0.03f else 0.72f)
            ))
        )
        style.getLayer("$source-outline")?.setProperties(
            PropertyFactory.lineColor(Expression.switchCase(
                isSelected, Expression.color(android.graphics.Color.parseColor("#B96A22")),
                Expression.color(android.graphics.Color.parseColor("#315645"))
            )),
            PropertyFactory.lineWidth(Expression.switchCase(isSelected, Expression.literal(3.2f), Expression.literal(1.5f)))
        )
    }
}

/** Noms réels et police système, sans URL externe de glyphes. */
private fun administrativeNameBitmap(name: String): Bitmap {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 15f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val metrics = paint.fontMetrics
    val width = ceil(paint.measureText(name)).toInt() + 10
    val height = ceil(metrics.descent - metrics.ascent).toInt() + 10
    val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val baseline = 5f - metrics.ascent
    paint.color = android.graphics.Color.parseColor("#173E2D")
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 3.5f
    canvas.drawText(name, 5f, baseline, paint)
    paint.color = android.graphics.Color.parseColor("#FAFCFA")
    paint.style = Paint.Style.FILL
    canvas.drawText(name, 5f, baseline, paint)
    return bitmap
}

/** Ajouter/retirer seulement le raster : les données et la caméra restent intactes. */
private fun setAdministrativePhoto(style: Style, visible: Boolean) {
    if (!visible) {
        style.removeLayer(ORTHOPHOTO_LAYER)
        style.removeSource(ORTHOPHOTO_SOURCE)
    } else if (style.getLayer(ORTHOPHOTO_LAYER) == null) {
        val tiles = TileSet("2.1.0", ORTHOPHOTO_URL).apply {
            minZoom = 0f
            maxZoom = 19f
            scheme = "xyz"
            attribution = "<a href=\"https://cartes.gouv.fr/rechercher-une-donnee/dataset/IGNF_BD-ORTHO\">$ORTHOPHOTO_ATTRIBUTION · catalogue 2026-09-11 · prises de vue variables</a>"
        }
        style.addSource(RasterSource(ORTHOPHOTO_SOURCE, tiles, 256))
        style.addLayerAbove(RasterLayer(ORTHOPHOTO_LAYER, ORTHOPHOTO_SOURCE), "background")
    }
}

private fun photoTileForCamera(latitude: Double, longitude: Double, zoom: Double): PhotoTile {
    val z = floor(zoom).toInt().coerceIn(0, 19)
    val count = 1 shl z
    val radians = Math.toRadians(latitude.coerceIn(-85.05112878, 85.05112878))
    val x = floor((longitude + 180.0) / 360.0 * count).toInt().coerceIn(0, count - 1)
    val y = floor((1.0 - ln(tan(radians) + 1.0 / cos(radians)) / Math.PI) / 2.0 * count).toInt().coerceIn(0, count - 1)
    return PhotoTile(z, x, y)
}

/** INTERNET indique une possibilité ; seule la réponse IGN qualifie le fond. */
@Composable
private fun rememberPackInternetConnection(context: Context): State<Boolean> {
    val manager = remember(context) { context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager }
    fun connected(): Boolean = runCatching {
        manager?.activeNetwork?.let { manager?.getNetworkCapabilities(it) }
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
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
private suspend fun Call.awaitPackMapResponse(): Response = suspendCancellableCoroutine { continuation ->
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

/** Une seule destruction, même si ON_DESTROY précède la sortie de composition. */
private class AdministrativeMapLifecycle(private val view: MapView) {
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
