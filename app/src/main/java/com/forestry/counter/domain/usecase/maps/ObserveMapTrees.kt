package com.forestry.counter.domain.usecase.maps

import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.repository.ParcelleRepository
import com.forestry.counter.domain.repository.TigeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** Map data context, independent of profession, renderer and display label. */
sealed interface MapDataScope {
    data object Free : MapDataScope
    data object AllForests : MapDataScope
    data object Invalid : MapDataScope
    data class Forest(val id: String) : MapDataScope { init { require(id.isNotBlank()) } }
    data class Parcel(val id: String) : MapDataScope { init { require(id.isNotBlank()) } }
    data class Plot(val id: String) : MapDataScope { init { require(id.isNotBlank()) } }

    companion object {
        /** Compatibility boundary only: existing navigation keys remain readable. */
        fun fromLegacyKey(key: String): MapDataScope = when {
            key == "none" -> Free
            key == "all" -> AllForests
            key.isBlank() -> Invalid
            key.startsWith("forest_") -> key.removePrefix("forest_").takeIf { it.isNotBlank() }?.let(::Forest) ?: Invalid
            key.startsWith("placette_") -> key.removePrefix("placette_").takeIf { it.isNotBlank() }?.let(::Plot) ?: Invalid
            else -> Parcel(key)
        }
    }
}

sealed interface MapTreeContent {
    val scope: MapDataScope
    data class Loading(override val scope: MapDataScope) : MapTreeContent
    data class Ready(override val scope: MapDataScope, val trees: List<Tige>) : MapTreeContent
    data class Unavailable(override val scope: MapDataScope, val reason: Reason) : MapTreeContent
    enum class Reason { INVALID_CONTEXT, FOREST_REPOSITORY_REQUIRED, DATA_READ }
}

/**
 * One read path for all map hosts. The injected repositories retain ownership/access
 * filtering; AllForests means their authorised results, never a direct database scan.
 * Loading, failure and a confirmed empty result are distinct. No SDK, DAO or GPS here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveMapTrees(
    private val trees: TigeRepository,
    private val parcels: ParcelleRepository?,
) {
    operator fun invoke(scope: MapDataScope): Flow<MapTreeContent> = flow {
        // Invoke repositories inside collection so synchronous failures are also bounded.
        val source: Flow<MapTreeContent> = when (scope) {
            MapDataScope.Invalid -> flowOf(MapTreeContent.Unavailable(scope, MapTreeContent.Reason.INVALID_CONTEXT))
            MapDataScope.Free -> flowOf(MapTreeContent.Ready(scope, emptyList()))
            MapDataScope.AllForests -> content(scope, trees.getAllTiges())
            is MapDataScope.Parcel -> content(scope, trees.getTigesByParcelle(scope.id))
            is MapDataScope.Plot -> content(scope, trees.getTigesByPlacette(scope.id))
            is MapDataScope.Forest -> {
                val repository = parcels
                if (repository == null) flowOf(MapTreeContent.Unavailable(scope, MapTreeContent.Reason.FOREST_REPOSITORY_REQUIRED))
                else repository.getParcellesByForest(scope.id)
                    .map { visibleParcels -> visibleParcels.filter { it.deletedAt == null && it.belongsToForestScope(scope.id) }.map { it.id }.distinct().sorted() }
                    .distinctUntilChanged()
                    .flatMapLatest { ids ->
                        val sourceTrees = if (ids.isEmpty()) flowOf(emptyList())
                        else combine(ids.map(trees::getTigesByParcelle)) { results -> results.flatMap { it } }
                        // Changing forest membership cancels the previous subscriptions and clears
                        // their content while waiting for the new authorised repository results.
                        content(scope, sourceTrees)
                    }
            }
        }
        emitAll(source)
    }.onStart { emit(MapTreeContent.Loading(scope)) }
        .catch { failure ->
            if (failure is CancellationException) throw failure
            emit(MapTreeContent.Unavailable(scope, MapTreeContent.Reason.DATA_READ))
        }.distinctUntilChanged()

    private fun content(scope: MapDataScope, source: Flow<List<Tige>>): Flow<MapTreeContent> =
        source.map<List<Tige>, MapTreeContent> { MapTreeContent.Ready(scope, it.toList()) }
            .onStart { emit(MapTreeContent.Loading(scope)) }
}
