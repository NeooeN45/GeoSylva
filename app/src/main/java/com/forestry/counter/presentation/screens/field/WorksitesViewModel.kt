package com.forestry.counter.presentation.screens.field

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forestry.counter.domain.field.*
import com.forestry.counter.domain.repository.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class WorksitesState(val sites: List<SavedWorksite> = emptyList(),val detail: SavedWorksite?=null,
    val form: WorksiteForm?=null,val parcels: List<WorksiteParcel> = emptyList(),val choices: List<FieldTargetChoice> = emptyList(),
    val linked: List<FieldTargetChoice> = emptyList(),val busy: Boolean=true,val error: FieldError?=null,val accessLost: Boolean=false)
class WorksitesViewModel(private val repository: FieldRepository): ViewModel() {
    private val mutable=MutableStateFlow(WorksitesState())
    val state=mutable.asStateFlow()
    private var access: FieldAccess?=null
    private var newId: String?=null
    init {
        action {access=repository.captureAccess();refresh()}
        viewModelScope.launch {repository.accessChanges().collect {
            if(access!=null && !repository.isCurrent(checkNotNull(access))) mutable.value=WorksitesState(busy=false,accessLost=true,error=FieldError.ACCESS_CHANGED)
        }}
    }
    private suspend fun refresh() {mutable.value=mutable.value.copy(sites=repository.sites(checkNotNull(access)))}
    fun reload() {if(!ready() || mutable.value.form!=null) return;action {
        refresh()
        val id=mutable.value.detail?.id
        if(id!=null) {
            val current=mutable.value.sites.firstOrNull {it.id==id} ?: throw FieldException(FieldError.UNAVAILABLE)
            mutable.value=mutable.value.copy(detail=current,linked=repository.linked(id,checkNotNull(access)),choices=repository.woodChoices(checkNotNull(access)))
        }
    }}
    fun create() {if(!ready()) return;newId=UUID.randomUUID().toString();action {
        mutable.value=mutable.value.copy(detail=null,form=WorksiteForm(),parcels=repository.parcelChoices(checkNotNull(access)))
    }}
    fun edit() {if(!ready()) return;action {mutable.value=mutable.value.copy(form=mutable.value.detail?.form,parcels=repository.parcelChoices(checkNotNull(access)))}}
    fun change(form: WorksiteForm) {if(ready()) mutable.value=mutable.value.copy(form=form,error=null)}
    fun open(site: SavedWorksite) {if(!ready()) return;action {
        mutable.value=mutable.value.copy(detail=site,form=null,linked=repository.linked(site.id,checkNotNull(access)),choices=repository.woodChoices(checkNotNull(access)))
    }}
    fun close() {if(ready()) {mutable.value=mutable.value.copy(detail=null,form=null,error=null);action {refresh()}}}
    fun save() {if(!ready()) return
        val form=mutable.value.form ?: return
        if (form.name.isBlank()) {
            mutable.value=mutable.value.copy(error=FieldError.INVALID)
            return
        }
        action {
            val old=mutable.value.detail
            val saved=repository.saveSite(old?.id ?: checkNotNull(newId),form,old?.revision ?: 0,old?.archived ?: false,checkNotNull(access))
            mutable.value=mutable.value.copy(detail=saved,form=null,linked=repository.linked(saved.id,checkNotNull(access)),choices=repository.woodChoices(checkNotNull(access)))
            refresh()
        }
    }
    fun archive() {if(!ready()) return;val site=mutable.value.detail ?: return
        action {
            repository.saveSite(site.id,site.form,site.revision,!site.archived,checkNotNull(access))
            mutable.value=mutable.value.copy(detail=null,form=null);refresh()
        }
    }
    fun link(choice: FieldTargetChoice) {if(!ready()) return;val site=mutable.value.detail ?: return
        action {repository.link(site.id,choice.target,checkNotNull(access));mutable.value=mutable.value.copy(linked=repository.linked(site.id,checkNotNull(access)))}
    }
    private fun ready()=!mutable.value.busy && !mutable.value.accessLost
    private fun action(block:suspend ()->Unit) {
        mutable.value=mutable.value.copy(busy=true,error=null)
        viewModelScope.launch {
            try {block()}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {
                if(access!=null && !repository.isCurrent(checkNotNull(access))) mutable.value=WorksitesState(accessLost=true,error=FieldError.ACCESS_CHANGED)
                else mutable.value=mutable.value.copy(error=(e as? FieldException)?.code ?: if(e is IllegalArgumentException) FieldError.INVALID else FieldError.IO)
            } finally {
                if(access!=null && !repository.isCurrent(checkNotNull(access))) mutable.value=WorksitesState(busy=false,accessLost=true,error=FieldError.ACCESS_CHANGED)
                else mutable.value=mutable.value.copy(busy=false)
            }
        }
    }
}
