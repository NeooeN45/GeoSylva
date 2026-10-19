package com.forestry.counter.security

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper

/** Owns its key until the first actual database opening, not just Room.build(). */
class SecureSqlCipherOpenHelperFactory(passphrase: ByteArray) : SupportSQLiteOpenHelper.Factory {
    private var pendingKey: ByteArray? = passphrase.copyOf().also {
        require(it.size == 32 && it.any { byte -> byte != 0.toByte() }) { "DATABASE_KEY_INVALID" }
    }

    @Synchronized
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val ownedKey = checkNotNull(pendingKey) { "DATABASE_FACTORY_ALREADY_USED" }
        pendingKey = null
        return SecureHelper(configuration, ownedKey)
    }

    private class SecureHelper(
        private val configuration: SupportSQLiteOpenHelper.Configuration,
        private val ownedKey: ByteArray,
    ) : SupportSQLiteOpenHelper {
        private var closed = false
        private var prepared = false
        private val helper: SQLiteOpenHelper

        init {
            SQLiteDatabase.loadLibs(configuration.context)
            helper = object : SQLiteOpenHelper(
                configuration.context, configuration.name, null, configuration.callback.version,
                null, LegacySqlCipherRecovery.preserveOnCorruption,
            ) {
                override fun onConfigure(db: SQLiteDatabase) = configuration.callback.onConfigure(db)
                override fun onCreate(db: SQLiteDatabase) = configuration.callback.onCreate(db)
                override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = configuration.callback.onUpgrade(db, old, new)
                override fun onDowngrade(db: SQLiteDatabase, old: Int, new: Int) = configuration.callback.onDowngrade(db, old, new)
                override fun onOpen(db: SQLiteDatabase) = configuration.callback.onOpen(db)
            }
        }

        override val databaseName: String? get() = configuration.name

        @Synchronized
        override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
            check(!closed) { "DATABASE_CLOSED" }
            helper.setWriteAheadLoggingEnabled(enabled)
        }

        override val writableDatabase: SupportSQLiteDatabase
            @Synchronized get() {
                check(!closed) { "DATABASE_CLOSED" }
                if (!prepared) {
                    val name = checkNotNull(configuration.name) { "DATABASE_NAME_REQUIRED" }
                    // Executed on Room's opening thread. No recovery I/O in Application.onCreate.
                    LegacySqlCipherRecovery.prepare(configuration.context, name, ownedKey)
                    prepared = true
                }
                return helper.getWritableDatabase(ownedKey).also { ownedKey.fill(0) }
            }

        override val readableDatabase: SupportSQLiteDatabase get() = writableDatabase

        @Synchronized
        override fun close() {
            try { helper.close() } finally {
                closed = true
                ownedKey.fill(0)
            }
        }
    }
}
