package com.forestry.counter.domain.wood

import java.math.BigDecimal
import java.util.Collections

/**
 * Decimal text in its original unit and separate exact metric conversions.
 * Normalization does not assert the accuracy of a field measurement.
 */
class ParsedWoodDimension internal constructor(
    val raw: RawWoodDimension?,
    val decimalInOriginalUnit: BigDecimal?,
    val canonicalDecimal: String?,
    val metres: BigDecimal?,
    val centimetres: BigDecimal?,
    diagnostics: List<WoodDiagnostic>,
) {
    val diagnostics: List<WoodDiagnostic> = Collections.unmodifiableList(ArrayList(diagnostics))
}

/**
 * ASCII signed plain decimals with one dot or comma; outer whitespace is ignored
 * for parsing only. Thousands separators, exponent notation, NaN and infinity are
 * rejected. A missing or unknown unit never defaults to metres or centimetres.
 */
object WoodRawDecimalParser {
    const val CONTRACT_VERSION = "wood-raw-decimal-1"
    private val decimalPattern = Regex("[+-]?[0-9]+(?:[.,][0-9]+)?")

    fun parse(raw: RawWoodDimension?, field: WoodField): ParsedWoodDimension {
        val diagnostics = mutableListOf<WoodDiagnostic>()
        val text = raw?.rawText?.trim { it.isWhitespace() || it == '\u0085' }
        val decimal = when {
            text.isNullOrEmpty() -> {
                diagnostics += WoodDiagnostic(field, WoodDiagnosticCode.MISSING_VALUE)
                null
            }
            !decimalPattern.matches(text) -> {
                diagnostics += WoodDiagnostic(field, WoodDiagnosticCode.INVALID_DECIMAL)
                null
            }
            else -> BigDecimal(text.replace(',', '.'))
        }
        val metricShift = when (raw?.unit) {
            "m" -> 0
            "cm" -> 2
            "mm" -> 3
            null, "" -> {
                diagnostics += WoodDiagnostic(field, WoodDiagnosticCode.MISSING_UNIT)
                null
            }
            else -> {
                diagnostics += WoodDiagnostic(field, WoodDiagnosticCode.UNKNOWN_UNIT)
                null
            }
        }
        val metres = if (decimal != null && metricShift != null) decimal.movePointLeft(metricShift) else null
        return ParsedWoodDimension(
            raw = raw,
            decimalInOriginalUnit = decimal,
            canonicalDecimal = decimal?.stripTrailingZeros()?.toPlainString(),
            metres = metres,
            centimetres = metres?.movePointRight(2),
            diagnostics = diagnostics,
        )
    }
}
