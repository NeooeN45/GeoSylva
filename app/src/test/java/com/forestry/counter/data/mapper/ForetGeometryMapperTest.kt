package com.forestry.counter.data.mapper

import com.forestry.counter.data.local.entity.ForetEntity
import com.forestry.counter.domain.model.Foret
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForetGeometryMapperTest {
    @Test fun `selected forest geometry and source identity survive both mapping directions`() {
        val forest = manualForest().copy(shape = SHAPE, sourceForestId = "OFFICIAL-TEST-123", sourceForestDataset = "TEST-source-dataset")
        val entity = forest.toEntity()
        assertEquals(SHAPE, entity.shape)
        assertEquals("OFFICIAL-TEST-123", entity.sourceForestId)
        assertEquals("TEST-source-dataset", entity.sourceForestDataset)
        assertEquals(forest, entity.toDomain())
    }

    @Test fun `legacy manual forest is not assigned inferred source or geometry`() {
        val forest = manualForest()
        val entity = forest.toEntity()
        assertNull(entity.shape)
        assertNull(entity.sourceForestId)
        assertNull(entity.sourceForestDataset)
        assertEquals(forest, entity.toDomain())
    }

    @Test fun `metadata enrichment does not overwrite official source identity`() {
        val entity = ForetEntity(
            foretId = "TEST-user-dossier", nom = "TEST forest", proprietaireNom = "TEST",
            proprietaireEmail = null, gestionnaireNom = null, typeForet = null, objectifGestion = null,
            psgNumero = null, psgDateExpiration = null, departement = null, remarques = null,
            createdAt = 1L, updatedAt = 2L, shape = SHAPE,
            sourceForestId = "OFFICIAL-TEST-123", sourceForestDataset = "TEST-source-dataset",
        )
        val updated = entity.toDomain().withMetadata("TEST-user", "manual", 2).toEntity()
        assertEquals("manual", updated.source)
        assertEquals(SHAPE, updated.shape)
        assertEquals(entity.sourceForestId, updated.sourceForestId)
        assertEquals(entity.sourceForestDataset, updated.sourceForestDataset)
        assertEquals(entity.foretId, updated.foretId)
    }

    private fun manualForest() = Foret(
        foretId = "TEST-user-dossier", nom = "TEST forest", proprietaireNom = "TEST",
        proprietaireEmail = null, gestionnaireNom = null, typeForet = null, objectifGestion = null,
        psgNumero = null, psgDateExpiration = null, departement = null, remarques = null,
        createdAt = 1L, updatedAt = 2L,
    )

    private companion object {
        const val SHAPE = "POLYGON ((2 46, 2.01 46, 2.01 46.01, 2 46.01, 2 46))"
    }
}
