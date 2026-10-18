package com.forestry.counter.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shared by one database's wood repositories and purge. Lock order: gate, then Room. */
class WoodDataAccessGate {
    data class State(val epoch: Long, val pendingPurges: Int)
    private val monitor = Any()
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(State(0, 0))
    val state = mutableState.asStateFlow()

    fun capture(): Long = synchronized(monitor) {
        check(mutableState.value.pendingPurges == 0) { "WOOD_PURGE_ACTIVE" }
        mutableState.value.epoch
    }

    fun isCurrent(epoch: Long): Boolean = state.value.let { it.epoch == epoch && it.pendingPurges == 0 }

    suspend fun <T> withAccess(epoch: Long, block: suspend () -> T): T = mutex.withLock {
        check(isCurrent(epoch)) { "WOOD_ACCESS_CHANGED" }
        block()
    }

    /** Invalidates queued work before waiting for its lock, including on cancellation/failure. */
    suspend fun <T> purge(block: suspend () -> T): T {
        synchronized(monitor) {
            val old = mutableState.value
            check(old.epoch < Long.MAX_VALUE && old.pendingPurges < Int.MAX_VALUE)
            mutableState.value = State(old.epoch + 1, old.pendingPurges + 1)
        }
        try {
            return mutex.withLock { block() }
        } finally {
            synchronized(monitor) {
                mutableState.value = mutableState.value.let { it.copy(pendingPurges = it.pendingPurges - 1) }
            }
        }
    }
}
