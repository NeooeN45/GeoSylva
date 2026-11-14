package com.forestry.counter.data.local.entity

import androidx.room.Entity

/** Append-only observations/events; only the currently open portion summary can grow. */
@Entity(tableName = "cartographic_trace_journal", primaryKeys = ["traceId", "kind", "ordinal"])
data class CartographicTraceJournalEntity(
    val traceId: String,
    val kind: String,
    val ordinal: Int,
    val payloadJson: String,
    val payloadSha256: String,
)
