package com.forestry.counter.data.packs

import android.content.Context
import com.forestry.counter.BuildConfig
import com.forestry.counter.data.remote.identity.EncryptedIdentitySessionStore
import com.forestry.counter.data.repository.IdentityRepositoryImpl
import com.forestry.counter.data.remote.identity.IdentityApiService
import com.forestry.counter.domain.model.pack.GeoPackDescriptor
import com.forestry.counter.domain.model.pack.SignedPackMetadata
import com.forestry.counter.domain.usecase.pack.downloadWithChecksum
import com.forestry.counter.network.SecureHttpClient
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlin.coroutines.coroutineContext

internal class PackCatalogException(val issue: PackCatalogIssue, message: String, cause: Throwable? = null) : IOException(message, cause)
private class AuthorizationRequired : IOException("Connexion GSIE requise pour télécharger les packs")

/** Catalogue signé sur disque, distinct du registre des installations. */
internal class SignedPackCatalogCache(private val file: File) {
    private val json = Json { ignoreUnknownKeys = false }
    @Serializable private data class Cache(val formatVersion: Int = 1, val items: List<SignedPackMetadata>)
    fun read(): List<SignedPackMetadata> {
        if (!file.exists()) return emptyList()
        require(file.length() in 1..MAX_CACHE_BYTES) { "Cache catalogue hors limites" }
        val bytes = file.readBytes()
        com.forestry.counter.capsule.DetachedManifestVerifier.requireBoundedJsonDepth(bytes)
        com.forestry.counter.capsule.json.StrictJsonParser.parse(bytes)
        val value = json.decodeFromString<Cache>(bytes.toString(Charsets.UTF_8))
        require(value.formatVersion == 1 && value.items.size <= MAX_CATALOG_ITEMS)
        return value.items
    }
    fun write(items: List<SignedPackMetadata>) {
        require(items.size <= MAX_CATALOG_ITEMS)
        val bytes = json.encodeToString(Cache.serializer(), Cache(items = items)).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_CACHE_BYTES)
        if (file.parentFile?.isDirectory != true && file.parentFile?.mkdirs() != true) throw IOException("Stockage catalogue inaccessible")
        val temporary = File(file.parentFile, "${file.name}.tmp")
        try {
            FileOutputStream(temporary).use { it.write(bytes); it.fd.sync() }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
    }
    companion object { private const val MAX_CACHE_BYTES = 4L * 1024 * 1024; internal const val MAX_CATALOG_ITEMS = 1000 }
}

internal class GsiePackRepository(
    private val context: Context,
    private val contract: AdministrativePackContract,
    private val onStoredSessionUpdated: () -> Unit = {}
) {
    private val sessionStore = EncryptedIdentitySessionStore(context)
    private val refreshMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = false }
    private val baseUrl = BuildConfig.GSIE_API_BASE_URL.trim().toHttpUrlOrNull()?.takeIf { url ->
        url.encodedPath == "/" && url.query == null && url.fragment == null &&
            (SecureHttpClient.isSafeRemoteHttpsUrl(url.toString()) || BuildConfig.DEBUG && SecureHttpClient.isSafeLocalDebugUrl(url.toString()))
    }
    private val client: OkHttpClient? = baseUrl?.let { base ->
        SecureHttpClient.createSecureClient(context, allowLocalDebug = BuildConfig.DEBUG && SecureHttpClient.isSafeLocalDebugUrl(base.toString()))
            .newBuilder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    }
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private fun refreshRepository(): IdentityRepositoryImpl {
        val identityJson = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val base = baseUrl ?: throw PackCatalogException(PackCatalogIssue.NOT_CONFIGURED, "Adresse GSIE absente")
        val transport = client ?: throw PackCatalogException(PackCatalogIssue.NOT_CONFIGURED, "Transport GSIE absent")
        val service = Retrofit.Builder().baseUrl(base).client(transport)
            .addConverterFactory(identityJson.asConverterFactory("application/json".toMediaType()))
            .build().create(IdentityApiService::class.java)
        return IdentityRepositoryImpl(service, base.toString(), BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank(), sessionStore)
    }

    fun artifactUrl(manifest: AdministrativeManifest): String? = baseUrl?.resolve(manifest.downloadPath)?.also(::requireSameOrigin)?.toString()

    suspend fun fetchCatalog(): List<SignedPackMetadata> = authenticated { token ->
        val base = baseUrl ?: throw PackCatalogException(PackCatalogIssue.NOT_CONFIGURED, "Adresse du serveur GSIE non configurée")
        if (!contract.isConfigured) throw PackCatalogException(PackCatalogIssue.NOT_CONFIGURED, "Clé publique de distribution GSIE non configurée")
        val result = mutableListOf<SignedPackMetadata>()
        var page = 1
        while (true) {
            coroutineContext.ensureActive()
            val endpoint = requireNotNull(base.resolve("/api/v1/packs")) { "Adresse catalogue GSIE invalide" }
            val url = endpoint.newBuilder().addQueryParameter("page", page.toString()).addQueryParameter("limit", "20").build()
            val bytes = withContext(Dispatchers.IO) {
                execute(url, token).use { response ->
                    validateStatus(response)
                    val body = response.body ?: throw PackCatalogException(PackCatalogIssue.INVALID_RESPONSE, "Réponse catalogue vide")
                    body.byteStream().use { input -> readBounded(input, 512 * 1024) }
                }
            }
            val catalog = try {
                com.forestry.counter.capsule.DetachedManifestVerifier.requireBoundedJsonDepth(bytes)
                com.forestry.counter.capsule.json.StrictJsonParser.parse(bytes)
                json.decodeFromString<CatalogDto>(bytes.toString(Charsets.UTF_8))
            } catch (error: Exception) {
                throw PackCatalogException(PackCatalogIssue.INVALID_RESPONSE, "Catalogue GSIE invalide", error)
            }
            if (catalog.page != page || catalog.limit != 20 || catalog.items.size > 20 || catalog.hasMore && catalog.items.size != 20) {
                throw PackCatalogException(PackCatalogIssue.INVALID_RESPONSE, "Pagination du catalogue incohérente")
            }
            for (signed in catalog.items) {
                contract.verify(signed.metadata())
                result += signed.metadata()
            }
            if (result.size > SignedPackCatalogCache.MAX_CATALOG_ITEMS) throw PackCatalogException(PackCatalogIssue.INVALID_RESPONSE, "Catalogue trop volumineux")
            if (!catalog.hasMore) break
            page++
            if (page > 50) throw PackCatalogException(PackCatalogIssue.INVALID_RESPONSE, "Pagination hors limites")
        }
        val manifests = result.map(contract::verify)
        require(manifests.map { it.packId }.distinct().size == manifests.size && manifests.map { it.territoryId }.distinct().size == manifests.size) { "Identités catalogue dupliquées" }
        result
    }

    suspend fun download(descriptor: GeoPackDescriptor, destination: File, onProgress: (Float) -> Unit) = authenticated { token ->
        val manifest = contract.verify(descriptor.signedManifest ?: error("Manifeste signé absent"))
        val url = artifactUrl(manifest) ?: throw PackCatalogException(PackCatalogIssue.NOT_CONFIGURED, "Adresse du serveur GSIE non configurée")
        withContext(Dispatchers.IO) {
            val downloadContext = coroutineContext
            downloadWithChecksum(URL(url), destination, manifest.sha256,
                checkCancellation = { downloadContext.ensureActive() }, expectedBytes = manifest.archiveBytes,
                connectionFactory = { target -> AuthenticatedConnection(target, token) }, onProgress = onProgress)
        }
    }

    private suspend fun <T> authenticated(block: suspend (String) -> T): T {
        if (client == null || !contract.isConfigured) throw PackCatalogException(PackCatalogIssue.NOT_CONFIGURED, "Distribution GSIE non configurée")
        val token = sessionStore.read()?.accessToken ?: throw PackCatalogException(PackCatalogIssue.AUTH_REQUIRED, "Connectez-vous à GSIE pour télécharger les packs")
        try { return block(token) } catch (_: AuthorizationRequired) {
            refreshMutex.withLock {
                if (sessionStore.read()?.accessToken == token) {
                    refreshRepository().refreshSession().getOrElse {
                        throw PackCatalogException(PackCatalogIssue.AUTH_REQUIRED, "Session GSIE expirée ; reconnectez-vous", it)
                    }
                }
            }
            val refreshed = sessionStore.read()?.accessToken ?: throw PackCatalogException(PackCatalogIssue.AUTH_REQUIRED, "Session GSIE absente")
            // Le transport reste durci ; seul l'état local du compte global est
            // remis à jour, sans second renouvellement ni exposition des jetons.
            onStoredSessionUpdated()
            try { return block(refreshed) } catch (error: AuthorizationRequired) {
                throw PackCatalogException(PackCatalogIssue.AUTH_REQUIRED, "Session GSIE refusée ; reconnectez-vous", error)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
    }

    private fun requireSameOrigin(url: HttpUrl) {
        val base = baseUrl ?: error("Adresse GSIE absente")
        require(url.scheme == base.scheme && url.host == base.host && url.port == base.port && url.username.isEmpty() && url.password.isEmpty()) {
            "Destination de téléchargement extérieure à GSIE refusée"
        }
    }
    private fun execute(url: HttpUrl, token: String, headers: Map<String, List<String>> = emptyMap()): Response {
        requireSameOrigin(url)
        val request = Request.Builder().url(url).get()
        for ((name, values) in headers) if (!name.equals("Authorization", true)) values.forEach { request.addHeader(name, it) }
        request.header("Authorization", "Bearer $token")
        val transport = client ?: throw PackCatalogException(PackCatalogIssue.NOT_CONFIGURED, "Transport GSIE absent")
        val response = transport.newCall(request.build()).execute()
        if (response.code == 401) { response.close(); throw AuthorizationRequired() }
        return response
    }
    private fun validateStatus(response: Response) {
        if (response.code !in 200..299) throw PackCatalogException(PackCatalogIssue.SERVER, "Serveur GSIE : HTTP ${response.code}")
    }

    /** Adaptateur conservant le moteur de reprise testé, sur le client TLS durci. */
    private inner class AuthenticatedConnection(url: URL, private val token: String) : HttpURLConnection(url) {
        private var response: Response? = null
        private fun response(): Response = response ?: execute(url.toString().toHttpUrlOrNull() ?: error("URL invalide"), token, requestProperties).also { response = it; connected = true }
        override fun connect() { response() }
        override fun getResponseCode(): Int = response().code
        override fun getHeaderField(name: String?): String? = name?.let { response().header(it) }
        override fun getInputStream(): InputStream = response().body?.byteStream() ?: throw IOException("Artefact GSIE vide")
        override fun disconnect() { response?.close(); response = null }
        override fun usingProxy(): Boolean = false
    }

    private fun readBounded(input: InputStream, maximum: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return output.toByteArray()
            if (output.size() + count > maximum) throw PackCatalogException(PackCatalogIssue.INVALID_RESPONSE, "Réponse catalogue hors limites")
            output.write(buffer, 0, count)
        }
    }
}
