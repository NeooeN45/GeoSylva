package com.forestry.counter.security

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteException
import com.forestry.counter.data.local.ForestryDatabase
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import net.sqlcipher.DatabaseErrorHandler
import net.sqlcipher.database.SQLiteDatabase

/**
 * One bounded recovery for historical GeoSylva databases opened with a prematurely
 * cleared 32-byte key. Never opens an unrecognised database with a fallback key.
 * The old file survives until a fully verified encrypted copy is atomically installed.
 */
internal object LegacySqlCipherRecovery {
    enum class Result { NEW_DATABASE, CURRENT_KEY, RECOVERED_LEGACY_KEY }
    enum class Phase { COPY_VERIFIED, COPY_INSTALLED }

    // SQLCipher's default error handler may delete corrupted databases. We never do.
    val preserveOnCorruption = DatabaseErrorHandler { throw SQLiteException("DATABASE_CORRUPT_PRESERVED") }

    fun prepare(
        context: Context,
        name: String,
        key: ByteArray,
        checkpoint: (Phase) -> Unit = {},
    ): Result {
        require(name == ForestryDatabase.DATABASE_NAME) { "DATABASE_NAME_NOT_AUTHORIZED" }
        require(key.size == 32 && key.any { it != 0.toByte() }) { "DATABASE_KEY_INVALID" }
        val path = context.getDatabasePath(name)
        if (!path.exists()) return Result.NEW_DATABASE
        check(path.isFile && path.length() > 0) { "DATABASE_EXISTING_FILE_INVALID" }
        SQLiteDatabase.loadLibs(context)
        // Only the local database startup code uses this lock; no Android database is
        // opened by this application until prepare() returns.
        FileChannel.open(File(path.parentFile, ".${path.name}.key-recovery.lock").toPath(),
            StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            channel.lock().use {
                val status = context.getSharedPreferences("geosylva_database_security", Context.MODE_PRIVATE)
                fun recordRecovered() {
                    check(status.edit().putBoolean("legacy_zero_key_recovered", true)
                        .putBoolean("legacy_zero_key_recovery_pending", false).commit()) {
                        "DATABASE_RECOVERY_STATUS_NOT_SAVED"
                    }
                }
                if (readable(path, key)) {
                    // A process may have stopped after the atomic replacement but before
                    // its status was recorded. The current key proves that switch completed.
                    if (status.getBoolean("legacy_zero_key_recovery_pending", false)) recordRecovered()
                    return Result.CURRENT_KEY
                }
                val legacyKey = ByteArray(32)
                try {
                    open(path, legacyKey, SQLiteDatabase.OPEN_READONLY).use { source ->
                        requireKnownLegacy(source)
                        requireIntegrity(source)
                    }
                    recover(path, key, legacyKey, checkpoint) {
                        check(status.edit().putBoolean("legacy_zero_key_recovery_pending", true).commit()) {
                            "DATABASE_RECOVERY_STATUS_NOT_SAVED"
                        }
                    }
                    // This marker records the operation, never its key or business data.
                    recordRecovered()
                    return Result.RECOVERED_LEGACY_KEY
                } catch (_: SQLiteException) {
                    throw IllegalStateException("DATABASE_KEY_OR_INTEGRITY_UNAVAILABLE_DATA_PRESERVED")
                } finally { legacyKey.fill(0) }
            }
        }
    }

    private fun readable(path: File, key: ByteArray): Boolean = try {
        open(path, key, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT count(*) FROM sqlite_master", emptyArray<String>()).use { it.moveToFirst() }
        }
    } catch (_: SQLiteException) { false }

    private fun open(path: File, key: ByteArray, flags: Int): SQLiteDatabase =
        SQLiteDatabase.openDatabase(path.absolutePath, key, null, flags, null, preserveOnCorruption)

    private fun recover(path: File, key: ByteArray, legacyKey: ByteArray, checkpoint: (Phase) -> Unit, beforeInstall: () -> Unit) {
        // A unique private file avoids overwriting an interrupted recovery or any other DB.
        // Only this operation's file is removed on normal failure. A process kill can leave
        // an encrypted temporary copy, but the source remains retryable and untouched.
        val copy = File.createTempFile(".${path.name}.key-recovery-", ".db", path.parentFile)
        try {
            val expected: String
            open(path, legacyKey, SQLiteDatabase.OPEN_READWRITE).use { source ->
                requireKnownLegacy(source)
                requireIntegrity(source)
                source.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", emptyArray<String>()).use {
                    check(it.moveToFirst() && it.getInt(0) == 0) { "DATABASE_RECOVERY_BUSY" }
                }
                source.rawQuery("PRAGMA journal_mode=DELETE", emptyArray<String>()).use {
                    check(it.moveToFirst() && it.getString(0).equals("delete", true)) { "DATABASE_RECOVERY_BUSY" }
                }
                // Binary bind keeps arbitrary 32-byte passwords intact; no key in SQL text.
                source.execSQL("ATTACH DATABASE ? AS recovered KEY ?", arrayOf(copy.absolutePath, key))
                try {
                    source.beginTransaction()
                    try {
                        expected = fingerprint(source)
                        source.rawQuery("SELECT sqlcipher_export('recovered')", emptyArray<String>()).use {
                            check(it.moveToFirst()) { "DATABASE_RECOVERY_EXPORT_FAILED" }
                        }
                        source.execSQL("PRAGMA recovered.user_version = ${source.version}")
                        source.execSQL("PRAGMA recovered.application_id = ${applicationId(source)}")
                        source.setTransactionSuccessful()
                    } finally { source.endTransaction() }
                } finally { source.execSQL("DETACH DATABASE recovered") }
            }
            open(copy, key, SQLiteDatabase.OPEN_READONLY).use { restored ->
                requireKnownLegacy(restored)
                requireIntegrity(restored)
                check(fingerprint(restored) == expected) { "DATABASE_RECOVERY_CONTENT_MISMATCH" }
            }
            // Closed/checkpointed source: these must never be moved onto a new ciphertext.
            check(listOf("-wal", "-journal").none { File(path.absolutePath + it).let { f -> f.exists() && f.length() > 0 } }) {
                "DATABASE_RECOVERY_SIDECAR_ACTIVE"
            }
            val sourceHash = fileHash(path)
            FileOutputStream(copy, true).use { it.fd.sync() }
            checkpoint(Phase.COPY_VERIFIED)
            check(fileHash(path) == sourceHash) { "DATABASE_RECOVERY_SOURCE_CHANGED" }
            beforeInstall()
            // Both paths share one directory/filesystem. No non-atomic fallback is allowed.
            Files.move(copy.toPath(), path.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            FileChannel.open(path.parentFile!!.toPath(), StandardOpenOption.READ).use { it.force(true) }
            checkpoint(Phase.COPY_INSTALLED)
            check(readable(path, key)) { "DATABASE_RECOVERY_REOPEN_FAILED" }
        } finally {
            // Never remove the original. Any leftover recovery file is encrypted with key.
            if (copy.exists()) copy.delete()
            listOf("-wal", "-shm", "-journal").forEach { File(copy.absolutePath + it).delete() }
        }
    }

    private fun requireKnownLegacy(db: SQLiteDatabase) {
        val expectedHash = historicalRoomHashes[db.version] ?: error("DATABASE_LEGACY_VERSION_NOT_RECOGNIZED")
        db.rawQuery("SELECT identity_hash FROM room_master_table WHERE id=42", emptyArray<String>()).use {
            check(it.moveToFirst() && it.getString(0) == expectedHash && !it.moveToNext()) {
                "DATABASE_LEGACY_IDENTITY_NOT_RECOGNIZED"
            }
        }
    }

    private fun requireIntegrity(db: SQLiteDatabase) {
        db.rawQuery("PRAGMA integrity_check", emptyArray<String>()).use {
            check(it.moveToFirst() && it.getString(0) == "ok" && !it.moveToNext()) { "DATABASE_INTEGRITY_FAILED" }
        }
    }

    private fun applicationId(db: SQLiteDatabase): Int =
        db.rawQuery("PRAGMA application_id", emptyArray<String>()).use { check(it.moveToFirst()); it.getInt(0) }

    /** Compare schema and the multiset of typed rows; physical rowids/pages may change. */
    private fun fingerprint(db: SQLiteDatabase): String {
        data class Schema(val type: String, val name: String, val table: String, val sql: String?)
        val schema = db.rawQuery("SELECT type,name,tbl_name,sql FROM sqlite_master ORDER BY type,name", emptyArray<String>()).use { cursor ->
            buildList { while (cursor.moveToNext()) add(Schema(cursor.getString(0),cursor.getString(1),cursor.getString(2),cursor.getString(3))) }
        }
        val fts = schema.filter { it.sql?.startsWith("CREATE VIRTUAL TABLE", true) == true }.map { it.name }
        val shadows = fts.flatMap { name -> listOf("_content", "_segments", "_segdir", "_docsize", "_stat").map { name + it } }.toSet()
        val included = schema.filter { it.name !in shadows && (!it.name.startsWith("sqlite_") || it.name == "sqlite_sequence") }
        val digest = MessageDigest.getInstance("SHA-256")
        fun value(text: String?) = bytes(digest, text?.toByteArray(Charsets.UTF_8))
        value(db.version.toString()); value(applicationId(db).toString())
        included.forEach { value(it.type); value(it.name); value(it.table); value(it.sql) }
        included.filter { it.type == "table" }.forEach { table ->
            value(table.name)
            val hashes = ArrayList<ByteArray>()
            val quoted = "\"" + table.name.replace("\"", "\"\"") + "\""
            db.rawQuery("SELECT * FROM $quoted", emptyArray<String>()).use { cursor ->
                cursor.columnNames.forEach(::value)
                while (cursor.moveToNext()) {
                    check(hashes.size < 1_000_000) { "DATABASE_RECOVERY_TABLE_TOO_LARGE" }
                    val row = MessageDigest.getInstance("SHA-256")
                    for (column in 0 until cursor.columnCount) {
                        row.update(cursor.getType(column).toByte())
                        bytes(row, when (cursor.getType(column)) {
                            Cursor.FIELD_TYPE_NULL -> null
                            Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(column)
                            else -> cursor.getString(column).toByteArray(Charsets.UTF_8)
                        })
                    }
                    hashes += row.digest()
                }
            }
            value(hashes.size.toString())
            hashes.sortWith { left, right ->
                var order = 0
                var i = 0
                while (order == 0 && i < left.size) {
                    order = (left[i].toInt() and 0xff).compareTo(right[i].toInt() and 0xff)
                    i++
                }
                order
            }
            hashes.forEach { bytes(digest, it) }
        }
        return hex(digest.digest())
    }

    private fun bytes(digest: MessageDigest, value: ByteArray?) {
        digest.update(ByteBuffer.allocate(4).putInt(value?.size ?: -1).array())
        if (value != null) digest.update(value)
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private fun fileHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val size = input.read(buffer); if (size < 0) break; digest.update(buffer, 0, size) }
        }
        return hex(digest.digest())
    }

    // Exact identities from committed Room schemas, frozen to affected historical versions.
    // Missing/unknown versions are preserved for supervised recovery, never guessed.
    private val historicalRoomHashes = mapOf(
        30 to "596c4f629855e4d88ff6f96863b4361b", 31 to "3729f94ab2eced9a4bb28e66af2ca7cb",
        32 to "6a2ada38ae54d804af460a29701fa9cc", 33 to "f7aeaaff97fbe57bd82385509ffd9580",
        34 to "d277986eaea862c5958a64b75c61f66c", 35 to "d5a3ccc69ef316a8193ddad9404543db",
        36 to "3209d659687f60b7a0177163edc884c6", 39 to "1f25bafc773c172d377c7859e41ae2f2",
        40 to "d9f1eb3fec4ec2004d70f82ff8871116", 41 to "616ca01ecb2b0713e46d7e502b3a13ab",
        42 to "5796ffd14f4abe172d647eddbe7ff30d", 43 to "22978fa095f12ff96dfb66c3bf2cc5d3",
        44 to "f3d3db9690ed57bb39d5f09d9322c0c0", 45 to "aa3472754fb87f3fb7c2e37fb96f8c60",
    )
}
