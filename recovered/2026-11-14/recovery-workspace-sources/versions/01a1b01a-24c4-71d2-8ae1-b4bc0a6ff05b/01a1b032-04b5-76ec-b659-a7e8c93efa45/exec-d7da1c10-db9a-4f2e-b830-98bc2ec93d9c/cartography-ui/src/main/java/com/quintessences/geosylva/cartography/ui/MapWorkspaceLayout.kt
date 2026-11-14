package com.quintessences.geosylva.cartography.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared by the sheet and native ornaments; use the workspace's actual height. */
fun mapWorkspacePanelHeight(availableHeight: Dp, fontScale: Float): Dp =
    availableHeight * if (fontScale > 1.1f || availableHeight < 500.dp) 0.82f else 0.58f
