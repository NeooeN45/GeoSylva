package com.forestry.counter.data.remote.identity

import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.MutableStateFlow

internal data class IdentitySessionSnapshot(
    val revision: String,
    val tokens: StoredIdentityTokens?,
    val epoch: Long = 0,
)

/** Coordination commune aux lecteurs du même fichier privé, sans bloquer logout sur le réseau. */
internal class IdentitySessionCoordinator {
    val monitor = Any()
    val refreshMutex = Mutex()
    val generation = MutableStateFlow(0L)
    var epoch: Long = 0
        set(value) {
            field = value
            generation.value = value
        }
    var suppressTokens: Boolean = false
}

internal sealed interface IdentitySessionWrite {
    data class Saved(val state: IdentitySessionSnapshot) : IdentitySessionWrite
    data object Stale : IdentitySessionWrite
    data object Unavailable : IdentitySessionWrite
}

/**
 * Les révisions sont persistées avec les jetons : un résultat lancé avant une
 * déconnexion ou une nouvelle authentification ne peut plus remplacer cet état.
 */
internal abstract class IdentitySessionStore(
    internal val coordinator: IdentitySessionCoordinator,
) {
    protected abstract fun readState(): IdentitySessionSnapshot?
    protected abstract fun writeState(state: IdentitySessionSnapshot): Boolean

    private fun guardedState(): IdentitySessionSnapshot? = readState()?.let {
        it.copy(epoch = coordinator.epoch, tokens = it.tokens.takeUnless { coordinator.suppressTokens })
    }

    fun snapshot(): IdentitySessionSnapshot? = synchronized(coordinator.monitor) { guardedState() }

    fun read(): StoredIdentityTokens? = snapshot()?.tokens

    fun reserveAuthentication(): IdentitySessionSnapshot? = synchronized(coordinator.monitor) {
        val previous = guardedState() ?: return@synchronized null
        val reserved = previous.copy(revision = nextRevision(), epoch = ++coordinator.epoch)
        if (writeState(reserved)) {
            coordinator.suppressTokens = false
            reserved
        } else null
    }

    fun saveIfCurrent(
        expected: IdentitySessionSnapshot,
        tokens: StoredIdentityTokens,
    ): IdentitySessionWrite = synchronized(coordinator.monitor) {
        val current = guardedState() ?: return@synchronized IdentitySessionWrite.Unavailable
        if (current != expected) return@synchronized IdentitySessionWrite.Stale
        val saved = IdentitySessionSnapshot(nextRevision(), tokens, ++coordinator.epoch)
        if (writeState(saved)) {
            coordinator.suppressTokens = false
            IdentitySessionWrite.Saved(saved)
        } else {
            // SharedPreferences peut avoir muté sa carte mémoire avant de
            // signaler l'échec disque : ces jetons ne sont jamais adoptés.
            coordinator.suppressTokens = true
            IdentitySessionWrite.Unavailable
        }
    }

    fun isCurrent(expected: IdentitySessionSnapshot): Boolean =
        synchronized(coordinator.monitor) { guardedState() == expected }

    fun withCurrent(expected: IdentitySessionSnapshot, action: () -> Unit): Boolean =
        synchronized(coordinator.monitor) {
            if (guardedState() != expected) return@synchronized false
            action()
            true
        }

    fun clearIfCurrent(expected: IdentitySessionSnapshot): Boolean =
        synchronized(coordinator.monitor) {
            if (guardedState() != expected) return@synchronized false
            coordinator.suppressTokens = true
            writeState(IdentitySessionSnapshot(nextRevision(), null, ++coordinator.epoch)).also {
                if (it) coordinator.suppressTokens = false
            }
        }

    fun clear(): Boolean = synchronized(coordinator.monitor) {
        // Même un échec disque invalide les réponses déjà en vol dans le
        // processus ; l'appelant signale que l'effacement durable a échoué.
        coordinator.suppressTokens = true
        writeState(IdentitySessionSnapshot(nextRevision(), null, ++coordinator.epoch)).also {
            if (it) coordinator.suppressTokens = false
        }
    }

    private fun nextRevision(): String = UUID.randomUUID().toString()
}
