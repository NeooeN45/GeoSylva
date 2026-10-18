package com.forestry.counter.domain.usecase.pack

/** Référence immuable : une autre version ou un autre SHA-256 désigne un fichier distinct. */
data class PackArtifactIdentity(
    val id: String,
    val version: String,
    val sha256: String
)

/**
 * Tailles mesurées en octets, indépendantes des kilo-octets estimés du catalogue.
 *
 * [installedBytes] comprend le contenu extrait et les index persistants.
 * [temporaryBytes] représente l'espace de travail supplémentaire nécessaire à
 * l'extraction et à l'indexation, hors archive et contenu final. Une taille
 * inconnue reste null ; zéro signifie une absence mesurée d'espace supplémentaire.
 */
data class PackArtifactStorage(
    val identity: PackArtifactIdentity,
    val archiveBytes: Long?,
    val installedBytes: Long?,
    val temporaryBytes: Long?
)

/**
 * Fragment existant dont le downloader a établi la réutilisation pour cette
 * [identity] exacte. Le calculateur ne prouve ni l'intégrité ni la validité
 * de reprise. Les octets occupés réduisent déjà l'espace libre fourni au calcul.
 */
data class PackReusableFragment(
    val identity: PackArtifactIdentity,
    val bytes: Long
)

sealed interface PackStorageBudget {
    data class Known(
        val artifacts: List<PackArtifactIdentity>,
        val downloadBytes: Long,
        val installedBytes: Long,
        val additionalPeakBytes: Long,
        val availableBytes: Long
    ) : PackStorageBudget {
        val fits: Boolean get() = additionalPeakBytes <= availableBytes
    }

    data class Indeterminate(val missing: List<MissingSize>) : PackStorageBudget

    data class Invalid(
        val reason: InvalidReason,
        val identity: PackArtifactIdentity? = null
    ) : PackStorageBudget

    data class MissingSize(val field: SizeField, val identity: PackArtifactIdentity? = null)

    enum class SizeField { ARCHIVE, INSTALLED, TEMPORARY, AVAILABLE }

    enum class InvalidReason {
        INVALID_IDENTITY,
        NEGATIVE_SIZE,
        CONFLICTING_ARTIFACT_METADATA,
        CONFLICTING_FRAGMENT_METADATA,
        FRAGMENT_EXCEEDS_ARCHIVE,
        ARITHMETIC_OVERFLOW
    }
}

/**
 * Budget pur et conservateur pour la liste des artefacts d'une sélection.
 *
 * La déduplication utilise la référence immuable, sans déduire l'inclusion d'un
 * territoire : les fichiers propres au département restent présents lorsqu'on
 * sélectionne sa région. Archives, fichiers installés et espaces temporaires
 * peuvent coexister. Le pic vaut donc la somme des archives restantes, contenus
 * installés et espaces temporaires, augmentée de la marge.
 *
 * [availableBytes] désigne l'espace actuellement utilisable sur le volume cible.
 * Les packs existants et fragments occupent déjà ce volume ; leurs tailles ne
 * s'ajoutent pas une seconde fois. Ce budget ne suppose aucune suppression de
 * version active. Le calcul préalable ne réserve pas d'espace et ne prouve pas
 * la réussite de l'installation.
 */
object PackStorageBudgetCalculator {
    private val sha256Pattern = Regex("[a-fA-F0-9]{64}")

    fun calculate(
        artifacts: Iterable<PackArtifactStorage>,
        availableBytes: Long?,
        reusableFragments: Iterable<PackReusableFragment> = emptyList(),
        marginBytes: Long = 0L
    ): PackStorageBudget {
        if (marginBytes < 0L || (availableBytes != null && availableBytes < 0L)) {
            return PackStorageBudget.Invalid(PackStorageBudget.InvalidReason.NEGATIVE_SIZE)
        }

        val unique = linkedMapOf<PackArtifactIdentity, PackArtifactStorage>()
        for (artifact in artifacts) {
            val identity = canonicalIdentity(artifact.identity)
                ?: return PackStorageBudget.Invalid(
                    PackStorageBudget.InvalidReason.INVALID_IDENTITY,
                    artifact.identity
                )
            if (listOf(artifact.archiveBytes, artifact.installedBytes, artifact.temporaryBytes)
                    .any { it != null && it < 0L }
            ) {
                return PackStorageBudget.Invalid(PackStorageBudget.InvalidReason.NEGATIVE_SIZE, identity)
            }
            val normalized = artifact.copy(identity = identity)
            val previous = unique[identity]
            if (previous != null && previous != normalized) {
                return PackStorageBudget.Invalid(
                    PackStorageBudget.InvalidReason.CONFLICTING_ARTIFACT_METADATA,
                    identity
                )
            }
            unique[identity] = normalized
        }

        val fragments = mutableMapOf<PackArtifactIdentity, Long>()
        for (fragment in reusableFragments) {
            val identity = canonicalIdentity(fragment.identity)
                ?: return PackStorageBudget.Invalid(
                    PackStorageBudget.InvalidReason.INVALID_IDENTITY,
                    fragment.identity
                )
            if (fragment.bytes < 0L) {
                return PackStorageBudget.Invalid(PackStorageBudget.InvalidReason.NEGATIVE_SIZE, identity)
            }
            // Un fragment d'une autre version ou empreinte ne réduit pas ce plan.
            val artifact = unique[identity] ?: continue
            val previous = fragments[identity]
            if (previous != null && previous != fragment.bytes) {
                return PackStorageBudget.Invalid(
                    PackStorageBudget.InvalidReason.CONFLICTING_FRAGMENT_METADATA,
                    identity
                )
            }
            if (artifact.archiveBytes != null && fragment.bytes > artifact.archiveBytes) {
                return PackStorageBudget.Invalid(
                    PackStorageBudget.InvalidReason.FRAGMENT_EXCEEDS_ARCHIVE,
                    identity
                )
            }
            fragments[identity] = fragment.bytes
        }

        val missing = buildList {
            for (artifact in unique.values) {
                if (artifact.archiveBytes == null) {
                    add(PackStorageBudget.MissingSize(PackStorageBudget.SizeField.ARCHIVE, artifact.identity))
                }
                if (artifact.installedBytes == null) {
                    add(PackStorageBudget.MissingSize(PackStorageBudget.SizeField.INSTALLED, artifact.identity))
                }
                if (artifact.temporaryBytes == null) {
                    add(PackStorageBudget.MissingSize(PackStorageBudget.SizeField.TEMPORARY, artifact.identity))
                }
            }
            if (availableBytes == null) {
                add(PackStorageBudget.MissingSize(PackStorageBudget.SizeField.AVAILABLE))
            }
        }
        if (missing.isNotEmpty()) return PackStorageBudget.Indeterminate(missing)
        val knownAvailableBytes = requireNotNull(availableBytes)

        return try {
            var downloadBytes = 0L
            var installedBytes = 0L
            var additionalPeakBytes = marginBytes
            for (artifact in unique.values) {
                val remainingArchive = requireNotNull(artifact.archiveBytes) - (fragments[artifact.identity] ?: 0L)
                val finalContentBytes = requireNotNull(artifact.installedBytes)
                val temporaryBytes = requireNotNull(artifact.temporaryBytes)
                downloadBytes = Math.addExact(downloadBytes, remainingArchive)
                installedBytes = Math.addExact(installedBytes, finalContentBytes)
                additionalPeakBytes = Math.addExact(additionalPeakBytes, remainingArchive)
                additionalPeakBytes = Math.addExact(additionalPeakBytes, finalContentBytes)
                additionalPeakBytes = Math.addExact(additionalPeakBytes, temporaryBytes)
            }
            PackStorageBudget.Known(
                artifacts = unique.keys.toList(),
                downloadBytes = downloadBytes,
                installedBytes = installedBytes,
                additionalPeakBytes = additionalPeakBytes,
                availableBytes = knownAvailableBytes
            )
        } catch (_: ArithmeticException) {
            PackStorageBudget.Invalid(PackStorageBudget.InvalidReason.ARITHMETIC_OVERFLOW)
        }
    }

    private fun canonicalIdentity(identity: PackArtifactIdentity): PackArtifactIdentity? {
        if (identity.id.isBlank() || identity.version.isBlank() ||
            !identity.sha256.matches(sha256Pattern)
        ) return null
        return identity.copy(sha256 = identity.sha256.lowercase())
    }
}
