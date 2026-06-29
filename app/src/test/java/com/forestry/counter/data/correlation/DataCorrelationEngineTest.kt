package com.forestry.counter.data.correlation

import com.forestry.counter.data.local.entity.CorrelationType
import com.forestry.counter.data.local.entity.TigeEntity
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests pour le moteur de corrélation de données.
 * Vérifie la détection des corrélations et l'analyse des relations.
 */
class DataCorrelationEngineTest {
    
    private val correlationEngine = DataCorrelationEngine()
    
    @Test
    fun `analyzeTigeCorrelations should detect diameter-height correlation`() = runTest {
        // Given
        val tiges = createTestTiges()
        val parcelleId = "test_parcelle"
        
        // When
        val correlations = correlationEngine.analyzeTigeCorrelations(tiges, parcelleId)
        
        // Then
        assertTrue(correlations.isNotEmpty())
        
        val diamHeightCorrelation = correlations.find { 
            it.sourceField == "diamCm" && it.targetField == "hauteurM"
        }
        assertNotNull(diamHeightCorrelation)
        assertEquals(CorrelationType.LINEAR, diamHeightCorrelation.correlationType)
        assertTrue(diamHeightCorrelation.correlationStrength > 0.5)
    }
    
    @Test
    fun `analyzeTigeCorrelations should detect spatial correlations`() = runTest {
        // Given
        val tiges = createTestTiges()
        val parcelleId = "test_parcelle"
        
        // When
        val correlations = correlationEngine.analyzeTigeCorrelations(tiges, parcelleId)
        
        // Then
        val spatialCorrelations = correlations.filter { 
            it.correlationType == CorrelationType.SPATIAL
        }
        assertTrue(spatialCorrelations.isNotEmpty())
    }
    
    @Test
    fun `analyzeTigeCorrelations should detect temporal correlations`() = runTest {
        // Given
        val tiges = createTestTigesWithDifferentTimestamps()
        val parcelleId = "test_parcelle"
        
        // When
        val correlations = correlationEngine.analyzeTigeCorrelations(tiges, parcelleId)
        
        // Then
        val temporalCorrelations = correlations.filter { 
            it.correlationType == CorrelationType.TEMPORAL
        }
        assertTrue(temporalCorrelations.isNotEmpty())
    }
    
    @Test
    fun `analyzeTigeCorrelations should handle empty tiges list`() = runTest {
        // Given
        val tiges = emptyList<TigeEntity>()
        val parcelleId = "test_parcelle"
        
        // When
        val correlations = correlationEngine.analyzeTigeCorrelations(tiges, parcelleId)
        
        // Then
        assertEquals(0, correlations.size)
    }
    
    @Test
    fun `analyzeTigeCorrelations should handle insufficient data`() = runTest {
        // Given
        val tiges = listOf(createTestTige(1, 10.0, 15.0))
        val parcelleId = "test_parcelle"
        
        // When
        val correlations = correlationEngine.analyzeTigeCorrelations(tiges, parcelleId)
        
        // Then
        // Moins de corrélations détectées avec peu de données
        assertTrue(correlations.size < 5)
    }
    
    private fun createTestTiges(): List<TigeEntity> {
        return listOf(
            createTestTige(1, 20.0, 18.0),
            createTestTige(2, 25.0, 22.0),
            createTestTige(3, 30.0, 26.0),
            createTestTige(4, 35.0, 30.0),
            createTestTige(5, 40.0, 34.0),
            createTestTige(6, 45.0, 38.0),
            createTestTige(7, 50.0, 42.0),
            createTestTige(8, 55.0, 46.0),
            createTestTige(9, 60.0, 50.0),
            createTestTige(10, 65.0, 54.0),
            createTestTige(11, 70.0, 58.0),
            createTestTige(12, 75.0, 62.0)
        )
    }
    
    private fun createTestTigesWithDifferentTimestamps(): List<TigeEntity> {
        val baseTime = System.currentTimeMillis()
        return listOf(
            createTestTige(1, 20.0, 18.0, baseTime - 86400000 * 30), // 30 jours ago
            createTestTige(2, 25.0, 22.0, baseTime - 86400000 * 20), // 20 jours ago
            createTestTige(3, 30.0, 26.0, baseTime - 86400000 * 10), // 10 jours ago
            createTestTige(4, 35.0, 30.0, baseTime - 86400000 * 5),  // 5 jours ago
            createTestTige(5, 40.0, 34.0, baseTime)                   // aujourd'hui
        )
    }
    
    private fun createTestTige(
        id: Int,
        diameter: Double,
        height: Double,
        timestamp: Long = System.currentTimeMillis()
    ): TigeEntity {
        return TigeEntity(
            tigeId = "tige_$id",
            parcelleOwnerId = "test_parcelle",
            placetteOwnerId = "placette_1",
            essenceCode = "CHENE",
            diamCm = diameter,
            hauteurM = height,
            gpsWkt = null,
            precisionM = null,
            altitudeM = null,
            timestamp = timestamp,
            note = null,
            produit = null,
            fCoef = null,
            valueEur = null,
            numero = id,
            categorie = null,
            qualite = 3,
            defauts = null,
            photoUri = null,
            qualiteDetail = null,
            sessionId = null,
            classeKraft = null,
            etatSanitaire = null,
            vigueur = null,
            origine = null,
            typeCoupe = null,
            biomasseFusTonnes = null,
            carboneFusTonnes = null,
            coefficientElancement = null,
            houppierM = null,
            houppierPct = null,
            isTigeHabitat = false
        )
    }
}
