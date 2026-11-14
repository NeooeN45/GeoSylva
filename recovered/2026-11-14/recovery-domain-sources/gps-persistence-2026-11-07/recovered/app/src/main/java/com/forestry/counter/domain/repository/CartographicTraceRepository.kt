package com.forestry.counter.domain.repository

import com.forestry.counter.domain.location.trace.TraceCaptureSnapshot
import com.forestry.counter.domain.usecase.maps.MapDataScope
import kotlinx.coroutines.flow.Flow

data class CartographicTraceDocument(
    val id: String,
    val name: String?,
    val snapshot: TraceCaptureSnapshot,
    val saved: Boolean,
    val revision: Long,
    val updatedAt: Long,
    val recordedAt: Long?,
    val sourceTraceId: String?,
    val coordinateReferenceSystem: String = "EPSG:4326",
    val source: String = "phone_location",
    val methodId: String = com.forestry.counter.domain.location.trace.SphericalTraceDistance.METHOD_ID,
    val methodVersion: Int = 1,
)

interface CartographicTraceRepository {
    suspend fun captureAccess(scope: MapDataScope): CartographicStoreResult<CartographicMeasurementAccess>
    fun isCurrent(access: CartographicMeasurementAccess): Boolean
    fun accessChanges(): Flow<Unit>
    /** Enforces account, original parent binding and expected revision inside the commit. */
    suspend fun writeDraft(id: String, name: String?, snapshot: TraceCaptureSnapshot,
        expectedRevision: Long, sourceTraceId: String?, access: CartographicMeasurementAccess): CartographicStoreResult<CartographicTraceDocument>
    suspend fun save(id: String, name: String?, snapshot: TraceCaptureSnapshot,
        expectedRevision: Long, sourceTraceId: String?, access: CartographicMeasurementAccess): CartographicStoreResult<CartographicTraceDocument>
    suspend fun read(id: String, access: CartographicMeasurementAccess): CartographicStoreResult<CartographicTraceDocument>
    suspend fun list(access: CartographicMeasurementAccess): CartographicStoreResult<List<CartographicTraceDocument>>
}
