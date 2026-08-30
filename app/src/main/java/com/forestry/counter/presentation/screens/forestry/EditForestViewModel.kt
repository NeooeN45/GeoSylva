package com.forestry.counter.presentation.screens.forestry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forestry.counter.domain.model.Foret
import com.forestry.counter.domain.model.Group
import com.forestry.counter.domain.repository.ForetRepository
import com.forestry.counter.domain.repository.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel pour l'édition d'une forêt existante — réutilise [CreateForestFormState]
 * et le même wizard [CreateForestWizard] (isEditing = true).
 *
 * Charge la forêt existante, pré-remplit le formulaire, puis enregistre
 * via [ForetRepository.update]. Met aussi à jour le [Group] lié si présent.
 */
class EditForestViewModel(
    private val foretRepository: ForetRepository,
    private val groupRepository: GroupRepository?,
    private val foretId: String,
) : ViewModel(), ForestFormViewModel {

    private val _formState = MutableStateFlow(CreateForestFormState())
    override val formState: StateFlow<CreateForestFormState> = _formState.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    override val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    private var originalForet: Foret? = null

    init {
        viewModelScope.launch {
            val foret = foretRepository.getById(foretId) ?: return@launch
            originalForet = foret
            _formState.value = CreateForestFormState(
                nom = foret.nom,
                proprietaireNom = foret.proprietaireNom,
                proprietaireEmail = foret.proprietaireEmail,
                gestionnaireNom = foret.gestionnaireNom,
                typeForet = foret.typeForet,
                objectifGestion = foret.objectifGestion,
                psgNumero = foret.psgNumero,
                departements = foret.departement
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?: emptyList(),
                remarques = foret.remarques,
                // La couleur de la forêt est portée par le Group lié, pas par Foret
                color = null,
            )
            // Charger la couleur depuis le Group lié si disponible
            groupRepository?.getByForetId(foretId)?.let { group ->
                _formState.value = _formState.value.copy(color = group.color)
            }
        }
    }

    override fun updateNom(nom: String) { _formState.value = _formState.value.copy(nom = nom) }
    override fun updateProprietaireNom(nom: String) { _formState.value = _formState.value.copy(proprietaireNom = nom) }
    override fun updateProprietaireEmail(email: String) { _formState.value = _formState.value.copy(proprietaireEmail = email.ifBlank { null }) }
    override fun updateGestionnaireNom(nom: String) { _formState.value = _formState.value.copy(gestionnaireNom = nom.ifBlank { null }) }
    override fun updateTypeForet(type: String) { _formState.value = _formState.value.copy(typeForet = type.ifBlank { null }) }
    override fun updateObjectifGestion(objectif: String) { _formState.value = _formState.value.copy(objectifGestion = objectif.ifBlank { null }) }
    override fun updateDepartements(depts: List<String>) { _formState.value = _formState.value.copy(departements = depts) }
    override fun updatePsgNumero(numero: String) { _formState.value = _formState.value.copy(psgNumero = numero.ifBlank { null }) }
    override fun updateRemarques(remarques: String) { _formState.value = _formState.value.copy(remarques = remarques.ifBlank { null }) }
    override fun updateColor(color: String?) {
        val normalized = color?.trim()?.let { c ->
            when {
                c.isBlank() -> null
                c.startsWith("#") -> c.uppercase()
                c.length == 6 -> "#${c.uppercase()}"
                else -> c.uppercase()
            }
        }
        _formState.value = _formState.value.copy(color = normalized)
    }

    override fun save() {
        val form = _formState.value
        if (!form.isValid) return
        val foret = originalForet ?: return
        viewModelScope.launch {
            val updated = foret.copy(
                nom = form.nom,
                proprietaireNom = form.proprietaireNom,
                proprietaireEmail = form.proprietaireEmail,
                gestionnaireNom = form.gestionnaireNom,
                typeForet = form.typeForet,
                objectifGestion = form.objectifGestion,
                psgNumero = form.psgNumero,
                departement = form.departements.joinToString(", ").ifBlank { null },
                remarques = form.remarques,
                updatedAt = System.currentTimeMillis(),
            )
            foretRepository.update(updated)

            // Mettre à jour le Group lié (nom + couleur)
            groupRepository?.getByForetId(foretId)?.let { group ->
                groupRepository.updateGroup(
                    group.copy(
                        name = form.nom,
                        color = form.color,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
            }

            _isSaved.value = true
        }
    }
}
