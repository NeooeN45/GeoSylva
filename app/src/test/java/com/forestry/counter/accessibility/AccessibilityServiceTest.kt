package com.forestry.counter.accessibility

import android.content.Context
import android.view.accessibility.AccessibilityManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.Before
import org.junit.Test

/**
 * Tests pour AccessibilityService - Couverture des fonctionnalités WCAG 2.1.
 * Vérifie la conformité aux normes d'accessibilité.
 */
class AccessibilityServiceTest {

    private lateinit var context: Context
    private lateinit var accessibilityManager: AccessibilityManager

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        accessibilityManager = mockk(relaxed = true)
        every { context.getSystemService(Context.ACCESSIBILITY_SERVICE) } returns accessibilityManager
    }

    @Test
    fun `isAccessibilityEnabled should return true when accessibility is enabled`() {
        // Given
        every { accessibilityManager.isEnabled } returns true

        // When
        val result = AccessibilityService.isAccessibilityEnabled(context)

        // Then
        assert(result)
    }

    @Test
    fun `isAccessibilityEnabled should return false when accessibility is disabled`() {
        // Given
        every { accessibilityManager.isEnabled } returns false

        // When
        val result = AccessibilityService.isAccessibilityEnabled(context)

        // Then
        assert(!result)
    }

    @Test
    fun `getPreferredFontSize should return SMALL for small font scale`() {
        // Given
        val configuration = mockk<android.content.res.Configuration>()
        configuration.fontScale = 0.8f
        every { context.resources.configuration } returns configuration

        // When
        val result = AccessibilityService.getPreferredFontSize(context)

        // Then
        assert(result == FontSize.SMALL)
    }

    @Test
    fun `getPreferredFontSize should return NORMAL for normal font scale`() {
        // Given
        val configuration = mockk<android.content.res.Configuration>()
        configuration.fontScale = 1.0f
        every { context.resources.configuration } returns configuration

        // When
        val result = AccessibilityService.getPreferredFontSize(context)

        // Then
        assert(result == FontSize.NORMAL)
    }

    @Test
    fun `getPreferredFontSize should return LARGE for large font scale`() {
        // Given
        val configuration = mockk<android.content.res.Configuration>()
        configuration.fontScale = 1.2f
        every { context.resources.configuration } returns configuration

        // When
        val result = AccessibilityService.getPreferredFontSize(context)

        // Then
        assert(result == FontSize.LARGE)
    }

    @Test
    fun `getPreferredFontSize should return EXTRA_LARGE for extra large font scale`() {
        // Given
        val configuration = mockk<android.content.res.Configuration>()
        configuration.fontScale = 1.25f
        every { context.resources.configuration } returns configuration

        // When
        val result = AccessibilityService.getPreferredFontSize(context)

        // Then
        assert(result == FontSize.EXTRA_LARGE)
    }

    @Test
    fun `getPreferredFontSize should return EXTRA_EXTRA_LARGE for very large font scale`() {
        // Given
        val configuration = mockk<android.content.res.Configuration>()
        configuration.fontScale = 1.4f
        every { context.resources.configuration } returns configuration

        // When
        val result = AccessibilityService.getPreferredFontSize(context)

        // Then
        assert(result == FontSize.EXTRA_EXTRA_LARGE)
    }

    @Test
    fun `isHighContrastEnabled should detect high contrast services`() {
        // Given
        val serviceInfo = mockk<android.accessibility.AccessibilityServiceInfo>()
        every { serviceInfo.resolveInfo.serviceInfo.toString() } returns "com.android.high_contrast"
        
        val services = listOf(serviceInfo)
        every { accessibilityManager.enabledAccessibilityServices } returns services

        // When
        val result = AccessibilityService.isHighContrastEnabled(context)

        // Then
        assert(result)
    }

    @Test
    fun `isHighContrastEnabled should return false when no high contrast service`() {
        // Given
        val serviceInfo = mockk<android.accessibility.AccessibilityServiceInfo>()
        every { serviceInfo.resolveInfo.serviceInfo.toString() } returns "com.normal.service"
        
        val services = listOf(serviceInfo)
        every { accessibilityManager.enabledAccessibilityServices } returns services

        // When
        val result = AccessibilityService.isHighContrastEnabled(context)

        // Then
        assert(!result)
    }
}
