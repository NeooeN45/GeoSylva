package com.forestry.counter.data.repository

import com.forestry.counter.data.local.dao.CounterDao
import com.forestry.counter.data.local.entity.CounterEntity
import com.forestry.counter.domain.model.Counter
import com.forestry.counter.domain.repository.CounterRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Tests pour CounterRepositoryImpl - Couverture des fonctionnalités de gestion des compteurs.
 * Vérifie la sécurité des accès et la gestion des erreurs.
 */
class CounterRepositoryImplTest {

    private lateinit var counterDao: CounterDao
    private lateinit var counterRepository: CounterRepository

    @Before
    fun setUp() {
        counterDao = mockk(relaxed = true)
        counterRepository = CounterRepositoryImpl(counterDao)
    }

    @Test
    fun `getAllCounters should return flow of counters`() = runTest {
        // Given
        val mockEntities = listOf(
            CounterEntity(id = 1, groupId = 1, name = "Tree Height", unit = "m", step = 0.1),
            CounterEntity(id = 2, groupId = 1, name = "Diameter", unit = "cm", step = 0.5)
        )
        every { counterDao.getAllCounters() } returns flowOf(mockEntities)

        // When
        val result = counterRepository.getAllCounters().first()

        // Then
        assert(result.size == 2)
        assert(result[0].name == "Tree Height")
        assert(result[0].unit == "m")
        assert(result[1].name == "Diameter")
        assert(result[1].unit == "cm")
    }

    @Test
    fun `getCountersByGroup should return filtered counters`() = runTest {
        // Given
        val groupId = 1L
        val mockEntities = listOf(
            CounterEntity(id = 1, groupId = groupId, name = "Height", unit = "m", step = 0.1),
            CounterEntity(id = 2, groupId = 2, name = "Other", unit = "cm", step = 0.5)
        )
        every { counterDao.getCountersByGroup(groupId) } returns flowOf(mockEntities.filter { it.groupId == groupId })

        // When
        val result = counterRepository.getCountersByGroup(groupId).first()

        // Then
        assert(result.size == 1)
        assert(result[0].name == "Height")
        assert(result[0].groupId == groupId)
    }

    @Test
    fun `getCounterById should return counter when exists`() = runTest {
        // Given
        val counterId = 1L
        val mockEntity = CounterEntity(id = counterId, groupId = 1, name = "Test", unit = "m", step = 0.1)
        coEvery { counterDao.getCounterById(counterId) } returns mockEntity

        // When
        val result = counterRepository.getCounterById(counterId)

        // Then
        assert(result != null)
        assert(result!!.id == counterId)
        assert(result.name == "Test")
    }

    @Test
    fun `getCounterById should return null when not exists`() = runTest {
        // Given
        val counterId = 999L
        coEvery { counterDao.getCounterById(counterId) } returns null

        // When
        val result = counterRepository.getCounterById(counterId)

        // Then
        assert(result == null)
    }

    @Test
    fun `insertCounter should create new counter`() = runTest {
        // Given
        val counter = Counter(
            id = 0, // New counter
            groupId = 1,
            name = "New Counter",
            unit = "m",
            step = 0.1
        )
        coEvery { counterDao.insertCounter(any()) } returns 1L

        // When
        val result = counterRepository.insertCounter(counter)

        // Then
        assert(result == 1L)
        coVerify { counterDao.insertCounter(any()) }
    }

    @Test
    fun `updateCounter should modify existing counter`() = runTest {
        // Given
        val counter = Counter(
            id = 1,
            groupId = 1,
            name = "Updated Counter",
            unit = "cm",
            step = 0.5
        )
        coEvery { counterDao.updateCounter(any()) } returns 1

        // When
        counterRepository.updateCounter(counter)

        // Then
        coVerify { counterDao.updateCounter(any()) }
    }

    @Test
    fun `deleteCounter should remove counter`() = runTest {
        // Given
        val counterId = 1L
        coEvery { counterDao.deleteCounter(counterId) } returns 1

        // When
        counterRepository.deleteCounter(counterId)

        // Then
        coVerify { counterDao.deleteCounter(counterId) }
    }

    @Test
    fun `deleteCountersByGroup should remove all group counters`() = runTest {
        // Given
        val groupId = 1L
        coEvery { counterDao.deleteCountersByGroup(groupId) } returns 3 // 3 counters deleted

        // When
        counterRepository.deleteCountersByGroup(groupId)

        // Then
        coVerify { counterDao.deleteCountersByGroup(groupId) }
    }

    @Test
    fun `insertCounter should validate input data`() = runTest {
        // Given
        val invalidCounter = Counter(
            id = 0,
            groupId = 1,
            name = "", // Invalid empty name
            unit = "m",
            step = 0.1
        )

        // When/Then
        try {
            counterRepository.insertCounter(invalidCounter)
            assert(false) { "Should throw exception for invalid data" }
        } catch (e: IllegalArgumentException) {
            assert(e.message?.contains("name") == true)
        }
    }

    @Test
    fun `updateCounter should validate input data`() = runTest {
        // Given
        val invalidCounter = Counter(
            id = 1,
            groupId = 1,
            name = "Valid Name",
            unit = "", // Invalid empty unit
            step = -0.1 // Invalid negative step
        )

        // When/Then
        try {
            counterRepository.updateCounter(invalidCounter)
            assert(false) { "Should throw exception for invalid data" }
        } catch (e: IllegalArgumentException) {
            assert(e.message?.contains("unit") == true || e.message?.contains("step") == true)
        }
    }

    @Test
    fun `getAllCounters should handle empty list`() = runTest {
        // Given
        every { counterDao.getAllCounters() } returns flowOf(emptyList())

        // When
        val result = counterRepository.getAllCounters().first()

        // Then
        assert(result.isEmpty())
    }

    @Test
    fun `getCountersByGroup should handle non-existent group`() = runTest {
        // Given
        val nonExistentGroupId = 999L
        every { counterDao.getCountersByGroup(nonExistentGroupId) } returns flowOf(emptyList())

        // When
        val result = counterRepository.getCountersByGroup(nonExistentGroupId).first()

        // Then
        assert(result.isEmpty())
    }

    @Test
    fun `entity mapping should preserve all fields`() = runTest {
        // Given
        val mockEntity = CounterEntity(
            id = 1,
            groupId = 2,
            name = "Test Counter",
            unit = "meters",
            step = 0.25
        )
        every { counterDao.getAllCounters() } returns flowOf(listOf(mockEntity))

        // When
        val result = counterRepository.getAllCounters().first()

        // Then
        assert(result.size == 1)
        val counter = result[0]
        assert(counter.id == 1L)
        assert(counter.groupId == 2L)
        assert(counter.name == "Test Counter")
        assert(counter.unit == "meters")
        assert(counter.step == 0.25)
    }
}
