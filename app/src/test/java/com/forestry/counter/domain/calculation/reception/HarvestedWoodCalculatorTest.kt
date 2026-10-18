package com.forestry.counter.domain.calculation.reception

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Références analytiques de solides ; aucun relevé ou prix fictif injecté dans le produit. */
class HarvestedWoodCalculatorTest {
    private fun cylinder(method: RoundwoodVolumeMethod, bark: BarkConvention = BarkConvention.OVER_BARK) =
        when (method) {
            RoundwoodVolumeMethod.HUBER -> LogMeasurement(method, 4.0, bark, middleDiameterCm = 40.0)
            RoundwoodVolumeMethod.SMALIAN -> LogMeasurement(method, 4.0, bark,
                startDiameterCm = 40.0, endDiameterCm = 40.0)
            RoundwoodVolumeMethod.NEWTON -> LogMeasurement(method, 4.0, bark,
                startDiameterCm = 40.0, middleDiameterCm = 40.0, endDiameterCm = 40.0)
        }

    private fun stack(protocol: String = "Protocole déclaré pour une enveloppe rectangulaire") =
        RectangularStackMeasurement(5.0, 2.0, 1.5, protocol)

    private fun assertRejected(block: () -> Unit) {
        try {
            block()
            fail("Une entrée invalide doit être refusée.")
        } catch (_: IllegalArgumentException) {
            // Rejet explicite attendu ; une autre exception ne constitue pas ce contrat.
        }
    }

    @Test
    fun `les trois methodes retrouvent le volume analytique du cylindre`() {
        // Cylindre de rayon 0,20 m et longueur 4 m : volume analytique 0,16 × pi.
        for (method in RoundwoodVolumeMethod.entries) {
            val result = HarvestedWoodCalculator.calculate(cylinder(method))
            assertEquals(0.5026548245743669, result.volumeM3, 1e-14)
            assertEquals(HarvestedWoodVolumeKind.GEOMETRIC_LOG_M3, result.kind)
            assertEquals(method.code, result.methodCode)
        }
    }

    @Test
    fun `newton retrouve le cone et conserve la difference des autres approximations`() {
        // Cône de rayon 0,50 m et longueur 6 m : volume analytique pi / 2.
        val newton = LogMeasurement(RoundwoodVolumeMethod.NEWTON, 6.0, BarkConvention.UNDER_BARK,
            startDiameterCm = 100.0, middleDiameterCm = 50.0, endDiameterCm = 0.0)
        val huber = LogMeasurement(RoundwoodVolumeMethod.HUBER, 6.0, BarkConvention.UNDER_BARK,
            middleDiameterCm = 50.0)
        val smalian = LogMeasurement(RoundwoodVolumeMethod.SMALIAN, 6.0, BarkConvention.UNDER_BARK,
            startDiameterCm = 100.0, endDiameterCm = 0.0)
        assertEquals(1.5707963267948966, HarvestedWoodCalculator.calculate(newton).volumeM3, 1e-14)
        assertEquals(1.1780972450961724, HarvestedWoodCalculator.calculate(huber).volumeM3, 1e-14)
        assertEquals(2.356194490192345, HarvestedWoodCalculator.calculate(smalian).volumeM3, 1e-14)
    }

    @Test
    fun `les methodes retrouvent le paraboloide a sections lineaires`() {
        // Rayon carré linéaire : aire médiane moitié de la base ; intégrale = 3pi / 4.
        val inputs = listOf(
            LogMeasurement(RoundwoodVolumeMethod.HUBER, 6.0, BarkConvention.OVER_BARK,
                middleDiameterCm = 70.71067811865476),
            LogMeasurement(RoundwoodVolumeMethod.SMALIAN, 6.0, BarkConvention.OVER_BARK,
                startDiameterCm = 100.0, endDiameterCm = 0.0),
            LogMeasurement(RoundwoodVolumeMethod.NEWTON, 6.0, BarkConvention.OVER_BARK,
                startDiameterCm = 100.0, middleDiameterCm = 70.71067811865476, endDiameterCm = 0.0),
        )
        for (input in inputs) {
            assertEquals(2.356194490192345, HarvestedWoodCalculator.calculate(input).volumeM3, 1e-14)
        }
    }

    @Test
    fun `la convention ecorce est conservee sans correction implicite`() {
        val over = cylinder(RoundwoodVolumeMethod.HUBER)
        val under = over.copy(barkConvention = BarkConvention.UNDER_BARK)
        val first = HarvestedWoodCalculator.calculate(over)
        val second = HarvestedWoodCalculator.calculate(under)
        assertSame(over, first.input)
        assertEquals(BarkConvention.OVER_BARK, first.barkConvention)
        assertEquals(BarkConvention.UNDER_BARK, second.barkConvention)
        assertEquals(first.volumeM3.toBits(), second.volumeM3.toBits())
        assertNull(first.protocolReference)
        assertEquals("1.0.0", first.methodVersion)
        assertEquals("wood-reception-volume-1", first.contractVersion)
    }

    @Test
    fun `les longueurs nulles negatives et non finies sont refusees pour chaque methode`() {
        for (length in listOf(0.0, -0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            for (method in RoundwoodVolumeMethod.entries) {
                assertRejected { HarvestedWoodCalculator.calculate(cylinder(method).copy(lengthM = length)) }
            }
        }
    }

    @Test
    fun `les diametres obligatoires manquants sont refuses`() {
        for (method in RoundwoodVolumeMethod.entries) {
            assertRejected { HarvestedWoodCalculator.calculate(LogMeasurement(method, 4.0, BarkConvention.OVER_BARK)) }
        }
        val smalian = cylinder(RoundwoodVolumeMethod.SMALIAN)
        assertRejected { HarvestedWoodCalculator.calculate(smalian.copy(startDiameterCm = null)) }
        assertRejected { HarvestedWoodCalculator.calculate(smalian.copy(endDiameterCm = null)) }
        val newton = cylinder(RoundwoodVolumeMethod.NEWTON)
        for (input in listOf(newton.copy(startDiameterCm = null), newton.copy(middleDiameterCm = null),
            newton.copy(endDiameterCm = null))) {
            assertRejected { HarvestedWoodCalculator.calculate(input) }
        }
    }

    @Test
    fun `les sections inutilisees sont refusees meme nulles ou non finies`() {
        for (diameter in listOf(0.0, 40.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val huber = cylinder(RoundwoodVolumeMethod.HUBER)
            assertRejected { HarvestedWoodCalculator.calculate(huber.copy(startDiameterCm = diameter)) }
            assertRejected { HarvestedWoodCalculator.calculate(huber.copy(endDiameterCm = diameter)) }
            assertRejected {
                HarvestedWoodCalculator.calculate(cylinder(RoundwoodVolumeMethod.SMALIAN).copy(middleDiameterCm = diameter))
            }
        }
    }

    @Test
    fun `les diametres negatifs et non finis sont refuses a chaque position`() {
        for (diameter in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertRejected {
                HarvestedWoodCalculator.calculate(cylinder(RoundwoodVolumeMethod.HUBER).copy(middleDiameterCm = diameter))
            }
            val smalian = cylinder(RoundwoodVolumeMethod.SMALIAN)
            assertRejected { HarvestedWoodCalculator.calculate(smalian.copy(startDiameterCm = diameter)) }
            assertRejected { HarvestedWoodCalculator.calculate(smalian.copy(endDiameterCm = diameter)) }
            val newton = cylinder(RoundwoodVolumeMethod.NEWTON)
            for (input in listOf(newton.copy(startDiameterCm = diameter), newton.copy(middleDiameterCm = diameter),
                newton.copy(endDiameterCm = diameter))) {
                assertRejected { HarvestedWoodCalculator.calculate(input) }
            }
        }
    }

    @Test
    fun `un diametre nul est permis a une extremite mais un volume nul est refuse`() {
        assertRejected {
            HarvestedWoodCalculator.calculate(cylinder(RoundwoodVolumeMethod.HUBER).copy(middleDiameterCm = 0.0))
        }
        for (zero in listOf(0.0, -0.0)) {
            assertRejected {
                HarvestedWoodCalculator.calculate(LogMeasurement(RoundwoodVolumeMethod.SMALIAN, 4.0,
                    BarkConvention.OVER_BARK, startDiameterCm = zero, endDiameterCm = zero))
            }
            assertRejected {
                HarvestedWoodCalculator.calculate(LogMeasurement(RoundwoodVolumeMethod.NEWTON, 4.0,
                    BarkConvention.OVER_BARK, startDiameterCm = zero, middleDiameterCm = zero, endDiameterCm = zero))
            }
            val result = HarvestedWoodCalculator.calculate(LogMeasurement(RoundwoodVolumeMethod.NEWTON, 4.0,
                BarkConvention.OVER_BARK, startDiameterCm = 40.0, middleDiameterCm = zero, endDiameterCm = zero))
            assertEquals(0.08377580409572782, result.volumeM3, 1e-14)
        }
    }

    @Test
    fun `les sections et volumes sous ou debordes ne deviennent pas des valeurs valides`() {
        for (diameter in listOf(Double.MIN_VALUE, 1e-200, Double.MAX_VALUE)) {
            for (method in RoundwoodVolumeMethod.entries) {
                val input = when (method) {
                    RoundwoodVolumeMethod.HUBER -> LogMeasurement(method, 1.0, BarkConvention.OVER_BARK,
                        middleDiameterCm = diameter)
                    RoundwoodVolumeMethod.SMALIAN -> LogMeasurement(method, 1.0, BarkConvention.OVER_BARK,
                        startDiameterCm = diameter, endDiameterCm = 0.0)
                    RoundwoodVolumeMethod.NEWTON -> LogMeasurement(method, 1.0, BarkConvention.OVER_BARK,
                        startDiameterCm = diameter, middleDiameterCm = 0.0, endDiameterCm = 0.0)
                }
                assertRejected { HarvestedWoodCalculator.calculate(input) }
            }
        }
        assertRejected {
            HarvestedWoodCalculator.calculate(cylinder(RoundwoodVolumeMethod.HUBER).copy(lengthM = Double.MIN_VALUE))
        }
        assertRejected {
            HarvestedWoodCalculator.calculate(LogMeasurement(RoundwoodVolumeMethod.HUBER, Double.MAX_VALUE,
                BarkConvention.OVER_BARK, middleDiameterCm = 200.0))
        }
    }

    @Test
    fun `smalian et newton evitent la somme et la ponderation intermediaires debordees`() {
        // Le volume attendu est la moitié de l'aire d'un cercle de diamètre 1,4 × 10^154 m.
        for (method in listOf(RoundwoodVolumeMethod.SMALIAN, RoundwoodVolumeMethod.NEWTON)) {
            val input = LogMeasurement(method, 0.5, BarkConvention.OVER_BARK,
                startDiameterCm = 1.4e156,
                middleDiameterCm = if (method == RoundwoodVolumeMethod.NEWTON) 1.4e156 else null,
                endDiameterCm = 1.4e156)
            val result = HarvestedWoodCalculator.calculate(input)
            assertTrue(result.volumeM3.isFinite())
            assertEquals(7.696902001294993e307, result.volumeM3, 2e292)
        }
    }

    @Test
    fun `la pile reste une enveloppe avec protocole exact sans convention ecorce`() {
        val input = stack("  Protocole local déclaré  ")
        val result = HarvestedWoodCalculator.calculate(input)
        assertEquals(15.0, result.volumeM3, 0.0)
        assertEquals(HarvestedWoodVolumeKind.STACKED_ENVELOPE_M3, result.kind)
        assertEquals("rectangular-envelope", result.methodCode)
        assertEquals(input.protocolReference, result.protocolReference)
        assertSame(input, result.input)
        assertNull(result.barkConvention)
    }

    @Test
    fun `les protocoles de pile absents sont refuses`() {
        for (protocol in listOf("", " ", "\n\t", "\u0085", "\u001c", "\u00a0")) {
            assertRejected { HarvestedWoodCalculator.calculate(stack(protocol)) }
        }
    }

    @Test
    fun `les dimensions de pile invalides sont refusees`() {
        for (value in listOf(0.0, -0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            for (input in listOf(stack().copy(lengthM = value), stack().copy(widthM = value),
                stack().copy(meanHeightM = value))) {
                assertRejected { HarvestedWoodCalculator.calculate(input) }
            }
        }
    }

    @Test
    fun `les sous et debordements intermediaires de pile sont refuses`() {
        for (input in listOf(
            RectangularStackMeasurement(Double.MAX_VALUE, 2.0, 1e-300, "Protocole déclaré"),
            RectangularStackMeasurement(Double.MIN_VALUE, 0.5, 1e300, "Protocole déclaré"),
            RectangularStackMeasurement(1.0, 1.0, Double.MIN_VALUE, "Protocole déclaré")
                .copy(lengthM = 0.5),
            RectangularStackMeasurement(1.0, 1.0, Double.MAX_VALUE, "Protocole déclaré")
                .copy(meanHeightM = Double.MAX_VALUE, lengthM = 2.0),
        )) {
            assertRejected { HarvestedWoodCalculator.calculate(input) }
        }
    }

    @Test
    fun `un resultat subnormal positif representable est conserve sans arrondi`() {
        val result = HarvestedWoodCalculator.calculate(RectangularStackMeasurement(1.0, 1.0,
            Double.MIN_VALUE, "Protocole déclaré"))
        assertEquals(Double.MIN_VALUE.toBits(), result.volumeM3.toBits())
    }

    @Test
    fun `un agregat accepte les methodes differentes dans une convention homogene`() {
        val inputs = RoundwoodVolumeMethod.entries.map { cylinder(it) }
        val aggregate = HarvestedWoodCalculator.aggregate(inputs)
        assertEquals(1.5079644737231008, aggregate.volumeM3, 1e-14)
        assertEquals(HarvestedWoodVolumeKind.GEOMETRIC_LOG_M3, aggregate.kind)
        assertEquals(BarkConvention.OVER_BARK, aggregate.barkConvention)
        assertNull(aggregate.protocolReference)
        assertEquals(listOf("huber", "smalian", "newton"), aggregate.results.map { it.methodCode })
        assertEquals("1.0.0", aggregate.methodVersion)
        assertEquals("wood-reception-volume-1", aggregate.contractVersion)
    }

    @Test
    fun `les agregats vides ou heterogenes sont refuses`() {
        assertRejected { HarvestedWoodCalculator.aggregate(emptyList()) }
        assertRejected { HarvestedWoodCalculator.aggregate(listOf(cylinder(RoundwoodVolumeMethod.HUBER), stack())) }
        assertRejected {
            HarvestedWoodCalculator.aggregate(listOf(cylinder(RoundwoodVolumeMethod.HUBER),
                cylinder(RoundwoodVolumeMethod.SMALIAN, BarkConvention.UNDER_BARK)))
        }
        assertRejected { HarvestedWoodCalculator.aggregate(listOf(stack("Référence A"), stack("Référence B"))) }
        assertRejected { HarvestedWoodCalculator.aggregate(listOf(stack("Référence"), stack(" Référence "))) }
    }

    @Test
    fun `les piles de meme protocole sont additionnees sans devenir du bois solide`() {
        val aggregate = HarvestedWoodCalculator.aggregate(listOf(stack(), stack()))
        assertEquals(30.0, aggregate.volumeM3, 0.0)
        assertEquals(HarvestedWoodVolumeKind.STACKED_ENVELOPE_M3, aggregate.kind)
        assertEquals(stack().protocolReference, aggregate.protocolReference)
        assertNull(aggregate.barkConvention)
    }

    @Test
    fun `la copie agregee resiste aux mutations de liste entree et de resultat`() {
        val inputs = mutableListOf<HarvestedWoodMeasurement>(cylinder(RoundwoodVolumeMethod.HUBER))
        val aggregate = HarvestedWoodCalculator.aggregate(inputs)
        inputs.clear()
        assertEquals(1, aggregate.results.size)
        assertEquals(0.5026548245743669, aggregate.volumeM3, 1e-14)
        try {
            (aggregate.results as MutableList<HarvestedWoodVolumeResult>).clear()
            fail("La liste des résultats doit être non modifiable.")
        } catch (_: UnsupportedOperationException) {
            // La liste publiée conserve les résultats calculés.
        }
    }

    @Test
    fun `la somme agregee refuse le debordement`() {
        val large = RectangularStackMeasurement(1.0, 1.0, Double.MAX_VALUE, "Protocole déclaré")
        assertRejected { HarvestedWoodCalculator.aggregate(listOf(large, large)) }
    }

    @Test
    fun `la somme proche du maximum reste representable sans debordement intermediaire`() {
        val largest = 1.7976931348623155e308
        val small = 1.097712170244096e292
        // Le total exact s'arrondit à MAX_VALUE ; une addition intermédiaire peut produire l'infini.
        for (volumes in listOf(listOf(largest, small, small), listOf(small, largest, small),
            listOf(small, small, largest))) {
            val inputs = volumes.map {
                RectangularStackMeasurement(1.0, 1.0, it, "Protocole déclaré")
            }
            val aggregate = HarvestedWoodCalculator.aggregate(inputs)
            assertEquals(Double.MAX_VALUE.toBits(), aggregate.volumeM3.toBits())
            assertEquals(3, aggregate.results.size)
        }
    }

    @Test
    fun `la somme exacte refuse aussi le depassement final du maximum`() {
        val inputs = listOf(Double.MAX_VALUE, Math.ulp(Double.MAX_VALUE)).map {
            RectangularStackMeasurement(1.0, 1.0, it, "Protocole déclaré")
        }
        assertRejected { HarvestedWoodCalculator.aggregate(inputs) }
    }

    @Test
    fun `la somme agregee conserve les contributions petites mais cumulables`() {
        val inputs = listOf(1e16, 1.0, 1.0).map {
            RectangularStackMeasurement(1.0, 1.0, it, "Protocole déclaré")
        }
        val aggregate = HarvestedWoodCalculator.aggregate(inputs)
        assertEquals(10_000_000_000_000_002.0.toBits(), aggregate.volumeM3.toBits())
    }
}
