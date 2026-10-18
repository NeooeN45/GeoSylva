package com.forestry.counter.domain.usecase.export

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Locale

@Serializable
enum class SpecializedReportType { REVENUE, COSTS, ENVIRONMENTAL }

/** Entered amounts are estimates or quotations, never evidence of a payment or realized cost. */
@Serializable
enum class SpecializedCostBasis { USER_ESTIMATE, USER_QUOTATION }

/** Units are explicit; this format does not convert hours, tonnes, hectares or cubic metres. */
@Serializable
enum class SpecializedCostUnit { HOUR, DAY, CUBIC_METRE, HECTARE, KILOMETRE, ITEM, FIXED_JOB }

/**
 * A user-declared operation captured with the export, outside the historical martelage snapshot.
 * Decimal strings preserve the entered precision and avoid binary floating point in money totals.
 * Missing quantity or price stays unknown, including when the other operand is explicitly zero.
 */
@Serializable
data class SpecializedCostLine(
    val id: String,
    val label: String,
    val quantity: String?,
    val unit: SpecializedCostUnit,
    val unitPriceEur: String?,
    val provenance: String,
    val priceDate: String? = null,
    val basis: SpecializedCostBasis = SpecializedCostBasis.USER_ESTIMATE,
) {
    init {
        require(id.isNotBlank() && id.length <= 200) { "SPECIALIZED_COST_ID_INVALID" }
        require(label.isNotBlank() && label.length <= 500) { "SPECIALIZED_COST_LABEL_INVALID" }
        require(provenance.isNotBlank() && provenance.length <= 2_000) { "SPECIALIZED_COST_PROVENANCE_INVALID" }
        priceDate?.let {
            require(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(it)) { "SPECIALIZED_COST_DATE_INVALID" }
            require(runCatching { LocalDate.parse(it) }.isSuccess) { "SPECIALIZED_COST_DATE_INVALID" }
        }
        val amount = quantity?.let(::specializedDecimal)
        unitPriceEur?.let(::specializedDecimal)
        require(unit != SpecializedCostUnit.FIXED_JOB || amount == null || amount.compareTo(BigDecimal.ONE) == 0) {
            "SPECIALIZED_FIXED_COST_QUANTITY_MUST_BE_ONE"
        }
        // Validate the individual amount before it can reach a renderer or a serialized archive.
        specializedCostAmountCents(this)
    }
}

/** Separate versioned envelope: no historical report, snapshot, JSON or PDF is rewritten. */
@Serializable
data class SpecializedSynthesisDocument(
    val type: SpecializedReportType,
    val illustrated: IllustratedSynthesisDocument,
    val costLines: List<SpecializedCostLine> = emptyList(),
    val formatVersion: Int = 1,
) {
    init {
        require(formatVersion == 1) { "SPECIALIZED_FORMAT_UNSUPPORTED" }
        require(type == SpecializedReportType.COSTS || costLines.isEmpty()) { "SPECIALIZED_COST_LINES_WRONG_REPORT_TYPE" }
        require(costLines.size <= 2_000) { "SPECIALIZED_TOO_MANY_COST_LINES" }
        require(costLines.map { it.id }.distinct().size == costLines.size) { "SPECIALIZED_DUPLICATE_COST_LINE" }
        specializedSumCents(costLines.map { specializedCostAmountCents(it) })
    }
}

data class SpecializedRevenueRow(
    val observationId: String,
    val essenceCode: String,
    val essenceName: String,
    val quality: Int?,
    val product: String?,
    val amountCents: Long?,
    val source: String?,
    /** Observation timestamp, not a market-price date. */
    val observedAt: Long,
)

data class SpecializedRevenueData(
    val rows: List<SpecializedRevenueRow>,
    val totalCents: Long?,
    val pricedCount: Int,
    val missingCount: Int,
    val invalidCount: Int,
    val observationCount: Int,
    /** Only completeness of entered observation values, not market or commercial coverage. */
    val isComplete: Boolean,
)

/**
 * Project only the individual values actually recorded in the captured observations.
 * No current price catalogue, volume multiplication, fiscal assumption or commercial stage is used.
 * Round each available observation amount once, HALF_UP to cents, then add those same displayed cents.
 */
fun buildSpecializedRevenueData(input: SpecializedSynthesisDocument): SpecializedRevenueData {
    val source = input.illustrated.document
    val names = source.report.perEssence.associate { it.code.trim().uppercase(Locale.ROOT) to it.name }
    val rows = source.observations.map { tree ->
        val code = tree.essenceCode.trim().uppercase(Locale.ROOT)
        val amount = tree.valueEur?.takeIf { it.isFinite() && it >= 0.0 }?.let {
            specializedMoneyCents(BigDecimal.valueOf(it))
        }
        SpecializedRevenueRow(tree.id, code, names[code] ?: tree.essenceCode,
            tree.qualite, tree.produit, amount, tree.source, tree.timestamp)
    }
    val priced = rows.count { it.amountCents != null }
    val missing = source.observations.count { it.valueEur == null }
    return SpecializedRevenueData(rows, specializedSumCents(rows.map { it.amountCents }), priced, missing,
        rows.size - priced - missing, rows.size, rows.isNotEmpty() && priced == rows.size)
}

data class SpecializedCostRow(val line: SpecializedCostLine, val amountCents: Long?)

data class SpecializedCostData(
    val rows: List<SpecializedCostRow>,
    val totalCents: Long?,
    val computedCount: Int,
    val missingCount: Int,
    val lineCount: Int,
    /** Completeness of these declared lines; does not establish an exhaustive exploitation budget. */
    val isComplete: Boolean,
)

fun buildSpecializedCostData(input: SpecializedSynthesisDocument): SpecializedCostData {
    val rows = input.costLines.map { SpecializedCostRow(it, specializedCostAmountCents(it)) }
    val computed = rows.count { it.amountCents != null }
    return SpecializedCostData(rows, specializedSumCents(rows.map { it.amountCents }), computed,
        rows.size - computed, rows.size, rows.isNotEmpty() && computed == rows.size)
}

data class SpecializedEnvironmentalData(
    val observationCount: Int,
    val habitatCount: Int,
    val categories: Map<String, Int>,
    val healthStates: Map<String, Int>,
    val qualityCount: Int,
    val locatedCount: Int,
    val measuredHeightCount: Int,
    /** Captured reference area, not evidence of the effective surveyed area. */
    val referenceAreaM2: Double?,
    val surfaceReason: String,
)

/** Descriptive observation counts only: no diagnostic score, stand extrapolation or prediction. */
fun buildSpecializedEnvironmentalData(input: SpecializedSynthesisDocument): SpecializedEnvironmentalData {
    val source = input.illustrated.document
    return SpecializedEnvironmentalData(
        observationCount = source.observations.size,
        habitatCount = source.observations.count { it.isTigeHabitat },
        categories = source.observations.mapNotNull { it.categorie?.takeIf(String::isNotBlank) }
            .groupingBy { it }.eachCount().toSortedMap(),
        healthStates = source.observations.mapNotNull { it.etatSanitaire?.takeIf(String::isNotBlank) }
            .groupingBy { it }.eachCount().toSortedMap(),
        qualityCount = source.report.qualityCount,
        locatedCount = source.report.locatedCount,
        measuredHeightCount = source.report.measuredHeightCount,
        referenceAreaM2 = source.report.surfaceM2,
        surfaceReason = source.report.surfaceReason,
    )
}

private fun specializedDecimal(value: String): BigDecimal {
    require(Regex("[0-9]{1,18}(?:\\.[0-9]{1,6})?").matches(value)) { "SPECIALIZED_DECIMAL_INVALID" }
    return BigDecimal(value)
}

private fun specializedCostAmountCents(line: SpecializedCostLine): Long? {
    val quantity = line.quantity?.let(::specializedDecimal)
    val price = line.unitPriceEur?.let(::specializedDecimal)
    if (quantity == null || price == null) return null
    return specializedMoneyCents(quantity.multiply(price))
}

private fun specializedMoneyCents(euros: BigDecimal): Long = try {
    euros.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
} catch (_: ArithmeticException) {
    throw IllegalArgumentException("SPECIALIZED_MONEY_OVERFLOW")
}

private fun specializedSumCents(amounts: List<Long?>): Long? {
    val available = amounts.filterNotNull()
    if (available.isEmpty()) return null
    return try {
        available.fold(0L) { total, amount -> Math.addExact(total, amount) }
    } catch (_: ArithmeticException) {
        throw IllegalArgumentException("SPECIALIZED_MONEY_OVERFLOW")
    }
}
