package com.forestry.counter.presentation.screens.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forestry.counter.domain.model.Counter
import com.forestry.counter.domain.model.Formula
import com.forestry.counter.domain.model.Group
import com.forestry.counter.domain.model.TileSize
import com.forestry.counter.domain.repository.CounterRepository
import com.forestry.counter.domain.repository.GroupRepository
import com.forestry.counter.domain.repository.FormulaRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class GroupViewModel(
    private val groupId: String,
    private val groupRepository: GroupRepository,
    private val counterRepository: CounterRepository,
    private val formulaRepository: FormulaRepository
) : ViewModel() {

    private val _group = MutableStateFlow<Group?>(null)
    val group: StateFlow<Group?> = _group.asStateFlow()

    private val _counters = MutableStateFlow<List<Counter>>(emptyList())
    val counters: StateFlow<List<Counter>> = _counters.asStateFlow()

    private val _selectedCounterId = MutableStateFlow<String?>(null)
    val selectedCounterId: StateFlow<String?> = _selectedCounterId.asStateFlow()

    init {
        loadGroup()
        loadCounters()
    }

    private fun loadGroup() {
        viewModelScope.launch {
            groupRepository.getGroupById(groupId).collect { group ->
                _group.value = group
            }
        }
    }

    private fun loadCounters() {
        viewModelScope.launch {
            counterRepository.getCountersByGroup(groupId).collect { counters ->
                _counters.value = counters
            }
        }
    }

    fun createCounter(
        id: String = java.util.UUID.randomUUID().toString(),
        name: String,
        step: Double = 1.0,
        min: Double? = null,
        max: Double? = null,
        targetValue: Double? = null,
        bgColor: String? = null,
        expression: String? = null,
        tileSize: TileSize = TileSize.NORMAL
    ) {
        viewModelScope.launch {
            var formulaId: String? = null
            val isComputed = !expression.isNullOrBlank()
            if (isComputed) {
                val formula = Formula(
                    id = UUID.randomUUID().toString(),
                    groupId = groupId,
                    name = "Formula for $name",
                    expression = expression?.trim() ?: "",
                    description = null
                )
                formulaRepository.insertFormula(formula)
                formulaId = formula.id
            }

            val counter = Counter(
                id = id,
                groupId = groupId,
                name = name,
                step = step,
                min = min,
                max = max,
                targetValue = targetValue,
                bgColor = bgColor,
                isComputed = isComputed,
                formulaId = formulaId,
                tileSize = tileSize
            )
            counterRepository.insertCounter(counter)
            if (isComputed) {
                counterRepository.updateComputedCounters(groupId)
            }
        }
    }

    fun incrementCounter(counterId: String) {
        viewModelScope.launch {
            counterRepository.incrementCounter(counterId)
        }
    }

    fun decrementCounter(counterId: String) {
        viewModelScope.launch {
            counterRepository.decrementCounter(counterId)
        }
    }

    fun resetCounter(counterId: String) {
        viewModelScope.launch {
            counterRepository.resetCounter(counterId)
        }
    }

    fun deleteCounter(counterId: String) {
        viewModelScope.launch {
            counterRepository.deleteCounter(counterId)
        }
    }

    fun duplicateCounter(counterId: String, count: Int = 1) {
        viewModelScope.launch {
            counterRepository.duplicateCounter(counterId, count)
        }
    }

    fun updateCounter(counter: Counter) {
        viewModelScope.launch {
            counterRepository.updateCounter(counter)
        }
    }

    fun updateCounterWithExpression(counter: Counter, expression: String?) {
        viewModelScope.launch {
            counterRepository.updateCounter(counter)
            if (counter.isComputed && !expression.isNullOrBlank() && counter.formulaId != null) {
                val existing = formulaRepository.getFormulaById(counter.formulaId).first()
                if (existing != null) {
                    formulaRepository.updateFormula(existing.copy(expression = expression.trim()))
                    counterRepository.updateComputedCounters(groupId)
                }
            }
        }
    }

    fun selectCounter(counterId: String?) {
        _selectedCounterId.value = counterId
    }
}
