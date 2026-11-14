package com.forestry.counter.presentation.screens.forestry

import androidx.lifecycle.ViewModelStore
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.repository.ParcelleRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateParcelleViewModelTest {
    @Test fun `failed manual creation retains form and identity while blocking concurrent submissions`() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val repository = mockk<ParcelleRepository>()
        val attempts = mutableListOf<Parcelle>()
        val finish = CompletableDeferred<Unit>()
        coEvery { repository.insertParcelle(any()) } coAnswers {
            attempts += firstArg<Parcelle>()
            if (attempts.size == 1) { finish.await(); throw IllegalStateException("fixture failure") }
        }
        val model = CreateParcelleViewModel(repository, "parent-fixture")
        try {
            runTest(dispatcher) {
                model.updateName("Parcelle saisie")
                model.updateSurfaceHa("1,25")
                model.updateRemarks("Surface annoncée par le propriétaire")
                model.save()
                model.save()
                model.updateName("Modification pendant sauvegarde")
                runCurrent()
                assertEquals(1, attempts.size)
                assertTrue(model.formState.value.isSaving)
                assertFalse(model.isSaved.value)
                finish.complete(Unit)
                runCurrent()
                assertTrue(model.formState.value.saveError)
                assertEquals("Parcelle saisie", model.formState.value.name)
                assertEquals(1.25, requireNotNull(model.formState.value.surfaceHa), 0.0)
                model.save()
                runCurrent()
                assertEquals(2, attempts.size)
                assertEquals(attempts[0], attempts[1])
                assertEquals("parent-fixture", attempts[1].foretId)
                assertNull(attempts[1].shape)
                assertTrue(model.isSaved.value)
            }
        } finally {
            ViewModelStore().apply { put("manual-parcel", model); clear() }
            Dispatchers.resetMain()
        }
    }
}
