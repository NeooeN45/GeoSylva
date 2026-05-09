package com.forestry.counter.domain.calculator

import com.forestry.counter.domain.model.Counter
import com.forestry.counter.domain.model.GroupVariable
import net.objecthunter.exp4j.ExpressionBuilder
import kotlin.math.PI
import kotlin.math.E

/**
 * Advanced formula parser supporting:
 * - Basic operators: + - * / % ^ ()
 * - Functions: sum, avg, min, max, count
 * - Conditions: if(cond, a, b)
 * - Filters: name:startsWith, name:endsWith, name:contains, tag:
 * - Constants: PI, E, custom variables
 * - Counter references: [CounterName]
 */
class FormulaParser {

    sealed class ParseResult {
        data class Success(val value: Double) : ParseResult()
        data class Error(val message: String) : ParseResult()
    }

    /**
     * Evaluate a formula expression with counter context
     */
    fun evaluate(
        expression: String,
        counters: List<Counter>,
        variables: Map<String, Double> = emptyMap()
    ): ParseResult {
        return try {
            val processedExpression = preprocessExpression(expression, counters, variables)
            val result = ExpressionBuilder(processedExpression)
                .variables(variables.keys + setOf("PI", "E"))
                .build()
                .apply {
                    variables.forEach { (name, value) ->
                        setVariable(name, value)
                    }
                    setVariable("PI", PI)
                    setVariable("E", E)
                }
                .evaluate()
            
            ParseResult.Success(result)
        } catch (e: Exception) {
            ParseResult.Error("Error evaluating expression: ${e.message}")
        }
    }

    /**
     * Preprocess the expression to handle custom functions and filters
     */
    private fun preprocessExpression(
        expression: String,
        counters: List<Counter>,
        _variables: Map<String, Double>
    ): String {
        var processed = expression

        // Handle counter references [CounterName]
        val counterRefPattern = """\[([^\]]+)]""".toRegex()
        counterRefPattern.findAll(expression).forEach { match ->
            val counterName = match.groupValues[1]
            val counter = counters.find { it.name.equals(counterName, ignoreCase = true) }
            val value = counter?.value ?: 0.0
            processed = processed.replace(match.value, value.toString())
        }

        // Handle sum() function
        processed = processSumFunction(processed, counters)

        // Handle avg() function
        processed = processAvgFunction(processed, counters)

        // Handle min() function
        processed = processMinFunction(processed, counters)

        // Handle max() function
        processed = processMaxFunction(processed, counters)

        // Handle count() function
        processed = processCountFunction(processed, counters)

        // Handle if() function - convert to ternary or evaluate
        processed = processIfFunction(processed)

        // Forestry and conversion helpers
        processed = processForestryFunctions(processed)

        return processed
    }

    private fun processForestryFunctions(expression: String): String {
        var s = expression

        // Conversions
        s = s.replaceFunction1Arg("cm_to_m") { x -> "(($x)/100.0)" }
        s = s.replaceFunction1Arg("m2_to_ha") { x -> "(($x)/10000.0)" }
        s = s.replaceFunction1Arg("ha_to_m2") { x -> "(($x)*10000.0)" }

        // Forestry basics
        // g(d) = PI*(d/200)^2
        s = s.replaceFunction1Arg("g") { d -> "(PI*pow((($d)/200.0),2))" }
        // vol(d,h,ff) = PI*(d/200)^2*h*ff
        s = s.replaceFunction3Args("vol") { d, h, ff -> "(PI*pow((($d)/200.0),2)*($h)*($ff))" }
        // vol_ha(V_plot,A_plot) = V_plot * (10000 / A_plot)
        s = s.replaceFunction2Args("vol_ha") { v, a -> "(($v)*(10000.0/($a)))" }
        // N_ha(N_plot,A_plot) = N_plot * (10000 / A_plot)
        s = s.replaceFunction2Args("N_ha") { n, a -> "(($n)*(10000.0/($a)))" }
        // E(N_ha) = sqrt(10000/N_ha)
        s = s.replaceFunction1Arg("E") { nha -> "(sqrt(10000.0/($nha)))" }
        // S_percent(E,Hdom) = (E/Hdom)*100
        s = s.replaceFunction2Args("S_percent") { e, hdom -> "((($e)/($hdom))*100.0)" }
        // SDI(N_ha,Dg) = N_ha * (Dg/25)^1.605
        s = s.replaceFunction2Args("SDI") { nha, dg -> "(($nha)*pow((($dg)/25.0),1.605))" }

        return s
    }

    // Helpers to replace function calls by regex (simple, non-nested args handling)
    private fun String.replaceFunction1Arg(name: String, build: (String) -> String): String {
        val regex = Regex("""$name\(([^)]+)\)""")
        var out = this
        regex.findAll(this).forEach { m ->
            val a = m.groupValues[1].trim()
            out = out.replace(m.value, build(a))
        }
        return out
    }

    private fun String.replaceFunction2Args(name: String, build: (String, String) -> String): String {
        val regex = Regex("""$name\(([^,]+),([^\)]+)\)""")
        var out = this
        regex.findAll(this).forEach { m ->
            val a = m.groupValues[1].trim()
            val b = m.groupValues[2].trim()
            out = out.replace(m.value, build(a, b))
        }
        return out
    }

    private fun String.replaceFunction3Args(name: String, build: (String, String, String) -> String): String {
        val regex = Regex("""$name\(([^,]+),([^,]+),([^\)]+)\)""")
        var out = this
        regex.findAll(this).forEach { m ->
            val a = m.groupValues[1].trim()
            val b = m.groupValues[2].trim()
            val c = m.groupValues[3].trim()
            out = out.replace(m.value, build(a, b, c))
        }
        return out
    }

    private fun processSumFunction(expression: String, counters: List<Counter>): String {
        return processFunctionCalls(expression, "sum") { filter ->
            val filteredCounters = applyFilter(filter, counters)
            filteredCounters.sumOf { it.value }.toString()
        }
    }

    private fun processAvgFunction(expression: String, counters: List<Counter>): String {
        return processFunctionCalls(expression, "avg") { filter ->
            val filteredCounters = applyFilter(filter, counters)
            val avg = if (filteredCounters.isNotEmpty()) {
                filteredCounters.sumOf { it.value } / filteredCounters.size
            } else {
                0.0
            }
            avg.toString()
        }
    }

    private fun processMinFunction(expression: String, counters: List<Counter>): String {
        return processFunctionCalls(expression, "min") { filter ->
            val filteredCounters = applyFilter(filter, counters)
            (filteredCounters.minOfOrNull { it.value } ?: 0.0).toString()
        }
    }

    private fun processMaxFunction(expression: String, counters: List<Counter>): String {
        return processFunctionCalls(expression, "max") { filter ->
            val filteredCounters = applyFilter(filter, counters)
            (filteredCounters.maxOfOrNull { it.value } ?: 0.0).toString()
        }
    }

    private fun processCountFunction(expression: String, counters: List<Counter>): String {
        return processFunctionCalls(expression, "count") { filter ->
            val filteredCounters = applyFilter(filter, counters)
            filteredCounters.size.toString()
        }
    }

    private fun processFunctionCalls(
        expression: String,
        functionName: String,
        compute: (String) -> String
    ): String {
        var s = expression
        var fromIndex = 0
        while (true) {
            val start = findFunctionCallStart(s, functionName, fromIndex)
            if (start == -1) break

            val openIndex = start + functionName.length
            if (openIndex >= s.length || s[openIndex] != '(') {
                fromIndex = start + functionName.length
                continue
            }
            val end = findMatchingParenIndex(s, openIndex)
            if (end == -1) break

            val arg = s.substring(openIndex + 1, end)
            val replacement = compute(arg)
            s = s.substring(0, start) + replacement + s.substring(end + 1)
            fromIndex = start + replacement.length
        }
        return s
    }

    private fun findFunctionCallStart(s: String, functionName: String, fromIndex: Int): Int {
        val needle = "$functionName("
        var idx = s.indexOf(needle, startIndex = fromIndex)
        while (idx != -1) {
            val beforeOk = idx == 0 || !isIdentifierChar(s[idx - 1])
            if (beforeOk) return idx
            idx = s.indexOf(needle, startIndex = idx + 1)
        }
        return -1
    }

    private fun findMatchingParenIndex(s: String, openIndex: Int): Int {
        var depth = 0
        var quote: Char? = null
        var i = openIndex
        while (i < s.length) {
            val c = s[i]
            if (quote != null) {
                if (c == quote) quote = null
                i += 1
                continue
            }
            if (c == '\'' || c == '"') {
                quote = c
                i += 1
                continue
            }
            when (c) {
                '(' -> depth += 1
                ')' -> {
                    depth -= 1
                    if (depth == 0) return i
                }
            }
            i += 1
        }
        return -1
    }

    private fun isIdentifierChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'

    private fun processIfFunction(expression: String): String {
        // Basic if() to ternary conversion - simplified for exp4j
        // if(a>b, x, y) -> ((a>b) ? x : y)
        // Note: exp4j doesn't support ternary, so we'll evaluate conditions directly
        val ifPattern = """if\(([^,]+),([^,]+),([^)]+)\)""".toRegex()
        var processed = expression

        ifPattern.findAll(expression).forEach { match ->
            val condition = match.groupValues[1].trim()
            val trueValue = match.groupValues[2].trim()
            val falseValue = match.groupValues[3].trim()

            // Try to evaluate the condition
            val result = evaluateCondition(condition, trueValue, falseValue)
            processed = processed.replace(match.value, result)
        }

        return processed
    }

    private fun evaluateCondition(condition: String, trueValue: String, falseValue: String): String {
        // Parse simple conditions like "x > y", "x == y", etc.
        val operators = listOf(">=", "<=", "==", "!=", ">", "<")
        
        for (op in operators) {
            if (condition.contains(op)) {
                val parts = condition.split(op).map { it.trim() }
                if (parts.size == 2) {
                    try {
                        val left = parts[0].toDoubleOrNull() ?: 0.0
                        val right = parts[1].toDoubleOrNull() ?: 0.0
                        
                        val result = when (op) {
                            ">" -> left > right
                            "<" -> left < right
                            ">=" -> left >= right
                            "<=" -> left <= right
                            "==" -> left == right
                            "!=" -> left != right
                            else -> false
                        }
                        
                        return if (result) trueValue else falseValue
                    } catch (e: Exception) {
                        // If parsing fails, return false value
                    }
                }
            }
        }
        
        return falseValue
    }

    /**
     * Apply filter to counters
     * Supported filters:
     * - "*" : all counters
     * - "name:startsWith('X')"
     * - "name:endsWith('X')"
     * - "name:contains('X')"
     * - "tag:'X'"
     */
    private fun applyFilter(filter: String, counters: List<Counter>): List<Counter> {
        val trimmedFilter = filter.trim()

        // All counters
        if (trimmedFilter == "*") {
            return counters.filter { !it.isComputed }
        }

        // Name filters
        if (trimmedFilter.startsWith("name:")) {
            val filterPart = trimmedFilter.substringAfter("name:")
            
            if (filterPart.startsWith("startsWith")) {
                val pattern = extractStringPattern(filterPart)
                return counters.filter { !it.isComputed && it.name.startsWith(pattern, ignoreCase = true) }
            }
            
            if (filterPart.startsWith("endsWith")) {
                val pattern = extractStringPattern(filterPart)
                return counters.filter { !it.isComputed && it.name.endsWith(pattern, ignoreCase = true) }
            }
            
            if (filterPart.startsWith("contains")) {
                val pattern = extractStringPattern(filterPart)
                return counters.filter { !it.isComputed && it.name.contains(pattern, ignoreCase = true) }
            }
        }

        // Tag filter
        if (trimmedFilter.startsWith("tag:")) {
            val tag = extractStringPattern(trimmedFilter.substringAfter("tag:"))
            return counters.filter { !it.isComputed && it.tags.any { t -> t.equals(tag, ignoreCase = true) } }
        }

        // Default: return empty
        return emptyList()
    }

    private fun extractStringPattern(input: String): String {
        // Extract string from 'string' or "string" format
        val pattern = """['"]([^'"]+)['"]""".toRegex()
        val match = pattern.find(input)
        return match?.groupValues?.get(1) ?: input.trim()
    }

    /**
     * Validate expression syntax
     */
    fun validate(expression: String): ValidationResult {
        if (expression.isBlank()) {
            return ValidationResult.Error("Expression cannot be empty")
        }

        try {
            // Basic syntax check
            val parenBalance = expression.count { it == '(' } - expression.count { it == ')' }
            if (parenBalance != 0) {
                return ValidationResult.Error("Unbalanced parentheses")
            }

            // Check for valid function names
            val functionPattern = """(\w+)\(""".toRegex()
            val validFunctions = setOf("sum", "avg", "min", "max", "count", "if", "sqrt", "sin", "cos", "tan", "log", "abs")
            functionPattern.findAll(expression).forEach { match ->
                val functionName = match.groupValues[1]
                if (functionName !in validFunctions) {
                    return ValidationResult.Warning("Unknown function: $functionName")
                }
            }

            return ValidationResult.Valid
        } catch (e: Exception) {
            return ValidationResult.Error("Invalid expression: ${e.message}")
        }
    }

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Warning(val message: String) : ValidationResult()
        data class Error(val message: String) : ValidationResult()
    }
}
