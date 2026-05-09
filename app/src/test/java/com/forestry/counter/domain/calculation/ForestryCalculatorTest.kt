package com.forestry.counter.domain.calculation

import com.forestry.counter.domain.model.ParameterItem
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.parameters.ParameterKeys
import com.forestry.counter.domain.repository.ParameterRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt

@OptIn(ExperimentalCoroutinesApi::class)
class ForestryCalculatorTest {

    private class FakeParameterRepository(initial: Map<String, String>) : ParameterRepository {
        private val data = initial.toMutableMap()

        override fun getAllParameters(): Flow<List<ParameterItem>> = flow {
            emit(data.map { (k, v) -> ParameterItem(key = k, valueJson = v) })
        }

        override fun getParameter(key: String): Flow<ParameterItem?> = flow {
            emit(data[key]?.let { ParameterItem(key = key, valueJson = it) })
        }

        override suspend fun setParameter(item: ParameterItem) {
            data[item.key] = item.valueJson
        }

        override suspend fun setParameters(items: List<ParameterItem>) {
            items.forEach { data[it.key] = it.valueJson }
        }

        override suspend fun deleteParameter(key: String) {
            data.remove(key)
        }
    }

    private fun assertNullableDoubleEquals(expected: Double?, actual: Double?, delta: Double = 1e-9) {
        if (expected == null || actual == null) {
            assertEquals(expected, actual)
            return
        }
        val diff = kotlin.math.abs(expected - actual)
        if (diff > delta) {
            throw AssertionError("Expected <$expected> but was <$actual> (diff=$diff)")
        }
    }

    @Test
    fun `synthesisForEssence uses wildcard product price entry`() = runTest {
        val json = Json { ignoreUnknownKeys = true }

        val coefs = listOf(CoefVolumeRange(essence = "HETRE", min = 0, max = 200, f = 0.5, method = null))
        val heightDefaults = listOf(HeightDefaultRange(essence = "HETRE", min = 0, max = 200, h = 20.0))
        val heightModes = emptyList<HeightModeEntry>()
        val rules = listOf(ProductRule(essence = "*", min = 0, max = null, product = "BO"))
        val prices = listOf(PriceEntry(essence = "HETRE", product = "*", min = 0, max = 200, eurPerM3 = 123.0))

        val repo = FakeParameterRepository(
            mapOf(
                ParameterKeys.COEFS_VOLUME to json.encodeToString(coefs),
                ParameterKeys.HAUTEURS_DEFAUT to json.encodeToString(heightDefaults),
                ParameterKeys.HEIGHT_MODES to json.encodeToString(heightModes),
                ParameterKeys.RULES_PRODUITS to json.encodeToString(rules),
                ParameterKeys.PRIX_MARCHE to json.encodeToString(prices)
            )
        )
        val calculator = ForestryCalculator(repo)
        val params = calculator.loadSynthesisParams()

        val tiges = listOf(
            Tige(
                id = "1",
                parcelleId = "P",
                placetteId = null,
                essenceCode = "HETRE",
                diamCm = 30.0,
                hauteurM = 20.0,
                gpsWkt = null,
                precisionM = null,
                altitudeM = null,
                note = null,
                produit = null,
                fCoef = null,
                valueEur = null
            )
        )

        val (rows, _) = calculator.synthesisForEssence(
            essenceCode = "HETRE",
            classes = listOf(10, 20, 30),
            tiges = tiges,
            manualHeights = null,
            method = null,
            params = params,
            requireHeights = true
        )

        val row = rows.firstOrNull { it.count > 0 }
        assertNotNull(row)
        val v = row!!.vSum
        val value = row.valueSumEur
        assertNotNull(v)
        assertNotNull(value)
        assertNullableDoubleEquals(123.0, value!! / v!!, delta = 1e-6)
    }

    @Test
    fun `computeV uses power-law volume formula`() = runTest {
        val repo = FakeParameterRepository(emptyMap())
        val calculator = ForestryCalculator(repo)

        val d = 40.0
        val h = 20.0

        val v = calculator.computeV(
            essenceCode = "CH_SESSILE",
            diamCm = d,
            heightM = h,
            _method = null
        )

        // Algan CH_SESSILE : V = a * D^b * H^c
        val expected = 0.0000423 * d.pow(2.118) * h.pow(0.872)
        assertNotNull(v)
        assertNullableDoubleEquals(expected, v, delta = 1e-6)
    }

    @Test
    fun `synthesisForEssence requireHeights uses default height as fallback when no measured height`() = runTest {
        val json = Json { ignoreUnknownKeys = true }

        val heightDefaults = listOf(
            HeightDefaultRange(essence = "HETRE", min = 0, max = 200, h = 20.0)
        )

        val repo = FakeParameterRepository(
            mapOf(
                ParameterKeys.HAUTEURS_DEFAUT to json.encodeToString(heightDefaults),
                ParameterKeys.HEIGHT_MODES to "[]",
                ParameterKeys.RULES_PRODUITS to "[]",
                ParameterKeys.PRIX_MARCHE to "[]",
                ParameterKeys.COEFS_VOLUME to "[]"
            )
        )
        val calculator = ForestryCalculator(repo)
        val params = calculator.loadSynthesisParams()

        val tiges = listOf(
            Tige(
                id = "1",
                parcelleId = "P",
                placetteId = null,
                essenceCode = "HETRE",
                diamCm = 30.0,
                hauteurM = null,
                gpsWkt = null,
                precisionM = null,
                altitudeM = null,
                note = null,
                produit = null,
                fCoef = null,
                valueEur = null
            )
        )

        val (_, totals) = calculator.synthesisForEssence(
            essenceCode = "HETRE",
            classes = listOf(30),
            tiges = tiges,
            manualHeights = null,
            method = null,
            params = params,
            requireHeights = true
        )

        // Default heights are now used as last-resort fallback even in requireHeights=true mode.
        // This prevents null volumes for TGB and other large trees with no measured height.
        assertEquals(1, totals.nTotal)
        assertNotNull(totals.vTotal)
        assertEquals(1, totals.volumeComputedCount)
    }

    @Test
    fun `synthesisForEssence requireHeights blocks volume when NO defaults and NO measured height`() = runTest {
        val json = Json { ignoreUnknownKeys = true }

        // No height defaults at all
        val repo = FakeParameterRepository(
            mapOf(
                ParameterKeys.HAUTEURS_DEFAUT to "[]",
                ParameterKeys.HEIGHT_MODES to "[]",
                ParameterKeys.RULES_PRODUITS to "[]",
                ParameterKeys.PRIX_MARCHE to "[]",
                ParameterKeys.COEFS_VOLUME to "[]"
            )
        )
        val calculator = ForestryCalculator(repo)
        val params = calculator.loadSynthesisParams()

        val tiges = listOf(
            Tige(
                id = "1",
                parcelleId = "P",
                placetteId = null,
                essenceCode = "HETRE",
                diamCm = 30.0,
                hauteurM = null,
                gpsWkt = null,
                precisionM = null,
                altitudeM = null,
                note = null,
                produit = null,
                fCoef = null,
                valueEur = null
            )
        )

        val (_, totals) = calculator.synthesisForEssence(
            essenceCode = "HETRE",
            classes = listOf(30),
            tiges = tiges,
            manualHeights = null,
            method = null,
            params = params,
            requireHeights = true
        )

        // No defaults, no measured height → volume must be null
        assertEquals(1, totals.nTotal)
        assertEquals(null, totals.vTotal)
        assertEquals(0, totals.volumeComputedCount)
    }

    @Test
    fun `synthesisForEssence uses default heights when requireHeights is false`() = runTest {
        val json = Json { ignoreUnknownKeys = true }

        val heightDefaults = listOf(
            HeightDefaultRange(essence = "HETRE", min = 0, max = 200, h = 20.0)
        )

        val repo = FakeParameterRepository(
            mapOf(
                ParameterKeys.HAUTEURS_DEFAUT to json.encodeToString(heightDefaults),
                ParameterKeys.HEIGHT_MODES to "[]",
                ParameterKeys.RULES_PRODUITS to "[]",
                ParameterKeys.PRIX_MARCHE to "[]",
                ParameterKeys.COEFS_VOLUME to "[]"
            )
        )
        val calculator = ForestryCalculator(repo)
        val params = calculator.loadSynthesisParams()

        val tiges = listOf(
            Tige(
                id = "1",
                parcelleId = "P",
                placetteId = null,
                essenceCode = "HETRE",
                diamCm = 30.0,
                hauteurM = null,
                gpsWkt = null,
                precisionM = null,
                altitudeM = null,
                note = null,
                produit = null,
                fCoef = null,
                valueEur = null
            )
        )

        val (rows, totals) = calculator.synthesisForEssence(
            essenceCode = "HETRE",
            classes = listOf(30),
            tiges = tiges,
            manualHeights = null,
            method = null,
            params = params,
            requireHeights = false
        )

        assertEquals(1, totals.nTotal)
        assertNotNull(totals.vTotal)

        // Algan HETRE_COMMUN : V = a * D^b * H^c
        val expected = 0.0000362 * 30.0.pow(2.158) * 20.0.pow(0.860)
        val row = rows.firstOrNull { it.diamClass == 30 }
        assertNotNull(row)
        assertNotNull(row!!.vSum)
        assertNullableDoubleEquals(expected, row.vSum, delta = 1e-6)
    }

    @Test
    fun `computeG and Dg match dendrometric definitions`() = runTest {
        val repo = FakeParameterRepository(emptyMap())
        val calculator = ForestryCalculator(repo)

        val diameters = listOf(20.0, 30.0)
        val gTotal = diameters.sumOf { calculator.computeG(it) }

        val expectedGFor20 = PI * (20.0 / 200.0).pow(2.0)
        assertNullableDoubleEquals(expectedGFor20, calculator.computeG(20.0), delta = 1e-12)

        val dgFromG = sqrt((4.0 * gTotal) / (PI * diameters.size.toDouble())) * 100.0
        val expectedDg = sqrt(diameters.map { it * it }.average())
        assertNullableDoubleEquals(expectedDg, dgFromG, delta = 1e-9)
    }

    @Test
    fun `synthesisForEssence cached params matches legacy behavior`() = runTest {
        val json = Json { ignoreUnknownKeys = true }

        val coefs = listOf(
            CoefVolumeRange(essence = "HETRE", min = 0, max = 200, f = 0.48, method = "LENT"),
            CoefVolumeRange(essence = "HETRE", min = 0, max = 200, f = 0.53, method = "RAPIDE")
        )
        val heightDefaults = listOf(
            HeightDefaultRange(essence = "HETRE", min = 0, max = 200, h = 20.0)
        )
        val heightModes = emptyList<HeightModeEntry>()
        val rules = listOf(ProductRule(essence = "*", min = 0, max = null, product = "BO"))
        val prices = listOf(PriceEntry(essence = "HETRE", product = "BO", min = 0, max = 200, eurPerM3 = 100.0))

        val repo = FakeParameterRepository(
            mapOf(
                ParameterKeys.COEFS_VOLUME to json.encodeToString(coefs),
                ParameterKeys.HAUTEURS_DEFAUT to json.encodeToString(heightDefaults),
                ParameterKeys.HEIGHT_MODES to json.encodeToString(heightModes),
                ParameterKeys.RULES_PRODUITS to json.encodeToString(rules),
                ParameterKeys.PRIX_MARCHE to json.encodeToString(prices)
            )
        )
        val calculator = ForestryCalculator(repo)

        val tiges = listOf(
            Tige(
                id = "1",
                parcelleId = "P",
                placetteId = null,
                essenceCode = "HETRE",
                diamCm = 30.0,
                hauteurM = null,
                gpsWkt = null,
                precisionM = null,
                altitudeM = null,
                note = null,
                produit = null,
                fCoef = null,
                valueEur = null
            ),
            Tige(
                id = "2",
                parcelleId = "P",
                placetteId = null,
                essenceCode = "HETRE",
                diamCm = 30.0,
                hauteurM = 24.0,
                gpsWkt = null,
                precisionM = null,
                altitudeM = null,
                note = null,
                produit = null,
                fCoef = null,
                valueEur = null
            ),
            Tige(
                id = "3",
                parcelleId = "P",
                placetteId = null,
                essenceCode = "HETRE",
                diamCm = 40.0,
                hauteurM = null,
                gpsWkt = null,
                precisionM = null,
                altitudeM = null,
                note = null,
                produit = null,
                fCoef = null,
                valueEur = null
            )
        )

        val classes = listOf(10, 20, 30)

        val (rowsLegacy, totalsLegacy) = calculator.synthesisForEssence(
            essenceCode = "HETRE",
            classes = classes,
            tiges = tiges,
            manualHeights = null,
            method = "LENT",
            params = null
        )

        val params = calculator.loadSynthesisParams()
        val (rowsCached, totalsCached) = calculator.synthesisForEssence(
            essenceCode = "HETRE",
            classes = classes,
            tiges = tiges,
            manualHeights = null,
            method = "LENT",
            params = params
        )

        assertEquals(rowsLegacy.size, rowsCached.size)
        rowsLegacy.zip(rowsCached).forEach { (a, b) ->
            assertEquals(a.diamClass, b.diamClass)
            assertEquals(a.count, b.count)
            assertNullableDoubleEquals(a.hMean, b.hMean)
            assertNullableDoubleEquals(a.vSum, b.vSum)
            assertNullableDoubleEquals(a.valueSumEur, b.valueSumEur)
        }

        assertEquals(totalsLegacy.nTotal, totalsCached.nTotal)
        assertNullableDoubleEquals(totalsLegacy.dmWeighted, totalsCached.dmWeighted)
        assertNullableDoubleEquals(totalsLegacy.hMean, totalsCached.hMean)
        assertNullableDoubleEquals(totalsLegacy.vTotal, totalsCached.vTotal)
    }

    @Test
    fun `synthesisForEssence respects height modes fixed and samples`() = runTest {
        val json = Json { ignoreUnknownKeys = true }

        val coefs = listOf(CoefVolumeRange(essence = "HETRE", min = 0, max = 200, f = 0.5, method = null))
        val heightDefaults = listOf(HeightDefaultRange(essence = "HETRE", min = 0, max = 200, h = 20.0))
        val heightModes = listOf(
            HeightModeEntry(essence = "HETRE", diamClass = 30, mode = "FIXED", fixed = 18.0),
            HeightModeEntry(essence = "HETRE", diamClass = 40, mode = "SAMPLES", fixed = null)
        )
        val rules = listOf(ProductRule(essence = "*", min = 0, max = null, product = "BO"))
        val prices = listOf(PriceEntry(essence = "HETRE", product = "BO", min = 0, max = 200, eurPerM3 = 100.0))

        val repo = FakeParameterRepository(
            mapOf(
                ParameterKeys.COEFS_VOLUME to json.encodeToString(coefs),
                ParameterKeys.HAUTEURS_DEFAUT to json.encodeToString(heightDefaults),
                ParameterKeys.HEIGHT_MODES to json.encodeToString(heightModes),
                ParameterKeys.RULES_PRODUITS to json.encodeToString(rules),
                ParameterKeys.PRIX_MARCHE to json.encodeToString(prices)
            )
        )
        val calculator = ForestryCalculator(repo)
        val params = calculator.loadSynthesisParams()

        val tiges = listOf(
            Tige(
                id = "1",
                parcelleId = "P",
                placetteId = null,
                essenceCode = "HETRE",
                diamCm = 30.0,
                hauteurM = 17.0,
                gpsWkt = null,
                precisionM = null,
                altitudeM = null,
                note = null,
                produit = null,
                fCoef = null,
                valueEur = null
            ),
            Tige(
                id = "2",
                parcelleId = "P",
                placetteId = null,
                essenceCode = "HETRE",
                diamCm = 30.0,
                hauteurM = null,
                gpsWkt = null,
                precisionM = null,
                altitudeM = null,
                note = null,
                produit = null,
                fCoef = null,
                valueEur = null
            ),
            Tige(
                id = "3",
                parcelleId = "P",
                placetteId = null,
                essenceCode = "HETRE",
                diamCm = 40.0,
                hauteurM = 22.0,
                gpsWkt = null,
                precisionM = null,
                altitudeM = null,
                note = null,
                produit = null,
                fCoef = null,
                valueEur = null
            ),
            Tige(
                id = "4",
                parcelleId = "P",
                placetteId = null,
                essenceCode = "HETRE",
                diamCm = 40.0,
                hauteurM = 24.0,
                gpsWkt = null,
                precisionM = null,
                altitudeM = null,
                note = null,
                produit = null,
                fCoef = null,
                valueEur = null
            ),
            Tige(
                id = "5",
                parcelleId = "P",
                placetteId = null,
                essenceCode = "HETRE",
                diamCm = 40.0,
                hauteurM = null,
                gpsWkt = null,
                precisionM = null,
                altitudeM = null,
                note = null,
                produit = null,
                fCoef = null,
                valueEur = null
            )
        )

        val (rows, _) = calculator.synthesisForEssence(
            essenceCode = "HETRE",
            classes = listOf(30, 40),
            tiges = tiges,
            manualHeights = null,
            method = null,
            params = params
        )

        val row30 = rows.firstOrNull { it.diamClass == 30 }
        val row40 = rows.firstOrNull { it.diamClass == 40 }
        assertNotNull(row30)
        assertNotNull(row40)

        assertNullableDoubleEquals(17.5, row30!!.hMean, delta = 1e-9)
        assertNullableDoubleEquals(23.0, row40!!.hMean, delta = 1e-9)
    }

    @Test
    fun `synthesisForEssence computes valueSum using class product price`() = runTest {
        val json = Json { ignoreUnknownKeys = true }

        val coefs = listOf(CoefVolumeRange(essence = "HETRE", min = 0, max = 200, f = 0.5, method = null))
        val heightDefaults = listOf(HeightDefaultRange(essence = "HETRE", min = 0, max = 200, h = 20.0))
        val heightModes = emptyList<HeightModeEntry>()
        val rules = listOf(
            ProductRule(essence = "*", min = 35, max = null, product = "BO"),
            ProductRule(essence = "*", min = 0, max = 34, product = "BI")
        )
        val prices = listOf(
            PriceEntry(essence = "HETRE", product = "BI", min = 0, max = 34, eurPerM3 = 50.0),
            PriceEntry(essence = "HETRE", product = "BO", min = 35, max = 200, eurPerM3 = 100.0)
        )

        val repo = FakeParameterRepository(
            mapOf(
                ParameterKeys.COEFS_VOLUME to json.encodeToString(coefs),
                ParameterKeys.HAUTEURS_DEFAUT to json.encodeToString(heightDefaults),
                ParameterKeys.HEIGHT_MODES to json.encodeToString(heightModes),
                ParameterKeys.RULES_PRODUITS to json.encodeToString(rules),
                ParameterKeys.PRIX_MARCHE to json.encodeToString(prices)
            )
        )
        val calculator = ForestryCalculator(repo)
        val params = calculator.loadSynthesisParams()

        val tiges = listOf(
            Tige(
                id = "1",
                parcelleId = "P",
                placetteId = null,
                essenceCode = "HETRE",
                diamCm = 30.0,
                hauteurM = 20.0,
                gpsWkt = null,
                precisionM = null,
                altitudeM = null,
                note = null,
                produit = null,
                fCoef = null,
                valueEur = null
            ),
            Tige(
                id = "2",
                parcelleId = "P",
                placetteId = null,
                essenceCode = "HETRE",
                diamCm = 40.0,
                hauteurM = 20.0,
                gpsWkt = null,
                precisionM = null,
                altitudeM = null,
                note = null,
                produit = null,
                fCoef = null,
                valueEur = null
            )
        )

        val (rows, _) = calculator.synthesisForEssence(
            essenceCode = "HETRE",
            classes = listOf(30, 40),
            tiges = tiges,
            manualHeights = null,
            method = null,
            params = params
        )

        val v30 = rows.first { it.diamClass == 30 }.vSum!!
        val v40 = rows.first { it.diamClass == 40 }.vSum!!
        val expectedValue30 = v30 * 50.0
        val expectedValue40 = v40 * 100.0

        assertNullableDoubleEquals(expectedValue30, rows.first { it.diamClass == 30 }.valueSumEur, delta = 1e-9)
        assertNullableDoubleEquals(expectedValue40, rows.first { it.diamClass == 40 }.valueSumEur, delta = 1e-9)
    }

    @Test
    fun `synthesisForEssence uses manual heights for missing tree heights`() = runTest {
        val json = Json { ignoreUnknownKeys = true }

        val coefs = listOf(CoefVolumeRange(essence = "HETRE", min = 0, max = 200, f = 0.5, method = null))
        val heightDefaults = listOf(HeightDefaultRange(essence = "HETRE", min = 0, max = 200, h = 20.0))
        val heightModes = emptyList<HeightModeEntry>()
        val rules = listOf(ProductRule(essence = "*", min = 0, max = null, product = "BO"))
        val prices = listOf(PriceEntry(essence = "HETRE", product = "BO", min = 0, max = 200, eurPerM3 = 100.0))

        val repo = FakeParameterRepository(
            mapOf(
                ParameterKeys.COEFS_VOLUME to json.encodeToString(coefs),
                ParameterKeys.HAUTEURS_DEFAUT to json.encodeToString(heightDefaults),
                ParameterKeys.HEIGHT_MODES to json.encodeToString(heightModes),
                ParameterKeys.RULES_PRODUITS to json.encodeToString(rules),
                ParameterKeys.PRIX_MARCHE to json.encodeToString(prices)
            )
        )
        val calculator = ForestryCalculator(repo)
        val params = calculator.loadSynthesisParams()

        val tiges = listOf(
            Tige(
                id = "1",
                parcelleId = "P",
                placetteId = null,
                essenceCode = "HETRE",
                diamCm = 30.0,
                hauteurM = 22.0,
                gpsWkt = null,
                precisionM = null,
                altitudeM = null,
                note = null,
                produit = null,
                fCoef = null,
                valueEur = null
            ),
            Tige(
                id = "2",
                parcelleId = "P",
                placetteId = null,
                essenceCode = "HETRE",
                diamCm = 30.0,
                hauteurM = null,
                gpsWkt = null,
                precisionM = null,
                altitudeM = null,
                note = null,
                produit = null,
                fCoef = null,
                valueEur = null
            )
        )

        val manualHeights = mapOf(30 to 26.0)

        val (rows, _) = calculator.synthesisForEssence(
            essenceCode = "HETRE",
            classes = listOf(30),
            tiges = tiges,
            manualHeights = manualHeights,
            method = null,
            params = params
        )

        val row30 = rows.first { it.diamClass == 30 }
        assertNullableDoubleEquals(24.0, row30.hMean, delta = 1e-9)

        // Algan HETRE_COMMUN : V = a * D^b * H^c
        val a = 0.0000362
        val b = 2.158
        val c = 0.860
        val expectedV = a * 30.0.pow(b) * 22.0.pow(c) + a * 30.0.pow(b) * 26.0.pow(c)
        assertNullableDoubleEquals(expectedV, row30.vSum, delta = 1e-6)
    }

    @Test
    fun `synthesisForEssence buckets diameters to nearest configured class (midpoint rule)`() = runTest {
        val json = Json { ignoreUnknownKeys = true }

        val coefs = listOf(CoefVolumeRange(essence = "HETRE", min = 0, max = 200, f = 0.5, method = null))
        val heightDefaults = listOf(HeightDefaultRange(essence = "HETRE", min = 0, max = 200, h = 20.0))
        val heightModes = emptyList<HeightModeEntry>()
        val rules = listOf(ProductRule(essence = "*", min = 0, max = null, product = "BO"))
        val prices = listOf(PriceEntry(essence = "HETRE", product = "BO", min = 0, max = 200, eurPerM3 = 100.0))

        val repo = FakeParameterRepository(
            mapOf(
                ParameterKeys.COEFS_VOLUME to json.encodeToString(coefs),
                ParameterKeys.HAUTEURS_DEFAUT to json.encodeToString(heightDefaults),
                ParameterKeys.HEIGHT_MODES to json.encodeToString(heightModes),
                ParameterKeys.RULES_PRODUITS to json.encodeToString(rules),
                ParameterKeys.PRIX_MARCHE to json.encodeToString(prices)
            )
        )
        val calculator = ForestryCalculator(repo)
        val params = calculator.loadSynthesisParams()

        val tiges = listOf(
            Tige(
                id = "1",
                parcelleId = "P",
                placetteId = null,
                essenceCode = "HETRE",
                diamCm = 32.0,
                hauteurM = 20.0,
                gpsWkt = null,
                precisionM = null,
                altitudeM = null,
                note = null,
                produit = null,
                fCoef = null,
                valueEur = null
            ),
            Tige(
                id = "2",
                parcelleId = "P",
                placetteId = null,
                essenceCode = "HETRE",
                diamCm = 33.0,
                hauteurM = 20.0,
                gpsWkt = null,
                precisionM = null,
                altitudeM = null,
                note = null,
                produit = null,
                fCoef = null,
                valueEur = null
            )
        )

        // With classes [30,35], midpoint boundary is 32.5: 32 -> 30, 33 -> 35
        val (rows, totals) = calculator.synthesisForEssence(
            essenceCode = "HETRE",
            classes = listOf(30, 35),
            tiges = tiges,
            manualHeights = null,
            method = null,
            params = params
        )

        assertEquals(2, totals.nTotal)
        assertEquals(1, rows.first { it.diamClass == 30 }.count)
        assertEquals(1, rows.first { it.diamClass == 35 }.count)
    }

    @Test
    fun `synthesisForEssence requireHeights uses defaults for class with no measured height giving full completeness`() = runTest {
        val json = Json { ignoreUnknownKeys = true }

        val coefs = listOf(CoefVolumeRange(essence = "HETRE", min = 0, max = 200, f = 0.5, method = null))
        // Height defaults cover both classes 30 and 35
        val heightDefaults = listOf(HeightDefaultRange(essence = "HETRE", min = 0, max = 200, h = 20.0))
        val heightModes = emptyList<HeightModeEntry>()
        val rules = listOf(ProductRule(essence = "*", min = 0, max = null, product = "BO"))
        val prices = listOf(PriceEntry(essence = "HETRE", product = "BO", min = 0, max = 200, eurPerM3 = 100.0))

        val repo = FakeParameterRepository(
            mapOf(
                ParameterKeys.COEFS_VOLUME to json.encodeToString(coefs),
                ParameterKeys.HAUTEURS_DEFAUT to json.encodeToString(heightDefaults),
                ParameterKeys.HEIGHT_MODES to json.encodeToString(heightModes),
                ParameterKeys.RULES_PRODUITS to json.encodeToString(rules),
                ParameterKeys.PRIX_MARCHE to json.encodeToString(prices)
            )
        )
        val calculator = ForestryCalculator(repo)
        val params = calculator.loadSynthesisParams()

        val tiges = listOf(
            // Class 30: measured height
            Tige(
                id = "1",
                parcelleId = "P",
                placetteId = null,
                essenceCode = "HETRE",
                diamCm = 30.0,
                hauteurM = 20.0,
                gpsWkt = null,
                precisionM = null,
                altitudeM = null,
                note = null,
                produit = null,
                fCoef = null,
                valueEur = null
            ),
            // Class 35: no measured height but defaults cover it → volume computed
            Tige(
                id = "2",
                parcelleId = "P",
                placetteId = null,
                essenceCode = "HETRE",
                diamCm = 35.0,
                hauteurM = null,
                gpsWkt = null,
                precisionM = null,
                altitudeM = null,
                note = null,
                produit = null,
                fCoef = null,
                valueEur = null
            )
        )

        val (rows, totals) = calculator.synthesisForEssence(
            essenceCode = "HETRE",
            classes = listOf(30, 35),
            tiges = tiges,
            manualHeights = null,
            method = null,
            params = params,
            requireHeights = true
        )

        // Both classes have height (class 30 measured, class 35 via default) → both volumes computed
        assertEquals(2, totals.nTotal)
        assertNotNull(totals.vTotal)
        assertNotNull(rows.first { it.diamClass == 30 }.vSum)
        assertNotNull(rows.first { it.diamClass == 35 }.vSum)
        assertEquals(2, totals.volumeComputedCount)
        assertEquals(2, totals.volumeExpectedCount)
        assertNullableDoubleEquals(100.0, totals.volumeCompletenessPct, delta = 1e-9)
    }

    @Test
    fun `synthesisForEssence requireHeights keeps partial volume when some classes have no defaults`() = runTest {
        val json = Json { ignoreUnknownKeys = true }

        val coefs = listOf(CoefVolumeRange(essence = "HETRE", min = 0, max = 200, f = 0.5, method = null))
        // Empty height defaults: class 30 will use its measured hauteurM, class 35 has nothing → partial
        val heightDefaults = emptyList<HeightDefaultRange>()
        val heightModes = emptyList<HeightModeEntry>()
        val rules = listOf(ProductRule(essence = "*", min = 0, max = null, product = "BO"))
        val prices = listOf(PriceEntry(essence = "HETRE", product = "BO", min = 0, max = 200, eurPerM3 = 100.0))

        val repo = FakeParameterRepository(
            mapOf(
                ParameterKeys.COEFS_VOLUME to json.encodeToString(coefs),
                ParameterKeys.HAUTEURS_DEFAUT to json.encodeToString(heightDefaults),
                ParameterKeys.HEIGHT_MODES to json.encodeToString(heightModes),
                ParameterKeys.RULES_PRODUITS to json.encodeToString(rules),
                ParameterKeys.PRIX_MARCHE to json.encodeToString(prices)
            )
        )
        val calculator = ForestryCalculator(repo)
        val params = calculator.loadSynthesisParams()

        val tiges = listOf(
            // Class 30: HAS a measured height → volume computed despite empty defaults
            Tige(
                id = "1",
                parcelleId = "P",
                placetteId = null,
                essenceCode = "HETRE",
                diamCm = 30.0,
                hauteurM = 20.0,
                gpsWkt = null,
                precisionM = null,
                altitudeM = null,
                note = null,
                produit = null,
                fCoef = null,
                valueEur = null
            ),
            // Class 35: no measured height, no defaults → null volume
            Tige(
                id = "2",
                parcelleId = "P",
                placetteId = null,
                essenceCode = "HETRE",
                diamCm = 35.0,
                hauteurM = null,
                gpsWkt = null,
                precisionM = null,
                altitudeM = null,
                note = null,
                produit = null,
                fCoef = null,
                valueEur = null
            )
        )

        val (rows, totals) = calculator.synthesisForEssence(
            essenceCode = "HETRE",
            classes = listOf(30, 35),
            tiges = tiges,
            manualHeights = null,
            method = null,
            params = params,
            requireHeights = true
        )

        assertEquals(2, totals.nTotal)
        assertNotNull(totals.vTotal)   // class 30 volume computed from measured height
        assertNotNull(rows.first { it.diamClass == 30 }.vSum)
        assertNullableDoubleEquals(rows.first { it.diamClass == 30 }.vSum, totals.vTotal, delta = 1e-9)
        assertEquals(null, rows.first { it.diamClass == 35 }.vSum)  // no height at all → null
        assertEquals(1, totals.volumeComputedCount)
        assertEquals(2, totals.volumeExpectedCount)
        assertNullableDoubleEquals(50.0, totals.volumeCompletenessPct, delta = 1e-9)
    }

    @Test
    fun `synthesisForEssence ignores method for volume under power-law model`() = runTest {
        val json = Json { ignoreUnknownKeys = true }

        val coefs = listOf(
            CoefVolumeRange(essence = "HETRE", min = 0, max = 200, f = 0.48, method = "LENT"),
            CoefVolumeRange(essence = "HETRE", min = 0, max = 200, f = 0.53, method = "RAPIDE")
        )
        val heightDefaults = listOf(HeightDefaultRange(essence = "HETRE", min = 0, max = 200, h = 20.0))
        val heightModes = emptyList<HeightModeEntry>()
        val rules = listOf(ProductRule(essence = "*", min = 0, max = null, product = "BO"))
        val prices = listOf(PriceEntry(essence = "HETRE", product = "BO", min = 0, max = 200, eurPerM3 = 100.0))

        val repo = FakeParameterRepository(
            mapOf(
                ParameterKeys.COEFS_VOLUME to json.encodeToString(coefs),
                ParameterKeys.HAUTEURS_DEFAUT to json.encodeToString(heightDefaults),
                ParameterKeys.HEIGHT_MODES to json.encodeToString(heightModes),
                ParameterKeys.RULES_PRODUITS to json.encodeToString(rules),
                ParameterKeys.PRIX_MARCHE to json.encodeToString(prices)
            )
        )
        val calculator = ForestryCalculator(repo)
        val params = calculator.loadSynthesisParams()

        val tiges = listOf(
            Tige(
                id = "1",
                parcelleId = "P",
                placetteId = null,
                essenceCode = "HETRE",
                diamCm = 30.0,
                hauteurM = 20.0,
                gpsWkt = null,
                precisionM = null,
                altitudeM = null,
                note = null,
                produit = null,
                fCoef = null,
                valueEur = null
            )
        )

        val (rowsLent, _) = calculator.synthesisForEssence(
            essenceCode = "HETRE",
            classes = listOf(30),
            tiges = tiges,
            manualHeights = null,
            method = "LENT",
            params = params
        )

        val (rowsRapide, _) = calculator.synthesisForEssence(
            essenceCode = "HETRE",
            classes = listOf(30),
            tiges = tiges,
            manualHeights = null,
            method = "RAPIDE",
            params = params
        )

        val vLent = rowsLent.first { it.diamClass == 30 }.vSum
        val vRapide = rowsRapide.first { it.diamClass == 30 }.vSum

        assertNotNull(vLent)
        assertNotNull(vRapide)
        assertNullableDoubleEquals(vLent, vRapide, delta = 1e-9)
    }

    @Test
    fun `synthesisForEssence matches params by essence alias for prices and heights`() = runTest {
        val json = Json { ignoreUnknownKeys = true }

        val coefs = listOf(CoefVolumeRange(essence = "HETRE_COMMUN", min = 0, max = 200, f = 0.5, method = null))
        val heightDefaults = listOf(HeightDefaultRange(essence = "HETRE_COMMUN", min = 0, max = 200, h = 20.0))
        val heightModes = emptyList<HeightModeEntry>()
        val rules = listOf(ProductRule(essence = "*", min = 0, max = null, product = "BO"))
        val prices = listOf(PriceEntry(essence = "HETRE_COMMUN", product = "BO", min = 0, max = 200, eurPerM3 = 100.0))

        val repo = FakeParameterRepository(
            mapOf(
                ParameterKeys.COEFS_VOLUME to json.encodeToString(coefs),
                ParameterKeys.HAUTEURS_DEFAUT to json.encodeToString(heightDefaults),
                ParameterKeys.HEIGHT_MODES to json.encodeToString(heightModes),
                ParameterKeys.RULES_PRODUITS to json.encodeToString(rules),
                ParameterKeys.PRIX_MARCHE to json.encodeToString(prices)
            )
        )
        val calculator = ForestryCalculator(repo)
        val params = calculator.loadSynthesisParams()

        val tiges = listOf(
            Tige(
                id = "1",
                parcelleId = "P",
                placetteId = null,
                essenceCode = "HETRE",
                diamCm = 40.0,
                hauteurM = null,
                gpsWkt = null,
                precisionM = null,
                altitudeM = null,
                note = null,
                produit = null,
                fCoef = null,
                valueEur = null
            )
        )

        val (rows, _) = calculator.synthesisForEssence(
            essenceCode = "HETRE",
            classes = listOf(40),
            tiges = tiges,
            manualHeights = null,
            method = null,
            params = params
        )

        val row40 = rows.first { it.diamClass == 40 }
        // Algan HETRE_COMMUN : V = a * D^b * H^c
        val a = 0.0000362
        val b = 2.158
        val c = 0.860
        val expectedV = a * 40.0.pow(b) * 20.0.pow(c)
        assertNullableDoubleEquals(expectedV, row40.vSum, delta = 1e-6)
        assertNullableDoubleEquals(expectedV * 100.0, row40.valueSumEur, delta = 1e-6)
    }
}
