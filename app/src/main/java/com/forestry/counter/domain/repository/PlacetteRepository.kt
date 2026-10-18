package com.forestry.counter.domain.repository

import com.forestry.counter.domain.model.Placette
import kotlinx.coroutines.flow.Flow

interface PlacetteRepository {
    fun getPlacettesByParcelle(parcelleId: String): Flow<List<Placette>>
    fun getPlacetteById(placetteId: String): Flow<Placette?>
    suspend fun insertPlacette(placette: Placette)
    /** Creates one complete new plot batch for a single live parcel, or none. */
    suspend fun insertPlacettesForParcelle(placettes: List<Placette>)
    suspend fun updatePlacette(placette: Placette)
    suspend fun deletePlacette(placetteId: String)
    suspend fun deletePlacettesByParcelle(parcelleId: String)
}
