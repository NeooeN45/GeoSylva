package com.forestry.counter.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object DatabaseEncryptionService {

    private const val KEY_ALIAS = "GeoSylva_DB_Master_Key"
    private const val SHARED_PREFS_NAME = "secure_db_prefs"
    private const val DB_KEY_PREF = "encrypted_db_key"

    fun isDatabaseEncrypted(context: Context): Boolean = runCatching {
        buildEncryptedPrefs(context).contains(DB_KEY_PREF)
    }.getOrDefault(false)

    fun rotateDatabaseKey(context: Context): Boolean = runCatching {
        buildEncryptedPrefs(context).edit().remove(DB_KEY_PREF).apply()
        true
    }.getOrDefault(false)

    private fun buildEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context, KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            SHARED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
