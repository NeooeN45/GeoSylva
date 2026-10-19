package com.forestry.counter.security

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseEncryptionKeyPersistenceTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = base.getSharedPreferences("TEST-database-key-persistence", Context.MODE_PRIVATE)
    private fun context(failCommit: Boolean = false) = object : ContextWrapper(base) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            if (!failCommit) preferences else object : SharedPreferences by preferences {
                override fun edit(): SharedPreferences.Editor {
                    val pending = preferences.edit()
                    return object : SharedPreferences.Editor by pending {
                        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                            pending.putString(key, value)
                            return this
                        }
                        override fun commit() = false
                    }
                }
            }
    }

    @Before fun setup() { preferences.edit().clear().commit() }
    @After fun cleanup() { preferences.edit().clear().commit() }

    @Test fun generated_key_is_durable_and_existing_key_is_reused_without_rotation() {
        val first = DatabaseEncryptionService(context()).getOrCreateDatabaseKey()
        val saved = preferences.all.toMap()
        val second = DatabaseEncryptionService(context()).getOrCreateDatabaseKey()
        try {
            assertEquals(32, first.size)
            assertTrue(first.any { it != 0.toByte() })
            assertTrue(first.contentEquals(second))
            assertTrue(saved == preferences.all) // No key material in assertion messages.
        } finally { first.fill(0); second.fill(0) }
    }

    @Test fun partial_metadata_is_not_replaced_with_a_new_key() {
        listOf("encrypted_db_key", "db_key_iv").forEach { name ->
            preferences.edit().clear().putString(name, "TEST partial metadata").commit()
            val before = preferences.all.toMap()
            reject { DatabaseEncryptionService(context()).getOrCreateDatabaseKey() }
            assertTrue(before == preferences.all)
        }
    }

    @Test fun failed_commit_never_returns_a_key_for_database_creation() {
        reject { DatabaseEncryptionService(context(failCommit = true)).getOrCreateDatabaseKey() }
        assertTrue(preferences.all.isEmpty())
    }

    @Test fun failed_durable_check_of_an_existing_key_does_not_replace_it() {
        val original = DatabaseEncryptionService(context()).getOrCreateDatabaseKey()
        try {
            val before = preferences.all.toMap()
            reject { DatabaseEncryptionService(context(failCommit = true)).getOrCreateDatabaseKey() }
            assertTrue(before == preferences.all)
            val reopened = DatabaseEncryptionService(context()).getOrCreateDatabaseKey()
            try { assertTrue(original.contentEquals(reopened)) } finally { reopened.fill(0) }
        } finally { original.fill(0) }
    }

    private fun reject(action: () -> Unit) {
        try { action(); fail("Expected key storage to fail closed") } catch (_: IllegalStateException) { }
    }
}
