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
 * Le pinning est actuellement **DÉSACTIVÉ** — la sécurité TLS repose sur la validation
 * CA standard du système Android.
 *
 * Pour activer le pinning en production :
 * 1. Extraire les hashes SHA-256 des clés publiques avec la commande ci-dessous.
 * 2. Décommenter les lignes `CertificatePinner` dans [createSecureClient].
 * 3. Planifier la rotation des hashes avant expiration des certificats (en général tous les 2 ans).
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
     * Crée un client HTTP avec configuration sécurisée.
     *
     * @param context       Contexte de l'application
     * @param enableLogging Active les logs HTTP en mode DEBUG uniquement
     * @return OkHttpClient configuré (pinning désactivé tant que les hashes ne sont pas renseignés)
     */
    fun createSecureClient(context: Context, enableLogging: Boolean = false): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        // Certificate pinning — DÉSACTIVÉ.
        // Décommenter et renseigner les vrais hashes SHA-256 avant activation en production.
        // val certificatePinner = CertificatePinner.Builder()
        //     .add("demotiles.maplibre.org", "sha256/<HASH_A>", "sha256/<HASH_B>")  // backup pin
        //     .add("tile.opentopomap.org",   "sha256/<HASH_A>", "sha256/<HASH_B>")
        //     .add("basemaps.cartocdn.com",  "sha256/<HASH_A>", "sha256/<HASH_B>")
        //     .add("server.arcgisonline.com","sha256/<HASH_A>", "sha256/<HASH_B>")
        //     .add("data.geopf.fr",          "sha256/<HASH_A>", "sha256/<HASH_B>")
        //     .build()
        // builder.certificatePinner(certificatePinner)

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
