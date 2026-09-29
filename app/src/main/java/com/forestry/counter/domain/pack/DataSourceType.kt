package com.forestry.counter.domain.pack

/**
 * Provenance d'une donnée affichée dans l'application.
 *
 * Utilisé pour indiquer à l'utilisateur si une valeur vient d'un pack
 * officiel (ONF, cadastre…), d'une saisie manuelle ou est inconnue —
 * jamais une valeur par défaut déguisée en donnée réelle.
 *
 * Contrat : toute valeur issue d'un pack doit transporter cette enum.
 * Elle alimente les badges visuels de provenance (Hub Martelage §4 spec).
 */
enum class DataSourceType {
    /** Données issues du Pack Global (forêts ONF nationales, cadastre). */
    SOURCE_ONF_PACK,

    /** Données issues d'un Pack Département (ONF local, DDT, tarifs régionaux). */
    SOURCE_DEPT_PACK,

    /** Données issues du parcellaire cadastral national. */
    SOURCE_CADASTRE_PACK,

    /** Valeur saisie manuellement par l'utilisateur. */
    SOURCE_USER_INPUT,

    /** Provenance inconnue ou pack non installé. */
    SOURCE_UNKNOWN,
}
