package com.forestry.counter.domain.usecase.export

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Late renderers and Android picker callbacks must not publish previously authorized data. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SynthesisExportAccessTest {
    @Test fun revoked_picker_permission_refuses_before_rendering_or_output() = runTest {
        var produced = false; var written = false
        val guard = SynthesisReadGuard({ false }, { true })
        val result = runCatching { guardedSynthesisOutput(guard, { produced = true; byteArrayOf(1) }, { written = true }) }
        assertTrue(result.isFailure); assertFalse(produced); assertFalse(written)
    }

    @Test fun logout_during_render_discards_late_bytes_before_the_output_is_opened() = runTest {
        var current = true; var written = false
        val rendering = CompletableDeferred<ByteArray>()
        val guard = SynthesisReadGuard({ current }, { current })
        val result = async { runCatching { guardedSynthesisOutput(guard, { rendering.await() }, { written = true }) } }
        runCurrent(); current = false; rendering.complete(byteArrayOf(1)); runCurrent()
        assertTrue(result.await().isFailure); assertFalse(written)
    }

    @Test fun authoritative_ownership_change_refuses_even_before_the_epoch_observer_runs() = runTest {
        var authorized = true; var written = false
        val guard = SynthesisReadGuard({ true }, { authorized })
        val result = runCatching { guardedSynthesisOutput(guard, { authorized = false; byteArrayOf(1) }, { written = true }) }
        assertTrue(result.isFailure); assertFalse(written)
    }

    @Test fun stable_permission_writes_the_exact_payload_once() = runTest {
        val expected = byteArrayOf(1, 2, 3); val writes = mutableListOf<ByteArray>(); var validations = 0
        val guard = SynthesisReadGuard({ true }, { validations++; true })
        guardedSynthesisOutput(guard, { expected }, { writes += it })
        assertEquals(2, validations); assertEquals(1, writes.size); assertArrayEquals(expected, writes.single())
    }
}
