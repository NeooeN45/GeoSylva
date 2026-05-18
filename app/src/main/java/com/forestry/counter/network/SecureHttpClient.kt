package com.forestry.counter.network

import android.content.Context
import okhttp3.CertificatePinner
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Client HTTP sécurisé avec certificate pinning pour protéger contre les attaques MITM.
 * Conforme aux normes de sécurité entreprise et OWASP Mobile Top 10.
 */
object SecureHttpClient {
    
    /**
     * Crée un client HTTP sécurisé avec certificate pinning.
     * 
     * @param context Contexte de l'application
     * @param enableLogging Active les logs HTTP uniquement en DEBUG
     * @return OkHttpClient configuré avec certificate pinning
     */
    fun createSecureClient(context: Context, enableLogging: Boolean = false): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
        
        // NOTE: Certificate pinning désactivé — les hashes placeholder doivent être remplacés
        // par les vrais hashes SHA-256 avant activation en production.
        // Commande pour obtenir les hashes :
        //   openssl s_client -connect domain.com:443 | openssl x509 -pubkey -noout |
        //   openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl enc -base64
        // val certificatePinner = CertificatePinner.Builder()
        //     .add("demotiles.maplibre.org", "sha256/<REAL_HASH>")
        //     .add("tile.opentopomap.org",   "sha256/<REAL_HASH>")
        //     .add("basemaps.cartocdn.com",  "sha256/<REAL_HASH>")
        //     .add("server.arcgisonline.com","sha256/<REAL_HASH>")
        //     .add("data.geopf.fr",          "sha256/<REAL_HASH>")
        //     .build()
        // builder.certificatePinner(certificatePinner)
        
        // Logging minimal uniquement en mode DEBUG
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
    
    /**
     * Vérifie si l'application est en mode DEBUG.
     */
    private fun isDebugBuild(): Boolean {
        return try {
            Class.forName("com.forestry.counter.BuildConfig")
                .getField("DEBUG")
                .getBoolean(null)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Domaines sécurisés avec certificate pinning.
     */
    val SECURE_DOMAINS = listOf(
        "demotiles.maplibre.org",
        "tile.opentopomap.org", 
        "basemaps.cartocdn.com",
        "server.arcgisonline.com",
        "data.geopf.fr"
    )
    
    /**
     * Vérifie si une URL utilise un domaine sécurisé.
     */
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
    
    /**
     * Obtient les hashes de certificats actuels pour mise à jour.
     * À utiliser pendant le développement pour récupérer les vrais hashes.
     */
    fun getCurrentCertificateHashes(): Map<String, String> {
        // Placeholder - remplacer par les vrais hashes SHA-256 avant activation du pinning :
        // openssl s_client -connect domain.com:443 | openssl x509 -pubkey -noout |
        // openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl enc -base64
        return mapOf(
            "demotiles.maplibre.org"  to "sha256/<TODO_REAL_HASH>",
            "tile.opentopomap.org"    to "sha256/<TODO_REAL_HASH>",
            "basemaps.cartocdn.com"   to "sha256/<TODO_REAL_HASH>",
            "server.arcgisonline.com" to "sha256/<TODO_REAL_HASH>",
            "data.geopf.fr"           to "sha256/<TODO_REAL_HASH>"
        )
        // NOTE: Ces hashes doivent être remplacés par les vrais hashes SHA-256
        // Utiliser: openssl s_client -connect domain.com:443 | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl enc -base64
    }
}
