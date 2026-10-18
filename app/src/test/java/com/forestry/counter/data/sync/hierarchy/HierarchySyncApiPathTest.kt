package com.forestry.counter.data.sync.hierarchy

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Exercises the real Retrofit annotations with the origin-only base used by the app. */
class HierarchySyncApiPathTest {
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private class Fixture : AutoCloseable {
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse().setResponseCode(
                        if (request.path?.startsWith("/api/v1/sync/geosylva/") == true) 204 else 404,
                    )
            }
            start()
        }
        val api: HierarchySyncApiService = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(hierarchyJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(HierarchySyncApiService::class.java)

        fun request(): RecordedRequest = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        override fun close() = server.shutdown()
    }

    @Test fun publication_uses_the_versioned_path_and_preserves_frozen_body_bytes() = runBlocking {
        Fixture().use { fixture ->
            val frozen = " \n{\"fixture\":\"Chêne 🌳\"}\n "
            val response = fixture.api.publish(
                "Bearer test-only", FOREST_ID, frozen.toRequestBody("application/json".toMediaType()),
            )
            assertEquals(204, response.code())
            val request = fixture.request()
            assertEquals("PUT", request.method)
            assertEquals("/api/v1/sync/geosylva/dossiers/$FOREST_ID", request.path)
            assertEquals("Bearer test-only", request.getHeader("Authorization"))
            assertEquals(frozen, request.body.readUtf8())
        }
    }

    @Test fun remote_dossier_read_uses_the_same_versioned_origin_path() = runBlocking {
        Fixture().use { fixture ->
            assertEquals(204, fixture.api.get("Bearer test-only", FOREST_ID).code())
            val request = fixture.request()
            assertEquals("GET", request.method)
            assertEquals("/api/v1/sync/geosylva/dossiers/$FOREST_ID", request.path)
        }
    }

    @Test fun remote_list_keeps_pagination_on_the_versioned_path() = runBlocking {
        Fixture().use { fixture ->
            assertEquals(204, fixture.api.list("Bearer test-only", page = 2, size = 10).code())
            val request = fixture.request()
            assertEquals("GET", request.method)
            assertEquals("/api/v1/sync/geosylva/dossiers", request.requestUrl?.encodedPath)
            assertEquals("2", request.requestUrl?.queryParameter("page"))
            assertEquals("10", request.requestUrl?.queryParameter("size"))
        }
    }

    @Test fun lost_response_receipt_read_uses_the_versioned_path() = runBlocking {
        Fixture().use { fixture ->
            assertEquals(204, fixture.api.receipt("Bearer test-only", OPERATION_ID).code())
            val request = fixture.request()
            assertEquals("GET", request.method)
            assertEquals("/api/v1/sync/geosylva/dossier-operations/$OPERATION_ID", request.path)
            assertNotNull(request.requestUrl)
        }
    }

    private companion object {
        const val FOREST_ID = "11111111-1111-4111-8111-111111111111"
        const val OPERATION_ID = "22222222-2222-4222-8222-222222222222"
    }
}
