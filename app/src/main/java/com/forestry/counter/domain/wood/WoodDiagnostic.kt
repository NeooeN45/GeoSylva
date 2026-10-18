package com.forestry.counter.domain.wood

enum class WoodField {
    LENGTH,
    WIDTH,
    MEAN_HEIGHT,
    START_DIAMETER,
    MIDDLE_DIAMETER,
    END_DIAMETER,
    SHAPE,
    PROTOCOL,
    METHOD,
    BARK_CONVENTION,
    SECTION,
    RESULT,
}

/** Stable codes for UI/export translation; no parsing of calculator error messages. */
enum class WoodDiagnosticCode {
    MISSING_VALUE,
    INVALID_DECIMAL,
    MISSING_UNIT,
    UNKNOWN_UNIT,
    NEGATIVE_VALUE,
    ZERO_NOT_ALLOWED,
    MISSING_SHAPE,
    UNSUPPORTED_SHAPE,
    MISSING_PROTOCOL,
    MISSING_METHOD,
    UNSUPPORTED_METHOD,
    MISSING_BARK_CONVENTION,
    UNSUPPORTED_BARK_CONVENTION,
    UNUSED_SECTION_PRESENT,
    ZERO_SECTION,
    NUMERIC_INPUT_UNREPRESENTABLE,
    NUMERIC_RESULT_UNREPRESENTABLE,
}

data class WoodDiagnostic(val field: WoodField, val code: WoodDiagnosticCode)
