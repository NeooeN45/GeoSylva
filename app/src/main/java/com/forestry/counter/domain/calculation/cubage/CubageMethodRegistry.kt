package com.forestry.counter.domain.calculation.cubage

/**
 * Registre en mémoire des méthodes de cubage V2 disponibles. Surface prête,
 * non branchée dans le flux Martelage actuel (branchement = Phase 3 du plan
 * de migration, docs/MIGRATION_CUBAGE_MARTELAGE_SYNTHESE_V2.md).
 */
object CubageMethodRegistry {

    private val standingTree: List<CubageMethod<StandingTreeMeasurement>> = listOf(
        Schaeffer1ECubageMethod,
        Schaeffer2ECubageMethod,
        AlganCubageMethod,
        IfnRapideCubageMethod,
        IfnLentCubageMethod,
        FghCubageMethod,
        CoefFormeCubageMethod
    )

    private val logSegment: List<CubageMethod<LogSegmentMeasurement>> = listOf(
        HuberMethod,
        SmalianMethod,
        NewtonMethod,
        ConeFrustumMethod
    )

    fun standingTreeMethods(): List<CubageMethod<StandingTreeMeasurement>> = standingTree

    fun logSegmentMethods(): List<CubageMethod<LogSegmentMeasurement>> = logSegment

    fun findStandingTree(id: String, version: String? = null): CubageMethod<StandingTreeMeasurement>? =
        standingTree.firstOrNull { it.id == id && (version == null || it.version == version) }

    fun findLogSegment(id: String, version: String? = null): CubageMethod<LogSegmentMeasurement>? =
        logSegment.firstOrNull { it.id == id && (version == null || it.version == version) }
}
