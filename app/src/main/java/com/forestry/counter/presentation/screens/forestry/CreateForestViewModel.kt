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
import java.util.UUID

/**
 * Interface commune pour CreateForestWizard — partagée entre création et édition.
 */
interface ForestFormViewModel {
    val formState: StateFlow<CreateForestFormState>
    val isSaved: StateFlow<Boolean>
    fun updateNom(nom: String)
    fun updateProprietaireNom(nom: String)
    fun updateProprietaireEmail(email: String)
    fun updateGestionnaireNom(nom: String)
    fun updateTypeForet(type: String)
    fun updateObjectifGestion(objectif: String)
    fun updateDepartements(depts: List<String>)
    fun updatePsgNumero(numero: String)
    fun updateRemarques(remarques: String)
    fun updateColor(color: String?)
    fun save()
}

/**
 * ViewModel pour CreateForestWizard — spec GEOSYLVA-003 §29.6.
 *
 * Wizard 4 étapes : Identité, Propriétaire, Gestion, Couleur.
 * Quand [groupRepository] est fourni, la sauvegarde crée aussi un Group
 * homologue dans la liste des forêts (GroupsScreen) avec le même nom et
 * la couleur choisie à l'étape 4.
 */
class CreateForestViewModel(
    private val foretRepository: ForetRepository,
    private val groupRepository: GroupRepository? = null,
) : ViewModel(), ForestFormViewModel {

    private val _formState = MutableStateFlow(CreateForestFormState())
    override val formState: StateFlow<CreateForestFormState> = _formState.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    override val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    override fun updateNom(nom: String) {
        _formState.value = _formState.value.copy(nom = nom)
    }

    override fun updateProprietaireNom(nom: String) {
        _formState.value = _formState.value.copy(proprietaireNom = nom)
    }

    override fun updateProprietaireEmail(email: String) {
        _formState.value = _formState.value.copy(proprietaireEmail = email.ifBlank { null })
    }

    override fun updateGestionnaireNom(nom: String) {
        _formState.value = _formState.value.copy(gestionnaireNom = nom.ifBlank { null })
    }

    override fun updateTypeForet(type: String) {
        _formState.value = _formState.value.copy(typeForet = type.ifBlank { null })
    }

    override fun updateObjectifGestion(objectif: String) {
        _formState.value = _formState.value.copy(objectifGestion = objectif.ifBlank { null })
    }

    /** Remplace la liste complète des départements sélectionnés. */
    override fun updateDepartements(depts: List<String>) {
        _formState.value = _formState.value.copy(departements = depts)
    }

    // Compat : anciens appelants passant un seul département.
    fun updateDepartement(dept: String) {
        val trimmed = dept.trim()
        val current = _formState.value.departements
        _formState.value = _formState.value.copy(
            departements = if (trimmed.isBlank()) current
            else if (current.contains(trimmed)) current - trimmed
            else current + trimmed
        )
    }

    override fun updatePsgNumero(numero: String) {
        _formState.value = _formState.value.copy(psgNumero = numero.ifBlank { null })
    }

    override fun updateRemarques(remarques: String) {
        _formState.value = _formState.value.copy(remarques = remarques.ifBlank { null })
    }

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
        viewModelScope.launch {
            val foret = Foret(
                foretId = UUID.randomUUID().toString(),
                nom = form.nom,
                proprietaireNom = form.proprietaireNom,
                proprietaireEmail = form.proprietaireEmail,
                gestionnaireNom = form.gestionnaireNom,
                typeForet = form.typeForet,
                objectifGestion = form.objectifGestion,
                psgNumero = form.psgNumero,
                psgDateExpiration = null,
                departement = form.departements.joinToString(", ").ifBlank { null },
                remarques = form.remarques,
            )
            foretRepository.insert(foret)

            // Créer également un Group homologue si le repository est fourni
            // (flux depuis GroupsScreen → CreateForestWizard).
            groupRepository?.insertGroup(
                Group(
                    id = UUID.randomUUID().toString(),
                    name = form.nom,
                    color = form.color,
                    foretId = foret.foretId,
                )
            )

            _isSaved.value = true
        }
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
    val departements: List<String> = emptyList(),
    val remarques: String? = null,
    val color: String? = null,
) {
    val isValid: Boolean
        get() = nom.isNotBlank() && proprietaireNom.isNotBlank()

    val step1Valid: Boolean get() = nom.isNotBlank()
    val step2Valid: Boolean get() = proprietaireNom.isNotBlank()
    val step3Valid: Boolean get() = true
    val step4Valid: Boolean get() = true
}
