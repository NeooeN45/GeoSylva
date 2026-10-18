package com.forestry.counter.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests UI pour MartelageScreen - Couverture des interactions de martelage.
 * Vérifie l'accessibilité et la fonctionnalité de l'écran de martelage.
 */
@RunWith(AndroidJUnit4::class)
class MartelageScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun martelageScreen_should_display_tree_data_entry_form() {
        // Given
        composeTestRule.setContent {
            // MartelageScreen content
        }

        // Then
        composeTestRule
            .onNodeWithText("Martelage")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Essence")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Diamètre")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Hauteur")
            .assertIsDisplayed()
    }

    @Test
    fun martelageScreen_should_validate_tree_diameter_input() {
        // Given
        composeTestRule.setContent {
            // MartelageScreen with form
        }

        // When
        composeTestRule
            .onNodeWithText("Diamètre")
            .performTextInput("150")

        composeTestRule
            .onNodeWithText("Valider")
            .performClick()

        // Then
        composeTestRule
            .onNodeWithText("Le diamètre ne peut pas dépasser 100 cm")
            .assertIsDisplayed()
    }

    @Test
    fun martelageScreen_should_calculate_tree_volume_automatically() {
        // Given
        composeTestRule.setContent {
            // MartelageScreen with calculation
        }

        // When
        composeTestRule
            .onNodeWithText("Diamètre")
            .performTextInput("45")

        composeTestRule
            .onNodeWithText("Hauteur")
            .performTextInput("25")

        // Then
        composeTestRule
            .onNodeWithText("Volume: 2.1 m³")
            .assertIsDisplayed()
    }

    @Test
    fun martelageScreen_should_be_accessible_with_screen_readers() {
        // Given
        composeTestRule.setContent {
            // Accessible MartelageScreen
        }

        // Then
        composeTestRule
            .onNodeWithContentDescription("Formulaire de saisie de martelage forestier")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription("Champ de saisie pour l'essence de l'arbre")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription("Champ de saisie pour le diamètre en centimètres")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription("Bouton de validation des données de martelage")
            .assertIsDisplayed()
    }

    @Test
    fun martelageScreen_should_handle_tree_species_selection() {
        // Given
        composeTestRule.setContent {
            // MartelageScreen with species selector
        }

        // When
        composeTestRule
            .onNodeWithText("Essence")
            .performClick()

        composeTestRule
            .onNodeWithText("Chêne")
            .performClick()

        // Then
        composeTestRule
            .onNodeWithText("Chêne")
            .assertIsDisplayed()
    }

    @Test
    fun martelageScreen_should_save_data_automatically() {
        // Given
        composeTestRule.setContent {
            // MartelageScreen with auto-save
        }

        // When
        composeTestRule
            .onNodeWithText("Diamètre")
            .performTextInput("45")

        composeTestRule
            .onNodeWithText("Hauteur")
            .performTextInput("25")

        // Wait for auto-save
        composeTestRule.waitForIdle()

        // Then
        composeTestRule
            .onNodeWithText("Données sauvegardées")
            .assertIsDisplayed()
    }

    @Test
    fun martelageScreen_should_show_error_for_invalid_height() {
        // Given
        composeTestRule.setContent {
            // MartelageScreen with validation
        }

        // When
        composeTestRule
            .onNodeWithText("Hauteur")
            .performTextInput("-5")

        composeTestRule
            .onNodeWithText("Valider")
            .performClick()

        // Then
        composeTestRule
            .onNodeWithText("La hauteur doit être positive")
            .assertIsDisplayed()
    }

    @Test
    fun martelageScreen_should_support_keyboard_navigation() {
        // Given
        composeTestRule.setContent {
            // Accessible MartelageScreen
        }

        // When - Navigate through form fields
        composeTestRule
            .onNodeWithText("Essence")
            .performSemanticsAction(SemanticsActions.RequestFocus)

        composeTestRule
            .onNodeWithText("Diamètre")
            .performSemanticsAction(SemanticsActions.RequestFocus)

        composeTestRule
            .onNodeWithText("Hauteur")
            .performSemanticsAction(SemanticsActions.RequestFocus)

        // Then
        composeTestRule
            .onNodeWithText("Valider")
            .assertIsDisplayed()
    }

    @Test
    fun martelageScreen_should_display_tree_statistics() {
        // Given
        composeTestRule.setContent {
            // MartelageScreen with statistics
        }

        // When
        composeTestRule
            .onNodeWithText("Statistiques")
            .performClick()

        // Then
        composeTestRule
            .onNodeWithText("Total arbres: 15")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Volume moyen: 1.8 m³")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Diamètre moyen: 42 cm")
            .assertIsDisplayed()
    }

    @Test
    fun martelageScreen_should_handle_data_export() {
        // Given
        composeTestRule.setContent {
            // MartelageScreen with export
        }

        // When
        composeTestRule
            .onNodeWithText("Exporter")
            .performClick()

        composeTestRule
            .onNodeWithText("CSV")
            .performClick()

        // Then
        composeTestRule
            .onNodeWithText("Exportation réussie")
            .assertIsDisplayed()
    }
}
