package com.forestry.counter.data.local

import com.forestry.counter.domain.model.AccountSession
import com.forestry.counter.domain.model.IdentityProvider
import com.forestry.counter.domain.repository.IdentityRepository
import com.forestry.counter.domain.repository.IdentitySessionGuard
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.MutableStateFlow

/** Synthetic identity only, no production service, preferences, tokens or mocking dependency. */
internal class BusinessReadIdentityFixture : IdentityRepository by unsupportedIdentity() {
    override val session = MutableStateFlow<AccountSession?>(null)
    override val sessionGeneration = MutableStateFlow(0L)
    private var generation = 0L
    fun login(account: String?) {
        generation += 1
        sessionGeneration.value = generation
        session.value = account?.let { AccountSession(it, IdentityProvider.LOCAL, emptyList(), 4_000_000_000L) }
    }
    override fun captureSessionGuard(expectedAccountId: String): Result<IdentitySessionGuard> =
        if (session.value?.accountId == expectedAccountId) Result.success(Guard(expectedAccountId, generation))
        else Result.failure(IllegalStateException("FIXTURE_SESSION_CHANGED"))
    override fun isSessionGuardCurrent(guard: IdentitySessionGuard): Boolean =
        (guard as? Guard)?.let { it.generation == generation && it.accountId == session.value?.accountId } == true
    private data class Guard(override val accountId: String, val generation: Long) : IdentitySessionGuard
}

private fun unsupportedIdentity(): IdentityRepository = Proxy.newProxyInstance(
    IdentityRepository::class.java.classLoader, arrayOf(IdentityRepository::class.java),
) { _, method, _ -> throw UnsupportedOperationException("Fixture does not implement ${method.name}") } as IdentityRepository
