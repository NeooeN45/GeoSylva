package com.forestry.counter.domain.usecase.pack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackStorageBudgetTest {
    private val cadastre = PackArtifactIdentity("cadastre.17", "2026-10", "a".repeat(64))
    private val departmentalExtra = PackArtifactIdentity("forest-rules.17", "2026-10", "b".repeat(64))

    private fun artifact(
        identity: PackArtifactIdentity = cadastre,
        archiveBytes: Long? = 100L,
        installedBytes: Long? = 300L,
        temporaryBytes: Long? = 50L
    ) = PackArtifactStorage(identity, archiveBytes, installedBytes, temporaryBytes)

    @Test
    fun `distinguishes transfer installed data and conservative additional peak`() {
        val result = PackStorageBudgetCalculator.calculate(
            listOf(artifact()), availableBytes = 500L, marginBytes = 25L
        ) as PackStorageBudget.Known

        assertEquals(100L, result.downloadBytes)
        assertEquals(300L, result.installedBytes)
        assertEquals(475L, result.additionalPeakBytes)
        assertTrue(result.fits)
    }

    @Test
    fun `region and department share files once while departmental extras survive`() {
        val regionArtifacts = listOf(artifact())
        val departmentArtifacts = listOf(artifact(), artifact(departmentalExtra, 20L, 60L, 10L))
        val result = PackStorageBudgetCalculator.calculate(
            regionArtifacts + departmentArtifacts, availableBytes = 540L
        ) as PackStorageBudget.Known

        assertEquals(listOf(cadastre, departmentalExtra), result.artifacts)
        assertEquals(120L, result.downloadBytes)
        assertEquals(360L, result.installedBytes)
        assertEquals(540L, result.additionalPeakBytes)
        assertTrue(result.fits)
    }

    @Test
    fun `different versions or checksums remain separate artifacts`() {
        val nextVersion = cadastre.copy(version = "2026-11")
        val differentContent = cadastre.copy(sha256 = "c".repeat(64))
        val result = PackStorageBudgetCalculator.calculate(
            listOf(artifact(), artifact(nextVersion), artifact(differentContent)), 1_350L
        ) as PackStorageBudget.Known

        assertEquals(3, result.artifacts.size)
        assertEquals(300L, result.downloadBytes)
        assertEquals(1_350L, result.additionalPeakBytes)
    }

    @Test
    fun `hexadecimal checksum case does not duplicate the same artifact`() {
        val result = PackStorageBudgetCalculator.calculate(
            listOf(artifact(), artifact(cadastre.copy(sha256 = cadastre.sha256.uppercase()))), 450L
        ) as PackStorageBudget.Known

        assertEquals(listOf(cadastre), result.artifacts)
        assertEquals(450L, result.additionalPeakBytes)
    }

    @Test
    fun `conflicting sizes for an immutable identity invalidate either input order`() {
        val original = artifact()
        val conflicting = artifact(installedBytes = 301L)
        for (artifacts in listOf(listOf(original, conflicting), listOf(conflicting, original))) {
            assertEquals(
                PackStorageBudget.Invalid(
                    PackStorageBudget.InvalidReason.CONFLICTING_ARTIFACT_METADATA, cadastre
                ),
                PackStorageBudgetCalculator.calculate(artifacts, Long.MAX_VALUE)
            )
        }
    }

    @Test
    fun `unknown sizes remain indeterminate even with abundant free disk space`() {
        val result = PackStorageBudgetCalculator.calculate(
            listOf(artifact(archiveBytes = null, installedBytes = null, temporaryBytes = null)),
            Long.MAX_VALUE
        ) as PackStorageBudget.Indeterminate

        assertEquals(
            listOf(
                PackStorageBudget.MissingSize(PackStorageBudget.SizeField.ARCHIVE, cadastre),
                PackStorageBudget.MissingSize(PackStorageBudget.SizeField.INSTALLED, cadastre),
                PackStorageBudget.MissingSize(PackStorageBudget.SizeField.TEMPORARY, cadastre)
            ), result.missing
        )
    }

    @Test
    fun `unknown free space cannot approve a fully measured plan`() {
        assertEquals(
            PackStorageBudget.Indeterminate(
                listOf(PackStorageBudget.MissingSize(PackStorageBudget.SizeField.AVAILABLE))
            ),
            PackStorageBudgetCalculator.calculate(listOf(artifact()), null)
        )
    }

    @Test
    fun `valid partial prefix reduces remaining transfer and additional disk need`() {
        val result = PackStorageBudgetCalculator.calculate(
            listOf(artifact()), 390L,
            reusableFragments = listOf(PackReusableFragment(cadastre, 60L))
        ) as PackStorageBudget.Known

        assertEquals(40L, result.downloadBytes)
        assertEquals(300L, result.installedBytes)
        assertEquals(390L, result.additionalPeakBytes)
        assertTrue(result.fits)
    }

    @Test
    fun `old active packs are already reflected in free space and are not charged again`() {
        // Le volume contient déjà un pack actif et un fragment réutilisable de 60 octets.
        // availableBytes est mesuré maintenant, sans supposer la suppression de ces fichiers.
        val result = PackStorageBudgetCalculator.calculate(
            listOf(artifact()), 390L, listOf(PackReusableFragment(cadastre, 60L))
        ) as PackStorageBudget.Known

        assertEquals(390L, result.additionalPeakBytes)
        assertTrue(result.fits)
    }

    @Test
    fun `stale prefix for a different version or checksum does not reduce transfer`() {
        val result = PackStorageBudgetCalculator.calculate(
            listOf(artifact()), 450L,
            listOf(
                PackReusableFragment(cadastre.copy(version = "2026-09"), 60L),
                PackReusableFragment(cadastre.copy(sha256 = "d".repeat(64)), 60L)
            )
        ) as PackStorageBudget.Known

        assertEquals(100L, result.downloadBytes)
        assertEquals(450L, result.additionalPeakBytes)
    }

    @Test
    fun `duplicate fragment references are counted only once`() {
        val fragment = PackReusableFragment(cadastre, 60L)
        val result = PackStorageBudgetCalculator.calculate(
            listOf(artifact()), 390L, listOf(fragment, fragment)
        ) as PackStorageBudget.Known

        assertEquals(40L, result.downloadBytes)
        assertEquals(390L, result.additionalPeakBytes)
    }

    @Test
    fun `contradictory fragment lengths are invalid instead of choosing arbitrarily`() {
        assertEquals(
            PackStorageBudget.Invalid(PackStorageBudget.InvalidReason.CONFLICTING_FRAGMENT_METADATA, cadastre),
            PackStorageBudgetCalculator.calculate(
                listOf(artifact()), 450L,
                listOf(PackReusableFragment(cadastre, 60L), PackReusableFragment(cadastre, 61L))
            )
        )
    }

    @Test
    fun `oversized reusable prefix cannot turn the budget negative`() {
        assertEquals(
            PackStorageBudget.Invalid(PackStorageBudget.InvalidReason.FRAGMENT_EXCEEDS_ARCHIVE, cadastre),
            PackStorageBudgetCalculator.calculate(
                listOf(artifact()), Long.MAX_VALUE, listOf(PackReusableFragment(cadastre, 101L))
            )
        )
    }

    @Test
    fun `zero temporary space must be explicit and exact free space is sufficient`() {
        val result = PackStorageBudgetCalculator.calculate(
            listOf(artifact(temporaryBytes = 0L)), 400L
        ) as PackStorageBudget.Known

        assertTrue(result.fits)
        assertFalse(
            (PackStorageBudgetCalculator.calculate(
                listOf(artifact(temporaryBytes = 0L)), 399L
            ) as PackStorageBudget.Known).fits
        )
    }

    @Test
    fun `terabyte volumes retain byte accuracy rather than overflowing an Int`() {
        val archive = 3_000_000_000_000L
        val installed = 8_000_000_000_000L
        val temporary = 1_000_000_000_000L
        val result = PackStorageBudgetCalculator.calculate(
            listOf(artifact(archiveBytes = archive, installedBytes = installed, temporaryBytes = temporary)),
            12_000_000_000_000L
        ) as PackStorageBudget.Known

        assertEquals(archive, result.downloadBytes)
        assertEquals(installed, result.installedBytes)
        assertEquals(12_000_000_000_000L, result.additionalPeakBytes)
        assertTrue(result.fits)
    }

    @Test
    fun `sum overflow is invalid instead of wrapping into a feasible negative budget`() {
        assertEquals(
            PackStorageBudget.Invalid(PackStorageBudget.InvalidReason.ARITHMETIC_OVERFLOW),
            PackStorageBudgetCalculator.calculate(
                listOf(artifact(archiveBytes = Long.MAX_VALUE, installedBytes = 1L, temporaryBytes = 0L)),
                Long.MAX_VALUE
            )
        )
    }

    @Test
    fun `overflow across artifacts and safety margin is detected`() {
        val enormous = artifact(archiveBytes = Long.MAX_VALUE - 1L, installedBytes = 0L, temporaryBytes = 0L)
        val small = artifact(departmentalExtra, 2L, 0L, 0L)
        val expected = PackStorageBudget.Invalid(PackStorageBudget.InvalidReason.ARITHMETIC_OVERFLOW)

        assertEquals(expected, PackStorageBudgetCalculator.calculate(listOf(enormous, small), Long.MAX_VALUE))
        assertEquals(
            expected,
            PackStorageBudgetCalculator.calculate(listOf(enormous), Long.MAX_VALUE, marginBytes = 2L)
        )
    }

    @Test
    fun `negative sizes free space fragments and margin are rejected`() {
        val expected = PackStorageBudget.InvalidReason.NEGATIVE_SIZE
        val results = listOf(
            PackStorageBudgetCalculator.calculate(listOf(artifact(archiveBytes = -1L)), 450L),
            PackStorageBudgetCalculator.calculate(listOf(artifact(installedBytes = -1L)), 450L),
            PackStorageBudgetCalculator.calculate(listOf(artifact(temporaryBytes = -1L)), 450L),
            PackStorageBudgetCalculator.calculate(listOf(artifact()), -1L),
            PackStorageBudgetCalculator.calculate(listOf(artifact()), 450L, marginBytes = -1L),
            PackStorageBudgetCalculator.calculate(listOf(artifact()), 450L, listOf(PackReusableFragment(cadastre, -1L)))
        )

        assertTrue(results.all { it is PackStorageBudget.Invalid && it.reason == expected })
    }

    @Test
    fun `blank identifiers or malformed checksums cannot establish immutable identity`() {
        for (identity in listOf(
            cadastre.copy(id = " "), cadastre.copy(version = ""), cadastre.copy(sha256 = "latest")
        )) {
            assertEquals(
                PackStorageBudget.InvalidReason.INVALID_IDENTITY,
                (PackStorageBudgetCalculator.calculate(
                    listOf(artifact(identity)), 450L
                ) as PackStorageBudget.Invalid).reason
            )
        }
    }

    @Test
    fun `empty selection with measured storage has a zero budget`() {
        val result = PackStorageBudgetCalculator.calculate(emptyList(), 0L) as PackStorageBudget.Known

        assertTrue(result.artifacts.isEmpty())
        assertEquals(0L, result.downloadBytes)
        assertEquals(0L, result.installedBytes)
        assertEquals(0L, result.additionalPeakBytes)
        assertTrue(result.fits)
    }
}
