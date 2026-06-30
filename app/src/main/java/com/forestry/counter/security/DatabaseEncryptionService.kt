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
 * RGPD compliance: database contains personal data (parcel ownership, GPS tracks).
 * Plaintext SQLite is a RGPD violation (Article 32 — security of processing).
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
    fun getOrCreateDatabaseKey(): ByteArray {
        val encryptedKey = securePrefs.getString(ENCRYPTED_DB_KEY, null)
        val storedIv = securePrefs.getString(DB_KEY_IV, null)

        return if (encryptedKey != null && storedIv != null) {
            // Existing key — decrypt with Keystore
            decryptKeyBytes(encryptedKey, Base64.decode(storedIv, Base64.NO_WRAP))
        } else {
            // First run — generate new key
            val newKey = ByteArray(DB_KEY_LENGTH_BYTES)
            java.security.SecureRandom().nextBytes(newKey)
            val (encrypted, iv) = encryptKey(newKey)
            securePrefs.edit()
                .putString(ENCRYPTED_DB_KEY, encrypted)
                .putString(DB_KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .apply()
            newKey
        }
    }

    /**
     * Checks whether the database key has been initialized.
     */
    fun isEncryptionKeyInitialized(): Boolean {
        return securePrefs.getString(ENCRYPTED_DB_KEY, null) != null
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }

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
        val masterKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val params = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, masterKey, params)
        return cipher.doFinal(Base64.decode(encryptedKey, Base64.NO_WRAP))
    }
}
