package com.forestry.counter.domain.calculation.cubage

/**
 * Une méthode de cubage versionnée (pattern Strategy). Additive par construction :
 * une nouvelle méthode = une nouvelle implémentation, jamais une modification
 * d'une méthode existante déjà en production (CLAUDE.md GeoSylva §2/§4).
 *
 * @param TInput famille d'entrée acceptée — [StandingTreeMeasurement] ou [LogSegmentMeasurement].
 */
interface CubageMethod<in TInput : CubageInput> {
    /** Identifiant stable (ex: "HUBER", "SCHAEFFER_1E"). */
    val id: String

    /** Version sémantique de l'implémentation (ex: "1.0.0"). */
    val version: String

    val qualification: MethodQualification

    /** Référence de la source scientifique/documentaire (jamais un coefficient inventé). */
    val sourceCitation: String

    fun compute(input: TInput): CubageResult
}
