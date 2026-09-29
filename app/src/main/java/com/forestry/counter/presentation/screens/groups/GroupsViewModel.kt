package com.forestry.counter.presentation.screens.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forestry.counter.domain.model.Group
import com.forestry.counter.domain.repository.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class GroupsViewModel(
    private val groupRepository: GroupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<GroupsUiState>(GroupsUiState.Loading)
    val uiState: StateFlow<GroupsUiState> = _uiState.asStateFlow()

    init {
        loadGroups()
    }

    private fun loadGroups() {
        viewModelScope.launch {
            groupRepository.getAllGroups().collect { groups ->
                _uiState.value = if (groups.isEmpty()) {
                    GroupsUiState.Empty
                } else {
                    GroupsUiState.Success(groups.sortedByDescending { it.updatedAt })
                }
            }
        }
    }

    fun createGroup(name: String, color: String? = null) {
        viewModelScope.launch {
            val group = Group(
                id = UUID.randomUUID().toString(),
                name = name,
                color = color
            )
            groupRepository.insertGroup(group)
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            groupRepository.deleteGroup(groupId)
        }
    }

    fun duplicateGroup(groupId: String) {
        viewModelScope.launch {
            groupRepository.duplicateGroup(groupId)
        }
    }

    fun renameGroup(groupId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val current = groupRepository.getGroupById(groupId).first() ?: return@launch
            groupRepository.updateGroup(
                current.copy(
                    name = trimmed,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun setGroupColor(groupId: String, colorHex: String?) {
        val raw = colorHex?.trim().orEmpty()
        val normalized = when {
            raw.isBlank() -> null
            raw.startsWith("#") -> raw.uppercase()
            raw.length == 6 -> "#${raw.uppercase()}"
            else -> raw.uppercase()
        }
        if (normalized != null && !normalized.matches(Regex("^#(?i)[0-9A-F]{6}$"))) return

        viewModelScope.launch {
            val current = groupRepository.getGroupById(groupId).first() ?: return@launch
            groupRepository.updateGroup(
                current.copy(
                    color = normalized,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }
}

sealed class GroupsUiState {
    object Loading : GroupsUiState()
    object Empty : GroupsUiState()
    data class Success(val groups: List<Group>) : GroupsUiState()
}
