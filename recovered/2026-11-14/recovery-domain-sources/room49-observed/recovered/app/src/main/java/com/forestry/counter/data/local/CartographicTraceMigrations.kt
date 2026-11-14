package com.forestry.counter.data.local

import androidx.sqlite.db.SupportSQLiteDatabase

object CartographicTraceMigrations {
    fun create(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS cartographic_traces (id TEXT NOT NULL PRIMARY KEY, authorityKey TEXT NOT NULL, ownerKey TEXT NOT NULL, scopeKind TEXT NOT NULL, scopeId TEXT NOT NULL, saved INTEGER NOT NULL, payloadJson TEXT NOT NULL, payloadSha256 TEXT NOT NULL, revision INTEGER NOT NULL, updatedAt INTEGER NOT NULL, recordedAt INTEGER, sourceTraceId TEXT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cartographic_traces_authorityKey_updatedAt ON cartographic_traces(authorityKey, updatedAt)")
        db.execSQL("CREATE TABLE IF NOT EXISTS cartographic_trace_journal (traceId TEXT NOT NULL, kind TEXT NOT NULL, ordinal INTEGER NOT NULL, payloadJson TEXT NOT NULL, payloadSha256 TEXT NOT NULL, PRIMARY KEY(traceId, kind, ordinal))")
    }
}
