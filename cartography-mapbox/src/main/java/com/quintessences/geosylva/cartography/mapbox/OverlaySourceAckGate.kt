package com.quintessences.geosylva.cartography.mapbox

/** Public QA keys contain no account/scope payload; data ID is an opaque submission identity. */
const val MAP_OVERLAY_SOURCE_ID = "gs-overlay-source"
const val MAP_OVERLAY_SCENE_REVISION_PROPERTY = "gs_scene_rev"
const val MAP_OVERLAY_DATA_ID_PROPERTY = "gs_scene_data_id"
const val MAP_OVERLAY_LABEL_PROPERTY = "gs_label_text"

/** Callback correlation only; never caches SDK resources, geometry or authorization. UI-thread confined. */
internal class OverlaySourceAckGate {
    internal class Ticket(val style: Any, val scene: MapOverlayScene, val dataId: String) {
        var acknowledged = false
        var revealedAtMillis: Long? = null
        var complete = false
    }
    private var latest: Ticket? = null
    fun submit(style: Any, scene: MapOverlayScene, dataId: String): Ticket {
        require(dataId.isNotBlank())
        return Ticket(style,scene,dataId).also { latest = it }
    }
    fun invalidate() { latest = null }
    private fun matches(ticket: Ticket, style: Any, scene: MapOverlayScene): Boolean =
        latest === ticket && ticket.style === style && ticket.scene === scene
    fun acknowledge(style: Any, scene: MapOverlayScene, sourceId: String, dataId: String?): Ticket? {
        val ticket = latest ?: return null
        if (!matches(ticket,style,scene) || sourceId != MAP_OVERLAY_SOURCE_ID || dataId != ticket.dataId || ticket.acknowledged) return null
        ticket.acknowledged = true
        return ticket
    }
    fun revealed(ticket: Ticket, afterMillis: Long): Boolean {
        if (latest !== ticket || !ticket.acknowledged || ticket.revealedAtMillis != null) return false
        ticket.revealedAtMillis = afterMillis
        return true
    }
    fun awaitingFrame(style: Any, scene: MapOverlayScene): Boolean = latest?.let {
        matches(it,style,scene) && it.revealedAtMillis != null && !it.complete
    } == true
    fun fullFrame(style: Any, scene: MapOverlayScene, startedAtMillis: Long, full: Boolean): Ticket? {
        val ticket = latest ?: return null
        val revealed = ticket.revealedAtMillis ?: return null
        // Reject a FULL frame already in flight when visibility was changed, including same-ms starts.
        if (!matches(ticket,style,scene) || ticket.complete || !full || startedAtMillis <= revealed) return null
        ticket.complete = true
        return ticket
    }
}
