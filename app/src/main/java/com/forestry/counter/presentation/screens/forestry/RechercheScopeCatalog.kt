package com.forestry.counter.presentation.screens.forestry

import com.forestry.counter.data.cartography.CartographicScopeAuthority
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.repository.CartographicStoreResult
import com.forestry.counter.domain.repository.ForetRepository
import com.forestry.counter.domain.repository.GroupRepository
import com.forestry.counter.domain.usecase.maps.ForestScopeChoice
import com.forestry.counter.domain.usecase.maps.ForestScopeKind
import com.forestry.counter.domain.usecase.maps.MapDataScope
import com.forestry.counter.domain.usecase.maps.belongsToForestScope
import com.forestry.counter.domain.usecase.maps.forestScopeChoices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart

internal data class RechercheScopeCatalogState(
    val loaded: Boolean=false,
    val failed: Boolean=false,
    val forests: List<ForestScopeChoice> = emptyList(),
    val access: BusinessReadAccess.AccessSnapshot?=null,
    val scopeAccess: CartographicScopeAuthority.Token?=null,
)
internal data class RechercheScopeTarget(
    val scope: MapDataScope,
    val forest: ForestScopeChoice?=null,
    val parcel: Parcelle?=null,
    val plot: Placette?=null,
)

/** Read projection and selection boundary, never a database copy or an ownership grant. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class RechercheScopeCatalog(
    forests: ForetRepository,
    groups: GroupRepository,
    private val reads: BusinessReadAccess,
    private val authority: CartographicScopeAuthority,
) {
    // Resubscribe rather than reusing combine's old tuples across purge/parent/authority transitions.
    private val source=authority.accessChanges().flatMapLatest {
        combine(forests.getAll(),groups.getAllGroups()) { canonical,legacy ->
            RechercheScopeCatalogState(loaded=true,forests=forestScopeChoices(canonical,legacy))
        }.onStart { emit(RechercheScopeCatalogState()) }
    }
    val states: Flow<RechercheScopeCatalogState> = reads.observe(source,RechercheScopeCatalogState()) { value,access ->
        if (!value.loaded) value else {
            val captured=authority.capture(MapDataScope.AllForests)
            val token=(captured as? CartographicStoreResult.Success)?.value
            if (token == null) RechercheScopeCatalogState(failed=true)
            else if (!reads.isCurrent(access) || !authority.isCurrent(token)) RechercheScopeCatalogState()
            else value.copy(forests=value.forests.filter { reads.canReadForest(it.id,access) },access=access,scopeAccess=token)
        }
    }.catch { failure ->
        if (failure is CancellationException) throw failure
        emit(RechercheScopeCatalogState(failed=true))
    }

    /** A callback retains the displayed account/epoch and exact source kind/parent IDs through every suspension. */
    suspend fun resolve(displayed: RechercheScopeCatalogState,target: RechercheScopeTarget): String? {
        val access=displayed.access ?: return null
        val displayedScope=displayed.scopeAccess ?: return null
        if (!displayed.loaded || displayed.failed || !reads.isCurrent(access) || !authority.isCurrent(displayedScope)) return null
        val forest=target.forest
        val parcel=target.parcel
        val plot=target.plot
        if (forest != null && displayed.forests.none { it.id == forest.id && it.kind == forest.kind }) return null
        val route=when (val scope=target.scope) {
            MapDataScope.AllForests -> if (forest == null && parcel == null && plot == null) "all" else null
            is MapDataScope.Forest -> if (forest?.id == scope.id && parcel == null && plot == null) "forest_${scope.id}" else null
            is MapDataScope.Parcel -> if (forest != null && parcel != null && parcel.id == scope.id && parcel.deletedAt == null &&
                parcel.belongsToForestScope(forest.id) && plot == null) parcel.id else null
            is MapDataScope.Plot -> if (forest != null && parcel != null && parcel.deletedAt == null &&
                parcel.belongsToForestScope(forest.id) && plot != null && plot.id == scope.id && plot.deletedAt == null &&
                plot.parcelleId == parcel.id) "placette_${plot.id}" else null
            MapDataScope.Free,MapDataScope.Invalid -> null
        } ?: return null
        // Existing String navigation keys must round-trip to the exact typed object, never a reserved-key alias.
        if (MapDataScope.fromLegacyKey(route) != target.scope) return null
        try {
            if (!authority.transaction(displayedScope) { reads.revalidate(access) }) return null
            var parentToken: CartographicScopeAuthority.Token?=null
            if (forest != null) {
                if (!reads.canReadForest(forest.id,access)) return null
                val captured=authority.capture(MapDataScope.Forest(forest.id))
                val token=(captured as? CartographicStoreResult.Success)?.value ?: return null
                val expected=if (forest.kind == ForestScopeKind.CANONICAL) "FOREST" else "LEGACY_FOREST_GROUP"
                if (token.binding.kind != expected || token.binding.id != forest.id) return null
                parentToken=token
                if (!authority.transaction(token) { reads.revalidate(access) }) return null
            }
            if (parcel != null && !reads.canReadParcelle(parcel.id,access)) return null
            val token=if (target.scope is MapDataScope.Forest) parentToken ?: return null else {
                val captured=authority.capture(target.scope)
                (captured as? CartographicStoreResult.Success)?.value ?: return null
            }
            if (token.scope != target.scope) return null
            if (parcel != null) {
                val expectedKind=if (plot == null) "PARCEL" else "PLOT"
                if (token.binding.kind != expectedKind || token.binding.parents != listOf(parcel.id,parcel.foretId,parcel.forestId)) return null
            }
            val resolved=authority.transaction(token) {
                if (reads.revalidate(access)) route else null
            }
            return resolved?.takeIf { reads.isCurrent(access) && authority.isCurrent(displayedScope) && authority.isCurrent(token) &&
                parentToken?.let(authority::isCurrent) != false }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return null }
    }
}
