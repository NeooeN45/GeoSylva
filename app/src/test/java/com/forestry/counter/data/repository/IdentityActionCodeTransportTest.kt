package com.forestry.counter.data.repository

import com.forestry.counter.data.remote.identity.IdentityApiService
import com.forestry.counter.data.remote.identity.IdentitySessionCoordinator
import com.forestry.counter.data.remote.identity.IdentitySessionSnapshot
import com.forestry.counter.data.remote.identity.IdentitySessionStore
import com.forestry.counter.data.remote.identity.StoredIdentityTokens
import com.forestry.counter.domain.model.IdentityClientException
import com.forestry.counter.domain.model.IdentityError
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Vérifie les corps HTTP du vrai client Retrofit avec des fixtures JVM isolées. */
class IdentityActionCodeTransportTest {
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private class Fixture : AutoCloseable {
        val server = MockWebServer().apply { start() }
        val store = object : IdentitySessionStore(IdentitySessionCoordinator()) {
            var state = IdentitySessionSnapshot("test-only-initial", StoredIdentityTokens(token(), "test-only-refresh"))
            override fun readState() = state
            override fun writeState(state: IdentitySessionSnapshot): Boolean { this.state = state; return true }
        }
        val repository = IdentityRepositoryImpl(
            api = Retrofit.Builder().baseUrl(server.url("/"))
                .client(OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build())
                .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
                .build().create(IdentityApiService::class.java),
            apiBaseUrl = server.url("/").toString(), isGoogleClientConfigured = false, sessionStore = store,
        )
        override fun close() = server.shutdown()

        private fun token(): String {
            val payload = """{"sub":"account-test","exp":4102444800,"auth_provider":"local","roles":["user"]}"""
            return "test-header." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8)) + ".test-signature"
        }
    }

    @Test
    fun should_send_ascii_canonical_verification_code_in_the_actual_http_body() = runBlocking {
        Fixture().use { fixture ->
            fixture.server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"account_id":"account-test","email_verified":true,"providers":["local"],"roles":["user"]}"""
            ))
            assertTrue(fixture.repository.confirmEmailVerification("abcd efgh").isSuccess)
            val request = requireNotNull(fixture.server.takeRequest(1, TimeUnit.SECONDS))
            assertEquals("/api/v1/auth/email/verification/confirm", request.path)
            assertEquals("ABCDEFGH", Json.parseToJsonElement(request.body.readUtf8()).jsonObject.getValue("code").jsonPrimitive.content)
        }
    }

    @Test
    fun should_send_ascii_canonical_reset_code_in_the_actual_http_body() = runBlocking {
        Fixture().use { fixture ->
            fixture.server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"completed":true}"""))
            assertTrue(fixture.repository.confirmPasswordReset(
                "account@example.invalid", " abcd - efgh ", "test-only-new-password",
            ).isSuccess)
            val request = requireNotNull(fixture.server.takeRequest(1, TimeUnit.SECONDS))
            assertEquals("/api/v1/auth/password/reset/confirm", request.path)
            assertEquals("ABCDEFGH", Json.parseToJsonElement(request.body.readUtf8()).jsonObject.getValue("code").jsonPrimitive.content)
            assertNull(fixture.store.read())
        }
    }

    @Test
    fun should_reject_unicode_verification_code_before_any_http_request() = runBlocking {
        Fixture().use { fixture ->
            val result = fixture.repository.confirmEmailVerification("abcdéfgh")
            assertEquals(IdentityError.INVALID_INPUT, (result.exceptionOrNull() as? IdentityClientException)?.error)
            assertEquals(0, fixture.server.requestCount)
            assertNotNull(fixture.store.read())
        }
    }

    @Test
    fun should_reject_unicode_reset_code_before_any_http_request_or_local_logout() = runBlocking {
        Fixture().use { fixture ->
            val result = fixture.repository.confirmPasswordReset(
                "account@example.invalid", "ABCßDEFG", "test-only-new-password",
            )
            assertEquals(IdentityError.INVALID_INPUT, (result.exceptionOrNull() as? IdentityClientException)?.error)
            assertEquals(0, fixture.server.requestCount)
            assertNotNull(fixture.store.read())
        }
    }
}
