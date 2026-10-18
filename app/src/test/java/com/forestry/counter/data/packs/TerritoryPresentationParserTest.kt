package com.forestry.counter.data.packs

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Métadonnées inventées exclusivement pour tester les refus ; aucun média produit. */
class TerritoryPresentationParserTest {
    private val source = TerritoryPresentationSource("https://example.test/source", "Source de test", "Éditeur de test", "2026-10-02T12:00:00Z")
    private val photo = TerritoryImage("test/photo.jpg", "Photo de test", "Auteur de test", "CC-BY-4.0",
        "https://creativecommons.org/licenses/by/4.0/", "https://example.test/photo", "revision-test", "Auteur de test · CC BY 4.0", "a".repeat(64), 123, 320, 240)
    private val presentation = TerritoryPresentation("department:16", "Charente", "Description de test exclusivement.", emptyList(), listOf(photo), listOf(source),
        TerritoryEmblem("territory_outline", "IGN ADMIN EXPRESS COG CARTO PE 2026", "etalab-2.0", "Silhouette originale de test"))

    private fun bytes(territories: List<TerritoryPresentation>, rights: TerritoryMediaRightsQualification? = null): ByteArray = buildJsonObject {
        put("schema_version", 1)
        put("territories", JsonArray(territories.map { Json.encodeToJsonElement(TerritoryPresentation.serializer(), it) }))
        rights?.let { put("rights_qualification", Json.encodeToJsonElement(TerritoryMediaRightsQualification.serializer(), it)) }
    }.toString().toByteArray(Charsets.UTF_8)
    private fun rejects(value: ByteArray) { assertTrue(runCatching { TerritoryPresentationParser.parse(value) }.isFailure) }

    @Test fun should_accept_local_commercial_photo_metadata_separate_from_pack_contract() {
        val parsed = TerritoryPresentationParser.parse(bytes(listOf(presentation)))
        assertEquals(presentation, parsed.territories.getValue("department:16"))
        assertNull(parsed.rightsQualification)
    }
    @Test fun should_reject_traversal_absolute_encoded_and_remote_image_paths() {
        for (path in listOf("../photo.jpg", "/photo.jpg", "x/../photo.jpg", "x\\photo.jpg", "%2e%2e/photo.jpg", "https://example.test/photo.jpg")) {
            rejects(bytes(listOf(presentation.copy(images = listOf(photo.copy(assetPath = path))))))
        }
    }
    @Test fun should_reject_noncommercial_or_unqualified_photo_licence() {
        for (licence in listOf("CC-BY-NC-4.0", "all-rights-reserved", "unknown", "etalab-2.0")) {
            rejects(bytes(listOf(presentation.copy(images = listOf(photo.copy(licenseSpdx = licence))))))
        }
    }
    @Test fun should_reject_official_logo_without_qualified_permission() {
        rejects(bytes(listOf(presentation.copy(emblem = presentation.emblem.copy(kind = "official_logo")))))
    }
    @Test fun should_reject_duplicate_or_nonexistent_territories() {
        rejects(bytes(listOf(presentation, presentation)))
        rejects(bytes(listOf(presentation.copy(territoryId = "region:99"))))
        rejects(bytes(listOf(presentation.copy(territoryId = "department:20"))))
    }
    @Test fun should_reject_missing_photo_credit_and_unsafe_source_url() {
        rejects(bytes(listOf(presentation.copy(images = listOf(photo.copy(author = ""))))))
        rejects(bytes(listOf(presentation.copy(images = listOf(photo.copy(sourceUrl = "http://example.test/photo"))))))
        rejects(bytes(listOf(presentation.copy(sources = listOf(source.copy(retrievedUtc = "2026-10-02T12:00:00+02:00"))))))
    }
    @Test fun should_reject_aerial_view_without_explicit_context_or_redistribution_proof() {
        val aerial = photo.copy(type = "aerial_view", licenseSpdx = "etalab-2.0")
        rejects(bytes(listOf(presentation.copy(images = listOf(aerial)))))
        rejects(bytes(listOf(presentation.copy(images = listOf(aerial))), rights()))
    }
    @Test fun should_accept_qualified_aerial_metadata_without_claiming_capture_date() {
        val aerial = photo.copy(type = "aerial_view", licenseSpdx = "etalab-2.0", caption = "Vue aérienne de test d’un secteur.",
            coordinates = listOf(0.2, 45.6), zoom = 15, tileSourceUrl = "https://example.test/aerial", catalogueDateUtc = "2026-10-02T12:00:00Z")
        val parsed = TerritoryPresentationParser.parse(bytes(listOf(presentation.copy(images = listOf(aerial))), rights()))
        assertNull(parsed.territories.getValue("department:16").images.single().captureDate)
        rejects(bytes(listOf(presentation.copy(images = listOf(aerial))), rights().copy(redistributionApk = false)))
    }
    @Test fun should_reject_duplicate_json_keys_before_deserializing() {
        rejects("""{"schema_version":1,"schema_version":1,"territories":[]}""".toByteArray())
    }
    @Test fun should_reject_duplicate_highlight_names_even_with_different_summaries() {
        val highlight = TerritoryHighlight("Repère de test", "forest", "Résumé de test", listOf(source.url))
        rejects(bytes(listOf(presentation.copy(highlights = listOf(highlight, highlight.copy(summary = "Autre résumé de test"))))))
    }
    @Test fun should_reject_duplicate_presentation_source_urls_even_with_different_titles() {
        rejects(bytes(listOf(presentation.copy(sources = listOf(source, source.copy(title = "Autre titre de test"))))))
    }
    @Test fun should_reject_duplicate_rights_source_urls_even_with_different_publishers() {
        rejects(bytes(listOf(presentation), rights().copy(sources = listOf(source, source.copy(publisher = "Autre éditeur de test")))))
    }
    @Test fun should_reject_undeclared_or_repeated_highlight_references() {
        val highlight = TerritoryHighlight("Repère de test", "forest", "Résumé de test", listOf("https://example.test/undeclared"))
        rejects(bytes(listOf(presentation.copy(highlights = listOf(highlight)))))
        rejects(bytes(listOf(presentation.copy(highlights = listOf(highlight.copy(sources = listOf(source.url, source.url)))))))
    }
    @Test fun should_accept_one_declared_source_shared_by_distinct_highlights_and_rights() {
        val first = TerritoryHighlight("Premier repère de test", "forest", "Premier résumé de test", listOf(source.url))
        val second = first.copy(name = "Second repère de test", summary = "Second résumé de test")
        val expected = presentation.copy(highlights = listOf(first, second))
        val parsed = TerritoryPresentationParser.parse(bytes(listOf(expected), rights()))
        assertEquals(expected, parsed.territories.getValue("department:16"))
        assertEquals(source, parsed.rightsQualification?.sources?.single())
    }
    private fun rights() = TerritoryMediaRightsQualification("etalab-2.0", "2026-10-02T12:00:00Z", listOf(source), true, true, true, true, "Qualification de test uniquement.")
}
