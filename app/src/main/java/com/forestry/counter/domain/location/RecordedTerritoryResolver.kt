package com.forestry.counter.domain.location

/**
 * Résout uniquement un point WGS84 explicitement enregistré dans un relevé.
 *
 * Le fournisseur doit apporter des contours déjà validés et une provenance/version
 * qualifiée. Cette brique ne vérifie ni signature, ni licence, ni exhaustivité de la
 * couverture. Elle n'utilise pas le GPS actuel, les centroïdes ou des prix de repli.
 * Le résultat concerne ce point : il ne décrit pas l'emprise entière d'une parcelle,
 * sa station forestière, la précision de la mesure ou le domaine d'un tarif.
 *
 * Les frontières extérieures sont incluses selon [ParcelGeometry.contains] ; une
 * frontière partagée présente dans le référentiel donne plusieurs candidats. Les
 * trous et leurs frontières sont exclus. Un voisin absent du référentiel ne peut
 * pas être deviné, et une limite généralisée ne devient pas une limite cadastrale.
 */
class RecordedTerritoryResolver(
    val reference: Reference,
    boundaries: List<Boundary>,
) {
    enum class Level(internal val identityPattern: Regex) {
        DEPARTMENT(Regex("department:(?:[0-9]{2,3}|2A|2B)")),
        REGION(Regex("region:[0-9]{2}")),
    }

    data class Reference(
        val sourceId: String,
        val version: String,
        val level: Level,
    ) {
        init {
            require(sourceId.isNotBlank() && sourceId == sourceId.trim()) { "Source territoriale absente ou ambiguë" }
            require(version.isNotBlank() && version == version.trim()) { "Version territoriale absente ou ambiguë" }
        }
    }

    data class Boundary(
        val territoryId: String,
        val geometry: ParcelGeometry,
    )

    enum class Status { RESOLVED, AMBIGUOUS, MISSING }

    enum class MissingReason { POINT_NOT_RECORDED, COVERAGE_NOT_AVAILABLE, OUTSIDE_REFERENCE_COVERAGE }

    data class Resolution internal constructor(
        val status: Status,
        val recordedPoint: ParcelCoordinate?,
        val reference: Reference,
        val candidateTerritoryIds: List<String>,
        val missingReason: MissingReason? = null,
    ) {
        /** Un seul identifiant est exposé comme résolu ; aucune priorité arbitraire. */
        val resolvedTerritoryId: String?
            get() = if (status == Status.RESOLVED) candidateTerritoryIds.singleOrNull() else null
    }

    private val boundaries = boundaries.toList()

    init {
        require(this.boundaries.all { reference.level.identityPattern.matches(it.territoryId) }) {
            "Identifiant territorial incompatible avec le niveau du référentiel"
        }
        require(this.boundaries.map { it.territoryId }.distinct().size == this.boundaries.size) {
            "Identifiant territorial dupliqué : choisir explicitement un seul référentiel"
        }
    }

    fun resolve(recordedPoint: ParcelCoordinate?): Resolution {
        if (recordedPoint == null) return missing(null, MissingReason.POINT_NOT_RECORDED)
        if (boundaries.isEmpty()) return missing(recordedPoint, MissingReason.COVERAGE_NOT_AVAILABLE)

        val candidates = boundaries.filter { it.geometry.contains(recordedPoint) }
            .map { it.territoryId }
            .sorted()
        return when (candidates.size) {
            0 -> missing(recordedPoint, MissingReason.OUTSIDE_REFERENCE_COVERAGE)
            1 -> Resolution(Status.RESOLVED, recordedPoint, reference, candidates)
            else -> Resolution(Status.AMBIGUOUS, recordedPoint, reference, candidates)
        }
    }

    private fun missing(point: ParcelCoordinate?, reason: MissingReason) =
        Resolution(Status.MISSING, point, reference, emptyList(), reason)
}
