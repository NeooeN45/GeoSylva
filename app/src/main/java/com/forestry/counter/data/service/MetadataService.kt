package com.forestry.counter.data.service

import com.forestry.counter.domain.model.Metadatable

/**
 * Renseigne les champs metadata (auteur, source, version) à l'écriture.
 * Spec GeoSylva 3.0 §3.1 — provenance et traçabilité.
 *
 * S'appuie sur l'interface [Metadatable] : chaque modèle de domaine expose
 * [Metadatable.withMetadata] qui retourne le type concret sans cast ni réflexion.
 *
 * @param accountProvider retourne l'UUID du compte Quintessences courant,
 *        ou null si non authentifié (mode offline sans compte).
 */
class MetadataService(
    private val accountProvider: () -> String?,
) {
    /** Current authenticated account, without inferring permissions from author metadata. */
    fun currentAccountId(): String? = accountProvider()

    /** Source par défaut selon le point d'entrée à l'origine de la donnée. */
    enum class Source(val value: String) {
        MANUAL("manual"),
        IMPORT("import"),
        SYNC("sync"),
        GPS("gps"),
    }

    /**
     * Renseigne auteur + source + version initiale.
     * À appeler avant toute création (insert) en base.
     */
    fun <T : Metadatable<T>> enrichForCreate(item: T, source: Source = Source.MANUAL): T {
        val auteur = accountProvider() ?: ANONYMOUS_AUTHOR
        return item.withMetadata(auteur = auteur, source = source.value, version = VERSION_INITIAL)
    }

    /**
     * Renseigne auteur + incrémente la version à partir de [baseVersion].
     * À appeler avant toute modification (update) en base.
     * La source existante est préservée (repli sur MANUAL si absente).
     */
    fun <T : Metadatable<T>> enrichForUpdate(item: T, baseVersion: Int): T {
        val auteur = accountProvider() ?: ANONYMOUS_AUTHOR
        val preservedSource = item.source ?: Source.MANUAL.value
        return item.withMetadata(auteur = auteur, source = preservedSource, version = baseVersion + VERSION_INCREMENT)
    }

    companion object {
        /** Auteur de repli quand aucun compte n'est authentifié (mode offline). */
        private const val ANONYMOUS_AUTHOR = "anonymous"

        /** Numéro de version posé à la création d'un enregistrement. */
        private const val VERSION_INITIAL = 1

        /** Incrément de version appliqué à chaque modification. */
        private const val VERSION_INCREMENT = 1
    }
}
