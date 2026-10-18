package com.forestry.counter.data.remote.identity

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

internal data class StoredIdentityTokens(
    val accessToken: String,
    val refreshToken: String,
)

/** Stockage dédié : aucun jeton d'identité ne transite par DataStore en clair. */
internal class EncryptedIdentitySessionStore(context: Context) : IdentitySessionStore(coordinator) {
    private val preferences: Result<SharedPreferences> = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    val isAvailable: Boolean
        get() = preferences.isSuccess

    override fun readState(): IdentitySessionSnapshot? {
        val prefs = preferences.getOrNull() ?: return null
        return runCatching {
            val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)?.takeIf(String::isNotBlank)
            val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)?.takeIf(String::isNotBlank)
            IdentitySessionSnapshot(
                revision = prefs.getString(KEY_REVISION, "") ?: "",
                tokens = if (accessToken != null && refreshToken != null) {
                    StoredIdentityTokens(accessToken, refreshToken)
                } else null,
            )
        }.getOrNull()
    }

    override fun writeState(state: IdentitySessionSnapshot): Boolean {
        val prefs = preferences.getOrNull() ?: return false
        return runCatching {
            prefs.edit()
                .putString(KEY_REVISION, state.revision)
                .putString(KEY_ACCESS_TOKEN, state.tokens?.accessToken)
                .putString(KEY_REFRESH_TOKEN, state.tokens?.refreshToken)
                .commit()
        }.getOrDefault(false)
    }

    private companion object {
        val coordinator = IdentitySessionCoordinator()
        const val FILE_NAME = "quintessences_identity_session"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_REVISION = "session_revision"
    }
}
