package com.forestry.counter.network

import android.content.Context

/**
 * Service de validation des URL de tuiles cartographiques.
 */
class SecureTileService(private val context: Context) {

    /**
     * Vérifie qu'une URL de tuile utilise un domaine autorisé.
     */
    fun validateTileUrl(url: String): Boolean {
        return SecureHttpClient.isSecureDomain(url)
    }

    /**
     * Obtient les statistiques de sécurité pour le monitoring.
     * Le certificate pinning est activé en release (voir SecureHttpClient).
     */
    fun getSecurityStats(): SecurityStats {
        val isDebug = try {
            Class.forName("com.forestry.counter.BuildConfig")
                .getField("DEBUG")
                .getBoolean(null)
        } catch (e: Exception) {
            false
        }
        return SecurityStats(
            secureDomainsCount = SecureHttpClient.SECURE_DOMAINS.size,
            certificatePinningEnabled = !isDebug, // activé en release, désactivé en debug
            loggingEnabled = false
        )
    }
}

/**
 * Statistiques de sécurité pour le monitoring.
 */
data class SecurityStats(
    val secureDomainsCount: Int,
    val certificatePinningEnabled: Boolean,
    val loggingEnabled: Boolean
)
