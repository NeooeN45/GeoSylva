package com.forestry.counter.data.repository

import com.forestry.counter.data.cartography.CartographicAuthorityFailure
import com.forestry.counter.data.cartography.CartographicScopeAuthority
import com.forestry.counter.data.local.dao.CartographicTraceDao
import com.forestry.counter.data.local.entity.CartographicTraceEntity
import com.forestry.counter.domain.location.trace.*
import com.forestry.counter.domain.repository.*
import com.forestry.counter.domain.usecase.maps.MapDataScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException

/** Encrypted Room records behind the shared account/purge/parent authority. */
class CartographicTraceRepositoryImpl(
    private val dao: CartographicTraceDao,
    private val authority: CartographicScopeAuthority,
    private val clock: () -> Long = System::currentTimeMillis,
) : CartographicTraceRepository {
    override suspend fun captureAccess(scope: MapDataScope): CartographicStoreResult<CartographicMeasurementAccess> = authority.capture(scope)
    override fun isCurrent(access: CartographicMeasurementAccess): Boolean = (access as? CartographicScopeAuthority.Token)?.let(authority::isCurrent) == true
    override fun accessChanges() = authority.accessChanges()

    override suspend fun writeDraft(id: String, name: String?, snapshot: TraceCaptureSnapshot, expectedRevision: Long,
        sourceTraceId: String?, access: CartographicMeasurementAccess) = write(id, name, snapshot, expectedRevision, sourceTraceId, access, saved = false)
    override suspend fun save(id: String, name: String?, snapshot: TraceCaptureSnapshot, expectedRevision: Long,
        sourceTraceId: String?, access: CartographicMeasurementAccess) = write(id, name, snapshot, expectedRevision, sourceTraceId, access, saved = true)

    private suspend fun write(id: String, name: String?, snapshot: TraceCaptureSnapshot, expectedRevision: Long,
        sourceTraceId: String?, access: CartographicMeasurementAccess, saved: Boolean): CartographicStoreResult<CartographicTraceDocument> = authority.result {
        val token = token(access)
        canonicalId(id)
        if (snapshot.sessionId != id || expectedRevision < 0 || sourceTraceId == id) fail(CartographicStoreError.INVALID_INPUT)
        sourceTraceId?.let(::canonicalId)
        val prepared = try { TracePayloadCodec.prepare(name, snapshot, saved) }
            catch (_: IllegalArgumentException) { fail(CartographicStoreError.INVALID_INPUT) }
        authority.transaction(token, mutation = true) { current ->
            sourceTraceId?.let { source ->
                val original = dao.byId(source) ?: fail(CartographicStoreError.UNAVAILABLE)
                owned(original, current)
                document(original)
                if (!original.saved) fail(CartographicStoreError.INVALID_INPUT)
            }
            val previous = dao.byId(id)
            if (previous != null) {
                owned(previous, current)
                validateRow(previous)
                if (previous.sourceTraceId != sourceTraceId) fail(CartographicStoreError.CONFLICT)
            } else if (expectedRevision != 0L) fail(CartographicStoreError.CONFLICT)
            if (previous?.saved == true) {
                if (!saved || previous.payloadJson != TracePayloadCodec.encodePrepared(prepared)) fail(CartographicStoreError.CONFLICT)
                return@transaction document(previous)
            }
            val checkpoint = previous?.let { try { TraceDraftJournal.decode(it.payloadJson) }
                catch (_: Exception) { fail(CartographicStoreError.CORRUPT) } }
            val plan = try { TraceDraftJournal.prepare(prepared.name, prepared.snapshot, checkpoint) }
                catch (_: IllegalArgumentException) { fail(CartographicStoreError.CONFLICT) }
            val encoded = if (saved) TracePayloadCodec.encodePrepared(prepared) else TraceDraftJournal.encode(plan.checkpoint)
            if (previous != null && !saved && previous.payloadJson == encoded) return@transaction document(previous)
            if (previous != null && previous.revision != expectedRevision) fail(CartographicStoreError.CONFLICT)
            // A final commit verifies every durable prefix row, then encodes the immutable full snapshot once.
            if (saved && previous != null) document(previous)
            val now = clock().takeIf { it > 0 } ?: fail(CartographicStoreError.IO)
            val row = CartographicTraceEntity(id, current.authorityKey, current.ownerKey,
                current.binding.kind, current.binding.id, saved, encoded, "", (previous?.revision ?: 0) + 1,
                now, if (saved) now else null, sourceTraceId)
            val complete = row.copy(payloadSha256 = TracePayloadCodec.rowHash(row))
            if (!saved) {
                if (plan.append.isNotEmpty()) dao.appendJournal(plan.append)
                plan.changedPortion?.let { dao.updatePortion(it) }
            }
            if (previous == null) dao.insert(complete) else if (dao.update(complete) != 1) fail(CartographicStoreError.CONFLICT)
            if (saved) dao.deleteJournal(id)
            val readBack = dao.byId(id) ?: fail(CartographicStoreError.IO)
            owned(readBack, current)
            if (readBack != complete) fail(CartographicStoreError.IO)
            // The same transaction acknowledged the new delta and checkpoint; avoid rereading the growing prefix.
            fromPayload(readBack, prepared)
        }
    }

    override suspend fun read(id: String, access: CartographicMeasurementAccess): CartographicStoreResult<CartographicTraceDocument> = authority.result {
        canonicalId(id)
        authority.transaction(token(access)) { current ->
            val row = dao.byId(id) ?: fail(CartographicStoreError.UNAVAILABLE)
            owned(row, current)
            document(row)
        }
    }
    override suspend fun list(access: CartographicMeasurementAccess): CartographicStoreResult<List<CartographicTraceDocument>> = authority.result {
        authority.transaction(token(access)) { current -> dao.inScope(current.authorityKey).map { owned(it, current); document(it) } }
    }

    private fun token(access: CartographicMeasurementAccess): CartographicScopeAuthority.Token {
        val token = access as? CartographicScopeAuthority.Token ?: fail(CartographicStoreError.ACCESS_CHANGED)
        authority.requireCurrent(token)
        return token
    }
    private fun owned(row: CartographicTraceEntity, token: CartographicScopeAuthority.Token) {
        if (row.authorityKey != token.authorityKey || row.ownerKey != token.ownerKey ||
            row.scopeKind != token.binding.kind || row.scopeId != token.binding.id) fail(CartographicStoreError.UNAVAILABLE)
    }
    private fun validateRow(row: CartographicTraceEntity) {
        if (row.payloadSha256 != TracePayloadCodec.rowHash(row) || row.revision <= 0 || row.updatedAt <= 0 ||
            row.saved != (row.recordedAt != null) || row.recordedAt?.let { it <= 0 } == true) fail(CartographicStoreError.CORRUPT)
    }
    private suspend fun document(row: CartographicTraceEntity): CartographicTraceDocument {
        validateRow(row)
        val payload = try {
            if (row.saved) TracePayloadCodec.decode(row.payloadJson, true)
            else {
                val checkpoint = TraceDraftJournal.decode(row.payloadJson)
                TracePayloadCodec.prepare(checkpoint.name, TraceDraftJournal.restore(checkpoint, dao.journal(row.id)), false)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { fail(CartographicStoreError.CORRUPT) }
        return fromPayload(row, payload)
    }
    private fun fromPayload(row: CartographicTraceEntity, payload: TracePayloadCodec.Payload): CartographicTraceDocument {
        if (payload.snapshot.sessionId != row.id) fail(CartographicStoreError.CORRUPT)
        return CartographicTraceDocument(row.id, payload.name, payload.snapshot, row.saved, row.revision,
            row.updatedAt, row.recordedAt, row.sourceTraceId, payload.crs, payload.source, payload.methodId, payload.methodVersion)
    }

    private fun canonicalId(id: String) {
        if (runCatching { UUID.fromString(id).toString() }.getOrNull() != id) fail(CartographicStoreError.INVALID_INPUT)
    }
    private fun fail(reason: CartographicStoreError): Nothing = throw CartographicAuthorityFailure(reason)
}

/** Strict, bounded durable snapshot; SI length is rechecked within each portion only. */
internal object TracePayloadCodec {
    const val MAX_NAME_LENGTH = 120
    const val MAX_PAYLOAD_CHARACTERS = 20_000_000
    private val json = Json { encodeDefaults = true }
    @Serializable
    data class Payload(val schema: Int = 1, val name: String?, val snapshot: TraceCaptureSnapshot,
        val crs: String = "EPSG:4326", val source: String = "phone_location",
        val methodId: String = SphericalTraceDistance.METHOD_ID, val methodVersion: Int = 1)

    fun prepare(name: String?, snapshot: TraceCaptureSnapshot, saved: Boolean): Payload {
        val cleanName = name?.trim()?.takeIf { it.isNotEmpty() }
        require(cleanName == null || cleanName.length <= MAX_NAME_LENGTH && cleanName.none { it.isISOControl() })
        val safe = snapshot.copy(lastReceivedFix = snapshot.lastReceivedFix?.takeIf(::serialisableFix))
        validate(safe, saved)
        return Payload(name = cleanName, snapshot = safe)
    }
    fun encode(name: String?, snapshot: TraceCaptureSnapshot, saved: Boolean): String = encodePrepared(prepare(name, snapshot, saved))
    fun encodePrepared(payload: Payload): String = json.encodeToString(payload).also { require(it.length <= MAX_PAYLOAD_CHARACTERS) }
    fun decode(payload: String, saved: Boolean): Payload {
        require(payload.length <= MAX_PAYLOAD_CHARACTERS)
        val decoded = json.decodeFromString<Payload>(payload)
        require(decoded.schema == 1 && decoded.crs == "EPSG:4326" && decoded.source == "phone_location" &&
            decoded.methodId == SphericalTraceDistance.METHOD_ID && decoded.methodVersion == 1)
        require(decoded.name == null || decoded.name.isNotBlank() && decoded.name == decoded.name.trim() &&
            decoded.name.length <= MAX_NAME_LENGTH && decoded.name.none { it.isISOControl() })
        validate(decoded.snapshot, saved)
        return decoded
    }
    fun rowHash(row: CartographicTraceEntity): String = hash(json.encodeToString(listOf(row.id, row.authorityKey,
        row.ownerKey, row.scopeKind, row.scopeId, row.saved.toString(), row.payloadJson, row.revision.toString(),
        row.updatedAt.toString(), row.recordedAt?.toString(), row.sourceTraceId)))
    private fun hash(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .let(::hex)
    private fun serialisableFix(fix: TraceFix): Boolean = (fix.provider?.length ?: 0) <= 64 && (fix.clockDomainId?.length ?: 0) <= 128 && fix.latitude.isFinite() && fix.longitude.isFinite() &&
        fix.horizontalAccuracyM?.isFinite() != false && fix.altitudeM?.isFinite() != false

    private fun validate(snapshot: TraceCaptureSnapshot, saved: Boolean) {
        require(snapshot.sessionId.isNotBlank() && snapshot.sessionElapsedNanos >= 0 && snapshot.generation >= 0)
        require(snapshot.calculationMethod == SphericalTraceDistance.METHOD_ID)
        require(snapshot.currentManualPauseElapsedNanos == null || snapshot.currentManualPauseElapsedNanos >= 0)
        require(snapshot.capturePolicy.maximumAcceptedPoints <= 50_000)
        require(snapshot.portions.size <= 50_000 && snapshot.acceptedPointCount <= snapshot.capturePolicy.maximumAcceptedPoints && snapshot.acceptedPointCount <= 50_000)
        require(snapshot.events.size <= 100_000)
        require(snapshot.portions.map { it.id }.distinct().size == snapshot.portions.size)
        snapshot.portions.forEach { portion ->
            require(portion.id.length <= 128)
            require(portion.points.isNotEmpty() && portion.lengthM.isFinite() && portion.lengthM >= 0)
            var length = 0.0
            portion.points.forEachIndexed { index, fix ->
                require(serialisableFix(fix) && SphericalTraceDistance.isValidCoordinate(fix.latitude, fix.longitude))
                require(!fix.isMock && fix.horizontalAccuracyM?.let { it > 0 && it <= snapshot.capturePolicy.maximumHorizontalAccuracyM } == true)
                require(fix.fixElapsedRealtimeNanos > 0 && fix.source == TracePositionSource.PHONE_LOCATION)
                if (index > 0) {
                    val previous = portion.points[index - 1]
                    require(previous.clockDomainId == fix.clockDomainId && previous.fixElapsedRealtimeNanos < fix.fixElapsedRealtimeNanos)
                    length += requireNotNull(SphericalTraceDistance.betweenM(previous, fix))
                }
            }
            require(length == portion.lengthM)
        }
        require(snapshot.lengthM.isFinite())
        if (saved) require(snapshot.phase == TraceCapturePhase.STOPPED_DRAFT && snapshot.portions.any { it.points.size >= 2 })
    }
    private fun hex(bytes: ByteArray): String {
        val digits = "0123456789abcdef"
        return CharArray(bytes.size * 2).also { out -> bytes.forEachIndexed { i, value ->
            val byte = value.toInt() and 255
            out[i * 2] = digits[byte ushr 4]
            out[i * 2 + 1] = digits[byte and 15]
        } }.concatToString()
    }

}
