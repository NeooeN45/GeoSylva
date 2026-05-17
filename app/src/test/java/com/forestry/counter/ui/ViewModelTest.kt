package com.forestry.counter.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.forestry.counter.presentation.viewmodel.MapViewModel
import com.forestry.counter.presentation.viewmodel.MartelageViewModel
import com.forestry.counter.presentation.viewmodel.SettingsViewModel
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests pour les ViewModels - Couverture de la logique de présentation.
 * Vérifie le comportement des ViewModels dans différents scénarios.
 */
@ExperimentalCoroutinesApi
class ViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `MapViewModel should handle location updates`() = runTest {
        // Given
        val viewModel = MapViewModel(mockk())
        val testLocation = android.location.Location("gps").apply {
            latitude = 48.8566
            longitude = 2.3522
        }

        // When
        viewModel.updateLocation(testLocation)

        // Then
        assert(viewModel.currentLocation.value != null)
        assert(viewModel.currentLocation.value?.latitude == 48.8566)
        assert(viewModel.currentLocation.value?.longitude == 2.3522)
    }

    @Test
    fun `MapViewModel should handle GPS permission changes`() = runTest {
        // Given
        val viewModel = MapViewModel(mockk())

        // When
        viewModel.onGPSPermissionGranted(true)

        // Then
        assert(viewModel.isGPSPermissionGranted.value == true)
    }

    @Test
    fun `MapViewModel should handle offline mode`() = runTest {
        // Given
        val viewModel = MapViewModel(mockk())

        // When
        viewModel.setOfflineMode(true)

        // Then
        assert(viewModel.isOfflineMode.value == true)
    }

    @Test
    fun `MartelageViewModel should validate tree data`() = runTest {
        // Given
        val viewModel = MartelageViewModel(mockk())

        // When
        viewModel.updateDiameter("45.5")
        viewModel.updateHeight("25.0")

        // Then
        assert(viewModel.diameter.value == "45.5")
        assert(viewModel.height.value == "25.0")
        assert(viewModel.isDataValid.value == true)
    }

    @Test
    fun `MartelageViewModel should reject invalid diameter`() = runTest {
        // Given
        val viewModel = MartelageViewModel(mockk())

        // When
        viewModel.updateDiameter("150")

        // Then
        assert(viewModel.diameter.value == "150")
        assert(viewModel.isDataValid.value == false)
        assert(viewModel.errorMessage.value?.contains("diamètre") == true)
    }

    @Test
    fun `MartelageViewModel should calculate tree volume`() = runTest {
        // Given
        val viewModel = MartelageViewModel(mockk())
        viewModel.updateDiameter("45")
        viewModel.updateHeight("25")

        // When
        val volume = viewModel.calculateVolume()

        // Then
        assert(volume > 0)
        assert(volume.toString().startsWith("2.1")) // Volume attendu ~2.1 m³
    }

    @Test
    fun `MartelageViewModel should save data automatically`() = runTest {
        // Given
        val viewModel = MartelageViewModel(mockk())
        viewModel.updateDiameter("45")
        viewModel.updateHeight("25")

        // When
        viewModel.saveData()

        // Then
        assert(viewModel.saveStatus.value == MartelageViewModel.SaveStatus.SUCCESS)
    }

    @Test
    fun `SettingsViewModel should handle theme changes`() = runTest {
        // Given
        val viewModel = SettingsViewModel(mockk())

        // When
        viewModel.setTheme("dark")

        // Then
        assert(viewModel.currentTheme.value == "dark")
    }

    @Test
    fun `SettingsViewModel should handle language changes`() = runTest {
        // Given
        val viewModel = SettingsViewModel(mockk())

        // When
        viewModel.setLanguage("fr")

        // Then
        assert(viewModel.currentLanguage.value == "fr")
    }

    @Test
    fun `SettingsViewModel should validate PIN code`() = runTest {
        // Given
        val viewModel = SettingsViewModel(mockk())

        // When
        val result = viewModel.validatePIN("1234")

        // Then
        assert(result == SettingsViewModel.PINValidationResult.TOO_SIMPLE)
    }

    @Test
    fun `SettingsViewModel should accept strong PIN`() = runTest {
        // Given
        val viewModel = SettingsViewModel(mockk())

        // When
        val result = viewModel.validatePIN("2580")

        // Then
        assert(result == SettingsViewModel.PINValidationResult.VALID)
    }

    @Test
    fun `SettingsViewModel should handle data export`() = runTest {
        // Given
        val viewModel = SettingsViewModel(mockk())

        // When
        viewModel.exportData("csv")

        // Then
        assert(viewModel.exportStatus.value == SettingsViewModel.ExportStatus.SUCCESS)
    }

    @Test
    fun `SettingsViewModel should handle data import`() = runTest {
        // Given
        val viewModel = SettingsViewModel(mockk())

        // When
        viewModel.importData("test_file.csv")

        // Then
        assert(viewModel.importStatus.value == SettingsViewModel.ImportStatus.SUCCESS)
    }

    @Test
    fun `ViewModels should handle errors gracefully`() = runTest {
        // Given
        val viewModel = MapViewModel(mockk())

        // When - Simulation d'erreur
        viewModel.handleError("Test error")

        // Then
        assert(viewModel.errorMessage.value == "Test error")
    }

    @Test
    fun `ViewModels should clear errors after display`() = runTest {
        // Given
        val viewModel = MartelageViewModel(mockk())
        viewModel.handleError("Test error")

        // When
        viewModel.clearError()

        // Then
        assert(viewModel.errorMessage.value == null)
    }

    @Test
    fun `ViewModels should handle loading states`() = runTest {
        // Given
        val viewModel = SettingsViewModel(mockk())

        // When
        viewModel.setLoading(true)

        // Then
        assert(viewModel.isLoading.value == true)

        // When
        viewModel.setLoading(false)

        // Then
        assert(viewModel.isLoading.value == false)
    }
}
