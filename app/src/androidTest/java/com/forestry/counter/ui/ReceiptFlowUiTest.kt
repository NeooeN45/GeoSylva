package com.forestry.counter.ui

import android.content.Context
import android.content.res.Configuration
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
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
import com.forestry.counter.domain.export.GeoSylvaFieldExporter
import com.forestry.counter.domain.model.*
import com.forestry.counter.domain.repository.*
import com.forestry.counter.domain.wood.*
import com.forestry.counter.presentation.screens.field.*
import com.forestry.counter.presentation.theme.ForestryCounterTheme
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Real encrypted Room and Compose. All fixtures are explicitly synthetic TEST data. */
@RunWith(AndroidJUnit4::class)
class ReceiptFlowUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context: Context by lazy { ApplicationProvider.getApplicationContext<Context>().let {
        it.createConfigurationContext(Configuration(it.resources.configuration).apply { setLocale(Locale.FRANCE) })
    } }
    private val store = ViewModelStore()
    private val viewModelJobs = mutableListOf<Job>()
    private val databaseName = "TEST-receipt-flow-ui-${UUID.randomUUID()}.db"
    private lateinit var db: ForestryDatabase
    private lateinit var repository: FieldRepositoryImpl
    private lateinit var site: SavedWorksite
    private val identity = BusinessReadIdentityFixture()
    private val wood = UUID.randomUUID()
    private val depot = UUID.randomUUID()

    @Before fun setup() = runBlocking<Unit> {
        context.deleteDatabase(databaseName)
        net.sqlcipher.database.SQLiteDatabase.loadLibs(context)
        db = Room.databaseBuilder(context, ForestryDatabase::class.java, databaseName)
            .openHelperFactory(net.sqlcipher.database.SupportFactory("TEST-only-receipt-ui-key".toByteArray()))
            .addMigrations(*DatabaseMigrations.ALL).build()
        val depots = WoodDepotRepositoryImpl(db, identity)
        depots.createDepot(depot.toString(), WoodDepotDraft("TEST · Dépôt du chemin nord", WoodDepotKind.ROADSIDE), depots.captureAccess())
        val woods = WoodObjectRepositoryImpl(db, identity)
        val observation = NewWoodObservation(UUID.randomUUID(), WoodObservationPayload(RawWoodMeasurement.Pile(
            RawWoodDimension("10", "m"), RawWoodDimension("5", "m"), RawWoodDimension("2", "m"),
            RawWoodMeasurement.Pile.RECTANGULAR_ENVELOPE, "TEST enveloppe rectangulaire",
        )))
        woods.execute(WoodObjectCommand.Create(WoodCommandContext(UUID.randomUUID(), depot, wood,
            WoodOwner(WoodDepotOwnerScope.LOCAL_DEVICE), "anonymous"), WoodObjectKind.PILE,
            WoodObjectHeader("TEST · Pile de chêne 01"), observation, true), woods.captureAccess())
        repository = FieldRepositoryImpl(db, identity, BusinessReadAccess(identity, db.parcelSyncDao()))
        val access = repository.captureAccess()
        site = repository.saveSite(UUID.randomUUID().toString(), WorksiteForm("TEST · Chantier des Deux-Sèvres",
            notes = "Données simulées pour la démonstration. Aucune opération commerciale.", parcels = listOf(
                WorksiteParcel("ONF", "TEST-ONF-21", "TEST parcelle forestière 21"),
                WorksiteParcel("CADASTRE", "TEST-AB-12", "TEST parcelle AB 12"))), 0, false, access)
        repository.link(site.id, FieldTarget(FieldTargetKind.WOOD_OBJECT, wood.toString()), access)
    }
    @After fun cleanup() {
        var failure: Throwable? = null
        fun attempt(block: () -> Unit) {
            try { block() } catch (error: Throwable) {
                if (failure == null) failure = error else failure!!.addSuppressed(error)
            }
        }
        // @After runs before the Compose rule closes its activity. Dispose observers and
        // lifecycle-owned VMs first, while their database still exists.
        attempt { compose.activityRule.scenario.close() }
        attempt { InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() } }
        var stopped = false
        attempt {
            // The test thread waits; Main stays free to finish cancellation/disposal.
            runBlocking { withTimeout(15_000) {
                viewModelJobs.forEach { it.cancel() }
                viewModelJobs.joinAll()
            } }
            stopped = true
        }
        // Never delete files underneath an unfinished transaction after a timeout.
        if (stopped) attempt {
            if (::db.isInitialized) db.close()
            context.deleteDatabase(databaseName)
        }
        failure?.let { throw it }
    }

    private fun <T : ViewModel> track(vm: T): T = vm.also {
        viewModelJobs += checkNotNull(it.viewModelScope.coroutineContext[Job])
    }

    @Test fun receipt_review_confirmation_and_frozen_result_use_real_local_stock() {
        lateinit var vm: ReceiptsViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync { vm = track(ReceiptsViewModel(repository, site.id)); store.put("receipts", vm) }
        compose.setContent { CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides compose.activity,
            LocalContext provides context, LocalConfiguration provides context.resources.configuration) {
            ForestryCounterTheme(themeMode = ThemeMode.LIGHT) { ReceiptsContent(vm, {}, {}) }
        } }
        compose.waitUntil(10_000) { !vm.state.value.busy }
        compose.onNodeWithTag("receipt_create").performClick()
        compose.waitUntil(10_000) { !vm.state.value.busy && vm.state.value.form != null }
        compose.runOnIdle {
            vm.change { copy(title = "TEST · Réception bord de route", observed = "19/10/2026 10:00", zone = "Europe/Paris") }
            vm.addWood(vm.state.value.choices.first())
            val line = vm.state.value.form!!.lines.single()
            vm.line(line.id) { copy(quantity = "100", convention = "TEST enveloppe rectangulaire", conventionVersion = "1") }
        }
        compose.onNodeWithTag("receipt_review").performClick()
        compose.waitUntil(10_000) { !vm.state.value.busy && vm.state.value.detail?.status == ReceiptStatus.READY }
        assertTrue(runBlocking { repository.entries(FieldTarget(FieldTargetKind.WOOD_OBJECT, wood.toString()), repository.captureAccess()) }.isEmpty())
        screenshot("reception-controle.png")
        compose.onNodeWithTag("receipt_content").performScrollToNode(hasText("Enveloppe géométrique : 100 m³ apparent"))
        compose.onNodeWithText("Enveloppe géométrique : 100 m³ apparent").assertIsDisplayed()
        screenshot("reception-mesure-et-quantite.png")
        compose.onNodeWithTag("receipt_validate").performClick()
        compose.onNodeWithTag("receipt_confirm").assertIsDisplayed()
        screenshot("reception-confirmation.png")
        compose.onNodeWithTag("receipt_confirm").performClick()
        compose.waitUntil(10_000) { !vm.state.value.busy && vm.state.value.detail?.status == ReceiptStatus.VALIDATED }
        val entries = runBlocking { repository.entries(FieldTarget(FieldTargetKind.WOOD_OBJECT, wood.toString()), repository.captureAccess()) }
        assertEquals(1, entries.size)
        assertEquals("100", entries.single().form.quantity)
        compose.onNodeWithTag("receipt_content").performScrollToIndex(0)
        screenshot("reception-validee.png")
    }

    @Test fun worksite_sections_are_connected_and_visible() {
        lateinit var vm: WorksitesViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync { vm = track(WorksitesViewModel(repository)); store.put("sites", vm) }
        var openedReceipt: String? = null
        compose.setContent { CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides compose.activity,
            LocalContext provides context, LocalConfiguration provides context.resources.configuration) {
            ForestryCounterTheme(themeMode = ThemeMode.LIGHT) { WorksitesContent(vm, {}, {}, { openedReceipt = it }, {}, {}) }
        } }
        compose.waitUntil(10_000) { !vm.state.value.busy }
        compose.runOnIdle { vm.open(site) }
        compose.waitUntil(10_000) { !vm.state.value.busy && vm.state.value.detail != null }
        screenshot("chantier-receptions.png")
        compose.onNodeWithTag("site_content").performScrollToNode(hasTestTag("site_receipts"))
        compose.onNodeWithTag("site_receipts").performClick()
        assertEquals(site.id, openedReceipt)
        compose.onNodeWithTag("site_content").performScrollToNode(hasTestTag("site_export"))
        screenshot("chantier-mouvements-exports.png")
    }

    @Test fun receipt_name_keeps_keyboard_focus_when_empty_review_is_cleared() {
        lateinit var vm: ReceiptsViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync { vm=track(ReceiptsViewModel(repository,site.id));store.put("receipts",vm) }
        compose.setContent { CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides compose.activity,
            LocalContext provides context,LocalConfiguration provides context.resources.configuration) {
            ForestryCounterTheme(themeMode=ThemeMode.LIGHT) { ReceiptsContent(vm,{}, {}) }
        } }
        compose.waitUntil(10_000) { !vm.state.value.busy }
        compose.onNodeWithTag("receipt_create").performClick()
        compose.waitUntil(10_000) { !vm.state.value.busy && vm.state.value.form!=null }
        compose.onNodeWithTag("receipt_content").performScrollToNode(hasText(context.getString(R.string.receipt_choose_wood)))
        compose.onNodeWithTag("receipt_review").performClick()
        compose.waitUntil(10_000) { vm.state.value.error==FieldError.INVALID }
        compose.onNodeWithText(context.getString(R.string.receipt_error_invalid)).assertIsDisplayed()
        compose.onNodeWithTag("receipt_content").performScrollToNode(hasTestTag("receipt_name"))

        // Do not performTextInput for every letter: that would restore focus and hide the bug.
        val input=compose.onNodeWithTag("receipt_name")
        input.performScrollTo().performClick().assertIsFocused()
        "TEST reception".forEach { letter ->
            InstrumentationRegistry.getInstrumentation().sendStringSync(letter.toString())
            compose.waitForIdle()
            input.assertIsFocused()
        }
        input.assertTextContains("TEST reception")
        assertEquals("TEST reception",vm.state.value.form?.title)
        assertNull(vm.state.value.error)
        compose.waitUntil(10_000) { vm.state.value.draftSaved }
    }

    @Test fun archive_is_prepared_and_verified_before_saf_action_is_enabled() {
        val exportedUri = AtomicReference<Uri?>(null)
        val pickerRegistry = ObservedSafRegistry(compose.activity.activityResultRegistry, exportedUri)
        val pickerOwner = object : ActivityResultRegistryOwner { override val activityResultRegistry = pickerRegistry }
        compose.setContent { CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides pickerOwner,
            LocalContext provides context, LocalConfiguration provides context.resources.configuration) {
            ForestryCounterTheme(themeMode = ThemeMode.LIGHT) { WorksiteExportScreen(repository, site.id, {}) }
        } }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // Retrieve the VM already created by the real screen, which belongs to the activity.
            track(ViewModelProvider(compose.activity)["site-export-${site.id}", WorksiteExportViewModel::class.java])
        }
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("field_export_prepare").assertIsEnabled(); true }.getOrDefault(false) }
        compose.onNodeWithTag("field_export_prepare").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("field_export_save").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("field_export_save").performScrollTo().assertIsEnabled()
        screenshot("chantier-export-verifie.png")
        val uniqueName = "TEST-geosylva-saf-${UUID.randomUUID()}.zip"
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val originalFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        try {
            compose.onNodeWithTag("field_export_save").performClick()
            // This opens and operates the actual system DocumentsUI, without constructing an output URI.
            val rootsButton = awaitDocumentNode("storage locations button") { node ->
                node.viewIdResourceName == "android:id/home" || node.contentDescription?.toString()?.lowercase(Locale.ROOT) in setOf(
                    "show roots", "show navigation drawer", "afficher les racines", "ouvrir le menu de navigation", "afficher les emplacements",
                )
            }
            assertTrue("Open the system storage locations", clickNodeOrParent(rootsButton))
            val downloads = awaitDocumentNode("Downloads location") { node ->
                node.text?.toString()?.lowercase(Locale.ROOT) in setOf("downloads", "téléchargements") &&
                    node.isVisibleToUser && hasClickableAncestor(node)
            }
            assertTrue("Choose Downloads in DocumentsUI", clickNodeOrParent(downloads))
            val fileName = awaitDocumentNode("filename input") { it.className?.toString() == "android.widget.EditText" }
            assertTrue("Set unique TEST filename", fileName.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, uniqueName)
            }))
            awaitDocumentNode("updated filename") { it.className?.toString() == "android.widget.EditText" && it.text?.toString() == uniqueName }
            saveScreenshot("chantier-export-selecteur.png")
            val save = awaitDocumentNode("Save button") { node ->
                node.isEnabled && (node.viewIdResourceName?.endsWith(":id/button1") == true ||
                    node.text?.toString()?.lowercase(Locale.ROOT) in setOf("save", "enregistrer"))
            }
            assertTrue("Save through the real system picker", clickNodeOrParent(save))
            compose.waitUntil(15_000) { exportedUri.get() != null }
            val success = "Dossier GeoSylva enregistré à l’emplacement choisi."
            compose.waitUntil(15_000) { compose.onAllNodesWithText(success).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(success).performScrollTo().assertIsDisplayed()
            val uri = checkNotNull(exportedUri.get())
            assertEquals("content", uri.scheme)
            assertTrue("The chosen destination must be Downloads", uri.authority?.contains("downloads.documents") == true ||
                DocumentsContract.getDocumentId(uri).startsWith("primary:Download/"))
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null).use { cursor ->
                assertNotNull("The saved SAF document must be readable", cursor)
                assertTrue(checkNotNull(cursor).moveToFirst())
                assertEquals(uniqueName, cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)))
                assertTrue(cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)) > 0L)
            }
            val verified = checkNotNull(context.contentResolver.openInputStream(uri)).use { GeoSylvaFieldExporter.verify(it) }
            assertEquals(site.id, verified.snapshot.site.id)
            assertEquals(wood.toString(), verified.snapshot.objects.single().id)
            assertEquals(2, verified.snapshot.site.form.parcels.size)
            screenshot("chantier-export-enregistre-et-relu.png")
        } finally {
            automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
            // Delete only the exact newly created TEST document, through its granted URI.
            exportedUri.get()?.let { uri ->
                assertTrue("Remove this test export after verifying it", DocumentsContract.deleteDocument(context.contentResolver, uri))
            }
        }
    }

    /** Observes the real picker result and forwards it unchanged to production Compose code. */
    private class ObservedSafRegistry(
        private val actual: ActivityResultRegistry,
        private val exportedUri: AtomicReference<Uri?>,
    ) : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            val key = "TEST-real-saf-${UUID.randomUUID()}"
            lateinit var launcher: ActivityResultLauncher<I>
            launcher = actual.register(key, contract) { result ->
                if (result is Uri) exportedUri.set(result)
                dispatchResult(requestCode, result)
                launcher.unregister()
            }
            launcher.launch(input, options)
        }
    }

    private fun awaitDocumentNode(description: String, timeoutMs: Long = 15_000, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            val roots = listOfNotNull(automation.rootInActiveWindow) + automation.windows.mapNotNull { it.root }
            roots.filter { it.packageName?.toString()?.contains("documentsui") == true }.forEach { root ->
                findNode(root, predicate)?.let { return it }
            }
            SystemClock.sleep(75)
        }
        val roots = listOfNotNull(automation.rootInActiveWindow) + automation.windows.mapNotNull { it.root }
        val documents = roots.filter { it.packageName?.toString()?.contains("documentsui") == true }
        val trace = StringBuilder()
        var count = 0
        fun describe(node: AccessibilityNodeInfo, depth: Int = 0) {
            if (count++ >= 80 || depth >= 20) return
            fun text(value: CharSequence?) = value?.toString()?.replace(Regex("[\\r\\n\\t]"), " ")?.take(100).orEmpty()
            trace.append("  ".repeat(depth.coerceAtMost(8))).append(text(node.className))
                .append(" id=").append(text(node.viewIdResourceName)).append(" text=").append(text(node.text))
                .append(" description=").append(text(node.contentDescription))
                .append(" clickable=").append(node.isClickable).append(" visible=").append(node.isVisibleToUser).append('\n')
            for (index in 0 until node.childCount) node.getChild(index)?.let { describe(it, depth + 1) }
        }
        documents.forEach { describe(it) }
        if (documents.isEmpty()) trace.append("No DocumentsUI root. Active window packages: ")
            .append(roots.map { it.packageName }.distinct().joinToString())
        runCatching { saveScreenshot("chantier-export-selecteur-echec.png") }
        throw AssertionError("Expected system DocumentsUI element was not found: $description\n$trace")
    }
    private fun findNode(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean, depth: Int = 0): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        if (depth >= 40) return null
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { child -> findNode(child, predicate, depth + 1)?.let { return it } }
        }
        return null
    }
    private fun clickNodeOrParent(initial: AccessibilityNodeInfo): Boolean {
        var node: AccessibilityNodeInfo? = initial
        repeat(8) {
            val current = node ?: return false
            if (current.isEnabled && current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            node = current.parent
        }
        return false
    }
    private fun hasClickableAncestor(initial: AccessibilityNodeInfo): Boolean {
        var node: AccessibilityNodeInfo? = initial
        repeat(8) {
            val current = node ?: return false
            if (current.isEnabled && current.isVisibleToUser && current.isClickable) return true
            node = current.parent
        }
        return false
    }

    private fun screenshot(name: String) {
        Espresso.closeSoftKeyboard(); compose.waitForIdle()
        saveScreenshot(name)
    }
    private fun saveScreenshot(name: String) {
        val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file = File(context.getExternalFilesDir(null), "receipts/$name"); checkNotNull(file.parentFile).mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
