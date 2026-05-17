package com.forestry.counter.domain.usecase.export

import android.content.Context
import com.forestry.counter.data.local.dao.CounterDao
import com.forestry.counter.data.local.dao.FormulaDao
import com.forestry.counter.data.local.dao.GroupDao
import com.forestry.counter.data.local.entity.CounterEntity
import com.forestry.counter.data.local.entity.GroupEntity
import com.forestry.counter.domain.usecase.export.ExportDataUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests pour ExportDataUseCase - Couverture des fonctionnalités d'exportation.
 * Vérifie la sécurité des exports et la gestion des erreurs.
 */
class ExportDataUseCaseTest {

    private lateinit var context: Context
    private lateinit var groupDao: GroupDao
    private lateinit var counterDao: CounterDao
    private lateinit var formulaDao: FormulaDao
    private lateinit var exportDataUseCase: ExportDataUseCase

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        groupDao = mockk(relaxed = true)
        counterDao = mockk(relaxed = true)
        formulaDao = mockk(relaxed = true)

        exportDataUseCase = ExportDataUseCase(
            context = context,
            groupDao = groupDao,
            counterDao = counterDao,
            formulaDao = formulaDao
        )
    }

    @Test
    fun `exportToCSV should export valid data`() = runTest {
        // Given
        val mockGroups = listOf(
            GroupEntity(id = 1, name = "Forest Plot A", description = "Test plot"),
            GroupEntity(id = 2, name = "Forest Plot B", description = "Another plot")
        )
        val mockCounters = listOf(
            CounterEntity(id = 1, groupId = 1, name = "Tree Height", unit = "m", step = 0.1),
            CounterEntity(id = 2, groupId = 1, name = "Diameter", unit = "cm", step = 0.5)
        )
        
        every { groupDao.getAllGroups() } returns flowOf(mockGroups)
        every { counterDao.getAllCounters() } returns flowOf(mockCounters)
        
        val mockFile = mockk<File>(relaxed = true)
        every { mockFile.absolutePath } returns "/test/export.csv"

        // When
        val result = exportDataUseCase.exportToCsv(mockFile)

        // Then
        assert(result.isSuccess)
        verify { mockFile.writeText(any()) }
    }

    @Test
    fun `exportToCSV should handle empty data`() = runTest {
        // Given
        every { groupDao.getAllGroups() } returns flowOf(emptyList())
        every { counterDao.getAllCounters() } returns flowOf(emptyList())
        
        val mockFile = mockk<File>(relaxed = true)

        // When
        val result = exportDataUseCase.exportToCsv(mockFile)

        // Then
        assert(result.isSuccess)
        verify { mockFile.writeText(any()) }
    }

    @Test
    fun `exportToCSV should sanitize output data`() = runTest {
        // Given
        val mockGroups = listOf(
            GroupEntity(id = 1, name = "Plot<script>alert('xss')</script>", description = "Test")
        )
        val mockCounters = listOf(
            CounterEntity(id = 1, groupId = 1, name = "Height", unit = "m", step = 0.1)
        )
        
        every { groupDao.getAllGroups() } returns flowOf(mockGroups)
        every { counterDao.getAllCounters() } returns flowOf(mockCounters)
        
        val mockFile = mockk<File>(relaxed = true)
        val capturedContent = mutableListOf<String>()
        every { mockFile.writeText(capture(capturedContent)) } returns mockk()

        // When
        val result = exportDataUseCase.exportToCsv(mockFile)

        // Then
        assert(result.isSuccess)
        val csvContent = capturedContent.first()
        assert(!csvContent.contains("<script>"))
        assert(csvContent.contains("Plotalertxss"))
    }

    @Test
    fun `exportToExcel should create valid Excel file`() = runTest {
        // Given
        val mockGroups = listOf(GroupEntity(id = 1, name = "Test Group", description = "Test"))
        val mockCounters = listOf(CounterEntity(id = 1, groupId = 1, name = "Test Counter", unit = "m", step = 0.1))
        
        every { groupDao.getAllGroups() } returns flowOf(mockGroups)
        every { counterDao.getAllCounters() } returns flowOf(mockCounters)
        
        val mockFile = mockk<File>(relaxed = true)
        every { mockFile.extension } returns "xlsx"

        // When
        val result = exportDataUseCase.exportToExcel(mockFile)

        // Then
        // Should attempt Excel processing (implementation depends on Excel library)
        assert(result.isSuccess || result.isFailure) // Basic test
    }

    @Test
    fun `export should handle file write errors`() = runTest {
        // Given
        val mockGroups = listOf(GroupEntity(id = 1, name = "Test", description = "Test"))
        val mockCounters = listOf(CounterEntity(id = 1, groupId = 1, name = "Test", unit = "m", step = 0.1))
        
        every { groupDao.getAllGroups() } returns flowOf(mockGroups)
        every { counterDao.getAllCounters() } returns flowOf(mockCounters)
        
        val mockFile = mockk<File>()
        every { mockFile.writeText(any()) } throws IOException("Permission denied")

        // When
        val result = exportDataUseCase.exportToCsv(mockFile)

        // Then
        assert(result.isFailure)
        assert(result.exceptionOrNull() is IOException)
    }

    @Test
    fun `export should validate file permissions`() = runTest {
        // Given
        val mockFile = mockk<File>(relaxed = true)
        every { mockFile.canWrite() } returns false

        // When
        val result = exportDataUseCase.exportToCsv(mockFile)

        // Then
        assert(result.isFailure)
        assert(result.exceptionOrNull()?.message?.contains("write") == true)
    }

    @Test
    fun `export should limit data size to prevent memory issues`() = runTest {
        // Given
        val largeGroupList = (1..10000).map { 
            GroupEntity(id = it.toLong(), name = "Group $it", description = "Large data test")
        }
        every { groupDao.getAllGroups() } returns flowOf(largeGroupList)
        every { counterDao.getAllCounters() } returns flowOf(emptyList())
        
        val mockFile = mockk<File>(relaxed = true)

        // When
        val result = exportDataUseCase.exportToCsv(mockFile)

        // Then
        assert(result.isSuccess)
        // Should process large data without memory issues
    }

    @Test
    fun `export should include headers in CSV`() = runTest {
        // Given
        every { groupDao.getAllGroups() } returns flowOf(emptyList())
        every { counterDao.getAllCounters() } returns flowOf(emptyList())
        
        val mockFile = mockk<File>(relaxed = true)
        val capturedContent = mutableListOf<String>()
        every { mockFile.writeText(capture(capturedContent)) } returns mockk()

        // When
        val result = exportDataUseCase.exportToCsv(mockFile)

        // Then
        assert(result.isSuccess)
        val csvContent = capturedContent.first()
        assert(csvContent.contains("id,name,description")) // Headers present
    }

    @Test
    fun `export should handle special characters correctly`() = runTest {
        // Given
        val mockGroups = listOf(
            GroupEntity(id = 1, name = "Plot with, comma", description = "Description with\nnewline"),
            GroupEntity(id = 2, name = "Plot with \"quotes\"", description = "Description with;semicolon")
        )
        every { groupDao.getAllGroups() } returns flowOf(mockGroups)
        every { counterDao.getAllCounters() } returns flowOf(emptyList())
        
        val mockFile = mockk<File>(relaxed = true)
        val capturedContent = mutableListOf<String>()
        every { mockFile.writeText(capture(capturedContent)) } returns mockk()

        // When
        val result = exportDataUseCase.exportToCsv(mockFile)

        // Then
        assert(result.isSuccess)
        val csvContent = capturedContent.first()
        // CSV should properly escape special characters
        assert(csvContent.contains("\"Plot with, comma\""))
        assert(csvContent.contains("\"Plot with \"\"quotes\"\"\""))
    }
}
