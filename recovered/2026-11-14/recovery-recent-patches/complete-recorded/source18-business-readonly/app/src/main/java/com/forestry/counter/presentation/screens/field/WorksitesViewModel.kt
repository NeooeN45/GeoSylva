package com.forestry.counter.presentation.screens.field

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forestry.counter.domain.field.*
import com.forestry.counter.domain.repository.*
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WorksitesState(
    val sites: List<SavedWorksite> = emptyList(), val detail: SavedWorksite? = null,
    val form: WorksiteForm? = null, val parcels: List<WorksiteParcel> = emptyList(),
    val choices: List<FieldTargetChoice> = emptyList(), val linked: List<FieldTargetChoice> = emptyList(),
    val busy: Boolean = true, val error: FieldError? = null, val accessLost: Boolean = false,
    val selectedWoodId: String? = null, val selectedLedger: FieldStockLedgerKey? = null,
    val stock: FieldStockResult? = null, val stockSnapshot: FieldStockSnapshot? = null,
    val stockWritable: Boolean = false, val siteWritable: Boolean = false,
    val depotTitle: String = "",
)
class WorksitesViewModel(
    private val repository: FieldRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val calculationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val mutable = MutableStateFlow(WorksitesState())
    val state = mutable.asStateFlow()
    private var access: FieldAccess? = null
    private var newId: String? = null
    private val selectedBySite = mutableMapOf<String, String>()
    init {
        action { access = repository.captureAccess(); refresh() }
        viewModelScope.launch { repository.accessChanges().collect {
            if (access != null && !repository.isCurrent(permit())) revoke()
        } }
    }
    private fun permit() = checkNotNull(access)
    private fun revoke() {
        selectedBySite.clear()
        mutable.value = WorksitesState(busy = false, accessLost = true, error = FieldError.ACCESS_CHANGED)
    }
    private suspend fun refresh() { mutable.value = mutable.value.copy(sites = repository.sites(permit())) }
    private suspend fun context(site: SavedWorksite) {
        val linked = repository.linked(site.id, permit())
        val selected = selectedBySite[site.id]?.takeIf { id -> linked.any { it.target == FieldTarget(FieldTargetKind.WOOD_OBJECT, id) } }
        if (selected == null) selectedBySite.remove(site.id)
        val sameSelection = mutable.value.detail?.id == site.id && mutable.value.selectedWoodId == selected
        mutable.value = mutable.value.copy(detail = site, form = null, linked = linked,
            choices = repository.woodChoices(permit()), selectedWoodId = selected,
            selectedLedger = mutable.value.selectedLedger.takeIf { sameSelection },
            stock = null, stockSnapshot = null, stockWritable = false, depotTitle = "",
            siteWritable = repository.writable(FieldTarget(FieldTargetKind.WORKSITE, site.id), permit()))
        if (selected != null) loadStock(site.id, selected)
    }
    private suspend fun loadStock(siteId: String, objectId: String) {
        val target = FieldTarget(FieldTargetKind.WOOD_OBJECT, objectId)
        val links = repository.linked(siteId, permit())
        if (links.none { it.target == target }) {
            selectedBySite.remove(siteId)
            mutable.value = mutable.value.copy(linked = links, selectedWoodId = null, selectedLedger = null,
                stock = null, stockSnapshot = null, stockWritable = false, depotTitle = "")
            return
        }
        val snapshot = repository.stockSnapshot(target, permit())
        require(snapshot.objectId == objectId)
        val writable = repository.writable(target, permit())
        val depotTitle = repository.title(FieldTarget(FieldTargetKind.WOOD_DEPOT, snapshot.depotId), permit())
        val key = mutable.value.selectedLedger
        val projection = withContext(calculationDispatcher) { FieldStockAssessment.assess(snapshot, clock.millis(), key) }
        if (!repository.isCurrent(permit())) throw FieldException(FieldError.ACCESS_CHANGED)
        mutable.value = mutable.value.copy(linked = links, stockSnapshot = snapshot, stock = projection,
            stockWritable = writable && mutable.value.siteWritable, depotTitle = depotTitle)
    }
    fun selectWood(id: String) {
        if (!ready()) return
        val site = mutable.value.detail ?: return
        if (mutable.value.linked.none { it.target.kind == FieldTargetKind.WOOD_OBJECT && it.target.id == id }) return
        selectedBySite[site.id] = id
        mutable.value = mutable.value.copy(selectedWoodId = id, selectedLedger = null, stock = null,
            stockSnapshot = null, stockWritable = false, depotTitle = "")
        action { loadStock(site.id, id) }
    }
    fun selectLedger(key: FieldStockLedgerKey) {
        if (!ready()) return
        val snapshot = mutable.value.stockSnapshot ?: return
        action {
            val projection = withContext(calculationDispatcher) { FieldStockAssessment.assess(snapshot, clock.millis(), key) }
            if (!repository.isCurrent(permit())) throw FieldException(FieldError.ACCESS_CHANGED)
            mutable.value = mutable.value.copy(selectedLedger = key, stock = projection)
        }
    }
    fun chooseLedgerAgain() {
        if (!ready()) return
        val snapshot = mutable.value.stockSnapshot ?: return
        action {
            val projection = withContext(calculationDispatcher) { FieldStockAssessment.assess(snapshot, clock.millis()) }
            if (!repository.isCurrent(permit())) throw FieldException(FieldError.ACCESS_CHANGED)
            mutable.value = mutable.value.copy(selectedLedger = null, stock = projection)
        }
    }
    fun reload() {
        if (!ready() || mutable.value.form != null) return
        action {
            if (access == null) access = repository.captureAccess()
            refresh()
            mutable.value.detail?.id?.let { id ->
                val site = mutable.value.sites.firstOrNull { it.id == id } ?: throw FieldException(FieldError.UNAVAILABLE)
                context(site)
            }
        }
    }
    fun create() { if (!ready()) return; newId = UUID.randomUUID().toString(); action {
        mutable.value = mutable.value.copy(detail = null, form = WorksiteForm(), parcels = repository.parcelChoices(permit()),
            selectedWoodId = null, stock = null, stockSnapshot = null, stockWritable = false)
    } }
    fun edit() { if (!ready() || !mutable.value.siteWritable) return; action {
        mutable.value = mutable.value.copy(form = mutable.value.detail?.form, parcels = repository.parcelChoices(permit()))
    } }
    fun change(form: WorksiteForm) { if (ready()) mutable.value = mutable.value.copy(form = form, error = null) }
    fun open(site: SavedWorksite) { if (ready()) action { context(site) } }
    fun close() { if (ready()) {
        mutable.value = mutable.value.copy(detail = null, form = null, error = null, selectedWoodId = null,
            selectedLedger = null, stock = null, stockSnapshot = null, stockWritable = false, siteWritable = false)
        action { refresh() }
    } }
    fun save() {
        if (!ready()) return
        val form = mutable.value.form ?: return
        if (form.name.isBlank()) { mutable.value = mutable.value.copy(error = FieldError.INVALID); return }
        action {
            val old = mutable.value.detail
            val saved = repository.saveSite(old?.id ?: checkNotNull(newId), form, old?.revision ?: 0, old?.archived ?: false, permit())
            context(saved); refresh()
        }
    }
    fun archive() { if (!ready()) return; val site = mutable.value.detail ?: return
        action {
            repository.saveSite(site.id, site.form, site.revision, !site.archived, permit())
            mutable.value = mutable.value.copy(detail = null, form = null, selectedWoodId = null,
                stock = null, stockSnapshot = null, stockWritable = false, siteWritable = false)
            refresh()
        }
    }
    fun link(choice: FieldTargetChoice) { if (!ready()) return; val site = mutable.value.detail ?: return
        action { repository.link(site.id, choice.target, permit()); context(site) }
    }
    private fun ready() = !mutable.value.busy && !mutable.value.accessLost
    private fun action(block: suspend () -> Unit) {
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (access != null && !repository.isCurrent(permit())) revoke()
                else {
                    val code = (e as? FieldException)?.code ?: if (e is IllegalArgumentException) FieldError.INVALID else FieldError.IO
                    if (code == FieldError.UNAVAILABLE) mutable.value.detail?.id?.let(selectedBySite::remove)
                    mutable.value = mutable.value.copy(error = code, stock = null, stockSnapshot = null,
                        selectedWoodId = mutable.value.selectedWoodId.takeUnless { code == FieldError.UNAVAILABLE },
                        stockWritable = false, siteWritable = false, depotTitle = "")
                }
            } finally {
                if (access != null && !repository.isCurrent(permit())) revoke()
                else mutable.value = mutable.value.copy(busy = false)
            }
        }
    }
}

/** A short reference remains unique even for imported IDs sharing the same UUID prefix. */
internal fun fieldWoodChoiceReference(choice: FieldTargetChoice, choices: List<FieldTargetChoice>): String {
    val id = choice.target.id
    val others = choices.map { it.target.id }.filter { it != id }
    val length = (8..id.length).firstOrNull { size -> others.none { it.take(size) == id.take(size) } } ?: id.length
    return id.take(length)
}
