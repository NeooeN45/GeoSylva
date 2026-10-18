package com.forestry.counter.presentation.screens.forestry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forestry.counter.domain.location.ForestSearchResult
import com.forestry.counter.domain.model.Foret
import com.forestry.counter.domain.repository.ForetRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** Local forest draft; no owner, location or management information is inferred. */
class CreateForestViewModel(
    private val foretRepository: ForetRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val forestLookup: suspend (String) -> List<ForestSearchResult> = { emptyList() },
) : ViewModel() {
    private val _formState = MutableStateFlow(
        CreateForestFormState(
            nom = savedStateHandle[NAME] ?: "",
            proprietaireNom = savedStateHandle[OWNER] ?: "",
            proprietaireEmail = savedStateHandle[EMAIL],
            gestionnaireNom = savedStateHandle[MANAGER],
            typeForet = savedStateHandle[TYPE],
            objectifGestion = savedStateHandle[OBJECTIVE],
            psgNumero = savedStateHandle[PLAN],
            departement = savedStateHandle[DEPARTMENT],
            remarques = savedStateHandle[REMARKS],
            currentStep = (savedStateHandle.get<Int>(STEP) ?: 0).coerceIn(0, 2),
        ),
    )
    val formState: StateFlow<CreateForestFormState> = _formState.asStateFlow()

    private val _isSaved = MutableStateFlow(savedStateHandle[SAVED] ?: false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    private val _saveError = MutableStateFlow(false)
    val saveError: StateFlow<Boolean> = _saveError.asStateFlow()
    private val _lookupState = MutableStateFlow(
        CreateForestLookupState(
            selected = savedStateHandle.get<String>(LOOKUP_ID)?.let { id ->
                ForestSearchResult(
                    id = id,
                    name = savedStateHandle[LOOKUP_NAME] ?: _formState.value.nom,
                    department = savedStateHandle[LOOKUP_DEPARTMENT],
                    geometry = null,
                    center = null,
                    sourceLabel = savedStateHandle[LOOKUP_SOURCE] ?: "",
                    sourceDataset = savedStateHandle[LOOKUP_DATASET],
                )
            },
        ),
    )
    val lookupState: StateFlow<CreateForestLookupState> = _lookupState.asStateFlow()
    private val _selectedForest = MutableStateFlow(_lookupState.value.selected)
    val selectedForest: StateFlow<ForestSearchResult?> = _selectedForest.asStateFlow()
    private var lookupJob: Job? = null
    private var lookupGeneration = 0L

    // Retries of this draft keep its identity instead of creating another forest.
    private val forestId = savedStateHandle.get<String>(ID) ?: UUID.randomUUID().toString().also {
        savedStateHandle[ID] = it
    }
    val createdForestId: String get() = forestId

    fun updateNom(nom: String) {
        if (_isSaving.value || _isSaved.value || _formState.value.nom == nom) return
        update { copy(nom = nom) }
        requestLookup(nom)
    }

    fun retryLookup() {
        if (_lookupState.value.hasError && !_isSaving.value && !_isSaved.value) requestLookup(_formState.value.nom)
    }

    private fun requestLookup(nom: String) {
        lookupJob?.cancel()
        val generation = ++lookupGeneration
        clearSelectedForest()
        val query = nom.trim()
        if (query.length < 3) {
            _lookupState.value = CreateForestLookupState(query = query)
            return
        }
        _lookupState.value = CreateForestLookupState(query = query, isLoading = true)
        lookupJob = viewModelScope.launch {
            delay(350)
            try {
                val suggestions = forestLookup(query)
                ensureActive()
                if (generation != lookupGeneration || _isSaving.value || _isSaved.value) return@launch
                _lookupState.value = CreateForestLookupState(
                    query = query,
                    suggestions = suggestions.filter { it.id.isNotBlank() && it.name.isNotBlank() }.distinctBy { it.id }.take(6),
                    hasSearched = true,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == lookupGeneration && !_isSaving.value && !_isSaved.value) {
                    _lookupState.value = CreateForestLookupState(query = query, hasError = true)
                }
            } finally {
                if (generation == lookupGeneration && _lookupState.value.isLoading) {
                    _lookupState.value = _lookupState.value.copy(isLoading = false)
                }
            }
        }
    }

    fun selectForestSuggestion(candidate: ForestSearchResult) {
        val lookup = _lookupState.value
        if (lookup.query != _formState.value.nom.trim()) return
        val current = lookup.suggestions.firstOrNull { it.id == candidate.id && it.name == candidate.name } ?: return
        selectForest(current)
    }

    /** The map passes its real selected feature; no proximity-based automatic acceptance. */
    fun selectForest(selected: ForestSearchResult) {
        if (_isSaving.value || _isSaved.value || selected.id.isBlank() || selected.name.isBlank()) return
        lookupJob?.cancel()
        lookupGeneration++
        update {
            copy(
                nom = selected.name,
                departement = selected.department?.takeIf { it.isNotBlank() } ?: departement,
            )
        }
        savedStateHandle[LOOKUP_ID] = selected.id
        savedStateHandle[LOOKUP_NAME] = selected.name
        savedStateHandle[LOOKUP_DEPARTMENT] = selected.department
        savedStateHandle[LOOKUP_SOURCE] = selected.sourceLabel
        savedStateHandle[LOOKUP_DATASET] = selected.sourceDataset
        _lookupState.value = CreateForestLookupState(query = selected.name, selected = selected)
        _selectedForest.value = selected
    }

    private fun clearSelectedForest() {
        _selectedForest.value = null
        savedStateHandle.remove<String>(LOOKUP_ID)
        savedStateHandle.remove<String>(LOOKUP_NAME)
        savedStateHandle.remove<String>(LOOKUP_DEPARTMENT)
        savedStateHandle.remove<String>(LOOKUP_SOURCE)
        savedStateHandle.remove<String>(LOOKUP_DATASET)
    }

    fun updateProprietaireNom(nom: String) = update { copy(proprietaireNom = nom) }
    fun updateProprietaireEmail(email: String) = update { copy(proprietaireEmail = email.ifBlank { null }) }
    fun updateGestionnaireNom(nom: String) = update { copy(gestionnaireNom = nom.ifBlank { null }) }
    fun updateTypeForet(type: String) = update { copy(typeForet = type.ifBlank { null }) }
    fun updateObjectifGestion(objectif: String) = update { copy(objectifGestion = objectif.ifBlank { null }) }
    fun updateDepartement(dept: String) = update { copy(departement = dept.ifBlank { null }) }
    fun updatePsgNumero(numero: String) = update { copy(psgNumero = numero.ifBlank { null }) }
    fun updateRemarques(remarques: String) = update { copy(remarques = remarques.ifBlank { null }) }

    fun nextStep() {
        val form = _formState.value
        if (form.canAdvance && form.currentStep < 2) update { copy(currentStep = currentStep + 1) }
    }

    fun previousStep() {
        if (_formState.value.currentStep > 0) update { copy(currentStep = currentStep - 1) }
    }

    private fun update(change: CreateForestFormState.() -> CreateForestFormState) {
        if (_isSaving.value || _isSaved.value) return
        val form = _formState.value.change()
        _formState.value = form
        _saveError.value = false
        savedStateHandle[NAME] = form.nom
        savedStateHandle[OWNER] = form.proprietaireNom
        savedStateHandle[EMAIL] = form.proprietaireEmail
        savedStateHandle[MANAGER] = form.gestionnaireNom
        savedStateHandle[TYPE] = form.typeForet
        savedStateHandle[OBJECTIVE] = form.objectifGestion
        savedStateHandle[PLAN] = form.psgNumero
        savedStateHandle[DEPARTMENT] = form.departement
        savedStateHandle[REMARKS] = form.remarques
        savedStateHandle[STEP] = form.currentStep
    }

    fun save() {
        val form = _formState.value
        if (_isSaving.value || _isSaved.value || !form.isValid) return
        if (_selectedForest.value?.let { it.sourceDataset != null && it.geometry == null && it.center == null } == true) {
            _saveError.value = true
            return
        }
        val selected = _selectedForest.value?.takeIf { it.geometry != null }
        // Set synchronously: two taps before the coroutine runs still insert once.
        _isSaving.value = true
        _saveError.value = false
        lookupJob?.cancel()
        lookupGeneration++
        _lookupState.value = _lookupState.value.copy(isLoading = false, suggestions = emptyList())
        viewModelScope.launch {
            try {
                foretRepository.insert(
                    Foret(
                        foretId = forestId,
                        nom = form.nom.trim(),
                        proprietaireNom = form.proprietaireNom.trim(),
                        proprietaireEmail = form.proprietaireEmail?.trim()?.ifBlank { null },
                        gestionnaireNom = form.gestionnaireNom?.trim()?.ifBlank { null },
                        typeForet = form.typeForet?.trim()?.ifBlank { null },
                        objectifGestion = form.objectifGestion?.trim()?.ifBlank { null },
                        psgNumero = form.psgNumero?.trim()?.ifBlank { null },
                        psgDateExpiration = null,
                        departement = form.departement?.trim()?.ifBlank { null },
                        remarques = form.remarques?.trim()?.ifBlank { null },
                        shape = selected?.geometry?.toWkt(),
                        sourceForestId = selected?.id,
                        sourceForestDataset = selected?.sourceDataset,
                    ),
                )
                savedStateHandle[SAVED] = true
                _isSaved.value = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the complete draft; do not expose storage details or personal data.
                _saveError.value = true
            } finally {
                _isSaving.value = false
            }
        }
    }

    private companion object {
        const val NAME = "forest_creation_name"
        const val OWNER = "forest_creation_owner"
        const val EMAIL = "forest_creation_email"
        const val MANAGER = "forest_creation_manager"
        const val TYPE = "forest_creation_type"
        const val OBJECTIVE = "forest_creation_objective"
        const val PLAN = "forest_creation_plan"
        const val DEPARTMENT = "forest_creation_department"
        const val REMARKS = "forest_creation_remarks"
        const val STEP = "forest_creation_step"
        const val ID = "forest_creation_id"
        const val SAVED = "forest_creation_saved"
        const val LOOKUP_ID = "forest_creation_lookup_id"
        const val LOOKUP_NAME = "forest_creation_lookup_name"
        const val LOOKUP_DEPARTMENT = "forest_creation_lookup_department"
        const val LOOKUP_SOURCE = "forest_creation_lookup_source"
        const val LOOKUP_DATASET = "forest_creation_lookup_dataset"
    }
}

data class CreateForestFormState(
    val nom: String = "",
    val proprietaireNom: String = "",
    val proprietaireEmail: String? = null,
    val gestionnaireNom: String? = null,
    val typeForet: String? = null,
    val objectifGestion: String? = null,
    val psgNumero: String? = null,
    val departement: String? = null,
    val remarques: String? = null,
    val currentStep: Int = 0,
) {
    val ownerEmailValid: Boolean
        get() = proprietaireEmail.isNullOrBlank() || OPTIONAL_EMAIL.matches(proprietaireEmail.trim())
    val isValid: Boolean get() = nom.isNotBlank() && ownerEmailValid
    val step1Valid: Boolean get() = nom.isNotBlank()
    val step2Valid: Boolean get() = ownerEmailValid
    val step3Valid: Boolean get() = true
    val canAdvance: Boolean
        get() = when (currentStep) {
            0 -> step1Valid
            1 -> step2Valid
            else -> isValid
        }

    private companion object {
        val OPTIONAL_EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}

/** A suggestion is never accepted until the user explicitly selects it. */
data class CreateForestLookupState(
    val query: String = "",
    val isLoading: Boolean = false,
    val suggestions: List<ForestSearchResult> = emptyList(),
    val selected: ForestSearchResult? = null,
    val hasError: Boolean = false,
    val hasSearched: Boolean = false,
)
