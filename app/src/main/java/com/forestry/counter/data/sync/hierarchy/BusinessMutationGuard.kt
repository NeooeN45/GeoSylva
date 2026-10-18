package com.forestry.counter.data.sync.hierarchy

import com.forestry.counter.domain.repository.IdentityRepository

/** Captures the initiating session generation; A -> logout -> A is not the original mutation. */
class BusinessMutationGuard(private val identity: IdentityRepository) {
    fun capture(accountId: String?): () -> Boolean {
        val generation = identity.sessionGeneration.value
        if (accountId == null) return { identity.session.value == null && identity.sessionGeneration.value == generation }
        val guard = identity.captureSessionGuard(accountId).getOrNull()
        return { guard != null && identity.sessionGeneration.value == generation &&
            identity.session.value?.accountId == accountId && identity.isSessionGuardCurrent(guard) }
    }
}
