package com.forestry.counter.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds durable requests without inventing snapshots for the former mutable queue. */
object ParcelJournalMigrations {
    val MIGRATION_37_38 = object : Migration(37, 38) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS parcel_sync_operations (
                    sequence INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    operationId TEXT NOT NULL,
                    parcelId TEXT NOT NULL,
                    originAccountId TEXT,
                    destinationKind TEXT,
                    apiOrigin TEXT,
                    operation TEXT NOT NULL,
                    requestJson TEXT NOT NULL,
                    baseVersion INTEGER,
                    expectedServerVersion INTEGER,
                    predecessorOperationId TEXT,
                    createdAt INTEGER NOT NULL,
                    payloadFingerprint TEXT,
                    legacyReferenceJson TEXT,
                    state TEXT NOT NULL,
                    retryCount INTEGER NOT NULL,
                    lastAttemptAt INTEGER,
                    attemptId TEXT,
                    nextAttemptAt INTEGER NOT NULL,
                    lastErrorCode TEXT,
                    ackVersion INTEGER,
                    ackAt INTEGER
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_parcel_sync_operations_operationId ON parcel_sync_operations(operationId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_parcel_sync_operations_account_parcel_sequence ON parcel_sync_operations(originAccountId, parcelId, sequence)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_parcel_sync_operations_ready ON parcel_sync_operations(originAccountId, state, nextAttemptAt)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_parcel_sync_operations_predecessor ON parcel_sync_operations(predecessorOperationId)")
        }
    }
}
