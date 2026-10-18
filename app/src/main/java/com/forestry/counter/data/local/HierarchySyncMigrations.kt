package com.forestry.counter.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** No retroactive activation, account attribution or invented operation is created. */
object HierarchySyncMigrations {
    val MIGRATION_38_39 = object : Migration(38, 39) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS hierarchy_sync_associations (
                    forestId TEXT NOT NULL PRIMARY KEY, accountId TEXT NOT NULL, apiOrigin TEXT NOT NULL,
                    activatedAt INTEGER NOT NULL, lastCapturedHash TEXT, serverVersion INTEGER,
                    lastSuccessAt INTEGER, lastErrorCode TEXT
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_hierarchy_sync_associations_accountId ON hierarchy_sync_associations(accountId)")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS hierarchy_sync_operations (
                    sequence INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    operationId TEXT NOT NULL, forestId TEXT NOT NULL, accountId TEXT NOT NULL,
                    apiOrigin TEXT NOT NULL, requestJson TEXT NOT NULL, requestSha256 TEXT NOT NULL,
                    dossierSha256 TEXT NOT NULL, baseVersion INTEGER, expectedServerVersion INTEGER NOT NULL,
                    predecessorOperationId TEXT, createdAt INTEGER NOT NULL, containsLocalPhotos INTEGER NOT NULL,
                    recoveryRemoteJson TEXT, state TEXT NOT NULL, retryCount INTEGER NOT NULL,
                    nextAttemptAt INTEGER NOT NULL, attemptId TEXT, lastAttemptAt INTEGER,
                    lastErrorCode TEXT, ackVersion INTEGER, serverPayloadSha256 TEXT, receiptJson TEXT
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_hierarchy_sync_operations_operationId ON hierarchy_sync_operations(operationId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_hierarchy_sync_operations_accountId_forestId_sequence ON hierarchy_sync_operations(accountId, forestId, sequence)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_hierarchy_sync_operations_accountId_state_nextAttemptAt ON hierarchy_sync_operations(accountId, state, nextAttemptAt)")
        }
    }
}
