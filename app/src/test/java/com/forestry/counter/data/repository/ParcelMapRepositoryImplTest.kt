package com.forestry.counter.data.repository

import com.forestry.counter.domain.location.ParcelBounds
import com.forestry.counter.domain.location.ParcelCoordinate
import com.forestry.counter.domain.location.ParcelMapSource
import java.security.MessageDigest
import kotlin.math.abs
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ParcelMapRepositoryImplTest {
    private val clients = mutableListOf<OkHttpClient>()

    @After fun cleanup() {
        clients.forEach { it.dispatcher.executorService.shutdown(); it.connectionPool.evictAll() }
    }

    @Test fun `unaltered official WFS response parses four real cadastral identities and source areas`() {
        val bytes = javaClass.getResourceAsStream("/geography/cadastre-chize-2026-10-03.json")!!.use { it.readBytes() }
        assertEquals("01a1b2f4a1e31aeb03afadfb0ea48c886ce9d982dc339c5a26a9f4aec7768310", sha256(bytes))
        val result = ParcelMapRepositoryImpl.parseCadastre(bytes.toString(Charsets.UTF_8), 123L)
        assertEquals(4, result.parcels.size)
        assertEquals(0, result.rejected)
        assertFalse(result.truncated)
        val parcel = result.parcels.first { it.id == "793500000B0008" }
        assertEquals("0B 0008", parcel.label)
        assertEquals("79350", parcel.communeCode)
        assertEquals("Villiers-en-Bois", parcel.communeName)
        assertEquals(1_426_203.0, parcel.cadastralAreaSquareMetres!!, 0.0)
        assertTrue(parcel.geometry.areaSquareMetres in 1_000_000.0..2_000_000.0)
        assertTrue(abs(parcel.geometry.areaSquareMetres - parcel.cadastralAreaSquareMetres!!) > 1.0)
        assertEquals(ParcelMapSource.CADASTRE, parcel.source)
        assertEquals(123L, parcel.fetchedAt)
        assertTrue(parcel.geometry.bounds.west < 0.0)
        assertTrue(parcel.geometry.bounds.south > 46.0)
        assertTrue(parcel.geometrySha256.matches(Regex("[0-9a-f]{64}")))
        assertTrue(parcel.provenance.contains("ign-parcellaire-express-wfs-2026:793500000B0008"))
        assertTrue(parcel.provenance.contains("retrieved=123; geometry-sha256="))
        assertTrue(parcel.attribution.contains("Licence Ouverte 2.0"))
    }

    @Test fun `holes remain excluded and legal contenance is not substituted for geometry area`() {
        val geometry = """{"type":"Polygon","coordinates":[[[0,0],[4,0],[4,4],[0,4],[0,0]],[[1,1],[2,1],[2,2],[1,2],[1,1]]]}"""
        val parcel = ParcelMapRepositoryImpl.parseCadastre(collection(feature(geometry = geometry, area = "999")), 123L).parcels.single()
        assertEquals(999.0, parcel.cadastralAreaSquareMetres!!, 0.0)
        assertTrue(parcel.geometry.contains(ParcelCoordinate(3.0, 3.0)))
        assertFalse(parcel.geometry.contains(ParcelCoordinate(1.5, 1.5)))
        assertFalse(parcel.geometry.contains(ParcelCoordinate(1.0, 1.5)))
        assertTrue(parcel.geometry.areaSquareMetres > 999.0)
    }

    @Test fun `missing or unusable source contenance remains unknown`() {
        listOf(null, "null", "0", "-1", "1e999").forEach { area ->
            val result = ParcelMapRepositoryImpl.parseCadastre(collection(feature(area = area)), 123L)
            assertEquals(1, result.parcels.size)
            assertNull(result.parcels.single().cadastralAreaSquareMetres)
        }
    }

    @Test fun `invalid identity missing shape and projected coordinates rejected without bbox fallback`() {
        val invalid = listOf(
            feature(id = "bad-id"),
            feature().let { JsonObject(it.filterKeys { key -> key != "geometry" } + ("bbox" to Json.parseToJsonElement("[0,0,1,1]"))) },
            feature(geometry = """{"type":"Point","coordinates":[0,0]}"""),
            feature(geometry = """{"type":"Polygon","coordinates":[[[700000,6600000],[700001,6600000],[700001,6600001],[700000,6600000]]]}"""),
        )
        val result = ParcelMapRepositoryImpl.parseCadastre(collection(*invalid.toTypedArray()), 123L)
        assertTrue(result.parcels.isEmpty())
        assertEquals(4, result.rejected)
    }

    @Test fun `genuine empty collection stays distinct from absent or malformed features`() {
        val empty = ParcelMapRepositoryImpl.parseCadastre("""{"type":"FeatureCollection","features":[],"numberMatched":0}""", 123L)
        assertTrue(empty.parcels.isEmpty())
        assertEquals(0, empty.rejected)
        assertFalse(empty.truncated)
        listOf(
            """{"type":"FeatureCollection"}""",
            """{"type":"FeatureCollection","features":null}""",
            """{"type":"FeatureCollection","features":{}}""",
            """{"type":"FeatureCollection","features":"none"}""",
        ).forEach { rejected { ParcelMapRepositoryImpl.parseCadastre(it, 123L) } }
    }

    @Test fun `unknown malformed or projected CRS declarations fail closed`() {
        listOf(
            """{"type":"name","properties":{"name":"EPSG:2154"}}""",
            """{"type":"name","properties":{"name":"EPSG:2154-CRS84"}}""",
            """{"type":"name","properties":{"name":"untrusted:CRS:84"}}""",
            """{"type":"link","properties":{"name":"CRS:84"}}""",
            "{}", "\"CRS:84\"",
        ).forEach { crs ->
            rejected { ParcelMapRepositoryImpl.parseCadastre(collection(feature(), crs = crs), 123L) }
        }
    }

    @Test fun `bounded feature counts reveal truncation but overlimit input fails`() {
        val first = feature()
        val truncated = ParcelMapRepositoryImpl.parseCadastre(collection(first, matched = 12), 123L)
        assertTrue(truncated.truncated)
        assertEquals(1, truncated.parcels.size)
        val full = ParcelMapRepositoryImpl.parseCadastre(collection(*List(200) { first }.toTypedArray(), matched = 200), 123L)
        assertTrue(full.truncated)
        // Repeated source identity is a single selection, never 200 local parcel candidates.
        assertEquals(1, full.parcels.size)
        rejected { ParcelMapRepositoryImpl.parseCadastre(collection(*List(201) { first }.toTypedArray()), 123L) }
        rejected { ParcelMapRepositoryImpl.parseCadastre(" ".repeat(2_000_001), 123L) }
    }

    @Test fun `feature collection renderer retains source polygon and stable selection id`() {
        val parcel = ParcelMapRepositoryImpl.parseCadastre(collection(feature()), 123L).parcels.single()
        val client = OkHttpClient().also { clients += it }
        val json = ParcelMapRepositoryImpl(client).featureCollection(listOf(parcel))
        val root = Json.parseToJsonElement(json) as JsonObject
        val rendered = (root["features"] as JsonArray).single() as JsonObject
        assertEquals(JsonPrimitive(parcel.id), rendered["id"])
        assertEquals(Json.parseToJsonElement(parcel.geometry.toGeoJson()), rendered["geometry"])
        assertEquals(JsonPrimitive(parcel.id), (rendered["properties"] as JsonObject)["parcel_id"])
    }

    @Test fun `public cadastral query explicitly requests CRS84 with bounded viewport and no identity headers`() = runTest {
        var request: Request? = null
        val client = intercepted(collection(feature())) { request = it }
        val repository = ParcelMapRepositoryImpl(client)
        repository.loadParcels(ParcelBounds(-0.42, 46.12, -0.40, 46.14), ParcelMapSource.CADASTRE)
        val sent = request!!
        assertEquals("https", sent.url.scheme)
        assertEquals("data.geopf.fr", sent.url.host)
        assertEquals("CRS:84", sent.url.queryParameter("srsName"))
        assertEquals("200", sent.url.queryParameter("count"))
        assertEquals("-0.42,46.12,-0.4,46.14,CRS:84", sent.url.queryParameter("bbox"))
        assertNull(sent.header("Authorization"))
        assertNull(sent.header("Cookie"))
    }

    @Test fun `oversized viewport refuses provider request`() = runTest {
        var requests = 0
        val repository = ParcelMapRepositoryImpl(intercepted(collection(feature())) { requests++ })
        rejectedSuspend { repository.loadParcels(ParcelBounds(0.0, 46.0, 0.16, 46.1), ParcelMapSource.CADASTRE) }
        assertEquals(0, requests)
    }

    @Test fun `normalisation tolerates accents punctuation and case without inventing exact match`() {
        assertEquals("foret de chize", ParcelMapRepositoryImpl.normalize(" Forêt de CHIZÉ "))
        assertEquals("saint severin sur boutonne", ParcelMapRepositoryImpl.normalize("Saint-Séverin-sur-Boutonne"))
    }

    @Test fun `unaltered official ONF response retains ten composite identities and Lambert93 source areas`() {
        val bytes = onfFixtureBytes()
        assertEquals("9cf2e6320e59d260b8d0a12e7ca8ecc31452752e00dbb3e01cb5f3587997e01f", sha256(bytes))
        val result = ParcelMapRepositoryImpl.parseOnf(bytes.toString(Charsets.UTF_8), 456L)
        assertEquals(10, result.parcels.size)
        assertEquals(0, result.rejected)
        assertFalse(result.truncated)
        assertEquals(setOf("168", "169", "179", "180", "181", "182", "183", "184", "185", "186"), result.parcels.map { it.label }.toSet())
        assertEquals(result.parcels.size, result.parcels.map { it.id }.distinct().size)
        val parcel = result.parcels.single { it.id == "F09793Q:168" }
        assertEquals(ParcelMapSource.ONF, parcel.source)
        assertEquals(241_442.859375, requireNotNull(parcel.sourceAreaSquareMetres), 0.0)
        assertTrue(abs(parcel.geometry.areaSquareMetres - requireNotNull(parcel.sourceAreaSquareMetres)) > 1.0)
        assertTrue(parcel.geometry.contains(ParcelCoordinate(-0.3965089511604008, 46.132814829012005)))
        assertEquals(456L, parcel.fetchedAt)
        assertEquals("onf-public-forest-parcels-arcgis-2026", parcel.sourceDataset)
        assertEquals(sha256(parcel.geometry.toGeoJson().toByteArray()), parcel.geometrySha256)
        result.parcels.forEach {
            assertNull(it.cadastralAreaSquareMetres)
            assertNull(it.cadastralSection)
            assertNull(it.cadastralNumber)
            assertNull(it.communeCode)
            assertTrue(it.id.startsWith("F09793Q:"))
            assertTrue(it.attribution.contains("© ONF Paris, 2026"))
        }
    }

    @Test fun `ONF objects missing forest parcel or polygon are rejected without GPS or bbox fallback`() {
        val official = (Json.parseToJsonElement(onfFixtureBytes().toString(Charsets.UTF_8)) as JsonObject)
        val first = (official["features"] as JsonArray).first() as JsonObject
        val properties = first["properties"] as JsonObject
        val invalid = listOf(
            JsonObject(first + ("properties" to JsonObject(properties.filterKeys { it != "iidtn_frt" }))),
            JsonObject(first + ("properties" to JsonObject(properties.filterKeys { it != "ccod_prf" }))),
            JsonObject(first.filterKeys { it != "geometry" } + ("bbox" to Json.parseToJsonElement("[-0.402,46.130,-0.390,46.136]"))),
            JsonObject(first + ("geometry" to Json.parseToJsonElement("""{"type":"Point","coordinates":[-0.3965,46.1328]}"""))),
        )
        val result = ParcelMapRepositoryImpl.parseOnf(collection(*invalid.toTypedArray()), 456L)
        assertTrue(result.parcels.isEmpty())
        assertEquals(4, result.rejected)
    }

    @Test fun `ONF GeoJSON refuses declared projected malformed or ambiguous CRS`() {
        val official = Json.parseToJsonElement(onfFixtureBytes().toString(Charsets.UTF_8)) as JsonObject
        listOf(
            """{"type":"name","properties":{"name":"EPSG:2154"}}""",
            """{"type":"name","properties":{"name":"EPSG:4326","axis":"latitude-first"}}""",
            "{}", "null", "\"CRS84\"",
        ).forEach { crs ->
            val input = JsonObject(official + ("crs" to Json.parseToJsonElement(crs))).toString()
            rejected { ParcelMapRepositoryImpl.parseOnf(input, 456L) }
        }
    }

    @Test fun `public ONF query sends bounded ArcGIS envelope WGS84 output and no account headers`() = runTest {
        val requests = mutableListOf<Request>()
        val client = intercepted(onfFixtureBytes().toString(Charsets.UTF_8)) { requests += it }
        val repository = ParcelMapRepositoryImpl(client)
        val result = repository.loadParcels(ParcelBounds(-0.407, 46.123, -0.399, 46.130), ParcelMapSource.ONF)
        assertEquals(10, result.parcels.size)
        val request = requests.single()
        assertEquals("https", request.url.scheme)
        assertEquals("services1.arcgis.com", request.url.host)
        assertTrue(request.url.encodedPath.endsWith("/FeatureServer/11/query"))
        assertEquals("-0.407,46.123,-0.399,46.13", request.url.queryParameter("geometry"))
        assertEquals("esriGeometryEnvelope", request.url.queryParameter("geometryType"))
        assertEquals("4326", request.url.queryParameter("inSR"))
        assertEquals("4326", request.url.queryParameter("outSR"))
        assertEquals("200", request.url.queryParameter("resultRecordCount"))
        assertEquals("geojson", request.url.queryParameter("f"))
        assertNull(request.header("Authorization"))
        assertNull(request.header("Cookie"))
        rejectedSuspend { repository.loadParcels(ParcelBounds(0.0, 46.0, 0.16, 46.1), ParcelMapSource.ONF) }
        assertEquals(1, requests.size)
    }

    private fun onfFixtureBytes(): ByteArray = requireNotNull(javaClass.getResourceAsStream("/geography/onf-parcels-chize-2026-10-03.json")).use { it.readBytes() }

    private fun intercepted(body: String, capture: (Request) -> Unit) = OkHttpClient.Builder().addInterceptor { chain ->
        capture(chain.request())
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("TEST response")
            .body(body.toResponseBody()).build()
    }.build().also { clients += it }

    private fun feature(id: String = "TEST00000001", geometry: String = SIMPLE_GEOMETRY, area: String? = null): JsonObject {
        val properties = mutableMapOf<String, kotlinx.serialization.json.JsonElement>("idu" to JsonPrimitive(id), "section" to JsonPrimitive("TEST"), "numero" to JsonPrimitive("0001"))
        area?.let { properties["contenance"] = Json.parseToJsonElement(it) }
        return JsonObject(mapOf("type" to JsonPrimitive("Feature"), "properties" to JsonObject(properties), "geometry" to Json.parseToJsonElement(geometry)))
    }

    private fun collection(vararg features: JsonObject, crs: String? = null, matched: Int = features.size): String {
        val fields = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
            "type" to JsonPrimitive("FeatureCollection"), "features" to JsonArray(features.toList()), "numberMatched" to JsonPrimitive(matched),
        )
        crs?.let { fields["crs"] = Json.parseToJsonElement(it) }
        return JsonObject(fields).toString()
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun rejected(block: () -> Unit) { try { block(); fail("Expected invalid input to be rejected") } catch (_: IllegalArgumentException) { } }
    private suspend fun rejectedSuspend(block: suspend () -> Unit) { try { block(); fail("Expected invalid input to be rejected") } catch (_: IllegalArgumentException) { } }

    private companion object {
        const val SIMPLE_GEOMETRY = """{"type":"Polygon","coordinates":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]}"""
    }
}
