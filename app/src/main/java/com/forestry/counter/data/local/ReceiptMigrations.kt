package com.forestry.counter.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object ReceiptMigrations {
    val MIGRATION_45_46 = object : Migration(45,46) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Additive only: historical stocks are not converted into certified receipts.
            db.execSQL("CREATE TABLE IF NOT EXISTS field_receipts (id TEXT NOT NULL PRIMARY KEY, ownerKey TEXT NOT NULL, siteId TEXT NOT NULL, revision INTEGER NOT NULL, FOREIGN KEY(siteId) REFERENCES field_worksites(id) ON DELETE RESTRICT)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_field_receipts_siteId ON field_receipts(siteId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_field_receipts_ownerKey ON field_receipts(ownerKey)")
            db.execSQL("CREATE TABLE IF NOT EXISTS field_receipt_versions (receiptId TEXT NOT NULL, revision INTEGER NOT NULL, payload TEXT NOT NULL, sha256 TEXT NOT NULL, PRIMARY KEY(receiptId,revision), FOREIGN KEY(receiptId) REFERENCES field_receipts(id) ON DELETE RESTRICT)")
            db.execSQL("CREATE TABLE IF NOT EXISTS field_receipt_credits (receiptId TEXT NOT NULL, revision INTEGER NOT NULL, lineId TEXT NOT NULL, objectId TEXT NOT NULL, entryId TEXT NOT NULL, cancellationEntryId TEXT, PRIMARY KEY(receiptId,revision,lineId), FOREIGN KEY(receiptId,revision) REFERENCES field_receipt_versions(receiptId,revision) ON DELETE RESTRICT, FOREIGN KEY(entryId) REFERENCES field_entries(id) ON DELETE RESTRICT, FOREIGN KEY(cancellationEntryId) REFERENCES field_entries(id) ON DELETE RESTRICT, FOREIGN KEY(objectId) REFERENCES wood_objects(objectId) ON DELETE RESTRICT)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_field_receipt_credits_entryId ON field_receipt_credits(entryId)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_field_receipt_credits_cancellationEntryId ON field_receipt_credits(cancellationEntryId)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_field_receipt_credits_objectId ON field_receipt_credits(objectId)")
            db.execSQL("CREATE TABLE IF NOT EXISTS field_receipt_commands (id TEXT NOT NULL PRIMARY KEY, ownerKey TEXT NOT NULL, receiptId TEXT NOT NULL, payload TEXT NOT NULL, sha256 TEXT NOT NULL, resultRevision INTEGER NOT NULL, FOREIGN KEY(receiptId,resultRevision) REFERENCES field_receipt_versions(receiptId,revision) ON DELETE RESTRICT)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_field_receipt_commands_receiptId_resultRevision ON field_receipt_commands(receiptId,resultRevision)")
        }
    }
}
