package com.forestry.counter.data.repository

import com.forestry.counter.data.remote.identity.IdentityApiService
import com.forestry.counter.data.remote.identity.IdentitySessionCoordinator
import com.forestry.counter.data.remote.identity.IdentitySessionSnapshot
import com.forestry.counter.data.remote.identity.IdentitySessionStore
import com.forestry.counter.data.remote.identity.StoredIdentityTokens
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class IdentityStoredSessionAdoptionTest {
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Test
    fun should_update_global_expired_session_after_transport_refresh_without_another_http_request() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            // Fixtures limitées au test : aucun jeton de recette ou de production.
            val store = object : IdentitySessionStore(IdentitySessionCoordinator()) {
                var stored = IdentitySessionSnapshot("initial", StoredIdentityTokens(token(1), "refresh-test-initial"))
                override fun readState() = stored
                override fun writeState(state: IdentitySessionSnapshot): Boolean { stored = state; return true }
            }
            val api = Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
                .build().create(IdentityApiService::class.java)
            val global = IdentityRepositoryImpl(api, server.url("/").toString(), false, store)
            assertTrue(requireNotNull(global.session.value).isExpired())
            val transport = IdentityRepositoryImpl(api, server.url("/").toString(), false, store)
            val newAccess = token(4_102_444_800L)
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"access_token":"$newAccess","refresh_token":"refresh-test-rotated","token_type":"bearer","expires_in":900}"""
            ))
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"account_id":"account-test","email_verified":false,"providers":["local"],"roles":["user"]}"""
            ))
            assertTrue(transport.refreshSession().isSuccess)
            assertTrue(requireNotNull(global.session.value).isExpired())
            global.reloadStoredSession()
            assertEquals(transport.session.value, global.session.value)
            assertFalse(requireNotNull(global.session.value).isExpired())
            assertEquals("/api/v1/auth/refresh", requireNotNull(server.takeRequest(1, TimeUnit.SECONDS)).path)
            assertEquals("/api/v1/auth/me", requireNotNull(server.takeRequest(1, TimeUnit.SECONDS)).path)
            assertEquals(2, server.requestCount)
        } finally { server.shutdown() }
    }

    private fun token(expires: Long): String {
        val payload = """{"sub":"account-test","exp":$expires,"auth_provider":"local","roles":["user"]}"""
        return "header." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8)) + ".test-signature"
    }
}
