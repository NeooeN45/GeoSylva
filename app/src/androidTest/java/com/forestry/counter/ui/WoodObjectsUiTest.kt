package com.forestry.counter.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.R
import com.forestry.counter.data.local.*
import com.forestry.counter.data.preferences.ThemeMode
import com.forestry.counter.data.repository.*
import com.forestry.counter.domain.model.*
import com.forestry.counter.domain.wood.*
import com.forestry.counter.presentation.screens.wood.*
import com.forestry.counter.presentation.theme.ForestryCounterTheme
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Actual production form/repository, with synthetic TEST values in a dedicated database file. */
@RunWith(AndroidJUnit4::class)
class WoodObjectsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val store = ViewModelStore()
    private lateinit var db: ForestryDatabase
    private lateinit var repo: WoodObjectRepositoryImpl
    private lateinit var vm: WoodObjectsViewModel
    private val mounted = mutableStateOf<WoodObjectsViewModel?>(null)
    private val identity = BusinessReadIdentityFixture()
    private val ctx: Context by lazy {
        val original = ApplicationProvider.getApplicationContext<Context>()
        original.createConfigurationContext(Configuration(original.resources.configuration).apply { setLocale(Locale.FRANCE) })
    }
    @Before fun setup() = runBlocking {
        ctx.deleteDatabase(DB)
        db = open()
        val depots = WoodDepotRepositoryImpl(db, identity)
        depots.createDepot(DEPOT.toString(), WoodDepotDraft("TEST · Bord de route", WoodDepotKind.ROADSIDE), depots.captureAccess())
        compose.activityRule.scenario.onActivity {
            it.enableEdgeToEdge()
            it.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        replaceVm()
    }
    @After fun cleanup() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        db.close(); ctx.deleteDatabase(DB)
    }
    private fun open() = Room.databaseBuilder(ctx, ForestryDatabase::class.java, DB).addMigrations(*DatabaseMigrations.ALL).build()
    private fun replaceVm() {
        repo = WoodObjectRepositoryImpl(db, identity)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            vm = WoodObjectsViewModel(repo, DEPOT, FieldRepositoryImpl(db, identity, com.forestry.counter.data.sync.hierarchy.BusinessReadAccess(identity, db.parcelSyncDao())))
            store.put("TEST-wood", vm); mounted.value = vm
        }
    }
    private fun content(scale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides ctx, LocalConfiguration provides ctx.resources.configuration,
                LocalDensity provides Density(density.density, scale)) {
                ForestryCounterTheme(themeMode = ThemeMode.LIGHT) { mounted.value?.let { WoodObjectsContent(it, {}) } }
            }
        }
        compose.waitUntil(10_000) { !vm.state.value.busy }
    }
    private fun field(tag: String) = compose.onNodeWithTag("wood_objects_content").performScrollToNode(hasTestTag(tag))
        .let { compose.waitForIdle(); compose.onNodeWithTag(tag) }
    private fun openMeasurement() {
        field("wood_object_measure").performClick()
        compose.waitUntil(10_000) { !vm.state.value.busy && vm.state.value.form?.identityOnly == false }
        compose.waitForIdle()
    }
    private fun create() {
        compose.onNodeWithTag("wood_object_create_pile").performClick()
        field("wood_name").performTextInput("TEST · Pile chêne 01")
        field("wood_tag").performTextInput("TEST-P001")
        field("wood_species").performTextInput("Chêne sessile")
        clickSave()
        compose.waitUntil(10_000) { vm.state.value.detail != null && !vm.state.value.busy }
    }
    // Native IME animations run outside Compose's idle tracking. Stabilize the window
    // before injecting a touch; keyboard visibility is checked separately below.
    private fun settleKeyboard() {
        Espresso.closeSoftKeyboard()
        compose.waitForIdle()
    }
    private fun clickSave() {
        settleKeyboard()
        compose.onNodeWithTag("wood_object_save").assertIsDisplayed().performClick()
    }
    private fun saveObservation() {
        clickSave()
        try { compose.waitUntil(10_000) { vm.state.value.form == null && !vm.state.value.busy } }
        catch (error: Throwable) { diagnostic("save-timeout"); throw error }
        assertNull(vm.state.value.error)
    }
    @Test fun manual_pile_is_saved_then_reread_after_database_and_screen_reopen() {
        content(); create()
        openMeasurement()
        field("wood_observed_date").performTextInput("18/10/2026 10:30")
        field("wood_length").performTextInput("12,50")
        field("wood_depth").performTextInput("2")
        field("wood_height").performTextInput("1,20")
        screenshot("pile-formulaire-mesures-fr.png")
        field("wood_shape").performClick()
        field("wood_protocol").performTextInput("TEST · Enveloppe et moyenne de trois hauteurs")
        field("wood_quality").performTextInput("TEST · Bois énergie déclaré")
        saveObservation()
        val original = vm.state.value.detail!!
        assertEquals("12,50", (original.observations.single().payload.measurement as RawWoodMeasurement.Pile).length?.rawText)
        assertEquals(30.0, (WoodObservationCalculator.calculate(original.observations.single().record.observation) as WoodObservationCalculation.Calculated).result.volumeM3, 1e-10)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear(); mounted.value = null }
        db.close(); db = open(); replaceVm()
        compose.waitUntil(10_000) { !vm.state.value.busy && vm.state.value.items.isNotEmpty() }
        field("wood_object_${original.item.id}").performClick()
        compose.waitUntil(10_000) { vm.state.value.detail != null && !vm.state.value.busy }
        assertEquals(original.item, vm.state.value.detail!!.item)
        assertEquals(original.observations, vm.state.value.detail!!.observations)
        compose.onNodeWithTag("wood_objects_content").performScrollToNode(hasText(ctx.getString(R.string.wo_history)))
        val savedDate = ctx.getString(R.string.wo_recorded, java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.SHORT, java.text.DateFormat.SHORT, Locale.FRANCE).format(java.util.Date.from(original.observations.single().record.observation.recordedAt)))
        compose.onNodeWithTag("wood_objects_content").performScrollToNode(hasText(savedDate))
        compose.onNodeWithText(savedDate).assertIsDisplayed()
        screenshot("pile-historique-enregistre-fr.png")
    }
    @Test fun partial_remeasurement_adds_history_to_the_same_pile_without_a_volume() {
        content(); create()
        repeat(2) { index ->
            openMeasurement()
            field("wood_length").performTextInput(if (index == 0) "10" else "7")
            saveObservation()
        }
        val saved = vm.state.value.detail!!
        assertEquals(1, vm.state.value.items.size)
        assertEquals(2, saved.observations.size)
        assertTrue(saved.observations.all { WoodObservationCalculator.calculate(it.record.observation) is WoodObservationCalculation.Uncalculable })
        assertNull(saved.history.currentStateObservation)
    }
    @Test fun log_form_uses_explicit_huber_method_and_bark_convention() {
        content()
        compose.onNodeWithTag("wood_object_create_log").performClick()
        field("wood_name").performTextInput("TEST · Grume 01")
        clickSave()
        compose.waitUntil(10_000) { vm.state.value.detail != null && !vm.state.value.busy }
        openMeasurement()
        field("wood_length").performTextInput("5")
        field("wood_method").performClick()
        if (compose.onAllNodesWithText(ctx.getString(R.string.wo_huber)).fetchSemanticsNodes().isEmpty()) diagnostic("log-menu")
        compose.onNodeWithText(ctx.getString(R.string.wo_huber)).performClick()
        field("wood_bark").performClick()
        compose.onNodeWithText(ctx.getString(R.string.wo_over)).performClick()
        field("wood_middle").performTextInput("40")
        saveObservation()
        val saved = vm.state.value.detail!!.observations.single()
        val raw = saved.payload.measurement as RawWoodMeasurement.Log
        assertEquals("huber", raw.methodCode); assertEquals("OVER_BARK", raw.barkConventionCode)
        assertNull(raw.startDiameter); assertNull(raw.endDiameter)
        assertEquals(kotlin.math.PI * 0.04 * 5, (WoodObservationCalculator.calculate(saved.record.observation) as WoodObservationCalculation.Calculated).result.volumeM3, 1e-10)
    }

    @Test fun large_text_form_keeps_manual_fields_and_save_button_accessible_with_keyboard() {
        content(scale = 2f); create()
        openMeasurement()
        field("wood_length").performTextInput("8,50")
        compose.onNodeWithTag("wood_object_save").assertIsDisplayed()
        field("wood_notes").performTextInput("TEST · Complément à relever à la prochaine visite")
        compose.onNodeWithTag("wood_object_save").assertIsDisplayed()
        screenshot("pile-formulaire-grande-police-fr.png")
        saveObservation()
        assertEquals("TEST · Complément à relever à la prochaine visite", vm.state.value.detail!!.observations.single().payload.description)
    }
    @Test fun interrupted_raw_form_is_restored_without_creating_a_new_object() {
        content(); create()
        openMeasurement()
        field("wood_length").performTextInput("8,")
        compose.waitUntil(10_000) { vm.state.value.draftSaved }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear(); mounted.value=null }
        replaceVm()
        compose.waitUntil(10_000) { !vm.state.value.busy && vm.state.value.form != null }
        assertEquals("8,", vm.state.value.form?.length)
        assertEquals(1, vm.state.value.items.size)
        assertTrue(checkNotNull(vm.state.value.detail).observations.isEmpty())
        field("wood_length").assertTextContains("8,")
    }
    @Test fun save_and_next_log_keeps_method_but_clears_identity_measurements_and_time() {
        content()
        compose.onNodeWithTag("wood_object_create_log").performClick()
        field("wood_name").performTextInput("TEST · Grume série 01")
        clickSave()
        compose.waitUntil(10_000) { !vm.state.value.busy && vm.state.value.detail != null }
        openMeasurement()
        field("wood_length").performTextInput("5")
        field("wood_method").performClick()
        compose.onNodeWithText(ctx.getString(R.string.wo_huber)).performClick()
        field("wood_bark").performClick()
        compose.onNodeWithText(ctx.getString(R.string.wo_over)).performClick()
        field("wood_middle").performTextInput("40")
        settleKeyboard()
        compose.onNodeWithText(ctx.getString(R.string.wood_next)).assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { !vm.state.value.busy && vm.state.value.form?.newWithMeasurement == true }
        val next=checkNotNull(vm.state.value.form)
        assertEquals("huber",next.method);assertEquals("OVER_BARK",next.bark)
        assertTrue(next.name.isEmpty() && next.tag.isEmpty() && next.length.isEmpty() && next.middle.isEmpty() && next.observedDate.isEmpty())
        field("wood_name").performTextInput("TEST · Grume série 02")
        field("wood_length").performTextInput("4")
        field("wood_middle").performTextInput("35")
        saveObservation()
        assertEquals(2,vm.state.value.items.size)
        assertEquals(1,checkNotNull(vm.state.value.detail).observations.size)
        screenshot("grume-serie-enregistree.png")
    }
    @Test fun restored_header_draft_cannot_overwrite_a_newer_revision() {
        content();create()
        compose.runOnIdle {vm.editIdentity()}
        field("wood_name").performTextReplacement("TEST ancienne saisie")
        compose.waitUntil(10_000) {vm.state.value.draftSaved}
        val item=checkNotNull(vm.state.value.detail).item
        runBlocking {
            repo.execute(WoodObjectCommand.UpdateHeader(WoodCommandContext(UUID.randomUUID(),DEPOT,item.id,
                WoodOwner(WoodDepotOwnerScope.LOCAL_DEVICE),"anonymous"),item.revision,item.header.copy(name="TEST modification concurrente")),repo.captureAccess())
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {store.clear();mounted.value=null}
        replaceVm()
        compose.waitUntil(10_000) {!vm.state.value.busy && vm.state.value.form!=null}
        assertTrue(vm.state.value.draftConflict)
        compose.runOnIdle {vm.change {copy(name="TEST tentative")};vm.save()}
        assertEquals("TEST ancienne saisie",vm.state.value.form?.name)
        runBlocking {assertEquals("TEST modification concurrente",repo.details(DEPOT,item.id,repo.captureAccess()).item.header.name)}
    }
    private fun diagnostic(name: String) {
        val dir = File(ctx.getExternalFilesDir(null), "wood-objects").also { it.mkdirs() }
        File(dir, "diagnostic-$name-state.txt").writeText(vm.state.value.toString())
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(dir, "diagnostic-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun screenshot(name: String) {
        Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file = File(ctx.getExternalFilesDir(null), "wood-objects/$name")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("GEOSYLVA_WOOD_FORMS_SCREENSHOT=${file.absolutePath}")
    }
    companion object {
        private const val DB = "TEST-wood-forms-ui.db"
        private val DEPOT = UUID.fromString("c0000000-0000-0000-0000-000000000001")
    }
}
