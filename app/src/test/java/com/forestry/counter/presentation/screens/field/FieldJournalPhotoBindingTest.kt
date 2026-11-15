package com.forestry.counter.presentation.screens.field

import androidx.lifecycle.ViewModelStore
import com.forestry.counter.domain.field.FieldCodec
import com.forestry.counter.domain.field.FieldDraft
import com.forestry.counter.domain.field.FieldEntryForm
import com.forestry.counter.domain.field.FieldPhoto
import com.forestry.counter.domain.field.FieldTarget
import com.forestry.counter.domain.field.FieldTargetKind
import com.forestry.counter.domain.repository.FieldAccess
import com.forestry.counter.domain.repository.FieldError
import com.forestry.counter.domain.repository.FieldException
import com.forestry.counter.domain.repository.FieldRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FieldJournalPhotoBindingTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val target = FieldTarget(FieldTargetKind.WORKSITE, UUID(0L, 810L).toString())
    private lateinit var fixture: PhotoFixture

    @Before fun prepare() { Dispatchers.setMain(dispatcher); fixture = PhotoFixture() }
    @After fun close() { store.clear(); Dispatchers.resetMain() }
    private fun model() = FieldJournalViewModel(fixture.repository, target).also { store.put("journal", it) }

    @Test fun `photo is attached and persisted only in its initiating draft with frozen bytes`() = runTest(dispatcher) {
        val vm = model(); runCurrent(); vm.start()
        val id = checkNotNull(vm.currentPhotoDraftId())
        val bytes = byteArrayOf(1, 2, 3)
        vm.addPhoto(id, bytes, "image/jpeg")
        bytes[0] = 9
        runCurrent()

        assertEquals(id, vm.currentPhotoDraftId())
        assertEquals(listOf(fixture.photoId), vm.state.value.form?.photoIds)
        assertArrayEquals(byteArrayOf(1, 2, 3), fixture.receivedPhotos.single())
        val saved = FieldCodec.json.decodeFromString<JournalDraft>(checkNotNull(fixture.saved[0]).content)
        assertEquals(id, saved.id)
        assertEquals(listOf(fixture.photoId), saved.form.photoIds)
        assertTrue(vm.state.value.draftSaved)
        assertFalse(vm.state.value.busy)
    }

    @Test fun `late gallery result for discarded draft cannot contaminate a new draft`() = runTest(dispatcher) {
        val vm = model(); runCurrent(); vm.start()
        val cameraDraft = checkNotNull(vm.currentPhotoDraftId())
        vm.discard(); runCurrent(); vm.start()
        vm.change { copy(title = "TEST nouveau brouillon") }
        val replacement = checkNotNull(vm.currentPhotoDraftId())
        assertNotEquals(cameraDraft, replacement)

        vm.addPhoto(cameraDraft, byteArrayOf(1), "image/jpeg"); runCurrent()

        assertTrue(fixture.receivedPhotos.isEmpty())
        assertEquals(replacement, vm.currentPhotoDraftId())
        assertEquals("TEST nouveau brouillon", vm.state.value.form?.title)
        assertTrue(checkNotNull(vm.state.value.form).photoIds.isEmpty())
        assertNull(vm.state.value.error)
    }

    @Test fun `camera result after discarding without replacement never creates a journal draft`() = runTest(dispatcher) {
        val vm = model(); runCurrent(); vm.start()
        val expected = checkNotNull(vm.currentPhotoDraftId())
        vm.discard(); runCurrent()
        vm.addPhoto(expected, byteArrayOf(1), "image/jpeg"); runCurrent()

        assertTrue(fixture.receivedPhotos.isEmpty())
        assertNull(vm.currentPhotoDraftId())
        assertNull(vm.state.value.form)
        assertFalse(vm.state.value.busy)
    }

    @Test fun `authority is checked before the access observer has processed revocation`() = runTest(dispatcher) {
        val vm = model(); runCurrent(); vm.start()
        val expected = checkNotNull(vm.currentPhotoDraftId())
        fixture.epoch.value++
        assertNull(vm.currentPhotoDraftId())
        vm.addPhoto(expected, byteArrayOf(1), "image/jpeg"); runCurrent()

        assertTrue(fixture.receivedPhotos.isEmpty())
        assertTrue(vm.state.value.accessLost)
        assertNull(vm.state.value.form)
        assertEquals(FieldError.ACCESS_CHANGED, vm.state.value.error)
    }

    @Test fun `already committed photo response after revocation cannot restore or attach a draft`() = runTest(dispatcher) {
        val vm = model(); runCurrent(); vm.start()
        val expected = checkNotNull(vm.currentPhotoDraftId())
        val response = CompletableDeferred<Unit>(); fixture.photoResponse = response
        try {
            vm.addPhoto(expected, byteArrayOf(1), "image/jpeg"); runCurrent()
            assertEquals(1, fixture.receivedPhotos.size)
            assertTrue(vm.state.value.busy)
            fixture.epoch.value++; runCurrent()
            response.complete(Unit); runCurrent()

            assertTrue(vm.state.value.accessLost)
            assertNull(vm.state.value.form)
            assertNull(vm.currentPhotoDraftId())
            coVerify(exactly = 0) { fixture.repository.saveDraft(any(), any(), any(), any(), any()) }
        } finally { response.complete(Unit); runCurrent() }
    }

    @Test fun `revocation while photo waits behind an autosave prevents the photo write`() = runTest(dispatcher) {
        val vm = model(); runCurrent(); vm.start()
        val expected = checkNotNull(vm.currentPhotoDraftId())
        val response = CompletableDeferred<Unit>(); fixture.draftResponse = response
        try {
            vm.flush(); runCurrent()
            assertNotNull(fixture.saved[0])
            vm.addPhoto(expected, byteArrayOf(1), "image/jpeg"); runCurrent()
            assertTrue(fixture.receivedPhotos.isEmpty())
            fixture.epoch.value++; runCurrent()
            response.complete(Unit); runCurrent()

            assertTrue(fixture.receivedPhotos.isEmpty())
            assertTrue(vm.state.value.accessLost)
            assertNull(vm.state.value.form)
        } finally { response.complete(Unit); runCurrent() }
    }

    @Test fun `reentrant photo callback cannot create a second attachment or write`() = runTest(dispatcher) {
        val vm = model(); runCurrent(); vm.start()
        val expected = checkNotNull(vm.currentPhotoDraftId())
        fixture.onPhotoStored = { vm.addPhoto(expected, byteArrayOf(2), "image/jpeg") }
        vm.addPhoto(expected, byteArrayOf(1), "image/jpeg"); runCurrent()

        assertEquals(1, fixture.receivedPhotos.size)
        assertEquals(listOf(fixture.photoId), vm.state.value.form?.photoIds)
        coVerify(exactly = 1) { fixture.repository.addPhoto(any(), any(), any(), any(), any()) }
    }

    @Test fun `late draft save acknowledgement cannot resurrect a revoked photo form`() = runTest(dispatcher) {
        val vm = model(); runCurrent(); vm.start()
        val expected = checkNotNull(vm.currentPhotoDraftId())
        val response = CompletableDeferred<Unit>(); fixture.draftResponse = response
        try {
            vm.addPhoto(expected, byteArrayOf(1), "image/jpeg"); runCurrent()
            assertEquals(listOf(fixture.photoId), vm.state.value.form?.photoIds)
            fixture.epoch.value++; runCurrent()
            response.complete(Unit); runCurrent()

            assertTrue(vm.state.value.accessLost)
            assertNull(vm.currentPhotoDraftId())
            assertNull(vm.state.value.form)
            assertFalse(vm.state.value.draftSaved)
        } finally { response.complete(Unit); runCurrent() }
    }

    @Test fun `restored read only draft cannot expose a capture token or write a photo`() = runTest(dispatcher) {
        val draft = JournalDraft(UUID(0L, 811L).toString(), FieldEntryForm())
        fixture.saved[0] = FieldDraft(1L, FieldCodec.json.encodeToString(draft), 1L)
        fixture.writable = false
        val vm = model(); runCurrent()
        assertNotNull(vm.state.value.form)

        assertNull(vm.currentPhotoDraftId())
        vm.addPhoto(draft.id, byteArrayOf(1), "image/jpeg"); runCurrent()

        assertTrue(fixture.receivedPhotos.isEmpty())
        assertTrue(checkNotNull(vm.state.value.form).photoIds.isEmpty())
    }

    @Test fun `submitted draft and null capture token cannot accept photo results`() = runTest(dispatcher) {
        val draft = JournalDraft(UUID(0L, 812L).toString(), FieldEntryForm(), submitted = true)
        fixture.saved[0] = FieldDraft(1L, FieldCodec.json.encodeToString(draft), 1L)
        val vm = model(); runCurrent()

        assertNull(vm.currentPhotoDraftId())
        vm.addPhoto(draft.id, byteArrayOf(1), "image/jpeg")
        vm.addPhoto(null, byteArrayOf(1), "image/jpeg"); runCurrent()

        assertTrue(fixture.receivedPhotos.isEmpty())
        assertTrue(vm.state.value.retry)
    }

    private class PhotoFixture {
        private class Access(val epoch: Int) : FieldAccess
        val repository = mockk<FieldRepository>()
        val epoch = MutableStateFlow(0)
        val saved = mutableMapOf<Int, FieldDraft>()
        val receivedPhotos = mutableListOf<ByteArray>()
        val photoId = UUID(0L, 813L).toString()
        var writable = true
        var photoResponse: CompletableDeferred<Unit>? = null
        var draftResponse: CompletableDeferred<Unit>? = null
        var onPhotoStored: (() -> Unit)? = null

        init {
            coEvery { repository.captureAccess() } answers { Access(epoch.value) }
            every { repository.isCurrent(any()) } answers { (firstArg<FieldAccess>() as? Access)?.epoch == epoch.value }
            every { repository.accessChanges() } returns epoch.map { Unit }
            coEvery { repository.title(any(), any()) } returns "TEST dossier"
            coEvery { repository.entries(any(), any()) } returns emptyList()
            coEvery { repository.writable(any(), any()) } answers { writable }
            coEvery { repository.measurements(any(), any()) } returns null
            coEvery { repository.protectedEntries(any(), any()) } returns emptySet()
            coEvery { repository.loadDraft(any(), any(), any()) } answers { saved[(thirdArg<FieldAccess>() as Access).epoch] }
            coEvery { repository.deleteDraft(any(), any(), any(), any()) } answers {
                saved.remove((arg<FieldAccess>(3) as Access).epoch); Unit
            }
            coEvery { repository.saveDraft(any(), any(), any(), any(), any()) } coAnswers {
                val owner = (arg<FieldAccess>(4) as Access).epoch
                if (owner != epoch.value) throw FieldException(FieldError.ACCESS_CHANGED)
                val previous = saved[owner]
                val expected = arg<Long?>(2)
                val content = arg<String>(3)
                if (previous?.revision != expected) throw FieldException(FieldError.CONFLICT)
                val result = FieldDraft((expected ?: 0L) + 1L, content, 1L)
                saved[owner] = result
                // A commit can precede a deferred response. Returning it never grants fresh authority.
                draftResponse?.await()
                result
            }
            coEvery { repository.addPhoto(any(), any(), any(), any(), any()) } coAnswers {
                if ((arg<FieldAccess>(4) as Access).epoch != epoch.value) throw FieldException(FieldError.ACCESS_CHANGED)
                val bytes = arg<ByteArray>(1).copyOf()
                receivedPhotos += bytes
                onPhotoStored?.invoke()
                photoResponse?.await()
                FieldPhoto(photoId, "", arg(2), bytes.size, "0".repeat(64))
            }
        }
    }
}
