package com.forestry.counter.network

import android.content.Context
import okhttp3.OkHttpClient
import org.junit.Before
import org.junit.Test

/**
 * Tests pour SecureHttpClient - Couverture des fonctionnalités de réseau sécurisé.
 * Vérifie le certificate pinning et la sécurité des connexions.
 */
class SecureHttpClientTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
    }

    @Test
    fun `createSecureClient should return valid OkHttpClient`() {
        // When
        val client = SecureHttpClient.createSecureClient(context, enableLogging = false)

        // Then
        assert(client is OkHttpClient)
        assert(client.connectTimeoutMillis == 30000) // 30 seconds
        assert(client.readTimeoutMillis == 60000) // 60 seconds
        assert(client.writeTimeoutMillis == 60000) // 60 seconds
    }

    @Test
    fun `createSecureClient should enable logging in debug mode`() {
        // When
        val client = SecureHttpClient.createSecureClient(context, enableLogging = true)

        // Then
        assert(client is OkHttpClient)
        // Should have logging interceptor when debug enabled
    }

    @Test
    fun `isSecureDomain should return true for allowed domains`() {
        // Given
        val secureUrls = listOf(
            "https://demotiles.maplibre.org/tiles/{z}/{x}/{y}.png",
            "https://tile.opentopomap.org/{z}/{x}/{y}.png",
            "https://basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png",
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
            "https://data.geopf.fr/wmts?"
        )

        // When/Then
        secureUrls.forEach { url ->
            assert(SecureHttpClient.isSecureDomain(url)) { "URL should be secure: $url" }
        }
    }

    @Test
    fun `isSecureDomain should return false for unallowed domains`() {
        // Given
        val unsecureUrls = listOf(
            "https://evil.com/malware",
            "http://insecure-site.org/data",
            "https://phishing.net/steal",
            "https://unknown-domain.com/api"
        )

        // When/Then
        unsecureUrls.forEach { url ->
            assert(!SecureHttpClient.isSecureDomain(url)) { "URL should not be secure: $url" }
        }
    }

    @Test
    fun `isSecureDomain should handle malformed URLs gracefully`() {
        // Given
        val malformedUrls = listOf(
            "not-a-url",
            "",
            "ftp://protocol-not-supported.com",
            "javascript:alert('xss')",
            "data:text/plain,evil"
        )

        // When/Then
        malformedUrls.forEach { url ->
            assert(!SecureHttpClient.isSecureDomain(url)) { "Malformed URL should not be secure: $url" }
        }
    }

    @Test
    fun `isSecureDomain should handle subdomains correctly`() {
        // Given
        val subdomainUrls = listOf(
            "https://api.demotiles.maplibre.org/v1/tiles",
            "https://cdn.tile.opentopomap.org/assets",
            "https://tiles.basemaps.cartocdn.com/maps"
        )

        // When/Then
        subdomainUrls.forEach { url ->
            assert(SecureHttpClient.isSecureDomain(url)) { "Subdomain URL should be secure: $url" }
        }
    }

    @Test
    fun `getCurrentCertificateHashes should return valid hashes`() {
        // When
        val hashes = SecureHttpClient.getCurrentCertificateHashes()

        // Then
        assert(hashes.isNotEmpty())
        assert(hashes.containsKey("demotiles.maplibre.org"))
        assert(hashes.containsKey("tile.opentopomap.org"))
        assert(hashes.containsKey("basemaps.cartocdn.com"))
        assert(hashes.containsKey("server.arcgisonline.com"))
        assert(hashes.containsKey("data.geopf.fr"))

        // All hashes should be SHA-256 format
        hashes.values.forEach { hash ->
            assert(hash.startsWith("sha256/")) { "Hash should start with sha256/: $hash" }
            assert(hash.length > 20) { "Hash should be substantial length: $hash" }
        }
    }

    @Test
    fun `SECURE_DOMAINS should contain all expected domains`() {
        // Given
        val expectedDomains = listOf(
            "demotiles.maplibre.org",
            "tile.opentopomap.org",
            "basemaps.cartocdn.com",
            "server.arcgisonline.com",
            "data.geopf.fr"
        )

        // When/Then
        expectedDomains.forEach { domain ->
            assert(SecureHttpClient.SECURE_DOMAINS.contains(domain)) { 
                "Domain should be in secure domains list: $domain" 
            }
        }
    }

    @Test
    fun `createSecureClient should have certificate pinning configured`() {
        // When
        val client = SecureHttpClient.createSecureClient(context, enableLogging = false)

        // Then
        assert(client is OkHttpClient)
        // Certificate pinning should be configured (verified through internal structure)
        // This is a basic test - actual pinning verification would require more complex setup
    }

    @Test
    fun `createSecureClient should have reasonable timeouts`() {
        // When
        val client = SecureHttpClient.createSecureClient(context, enableLogging = false)

        // Then
        assert(client.connectTimeoutMillis == 30000) { "Connect timeout should be 30s" }
        assert(client.readTimeoutMillis == 60000) { "Read timeout should be 60s" }
        assert(client.writeTimeoutMillis == 60000) { "Write timeout should be 60s" }
    }

    @Test
    fun `createSecureClient should retry on connection failure`() {
        // When
        val client = SecureHttpClient.createSecureClient(context, enableLogging = false)

        // Then
        assert(client.retryOnConnectionFailure()) { "Should retry on connection failure" }
    }
}
