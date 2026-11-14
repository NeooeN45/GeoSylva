package com.forestry.counter.data.repository

import com.forestry.counter.data.local.entity.CartographicTraceJournalEntity
import com.forestry.counter.domain.location.trace.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/** Bounded checkpoint plus append deltas. No draft write serialises its accepted prefix again. */
internal object TraceDraftJournal {
    const val MAX_CHECKPOINT_CHARACTERS = 4_096
    private const val EMPTY_CHAIN = "geosylva-trace-journal-v1"
    private val json = Json { encodeDefaults = true }
    @Serializable data class Checkpoint(
        val schema: Int = 1,
        val name: String?,
        /** Always empty geometry/events; constant-size policy, phase, times and last observation. */
        val frame: TraceCaptureSnapshot,
        val pointCount: Int,
        val portionCount: Int,
        val eventCount: Int,
        val lastPortionPointCount: Int,
        val lastPortionLengthM: Double,
        val pointChain: String,
        val portionIdentityChain: String,
        val eventChain: String,
    )
    @Serializable data class PointEntry(val portionOrdinal: Int, val pointOrdinal: Int, val fix: TraceFix)
    @Serializable data class PortionEntry(val id: String, val startReason: TraceBreakReason, val pointCount: Int, val lengthM: Double)
    data class Plan(val checkpoint: Checkpoint, val append: List<CartographicTraceJournalEntity>, val changedPortion: CartographicTraceJournalEntity?)

    fun prepare(name: String?, snapshot: TraceCaptureSnapshot, previous: Checkpoint?): Plan {
        val payload = TracePayloadCodec.prepare(name, snapshot, false)
        val safe = payload.snapshot
        if (previous != null) {
            require(previous.frame.sessionId == safe.sessionId && previous.frame.capturePolicy == safe.capturePolicy)
            require(safe.sessionElapsedNanos >= previous.frame.sessionElapsedNanos && safe.generation >= previous.frame.generation)
            require(safe.acceptedPointCount >= previous.pointCount && safe.portions.size >= previous.portionCount && safe.events.size >= previous.eventCount)
        }
        val append = mutableListOf<CartographicTraceJournalEntity>()
        var pointChain = EMPTY_CHAIN
        var pointOrdinal = 0
        var prefixPointChain: String? = if (previous?.pointCount == 0) EMPTY_CHAIN else null
        var portionChain = EMPTY_CHAIN
        var prefixPortionChain: String? = if (previous?.portionCount == 0) EMPTY_CHAIN else null
        safe.portions.forEachIndexed { portionIndex, portion ->
            portionChain = chain(portionChain, json.encodeToString(listOf(portion.id, portion.startReason.name)))
            if (portionIndex + 1 == previous?.portionCount) prefixPortionChain = portionChain
            portion.points.forEachIndexed { index, point ->
                val encoded = json.encodeToString(PointEntry(portionIndex, index, point))
                pointChain = chain(pointChain, encoded)
                if (pointOrdinal >= (previous?.pointCount ?: 0)) append += row(safe.sessionId, "POINT", pointOrdinal, encoded)
                pointOrdinal++
                if (pointOrdinal == previous?.pointCount) prefixPointChain = pointChain
            }
        }
        var eventChain = EMPTY_CHAIN
        var prefixEventChain: String? = if (previous?.eventCount == 0) EMPTY_CHAIN else null
        safe.events.forEachIndexed { index, event ->
            val encoded = json.encodeToString(event)
            eventChain = chain(eventChain, encoded)
            if (index >= (previous?.eventCount ?: 0)) append += row(safe.sessionId, "EVENT", index, encoded)
            if (index + 1 == previous?.eventCount) prefixEventChain = eventChain
        }
        if (previous != null) {
            require(prefixPointChain == previous.pointChain && prefixEventChain == previous.eventChain && prefixPortionChain == previous.portionIdentityChain) {
                "An existing trace prefix is immutable"
            }
        }
        var changed: CartographicTraceJournalEntity? = null
        safe.portions.forEachIndexed { index, portion ->
            val encoded = json.encodeToString(PortionEntry(portion.id, portion.startReason, portion.points.size, portion.lengthM))
            if (index >= (previous?.portionCount ?: 0)) append += row(safe.sessionId, "PORTION", index, encoded)
            else if (previous != null && index == previousPortionIndex(previous) &&
                (portion.points.size != previous.lastPortionPointCount || portion.lengthM != previous.lastPortionLengthM)) {
                require(portion.points.size >= previous.lastPortionPointCount)
                changed = row(safe.sessionId, "PORTION", index, encoded)
            }
        }
        val last = safe.portions.lastOrNull()
        val checkpoint = Checkpoint(name = payload.name, frame = safe.copy(portions = emptyList(), events = emptyList()),
            pointCount = safe.acceptedPointCount, portionCount = safe.portions.size, eventCount = safe.events.size,
            lastPortionPointCount = last?.points?.size ?: 0, lastPortionLengthM = last?.lengthM ?: 0.0,
            pointChain = pointChain, portionIdentityChain = portionChain, eventChain = eventChain)
        encode(checkpoint) // Asserts the metadata bound before any journal mutation.
        return Plan(checkpoint, append, changed)
    }
    private fun previousPortionIndex(previous: Checkpoint?) = (previous?.portionCount ?: 0) - 1
    fun encode(value: Checkpoint): String = json.encodeToString(value).also { require(it.length <= MAX_CHECKPOINT_CHARACTERS) }
    fun decode(value: String): Checkpoint {
        require(value.length <= MAX_CHECKPOINT_CHARACTERS)
        return json.decodeFromString<Checkpoint>(value).also {
            require(it.schema == 1 && it.frame.portions.isEmpty() && it.frame.events.isEmpty())
            require(it.pointCount in 0..50_000 && it.portionCount in 0..50_000 && it.eventCount in 0..100_000)
            require(it.pointCount >= it.portionCount && it.lastPortionPointCount in 0..it.pointCount)
            require(it.lastPortionLengthM.isFinite() && it.lastPortionLengthM >= 0)
        }
    }
    fun restore(checkpoint: Checkpoint, rows: List<CartographicTraceJournalEntity>): TraceCaptureSnapshot {
        require(rows.size == checkpoint.pointCount + checkpoint.portionCount + checkpoint.eventCount)
        require(rows.all { it.traceId == checkpoint.frame.sessionId && it.payloadSha256 == rowHash(it) })
        fun group(kind: String, count: Int): List<CartographicTraceJournalEntity> = rows.filter { it.kind == kind }.sortedBy { it.ordinal }.also { selected ->
            require(selected.size == count && selected.map { it.ordinal } == (0 until count).toList())
        }
        val pointRows = group("POINT", checkpoint.pointCount)
        val portionRows = group("PORTION", checkpoint.portionCount)
        val eventRows = group("EVENT", checkpoint.eventCount)
        var pointChain = EMPTY_CHAIN
        val points = pointRows.map { pointChain = chain(pointChain, it.payloadJson); json.decodeFromString<PointEntry>(it.payloadJson) }
        var eventChain = EMPTY_CHAIN
        val events = eventRows.map { eventChain = chain(eventChain, it.payloadJson); json.decodeFromString<TraceCaptureEvent>(it.payloadJson) }
        var portionChain = EMPTY_CHAIN
        val grouped = points.groupBy { it.portionOrdinal }
        val portions = portionRows.mapIndexed { index, row ->
            val meta = json.decodeFromString<PortionEntry>(row.payloadJson)
            portionChain = chain(portionChain, json.encodeToString(listOf(meta.id, meta.startReason.name)))
            val entries = grouped[index].orEmpty()
            require(entries.size == meta.pointCount && entries.map { it.pointOrdinal } == (0 until meta.pointCount).toList())
            TracePortion(meta.id, meta.startReason, entries.map { it.fix }, meta.lengthM)
        }
        require(pointChain == checkpoint.pointChain && eventChain == checkpoint.eventChain && portionChain == checkpoint.portionIdentityChain)
        require(portions.sumOf { it.points.size } == checkpoint.pointCount)
        val snapshot = checkpoint.frame.copy(portions = portions, events = events)
        TracePayloadCodec.prepare(checkpoint.name, snapshot, false)
        require((snapshot.portions.lastOrNull()?.points?.size ?: 0) == checkpoint.lastPortionPointCount)
        require((snapshot.portions.lastOrNull()?.lengthM ?: 0.0) == checkpoint.lastPortionLengthM)
        return snapshot
    }
    fun row(traceId: String, kind: String, ordinal: Int, payload: String): CartographicTraceJournalEntity =
        CartographicTraceJournalEntity(traceId, kind, ordinal, payload, "").let { it.copy(payloadSha256 = rowHash(it)) }
    private fun rowHash(row: CartographicTraceJournalEntity) = sha256(json.encodeToString(listOf(row.traceId, row.kind, row.ordinal.toString(), row.payloadJson)))
    private fun chain(previous: String, payload: String) = sha256(previous + "\n" + payload)
    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
