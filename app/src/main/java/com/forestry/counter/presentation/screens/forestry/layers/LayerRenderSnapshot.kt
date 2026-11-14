package com.forestry.counter.presentation.screens.forestry.layers

import com.forestry.counter.domain.model.cartography.LayerGeometryFamily
import com.forestry.counter.domain.model.cartography.LayerStyle
import com.forestry.counter.domain.repository.LayerStoreError
import com.quintessences.geosylva.cartography.ui.layers.LayerWorkspaceState

/** SDK-neutral features projected by the host's existing common map engine. */
data class LayerRenderSnapshot(
    val id: String,
    val geoJson: String,
    val style: LayerStyle,
    val geometryFamily: LayerGeometryFamily,
    val selectedFeatureId: String?,
    val preview: Boolean,
)

data class MapLayerHostState(
    val visible: Boolean = false,
    val ui: LayerWorkspaceState = LayerWorkspaceState(),
    val error: LayerStoreError? = null,
    val renders: List<LayerRenderSnapshot> = emptyList(),
)
