package com.forestry.counter.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect as AndroidRect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.R
import com.forestry.counter.data.local.BusinessReadIdentityFixture
import com.forestry.counter.data.local.ForestryDatabase
import com.forestry.counter.data.preferences.ThemeMode
import com.forestry.counter.data.repository.WoodDepotRepositoryImpl
import com.forestry.counter.domain.model.WoodDepotLocationSource
import com.forestry.counter.domain.model.WoodDepotOwnerScope
import com.forestry.counter.presentation.screens.wood.WoodDepotsContent
import com.forestry.counter.presentation.screens.wood.WoodDepotsViewModel
import com.forestry.counter.presentation.theme.ForestryCounterTheme
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Production UI/repository with an isolated in-memory TEST database; no user preferences or records. */
@RunWith(AndroidJUnit4::class)
class WoodDepotsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val store = ViewModelStore()
    private lateinit var db: ForestryDatabase
    private lateinit var repository: WoodDepotRepositoryImpl
    private lateinit var vm: WoodDepotsViewModel
    private var focusManager: FocusManager? = null
    private val currentVm = mutableStateOf<WoodDepotsViewModel?>(null)
    private val frenchContext: Context by lazy {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.FRANCE) })
    }

    @Before fun setup() {
        compose.activityRule.scenario.onActivity { activity ->
            // Mirror the production activity before mounting the tested Compose content.
            activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            activity.enableEdgeToEdge()
        }
        db = Room.inMemoryDatabaseBuilder(frenchContext, ForestryDatabase::class.java).build()
        repository = WoodDepotRepositoryImpl(db, BusinessReadIdentityFixture())
        replaceViewModel()
    }
    @After fun cleanup() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        db.close()
    }

    @Test fun a_depot_can_be_created_without_coordinates_then_read_by_a_new_repository_and_viewmodel() {
        content()
        compose.waitUntil(5_000) { !vm.uiState.value.loading }
        compose.onNodeWithText(frenchContext.getString(R.string.wood_depots_empty_title)).assertIsDisplayed()
        screenshot("depots-conducteur-etat-vide-fr.png")
        beginForm("TEST · Dépôt bord de route")
        screenshot("depot-formulaire-sans-position-fr.png")
        scrollToTag("wood_depot_notes").performTextInput("TEST · Accès déclaré par le technicien")
        compose.onNodeWithTag("wood_depot_save").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { vm.uiState.value.detail != null && !vm.uiState.value.busy }
        val created = checkNotNull(vm.uiState.value.detail)
        assertNull(created.draft.location)
        assertEquals(WoodDepotOwnerScope.LOCAL_DEVICE, created.ownerScope)
        assertEquals(1, created.revision)
        screenshot("depot-fiche-enregistree-fr.png")
        val frenchDate = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.FRANCE)
            .format(Date(created.updatedAt))
        val updatedLabel = frenchContext.getString(R.string.wood_depots_updated_at, frenchDate)
        compose.onNodeWithTag("wood_depots_content").performScrollToNode(hasText(updatedLabel))
        compose.onNodeWithText(updatedLabel).performScrollTo().assertIsDisplayed()
        // Reopen actual DAO-backed data with a new repository and ViewModel, without seeding anything.
        repository = WoodDepotRepositoryImpl(db, BusinessReadIdentityFixture())
        replaceViewModel()
        compose.waitUntil(5_000) { vm.uiState.value.depots.any { it.id == created.id } }
        scrollToTag("wood_depot_${created.id}").performClick()
        compose.waitUntil(5_000) { vm.uiState.value.detail != null }
        compose.onNodeWithTag("wood_depot_detail_name").assertIsDisplayed()
        assertEquals(created, vm.uiState.value.detail)
        assertEquals(created, runBlocking { repository.getDepot(created.id, repository.captureAccess()) })
    }

    @Test fun manual_decimal_coordinates_have_manual_source_and_archive_requires_confirmation() {
        content(professionCode = "commerce_bois")
        compose.waitUntil(5_000) { !vm.uiState.value.loading }
        beginForm("TEST · Emplacement déclaré")
        scrollToTag("wood_depot_position_toggle").performClick()
        scrollToTag("wood_depot_latitude").performTextInput("46,1827")
        scrollToTag("wood_depot_longitude").performTextInput("-0,5371")
        compose.onNodeWithTag("wood_depots_content").performScrollToNode(
            hasText(frenchContext.getString(R.string.wood_depots_position_manual)))
        compose.onNodeWithText(frenchContext.getString(R.string.wood_depots_position_manual)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("wood_depot_save").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { vm.uiState.value.detail != null && !vm.uiState.value.busy }
        val created = checkNotNull(vm.uiState.value.detail)
        val position = checkNotNull(created.draft.location)
        assertEquals(WoodDepotLocationSource.MANUAL, position.source)
        assertNull(position.accuracyM)
        assertEquals(-0.5371, position.longitude, 0.0)
        scrollToTag("wood_depot_archive").performClick()
        assertNotNull(runBlocking { repository.getDepot(created.id, repository.captureAccess()) })
        compose.onNodeWithTag("wood_depot_confirm_archive").performClick()
        compose.waitUntil(5_000) {
            vm.uiState.value.archiveCandidate == null && !vm.uiState.value.busy && vm.uiState.value.depots.isEmpty()
        }
        assertNull(runBlocking { repository.getDepot(created.id, repository.captureAccess()) })
        val retained = runBlocking { db.woodDepotDao().getByIdIncludingDeleted(created.id) }
        assertNotNull(retained?.deletedAt)
        assertEquals(created.draft.name, retained?.name)
        assertTrue(vm.uiState.value.depots.isEmpty())
    }

    @Test fun field_form_remains_scrollable_with_large_text_and_save_action_remains_visible() {
        content(fontScale = 2f)
        compose.waitUntil(5_000) { !vm.uiState.value.loading }
        beginForm("TEST · Dépôt terrain")
        compose.onNodeWithTag("wood_depot_kind_ROADSIDE").assertIsSelected()
        scrollToTag("wood_depot_position_toggle").assertIsDisplayed()
        compose.onNodeWithTag("wood_depot_save").assertIsDisplayed()
        assertHeaderTitleIsCompletelyVisible()
        assertSaveLabelIsOneCompleteLine()
        assertTextHasNoHeightOverflowOrEllipsis()
        screenshot("depot-formulaire-grande-police-fr.png")
        scrollToTag("wood_depot_notes").assertIsDisplayed()
        assertHeaderTitleIsCompletelyVisible()
        assertSaveLabelIsOneCompleteLine()
        compose.onNodeWithTag("wood_depot_save").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { vm.uiState.value.detail != null && !vm.uiState.value.busy }
        assertNull(vm.uiState.value.detail!!.draft.location)
    }

    private fun beginForm(name: String) {
        scrollToTag("wood_depot_create").performClick()
        scrollToTag("wood_depot_name").performTextInput(name)
        scrollToTag("wood_depot_kind_ROADSIDE").performClick()
    }

    /** Move the actual lazy list until the target is composed, then make the control fully visible. */
    private fun scrollToTag(tag: String) = compose.onNodeWithTag("wood_depots_content")
        .performScrollToNode(hasTestTag(tag)).let { compose.onNodeWithTag(tag).performScrollTo() }

    private fun replaceViewModel() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            vm = WoodDepotsViewModel(repository)
            store.put("TEST-wood-depots", vm)
            currentVm.value = vm
        }
    }

    private fun content(fontScale: Float = 1f, professionCode: String = "conducteur_travaux_forestiers") {
        compose.setContent {
            val density = LocalDensity.current
            val manager = LocalFocusManager.current
            SideEffect { focusManager = manager }
            CompositionLocalProvider(LocalContext provides frenchContext,
                LocalConfiguration provides frenchContext.resources.configuration,
                LocalDensity provides Density(density.density, fontScale)) {
                ForestryCounterTheme(themeMode = ThemeMode.LIGHT) {
                    currentVm.value?.let { WoodDepotsContent(it, {}, professionCode, Modifier) }
                }
            }
        }
    }

    private fun assertTextHasNoHeightOverflowOrEllipsis() {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        assertTrue(nodes.fetchSemanticsNodes().isNotEmpty())
        for (index in nodes.fetchSemanticsNodes().indices) {
            val layouts = mutableListOf<TextLayoutResult>()
            nodes[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            for (layout in layouts) {
                assertFalse("Text must remain readable", layout.didOverflowHeight)
                for (line in 0 until layout.lineCount) assertFalse("No hidden explanatory text", layout.isLineEllipsized(line))
            }
        }
    }

    /** isDisplayed permits partial intersections; these assertions require the entire physical title. */
    private fun assertHeaderTitleIsCompletelyVisible() {
        val interaction = compose.onNodeWithTag("wood_depots_screen_title", useUnmergedTree = true)
        val node = interaction.fetchSemanticsNode()
        val full = Rect(node.positionInRoot.x, node.positionInRoot.y,
            node.positionInRoot.x + node.size.width, node.positionInRoot.y + node.size.height)
        val header = compose.onNodeWithTag("wood_depots_header").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        println("GEOSYLVA_TEST_WOOD_DEPOTS_GEOMETRY=titleFull=$full;titleVisible=${node.boundsInRoot};header=$header;root=$root")
        var ancestor = node
        var layer = 0
        while (true) {
            println("GEOSYLVA_TEST_WOOD_DEPOTS_GEOMETRY=layer=$layer;position=${ancestor.positionInRoot};size=${ancestor.size};visible=${ancestor.boundsInRoot}")
            ancestor = ancestor.parent ?: break
            layer++
        }
        interaction.assertIsDisplayed()
        fun contains(bounds: Rect) = full.left >= bounds.left - 1f && full.top >= bounds.top - 1f &&
            full.right <= bounds.right + 1f && full.bottom <= bounds.bottom + 1f
        assertTrue("Title clipped: full=$full, visible=${node.boundsInRoot}, header=$header, root=$root",
            contains(node.boundsInRoot) && contains(header) && contains(root))
        val layouts = mutableListOf<TextLayoutResult>()
        interaction.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach {
            assertFalse(it.didOverflowHeight)
            assertFalse(it.didOverflowWidth)
            assertEquals(it.layoutInput.text.text.length, it.getLineEnd(it.lineCount - 1, visibleEnd = true))
        }
    }

    private fun assertSaveLabelIsOneCompleteLine() {
        val interaction = compose.onNodeWithTag("wood_depot_save_label", useUnmergedTree = true).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        interaction.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
        assertTrue(layouts.isNotEmpty())
        for (layout in layouts) {
            assertEquals(frenchContext.getString(R.string.wood_depots_save), layout.layoutInput.text.text)
            assertEquals("The save word must not split across lines", 1, layout.lineCount)
            val node = interaction.fetchSemanticsNode()
            println("GEOSYLVA_TEST_WOOD_DEPOTS_SAVE_LAYOUT=size=${layout.size};paragraphWidth=${layout.multiParagraph.width};" +
                "intrinsicWidth=${layout.multiParagraph.intrinsics.maxIntrinsicWidth};lineLeft=${layout.getLineLeft(0)};" +
                "lineRight=${layout.getLineRight(0)};constraints=${layout.layoutInput.constraints};" +
                "position=${node.positionInRoot};nodeSize=${node.size};visible=${node.boundsInRoot};" +
                "fontScale=${layout.layoutInput.density.fontScale};text=${layout.layoutInput.text.text};" +
                "expected=${frenchContext.getString(R.string.wood_depots_save)};overflowWidth=${layout.didOverflowWidth}")
            println("GEOSYLVA_TEST_WOOD_DEPOTS_SAVE_LAYOUT=characters=" +
                layout.layoutInput.text.text.indices.joinToString { "$it:${layout.getBoundingBox(it)}" })
            // Diagnostic native 2026-10-13: paragraph reserves 552px, wrap-content Text is 210px,
            // while every glyph fits in 209px. didOverflowWidth compares these boxes, not glyph clipping.
            // Proof: ../.onboarding/forlog-depots-2026-10-13/native-diagnostic-20261013T185546Z/GEOMETRY.log.
            val tolerancePx = 1f
            assertTrue("The complete line must fit the label", layout.getLineLeft(0) >= -tolerancePx &&
                layout.getLineRight(0) <= layout.size.width + tolerancePx)
            for (offset in layout.layoutInput.text.text.indices) {
                val character = layout.getBoundingBox(offset)
                assertTrue("Save character $offset must fit: $character in ${layout.size}",
                    character.left >= -tolerancePx && character.top >= -tolerancePx &&
                        character.right <= layout.size.width + tolerancePx &&
                        character.bottom <= layout.size.height + tolerancePx)
            }
            assertFalse(layout.didOverflowHeight)
            assertFalse(layout.isLineEllipsized(0))
            assertEquals(layout.layoutInput.text.text.length, layout.getLineEnd(0, visibleEnd = true))
        }
        val node = interaction.fetchSemanticsNode()
        val full = Rect(node.positionInRoot.x, node.positionInRoot.y,
            node.positionInRoot.x + node.size.width, node.positionInRoot.y + node.size.height)
        assertTrue("The entire save label must fit its clipped bounds", full.left >= node.boundsInRoot.left - 1f &&
            full.top >= node.boundsInRoot.top - 1f && full.right <= node.boundsInRoot.right + 1f &&
            full.bottom <= node.boundsInRoot.bottom + 1f)
    }

    private fun screenshot(name: String) {
        settleNativeWindowForScreenshot()
        if (vm.uiState.value.form != null) {
            assertHeaderTitleIsCompletelyVisible()
            assertSaveLabelIsOneCompleteLine()
            assertCancelIsWithinNativeSafeArea()
        }
        val directory = File(requireNotNull(frenchContext.getExternalFilesDir(null)),
            "GeoSylva-TEST-wood-depots-2026-10-13/${UUID.randomUUID()}")
        assertTrue(directory.mkdirs())
        val file = File(directory, name)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        if (vm.uiState.value.form != null) {
            assertHeaderTitleIsCompletelyVisible()
            assertSaveLabelIsOneCompleteLine()
            assertCancelIsWithinNativeSafeArea()
        }
        println("GEOSYLVA_TEST_WOOD_DEPOTS_SCREENSHOT=${file.absolutePath}")
    }

    /** Compose idle alone does not wait for native IME/window animations or PixelCopy frames. */
    private fun settleNativeWindowForScreenshot() {
        compose.runOnIdle { checkNotNull(focusManager).clearFocus(force = true) }
        Espresso.closeSoftKeyboard()
        var previous: NativeWindowState? = null
        var stableSince = SystemClock.uptimeMillis()
        compose.waitUntil(5_000) {
            val current = readNativeWindowState()
            if (current.imeVisible || current != previous) stableSince = SystemClock.uptimeMillis()
            previous = current
            !current.imeVisible && SystemClock.uptimeMillis() - stableSince >= 500L
        }
        val framesDrawn = AtomicBoolean(false)
        compose.activityRule.scenario.onActivity { activity ->
            activity.window.decorView.postOnAnimation {
                activity.window.decorView.postOnAnimation { framesDrawn.set(true) }
            }
        }
        compose.waitUntil(5_000) { framesDrawn.get() }
        compose.waitForIdle()
        assertFalse("The native IME must be hidden before capture", readNativeWindowState().imeVisible)
    }

    private fun assertCancelIsWithinNativeSafeArea() {
        val interaction = compose.onNodeWithTag("wood_depot_cancel").assertIsDisplayed()
        val node = interaction.fetchSemanticsNode()
        val window = readNativeWindowState()
        assertFalse(window.imeVisible)
        val fullOnScreen = Rect(window.composeOrigin.x + node.positionInRoot.x,
            window.composeOrigin.y + node.positionInRoot.y,
            window.composeOrigin.x + node.positionInRoot.x + node.size.width,
            window.composeOrigin.y + node.positionInRoot.y + node.size.height)
        val safe = window.safeBounds
        println("GEOSYLVA_TEST_WOOD_DEPOTS_NATIVE_SAFE_AREA=cancel=$fullOnScreen;safe=$safe;window=$window")
        assertTrue("Cancel must be completely outside the navigation bar: $fullOnScreen in $safe",
            fullOnScreen.left >= safe.left - 1f && fullOnScreen.top >= safe.top - 1f &&
                fullOnScreen.right <= safe.right + 1f && fullOnScreen.bottom <= safe.bottom + 1f)
    }

    private data class NativeWindowState(
        val imeVisible: Boolean,
        val composeOrigin: Offset,
        val safeBounds: Rect,
        val nativeLayout: List<Int>,
    )

    private fun readNativeWindowState(): NativeWindowState {
        var result: NativeWindowState? = null
        compose.activityRule.scenario.onActivity { activity ->
            val decor = activity.window.decorView
            val insets = checkNotNull(ViewCompat.getRootWindowInsets(decor))
            val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val visible = AndroidRect()
            decor.getWindowVisibleDisplayFrame(visible)
            val decorOrigin = IntArray(2).also(decor::getLocationOnScreen)
            val host = checkNotNull(findComposeHost(decor))
            val composeOrigin = IntArray(2).also(host::getLocationOnScreen)
            result = NativeWindowState(insets.isVisible(WindowInsetsCompat.Type.ime()),
                Offset(composeOrigin[0].toFloat(), composeOrigin[1].toFloat()),
                Rect((decorOrigin[0] + navigation.left).toFloat(), (decorOrigin[1] + navigation.top).toFloat(),
                    (decorOrigin[0] + decor.width - navigation.right).toFloat(),
                    (decorOrigin[1] + decor.height - navigation.bottom).toFloat()),
                listOf(decorOrigin[0], decorOrigin[1], decor.width, decor.height,
                    composeOrigin[0], composeOrigin[1], host.width, host.height,
                    visible.left, visible.top, visible.right, visible.bottom, ime.bottom,
                    navigation.left, navigation.top, navigation.right, navigation.bottom))
        }
        return checkNotNull(result)
    }

    private fun findComposeHost(view: View): View? {
        if (view.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findComposeHost(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
