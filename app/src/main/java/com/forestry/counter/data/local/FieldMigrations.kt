package com.forestry.counter.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object FieldMigrations {
    val MIGRATION_44_45 = object : Migration(44, 45) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS field_worksites (id TEXT NOT NULL PRIMARY KEY, ownerKey TEXT NOT NULL, payload TEXT NOT NULL, sha256 TEXT NOT NULL, revision INTEGER NOT NULL, archived INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_field_worksites_ownerKey ON field_worksites(ownerKey)")
            db.execSQL("CREATE TABLE IF NOT EXISTS field_site_links (siteId TEXT NOT NULL, targetKind TEXT NOT NULL, targetId TEXT NOT NULL, PRIMARY KEY(siteId,targetKind,targetId), FOREIGN KEY(siteId) REFERENCES field_worksites(id) ON DELETE RESTRICT)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_field_site_links_targetKind_targetId ON field_site_links(targetKind,targetId)")
            db.execSQL("CREATE TABLE IF NOT EXISTS field_entries (id TEXT NOT NULL PRIMARY KEY, ownerKey TEXT NOT NULL, targetKind TEXT NOT NULL, targetId TEXT NOT NULL, payload TEXT NOT NULL, sha256 TEXT NOT NULL, recordedAt INTEGER NOT NULL, author TEXT NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_field_entries_ownerKey_targetKind_targetId ON field_entries(ownerKey,targetKind,targetId)")
            db.execSQL("CREATE TABLE IF NOT EXISTS field_drafts (ownerKey TEXT NOT NULL, targetKind TEXT NOT NULL, targetId TEXT NOT NULL, slot TEXT NOT NULL, revision INTEGER NOT NULL, content TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(ownerKey,targetKind,targetId,slot))")
            db.execSQL("CREATE TABLE IF NOT EXISTS field_photos (id TEXT NOT NULL PRIMARY KEY, ownerKey TEXT NOT NULL, targetKind TEXT NOT NULL, targetId TEXT NOT NULL, caption TEXT NOT NULL, mime TEXT NOT NULL, bytes INTEGER NOT NULL, sha256 TEXT NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_field_photos_ownerKey_targetKind_targetId ON field_photos(ownerKey,targetKind,targetId)")
            db.execSQL("CREATE TABLE IF NOT EXISTS field_photo_chunks (photoId TEXT NOT NULL, part INTEGER NOT NULL, bytes BLOB NOT NULL, PRIMARY KEY(photoId,part), FOREIGN KEY(photoId) REFERENCES field_photos(id) ON DELETE CASCADE)")
        }
    }
}
