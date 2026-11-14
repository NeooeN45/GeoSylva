package com.forestry.counter.presentation.screens.forestry

import com.quintessences.geosylva.cartography.mapbox.MapRenderStatus

/** A catalog selection is an observation, never an optimistic download/paint claim. */
internal fun acknowledgedWorkspaceBasemapId(
    renderStatus: MapRenderStatus,
    renderedStyleId: String,
    localVectorMode: Boolean,
): String? = (renderStatus as? MapRenderStatus.Ready)
    ?.styleId?.takeIf { it == renderedStyleId && !localVectorMode }

/** These controls edit DEM relief; other tools can still use the local vector canvas. */
internal fun workspaceControlsEnabled(
    status: MapRenderStatus,
    renderedStyleId: String,
    localVectorMode: Boolean,
    hasDem: Boolean,
    overlayFailed: Boolean,
): Boolean = hasDem && !overlayFailed &&
    acknowledgedWorkspaceBasemapId(status, renderedStyleId, localVectorMode) != null
