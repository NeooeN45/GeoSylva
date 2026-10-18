package com.forestry.counter.data.repository

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class WoodDataAccessGateTest {
    @Test fun queued_mutation_is_invalidated_before_purge_gets_lock() = runTest {
        val gate = WoodDataAccessGate()
        val epoch = gate.capture()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val holder = launch(start = CoroutineStart.UNDISPATCHED) { gate.withAccess(epoch) { entered.complete(Unit); release.await() } }
        entered.await()
        var wrote = false
        val queued = async(start = CoroutineStart.UNDISPATCHED) { runCatching { gate.withAccess(epoch) { wrote = true } } }
        val purge = launch(start = CoroutineStart.UNDISPATCHED) { gate.purge {} }
        assertFalse(gate.isCurrent(epoch))
        assertTrue(runCatching { gate.capture() }.isFailure)
        release.complete(Unit); holder.join(); purge.join()
        assertTrue(queued.await().isFailure); assertFalse(wrote)
        assertTrue(gate.isCurrent(gate.capture()))
    }
    @Test fun cancelled_waiting_purge_releases_block_but_does_not_restore_old_epoch() = runTest {
        val gate = WoodDataAccessGate(); val epoch = gate.capture()
        val release = CompletableDeferred<Unit>()
        val holder = launch(start = CoroutineStart.UNDISPATCHED) { gate.withAccess(epoch) { release.await() } }
        val purge = launch(start = CoroutineStart.UNDISPATCHED) { gate.purge {} }
        purge.cancelAndJoin()
        assertFalse(gate.isCurrent(epoch)); assertEquals(0, gate.state.value.pendingPurges)
        release.complete(Unit); holder.join()
        gate.withAccess(gate.capture()) {}
    }
    @Test fun cleanup_failure_retains_invalidation_and_unblocks_future_explicit_access() = runTest {
        val gate = WoodDataAccessGate(); val epoch = gate.capture()
        assertTrue(runCatching { gate.purge { error("TEST file failure") } }.isFailure)
        assertFalse(gate.isCurrent(epoch)); assertEquals(0, gate.state.value.pendingPurges)
        gate.withAccess(gate.capture()) {}
    }
}
