package com.forestry.counter.domain.model

/** Documents juridiques nécessaires à la création d'une identité locale. */
data class AccountConsentRequirement(
    val type: String,
    val documentVersion: String,
)

/** Source unique des types et versions envoyés par GeoSylva à Quintessences. */
object AccountConsentPolicy {
    const val TERMS = "terms"
    const val PRIVACY = "privacy"

    val required: List<AccountConsentRequirement> = listOf(
        AccountConsentRequirement(TERMS, "v1"),
        AccountConsentRequirement(PRIVACY, "v1"),
    )

    fun versionFor(type: String): String? =
        required.firstOrNull { it.type == type }?.documentVersion
}
