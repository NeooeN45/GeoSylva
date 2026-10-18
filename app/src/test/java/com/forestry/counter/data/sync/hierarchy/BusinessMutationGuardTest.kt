package com.forestry.counter.data.sync.hierarchy

import com.forestry.counter.domain.model.AccountSession
import com.forestry.counter.domain.model.IdentityProvider
import com.forestry.counter.domain.repository.IdentityRepository
import com.forestry.counter.domain.repository.IdentitySessionGuard
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class BusinessMutationGuardTest {
    @Test fun unchanged_guest_generation_allows_the_original_local_mutation() {
        val f = Fixture()
        val original = f.guard.capture(null)
        assertTrue(original())
        assertTrue(original())
    }

    @Test fun guest_to_account_to_guest_never_restores_the_original_authority() {
        val f = Fixture()
        val original = f.guard.capture(null)
        f.login("A")
        assertFalse(original())
        f.login(null)
        assertNull(f.sessions.value)
        assertFalse(original())
        assertTrue(f.guard.capture(null)())
    }

    @Test fun reserved_authentication_epoch_alone_revokes_the_guest_mutation() {
        val f = Fixture()
        val original = f.guard.capture(null)
        f.epochs.value += 1L
        assertNull(f.sessions.value)
        assertFalse(original())
    }

    @Test fun epoch_revocation_is_required_even_if_an_authenticated_guard_is_stale_but_reports_current() {
        val f = Fixture()
        f.login("A")
        val token = object : IdentitySessionGuard { override val accountId = "A" }
        every { f.identity.captureSessionGuard("A") } returns Result.success(token)
        every { f.identity.isSessionGuardCurrent(token) } returns true
        val original = f.guard.capture("A")
        assertTrue(original())
        f.epochs.value += 1L
        assertEquals("A", f.sessions.value!!.accountId)
        assertFalse(original())
    }

    private class Fixture {
        val sessions = MutableStateFlow<AccountSession?>(null)
        val epochs = MutableStateFlow(0L)
        val identity = mockk<IdentityRepository>()
        init {
            every { identity.session } returns sessions
            every { identity.sessionGeneration } returns epochs
        }
        val guard = BusinessMutationGuard(identity)
        fun login(accountId: String?) {
            epochs.value += 1L
            sessions.value = accountId?.let { AccountSession(it, IdentityProvider.LOCAL, emptyList(), 4_000_000_000L) }
        }
    }
}
