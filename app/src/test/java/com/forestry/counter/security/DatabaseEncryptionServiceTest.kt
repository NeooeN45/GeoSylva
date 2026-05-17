package com.forestry.counter.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Before
import org.junit.Test

/**
 * Tests pour DatabaseEncryptionService - Couverture des fonctionnalités de chiffrement.
 * Vérifie la sécurité du chiffrement de base de données.
 */
class DatabaseEncryptionServiceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        mockkStatic(EncryptedSharedPreferences::class)
    }

    @Test
    fun `createEncryptedDatabaseFactory should return valid SupportFactory`() {
        // When
        val factory = DatabaseEncryptionService.createEncryptedDatabaseFactory(context)

        // Then
        assert(factory != null)
        // SupportFactory should be created with valid passphrase
    }

    @Test
    fun `isDatabaseEncrypted should return false when no key exists`() {
        // Given
        val mockEncryptedPrefs = mockk<EncryptedSharedPreferences>(relaxed = true)
        every { mockEncryptedPrefs.contains("encrypted_db_key") } returns false
        
        mockkStatic(EncryptedSharedPreferences::class)
        every { 
            EncryptedSharedPreferences.create(any(), any(), any(), any(), any()) 
        } returns mockEncryptedPrefs

        // When
        val result = DatabaseEncryptionService.isDatabaseEncrypted(context)

        // Then
        assert(!result)
    }

    @Test
    fun `isDatabaseEncrypted should return true when key exists`() {
        // Given
        val mockEncryptedPrefs = mockk<EncryptedSharedPreferences>(relaxed = true)
        every { mockEncryptedPrefs.contains("encrypted_db_key") } returns true
        
        mockkStatic(EncryptedSharedPreferences::class)
        every { 
            EncryptedSharedPreferences.create(any(), any(), any(), any(), any()) 
        } returns mockEncryptedPrefs

        // When
        val result = DatabaseEncryptionService.isDatabaseEncrypted(context)

        // Then
        assert(result)
    }

    @Test
    fun `rotateDatabaseKey should return true on success`() {
        // Given
        val mockEncryptedPrefs = mockk<EncryptedSharedPreferences>(relaxed = true)
        val mockEditor = mockk<android.content.SharedPreferences.Editor>(relaxed = true)
        every { mockEncryptedPrefs.edit() } returns mockEditor
        every { mockEditor.remove("encrypted_db_key") } returns mockEditor
        every { mockEditor.apply() } returns Unit
        
        mockkStatic(EncryptedSharedPreferences::class)
        every { 
            EncryptedSharedPreferences.create(any(), any(), any(), any(), any()) 
        } returns mockEncryptedPrefs

        // When
        val result = DatabaseEncryptionService.rotateDatabaseKey(context)

        // Then
        assert(result)
        verify { mockEditor.remove("encrypted_db_key") }
        verify { mockEditor.apply() }
    }

    @Test
    fun `rotateDatabaseKey should return false on failure`() {
        // Given
        mockkStatic(EncryptedSharedPreferences::class)
        every { 
            EncryptedSharedPreferences.create(any(), any(), any(), any(), any()) 
        } throws RuntimeException("Encryption error")

        // When
        val result = DatabaseEncryptionService.rotateDatabaseKey(context)

        // Then
        assert(!result)
    }

    @Test
    fun `getEncryptionStats should return valid stats`() {
        // Given
        val mockEncryptedPrefs = mockk<EncryptedSharedPreferences>(relaxed = true)
        every { mockEncryptedPrefs.contains("encrypted_db_key") } returns true
        
        mockkStatic(EncryptedSharedPreferences::class)
        every { 
            EncryptedSharedPreferences.create(any(), any(), any(), any(), any()) 
        } returns mockEncryptedPrefs

        // When
        val stats = DatabaseEncryptionService.getEncryptionStats(context)

        // Then
        assert(stats.isEncrypted)
        assert(stats.keyAlias == "GeoSylva_DB_Master_Key")
        assert(stats.keyStoreProvider == "AndroidKeyStore")
        assert(stats.algorithm == "AES-256-GCM")
        assert(stats.sqlCipherVersion.isNotEmpty())
    }

    @Test
    fun `getEncryptionStats should handle unencrypted database`() {
        // Given
        val mockEncryptedPrefs = mockk<EncryptedSharedPreferences>(relaxed = true)
        every { mockEncryptedPrefs.contains("encrypted_db_key") } returns false
        
        mockkStatic(EncryptedSharedPreferences::class)
        every { 
            EncryptedSharedPreferences.create(any(), any(), any(), any(), any()) 
        } returns mockEncryptedPrefs

        // When
        val stats = DatabaseEncryptionService.getEncryptionStats(context)

        // Then
        assert(!stats.isEncrypted)
        assert(stats.keyAlias == "GeoSylva_DB_Master_Key")
    }
}
