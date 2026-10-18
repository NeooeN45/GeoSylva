package com.forestry.counter.domain.usecase.pack

import com.forestry.counter.domain.model.pack.GeoPackDescriptor
import com.forestry.counter.domain.model.pack.PackLevel

/** Refuse une région incomplète plutôt que de présenter ses seuls contours comme un groupe complet. */
class IncompleteRegionalPackException : IllegalStateException("Les départements de cette région ne sont pas tous disponibles dans le catalogue.")

object RegionalPackSelection {
    /** Projection UI des seules publications connues ; la complétude est vérifiée avant confirmation. */
    fun visibleSelection(catalog: List<GeoPackDescriptor>, selectedTerritories: Set<String>): List<GeoPackDescriptor> {
        val selected = catalog.filter { it.contentKind in setOf("administrative_boundaries", "forestry_layers") && it.territoryId in selectedTerritories }
        val regionalIds = selected.filter { it.level == PackLevel.REGIONAL }.map { it.id }.toSet()
        return (selected + catalog.filter {
            it.contentKind in setOf("administrative_boundaries", "forestry_layers") && it.level == PackLevel.DEPARTEMENTAL && it.parentId in regionalIds
        }).distinctBy { it.id }
    }

    fun resolve(
        catalog: List<GeoPackDescriptor>,
        requestedIds: Set<String>,
        departmentsByRegion: Map<String, Set<String>>
    ): List<GeoPackDescriptor> {
        val result = linkedMapOf<String, GeoPackDescriptor>()
        for (id in requestedIds) {
            val requested = catalog.singleOrNull { it.id == id }
                ?: error("Le catalogue a changé. Actualisez les packs avant de continuer.")
            result[id] = requested
            if (requested.level != PackLevel.REGIONAL) continue
            val territory = requested.territoryId ?: throw IncompleteRegionalPackException()
            val expected = departmentsByRegion[territory]?.takeIf { it.isNotEmpty() }
                ?: throw IncompleteRegionalPackException()
            val children = catalog.filter {
                it.level == PackLevel.DEPARTEMENTAL && it.contentKind in setOf("administrative_boundaries", "forestry_layers") &&
                    (it.parentId == requested.id || it.territoryId in expected)
            }
            if (children.size != expected.size || children.map { it.territoryId }.toSet() != expected ||
                children.any { it.parentId != requested.id }) throw IncompleteRegionalPackException()
            children.sortedBy { it.territoryId }.forEach { result[it.id] = it }
        }
        return result.values.toList()
    }
}
