package com.forestry.counter.domain.location

import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordedTerritoryResolverTest {
    // Géométries synthétiques réservées aux tests, jamais un contenu de pack produit.
    private val reference = RecordedTerritoryResolver.Reference(
        "test-only:territorial-reference", "test-v1", RecordedTerritoryResolver.Level.DEPARTMENT,
    )

    private fun boundary(id: String, wkt: String) =
        RecordedTerritoryResolver.Boundary(id, ParcelGeometry.fromWkt(wkt))

    private val left = boundary("department:16", "POLYGON ((0 0, 1 0, 1 1, 0 1, 0 0))")
    private val right = boundary("department:17", "POLYGON ((1 0, 2 0, 2 1, 1 1, 1 0))")

    @Test fun `point enregistre conserve axes et provenance explicites`() {
        val point = ParcelCoordinate(0.25, 0.75)
        val result = RecordedTerritoryResolver(reference, listOf(left, right)).resolve(point)
        assertEquals(RecordedTerritoryResolver.Status.RESOLVED, result.status)
        assertEquals("department:16", result.resolvedTerritoryId)
        assertEquals(listOf("department:16"), result.candidateTerritoryIds)
        assertEquals(point, result.recordedPoint)
        assertSame(reference, result.reference)
        assertNull(result.missingReason)
    }

    @Test fun `point absent ne prend ni ancre ni centroide de repli`() {
        val result = RecordedTerritoryResolver(reference, listOf(left)).resolve(null)
        assertEquals(RecordedTerritoryResolver.Status.MISSING, result.status)
        assertEquals(RecordedTerritoryResolver.MissingReason.POINT_NOT_RECORDED, result.missingReason)
        assertNull(result.recordedPoint)
        assertNull(result.resolvedTerritoryId)
        assertTrue(result.candidateTerritoryIds.isEmpty())
    }

    @Test fun `referentiel vide reste distinct du point hors couverture`() {
        val point = ParcelCoordinate(0.5, 0.5)
        val result = RecordedTerritoryResolver(reference, emptyList()).resolve(point)
        assertEquals(RecordedTerritoryResolver.Status.MISSING, result.status)
        assertEquals(RecordedTerritoryResolver.MissingReason.COVERAGE_NOT_AVAILABLE, result.missingReason)
        assertEquals(point, result.recordedPoint)
    }

    @Test fun `point proche mais exterieur ne prend jamais le territoire le plus proche`() {
        val result = RecordedTerritoryResolver(reference, listOf(left)).resolve(ParcelCoordinate(1.00001, 0.5))
        assertEquals(RecordedTerritoryResolver.Status.MISSING, result.status)
        assertEquals(RecordedTerritoryResolver.MissingReason.OUTSIDE_REFERENCE_COVERAGE, result.missingReason)
        assertNull(result.resolvedTerritoryId)
        assertTrue(result.candidateTerritoryIds.isEmpty())
    }

    @Test fun `forme concave exclut un point situe seulement dans la bounding box`() {
        val concave = boundary("department:16", "POLYGON ((0 0, 3 0, 3 1, 1 1, 1 3, 0 3, 0 0))")
        val resolver = RecordedTerritoryResolver(reference, listOf(concave))
        assertEquals(RecordedTerritoryResolver.Status.MISSING, resolver.resolve(ParcelCoordinate(2.0, 2.0)).status)
        assertEquals("department:16", resolver.resolve(ParcelCoordinate(0.5, 2.0)).resolvedTerritoryId)
    }

    @Test fun `trou et sa frontiere sont exclus sans attribution forcee`() {
        val withHole = boundary("department:16", "POLYGON ((0 0, 4 0, 4 4, 0 4, 0 0), (1 1, 2 1, 2 2, 1 2, 1 1))")
        val resolver = RecordedTerritoryResolver(reference, listOf(withHole))
        listOf(ParcelCoordinate(1.5, 1.5), ParcelCoordinate(1.0, 1.5)).forEach { point ->
            assertEquals(RecordedTerritoryResolver.Status.MISSING, resolver.resolve(point).status)
        }
        assertEquals("department:16", resolver.resolve(ParcelCoordinate(3.0, 3.0)).resolvedTerritoryId)
    }

    @Test fun `ile du multipolygone est resolue mais pas son intervalle vide`() {
        val islands = boundary("department:2A", "MULTIPOLYGON (((0 0, 1 0, 1 1, 0 1, 0 0)), ((3 0, 4 0, 4 1, 3 1, 3 0)))")
        val resolver = RecordedTerritoryResolver(reference, listOf(islands))
        assertEquals("department:2A", resolver.resolve(ParcelCoordinate(3.5, 0.5)).resolvedTerritoryId)
        assertEquals(RecordedTerritoryResolver.Status.MISSING, resolver.resolve(ParcelCoordinate(2.0, 0.5)).status)
    }

    @Test fun `frontiere partagee reste ambigue et garde les deux candidats`() {
        val result = RecordedTerritoryResolver(reference, listOf(right, left)).resolve(ParcelCoordinate(1.0, 0.5))
        assertEquals(RecordedTerritoryResolver.Status.AMBIGUOUS, result.status)
        assertEquals(listOf("department:16", "department:17"), result.candidateTerritoryIds)
        assertNull(result.resolvedTerritoryId)
        assertNull(result.missingReason)
    }

    @Test fun `recouvrement ne choisit jamais le premier contour de la liste`() {
        val overlapping = boundary("department:17", "POLYGON ((0.5 0, 1.5 0, 1.5 1, 0.5 1, 0.5 0))")
        val point = ParcelCoordinate(0.75, 0.5)
        val forward = RecordedTerritoryResolver(reference, listOf(left, overlapping)).resolve(point)
        val reverse = RecordedTerritoryResolver(reference, listOf(overlapping, left)).resolve(point)
        assertEquals(RecordedTerritoryResolver.Status.AMBIGUOUS, forward.status)
        assertEquals(listOf("department:16", "department:17"), forward.candidateTerritoryIds)
        assertEquals(forward, reverse)
        assertNull(forward.resolvedTerritoryId)
    }

    @Test fun `liste source modifiee ensuite ne change pas le referentiel construit`() {
        val original = mutableListOf(left)
        val resolver = RecordedTerritoryResolver(reference, original)
        original.clear()
        original += right
        assertEquals("department:16", resolver.resolve(ParcelCoordinate(0.5, 0.5)).resolvedTerritoryId)
        assertEquals(RecordedTerritoryResolver.Status.MISSING, resolver.resolve(ParcelCoordinate(1.5, 0.5)).status)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `identifiants dupliques exigent un choix explicite de referentiel`() {
        RecordedTerritoryResolver(reference, listOf(left, left.copy(geometry = right.geometry)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `niveau region ne peut pas etre confondu avec departement meme code`() {
        val regionReference = reference.copy(level = RecordedTerritoryResolver.Level.REGION)
        RecordedTerritoryResolver(regionReference, listOf(left.copy(territoryId = "department:75")))
    }

    @Test fun `referentiel regional conserve son espace de noms`() {
        val regionReference = reference.copy(level = RecordedTerritoryResolver.Level.REGION)
        val region = left.copy(territoryId = "region:75")
        val result = RecordedTerritoryResolver(regionReference, listOf(region)).resolve(ParcelCoordinate(0.5, 0.5))
        assertEquals("region:75", result.resolvedTerritoryId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `source manquante ne se presente pas comme referentiel qualifie`() {
        reference.copy(sourceId = " ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `version manquante ne se presente pas comme referentiel qualifie`() {
        reference.copy(version = "")
    }

    @Test fun `points en Charente et dans le Gard utilisent les vrais contours IGN conserves`() {
        val asset = sequenceOf(
            File("src/main/assets/packs/departments.geojson"),
            File("app/src/main/assets/packs/departments.geojson"),
        ).firstOrNull(File::isFile) ?: error("Snapshot IGN du produit absent")
        val bytes = asset.readBytes()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals("3f25bf4c7b21d62b49ed6368b50f25ddbc6aa04fb4f62220f12d48e988860687", digest)

        val features = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject.getValue("features").jsonArray
        assertEquals(101, features.size)
        val boundaries = features.map { it.jsonObject }.filter {
            it.getValue("id").jsonPrimitive.content in setOf("department:16", "department:30")
        }.map { feature ->
            val id = feature.getValue("id").jsonPrimitive.content
            assertEquals(id, feature.getValue("properties").jsonObject.getValue("territory_id").jsonPrimitive.content)
            RecordedTerritoryResolver.Boundary(id, ParcelGeometry.fromGeoJson(feature.getValue("geometry").toString()))
        }
        assertEquals(2, boundaries.size)
        val realReference = RecordedTerritoryResolver.Reference(
            "IGN:ADMINEXPRESS-COG-CARTO-PE:departement", "2026", RecordedTerritoryResolver.Level.DEPARTMENT,
        )
        val resolver = RecordedTerritoryResolver(realReference, boundaries)
        // Points de contrôle en ville, pas mesures forestières ni position du téléphone.
        val angouleme = resolver.resolve(ParcelCoordinate(0.1562, 45.6484))
        val nimes = resolver.resolve(ParcelCoordinate(4.3601, 43.8367))
        assertEquals("department:16", angouleme.resolvedTerritoryId)
        assertEquals("department:30", nimes.resolvedTerritoryId)
        assertEquals(realReference, nimes.reference)
        assertEquals("department:16", resolver.resolve(angouleme.recordedPoint).resolvedTerritoryId)
        assertEquals(RecordedTerritoryResolver.Status.MISSING, resolver.resolve(ParcelCoordinate(43.8367, 4.3601)).status)
    }
}
