package com.forestry.counter.domain.usecase.maps

import com.forestry.counter.domain.model.Foret
import com.forestry.counter.domain.model.Group
import com.forestry.counter.domain.model.Parcelle
import java.util.Locale

/** Display projection only; repositories and the captured read authority decide which rows may enter it. */
enum class ForestScopeKind { CANONICAL, LEGACY_GROUP }
data class ForestScopeChoice(val id: String,val name: String,val kind: ForestScopeKind)

fun forestScopeChoices(forests: List<Foret>,groups: List<Group>): List<ForestScopeChoice> {
    val canonicalIds=forests.map { it.foretId }.toSet()
    val canonical=forests.filter { it.deletedAt == null && it.foretId.isNotBlank() }
        .distinctBy { it.foretId }.map { ForestScopeChoice(it.foretId,it.nom,ForestScopeKind.CANONICAL) }
    val legacy=groups.filter { it.id.isNotBlank() && it.id !in canonicalIds }
        .distinctBy { it.id }.map { ForestScopeChoice(it.id,it.name,ForestScopeKind.LEGACY_GROUP) }
    return (canonical+legacy).sortedWith(compareBy({ it.name.lowercase(Locale.ROOT) },{ it.id }))
}

/** Canonical affiliation wins; retain the historical fallback without inferring whether that ID is a Group or a Foret. */
fun Parcelle.belongsToForestScope(id: String): Boolean = foretId?.let { it == id } ?: (forestId == id)
