package com.forestry.counter.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages SQLCipher database encryption key via Android Keystore.
 *
 * The key is generated once, stored in Android Keystore (hardware-backed on
 * supported devices), and never leaves the Keystore. The actual SQLCipher key
 * is encrypted with the Keystore key and persisted in SharedPreferences (encrypted).
 *
 * Protects sensitive local data (parcel ownership, GPS tracks). Article 32 RGPD
 * requires proportionate security measures; encryption alone is not compliance.
 */
class DatabaseEncryptionService(private val context: Context) {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "geosylva_db_master_key"
        private const val PREFS_NAME = "geosylva_secure_prefs"
        private const val ENCRYPTED_DB_KEY = "encrypted_db_key"
        private const val DB_KEY_IV = "db_key_iv"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val DB_KEY_LENGTH_BYTES = 32 // 256-bit key for SQLCipher
        private val databaseKeyLock = Any()
    }

    private val securePrefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Returns the SQLCipher database key, generating and persisting it on first call.
     * The key is a 256-bit random key, encrypted with the Android Keystore
     * master key and stored in SharedPreferences.
     *
     * @return SQLCipher key as ByteArray (caller should zero it after use)
     */
    fun getOrCreateDatabaseKey(): ByteArray = synchronized(databaseKeyLock) {
        val encryptedKey = securePrefs.getString(ENCRYPTED_DB_KEY, null)
        val storedIv = securePrefs.getString(DB_KEY_IV, null)
        check((encryptedKey == null) == (storedIv == null)) { "DATABASE_KEY_METADATA_INCOMPLETE" }

        if (encryptedKey != null && storedIv != null) {
            // Missing/corrupt metadata or an unavailable master key must never generate
            // replacement material for an existing encrypted database.
            val bytes = decryptKeyBytes(encryptedKey, Base64.decode(storedIv, Base64.NO_WRAP))
            try {
                check(bytes.size == DB_KEY_LENGTH_BYTES && bytes.any { it != 0.toByte() }) { "DATABASE_KEY_INVALID" }
                // Also retries a previous failed durable write whose value may have
                // entered SharedPreferences' process cache. Values are unchanged.
                persistKey(encryptedKey, storedIv)
                bytes
            } catch (error: Exception) {
                bytes.fill(0)
                throw error
            }
        } else {
            val newKey = ByteArray(DB_KEY_LENGTH_BYTES)
            java.security.SecureRandom().nextBytes(newKey)
            try {
                check(newKey.any { it != 0.toByte() }) { "DATABASE_KEY_INVALID" }
                val (encrypted, iv) = encryptKey(newKey)
                // Persist before the key can be used to create a database. apply() is
                // asynchronous and could lose the only copy on an immediate process kill.
                persistKey(encrypted, Base64.encodeToString(iv, Base64.NO_WRAP))
                newKey
            } catch (error: Exception) {
                newKey.fill(0)
                throw error
            }
        }
    }

    private fun persistKey(encrypted: String, iv: String) {
        check(securePrefs.edit().putString(ENCRYPTED_DB_KEY, encrypted)
            .putString(DB_KEY_IV, iv).commit()) { "DATABASE_KEY_PERSISTENCE_FAILED" }
    }

    /**
     * Checks whether the database key has been initialized.
     */
    fun isEncryptionKeyInitialized(): Boolean {
        return securePrefs.getString(ENCRYPTED_DB_KEY, null) != null
    }

    private fun getOrCreateMasterKey(allowCreation: Boolean = true): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }
        check(allowCreation) { "DATABASE_MASTER_KEY_UNAVAILABLE" }

        // Generate new AES-256 key in Keystore
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encryptKey(plainKey: ByteArray): Pair<String, ByteArray> {
        val masterKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        val encrypted = cipher.doFinal(plainKey)
        val iv = cipher.iv
        return Base64.encodeToString(encrypted, Base64.NO_WRAP) to iv
    }

    private fun decryptKeyBytes(encryptedKey: String, iv: ByteArray): ByteArray {
        val masterKey = getOrCreateMasterKey(allowCreation = false)
        check(iv.size == GCM_IV_LENGTH) { "DATABASE_KEY_IV_INVALID" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val params = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, masterKey, params)
        return cipher.doFinal(Base64.decode(encryptedKey, Base64.NO_WRAP))
    }
}
