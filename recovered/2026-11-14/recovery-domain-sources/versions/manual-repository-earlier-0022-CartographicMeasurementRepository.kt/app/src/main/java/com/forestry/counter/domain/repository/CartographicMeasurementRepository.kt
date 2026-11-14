package com.forestry.counter.domain.repository

import com.forestry.counter.domain.calculation.cartography.CartographicCoordinate
import com.forestry.counter.domain.calculation.cartography.CartographicMeasureKind
import com.forestry.counter.domain.usecase.maps.MapDataScope
import kotlinx.coroutines.flow.Flow

/** Opaque authority captured before editing; never replace it during a pending save. */
interface CartographicMeasurementAccess {
    val scope: MapDataScope
    val localDevice: Boolean
}

data class CartographicMeasurementInput(
    val id: String,
    val name: String?,
    val kind: CartographicMeasureKind,
    val points: List<CartographicCoordinate>,
)

/** Immutable cartographic snapshot; SI values are not terrain or cadastral certification. */
data class SavedCartographicMeasurement(
    val id: String,
    val scope: MapDataScope,
    val name: String?,
    val kind: CartographicMeasureKind,
    val points: List<CartographicCoordinate>,
    val valueSi: Double,
    val methodId: String,
    val methodVersion: Int,
    val recordedAt: Long,
    val source: String = "manual_map",
    val coordinateReferenceSystem: String = "EPSG:4326",
)

enum class CartographicStoreError {
    ACCESS_CHANGED, UNAVAILABLE, INVALID_INPUT, CONFLICT, IO, CORRUPT, UNKNOWN_RESULT,
}

sealed interface CartographicStoreResult<out T> {
    data class Success<T>(val value: T) : CartographicStoreResult<T>
    data class Failure(val reason: CartographicStoreError) : CartographicStoreResult<Nothing>
}

interface CartographicMeasurementRepository {
    suspend fun captureAccess(scope: MapDataScope): CartographicStoreResult<CartographicMeasurementAccess>
    /** Synchronous session/purge hint. Every I/O operation also revalidates the live scope. */
    fun isCurrent(access: CartographicMeasurementAccess): Boolean
    fun accessChanges(): Flow<Unit>
    suspend fun save(input: CartographicMeasurementInput, access: CartographicMeasurementAccess): CartographicStoreResult<SavedCartographicMeasurement>
    suspend fun list(access: CartographicMeasurementAccess): CartographicStoreResult<List<SavedCartographicMeasurement>>
    suspend fun read(id: String, access: CartographicMeasurementAccess): CartographicStoreResult<SavedCartographicMeasurement>
}
