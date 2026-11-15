package com.forestry.counter.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.domain.field.*
import com.forestry.counter.domain.model.*
import com.forestry.counter.domain.repository.*
import com.forestry.counter.domain.wood.*
import com.forestry.counter.presentation.screens.field.*
import com.forestry.counter.presentation.theme.ForestryCounterTheme
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Synthetic TEST scenarios, isolated database. Real UI, repository and Android storage. */
@RunWith(AndroidJUnit4::class)
class FieldToolsUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val context:Context by lazy {ApplicationProvider.getApplicationContext<Context>().let {it.createConfigurationContext(Configuration(it.resources.configuration).apply {setLocale(Locale.FRANCE)})}}
    private lateinit var db:ForestryDatabase
    private lateinit var repo:FieldRepositoryImpl
    private val store=ViewModelStore()
    private val identity=BusinessReadIdentityFixture()
    private var mounted by mutableStateOf<FieldJournalViewModel?>(null)
    private val forest=FieldTarget(FieldTargetKind.FOREST,"TEST-legacy-forest")
    @Before fun setup()=runBlocking<Unit> {
        context.deleteDatabase(DB);db=Room.databaseBuilder(context,ForestryDatabase::class.java,DB).addMigrations(*DatabaseMigrations.ALL).build()
        db.openHelper.writableDatabase.execSQL("INSERT INTO forets(foretId,nom,proprietaireNom,createdAt,updatedAt) VALUES('TEST-legacy-forest','TEST · Forêt de démonstration','TEST owner',10,20)")
        repo=FieldRepositoryImpl(db,identity,BusinessReadAccess(identity,db.parcelSyncDao()))
    }
    @After fun close() {InstrumentationRegistry.getInstrumentation().runOnMainSync {store.clear();mounted=null};db.close();context.deleteDatabase(DB)}
    private fun mount(target:FieldTarget) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {mounted=FieldJournalViewModel(repo,target).also {store.put("journal",it)}}
    }
    private fun content() {
        compose.setContent {CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides compose.activity, LocalContext provides context,LocalConfiguration provides context.resources.configuration) {
            ForestryCounterTheme(themeMode=ThemeMode.LIGHT) {mounted?.let {FieldJournalContent(it,{})}}
        }}
        compose.waitUntil(10_000) {mounted?.state?.value?.busy==false}
    }
    private fun field(tag:String)=compose.onNodeWithTag("field_content").performScrollToNode(hasTestTag(tag)).let {compose.onNodeWithTag(tag).performScrollTo()}
    @Test fun management_note_autosaves_restores_then_saves_without_stock_controls() {
        mount(forest);content()
        compose.onNodeWithTag("field_add").performClick()
        field("field_name").performTextInput("TEST · Accès à vérifier")
        field("field_notes").performTextInput("TEST · Barrière à ouvrir avant la visite. Photographier avant et après travaux.")
        compose.waitUntil(10_000) {mounted?.state?.value?.draftSaved==true}
        InstrumentationRegistry.getInstrumentation().runOnMainSync {store.clear();mounted=null}
        mount(forest)
        compose.waitUntil(10_000) {mounted?.state?.value?.busy==false && mounted?.state?.value?.form!=null}
        assertEquals("TEST · Accès à vérifier",mounted?.state?.value?.form?.title)
        // Save from the end of a long restored form; the resulting journal starts at the top.
        field("field_notes")
        compose.onNodeWithTag("field_save").performClick()
        compose.waitUntil(10_000) {mounted?.state?.value?.form==null && mounted?.state?.value?.entries?.size==1}
        compose.onNodeWithTag("field_add").assertIsDisplayed()
        compose.onNodeWithText("TEST · Accès à vérifier").assertIsDisplayed()
        compose.onNodeWithText("Stock physique initial").assertDoesNotExist()
        screenshot("gestion-forestiere-carnet.png")
    }
    @Test fun wood_timeline_shows_partial_removals_and_remaining_discrepancy()=runBlocking<Unit> {
        val depot=UUID.randomUUID();val wood=UUID.randomUUID()
        val depots=WoodDepotRepositoryImpl(db,identity)
        depots.createDepot(depot.toString(),WoodDepotDraft("TEST bord de route",WoodDepotKind.ROADSIDE),depots.captureAccess())
        val woods=WoodObjectRepositoryImpl(db,identity)
        woods.execute(WoodObjectCommand.Create(WoodCommandContext(UUID.randomUUID(),depot,wood,WoodOwner(WoodDepotOwnerScope.LOCAL_DEVICE),"anonymous"),WoodObjectKind.PILE,WoodObjectHeader("TEST · Pile chêne 01")),woods.captureAccess())
        val target=FieldTarget(FieldTargetKind.WOOD_OBJECT,wood.toString());val access=repo.captureAccess()
        val base=FieldEntryForm(FieldEntryKind.INITIAL_STOCK,title="TEST · Stock déclaré",observed="18/10/2026 10:00",zone="Europe/Paris",quantity="30",unit=FieldQuantityUnit.APPARENT_M3,convention="TEST enveloppe apparente")
        repo.append(UUID.randomUUID().toString(),target,base,access)
        repo.append(UUID.randomUUID().toString(),target,base.copy(kind=FieldEntryKind.REMOVAL,title="TEST · Premier passage",quantity="12",observed="18/10/2026 11:00",counterparty="TEST transporteur",passageReference="TEST passage 01"),access)
        repo.append(UUID.randomUUID().toString(),target,base.copy(kind=FieldEntryKind.REMAINING,title="TEST · Reste à reprendre",quantity="17,5",observed="18/10/2026 12:00",remainder=FieldRemainder.BOTTOM),access)
        mount(target);content()
        compose.onNodeWithTag("field_content").performScrollToNode(hasText("TEST · Reste à reprendre"))
        screenshot("bois-controle-reste.png")
        compose.onNodeWithTag("field_content").performScrollToNode(hasText("Écart constaté : -0,5 m³ apparent"))
        compose.onNodeWithText("Écart constaté : -0,5 m³ apparent").assertIsDisplayed()
        assertEquals(3,mounted?.state?.value?.entries?.size)
    }
    @Test fun worksite_records_multiple_parcels_and_archives_without_deleting() {
        lateinit var vm:WorksitesViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {vm=WorksitesViewModel(repo);store.put("sites",vm)}
        compose.setContent {CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides compose.activity, LocalContext provides context,LocalConfiguration provides context.resources.configuration) {
            ForestryCounterTheme(themeMode=ThemeMode.LIGHT) {WorksitesContent(vm,{}, {})}
        }}
        compose.waitUntil(10_000) {!vm.state.value.busy}
        compose.onNodeWithTag("site_create").performClick()
        compose.waitUntil(10_000) {!vm.state.value.busy && vm.state.value.form!=null}
        compose.onNodeWithTag("site_name").performTextInput("TEST · Exploitation chêne")
        compose.runOnIdle {vm.change(checkNotNull(vm.state.value.form).copy(parcels=listOf(WorksiteParcel("ONF","TEST-21","TEST parcelle ONF 21"),WorksiteParcel("CADASTRE","TEST-AB-12","TEST parcelle AB 12"))))}
        compose.onNodeWithTag("site_save").performClick()
        compose.waitUntil(10_000) {!vm.state.value.busy && vm.state.value.detail!=null}
        compose.onNodeWithText("TEST · Exploitation chêne").assertIsDisplayed()
        screenshot("chantier-parcelles.png")
        compose.onNodeWithTag("site_options").assertIsEnabled().performClick()
        compose.onNodeWithTag("site_menu_archive").assertIsEnabled().performClick()
        compose.onNodeWithText("Enregistrer").performClick()
        compose.waitUntil(10_000) {!vm.state.value.busy && vm.state.value.detail==null}
        assertTrue(vm.state.value.sites.single().archived)
    }
    @Test fun worksite_name_keeps_keyboard_focus_when_empty_validation_is_cleared() {
        lateinit var vm: WorksitesViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync { vm=WorksitesViewModel(repo);store.put("sites",vm) }
        compose.setContent { CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides compose.activity,
            LocalContext provides context,LocalConfiguration provides context.resources.configuration) {
            ForestryCounterTheme(themeMode=ThemeMode.LIGHT) { WorksitesContent(vm,{}, {}) }
        } }
        compose.waitUntil(10_000) { !vm.state.value.busy }
        compose.onNodeWithTag("site_create").performClick()
        compose.waitUntil(10_000) { !vm.state.value.busy && vm.state.value.form!=null }
        compose.onNodeWithTag("site_save").performClick()
        compose.onNodeWithText(context.getString(R.string.site_name_required)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.field_error_invalid)).assertDoesNotExist()

        compose.onNodeWithTag("site_content").performScrollToNode(hasTestTag("site_name"))
        typeWithoutRefocusing("site_name","TEST chantier")
        compose.onNodeWithText(context.getString(R.string.site_name_required)).assertDoesNotExist()
        assertEquals("TEST chantier",vm.state.value.form?.name)
        screenshot("chantier-saisie-apres-erreur.png")

        // Save from the bottom of the editor; the detail must start at its heading.
        compose.onNodeWithTag("site_content").performScrollToNode(hasText(context.getString(R.string.site_add_parcel)))
        compose.onNodeWithText(context.getString(R.string.site_add_parcel)).performScrollTo()
        compose.onNodeWithTag("site_save").performClick()
        compose.waitUntil(10_000) { !vm.state.value.busy && vm.state.value.detail!=null }
        compose.onNodeWithText("TEST chantier").assertIsDisplayed()
        assertEquals("TEST chantier",runBlocking { repo.sites(repo.captureAccess()) }.single().form.name)
    }
    @Test fun journal_name_keeps_keyboard_focus_when_empty_validation_is_cleared() {
        mount(forest);content()
        compose.onNodeWithTag("field_add").performClick()
        field("field_notes").performTextInput("TEST note sans nom")
        Espresso.closeSoftKeyboard()
        field("field_notes")
        compose.onNodeWithTag("field_save").performClick()
        compose.waitUntil(10_000) { mounted?.state?.value?.error==FieldError.INVALID }
        // No recovery scroll: an invalid save must show its explanation immediately.
        compose.onNodeWithText(context.getString(R.string.field_error_invalid)).assertIsDisplayed()
        field("field_name")
        typeWithoutRefocusing("field_name","TEST observation")
        assertEquals("TEST observation",mounted?.state?.value?.form?.title)
        assertNull(mounted?.state?.value?.error)
        compose.waitUntil(10_000) { mounted?.state?.value?.draftSaved==true }
    }
    private fun typeWithoutRefocusing(tag: String,text: String) {
        // One tap only. Real key events never request focus again between letters:
        // a disposed LazyColumn editor must fail this regression after the first key.
        val input=compose.onNodeWithTag(tag)
        input.performClick().assertIsFocused()
        text.forEach { letter ->
            InstrumentationRegistry.getInstrumentation().sendStringSync(letter.toString())
            compose.waitForIdle()
            input.assertIsFocused()
        }
        input.assertTextContains(text)
    }
    private fun screenshot(name:String) {
        Espresso.closeSoftKeyboard();compose.waitForIdle()
        val image=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file=File(context.getExternalFilesDir(null),"field-tools/$name");checkNotNull(file.parentFile).mkdirs()
        file.outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    companion object {private const val DB="TEST-field-tools-ui.db"}
}
