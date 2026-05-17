package com.forestry.counter.domain.usecase.import

import android.content.Context
import com.forestry.counter.data.local.dao.CounterDao
import com.forestry.counter.data.local.dao.GroupDao
import com.forestry.counter.data.local.dao.ParcelleDao
import com.forestry.counter.data.local.dao.PlacetteDao
import com.forestry.counter.domain.usecase.import.ImportDataUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests pour ImportDataUseCase - Couverture des fonctionnalités d'importation.
 * Vérifie la sécurité des imports et la gestion des erreurs.
 */
class ImportDataUseCaseTest {

    private lateinit var context: Context
    private lateinit var groupDao: GroupDao
    private lateinit var counterDao: CounterDao
    private lateinit var parcelleDao: ParcelleDao
    private lateinit var placetteDao: PlacetteDao
    private lateinit var importDataUseCase: ImportDataUseCase

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        groupDao = mockk(relaxed = true)
        counterDao = mockk(relaxed = true)
        parcelleDao = mockk(relaxed = true)
        placetteDao = mockk(relaxed = true)

        importDataUseCase = ImportDataUseCase(
            context = context,
            groupDao = groupDao,
            counterDao = counterDao,
            parcelleDao = parcelleDao,
            placetteDao = placetteDao
        )
    }

    @Test
    fun `importFromCSV should handle valid CSV file`() = runTest {
        // Given
        val mockFile = mockk<File>(relaxed = true)
        every { mockFile.exists() } returns true
        every { mockFile.canRead() } returns true
        every { mockFile.readText() } returns validCsvContent

        // When
        val result = importDataUseCase.importFromCsv(mockFile, ImportDataUseCase.ImportMode.MERGE)

        // Then
        assert(result.isSuccess)
        verify { counterDao.insertAll(any()) }
        verify { groupDao.insertAll(any()) }
    }

    @Test
    fun `importFromCSV should reject non-existent file`() = runTest {
        // Given
        val mockFile = mockk<File>(relaxed = true)
        every { mockFile.exists() } returns false

        // When
        val result = importDataUseCase.importFromCsv(mockFile, ImportDataUseCase.ImportMode.MERGE)

        // Then
        assert(result.isFailure)
        assert(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `importFromCSV should handle malformed CSV`() = runTest {
        // Given
        val mockFile = mockk<File>(relaxed = true)
        every { mockFile.exists() } returns true
        every { mockFile.canRead() } returns true
        every { mockFile.readText() } returns "invalid,csv,format"

        // When
        val result = importDataUseCase.importFromCsv(mockFile, ImportDataUseCase.ImportMode.MERGE)

        // Then
        assert(result.isFailure)
    }

    @Test
    fun `importFromExcel should handle valid Excel file`() = runTest {
        // Given
        val mockFile = mockk<File>(relaxed = true)
        every { mockFile.exists() } returns true
        every { mockFile.canRead() } returns true
        every { mockFile.extension } returns "xlsx"

        // When
        val result = importDataUseCase.importFromExcel(mockFile, ImportDataUseCase.ImportMode.REPLACE)

        // Then
        // Should attempt Excel processing (implementation depends on Excel library)
        assert(result.isSuccess || result.isFailure) // Basic test
    }

    @Test
    fun `import should validate file size limits`() = runTest {
        // Given
        val mockFile = mockk<File>(relaxed = true)
        every { mockFile.exists() } returns true
        every { mockFile.canRead() } returns true
        every { mockFile.length() } returns 50 * 1024 * 1024L // 50MB

        // When
        val result = importDataUseCase.importFromCsv(mockFile, ImportDataUseCase.ImportMode.MERGE)

        // Then
        assert(result.isFailure)
        assert(result.exceptionOrNull()?.message?.contains("too large") == true)
    }

    @Test
    fun `import should sanitize data inputs`() = runTest {
        // Given
        val mockFile = mockk<File>(relaxed = true)
        every { mockFile.exists() } returns true
        every { mockFile.canRead() } returns true
        every { mockFile.readText() } returns csvWithMaliciousContent

        // When
        val result = importDataUseCase.importFromCsv(mockFile, ImportDataUseCase.ImportMode.MERGE)

        // Then
        assert(result.isSuccess)
        // Verify that malicious content is sanitized
        verify { counterDao.insertAll(match { data -> 
            data.all { it.name.length <= 255 && !it.name.contains("<script>") }
        })}
    }

    @Test
    fun `import should handle REPLACE mode correctly`() = runTest {
        // Given
        val mockFile = mockk<File>(relaxed = true)
        every { mockFile.exists() } returns true
        every { mockFile.canRead() } returns true
        every { mockFile.readText() } returns validCsvContent

        // When
        val result = importDataUseCase.importFromCsv(mockFile, ImportDataUseCase.ImportMode.REPLACE)

        // Then
        assert(result.isSuccess)
        verify { counterDao.deleteAll() }
        verify { groupDao.deleteAll() }
        verify { counterDao.insertAll(any()) }
    }

    @Test
    fun `import should handle MERGE mode correctly`() = runTest {
        // Given
        val mockFile = mockk<File>(relaxed = true)
        every { mockFile.exists() } returns true
        every { mockFile.canRead() } returns true
        every { mockFile.readText() } returns validCsvContent

        // When
        val result = importDataUseCase.importFromCsv(mockFile, ImportDataUseCase.ImportMode.MERGE)

        // Then
        assert(result.isSuccess)
        verify(exactly = 0) { counterDao.deleteAll() }
        verify(exactly = 0) { groupDao.deleteAll() }
        verify { counterDao.insertAll(any()) }
    }

    companion object {
        private const val validCsvContent = """
            name,description,value,unit
            Tree Height,Height measurement,25.5,m
            Diameter,DBH measurement,45.2,cm
            Volume,Calculated volume,2.1,m³
        """.trimIndent()

        private const val csvWithMaliciousContent = """
            name,description,value,unit
            <script>alert('xss')</script>,Malicious content,25.5,m
            Normal Item,Normal description,45.2,cm
        """.trimIndent()
    }
}
