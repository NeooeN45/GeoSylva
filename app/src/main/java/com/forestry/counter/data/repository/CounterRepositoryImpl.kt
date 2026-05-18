package com.forestry.counter.data.repository

import com.forestry.counter.data.local.dao.CounterDao
import com.forestry.counter.data.local.dao.FormulaDao
import com.forestry.counter.data.local.dao.GroupVariableDao
import com.forestry.counter.data.mapper.toCounter
import com.forestry.counter.data.mapper.toCounterEntity
import com.forestry.counter.domain.calculator.FormulaParser
import com.forestry.counter.domain.model.Counter
import com.forestry.counter.domain.repository.CounterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

class CounterRepositoryImpl(
    private val counterDao: CounterDao,
    private val formulaDao: FormulaDao,
    private val groupVariableDao: GroupVariableDao,
    private val formulaParser: FormulaParser
) : CounterRepository {

    override fun getCountersByGroup(groupId: String): Flow<List<Counter>> {
        return counterDao.getCountersByGroup(groupId).map { entities ->
            entities.map { it.toCounter() }
        }
    }

    override fun getCounterById(counterId: String): Flow<Counter?> {
        return counterDao.getCounterByIdFlow(counterId).map { it?.toCounter() }
    }

    override suspend fun insertCounter(counter: Counter) {
        val sanitized = sanitize(counter)
        counterDao.insertCounter(sanitized.toCounterEntity())
    }

    override suspend fun updateCounter(counter: Counter) {
        val sanitized = sanitize(counter)
        counterDao.updateCounter(sanitized.toCounterEntity())
    }

    override suspend fun deleteCounter(counterId: String) {
        counterDao.deleteCounterById(counterId)
    }

    override suspend fun incrementCounter(counterId: String) {
        val counter = counterDao.getCounterById(counterId) ?: return
        if (counter.isComputed) return

        val newValue = counter.value + counter.step
        val constrainedValue = counter.max?.let { newValue.coerceAtMost(it) } ?: newValue

        counterDao.updateCounter(
            counter.copy(
                value = constrainedValue,
                lastUpdatedAt = System.currentTimeMillis()
            )
        )

        // Update computed counters in the same group
        updateComputedCounters(counter.groupOwnerId)
    }

    override suspend fun decrementCounter(counterId: String) {
        val counter = counterDao.getCounterById(counterId) ?: return
        if (counter.isComputed) return

        val newValue = counter.value - counter.step
        val constrainedValue = counter.min?.let { newValue.coerceAtLeast(it) } ?: newValue

        counterDao.updateCounter(
            counter.copy(
                value = constrainedValue,
                lastUpdatedAt = System.currentTimeMillis()
            )
        )

        // Update computed counters in the same group
        updateComputedCounters(counter.groupOwnerId)
    }

    override suspend fun resetCounter(counterId: String) {
        val counter = counterDao.getCounterById(counterId) ?: return
        if (counter.isComputed) return

        counterDao.updateCounter(
            counter.copy(
                value = counter.resetValue ?: counter.initialValue ?: 0.0,
                lastUpdatedAt = System.currentTimeMillis()
            )
        )

        // Update computed counters in the same group
        updateComputedCounters(counter.groupOwnerId)
    }

    override suspend fun duplicateCounter(counterId: String, count: Int): List<String> {
        val counter = counterDao.getCounterById(counterId) ?: return emptyList()
        val maxSortIndex = counterDao.getMaxSortIndex(counter.groupOwnerId) ?: 0

        val newIds = mutableListOf<String>()
        repeat(count) { index ->
            val newId = UUID.randomUUID().toString()
            newIds.add(newId)
            
            counterDao.insertCounter(
                counter.copy(
                    counterId = newId,
                    name = "${counter.name} (${index + 1})",
                    value = 0.0,
                    sortIndex = maxSortIndex + index + 1,
                    createdAt = System.currentTimeMillis()
                )
            )
        }

        return newIds
    }

    override suspend fun updateComputedCounters(groupId: String) {
        val allCounters = counterDao.getCountersByGroup(groupId).first()
        val computedCounters = allCounters.filter { it.isComputed && it.formulaId != null }
        
        if (computedCounters.isEmpty()) return

        val nonComputedDomain = allCounters.filter { !it.isComputed }.map { it.toCounter() }
        val variables = groupVariableDao.getVariablesByGroup(groupId).first()
            .associate { it.name to it.value }

        computedCounters.forEach { computed ->
            val formulaId = computed.formulaId ?: return@forEach
            val formula = formulaDao.getFormulaById(formulaId) ?: return@forEach
            
            val result = formulaParser.evaluate(
                expression = formula.expression,
                counters = nonComputedDomain,
                variables = variables
            )

            if (result is FormulaParser.ParseResult.Success) {
                counterDao.updateCounter(
                    computed.copy(
                        value = result.value,
                        lastUpdatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun sanitize(counter: Counter): Counter {
        // step must be > 0
        val step = if (counter.step <= 0.0 || counter.step.isNaN()) 1.0 else counter.step

        // sanitize min/max
        var minV = counter.min
        var maxV = counter.max
        if (minV != null && maxV != null && minV > maxV) {
            val t = minV; minV = maxV; maxV = t
        }

        // clamp value to bounds
        var value = counter.value
        if (minV != null) value = kotlin.math.max(value, minV)
        if (maxV != null) value = kotlin.math.min(value, maxV)

        // decimalPlaces bounds 0..6 (null = inherit/default)
        val dp = counter.decimalPlaces?.let { it.coerceIn(0, 6) }

        // vibration intensity bounds 1..3 or null
        val vibInt = counter.vibrationIntensity?.let { it.coerceIn(1, 3) }

        // sanitize color hex (#RRGGBB) or null
        val bg = counter.bgColor?.let { hex ->
            val h = hex.trim()
            if (h.matches(Regex("^#(?i)[0-9A-F]{6}$"))) h.uppercase() else null
        }

        return counter.copy(
            step = step,
            min = minV,
            max = maxV,
            value = value,
            decimalPlaces = dp,
            vibrationIntensity = vibInt,
            bgColor = bg
        )
    }
}
