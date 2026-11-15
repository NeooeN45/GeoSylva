package com.forestry.counter.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.R
import com.forestry.counter.data.preferences.ThemeMode
import com.forestry.counter.domain.field.*
import com.forestry.counter.domain.repository.*
import com.forestry.counter.presentation.screens.field.WorksitesContent
import com.forestry.counter.presentation.screens.field.WorksitesViewModel
import com.forestry.counter.presentation.theme.ForestryCounterTheme
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real screen and ViewModel wiring with an isolated repository fixture, not a storage/stock test. */
@RunWith(AndroidJUnit4::class)
class WorksiteDossierWiringUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val store = ViewModelStore()
    private var restoreWindow: (() -> Unit)? = null
    private lateinit var vm: WorksitesViewModel
    private val mapCalls = mutableListOf<String>()
    private val exportCalls = mutableListOf<String>()
    private val receiptCalls = mutableListOf<String>()
    private val journalCalls = mutableListOf<FieldTarget>()
    private var manageWoodCalls = 0
    private var backCalls = 0

    @After fun cleanup() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        restoreWindow?.invoke()
    }

    @Test fun dossierUsesActualCountsAndPreservesShortcutAndEditorIdentity() {
        val fixture = DossierFixture()
        render(fixture)
        compose.onNodeWithTag("site_map").performClick()
        compose.onNodeWithTag("site_export").performClick()
        scrollTo("site_open_journal").performClick()
        scrollTo("site_view_woods")
        compose.onNode(hasText("2") and hasAnyAncestor(hasTestTag("site_view_woods")), useUnmergedTree = true)
            .assertExists()
        scrollTo("site_receipts").performClick()
        compose.runOnIdle {
            assertEquals(listOf(fixture.site.id), mapCalls)
            assertEquals(listOf(fixture.site.id), exportCalls)
            assertEquals(listOf(fixture.site.id), receiptCalls)
            assertEquals(listOf(FieldTarget(FieldTargetKind.WORKSITE, fixture.site.id)), journalCalls)
            assertTrue(fixture.saved.isEmpty())
        }
        compose.onNodeWithTag("site_options").performClick()
        compose.onNodeWithTag("site_menu_edit").performClick()
        compose.waitUntil(10_000) { !vm.state.value.busy && vm.state.value.form != null }
        compose.runOnIdle {
            assertEquals(fixture.site.form, vm.state.value.form)
            assertEquals(2, vm.state.value.form?.parcels?.size)
            assertTrue(fixture.saved.isEmpty())
        }
        Espresso.pressBack()
        compose.onNodeWithText(compose.activity.getString(R.string.field_cancel)).performClick()
        compose.runOnIdle { assertNotNull(vm.state.value.form); assertTrue(fixture.saved.isEmpty()) }
    }

    @Test fun associatedWoodKeepsRealTargetIdsAndBackReturnsToDossier() {
        val fixture = DossierFixture()
        render(fixture)
        scrollTo("site_view_woods").performClick()
        scrollTo("site_manage_wood").performClick()
        val wood = fixture.choices.first { it.target.kind == FieldTargetKind.WOOD_OBJECT }
        scrollTo("site_linked_${wood.target.kind}_${wood.target.id}").performClick()
        val depot = fixture.choices.first { it.target.kind == FieldTargetKind.WOOD_DEPOT }
        scrollTo("site_linked_${depot.target.kind}_${depot.target.id}").performClick()
        Espresso.pressBack()
        compose.onNodeWithTag("site_open_journal").assertExists()
        compose.runOnIdle {
            assertEquals(listOf(wood.target, depot.target), journalCalls)
            assertEquals(1, manageWoodCalls)
            assertEquals(0, backCalls)
            assertEquals(fixture.site.id, vm.state.value.detail?.id)
            assertTrue(fixture.saved.isEmpty())
            assertTrue(fixture.linkCalls.isEmpty())
        }
    }

    @Test fun emptyDossierIsExplicitAndRevocationRemovesItsActions() {
        val fixture = DossierFixture(initiallyLinked = false, emptyDetails = true)
        render(fixture)
        compose.onNodeWithText(compose.activity.getString(R.string.retained_no_note)).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.retained_no_parcels)).assertExists()
        scrollTo("site_view_woods")
        compose.onNode(hasText("0") and hasAnyAncestor(hasTestTag("site_view_woods")), useUnmergedTree = true)
            .assertExists()
        compose.onNodeWithTag("site_view_woods").performClick()
        scrollTo("site_associated_wood")
        compose.onNodeWithText(compose.activity.getString(R.string.site_empty_wood)).assertExists()
        compose.runOnIdle { fixture.epoch.value++ }
        compose.waitUntil(10_000) { vm.state.value.accessLost }
        compose.onNodeWithTag("site_map").assertDoesNotExist()
        compose.onNodeWithTag("site_options").assertDoesNotExist()
        compose.onNodeWithTag("site_manage_wood").assertDoesNotExist()
        compose.onNodeWithTag("site_open_journal").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(fixture.saved.isEmpty())
            assertTrue(fixture.linkCalls.isEmpty())
            assertTrue(journalCalls.isEmpty())
        }
    }

    @Test fun archiveRequiresConfirmationAndRetainsNoteAndParcelReferences() {
        val fixture = DossierFixture()
        val original = fixture.site
        render(fixture)
        compose.onNodeWithTag("site_options").performClick()
        compose.runOnIdle { assertTrue(fixture.saved.isEmpty()); assertNull(vm.state.value.form) }
        compose.onNodeWithTag("site_menu_archive").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.field_cancel)).performClick()
        compose.runOnIdle { assertTrue(fixture.saved.isEmpty()) }
        compose.onNodeWithTag("site_options").performClick()
        compose.onNodeWithTag("site_menu_archive").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.field_save)).performClick()
        compose.waitUntil(10_000) { !vm.state.value.busy && vm.state.value.detail == null }
        compose.runOnIdle {
            assertEquals(1, fixture.saved.size)
            assertEquals(original.id, fixture.saved.single().id)
            assertEquals(original.form, fixture.saved.single().form)
            assertTrue(fixture.saved.single().archived)
        }
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText(original.form.name).performClick()
        compose.waitUntil(10_000) { !vm.state.value.busy && vm.state.value.detail != null }
        compose.onNodeWithTag("site_options").performClick()
        compose.onNodeWithTag("site_menu_edit").assertDoesNotExist()
        compose.onNodeWithTag("site_menu_archive").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.field_save)).performClick()
        compose.waitUntil(10_000) { !vm.state.value.busy && vm.state.value.detail == null }
        compose.runOnIdle {
            assertEquals(2, fixture.saved.size)
            assertFalse(fixture.saved.last().archived)
            assertEquals(original.form, fixture.saved.last().form)
        }
    }

    private fun render(fixture: DossierFixture) {
        restoreWindow = useFrenchMapTestWindow(compose.activity)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            vm = WorksitesViewModel(fixture)
            store.put("TEST-dossier", vm)
        }
        compose.setContent {
            ForestryCounterTheme(themeMode = ThemeMode.LIGHT, dynamicColor = false) {
                WorksitesContent(vm, { journalCalls += it }, { backCalls++ },
                    onReceipts = { receiptCalls += it }, onExport = { exportCalls += it },
                    onManageWood = { manageWoodCalls++ }, onMap = { mapCalls += it })
            }
        }
        compose.waitUntil(10_000) { !vm.state.value.busy }
        compose.onNodeWithTag("site_content").performScrollToNode(hasText(fixture.site.form.name))
        compose.onNodeWithText(fixture.site.form.name).performClick()
        compose.waitUntil(10_000) { !vm.state.value.busy && vm.state.value.detail?.id == fixture.site.id }
    }

    private fun scrollTo(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("site_content").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag).performScrollTo()
    }
}

/** Bounded read/write fixture for site UI callbacks; unsupported journal/stock operations fail. */
private class DossierFixture(initiallyLinked: Boolean = true, emptyDetails: Boolean = false) : FieldRepository {
    val epoch = MutableStateFlow(0)
    var site = SavedWorksite(UUID(0, 101).toString(), WorksiteForm("TEST dossier", if (emptyDetails) "" else "TEST note preserved",
        if (emptyDetails) emptyList() else listOf(WorksiteParcel("LOCAL", "TEST-P1", "TEST parcel one", UUID(0, 201).toString()),
            WorksiteParcel("EXTERNAL", "TEST-P2", "TEST external parcel", sourceVersion = "TEST-v1"))), 3, false)
        private set
    val choices = listOf(
        FieldTargetChoice(FieldTarget(FieldTargetKind.WOOD_OBJECT, UUID(0, 301).toString()), "TEST pile one"),
        FieldTargetChoice(FieldTarget(FieldTargetKind.WOOD_OBJECT, UUID(0, 302).toString()), "TEST pile two"),
        FieldTargetChoice(FieldTarget(FieldTargetKind.WOOD_DEPOT, UUID(0, 401).toString()), "TEST depot"),
    )
    private val linked = (if (initiallyLinked) choices else emptyList()).toMutableList()
    val saved = mutableListOf<SavedWorksite>()
    val linkCalls = mutableListOf<Pair<String, FieldTarget>>()
    private data class Access(val owner: DossierFixture, val generation: Int) : FieldAccess
    override suspend fun captureAccess(): FieldAccess = Access(this, epoch.value)
    override fun isCurrent(access: FieldAccess) = (access as? Access)?.let { it.owner === this && it.generation == epoch.value } == true
    override fun accessChanges() = epoch.map { Unit }
    private fun check(access: FieldAccess) { if (!isCurrent(access)) throw FieldException(FieldError.ACCESS_CHANGED) }
    override suspend fun sites(access: FieldAccess): List<SavedWorksite> { check(access); return listOf(site) }
    override suspend fun parcelChoices(access: FieldAccess): List<WorksiteParcel> { check(access); return site.form.parcels }
    override suspend fun woodChoices(access: FieldAccess): List<FieldTargetChoice> { check(access); return choices }
    override suspend fun linked(site: String, access: FieldAccess): List<FieldTargetChoice> {
        check(access); require(site == this.site.id); return linked.toList()
    }
    override suspend fun link(site: String, target: FieldTarget, access: FieldAccess) {
        check(access); require(site == this.site.id && !this.site.archived)
        val choice = choices.single { it.target == target }
        linkCalls += site to target
        if (linked.none { it.target == target }) linked += choice
    }
    override suspend fun saveSite(id: String, form: WorksiteForm, revision: Int, archived: Boolean, access: FieldAccess): SavedWorksite {
        check(access); require(id == site.id && revision == site.revision)
        site = SavedWorksite(id, form.validate(), revision + 1, archived)
        saved += site
        return site
    }
    private fun unexpected(operation: String): Nothing = throw AssertionError("Unexpected worksite UI operation: $operation")
    override suspend fun title(target: FieldTarget, access: FieldAccess): String = unexpected("title")
    override suspend fun writable(target: FieldTarget, access: FieldAccess): Boolean = unexpected("writable")
    override suspend fun measurements(target: FieldTarget, access: FieldAccess): WoodObjectDetails? = unexpected("measurements")
    override suspend fun entries(target: FieldTarget, access: FieldAccess): List<FieldEntry> = unexpected("entries")
    override suspend fun append(id: String, target: FieldTarget, form: FieldEntryForm, access: FieldAccess): FieldEntry = unexpected("append")
    override suspend fun loadDraft(target: FieldTarget, slot: String, access: FieldAccess): FieldDraft? = unexpected("loadDraft")
    override suspend fun saveDraft(target: FieldTarget, slot: String, expected: Long?, content: String, access: FieldAccess): FieldDraft = unexpected("saveDraft")
    override suspend fun deleteDraft(target: FieldTarget, slot: String, expected: Long, access: FieldAccess): Unit = unexpected("deleteDraft")
    override suspend fun addPhoto(target: FieldTarget, bytes: ByteArray, mime: String, caption: String, access: FieldAccess): FieldPhoto = unexpected("addPhoto")
    override suspend fun photo(id: String, target: FieldTarget, access: FieldAccess): Pair<FieldPhoto, ByteArray> = unexpected("photo")
}
