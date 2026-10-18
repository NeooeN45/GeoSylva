package com.forestry.counter.domain.usecase.pack

import java.io.File
import java.io.IOException
import java.net.URL
import java.security.MessageDigest
import java.util.Properties
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Tests réseau sur JVM : les reprises partent d'une coupure réellement reçue. */
class ResumableDownloaderTest {
    @Test
    fun `signature length contract rejects different response before activation`() {
        server.enqueue(fullResponse())
        val destination = File(tempFolder.root, "signed-length.part")
        val error = runCatching { downloadWithChecksum(url(), destination, sha256(payload), expectedBytes = payload.size.toLong() + 1) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertFalse(destination.exists())
    }

    @Test
    fun `redirects are never followed even with a matching checksum`() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/elsewhere")))
        val destination = File(tempFolder.root, "redirect.part")
        assertTrue(runCatching { downloadWithChecksum(url(), destination, sha256(payload)) }.isFailure)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `connection factory is honored without changing resume algorithm`() {
        server.enqueue(fullResponse())
        val destination = File(tempFolder.root, "factory.part")
        var calls = 0
        downloadWithChecksum(url(), destination, sha256(payload), expectedBytes = payload.size.toLong(),
            connectionFactory = { calls++; it.openConnection() as java.net.HttpURLConnection })
        assertEquals(1, calls)
        assertTrue(destination.readBytes().contentEquals(payload))
    }
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val etag = "\"etag-v1\""
    private val payload = "pack-geosylva-0123456789-".repeat(4_000).toByteArray()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun sha256(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    private fun url(path: String = "/pack"): URL = URL(server.url(path).toString())
    private fun metadataFile(destination: File): File = File(destination.parentFile, "${destination.name}.etag")
    private fun fullResponse(data: ByteArray = payload, responseEtag: String? = etag): MockResponse =
        MockResponse().setResponseCode(200).setBody(Buffer().write(data)).apply {
            responseEtag?.let { setHeader("ETag", it) }
        }

    private fun rangeResponse(offset: Int, data: ByteArray = payload, responseEtag: String = etag): MockResponse =
        MockResponse().setResponseCode(206)
            .setHeader("ETag", responseEtag)
            .setHeader("Content-Range", "bytes $offset-${data.lastIndex}/${data.size}")
            .setBody(Buffer().write(data.copyOfRange(offset, data.size)))

    private fun interruptedDownload(responseEtag: String? = etag): File {
        server.enqueue(fullResponse(responseEtag = responseEtag).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        val destination = File(tempFolder.root, "pack.part")
        val error = runCatching { downloadWithChecksum(url(), destination, sha256(payload)) }.exceptionOrNull()
        assertTrue("la première tentative doit échouer pendant le corps", error is IOException)
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        return destination
    }

    @Test
    fun `telechargement complet reussit et le checksum correspond`() {
        server.enqueue(fullResponse())
        val destination = File(tempFolder.root, "pack.part")
        var lastProgress = 0f
        downloadWithChecksum(url(), destination, sha256(payload)) { lastProgress = it }
        assertTrue(destination.readBytes().contentEquals(payload))
        assertEquals(1f, lastProgress)
    }

    @Test
    fun `premiere coupure enregistre metadata et reprend reellement sans fabriquer le fragment`() {
        val destination = interruptedDownload()
        val offset = destination.length().toInt()
        assertTrue("des octets doivent réellement rester après la première coupure", offset in 1 until payload.size)
        val properties = Properties().apply { metadataFile(destination).inputStream().use { load(it) } }
        assertEquals(etag, properties.getProperty("etag"))
        assertEquals(sha256(payload), properties.getProperty("sha256"))
        assertEquals(url().toExternalForm(), properties.getProperty("url"))
        assertEquals(payload.size.toString(), properties.getProperty("total"))

        server.enqueue(rangeResponse(offset))
        downloadWithChecksum(url(), destination, sha256(payload))
        assertTrue(destination.readBytes().contentEquals(payload))
        val request = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(request)
        assertEquals("bytes=$offset-", request?.getHeader("Range"))
        assertEquals(etag, request?.getHeader("If-Range"))
        assertEquals("identity", request?.getHeader("Accept-Encoding"))
    }

    @Test
    fun `reponse 200 apres demande de reprise remplace le fragment`() {
        val destination = interruptedDownload()
        val offset = destination.length()
        server.enqueue(fullResponse())
        downloadWithChecksum(url(), destination, sha256(payload))
        assertTrue(destination.readBytes().contentEquals(payload))
        assertEquals("bytes=$offset-", server.takeRequest(2, TimeUnit.SECONDS)?.getHeader("Range"))
    }

    @Test
    fun `ETag change dans un 206 impose une seule requete complete sans melange`() {
        val destination = interruptedDownload()
        val offset = destination.length().toInt()
        server.enqueue(rangeResponse(offset, responseEtag = "\"etag-v2\""))
        server.enqueue(fullResponse(responseEtag = "\"etag-v2\""))
        downloadWithChecksum(url(), destination, sha256(payload))
        assertTrue(destination.readBytes().contentEquals(payload))
        assertEquals("bytes=$offset-", server.takeRequest(2, TimeUnit.SECONDS)?.getHeader("Range"))
        assertNull(server.takeRequest(2, TimeUnit.SECONDS)?.getHeader("Range"))
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `Content Range decale ne peut pas etre assemble au fragment`() {
        val destination = interruptedDownload()
        val offset = destination.length().toInt()
        server.enqueue(rangeResponse(offset).setHeader("Content-Range", "bytes ${offset + 1}-${payload.lastIndex}/${payload.size}"))
        server.enqueue(fullResponse())
        downloadWithChecksum(url(), destination, sha256(payload))
        assertTrue(destination.readBytes().contentEquals(payload))
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertNull(server.takeRequest(2, TimeUnit.SECONDS)?.getHeader("Range"))
    }

    @Test
    fun `Content Range absent dans un 206 impose un redemarrage complet`() {
        val destination = interruptedDownload()
        val offset = destination.length().toInt()
        server.enqueue(rangeResponse(offset).removeHeader("Content-Range"))
        server.enqueue(fullResponse())
        downloadWithChecksum(url(), destination, sha256(payload))
        assertTrue(destination.readBytes().contentEquals(payload))
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `Content Length incompatible avec Content Range est rejete avant assemblage`() {
        val destination = interruptedDownload()
        val offset = destination.length().toInt()
        server.enqueue(rangeResponse(offset).setBody("court"))
        val error = runCatching { downloadWithChecksum(url(), destination, sha256(payload)) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error?.message?.contains("Content-Length") == true)
        assertFalse(destination.exists())
        assertFalse(metadataFile(destination).exists())
    }

    @Test
    fun `ETag faible ne laisse pas un fragment reutilisable apres coupure`() {
        val destination = interruptedDownload(responseEtag = "W/\"etag-v1\"")
        assertFalse(destination.exists())
        assertFalse(metadataFile(destination).exists())
        server.enqueue(fullResponse())
        downloadWithChecksum(url(), destination, sha256(payload))
        assertNull(server.takeRequest(2, TimeUnit.SECONDS)?.getHeader("Range"))
    }

    @Test
    fun `changement de checksum interdit de reprendre un fragment ancien`() {
        val destination = interruptedDownload()
        val changedPayload = "nouvelle-version".toByteArray()
        server.enqueue(fullResponse(changedPayload))
        downloadWithChecksum(url(), destination, sha256(changedPayload))
        assertTrue(destination.readBytes().contentEquals(changedPayload))
        assertNull(server.takeRequest(2, TimeUnit.SECONDS)?.getHeader("Range"))
    }

    @Test
    fun `changement URL interdit de reutiliser le fragment meme avec le meme checksum`() {
        val destination = interruptedDownload()
        server.enqueue(fullResponse())
        downloadWithChecksum(url("/autre-pack"), destination, sha256(payload))
        val request = server.takeRequest(2, TimeUnit.SECONDS)
        assertEquals("/autre-pack", request?.path)
        assertNull(request?.getHeader("Range"))
    }

    @Test
    fun `ancien sidecar ETag sans identite est ignore`() {
        val destination = File(tempFolder.root, "pack.part").apply { writeText("ancien-fragment") }
        metadataFile(destination).writeText(etag)
        server.enqueue(fullResponse())
        downloadWithChecksum(url(), destination, sha256(payload))
        assertTrue(destination.readBytes().contentEquals(payload))
        assertNull(server.takeRequest(2, TimeUnit.SECONDS)?.getHeader("Range"))
    }

    @Test
    fun `416 sur fragment incomplet provoque un redemarrage complet borne`() {
        val destination = interruptedDownload()
        server.enqueue(MockResponse().setResponseCode(416).setHeader("Content-Range", "bytes */${payload.size}"))
        server.enqueue(fullResponse())
        downloadWithChecksum(url(), destination, sha256(payload))
        assertTrue(destination.readBytes().contentEquals(payload))
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `416 valide un fichier deja complet uniquement apres verification SHA`() {
        server.enqueue(fullResponse())
        val destination = File(tempFolder.root, "pack.part")
        downloadWithChecksum(url(), destination, sha256(payload))
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        server.enqueue(MockResponse().setResponseCode(416).setHeader("Content-Range", "bytes */${payload.size}"))
        downloadWithChecksum(url(), destination, sha256(payload))
        assertTrue(destination.readBytes().contentEquals(payload))
        assertEquals("bytes=${payload.size}-", server.takeRequest(2, TimeUnit.SECONDS)?.getHeader("Range"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `416 ne valide pas un fichier complet corrompu`() {
        server.enqueue(fullResponse())
        val destination = File(tempFolder.root, "pack.part")
        downloadWithChecksum(url(), destination, sha256(payload))
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        val corrupted = payload.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() }
        destination.writeBytes(corrupted)
        server.enqueue(MockResponse().setResponseCode(416).setHeader("Content-Range", "bytes */${payload.size}"))
        server.enqueue(fullResponse())
        downloadWithChecksum(url(), destination, sha256(payload))
        assertTrue(destination.readBytes().contentEquals(payload))
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `annulation conserve le fragment coherent et sa reprise`() {
        server.enqueue(fullResponse())
        val destination = File(tempFolder.root, "pack.part")
        var cancel = false
        val error = runCatching {
            downloadWithChecksum(
                url(), destination, sha256(payload),
                checkCancellation = { if (cancel) throw CancellationException("annulé") }
            ) { cancel = true }
        }.exceptionOrNull()
        assertTrue(error is CancellationException)
        assertTrue(destination.length() in 1 until payload.size.toLong())
        assertTrue(metadataFile(destination).isFile)
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        val offset = destination.length().toInt()
        server.enqueue(rangeResponse(offset))
        downloadWithChecksum(url(), destination, sha256(payload))
        assertTrue(destination.readBytes().contentEquals(payload))
        assertEquals("bytes=$offset-", server.takeRequest(2, TimeUnit.SECONDS)?.getHeader("Range"))
    }

    @Test
    fun `annulation avant connexion ne genere aucune requete`() {
        val destination = File(tempFolder.root, "pack.part")
        val error = runCatching {
            downloadWithChecksum(url(), destination, sha256(payload), checkCancellation = { throw CancellationException() })
        }.exceptionOrNull()
        assertTrue(error is CancellationException)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `corruption a la reprise retente une seule fois puis nettoie`() {
        val destination = interruptedDownload()
        val offset = destination.length().toInt()
        val corrupted = payload.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        server.enqueue(rangeResponse(offset, corrupted))
        server.enqueue(fullResponse(corrupted))
        val error = runCatching { downloadWithChecksum(url(), destination, sha256(payload)) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error?.message?.contains("Checksum") == true)
        assertFalse(destination.exists())
        assertFalse(metadataFile(destination).exists())
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `checksum invalide rejette le fichier et ne laisse aucun fragment`() {
        server.enqueue(fullResponse())
        val destination = File(tempFolder.root, "pack.part")
        val error = runCatching { downloadWithChecksum(url(), destination, sha256("autre".toByteArray())) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertFalse(destination.exists())
        assertFalse(metadataFile(destination).exists())
    }

    @Test
    fun `fragment sans taille declaree se reprend avec Content Range`() {
        server.enqueue(fullResponse().setChunkedBody(Buffer().write(payload), 4_096).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        val destination = File(tempFolder.root, "pack.part")
        val error = runCatching { downloadWithChecksum(url(), destination, sha256(payload)) }.exceptionOrNull()
        assertTrue(error is IOException)
        assertTrue(destination.length() in 1 until payload.size.toLong())
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        val offset = destination.length().toInt()
        server.enqueue(rangeResponse(offset))
        downloadWithChecksum(url(), destination, sha256(payload))
        assertTrue(destination.readBytes().contentEquals(payload))
    }

    @Test
    fun `code HTTP erreur serveur est signale explicitement`() {
        server.enqueue(MockResponse().setResponseCode(500))
        val destination = File(tempFolder.root, "pack.part")
        val error = runCatching { downloadWithChecksum(url(), destination, "0".repeat(64)) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error?.message?.contains("HTTP 500") == true)
    }

    @Test
    fun `206 sans demande de reprise est rejete`() {
        server.enqueue(rangeResponse(0))
        val destination = File(tempFolder.root, "pack.part")
        val error = runCatching { downloadWithChecksum(url(), destination, sha256(payload)) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertFalse(destination.exists())
    }

    @Test
    fun `espace disque insuffisant est rejete explicitement avant ecriture`() {
        val error = runCatching { checkDiskSpace(1_000L, 10_000L, "/pack/region.pack") }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error?.message?.contains("Espace disque insuffisant") == true)
        assertTrue(error?.message?.contains("10000") == true)
    }

    @Test
    fun `espace disque suffisant ne leve pas erreur`() {
        checkDiskSpace(10_000L, 10_000L, "/pack/ok.pack")
        checkDiskSpace(Long.MAX_VALUE, 1L, "/pack/ok.pack")
    }

    @Test
    fun `schema non HTTPS est rejete explicitement`() {
        val error = runCatching { checkHttpsScheme(URL("http://example.com/pack/region.pack")) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message?.contains("HTTPS obligatoire") == true)
    }

    @Test
    fun `schema HTTPS est accepte`() {
        checkHttpsScheme(URL("https://example.com/pack/region.pack"))
        checkHttpsScheme(URL("HTTPS://example.com/pack/region.pack"))
    }

    @Test
    fun `pack depassant la taille maximale est rejete`() {
        val error = runCatching { checkPackSize(600L * 1024 * 1024, "/pack/region.pack") }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error?.message?.contains("trop volumineux") == true)
    }

    @Test
    fun `pack sous la taille maximale est accepte`() {
        checkPackSize(0L, "/pack/empty.pack")
        checkPackSize(512L * 1024 * 1024, "/pack/limit.pack")
    }
}
