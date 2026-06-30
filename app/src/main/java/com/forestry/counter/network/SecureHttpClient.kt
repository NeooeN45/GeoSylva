package com.forestry.counter.network

import android.content.Context
import okhttp3.CertificatePinner
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Client HTTP sécurisé pour protéger contre les attaques MITM.
 *
 * ## Certificate pinning
 * Le pinning est ACTIVÉ pour les domaines critiques (IGN, OpenTopoMap, CartoCDN, Esri).
 * Les hashes SHA-256 des clés publiques sont extraits via openssl/cryptography.
 *
 * Rotation : les hashes doivent être vérifiés avant expiration des certificats
 * (en général tous les 2 ans). En cas de rotation, ajouter le nouveau hash en backup
 * avant de retirer l'ancien.
 *
 * Commande d'extraction (une par domaine) :
 * ```
 * openssl s_client -connect <domain>:443 </dev/null \
 *   | openssl x509 -noout -pubkey \
 *   | openssl pkey -pubin -outform DER \
 *   | openssl dgst -sha256 -binary \
 *   | base64
 * ```
 */
object SecureHttpClient {

    /**
     * Crée un client HTTP avec configuration sécurisée (certificate pinning activé).
     *
     * @param context       Contexte de l'application
     * @param enableLogging Active les logs HTTP en mode DEBUG uniquement
     * @return OkHttpClient configuré avec certificate pinning
     */
    fun createSecureClient(context: Context, enableLogging: Boolean = false): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        // Certificate pinning — ACTIVÉ avec hashes SHA-256 extraits le 2026-06-29.
        // En debug, le pinning est désactivé pour permettre le dev avec mitmproxy.
        if (!isDebugBuild()) {
            val certificatePinner = CertificatePinner.Builder()
                .add("data.geopf.fr",           "sha256/TmC5ZipQAN+9APsZU4ute175dmH5SHa3D2LH4xf23f4=")
                .add("tile.opentopomap.org",    "sha256/EWMn6zmyhVAzYedTJIlzWQZWcpptvzbnaQguv9d5Lzk=")
                .add("basemaps.cartocdn.com",   "sha256/KO2Vmmrijw/nR0v8Hq9QVwZV0UGb4F0mLD2jyBHlbmQ=")
                .add("server.arcgisonline.com", "sha256/g9GFyx49oZdtQt7Gtx7eO7+csB+PD/O5w893a09Q3VY=")
                .build()
            builder.certificatePinner(certificatePinner)
        }

        if (enableLogging && isDebugBuild()) {
            builder.addInterceptor(Interceptor { chain ->
                val req = chain.request()
                android.util.Log.d("SecureHttpClient", "→ ${req.method} ${req.url.host}")
                val resp: Response = chain.proceed(req)
                android.util.Log.d("SecureHttpClient", "← ${resp.code}")
                resp
            })
        }

        return builder.build()
    }

    /** Domaines pour lesquels le pinning sera activé une fois les hashes renseignés. */
    val SECURE_DOMAINS = listOf(
        "demotiles.maplibre.org",
        "tile.opentopomap.org",
        "basemaps.cartocdn.com",
        "server.arcgisonline.com",
        "data.geopf.fr"
    )

    /** Retourne true si l'URL cible un domaine de la liste [SECURE_DOMAINS]. */
    fun isSecureDomain(url: String): Boolean {
        return try {
            val host = java.net.URL(url).host
            SECURE_DOMAINS.any { domain ->
                host.equals(domain, ignoreCase = true) || host.endsWith(".$domain", ignoreCase = true)
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun isDebugBuild(): Boolean {
        return try {
            Class.forName("com.forestry.counter.BuildConfig")
                .getField("DEBUG")
                .getBoolean(null)
        } catch (e: Exception) {
            false
        }
    }
}
