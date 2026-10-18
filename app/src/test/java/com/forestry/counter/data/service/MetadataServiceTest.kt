package com.forestry.counter.data.service

import com.forestry.counter.domain.model.Foret
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Tige
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests du [MetadataService] — contrat GeoSylva 3.0 §3.1.
 * Vérifie le renseignement automatique des metadata à l'écriture.
 */
class MetadataServiceTest {

    @Test fun current_account_is_a_pure_identity_read_without_anonymous_fallback() {
        var account: String? = null
        val service = MetadataService { account }
        assertNull(service.currentAccountId())
        account = "account-uuid-123"
        assertEquals("account-uuid-123", service.currentAccountId())
        account = null
        assertNull(service.currentAccountId())
    }

    // ── Arrange : fabriques de modèles domaine minimaux ──────────────────────

    private fun foret(): Foret = Foret(
        foretId = "foret-1",
        nom = "Forêt de test",
        proprietaireNom = "Dupont",
        proprietaireEmail = null,
        gestionnaireNom = null,
        typeForet = null,
        objectifGestion = null,
        psgNumero = null,
        psgDateExpiration = null,
        departement = null,
        remarques = null,
    )

    private fun parcelle(): Parcelle = Parcelle(
        id = "parcelle-1",
        forestId = null,
        foretId = null,
        name = "Parcelle A",
        surfaceHa = 1.5,
        shape = null,
        slopePct = null,
        aspect = null,
        access = null,
        altitudeM = null,
        objectifType = null,
        objectifVal = null,
        tolerancePct = null,
        samplingMode = null,
        sampleAreaM2 = null,
        targetSpeciesCsv = null,
        srid = null,
        remarks = null,
    )

    private fun tige(): Tige = Tige(
        id = "tige-1",
        parcelleId = "parcelle-1",
        placetteId = null,
        essenceCode = "CHENE",
        diamCm = 30.0,
        hauteurM = 15.0,
        gpsWkt = null,
        precisionM = null,
        altitudeM = null,
        note = null,
        produit = null,
        fCoef = null,
        valueEur = null,
    )

    // ── enrichForCreate ──────────────────────────────────────────────────────

    @Test
    fun should_set_auteur_source_and_version_when_enrichForCreate_with_authenticated_account() {
        val service = MetadataService(accountProvider = { "account-uuid-123" })

        val result = service.enrichForCreate(foret(), source = MetadataService.Source.MANUAL)

        assertEquals("account-uuid-123", result.auteur)
        assertEquals("manual", result.source)
        assertEquals(1, result.version)
    }

    @Test
    fun should_set_anonymous_auteur_when_accountProvider_returns_null() {
        val service = MetadataService(accountProvider = { null })

        val result = service.enrichForCreate(parcelle())

        assertEquals("anonymous", result.auteur)
        assertEquals("manual", result.source)
        assertEquals(1, result.version)
    }

    @Test
    fun should_set_import_source_when_enrichForCreate_with_import_source() {
        val service = MetadataService(accountProvider = { "account-uuid-123" })

        val result = service.enrichForCreate(tige(), source = MetadataService.Source.IMPORT)

        assertEquals("import", result.source)
    }

    @Test
    fun should_enrich_for_create_on_foret_model() {
        val service = MetadataService(accountProvider = { "account-uuid-123" })

        val result = service.enrichForCreate(foret())

        assertEquals("account-uuid-123", result.auteur)
        assertEquals("manual", result.source)
        assertEquals(1, result.version)
    }

    @Test
    fun should_enrich_for_create_on_parcelle_model() {
        val service = MetadataService(accountProvider = { "account-uuid-123" })

        val result = service.enrichForCreate(parcelle())

        assertEquals("account-uuid-123", result.auteur)
        assertEquals("manual", result.source)
        assertEquals(1, result.version)
    }

    @Test
    fun should_enrich_for_create_on_tige_model() {
        val service = MetadataService(accountProvider = { "account-uuid-123" })

        val result = service.enrichForCreate(tige())

        assertEquals("account-uuid-123", result.auteur)
        assertEquals("manual", result.source)
        assertEquals(1, result.version)
    }

    // ── enrichForUpdate ─────────────────────────────────────────────────────

    @Test
    fun should_increment_version_when_enrichForUpdate() {
        val service = MetadataService(accountProvider = { "account-uuid-123" })

        val result = service.enrichForUpdate(foret(), baseVersion = 3)

        assertEquals(4, result.version)
        assertEquals("account-uuid-123", result.auteur)
    }

    @Test
    fun should_set_anonymous_auteur_on_update_when_accountProvider_returns_null() {
        val service = MetadataService(accountProvider = { null })

        val result = service.enrichForUpdate(parcelle(), baseVersion = 1)

        assertEquals("anonymous", result.auteur)
        assertEquals(2, result.version)
    }

    @Test
    fun should_preserve_existing_source_when_enrichForUpdate() {
        val service = MetadataService(accountProvider = { "account-uuid-123" })
        val existing = tige().copy(source = "gps", version = 2)

        val result = service.enrichForUpdate(existing, baseVersion = 2)

        assertEquals("gps", result.source)
        assertEquals(3, result.version)
    }

    @Test
    fun should_default_source_to_manual_when_enrichForUpdate_on_null_source() {
        val service = MetadataService(accountProvider = { "account-uuid-123" })

        val result = service.enrichForUpdate(tige(), baseVersion = 1)

        assertEquals("manual", result.source)
    }

    // ── Preservation des autres champs ───────────────────────────────────────

    @Test
    fun should_preserve_deletedAt_when_enrichForCreate() {
        val service = MetadataService(accountProvider = { "account-uuid-123" })
        val withDeletedAt = foret().copy(deletedAt = 1700000000L)

        val result = service.enrichForCreate(withDeletedAt)

        assertEquals(1700000000L, result.deletedAt)
    }

    @Test
    fun should_preserve_business_fields_when_enrichForCreate_on_parcelle() {
        val service = MetadataService(accountProvider = { "account-uuid-123" })

        val result = service.enrichForCreate(parcelle())

        assertEquals("parcelle-1", result.id)
        assertEquals("Parcelle A", result.name)
        assertEquals(1.5, result.surfaceHa ?: -1.0, 0.001)
    }

    @Test
    fun should_not_mutate_original_instance() {
        val service = MetadataService(accountProvider = { "account-uuid-123" })
        val original = foret()

        service.enrichForCreate(original)

        assertNull(original.auteur)
        assertNull(original.source)
        assertEquals(1, original.version)
    }
}
