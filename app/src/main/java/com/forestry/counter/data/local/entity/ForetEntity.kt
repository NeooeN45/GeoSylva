package com.forestry.counter.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "forets",
    indices = [
        Index(name = "index_forets_proprietaireNom", value = ["proprietaireNom"]),
        Index(name = "index_forets_uuid", value = ["uuid"], unique = true)
    ]
)
data class ForetEntity(
    @PrimaryKey
    val foretId: String,
    /** UUID normalisé (RFC 4122) pour interop GSIE serveur — backfill asynchrone. */
    val uuid: String? = null,
    val nom: String,
    val proprietaireNom: String,
    /**
     * Email du propriétaire forestier.
     *
     * Finalité RGPD (Art. 5§1.b) : utilisé uniquement pour contacter le
     * propriétaire pour les rapports de gestion. Optionnel — non transmis
     * à aucun tiers. Voir PRIVACY_POLICY.md §1.1.
     */
    val proprietaireEmail: String?,
    val gestionnaireNom: String?,
    val typeForet: String?,
    val objectifGestion: String?,
    val psgNumero: String?,
    val psgDateExpiration: Long?,
    val departement: String?,
    val remarques: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    /** Provenance normalisée (organisme source, licence, précision, statut) — spec §29.13. */
    @Embedded
    val provenance: ProvenanceEmbed = ProvenanceEmbed(null, null, null, null, null),

    // Metadata spec GeoSylva 3.0 (GEOSYLVA-003 §3.1)
    val deletedAt: Long? = null,
    val auteur: String? = null,
    val source: String? = null,
    @ColumnInfo(name = "version", defaultValue = "1")
    val version: Int = 1,
    /** WGS84 2D polygon WKT; null for existing/manual forests without a known boundary. */
    val shape: String? = null,
    /** Official source feature identifier; not a uniqueness constraint on user dossiers. */
    val sourceForestId: String? = null,
    /** Source dataset reference retained separately from mutable local source metadata. */
    val sourceForestDataset: String? = null,
)
