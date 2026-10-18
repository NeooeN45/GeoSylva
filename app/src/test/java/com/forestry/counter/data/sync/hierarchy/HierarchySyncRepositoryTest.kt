package com.forestry.counter.data.sync.hierarchy

import android.content.Context
import com.forestry.counter.data.local.dao.HierarchySyncDao
import com.forestry.counter.data.local.entity.*
import com.forestry.counter.data.mapper.toEntity
import com.forestry.counter.data.remote.identity.*
import com.forestry.counter.data.repository.IdentityRepositoryImpl
import com.forestry.counter.domain.model.Foret
import io.mockk.*
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

/** Directed identity/HTTP races on synthetic fixtures; transaction/reopen is qualified separately on Room. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HierarchySyncRepositoryTest {
    @Test fun opening_selection_or_confirming_nothing_never_activates_or_uploads() = runTest {
        val f = Fixture()
        val selection = f.repository.loadSelection().getOrThrow()
        assertEquals(FOREST, selection.forests.single().forestId)
        assertEquals(0, f.repository.activateSelected(selection, emptySet()).getOrThrow())
        assertTrue(f.associations.isEmpty())
        assertTrue(f.operations.isEmpty())
        assertEquals(0, f.repository.captureActivated())
        assertEquals(0, f.repository.processPending().synchronized)
        coVerify(exactly = 0) { f.api.publish(any(), any(), any()) }
    }

    @Test fun explicit_activation_and_repeat_confirmation_preserve_the_initial_frozen_request() = runTest {
        val f = Fixture()
        val selection = f.repository.loadSelection().getOrThrow()
        assertEquals(1, f.repository.activateSelected(selection, setOf(FOREST)).getOrThrow())
        val original = f.operations.values.single()
        assertEquals("A", original.accountId)
        assertEquals(ORIGIN, original.apiOrigin)
        assertEquals(hierarchySha256(original.requestJson), original.requestSha256)
        assertNull(original.baseVersion)
        assertEquals(1, original.expectedServerVersion)
        assertEquals(0, f.repository.activateSelected(selection, setOf(FOREST)).getOrThrow())
        assertEquals(listOf(original), f.operations.values.toList())
    }

    @Test fun foreign_parcel_association_redacts_selection_and_refuses_a_late_confirmation() = runTest {
        val f = Fixture()
        val selection = f.repository.loadSelection().getOrThrow()
        f.parcelAccounts = listOf("B")
        assertTrue(f.repository.activateSelected(selection, setOf(FOREST)).isFailure)
        val candidate = f.repository.loadSelection().getOrThrow().forests.single()
        assertTrue(candidate.blockedByOtherAccount)
        assertEquals("", candidate.name)
        assertTrue(f.operations.isEmpty())
        assertTrue(f.associations.isEmpty())
    }

    @Test fun a_selection_from_before_logout_and_relogin_a_cannot_activate_any_forest() = runTest {
        val f = Fixture()
        val selection = f.repository.loadSelection().getOrThrow()
        f.logout()
        f.login("A", "original")
        assertTrue(f.repository.activateSelected(selection, setOf(FOREST)).isFailure)
        assertTrue(f.associations.isEmpty())
        assertTrue(f.operations.isEmpty())
    }

    @Test fun retry_buttons_bound_to_the_old_selection_never_adopt_the_next_account() = runTest {
        val f = Fixture()
        f.activate()
        val selection = f.repository.loadSelection().getOrThrow()
        val original = f.operations.values.single()
        f.login("B")
        assertNotNull(runCatching { f.repository.captureActivated(selection.sessionGuard) }.exceptionOrNull())
        assertTrue(f.repository.processPending(selection.sessionGuard).shouldRetry)
        assertEquals(listOf(original), f.operations.values.toList())
        assertTrue(f.requests.isEmpty())
    }

    @Test fun account_change_during_transactional_capture_rolls_back_consent_and_request() = runTest {
        val f = Fixture()
        val selection = f.repository.loadSelection().getOrThrow()
        val trees = CompletableDeferred<List<TigeEntity>>()
        coEvery { f.dao.trees(FOREST) } coAnswers { trees.await() }
        val pending = async { f.repository.activateSelected(selection, setOf(FOREST)) }
        runCurrent()
        f.login("B")
        trees.complete(emptyList())
        assertTrue(pending.await().isFailure)
        assertTrue(f.associations.isEmpty())
        assertTrue(f.operations.isEmpty())
        assertEquals(0, f.scheduled)
    }

    @Test fun committed_changes_are_caught_up_without_replacing_the_previous_request() = runTest {
        val f = Fixture()
        f.activate()
        val original = f.operations.values.single()
        f.forest = f.forest.copy(nom = "Updated local forest", updatedAt = 300L)
        assertEquals(1, f.repository.captureActivated())
        val successor = f.operations.values.last()
        assertEquals(original, f.operations[original.operationId])
        assertEquals(original.operationId, successor.predecessorOperationId)
        assertEquals(1, successor.baseVersion)
        assertEquals(2, successor.expectedServerVersion)
        assertNotEquals(original.operationId, successor.operationId)
        assertEquals(0, f.repository.captureActivated())
        assertEquals(2, f.operations.size)
    }

    @Test fun delivery_replays_the_stored_wire_json_even_if_business_data_changed_since_capture() = runTest {
        val f = Fixture()
        f.activate()
        val original = f.operations.values.single()
        f.forest = f.forest.copy(nom = "New unsent business name", updatedAt = 999L)
        assertEquals(1, f.repository.processPending().synchronized)
        assertEquals(listOf(original.requestJson), f.requests)
        assertEquals(original.requestSha256, f.operations[original.operationId]?.requestSha256)
        assertEquals(1, f.associations[FOREST]?.serverVersion)
        assertEquals("SYNCED", f.operations[original.operationId]?.state)
    }

    @Test fun a_foreign_parcel_link_added_before_delivery_prevents_any_transport() = runTest {
        val f = Fixture()
        f.activate()
        f.parcelAccounts = listOf("B")
        assertEquals(0, f.repository.processPending().synchronized)
        assertTrue(f.requests.isEmpty())
        assertEquals(Long.MAX_VALUE, f.operations.values.single().nextAttemptAt)
    }

    @Test fun delayed_ack_after_logout_is_not_adopted_and_the_original_request_remains_retryable() = runTest {
        val f = Fixture()
        f.activate()
        val original = f.operations.values.single()
        val response = CompletableDeferred<Response<HierarchyReceiptDto>>()
        coEvery { f.api.publish(any(), any(), any()) } coAnswers { response.await() }
        val pending = async { f.repository.processPending() }
        runCurrent()
        f.logout()
        response.complete(Response.success(ack(original)))
        assertTrue(pending.await().shouldRetry)
        val retained = requireNotNull(f.operations[original.operationId])
        assertEquals("A", retained.accountId)
        assertEquals("ERROR", retained.state)
        assertEquals(original.requestJson, retained.requestJson)
        assertNull(retained.ackVersion)
        assertNull(f.associations[FOREST]?.serverVersion)
    }

    @Test fun refresh_uses_the_verified_child_session_and_replays_exactly_the_same_request() = runTest {
        val f = Fixture()
        f.activate()
        var count = 0
        coEvery { f.api.publish(any(), any(), any()) } coAnswers {
            f.headers += firstArg<String>()
            val buffer = Buffer(); thirdArg<okhttp3.RequestBody>().writeTo(buffer)
            f.requests += buffer.readUtf8()
            if (count++ == 0) httpError(401) else Response.success(ack(f.operations.values.single()))
        }
        assertEquals(1, f.repository.processPending().synchronized)
        assertEquals(listOf("Bearer ${token("A", "original")}", "Bearer ${token("A", "renewed")}"), f.headers)
        assertEquals(2, f.requests.size)
        assertEquals(f.requests[0], f.requests[1])
    }

    @Test fun a_refresh_response_for_a_cannot_be_adopted_after_b_logs_in() = runTest {
        val f = Fixture()
        f.activate()
        val refreshed = CompletableDeferred<TokenResponseDto>()
        coEvery { f.api.publish(any(), any(), any()) } returns httpError(401)
        coEvery { f.identityApi.refresh(any()) } coAnswers { refreshed.await() }
        val pending = async { f.repository.processPending() }
        runCurrent()
        f.login("B")
        refreshed.complete(TokenResponseDto(accessToken = token("A", "late"), refreshToken = "fixture-late", tokenType = "bearer", expiresIn = 900))
        assertTrue(pending.await().shouldRetry)
        coVerify(exactly = 1) { f.api.publish(any(), any(), any()) }
        assertEquals("A", f.operations.values.single().accountId)
        assertEquals("ERROR", f.operations.values.single().state)
    }

    @Test fun a_lost_response_is_recovered_from_the_server_receipt_without_republishing() = runTest {
        val f = Fixture()
        f.activate()
        val original = f.operations.values.single()
        f.operations[original.operationId] = original.copy(state = "ERROR", retryCount = 1, nextAttemptAt = 0L)
        coEvery { f.api.receipt(any(), original.operationId) } returns Response.success(ack(original))
        assertEquals(1, f.repository.processPending().synchronized)
        coVerify(exactly = 1) { f.api.receipt(any(), original.operationId) }
        coVerify(exactly = 0) { f.api.publish(any(), any(), any()) }
    }

    @Test fun receipt_with_an_old_version_does_not_mark_the_snapshot_synchronized() = runTest {
        val f = Fixture()
        f.activate()
        val original = f.operations.values.single()
        coEvery { f.api.publish(any(), any(), any()) } returns Response.success(ack(original).copy(serverVersion = 0))
        assertEquals(0, f.repository.processPending().synchronized)
        assertEquals("ERROR", f.operations[original.operationId]?.state)
        assertEquals(original.requestJson, f.operations[original.operationId]?.requestJson)
        assertNull(f.associations[FOREST]?.serverVersion)
    }

    @Test fun an_ack_with_the_same_identity_but_a_wrong_or_missing_raw_body_hash_is_refused() = runTest {
        listOf("b".repeat(64), null).forEach { wrongHash ->
            val f = Fixture()
            f.activate()
            val original = f.operations.values.single()
            coEvery { f.api.publish(any(), any(), any()) } returns Response.success(ack(original).copy(requestSha256 = wrongHash))
            assertEquals(0, f.repository.processPending().synchronized)
            assertEquals("ERROR", f.operations[original.operationId]?.state)
            assertEquals(original.requestJson, f.operations[original.operationId]?.requestJson)
            assertNull(f.associations[FOREST]?.serverVersion)
        }
    }

    @Test fun a_conflict_retains_the_candidate_receipt_and_blocks_its_unchanged_successor() = runTest {
        val f = Fixture()
        f.activate()
        val original = f.operations.values.single()
        f.forest = f.forest.copy(nom = "Successor", updatedAt = 300L)
        f.repository.captureActivated()
        val successor = f.operations.values.last()
        val receipt = ack(original).copy(status = "conflict", serverVersion = null, reason = "VERSION_CONFLICT")
        coEvery { f.api.publish(any(), any(), any()) } returns Response.error(
            409, hierarchyJson.encodeToString(receipt).toResponseBody("application/json".toMediaType()),
        )
        assertEquals(1, f.repository.processPending().conflicts)
        assertEquals("CONFLICT", f.operations[original.operationId]?.state)
        assertNotNull(f.operations[original.operationId]?.receiptJson)
        assertEquals("BLOCKED_PREDECESSOR", f.operations[successor.operationId]?.state)
        assertEquals(successor.requestJson, f.operations[successor.operationId]?.requestJson)
        assertEquals(successor.baseVersion, f.operations[successor.operationId]?.baseVersion)
    }

    @Test fun remote_recovery_is_read_only_and_republication_rejects_an_outdated_local_preview() = runTest {
        val f = Fixture()
        f.activate()
        val original = f.operations.values.single()
        val preview = f.repository.prepareRecovery(FOREST).getOrThrow()
        assertNull(preview.remote)
        assertEquals(listOf(original), f.operations.values.toList())
        f.forest = f.forest.copy(nom = "Changed after preview")
        assertTrue(f.repository.confirmRepublishLocal(preview).isFailure)
        assertEquals(listOf(original), f.operations.values.toList())
        assertTrue(f.requests.isEmpty())
    }

    @Test fun over_limit_large_fields_are_refused_before_allocating_a_snapshot_or_granting_consent() = runTest {
        val f = Fixture()
        coEvery { f.dao.largeFieldBytes(FOREST) } returns HIERARCHY_MAX_BYTES.toLong() + 1
        val selection = f.repository.loadSelection().getOrThrow()
        assertTrue(f.repository.activateSelected(selection, setOf(FOREST)).isFailure)
        assertTrue(f.operations.isEmpty())
        assertTrue(f.associations.isEmpty())
        coVerify(exactly = 0) { f.dao.sessions(any()) }
    }

    @Test fun excessive_counts_are_refused_before_loading_children_or_granting_consent() = runTest {
        for (counts in listOf(HierarchyLocalCounts(1001, 0, 0, 0, 0), HierarchyLocalCounts(0, 5001, 0, 0, 0),
            HierarchyLocalCounts(0, 0, 1001, 0, 0), HierarchyLocalCounts(0, 0, 0, 50001, 0))) {
            val f = Fixture()
            coEvery { f.dao.counts(FOREST) } returns counts
            val selection = f.repository.loadSelection().getOrThrow()
            assertTrue(f.repository.activateSelected(selection, setOf(FOREST)).isFailure)
            assertTrue(f.operations.isEmpty())
            assertTrue(f.associations.isEmpty())
            coVerify(exactly = 0) { f.dao.parcels(any()) }
            coVerify(exactly = 0) { f.dao.sessions(any()) }
            coVerify(exactly = 0) { f.dao.trees(any()) }
        }
    }

    private class TestStore : IdentitySessionStore(IdentitySessionCoordinator()) {
        var state = IdentitySessionSnapshot("fixture", StoredIdentityTokens(token("A", "original"), "fixture-refresh-A"))
        override fun readState() = state
        override fun writeState(state: IdentitySessionSnapshot): Boolean { this.state = state; return true }
    }

    private class Fixture {
        val store = TestStore()
        val identityApi = mockk<IdentityApiService>()
        val identity = IdentityRepositoryImpl(identityApi, "$ORIGIN/", false, store)
        val dao = mockk<HierarchySyncDao>()
        val api = mockk<HierarchySyncApiService>()
        val context = mockk<Context>()
        var forest = forestFixture()
        var parcelAccounts = emptyList<String>()
        val associations = linkedMapOf<String, HierarchySyncAssociationEntity>()
        val operations = linkedMapOf<String, HierarchySyncOperationEntity>()
        val requests = mutableListOf<String>()
        val headers = mutableListOf<String>()
        var scheduled = 0
        val repository: HierarchySyncRepositoryImpl

        init {
            every { context.applicationContext } returns context
            coEvery { identityApi.refresh(any()) } returns TokenResponseDto(accessToken = token("A", "renewed"), refreshToken = "fixture-refreshed", tokenType = "bearer", expiresIn = 900)
            coEvery { identityApi.profile(any()) } returns AccountProfileDto(accountId = "A", emailVerified = false, providers = listOf("local"), roles = listOf("user"))
            coEvery { dao.forests() } coAnswers { listOf(forest) }
            coEvery { dao.forest(any()) } coAnswers { forest.takeIf { it.foretId == firstArg<String>() } }
            coEvery { dao.parcels(any()) } returns emptyList()
            coEvery { dao.plots(any()) } returns emptyList()
            coEvery { dao.sessions(any()) } returns emptyList()
            coEvery { dao.trees(any()) } returns emptyList()
            coEvery { dao.largeFieldBytes(any()) } returns 0L
            coEvery { dao.parcelAccounts(any()) } coAnswers { parcelAccounts }
            coEvery { dao.counts(any()) } returns HierarchyLocalCounts(0, 0, 0, 0, 0)
            coEvery { dao.association(any()) } coAnswers { associations[firstArg<String>()] }
            coEvery { dao.associations(any()) } coAnswers { associations.values.filter { it.accountId == firstArg<String>() } }
            coEvery { dao.insertAssociation(any()) } coAnswers {
                val value = firstArg<HierarchySyncAssociationEntity>(); check(value.forestId !in associations)
                associations[value.forestId] = value
            }
            coEvery { dao.recordCapture(any(), any(), any()) } coAnswers {
                val old = associations[firstArg<String>()]
                if (old == null || old.accountId != secondArg<String>()) 0 else {
                    associations[old.forestId] = old.copy(lastCapturedHash = thirdArg(), lastErrorCode = null); 1
                }
            }
            coEvery { dao.captureError(any(), any(), any()) } returns 1
            coEvery { dao.checkpoint(any(), any(), any(), any()) } coAnswers {
                val old = requireNotNull(associations[firstArg<String>()])
                associations[old.forestId] = old.copy(serverVersion = thirdArg(), lastSuccessAt = arg(3)); 1
            }
            coEvery { dao.append(any()) } coAnswers {
                val value = firstArg<HierarchySyncOperationEntity>(); check(value.operationId !in operations)
                val sequence = (operations.values.maxOfOrNull { it.sequence } ?: 0L) + 1
                operations[value.operationId] = value.copy(sequence = sequence); sequence
            }
            coEvery { dao.operation(any()) } coAnswers { operations[firstArg<String>()] }
            coEvery { dao.tail(any(), any()) } coAnswers {
                operations.values.lastOrNull { it.forestId == firstArg<String>() && it.accountId == secondArg<String>() && it.state != "SUPERSEDED" }
            }
            coEvery { dao.pendingCount(any()) } coAnswers {
                operations.values.count { it.accountId == firstArg<String>() && (it.state in setOf("PENDING", "SYNCING") || it.state == "ERROR" && it.nextAttemptAt != Long.MAX_VALUE) }
            }
            coEvery { dao.ready(any(), any(), any(), any()) } coAnswers {
                operations.values.filter { value -> value.accountId == firstArg<String>() && isHead(value) &&
                    ((value.state in setOf("PENDING", "ERROR") && value.nextAttemptAt <= secondArg<Long>()) ||
                        value.state == "SYNCING" && value.lastAttemptAt?.let { it <= thirdArg<Long>() } == true)
                }.take(arg<Int>(3))
            }
            coEvery { dao.claim(any(), any(), any(), any(), any(), any(), any()) } coAnswers {
                val value = operations[firstArg<String>()]
                if (value == null || value.accountId != secondArg<String>() || value.state != thirdArg<String>() ||
                    value.attemptId != arg<String?>(3) || value.lastAttemptAt != arg<Long?>(4) || !isHead(value)) 0
                else { operations[value.operationId] = value.copy(state = "SYNCING", attemptId = arg(5), lastAttemptAt = arg(6)); 1 }
            }
            coEvery { dao.acknowledge(any(), any(), any(), any(), any(), any()) } coAnswers {
                val value = operations[firstArg<String>()]
                if (value == null || value.accountId != secondArg<String>() || value.state != "SYNCING" || value.attemptId != thirdArg<String>() || value.expectedServerVersion != arg<Int>(3)) 0
                else { operations[value.operationId] = value.copy(state = "SYNCED", ackVersion = arg(3), serverPayloadSha256 = arg(4), receiptJson = arg(5)); 1 }
            }
            coEvery { dao.fail(any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
                val value = operations[firstArg<String>()]
                if (value == null || value.accountId != secondArg<String>() || value.state != "SYNCING" || value.attemptId != thirdArg<String>()) 0
                else { operations[value.operationId] = value.copy(state = arg(3), retryCount = arg(4), nextAttemptAt = arg(5), lastErrorCode = arg(6), receiptJson = arg(7)); 1 }
            }
            coEvery { dao.blockFollowing(any(), any(), any(), any()) } coAnswers {
                val changed = operations.values.filter { it.forestId == firstArg<String>() && it.accountId == secondArg<String>() && it.sequence > thirdArg<Long>() && it.state !in setOf("SYNCED", "SUPERSEDED") }
                changed.forEach { operations[it.operationId] = it.copy(state = "BLOCKED_PREDECESSOR", lastErrorCode = arg(3)) }; changed.size
            }
            coEvery { dao.supersede(any(), any()) } coAnswers {
                val changed = operations.values.filter { it.forestId == firstArg<String>() && it.accountId == secondArg<String>() && it.state != "SYNCED" }
                changed.forEach { operations[it.operationId] = it.copy(state = "SUPERSEDED") }; changed.size
            }
            coEvery { dao.transaction(any()) } coAnswers {
                val beforeAssociations = associations.toMap(); val beforeOperations = operations.toMap()
                try { firstArg<suspend () -> Unit>().invoke() }
                catch (failure: Throwable) {
                    associations.clear(); associations.putAll(beforeAssociations)
                    operations.clear(); operations.putAll(beforeOperations)
                    throw failure
                }
            }
            coEvery { api.publish(any(), any(), any()) } coAnswers {
                headers += firstArg<String>()
                val buffer = Buffer(); thirdArg<okhttp3.RequestBody>().writeTo(buffer)
                val request = buffer.readUtf8(); requests += request
                val mutation = hierarchyJson.decodeFromString<HierarchyMutationDto>(request)
                Response.success(ack(requireNotNull(operations[mutation.operationId])))
            }
            coEvery { api.get(any(), any()) } returns httpError(404)
            coEvery { api.receipt(any(), any()) } returns httpError(404)
            repository = HierarchySyncRepositoryImpl(context, dao, identity, store, api, ORIGIN, { scheduled += 1 }, { 1000L })
        }

        suspend fun activate() { assertEquals(1, repository.activateSelected(repository.loadSelection().getOrThrow(), setOf(FOREST)).getOrThrow()) }
        private fun isHead(value: HierarchySyncOperationEntity): Boolean {
            val predecessor = value.predecessorOperationId?.let { operations[it] }
            if (value.predecessorOperationId != null && (predecessor == null || predecessor.state != "SYNCED" || predecessor.ackVersion != value.baseVersion)) return false
            return operations.values.none { it.accountId == value.accountId && it.forestId == value.forestId && it.sequence < value.sequence && it.state !in setOf("SYNCED", "SUPERSEDED") }
        }
        fun logout() { assertTrue(store.clear()); identity.reloadStoredSession() }
        fun login(account: String, version: String = "login") {
            val expected = requireNotNull(store.reserveAuthentication())
            assertTrue(store.saveIfCurrent(expected, StoredIdentityTokens(token(account, version), "fixture-refresh-$account")) is IdentitySessionWrite.Saved)
            identity.reloadStoredSession()
        }
    }

    private companion object {
        const val FOREST = "11111111-1111-4111-8111-111111111111"
        const val ORIGIN = "https://gsie.test/api/v1"
        fun forestFixture() = Foret(FOREST, "Synthetic forest", "", null, null, null, null, null, null, null, null, 100L, 200L).toEntity()
        fun ack(operation: HierarchySyncOperationEntity) = HierarchyReceiptDto("accepted", operation.operationId, "a".repeat(64), operation.forestId, operation.expectedServerVersion, "2026-10-03T10:00:00Z", requestSha256 = operation.requestSha256)
        fun <T> httpError(status: Int): Response<T> = Response.error(status, "{}".toResponseBody("application/json".toMediaType()))
        fun token(account: String, version: String): String {
            val payload = buildJsonObject { put("sub", account); put("exp", 4_000_000_000L); put("auth_provider", "local"); put("jti", version) }.toString()
            val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
            return "eyJhbGciOiJub25lIn0.$encoded.fixture"
        }
    }
}
